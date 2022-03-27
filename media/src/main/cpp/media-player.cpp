/*
 * Copyright 2022 A2va
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <optional>

extern "C"
{
#include <libavutil/avutil.h>
}

#include "jni/utils.h"
#include "MediaPlayer.h"
#include "jni/exception.h"
#include <jni.h>

static MediaPlayer *getPlayer(JNIEnv *env, jobject playerObject);
static MediaPlayer *setPlayer(JNIEnv *env, jobject playerObject, MediaPlayer *player);
static int jniGetFDFromFileDescriptor(JNIEnv *env, jobject fileDescriptor);
static void setDefaultAudioValues(JNIEnv *env);

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set video surface.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param surface video surface
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1setVideoSurface(JNIEnv *env,
														   jobject thiz,
														   jobject surface) {
  // TODO: implement _setVideoSurface()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set data source from path and header.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param path file path
 * @param keys header keys
 * @params headers
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_nativeSetDataSource(JNIEnv *env,
														 jobject thiz,
														 jstring path,
														 jobjectArray keys,
														 jobjectArray values) {
  std::string str_path = javaStringToString(env, path);

  // Reference: https://github.com/wseemann/FFmpegMediaPlayer
  // wseemann_media_MediaPlayer.cpp Lines 231-235
  auto pos = str_path.find("mms://");
  if(pos != std::string::npos) {
	std::string rep = "mmsh://";
	str_path.replace(pos,rep.length(),rep);
  }


  std::string headers = "";
  if(keys && values != nullptr) {
	int keysCount = env->GetArrayLength(keys);
	int valuesCount = env->GetArrayLength(values);

	if(keysCount !=valuesCount) {
	  const char *exClassName = "java/lang/IllegalArgumentException";
	  NewJavaException(env,exClassName,"Keys and are not the same length");
	  return;
	}

	for(int i=0; i < keysCount; i++) {
	  std::string key = javaStringToString(env,(jstring) env->GetObjectArrayElement(keys, i));
	  headers += key;
	  headers += ": ";

	  std::string value = javaStringToString(env,(jstring) env->GetObjectArrayElement(values, i));
	  headers += value;
	  headers += "\r\n";
	}

  }

  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->setDataSource(str_path, headers);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set data source from file descriptor.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param fileDescriptor file descriptor
 * @param offset offset of file descriptor
 * @param length length of file descriptor
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1setDataSource(JNIEnv *env,
													   jobject thiz,
													   jobject fd,
													   jlong offset,
													   jlong length) {
  if (fd == NULL) {
	NewJavaException(env, "java/lang/IllegalArgumentException", "File descriptor is null");
	return;
  }
  int fd_int = jniGetFDFromFileDescriptor(env, fd);

  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->setDataSource(fd_int, offset, length);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Prepare the player, init ffmpeg and oboe.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1prepare(JNIEnv *env, jobject thiz) {
  MediaPlayer* player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }
  player->prepare();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Prepare the player, init ffmpeg and oboe.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_prepareAsync(JNIEnv *env, jobject thiz) {
  Java_com_github_a2va_media_MediaPlayer__1prepare(env, thiz); // FIXME For now call prepare
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Start/Resume playback.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1start(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }
  player->start();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get audio stream type.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer__1getAudioStreamType(JNIEnv * env, jobject thiz) {
  // TODO: implement _getAudioStreamType()
  return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Stop playback.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1stop(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->stop();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Pause playback.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1pause(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->pause();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get devices, like getDevicesStatic in AudioManager.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1getDevices(JNIEnv * env, jclass clazz, jint flags) {

  jclass audioManagerClass = env->FindClass("android/media/AudioManager");
  jmethodID getDevicesStaticID = env->GetStaticMethodID(audioManagerClass,"getDevicesStatic","(I)Ljava/util/ArrayList;");

  if(env->ExceptionCheck() || getDevicesStaticID == nullptr || audioManagerClass == nullptr) {
	env->ExceptionDescribe();
	env->ExceptionClear();
	return nullptr;
  }

  jobject devices = env->CallStaticObjectMethod(audioManagerClass,getDevicesStaticID, flags);
  return static_cast<jobjectArray>(devices);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set output device.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1setOutputDevice(JNIEnv *env, jobject thiz, jint device_id) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return false;
  }

  player->setOutputDevice(device_id);
  return true;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get routed output device.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1getRoutedDeviceId(JNIEnv * env, jobject thiz) {
	// TODO: implement native_getRoutedDeviceId()

	return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Enable device id callback.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1enableDeviceCallback(JNIEnv *env,jobject thiz, jboolean enabled) {
	// TODO: implement native_enableDeviceCallback()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get video width.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return video width
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_getVideoWidth(JNIEnv *env, jobject thiz) {
  // TODO: implement getVideoWidth()
  return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get video height.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return video height
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_getVideoHeight(JNIEnv *env, jobject thiz) {
  // TODO: implement getVideoHeight()
  return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get metrics.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return metrics object
 * */
extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1getMetrics(JNIEnv * env, jobject thiz) {
  // TODO: implement native_getMetrics()
  return nullptr;
}

/**
 * Native interface with MediaPlayerOboe, see doc of prepare method in java class.
 * Get if the player is playing.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return true if the player is playing
 * */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_a2va_media_MediaPlayer_isPlaying(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return false;
  }
  return player->isPlaying();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set playback params.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param params object
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_setPlaybackParams(JNIEnv *env,jobject thiz, jobject params) {
// TODO: implement setPlaybackParams()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get playback params.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return params object
 * */
extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_a2va_media_MediaPlayer_getPlaybackParams(JNIEnv *env,jobject thiz) {
// TODO: implement getPlaybackParams()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set sync params.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param sync params object
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_setSyncParams(JNIEnv *env,jobject thiz, jobject params) {
  // TODO: implement setPlaybackParams()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get sync params.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return sync params object
 * */
extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_a2va_media_MediaPlayer_getSyncParams(JNIEnv *env,jobject thiz) {
  // TODO: implement setPlaybackParams()
  return nullptr;
}

/**
 * Native interface with MediaPlayer, see documentation of prepare method in java class.
 * Seek in audio in milliseconds.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param msec milliseconds
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1seekTo(JNIEnv *env, jobject thiz, jint msec) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }
  player->seekTo(msec);
}

/**
 * Native interface with MediaPlayerOboe, see doc in java class.
 * Get current position in file.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return current position in milliseconds
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_getCurrentPosition(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return -1;
  }
  return player->getCurrentPosition();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get duration of file.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return duration in milliseconds
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_getDuration(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return -1;
  }
  return player->getDuration();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Release the native object.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_setNextMediaPlayer(JNIEnv *env,
															jobject thiz,
															jobject next) {

  // Retrieve next player native
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  MediaPlayer *nextPlayer = getPlayer(env, next);
  if(nextPlayer == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }
  return player->setNextMediaPlayer(nextPlayer);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Release the native object.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1release(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = setPlayer(env, thiz, nullptr);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }


  player->release();
  delete player;
  setPlayer(env, thiz, nullptr); // Reset native context

}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Reset the player.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1reset(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->reset();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Notify at specific timestamp.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param media_time_us timestamp
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1notifyAt(JNIEnv *env, jobject thiz, jlong media_time_us) {
// TODO: implement _notifyAt()
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set audio stream type.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param streamtype stream type
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1setAudioStreamType(JNIEnv *env,
															jobject thiz,
															jint streamtype) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->setAudioStreamType(streamtype);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set looping to player.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_setLooping(JNIEnv *env, jobject thiz, jboolean looping) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->setLooping(looping);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get if the player is looping.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_a2va_media_MediaPlayer_isLooping(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return false;
  }

  return player->isPlaying();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set left and right volumes.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param leftVolume left volume
 * @param rightVolume right volume
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer__1setVolume(JNIEnv *env,
												   jobject thiz,
												   jfloat left_volume,
												   jfloat right_volume) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }
  player->setVolume(left_volume, right_volume);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set the audio session id.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param session_id audio session id
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_setAudioSessionId(JNIEnv *env,
														   jobject thiz,
														   jint session_id) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return;
  }

  player->setAudioSessionId(session_id);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get the audio session id.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return audio session id
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_getAudioSessionId(JNIEnv *env, jobject thiz) {
  MediaPlayer *player = getPlayer(env, thiz);
  if(player == nullptr) {
	NewJavaException(env,"java/lang/IllegalStateException","Native player can't be retrieve");
	return static_cast<jint>(oboe::SessionId::None);
  }

  return player->getAudioSessionId();
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Native invoke.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return result code
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1invoke(JNIEnv *env,
														jobject thiz,
														jobject request,
														jobject reply) {
  // TODO: implement native_invoke()
  return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Get metadata.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param update_only
 * @param apply_filter
 * @param reply
 * @return result
 * */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1getMetadata(JNIEnv *env,
															 jobject thiz,
															 jboolean update_only,
															 jboolean apply_filter,
															 jobject reply) {
  // TODO: implement native_getMetadata()
  return false;
}

static void callback(void *ptr, int level, const char *fmt, va_list vl) {
  if (level > av_log_get_level())
	return;

  va_list vl2;
  char line[1024];
  static int print_prefix = 1;

  va_copy(vl2, vl);
  av_log_format_line(ptr, level, fmt, vl2, line, sizeof(line), &print_prefix);
  va_end(vl2);

  LOGI("FFMPEG: %s", line);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1init(JNIEnv *env,jclass clazz) {
#ifdef NDEBUG
  av_log_set_level(AV_LOG_FATAL);
#else
  av_log_set_level(AV_LOG_VERBOSE);
#endif

  av_log_set_callback(callback);

  oboe::OboeGlobals::setWorkaroundsEnabled(true);

  setDefaultAudioValues(env);

#if 0
  // FIXME For now disable this
  // Find exclusive cores
  if(getSDKVersion() >= N) {
	jclass process_class = env->FindClass("android/os/Process");
	if(env->ExceptionCheck() || process_class == nullptr) {
	env->ExceptionDescribe();
	env->ExceptionClear();
	return;
	}
	jmethodID getExclusiveCoresID = env->GetStaticMethodID(process_class,"getExclusiveCores","()[I");

	jintArray jCpuIds = static_cast<jintArray>(env->CallStaticObjectMethod(process_class, getExclusiveCoresID));
	std::vector<int> cpuIds = javaArrayToVector(env, jCpuIds);
	MediaPlayerOboe::setCpuIds(cpuIds);

  }
#endif
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Called in constructor, instantiate a new player object and return to java class.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return cast object to long to save into java class
 * */
extern "C"

JNIEXPORT jlong JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1setup(JNIEnv *env,
													   jobject thiz,
													   jobject mediaplayer_this) {
  MediaPlayer *player = new(std::nothrow) MediaPlayer(thiz, mediaplayer_this);
  if (player == nullptr) {
	LOGE("Could not instantiate MediaPlayerOboe");
	return -1;
  }
  return reinterpret_cast<jlong>(player);
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Called in java descrutor, instantiate a new player object and return to java class.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @return cast object to long to save into java class
 * */
extern "C"
JNIEXPORT void JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1finalize(JNIEnv *env, jobject thiz) {
  Java_com_github_a2va_media_MediaPlayer__1release(env, thiz);
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1pullBatteryData(JNIEnv *env,
																 jclass clazz,
																 jobject reply) {
// TODO: implement native_pullBatteryData()
  return 0;
}

/**
 * Native interface with MediaPlayer, see documentation in java class.
 * Set retransmit endpoint.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * @param addr_string
 * @param port
 * @return result code
 * */
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_a2va_media_MediaPlayer_native_1setRetransmitEndpoint(JNIEnv * env, jobject thiz,
																	   jstring addr_string, jint port) {
	// TODO: implement native_setRetransmitEndpoint()
	return 0;
}

/**
 * Set default audios (sample rate and frame per burst) in oboe
 *
 * @param env jni env
 * */
static void setDefaultAudioValues(JNIEnv *env) {
  // Port of java code in jni.
  // Get from getting started Optimal latency on google oboe repo.
  if(getSDKVersion() >= JELLY_BEAN_MR1) {
	std::string temp;
	jobject context = getGlobalContext(env);
	if(env->ExceptionCheck() || context == nullptr) {
	  LOGE("Unable to get context instance");
	  env->ExceptionDescribe();
	  env->ExceptionClear();
	  return;
	}
	// Get audio manager from context
	jclass Context_class = env->FindClass("android/content/Context");
	jmethodID getSystemServiceID = env->GetMethodID(Context_class, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
	jobject AudioMgr = env->CallObjectMethod(context,getSystemServiceID,env->NewStringUTF("audio"));
	if(env->ExceptionCheck() || AudioMgr == nullptr) {
	  LOGE("Unable to get AudioManager instance");
	  env->ExceptionDescribe();
	  env->ExceptionClear();
	  return;
	}

	jclass AudioManager_class = env->FindClass("android/media/AudioManager");
	jmethodID getPropertyID = env->GetMethodID(AudioManager_class, "getProperty","(Ljava/lang/String;)Ljava/lang/String;");

	// Get sample rate
	jstring sampleRateStr = static_cast<jstring>(env->CallObjectMethod(AudioMgr, getPropertyID,env->NewStringUTF("android.media.property.OUTPUT_SAMPLE_RATE")));
	if(env->ExceptionCheck() || sampleRateStr == nullptr) {
	  LOGE("Unable to get sample rate jstring");
	  env->ExceptionDescribe();
	  env->ExceptionClear();
	  return;
	}
	temp = javaStringToString(env,sampleRateStr);
	int sampleRate = std::stoi(temp);

	// Get frames per burst
	jstring framePerBurstStr = static_cast<jstring>(env->CallObjectMethod(AudioMgr, getPropertyID,env->NewStringUTF("android.media.property.OUTPUT_FRAMES_PER_BUFFER")));
	temp = javaStringToString(env,framePerBurstStr);
	if(env->ExceptionCheck() || framePerBurstStr == nullptr) {
	  LOGE("Unable to get frame per burst jstring");
	  env->ExceptionDescribe();
	  env->ExceptionClear();
	  return;
	}
	int framePerBurst = std::stoi(temp);

	// Set default sample rate and frame per burst in oboe
	oboe::DefaultStreamValues::SampleRate = static_cast<int32_t>(sampleRate);
	oboe::DefaultStreamValues::FramesPerBurst = static_cast<int32_t>(framePerBurst);
  }
}

/**
 * Get a player instance from native context in java instance.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
static MediaPlayer *getPlayer(JNIEnv *env, jobject playerObject) {
  jlong player = -1;
  jclass fdClass = env->FindClass(MEDIA_PACKAGE_PLAYER);

  if (fdClass != nullptr) {
	jfieldID fdClassDescriptorFieldID = env->GetFieldID(fdClass, "mNativeContext", "J");
	if (fdClassDescriptorFieldID != nullptr && playerObject != nullptr) {
	  player = env->GetLongField(playerObject, fdClassDescriptorFieldID);
	}
  }

  if (player == -1) {
	const char *exClassName = "java/lang/IllegalArgumentException";
	NewJavaException(env,exClassName,"Unable to retrieve native instance");
	return nullptr;
  }

  if(player == 0) {
    // Native class already release
	return nullptr;
  }

  return reinterpret_cast<MediaPlayer *>(player);
}

/**
 * Get a player instance from native context in java instance.
 *
 * @param env jni env
 * @param thiz MediaPlayer instance
 * */
static MediaPlayer* setPlayer(JNIEnv *env, jobject playerObject, MediaPlayer *player) {
  jlong oldPlayer = -1;
  jclass fdClass = env->FindClass(MEDIA_PACKAGE_PLAYER);

  if (fdClass != nullptr) {
	jfieldID fdClassDescriptorFieldID = env->GetFieldID(fdClass, "mNativeContext", "J");
	if (fdClassDescriptorFieldID != nullptr && playerObject != nullptr) {
		oldPlayer = env->GetLongField(playerObject, fdClassDescriptorFieldID);
		env->SetLongField(playerObject, fdClassDescriptorFieldID, reinterpret_cast<jlong>(player));
	}
  }

  if (oldPlayer == -1) {
	const char *exClassName = "java/lang/IllegalArgumentException";
	NewJavaException(env,exClassName,"Unable to retrieve native instance");
	return nullptr;
  }

  if(oldPlayer == 0) {
	// Native class already release
	return nullptr;
  }

  return reinterpret_cast<MediaPlayer *>(player);
}

/**
 * Get the file descriptor integer from a file descriptor object.
 *
 * @param env jni env
 * @param fileDescriptor file descriptor object
 * @return file descriptor integer
 * @throw IOException
 * */
static int jniGetFDFromFileDescriptor(JNIEnv *env, jobject fileDescriptor) {
  jint fd = -1;
  jclass fdClass = env->FindClass("java/io/FileDescriptor");
  if (fdClass != nullptr) {
	jfieldID fdClassDescriptorFieldID = env->GetFieldID(fdClass, "descriptor", "I");
	if (fdClassDescriptorFieldID != nullptr && fileDescriptor != nullptr) {
	  fd = env->GetIntField(fileDescriptor, fdClassDescriptorFieldID);
	}
  }

  if (fd == -1) {
	NewJavaException(env, "java/lang/IOException", "Unable to get file descriptor integer");
  }

  return fd;
}
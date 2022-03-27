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

#include <unistd.h>
#include <sched.h>
#include <exception>
#include <oboe/AudioStreamBuilder.h>

#include "MediaPlayer.h"
#include "jni/exception.h"
#include "jni/utils.h"
#include "constants.h"

std::vector<int> MediaPlayer::mCpuIds;

MediaPlayer::MediaPlayer(jobject thiz, jobject weak_thiz) {

  ScopedEnv env;

  //jclass clazz = env.get()->GetObjectClass(mThiz); // FIXME Find a fix for this
  // Alternatives https://stackoverflow.com/questions/12719766/can-i-know-the-name-of-the-class-that-calls-a-jni-c-method
  jclass clazz = env.get()->FindClass(MEDIA_PACKAGE_PLAYER);
  if(clazz == nullptr){
    LOGE("Unable to get object class from this");
  }

  mClass = static_cast<jclass>(env.get()->NewGlobalRef(clazz));
  mThiz = static_cast<jclass>(env.get()->NewGlobalRef(thiz));

  mWeakThiz  = env.get()->NewGlobalRef(weak_thiz);

  mNotifyID = env.get()->GetStaticMethodID(mClass, "postEventFromNative",
												   "(Ljava/lang/Object;IIILjava/lang/Object;)V");

}

MediaPlayer::~MediaPlayer() {
  ScopedEnv env;
  env.get()->DeleteGlobalRef(mWeakThiz);
  env.get()->DeleteGlobalRef(mClass);
  env.get()->DeleteGlobalRef(mThiz);

}

/**
 * Set data source from url and headers.
 *
 * @param url file path
 * @param headers http headers
 * */
void MediaPlayer::setDataSource(std::string &url, std::string &headers) {
  if(mState != State::IDLE) {
    ScopedEnv env;
	NewJavaException(env.get(),"java/lang/IllegalStateException","MediaPlayer is not in IDLE state");
	return; // Wrong state
  }
  mState = State::INITIALIZED;
  mDecoder.setDataSource(url, headers);
}

/**
 * Set data source from file descriptor.
 *
 * @param fd file descriptor
 * @param offset offset
 * @param length length
 * */
void MediaPlayer::setDataSource(int fd, int64_t offset, int64_t length) {
  if(mState != State::IDLE) {
	ScopedEnv env;
	NewJavaException(env.get(),"java/lang/IllegalStateException","MediaPlayer is not in IDLE state");
	return; // Wrong state
  }
  mState = State::INITIALIZED;
  mDecoder.setDataSource(fd, offset, length);
}

/**
 * Prepare the decoder. Must call before everything else.
 * */
void MediaPlayer::prepare() {
  // TODO Create async prepare
  ScopedEnv env;
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  if ((mState != State::INITIALIZED) && (mState != State::STOPPED)) {
	NewJavaException(env.get(),
					 "java/lang/IllegalStateException",
					 "MediaPlayer is not in INITIALIZED or STOPPED state");
	return; // Wrong state
  }

  oboe::Result result = openOboeStream();
  if (result != oboe::Result::OK) {
	notify(MEDIA_ERROR,MEDIA_ERROR_UNKNOWN,0);
	mState = State::ERROR;
	return;
  }

  try {
	mDecoder.prepare();
	mDecoder.setCompletionCallback(this);
	mDecoder.setErrorCallback(this);
	mState = State::PREPARED;
  } catch(std::runtime_error &e) {
	const char *exClassName = "java/lang/IllegalArgumentException";
	NewJavaException(env.get(), exClassName, "FFmpeg error");
  }

  notify(MEDIA_PREPARED,0,0);
}

/**
 * Start/Resume the playback.
 * */
void MediaPlayer::start() {
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  if((mState != State::PREPARED)  && (mState != State::PLAYBACKCOMPLETED) && (mState!= State::PAUSED)) {
	ScopedEnv env;
    NewJavaException(env.get(),"java/lang/IllegalStateException","MediaPlayer is not in INITIALIZED or STOPPED state");
	return;
  }

  if(mState == State::PLAYBACKCOMPLETED) {
	seekTo(0); // Restart from the beginning
  }

  mState = State::STARTED;
  mStream->requestStart();
  mDecoder.start();
  notify(MEDIA_STARTED,0,0);
}

/**
 * Stop the playback.
 * */
void MediaPlayer::stop() {
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  if((mState != State::PAUSED) && (mState != State::STARTED)
	  && (mState != State::PLAYBACKCOMPLETED)) {
	ScopedEnv env;
	NewJavaException(env.get(),"java/lang/IllegalStateException","MediaPlayer is not in PAUSED, STARTED or PLAYBACKCOMPLETED state");
	return;
  }

  mState = State::STOPPED;
  mStream->requestStop();
  mDecoder.pause();
  notify(MEDIA_STOPPED,0,0);
}

/**
 * Pause the playback.
 * */
void MediaPlayer::pause() {
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  if(mState != State::STARTED) {
	ScopedEnv env;
	NewJavaException(env.get(),"java/lang/IllegalStateException","MediaPlayer is not in STARTED state");
	return;
  }

  mState = State::PAUSED;
  mStream->requestPause();
  mDecoder.pause();
  notify(MEDIA_PAUSED,0,0);
}

void MediaPlayer::setOutputDevice(int device) {
  mOutputDevice = device;
  restartOboeStream();
}

int MediaPlayer::getRoutedDeviceI() {
  return mStream->getDeviceId();
}

void MediaPlayer::setLooping(bool loop) {
  mIsLooping = loop;
}

/**
 * Get if the player is looping.
 * */
bool MediaPlayer::isLooping() {
  return false;
}

/**
 * Get is the player is playing
 * */
bool MediaPlayer::isPlaying() {
  return mDecoder.isPlaying();
}

/**
 * Seek in the file.
 *
 * @param msecs in milliseconds
 *
 * */
void MediaPlayer::seekTo(int msecs) {
  mDecoder.seekTo(msecs);
  notify(MEDIA_SEEK_COMPLETE,0,0);
}

/**
 * Get position in the file
 *
 * @return position in the file
 * */
int MediaPlayer::getCurrentPosition() {
  return mDecoder.getCurrentPosition();
}

/**
 * Get the duration in milliseconds.
 *
 * @return duration of the file in milliseconds
 * */
int MediaPlayer::getDuration() {
  return mDecoder.getDuration();
}

/**
 * Set the next media player
 *
 * @param nextPlayer the player to set to
 * */
void MediaPlayer::setNextMediaPlayer(MediaPlayer *nextPlayer) {
  mNextPlayer = nextPlayer;
}

/**
 * Release the decoder.
 * */
void MediaPlayer::release() {
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  mState = State::END;
  closeOboeStream();
  mDecoder.release();
}

/**
 * Reset the player, return before setDataSource and prepare.
 * */
void MediaPlayer::reset() {
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  mState = State::IDLE;
  closeOboeStream();  // Close oboe stream
  mDecoder.reset(); // Reset the decoder

}

void MediaPlayer::setAudioStreamType(int streamType) {
	// Audio stream type can be only set with oboe stream builder
	// so we have to reopen oboe stream.
	mStreamType = streamType;
	restartOboeStream();
}

/**
 * Set both volumes for left and right.
 *
 * @param leftVolume left volume
 * @param rightVolume right volume
 * */
void MediaPlayer::setVolume(float leftVolume, float rightVolume) {
  mLeftVolume = leftVolume;
  mRightVolume = rightVolume;
}

/**
 * Set the audio session id.
 *
 * @param id audio session id
 * */
void MediaPlayer::setAudioSessionId(int id) {
  mSessionsID = id;
  // Session id can be only set with oboe stream builder
  // so we have to reopen oboe stream.
  restartOboeStream();
}

/**
 * Get the audio session id.
 *
 * @return audio session id
 * */
int MediaPlayer::getAudioSessionId() {
  return static_cast<int>(mStream->getSessionId());
}

// TODO Add doc on callback
oboe::DataCallbackResult MediaPlayer::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {

  if (mIsThreadAffinityEnabled && !mIsThreadAffinitySet) {
	//setThreadAffinity();
	mIsThreadAffinitySet = true;
  }

  auto *outputData = static_cast<float *>(audioData);

  // TODO Set volume to each frame/channel
  // https://github.com/google/oboe/issues/798#issuecomment-596144040

  //LOGD("Available write frame %d", mDecoder.getWriteFramesAvailable());
  mDecoder.getFrame(audioData, numFrames);
  // TODO Rework notify
  mDecoder.notify();

  // TODO Mutex ?
  if(mState == State::ERROR) {
	return oboe::DataCallbackResult::Stop;
  }

  return oboe::DataCallbackResult::Continue;
}


void MediaPlayer::onCompletion() {
  mState= State::PLAYBACKCOMPLETED;
  notify(MEDIA_PLAYBACK_COMPLETE, 0,0);
  if(mIsLooping) {
	mDecoder.seekTo(0);
    mDecoder.launchDecodeThread();
	return;
  }

  LOGD("File completed");
  stop();

  // Directly start the next player
  if(mNextPlayer!=nullptr) {
	mNextPlayer->start();
  }
}

/**
 * Callback when the End of file is reached.
 * */
void MediaPlayer::onFFmpegError(const char *msg, int code) {
  LOGD("Ffmpeg error");
  // TODO Better ffmpeg error code handling
  notify(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN,0);
}

/**
 * Callback to player when ffmpeg produce and error.
 *
 * @param msg msg if there is one
 * @param code error code
 * */
bool MediaPlayer::onError(oboe::AudioStream* oboeStream, oboe::Result error){
  notify(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN,0);
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  mState = State::ERROR;
  LOGE("Oboe error");
  return false;
}

/**
 * Callback when oboe get an error.
 *
 * @param oboeStream stream where the error occured
 * @param error
 * */
void MediaPlayer::onErrorBeforeClose(oboe::AudioStream* oboeStream, oboe::Result error) {
  LOGE("Oboe error before close");
  notify(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN,0);
	// State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  mState = State::ERROR;
}

/**
 * Callback when oboe get an error.
 *
 * @param oboeStream stream where the error occured
 * @param error
 * */
void MediaPlayer::onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error) {
  LOGE("Oboe error after close");
  notify(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN,0);
  // State diagram https://developer.android.com/images/mediaplayer_state_diagram.gif
  mState = State::ERROR;
}

/*
 * Notify the java class.
 *
 * @param msg message id, error, playback complete,..
 * @param ext1
 * @param ext2
 * */
void MediaPlayer::notify(int msg, int ext1, int ext2) {

  ScopedEnv env;

  // Notify the java instance
  env.get()->CallStaticVoidMethod(mClass, mNotifyID, mWeakThiz, msg, ext1, ext2, nullptr);

  if (env.get()->ExceptionCheck()) {
	LOGE("An exception occurred while notifying an event");
	env.get()->ExceptionDescribe();
	env.get()->ExceptionClear();
  }
}

/**
 * Set the CPU IDs to bind the audio callback thread to
 *
 * @param mCpuIds - the CPU IDs to bind to
 */
void MediaPlayer::setCpuIds(std::vector<int> cpuIds) {
  mCpuIds = std::move(cpuIds);
}

/**
 * Set the thread affinity for the current thread to mCpuIds. This can be useful to call on the
 * audio thread to avoid underruns caused by CPU core migrations to slower CPU cores.
 */
void MediaPlayer::setThreadAffinity() {
  pid_t current_thread_id = gettid();
  cpu_set_t cpu_set;
  CPU_ZERO(&cpu_set);

  // If the callback cpu ids aren't specified then bind to the current cpu
  if (mCpuIds.empty()) {
	int current_cpu_id = sched_getcpu();
	LOGD("Binding to current CPU ID %d", current_cpu_id);
	CPU_SET(current_cpu_id, &cpu_set);
  } else {
	LOGD("Binding to %d CPU IDs", static_cast<int>(mCpuIds.size()));
	for (size_t i = 0; i < mCpuIds.size(); i++) {
	  int cpu_id = mCpuIds.at(i);
	  LOGD("CPU ID %d added to cores set", cpu_id);
	  CPU_SET(cpu_id, &cpu_set);
	}
  }

  int result = sched_setaffinity(current_thread_id, sizeof(cpu_set_t), &cpu_set);
  if (result == 0) {
	LOGV("Thread affinity set");
  } else {
	LOGW("Error setting thread affinity. Error no: %d", result);
  }

  mIsThreadAffinitySet = true;
}

/**
 * Build oboe stream.
 * */
oboe::Result MediaPlayer::buildOboeStream() {
  oboe::AudioStreamBuilder builder;
  oboe::Result result = builder.setSharingMode(oboe::SharingMode::Shared)
	  ->setPerformanceMode(oboe::PerformanceMode::PowerSaving)
	  ->setFormat(oboe::AudioFormat::Float)
	  ->setFormatConversionAllowed(true)
	  ->setChannelCount(kChannelCount)
	  ->setSampleRate(kSampleRate)
	  ->setDataCallback(this)
	  ->setErrorCallback(this)
	  ->setUsage(oboe::Usage::Media)
	  ->setDeviceId(mOutputDevice)
	  ->setContentType(static_cast<oboe::ContentType>(mStreamType))
	  ->setFramesPerDataCallback(700)
	  ->setSessionId(static_cast<oboe::SessionId>(mSessionsID))
	  ->openStream(mStream);
  oboe::ResultWithValue<int32_t> setBufferSizeResult = mStream->setBufferSizeInFrames(mStream->getFramesPerBurst()*5);
  if(setBufferSizeResult) {
	LOGD("Set Buffer size to %d", setBufferSizeResult.value());
  }

  if(!mStream->usesAAudio()) {
    LOGW("Oboe stream doest use AAudio");
  }

  return result;
}

/**
 * Open oboe audio stream.
 * */
oboe::Result MediaPlayer::openOboeStream() {
  std::lock_guard<std::mutex> lock(mLock);
  oboe::Result result = buildOboeStream();
  if(result != oboe::Result::OK) {
	LOGE("Error creating playback stream.");
	return result;
  }

  return result;
}

/**
 * Close oboe audio stream.
 * */
oboe::Result MediaPlayer::closeOboeStream() {
  oboe::Result result = oboe::Result::OK;
  // Stop, close and delete in case not already closed.
  std::lock_guard<std::mutex> lock(mLock);
  if (mStream) {
	mStream->close();
	mStream.reset();
  }
  return result;
}

/**
 * Free oboe stream and reopen it.
 * */
oboe::Result MediaPlayer::reopenOboeStream() {
  if (mStream) {
	closeOboeStream();
	return openOboeStream();
  } else {
	return oboe::Result::OK;
  }
}

/**
 * Reopen oboe stream and send notify error if needed.
 * */
void MediaPlayer::restartOboeStream() {
  stop(); // Stop current playback
  oboe::Result result = reopenOboeStream();
  if(result != oboe::Result::OK) {
	notify(MEDIA_ERROR,MEDIA_ERROR_UNKNOWN,0);
	mState = State::ERROR;
  }
  start(); // Restart playback
}
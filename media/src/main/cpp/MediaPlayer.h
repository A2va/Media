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

#ifndef MEDIAPLAYER_CLASS
#define MEDIAPLAYER_CLASS

#include <jni.h>
#include <string>
#include <vector>
#include <oboe/AudioStream.h>

#include "callback/FFmpegCallback.h"
#include "FFmpegDecoder.h"

// TODO constexpr ?

// Media error constants
// See documentation in MediaPlayer class
const int MEDIA_ERROR_UNKNOWN = 1;
const int MEDIA_ERROR_SERVER_DIED = 100;
const int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;
const int MEDIA_ERROR_IO = -1004;
const int MEDIA_ERROR_MALFORMED = -1007;
const int MEDIA_ERROR_UNSUPPORTED = -1010;
const int MEDIA_ERROR_TIMED_OUT = -110;
const int MEDIA_ERROR_SYSTEM = -2147483648;

// Media event constants
const int MEDIA_NOP = 0;
const int MEDIA_PREPARED = 1;
const int MEDIA_PLAYBACK_COMPLETE = 2;
const int MEDIA_BUFFERING_UPDATE = 3;
const int MEDIA_SEEK_COMPLETE = 4;
const int MEDIA_SET_VIDEO_SIZE = 5;
const int MEDIA_STARTED = 6;
const int MEDIA_PAUSED = 7;
const int MEDIA_STOPPED = 8;
const int MEDIA_SKIPPED = 9;
const int MEDIA_NOTIFY_TIME = 98;
const int MEDIA_TIME_TEXT = 99;
const int MEDIA_ERROR = 100;
const int MEDIA_INFO = 200;
const int MEDIA_SUBTITLE_DATA = 201;
const int MEDIA_META_DATA = 202;
const int MEDIA_DRM_INFO = 210;
const int MEDIA_TIME_DISCONTINUITY = 211;
const int MEDIA_AUDIO_ROUTING_CHANGED = 10000;


// TODO Future idea MediaPlayer have only the state machine, and the rest is for decoder
// Callaback between producer (decoder one) to consumer (audio and video).
// TODO Find library like oboe but for handling images.

enum class State: int32_t { IDLE=0, END, ERROR, INITIALIZED, PREPARING, PREPARED, STARTED, STOPPED, PAUSED, PLAYBACKCOMPLETED};

class MediaPlayer : public CompletionCallback, ErrorCallback, oboe::AudioStreamErrorCallback, oboe::AudioStreamDataCallback {
 public:
  	MediaPlayer(jobject thiz, jobject weak_thiz);
  	~MediaPlayer();
    /**
     * Set data source from url and headers.
     *
     * @param url file path
     * @param headers http headers
     * */
    void setDataSource(std::string &url, std::string &headers);

    /**
     * Set data source from file descriptor.
     *
     * @param fd file descriptor
	 * @param offset offset
	 * @param length length
     * */
   	void setDataSource(int fd, int64_t offset, int64_t length);

    /**
     * Prepare the decode. Must call before everything else.
     * */
    void prepare();

    /**
     * Start/Resume the playback.
     * */
	void start();

	/**
	 * Stop the playback.
	 * */
  	void stop();

  	/**
  	 * Pause the playback.
  	 * */
  	void pause();

  	/**
  	 * Set output device.
  	 * */
  	void setOutputDevice(int device);

  	/**
  	 * Get routed device id.
  	 * */
  	int getRoutedDeviceI();

  	/**
  	 * Set isLooping.
  	 * */
  	void setLooping(bool loop);

	/**
	 * Get if the player is looping
	 * */
	bool isLooping();

  	/**
  	 * Get is the player is playing
  	 * */
  	bool isPlaying();

  	/**
  	 * Seek in the file.
  	 *
  	 * @param msecs in milliseconds
  	 *
  	 * */
  	void seekTo(int msecs);

  	/**
  	 * Get position in the file
  	 *
  	 * @return position in the file
  	 * */
	int getCurrentPosition();

	/**
	 * Get the duration in milliseconds.
	 *
	 * @return duration of the file in milliseconds
	 * */
  	int getDuration();

  	/**
  	 * Set the next player.
  	 * */
  	void setNextMediaPlayer(MediaPlayer *nextPlayer);

    /**
     * Release the decoder.
     * */
    void release();

    /**
     * Reset the player, return before setDataSource and prepare.
     * */
    void reset();

    /**
     * Set audio stream type.
     * */
  	void setAudioStreamType(int streamType);

	/**
	 * Set both volumes for left and right.
	 *
	 * @param leftVolume left volume
	 * @param rightVolume right volume
	 * */
	void setVolume(float leftVolume,float rightVolume);

    /**
     * Set the audio session id.
     *
     * @param id audio session id
     * */
    void setAudioSessionId(int id);

  	/**
  	 * Get the audio session id.
  	 *
  	 * @return audio session id
  	 * */
    int getAudioSessionId();


	// Inherited from oboe::AudioStreamDataCallback.
	oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;

	// Inherited from oboe::AudioStreamErrorCallback.
	bool onError(oboe::AudioStream* oboeStream, oboe::Result error) override;
	void onErrorBeforeClose(oboe::AudioStream* oboeStream, oboe::Result error) override;
	void onErrorAfterClose(oboe::AudioStream* oboeStream, oboe::Result error) override;


	/**
	 * Callback when the End of file is reached.
	 * */
	void onCompletion() override;

	/**
	 * Callback to player when ffmpeg produce and error.
	 *
	 * @param msg msg if there is one
	 * @param code error code
	 * */
	void onFFmpegError(const char *msg,int code) override;

	static void setCpuIds(std::vector<int> cpuIds);

   private:

	oboe::Result buildOboeStream();
	oboe::Result reopenOboeStream();
	oboe::Result openOboeStream();
	oboe::Result closeOboeStream();
	void restartOboeStream();
	// TODO Maybe get the value for MEDIA_ERROR in java class

	// Thread affinity stuff
	void setThreadAffinity();
	static std::vector<int> mCpuIds; // IDs of CPU cores which the audio callback should be bound to
	std::atomic<bool> mIsThreadAffinityEnabled { false };
	std::atomic<bool> mIsThreadAffinitySet { false };
	/*
	 * Notify the java class.
	 *
	 * @param msg message id, error, playback complete,..
	 * @param ext1
	 * @param ext2
	 * */
	void notify(int msg, int ext1, int ext2);

	float mLeftVolume = 1;
	float mRightVolume = 1;

	FFmpegDecoder mDecoder;
	MediaPlayer *mNextPlayer = nullptr;

	bool mIsLooping = false;
  	State mState = State::IDLE;

	int mSessionsID = oboe::SessionId::Allocate;
	int mStreamType = oboe::kUnspecified;
	int mOutputDevice = oboe::kUnspecified;
	int mUsage = oboe::kUnspecified;

	std::shared_ptr<oboe::AudioStream> mStream;
	std::mutex mLock;

	jclass mClass;     // Reference to MediaPlayer class
	jobject mWeakThiz;    // Weak Reference to MediaPlayer Java object to call on
	jobject mThiz;		// Reference to MediaPlayer Java object
 	jmethodID mNotifyID; // Callback function to java class
};

#endif //MEDIAPLAYER_CLASS

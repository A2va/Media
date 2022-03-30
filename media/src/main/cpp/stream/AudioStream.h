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

#ifndef MEDIA_AUDIOSTREAM
#define MEDIA_AUDIOSTREAM

#include <oboe/AudioStream.h>

#include "Stream.h"

class AudioStream: public Stream, oboe::AudioStreamDataCallback{

 public:
  /**
   * Open stream.
   * */
  void open() override;

  /*
   * CLose stream.
   * */
  void close() override;

  /**
   * Start the stream.
   * */
  void start(int64_t timeoutNanoseconds) override;

  /**
   * Pause the stream.
   * */
  void pause(int64_t timeoutNanoseconds) override;

  /**
   * Flush the stream.
   * */
  void flush(int64_t timeoutNanoseconds) override;

  /**
   * Stop the stream.
   * */
  void stop(int64_t timeoutNanoseconds) override;

  /**
   * Start the stream asynchronously.
   * */
  void requestStart() override;

  /**
   * Pause the stream asynchronously.
   * */
  void requestPause() override;

  /**
   * Flush the stream asynchronously.
   * */
  void requestFlush() override;

  /*
  /*
   * Stop the stream asynchronously.
   * */
  void requestStop() override;

  /**
   * Set stream usage.
   *
   * @param usage
   * */
  void setUsage(int usage);

  /**
   * Set audio stream type.
   *
   * @param streamType
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


  static void setCpuIds(std::vector<int> cpuIds);

 private:
  std::shared_ptr<oboe::AudioStream> mStream;
  std::mutex mLock;

  int mSessionsID = oboe::SessionId::Allocate;
  int mStreamType = oboe::kUnspecified;
  int mUsage = oboe::kUnspecified;
  int mOutputDevice = oboe::kUnspecified;

  float mLeftVolume = 1;
  float mRightVolume = 1;

  // Thread affinity stuff
  void setThreadAffinity();
  static std::vector<int> mCpuIds; // IDs of CPU cores which the audio callback should be bound to
  std::atomic<bool> mIsThreadAffinityEnabled { false };
  std::atomic<bool> mIsThreadAffinitySet { false };


};

#endif //MEDIA_AUDIOSTREAM

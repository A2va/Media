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

#include <sched.h>
#include <unistd.h>

#include <oboe/AudioStreamBuilder.h>

#include "jni/utils.h"
#include "constants.h"
#include "AudioStream.h"

std::vector<int> AudioStream::mCpuIds;

/**
 * Open stream.
 * */
void AudioStream::open() {
  std::lock_guard<std::mutex> lock(mLock);
  oboe::AudioStreamBuilder builder;
  builder.setSharingMode(oboe::SharingMode::Shared)
	  ->setPerformanceMode(oboe::PerformanceMode::PowerSaving)
	  ->setFormat(oboe::AudioFormat::Float)
	  ->setFormatConversionAllowed(true)
	  ->setChannelCount(kChannelCount)
	  ->setSampleRate(kSampleRate)
	  //->setDataCallback(this)
	  //->setErrorCallback(this)
	  ->setSessionId(static_cast<oboe::SessionId>(mSessionsID))
	  ->setFramesPerDataCallback(700);

  // Set stream usage
  if(mUsage != oboe::kUnspecified) {
    builder.setUsage(static_cast<oboe::Usage>(mUsage));
  }

  // Set stream content type
  if(mStreamType != oboe::kUnspecified) {
    builder.setContentType(static_cast<oboe::ContentType>(mStreamType));
  }

  // Set output device
  if(mOutputDevice != oboe::kUnspecified) {
    builder.setDeviceId(mOutputDevice);
  }

  // Open stream
  oboe::Result result = builder.openStream(mStream);
  if(result != oboe::Result::OK) {
	// TODO Exception
	return;
  }

  oboe::ResultWithValue<int32_t> setBufferSizeResult = mStream->setBufferSizeInFrames(mStream->getFramesPerBurst()*5);
  if(setBufferSizeResult) {
	LOGD("Set Buffer size to %d", setBufferSizeResult.value());
  }

  if(!mStream->usesAAudio()) {
	LOGW("Oboe stream doest use AAudio");
  }
}

/**
 * Close stream.
 * */
void AudioStream::close() {
  oboe::Result result = oboe::Result::OK;
  std::lock_guard<std::mutex> lock(mLock);
  if (mStream) {
    result = mStream->close();
    if(result != oboe::Result::OK) {
      // TODO Exception
	  return;
    }
	mStream.reset();
  }
}
/**
 * Start the stream.
 *
 * @param timeoutNanoseconds
 * */
void AudioStream::start(int64_t timeoutNanoseconds) {
  oboe::Result result = oboe::Result::OK;
  result = mStream->start(timeoutNanoseconds);
  if(result != oboe::Result::OK) {
	// TODO Exception
	return;
  }
}

/**
 * Pause the stream.
 *
 * @param timeoutNanoseconds
 * */
void AudioStream::pause(int64_t timeoutNanoseconds) {
  oboe::Result result = oboe::Result::OK;
  result = mStream->pause(timeoutNanoseconds);
  if(result != oboe::Result::OK) {
	// TODO Exception
	return;
  }
}

/**
 * Flush the stream.
 *
 * @param timeoutNanoseconds
 * */
void AudioStream::flush(int64_t timeoutNanoseconds) {
  oboe::Result result = oboe::Result::OK;
  result = mStream->flush(timeoutNanoseconds);
  if(result != oboe::Result::OK) {
	// TODO Exception
	return;
  }
}

/**
 * Stop the stream.
 *
 * @param timeoutNanoseconds
 * */
void AudioStream::stop(int64_t timeoutNanoseconds) {
  oboe::Result result = oboe::Result::OK;
  result = mStream->stop(timeoutNanoseconds);
  if(result != oboe::Result::OK) {
	// TODO Exception
	return;
  }
}

/**
 * Start the stream asynchronously. start with timeout at 0 is the as requestStart
 * */
void AudioStream::requestStart() {
  start(0);
}

/**
 * Pause the stream asynchronously.
 * */
void AudioStream::requestPause() {
  pause(0);
}

/**
 * Flush the stream asynchronously. start with timeout at 0 is the as requestStart
 * */
void AudioStream::requestFlush() {
  flush(0);
}

/**
 * Flush the stream asynchronously. start with timeout at 0 is the as requestStart
 * */
void AudioStream::requestStop() {
  stop(0);
}

/**
 * Set stream usage.
 *
 * @param usage
 * */
void AudioStream::setUsage(int usage) {
  mUsage = usage;
  close();
  open();
  // TODO Get old state and restore
}

/**
 * Set audio stream type.
 *
 * @param streamType
 * */
void AudioStream::setAudioStreamType(int streamType) {
  mStreamType = streamType;
  close();
  open();
  // TODO Get old state and restore
}

/**
 * Set the audio session id.
 *
 * @param id audio session id
 * */
void AudioStream::setAudioSessionId(int sessionId) {
  mSessionsID = sessionId;
  close();
  open();
  // TODO Get old state and restore
}

/**
 * Set both volumes for left and right.
 *
 * @param leftVolume left volume
 * @param rightVolume right volume
 * */
void AudioStream::setVolume(float leftVolume,float rightVolume) {
  mLeftVolume = leftVolume;
  mRightVolume = rightVolume;
}

/**
 * Get the audio session id.
 *
 * @return audio session id
 * */
int AudioStream::getAudioSessionId() {
  if(mStream) {
	return static_cast<int>(mStream->getSessionId());
  }
  return -1;
}

oboe::DataCallbackResult AudioStream::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {

  if (mIsThreadAffinityEnabled && !mIsThreadAffinitySet) {
	//setThreadAffinity();
	mIsThreadAffinitySet = true;
  }

  // TODO Get data from fifo
}

/**
 * Set the CPU IDs to bind the audio callback thread to
 *
 * @param mCpuIds - the CPU IDs to bind to
 */
void AudioStream::setCpuIds(std::vector<int> cpuIds) {
  mCpuIds = std::move(cpuIds);
}

/**
 * Set the thread affinity for the current thread to mCpuIds. This can be useful to call on the
 * audio thread to avoid underruns caused by CPU core migrations to slower CPU cores.
 */
void AudioStream::setThreadAffinity() {
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


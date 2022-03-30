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

#ifndef MEDIA_FFMPEG_DECODER
#define MEDIA_FFMPEG_DECODER

#include <jni.h>
#include <string>
#include <memory>
#include <stdint.h>

#include <thread>
#include <condition_variable>
#include <mutex>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
}

#include "fifo/FifoBuffer.h"
#include "callback/FFmpegCallback.h"
#include "utils.h"
#include "constants.h"

#define HW_ACCEL 0

class FFmpegDecoder {
 public:
  /**
   * Set data source from url and headers.
   * Must call before prepare().
   *
   * @param url file path
   * */
  void setDataSource(std::string &url, std::string &headers);
  /**
   * Set data source from file descriptor.
   * Must call before prepare().
   *
   * @param fd file descriptor
   * @param offset offset
   * @param length length
   * */
  void setDataSource(int fd, int64_t offset, int64_t length);

  /**
   * Prepare the decoder. Must call before everything else.
   * */
  void prepare();

  /**
   * Start/Resume the decoding.
   * */
  void start();

  /**
   * Stop the decoding.
   * */
  void stop();

  /**
   * Pause the decoding.
   * */
  void pause();

  /**
   * Get if the decoder is playing.
   * */
  bool isPlaying();

  /**
   * Seek in the file.
   *
   * @param time in milliseconds
   * */
  void seekTo(int msecs);

  /**
   * Get position in the file.
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
   * Release the decoder.
   * */
  void release();

  /**
   * Reset decoder, return to state before setDataSource and prepare.
   * */
  void reset();

  /**
   * Set the completion callback. Call the ending of file is reach.
   *
   * @param completionCallback pointer to the callback
   * */
  void setCompletionCallback(CompletionCallback *completionCallback) {
	mCompletionCallback = completionCallback;
  }

  /**
   * Call when error is detected.
   *
   * @param errorCallback pointer to the callback
   */
  void setErrorCallback(ErrorCallback *errorCallback) { mErrorCallback = errorCallback; }

  /**
   * Read framesToRead or, if not enough, then read as many as are available.
   *
   * @param destination
   * @param framesToRead number of frames requested
   * @return number of frames actually read
   */
  int32_t getFrame(void *destination, int32_t framesToRead) {
    return mFifoBuffer->read(destination, framesToRead);
  }

  void launchDecodeThread();

  uint32_t getFullFramesAvailable() {
    return mFifoBuffer->getFullFramesAvailable();
  }

  uint32_t getWriteFramesAvailable() {
	return mFifoBuffer->getBufferCapacityInFrames() - mFifoBuffer->getFullFramesAvailable();
  }

  void notify() {
    mCVPlaying.notify_one();
  }

 private:
  uint8_t mChannelCount = kChannelCount;
  int mSampleRate;
  std::atomic<int64_t > mCurrentPosition{};

  /**
   * Ffmpeg.
   * */
  AVFormatContext *mFmt_ctx = nullptr;  //FFmpeg context
  AVCodecContext *mCodec_ctx = nullptr; //Decoder context
  AVStream *mStream = nullptr;
  AVCodec *mCodec = nullptr;
  SwrContext *mSwr = nullptr;
  enum AVPixelFormat mHW_pix_fmt = AV_PIX_FMT_NONE;

  /**
   * Playing flag and buffer.
   * */
  bool mIsPlaying = false;
  std::unique_ptr<oboe::FifoBuffer> mFifoBuffer = std::make_unique<oboe::FifoBuffer>(kChannelCount * sizeof(float), kBufferSize);

  /**
   * Reset.
   * */
  bool mReset = false;
  bool mStopWaitReset = false;

  /*
   * Concurrency.
   * */
  std::condition_variable mCVPlaying;
  std::condition_variable mCVReset;
  std::mutex mMutex;

  /**
   * Input (Url, headers and file descriptor)
   * */
  std::string mUrl = "";
  std::string mHeaders = "";
  int64_t mOffset = 0;
  int64_t mLength = 0;

  /**
   * Callback.
   * */
  CompletionCallback *mCompletionCallback = nullptr;
  ErrorCallback *mErrorCallback = nullptr;

  /**
   * Private method.
   * */
  void decode();
#if HW_ACCEL
  enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                   const enum AVPixelFormat *pix_fmts);
#endif
};

#endif //MEDIAPLAYER_FFMPEG_DECODER

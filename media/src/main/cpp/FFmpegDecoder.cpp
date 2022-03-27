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

#include <string>
#include <jni.h>
#include <unistd.h>

extern "C" {
#include <libavutil/rational.h>
#include <libavutil/hwcontext.h>
#include <libavutil/buffer.h>
}

#include "FFmpegDecoder.h"
#include "jni/utils.h"
#include "jni/exception.h"

#if HW_ACCEL
/**
 * Callback for get_format
 *
 * @param ctx
 * @param pix_fmts
 * */
typedef enum AVPixelFormat
(*getFormatCallback)(AVCodecContext *ctx, const enum AVPixelFormat *pix_fmts);


/**
 * Get pointer from method.
 * */
template<typename M> inline void* getMethodPointer(M ptr){
  return *reinterpret_cast<void**>(&ptr);
}
#endif

/**
 * Throw an ffmpeg exception.
 * */
void throwFFmpegError(const std::string &what, int ret) {
  std::string err(av_err2str(ret));
  throw FFmpegError(what + err);
}

/**
 * Set data source from url and headers.
 * Must be called before prepare().
 *
 * @param url file path
 * @param headers
 * */
void FFmpegDecoder::setDataSource(std::string &url, std::string &headers) {

  mUrl = url;
  if(headers.length() > 0) {
	mHeaders = headers;
  }
}

/**
 * Set data source from file descriptor.
 * Must call before prepare().
 *
 * @param fd file descriptor
 * @param offset offset
 * @param length length
 * */
void FFmpegDecoder::setDataSource(int fd, int64_t offset, int64_t length) {

  // See https://github.com/wseemann/FFmpegMediaMetadataRetriever/
  // and https://gist.github.com/wseemann/b1694cbef5689ca2a4ded5064eb91750

  /**
   * Old file descriptor code.
   * */
  int dupFd = dup(fd);
  //mUrl = string_format("pipe:%d", dupFd);
  mOffset = offset;
  mLength = length;


  // Get real path from file descriptor
  char buffer[1024];
  std::string fd_path = string_format("/proc/self/fd/%d", fd);
  ssize_t len = readlink(fd_path.c_str(), buffer, sizeof(buffer)-1);

  if (len == -1) {
	mUrl = "";
	return;
  }

  buffer[len] = '\0';
  mUrl = std::string(buffer);
}


/**
 * Prepare the decoder. Must call before everything else.
 * */
void FFmpegDecoder::prepare() {

  // TODO Split this method
  // Keep in ming video too
  int ret = 0;

  AVDictionary *options = NULL;
  av_dict_set(&options, "icy", "1", 0);
  av_dict_set(&options, "user-agent",LIBRARY_NAME, 0);

  if(mHeaders.length() > 0) {
	av_dict_set(&options, "headers", mHeaders.c_str(), 0);
  }


  // Skip byte for file descriptor
  /*if(mOffset > 0) {
	mFmt_ctx = avformat_alloc_context();
	//mFmt_ctx->skip_initial_bytes = mOffset;
  }*/

  /**
   * Open input.
   * */
  ret = avformat_open_input(&mFmt_ctx, mUrl.c_str(), NULL, NULL);
  if (ret < 0) {
	LOGE("Could not open file:%s, %s", mUrl.c_str(), av_err2str(ret));
	// TODO Call release method in MediaPlayer (try/catch)
	if (mFmt_ctx == nullptr) {
	  throw FFmpegErrorAlloc("Could not alloc format context");
	} else {
	  throwFFmpegError("Could not open file", ret);
	}
  }

  /**
   * Find stream info.
   * */
  ret = avformat_find_stream_info(mFmt_ctx, nullptr);
  if (ret < 0) {
	LOGE("Does not found stream info");
	throwFFmpegError("Does not found stream info", ret);
  }

  /**
  * Get audio stream.
  * */
  int streamIndex = av_find_best_stream(mFmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);

  if (streamIndex < 0) {
	LOGE("Does not found best stream");
	throw FFmpegError("Does not found best stream");
  } else {
	mStream = mFmt_ctx->streams[streamIndex];
  }

  /**
  * Alloc codec context.
  * */
  mCodec_ctx = avcodec_alloc_context3(NULL);
  if (mFmt_ctx == nullptr) {
	throw FFmpegErrorAlloc("Could not alloc format context");
  }

  ret = avcodec_parameters_to_context(mCodec_ctx, mStream->codecpar);
  if (ret < 0) {
	LOGE("Could not open codec");
	throwFFmpegError("Could not open codec", ret);
  }

  mCodec = avcodec_find_decoder(mCodec_ctx->codec_id);

#if HW_ACCEL
  /**
   * Hardware acceleration.
   * */
  for (int i = 0;; i++) {
	const AVCodecHWConfig *config = avcodec_get_hw_config(mCodec, i);
	if (!config) {
	  LOGW("Decoder %s does not support device type %s.\n",
		   mCodec->name, av_hwdevice_get_type_name(AV_HWDEVICE_TYPE_MEDIACODEC));
	  break;
	}
	if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX &&
		config->device_type == AV_HWDEVICE_TYPE_MEDIACODEC) {
	  mHW_pix_fmt = config->pix_fmt;
	  break;
	}
  }

  // If an pixel format can decode with hardware acceleration
  if(mHW_pix_fmt != AV_PIX_FMT_NONE) {
	ret = av_hwdevice_ctx_create(&(mCodec_ctx->hw_device_ctx), AV_HWDEVICE_TYPE_MEDIACODEC, nullptr, nullptr,0);
	if(ret < 0) {
	  LOGW("Hardware accerlaration failed to init");
	}
  }

  mCodec_ctx->get_format = reinterpret_cast<getFormatCallback>(getMethodPointer(&FFmpegDecoder::get_hw_format));

#endif
  // Open the codec
  ret = avcodec_open2(mCodec_ctx, mCodec, nullptr);
  if (ret < 0) {
	LOGE("Could not open codec");
	throwFFmpegError("Could not open codec", ret);
  }

  /**
   * Alloc swr and setup.
   * */
  mSwr = swr_alloc();
  if (mSwr == nullptr) {
	throw FFmpegErrorAlloc("Could not alloc resample context");
  }

  int32_t outChannelLayout = (1 << mChannelCount) - 1;
  // TODO Use sample rate from ffmpeg and not hardcode
  mSampleRate = mCodec_ctx->sample_rate;

  av_opt_set_int(mSwr, "in_channel_count", mStream->codecpar->channels, 0);
  av_opt_set_int(mSwr, "out_channel_count", kChannelCount, 0);
  av_opt_set_int(mSwr, "in_channel_layout", mStream->codecpar->channel_layout, 0);
  av_opt_set_int(mSwr, "out_channel_layout", outChannelLayout, 0);
  av_opt_set_int(mSwr, "in_sample_rate", mStream->codecpar->sample_rate, 0);
  av_opt_set_int(mSwr, "out_sample_rate", kSampleRate, 0);
  av_opt_set_int(mSwr, "in_sample_fmt", mStream->codecpar->format, 0);
  av_opt_set_sample_fmt(mSwr, "out_sample_fmt", AV_SAMPLE_FMT_FLT  , 0);
  av_opt_set_int(mSwr, "force_resampling", 1, 0);

  ret = swr_init(mSwr);
  if (ret < 0) {
	LOGE("Error init SwrContext");
	throwFFmpegError("Error init SwrContext", ret);
  }

  if (!swr_is_initialized(mSwr)) {
	LOGE("swr_is_initialized is false\n");
	throw FFmpegError("swr_is_initialized is false");
  }

  launchDecodeThread();
}

/**
 * Start/Resume the decoding.
 * */
void FFmpegDecoder::start() {
  // Set play to true and notify the decoding thread
  mIsPlaying = true;
  mCVPlaying.notify_one();
}

/**
 * Stop the decoding.
 * */
void FFmpegDecoder::stop() {
  pause();
}

/**
 * Pause the decoding.
 * */
void FFmpegDecoder::pause() {
  mIsPlaying = false;
}

/**
 * Get if the decoder is playing.
 * */
bool FFmpegDecoder::isPlaying() {
  return mIsPlaying;
}

/**
 * Seek in the file.
 *
 * @param time in milliseconds
 * */
void FFmpegDecoder::seekTo(int msecs) {
  // TODO Mutex to wait the tread ?
  // FIXME Seek is to long
  pause();

  int64_t seek = av_rescale(
	  msecs/1000,
	  mStream->time_base.den,
	  mStream->time_base.num
  );

 // avcodec_flush_buffers(mCodec_ctx);
// AVSEEK_FLAG_ANY
// AVSEEK_FLAG_FRAME
// AVSEEK_FLAG_BACKWARD
 int ret = avformat_seek_file(mFmt_ctx,mStream->index,INT64_MIN,seek,INT64_MAX,AVSEEK_FLAG_FRAME);
  if( ret < 0 ) {
    LOGE("seekTo error:%s", av_err2str(ret));
  }

  // TODO Clear the fifo ?
  start();
}

/**
 * Get position in the file.
 *
 * @return position in the file
 * */
int FFmpegDecoder::getCurrentPosition() {
  if (mStream == nullptr) {
	return -1;
  }
  // Retrieve the current position in milliseconds
  int64_t currentPosition = mCurrentPosition.load(std::memory_order_acquire);
  if(currentPosition == AV_NOPTS_VALUE) {
	return -1;
  }

  currentPosition *= 1000;
  currentPosition = currentPosition * mStream->time_base.num / mStream->time_base.den;

  return currentPosition;
}

/**
 * Get the duration in milliseconds.
 *
 * @return duration of the file in milliseconds
 * */
int FFmpegDecoder::getDuration() {
  if (mStream == nullptr) {
	return -1;
  }
  // Stream duration is relative to stream time base
  int64_t duration = mStream->duration;
  duration *=1000;
  //duration = duration/AV_TIME_BASE;
  duration = duration * mStream->time_base.num / mStream->time_base.den;
  return duration;
}

/**
 * Release the decoder.
 * */
void FFmpegDecoder::release() {

  if (mCodec_ctx) {
	// av_buffer_unref((void *)&(mCodec_ctx->hwaccel_context));
	avcodec_free_context(&mCodec_ctx);
	mCodec_ctx = nullptr;
  }

  if (mSwr) {
	swr_free(&mSwr);
  }

  if (mFmt_ctx) {
	// NOTE: This also frees mStream
	avformat_free_context(mFmt_ctx);
	mFmt_ctx = nullptr;
  }

  mStream = nullptr;
  mCodec = nullptr;
}

/**
 * Reset decoder, return to state before setDataSource and prepare.
 * */
void FFmpegDecoder::reset() {

  start();
  mReset = true;
  // Wait the decode loop is finished
  std::unique_lock<std::mutex> lk(mMutex);
  mCVReset.wait(lk, [&] {return mStopWaitReset;});
  // TODO Remove data in fifo
  avcodec_flush_buffers(mCodec_ctx);


  // Reset data source
  mOffset = 0;
  mLength = 0;
  mUrl = "";
  mHeaders = "";

  // Release ffmpeg context
  release();

  mReset = false;
  lk.unlock();
}

void FFmpegDecoder::launchDecodeThread() {
  /*
  * Start thread.
  * */
  std::thread decodeThread(&FFmpegDecoder::decode, this);
  thread::setScheduling(decodeThread,SCHED_RR,50);
  thread::setName(decodeThread, "FFmpegDecoder");
  decodeThread.detach();
}

void FFmpegDecoder::decode() {
  LOGD("DECODE START");
  int result = 0;

  AVPacket *avPacket = av_packet_alloc(); // Stores compressed audio data
  AVFrame *decodedFrame = av_frame_alloc(); // Stores raw audio data
  AVFrame *tempFrame = av_frame_alloc();

  // While there is more data to read, read it into the avPacket
  while (true) {
	result = av_read_frame(mFmt_ctx, avPacket);
	if (result < 0) {
	  break;
	}

	// If reset started stop decoding loop
	if(mReset) {
	  break;
	}

	if (avPacket->stream_index == mStream->index && avPacket->size > 0) {

	  // Pass our compressed data into the codec
	  result = avcodec_send_packet(mCodec_ctx, avPacket);
	  if (result != 0) {
		LOGE("avcodec_send_packet error: %s", av_err2str(result));
		if (mErrorCallback != nullptr) {
		  mErrorCallback->onFFmpegError("avcodec_send_packet error", result);
		}
		break;
	  }

	  // Retrieve our raw data from the codec
	  result = avcodec_receive_frame(mCodec_ctx, decodedFrame);
	  if (result == AVERROR(EAGAIN)) {
		// The codec needs more data before it can decode
		LOGI("avcodec_receive_frame returned EAGAIN");
		av_packet_unref(avPacket);
		continue;
	  } else if (result != 0) {
		LOGE("avcodec_receive_frame error: %s", av_err2str(result));
		if (mErrorCallback != nullptr) {
		  mErrorCallback->onFFmpegError("avcodec_receive_frame error", result);
		}
		break;
	  }

#if HW_ACCEL
	  // Get frame from hardware acceleration
	  if (decodedFrame->format == mHW_pix_fmt) {
		/* retrieve data from GPU to CPU */
		if ((result = av_hwframe_transfer_data(decodedFrame, tempFrame, 0)) < 0) {
		  LOGE("Error transferring the data to system memory\n");
		  return;
		}
	  } else {
		tempFrame = decodedFrame;
	  }
#else
	  tempFrame = decodedFrame;
#endif


	  /**
	   * Resampling.
	   * */

	  auto dst_nb_samples = (int32_t) av_rescale_rnd(
		  swr_get_delay(mSwr, decodedFrame->sample_rate) + decodedFrame->nb_samples,
		  kSampleRate,
		  decodedFrame->sample_rate,
		  AV_ROUND_UP);

	  short *buffer1;
	  av_samples_alloc(
		  (uint8_t **) &buffer1,
		  nullptr,
		  kSampleRate,
		  dst_nb_samples,
		  AV_SAMPLE_FMT_FLT,
		  0);
	  int frame_count = swr_convert(
		  mSwr,
		  (uint8_t **) &buffer1,
		  dst_nb_samples,
		  (const uint8_t **) decodedFrame->data,
		  decodedFrame->nb_samples);



	  // Wait the play flag are true and there is enough place in fifo
	  std::unique_lock<std::mutex> lk(mMutex);
	  // TODO Implement clock
	  mCVPlaying.wait(lk, [&] { return mIsPlaying && (frame_count <= getWriteFramesAvailable()); });

	  // Store current pts
	  mCurrentPosition.store(decodedFrame->pts, std::memory_order_release);


	  mFifoBuffer->write(buffer1, frame_count); // Write array to the fifo buffer

	  av_freep(&buffer1);

	  av_packet_unref(avPacket);
	}
  }

  // End of file reached, call completion callback
  if (result == AVERROR_EOF && mCompletionCallback != nullptr && mReset == false) {
	mCompletionCallback->onCompletion();
  }


  av_frame_free(&decodedFrame);
  LOGD("DECODE END");


  // Notify reset method the decode loop is finished
  std::lock_guard<std::mutex> lk(mMutex);
  mStopWaitReset = true;
  mCVReset.notify_one();
}

#if HW_ACCEL
enum AVPixelFormat FFmpegDecoder::get_hw_format(AVCodecContext *ctx,
												const enum AVPixelFormat *pix_fmts) {
  const enum AVPixelFormat *p;

  for (p = pix_fmts; static_cast<int>(*p) != -1; p++) {
	if (*p == mHW_pix_fmt)
	  return *p;
  }

  LOGE("Failed to get HW surface format.\n");
  return AV_PIX_FMT_NONE;

}

#endif

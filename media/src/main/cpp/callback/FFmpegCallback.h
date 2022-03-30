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

#ifndef MEDIA_FFMPEG_CALLBACK
#define MEDIA_FFMPEG_CALLBACK

class CompletionCallback {
 public:
  virtual ~CompletionCallback() = default;
  /**
   * Callback when the End of file is reached.
   * */
  virtual void onCompletion(void) {}

};

class ErrorCallback {
 public:
  virtual ~ErrorCallback() = default;
  /**
   * Callback to player when ffmpeg produce and error.
   *
   * @param msg msg if there is one
   * @param code error code
   * */
  virtual void onFFmpegError(const char *msg,int code) {}
};

#endif //MEDIAPLAYER_FFMPEG_CALLBACK

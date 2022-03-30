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

#ifndef MEDIA_STREAM
#define MEDIA_STREAM

class Stream {
 public:
  /**
   * Open stream.
   * */
  virtual void open() {};

  /*
   * CLose stream.
   * */
  virtual void close() {};

  /**
   * Start the stream.
   * */
  virtual void start(int64_t timeoutNanoseconds = oboe::kDefaultTimeoutNanos) {};

  /**
   * Pause the stream.
   * */
  virtual void pause(int64_t timeoutNanoseconds = oboe::kDefaultTimeoutNanos) {};

  /**
   * Flush the stream.
   * */
  virtual void flush(int64_t timeoutNanoseconds = oboe::kDefaultTimeoutNanos) {};

  /**
   * Stop the stream.
   * */
  virtual void stop(int64_t timeoutNanoseconds = oboe::kDefaultTimeoutNanos) {};

  /**
   * Start the stream asynchronously.
   * */
  virtual void requestStart() {};

  /**
   * Pause the stream asynchronously.
   * */
  virtual void requestPause() {};

  /**
   * Flush the stream asynchronously.
   * */
  virtual void requestFlush() {};

  /**
   * Stop the stream asynchronously.
   * */
  virtual void requestStop() {};

};

#endif //MEDIA_STREAM

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

#ifndef MEDIAPLAYER_CONSTANTS
#define MEDIAPLAYER_CONSTANTS

#include <stdint.h>


#define LIBRARY_NAME "Media"

#define MEDIA_PACKAGE_PLAYER "com/github/a2va/media/MediaPlayer"

const int kSampleRate = 48000;
const uint8_t kChannelCount = 2;

// Buffer size in frame between decoder and oboe
const int kBufferSize = 16384;

#endif //MEDIAPLAYER_CONSTANTS

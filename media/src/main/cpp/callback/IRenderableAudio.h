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

#ifndef MEDIA_IRENDERABLEAUDIO
#define MEDIA_IRENDERABLEAUDIO

class IRenderableAudio {
 public:
  virtual ~IRenderableAudio() = default;
  virtual void renderAudio(float *audioData, int32_t numFrames) = 0;
};



#endif //MEDIAPLAYER_IRENDERABLEAUDIO

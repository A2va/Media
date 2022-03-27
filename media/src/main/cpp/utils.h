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

#ifndef ANDROID_UTILS_H
#define ANDROID_UTILS_H

#include <thread>
#include <string>
#include <string_view>
#include <stdarg.h>

class thread : public std::thread
{
 public:
  static void setName(std::thread &th, const char* name);
  static void setScheduling(std::thread &th, int policy, int priority);
};


std::string string_format(const std::string fmt_str, ...);

#endif //ANDROID_UTILS_H

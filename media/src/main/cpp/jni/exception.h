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

#ifndef MEDIAPLAYER_EXCEPTION
#define MEDIAPLAYER_EXCEPTION

#include <stdexcept>
#include <exception>
#include <string>
#include <jni.h>

/**
 * Exception when ffmpeg cannot allocate.
 * */
class FFmpegErrorAlloc: public std::runtime_error {
 public:
  FFmpegErrorAlloc(const std::string& what): std::runtime_error(what){}
  FFmpegErrorAlloc(const char* what): std::runtime_error(what){}
};

/**
 * Exception for general ffmpeg error.
 * */
class FFmpegError: public std::runtime_error {
 public:
  FFmpegError(const std::string& what): std::runtime_error(what){}
  FFmpegError(const char* what): std::runtime_error(what){}
};

//This is how we represent a Java exception already in progress
struct ThrownJavaException : std::runtime_error {
  ThrownJavaException() :std::runtime_error("") {}
  ThrownJavaException(const std::string& msg ) :std::runtime_error(msg) {}
};

struct NewJavaException : public ThrownJavaException{
  NewJavaException(JNIEnv * env, const char* type="", const char* message="");
};

#endif //MEDIAPLAYER_EXCEPTION

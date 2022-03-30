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

#ifndef MEDIA_JNI_UTILS
#define MEDIA_JNI_UTILS

#include <android/log.h>
#include <thread>
#include <jni.h>
#include <string>
#include <vector>

#include "constants.h"

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LIBRARY_NAME, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LIBRARY_NAME, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LIBRARY_NAME, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LIBRARY_NAME, __VA_ARGS__))
#define LOGV(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE, LIBRARY_NAME, __VA_ARGS__))

enum VersionCodes {
  BASE=1,
  BASE_1_1,
  CUPCAKE,
  CUR_DEVELOPMENT=10000,
  DONUT=4,
  ECLAIR,
  ECLAIR_0_1,
  ECLAIR_MR1,
  FROYO,
  GINGERBREAD,
  GINGERBREAD_MR1,
  HONEYCOMB,
  HONEYCOMB_MR1,
  HONEYCOMB_MR2,
  ICE_CREAM_SANDWICH,
  ICE_CREAM_SANDWICH_MR1,
  JELLY_BEAN,
  JELLY_BEAN_MR1,
  JELLY_BEAN_MR2,
  KITKAT,
  KITKAT_WATCH,
  LOLLIPOP,
  LOLLIPOP_MR1,
  M,
  N,
  N_MR1,
  O,
  O_MR1,
  P,
  Q,
  R,
  S

};

// Based on this idea:
// https://stackoverflow.com/questions/30026030/what-is-the-best-way-to-save-jnienv/30026231
class ScopedEnv {
 public:
  ScopedEnv();

  ScopedEnv(const ScopedEnv&) = delete;
  ScopedEnv& operator=(const ScopedEnv&) = delete;

  virtual ~ScopedEnv();

  /**
   * Get the env.
   *
   * @return jni env
   * */
  JNIEnv* const get();

 private:
  bool getJniEnv(JavaVM *vm, JNIEnv **env);

  bool mAttachedEnv = false;
  JNIEnv *mEnv = nullptr;
};



jobject getGlobalContext(JNIEnv *env);
int getSDKVersion();

std::string javaStringToString(JNIEnv *env, jstring jStr);
std::vector<int> javaArrayToVector(JNIEnv *env, jintArray intArray);

#endif //MEDIAPLAYER_JNI_UTILS

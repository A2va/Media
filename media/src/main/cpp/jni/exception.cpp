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

#include "exception.h"

NewJavaException::NewJavaException(JNIEnv *env, const char* type, const char* message)
:ThrownJavaException(type+std::string(" ")+message){


  jclass newExcCls = env->FindClass(type);
  if (newExcCls != NULL)
  env->ThrowNew(newExcCls, message);
  //if it is null, a NoClassDefFoundError was already thrown
}

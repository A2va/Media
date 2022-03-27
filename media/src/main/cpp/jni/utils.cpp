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

#include <cerrno>
#include <android/api-level.h>

#include "utils.h"

static JavaVM* jVM;

ScopedEnv::ScopedEnv() : mAttachedEnv(false) {
  mAttachedEnv = getJniEnv(jVM, &mEnv);
}

JNIEnv* const ScopedEnv::get() {
  return mEnv;
}

bool ScopedEnv::getJniEnv(JavaVM *vm, JNIEnv **env) {
  bool attachThreadOk = false;
  *env = nullptr;
  // Check if the current thread is attached to the VM
  jint res = vm->GetEnv((void**)env, JNI_VERSION_1_6);
  if (res == JNI_EDETACHED) {
    if (vm->AttachCurrentThread(env, NULL) == JNI_OK) {
        attachThreadOk = true;
    } else {
      // TODO Failed to attach thread. Throw an exception if you want to.
    }
  } else if (res == JNI_EVERSION) {
    // TODO Unsupported JNI version. Throw an exception if you want to.
  }
  return attachThreadOk;
}

ScopedEnv::~ScopedEnv() {
  if(mAttachedEnv) {
    jVM->DetachCurrentThread();
    mAttachedEnv = false;
  }
}

/**
 * Convert jintArray object to std vector.
 *
 * @param env
 * @param jStr
 * @return standard vector
 * */
std::vector<int> javaArrayToVector(JNIEnv *env, jintArray intArray) {
  std::vector<int> v;
  jsize length = env->GetArrayLength(intArray);
  if (length > 0) {
    jint *elements = env->GetIntArrayElements(intArray, nullptr);
    v.insert(v.end(), &elements[0], &elements[length]);
    // Unpin the memory for the array, or free the copy.
    env->ReleaseIntArrayElements(intArray, elements, 0);
  }
  return v;
}

/**
 * Convert jstring object to std string.
 *
 * @param env
 * @param jStr
 * @return standard string
 * */
std::string javaStringToString(JNIEnv *env, jstring jStr) {
  if (!jStr)
	return "";

  const jclass stringClass = env->GetObjectClass(jStr);
  const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
  const jbyteArray
	  stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));

  size_t length = (size_t) env->GetArrayLength(stringJbytes);
  jbyte *pBytes = env->GetByteArrayElements(stringJbytes, NULL);

  std::string ret = std::string((char *) pBytes, length);
  env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

  env->DeleteLocalRef(stringJbytes);
  env->DeleteLocalRef(stringClass);
  return ret;
}

/**
 * Get global context of application.
 *
 * @param env
 * @return context object
 * */
jobject getGlobalContext(JNIEnv *env) {
  //Get the instance object of Activity Thread
  jclass activityThread = env->FindClass("android/app/ActivityThread");
  jmethodID currentActivityThread = env->GetStaticMethodID(activityThread, "currentActivityThread", "()Landroid/app/ActivityThread;");
  jobject at = env->CallStaticObjectMethod(activityThread, currentActivityThread);
  //Get Application, which is the global Context
  jmethodID getApplication = env->GetMethodID(activityThread, "getApplication", "()Landroid/app/Application;");
  jobject context = env->CallObjectMethod(at, getApplication);
  return context;
}

/**
 * Returns the API level of the device we're actually running on, or -1 on failure.
 * The returned values correspond to the named constants in `<android/api-level.h>`,
 * and is equivalent to the Java `Build.VERSION.SDK_INT` API.
 *
 * See also android_get_application_target_sdk_version().
 */
int getSDKVersion() {
  return android_get_device_api_level();
}


void getPathfromFileDescriptor(jobject fileDescriptor) {


}

/**
 * Called by jni at loading.
 *
 * @param vm Java vm
 * @param reserved
 * */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  jVM = vm; // Save VM
  // TODO Test supported jni version
  return JNI_VERSION_1_6;
}

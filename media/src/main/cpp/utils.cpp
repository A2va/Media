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

#include <stdexcept>
#include <memory>
#include <jni.h>

#include "utils.h"
#include "jni/utils.h"


/**
 * Set a thread name.
 *
 * @param th thread to set the name on
 * @param name
 * */
void thread::setName(std::thread &th, const char* name) {
  if(pthread_setname_np(th.native_handle(), name)){
	LOGE("Unable to set thread name: %s", std::strerror(errno));
  }
}

/**
 * Set a thread scheduling.
 *
 * @param th thread to set scheduling on
 * @param policy see sched.h for all policy
 * @param priority
 * */
void thread::setScheduling(std::thread &th, int policy, int priority) {
  int max_priority = sched_get_priority_max(policy);
  int min_priority = sched_get_priority_min(policy);

  // If priority is too high
  if(priority > max_priority) {
	priority = max_priority;
  }
  // Or too low
  if(priority < min_priority) {
	priority = min_priority;
  }

  sched_param sch_params;
  sch_params.sched_priority = priority;
  if(pthread_setschedparam(th.native_handle(), policy, &sch_params)) {
	LOGE("Unable to set thread priority: %s", std::strerror(errno));
  }
}

/**
 * @param fmt_str format string
 * @param ... list of variable to format
 * */
std::string string_format(const std::string fmt_str, ...) {
  int final_n, n = ((int)fmt_str.size()) * 2; /* Reserve two times as much as the length of the fmt_str */
  std::unique_ptr<char[]> formatted;
  va_list ap;
  while(1) {
	formatted.reset(new char[n]); /* Wrap the plain char array into the unique_ptr */
	strcpy(&formatted[0], fmt_str.c_str());
	va_start(ap, fmt_str);
	final_n = vsnprintf(&formatted[0], n, fmt_str.c_str(), ap);
	va_end(ap);
	if (final_n < 0 || final_n >= n)
	  n += abs(final_n - n + 1);
	else
	  break;
  }
  return std::string(formatted.get());
}


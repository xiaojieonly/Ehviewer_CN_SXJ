/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//
// Created by Hippo on 10/19/2015.
//

#ifndef STREAM_INPUT_STREAM_PIPE_H
#define STREAM_INPUT_STREAM_PIPE_H

#include "config.h"
#if defined(STREAM_SUPPORT_INPUT) && defined(STREAM_SUPPORT_INPUT_PIPE)

#include <jni.h>

#include "input_stream.h"

typedef struct
{
  jobject isPipe;
  jmethodID obtainMID;
  jmethodID releaseMID;
  jmethodID openMID;
  jmethodID closeMID;
} InputStreamPipe;

InputStreamPipe* create_input_stream_pipe(JNIEnv* env, jobject isPipe);
void destroy_input_stream_pipe(JNIEnv* env, InputStreamPipe** inputStreamPipe);
void obtain_input_stream_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe);
void release_input_stream_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe);
InputStream* open_input_stream_from_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe);
void close_input_stream_from_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe);

#endif // STREAM_SUPPORT_INPUT && STREAM_SUPPORT_INPUT_PIPE

#endif // STREAM_INPUT_STREAM_PIPE_H

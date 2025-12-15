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

#ifndef STREAM_OUTPUT_STREAM_PIPE_H
#define STREAM_OUTPUT_STREAM_PIPE_H

#include "config.h"
#if defined(STREAM_SUPPORT_OUTPUT) && defined(STREAM_SUPPORT_OUTPUT_PIPE)

#include <jni.h>

#include "output_stream.h"

typedef struct
{
  jobject osPipe;
  jmethodID obtainMID;
  jmethodID releaseMID;
  jmethodID openMID;
  jmethodID closeMID;
} OutputStreamPipe;

OutputStreamPipe* create_output_stream_pipe(JNIEnv* env, jobject osPipe);
void destroy_output_stream_pipe(JNIEnv* env, OutputStreamPipe** outputStreamPipe);
void obtain_output_stream_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe);
void release_output_stream_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe);
OutputStream* open_output_stream_from_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe);
void close_output_stream_from_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe);

#endif // STREAM_SUPPORT_OUTPUT && STREAM_SUPPORT_OUTPUT_PIPE

#endif // STREAM_OUTPUT_STREAM_PIPE_H

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

#include "config.h"
#if defined(STREAM_SUPPORT_OUTPUT) && defined(STREAM_SUPPORT_OUTPUT_PIPE)

#include <stdlib.h>

#include "output_stream_pipe.h"
#include "../log.h"

OutputStreamPipe* create_output_stream_pipe(JNIEnv* env, jobject osPipe)
{
  jclass streamPipeCls = (*env)->GetObjectClass(env, osPipe);
  jmethodID obtainMID = (*env)->GetMethodID(env, streamPipeCls, "obtain", "()V");
  jmethodID releaseMID = (*env)->GetMethodID(env, streamPipeCls, "release", "()V");
  jmethodID openMID = (*env)->GetMethodID(env, streamPipeCls, "open", "()Ljava/io/OutputStream;");
  jmethodID closeMID = (*env)->GetMethodID(env, streamPipeCls, "close", "()V");

  if (obtainMID == NULL || releaseMID == NULL || openMID == NULL || closeMID == NULL) {
    LOGE(MSG("Can't get method id"));
    return NULL;
  }

  OutputStreamPipe* outputStreamPipe = (OutputStreamPipe*) malloc(sizeof(OutputStreamPipe));
  if (outputStreamPipe == NULL) {
    LOGE(MSG("Out of memory"));
    return NULL;
  }

  outputStreamPipe->osPipe = (*env)->NewGlobalRef(env, osPipe);
  outputStreamPipe->obtainMID = obtainMID;
  outputStreamPipe->releaseMID = releaseMID;
  outputStreamPipe->openMID = openMID;
  outputStreamPipe->closeMID = closeMID;

  return outputStreamPipe;
}

void destroy_output_stream_pipe(JNIEnv* env, OutputStreamPipe** outputStreamPipe)
{
  if (outputStreamPipe != NULL && *outputStreamPipe != NULL) {
    (*env)->DeleteGlobalRef(env, (*outputStreamPipe)->osPipe);
    free(*outputStreamPipe);
    *outputStreamPipe = NULL;
  }
}

void obtain_output_stream_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe)
{
  (*env)->CallVoidMethod(env, outputStreamPipe->osPipe, outputStreamPipe->obtainMID);
}

void release_output_stream_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe)
{
  (*env)->CallVoidMethod(env, outputStreamPipe->osPipe, outputStreamPipe->releaseMID);
}

OutputStream* open_output_stream_from_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe)
{
  jobject os = (*env)->CallObjectMethod(env, outputStreamPipe->osPipe, outputStreamPipe->openMID);
  if ((*env)->ExceptionCheck(env)) {
    LOGE(MSG("Catch exception"));
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
    return NULL;
  }

  return create_output_stream(env, os);
}

void close_output_stream_from_pipe(JNIEnv* env, OutputStreamPipe* outputStreamPipe)
{
  (*env)->CallVoidMethod(env, outputStreamPipe->osPipe, outputStreamPipe->closeMID);
}

#endif // STREAM_SUPPORT_OUTPUT && STREAM_SUPPORT_OUTPUT_PIPE

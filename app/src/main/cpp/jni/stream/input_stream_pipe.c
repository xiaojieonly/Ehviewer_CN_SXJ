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
#if defined(STREAM_SUPPORT_INPUT) && defined(STREAM_SUPPORT_INPUT_PIPE)

#include <stdlib.h>

#include "input_stream_pipe.h"
#include "../log.h"

InputStreamPipe* create_input_stream_pipe(JNIEnv* env, jobject isPipe)
{
  jclass streamPipeCls = (*env)->GetObjectClass(env, isPipe);
  jmethodID obtainMID = (*env)->GetMethodID(env, streamPipeCls, "obtain", "()V");
  jmethodID releaseMID = (*env)->GetMethodID(env, streamPipeCls, "release", "()V");
  jmethodID openMID = (*env)->GetMethodID(env, streamPipeCls, "open", "()Ljava/io/InputStream;");
  jmethodID closeMID = (*env)->GetMethodID(env, streamPipeCls, "close", "()V");

  if (obtainMID == NULL || releaseMID == NULL || openMID == NULL || closeMID == NULL) {
    LOGE(MSG("Can't get method id"));
    return NULL;
  }

  InputStreamPipe* inputStreamPipe = (InputStreamPipe*) malloc(sizeof(InputStreamPipe));
  if (inputStreamPipe == NULL) {
    LOGE(MSG("Out of memory"));
    return NULL;
  }

  inputStreamPipe->isPipe = (*env)->NewGlobalRef(env, isPipe);
  inputStreamPipe->obtainMID = obtainMID;
  inputStreamPipe->releaseMID = releaseMID;
  inputStreamPipe->openMID = openMID;
  inputStreamPipe->closeMID = closeMID;

  return inputStreamPipe;
}

void destroy_input_stream_pipe(JNIEnv* env, InputStreamPipe** inputStreamPipe)
{
  if (inputStreamPipe != NULL && *inputStreamPipe != NULL) {
    (*env)->DeleteGlobalRef(env, (*inputStreamPipe)->isPipe);
    free(*inputStreamPipe);
    *inputStreamPipe = NULL;
  }
}

void obtain_input_stream_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe)
{
  (*env)->CallVoidMethod(env, inputStreamPipe->isPipe, inputStreamPipe->obtainMID);
}

void release_input_stream_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe)
{
  (*env)->CallVoidMethod(env, inputStreamPipe->isPipe, inputStreamPipe->releaseMID);
}

InputStream* open_input_stream_from_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe)
{
  jobject is = (*env)->CallObjectMethod(env, inputStreamPipe->isPipe, inputStreamPipe->openMID);
  if ((*env)->ExceptionCheck(env)) {
    LOGE(MSG("Catch exception"));
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
    return NULL;
  }

  return create_input_stream(env, is);
}

void close_input_stream_from_pipe(JNIEnv* env, InputStreamPipe* inputStreamPipe)
{
  (*env)->CallVoidMethod(env, inputStreamPipe->isPipe, inputStreamPipe->closeMID);
}

#endif // STREAM_SUPPORT_INPUT && STREAM_SUPPORT_INPUT_PIPE

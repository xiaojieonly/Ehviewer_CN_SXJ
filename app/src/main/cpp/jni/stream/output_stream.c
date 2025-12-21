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
#ifdef STREAM_SUPPORT_OUTPUT

#include <stdlib.h>

#include "../log.h"
#include "../utils.h"
#include "output_stream.h"
#include "stream_utils.h"

OutputStream* create_output_stream(JNIEnv* env, jobject os)
{
  jclass streamCls = (*env)->GetObjectClass(env, os);
  jmethodID writeMID = (*env)->GetMethodID(env, streamCls, "write", "([BII)V");
  jmethodID closeMID = (*env)->GetMethodID(env, streamCls, "close", "()V");
  jbyteArray buffer;

  if (writeMID == NULL || closeMID == NULL) {
    LOGE(MSG("Can't get write or close method id"));
    return NULL;
  }

  buffer = (*env)->NewByteArray(env, BUFFER_SIZE);
  buffer = (*env)->NewGlobalRef(env, buffer);
  if (buffer == NULL) {
    LOGE(MSG("Can't create buffer"));
    return NULL;
  }

  OutputStream* outputStream = (OutputStream*) malloc(sizeof(OutputStream));
  if (outputStream == NULL) {
    LOGE(MSG("Out of memory"));
    return NULL;
  }

  outputStream->os = (*env)->NewGlobalRef(env, os);
  outputStream->writeMID = writeMID;
  outputStream->closeMID = closeMID;
  outputStream->buffer = buffer;

  return outputStream;
}

void destroy_output_stream(JNIEnv* env, OutputStream** outputStream)
{
  if (outputStream != NULL && *outputStream != NULL) {
    (*env)->DeleteGlobalRef(env, (*outputStream)->os);
    (*env)->DeleteGlobalRef(env, (*outputStream)->buffer);
    free(*outputStream);
    *outputStream = NULL;
  }
}

size_t write_output_stream(JNIEnv* env, OutputStream* outputStream, const unsigned char* buffer, int offset, size_t size)
{
  size_t remainSize = size;
  size_t readSize = 0;
  int bufferOffset = offset;
  int len;

  while (remainSize > 0) {
    len = MIN(BUFFER_SIZE, remainSize);

    // Copy date from buffer to outputStream->buffer
    (*env)->SetByteArrayRegion(env, outputStream->buffer, 0, len, (jbyte *) (buffer + bufferOffset));

    // Invoke OutputStream.write
    (*env)->CallVoidMethod(env, outputStream->os, outputStream->writeMID, outputStream->buffer, 0, len);

    // Catch exception
    if ((*env)->ExceptionCheck(env)) {
      LOGE(MSG("Catch exception"));
      (*env)->ExceptionDescribe(env);
      (*env)->ExceptionClear(env);
      break;
    }

    remainSize -= len;
    readSize += len;
    bufferOffset += len;
  }

  return readSize;
}

void close_output_stream(JNIEnv* env, OutputStream* outputStream)
{
  (*env)->CallVoidMethod(env, outputStream->os, outputStream->closeMID);
  if ((*env)->ExceptionCheck(env)) {
    LOGE(MSG("Catch exception"));
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
  }
}

#endif // STREAM_SUPPORT_OUTPUT

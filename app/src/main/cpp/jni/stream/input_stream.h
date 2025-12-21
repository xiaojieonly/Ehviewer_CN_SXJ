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

#ifndef STREAM_INPUT_STREAM_H
#define STREAM_INPUT_STREAM_H

#include "config.h"
#ifdef STREAM_SUPPORT_INPUT

#include <jni.h>
#include <stddef.h>

typedef struct
{
  jobject is;
  jmethodID readMID;
  jmethodID closeMID;
  jbyteArray buffer;
} InputStream;

InputStream* create_input_stream(JNIEnv* env, jobject is);
void destroy_input_stream(JNIEnv* env, InputStream** inputStream);
size_t read_input_stream(JNIEnv* env, InputStream* inputStream, unsigned char* buffer, int offset, size_t size);
void close_input_stream(JNIEnv* env, InputStream* inputStream);

#endif // STREAM_SUPPORT_INPUT

#endif // STREAM_INPUT_STREAM_H

/*
 * Copyright 2016 Hippo Seven
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
// Created by Hippo on 1/1/2016.
//

#ifndef STREAM_PATCH_HEAD_INPUT_STREAM_H
#define STREAM_PATCH_HEAD_INPUT_STREAM_H

#include "config.h"
#if defined(STREAM_SUPPORT_INPUT) && defined(STREAM_SUPPORT_INPUT_PATCH_HEAD)

#include "input_stream.h"

typedef struct
{
  InputStream* input_stream;
  unsigned char* patch;
  unsigned int patch_length;
  unsigned int read_count;
} PatchHeadInputStream;

PatchHeadInputStream* create_patch_head_input_stream(
    InputStream* input_stream, const unsigned char* patch, unsigned int patch_length);
void destroy_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream** patch_head_input_stream);
size_t read_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream,
    unsigned char* buffer, int offset, size_t size);
void* read_patch_head_input_stream_all(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, size_t* length);
void close_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream);

#endif // STREAM_SUPPORT_INPUT && STREAM_SUPPORT_INPUT_PATCH_HEAD

#endif // STREAM_PATCH_HEAD_INPUT_STREAM_H

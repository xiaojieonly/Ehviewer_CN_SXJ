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

#include "config.h"
#if defined(STREAM_SUPPORT_INPUT) && defined(STREAM_SUPPORT_INPUT_PATCH_HEAD)

#include <stdlib.h>
#include <string.h>

#include "patch_head_input_stream.h"
#include "../log.h"
#include "../utils.h"

#define READ_BUFFER_SIZE (256 * 1024) // 256KB

PatchHeadInputStream* create_patch_head_input_stream(
    InputStream* input_stream, const unsigned char* patch, unsigned int patch_length)
{
  unsigned char* patch_copy;

  PatchHeadInputStream* patch_head_input_stream = (PatchHeadInputStream*) malloc(sizeof(PatchHeadInputStream));
  if (patch_head_input_stream == NULL) {
    WTF_OM;
    return NULL;
  }

  // Copy patch
  patch_copy = (unsigned char*) malloc(patch_length * sizeof(char));
  if (patch_copy == NULL) {
    WTF_OM;
    free(patch_head_input_stream);
    return NULL;
  }
  memcpy(patch_copy, patch, patch_length);

  patch_head_input_stream->input_stream = input_stream;
  patch_head_input_stream->patch = patch_copy;
  patch_head_input_stream->patch_length = patch_length;
  patch_head_input_stream->read_count = 0;

  return patch_head_input_stream;
}

size_t read_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream,
    unsigned char* buffer, int offset, size_t size)
{
  size_t len = MIN(size, patch_head_input_stream->patch_length - patch_head_input_stream->read_count);
  int buffer_offset = offset;

  if (len > 0) {
    memcpy(buffer + buffer_offset, patch_head_input_stream->patch + patch_head_input_stream->read_count, len);
    patch_head_input_stream->read_count += len;
    buffer_offset += len;
  }

  if (size > len) {
    len += read_input_stream(env, patch_head_input_stream->input_stream, buffer, buffer_offset, size - len);
  }

  return len;
}

void* read_patch_head_input_stream_all(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, size_t* length)
{
  size_t len = 0;
  size_t read;
  void* buffer = NULL;

  buffer = malloc(READ_BUFFER_SIZE);
  if (buffer == NULL) {
    WTF_OM;
    return NULL;
  }

  for (;;) {
    // Read from stream
    read = read_patch_head_input_stream(env, patch_head_input_stream, buffer, len, READ_BUFFER_SIZE);
    len += read;
    // Check stream end
    if (read < READ_BUFFER_SIZE) {
      // Get the end, shrink the buffer
      buffer = realloc(buffer, len);
      *length = len;
      return buffer;
    }
    // Extent buffer
    buffer = realloc(buffer, len + READ_BUFFER_SIZE);
    if (buffer == NULL) {
      WTF_OM;
      return NULL;
    }
  }
}

void destroy_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream** patch_head_input_stream)
{
  if (patch_head_input_stream != NULL && *patch_head_input_stream != NULL) {
    // Free input stream
    destroy_input_stream(env, &(*patch_head_input_stream)->input_stream);
    // Free patch
    free((*patch_head_input_stream)->patch);
    (*patch_head_input_stream)->patch = NULL;
    // Free itself
    free(*patch_head_input_stream);
    *patch_head_input_stream = NULL;
  }
}

void close_patch_head_input_stream(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream)
{
  close_input_stream(env, patch_head_input_stream->input_stream);
}

#endif // STREAM_SUPPORT_INPUT && STREAM_SUPPORT_INPUT_PATCH_HEAD

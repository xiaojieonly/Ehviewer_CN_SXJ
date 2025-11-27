/*
 * Copyright 2025 Hippo
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

#ifndef IMAGE_IMAGE_WEBP_H
#define IMAGE_IMAGE_WEBP_H

#include "config.h"
#ifdef IMAGE_SUPPORT_WEBP

#include <stdbool.h>

#include "patch_head_input_stream.h"
#include "../utils.h"
#include "webp/decode.h"

#define IMAGE_WEBP_DECODER_DESCRIPTION \
  ("libwebp " MAKESTRING(STRINGIZE, WEBP_DECODER_ABI_VERSION))

#define IMAGE_WEBP_MAGIC_NUMBER_0 0x52
#define IMAGE_WEBP_MAGIC_NUMBER_1 0x49
#define IMAGE_WEBP_MAGIC_NUMBER_00 0x57
#define IMAGE_WEBP_MAGIC_NUMBER_11 0x45

typedef struct {
  unsigned int width;
  unsigned int height;
  bool animated;
  bool is_opaque;
  unsigned int frame_count;
  unsigned int current_frame;
  unsigned char* buffer;
  unsigned char** frames;
  int* delays;
} WEBP;

void* WEBP_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially);
bool WEBP_complete(JNIEnv* env, WEBP* webp);
bool WEBP_is_completed(WEBP* webp);
void* WEBP_get_pixels(WEBP* webp);
int WEBP_get_width(WEBP* webp);
int WEBP_get_height(WEBP* webp);
int WEBP_get_byte_count(WEBP* webp);
void WEBP_render(WEBP* webp, int src_x, int src_y,
                 void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
                 int width, int height, bool fill_blank, int default_color);
void WEBP_advance(WEBP* webp);
int WEBP_get_delay(WEBP* webp);
int WEBP_get_frame_count(WEBP* webp);
bool WEBP_is_opaque(WEBP* webp);
void WEBP_recycle(JNIEnv* env, WEBP* webp);

#endif // IMAGE_SUPPORT_WEBP

#endif // IMAGE_IMAGE_WEBP_H


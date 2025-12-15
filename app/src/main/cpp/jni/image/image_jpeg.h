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
// Created by Hippo on 1/3/2016.
//

#ifndef IMAGE_IMAGE_JPEG_H
#define IMAGE_IMAGE_JPEG_H

#include "config.h"
#ifdef IMAGE_SUPPORT_JPEG

#include <stdio.h>
#include <stdbool.h>

#include "jpeglib.h"
#include "patch_head_input_stream.h"
#include "../utils.h"

#define IMAGE_JPEG_DECODER_DESCRIPTION ("libjpeg-turbo " MAKESTRING(STRINGIZE, LIBJPEG_TURBO_VERSION))

#define IMAGE_JPEG_MAGIC_NUMBER_0 0xFF
#define IMAGE_JPEG_MAGIC_NUMBER_1 0xD8

typedef struct
{
  unsigned int width;
  unsigned int height;
  void* buffer;
} JPEG;

void* JPEG_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially);
bool JPEG_complete(JPEG* jpeg);
bool JPEG_is_completed(JPEG* jpeg);
void* JPEG_get_pixels(JPEG* jpeg);
int JPEG_get_width(JPEG* jpeg);
int JPEG_get_height(JPEG* jpeg);
int JPEG_get_byte_count(JPEG* jpeg);
void JPEG_render(JPEG* jpeg, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);
void JPEG_advance(JPEG* jpeg);
int JPEG_get_delay(JPEG* jpeg);
int JPEG_get_frame_count(JPEG* jpeg);
bool JPEG_is_opaque(JPEG* jpeg);
void JPEG_recycle(JPEG* jpeg);

#endif // IMAGE_SUPPORT_JPEG

#endif // IMAGE_IMAGE_JPEG_H

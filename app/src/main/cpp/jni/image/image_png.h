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
// Created by Hippo on 12/27/2015.
//

#ifndef IMAGE_IMAGE_PNG_H
#define IMAGE_IMAGE_PNG_H

#include "config.h"
#ifdef IMAGE_SUPPORT_PNG

#include <stdbool.h>

#include "png.h"
#include "patch_head_input_stream.h"

#define IMAGE_PNG_DECODER_DESCRIPTION ("libpng " PNG_LIBPNG_VER_STRING)

#define IMAGE_PNG_MAGIC_NUMBER_0 0x89
#define IMAGE_PNG_MAGIC_NUMBER_1 0x50

#define IMAGE_PNG_PREPARE_UNKNOWN 0x00
#define IMAGE_PNG_PREPARE_NONE 0x01
#define IMAGE_PNG_PREPARE_BACKGROUND 0x02
#define IMAGE_PNG_PREPARE_USE_BACKUP 0x03

typedef struct {
  unsigned char* buffer;
  unsigned int width;
  unsigned int height;
  unsigned int offset_x;
  unsigned int offset_y;
  unsigned int delay; // ms
  unsigned char dop;
  unsigned char bop;
  unsigned char pop;
} PNG_FRAME_INFO;

typedef struct
{
  unsigned int width;
  unsigned int height;
  bool is_opaque;
  unsigned char* buffer;
  bool apng;
  int buffer_index;
  PNG_FRAME_INFO* frame_info_array;
  unsigned int frame_count;
  unsigned char* backup;
  bool partially;
  png_structp png_ptr;
  png_infop info_ptr;
  PatchHeadInputStream* patch_head_input_stream;
} PNG;

void* PNG_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially);
bool PNG_complete(JNIEnv* env, PNG* png);
bool PNG_is_completed(PNG* png);
void* PNG_get_pixels(PNG* png);
int PNG_get_width(PNG* png);
int PNG_get_height(PNG* png);
int PNG_get_byte_count(PNG* png);
void PNG_render(PNG* png, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);
void PNG_advance(PNG* png);
int PNG_get_delay(PNG* png);
int PNG_get_frame_count(PNG* png);
bool PNG_is_opaque(PNG* png);
void PNG_recycle(JNIEnv* env, PNG* png);

#endif // IMAGE_SUPPORT_PNG

#endif // IMAGE_IMAGE_PNG_H

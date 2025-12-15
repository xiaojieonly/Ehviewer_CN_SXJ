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

#ifndef IMAGE_IMAGE_GIF_H
#define IMAGE_IMAGE_GIF_H

#include "config.h"
#ifdef IMAGE_SUPPORT_GIF

#include <stdbool.h>

#include "patch_head_input_stream.h"
#include "gif_lib.h"
#include "../utils.h"

#define IMAGE_GIF_DECODER_DESCRIPTION ("giflib " MAKESTRING(STRINGIZE, GIFLIB_MAJOR) "." \
                                       MAKESTRING(STRINGIZE, GIFLIB_MINOR) "." \
                                       MAKESTRING(STRINGIZE, GIFLIB_RELEASE))

#define IMAGE_GIF_MAGIC_NUMBER_0 0x47
#define IMAGE_GIF_MAGIC_NUMBER_1 0x49

#define IMAGE_GIF_PREPARE_UNKNOWN 0x00
#define IMAGE_GIF_PREPARE_NONE 0x01
#define IMAGE_GIF_PREPARE_BACKGROUND 0x02
#define IMAGE_GIF_PREPARE_USE_BACKUP 0x03

typedef struct
{
  int tran;
  int disposal;
  int delay;
  int prepare;
} GIF_FRAME_INFO;

typedef struct
{
  GifFileType* gif_file;
  GIF_FRAME_INFO* frame_info_array;
  void* buffer;
  int buffer_index;
  void* backup;
  bool partially;
  // Use extra buffer to avoid blink
  void* shown_buffer;
  PatchHeadInputStream* patch_head_input_stream;
} GIF;

void* GIF_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially);
bool GIF_complete(JNIEnv* env, GIF* gif);
bool GIF_is_completed(GIF* gif);
void* GIF_get_pixels(GIF* gif);
int GIF_get_width(GIF* gif);
int GIF_get_height(GIF* gif);
int GIF_get_byte_count(GIF* gif);
void GIF_render(GIF* gif, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);
void GIF_advance(GIF* gif);
int GIF_get_delay(GIF* gif);
int GIF_get_frame_count(GIF* gif);
bool GIF_is_opaque(GIF* gif);
void GIF_recycle(JNIEnv* env, GIF* gif);

#endif // IMAGE_SUPPORT_GIF

#endif // IMAGE_IMAGE_GIF_H

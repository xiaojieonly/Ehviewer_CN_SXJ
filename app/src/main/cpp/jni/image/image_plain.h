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
// Created by Hippo on 5/3/2016.
//

#ifndef IMAGE_IMAGE_PLAIN_H
#define IMAGE_IMAGE_PLAIN_H

#include "config.h"
#ifdef IMAGE_SUPPORT_PLAIN

#include <stdbool.h>

#include "patch_head_input_stream.h"

typedef struct
{
  unsigned int width;
  unsigned int height;
  void* buffer;
} PLAIN;

void* PLAIN_create(unsigned int width, unsigned int height, const void* data);
bool PLAIN_complete(PLAIN* plain);
bool PLAIN_is_completed(PLAIN* plain);
void* PLAIN_get_pixels(PLAIN* plain);
int PLAIN_get_width(PLAIN* plain);
int PLAIN_get_height(PLAIN* plain);
int PLAIN_get_byte_count(PLAIN* plain);
void PLAIN_render(PLAIN* plain, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);
void PLAIN_advance(PLAIN* plain);
int PLAIN_get_delay(PLAIN* plain);
int PLAIN_get_frame_count(PLAIN* plain);
bool PLAIN_is_opaque(PLAIN* plain);
void PLAIN_recycle(PLAIN* plain);

#endif // IMAGE_SUPPORT_PLAIN

#endif // IMAGE_IMAGE_PLAIN_H

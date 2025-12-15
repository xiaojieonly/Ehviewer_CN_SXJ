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

#include "config.h"
#ifdef IMAGE_SUPPORT_PLAIN

#include <stdlib.h>
#include <string.h>

#include "image_plain.h"
#include "image_utils.h"
#include "../log.h"

void* PLAIN_create(unsigned int width, unsigned int height, const void* data)
{
  PLAIN* plain = NULL;
  void* buffer = NULL;
  size_t length;

  plain = (PLAIN*) malloc(sizeof(PLAIN));
  if (plain == NULL) {
    WTF_OM;
    return NULL;
  }

  length = (size_t) (width * height * 4);

  buffer = malloc(length);
  if (buffer == NULL) {
    WTF_OM;
    free(plain);
    return NULL;
  }

  memcpy(buffer, data, length);

  plain->width = width;
  plain->height = height;
  plain->buffer = buffer;

  return plain;
}

bool PLAIN_complete(PLAIN* plain)
{
  return true;
}

bool PLAIN_is_completed(PLAIN* plain)
{
  return true;
}

void* PLAIN_get_pixels(PLAIN* plain)
{
  return plain->buffer;
}

int PLAIN_get_width(PLAIN* plain)
{
  return plain->width;
}

int PLAIN_get_height(PLAIN* plain)
{
  return plain->height;
}

int PLAIN_get_byte_count(PLAIN* plain)
{
  return plain->width * plain->height * 4;
}

void PLAIN_render(PLAIN* plain, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color)
{
  copy_pixels(plain->buffer, plain->width, plain->height, src_x, src_y,
      dst, dst_w, dst_h, dst_x, dst_y,
      width, height, fill_blank, default_color);
}

void PLAIN_advance(PLAIN* plain)
{

}

int PLAIN_get_delay(PLAIN* plain)
{
  return 0;
}

int PLAIN_get_frame_count(PLAIN* plain)
{
  return 1;
}

bool PLAIN_is_opaque(PLAIN* plain)
{
  // TODO Check plain all alpha
  return false;
}

void PLAIN_recycle(PLAIN* plain)
{
  free(plain->buffer);
  plain->buffer = NULL;

  free(plain);
}

#endif // IMAGE_SUPPORT_PLAIN

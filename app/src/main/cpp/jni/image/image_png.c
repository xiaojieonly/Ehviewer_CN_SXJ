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

#include "config.h"
#ifdef IMAGE_SUPPORT_PNG

#include <stdlib.h>
#include <string.h>

#include "../log.h"
#include "../utils.h"
#include "image_png.h"
#include "image_utils.h"
#include "java_wrapper.h"

static void user_read_fn(png_structp png_ptr,
    png_bytep data, png_size_t length)
{
  bool attach;
  PatchHeadInputStream* patch_head_input_stream = png_get_io_ptr(png_ptr);
  JNIEnv *env = obtain_env(&attach);

  if (env == NULL) {
    LOGE(MSG("Can't get JNIEnv"));
  }

  read_patch_head_input_stream(env, patch_head_input_stream, data, 0, length);

  if (attach) {
    release_env();
  }
}

static void user_error_fn(png_structp png_ptr,
    png_const_charp error_msg)
{
  LOGE(MSG("%s"), error_msg);
}

static void user_warn_fn(png_structp png_ptr,
    png_const_charp error_msg)
{
  LOGW(MSG("%s"), error_msg);
}

static void free_frame_info_array(PNG_FRAME_INFO* frame_info_array, unsigned int count)
{
  int i;

  if (frame_info_array == NULL) {
    return;
  }

  for (i = 0; i < count; i++) {
    PNG_FRAME_INFO* frame_info = frame_info_array + i;
    free(frame_info->buffer);
    frame_info->buffer = NULL;
  }
  free(frame_info_array);
}

static bool read_image(png_structp png_ptr, unsigned char* buffer, unsigned int width, unsigned int height)
{
  int i;
  unsigned char** line_buffer_ptr_array = (unsigned char**) malloc(height * sizeof(unsigned char*));
  if (line_buffer_ptr_array == NULL) {
    WTF_OM;
    return false;
  }
  for (i = 0; i < height; i++) {
    line_buffer_ptr_array[i] = buffer + (width * i * 4);
  }
  png_read_image(png_ptr, line_buffer_ptr_array);
  free(line_buffer_ptr_array);
  return true;
}

static void read_frame(png_structp png_ptr, png_infop info_ptr, PNG_FRAME_INFO* frame_info)
{
  unsigned int width;
  unsigned int height;
  unsigned int offset_x;
  unsigned int offset_y;
  unsigned short delay_num;
  unsigned short delay_den;
  unsigned char dop;
  unsigned char bop;
  unsigned int delay;
  unsigned char* buffer = NULL;

  png_read_frame_head(png_ptr, info_ptr);
  png_get_next_frame_fcTL(png_ptr, info_ptr, &width, &height, &offset_x, &offset_y, &delay_num, &delay_den, &dop, &bop);

  delay = 1000u * delay_num / delay_den;

  frame_info->width = width;
  frame_info->height = height;
  frame_info->offset_x = offset_x;
  frame_info->offset_y = offset_y;
  frame_info->delay = delay;
  frame_info->dop = dop;
  frame_info->bop = bop;
  frame_info->pop = IMAGE_PNG_PREPARE_UNKNOWN;

  buffer = (unsigned char*) malloc(width * height * 4);
  if (buffer == NULL) {
    frame_info->buffer = NULL;
    return;
  }

  read_image(png_ptr, buffer, width, height);
  frame_info->buffer = buffer;
}

static void generate_pop(PNG_FRAME_INFO* frame_info_array, int count)
{
  unsigned char dop = 0xff;
  int i;

  for (i = 0; i < count; i++){
    PNG_FRAME_INFO* frame_info = frame_info_array + i;

    switch (dop) {
      case PNG_DISPOSE_OP_NONE:
        frame_info->pop = IMAGE_PNG_PREPARE_NONE;
        break;
      case PNG_DISPOSE_OP_BACKGROUND:
        frame_info->pop = IMAGE_PNG_PREPARE_BACKGROUND;
        break;
      case PNG_DISPOSE_OP_PREVIOUS:
        frame_info->pop = IMAGE_PNG_PREPARE_USE_BACKUP;
        break;
      default:
        frame_info->pop = IMAGE_PNG_PREPARE_BACKGROUND;
        break;
    }

    dop = frame_info->dop;
  }
}

void* PNG_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially)
{
  PNG *png = NULL;
  png_structp png_ptr = NULL;
  png_infop info_ptr = NULL;
  bool apng;
  unsigned int width;
  unsigned int height;
  int color_type;
  int bit_depth;
  bool is_opaque;
  unsigned char* buffer = NULL;
  unsigned int frame_count = 0;
  bool hide_first_frame = false;
  PNG_FRAME_INFO* frame_info_array = NULL;
  int i;

  png = (PNG *) malloc(sizeof(PNG));
  if (png == NULL) {
    WTF_OM;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, &user_error_fn, &user_warn_fn);
  if (png_ptr == NULL) {
    free(png);
    png = NULL;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  info_ptr = png_create_info_struct(png_ptr);
  if (info_ptr == NULL) {
    png_destroy_read_struct(&png_ptr, NULL, NULL);
    free(png);
    png = NULL;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  if (setjmp(png_jmpbuf(png_ptr))) {
    LOGE(MSG("Error in png decode"));
    free_frame_info_array(frame_info_array, frame_count);
    frame_info_array = NULL;
    free(buffer);
    buffer = NULL;
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
    free(png);
    png = NULL;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  // Set custom read function
  png_set_read_fn(png_ptr, patch_head_input_stream, &user_read_fn);

  // Get png info
  png_read_info(png_ptr, info_ptr);

  // Check apng
  if (png_get_valid(png_ptr, info_ptr, PNG_INFO_acTL)) {
    apng = true;
  } else {
    apng = false;
  }

  // PNG info
  width = png_get_image_width(png_ptr, info_ptr);
  height = png_get_image_height(png_ptr, info_ptr);
  color_type = png_get_color_type(png_ptr, info_ptr);
  bit_depth = png_get_bit_depth(png_ptr, info_ptr);

  // Create buffer
  buffer = (unsigned char*) malloc(width * height * 4);
  if (buffer == NULL) {
    WTF_OM;
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
    free(png);
    png = NULL;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  if (apng) {
    // Get frame count
    frame_count = png_get_num_frames(png_ptr, info_ptr);
    hide_first_frame = png_get_first_frame_is_hidden(png_ptr, info_ptr);
    if (hide_first_frame) {
      frame_count--;
    }

    // Create frame info array
    frame_info_array = (PNG_FRAME_INFO*) calloc(frame_count, sizeof(PNG_FRAME_INFO));
    if (frame_info_array == NULL) {
      WTF_OM;
      free(buffer);
      buffer = NULL;
      png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
      free(png);
      png = NULL;
      close_patch_head_input_stream(env, patch_head_input_stream);
      destroy_patch_head_input_stream(env, &patch_head_input_stream);
      return NULL;
    }
  }

  // Configure to ARGB
  png_set_expand(png_ptr);
  if (bit_depth == 16) {
    png_set_scale_16(png_ptr);
  }
  if (color_type == PNG_COLOR_TYPE_GRAY ||
      color_type == PNG_COLOR_TYPE_GRAY_ALPHA) {
    png_set_gray_to_rgb(png_ptr);
  }
  if (!(color_type & PNG_COLOR_MASK_ALPHA)) {
    is_opaque = true;
    png_set_add_alpha(png_ptr, 0xff, PNG_FILLER_AFTER);
  } else {
    is_opaque = false;
  }

  if (apng) {
    if (hide_first_frame) {
      // Skip first frame
      read_image(png_ptr, buffer, width, height);
    }

    // Read first frame
    read_frame(png_ptr, info_ptr, frame_info_array);
    // Fix dop
    if (frame_info_array->dop == PNG_DISPOSE_OP_PREVIOUS) {
      frame_info_array->dop = PNG_DISPOSE_OP_BACKGROUND;
    }

    if (!partially || frame_count == 1) {
      // Read all frame
      for (i = 1; i < frame_count; read_frame(png_ptr, info_ptr, frame_info_array + i++));

      // Generate pop
      generate_pop(frame_info_array, frame_count);

      // End read
      png_read_end(png_ptr, info_ptr);
      png_destroy_read_struct(&png_ptr, &info_ptr, NULL);

      // Close input stream
      close_patch_head_input_stream(env, patch_head_input_stream);
      destroy_patch_head_input_stream(env, &patch_head_input_stream);

      png->partially = false;
      png->png_ptr = NULL;
      png->info_ptr = NULL;
      png->patch_head_input_stream = NULL;
    } else {
      png->partially = true;
      png->png_ptr = png_ptr;
      png->info_ptr = info_ptr;
      png->patch_head_input_stream = patch_head_input_stream;
    }

    // Fill PNG
    png->width = width;
    png->height = height;
    png->is_opaque = is_opaque;
    png->buffer = buffer;
    png->apng = true;
    png->buffer_index = -1;
    png->frame_info_array = frame_info_array;
    png->frame_count = frame_count;
    png->backup = NULL;

    // Render first frame
    PNG_advance(png);
  } else {
    read_image(png_ptr, buffer, width, height);

    // End read
    png_read_end(png_ptr, info_ptr);
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);

    // Close input stream
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);

    // Fill PNG
    png->width = width;
    png->height = height;
    png->buffer = buffer;
    png->apng = false;
    png->buffer_index = 0;
    png->frame_info_array = NULL;
    png->frame_count = 0;
    png->backup = NULL;
    png->partially = false;
    png->png_ptr = NULL;
    png->info_ptr = NULL;
    png->patch_head_input_stream = NULL;
  }

  return png;
}

bool PNG_complete(JNIEnv *env, PNG* png)
{
  int i;

  if (!png->partially) {
    return true;
  }

  if (png->png_ptr == NULL || png->info_ptr == NULL || png->patch_head_input_stream == NULL) {
    LOGE(MSG("Some stuff is NULL"));
    return false;
  }

  // Read left frames
  for (i = 1; i < png->frame_count; read_frame(png->png_ptr, png->info_ptr, png->frame_info_array + i++));

  // Generate pop
  generate_pop(png->frame_info_array, png->frame_count);

  // End read
  png_read_end(png->png_ptr, png->info_ptr);
  png_destroy_read_struct(&png->png_ptr, &png->info_ptr, NULL);

  // Close input stream
  close_patch_head_input_stream(env, png->patch_head_input_stream);
  destroy_patch_head_input_stream(env, &png->patch_head_input_stream);

  // Clean up
  png->partially = false;
  png->png_ptr = NULL;
  png->info_ptr = NULL;
  png->patch_head_input_stream = NULL;

  return true;
}

bool PNG_is_completed(PNG* png)
{
  return !png->partially;
}

void* PNG_get_pixels(PNG* png)
{
  if (!png->partially && PNG_get_frame_count(png) == 1) {
    return png->buffer;
  } else {
    return NULL;
  }
}

int PNG_get_width(PNG* png)
{
  return png->width;
}

int PNG_get_height(PNG* png)
{
  return png->height;
}

int PNG_get_byte_count(PNG* png)
{
  int i;
  PNG_FRAME_INFO* frame_info;
  int size = 0;
  // Add buffer size
  if (png->buffer != NULL) {
    size += png->width * png->height * 4;
  }
  // Add backup size
  if (png->backup != NULL) {
    size += png->width * png->height * 4;
  }
  // Add frames size
  if (png->frame_info_array != NULL) {
    for (i = 0; i < png->frame_count; i++) {
      frame_info = png->frame_info_array + i;
      // Add frame buffer size
      if (frame_info->buffer != NULL) {
        size += frame_info->width * frame_info->height * 4;
      }
    }
  }
  return size;
}

void PNG_render(PNG* png, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color)
{
  copy_pixels(png->buffer, png->width, png->height, src_x, src_y,
      dst, dst_w, dst_h, dst_x, dst_y,
      width, height, fill_blank, default_color);
}

static void backup(PNG* png)
{
  if (png->backup == NULL) {
    png->backup = (unsigned char*) malloc(png->width * png->height * 4);
    if (png->backup == NULL) {
      WTF_OM;
      return;
    }
  }
  memcpy(png->backup, png->buffer, png->width * png->height * 4);
}

static void restore(PNG* png)
{
  if (png->backup == NULL) {
    LOGE(MSG("Backup is NULL"));
  } else {
    memcpy(png->buffer, png->backup, png->width * png->height * 4);
  }
}

static void switch_buffer_backup(PNG* png)
{
  unsigned char* temp;

  if (png->backup == NULL) {
    backup(png);
  } else {
    temp = png->buffer;
    png->buffer = png->backup;
    png->backup = temp;
  }
}

static void blend_over(unsigned char* dp, const unsigned char* sp, size_t len)
{
  unsigned int i;
  int u, v, al;

  for (i = 0; i < len; i += 4, sp += 4, dp += 4) {
    if (sp[3] == 255) {
      memcpy(dp, sp, 4);
    } else if (sp[3] != 0) {
      if (dp[3] != 0) {
        u = sp[3] * 255;
        v = (255 - sp[3]) * dp[3];
        al = u + v;
        dp[0] = (unsigned char) ((sp[0] * u + dp[0] * v) / al);
        dp[1] = (unsigned char) ((sp[1] * u + dp[1] * v) / al);
        dp[2] = (unsigned char) ((sp[2] * u + dp[2] * v) / al);
        dp[3] = (unsigned char) (al / 255);
      } else {
        memcpy(dp, sp, 4);
      }
    }
  }
}

static void blend(unsigned char* src, int src_width, int src_height, int offset_x, int offset_y,
    unsigned char* dst, int dst_width, int dst_height, bool blend_op_over)
{
  int i;
  unsigned char* src_ptr;
  unsigned char* dst_ptr;
  size_t len;
  int copy_width = MIN(dst_width - offset_x, src_width);
  int copy_height = MIN(dst_height - offset_y, src_height);

  for (i = 0; i < copy_height; i++) {
    src_ptr = src + (i * src_width * 4);
    dst_ptr = dst + (((offset_y + i) * dst_width + offset_x) * 4);
    len = (size_t) (copy_width * 4);

    if (blend_op_over) {
      blend_over(dst_ptr, src_ptr, len);
    } else {
      memcpy(dst_ptr, src_ptr, len);
    }
  }
}

void PNG_advance(PNG* png)
{
  int index;

  if (!png->apng) {
    LOGW(MSG("It is not apng, no need to advance"));
    return;
  }

  index = (png->buffer_index + 1) % png->frame_count;
  if (index != 0 && png->partially) {
    LOGE(MSG("The png is only decoded partially. Only the first frame can be shown."));
    return;
  }

  PNG_FRAME_INFO* frame_info = png->frame_info_array + index;

  if (frame_info->dop == PNG_DISPOSE_OP_PREVIOUS && frame_info->pop == IMAGE_PNG_PREPARE_USE_BACKUP) {
    switch_buffer_backup(png);
  } else {
    // Backup
    if (frame_info->dop == PNG_DISPOSE_OP_PREVIOUS) {
      backup(png);
    }

    // Prepare
    switch (frame_info->pop) {
      case IMAGE_PNG_PREPARE_NONE:
        // Do nothing
        break;
      default:
      case IMAGE_PNG_PREPARE_BACKGROUND:
        // Set transparent
        memset(png->buffer, '\0', png->width * png->height * 4);
        break;
      case IMAGE_PNG_PREPARE_USE_BACKUP:
        restore(png);
        break;
    }
  }

  blend(frame_info->buffer, frame_info->width, frame_info->height, frame_info->offset_x, frame_info->offset_y,
      png->buffer, png->width, png->height, frame_info->bop == PNG_BLEND_OP_OVER);

  png->buffer_index = index;
}

int PNG_get_delay(PNG* png)
{
  if (png->apng) {
    return png->frame_info_array[png->buffer_index].delay;
  } else {
    return 0;
  }
}

int PNG_get_frame_count(PNG* png)
{
  if (png->apng) {
    return png->frame_count;
  } else {
    return 1;
  }
}

bool PNG_is_opaque(PNG* png)
{
  return png->is_opaque;
}

void PNG_recycle(JNIEnv *env, PNG* png)
{
  if (png == NULL) {
    return;
  }

  free(png->buffer);
  png->buffer = NULL;

  free_frame_info_array(png->frame_info_array, png->frame_count);
  png->frame_info_array = NULL;

  free(png->backup);
  png->backup = NULL;

  if (png->png_ptr != NULL && png->info_ptr != NULL) {
    png_destroy_read_struct(&png->png_ptr, &png->info_ptr, NULL);
  }
  png->png_ptr = NULL;
  png->info_ptr = NULL;

  if (png->patch_head_input_stream != NULL) {
    close_patch_head_input_stream(env, png->patch_head_input_stream);
    destroy_patch_head_input_stream(env, &png->patch_head_input_stream);
    png->patch_head_input_stream = NULL;
  }
}

#endif // IMAGE_SUPPORT_PNG

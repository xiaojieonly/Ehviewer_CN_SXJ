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

#include "config.h"
#ifdef IMAGE_SUPPORT_JPEG

#include <setjmp.h>
#include <stdlib.h>

#include "image_jpeg.h"
#include "image_utils.h"
#include "java_wrapper.h"
#include "../log.h"

struct my_error_mgr {
  struct jpeg_error_mgr pub;
  jmp_buf setjmp_buffer;
};

typedef struct my_error_mgr * my_error_ptr;

static char emsg[JMSG_LENGTH_MAX];

static void my_error_exit(j_common_ptr cinfo)
{
  my_error_ptr myerr = (my_error_ptr) cinfo->err;
  (*cinfo->err->format_message)(cinfo, emsg);
  longjmp(myerr->setjmp_buffer, 1);
}

static size_t custom_read(void * custom_stuff, unsigned char * buffer, size_t size)
{
  bool attach;
  size_t read;
  PatchHeadInputStream* patch_head_input_stream = custom_stuff;
  JNIEnv *env = obtain_env(&attach);

  if (env == NULL) {
    LOGE(MSG("Can't get JNIEnv"));
    return 0;
  }

  read = read_patch_head_input_stream(env, patch_head_input_stream, buffer, 0, size);

  if (attach) {
    release_env();
  }

  return read;
}

void* JPEG_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially)
{
  JPEG* jpeg = NULL;
  struct jpeg_decompress_struct cinfo;
  struct my_error_mgr jerr;
  unsigned char* buffer = NULL;
  size_t stride;
  unsigned char* line_buffer_array[3];
  int read_lines;

  jpeg = (JPEG*) malloc(sizeof(JPEG));
  if (jpeg == NULL) {
    WTF_OM;
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  // Init
  cinfo.err = jpeg_std_error(&jerr.pub);
  jerr.pub.error_exit = my_error_exit;
  if (setjmp(jerr.setjmp_buffer)) {
    LOGE(MSG("%s"), emsg);
    free(jpeg);
    free(buffer);
    jpeg_destroy_decompress(&cinfo);
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }
  jpeg_create_decompress(&cinfo);
  jpeg_custom_src(&cinfo, &custom_read, patch_head_input_stream);
  jpeg_read_header(&cinfo, TRUE);

  // Start decompress
  cinfo.out_color_space = JCS_EXT_RGBA;
  jpeg_start_decompress(&cinfo);

  stride = cinfo.output_components * cinfo.output_width;
  buffer = malloc(stride * cinfo.output_height);
  if (buffer == NULL) {
    free(jpeg);
    jpeg_destroy_decompress(&cinfo);
    close_patch_head_input_stream(env, patch_head_input_stream);
    destroy_patch_head_input_stream(env, &patch_head_input_stream);
    return NULL;
  }

  // Copy buffer
  line_buffer_array[0] = buffer;
  line_buffer_array[1] = line_buffer_array[0] + stride;
  line_buffer_array[2] = line_buffer_array[1] + stride;
  while (cinfo.output_scanline < cinfo.output_height) {
    read_lines = jpeg_read_scanlines(&cinfo, line_buffer_array, 3);
    line_buffer_array[0] += stride * read_lines;
    line_buffer_array[1] = line_buffer_array[0] + stride;
    line_buffer_array[2] = line_buffer_array[1] + stride;
  }

  // Finish decompress
  jpeg_finish_decompress(&cinfo);
  jpeg_destroy_decompress(&cinfo);

  // Close stream
  close_patch_head_input_stream(env, patch_head_input_stream);
  destroy_patch_head_input_stream(env, &patch_head_input_stream);

  // Fill jpeg
  jpeg->width = cinfo.output_width;
  jpeg->height = cinfo.output_height;
  jpeg->buffer = buffer;

  return jpeg;
}

bool JPEG_complete(JPEG* jpeg)
{
  return true;
}

bool JPEG_is_completed(JPEG* jpeg)
{
  return true;
}

void* JPEG_get_pixels(JPEG* jpeg)
{
  return jpeg->buffer;
}

int JPEG_get_width(JPEG* jpeg)
{
  return jpeg->width;
}

int JPEG_get_height(JPEG* jpeg)
{
  return jpeg->height;
}

int JPEG_get_byte_count(JPEG* jpeg)
{
  return jpeg->width * jpeg->height * 4;
}

void JPEG_render(JPEG* jpeg, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color)
{
  copy_pixels(jpeg->buffer, jpeg->width, jpeg->height, src_x, src_y,
      dst, dst_w, dst_h, dst_x, dst_y,
      width, height, fill_blank, default_color);
}

void JPEG_advance(JPEG* jpeg)
{
}

int JPEG_get_delay(JPEG* jpeg)
{
  return 0;
}

int JPEG_get_frame_count(JPEG* jpeg)
{
  return 1;
}

bool JPEG_is_opaque(JPEG* jpeg)
{
  return true;
}

void JPEG_recycle(JPEG* jpeg)
{
  free(jpeg->buffer);
  jpeg->buffer = NULL;

  free(jpeg);
}

#endif // IMAGE_SUPPORT_JPEG

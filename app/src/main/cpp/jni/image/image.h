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

#ifndef IMAGE_IMAGE_H
#define IMAGE_IMAGE_H

#include <stdbool.h>

#include "com_hippo_lib_image_Image.h"
#include "config.h"
#include "input_stream.h"

#define IMAGE_FORMAT_UNKNOWN com_hippo_lib_image_Image_FORMAT_UNKNOWN
#ifdef IMAGE_SUPPORT_PLAIN
#define IMAGE_FORMAT_PLAIN  com_hippo_lib_image_Image_FORMAT_PLAIN
#endif
#ifdef IMAGE_SUPPORT_JPEG
#define IMAGE_FORMAT_JPEG com_hippo_lib_image_Image_FORMAT_JPEG
#endif
#ifdef IMAGE_SUPPORT_PNG
#define IMAGE_FORMAT_PNG com_hippo_lib_image_Image_FORMAT_PNG
#endif
#ifdef IMAGE_SUPPORT_GIF
#define IMAGE_FORMAT_GIF com_hippo_lib_image_Image_FORMAT_GIF
#endif
#ifdef IMAGE_SUPPORT_WEBP
#define IMAGE_FORMAT_WEBP com_hippo_lib_image_Image_FORMAT_WEBP
#endif

#define IMAGE_MAX_SUPPORTED_FORMAT_COUNT 4

void* decode(JNIEnv* env, InputStream* stream, bool partially, int* format);
void* create(unsigned int width, unsigned int height, const void* data);
bool complete(JNIEnv* env, void* image, int format);
bool is_completed(void* image, int format);
int get_width(void* image, int format);
int get_height(void* image, int format);
int get_byte_count(void* image, int format);
void render(void* image, int format, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);
void advance(void* image, int format);
int get_delay(void* image, int format);
int get_frame_count(void* image, int format);
bool is_opaque(void* image, int format);
bool is_gray(void* image, int format, int error);
void clahe(void* image, int format, bool to_gray);
void recycle(JNIEnv *env, void* image, int format);
int get_supported_formats(int *);
const char *get_decoder_description(int);

#endif //IMAGE_IMAGE_H

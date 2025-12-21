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

#include "config.h"
#ifdef IMAGE_SUPPORT_WEBP

#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>

#include "../log.h"
#include "image_utils.h"
#include "image_webp.h"
#include "patch_head_input_stream.h"
#include "webp/demux.h"
#include "webp/decode.h"

#define WEBP_DEFAULT_FRAME_DELAY 100

static void free_animation_buffers(WEBP* webp, unsigned int decoded) {
  unsigned int i;
  if (webp->frames != NULL) {
    for (i = 0; i < decoded; i++) {
      free(webp->frames[i]);
      webp->frames[i] = NULL;
    }
    free(webp->frames);
    webp->frames = NULL;
  }
  free(webp->delays);
  webp->delays = NULL;
}

static bool decode_static_image(WEBP* webp,
                                const uint8_t* data,
                                size_t length,
                                const WebPBitstreamFeatures* features) {
  size_t buffer_size;
  unsigned int width;
  unsigned int height;
  unsigned char* buffer = NULL;
  size_t pixel_count;
  if (features->width <= 0 || features->height <= 0) {
    LOGE(MSG("Invalid WebP dimension %d x %d"), features->width, features->height);
    return false;
  }
  width = (unsigned int) features->width;
  height = (unsigned int) features->height;
  pixel_count = (size_t) width * (size_t) height;
  if (width == 0 || height == 0 || pixel_count / width != height || pixel_count > SIZE_MAX / 4u) {
    LOGE(MSG("WebP dimension overflow %u x %u"), width, height);
    return false;
  }
  buffer_size = pixel_count * 4u;
  buffer = (unsigned char*) malloc(buffer_size);
  if (buffer == NULL) {
    WTF_OM;
    return false;
  }
  if (WebPDecodeRGBAInto(data, length, buffer, buffer_size, (int) (width * 4u)) == NULL) {
    LOGE(MSG("Failed to decode static WebP image"));
    free(buffer);
    return false;
  }
  webp->width = width;
  webp->height = height;
  webp->buffer = buffer;
  webp->animated = false;
  webp->frame_count = 1;
  webp->current_frame = 0;
  return true;
}

static bool decode_animation(WEBP* webp, const uint8_t* data, size_t length) {
  WebPAnimDecoderOptions options;
  WebPAnimDecoder* decoder = NULL;
  WebPAnimInfo info;
  WebPData webp_data;
  unsigned int decoded = 0;
  size_t frame_bytes;
  uint8_t* frame = NULL;
  int timestamp = 0;
  if (!WebPAnimDecoderOptionsInit(&options)) {
    LOGE(MSG("Failed to init WebPAnimDecoderOptions"));
    return false;
  }
  options.color_mode = MODE_RGBA;
  webp_data.bytes = data;
  webp_data.size = length;
  decoder = WebPAnimDecoderNew(&webp_data, &options);
  if (decoder == NULL) {
    LOGE(MSG("Failed to create WebPAnimDecoder"));
    return false;
  }
  if (!WebPAnimDecoderGetInfo(decoder, &info)) {
    LOGE(MSG("Failed to query WebP animation info"));
    WebPAnimDecoderDelete(decoder);
    return false;
  }
  if (info.canvas_width == 0 || info.canvas_height == 0 || info.frame_count == 0) {
    LOGE(MSG("Invalid WebP animation info"));
    WebPAnimDecoderDelete(decoder);
    return false;
  }
  {
    size_t pixel_count = (size_t) info.canvas_width * (size_t) info.canvas_height;
    if (pixel_count / info.canvas_width != info.canvas_height || pixel_count > SIZE_MAX / 4u) {
      LOGE(MSG("WebP animation dimension overflow %u x %u"),
           info.canvas_width, info.canvas_height);
      WebPAnimDecoderDelete(decoder);
      return false;
    }
    frame_bytes = pixel_count * 4u;
  }
  webp->frames = (unsigned char**) calloc(info.frame_count, sizeof(unsigned char*));
  webp->delays = (int*) calloc(info.frame_count, sizeof(int));
  if (webp->frames == NULL || webp->delays == NULL) {
    WTF_OM;
    WebPAnimDecoderDelete(decoder);
    return false;
  }
  while (decoded < info.frame_count && WebPAnimDecoderGetNext(decoder, &frame, &timestamp)) {
    webp->frames[decoded] = (unsigned char*) malloc(frame_bytes);
    if (webp->frames[decoded] == NULL) {
      WTF_OM;
      WebPAnimDecoderDelete(decoder);
      free_animation_buffers(webp, decoded);
      return false;
    }
    memcpy(webp->frames[decoded], frame, frame_bytes);
    decoded++;
  }
  if (decoded == 0) {
    LOGE(MSG("No WebP animation frames decoded"));
    WebPAnimDecoderDelete(decoder);
    free_animation_buffers(webp, 0);
    return false;
  }
  {
    const WebPDemuxer* demux = WebPAnimDecoderGetDemuxer(decoder);
    if (demux != NULL) {
      WebPIterator iter;
      if (WebPDemuxGetFrame(demux, 1, &iter)) {
        do {
          int index = iter.frame_num - 1;
          if (index >= 0 && (unsigned int) index < decoded) {
            webp->delays[index] = iter.duration;
          }
        } while (WebPDemuxNextFrame(&iter));
        WebPDemuxReleaseIterator(&iter);
      }
    }
  }
  for (unsigned int i = 0; i < decoded; i++) {
    if (webp->delays[i] <= 0) {
      webp->delays[i] = WEBP_DEFAULT_FRAME_DELAY;
    }
  }
  WebPAnimDecoderDelete(decoder);
  webp->width = info.canvas_width;
  webp->height = info.canvas_height;
  webp->animated = true;
  webp->frame_count = decoded;
  webp->current_frame = 0;
  return true;
}

void* WEBP_decode(JNIEnv* env, PatchHeadInputStream* patch_head_input_stream, bool partially) {
  WEBP* webp = NULL;
  size_t length = 0;
  unsigned char* data = NULL;
  WebPBitstreamFeatures features;
  VP8StatusCode status;
  (void) partially;

  data = (unsigned char*) read_patch_head_input_stream_all(env, patch_head_input_stream, &length);
  close_patch_head_input_stream(env, patch_head_input_stream);
  destroy_patch_head_input_stream(env, &patch_head_input_stream);

  if (data == NULL) {
    WTF_OM;
    return NULL;
  }

  status = WebPGetFeatures(data, length, &features);
  if (status != VP8_STATUS_OK) {
    LOGE(MSG("WebPGetFeatures failed with status %d"), status);
    free(data);
    return NULL;
  }

  webp = (WEBP*) calloc(1, sizeof(WEBP));
  if (webp == NULL) {
    WTF_OM;
    free(data);
    return NULL;
  }

  webp->is_opaque = !features.has_alpha;

  if (features.has_animation) {
    if (!decode_animation(webp, data, length)) {
      free(webp);
      free(data);
      return NULL;
    }
  } else {
    if (!decode_static_image(webp, data, length, &features)) {
      free(webp);
      free(data);
      return NULL;
    }
  }

  free(data);
  return webp;
}

bool WEBP_complete(JNIEnv* env, WEBP* webp) {
  (void) env;
  (void) webp;
  return true;
}

bool WEBP_is_completed(WEBP* webp) {
  (void) webp;
  return true;
}

void* WEBP_get_pixels(WEBP* webp) {
  if (webp == NULL) {
    return NULL;
  }
  if (!webp->animated) {
    return webp->buffer;
  }
  if (webp->frame_count == 1 && webp->frames != NULL) {
    return webp->frames[0];
  }
  return NULL;
}

int WEBP_get_width(WEBP* webp) {
  return (int) webp->width;
}

int WEBP_get_height(WEBP* webp) {
  return (int) webp->height;
}

int WEBP_get_byte_count(WEBP* webp) {
  unsigned int i;
  size_t size = sizeof(WEBP);
  if (webp->buffer != NULL) {
    size += (size_t) webp->width * webp->height * 4u;
  }
  if (webp->frames != NULL) {
    size += (size_t) webp->frame_count * sizeof(unsigned char*);
    for (i = 0; i < webp->frame_count; i++) {
      if (webp->frames[i] != NULL) {
        size += (size_t) webp->width * webp->height * 4u;
      }
    }
  }
  if (webp->delays != NULL) {
    size += (size_t) webp->frame_count * sizeof(int);
  }
  return (int) size;
}

void WEBP_render(WEBP* webp, int src_x, int src_y,
                 void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
                 int width, int height, bool fill_blank, int default_color) {
  unsigned char* source = NULL;
  if (webp->animated && webp->frames != NULL && webp->frame_count > 0) {
    source = webp->frames[webp->current_frame % webp->frame_count];
  } else {
    source = webp->buffer;
  }
  if (source == NULL) {
    LOGE(MSG("WEBP render source is NULL"));
    return;
  }
  copy_pixels(source, webp->width, webp->height, src_x, src_y,
              dst, dst_w, dst_h, dst_x, dst_y,
              width, height, fill_blank, default_color);
}

void WEBP_advance(WEBP* webp) {
  if (webp->animated && webp->frame_count > 0) {
    webp->current_frame = (webp->current_frame + 1) % webp->frame_count;
  }
}

int WEBP_get_delay(WEBP* webp) {
  if (!webp->animated || webp->delays == NULL || webp->frame_count == 0) {
    return 0;
  }
  return webp->delays[webp->current_frame % webp->frame_count];
}

int WEBP_get_frame_count(WEBP* webp) {
  if (webp->animated) {
    return (int) webp->frame_count;
  } else {
    return 1;
  }
}

bool WEBP_is_opaque(WEBP* webp) {
  return webp->is_opaque;
}

void WEBP_recycle(JNIEnv* env, WEBP* webp) {
  unsigned int frame_count;
  (void) env;
  if (webp == NULL) {
    return;
  }
  if (webp->buffer != NULL) {
    free(webp->buffer);
    webp->buffer = NULL;
  }
  if (webp->frames != NULL) {
    frame_count = webp->frame_count;
    for (unsigned int i = 0; i < frame_count; i++) {
      free(webp->frames[i]);
      webp->frames[i] = NULL;
    }
    free(webp->frames);
    webp->frames = NULL;
  }
  if (webp->delays != NULL) {
    free(webp->delays);
    webp->delays = NULL;
  }
  free(webp);
}

#endif // IMAGE_SUPPORT_WEBP


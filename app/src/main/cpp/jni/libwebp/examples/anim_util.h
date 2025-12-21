// Copyright 2015 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Utilities for animated images

#ifndef WEBP_EXAMPLES_ANIM_UTIL_H_
#define WEBP_EXAMPLES_ANIM_UTIL_H_

#ifdef HAVE_CONFIG_H
#include "../webp/config.h"
#endif

#include <stdint.h>
#include "../src/webp/types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  ANIM_GIF,
  ANIM_WEBP
} AnimatedFileFormat;

typedef struct {
  uint8_t* rgba;         // Decoded and reconstructed full frame.
  int duration;          // Frame duration in milliseconds.
  int is_key_frame;      // True if this frame is a key-frame.
} DecodedFrame;

typedef struct {
  AnimatedFileFormat format;
  uint32_t canvas_width;
  uint32_t canvas_height;
  uint32_t bgcolor;
  uint32_t loop_count;
  DecodedFrame* frames;
  uint32_t num_frames;
  void* raw_mem;
} AnimatedImage;

// Deallocate everything in 'image' (but not the object itself).
void ClearAnimatedImage(AnimatedImage* const image);

// Read animated image file into 'AnimatedImage' struct.
// If 'dump_frames' is true, dump frames to 'dump_folder'.
// Previous content of 'image' is obliterated.
// Upon successful return, content of 'image' must be deleted by
// calling 'ClearAnimatedImage'.
int ReadAnimatedImage(const char filename[], AnimatedImage* const image,
                      int dump_frames, const char dump_folder[]);

// Given two RGBA buffers, calculate max pixel difference and PSNR.
// If 'premultiply' is true, R/G/B values will be pre-multiplied by the
// transparency before comparison.
void GetDiffAndPSNR(const uint8_t rgba1[], const uint8_t rgba2[],
                    uint32_t width, uint32_t height, int premultiply,
                    int* const max_diff, double* const psnr);

// Return library versions used by anim_util.
void GetAnimatedImageVersions(int* const decoder_version,
                              int* const demux_version);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_EXAMPLES_ANIM_UTIL_H_

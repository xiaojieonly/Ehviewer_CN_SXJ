// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  All-in-one library to save PNG/JPEG/WebP/TIFF/WIC images.
//
// Author: Skal (pascal.massimino@gmail.com)

#ifndef WEBP_IMAGEIO_IMAGE_ENC_H_
#define WEBP_IMAGEIO_IMAGE_ENC_H_

#include <stdio.h>

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include "../src/webp/types.h"
#include "../src/webp/decode.h"

#ifdef __cplusplus
extern "C" {
#endif

// Output types
typedef enum {
  PNG = 0,
  PAM,
  PPM,
  PGM,
  BMP,
  TIFF,
  RAW_YUV,
  ALPHA_PLANE_ONLY,  // this is for experimenting only
  // forced colorspace output (for testing, mostly)
  RGB, RGBA, BGR, BGRA, ARGB,
  RGBA_4444, RGB_565,
  rgbA, bgrA, Argb, rgbA_4444,
  YUV, YUVA
} WebPOutputFileFormat;

// General all-purpose call.
// Most formats expect a 'buffer' containing RGBA-like samples, except
// RAW_YUV, YUV and YUVA formats.
// If 'out_file_name' is "-", data is saved to stdout.
// Returns false if an error occurred, true otherwise.
int WebPSaveImage(const WebPDecBuffer* const buffer,
                  WebPOutputFileFormat format, const char* const out_file_name);

// Save to PNG.
#ifdef HAVE_WINCODEC_H
int WebPWritePNG(const char* out_file_name, int use_stdout,
                 const struct WebPDecBuffer* const buffer);
#else
int WebPWritePNG(FILE* out_file, const WebPDecBuffer* const buffer);
#endif

// Save to PPM format (RGB, no alpha)
int WebPWritePPM(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save to PAM format (= PPM + alpha)
int WebPWritePAM(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save 16b mode (RGBA4444, RGB565, ...) for debugging purposes.
int WebPWrite16bAsPGM(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save as BMP
int WebPWriteBMP(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save as TIFF
int WebPWriteTIFF(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save the ALPHA plane (only) as a PGM
int WebPWriteAlphaPlane(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save as YUV samples as PGM format (using IMC4 layout).
// See: https://www.fourcc.org/yuv.php#IMC4.
// (very convenient format for viewing the samples, esp. for odd dimensions).
int WebPWritePGM(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save YUV(A) planes sequentially (raw dump)
int WebPWriteYUV(FILE* fout, const struct WebPDecBuffer* const buffer);

// Save 16b mode (RGBA4444, RGB565, ...) as PGM format, for debugging purposes.
int WebPWrite16bAsPGM(FILE* fout, const struct WebPDecBuffer* const buffer);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_IMAGEIO_IMAGE_ENC_H_

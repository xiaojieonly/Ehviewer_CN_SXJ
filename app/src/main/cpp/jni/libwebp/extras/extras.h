// Copyright 2015 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//

#ifndef WEBP_EXTRAS_EXTRAS_H_
#define WEBP_EXTRAS_EXTRAS_H_

#include "webp/types.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "../sharpyuv/sharpyuv.h
#include "webp/encode.h"

#define WEBP_EXTRAS_ABI_VERSION 0x0003    // MAJOR(8b) + MINOR(8b)

//------------------------------------------------------------------------------

// Returns the version number of the extras library, packed in hexadecimal using
// 8bits for each of major/minor/revision. E.g: v2.5.7 is 0x020507.
WEBP_EXTERN int WebPGetExtrasVersion(void);

//------------------------------------------------------------------------------
// Ad-hoc colorspace importers.

// Import luma sample (gray scale image) into 'picture'. The 'picture'
// width and height must be set prior to calling this function.
WEBP_EXTERN int WebPImportGray(const uint8_t* gray, WebPPicture* picture);

// Import rgb sample in RGB565 packed format into 'picture'. The 'picture'
// width and height must be set prior to calling this function.
WEBP_EXTERN int WebPImportRGB565(const uint8_t* rgb565, WebPPicture* pic);

// Import rgb sample in RGB4444 packed format into 'picture'. The 'picture'
// width and height must be set prior to calling this function.
WEBP_EXTERN int WebPImportRGB4444(const uint8_t* rgb4444, WebPPicture* pic);

// Import a color mapped image. The number of colors is less or equal to
// MAX_PALETTE_SIZE. 'pic' must have been initialized. Its content, if any,
// will be discarded. Returns 'false' in case of error, or if indexed[] contains
// invalid indices.
WEBP_EXTERN int
WebPImportColorMappedARGB(const uint8_t* indexed, int indexed_stride,
                          const uint32_t palette[], int palette_size,
                          WebPPicture* pic);

// Convert the ARGB content of 'pic' from associated to unassociated.
// 'pic' can be for instance the result of calling of some WebPPictureImportXXX
// functions, with pic->use_argb set to 'true'. It is assumed (and not checked)
// that the pre-multiplied r/g/b values as less or equal than the alpha value.
// Return false in case of error (invalid parameter, ...).
WEBP_EXTERN int WebPUnmultiplyARGB(WebPPicture* pic);

//------------------------------------------------------------------------------

// Parse a bitstream, search for VP8 (lossy) header and report a
// rough estimation of the quality factor used for compressing the bitstream.
// If the bitstream is in lossless format, the special value '101' is returned.
// Otherwise (lossy bitstream), the returned value is in the range [0..100].
// Any error (invalid bitstream, animated WebP, incomplete header, etc.)
// will return a value of -1.
WEBP_EXTERN int VP8EstimateQuality(const uint8_t* const data, size_t size);

//------------------------------------------------------------------------------

// Computes a score between 0 and 100 which represents the risk of having visual
// quality loss from converting an RGB image to YUV420.
// A low score, typically < 40, means there is a low risk of artifacts from
// chroma subsampling and a simple averaging algorithm can be used instead of
// the more expensive SharpYuvConvert function.
// A medium score, typically >= 40 and < 70, means that simple chroma
// subsampling will produce artifacts and it may be advisable to use the more
// costly SharpYuvConvert for YUV420 conversion.
// A high score, typically >= 70, means there is a very high risk of artifacts
// from chroma subsampling even with SharpYuvConvert, and best results might be
// achieved by using YUV444.
// If not using SharpYuvConvert, a threshold of about 50 can be used to decide
// between (simple averaging) 420 and 444.
// r_ptr, g_ptr, b_ptr: pointers to the source r, g and b channels. Should point
//     to uint8_t buffers if rgb_bit_depth is 8, or uint16_t buffers otherwise.
// rgb_step: distance in bytes between two horizontally adjacent pixels on the
//     r, g and b channels. If rgb_bit_depth is > 8, it should be a
//     multiple of 2.
// rgb_stride: distance in bytes between two vertically adjacent pixels on the
//     r, g, and b channels. If rgb_bit_depth is > 8, it should be a
//     multiple of 2.
// rgb_bit_depth: number of bits for each r/g/b value. Only a value of 8 is
//     currently supported.
// width, height: width and height of the image in pixels
// Returns 0 on failure.
WEBP_EXTERN int SharpYuvEstimate420Risk(
    const void* r_ptr, const void* g_ptr, const void* b_ptr, int rgb_step,
    int rgb_stride, int rgb_bit_depth, int width, int height,
    const SharpYuvOptions* options, float* score);

//------------------------------------------------------------------------------

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_EXTRAS_EXTRAS_H_

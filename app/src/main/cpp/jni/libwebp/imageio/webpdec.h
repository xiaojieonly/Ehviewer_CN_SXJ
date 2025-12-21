// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// WebP decode.

#ifndef WEBP_IMAGEIO_WEBPDEC_H_
#define WEBP_IMAGEIO_WEBPDEC_H_

#include "../src/webp/decode.h"

#ifdef __cplusplus
extern "C" {
#endif

struct Metadata;
struct WebPPicture;

//------------------------------------------------------------------------------
// WebP decoding

// Prints an informative error message regarding decode failure of 'in_file'.
// 'status' is treated as a VP8StatusCode and if valid will be printed as a
// text string.
void PrintWebPError(const char* const in_file, int status);

// Reads a WebP from 'in_file', returning the contents and size in 'data' and
// 'data_size'. If not NULL, 'bitstream' is populated using WebPGetFeatures().
// Returns true on success.
int LoadWebP(const char* const in_file,
             const uint8_t** data, size_t* data_size,
             WebPBitstreamFeatures* bitstream);

// Decodes the WebP contained in 'data'.
// 'config' is a structure previously initialized by WebPInitDecoderConfig().
// 'config->output' should have the desired colorspace selected.
// Returns the decoder status. On success 'config->output' will contain the
// decoded picture.
VP8StatusCode DecodeWebP(const uint8_t* const data, size_t data_size,
                         WebPDecoderConfig* const config);

// Same as DecodeWebP(), but using the incremental decoder.
VP8StatusCode DecodeWebPIncremental(
    const uint8_t* const data, size_t data_size,
    WebPDecoderConfig* const config);

//------------------------------------------------------------------------------

// Decodes a WebP contained in 'data', returning the decoded output in 'pic'.
// Output is RGBA or YUVA, depending on pic->use_argb value.
// If 'keep_alpha' is true and the WebP has an alpha channel, the output is RGBA
// or YUVA. Otherwise, alpha channel is dropped and output is RGB or YUV.
// Returns true on success.
int ReadWebP(const uint8_t* const data, size_t data_size,
             struct WebPPicture* const pic,
             int keep_alpha, struct Metadata* const metadata);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_IMAGEIO_WEBPDEC_H_

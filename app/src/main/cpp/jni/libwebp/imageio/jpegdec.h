// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// JPEG decode.

#ifndef WEBP_IMAGEIO_JPEGDEC_H_
#define WEBP_IMAGEIO_JPEGDEC_H_

#include "webp/types.h"

#ifdef __cplusplus
extern "C" {
#endif

struct Metadata;
struct WebPPicture;

// Reads a JPEG from 'data', returning the decoded output in 'pic'.
// The output is RGB or YUV depending on pic->use_argb value.
// Returns true on success.
// 'keep_alpha' has no effect, but is kept for coherence with other signatures
// for image readers.
int ReadJPEG(const uint8_t* const data, size_t data_size,
             struct WebPPicture* const pic, int keep_alpha,
             struct Metadata* const metadata);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_IMAGEIO_JPEGDEC_H_

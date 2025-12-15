// Copyright 2013 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Windows Imaging Component (WIC) decode.

#ifndef WEBP_IMAGEIO_WICDEC_H_
#define WEBP_IMAGEIO_WICDEC_H_

#ifdef __cplusplus
extern "C" {
#endif

struct Metadata;
struct WebPPicture;

// Reads an image from 'filename', returning the decoded output in 'pic'.
// If 'keep_alpha' is true and the image has an alpha channel, the output is
// RGBA otherwise it will be RGB. pic->use_argb is always forced to true.
// Returns true on success.
int ReadPictureWithWIC(const char* const filename,
                       struct WebPPicture* const pic, int keep_alpha,
                       struct Metadata* const metadata);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_IMAGEIO_WICDEC_H_

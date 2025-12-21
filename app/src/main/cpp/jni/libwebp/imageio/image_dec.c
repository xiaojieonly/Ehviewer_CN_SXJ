// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Generic image-type guessing.

#include "./image_dec.h"

const char* WebPGetEnabledInputFileFormats(void) {
  return "WebP"
#ifdef WEBP_HAVE_JPEG
         ", JPEG"
#endif
#ifdef WEBP_HAVE_PNG
         ", PNG"
#endif
         ", PNM (PGM, PPM, PAM)"
#ifdef WEBP_HAVE_TIFF
         ", TIFF"
#endif
#ifdef HAVE_WINCODEC_H
         ", Windows Imaging Component (WIC)"
#endif
         "";
}

static WEBP_INLINE uint32_t GetBE32(const uint8_t buf[]) {
  return ((uint32_t)buf[0] << 24) | (buf[1] << 16) | (buf[2] << 8) | buf[3];
}

WebPInputFileFormat WebPGuessImageType(const uint8_t* const data,
                                       size_t data_size) {
  WebPInputFileFormat format = WEBP_UNSUPPORTED_FORMAT;
  if (data != NULL && data_size >= 12) {
    const uint32_t magic1 = GetBE32(data + 0);
    const uint32_t magic2 = GetBE32(data + 8);
    if (magic1 == 0x89504E47U) {
      format = WEBP_PNG_FORMAT;
    } else if (magic1 >= 0xFFD8FF00U && magic1 <= 0xFFD8FFFFU) {
      format = WEBP_JPEG_FORMAT;
    } else if (magic1 == 0x49492A00 || magic1 == 0x4D4D002A) {
      format = WEBP_TIFF_FORMAT;
    } else if (magic1 == 0x52494646 && magic2 == 0x57454250) {
      format = WEBP_WEBP_FORMAT;
    } else if (((magic1 >> 24) & 0xff) == 'P') {
      const int type = (magic1 >> 16) & 0xff;
      // we only support 'P5 -> P7' for now.
      if (type >= '5' && type <= '7') format = WEBP_PNM_FORMAT;
    }
  }
  return format;
}

static int FailReader(const uint8_t* const data, size_t data_size,
                      struct WebPPicture* const pic,
                      int keep_alpha, struct Metadata* const metadata) {
  (void)data;
  (void)data_size;
  (void)pic;
  (void)keep_alpha;
  (void)metadata;
  return 0;
}

WebPImageReader WebPGetImageReader(WebPInputFileFormat format) {
  switch (format) {
    case WEBP_PNG_FORMAT: return ReadPNG;
    case WEBP_JPEG_FORMAT: return ReadJPEG;
    case WEBP_TIFF_FORMAT: return ReadTIFF;
    case WEBP_WEBP_FORMAT: return ReadWebP;
    case WEBP_PNM_FORMAT: return ReadPNM;
    default: return (WebPImageReader) FailReader;
  }
}

WebPImageReader WebPGuessImageReader(const uint8_t* const data,
                                     size_t data_size) {
  return WebPGetImageReader(WebPGuessImageType(data, data_size));
}

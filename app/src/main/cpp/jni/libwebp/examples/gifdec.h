// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// GIF decode.

#ifndef WEBP_EXAMPLES_GIFDEC_H_
#define WEBP_EXAMPLES_GIFDEC_H_

#include <stdio.h>
#include "../src/webp/types.h"

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#ifdef WEBP_HAVE_GIF
#include <gif_lib.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

// GIFLIB_MAJOR is only defined in libgif >= 4.2.0.
#if defined(GIFLIB_MAJOR) && defined(GIFLIB_MINOR)
# define LOCAL_GIF_VERSION ((GIFLIB_MAJOR << 8) | GIFLIB_MINOR)
# define LOCAL_GIF_PREREQ(maj, min) \
    (LOCAL_GIF_VERSION >= (((maj) << 8) | (min)))
#else
# define LOCAL_GIF_VERSION 0
# define LOCAL_GIF_PREREQ(maj, min) 0
#endif

#define GIF_INDEX_INVALID (-1)

typedef enum GIFDisposeMethod {
  GIF_DISPOSE_NONE,
  GIF_DISPOSE_BACKGROUND,
  GIF_DISPOSE_RESTORE_PREVIOUS
} GIFDisposeMethod;

typedef struct {
  int x_offset, y_offset, width, height;
} GIFFrameRect;

struct WebPData;
struct WebPPicture;

#ifndef WEBP_HAVE_GIF
struct ColorMapObject;
struct GifFileType;
typedef unsigned char GifByteType;
#endif

// Given the index of background color and transparent color, returns the
// corresponding background color (in BGRA format) in 'bgcolor'.
void GIFGetBackgroundColor(const struct ColorMapObject* const color_map,
                           int bgcolor_index, int transparent_index,
                           uint32_t* const bgcolor);

// Parses the given graphics extension data to get frame duration (in 1ms
// units), dispose method and transparent color index.
// Returns true on success.
int GIFReadGraphicsExtension(const GifByteType* const buf, int* const duration,
                             GIFDisposeMethod* const dispose,
                             int* const transparent_index);

// Reads the next GIF frame from 'gif' into 'picture'. Also, returns the GIF
// frame dimensions and offsets in 'rect'.
// Returns true on success.
int GIFReadFrame(struct GifFileType* const gif, int transparent_index,
                 GIFFrameRect* const gif_rect,
                 struct WebPPicture* const picture);

// Parses loop count from the given Netscape extension data.
int GIFReadLoopCount(struct GifFileType* const gif, GifByteType** const buf,
                     int* const loop_count);

// Parses the given ICC or XMP extension data and stores it into 'metadata'.
// Returns true on success.
int GIFReadMetadata(struct GifFileType* const gif, GifByteType** const buf,
                    struct WebPData* const metadata);

// Dispose the pixels within 'rect' of 'curr_canvas' based on 'dispose' method
// and 'prev_canvas'.
void GIFDisposeFrame(GIFDisposeMethod dispose, const GIFFrameRect* const rect,
                     const struct WebPPicture* const prev_canvas,
                     struct WebPPicture* const curr_canvas);

// Given 'src' picture and its frame rectangle 'rect', blend it into 'dst'.
void GIFBlendFrames(const struct WebPPicture* const src,
                    const GIFFrameRect* const rect,
                    struct WebPPicture* const dst);

// Prints an error string based on 'gif_error'.
void GIFDisplayError(const struct GifFileType* const gif, int gif_error);

// In the given 'pic', clear the pixels in 'rect' to transparent color.
void GIFClearPic(struct WebPPicture* const pic, const GIFFrameRect* const rect);

// Copy pixels from 'src' to 'dst' honoring strides. 'src' and 'dst' are assumed
// to be already allocated.
void GIFCopyPixels(const struct WebPPicture* const src,
                   struct WebPPicture* const dst);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_EXAMPLES_GIFDEC_H_

// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// GIF decode.

#include "./gifdec.h"

#include <stdio.h>

#ifdef WEBP_HAVE_GIF
#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "webp/encode.h"
#include "webp/mux_types.h"

#define GIF_TRANSPARENT_COLOR 0x00000000u
#define GIF_WHITE_COLOR       0xffffffffu
#define GIF_TRANSPARENT_MASK  0x01
#define GIF_DISPOSE_MASK      0x07
#define GIF_DISPOSE_SHIFT     2

// from utils/utils.h
#ifdef __cplusplus
extern "C" {
#endif
extern void WebPCopyPlane(const uint8_t* src, int src_stride,
                          uint8_t* dst, int dst_stride,
                          int width, int height);
extern void WebPCopyPixels(const WebPPicture* const src,
                           WebPPicture* const dst);
#ifdef __cplusplus
}
#endif

void GIFGetBackgroundColor(const ColorMapObject* const color_map,
                           int bgcolor_index, int transparent_index,
                           uint32_t* const bgcolor) {
  if (transparent_index != GIF_INDEX_INVALID &&
      bgcolor_index == transparent_index) {
    *bgcolor = GIF_TRANSPARENT_COLOR;  // Special case.
  } else if (color_map == NULL || color_map->Colors == NULL
             || bgcolor_index >= color_map->ColorCount) {
    *bgcolor = GIF_WHITE_COLOR;
    fprintf(stderr,
            "GIF decode warning: invalid background color index. Assuming "
            "white background.\n");
  } else {
    const GifColorType color = color_map->Colors[bgcolor_index];
    *bgcolor = (0xffu       << 24)
             | (color.Red   << 16)
             | (color.Green <<  8)
             | (color.Blue  <<  0);
  }
}

int GIFReadGraphicsExtension(const GifByteType* const buf, int* const duration,
                             GIFDisposeMethod* const dispose,
                             int* const transparent_index) {
  const int flags = buf[1];
  const int dispose_raw = (flags >> GIF_DISPOSE_SHIFT) & GIF_DISPOSE_MASK;
  const int duration_raw = buf[2] | (buf[3] << 8);  // In 10 ms units.
  if (buf[0] != 4) return 0;
  *duration = duration_raw * 10;  // Duration is in 1 ms units.
  switch (dispose_raw) {
    case 3:
      *dispose = GIF_DISPOSE_RESTORE_PREVIOUS;
      break;
    case 2:
      *dispose = GIF_DISPOSE_BACKGROUND;
      break;
    case 1:
    case 0:
    default:
      *dispose = GIF_DISPOSE_NONE;
      break;
  }
  *transparent_index =
      (flags & GIF_TRANSPARENT_MASK) ? buf[4] : GIF_INDEX_INVALID;
  return 1;
}

static int Remap(const GifFileType* const gif, const uint8_t* const src,
                 int len, int transparent_index, uint32_t* dst) {
  int i;
  const GifColorType* colors;
  const ColorMapObject* const cmap =
      gif->Image.ColorMap ? gif->Image.ColorMap : gif->SColorMap;
  if (cmap == NULL) return 1;
  if (cmap->Colors == NULL || cmap->ColorCount <= 0) return 0;
  colors = cmap->Colors;

  for (i = 0; i < len; ++i) {
    if (src[i] == transparent_index) {
      dst[i] = GIF_TRANSPARENT_COLOR;
    } else if (src[i] < cmap->ColorCount) {
      const GifColorType c = colors[src[i]];
      dst[i] = c.Blue | (c.Green << 8) | (c.Red << 16) | (0xffu << 24);
    } else {
      return 0;
    }
  }
  return 1;
}

int GIFReadFrame(GifFileType* const gif, int transparent_index,
                 GIFFrameRect* const gif_rect, WebPPicture* const picture) {
  WebPPicture sub_image;
  const GifImageDesc* const image_desc = &gif->Image;
  uint32_t* dst = NULL;
  uint8_t* tmp = NULL;
  const GIFFrameRect rect = {
      image_desc->Left, image_desc->Top, image_desc->Width, image_desc->Height
  };
  const uint64_t memory_needed = 4 * rect.width * (uint64_t)rect.height;
  int ok = 0;
  *gif_rect = rect;

  if (memory_needed != (size_t)memory_needed || memory_needed > (4ULL << 32)) {
    fprintf(stderr, "Image is too large (%d x %d).", rect.width, rect.height);
    return 0;
  }

  // Use a view for the sub-picture:
  if (!WebPPictureView(picture, rect.x_offset, rect.y_offset,
                       rect.width, rect.height, &sub_image)) {
    fprintf(stderr, "Sub-image %dx%d at position %d,%d is invalid!\n",
            rect.width, rect.height, rect.x_offset, rect.y_offset);
    return 0;
  }
  dst = sub_image.argb;

  tmp = (uint8_t*)WebPMalloc(rect.width * sizeof(*tmp));
  if (tmp == NULL) goto End;

  if (image_desc->Interlace) {  // Interlaced image.
    // We need 4 passes, with the following offsets and jumps.
    const int interlace_offsets[] = { 0, 4, 2, 1 };
    const int interlace_jumps[]   = { 8, 8, 4, 2 };
    int pass;
    for (pass = 0; pass < 4; ++pass) {
      const size_t stride = (size_t)sub_image.argb_stride;
      int y = interlace_offsets[pass];
      uint32_t* row = dst + y * stride;
      const size_t jump = interlace_jumps[pass] * stride;
      for (; y < rect.height; y += interlace_jumps[pass], row += jump) {
        if (DGifGetLine(gif, tmp, rect.width) == GIF_ERROR) goto End;
        if (!Remap(gif, tmp, rect.width, transparent_index, row)) goto End;
      }
    }
  } else {  // Non-interlaced image.
    int y;
    uint32_t* ptr = dst;
    for (y = 0; y < rect.height; ++y, ptr += sub_image.argb_stride) {
      if (DGifGetLine(gif, tmp, rect.width) == GIF_ERROR) goto End;
      if (!Remap(gif, tmp, rect.width, transparent_index, ptr)) goto End;
    }
  }
  ok = 1;

 End:
  if (!ok) picture->error_code = sub_image.error_code;
  WebPPictureFree(&sub_image);
  WebPFree(tmp);
  return ok;
}

int GIFReadLoopCount(GifFileType* const gif, GifByteType** const buf,
                     int* const loop_count) {
  assert(!memcmp(*buf + 1, "NETSCAPE2.0", 11) ||
         !memcmp(*buf + 1, "ANIMEXTS1.0", 11));
  if (DGifGetExtensionNext(gif, buf) == GIF_ERROR) {
    return 0;
  }
  if (*buf == NULL) {
    return 0;  // Loop count sub-block missing.
  }
  if ((*buf)[0] < 3 || (*buf)[1] != 1) {
    return 0;   // wrong size/marker
  }
  *loop_count = (*buf)[2] | ((*buf)[3] << 8);
  return 1;
}

int GIFReadMetadata(GifFileType* const gif, GifByteType** const buf,
                    WebPData* const metadata) {
  const int is_xmp = !memcmp(*buf + 1, "XMP DataXMP", 11);
  const int is_icc = !memcmp(*buf + 1, "ICCRGBG1012", 11);
  assert(is_xmp || is_icc);
  (void)is_icc;  // silence unused warning.
  // Construct metadata from sub-blocks.
  // Usual case (including ICC profile): In each sub-block, the
  // first byte specifies its size in bytes (0 to 255) and the
  // rest of the bytes contain the data.
  // Special case for XMP data: In each sub-block, the first byte
  // is also part of the XMP payload. XMP in GIF also has a 257
  // byte padding data. See the XMP specification for details.
  while (1) {
    WebPData subblock;
    const uint8_t* tmp;
    if (DGifGetExtensionNext(gif, buf) == GIF_ERROR) {
      return 0;
    }
    if (*buf == NULL) break;  // Finished.
    subblock.size = is_xmp ? (*buf)[0] + 1 : (*buf)[0];
    assert(subblock.size > 0);
    subblock.bytes = is_xmp ? *buf : *buf + 1;
    // Note: We store returned value in 'tmp' first, to avoid
    // leaking old memory in metadata->bytes on error.
    tmp = (uint8_t*)realloc((void*)metadata->bytes,
                            metadata->size + subblock.size);
    if (tmp == NULL) {
      return 0;
    }
    memcpy((void*)(tmp + metadata->size),
           subblock.bytes, subblock.size);
    metadata->bytes = tmp;
    metadata->size += subblock.size;
  }
  if (is_xmp) {
    // XMP padding data is 0x01, 0xff, 0xfe ... 0x01, 0x00.
    const size_t xmp_pading_size = 257;
    if (metadata->size > xmp_pading_size) {
      metadata->size -= xmp_pading_size;
    }
  }
  return 1;
}

static void ClearRectangle(WebPPicture* const picture,
                           int left, int top, int width, int height) {
  int i, j;
  const size_t stride = picture->argb_stride;
  uint32_t* dst = picture->argb + top * stride + left;
  for (j = 0; j < height; ++j, dst += stride) {
    for (i = 0; i < width; ++i) dst[i] = GIF_TRANSPARENT_COLOR;
  }
}

void GIFClearPic(WebPPicture* const pic, const GIFFrameRect* const rect) {
  if (rect != NULL) {
    ClearRectangle(pic, rect->x_offset, rect->y_offset,
                   rect->width, rect->height);
  } else {
    ClearRectangle(pic, 0, 0, pic->width, pic->height);
  }
}

void GIFCopyPixels(const WebPPicture* const src, WebPPicture* const dst) {
  WebPCopyPixels(src, dst);
}

void GIFDisposeFrame(GIFDisposeMethod dispose, const GIFFrameRect* const rect,
                     const WebPPicture* const prev_canvas,
                     WebPPicture* const curr_canvas) {
  assert(rect != NULL);
  if (dispose == GIF_DISPOSE_BACKGROUND) {
    GIFClearPic(curr_canvas, rect);
  } else if (dispose == GIF_DISPOSE_RESTORE_PREVIOUS) {
    const size_t src_stride = prev_canvas->argb_stride;
    const uint32_t* const src = prev_canvas->argb + rect->x_offset
                              + rect->y_offset * src_stride;
    const size_t dst_stride = curr_canvas->argb_stride;
    uint32_t* const dst = curr_canvas->argb + rect->x_offset
                        + rect->y_offset * dst_stride;
    assert(prev_canvas != NULL);
    WebPCopyPlane((uint8_t*)src, (int)(4 * src_stride),
                  (uint8_t*)dst, (int)(4 * dst_stride),
                  4 * rect->width, rect->height);
  }
}

void GIFBlendFrames(const WebPPicture* const src,
                    const GIFFrameRect* const rect, WebPPicture* const dst) {
  int i, j;
  const size_t src_stride = src->argb_stride;
  const size_t dst_stride = dst->argb_stride;
  assert(src->width == dst->width && src->height == dst->height);
  for (j = rect->y_offset; j < rect->y_offset + rect->height; ++j) {
    for (i = rect->x_offset; i < rect->x_offset + rect->width; ++i) {
      const uint32_t src_pixel = src->argb[j * src_stride + i];
      const int src_alpha = src_pixel >> 24;
      if (src_alpha != 0) {
        dst->argb[j * dst_stride + i] = src_pixel;
      }
    }
  }
}

void GIFDisplayError(const GifFileType* const gif, int gif_error) {
  // libgif 4.2.0 has retired PrintGifError() and added GifErrorString().
#if LOCAL_GIF_PREREQ(4,2)
#if LOCAL_GIF_PREREQ(5,0)
  // Static string actually, hence the const char* cast.
  const char* error_str = (const char*)GifErrorString(
      (gif == NULL) ? gif_error : gif->Error);
#else
  const char* error_str = (const char*)GifErrorString();
  (void)gif;
#endif
  if (error_str == NULL) error_str = "Unknown error";
  fprintf(stderr, "GIFLib Error %d: %s\n", gif_error, error_str);
#else
  (void)gif;
  fprintf(stderr, "GIFLib Error %d: ", gif_error);
  PrintGifError();
  fprintf(stderr, "\n");
#endif
}

#else  // !WEBP_HAVE_GIF

static void ErrorGIFNotAvailable(void) {
  fprintf(stderr, "GIF support not compiled. Please install the libgif-dev "
          "package before building.\n");
}

void GIFGetBackgroundColor(const struct ColorMapObject* const color_map,
                           int bgcolor_index, int transparent_index,
                           uint32_t* const bgcolor) {
  (void)color_map;
  (void)bgcolor_index;
  (void)transparent_index;
  (void)bgcolor;
  ErrorGIFNotAvailable();
}

int GIFReadGraphicsExtension(const GifByteType* const data, int* const duration,
                             GIFDisposeMethod* const dispose,
                             int* const transparent_index) {
  (void)data;
  (void)duration;
  (void)dispose;
  (void)transparent_index;
  ErrorGIFNotAvailable();
  return 0;
}

int GIFReadFrame(struct GifFileType* const gif, int transparent_index,
                 GIFFrameRect* const gif_rect,
                 struct WebPPicture* const picture) {
  (void)gif;
  (void)transparent_index;
  (void)gif_rect;
  (void)picture;
  ErrorGIFNotAvailable();
  return 0;
}

int GIFReadLoopCount(struct GifFileType* const gif, GifByteType** const buf,
                     int* const loop_count) {
  (void)gif;
  (void)buf;
  (void)loop_count;
  ErrorGIFNotAvailable();
  return 0;
}

int GIFReadMetadata(struct GifFileType* const gif, GifByteType** const buf,
                    struct WebPData* const metadata) {
  (void)gif;
  (void)buf;
  (void)metadata;
  ErrorGIFNotAvailable();
  return 0;
}

void GIFDisposeFrame(GIFDisposeMethod dispose, const GIFFrameRect* const rect,
                     const struct WebPPicture* const prev_canvas,
                     struct WebPPicture* const curr_canvas) {
  (void)dispose;
  (void)rect;
  (void)prev_canvas;
  (void)curr_canvas;
  ErrorGIFNotAvailable();
}

void GIFBlendFrames(const struct WebPPicture* const src,
                    const GIFFrameRect* const rect,
                    struct WebPPicture* const dst) {
  (void)src;
  (void)rect;
  (void)dst;
  ErrorGIFNotAvailable();
}

void GIFDisplayError(const struct GifFileType* const gif, int gif_error) {
  (void)gif;
  (void)gif_error;
  ErrorGIFNotAvailable();
}

void GIFClearPic(struct WebPPicture* const pic,
                 const GIFFrameRect* const rect) {
  (void)pic;
  (void)rect;
  ErrorGIFNotAvailable();
}

void GIFCopyPixels(const struct WebPPicture* const src,
                   struct WebPPicture* const dst) {
  (void)src;
  (void)dst;
  ErrorGIFNotAvailable();
}

#endif  // WEBP_HAVE_GIF

// -----------------------------------------------------------------------------

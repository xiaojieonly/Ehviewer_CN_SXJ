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

#include "./anim_util.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#if defined(WEBP_HAVE_GIF)
#include <gif_lib.h>
#endif
#include "../src/webp/format_constants.h"
#include "../src/webp/decode.h"
#include "../src/webp/demux.h"
#include "../src/webp/types.h"
#include "../imageio/imageio_util.h"
#include "./gifdec.h"
#include "./unicode.h"
#include "./unicode_gif.h"

#if defined(_MSC_VER) && _MSC_VER < 1900
#define snprintf _snprintf
#endif

static const int kNumChannels = 4;

// -----------------------------------------------------------------------------
// Common utilities.

#if defined(WEBP_HAVE_GIF)
// Returns true if the frame covers the full canvas.
static int IsFullFrame(int width, int height,
                       int canvas_width, int canvas_height) {
  return (width == canvas_width && height == canvas_height);
}
#endif // WEBP_HAVE_GIF

static int CheckSizeForOverflow(uint64_t size) {
  return (size == (size_t)size);
}

static int AllocateFrames(AnimatedImage* const image, uint32_t num_frames) {
  uint32_t i;
  uint8_t* mem = NULL;
  DecodedFrame* frames = NULL;
  const uint64_t rgba_size =
      (uint64_t)image->canvas_width * kNumChannels * image->canvas_height;
  const uint64_t total_size = (uint64_t)num_frames * rgba_size * sizeof(*mem);
  const uint64_t total_frame_size = (uint64_t)num_frames * sizeof(*frames);
  if (!CheckSizeForOverflow(total_size) ||
      !CheckSizeForOverflow(total_frame_size)) {
    return 0;
  }
  mem = (uint8_t*)WebPMalloc((size_t)total_size);
  frames = (DecodedFrame*)WebPMalloc((size_t)total_frame_size);

  if (mem == NULL || frames == NULL) {
    WebPFree(mem);
    WebPFree(frames);
    return 0;
  }
  WebPFree(image->raw_mem);
  image->num_frames = num_frames;
  image->frames = frames;
  for (i = 0; i < num_frames; ++i) {
    frames[i].rgba = mem + i * rgba_size;
    frames[i].duration = 0;
    frames[i].is_key_frame = 0;
  }
  image->raw_mem = mem;
  return 1;
}

void ClearAnimatedImage(AnimatedImage* const image) {
  if (image != NULL) {
    WebPFree(image->raw_mem);
    WebPFree(image->frames);
    image->num_frames = 0;
    image->frames = NULL;
    image->raw_mem = NULL;
  }
}

#if defined(WEBP_HAVE_GIF)
// Clear the canvas to transparent.
static void ZeroFillCanvas(uint8_t* rgba,
                           uint32_t canvas_width, uint32_t canvas_height) {
  memset(rgba, 0, canvas_width * kNumChannels * canvas_height);
}

// Clear given frame rectangle to transparent.
static void ZeroFillFrameRect(uint8_t* rgba, int rgba_stride, int x_offset,
                              int y_offset, int width, int height) {
  int j;
  assert(width * kNumChannels <= rgba_stride);
  rgba += y_offset * rgba_stride + x_offset * kNumChannels;
  for (j = 0; j < height; ++j) {
    memset(rgba, 0, width * kNumChannels);
    rgba += rgba_stride;
  }
}

// Copy width * height pixels from 'src' to 'dst'.
static void CopyCanvas(const uint8_t* src, uint8_t* dst,
                       uint32_t width, uint32_t height) {
  assert(src != NULL && dst != NULL);
  memcpy(dst, src, width * kNumChannels * height);
}

// Copy pixels in the given rectangle from 'src' to 'dst' honoring the 'stride'.
static void CopyFrameRectangle(const uint8_t* src, uint8_t* dst, int stride,
                               int x_offset, int y_offset,
                               int width, int height) {
  int j;
  const int width_in_bytes = width * kNumChannels;
  const size_t offset = y_offset * stride + x_offset * kNumChannels;
  assert(width_in_bytes <= stride);
  src += offset;
  dst += offset;
  for (j = 0; j < height; ++j) {
    memcpy(dst, src, width_in_bytes);
    src += stride;
    dst += stride;
  }
}
#endif // WEBP_HAVE_GIF

// Canonicalize all transparent pixels to transparent black to aid comparison.
static void CleanupTransparentPixels(uint32_t* rgba,
                                     uint32_t width, uint32_t height) {
  const uint32_t* const rgba_end = rgba + width * height;
  while (rgba < rgba_end) {
    const uint8_t alpha = (*rgba >> 24) & 0xff;
    if (alpha == 0) {
      *rgba = 0;
    }
    ++rgba;
  }
}

// Dump frame to a PAM file. Returns true on success.
static int DumpFrame(const char filename[], const char dump_folder[],
                     uint32_t frame_num, const uint8_t rgba[],
                     int canvas_width, int canvas_height) {
  int ok = 0;
  size_t max_len;
  int y;
  const W_CHAR* base_name = NULL;
  W_CHAR* file_name = NULL;
  FILE* f = NULL;
  const char* row;

  if (dump_folder == NULL) dump_folder = (const char*)TO_W_CHAR(".");

  base_name = WSTRRCHR(filename, '/');
  base_name = (base_name == NULL) ? (const W_CHAR*)filename : base_name + 1;
  max_len = WSTRLEN(dump_folder) + 1 + WSTRLEN(base_name)
          + strlen("_frame_") + strlen(".pam") + 8;
  file_name = (W_CHAR*)WebPMalloc(max_len * sizeof(*file_name));
  if (file_name == NULL) goto End;

  if (WSNPRINTF(file_name, max_len, "%s/%s_frame_%d.pam",
                (const W_CHAR*)dump_folder, base_name, frame_num) < 0) {
    fprintf(stderr, "Error while generating file name\n");
    goto End;
  }

  f = WFOPEN(file_name, "wb");
  if (f == NULL) {
    WFPRINTF(stderr, "Error opening file for writing: %s\n", file_name);
    ok = 0;
    goto End;
  }
  if (fprintf(f, "P7\nWIDTH %d\nHEIGHT %d\n"
              "DEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n",
              canvas_width, canvas_height) < 0) {
    WFPRINTF(stderr, "Write error for file %s\n", file_name);
    goto End;
  }
  row = (const char*)rgba;
  for (y = 0; y < canvas_height; ++y) {
    if (fwrite(row, canvas_width * kNumChannels, 1, f) != 1) {
      WFPRINTF(stderr, "Error writing to file: %s\n", file_name);
      goto End;
    }
    row += canvas_width * kNumChannels;
  }
  ok = 1;
 End:
  if (f != NULL) fclose(f);
  WebPFree(file_name);
  return ok;
}

// -----------------------------------------------------------------------------
// WebP Decoding.

// Returns true if this is a valid WebP bitstream.
static int IsWebP(const WebPData* const webp_data) {
  return (WebPGetInfo(webp_data->bytes, webp_data->size, NULL, NULL) != 0);
}

// Read animated WebP bitstream 'webp_data' into 'AnimatedImage' struct.
static int ReadAnimatedWebP(const char filename[],
                            const WebPData* const webp_data,
                            AnimatedImage* const image, int dump_frames,
                            const char dump_folder[]) {
  int ok = 0;
  int dump_ok = 1;
  uint32_t frame_index = 0;
  int prev_frame_timestamp = 0;
  WebPAnimDecoder* dec;
  WebPAnimInfo anim_info;

  memset(image, 0, sizeof(*image));

  dec = WebPAnimDecoderNew(webp_data, NULL);
  if (dec == NULL) {
    WFPRINTF(stderr, "Error parsing image: %s\n", (const W_CHAR*)filename);
    goto End;
  }

  if (!WebPAnimDecoderGetInfo(dec, &anim_info)) {
    fprintf(stderr, "Error getting global info about the animation\n");
    goto End;
  }

  // Animation properties.
  image->canvas_width = anim_info.canvas_width;
  image->canvas_height = anim_info.canvas_height;
  image->loop_count = anim_info.loop_count;
  image->bgcolor = anim_info.bgcolor;

  // Allocate frames.
  if (!AllocateFrames(image, anim_info.frame_count)) goto End;

  // Decode frames.
  while (WebPAnimDecoderHasMoreFrames(dec)) {
    DecodedFrame* curr_frame;
    uint8_t* curr_rgba;
    uint8_t* frame_rgba;
    int timestamp;

    if (!WebPAnimDecoderGetNext(dec, &frame_rgba, &timestamp)) {
      fprintf(stderr, "Error decoding frame #%u\n", frame_index);
      goto End;
    }
    assert(frame_index < anim_info.frame_count);
    curr_frame = &image->frames[frame_index];
    curr_rgba = curr_frame->rgba;
    curr_frame->duration = timestamp - prev_frame_timestamp;
    curr_frame->is_key_frame = 0;  // Unused.
    memcpy(curr_rgba, frame_rgba,
           image->canvas_width * kNumChannels * image->canvas_height);

    // Needed only because we may want to compare with GIF later.
    CleanupTransparentPixels((uint32_t*)curr_rgba,
                             image->canvas_width, image->canvas_height);

    if (dump_frames && dump_ok) {
      dump_ok = DumpFrame(filename, dump_folder, frame_index, curr_rgba,
                          image->canvas_width, image->canvas_height);
      if (!dump_ok) {  // Print error once, but continue decode loop.
        fprintf(stderr, "Error dumping frames to %s\n", dump_folder);
      }
    }

    ++frame_index;
    prev_frame_timestamp = timestamp;
  }
  ok = dump_ok;
  if (ok) image->format = ANIM_WEBP;

 End:
  WebPAnimDecoderDelete(dec);
  return ok;
}

// -----------------------------------------------------------------------------
// GIF Decoding.

#if defined(WEBP_HAVE_GIF)

// Returns true if this is a valid GIF bitstream.
static int IsGIF(const WebPData* const data) {
  return data->size > GIF_STAMP_LEN &&
         (!memcmp(GIF_STAMP, data->bytes, GIF_STAMP_LEN) ||
          !memcmp(GIF87_STAMP, data->bytes, GIF_STAMP_LEN) ||
          !memcmp(GIF89_STAMP, data->bytes, GIF_STAMP_LEN));
}

// GIFLIB_MAJOR is only defined in libgif >= 4.2.0.
#if defined(GIFLIB_MAJOR) && defined(GIFLIB_MINOR)
# define LOCAL_GIF_VERSION ((GIFLIB_MAJOR << 8) | GIFLIB_MINOR)
# define LOCAL_GIF_PREREQ(maj, min) \
    (LOCAL_GIF_VERSION >= (((maj) << 8) | (min)))
#else
# define LOCAL_GIF_VERSION 0
# define LOCAL_GIF_PREREQ(maj, min) 0
#endif

#if !LOCAL_GIF_PREREQ(5, 0)

// Added in v5.0
typedef struct {
  int DisposalMode;
#define DISPOSAL_UNSPECIFIED      0       // No disposal specified
#define DISPOSE_DO_NOT            1       // Leave image in place
#define DISPOSE_BACKGROUND        2       // Set area to background color
#define DISPOSE_PREVIOUS          3       // Restore to previous content
  int UserInputFlag;       // User confirmation required before disposal
  int DelayTime;           // Pre-display delay in 0.01sec units
  int TransparentColor;    // Palette index for transparency, -1 if none
#define NO_TRANSPARENT_COLOR     -1
} GraphicsControlBlock;

static int DGifExtensionToGCB(const size_t GifExtensionLength,
                              const GifByteType* GifExtension,
                              GraphicsControlBlock* gcb) {
  if (GifExtensionLength != 4) {
    return GIF_ERROR;
  }
  gcb->DisposalMode = (GifExtension[0] >> 2) & 0x07;
  gcb->UserInputFlag = (GifExtension[0] & 0x02) != 0;
  gcb->DelayTime = GifExtension[1] | (GifExtension[2] << 8);
  if (GifExtension[0] & 0x01) {
    gcb->TransparentColor = (int)GifExtension[3];
  } else {
    gcb->TransparentColor = NO_TRANSPARENT_COLOR;
  }
  return GIF_OK;
}

static int DGifSavedExtensionToGCB(GifFileType* GifFile, int ImageIndex,
                                   GraphicsControlBlock* gcb) {
  int i;
  if (ImageIndex < 0 || ImageIndex > GifFile->ImageCount - 1) {
    return GIF_ERROR;
  }
  gcb->DisposalMode = DISPOSAL_UNSPECIFIED;
  gcb->UserInputFlag = 0;
  gcb->DelayTime = 0;
  gcb->TransparentColor = NO_TRANSPARENT_COLOR;

  for (i = 0; i < GifFile->SavedImages[ImageIndex].ExtensionBlockCount; i++) {
    ExtensionBlock* ep = &GifFile->SavedImages[ImageIndex].ExtensionBlocks[i];
    if (ep->Function == GRAPHICS_EXT_FUNC_CODE) {
      return DGifExtensionToGCB(
          ep->ByteCount, (const GifByteType*)ep->Bytes, gcb);
    }
  }
  return GIF_ERROR;
}

#define CONTINUE_EXT_FUNC_CODE 0x00

// Signature was changed in v5.0
#define DGifOpenFileName(a, b) DGifOpenFileName(a)

#endif  // !LOCAL_GIF_PREREQ(5, 0)

// Signature changed in v5.1
#if !LOCAL_GIF_PREREQ(5, 1)
#define DGifCloseFile(a, b) DGifCloseFile(a)
#endif

static int IsKeyFrameGIF(const GifImageDesc* prev_desc, int prev_dispose,
                         const DecodedFrame* const prev_frame,
                         int canvas_width, int canvas_height) {
  if (prev_frame == NULL) return 1;
  if (prev_dispose == DISPOSE_BACKGROUND) {
    if (IsFullFrame(prev_desc->Width, prev_desc->Height,
                    canvas_width, canvas_height)) {
      return 1;
    }
    if (prev_frame->is_key_frame) return 1;
  }
  return 0;
}

static int GetTransparentIndexGIF(GifFileType* gif) {
  GraphicsControlBlock first_gcb;
  memset(&first_gcb, 0, sizeof(first_gcb));
  DGifSavedExtensionToGCB(gif, 0, &first_gcb);
  return first_gcb.TransparentColor;
}

static uint32_t GetBackgroundColorGIF(GifFileType* gif) {
  const int transparent_index = GetTransparentIndexGIF(gif);
  const ColorMapObject* const color_map = gif->SColorMap;
  if (transparent_index != NO_TRANSPARENT_COLOR &&
      gif->SBackGroundColor == transparent_index) {
    return 0x00000000;  // Special case: transparent black.
  } else if (color_map == NULL || color_map->Colors == NULL
             || gif->SBackGroundColor >= color_map->ColorCount) {
    return 0xffffffff;  // Invalid: assume white.
  } else {
    const GifColorType color = color_map->Colors[gif->SBackGroundColor];
    return (0xffu << 24) |
           (color.Red << 16) |
           (color.Green << 8) |
           (color.Blue << 0);
  }
}

// Find appropriate app extension and get loop count from the next extension.
// We use Chrome's interpretation of the 'loop_count' semantics:
//   if not present -> loop once
//   if present and loop_count == 0, return 0 ('infinite').
//   if present and loop_count != 0, it's the number of *extra* loops
//     so we need to return loop_count + 1 as total loop number.
static uint32_t GetLoopCountGIF(const GifFileType* const gif) {
  int i;
  for (i = 0; i < gif->ImageCount; ++i) {
    const SavedImage* const image = &gif->SavedImages[i];
    int j;
    for (j = 0; (j + 1) < image->ExtensionBlockCount; ++j) {
      const ExtensionBlock* const eb1 = image->ExtensionBlocks + j;
      const ExtensionBlock* const eb2 = image->ExtensionBlocks + j + 1;
      const char* const signature = (const char*)eb1->Bytes;
      const int signature_is_ok =
          (eb1->Function == APPLICATION_EXT_FUNC_CODE) &&
          (eb1->ByteCount == 11) &&
          (!memcmp(signature, "NETSCAPE2.0", 11) ||
           !memcmp(signature, "ANIMEXTS1.0", 11));
      if (signature_is_ok &&
          eb2->Function == CONTINUE_EXT_FUNC_CODE && eb2->ByteCount >= 3 &&
          eb2->Bytes[0] == 1) {
        const uint32_t extra_loop = ((uint32_t)(eb2->Bytes[2]) << 8) +
                                    ((uint32_t)(eb2->Bytes[1]) << 0);
        return (extra_loop > 0) ? extra_loop + 1 : 0;
      }
    }
  }
  return 1;  // Default.
}

// Get duration of 'n'th frame in milliseconds.
static int GetFrameDurationGIF(GifFileType* gif, int n) {
  GraphicsControlBlock gcb;
  memset(&gcb, 0, sizeof(gcb));
  DGifSavedExtensionToGCB(gif, n, &gcb);
  return gcb.DelayTime * 10;
}

// Returns true if frame 'target' completely covers 'covered'.
static int CoversFrameGIF(const GifImageDesc* const target,
                          const GifImageDesc* const covered) {
  return target->Left <= covered->Left &&
         covered->Left + covered->Width <= target->Left + target->Width &&
         target->Top <= covered->Top &&
         covered->Top + covered->Height <= target->Top + target->Height;
}

static void RemapPixelsGIF(const uint8_t* const src,
                           const ColorMapObject* const cmap,
                           int transparent_color, int len, uint8_t* dst) {
  int i;
  for (i = 0; i < len; ++i) {
    if (src[i] != transparent_color) {
      // If a pixel in the current frame is transparent, we don't modify it, so
      // that we can see-through the corresponding pixel from an earlier frame.
      const GifColorType c = cmap->Colors[src[i]];
      dst[4 * i + 0] = c.Red;
      dst[4 * i + 1] = c.Green;
      dst[4 * i + 2] = c.Blue;
      dst[4 * i + 3] = 0xff;
    }
  }
}

static int ReadFrameGIF(const SavedImage* const gif_image,
                        const ColorMapObject* cmap, int transparent_color,
                        int out_stride, uint8_t* const dst) {
  const GifImageDesc* image_desc = &gif_image->ImageDesc;
  const uint8_t* in;
  uint8_t* out;
  int j;

  if (image_desc->ColorMap) cmap = image_desc->ColorMap;

  if (cmap == NULL || cmap->ColorCount != (1 << cmap->BitsPerPixel)) {
    fprintf(stderr, "Potentially corrupt color map.\n");
    return 0;
  }

  in = (const uint8_t*)gif_image->RasterBits;
  out = dst + image_desc->Top * out_stride + image_desc->Left * kNumChannels;

  for (j = 0; j < image_desc->Height; ++j) {
    RemapPixelsGIF(in, cmap, transparent_color, image_desc->Width, out);
    in += image_desc->Width;
    out += out_stride;
  }
  return 1;
}

// Read animated GIF bitstream from 'filename' into 'AnimatedImage' struct.
static int ReadAnimatedGIF(const char filename[], AnimatedImage* const image,
                           int dump_frames, const char dump_folder[]) {
  uint32_t frame_count;
  uint32_t canvas_width, canvas_height;
  uint32_t i;
  int gif_error;
  GifFileType* gif;

  gif = DGifOpenFileUnicode((const W_CHAR*)filename, NULL);
  if (gif == NULL) {
    WFPRINTF(stderr, "Could not read file: %s.\n", (const W_CHAR*)filename);
    return 0;
  }

  gif_error = DGifSlurp(gif);
  if (gif_error != GIF_OK) {
    WFPRINTF(stderr, "Could not parse image: %s.\n", (const W_CHAR*)filename);
    GIFDisplayError(gif, gif_error);
    DGifCloseFile(gif, NULL);
    return 0;
  }

  // Animation properties.
  image->canvas_width = (uint32_t)gif->SWidth;
  image->canvas_height = (uint32_t)gif->SHeight;
  if (image->canvas_width > MAX_CANVAS_SIZE ||
      image->canvas_height > MAX_CANVAS_SIZE) {
    fprintf(stderr, "Invalid canvas dimension: %d x %d\n",
            image->canvas_width, image->canvas_height);
    DGifCloseFile(gif, NULL);
    return 0;
  }
  image->loop_count = GetLoopCountGIF(gif);
  image->bgcolor = GetBackgroundColorGIF(gif);

  frame_count = (uint32_t)gif->ImageCount;
  if (frame_count == 0) {
    DGifCloseFile(gif, NULL);
    return 0;
  }

  if (image->canvas_width == 0 || image->canvas_height == 0) {
    image->canvas_width = gif->SavedImages[0].ImageDesc.Width;
    image->canvas_height = gif->SavedImages[0].ImageDesc.Height;
    gif->SavedImages[0].ImageDesc.Left = 0;
    gif->SavedImages[0].ImageDesc.Top = 0;
    if (image->canvas_width == 0 || image->canvas_height == 0) {
      fprintf(stderr, "Invalid canvas size in GIF.\n");
      DGifCloseFile(gif, NULL);
      return 0;
    }
  }
  // Allocate frames.
  if (!AllocateFrames(image, frame_count)) {
    DGifCloseFile(gif, NULL);
    return 0;
  }

  canvas_width = image->canvas_width;
  canvas_height = image->canvas_height;

  // Decode and reconstruct frames.
  for (i = 0; i < frame_count; ++i) {
    const int canvas_width_in_bytes = canvas_width * kNumChannels;
    const SavedImage* const curr_gif_image = &gif->SavedImages[i];
    GraphicsControlBlock curr_gcb;
    DecodedFrame* curr_frame;
    uint8_t* curr_rgba;

    memset(&curr_gcb, 0, sizeof(curr_gcb));
    DGifSavedExtensionToGCB(gif, i, &curr_gcb);

    curr_frame = &image->frames[i];
    curr_rgba = curr_frame->rgba;
    curr_frame->duration = GetFrameDurationGIF(gif, i);
    // Force frames with a small or no duration to 100ms to be consistent
    // with web browsers and other transcoding tools (like gif2webp itself).
    if (curr_frame->duration <= 10) curr_frame->duration = 100;

    if (i == 0) {  // Initialize as transparent.
      curr_frame->is_key_frame = 1;
      ZeroFillCanvas(curr_rgba, canvas_width, canvas_height);
    } else {
      DecodedFrame* const prev_frame = &image->frames[i - 1];
      const GifImageDesc* const prev_desc = &gif->SavedImages[i - 1].ImageDesc;
      GraphicsControlBlock prev_gcb;
      memset(&prev_gcb, 0, sizeof(prev_gcb));
      DGifSavedExtensionToGCB(gif, i - 1, &prev_gcb);

      curr_frame->is_key_frame =
          IsKeyFrameGIF(prev_desc, prev_gcb.DisposalMode, prev_frame,
                        canvas_width, canvas_height);

      if (curr_frame->is_key_frame) {  // Initialize as transparent.
        ZeroFillCanvas(curr_rgba, canvas_width, canvas_height);
      } else {
        int prev_frame_disposed, curr_frame_opaque;
        int prev_frame_completely_covered;
        // Initialize with previous canvas.
        uint8_t* const prev_rgba = image->frames[i - 1].rgba;
        CopyCanvas(prev_rgba, curr_rgba, canvas_width, canvas_height);

        // Dispose previous frame rectangle.
        prev_frame_disposed =
            (prev_gcb.DisposalMode == DISPOSE_BACKGROUND ||
             prev_gcb.DisposalMode == DISPOSE_PREVIOUS);
        curr_frame_opaque =
            (curr_gcb.TransparentColor == NO_TRANSPARENT_COLOR);
        prev_frame_completely_covered =
            curr_frame_opaque &&
            CoversFrameGIF(&curr_gif_image->ImageDesc, prev_desc);

        if (prev_frame_disposed && !prev_frame_completely_covered) {
          switch (prev_gcb.DisposalMode) {
            case DISPOSE_BACKGROUND: {
              ZeroFillFrameRect(curr_rgba, canvas_width_in_bytes,
                                prev_desc->Left, prev_desc->Top,
                                prev_desc->Width, prev_desc->Height);
              break;
            }
            case DISPOSE_PREVIOUS: {
              int src_frame_num = i - 2;
              while (src_frame_num >= 0) {
                GraphicsControlBlock src_frame_gcb;
                memset(&src_frame_gcb, 0, sizeof(src_frame_gcb));
                DGifSavedExtensionToGCB(gif, src_frame_num, &src_frame_gcb);
                if (src_frame_gcb.DisposalMode != DISPOSE_PREVIOUS) break;
                --src_frame_num;
              }
              if (src_frame_num >= 0) {
                // Restore pixels inside previous frame rectangle to
                // corresponding pixels in source canvas.
                uint8_t* const src_frame_rgba =
                    image->frames[src_frame_num].rgba;
                CopyFrameRectangle(src_frame_rgba, curr_rgba,
                                   canvas_width_in_bytes,
                                   prev_desc->Left, prev_desc->Top,
                                   prev_desc->Width, prev_desc->Height);
              } else {
                // Source canvas doesn't exist. So clear previous frame
                // rectangle to background.
                ZeroFillFrameRect(curr_rgba, canvas_width_in_bytes,
                                  prev_desc->Left, prev_desc->Top,
                                  prev_desc->Width, prev_desc->Height);
              }
              break;
            }
            default:
              break;  // Nothing to do.
          }
        }
      }
    }

    // Decode current frame.
    if (!ReadFrameGIF(curr_gif_image, gif->SColorMap, curr_gcb.TransparentColor,
                      canvas_width_in_bytes, curr_rgba)) {
      DGifCloseFile(gif, NULL);
      return 0;
    }

    if (dump_frames) {
      if (!DumpFrame(filename, dump_folder, i, curr_rgba,
                     canvas_width, canvas_height)) {
        DGifCloseFile(gif, NULL);
        return 0;
      }
    }
  }
  image->format = ANIM_GIF;
  DGifCloseFile(gif, NULL);
  return 1;
}

#else

static int IsGIF(const WebPData* const data) {
  (void)data;
  return 0;
}

static int ReadAnimatedGIF(const char filename[], AnimatedImage* const image,
                           int dump_frames, const char dump_folder[]) {
  (void)filename;
  (void)image;
  (void)dump_frames;
  (void)dump_folder;
  fprintf(stderr, "GIF support not compiled. Please install the libgif-dev "
          "package before building.\n");
  return 0;
}

#endif  // WEBP_HAVE_GIF

// -----------------------------------------------------------------------------

int ReadAnimatedImage(const char filename[], AnimatedImage* const image,
                      int dump_frames, const char dump_folder[]) {
  int ok = 0;
  WebPData webp_data;

  WebPDataInit(&webp_data);
  memset(image, 0, sizeof(*image));

  if (!ImgIoUtilReadFile(filename, &webp_data.bytes, &webp_data.size)) {
    WFPRINTF(stderr, "Error reading file: %s\n", (const W_CHAR*)filename);
    return 0;
  }

  if (IsWebP(&webp_data)) {
    ok = ReadAnimatedWebP(filename, &webp_data, image, dump_frames,
                          dump_folder);
  } else if (IsGIF(&webp_data)) {
    ok = ReadAnimatedGIF(filename, image, dump_frames, dump_folder);
  } else {
    WFPRINTF(stderr,
             "Unknown file type: %s. Supported file types are WebP and GIF\n",
             (const W_CHAR*)filename);
    ok = 0;
  }
  if (!ok) ClearAnimatedImage(image);
  WebPDataClear(&webp_data);
  return ok;
}

static void Accumulate(double v1, double v2, double* const max_diff,
                       double* const sse) {
  const double diff = fabs(v1 - v2);
  if (diff > *max_diff) *max_diff = diff;
  *sse += diff * diff;
}

void GetDiffAndPSNR(const uint8_t rgba1[], const uint8_t rgba2[],
                    uint32_t width, uint32_t height, int premultiply,
                    int* const max_diff, double* const psnr) {
  const uint32_t stride = width * kNumChannels;
  const int kAlphaChannel = kNumChannels - 1;
  double f_max_diff = 0.;
  double sse = 0.;
  uint32_t x, y;
  for (y = 0; y < height; ++y) {
    for (x = 0; x < stride; x += kNumChannels) {
      int k;
      const size_t offset = (size_t)y * stride + x;
      const int alpha1 = rgba1[offset + kAlphaChannel];
      const int alpha2 = rgba2[offset + kAlphaChannel];
      Accumulate(alpha1, alpha2, &f_max_diff, &sse);
      if (!premultiply) {
        for (k = 0; k < kAlphaChannel; ++k) {
          Accumulate(rgba1[offset + k], rgba2[offset + k], &f_max_diff, &sse);
        }
      } else {
        // premultiply R/G/B channels with alpha value
        for (k = 0; k < kAlphaChannel; ++k) {
          Accumulate(rgba1[offset + k] * alpha1 / 255.,
                     rgba2[offset + k] * alpha2 / 255.,
                     &f_max_diff, &sse);
        }
      }
    }
  }
  *max_diff = (int)f_max_diff;
  if (*max_diff == 0) {
    *psnr = 99.;  // PSNR when images are identical.
  } else {
    sse /= stride * height;
    *psnr = 4.3429448 * log(255. * 255. / sse);
  }
}

void GetAnimatedImageVersions(int* const decoder_version,
                              int* const demux_version) {
  *decoder_version = WebPGetDecoderVersion();
  *demux_version = WebPGetDemuxVersion();
}

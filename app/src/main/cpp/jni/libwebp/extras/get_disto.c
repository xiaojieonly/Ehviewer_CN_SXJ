// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Simple tool to load two webp/png/jpg/tiff files and compute PSNR/SSIM.
// This is mostly a wrapper around WebPPictureDistortion().
//
/*
 gcc -o get_disto get_disto.c -O3 -I../ -L../examples -L../imageio \
    -lexample_util -limageio_util -limagedec -lwebp -L/opt/local/lib \
    -lpng -lz -ljpeg -ltiff -lm -lpthread
*/
//
// Author: Skal (pascal.massimino@gmail.com)

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "webp/encode.h"
#include "imageio/image_dec.h"
#include "imageio/imageio_util.h"
#include "../examples/unicode.h"

static size_t ReadPicture(const char* const filename, WebPPicture* const pic,
                          int keep_alpha) {
  const uint8_t* data = NULL;
  size_t data_size = 0;
  WebPImageReader reader = NULL;
  int ok = ImgIoUtilReadFile(filename, &data, &data_size);
  if (!ok) goto End;

  pic->use_argb = 1;  // force ARGB

#ifdef HAVE_WINCODEC_H
  // Try to decode the file using WIC falling back to the other readers for
  // e.g., WebP.
  ok = ReadPictureWithWIC(filename, pic, keep_alpha, NULL);
  if (ok) goto End;
#endif
  reader = WebPGuessImageReader(data, data_size);
  ok = reader(data, data_size, pic, keep_alpha, NULL);

 End:
  if (!ok) {
    WFPRINTF(stderr, "Error! Could not process file %s\n",
             (const W_CHAR*)filename);
  }
  free((void*)data);
  return ok ? data_size : 0;
}

static void RescalePlane(uint8_t* plane, int width, int height,
                         int x_stride, int y_stride, int max) {
  const uint32_t factor = (max > 0) ? (255u << 16) / max : 0;
  int x, y;
  for (y = 0; y < height; ++y) {
    uint8_t* const ptr = plane + y * y_stride;
    for (x = 0; x < width * x_stride; x += x_stride) {
      const uint32_t diff = (ptr[x] * factor + (1 << 15)) >> 16;
      ptr[x] = diff;
    }
  }
}

// Return the max absolute difference.
static int DiffScaleChannel(uint8_t* src1, int stride1,
                            const uint8_t* src2, int stride2,
                            int x_stride, int w, int h, int do_scaling) {
  int x, y;
  int max = 0;
  for (y = 0; y < h; ++y) {
    uint8_t* const ptr1 = src1 + y * stride1;
    const uint8_t* const ptr2 = src2 + y * stride2;
    for (x = 0; x < w * x_stride; x += x_stride) {
      const int diff = abs(ptr1[x] - ptr2[x]);
      if (diff > max) max = diff;
      ptr1[x] = diff;
    }
  }

  if (do_scaling) RescalePlane(src1, w, h, x_stride, stride1, max);
  return max;
}

//------------------------------------------------------------------------------
// SSIM calculation. We re-implement these functions here, out of dsp/, to avoid
// breaking the library's hidden visibility. This code duplication avoids the
// bigger annoyance of having to open up internal details of libdsp...

#define SSIM_KERNEL 3   // total size of the kernel: 2 * SSIM_KERNEL + 1

// struct for accumulating statistical moments
typedef struct {
  uint32_t w;              // sum(w_i) : sum of weights
  uint32_t xm, ym;         // sum(w_i * x_i), sum(w_i * y_i)
  uint32_t xxm, xym, yym;  // sum(w_i * x_i * x_i), etc.
} DistoStats;

// hat-shaped filter. Sum of coefficients is equal to 16.
static const uint32_t kWeight[2 * SSIM_KERNEL + 1] = { 1, 2, 3, 4, 3, 2, 1 };

static WEBP_INLINE double SSIMCalculation(const DistoStats* const stats) {
  const uint32_t N = stats->w;
  const uint32_t w2 =  N * N;
  const uint32_t C1 = 20 * w2;
  const uint32_t C2 = 60 * w2;
  const uint32_t C3 = 8 * 8 * w2;   // 'dark' limit ~= 6
  const uint64_t xmxm = (uint64_t)stats->xm * stats->xm;
  const uint64_t ymym = (uint64_t)stats->ym * stats->ym;
  if (xmxm + ymym >= C3) {
    const int64_t xmym = (int64_t)stats->xm * stats->ym;
    const int64_t sxy = (int64_t)stats->xym * N - xmym;    // can be negative
    const uint64_t sxx = (uint64_t)stats->xxm * N - xmxm;
    const uint64_t syy = (uint64_t)stats->yym * N - ymym;
    // we descale by 8 to prevent overflow during the fnum/fden multiply.
    const uint64_t num_S = (2 * (uint64_t)(sxy < 0 ? 0 : sxy) + C2) >> 8;
    const uint64_t den_S = (sxx + syy + C2) >> 8;
    const uint64_t fnum = (2 * xmym + C1) * num_S;
    const uint64_t fden = (xmxm + ymym + C1) * den_S;
    const double r = (double)fnum / fden;
    assert(r >= 0. && r <= 1.0);
    return r;
  }
  return 1.;   // area is too dark to contribute meaningfully
}

static double SSIMGetClipped(const uint8_t* src1, int stride1,
                             const uint8_t* src2, int stride2,
                             int xo, int yo, int W, int H) {
  DistoStats stats = { 0, 0, 0, 0, 0, 0 };
  const int ymin = (yo - SSIM_KERNEL < 0) ? 0 : yo - SSIM_KERNEL;
  const int ymax = (yo + SSIM_KERNEL > H - 1) ? H - 1 : yo + SSIM_KERNEL;
  const int xmin = (xo - SSIM_KERNEL < 0) ? 0 : xo - SSIM_KERNEL;
  const int xmax = (xo + SSIM_KERNEL > W - 1) ? W - 1 : xo + SSIM_KERNEL;
  int x, y;
  src1 += ymin * stride1;
  src2 += ymin * stride2;
  for (y = ymin; y <= ymax; ++y, src1 += stride1, src2 += stride2) {
    for (x = xmin; x <= xmax; ++x) {
      const uint32_t w = kWeight[SSIM_KERNEL + x - xo]
                       * kWeight[SSIM_KERNEL + y - yo];
      const uint32_t s1 = src1[x];
      const uint32_t s2 = src2[x];
      stats.w   += w;
      stats.xm  += w * s1;
      stats.ym  += w * s2;
      stats.xxm += w * s1 * s1;
      stats.xym += w * s1 * s2;
      stats.yym += w * s2 * s2;
    }
  }
  return SSIMCalculation(&stats);
}

// Compute SSIM-score map. Return -1 in case of error, max diff otherwise.
static int SSIMScaleChannel(uint8_t* src1, int stride1,
                            const uint8_t* src2, int stride2,
                            int x_stride, int w, int h, int do_scaling) {
  int x, y;
  int max = 0;
  uint8_t* const plane1 = (uint8_t*)malloc(2 * w * h * sizeof(*plane1));
  uint8_t* const plane2 = plane1 + w * h;
  if (plane1 == NULL) return -1;

  // extract plane
  for (y = 0; y < h; ++y) {
    for (x = 0; x < w; ++x) {
      plane1[x + y * w] = src1[x * x_stride + y * stride1];
      plane2[x + y * w] = src2[x * x_stride + y * stride2];
    }
  }
  for (y = 0; y < h; ++y) {
    for (x = 0; x < w; ++x) {
      const double ssim = SSIMGetClipped(plane1, w, plane2, w, x, y, w, h);
      int diff = (int)(255 * (1. - ssim));
      if (diff < 0) {
        diff = 0;
      } else if (diff > max) {
        max = diff;
      }
      src1[x * x_stride + y * stride1] = (diff > 255) ? 255u : (uint8_t)diff;
    }
  }
  free(plane1);

  if (do_scaling) RescalePlane(src1, w, h, x_stride, stride1, max);
  return max;
}

// Convert an argb picture to luminance.
static void ConvertToGray(WebPPicture* const pic) {
  int x, y;
  assert(pic != NULL);
  assert(pic->use_argb);
  for (y = 0; y < pic->height; ++y) {
    uint32_t* const row = &pic->argb[y * pic->argb_stride];
    for (x = 0; x < pic->width; ++x) {
      const uint32_t argb = row[x];
      const uint32_t r = (argb >> 16) & 0xff;
      const uint32_t g = (argb >>  8) & 0xff;
      const uint32_t b = (argb >>  0) & 0xff;
      // We use BT.709 for converting to luminance.
      const uint32_t Y = (uint32_t)(0.2126 * r + 0.7152 * g + 0.0722 * b + .5);
      row[x] = (argb & 0xff000000u) | (Y * 0x010101u);
    }
  }
}

static void Help(void) {
  fprintf(stderr,
          "Usage: get_disto [-ssim][-psnr][-alpha] compressed.webp orig.webp\n"
          "  -ssim ..... print SSIM distortion\n"
          "  -psnr ..... print PSNR distortion (default)\n"
          "  -alpha .... preserve alpha plane\n"
          "  -h ........ this message\n"
          "  -o <file> . save the diff map as a WebP lossless file\n"
          "  -scale .... scale the difference map to fit [0..255] range\n"
          "  -gray ..... use grayscale for difference map (-scale)\n"
          "\nSupported input formats:\n  %s\n",
          WebPGetEnabledInputFileFormats());
}

// Returns EXIT_SUCCESS on success, EXIT_FAILURE on failure.
int main(int argc, const char* argv[]) {
  WebPPicture pic1, pic2;
  size_t size1 = 0, size2 = 0;
  int ret = EXIT_FAILURE;
  float disto[5];
  int type = 0;
  int c;
  int help = 0;
  int keep_alpha = 0;
  int scale = 0;
  int use_gray = 0;
  const char* name1 = NULL;
  const char* name2 = NULL;
  const char* output = NULL;

  INIT_WARGV(argc, argv);

  if (!WebPPictureInit(&pic1) || !WebPPictureInit(&pic2)) {
    fprintf(stderr, "Can't init pictures\n");
    FREE_WARGV_AND_RETURN(EXIT_FAILURE);
  }

  for (c = 1; c < argc; ++c) {
    if (!strcmp(argv[c], "-ssim")) {
      type = 1;
    } else if (!strcmp(argv[c], "-psnr")) {
      type = 0;
    } else if (!strcmp(argv[c], "-alpha")) {
      keep_alpha = 1;
    } else if (!strcmp(argv[c], "-scale")) {
      scale = 1;
    } else if (!strcmp(argv[c], "-gray")) {
      use_gray = 1;
    } else if (!strcmp(argv[c], "-h")) {
      help = 1;
      ret = EXIT_SUCCESS;
    } else if (!strcmp(argv[c], "-o")) {
      if (++c == argc) {
        fprintf(stderr, "missing file name after %s option.\n", argv[c - 1]);
        goto End;
      }
      output = (const char*)GET_WARGV(argv, c);
    } else if (name1 == NULL) {
      name1 = (const char*)GET_WARGV(argv, c);
    } else {
      name2 = (const char*)GET_WARGV(argv, c);
    }
  }
  if (help || name1 == NULL || name2 == NULL) {
    if (!help) {
      fprintf(stderr, "Error: missing arguments.\n");
    }
    Help();
    goto End;
  }
  size1 = ReadPicture(name1, &pic1, 1);
  size2 = ReadPicture(name2, &pic2, 1);
  if (size1 == 0 || size2 == 0) goto End;

  if (!keep_alpha) {
    WebPBlendAlpha(&pic1, 0x00000000);
    WebPBlendAlpha(&pic2, 0x00000000);
  }

  if (!WebPPictureDistortion(&pic1, &pic2, type, disto)) {
    fprintf(stderr, "Error while computing the distortion.\n");
    goto End;
  }
  printf("%u %.2f    %.2f %.2f %.2f %.2f [ %.2f bpp ]\n",
         (unsigned int)size1,
         disto[4], disto[0], disto[1], disto[2], disto[3],
         8.f * size1 / pic1.width / pic1.height);

  if (output != NULL) {
    uint8_t* data = NULL;
    size_t data_size = 0;
    if (pic1.use_argb != pic2.use_argb) {
      fprintf(stderr, "Pictures are not in the same argb format. "
                      "Can't save the difference map.\n");
      goto End;
    }
    if (pic1.use_argb) {
      int n;
      fprintf(stderr, "max differences per channel: ");
      for (n = 0; n < 3; ++n) {    // skip the alpha channel
        const int range = (type == 1) ?
          SSIMScaleChannel((uint8_t*)pic1.argb + n, pic1.argb_stride * 4,
                           (const uint8_t*)pic2.argb + n, pic2.argb_stride * 4,
                           4, pic1.width, pic1.height, scale) :
          DiffScaleChannel((uint8_t*)pic1.argb + n, pic1.argb_stride * 4,
                           (const uint8_t*)pic2.argb + n, pic2.argb_stride * 4,
                           4, pic1.width, pic1.height, scale);
        if (range < 0) fprintf(stderr, "\nError computing diff map\n");
        fprintf(stderr, "[%d]", range);
      }
      fprintf(stderr, "\n");
      if (use_gray) ConvertToGray(&pic1);
    } else {
      fprintf(stderr, "Can only compute the difference map in ARGB format.\n");
      goto End;
    }
#if !defined(WEBP_REDUCE_CSP)
    data_size = WebPEncodeLosslessBGRA((const uint8_t*)pic1.argb,
                                       pic1.width, pic1.height,
                                       pic1.argb_stride * 4,
                                       &data);
    if (data_size == 0) {
      fprintf(stderr, "Error during lossless encoding.\n");
      goto End;
    }
    ret = ImgIoUtilWriteFile(output, data, data_size) ? EXIT_SUCCESS
                                                      : EXIT_FAILURE;
    WebPFree(data);
    if (ret) goto End;
#else
    (void)data;
    (void)data_size;
    fprintf(stderr, "Cannot save the difference map. Please recompile "
                    "without the WEBP_REDUCE_CSP flag.\n");
    goto End;
#endif  // WEBP_REDUCE_CSP
  }
  ret = EXIT_SUCCESS;

 End:
  WebPPictureFree(&pic1);
  WebPPictureFree(&pic2);
  FREE_WARGV_AND_RETURN(ret);
}

// Copyright 2015 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Additional WebP utilities.
//

#include "extras/extras.h"

#include <assert.h>
#include <limits.h>
#include <string.h>

#include "extras/sharpyuv_risk_table.h"
#include "../sharpyuv/sharpyuv.h
#include "../src/dsp/dsp.h"
#include "../src/utils/utils.h"
#include "webp/format_constants.h"
#include "webp/types.h"

#define XTRA_MAJ_VERSION 1
#define XTRA_MIN_VERSION 4
#define XTRA_REV_VERSION 0

//------------------------------------------------------------------------------

int WebPGetExtrasVersion(void) {
  return (XTRA_MAJ_VERSION << 16) | (XTRA_MIN_VERSION << 8) | XTRA_REV_VERSION;
}

//------------------------------------------------------------------------------

int WebPImportGray(const uint8_t* gray_data, WebPPicture* pic) {
  int y, width, uv_width;
  if (pic == NULL || gray_data == NULL) return 0;
  pic->colorspace = WEBP_YUV420;
  if (!WebPPictureAlloc(pic)) return 0;
  width = pic->width;
  uv_width = (width + 1) >> 1;
  for (y = 0; y < pic->height; ++y) {
    memcpy(pic->y + y * pic->y_stride, gray_data, width);
    gray_data += width;    // <- we could use some 'data_stride' here if needed
    if ((y & 1) == 0) {
      memset(pic->u + (y >> 1) * pic->uv_stride, 128, uv_width);
      memset(pic->v + (y >> 1) * pic->uv_stride, 128, uv_width);
    }
  }
  return 1;
}

int WebPImportRGB565(const uint8_t* rgb565, WebPPicture* pic) {
  int x, y;
  uint32_t* dst;
  if (pic == NULL || rgb565 == NULL) return 0;
  pic->colorspace = WEBP_YUV420;
  pic->use_argb = 1;
  if (!WebPPictureAlloc(pic)) return 0;
  dst = pic->argb;
  for (y = 0; y < pic->height; ++y) {
    const int width = pic->width;
    for (x = 0; x < width; ++x) {
#if defined(WEBP_SWAP_16BIT_CSP) && (WEBP_SWAP_16BIT_CSP == 1)
      const uint32_t rg = rgb565[2 * x + 1];
      const uint32_t gb = rgb565[2 * x + 0];
#else
      const uint32_t rg = rgb565[2 * x + 0];
      const uint32_t gb = rgb565[2 * x + 1];
#endif
      uint32_t r = rg & 0xf8;
      uint32_t g = ((rg << 5) | (gb >> 3)) & 0xfc;
      uint32_t b = (gb << 5);
      // dithering
      r = r | (r >> 5);
      g = g | (g >> 6);
      b = b | (b >> 5);
      dst[x] = (0xffu << 24) | (r << 16) | (g << 8) | b;
    }
    rgb565 += 2 * width;
    dst += pic->argb_stride;
  }
  return 1;
}

int WebPImportRGB4444(const uint8_t* rgb4444, WebPPicture* pic) {
  int x, y;
  uint32_t* dst;
  if (pic == NULL || rgb4444 == NULL) return 0;
  pic->colorspace = WEBP_YUV420;
  pic->use_argb = 1;
  if (!WebPPictureAlloc(pic)) return 0;
  dst = pic->argb;
  for (y = 0; y < pic->height; ++y) {
    const int width = pic->width;
    for (x = 0; x < width; ++x) {
#if defined(WEBP_SWAP_16BIT_CSP) && (WEBP_SWAP_16BIT_CSP == 1)
      const uint32_t rg = rgb4444[2 * x + 1];
      const uint32_t ba = rgb4444[2 * x + 0];
#else
      const uint32_t rg = rgb4444[2 * x + 0];
      const uint32_t ba = rgb4444[2 * x + 1];
#endif
      uint32_t r = rg & 0xf0;
      uint32_t g = (rg << 4);
      uint32_t b = (ba & 0xf0);
      uint32_t a = (ba << 4);
      // dithering
      r = r | (r >> 4);
      g = g | (g >> 4);
      b = b | (b >> 4);
      a = a | (a >> 4);
      dst[x] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    rgb4444 += 2 * width;
    dst += pic->argb_stride;
  }
  return 1;
}

int WebPImportColorMappedARGB(const uint8_t* indexed, int indexed_stride,
                              const uint32_t palette[], int palette_size,
                              WebPPicture* pic) {
  int x, y;
  uint32_t* dst;
  // 256 as the input buffer is uint8_t.
  assert(MAX_PALETTE_SIZE <= 256);
  if (pic == NULL || indexed == NULL || indexed_stride < pic->width ||
      palette == NULL || palette_size > MAX_PALETTE_SIZE || palette_size <= 0) {
    return 0;
  }
  pic->use_argb = 1;
  if (!WebPPictureAlloc(pic)) return 0;
  dst = pic->argb;
  for (y = 0; y < pic->height; ++y) {
    for (x = 0; x < pic->width; ++x) {
      // Make sure we are within the palette.
      if (indexed[x] >= palette_size) {
        WebPPictureFree(pic);
        return 0;
      }
      dst[x] = palette[indexed[x]];
    }
    indexed += indexed_stride;
    dst += pic->argb_stride;
  }
  return 1;
}

//------------------------------------------------------------------------------

int WebPUnmultiplyARGB(WebPPicture* pic) {
  int y;
  uint32_t* dst;
  if (pic == NULL || pic->use_argb != 1 || pic->argb == NULL) return 0;
  WebPInitAlphaProcessing();
  dst = pic->argb;
  for (y = 0; y < pic->height; ++y) {
    WebPMultARGBRow(dst, pic->width, /*inverse=*/1);
    dst += pic->argb_stride;
  }
  return 1;
}

//------------------------------------------------------------------------------
// 420 risk metric

#define YUV_FIX 16  // fixed-point precision for RGB->YUV
static const int kYuvHalf = 1 << (YUV_FIX - 1);

// Maps a value in [0, (256 << YUV_FIX) - 1] to [0,
// precomputed_scores_table_sampling - 1]. It is important that the extremal
// values are preserved and 1:1 mapped:
//  ConvertValue(0) = 0
//  ConvertValue((256 << 16) - 1) = rgb_sampling_size - 1
static int SharpYuvConvertValueToSampledIdx(int v, int rgb_sampling_size) {
  v = (v + kYuvHalf) >> YUV_FIX;
  v = (v < 0) ? 0 : (v > 255) ? 255 : v;
  return (v * (rgb_sampling_size - 1)) / 255;
}

#undef YUV_FIX

// For each pixel, computes the index to look up that color in a precomputed
// risk score table where the YUV space is subsampled to a size of
// precomputed_scores_table_sampling^3 (see sharpyuv_risk_table.h)
static int SharpYuvConvertToYuvSharpnessIndex(
    int r, int g, int b, const SharpYuvConversionMatrix* matrix,
    int precomputed_scores_table_sampling) {
  const int y = SharpYuvConvertValueToSampledIdx(
      matrix->rgb_to_y[0] * r + matrix->rgb_to_y[1] * g +
          matrix->rgb_to_y[2] * b + matrix->rgb_to_y[3],
      precomputed_scores_table_sampling);
  const int u = SharpYuvConvertValueToSampledIdx(
      matrix->rgb_to_u[0] * r + matrix->rgb_to_u[1] * g +
          matrix->rgb_to_u[2] * b + matrix->rgb_to_u[3],
      precomputed_scores_table_sampling);
  const int v = SharpYuvConvertValueToSampledIdx(
      matrix->rgb_to_v[0] * r + matrix->rgb_to_v[1] * g +
          matrix->rgb_to_v[2] * b + matrix->rgb_to_v[3],
      precomputed_scores_table_sampling);
  return y + u * precomputed_scores_table_sampling +
         v * precomputed_scores_table_sampling *
             precomputed_scores_table_sampling;
}

static void SharpYuvRowToYuvSharpnessIndex(
    const uint8_t* r_ptr, const uint8_t* g_ptr, const uint8_t* b_ptr,
    int rgb_step, int rgb_bit_depth, int width, uint16_t* dst,
    const SharpYuvConversionMatrix* matrix,
    int precomputed_scores_table_sampling) {
  int i;
  assert(rgb_bit_depth == 8);
  (void)rgb_bit_depth;  // Unused for now.
  for (i = 0; i < width;
       ++i, r_ptr += rgb_step, g_ptr += rgb_step, b_ptr += rgb_step) {
    dst[i] =
        SharpYuvConvertToYuvSharpnessIndex(r_ptr[0], g_ptr[0], b_ptr[0], matrix,
                                           precomputed_scores_table_sampling);
  }
}

#define SAFE_ALLOC(W, H, T) ((T*)WebPSafeMalloc((uint64_t)(W) * (H), sizeof(T)))

static int DoEstimateRisk(const uint8_t* r_ptr, const uint8_t* g_ptr,
                          const uint8_t* b_ptr, int rgb_step, int rgb_stride,
                          int rgb_bit_depth, int width, int height,
                          const SharpYuvOptions* options,
                          const uint8_t precomputed_scores_table[],
                          int precomputed_scores_table_sampling,
                          float* score_out) {
  const int sampling3 = precomputed_scores_table_sampling *
                        precomputed_scores_table_sampling *
                        precomputed_scores_table_sampling;
  const int kNoiseLevel = 4;
  double total_score = 0;
  double count = 0;
  // Rows of indices in
  uint16_t* row1 = SAFE_ALLOC(width, 1, uint16_t);
  uint16_t* row2 = SAFE_ALLOC(width, 1, uint16_t);
  uint16_t* tmp;
  int i, j;

  if (row1 == NULL || row2 == NULL) {
    WebPFree(row1);
    WebPFree(row2);
    return 0;
  }

  // Convert the first row ahead.
  SharpYuvRowToYuvSharpnessIndex(r_ptr, g_ptr, b_ptr, rgb_step, rgb_bit_depth,
                                 width, row2, options->yuv_matrix,
                                 precomputed_scores_table_sampling);

  for (j = 1; j < height; ++j) {
    r_ptr += rgb_stride;
    g_ptr += rgb_stride;
    b_ptr += rgb_stride;
    // Swap row 1 and row 2.
    tmp = row1;
    row1 = row2;
    row2 = tmp;
    // Convert the row below.
    SharpYuvRowToYuvSharpnessIndex(r_ptr, g_ptr, b_ptr, rgb_step, rgb_bit_depth,
                                   width, row2, options->yuv_matrix,
                                   precomputed_scores_table_sampling);
    for (i = 0; i < width - 1; ++i) {
      const int idx0 = row1[i + 0];
      const int idx1 = row1[i + 1];
      const int idx2 = row2[i + 0];
      const int score = precomputed_scores_table[idx0 + sampling3 * idx1] +
                        precomputed_scores_table[idx0 + sampling3 * idx2] +
                        precomputed_scores_table[idx1 + sampling3 * idx2];
      if (score > kNoiseLevel) {
        total_score += score;
        count += 1.0;
      }
    }
  }
  if (count > 0.) total_score /= count;

  // If less than 1% of pixels were evaluated -> below noise level.
  if (100. * count / (width * height) < 1.) total_score = 0.;

  // Rescale to [0:100]
  total_score = (total_score > 25.) ? 100. : total_score * 100. / 25.;

  WebPFree(row1);
  WebPFree(row2);

  *score_out = (float)total_score;
  return 1;
}

#undef SAFE_ALLOC

int SharpYuvEstimate420Risk(const void* r_ptr, const void* g_ptr,
                            const void* b_ptr, int rgb_step, int rgb_stride,
                            int rgb_bit_depth, int width, int height,
                            const SharpYuvOptions* options, float* score) {
  if (width < 1 || height < 1 || width == INT_MAX || height == INT_MAX ||
      r_ptr == NULL || g_ptr == NULL || b_ptr == NULL || options == NULL ||
      score == NULL) {
    return 0;
  }
  if (rgb_bit_depth != 8) {
    return 0;
  }

  if (width <= 4 || height <= 4) {
    *score = 0.0f;  // too small, no real risk.
    return 1;
  }

  return DoEstimateRisk(
      (const uint8_t*)r_ptr, (const uint8_t*)g_ptr, (const uint8_t*)b_ptr,
      rgb_step, rgb_stride, rgb_bit_depth, width, height, options,
      kSharpYuvPrecomputedRisk, kSharpYuvPrecomputedRiskYuvSampling, score);
}

//------------------------------------------------------------------------------

// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  VP8EstimateQuality(): rough encoding quality estimate
//
// Author: Skal (pascal.massimino@gmail.com)

#include "extras/extras.h"
#include "webp/decode.h"

#include <math.h>

//------------------------------------------------------------------------------

#define INVALID_BIT_POS (1ull << 63)

// In most cases, we don't need to use a full arithmetic decoder, since
// all the header's bits are written using a uniform probability of 128.
// We can just parse the header as if it was bits (works in 99.999% cases).
static WEBP_INLINE uint32_t GetBit(const uint8_t* const data, size_t nb,
                                   uint64_t max_size, uint64_t* const bit_pos) {
  uint32_t val = 0;
  if (*bit_pos + nb <= 8 * max_size) {
    while (nb-- > 0) {
      const uint64_t p = (*bit_pos)++;
      const int bit = !!(data[p >> 3] & (128 >> ((p & 7))));
      val = (val << 1) | bit;
    }
  } else {
    *bit_pos = INVALID_BIT_POS;
  }
  return val;
}

#define GET_BIT(n) GetBit(data, (n), size, &bit_pos)
#define CONDITIONAL_SKIP(n) (GET_BIT(1) ? GET_BIT((n)) : 0)

int VP8EstimateQuality(const uint8_t* const data, size_t size) {
  size_t pos = 0;
  uint64_t bit_pos;
  uint64_t sig = 0x00;
  int ok = 0;
  int Q = -1;
  WebPBitstreamFeatures features;

  if (data == NULL) return -1;

  if (WebPGetFeatures(data, size, &features) != VP8_STATUS_OK) {
    return -1;   // invalid file
  }
  if (features.format == 2) return 101;  // lossless
  if (features.format == 0 || features.has_animation) return -1;   // mixed

  while (pos < size) {
    sig = (sig >> 8) | ((uint64_t)data[pos++] << 40);
    if ((sig >> 24) == 0x2a019dull) {
      ok = 1;
      break;
    }
  }
  if (!ok) return -1;
  if (pos + 4 > size) return -1;

  // Skip main Header
  // width  = (data[pos + 0] | (data[pos + 1] << 8)) & 0x3fff;
  // height = (data[pos + 2] | (data[pos + 3] << 8)) & 0x3fff;
  pos += 4;
  bit_pos = pos * 8;

  GET_BIT(2);  // colorspace + clamp type

  // Segment header
  if (GET_BIT(1)) {       // use_segment_
    int s;
    const int update_map = GET_BIT(1);
    if (GET_BIT(1)) {     // update data
      const int absolute_delta = GET_BIT(1);
      int q[4]  = { 0, 0, 0, 0 };
      for (s = 0; s < 4; ++s) {
        if (GET_BIT(1)) {
          q[s] = GET_BIT(7);
          if (GET_BIT(1)) q[s] = -q[s];   // sign
        }
      }
      if (absolute_delta) Q = q[0];  // just use the first segment's quantizer
      for (s = 0; s < 4; ++s) CONDITIONAL_SKIP(7);   //  filter strength
    }
    if (update_map) {
      for (s = 0; s < 3; ++s) CONDITIONAL_SKIP(8);
    }
  }
  // Filter header
  GET_BIT(1 + 6 + 3);     // simple + level + sharpness
  if (GET_BIT(1)) {       // use_lf_delta
    if (GET_BIT(1)) {     // update lf_delta?
      int n;
      for (n = 0; n < 4 + 4; ++n) CONDITIONAL_SKIP(6);
    }
  }
  // num partitions
  GET_BIT(2);

  // ParseQuant
  {
    const int base_q = GET_BIT(7);
    /* dqy1_dc = */ CONDITIONAL_SKIP(5);
    /* dqy2_dc = */ CONDITIONAL_SKIP(5);
    /* dqy2_ac = */ CONDITIONAL_SKIP(5);
    /* dquv_dc = */ CONDITIONAL_SKIP(5);
    /* dquv_ac = */ CONDITIONAL_SKIP(5);

    if (Q < 0) Q = base_q;
  }
  if (bit_pos == INVALID_BIT_POS) return -1;

  // base mapping
  Q = (127 - Q) * 100 / 127;
  // correction for power-law behavior in low range
  if (Q < 80) {
    Q = (int)(pow(Q / 80., 1. / 0.38) * 80);
  }
  return Q;
}

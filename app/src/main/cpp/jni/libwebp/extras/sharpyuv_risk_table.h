// Copyright 2023 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Precomputed data for 420 risk estimation.

#ifndef WEBP_EXTRAS_SHARPYUV_RISK_TABLE_H_
#define WEBP_EXTRAS_SHARPYUV_RISK_TABLE_H_

#include "../src/webp/types.h"

extern const int kSharpYuvPrecomputedRiskYuvSampling;
// Table of precomputed risk scores when chroma subsampling images with two
// given colors.
// Since precomputing values for all possible YUV colors would create a huge
// table, the YUV space (i.e. [0, 255]^3) is reduced to
// [0, kSharpYuvPrecomputedRiskYuvSampling-1]^3
// where 255 maps to kSharpYuvPrecomputedRiskYuvSampling-1.
// Table size: kSharpYuvPrecomputedRiskYuvSampling^6 bytes or 114 KiB
extern const uint8_t kSharpYuvPrecomputedRisk[];

#endif  // WEBP_EXTRAS_SHARPYUV_RISK_TABLE_H_

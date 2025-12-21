// Copyright 2018-2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

#ifndef WEBP_TESTS_FUZZER_FUZZ_UTILS_H_
#define WEBP_TESTS_FUZZER_FUZZ_UTILS_H_

#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "./img_alpha.h"
#include "./img_grid.h"
#include "./img_peak.h"
#include "../src/dsp/cpu.h"
#include "../src/webp/encode.h"
#include "fuzztest/fuzztest.h"

namespace fuzz_utils {

//------------------------------------------------------------------------------
// Arbitrary limits to prevent OOM, timeout, or slow execution.

// The decoded image size, and for animations additionally the canvas size.
// Enabling some sanitizers slow down runtime significantly.
// Use a very low threshold in this case to avoid timeouts.
#if defined(__SANITIZE_ADDRESS__)  // GCC
static const size_t kFuzzPxLimit = 1024 * 1024 / 10;
#elif !defined(__has_feature)  // Clang
static const size_t kFuzzPxLimit = 1024 * 1024;
#elif __has_feature(address_sanitizer) || __has_feature(memory_sanitizer)
static const size_t kFuzzPxLimit = 1024 * 1024 / 18;
#else
static const size_t kFuzzPxLimit = 1024 * 1024;
#endif

// Demuxed or decoded animation frames.
static const int kFuzzFrameLimit = 3;

// Reads and sums (up to) 128 spread-out bytes.
static WEBP_INLINE uint8_t FuzzHash(const uint8_t* const data, size_t size) {
  uint8_t value = 0;
  size_t incr = size / 128;
  if (!incr) incr = 1;
  for (size_t i = 0; i < size; i += incr) value += data[i];
  return value;
}

#ifdef __cplusplus
extern "C" VP8CPUInfo VP8GetCPUInfo;
#else
extern VP8CPUInfo VP8GetCPUInfo;
#endif

//------------------------------------------------------------------------------

constexpr const uint8_t* kImagesData[] = {kImgAlphaData, kImgGridData,
                                          kImgPeakData};
constexpr size_t kNumSourceImages =
    sizeof(kImagesData) / sizeof(kImagesData[0]);

WebPPicture GetSourcePicture(int image_index, bool use_argb);

static inline auto ArbitraryWebPConfig() {
  return fuzztest::Map(
      [](int lossless, int quality, int method, int image_hint, int segments,
         int sns_strength, int filter_strength, int filter_sharpness,
         int filter_type, int autofilter, int alpha_compression,
         int alpha_filtering, int alpha_quality, int pass, int preprocessing,
         int partitions, int partition_limit, int emulate_jpeg_size,
         int thread_level, int low_memory, int near_lossless, int exact,
         int use_delta_palette, int use_sharp_yuv) -> WebPConfig {
        WebPConfig config;
        if (!WebPConfigInit(&config)) abort();
        config.lossless = lossless;
        config.quality = quality;
        config.method = method;
        config.image_hint = (WebPImageHint)image_hint;
        config.segments = segments;
        config.sns_strength = sns_strength;
        config.filter_strength = filter_strength;
        config.filter_sharpness = filter_sharpness;
        config.filter_type = filter_type;
        config.autofilter = autofilter;
        config.alpha_compression = alpha_compression;
        config.alpha_filtering = alpha_filtering;
        config.alpha_quality = alpha_quality;
        config.pass = pass;
        config.show_compressed = 1;
        config.preprocessing = preprocessing;
        config.partitions = partitions;
        config.partition_limit = 10 * partition_limit;
        config.emulate_jpeg_size = emulate_jpeg_size;
        config.thread_level = thread_level;
        config.low_memory = low_memory;
        config.near_lossless = 20 * near_lossless;
        config.exact = exact;
        config.use_delta_palette = use_delta_palette;
        config.use_sharp_yuv = use_sharp_yuv;
        if (!WebPValidateConfig(&config)) abort();
        return config;
      },
      /*lossless=*/fuzztest::InRange<int>(0, 1),
      /*quality=*/fuzztest::InRange<int>(0, 100),
      /*method=*/fuzztest::InRange<int>(0, 6),
      /*image_hint=*/fuzztest::InRange<int>(0, WEBP_HINT_LAST - 1),
      /*segments=*/fuzztest::InRange<int>(1, 4),
      /*sns_strength=*/fuzztest::InRange<int>(0, 100),
      /*filter_strength=*/fuzztest::InRange<int>(0, 100),
      /*filter_sharpness=*/fuzztest::InRange<int>(0, 7),
      /*filter_type=*/fuzztest::InRange<int>(0, 1),
      /*autofilter=*/fuzztest::InRange<int>(0, 1),
      /*alpha_compression=*/fuzztest::InRange<int>(0, 1),
      /*alpha_filtering=*/fuzztest::InRange<int>(0, 2),
      /*alpha_quality=*/fuzztest::InRange<int>(0, 100),
      /*pass=*/fuzztest::InRange<int>(1, 10),
      /*preprocessing=*/fuzztest::InRange<int>(0, 2),
      /*partitions=*/fuzztest::InRange<int>(0, 3),
      /*partition_limit=*/fuzztest::InRange<int>(0, 10),
      /*emulate_jpeg_size=*/fuzztest::InRange<int>(0, 1),
      /*thread_level=*/fuzztest::InRange<int>(0, 1),
      /*low_memory=*/fuzztest::InRange<int>(0, 1),
      /*near_lossless=*/fuzztest::InRange<int>(0, 5),
      /*exact=*/fuzztest::InRange<int>(0, 1),
      /*use_delta_palette=*/fuzztest::InRange<int>(0, 1),
      /*use_sharp_yuv=*/fuzztest::InRange<int>(0, 1));
}

struct CropOrScaleParams {
  bool alter_input;
  bool crop_or_scale;
  int width_ratio;
  int height_ratio;
  int left_ratio;
  int top_ratio;
};

static inline auto ArbitraryCropOrScaleParams() {
  return fuzztest::Map(
      [](const std::optional<std::pair<int, int>>& width_height_ratio,
         const std::optional<std::pair<int, int>>& left_top_ratio)
          -> CropOrScaleParams {
        CropOrScaleParams params;
        params.alter_input = width_height_ratio.has_value();
        if (params.alter_input) {
          params.width_ratio = width_height_ratio->first;
          params.height_ratio = width_height_ratio->second;
          params.crop_or_scale = left_top_ratio.has_value();
          if (params.crop_or_scale) {
            params.left_ratio = left_top_ratio->first;
            params.top_ratio = left_top_ratio->second;
          }
        }
        return params;
      },
      fuzztest::OptionalOf(
          fuzztest::PairOf(fuzztest::InRange(1, 8), fuzztest::InRange(1, 8))),
      fuzztest::OptionalOf(
          fuzztest::PairOf(fuzztest::InRange(1, 8), fuzztest::InRange(1, 8))));
}

// Crops or scales a picture according to the given params.
int CropOrScale(WebPPicture* pic, const CropOrScaleParams& params);

// Imposes a level of optimization among one of the kMaxOptimizationIndex+1
// possible values: OnlyC, ForceSlowSSSE3, NoSSE41, NoAVX, default.
static constexpr uint32_t kMaxOptimizationIndex = 4;
void SetOptimization(VP8CPUInfo default_VP8GetCPUInfo, uint32_t index);

//------------------------------------------------------------------------------

// See https://developers.google.com/speed/webp/docs/riff_container.
static constexpr size_t kMaxWebPFileSize = (1ull << 32) - 2;  // 4 GiB - 2

std::vector<std::string> GetDictionaryFromFiles(
    const std::vector<std::string_view>& file_paths);

// Checks whether the binary blob containing a JPEG or WebP is too big for the
// fuzzer.
bool IsImageTooBig(const uint8_t* data, size_t size);

}  // namespace fuzz_utils

#endif  // WEBP_TESTS_FUZZER_FUZZ_UTILS_H_

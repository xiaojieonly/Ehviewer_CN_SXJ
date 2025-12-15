// Copyright 2018 Google Inc.
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

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <string_view>
#include <utility>
#include <vector>

#include "./fuzz_utils.h"
#include "../src/dsp/cpu.h"
#include "../src/webp/encode.h"
#include "../src/webp/mux.h"
#include "../src/webp/mux_types.h"

namespace {

const VP8CPUInfo default_VP8GetCPUInfo = fuzz_utils::VP8GetCPUInfo;

struct FrameConfig {
  int use_argb;
  int timestamp;
  WebPConfig webp_config;
  fuzz_utils::CropOrScaleParams crop_or_scale_params;
  int source_image_index;
};

auto ArbitraryKMinKMax() {
  return fuzztest::FlatMap(
      [](int kmax) {
        const int min_kmin = (kmax > 1) ? (kmax / 2) : 0;
        const int max_kmin = (kmax > 1) ? (kmax - 1) : 0;
        return fuzztest::PairOf(fuzztest::InRange(min_kmin, max_kmin),
                                fuzztest::Just(kmax));
      },
      fuzztest::InRange(0, 15));
}

int AddFrame(WebPAnimEncoder** const enc,
             const WebPAnimEncoderOptions& anim_config, int* const width,
             int* const height, int timestamp_ms,
             const FrameConfig& frame_config, const uint8_t data[], size_t size,
             uint32_t* const bit_pos) {
  if (enc == nullptr || width == nullptr || height == nullptr) {
    fprintf(stderr, "NULL parameters.\n");
    if (enc != nullptr) WebPAnimEncoderDelete(*enc);
    abort();
  }

  // Init the source picture.
  WebPPicture pic = fuzz_utils::GetSourcePicture(
      frame_config.source_image_index, frame_config.use_argb);

  // Crop and scale.
  if (*enc == nullptr) {  // First frame will set canvas width and height.
    if (!fuzz_utils::CropOrScale(&pic, frame_config.crop_or_scale_params)) {
      const WebPEncodingError error_code = pic.error_code;
      WebPPictureFree(&pic);
      if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY) return 0;
      fprintf(stderr, "ExtractAndCropOrScale failed. Error code: %d\n",
              error_code);
      abort();
    }
  } else {  // Other frames will be resized to the first frame's dimensions.
    if (!WebPPictureRescale(&pic, *width, *height)) {
      const WebPEncodingError error_code = pic.error_code;
      WebPAnimEncoderDelete(*enc);
      WebPPictureFree(&pic);
      if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY) return 0;
      fprintf(stderr,
              "WebPPictureRescale failed. Size: %d,%d. Error code: %d\n",
              *width, *height, error_code);
      abort();
    }
  }

  // Create encoder if it doesn't exist.
  if (*enc == nullptr) {
    *width = pic.width;
    *height = pic.height;
    *enc = WebPAnimEncoderNew(*width, *height, &anim_config);
    if (*enc == nullptr) {
      WebPPictureFree(&pic);
      return 0;
    }
  }

  // Create frame encoding config.
  WebPConfig config = frame_config.webp_config;
  // Skip slow settings on big images, it's likely to timeout.
  if (pic.width * pic.height > 32 * 32) {
    config.method = (config.method > 4) ? 4 : config.method;
    config.quality = (config.quality > 99.0f) ? 99.0f : config.quality;
    config.alpha_quality =
        (config.alpha_quality > 99) ? 99 : config.alpha_quality;
  }

  // Encode.
  if (!WebPAnimEncoderAdd(*enc, &pic, timestamp_ms, &config)) {
    const WebPEncodingError error_code = pic.error_code;
    WebPAnimEncoderDelete(*enc);
    WebPPictureFree(&pic);
    // Tolerate failures when running under the nallocfuzz engine as
    // WebPAnimEncoderAdd() may fail due to memory allocation errors outside of
    // the encoder; in muxer functions that return booleans for instance.
    if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY ||
        error_code == VP8_ENC_ERROR_BAD_WRITE ||
        getenv("NALLOC_FUZZ_VERSION") != nullptr) {
      return 0;
    }
    fprintf(stderr, "WebPEncode failed. Error code: %d\n", error_code);
    abort();
  }

  WebPPictureFree(&pic);
  return 1;
}

void AnimEncoderTest(std::string_view blob, bool minimize_size,
                     std::pair<int, int> kmin_kmax, bool allow_mixed,
                     const std::vector<FrameConfig>& frame_configs,
                     int optimization_index) {
  WebPAnimEncoder* enc = nullptr;
  int width = 0, height = 0, timestamp_ms = 0;
  uint32_t bit_pos = 0;
  const uint8_t* const data = reinterpret_cast<const uint8_t*>(blob.data());
  const size_t size = blob.size();

  fuzz_utils::SetOptimization(default_VP8GetCPUInfo, optimization_index);

  // Extract a configuration from the packed bits.
  WebPAnimEncoderOptions anim_config;
  if (!WebPAnimEncoderOptionsInit(&anim_config)) {
    fprintf(stderr, "WebPAnimEncoderOptionsInit failed.\n");
    abort();
  }
  anim_config.minimize_size = minimize_size;
  anim_config.kmin = kmin_kmax.first;
  anim_config.kmax = kmin_kmax.second;
  anim_config.allow_mixed = allow_mixed;
  anim_config.verbose = 0;

  // For each frame.
  for (const FrameConfig& frame_config : frame_configs) {
    if (!AddFrame(&enc, anim_config, &width, &height, timestamp_ms,
                  frame_config, data, size, &bit_pos)) {
      return;
    }

    timestamp_ms += frame_config.timestamp;
  }

  // Assemble.
  if (!WebPAnimEncoderAdd(enc, nullptr, timestamp_ms, nullptr)) {
    fprintf(stderr, "Last WebPAnimEncoderAdd failed: %s.\n",
            WebPAnimEncoderGetError(enc));
    WebPAnimEncoderDelete(enc);
    abort();
  }
  WebPData webp_data;
  WebPDataInit(&webp_data);
  // Tolerate failures when running under the nallocfuzz engine as allocations
  // during assembly may fail.
  if (!WebPAnimEncoderAssemble(enc, &webp_data) &&
      getenv("NALLOC_FUZZ_VERSION") == nullptr) {
    fprintf(stderr, "WebPAnimEncoderAssemble failed: %s.\n",
            WebPAnimEncoderGetError(enc));
    WebPAnimEncoderDelete(enc);
    WebPDataClear(&webp_data);
    abort();
  }

  WebPAnimEncoderDelete(enc);
  WebPDataClear(&webp_data);
}

}  // namespace

FUZZ_TEST(AnimEncoder, AnimEncoderTest)
    .WithDomains(
        fuzztest::String(),
        /*minimize_size=*/fuzztest::Arbitrary<bool>(), ArbitraryKMinKMax(),
        /*allow_mixed=*/fuzztest::Arbitrary<bool>(),
        fuzztest::VectorOf(
            fuzztest::StructOf<FrameConfig>(
                fuzztest::InRange<int>(0, 1), fuzztest::InRange<int>(0, 131073),
                fuzz_utils::ArbitraryWebPConfig(),
                fuzz_utils::ArbitraryCropOrScaleParams(),
                fuzztest::InRange<int>(0, fuzz_utils::kNumSourceImages - 1)))
            .WithMinSize(1)
            .WithMaxSize(15),
        /*optimization_index=*/
        fuzztest::InRange<uint32_t>(0, fuzz_utils::kMaxOptimizationIndex));

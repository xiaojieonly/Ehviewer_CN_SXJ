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

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string_view>

#include "./fuzz_utils.h"
#include "../dec/webpi_dec.h"
#include "../src/utils/rescaler_utils.h"
#include "../src/webp/decode.h"

namespace {

void AdvancedApiTest(std::string_view blob, uint8_t factor_u8, bool flip,
                     bool bypass_filtering, bool no_fancy_upsampling,
                     bool use_threads, bool use_cropping, bool use_scaling,
                     bool use_dithering, int colorspace, bool incremental) {
  WebPDecoderConfig config;
  if (!WebPInitDecoderConfig(&config)) return;
  const uint8_t* const data = reinterpret_cast<const uint8_t*>(blob.data());
  const size_t size = blob.size();
  if (WebPGetFeatures(data, size, &config.input) != VP8_STATUS_OK) return;
  if ((size_t)config.input.width * config.input.height >
      fuzz_utils::kFuzzPxLimit) {
    return;
  }

  // Using two independent criteria ensures that all combinations of options
  // can reach each path at the decoding stage, with meaningful differences.

  const uint8_t value = fuzz_utils::FuzzHash(data, size);
  const float factor = factor_u8 / 255.f;  // 0-1

  config.options.flip = flip;
  config.options.bypass_filtering = bypass_filtering;
  config.options.no_fancy_upsampling = no_fancy_upsampling;
  config.options.use_threads = use_threads;
  if (use_cropping) {
    config.options.use_cropping = 1;
    config.options.crop_width = (int)(config.input.width * (1 - factor));
    config.options.crop_height = (int)(config.input.height * (1 - factor));
    config.options.crop_left = config.input.width - config.options.crop_width;
    config.options.crop_top = config.input.height - config.options.crop_height;
  }
  if (use_dithering) {
    int strength = (int)(factor * 100);
    config.options.dithering_strength = strength;
    config.options.alpha_dithering_strength = 100 - strength;
  }
  if (use_scaling) {
    config.options.use_scaling = 1;
    config.options.scaled_width = (int)(config.input.width * factor * 2);
    config.options.scaled_height = (int)(config.input.height * factor * 2);
  }
  config.output.colorspace = static_cast<WEBP_CSP_MODE>(colorspace);

  for (int i = 0; i < 2; ++i) {
    if (i == 1) {
      // Use the bitstream data to generate extreme ranges for the options. An
      // alternative approach would be to use a custom corpus containing webp
      // files prepended with sizeof(config.options) zeroes to allow the fuzzer
      // to modify these independently.
      const int data_offset = 50;
      if (data_offset + sizeof(config.options) >= size) break;
      memcpy(&config.options, data + data_offset, sizeof(config.options));

      // Skip easily avoidable out-of-memory fuzzing errors.
      if (config.options.use_scaling) {
        int input_width = config.input.width;
        int input_height = config.input.height;
        if (config.options.use_cropping) {
          const int cw = config.options.crop_width;
          const int ch = config.options.crop_height;
          const int x = config.options.crop_left & ~1;
          const int y = config.options.crop_top & ~1;
          if (WebPCheckCropDimensions(input_width, input_height, x, y, cw,
                                      ch)) {
            input_width = cw;
            input_height = ch;
          }
        }

        int scaled_width = config.options.scaled_width;
        int scaled_height = config.options.scaled_height;
        if (WebPRescalerGetScaledDimensions(input_width, input_height,
                                            &scaled_width, &scaled_height)) {
          size_t fuzz_px_limit = fuzz_utils::kFuzzPxLimit;
          if (scaled_width != config.input.width ||
              scaled_height != config.input.height) {
            // Using the WebPRescalerImport internally can significantly slow
            // down the execution. Avoid timeouts due to that.
            fuzz_px_limit /= 2;
          }
          // A big output canvas can lead to out-of-memory and timeout issues,
          // but a big internal working buffer can too. Also, rescaling from a
          // very wide input image to a very tall canvas can be as slow as
          // decoding a huge number of pixels. Avoid timeouts due to these.
          const uint64_t max_num_operations =
              (uint64_t)std::max(scaled_width, config.input.width) *
              std::max(scaled_height, config.input.height);
          if (max_num_operations > fuzz_px_limit) {
            break;
          }
        }
      }
    }
    if (incremental) {
      // Decodes incrementally in chunks of increasing size.
      WebPIDecoder* idec = WebPIDecode(NULL, 0, &config);
      if (!idec) return;
      VP8StatusCode status;
      if (size & 8) {
        size_t available_size = value + 1;
        while (1) {
          if (available_size > size) available_size = size;
          status = WebPIUpdate(idec, data, available_size);
          if (status != VP8_STATUS_SUSPENDED || available_size == size) break;
          available_size *= 2;
        }
      } else {
        // WebPIAppend expects new data and its size with each call.
        // Implemented here by simply advancing the pointer into data.
        const uint8_t* new_data = data;
        size_t new_size = value + 1;
        while (1) {
          if (new_data + new_size > data + size) {
            new_size = data + size - new_data;
          }
          status = WebPIAppend(idec, new_data, new_size);
          if (status != VP8_STATUS_SUSPENDED || new_size == 0) break;
          new_data += new_size;
          new_size *= 2;
        }
      }
      WebPIDelete(idec);
    } else {
      (void)WebPDecode(data, size, &config);
    }

    WebPFreeDecBuffer(&config.output);
  }
}

}  // namespace

FUZZ_TEST(AdvancedApi, AdvancedApiTest)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1),
        /*factor_u8=*/fuzztest::Arbitrary<uint8_t>(),
        /*flip=*/fuzztest::Arbitrary<bool>(),
        /*bypass_filtering=*/fuzztest::Arbitrary<bool>(),
        /*no_fancy_upsampling=*/fuzztest::Arbitrary<bool>(),
        /*use_threads=*/fuzztest::Arbitrary<bool>(),
        /*use_cropping=*/fuzztest::Arbitrary<bool>(),
        /*use_scaling=*/fuzztest::Arbitrary<bool>(),
        /*use_dithering=*/fuzztest::Arbitrary<bool>(),
#if defined(WEBP_REDUCE_CSP)
        fuzztest::ElementOf<int>({static_cast<int>(MODE_RGBA),
                                  static_cast<int>(MODE_BGRA),
                                  static_cast<int>(MODE_rgbA),
                                  static_cast<int>(MODE_bgrA)}),
#else
        fuzztest::InRange<int>(0, static_cast<int>(MODE_LAST) - 1),
#endif
        /*incremental=*/fuzztest::Arbitrary<bool>());

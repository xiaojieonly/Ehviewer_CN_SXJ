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

#include "./fuzz_utils.h"
#include "../src/dsp/cpu.h"
#include "../src/webp/decode.h"
#include "../src/webp/encode.h"

namespace {

const VP8CPUInfo default_VP8GetCPUInfo = fuzz_utils::VP8GetCPUInfo;

void EncDecTest(bool use_argb, int source_image_index, WebPConfig config,
                int optimization_index,
                const fuzz_utils::CropOrScaleParams& crop_or_scale_params) {
  fuzz_utils::SetOptimization(default_VP8GetCPUInfo, optimization_index);

  // Init the source picture.
  WebPPicture pic = fuzz_utils::GetSourcePicture(source_image_index, use_argb);

  // Crop and scale.
  if (!fuzz_utils::CropOrScale(&pic, crop_or_scale_params)) {
    const WebPEncodingError error_code = pic.error_code;
    WebPPictureFree(&pic);
    if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY) return;
    fprintf(stderr, "ExtractAndCropOrScale failed. Error code: %d\n",
            error_code);
    abort();
  }

  // Skip slow settings on big images, it's likely to timeout.
  if (pic.width * pic.height > 32 * 32) {
    if (config.lossless) {
      if (config.quality > 99.0f && config.method >= 5) {
        config.quality = 99.0f;
        config.method = 5;
      }
    } else {
      if (config.quality > 99.0f && config.method == 6) {
        config.quality = 99.0f;
      }
    }
    if (config.alpha_quality == 100 && config.method == 6) {
      config.alpha_quality = 99;
    }
  }

  // Encode.
  WebPMemoryWriter memory_writer;
  WebPMemoryWriterInit(&memory_writer);
  pic.writer = WebPMemoryWrite;
  pic.custom_ptr = &memory_writer;
  if (!WebPEncode(&config, &pic)) {
    const WebPEncodingError error_code = pic.error_code;
    WebPMemoryWriterClear(&memory_writer);
    WebPPictureFree(&pic);
    if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY ||
        error_code == VP8_ENC_ERROR_BAD_WRITE) {
      return;
    }
    fprintf(stderr, "WebPEncode failed. Error code: %d\n", error_code);
    abort();
  }

  // Try decoding the result.
  const uint8_t* const out_data = memory_writer.mem;
  const size_t out_size = memory_writer.size;
  WebPDecoderConfig dec_config;
  if (!WebPInitDecoderConfig(&dec_config)) {
    fprintf(stderr, "WebPInitDecoderConfig failed.\n");
    WebPMemoryWriterClear(&memory_writer);
    WebPPictureFree(&pic);
    abort();
  }

  dec_config.output.colorspace = MODE_BGRA;
  const VP8StatusCode status = WebPDecode(out_data, out_size, &dec_config);
  if ((status != VP8_STATUS_OK && status != VP8_STATUS_OUT_OF_MEMORY &&
       status != VP8_STATUS_USER_ABORT) ||
      (status == VP8_STATUS_OK && (dec_config.output.width != pic.width ||
                                   dec_config.output.height != pic.height))) {
    fprintf(stderr, "WebPDecode failed. status: %d.\n", status);
    WebPFreeDecBuffer(&dec_config.output);
    WebPMemoryWriterClear(&memory_writer);
    WebPPictureFree(&pic);
    abort();
  }

  if (status == VP8_STATUS_OK) {
    const uint8_t* const rgba = dec_config.output.u.RGBA.rgba;
    const int w = dec_config.output.width;
    const int h = dec_config.output.height;

    // Compare the results if exact encoding.
    if (pic.use_argb && config.lossless && config.near_lossless == 100) {
      const uint32_t* src1 = (const uint32_t*)rgba;
      const uint32_t* src2 = pic.argb;
      for (int y = 0; y < h; ++y, src1 += w, src2 += pic.argb_stride) {
        for (int x = 0; x < w; ++x) {
          uint32_t v1 = src1[x], v2 = src2[x];
          if (!config.exact) {
            if ((v1 & 0xff000000u) == 0 || (v2 & 0xff000000u) == 0) {
              // Only keep alpha for comparison of fully transparent area.
              v1 &= 0xff000000u;
              v2 &= 0xff000000u;
            }
          }
          if (v1 != v2) {
            fprintf(stderr, "Lossless compression failed pixel-exactness.\n");
            WebPFreeDecBuffer(&dec_config.output);
            WebPMemoryWriterClear(&memory_writer);
            WebPPictureFree(&pic);
            abort();
          }
        }
      }
    }
  }

  WebPFreeDecBuffer(&dec_config.output);
  WebPMemoryWriterClear(&memory_writer);
  WebPPictureFree(&pic);
}

}  // namespace

FUZZ_TEST(EncDec, EncDecTest)
    .WithDomains(/*use_argb=*/fuzztest::Arbitrary<bool>(),
                 /*source_image_index=*/
                 fuzztest::InRange<int>(0, fuzz_utils::kNumSourceImages - 1),
                 fuzz_utils::ArbitraryWebPConfig(),
                 /*optimization_index=*/
                 fuzztest::InRange<uint32_t>(0,
                                             fuzz_utils::kMaxOptimizationIndex),
                 fuzz_utils::ArbitraryCropOrScaleParams());

// Copyright 2024 Google Inc.
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
#include <cstdlib>
#include <iostream>
#include <string>
#include <string_view>

#include "imageio/image_dec.h"
#include "../src/dsp/cpu.h"
#include "../src/webp/decode.h"
#include "../src/webp/encode.h"
#include "../src/webp/types.h"
#include "tests/fuzzer/fuzz_utils.h"

namespace {

const VP8CPUInfo default_VP8GetCPUInfo = fuzz_utils::VP8GetCPUInfo;

void EncTest(std::string_view file, uint32_t optimization_index, bool use_argb,
             WebPConfig config,
             const fuzz_utils::CropOrScaleParams& crop_or_scale_params) {
  fuzz_utils::SetOptimization(default_VP8GetCPUInfo, optimization_index);

  // Init the source picture.
  WebPPicture pic;
  if (!WebPPictureInit(&pic)) {
    std::cerr << "WebPPictureInit failed.\n";
    abort();
  }
  pic.use_argb = use_argb;

  const uint8_t* const file_data =
      reinterpret_cast<const uint8_t*>(file.data());
  if (fuzz_utils::IsImageTooBig(file_data, file.size())) return;
  WebPImageReader reader = WebPGuessImageReader(file_data, file.size());
  if (!reader(file_data, file.size(), &pic, 1, NULL)) return;

  // Crop and scale.
  if (!CropOrScale(&pic, crop_or_scale_params)) {
    const WebPEncodingError error_code = pic.error_code;
    WebPPictureFree(&pic);
    if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY) return;
    std::cerr << "CropOrScale failed. Error code: " << error_code << "\n";
    abort();
  }

  // Skip the cruncher except on small images, it's likely to timeout.
  if (config.lossless && config.quality == 100. && config.method == 6 &&
      pic.width * pic.height >= 16384) {
    config.lossless = 0;
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
    if (error_code == VP8_ENC_ERROR_OUT_OF_MEMORY) return;
    std::cerr << "WebPEncode failed. Error code: " << error_code
              << " \nFile starts with: " << file.substr(0, 20) << "\n";
    abort();
  }

  // Try decoding the result.
  int w, h;
  const uint8_t* const out_data = memory_writer.mem;
  const size_t out_size = memory_writer.size;
  uint8_t* const rgba = WebPDecodeBGRA(out_data, out_size, &w, &h);
  if (rgba == nullptr || w != pic.width || h != pic.height) {
    std::cerr << "WebPDecodeBGRA failed.\nFile starts with: "
              << file.substr(0, 20) << "\n";
    WebPFree(rgba);
    WebPMemoryWriterClear(&memory_writer);
    WebPPictureFree(&pic);
    abort();
  }

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
          std::cerr
              << "Lossless compression failed pixel-exactness.\nFile starts "
                 "with: "
              << file.substr(0, 20) << "\n";
          WebPFree(rgba);
          WebPMemoryWriterClear(&memory_writer);
          WebPPictureFree(&pic);
          abort();
        }
      }
    }
  }

  WebPFree(rgba);
  WebPMemoryWriterClear(&memory_writer);
  WebPPictureFree(&pic);
}

}  // namespace

FUZZ_TEST(Enc, EncTest)
    .WithDomains(
        fuzztest::Arbitrary<std::string>(),
        /*optimization_index=*/
        fuzztest::InRange<uint32_t>(0, fuzz_utils::kMaxOptimizationIndex),
        /*use_argb=*/fuzztest::Arbitrary<bool>(),
        fuzz_utils::ArbitraryWebPConfig(),
        fuzz_utils::ArbitraryCropOrScaleParams());

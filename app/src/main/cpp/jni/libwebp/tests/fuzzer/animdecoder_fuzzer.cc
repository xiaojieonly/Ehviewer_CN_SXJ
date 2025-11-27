// Copyright 2020 Google Inc.
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
#include <string_view>

#include "./fuzz_utils.h"
#include "imageio/imageio_util.h"
#include "../src/webp/decode.h"
#include "../webp/demux.h"
#include "../src/webp/mux_types.h"

namespace {

void AnimDecoderTest(std::string_view blob) {
  const uint8_t* const data = reinterpret_cast<const uint8_t*>(blob.data());
  const size_t size = blob.size();

  // WebPAnimDecoderGetInfo() is too late to check the canvas size as
  // WebPAnimDecoderNew() will handle the allocations.
  const size_t kMaxNumBytes = 2684354560;  // RSS (resident set size) limit.
  const size_t kMaxNumPixels = kMaxNumBytes / 4;       // At most ARGB.
  const size_t kMaxNumPixelsSafe = kMaxNumPixels / 2;  // Allow one buffer copy.
  WebPBitstreamFeatures features;
  if (WebPGetFeatures(data, size, &features) == VP8_STATUS_OK) {
    if (!ImgIoUtilCheckSizeArgumentsOverflow(features.width * 4,
                                             features.height) ||
        static_cast<size_t>(features.width) * features.height >
            kMaxNumPixelsSafe) {
      return;
    }
  }

  // decode everything as an animation
  WebPData webp_data = {data, size};
  WebPAnimDecoder* const dec = WebPAnimDecoderNew(&webp_data, nullptr);
  if (dec == nullptr) return;

  WebPAnimInfo info;
  if (!WebPAnimDecoderGetInfo(dec, &info)) goto End;
  if (!ImgIoUtilCheckSizeArgumentsOverflow(info.canvas_width * 4,
                                           info.canvas_height)) {
    goto End;
  }

  while (WebPAnimDecoderHasMoreFrames(dec)) {
    uint8_t* buf;
    int timestamp;
    if (!WebPAnimDecoderGetNext(dec, &buf, &timestamp)) break;
  }
End:
  WebPAnimDecoderDelete(dec);
}

}  // namespace

FUZZ_TEST(AnimDecoder, AnimDecoderTest)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1));

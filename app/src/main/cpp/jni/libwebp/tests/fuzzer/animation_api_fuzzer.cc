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
#include <string_view>

#include "./fuzz_utils.h"
#include "../src/webp/decode.h"
#include "../webp/demux.h"
#include "../src/webp/mux_types.h"

namespace {

void AnimationApiTest(std::string_view blob, bool use_threads,
                      WEBP_CSP_MODE color_mode) {
  const size_t size = blob.size();
  WebPData webp_data;
  WebPDataInit(&webp_data);
  webp_data.size = size;
  webp_data.bytes = reinterpret_cast<const uint8_t*>(blob.data());

  // WebPAnimDecoderNew uses WebPDemux internally to calloc canvas size.
  WebPDemuxer* const demux = WebPDemux(&webp_data);
  if (!demux) return;
  const uint32_t cw = WebPDemuxGetI(demux, WEBP_FF_CANVAS_WIDTH);
  const uint32_t ch = WebPDemuxGetI(demux, WEBP_FF_CANVAS_HEIGHT);
  if ((size_t)cw * ch > fuzz_utils::kFuzzPxLimit) {
    WebPDemuxDelete(demux);
    return;
  }

  // In addition to canvas size, check each frame separately.
  WebPIterator iter;
  for (int i = 0; i < fuzz_utils::kFuzzFrameLimit; i++) {
    if (!WebPDemuxGetFrame(demux, i + 1, &iter)) break;
    int w, h;
    if (WebPGetInfo(iter.fragment.bytes, iter.fragment.size, &w, &h)) {
      if ((size_t)w * h >
          fuzz_utils::kFuzzPxLimit) {  // image size of the frame payload
        WebPDemuxReleaseIterator(&iter);
        WebPDemuxDelete(demux);
        return;
      }
    }
  }

  WebPDemuxReleaseIterator(&iter);
  WebPDemuxDelete(demux);

  WebPAnimDecoderOptions dec_options;
  if (!WebPAnimDecoderOptionsInit(&dec_options)) return;

  dec_options.use_threads = use_threads;
  dec_options.color_mode = color_mode;

  WebPAnimDecoder* dec = WebPAnimDecoderNew(&webp_data, &dec_options);
  if (!dec) return;

  for (int i = 0; i < fuzz_utils::kFuzzFrameLimit; i++) {
    uint8_t* buf;
    int timestamp;
    if (!WebPAnimDecoderGetNext(dec, &buf, &timestamp)) break;
  }

  WebPAnimDecoderDelete(dec);
}

}  // namespace

FUZZ_TEST(AnimationApi, AnimationApiTest)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1),
        /*use_threads=*/fuzztest::Arbitrary<bool>(),
        // Animations only support 4 (out of 12) modes.
        fuzztest::ElementOf<WEBP_CSP_MODE>({MODE_RGBA, MODE_BGRA, MODE_rgbA,
                                            MODE_bgrA}));

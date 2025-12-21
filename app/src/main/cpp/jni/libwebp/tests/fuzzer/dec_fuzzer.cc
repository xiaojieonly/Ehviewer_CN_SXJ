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

#include <cstdint>
#include <cstdio>
#include <string_view>

#include "../src/webp/decode.h"
#include "tests/fuzzer/fuzz_utils.h"

namespace {

void DecodeWebP(std::string_view arbitrary_bytes) {
  WebPDecoderConfig decoder_config;
  if (!WebPInitDecoderConfig(&decoder_config)) {
    fprintf(stderr, "WebPInitDecoderConfig failed.\n");
    abort();
  }
  const VP8StatusCode status =
      WebPDecode(reinterpret_cast<const uint8_t*>(arbitrary_bytes.data()),
                 arbitrary_bytes.size(), &decoder_config);
  WebPFreeDecBuffer(&decoder_config.output);
  // The decoding may fail (because the fuzzed input can be anything) but not
  // for these reasons.
  if (status == VP8_STATUS_SUSPENDED || status == VP8_STATUS_USER_ABORT) {
    abort();
  }
}

FUZZ_TEST(WebPSuite, DecodeWebP)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1));

}  // namespace

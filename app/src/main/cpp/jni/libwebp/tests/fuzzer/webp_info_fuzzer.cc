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

#include "../src/webp/mux_types.h"
#include "tests/fuzzer/fuzz_utils.h"

// Don't do that at home!
#define main exec_main
#include "examples/webpinfo.c"
#undef main

void WebPInfoTest(std::string_view data) {
  WebPInfo webp_info;
  WebPInfoInit(&webp_info);
  webp_info.quiet_ = 1;
  webp_info.show_summary_ = 0;
  webp_info.show_diagnosis_ = 0;
  webp_info.parse_bitstream_ = 1;
  WebPData webp_data = {reinterpret_cast<const uint8_t *>(data.data()),
                        data.size()};
  AnalyzeWebP(&webp_info, &webp_data);
}

FUZZ_TEST(WebPInfo, WebPInfoTest)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1));

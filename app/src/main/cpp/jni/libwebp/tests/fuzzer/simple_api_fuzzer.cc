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

namespace {

void SimpleApiTest(std::string_view data_in) {
  const uint8_t* const data = reinterpret_cast<const uint8_t*>(data_in.data());
  const size_t size = data_in.size();
  int w, h;
  if (!WebPGetInfo(data, size, &w, &h)) return;
  if ((size_t)w * h > fuzz_utils::kFuzzPxLimit) return;

  const uint8_t value = fuzz_utils::FuzzHash(data, size);
  uint8_t* buf = NULL;

  // For *Into functions, which decode into an external buffer, an
  // intentionally too small buffer can be given with low probability.
  if (value < 0x16) {
    buf = WebPDecodeRGBA(data, size, &w, &h);
  } else if (value < 0x2b) {
    buf = WebPDecodeBGRA(data, size, &w, &h);
#if !defined(WEBP_REDUCE_CSP)
  } else if (value < 0x40) {
    buf = WebPDecodeARGB(data, size, &w, &h);
  } else if (value < 0x55) {
    buf = WebPDecodeRGB(data, size, &w, &h);
  } else if (value < 0x6a) {
    buf = WebPDecodeBGR(data, size, &w, &h);
#endif  // !defined(WEBP_REDUCE_CSP)
  } else if (value < 0x7f) {
    uint8_t *u, *v;
    int stride, uv_stride;
    buf = WebPDecodeYUV(data, size, &w, &h, &u, &v, &stride, &uv_stride);
  } else if (value < 0xe8) {
    const int stride = (value < 0xbe ? 4 : 3) * w;
    size_t buf_size = stride * h;
    if (value % 0x10 == 0) buf_size--;
    uint8_t* const ext_buf = (uint8_t*)malloc(buf_size);
    if (value < 0x94) {
      (void)WebPDecodeRGBAInto(data, size, ext_buf, buf_size, stride);
#if !defined(WEBP_REDUCE_CSP)
    } else if (value < 0xa9) {
      (void)WebPDecodeARGBInto(data, size, ext_buf, buf_size, stride);
    } else if (value < 0xbe) {
      (void)WebPDecodeBGRInto(data, size, ext_buf, buf_size, stride);
    } else if (value < 0xd3) {
      (void)WebPDecodeRGBInto(data, size, ext_buf, buf_size, stride);
#endif  // !defined(WEBP_REDUCE_CSP)
    } else {
      (void)WebPDecodeBGRAInto(data, size, ext_buf, buf_size, stride);
    }
    free(ext_buf);
  } else {
    size_t luma_size = w * h;
    const int uv_stride = (w + 1) / 2;
    size_t u_size = uv_stride * (h + 1) / 2;
    size_t v_size = uv_stride * (h + 1) / 2;
    if (value % 0x10 == 0) {
      if (size & 1) luma_size--;
      if (size & 2) u_size--;
      if (size & 4) v_size--;
    }
    uint8_t* const luma_buf = (uint8_t*)malloc(luma_size);
    uint8_t* const u_buf = (uint8_t*)malloc(u_size);
    uint8_t* const v_buf = (uint8_t*)malloc(v_size);
    (void)WebPDecodeYUVInto(data, size, luma_buf, luma_size,
                            w /* luma_stride */, u_buf, u_size, uv_stride,
                            v_buf, v_size, uv_stride);
    free(luma_buf);
    free(u_buf);
    free(v_buf);
  }

  if (buf) WebPFree(buf);
}

}  // namespace

FUZZ_TEST(SimpleApi, SimpleApiTest)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1));

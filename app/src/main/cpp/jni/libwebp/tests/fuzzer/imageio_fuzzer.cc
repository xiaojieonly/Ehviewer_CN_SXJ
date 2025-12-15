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

// Fuzzing of libwebp's image readers

#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <iostream>

#include "imageio/image_dec.h"
#include "imageio/metadata.h"
#include "../src/webp/encode.h"
#include "tests/fuzzer/fuzz_utils.h"

namespace {

void TestReader(const uint8_t *data, size_t size, WebPImageReader reader,
                bool keep_alpha, bool use_argb) {
  WebPPicture pic;
  if (!WebPPictureInit(&pic)) {
    std::cerr << "WebPPictureInit failed" << std::endl;
    abort();
  }
  Metadata metadata;
  MetadataInit(&metadata);
  pic.use_argb = use_argb ? 1 : 0;

  if (!fuzz_utils::IsImageTooBig(data, size)) {
    (void)(*reader)(data, size, &pic, keep_alpha ? 1 : 0, &metadata);
  }
  WebPPictureFree(&pic);
  MetadataFree(&metadata);
}

constexpr WebPInputFileFormat kUnknown = WEBP_UNSUPPORTED_FORMAT;

void Decode(std::string_view arbitrary_bytes, WebPInputFileFormat format,
            bool keep_alpha, bool use_argb) {
  const uint8_t *data =
      reinterpret_cast<const uint8_t *>(arbitrary_bytes.data());
  const size_t size = arbitrary_bytes.size();
  if (format == kUnknown) {
    (void)WebPGuessImageType(data, size);  // shouldn't fail
    TestReader(data, size, WebPGuessImageReader(data, size), keep_alpha,
               use_argb);
  } else {
    TestReader(data, size, WebPGetImageReader(format), keep_alpha, use_argb);
  }
}

FUZZ_TEST(ImageIOSuite, Decode)
    .WithDomains(
        fuzztest::String()
            .WithMaxSize(fuzz_utils::kMaxWebPFileSize + 1),
        fuzztest::ElementOf<WebPInputFileFormat>(
            {WEBP_PNG_FORMAT, WEBP_JPEG_FORMAT, WEBP_TIFF_FORMAT,
             WEBP_WEBP_FORMAT, WEBP_PNM_FORMAT, kUnknown}),
        /*keep_alpha=*/fuzztest::Arbitrary<bool>(),
        /*use_argb=*/fuzztest::Arbitrary<bool>());

}  // namespace

// Copyright 2023 Google Inc.
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
#include "../src/dec/vp8li_dec.h
#include "../src/utils/bit_reader_utils.h"
#include "../src/utils/huffman_utils.h"
#include "../src/utils/utils.h"
#include "../src/webp/format_constants.h"

namespace {

void HuffmanTest(std::string_view blob) {
  const uint8_t* const data = reinterpret_cast<const uint8_t*>(blob.data());
  const size_t size = blob.size();

  // Number of bits to initialize data.
  static const int kColorCacheBitsBits = 4;
  // 'num_htree_groups' is contained in the RG channel, hence 16 bits.
  static const int kNumHtreeGroupsBits = 16;
  if (size * sizeof(*data) < kColorCacheBitsBits + kNumHtreeGroupsBits) {
    return;
  }

  // A non-NULL mapping brings minor changes that are tested by the normal
  // fuzzer.
  int* const mapping = NULL;
  HuffmanTables huffman_tables;
  memset(&huffman_tables, 0, sizeof(huffman_tables));
  HTreeGroup* htree_groups = NULL;

  int num_htree_groups, num_htree_groups_max, color_cache_bits;
  VP8LBitReader* br;
  VP8LDecoder* dec = VP8LNew();
  if (dec == NULL) goto Error;
  br = &dec->br_;
  VP8LInitBitReader(br, data, size);

  color_cache_bits = VP8LReadBits(br, kColorCacheBitsBits);
  if (color_cache_bits < 1 || color_cache_bits > MAX_CACHE_BITS) goto Error;

  num_htree_groups = VP8LReadBits(br, kNumHtreeGroupsBits);
  // 'num_htree_groups' cannot be 0 as it is built from a non-empty image.
  if (num_htree_groups == 0) goto Error;
  // This variable is only useful when mapping is not NULL.
  num_htree_groups_max = num_htree_groups;
  (void)ReadHuffmanCodesHelper(color_cache_bits, num_htree_groups,
                               num_htree_groups_max, mapping, dec,
                               &huffman_tables, &htree_groups);

Error:
  WebPSafeFree(mapping);
  VP8LHtreeGroupsFree(htree_groups);
  VP8LHuffmanTablesDeallocate(&huffman_tables);
  VP8LDelete(dec);
}

}  // namespace

FUZZ_TEST(Huffman, HuffmanTest).WithDomains(fuzztest::String());

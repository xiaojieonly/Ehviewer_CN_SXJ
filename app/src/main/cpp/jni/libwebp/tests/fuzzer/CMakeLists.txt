#  Copyright (c) 2024 Google LLC
#
#  Use of this source code is governed by a BSD-style license
#  that can be found in the LICENSE file in the root of the source
#  tree. An additional intellectual property rights grant can be found
#  in the file PATENTS.  All contributing project authors may
#  be found in the AUTHORS file in the root of the source tree.

# Adds a fuzztest from file TEST_NAME.cc located in the gtest folder. Extra
# arguments are considered as extra source files.

if(CMAKE_VERSION VERSION_LESS "3.19.0")
  return()
endif()

macro(add_webp_fuzztest TEST_NAME)
  add_executable(${TEST_NAME} ${TEST_NAME}.cc)
  # FuzzTest bundles GoogleTest so no need to link to gtest libraries.
  target_link_libraries(${TEST_NAME} PRIVATE fuzz_utils webp ${ARGN})
  target_include_directories(${TEST_NAME} PRIVATE ${CMAKE_BINARY_DIR}/src)
  link_fuzztest(${TEST_NAME})
  add_test(NAME ${TEST_NAME} COMMAND ${TEST_NAME})
  set_property(
    TEST ${TEST_NAME}
    PROPERTY ENVIRONMENT "TEST_DATA_DIRS=${CMAKE_CURRENT_SOURCE_DIR}/data/")
endmacro()

enable_language(CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(FETCHCONTENT_QUIET FALSE)
set(fuzztest_SOURCE_DIR ${CMAKE_BINARY_DIR}/_deps/fuzztest-src)
FetchContent_Declare(
  fuzztest
  GIT_REPOSITORY https://github.com/google/fuzztest.git
  GIT_TAG 078ea0871cc96d3a69bad406577f176a4fa14ae9
  GIT_PROGRESS TRUE
  PATCH_COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/patch.sh)

FetchContent_MakeAvailable(fuzztest)

fuzztest_setup_fuzzing_flags()

add_library(fuzz_utils fuzz_utils.h fuzz_utils.cc img_alpha.h img_grid.h
                       img_peak.h)
target_link_libraries(fuzz_utils PUBLIC webpdecoder)
link_fuzztest(fuzz_utils)

add_webp_fuzztest(advanced_api_fuzzer webpdecode webpdspdecode webputilsdecode)
add_webp_fuzztest(dec_fuzzer)
add_webp_fuzztest(enc_dec_fuzzer)
add_webp_fuzztest(enc_fuzzer imagedec)
add_webp_fuzztest(huffman_fuzzer webpdecode webpdspdecode webputilsdecode)
add_webp_fuzztest(imageio_fuzzer imagedec)
add_webp_fuzztest(simple_api_fuzzer)

if(WEBP_BUILD_LIBWEBPMUX)
  add_webp_fuzztest(animation_api_fuzzer webpdemux)
  add_webp_fuzztest(animdecoder_fuzzer imageioutil webpdemux)
  add_webp_fuzztest(animencoder_fuzzer libwebpmux)
  add_webp_fuzztest(mux_demux_api_fuzzer libwebpmux webpdemux)
endif()

if(WEBP_BUILD_WEBPINFO)
  add_webp_fuzztest(webp_info_fuzzer imageioutil)
endif()

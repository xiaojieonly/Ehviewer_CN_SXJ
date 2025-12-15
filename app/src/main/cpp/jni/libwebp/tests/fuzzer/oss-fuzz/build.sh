#!/bin/bash
# Copyright 2018 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
################################################################################

# This script is meant to be run by the oss-fuzz infrastructure from the script
# https://github.com/google/oss-fuzz/blob/master/projects/libwebp/build.sh
# It builds the different fuzz targets.
# Only the libfuzzer engine is supported.

# To test changes to this file:
# - make changes and commit to your REPO
# - run:
#     git clone --depth=1 git@github.com:google/oss-fuzz.git
#     cd oss-fuzz
# - modify projects/libwebp/Dockerfile to point to your REPO
# - run:
#     python3 infra/helper.py build_image libwebp
#     # enter 'y' and wait for everything to be downloaded
# - run:
#     python3 infra/helper.py build_fuzzers --sanitizer address libwebp
#     # wait for the tests to be built
# And then run the fuzzer locally, for example:
#     python3 infra/helper.py run_fuzzer libwebp \
#     --sanitizer address \
#     animencoder_fuzzer@AnimEncoder.AnimEncoderTest

set -eu

EXTRA_CMAKE_FLAGS=""
export CXXFLAGS="${CXXFLAGS} -DFUZZTEST_COMPATIBILITY_MODE"
EXTRA_CMAKE_FLAGS="-DFUZZTEST_COMPATIBILITY_MODE=libfuzzer"

# limit allocation size to reduce spurious OOMs
WEBP_CFLAGS="$CFLAGS -DWEBP_MAX_IMAGE_SIZE=838860800" # 800MiB

export CFLAGS="$WEBP_CFLAGS"
cmake -S . -B build -DWEBP_BUILD_FUZZTEST=ON ${EXTRA_CMAKE_FLAGS}
cd build && make -j$(nproc) && cd ..

find $SRC/libwebp-test-data -type f -size -32k -iname "*.webp" \
  -exec zip -qju fuzz_seed_corpus.zip "{}" \;

# The following is taken from https://github.com/google/oss-fuzz/blob/31ac7244748ea7390015455fb034b1f4eda039d9/infra/base-images/base-builder/compile_fuzztests.sh#L59
# Iterate the fuzz binaries and list each fuzz entrypoint in the binary. For
# each entrypoint create a wrapper script that calls into the binaries the
# given entrypoint as argument.
# The scripts will be named:
# {binary_name}@{fuzztest_entrypoint}
FUZZ_TEST_BINARIES_OUT_PATHS=$(find ./build/tests/fuzzer/ -executable -type f)
echo "Fuzz binaries: $FUZZ_TEST_BINARIES_OUT_PATHS"
for fuzz_main_file in $FUZZ_TEST_BINARIES_OUT_PATHS; do
  FUZZ_TESTS=$($fuzz_main_file --list_fuzz_tests | cut -d ' ' -f 4)
  cp -f ${fuzz_main_file} $OUT/
  fuzz_basename=$(basename $fuzz_main_file)
  chmod -x $OUT/$fuzz_basename
  for fuzz_entrypoint in $FUZZ_TESTS; do
    TARGET_FUZZER="${fuzz_basename}@$fuzz_entrypoint"
    # Write executer script
    cat << EOF > $OUT/$TARGET_FUZZER
#!/bin/sh
# LLVMFuzzerTestOneInput for fuzzer detection.
this_dir=\$(dirname "\$0")
export TEST_DATA_DIRS=\$this_dir/corpus
chmod +x \$this_dir/$fuzz_basename
\$this_dir/$fuzz_basename --fuzz=$fuzz_entrypoint -- \$@
chmod -x \$this_dir/$fuzz_basename
EOF
    chmod +x $OUT/$TARGET_FUZZER
  done
  # Copy data.
  cp fuzz_seed_corpus.zip $OUT/${fuzz_basename}_seed_corpus.zip
  cp tests/fuzzer/fuzz.dict $OUT/${fuzz_basename}.dict
done

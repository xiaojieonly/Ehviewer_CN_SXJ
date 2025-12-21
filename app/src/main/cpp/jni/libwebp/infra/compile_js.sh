#!/bin/bash
# Copyright (c) 2021, Google Inc. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#   * Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#
#   * Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in
#     the documentation and/or other materials provided with the
#     distribution.
#
#   * Neither the name of Google nor the names of its contributors may
#     be used to endorse or promote products derived from this software
#     without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

set -ex

readonly WORKSPACE="${WORKSPACE:-"$(mktemp -d -t webp.js.XXX)"}"
readonly BUILD_DIR="${WORKSPACE}/webp_js/"
readonly LIBWEBP_ROOT="$(realpath "$(dirname "$0")/..")"

# shellcheck source=infra/common.sh
source "${LIBWEBP_ROOT}/infra/common.sh"

usage() {
  cat << EOF
Usage: $(basename "$0")
Environment variables:
WORKSPACE directory where the build is done
EMSDK_DIR directory where emsdk is installed
EOF
}

[[ -d "${EMSDK_DIR:?Not defined}" ]] \
  || (log_err "${EMSDK_DIR} is not a valid directory." && exit 1)

# shellcheck source=/opt/emsdk/emsdk_env.sh
source "${EMSDK_DIR}/emsdk_env.sh"

readonly EMSCRIPTEN=${EMSCRIPTEN:-"${EMSDK}/upstream/emscripten"}
readonly \
  EMSCRIPTEN_CMAKE_FILE="${EMSCRIPTEN}/cmake/Modules/Platform/Emscripten.cmake"
make_build_dir "${BUILD_DIR}"

pushd "${BUILD_DIR}"
opts=("-GUnix Makefiles" "-DWEBP_BUILD_WEBP_JS=ON")
if [[ -z "$(command -v emcmake)" ]]; then
  opts+=("-DCMAKE_TOOLCHAIN_FILE=${EMSCRIPTEN_CMAKE_FILE}")
  cmake \
    "${opts[@]}" \
    "${LIBWEBP_ROOT}"
  make -j
else
  emcmake cmake \
    "${opts[@]}" \
    "${LIBWEBP_ROOT}"
  emmake make -j
fi
popd

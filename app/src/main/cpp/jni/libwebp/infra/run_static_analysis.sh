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

set -xe

LIBWEBP_ROOT="$(realpath "$(dirname "$0")/..")"
readonly LIBWEBP_ROOT
readonly WORKSPACE=${WORKSPACE:-"$(mktemp -d -t webp.scanbuild.XXX)"}

# shellcheck source=infra/common.sh
source "${LIBWEBP_ROOT}/infra/common.sh"

usage() {
  cat << EOF
Usage: $(basename "$0") MODE
Options:
MODE supported scan modes: (shallow|deep)
Environment variables:
WORKSPACE directory where the build is done.
EOF
}

#######################################
# Wrap clang-tools scan-build.
# Globals:
#   OUTPUT_DIR target directory where scan-build report is generated.
#   MODE scan-build mode
# Arguments:
#   $* scan-build additional args.
# Returns:
#   scan-build retcode
#######################################
scan_build() {
  scan-build -o "${OUTPUT_DIR}" --use-analyzer="$(command -v clang)" \
    -analyzer-config mode="${MODE}" "$*"
}

MODE=${1:?"MODE is not specified.$(
  echo
  usage
)"}

readonly OUTPUT_DIR="${WORKSPACE}/output-${MODE}"
readonly BUILD_DIR="${WORKSPACE}/build"

make_build_dir "${OUTPUT_DIR}"
make_build_dir "${BUILD_DIR}"

cd "${LIBWEBP_ROOT}"
./autogen.sh

cd "${BUILD_DIR}"
grep -m 1 -q 'enable-asserts' "${LIBWEBP_ROOT}/configure.ac" \
    && args='--enable-asserts'
scan_build "${LIBWEBP_ROOT}/configure" --enable-everything "${args}"
scan_build make -j4

index="$(find "${OUTPUT_DIR}" -name index.html)"
if [[ -f "${index}" ]]; then
  mv "$(dirname "${index}")/"* "${OUTPUT_DIR}"
else
  # make a empty report to wipe out any old bug reports.
  cat << EOT > "${OUTPUT_DIR}/index.html"
<html>
<body>
No bugs reported.
</body>
</html>
EOT
fi

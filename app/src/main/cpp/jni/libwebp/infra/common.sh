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

log_err() {
  echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')]: $*" >&2
}

#######################################
# Create build directory. Build directory will be deleted if it exists.
# Arguments:
#   None.
# Returns:
#   mkdir result.
#######################################
make_build_dir() {
  if [[ "$#" -ne 1 ]]; then
    return 1
  fi

  local build_dir
  build_dir="$1"
  rm -rf "${build_dir}"
  mkdir -p "${build_dir}"
}

#######################################
# Cleanup files from the build directory.
# Globals:
#   LIBWEBP_ROOT repository's root path.
# Arguments:
#   $1 build directory.
#######################################
cleanup() {
  # $1 is not completely removed to allow for binary artifacts to be
  # extracted.
  find "${1:?"Build directory not defined"}" \
    \( -name "*.[ao]" -o -name "*.l[ao]" \) -exec rm -f {} +
}

#######################################
# Setup ccache for toolchain.
# Globals:
#   PATH
# Arguments:
#   None.
#######################################
setup_ccache() {
  if [[ -x "$(command -v ccache)" ]]; then
    export CCACHE_CPP2=yes
    export PATH="/usr/lib/ccache:${PATH}"
  fi
}

#######################################
# Detects whether test block should be run in the current test shard.
# Globals:
#   TEST_TOTAL_SHARDS: Valid range: [1, N]. Defaults to 1.
#   TEST_SHARD_INDEX: Valid range: [0, TEST_TOTAL_SHARDS). Defaults to 0.
#   libwebp_test_id: current test number; incremented with each call.
# Arguments:
#   None
# Returns:
#   true if the shard is active
#   false if the shard is inactive
#######################################
shard_should_run() {
  TEST_TOTAL_SHARDS=${TEST_TOTAL_SHARDS:=1}
  TEST_SHARD_INDEX=${TEST_SHARD_INDEX:=0}
  libwebp_test_id=${libwebp_test_id:=-1}
  : $((libwebp_test_id += 1))

  if [[ "${TEST_SHARD_INDEX}" -lt 0 ||
    "${TEST_SHARD_INDEX}" -ge "${TEST_TOTAL_SHARDS}" ]]; then
    log_err "Invalid TEST_SHARD_INDEX (${TEST_SHARD_INDEX})!" \
      "Expected [0, ${TEST_TOTAL_SHARDS})."
  fi

  [[ "$((libwebp_test_id % TEST_TOTAL_SHARDS))" -eq "${TEST_SHARD_INDEX}" ]]
}

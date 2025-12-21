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
readonly WORKSPACE=${WORKSPACE:-"$(mktemp -d -t webp.android.XXX)"}
# shellcheck source=infra/common.sh
source "${LIBWEBP_ROOT}/infra/common.sh"

usage() {
  cat << EOF
Usage: $(basename "$0") BUILD_TYPE APP_ABI
Options:
BUILD_TYPE  supported build types:
              static
              static-debug
              shared
              shared-debug
APP_ABI     supported application binary interfaces:
              armeabi-v7a
              arm64-v8a
              x86
              x86_64
Environment variables:
WORKSPACE   directory where the build is done.
ANDROID_NDK_DIR directory where the android ndk tools are.
EOF
}

################################################################################
echo "Building libwebp for Android in ${WORKSPACE}"

if [[ ! -d "${WORKSPACE}" ]]; then
  log_err "${WORKSPACE} directory does not exist."
  exit 1
fi

readonly BUILD_TYPE=${1:?"BUILD_TYPE is not defined.$(
  echo
  usage
)"}
readonly APP_ABI=${2:?"APP_ABI not defined.$(
  echo
  usage
)"}
readonly ANDROID_NDK_DIR=${ANDROID_NDK_DIR:?"ANDROID_NDK_DIR is not defined.$(
  echo
  usage
)"}
readonly BUILD_DIR="${WORKSPACE}/build-${BUILD_TYPE}"
readonly STANDALONE_ANDROID_DIR="${WORKSPACE}/android"

if [[ ! -x "${ANDROID_NDK_DIR}/ndk-build" ]]; then
  log_err "unable to find ndk-build in ANDROID_NDK_DIR: ${ANDROID_NDK_DIR}."
  exit 1
fi

CFLAGS=
LDFLAGS=
opts=()
case "${BUILD_TYPE}" in
  *debug)
    readonly APP_OPTIM="debug"
    CFLAGS="-O0 -g"
    opts+=("--enable-asserts")
    ;;
  static* | shared*)
    readonly APP_OPTIM="release"
    CFLAGS="-O2 -g"
    ;;
  *)
    usage
    exit 1
    ;;
esac

case "${BUILD_TYPE}" in
  shared*) readonly SHARED="1" ;;
  *)
    readonly SHARED="0"
    CFLAGS="${CFLAGS} -fPIE"
    LDFLAGS="${LDFLAGS} -Wl,-pie"
    opts+=("--disable-shared")
    ;;
esac

# Create a fresh build directory
make_build_dir "${BUILD_DIR}"
cd "${BUILD_DIR}"
ln -s "${LIBWEBP_ROOT}" jni

"${ANDROID_NDK_DIR}/ndk-build" -j2 \
  APP_ABI="${APP_ABI}" \
  APP_OPTIM="${APP_OPTIM}" \
  ENABLE_SHARED="${SHARED}"

cd "${LIBWEBP_ROOT}"
./autogen.sh

case "${APP_ABI}" in
  armeabi*) arch="arm" ;;
  arm64*) arch="arm64" ;;
  *) arch="${APP_ABI}" ;;
esac
# TODO(b/185520507): remove this and use the binaries from
# toolchains/llvm/prebuilt/ directly.
rm -rf "${STANDALONE_ANDROID_DIR}"
"${ANDROID_NDK_DIR}/build/tools/make_standalone_toolchain.py" \
  --api 24 --arch "${arch}" --stl gnustl --install-dir \
  "${STANDALONE_ANDROID_DIR}"
export PATH="${STANDALONE_ANDROID_DIR}/bin:${PATH}"

rm -rf "${BUILD_DIR}"
make_build_dir "${BUILD_DIR}"
cd "${BUILD_DIR}"

case "${arch}" in
  arm)
    host="arm-linux-androideabi"
    case "${APP_ABI}" in
      armeabi) ;;
      armeabi-v7a)
        CFLAGS="${CFLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp"
        ;;
      *) ;;  # No configuration needed
    esac
    ;;
  arm64)
    host="aarch64-linux-android"
    ;;
  x86)
    host="i686-linux-android"
    ;;
  x86_64)
    host="x86_64-linux-android"
    ;;
  *) ;;  # Skip configuration
esac

setup_ccache
CC="clang"

"${LIBWEBP_ROOT}/configure" --host "${host}" --build \
  "$("${LIBWEBP_ROOT}/config.guess")" CC="${CC}" CFLAGS="${CFLAGS}" \
  LDFLAGS="${LDFLAGS}" "${opts[@]}"
make -j

if [[ "${GERRIT_REFSPEC:-}" = "refs/heads/portable-intrinsics" ]] \
  || [[ "${GERRIT_BRANCH:-}" = "portable-intrinsics" ]]; then
  cd "${WORKSPACE}"
  rm -rf build && mkdir build
  cd build
  standalone="${WORKSPACE}/android"
  cmake ../libwebp \
    -DWEBP_BUILD_DWEBP=1 \
    -DCMAKE_C_COMPILER="${standalone}/bin/clang" \
    -DCMAKE_PREFIX_PATH="${standalone}/sysroot/usr/lib" \
    -DCMAKE_C_FLAGS=-fPIE \
    -DCMAKE_EXE_LINKER_FLAGS=-Wl,-pie \
    -DCMAKE_BUILD_TYPE=Release \
    -DWEBP_ENABLE_WASM=1
  make -j2

  cd "${WORKSPACE}"
  make_build_dir "${BUILD_DIR}"
  cd "${BUILD_DIR}"
  case "${APP_ABI}" in
    armeabi-v7a | arm64*)
      cmake "${LIBWEBP_ROOT}" \
        -DWEBP_BUILD_DWEBP=1 \
        -DCMAKE_C_COMPILER="${standalone}/bin/clang" \
        -DCMAKE_PREFIX_PATH="${standalone}/sysroot/usr/lib" \
        -DCMAKE_C_FLAGS='-fPIE -DENABLE_NEON_BUILTIN_MULHI_INT16X8' \
        -DCMAKE_EXE_LINKER_FLAGS=-Wl,-pie \
        -DCMAKE_BUILD_TYPE=Release \
        -DWEBP_ENABLE_WASM=1
      make -j2
      ;;
    x86*)
      cmake "${LIBWEBP_ROOT}" \
        -DWEBP_BUILD_DWEBP=1 \
        -DCMAKE_C_COMPILER="${standalone}/bin/clang" \
        -DCMAKE_PREFIX_PATH="${standalone}/sysroot/usr/lib" \
        -DCMAKE_C_FLAGS='-fPIE -DENABLE_X86_BUILTIN_MULHI_INT16X8' \
        -DCMAKE_EXE_LINKER_FLAGS=-Wl,-pie \
        -DCMAKE_BUILD_TYPE=Release \
        -DWEBP_ENABLE_WASM=1
      make -j2
      ;;
    *)
      log_err "APP_ABI not supported."
      exit 1
      ;;
  esac
fi

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
WORKSPACE=${WORKSPACE:-"$(mktemp -d -t webp.XXX)"}

# shellcheck source=infra/common.sh
source "${LIBWEBP_ROOT}/infra/common.sh"

usage() {
  cat << EOF
Usage: compile.sh BUILD_TYPE TARGET
Options:
BUILD_TYPE  supported build type: (shared, static, static-debug)
TARGET      supported target platforms:
              aarch64-linux-clang
              aarch64-linux-gnu
              arm-linux-gnueabi
              arm-neon-linux-gnueabi
              cmake
              cmake-aarch64
              cmake-arm
              cmake-clang
              disable-near-lossless
              disable-sse4.1
              disable-stats
              force-aligned-32
              force-aligned-64
              gradle
              i686-linux-asan
              i686-linux-clang
              i686-linux-gnu
              i686-w64-mingw32
              mips2el-linux-gnu
              mips32dspr2el-linux-gnu
              mips32eb-linux-gnu
              mips32el-linux-gnu
              mips32r2el-linux-gnu
              mips32r5el-linux-gnu
              mips64r2el-linux-gnu
              mips64r6el-linux-gnu
              native
              reduce-csp
              reduce-size
              reduce-size-disable-stats
              visibility-default-gnu
              visibility-hidden-clang
              visibility-hidden-gnu
              wasm
              x86_64-linux-clang
              x86_64-linux-gnu
              x86_64-linux-msan
              x86_64-w64-mingw32
Environment variables:
WORKSPACE   directory where the build is done
EOF
}

################################################################################
echo "Building libwebp in ${WORKSPACE}"

if [[ ! -d "${WORKSPACE}" ]]; then
  log_err "${WORKSPACE} directory does not exist"
  exit 1
fi

BUILD_TYPE=${1:?"Build type not defined.$(
  echo
  usage
)"}
TARGET=${2:?"Target not defined.$(
  echo
  usage
)"}
readonly BUILD_DIR="${WORKSPACE}/build-${BUILD_TYPE}"

trap 'cleanup ${BUILD_DIR}' EXIT
make_build_dir "${BUILD_DIR}"

config_flags=()
case "${BUILD_TYPE}" in
  shared*) ;;  # Valid BUILD_TYPE but no setup required
  static*) config_flags+=("--disable-shared") ;;
  experimental) config_flags+=("--enable-experimental") ;;
  *)
    log_err "Invalid BUILD_TYPE"
    usage
    exit 1
    ;;
esac

if grep -m 1 -q "enable-asserts" "${LIBWEBP_ROOT}/configure.ac"; then
  config_flags+=("--enable-asserts")
fi

case "${TARGET}" in
  aarch64-linux-clang)
    TARGET="aarch64-linux-gnu"
    CC="clang"
    CC="${CC} --target=aarch64-linux-gnu"
    export CC
    export CFLAGS="-isystem /usr/aarch64-linux-gnu/include"
    ;;
  arm-linux-gnueabi)
    export CFLAGS="-O3 -march=armv7-a -mfloat-abi=softfp -ftree-vectorize"
    ;;
  arm-neon-linux-gnueabi)
    TARGET="arm-linux-gnueabi"
    CFLAGS="-O3 -march=armv7-a -mfpu=neon -mfloat-abi=softfp -ftree-vectorize"
    export CFLAGS
    ;;
  mips2el-linux-gnu)
    export CFLAGS="-EL -O2 -mips2"
    TARGET="mipsel-linux-gnu"
    ;;
  mips32el-linux-gnu)
    export CFLAGS="-EL -O2 -mips32"
    TARGET="mipsel-linux-gnu"
    ;;
  mips32r2el-linux-gnu)
    export CFLAGS="-EL -O2 -mips32r2"
    TARGET="mipsel-linux-gnu"
    ;;
  mips32dspr2el-linux-gnu)
    export CFLAGS="-EL -O2 -mdspr2"
    TARGET="mipsel-linux-gnu"
    ;;
  mips32r5el-linux-gnu)
    export CFLAGS="-EL -O2 -mips32r5 -mmsa"
    TARGET="mipsel-linux-gnu"
    ;;
  mips32eb-linux-gnu)
    export CFLAGS="-EB -O2 -mips32"
    TARGET="mips-linux-gnu"
    ;;
  mips64r2el-linux-gnu)
    export CFLAGS="-EL -O2 -mips64r2 -mabi=64"
    TARGET="mips64el-linux-gnuabi64"
    ;;
  mips64r6el-linux-gnu)
    export CFLAGS="-EL -O2 -mips64r6 -mabi=64 -mmsa"
    TARGET="mips-img-linux-gnu"
    ;;
  i686-linux-gnu)
    export CC="gcc -m32"
    ;;
  i686-linux-clang)
    TARGET="i686-linux-gnu"
    export CC="clang -m32"
    ;;
  i686-linux-asan)
    TARGET="i686-linux-gnu"
    export CC="clang -m32 -fsanitize=address"
    ;;
  i686-linux-msan)
    TARGET="i686-linux-gnu"
    export CC="clang -m32 -fsanitize=memory"
    ;;
  x86_64-linux-clang)
    TARGET="x86_64-linux-gnu"
    export CC=clang
    ;;
  x86_64-linux-msan)
    TARGET="x86_64-linux-gnu"
    export CC="clang -fsanitize=memory"
    ;;
  force-aligned-32)
    config_flags+=("--enable-aligned")
    TARGET="i686-linux-gnu"
    export CC="gcc -m32"
    ;;
  force-aligned-64)
    config_flags+=("--enable-aligned")
    TARGET="x86_64-linux-gnu"
    ;;
  visibility-default-*)
    export CFLAGS="-O2 -g -fvisibility=default"
    TARGET="x86_64-linux-gnu"
    ;;
  visibility-hidden-*)
    export CFLAGS="-O2 -g -fvisibility=hidden"
    if [[ "${TARGET}" = "visibility-hidden-clang" ]]; then
      export CC=clang
    fi
    TARGET="x86_64-linux-gnu"
    ;;
  disable-sse4.1)
    grep "${TARGET}" "${LIBWEBP_ROOT}/configure.ac" || exit 0
    config_flags+=("--${TARGET}")
    TARGET="x86_64-linux-gnu"
    ;;
  disable-near-lossless)
    grep "${TARGET}" "${LIBWEBP_ROOT}/configure.ac" || exit 0
    config_flags+=("--${TARGET}")
    TARGET="x86_64-linux-gnu"
    ;;
  disable-stats)
    git -C "${LIBWEBP_ROOT}" grep WEBP_DISABLE_STATS || exit 0
    export CFLAGS="-O2 -g -DWEBP_DISABLE_STATS"
    TARGET="x86_64-linux-gnu"
    ;;
  reduce-size)
    git -C "${LIBWEBP_ROOT}" grep WEBP_REDUCE_SIZE || exit 0
    export CFLAGS="-O2 -g -DWEBP_REDUCE_SIZE"
    TARGET="x86_64-linux-gnu"
    ;;
  reduce-size-disable-stats)
    git -C "${LIBWEBP_ROOT}" grep -e WEBP_DISABLE_STATS -e WEBP_REDUCE_SIZE \
      || exit 0
    export CFLAGS="-O2 -g -DWEBP_DISABLE_STATS -DWEBP_REDUCE_SIZE"
    TARGET="x86_64-linux-gnu"
    ;;
  reduce-csp)
    git -C "${LIBWEBP_ROOT}" grep WEBP_REDUCE_CSP || exit 0
    export CFLAGS="-O2 -g -DWEBP_REDUCE_CSP"
    TARGET="x86_64-linux-gnu"
    ;;
  x86_64-linux-gnu | *mingw32 | aarch64*) ;;  # Default target configuration
  # non-configure based builds
  native)
    setup_ccache
    # exercise makefile.unix then quit
    make -C "${LIBWEBP_ROOT}" -f makefile.unix -j all
    for tgt in extras examples/anim_diff; do
      grep -q -m 1 "${tgt}" "${LIBWEBP_ROOT}/makefile.unix" \
        && make -C "${LIBWEBP_ROOT}" -f makefile.unix -j "${tgt}"
    done
    [[ -d "${LIBWEBP_ROOT}/tests/fuzzer" ]] \
      && make -j -C "${LIBWEBP_ROOT}/tests/fuzzer" -f makefile.unix
    exit 0
    ;;
  cmake*)
    setup_ccache
    # exercise cmake then quit
    opts=()
    case "${TARGET}" in
      cmake-clang)
        opts+=("-DCMAKE_C_COMPILER=clang")
        ;;
      cmake-arm)
        opts+=("-DCMAKE_C_COMPILER=arm-linux-gnueabi-gcc")
        case "${GERRIT_BRANCH:-}" in
          portable-intrinsics | 0.6.1) exit 0 ;;
          *) ;;  # Skip configuration
        esac
        ;;
      cmake-aarch64)
        opts+=("-DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc")
        case "${GERRIT_BRANCH:-}" in
          portable-intrinsics | 0.6.1) exit 0 ;;
          *) ;;  # Skip configuration
        esac
        ;;
      *) ;;  # Skip configuration
    esac
    case "${BUILD_TYPE}" in
      static*)
        opts+=("-DBUILD_SHARED_LIBS=OFF")
        ;;
      experimental)
        opts+=("-DWEBP_EXPERIMENTAL_FEATURES=ON" "-DBUILD_SHARED_LIBS=ON")
        ;;
      *)
        opts+=("-DBUILD_SHARED_LIBS=ON")
        ;;
    esac
    case "${BUILD_TYPE}" in
      *debug) opts+=("-DCMAKE_BUILD_TYPE=Debug") ;;
      *) opts+=("-DCMAKE_BUILD_TYPE=RelWithDebInfo") ;;
    esac
    cd "${BUILD_DIR}"
    opts+=("-DWEBP_BUILD_CWEBP=ON" "-DWEBP_BUILD_DWEBP=ON")
    grep -m 1 -q WEBP_BUILD_GIF2WEBP "${LIBWEBP_ROOT}/CMakeLists.txt" \
      && opts+=("-DWEBP_BUILD_GIF2WEBP=ON")
    grep -m 1 -q WEBP_BUILD_IMG2WEBP "${LIBWEBP_ROOT}/CMakeLists.txt" \
      && opts+=("-DWEBP_BUILD_IMG2WEBP=ON")
    cmake "${opts[@]}" "${LIBWEBP_ROOT}"
    make VERBOSE=1 -j
    case "${BUILD_TYPE}" in
      static)
        mkdir -p examples
        cp [cd]webp examples
        ;;
      *) ;;  # Skip configuration.
    esac

    grep "install" "${LIBWEBP_ROOT}/CMakeLists.txt" || exit 0

    make DESTDIR="${BUILD_DIR}/webp-install" install/strip
    mkdir tmp
    cd tmp
    cat > CMakeLists.txt << EOF
cmake_minimum_required(VERSION 2.8.7)

project(libwebp C)

find_package(WebP)
if (NOT WebP_FOUND)
  message(FATAL_ERROR "WebP package not found")
endif ()
message("WebP_FOUND: \${WebP_FOUND}")
message("WebP_INCLUDE_DIRS: \${WebP_INCLUDE_DIRS}")
message("WebP_LIBRARIES: \${WebP_LIBRARIES}")
message("WEBP_INCLUDE_DIRS: \${WEBP_INCLUDE_DIRS}")
message("WEBP_LIBRARIES: \${WEBP_LIBRARIES}")
EOF
    cmake . "${opts[@]}" \
      "-DCMAKE_PREFIX_PATH=${BUILD_DIR}/webp-install/usr/local"
    exit 0
    ;;
  gradle)
    setup_ccache
    # exercise gradle then quit
    [[ -f "${LIBWEBP_ROOT}/gradlew" ]] || exit 0

    cd "${BUILD_DIR}"
    # TODO -g / --gradle-user-home could be used if there's a race between jobs
    "${LIBWEBP_ROOT}/gradlew" -p "${LIBWEBP_ROOT}" buildAllExecutables
    exit 0
    ;;
  wasm)
    grep -m 1 -q WEBP_ENABLE_WASM "${LIBWEBP_ROOT}/CMakeLists.txt" || exit 0
    opts+=("-DCMAKE_C_COMPILER=clang" "-DWEBP_ENABLE_WASM=ON")
    opts+=("-DWEBP_BUILD_CWEBP=ON" "-DWEBP_BUILD_DWEBP=ON")
    case "${BUILD_TYPE}" in
      *debug) opts+=("-DCMAKE_BUILD_TYPE=Debug") ;;
      *) opts+=("-DCMAKE_BUILD_TYPE=RelWithDebInfo") ;;
    esac
    cd "${BUILD_DIR}"
    cmake "${opts[@]}" "${LIBWEBP_ROOT}"
    make VERBOSE=1 -j
    mkdir examples
    case "${BUILD_TYPE}" in
      static)
        mkdir -p examples
        cp [cd]webp examples
        ;;
      *) ;;  # Skip configuration
    esac
    exit 0
    ;;
  *)
    log_err "Invalid TARGET"
    usage
    exit 1
    ;;
esac

case "${TARGET}" in
  *mingw32) ;;  # Skip configuration
  *)
    case "${TARGET}-${CC}" in
      static-debug-gcc* | static-debug-)
        CFLAGS="${CFLAGS} -fprofile-arcs -ftest-coverage -O0 -g"
        CXXFLAGS="${CXXFLAGS} -fprofile-arcs -ftest-coverage -O0 -g"
        export CFLAGS CXXFLAGS
        ;;
      *) ;;  # This case should not be reached.
    esac
    ;;
esac

setup_ccache

cd "${LIBWEBP_ROOT}"
./autogen.sh

cd "${BUILD_DIR}"
"${LIBWEBP_ROOT}/configure" \
  --host "${TARGET}" --build "$("${LIBWEBP_ROOT}/config.guess")" \
  --enable-everything "${config_flags[@]}"
make -j V=1

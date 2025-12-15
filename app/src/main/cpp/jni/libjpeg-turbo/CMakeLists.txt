# Copyright 2018 Hippo Seven
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 3.14)
project(libjpeg-turbo C)

set(LIBJPEG_TURBO_SOURCES
    libjpeg-turbo/jaricom.c
    libjpeg-turbo/jcomapi.c
    libjpeg-turbo/jdapimin.c
    libjpeg-turbo/jdapistd.c
    libjpeg-turbo/jdarith.c
    libjpeg-turbo/jdatasrc.c
    libjpeg-turbo/jdatasrc-tj.c
    libjpeg-turbo/jdcoefct.c
    libjpeg-turbo/jdcolor.c
    libjpeg-turbo/jddctmgr.c
    libjpeg-turbo/jdhuff.c
    libjpeg-turbo/jdinput.c
    libjpeg-turbo/jdmainct.c
    libjpeg-turbo/jdmarker.c
    libjpeg-turbo/jdmaster.c
    libjpeg-turbo/jdmerge.c
    libjpeg-turbo/jdphuff.c
    libjpeg-turbo/jdpostct.c
    libjpeg-turbo/jdsample.c
    libjpeg-turbo/jdtrans.c
    libjpeg-turbo/jerror.c
    libjpeg-turbo/jfdctflt.c
    libjpeg-turbo/jfdctfst.c
    libjpeg-turbo/jfdctint.c
    libjpeg-turbo/jidctflt.c
    libjpeg-turbo/jidctfst.c
    libjpeg-turbo/jidctint.c
    libjpeg-turbo/jidctred.c
    libjpeg-turbo/jmemmgr.c
    libjpeg-turbo/jmemnobs.c
    libjpeg-turbo/jquant1.c
    libjpeg-turbo/jquant2.c
    libjpeg-turbo/jutils.c
)
set(LIBJPEG_TURBO_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")

  enable_language(ASM)

  set(LIBJPEG_TURBO_SOURCES
      ${LIBJPEG_TURBO_SOURCES}
      libjpeg-turbo/simd/arm/jsimd.c
      libjpeg-turbo/simd/arm/jsimd_neon.S
  )

elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")

  enable_language(ASM)

  set(LIBJPEG_TURBO_SOURCES
      ${LIBJPEG_TURBO_SOURCES}
      libjpeg-turbo/simd/arm64/jsimd.c
      libjpeg-turbo/simd/arm64/jsimd_neon.S
  )

elseif (${ANDROID_ABI} STREQUAL "x86")

  enable_language(ASM_NASM)

  set(LIBJPEG_TURBO_SOURCES
      ${LIBJPEG_TURBO_SOURCES}
      libjpeg-turbo/simd/i386/jsimd.c
      libjpeg-turbo/simd/i386/jsimdcpu.asm
      libjpeg-turbo/simd/i386/jfdctflt-3dn.asm
      libjpeg-turbo/simd/i386/jidctflt-3dn.asm
      libjpeg-turbo/simd/i386/jquant-3dn.asm
      libjpeg-turbo/simd/i386/jccolor-mmx.asm
      libjpeg-turbo/simd/i386/jcgray-mmx.asm
      libjpeg-turbo/simd/i386/jcsample-mmx.asm
      libjpeg-turbo/simd/i386/jdcolor-mmx.asm
      libjpeg-turbo/simd/i386/jdmerge-mmx.asm
      libjpeg-turbo/simd/i386/jdsample-mmx.asm
      libjpeg-turbo/simd/i386/jfdctfst-mmx.asm
      libjpeg-turbo/simd/i386/jfdctint-mmx.asm
      libjpeg-turbo/simd/i386/jidctfst-mmx.asm
      libjpeg-turbo/simd/i386/jidctint-mmx.asm
      libjpeg-turbo/simd/i386/jidctred-mmx.asm
      libjpeg-turbo/simd/i386/jquant-mmx.asm
      libjpeg-turbo/simd/i386/jfdctflt-sse.asm
      libjpeg-turbo/simd/i386/jidctflt-sse.asm
      libjpeg-turbo/simd/i386/jquant-sse.asm
      libjpeg-turbo/simd/i386/jccolor-sse2.asm
      libjpeg-turbo/simd/i386/jcgray-sse2.asm
      libjpeg-turbo/simd/i386/jchuff-sse2.asm
      libjpeg-turbo/simd/i386/jcphuff-sse2.asm
      libjpeg-turbo/simd/i386/jcsample-sse2.asm
      libjpeg-turbo/simd/i386/jdcolor-sse2.asm
      libjpeg-turbo/simd/i386/jdmerge-sse2.asm
      libjpeg-turbo/simd/i386/jdsample-sse2.asm
      libjpeg-turbo/simd/i386/jfdctfst-sse2.asm
      libjpeg-turbo/simd/i386/jfdctint-sse2.asm
      libjpeg-turbo/simd/i386/jidctflt-sse2.asm
      libjpeg-turbo/simd/i386/jidctfst-sse2.asm
      libjpeg-turbo/simd/i386/jidctint-sse2.asm
      libjpeg-turbo/simd/i386/jidctred-sse2.asm
      libjpeg-turbo/simd/i386/jquantf-sse2.asm
      libjpeg-turbo/simd/i386/jquanti-sse2.asm
      libjpeg-turbo/simd/i386/jccolor-avx2.asm
      libjpeg-turbo/simd/i386/jcgray-avx2.asm
      libjpeg-turbo/simd/i386/jcsample-avx2.asm
      libjpeg-turbo/simd/i386/jdcolor-avx2.asm
      libjpeg-turbo/simd/i386/jdmerge-avx2.asm
      libjpeg-turbo/simd/i386/jdsample-avx2.asm
      libjpeg-turbo/simd/i386/jfdctint-avx2.asm
      libjpeg-turbo/simd/i386/jidctint-avx2.asm
      libjpeg-turbo/simd/i386/jquanti-avx2.asm
  )

  set(CMAKE_ASM_NASM_FLAGS "-DELF -DPIC -I${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/simd/nasm")

elseif (${ANDROID_ABI} STREQUAL "x86_64")

  enable_language(ASM_NASM)

  set(LIBJPEG_TURBO_SOURCES
      ${LIBJPEG_TURBO_SOURCES}
      libjpeg-turbo/simd/x86_64/jsimd.c
      libjpeg-turbo/simd/x86_64/jsimdcpu.asm
      libjpeg-turbo/simd/x86_64/jfdctflt-sse.asm
      libjpeg-turbo/simd/x86_64/jccolor-sse2.asm
      libjpeg-turbo/simd/x86_64/jcgray-sse2.asm
      libjpeg-turbo/simd/x86_64/jchuff-sse2.asm
      libjpeg-turbo/simd/x86_64/jcphuff-sse2.asm
      libjpeg-turbo/simd/x86_64/jcsample-sse2.asm
      libjpeg-turbo/simd/x86_64/jdcolor-sse2.asm
      libjpeg-turbo/simd/x86_64/jdmerge-sse2.asm
      libjpeg-turbo/simd/x86_64/jdsample-sse2.asm
      libjpeg-turbo/simd/x86_64/jfdctfst-sse2.asm
      libjpeg-turbo/simd/x86_64/jfdctint-sse2.asm
      libjpeg-turbo/simd/x86_64/jidctflt-sse2.asm
      libjpeg-turbo/simd/x86_64/jidctfst-sse2.asm
      libjpeg-turbo/simd/x86_64/jidctint-sse2.asm
      libjpeg-turbo/simd/x86_64/jidctred-sse2.asm
      libjpeg-turbo/simd/x86_64/jquantf-sse2.asm
      libjpeg-turbo/simd/x86_64/jquanti-sse2.asm
      libjpeg-turbo/simd/x86_64/jccolor-avx2.asm
      libjpeg-turbo/simd/x86_64/jcgray-avx2.asm
      libjpeg-turbo/simd/x86_64/jcsample-avx2.asm
      libjpeg-turbo/simd/x86_64/jdcolor-avx2.asm
      libjpeg-turbo/simd/x86_64/jdmerge-avx2.asm
      libjpeg-turbo/simd/x86_64/jdsample-avx2.asm
      libjpeg-turbo/simd/x86_64/jfdctint-avx2.asm
      libjpeg-turbo/simd/x86_64/jidctint-avx2.asm
      libjpeg-turbo/simd/x86_64/jquanti-avx2.asm
  )

  set(CMAKE_ASM_NASM_FLAGS "-D__x86_64__ -DELF -DPIC -I${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/simd/nasm")

else ()

  set(LIBJPEG_TURBO_SOURCES
      ${LIBJPEG_TURBO_SOURCES}
      libjpeg-turbo/jsimd_none.c
  )

endif ()

add_library(libjpeg-turbo STATIC ${LIBJPEG_TURBO_SOURCES})
target_include_directories(libjpeg-turbo PUBLIC ${LIBJPEG_TURBO_INCLUDES})

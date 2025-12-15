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
project(libpng C)

set(LIBPNG_SOURCES
    libpng/png.c
    libpng/pngerror.c
    libpng/pngget.c
    libpng/pngmem.c
    libpng/pngpread.c
    libpng/pngread.c
    libpng/pngrio.c
    libpng/pngrtran.c
    libpng/pngrutil.c
    libpng/pngset.c
    libpng/pngtrans.c
)
set(LIBPNG_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/libpng
)
set(LIBPNG_DEFINITIONS)
set(LIBPNG_LIBRARIES z)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")

  enable_language(ASM)

  set(LIBPNG_SOURCES
      ${LIBPNG_SOURCES}
      libpng/arm/arm_init.c
      libpng/arm/filter_neon.S
  )
  set(LIBPNG_INCLUDES
      ${LIBPNG_INCLUDES}
      ${ANDROID_NDK}/sources/android/cpufeatures
  )
  set(LIBPNG_DEFINITIONS
      ${LIBPNG_DEFINITIONS}
      -DPNG_ARM_NEON_API_SUPPORTED
      -DPNG_ARM_NEON_CHECK_SUPPORTED
      -DPNG_ARM_NEON_OPT=1
      -DPNG_ARM_NEON_IMPLEMENTATION=2
      -DPNG_ARM_NEON_FILE=\"contrib/arm-neon/android-ndk.c\"
  )
  set(LIBPNG_LIBRARIES
      ${LIBPNG_LIBRARIES}
      cpufeatures
  )

elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")

  enable_language(ASM)

  set(LIBPNG_SOURCES
      ${LIBPNG_SOURCES}
      libpng/arm/arm_init.c
      libpng/arm/filter_neon_intrinsics.c
      libpng/arm/palette_neon_intrinsics.c
  )
  set(LIBPNG_INCLUDES
      ${LIBPNG_INCLUDES}
      ${ANDROID_NDK}/sources/android/cpufeatures
  )
  set(LIBPNG_DEFINITIONS
      ${LIBPNG_DEFINITIONS}
      -DPNG_ARM_NEON_API_SUPPORTED
      -DPNG_ARM_NEON_CHECK_SUPPORTED
      -DPNG_ARM_NEON_OPT=1
      -DPNG_ARM_NEON_IMPLEMENTATION=1
      -DPNG_ARM_NEON_FILE=\"contrib/arm-neon/android-ndk.c\"
  )
  set(LIBPNG_LIBRARIES
      ${LIBPNG_LIBRARIES}
      cpufeatures
  )

endif ()

add_library(libpng STATIC ${LIBPNG_SOURCES})
target_include_directories(libpng PUBLIC ${LIBPNG_INCLUDES})
target_compile_definitions(libpng PRIVATE ${LIBPNG_DEFINITIONS})
target_link_libraries(libpng PRIVATE ${LIBPNG_LIBRARIES})

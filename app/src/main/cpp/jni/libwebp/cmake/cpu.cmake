#  Copyright (c) 2021 Google LLC.
#
#  Use of this source code is governed by a BSD-style license
#  that can be found in the LICENSE file in the root of the source
#  tree. An additional intellectual property rights grant can be found
#  in the file PATENTS.  All contributing project authors may
#  be found in the AUTHORS file in the root of the source tree.

# Check for SIMD extensions.
include(CMakePushCheckState)

function(webp_check_compiler_flag WEBP_SIMD_FLAG ENABLE_SIMD)
  if(NOT ENABLE_SIMD)
    message(STATUS "Disabling ${WEBP_SIMD_FLAG} optimization.")
    set(WEBP_HAVE_${WEBP_SIMD_FLAG} 0 PARENT_SCOPE)
    return()
  endif()
  unset(WEBP_HAVE_FLAG_${WEBP_SIMD_FLAG} CACHE)
  cmake_push_check_state()
  set(CMAKE_REQUIRED_INCLUDES ${CMAKE_CURRENT_SOURCE_DIR})
  check_c_source_compiles(
    "
      #include \"${CMAKE_CURRENT_LIST_DIR}/../src/dsp/dsp.h\"
      int main(void) {
        #if !defined(WEBP_USE_${WEBP_SIMD_FLAG})
        this is not valid code
        #endif
        return 0;
      }
    "
    WEBP_HAVE_FLAG_${WEBP_SIMD_FLAG})
  cmake_pop_check_state()
  if(WEBP_HAVE_FLAG_${WEBP_SIMD_FLAG})
    set(WEBP_HAVE_${WEBP_SIMD_FLAG} 1 PARENT_SCOPE)
  else()
    set(WEBP_HAVE_${WEBP_SIMD_FLAG} 0 PARENT_SCOPE)
  endif()
endfunction()

# those are included in the names of WEBP_USE_* in c++ code.
set(WEBP_SIMD_FLAGS "SSE41;SSE2;MIPS32;MIPS_DSP_R2;NEON;MSA")
set(WEBP_SIMD_FILE_EXTENSIONS
    "_sse41.c;_sse2.c;_mips32.c;_mips_dsp_r2.c;_neon.c;_msa.c")
if(MSVC AND CMAKE_C_COMPILER_ID STREQUAL "MSVC")
  # With at least Visual Studio 12 (2013)+ /arch is not necessary to build SSE2
  # or SSE4 code unless a lesser /arch is forced. MSVC does not have a SSE4
  # flag, but an AVX one. Using that with SSE4 code risks generating illegal
  # instructions when used on machines with SSE4 only. The flags are left for
  # older (untested) versions to avoid any potential compatibility issues.
  if(MSVC_VERSION GREATER_EQUAL 1800 AND NOT CMAKE_C_FLAGS MATCHES "/arch:")
    set(SIMD_ENABLE_FLAGS)
  else()
    set(SIMD_ENABLE_FLAGS "/arch:AVX;/arch:SSE2;;;;")
  endif()
  set(SIMD_DISABLE_FLAGS)
else()
  set(SIMD_ENABLE_FLAGS "-msse4.1;-msse2;-mips32;-mdspr2;-mfpu=neon;-mmsa")
  set(SIMD_DISABLE_FLAGS "-mno-sse4.1;-mno-sse2;;-mno-dspr2;;-mno-msa")
endif()

set(WEBP_SIMD_FILES_TO_INCLUDE)
set(WEBP_SIMD_FLAGS_TO_INCLUDE)

if(ANDROID AND ANDROID_ABI)
  if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    # This is because Android studio uses the configuration "-march=armv7-a
    # -mfloat-abi=softfp -mfpu=vfpv3-d16" that does not trigger neon
    # optimizations but should (as this configuration does not exist anymore).
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon ")
  endif()
endif()

list(LENGTH WEBP_SIMD_FLAGS WEBP_SIMD_FLAGS_LENGTH)
math(EXPR WEBP_SIMD_FLAGS_RANGE "${WEBP_SIMD_FLAGS_LENGTH} - 1")

foreach(I_SIMD RANGE ${WEBP_SIMD_FLAGS_RANGE})
  # With Emscripten 2.0.9 -msimd128 -mfpu=neon will enable NEON, but the source
  # will fail to compile.
  if(EMSCRIPTEN AND ${I_SIMD} GREATER_EQUAL 2)
    break()
  endif()

  list(GET WEBP_SIMD_FLAGS ${I_SIMD} WEBP_SIMD_FLAG)

  # First try with no extra flag added as the compiler might have default flags
  # (especially on Android).
  unset(WEBP_HAVE_${WEBP_SIMD_FLAG} CACHE)
  cmake_push_check_state()
  set(CMAKE_REQUIRED_FLAGS)
  webp_check_compiler_flag(${WEBP_SIMD_FLAG} ${WEBP_ENABLE_SIMD})
  if(NOT WEBP_HAVE_${WEBP_SIMD_FLAG})
    list(GET SIMD_ENABLE_FLAGS ${I_SIMD} SIMD_COMPILE_FLAG)
    if(EMSCRIPTEN)
      set(SIMD_COMPILE_FLAG "-msimd128 ${SIMD_COMPILE_FLAG}")
    endif()
    set(CMAKE_REQUIRED_FLAGS ${SIMD_COMPILE_FLAG})
    webp_check_compiler_flag(${WEBP_SIMD_FLAG} ${WEBP_ENABLE_SIMD})
  else()
    if(MSVC AND SIMD_ENABLE_FLAGS)
      # The detection for SSE2/SSE4 support under MSVC is based on the compiler
      # version so e.g., clang-cl will require flags to enable the assembly.
      list(GET SIMD_ENABLE_FLAGS ${I_SIMD} SIMD_COMPILE_FLAG)
    else()
      set(SIMD_COMPILE_FLAG " ")
    endif()
  endif()
  # Check which files we should include or not.
  list(GET WEBP_SIMD_FILE_EXTENSIONS ${I_SIMD} WEBP_SIMD_FILE_EXTENSION)
  file(GLOB SIMD_FILES
       "${CMAKE_CURRENT_LIST_DIR}/../sharpyuv/*${WEBP_SIMD_FILE_EXTENSION}"
       "${CMAKE_CURRENT_LIST_DIR}/../src/dsp/*${WEBP_SIMD_FILE_EXTENSION}")
  if(WEBP_HAVE_${WEBP_SIMD_FLAG})
    # Memorize the file and flags.
    foreach(FILE ${SIMD_FILES})
      list(APPEND WEBP_SIMD_FILES_TO_INCLUDE ${FILE})
      list(APPEND WEBP_SIMD_FLAGS_TO_INCLUDE ${SIMD_COMPILE_FLAG})
    endforeach()
  else()
    # Remove the file from the list.
    foreach(FILE ${SIMD_FILES})
      list(APPEND WEBP_SIMD_FILES_NOT_TO_INCLUDE ${FILE})
    endforeach()
    # Explicitly disable SIMD.
    if(SIMD_DISABLE_FLAGS)
      list(GET SIMD_DISABLE_FLAGS ${I_SIMD} SIMD_COMPILE_FLAG)
      include(CheckCCompilerFlag)
      if(SIMD_COMPILE_FLAG)
        # Between 3.17.0 and 3.18.2 check_cxx_compiler_flag() sets a normal
        # variable at parent scope while check_cxx_source_compiles() continues
        # to set an internal cache variable, so we unset both to avoid the
        # failure / success state persisting between checks. See
        # https://gitlab.kitware.com/cmake/cmake/-/issues/21207.
        unset(HAS_COMPILE_FLAG)
        unset(HAS_COMPILE_FLAG CACHE)
        check_c_compiler_flag(${SIMD_COMPILE_FLAG} HAS_COMPILE_FLAG)
        if(HAS_COMPILE_FLAG)
          # Do one more check for Clang to circumvent CMake issue 13194.
          if(COMMAND check_compiler_flag_common_patterns)
            # Only in CMake 3.0 and above.
            check_compiler_flag_common_patterns(COMMON_PATTERNS)
          else()
            set(COMMON_PATTERNS)
          endif()
          set(CMAKE_REQUIRED_DEFINITIONS ${SIMD_COMPILE_FLAG})
          check_c_source_compiles(
            "int main(void) {return 0;}" FLAG_${SIMD_COMPILE_FLAG} FAIL_REGEX
            "warning: argument unused during compilation:" ${COMMON_PATTERNS})
          if(NOT FLAG_${SIMD_COMPILE_FLAG})
            unset(HAS_COMPILE_FLAG)
            unset(HAS_COMPILE_FLAG CACHE)
          endif()
        endif()
        if(HAS_COMPILE_FLAG)
          set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SIMD_COMPILE_FLAG}")
        endif()
      endif()
    endif()
  endif()
  cmake_pop_check_state()
endforeach()

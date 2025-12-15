cmake_minimum_required(VERSION 2.8.12)

if(CMAKE_EXECUTABLE_SUFFIX)
  set(CMAKE_EXECUTABLE_SUFFIX_TMP ${CMAKE_EXECUTABLE_SUFFIX})
endif()

project(libjpeg-turbo C)
set(VERSION 2.0.0)
string(REPLACE "." ";" VERSION_TRIPLET ${VERSION})
list(GET VERSION_TRIPLET 0 VERSION_MAJOR)
list(GET VERSION_TRIPLET 1 VERSION_MINOR)
list(GET VERSION_TRIPLET 2 VERSION_REVISION)
function(pad_number NUMBER OUTPUT_LEN)
  string(LENGTH "${${NUMBER}}" INPUT_LEN)
  if(INPUT_LEN LESS OUTPUT_LEN)
    math(EXPR ZEROES "${OUTPUT_LEN} - ${INPUT_LEN} - 1")
    set(NUM ${${NUMBER}})
    foreach(C RANGE ${ZEROES})
      set(NUM "0${NUM}")
    endforeach()
    set(${NUMBER} ${NUM} PARENT_SCOPE)
  endif()
endfunction()
pad_number(VERSION_MINOR 3)
pad_number(VERSION_REVISION 3)
set(LIBJPEG_TURBO_VERSION_NUMBER ${VERSION_MAJOR}${VERSION_MINOR}${VERSION_REVISION})

string(TIMESTAMP DEFAULT_BUILD "%Y%m%d")
set(BUILD ${DEFAULT_BUILD} CACHE STRING "Build string (default: ${DEFAULT_BUILD})")

# NOTE: On Windows, this does nothing except when using MinGW or Cygwin.
# CMAKE_BUILD_TYPE has no meaning in Visual Studio, and it always defaults to
# Debug when using NMake.
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()
message(STATUS "CMAKE_BUILD_TYPE = ${CMAKE_BUILD_TYPE}")

message(STATUS "VERSION = ${VERSION}, BUILD = ${BUILD}")

# Detect CPU type and whether we're building 64-bit or 32-bit code
math(EXPR BITS "${CMAKE_SIZEOF_VOID_P} * 8")
string(TOLOWER ${CMAKE_SYSTEM_PROCESSOR} CMAKE_SYSTEM_PROCESSOR_LC)
if(CMAKE_SYSTEM_PROCESSOR_LC MATCHES "x86_64" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "amd64" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "i[0-9]86" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "x86" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "ia32")
  if(BITS EQUAL 64)
    set(CPU_TYPE x86_64)
  else()
    set(CPU_TYPE i386)
  endif()
  if(NOT CMAKE_SYSTEM_PROCESSOR STREQUAL ${CPU_TYPE})
    set(CMAKE_SYSTEM_PROCESSOR ${CPU_TYPE})
  endif()
elseif(CMAKE_SYSTEM_PROCESSOR_LC STREQUAL "aarch64" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "arm*64*")
  set(CPU_TYPE arm64)
elseif(CMAKE_SYSTEM_PROCESSOR_LC MATCHES "arm*")
  set(CPU_TYPE arm)
elseif(CMAKE_SYSTEM_PROCESSOR_LC MATCHES "ppc*" OR
  CMAKE_SYSTEM_PROCESSOR_LC MATCHES "powerpc*")
  set(CPU_TYPE powerpc)
else()
  set(CPU_TYPE ${CMAKE_SYSTEM_PROCESSOR_LC})
endif()
message(STATUS "${BITS}-bit build (${CPU_TYPE})")


###############################################################################
# INSTALL DIRECTORIES
###############################################################################

if(WIN32)
  if(MSVC)
    set(CMAKE_INSTALL_DEFAULT_PREFIX "c:/${CMAKE_PROJECT_NAME}")
  else()
    set(CMAKE_INSTALL_DEFAULT_PREFIX "c:/${CMAKE_PROJECT_NAME}-gcc")
  endif()
  if(BITS EQUAL 64)
    set(CMAKE_INSTALL_DEFAULT_PREFIX "${CMAKE_INSTALL_DEFAULT_PREFIX}64")
  endif()
else()
  set(CMAKE_INSTALL_DEFAULT_PREFIX /opt/${CMAKE_PROJECT_NAME})
endif()
if(CMAKE_INSTALL_PREFIX_INITIALIZED_TO_DEFAULT)
  set(CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_DEFAULT_PREFIX}" CACHE PATH
    "Directory into which to install ${CMAKE_PROJECT_NAME} (default: ${CMAKE_INSTALL_DEFAULT_PREFIX})"
    FORCE)
endif()
message(STATUS "CMAKE_INSTALL_PREFIX = ${CMAKE_INSTALL_PREFIX}")

# When the prefix is /opt/${CMAKE_PROJECT_NAME}, we assume that an "official"
# build is being created, and thus we install things into specific locations.

if(CMAKE_INSTALL_PREFIX STREQUAL "${CMAKE_INSTALL_DEFAULT_PREFIX}")
  set(CMAKE_INSTALL_DEFAULT_DATAROOTDIR "")
  set(CMAKE_INSTALL_DEFAULT_DOCDIR "<CMAKE_INSTALL_DATAROOTDIR>/doc")
  set(CMAKE_INSTALL_DEFAULT_JAVADIR "<CMAKE_INSTALL_DATAROOTDIR>/classes")
  if(UNIX AND NOT APPLE)
    if(BITS EQUAL 64)
      set(CMAKE_INSTALL_DEFAULT_LIBDIR "lib64")
    else()
      set(CMAKE_INSTALL_DEFAULT_LIBDIR "lib32")
    endif()
  endif()
endif()

include(cmakescripts/GNUInstallDirs.cmake)

set(CMAKE_INSTALL_RPATH ${CMAKE_INSTALL_FULL_LIBDIR})

macro(report_directory var)
  if(CMAKE_INSTALL_${var} STREQUAL CMAKE_INSTALL_FULL_${var})
    message(STATUS "CMAKE_INSTALL_${var} = ${CMAKE_INSTALL_${var}}")
  else()
    message(STATUS "CMAKE_INSTALL_${var} = ${CMAKE_INSTALL_${var}} (${CMAKE_INSTALL_FULL_${var}})")
  endif()
  mark_as_advanced(CLEAR CMAKE_INSTALL_${var})
endmacro()

set(DIRLIST "BINDIR;DATAROOTDIR;DOCDIR;INCLUDEDIR;LIBDIR")
if(UNIX)
  list(APPEND DIRLIST "MANDIR")
endif()
foreach(dir ${DIRLIST})
  report_directory(${dir})
endforeach()


###############################################################################
# CONFIGURATION OPTIONS
###############################################################################

macro(boolean_number var)
  if(${var})
    set(${var} 1)
  else()
    set(${var} 0)
  endif()
endmacro()

option(ENABLE_SHARED "Build shared libraries" TRUE)
boolean_number(ENABLE_SHARED)
option(ENABLE_STATIC "Build static libraries" TRUE)
boolean_number(ENABLE_STATIC)
option(REQUIRE_SIMD "Generate a fatal error if SIMD extensions are not available for this platform (default is to fall back to a non-SIMD build)" FALSE)
boolean_number(REQUIRE_SIMD)
option(WITH_12BIT "Encode/decode JPEG images with 12-bit samples (implies WITH_ARITH_DEC=0 WITH_ARITH_ENC=0 WITH_JAVA=0 WITH_SIMD=0 WITH_TURBOJPEG=0 )" FALSE)
boolean_number(WITH_12BIT)
option(WITH_ARITH_DEC "Include arithmetic decoding support when emulating the libjpeg v6b API/ABI" TRUE)
boolean_number(WITH_ARITH_DEC)
option(WITH_ARITH_ENC "Include arithmetic encoding support when emulating the libjpeg v6b API/ABI" TRUE)
boolean_number(WITH_ARITH_ENC)
option(WITH_JAVA "Build Java wrapper for the TurboJPEG API library (implies ENABLE_SHARED=1)" FALSE)
boolean_number(WITH_JAVA)
option(WITH_JPEG7 "Emulate libjpeg v7 API/ABI (this makes ${CMAKE_PROJECT_NAME} backward-incompatible with libjpeg v6b)" FALSE)
boolean_number(WITH_JPEG7)
option(WITH_JPEG8 "Emulate libjpeg v8 API/ABI (this makes ${CMAKE_PROJECT_NAME} backward-incompatible with libjpeg v6b)" FALSE)
boolean_number(WITH_JPEG8)
option(WITH_MEM_SRCDST "Include in-memory source/destination manager functions when emulating the libjpeg v6b or v7 API/ABI" TRUE)
boolean_number(WITH_MEM_SRCDST)
option(WITH_SIMD "Include SIMD extensions, if available for this platform" TRUE)
boolean_number(WITH_SIMD)
option(WITH_TURBOJPEG "Include the TurboJPEG API library and associated test programs" TRUE)
boolean_number(WITH_TURBOJPEG)

macro(report_option var desc)
  if(${var})
    message(STATUS "${desc} enabled (${var} = ${${var}})")
  else()
    message(STATUS "${desc} disabled (${var} = ${${var}})")
  endif()
endmacro()

if(WITH_JAVA)
  set(ENABLE_SHARED 1)
endif()

# Explicitly setting CMAKE_POSITION_INDEPENDENT_CODE=FALSE disables PIC for all
# targets, which will cause the shared library builds to fail.  Thus, if shared
# libraries are enabled and CMAKE_POSITION_INDEPENDENT_CODE is explicitly set
# to FALSE, we need to unset it, thus restoring the default behavior
# (automatically using PIC for shared library targets.)
if(DEFINED CMAKE_POSITION_INDEPENDENT_CODE AND
  NOT CMAKE_POSITION_INDEPENDENT_CODE AND ENABLE_SHARED)
  unset(CMAKE_POSITION_INDEPENDENT_CODE CACHE)
endif()

report_option(ENABLE_SHARED "Shared libraries")
report_option(ENABLE_STATIC "Static libraries")

if(WITH_12BIT)
  set(WITH_ARITH_DEC 0)
  set(WITH_ARITH_ENC 0)
  set(WITH_JAVA 0)
  set(WITH_SIMD 0)
  set(WITH_TURBOJPEG 0)
  set(BITS_IN_JSAMPLE 12)
else()
  set(BITS_IN_JSAMPLE 8)
endif()
report_option(WITH_12BIT "12-bit JPEG support")

if(WITH_JPEG8 OR WITH_JPEG7)
  set(WITH_ARITH_ENC 1)
  set(WITH_ARITH_DEC 1)
endif()
if(WITH_JPEG8)
  set(WITH_MEM_SRCDST 0)
endif()

if(WITH_ARITH_DEC)
  set(D_ARITH_CODING_SUPPORTED 1)
endif()
if(NOT WITH_12BIT)
  report_option(WITH_ARITH_DEC "Arithmetic decoding support")
endif()

if(WITH_ARITH_ENC)
  set(C_ARITH_CODING_SUPPORTED 1)
endif()
if(NOT WITH_12BIT)
  report_option(WITH_ARITH_ENC "Arithmetic encoding support")
endif()

if(NOT WITH_12BIT)
  report_option(WITH_TURBOJPEG "TurboJPEG API library")
  report_option(WITH_JAVA "TurboJPEG Java wrapper")
endif()

if(WITH_MEM_SRCDST)
  set(MEM_SRCDST_SUPPORTED 1)
  set(MEM_SRCDST_FUNCTIONS "global:  jpeg_mem_dest;  jpeg_mem_src;")
endif()
if(NOT WITH_JPEG8)
  report_option(WITH_MEM_SRCDST "In-memory source/destination managers")
endif()

set(SO_AGE 2)
if(WITH_MEM_SRCDST)
  set(SO_AGE 3)
endif()

if(WITH_JPEG8)
  set(JPEG_LIB_VERSION 80)
elseif(WITH_JPEG7)
  set(JPEG_LIB_VERSION 70)
else()
  set(JPEG_LIB_VERSION 62)
endif()

math(EXPR JPEG_LIB_VERSION_DIV10 "${JPEG_LIB_VERSION} / 10")
math(EXPR JPEG_LIB_VERSION_MOD10 "${JPEG_LIB_VERSION} % 10")
if(JPEG_LIB_VERSION STREQUAL "62")
  set(DEFAULT_SO_MAJOR_VERSION ${JPEG_LIB_VERSION})
else()
  set(DEFAULT_SO_MAJOR_VERSION ${JPEG_LIB_VERSION_DIV10})
endif()
if(JPEG_LIB_VERSION STREQUAL "80")
  set(DEFAULT_SO_MINOR_VERSION 2)
else()
  set(DEFAULT_SO_MINOR_VERSION 0)
endif()

# This causes SO_MAJOR_VERSION/SO_MINOR_VERSION to reset to defaults if
# WITH_JPEG7 or WITH_JPEG8 has changed.
if((DEFINED WITH_JPEG7_INT AND NOT WITH_JPEG7 EQUAL WITH_JPEG7_INT) OR
  (DEFINED WITH_JPEG8_INT AND NOT WITH_JPEG8 EQUAL WITH_JPEG8_INT))
  set(FORCE_SO_VERSION "FORCE")
endif()
set(WITH_JPEG7_INT ${WITH_JPEG7} CACHE INTERNAL "")
set(WITH_JPEG8_INT ${WITH_JPEG8} CACHE INTERNAL "")

set(SO_MAJOR_VERSION ${DEFAULT_SO_MAJOR_VERSION} CACHE STRING
  "Major version of the libjpeg API shared library (default: ${DEFAULT_SO_MAJOR_VERSION})"
  ${FORCE_SO_VERSION})
set(SO_MINOR_VERSION ${DEFAULT_SO_MINOR_VERSION} CACHE STRING
  "Minor version of the libjpeg API shared library (default: ${DEFAULT_SO_MINOR_VERSION})"
  ${FORCE_SO_VERSION})

set(JPEG_LIB_VERSION_DECIMAL "${JPEG_LIB_VERSION_DIV10}.${JPEG_LIB_VERSION_MOD10}")
message(STATUS "Emulating libjpeg API/ABI v${JPEG_LIB_VERSION_DECIMAL} (WITH_JPEG7 = ${WITH_JPEG7}, WITH_JPEG8 = ${WITH_JPEG8})")
message(STATUS "libjpeg API shared library version = ${SO_MAJOR_VERSION}.${SO_AGE}.${SO_MINOR_VERSION}")

# Because the TurboJPEG API library uses versioned symbols and changes the
# names of functions whenever they are modified in a backward-incompatible
# manner, it is always backward-ABI-compatible with itself, so the major and
# minor SO versions don't change.  However, we increase the middle number (the
# SO "age") whenever functions are added to the API.
set(TURBOJPEG_SO_MAJOR_VERSION 0)
set(TURBOJPEG_SO_VERSION 0.2.0)


###############################################################################
# COMPILER SETTINGS
###############################################################################

if(MSVC)
  option(WITH_CRT_DLL
    "Link all ${CMAKE_PROJECT_NAME} libraries and executables with the C run-time DLL (msvcr*.dll) instead of the static C run-time library (libcmt*.lib.)  The default is to use the C run-time DLL only with the libraries and executables that need it."
    FALSE)
  if(NOT WITH_CRT_DLL)
    # Use the static C library for all build types
    foreach(var CMAKE_C_FLAGS CMAKE_C_FLAGS_DEBUG CMAKE_C_FLAGS_RELEASE
      CMAKE_C_FLAGS_MINSIZEREL CMAKE_C_FLAGS_RELWITHDEBINFO)
      if(${var} MATCHES "/MD")
        string(REGEX REPLACE "/MD" "/MT" ${var} "${${var}}")
      endif()
    endforeach()
  endif()
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /W3 /wd4996")
endif()

if(CMAKE_COMPILER_IS_GNUCC OR CMAKE_C_COMPILER_ID STREQUAL "Clang")
  # Use the maximum optimization level for release builds
  foreach(var CMAKE_C_FLAGS_RELEASE CMAKE_C_FLAGS_RELWITHDEBINFO)
    if(${var} MATCHES "-O2")
      string(REGEX REPLACE "-O2" "-O3" ${var} "${${var}}")
    endif()
  endforeach()
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "SunOS")
  if(CMAKE_C_COMPILER_ID MATCHES "SunPro")
    # Use the maximum optimization level for release builds
    foreach(var CMAKE_C_FLAGS_RELEASE CMAKE_C_FLAGS_RELWITHDEBINFO)
      if(${var} MATCHES "-xO3")
        string(REGEX REPLACE "-xO3" "-xO5" ${var} "${${var}}")
      endif()
      if(${var} MATCHES "-xO2")
        string(REGEX REPLACE "-xO2" "-xO5" ${var} "${${var}}")
      endif()
    endforeach()
  endif()
endif()

string(TOUPPER ${CMAKE_BUILD_TYPE} CMAKE_BUILD_TYPE_UC)

set(EFFECTIVE_C_FLAGS "${CMAKE_C_FLAGS} ${CMAKE_C_FLAGS_${CMAKE_BUILD_TYPE_UC}}")
message(STATUS "Compiler flags = ${EFFECTIVE_C_FLAGS}")

set(EFFECTIVE_LD_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${CMAKE_EXE_LINKER_FLAGS_${CMAKE_BUILD_TYPE_UC}}")
message(STATUS "Linker flags = ${EFFECTIVE_LD_FLAGS}")

include(CheckCSourceCompiles)
include(CheckIncludeFiles)
include(CheckTypeSize)

check_type_size("size_t" SIZE_T)
check_type_size("unsigned long" UNSIGNED_LONG)

if(SIZE_T EQUAL UNSIGNED_LONG)
  check_c_source_compiles("int main(int argc, char **argv) { unsigned long a = argc;  return __builtin_ctzl(a); }"
    HAVE_BUILTIN_CTZL)
endif()
if(MSVC)
  check_include_files("intrin.h" HAVE_INTRIN_H)
endif()

if(UNIX)
  # Check for headers
  check_include_files(locale.h HAVE_LOCALE_H)
  check_include_files(stddef.h HAVE_STDDEF_H)
  check_include_files(stdlib.h HAVE_STDLIB_H)
  check_include_files(sys/types.h NEED_SYS_TYPES_H)

  # Check for functions
  include(CheckSymbolExists)
  check_symbol_exists(memset string.h HAVE_MEMSET)
  check_symbol_exists(memcpy string.h HAVE_MEMCPY)
  if(NOT HAVE_MEMSET AND NOT HAVE_MEMCPY)
    set(NEED_BSD_STRINGS 1)
  endif()

  # Check for types
  check_type_size("unsigned char" UNSIGNED_CHAR)
  check_type_size("unsigned short" UNSIGNED_SHORT)

  # Check for compiler features
  check_c_source_compiles("int main(void) { typedef struct undefined_structure *undef_struct_ptr;  undef_struct_ptr ptr = 0;  return ptr != 0; }"
    INCOMPLETE_TYPES)
  if(INCOMPLETE_TYPES)
    message(STATUS "Compiler supports pointers to undefined structures.")
  else()
    set(INCOMPLETE_TYPES_BROKEN 1)
    message(STATUS "Compiler does not support pointers to undefined structures.")
  endif()

  if(CMAKE_CROSSCOMPILING)
    set(RIGHT_SHIFT_IS_UNSIGNED 0)
  else()
    include(CheckCSourceRuns)
    check_c_source_runs("
      #include <stdio.h>
      #include <stdlib.h>
      int is_shifting_signed (long arg) {
        long res = arg >> 4;
        if (res == -0x7F7E80CL)
          return 1; /* right shift is signed */
        /* see if unsigned-shift hack will fix it. */
        /* we can't just test exact value since it depends on width of long... */
        res |= (~0L) << (32-4);
        if (res == -0x7F7E80CL)
          return 0; /* right shift is unsigned */
        printf(\"Right shift isn't acting as I expect it to.\\\\n\");
        printf(\"I fear the JPEG software will not work at all.\\\\n\\\\n\");
        return 0; /* try it with unsigned anyway */
      }
      int main (void) {
        exit(is_shifting_signed(-0x7F7E80B1L));
      }" RIGHT_SHIFT_IS_UNSIGNED)
  endif()

  if(CMAKE_CROSSCOMPILING)
    set(__CHAR_UNSIGNED__ 0)
  else()
    check_c_source_runs("int main(void) { return ((char) -1 < 0); }"
      __CHAR_UNSIGNED__)
  endif()
endif()

if(MSVC)
  set(INLINE_OPTIONS "__inline;inline")
else()
  set(INLINE_OPTIONS "__inline__;inline")
endif()
option(FORCE_INLINE "Force function inlining" TRUE)
boolean_number(FORCE_INLINE)
if(FORCE_INLINE)
  if(MSVC)
    list(INSERT INLINE_OPTIONS 0 "__forceinline")
  else()
    list(INSERT INLINE_OPTIONS 0 "inline __attribute__((always_inline))")
    list(INSERT INLINE_OPTIONS 0 "__inline__ __attribute__((always_inline))")
  endif()
endif()
foreach(inline ${INLINE_OPTIONS})
  check_c_source_compiles("${inline} static int foo(void) { return 0; } int main(void) { return foo(); }"
    INLINE_WORKS)
  if(INLINE_WORKS)
    set(INLINE ${inline})
    break()
  endif()
endforeach()
if(NOT INLINE_WORKS)
  message(FATAL_ERROR "Could not determine how to inline functions.")
endif()
message(STATUS "INLINE = ${INLINE} (FORCE_INLINE = ${FORCE_INLINE})")

if(UNIX AND NOT APPLE)
  file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/conftest.map "VERS_1 { global: *; };")
  set(CMAKE_REQUIRED_FLAGS
    "-Wl,--version-script,${CMAKE_CURRENT_BINARY_DIR}/conftest.map")
  check_c_source_compiles("int main(void) { return 0; }" HAVE_VERSION_SCRIPT)
  set(CMAKE_REQUIRED_FLAGS)
  file(REMOVE ${CMAKE_CURRENT_BINARY_DIR}/conftest.map)
  if(HAVE_VERSION_SCRIPT)
    message(STATUS "Linker supports GNU-style version scripts")
    set(MAPFLAG "-Wl,--version-script,")
    set(TJMAPFLAG "-Wl,--version-script,")
  else()
    message(STATUS "Linker does not support GNU-style version scripts")
    if(CMAKE_SYSTEM_NAME STREQUAL "SunOS")
      # The Solaris linker doesn't like our version script for the libjpeg API
      # library, but the version script for the TurboJPEG API library should
      # still work.
      file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/conftest.map
        "VERS_1 { global: foo;  local: *; }; VERS_2 { global: foo2; } VERS_1;")
      set(CMAKE_REQUIRED_FLAGS "-Wl,-M,${CMAKE_CURRENT_BINARY_DIR}/conftest.map")
      check_c_source_compiles("void foo() {} void foo2() {} int main(void) { return 0; }"
        HAVE_MAPFILE)
      set(CMAKE_REQUIRED_FLAGS)
      file(REMOVE ${CMAKE_CURRENT_BINARY_DIR}/conftest.map)
      if(HAVE_MAPFILE)
        message(STATUS "Linker supports mapfiles")
        set(TJMAPFLAG "-Wl,-M,")
      else()
        message(STATUS "Linker does not support mapfiles")
      endif()
    endif()
  endif()
endif()

# Generate files
if(WIN32)
  configure_file(win/jconfig.h.in jconfig.h)
else()
  configure_file(jconfig.h.in jconfig.h)
endif()
configure_file(jconfigint.h.in jconfigint.h)
if(UNIX)
  configure_file(libjpeg.map.in libjpeg.map)
endif()

# Include directories and compiler definitions
include_directories(${CMAKE_CURRENT_BINARY_DIR} ${CMAKE_CURRENT_SOURCE_DIR})


###############################################################################
# TARGETS
###############################################################################

if(CMAKE_EXECUTABLE_SUFFIX_TMP)
  set(CMAKE_EXECUTABLE_SUFFIX ${CMAKE_EXECUTABLE_SUFFIX_TMP})
endif()
message(STATUS "CMAKE_EXECUTABLE_SUFFIX = ${CMAKE_EXECUTABLE_SUFFIX}")

set(JPEG_SOURCES jcapimin.c jcapistd.c jccoefct.c jccolor.c jcdctmgr.c jchuff.c
  jcicc.c jcinit.c jcmainct.c jcmarker.c jcmaster.c jcomapi.c jcparam.c
  jcphuff.c jcprepct.c jcsample.c jctrans.c jdapimin.c jdapistd.c jdatadst.c
  jdatasrc.c jdcoefct.c jdcolor.c jddctmgr.c jdhuff.c jdicc.c jdinput.c
  jdmainct.c jdmarker.c jdmaster.c jdmerge.c jdphuff.c jdpostct.c jdsample.c
  jdtrans.c jerror.c jfdctflt.c jfdctfst.c jfdctint.c jidctflt.c jidctfst.c
  jidctint.c jidctred.c jquant1.c jquant2.c jutils.c jmemmgr.c jmemnobs.c)

if(WITH_ARITH_ENC OR WITH_ARITH_DEC)
  set(JPEG_SOURCES ${JPEG_SOURCES} jaricom.c)
endif()

if(WITH_ARITH_ENC)
  set(JPEG_SOURCES ${JPEG_SOURCES} jcarith.c)
endif()

if(WITH_ARITH_DEC)
  set(JPEG_SOURCES ${JPEG_SOURCES} jdarith.c)
endif()

if(WITH_SIMD)
  add_subdirectory(simd)
elseif(NOT WITH_12BIT)
  message(STATUS "SIMD extensions: None (WITH_SIMD = ${WITH_SIMD})")
endif()
if(WITH_SIMD)
  message(STATUS "SIMD extensions: ${CPU_TYPE} (WITH_SIMD = ${WITH_SIMD})")
  if(MSVC_IDE)
    set_source_files_properties(${SIMD_OBJS} PROPERTIES GENERATED 1)
  endif()
else()
  add_library(simd OBJECT jsimd_none.c)
endif()

if(WITH_JAVA)
  add_subdirectory(java)
endif()

if(ENABLE_SHARED)
  add_subdirectory(sharedlib)
endif()

if(ENABLE_STATIC)
  add_library(jpeg-static STATIC ${JPEG_SOURCES} $<TARGET_OBJECTS:simd>
    ${SIMD_OBJS})
  if(NOT MSVC)
    set_target_properties(jpeg-static PROPERTIES OUTPUT_NAME jpeg)
  endif()
endif()

if(WITH_TURBOJPEG)
  if(ENABLE_SHARED)
    set(TURBOJPEG_SOURCES ${JPEG_SOURCES} $<TARGET_OBJECTS:simd> ${SIMD_OBJS}
      turbojpeg.c transupp.c jdatadst-tj.c jdatasrc-tj.c rdbmp.c rdppm.c
      wrbmp.c wrppm.c)
    set(TJMAPFILE ${CMAKE_CURRENT_SOURCE_DIR}/turbojpeg-mapfile)
    if(WITH_JAVA)
      set(TURBOJPEG_SOURCES ${TURBOJPEG_SOURCES} turbojpeg-jni.c)
      include_directories(${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH2})
      set(TJMAPFILE ${CMAKE_CURRENT_SOURCE_DIR}/turbojpeg-mapfile.jni)
    endif()
    add_library(turbojpeg SHARED ${TURBOJPEG_SOURCES})
    set_property(TARGET turbojpeg PROPERTY COMPILE_FLAGS
      "-DBMP_SUPPORTED -DPPM_SUPPORTED")
    if(WIN32)
      set_target_properties(turbojpeg PROPERTIES DEFINE_SYMBOL DLLDEFINE)
    endif()
    if(MINGW)
      set_target_properties(turbojpeg PROPERTIES LINK_FLAGS -Wl,--kill-at)
    endif()
    if(APPLE AND (NOT CMAKE_OSX_DEPLOYMENT_TARGET OR
                  CMAKE_OSX_DEPLOYMENT_TARGET VERSION_GREATER 10.4))
      if(NOT CMAKE_SHARED_LIBRARY_RUNTIME_C_FLAG)
        set(CMAKE_SHARED_LIBRARY_RUNTIME_C_FLAG "-Wl,-rpath,")
      endif()
      set_target_properties(turbojpeg PROPERTIES MACOSX_RPATH 1)
    endif()
    set_target_properties(turbojpeg PROPERTIES
      SOVERSION ${TURBOJPEG_SO_MAJOR_VERSION} VERSION ${TURBOJPEG_SO_VERSION})
    if(TJMAPFLAG)
      set_target_properties(turbojpeg PROPERTIES
        LINK_FLAGS "${TJMAPFLAG}${TJMAPFILE}")
    endif()

    add_executable(tjunittest tjunittest.c tjutil.c md5/md5.c md5/md5hl.c)
    target_link_libraries(tjunittest turbojpeg)

    add_executable(tjbench tjbench.c tjutil.c)
    target_link_libraries(tjbench turbojpeg)
    if(UNIX)
      target_link_libraries(tjbench m)
    endif()

    add_executable(tjexample tjexample.c)
    target_link_libraries(tjexample turbojpeg)
  endif()

  if(ENABLE_STATIC)
    add_library(turbojpeg-static STATIC ${JPEG_SOURCES} $<TARGET_OBJECTS:simd>
      ${SIMD_OBJS} turbojpeg.c transupp.c jdatadst-tj.c jdatasrc-tj.c rdbmp.c
      rdppm.c wrbmp.c wrppm.c)
    set_property(TARGET turbojpeg-static PROPERTY COMPILE_FLAGS
      "-DBMP_SUPPORTED -DPPM_SUPPORTED")
    if(NOT MSVC)
      set_target_properties(turbojpeg-static PROPERTIES OUTPUT_NAME turbojpeg)
    endif()

    add_executable(tjunittest-static tjunittest.c tjutil.c md5/md5.c
      md5/md5hl.c)
    target_link_libraries(tjunittest-static turbojpeg-static)

    add_executable(tjbench-static tjbench.c tjutil.c)
    target_link_libraries(tjbench-static turbojpeg-static)
    if(UNIX)
      target_link_libraries(tjbench-static m)
    endif()
  endif()
endif()

if(WIN32)
  set(USE_SETMODE "-DUSE_SETMODE")
endif()
if(WITH_12BIT)
  set(COMPILE_FLAGS "-DGIF_SUPPORTED -DPPM_SUPPORTED ${USE_SETMODE}")
else()
  set(COMPILE_FLAGS "-DBMP_SUPPORTED -DGIF_SUPPORTED -DPPM_SUPPORTED -DTARGA_SUPPORTED ${USE_SETMODE}")
  set(CJPEG_BMP_SOURCES rdbmp.c rdtarga.c)
  set(DJPEG_BMP_SOURCES wrbmp.c wrtarga.c)
endif()

if(ENABLE_STATIC)
  add_executable(cjpeg-static cjpeg.c cdjpeg.c rdgif.c rdppm.c rdswitch.c
    ${CJPEG_BMP_SOURCES})
  set_property(TARGET cjpeg-static PROPERTY COMPILE_FLAGS ${COMPILE_FLAGS})
  target_link_libraries(cjpeg-static jpeg-static)

  add_executable(djpeg-static djpeg.c cdjpeg.c rdcolmap.c rdswitch.c wrgif.c
    wrppm.c ${DJPEG_BMP_SOURCES})
  set_property(TARGET djpeg-static PROPERTY COMPILE_FLAGS ${COMPILE_FLAGS})
  target_link_libraries(djpeg-static jpeg-static)

  add_executable(jpegtran-static jpegtran.c cdjpeg.c rdswitch.c transupp.c)
  target_link_libraries(jpegtran-static jpeg-static)
  set_property(TARGET jpegtran-static PROPERTY COMPILE_FLAGS "${USE_SETMODE}")
endif()

add_executable(rdjpgcom rdjpgcom.c)

add_executable(wrjpgcom wrjpgcom.c)


###############################################################################
# TESTS
###############################################################################

add_subdirectory(md5)

if(MSVC_IDE)
  set(OBJDIR "\${CTEST_CONFIGURATION_TYPE}/")
else()
  set(OBJDIR "")
endif()

enable_testing()

if(WITH_12BIT)
  set(TESTORIG testorig12.jpg)
  set(MD5_JPEG_RGB_ISLOW 9d7369207c520d37f2c1cbfcb82b2964)
  set(MD5_JPEG_RGB_ISLOW2 a00bd20d8ae49684640ef7177d2e0b64)
  set(MD5_PPM_RGB_ISLOW f3301d2219783b8b3d942b7239fa50c0)
  set(MD5_JPEG_422_IFAST_OPT 7322e3bd2f127f7de4b40d4480ce60e4)
  set(MD5_PPM_422_IFAST 79807fa552899e66a04708f533e16950)
  set(MD5_PPM_422M_IFAST 07737bfe8a7c1c87aaa393a0098d16b0)
  set(MD5_JPEG_420_IFAST_Q100_PROG a1da220b5604081863a504297ed59e55)
  set(MD5_PPM_420_Q100_IFAST 1b3730122709f53d007255e8dfd3305e)
  set(MD5_PPM_420M_Q100_IFAST 980a1a3c5bf9510022869d30b7d26566)
  set(MD5_JPEG_GRAY_ISLOW 235c90707b16e2e069f37c888b2636d9)
  set(MD5_PPM_GRAY_ISLOW 7213c10af507ad467da5578ca5ee1fca)
  set(MD5_PPM_GRAY_ISLOW_RGB e96ee81c30a6ed422d466338bd3de65d)
  set(MD5_JPEG_420S_IFAST_OPT 7af8e60be4d9c227ec63ac9b6630855e)

  set(MD5_JPEG_3x2_FLOAT_PROG_SSE a8c17daf77b457725ec929e215b603f8)
  set(MD5_PPM_3x2_FLOAT_SSE 42876ab9e5c2f76a87d08db5fbd57956)
  set(MD5_JPEG_3x2_FLOAT_PROG_32BIT a8c17daf77b457725ec929e215b603f8)
  set(MD5_PPM_3x2_FLOAT_32BIT ${MD5_PPM_3x2_FLOAT_SSE})
  set(MD5_JPEG_3x2_FLOAT_PROG_64BIT ${MD5_JPEG_3x2_FLOAT_PROG_32BIT})
  set(MD5_PPM_3x2_FLOAT_64BIT ${MD5_PPM_3x2_FLOAT_SSE})
  set(MD5_JPEG_3x2_FLOAT_PROG_387 bc6dbbefac2872f6b9d6c4a0ae60c3c0)
  set(MD5_PPM_3x2_FLOAT_387 bcc5723c61560463ac60f772e742d092)
  set(MD5_JPEG_3x2_FLOAT_PROG_MSVC e27840755870fa849872e58aa0cd1400)
  set(MD5_PPM_3x2_FLOAT_MSVC 6c2880b83bb1aa41dfe330e7a9768690)

  set(MD5_JPEG_3x2_IFAST_PROG 1396cc2b7185cfe943d408c9d305339e)
  set(MD5_PPM_3x2_IFAST 3975985ef6eeb0a2cdc58daa651ccc00)
  set(MD5_PPM_420M_ISLOW_2_1 4ca6be2a6f326ff9eaab63e70a8259c0)
  set(MD5_PPM_420M_ISLOW_15_8 12aa9f9534c1b3d7ba047322226365eb)
  set(MD5_PPM_420M_ISLOW_13_8 f7e22817c7b25e1393e4ec101e9d4e96)
  set(MD5_PPM_420M_ISLOW_11_8 800a16f9f4dc9b293197bfe11be10a82)
  set(MD5_PPM_420M_ISLOW_9_8 06b7a92a9bc69f4dc36ec40f1937d55c)
  set(MD5_PPM_420M_ISLOW_7_8 3ec444a14a4ab4eab88ffc49c48eca43)
  set(MD5_PPM_420M_ISLOW_3_4 3e726b7ea872445b19437d1c1d4f0d93)
  set(MD5_PPM_420M_ISLOW_5_8 a8a771abdc94301d20ffac119b2caccd)
  set(MD5_PPM_420M_ISLOW_1_2 b419124dd5568b085787234866102866)
  set(MD5_PPM_420M_ISLOW_3_8 343d19015531b7bbe746124127244fa8)
  set(MD5_PPM_420M_ISLOW_1_4 35fd59d866e44659edfa3c18db2a3edb)
  set(MD5_PPM_420M_ISLOW_1_8 ccaed48ac0aedefda5d4abe4013f4ad7)
  set(MD5_PPM_420_ISLOW_SKIP15_31 86664cd9dc956536409e44e244d20a97)
  set(MD5_PPM_420_ISLOW_PROG_CROP62x62_71_71 452a21656115a163029cfba5c04fa76a)
  set(MD5_PPM_444_ISLOW_SKIP1_6 ef63901f71ef7a75cd78253fc0914f84)
  set(MD5_PPM_444_ISLOW_PROG_CROP98x98_13_13 15b173fb5872d9575572fbcc1b05956f)
  set(MD5_JPEG_CROP cdb35ff4b4519392690ea040c56ea99c)
else()
  set(TESTORIG testorig.jpg)
  set(MD5_JPEG_RGB_ISLOW 1d44a406f61da743b5fd31c0a9abdca3)
  set(MD5_JPEG_RGB_ISLOW2 31d121e57b6c2934c890a7fc7763bcd4)
  set(MD5_PPM_RGB_ISLOW 00a257f5393fef8821f2b88ac7421291)
  set(MD5_BMP_RGB_ISLOW_565 f07d2e75073e4bb10f6c6f4d36e2e3be)
  set(MD5_BMP_RGB_ISLOW_565D 4cfa0928ef3e6bb626d7728c924cfda4)
  set(MD5_JPEG_422_IFAST_OPT 2540287b79d913f91665e660303ab2c8)
  set(MD5_PPM_422_IFAST 35bd6b3f833bad23de82acea847129fa)
  set(MD5_PPM_422M_IFAST 8dbc65323d62cca7c91ba02dd1cfa81d)
  set(MD5_BMP_422M_IFAST_565 3294bd4d9a1f2b3d08ea6020d0db7065)
  set(MD5_BMP_422M_IFAST_565D da98c9c7b6039511be4a79a878a9abc1)
  set(MD5_JPEG_420_IFAST_Q100_PROG 990cbe0329c882420a2094da7e5adade)
  set(MD5_PPM_420_Q100_IFAST 5a732542015c278ff43635e473a8a294)
  set(MD5_PPM_420M_Q100_IFAST ff692ee9323a3b424894862557c092f1)
  set(MD5_JPEG_GRAY_ISLOW 72b51f894b8f4a10b3ee3066770aa38d)
  set(MD5_PPM_GRAY_ISLOW 8d3596c56eace32f205deccc229aa5ed)
  set(MD5_PPM_GRAY_ISLOW_RGB 116424ac07b79e5e801f00508eab48ec)
  set(MD5_BMP_GRAY_ISLOW_565 12f78118e56a2f48b966f792fedf23cc)
  set(MD5_BMP_GRAY_ISLOW_565D bdbbd616441a24354c98553df5dc82db)
  set(MD5_JPEG_420S_IFAST_OPT 388708217ac46273ca33086b22827ed8)

  set(MD5_JPEG_3x2_FLOAT_PROG_SSE 343e3f8caf8af5986ebaf0bdc13b5c71)
  set(MD5_PPM_3x2_FLOAT_SSE 1a75f36e5904d6fc3a85a43da9ad89bb)
  set(MD5_JPEG_3x2_FLOAT_PROG_32BIT 9bca803d2042bd1eb03819e2bf92b3e5)
  set(MD5_PPM_3x2_FLOAT_32BIT f6bfab038438ed8f5522fbd33595dcdc)
  set(MD5_JPEG_3x2_FLOAT_PROG_64BIT ${MD5_JPEG_3x2_FLOAT_PROG_32BIT})
  set(MD5_PPM_3x2_FLOAT_64BIT 0e917a34193ef976b679a6b069b1be26)
  set(MD5_JPEG_3x2_FLOAT_PROG_387 1657664a410e0822c924b54f6f65e6e9)
  set(MD5_PPM_3x2_FLOAT_387 cb0a1f027f3d2917c902b5640214e025)
  set(MD5_JPEG_3x2_FLOAT_PROG_MSVC 7999ce9cd0ee9b6c7043b7351ab7639d)
  set(MD5_PPM_3x2_FLOAT_MSVC 28cdc448a6b75e97892f0e0f8d4b21f3)

  set(MD5_JPEG_3x2_IFAST_PROG 1ee5d2c1a77f2da495f993c8c7cceca5)
  set(MD5_PPM_3x2_IFAST fd283664b3b49127984af0a7f118fccd)
  set(MD5_JPEG_420_ISLOW_ARI e986fb0a637a8d833d96e8a6d6d84ea1)
  set(MD5_JPEG_444_ISLOW_PROGARI 0a8f1c8f66e113c3cf635df0a475a617)
  set(MD5_PPM_420M_IFAST_ARI 72b59a99bcf1de24c5b27d151bde2437)
  set(MD5_JPEG_420_ISLOW 9a68f56bc76e466aa7e52f415d0f4a5f)
  set(MD5_PPM_420M_ISLOW_2_1 9f9de8c0612f8d06869b960b05abf9c9)
  set(MD5_PPM_420M_ISLOW_15_8 b6875bc070720b899566cc06459b63b7)
  set(MD5_PPM_420M_ISLOW_13_8 bc3452573c8152f6ae552939ee19f82f)
  set(MD5_PPM_420M_ISLOW_11_8 d8cc73c0aaacd4556569b59437ba00a5)
  set(MD5_PPM_420M_ISLOW_9_8 d25e61bc7eac0002f5b393aa223747b6)
  set(MD5_PPM_420M_ISLOW_7_8 ddb564b7c74a09494016d6cd7502a946)
  set(MD5_PPM_420M_ISLOW_3_4 8ed8e68808c3fbc4ea764fc9d2968646)
  set(MD5_PPM_420M_ISLOW_5_8 a3363274999da2366a024efae6d16c9b)
  set(MD5_PPM_420M_ISLOW_1_2 e692a315cea26b988c8e8b29a5dbcd81)
  set(MD5_PPM_420M_ISLOW_3_8 79eca9175652ced755155c90e785a996)
  set(MD5_PPM_420M_ISLOW_1_4 79cd778f8bf1a117690052cacdd54eca)
  set(MD5_PPM_420M_ISLOW_1_8 391b3d4aca640c8567d6f8745eb2142f)
  set(MD5_BMP_420_ISLOW_256 4980185e3776e89bd931736e1cddeee6)
  set(MD5_BMP_420_ISLOW_565 bf9d13e16c4923b92e1faa604d7922cb)
  set(MD5_BMP_420_ISLOW_565D 6bde71526acc44bcff76f696df8638d2)
  set(MD5_BMP_420M_ISLOW_565 8dc0185245353cfa32ad97027342216f)
  set(MD5_BMP_420M_ISLOW_565D ce034037d212bc403330df6f915c161b)
  set(MD5_PPM_420_ISLOW_SKIP15_31 c4c65c1e43d7275cd50328a61e6534f0)
  set(MD5_PPM_420_ISLOW_ARI_SKIP16_139 087c6b123db16ac00cb88c5b590bb74a)
  set(MD5_PPM_420_ISLOW_PROG_CROP62x62_71_71 26eb36ccc7d1f0cb80cdabb0ac8b5d99)
  set(MD5_PPM_420_ISLOW_ARI_CROP53x53_4_4 886c6775af22370257122f8b16207e6d)
  set(MD5_PPM_444_ISLOW_SKIP1_6 5606f86874cf26b8fcee1117a0a436a6)
  set(MD5_PPM_444_ISLOW_PROG_CROP98x98_13_13 db87dc7ce26bcdc7a6b56239ce2b9d6c)
  set(MD5_PPM_444_ISLOW_ARI_CROP37x37_0_0 cb57b32bd6d03e35432362f7bf184b6d)
  set(MD5_JPEG_CROP b4197f377e621c4e9b1d20471432610d)
endif()

if(WITH_JAVA)
  add_test(TJUnitTest
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest)
  add_test(TJUnitTest-yuv
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest -yuv)
  add_test(TJUnitTest-yuv-nopad
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest -yuv -noyuvpad)
  add_test(TJUnitTest-bi
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest -bi)
  add_test(TJUnitTest-bi-yuv
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest -bi -yuv)
  add_test(TJUnitTest-bi-yuv-nopad
    ${Java_JAVA_EXECUTABLE} ${JAVAARGS} -cp java/turbojpeg.jar
      -Djava.library.path=${CMAKE_CURRENT_BINARY_DIR}/${OBJDIR}
      TJUnitTest -bi -yuv -noyuvpad)
endif()

set(TEST_LIBTYPES "")
if(ENABLE_SHARED)
  set(TEST_LIBTYPES ${TEST_LIBTYPES} shared)
endif()
if(ENABLE_STATIC)
  set(TEST_LIBTYPES ${TEST_LIBTYPES} static)
endif()

set(TESTIMAGES ${CMAKE_CURRENT_SOURCE_DIR}/testimages)
set(MD5CMP ${CMAKE_CURRENT_BINARY_DIR}/md5/md5cmp)
if(CMAKE_CROSSCOMPILING)
  file(RELATIVE_PATH TESTIMAGES ${CMAKE_CURRENT_BINARY_DIR} ${TESTIMAGES})
  file(RELATIVE_PATH MD5CMP ${CMAKE_CURRENT_BINARY_DIR} ${MD5CMP})
endif()

# The output of the floating point DCT/IDCT algorithms differs depending on the
# type of floating point math used, so the FLOATTEST CMake variable must be
# set in order to tell the testing system which floating point results it
# should expect:
#
# sse = validate against the expected results from the libjpeg-turbo SSE SIMD
#       extensions
# 32bit = validate against the expected results from the C code when running on
#         a 32-bit FPU (or when SSE is being used for floating point math,
#         which is generally the default with x86-64 compilers)
# 64bit = validate against the expected results from the C code when running
#         on a 64-bit FPU
# 387 = validate against the expected results from the C code when the 387 FPU
#       is being used for floating point math (which is generally the default
#       with x86 compilers)
# msvc = validate against the expected results from the C code when compiled
#        with a 32-bit version of Visual C++

if(CPU_TYPE STREQUAL "x86_64" OR CPU_TYPE STREQUAL "i386")
  if(WITH_SIMD)
    set(DEFAULT_FLOATTEST sse)
  elseif(CPU_TYPE STREQUAL "x86_64")
    set(DEFAULT_FLOATTEST 32bit)
  elseif(CPU_TYPE STREQUAL "i386" AND MSVC)
    set(DEFAULT_FLOATTEST msvc)
  endif()
else()
  if(BITS EQUAL 64)
    set(DEFAULT_FLOATTEST 64bit)
  elseif(BITS EQUAL 32)
    set(DEFAULT_FLOATTEST 32bit)
  endif()
endif()

# This causes FLOATTEST to reset to the default value if WITH_SIMD has
# changed.
if(DEFINED WITH_SIMD_INT AND NOT WITH_SIMD EQUAL WITH_SIMD_INT)
  set(FORCE_FLOATTEST "FORCE")
endif()
set(WITH_SIMD_INT ${WITH_SIMD} CACHE INTERNAL "")
set(FLOATTEST ${DEFAULT_FLOATTEST} CACHE STRING
  "The type of floating point math used by the floating point DCT/IDCT algorithms.  This tells the testing system which numerical results it should expect from those tests.  [sse = libjpeg-turbo x86/x86-64 SIMD extensions, 32bit = generic 32-bit FPU or SSE, 64bit = generic 64-bit FPU, 387 = 387 FPU, msvc = 32-bit Visual Studio] (default = ${DEFAULT_FLOATTEST})"
  ${FORCE_FLOATTEST})
message(STATUS "FLOATTEST = ${FLOATTEST}")

if(FLOATTEST)
  string(TOUPPER ${FLOATTEST} FLOATTEST_UC)
  string(TOLOWER ${FLOATTEST} FLOATTEST)
  if(NOT FLOATTEST STREQUAL "sse" AND NOT FLOATTEST STREQUAL "32bit" AND
    NOT FLOATTEST STREQUAL "64bit" AND NOT FLOATTEST STREQUAL "387" AND
    NOT FLOATTEST STREQUAL "msvc")
    message(FATAL_ERROR "\"${FLOATTEST}\" is not a valid value for FLOATTEST.")
  endif()
endif()

foreach(libtype ${TEST_LIBTYPES})
  if(libtype STREQUAL "static")
    set(suffix -static)
  endif()
  if(WITH_TURBOJPEG)
    add_test(tjunittest-${libtype} tjunittest${suffix})
    add_test(tjunittest-${libtype}-alloc tjunittest${suffix} -alloc)
    add_test(tjunittest-${libtype}-yuv tjunittest${suffix} -yuv)
    add_test(tjunittest-${libtype}-yuv-alloc tjunittest${suffix} -yuv -alloc)
    add_test(tjunittest-${libtype}-yuv-nopad tjunittest${suffix} -yuv -noyuvpad)
    add_test(tjunittest-${libtype}-bmp tjunittest${suffix} -bmp)

    set(MD5_PPM_GRAY_TILE 89d3ca21213d9d864b50b4e4e7de4ca6)
    set(MD5_PPM_420_8x8_TILE 847fceab15c5b7b911cb986cf0f71de3)
    set(MD5_PPM_420_16x16_TILE ca45552a93687e078f7137cc4126a7b0)
    set(MD5_PPM_420_32x32_TILE d8676f1d6b68df358353bba9844f4a00)
    set(MD5_PPM_420_64x64_TILE 4e4c1a3d7ea4bace4f868bcbe83b7050)
    set(MD5_PPM_420_128x128_TILE f24c3429c52265832beab9df72a0ceae)
    set(MD5_PPM_420M_8x8_TILE bc25320e1f4c31ce2e610e43e9fd173c)
    set(MD5_PPM_420M_TILE 75ffdf14602258c5c189522af57fa605)
    set(MD5_PPM_422_8x8_TILE d83dacd9fc73b0a6f10c09acad64eb1e)
    set(MD5_PPM_422_16x16_TILE 35077fb610d72dd743b1eb0cbcfe10fb)
    set(MD5_PPM_422_32x32_TILE e6902ed8a449ecc0f0d6f2bf945f65f7)
    set(MD5_PPM_422_64x64_TILE 2b4502a8f316cedbde1da7bce3d2231e)
    set(MD5_PPM_422_128x128_TILE f0b5617d578f5e13c8eee215d64d4877)
    set(MD5_PPM_422M_8x8_TILE 828941d7f41cd6283abd6beffb7fd51d)
    set(MD5_PPM_422M_TILE e877ae1324c4a280b95376f7f018172f)
    set(MD5_PPM_444_TILE 7964e41e67cfb8d0a587c0aa4798f9c3)

    # Test compressing from/decompressing to an arbitrary subregion of a larger
    # image buffer
    add_test(tjbench-${libtype}-tile-cp
      ${CMAKE_COMMAND} -E copy_if_different ${TESTIMAGES}/testorig.ppm
        testout_tile.ppm)
    add_test(tjbench-${libtype}-tile
      tjbench${suffix} testout_tile.ppm 95 -rgb -quiet -tile -benchtime 0.01
        -warmup 0)
    set_tests_properties(tjbench-${libtype}-tile
      PROPERTIES DEPENDS tjbench-${libtype}-tile-cp)

    foreach(tile 8 16 32 64 128)
      add_test(tjbench-${libtype}-tile-gray-${tile}x${tile}-cmp
        ${MD5CMP} ${MD5_PPM_GRAY_TILE}
          testout_tile_GRAY_Q95_${tile}x${tile}.ppm)
      foreach(subsamp 420 422)
        add_test(tjbench-${libtype}-tile-${subsamp}-${tile}x${tile}-cmp
          ${MD5CMP} ${MD5_PPM_${subsamp}_${tile}x${tile}_TILE}
            testout_tile_${subsamp}_Q95_${tile}x${tile}.ppm)
      endforeach()
      add_test(tjbench-${libtype}-tile-444-${tile}x${tile}-cmp
        ${MD5CMP} ${MD5_PPM_444_TILE}
          testout_tile_444_Q95_${tile}x${tile}.ppm)
      foreach(subsamp gray 420 422 444)
        set_tests_properties(tjbench-${libtype}-tile-${subsamp}-${tile}x${tile}-cmp
          PROPERTIES DEPENDS tjbench-${libtype}-tile)
      endforeach()
    endforeach()

    add_test(tjbench-${libtype}-tilem-cp
      ${CMAKE_COMMAND} -E copy_if_different ${TESTIMAGES}/testorig.ppm
        testout_tilem.ppm)
    add_test(tjbench-${libtype}-tilem
      tjbench${suffix} testout_tilem.ppm 95 -rgb -fastupsample -quiet -tile
        -benchtime 0.01 -warmup 0)
    set_tests_properties(tjbench-${libtype}-tilem
      PROPERTIES DEPENDS tjbench-${libtype}-tilem-cp)

    add_test(tjbench-${libtype}-tile-420m-8x8-cmp
      ${MD5CMP} ${MD5_PPM_420M_8x8_TILE} testout_tilem_420_Q95_8x8.ppm)
    add_test(tjbench-${libtype}-tile-422m-8x8-cmp
      ${MD5CMP} ${MD5_PPM_422M_8x8_TILE} testout_tilem_422_Q95_8x8.ppm)
    foreach(tile 16 32 64 128)
      foreach(subsamp 420 422)
        add_test(tjbench-${libtype}-tile-${subsamp}m-${tile}x${tile}-cmp
          ${MD5CMP} ${MD5_PPM_${subsamp}M_TILE}
            testout_tilem_${subsamp}_Q95_${tile}x${tile}.ppm)
      endforeach()
    endforeach()
    foreach(tile 8 16 32 64 128)
      foreach(subsamp 420 422)
        set_tests_properties(tjbench-${libtype}-tile-${subsamp}m-${tile}x${tile}-cmp
          PROPERTIES DEPENDS tjbench-${libtype}-tilem)
      endforeach()
    endforeach()
  endif()

  # These tests are carefully crafted to provide full coverage of as many of
  # the underlying algorithms as possible (including all of the
  # SIMD-accelerated ones.)

  macro(add_bittest PROG NAME ARGS OUTFILE INFILE MD5SUM)
    add_test(${PROG}-${libtype}-${NAME}
      ${PROG}${suffix} ${ARGS} -outfile ${OUTFILE} ${INFILE})
    add_test(${PROG}-${libtype}-${NAME}-cmp
      ${MD5CMP} ${MD5SUM} ${OUTFILE})
    set_tests_properties(${PROG}-${libtype}-${NAME}-cmp PROPERTIES
      DEPENDS ${PROG}-${libtype}-${NAME})
    if(${ARGC} GREATER 6)
      set(DEPENDS ${ARGN})
      set_tests_properties(${PROG}-${libtype}-${NAME} PROPERTIES
        DEPENDS ${DEPENDS})
    endif()
  endmacro()

  # CC: null  SAMP: fullsize  FDCT: islow  ENT: huff
  add_bittest(cjpeg rgb-islow "-rgb;-dct;int;-icc;${TESTIMAGES}/test1.icc"
    testout_rgb_islow.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_RGB_ISLOW})

  # CC: null  SAMP: fullsize  IDCT: islow  ENT: huff
  add_bittest(djpeg rgb-islow "-dct;int;-ppm;-icc;testout_rgb_islow.icc"
    testout_rgb_islow.ppm testout_rgb_islow.jpg
    ${MD5_PPM_RGB_ISLOW} cjpeg-${libtype}-rgb-islow)

  add_test(djpeg-${libtype}-rgb-islow-icc-cmp
    ${MD5CMP} b06a39d730129122e85c1363ed1bbc9e testout_rgb_islow.icc)

  add_bittest(jpegtran icc "-copy;all;-icc;${TESTIMAGES}/test2.icc"
    testout_rgb_islow2.jpg testout_rgb_islow.jpg ${MD5_JPEG_RGB_ISLOW2})

  if(NOT WITH_12BIT)
    # CC: RGB->RGB565  SAMP: fullsize  IDCT: islow  ENT: huff
    add_bittest(djpeg rgb-islow-565 "-dct;int;-rgb565;-dither;none;-bmp"
      testout_rgb_islow_565.bmp testout_rgb_islow.jpg
      ${MD5_BMP_RGB_ISLOW_565} cjpeg-${libtype}-rgb-islow)

    # CC: RGB->RGB565 (dithered)  SAMP: fullsize  IDCT: islow  ENT: huff
    add_bittest(djpeg rgb-islow-565D "-dct;int;-rgb565;-bmp"
      testout_rgb_islow_565D.bmp testout_rgb_islow.jpg
      ${MD5_BMP_RGB_ISLOW_565D} cjpeg-${libtype}-rgb-islow)
  endif()

  # CC: RGB->CMYK  SAMP: fullsize/h2v1  FDCT: ifast  ENT: 2-pass huff
  add_test(cjpeg${suffix}-422-ifast-opt-cmyk
    ${dir}cjpeg${suffix} -sample 2x1 -dct fast -opt -cmyk
      -outfile testout_422_ifast_opt_cmyk.jpg ${TESTIMAGES}/testorig.ppm)

  # CC: CMYK->RGB  SAMP: fullsize/h2v1 fancy  IDCT: ifast  ENT: huff
  add_test(djpeg${suffix}-422-ifast-cmyk
    ${dir}djpeg${suffix} -dct fast -rgb
      -outfile testout_422_ifast_cmyk.ppm testout_422_ifast_opt_cmyk.jpg)

  # CC: CMYK->RGB  SAMP: h2v1 merged  IDCT: ifast  ENT: huff
  add_test(djpeg${suffix}-422m-ifast-cmyk
    ${dir}djpeg${suffix} -dct fast -nosmooth -rgb
      -outfile testout_422m_ifast_cmyk.ppm testout_422_ifast_opt_cmyk.jpg)

  # CC: CMYK->RGB565  SAMP: h2v1 merged  IDCT: ifast  ENT: huff  DITHER: none
  add_test(djpeg${suffix}-422m-ifast-565-cmyk
    ${dir}djpeg${suffix} -dct int -nosmooth -rgb565 -dither none -bmp
      -outfile testout_422m_ifast_565_cmyk.bmp testout_422_ifast_opt_cmyk.jpg)

  # CC: CMYK->RGB565  SAMP: h2v1 merged  IDCT: ifast  ENT: huff  DITHER: ordered
  add_test(djpeg${suffix}-422m-ifast-565D-cmyk
    ${dir}djpeg${suffix} -dct int -nosmooth -rgb565 -dither ordered -bmp
      -outfile testout_422m_ifast_565D_cmyk.bmp testout_422_ifast_opt_cmyk.jpg)

  # CC: RGB->YCCK  SAMP: fullsize/h2v1  FDCT: ifast  ENT: 2-pass huff
  add_test(cjpeg${suffix}-422-ifast-opt-ycck
    ${dir}cjpeg${suffix} -sample 2x1 -dct fast -opt -ycck
      -outfile testout_422_ifast_opt_ycck.jpg ${TESTIMAGES}/testorig.ppm)

  # CC: YCCK->RGB  SAMP: fullsize/h2v1 fancy  IDCT: ifast  ENT: huff
  add_test(djpeg${suffix}-422-ifast-ycck
    ${dir}djpeg${suffix} -dct fast -rgb
      -outfile testout_422_ifast_ycck.ppm testout_422_ifast_opt_ycck.jpg)

  # CC: YCCK->RGB  SAMP: h2v1 merged  IDCT: ifast  ENT: huff
  add_test(djpeg${suffix}-422m-ifast-ycck
    ${dir}djpeg${suffix} -dct fast -nosmooth -rgb
      -outfile testout_422m_ifast_ycck.ppm testout_422_ifast_opt_ycck.jpg)

  # CC: YCCK->RGB565  SAMP: h2v1 merged  IDCT: ifast  ENT: huff  DITHER: none
  add_test(djpeg${suffix}-422m-ifast-565-ycck
    ${dir}djpeg${suffix} -dct int -nosmooth -rgb565 -dither none -bmp
      -outfile testout_422m_ifast_565_ycck.bmp testout_422_ifast_opt_ycck.jpg)

  # CC: YCCK->RGB565  SAMP: h2v1 merged  IDCT: ifast  ENT: huff  DITHER: ordered
  add_test(djpeg${suffix}-422m-ifast-565D-ycck
    ${dir}djpeg${suffix} -dct int -nosmooth -rgb565 -dither ordered -bmp
      -outfile testout_422m_ifast_565D_ycck.bmp testout_422_ifast_opt_ycck.jpg)

  # CC: RGB->YCC  SAMP: fullsize/h2v1  FDCT: ifast  ENT: 2-pass huff
  add_bittest(cjpeg 422-ifast-opt "-sample;2x1;-dct;fast;-opt"
    testout_422_ifast_opt.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_422_IFAST_OPT})

  # CC: YCC->RGB  SAMP: fullsize/h2v1 fancy  IDCT: ifast  ENT: huff
  add_bittest(djpeg 422-ifast "-dct;fast"
    testout_422_ifast.ppm testout_422_ifast_opt.jpg
    ${MD5_PPM_422_IFAST} cjpeg-${libtype}-422-ifast-opt)

  # CC: YCC->RGB  SAMP: h2v1 merged  IDCT: ifast  ENT: huff
  add_bittest(djpeg 422m-ifast "-dct;fast;-nosmooth"
    testout_422m_ifast.ppm testout_422_ifast_opt.jpg
    ${MD5_PPM_422M_IFAST} cjpeg-${libtype}-422-ifast-opt)

  if(NOT WITH_12BIT)
    # CC: YCC->RGB565  SAMP: h2v1 merged  IDCT: ifast  ENT: huff
    add_bittest(djpeg 422m-ifast-565
      "-dct;int;-nosmooth;-rgb565;-dither;none;-bmp"
      testout_422m_ifast_565.bmp testout_422_ifast_opt.jpg
      ${MD5_BMP_422M_IFAST_565} cjpeg-${libtype}-422-ifast-opt)

    # CC: YCC->RGB565 (dithered)  SAMP: h2v1 merged  IDCT: ifast  ENT: huff
    add_bittest(djpeg 422m-ifast-565D "-dct;int;-nosmooth;-rgb565;-bmp"
      testout_422m_ifast_565D.bmp testout_422_ifast_opt.jpg
      ${MD5_BMP_422M_IFAST_565D} cjpeg-${libtype}-422-ifast-opt)
  endif()

  # CC: RGB->YCC  SAMP: fullsize/h2v2  FDCT: ifast  ENT: prog huff
  add_bittest(cjpeg 420-q100-ifast-prog
    "-sample;2x2;-quality;100;-dct;fast;-prog"
    testout_420_q100_ifast_prog.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_420_IFAST_Q100_PROG})

  # CC: YCC->RGB  SAMP: fullsize/h2v2 fancy  IDCT: ifast  ENT: prog huff
  add_bittest(djpeg 420-q100-ifast-prog "-dct;fast"
    testout_420_q100_ifast.ppm testout_420_q100_ifast_prog.jpg
    ${MD5_PPM_420_Q100_IFAST} cjpeg-${libtype}-420-q100-ifast-prog)

  # CC: YCC->RGB  SAMP: h2v2 merged  IDCT: ifast  ENT: prog huff
  add_bittest(djpeg 420m-q100-ifast-prog "-dct;fast;-nosmooth"
    testout_420m_q100_ifast.ppm testout_420_q100_ifast_prog.jpg
    ${MD5_PPM_420M_Q100_IFAST} cjpeg-${libtype}-420-q100-ifast-prog)

  # CC: RGB->Gray  SAMP: fullsize  FDCT: islow  ENT: huff
  add_bittest(cjpeg gray-islow "-gray;-dct;int"
    testout_gray_islow.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_GRAY_ISLOW})

  # CC: Gray->Gray  SAMP: fullsize  IDCT: islow  ENT: huff
  add_bittest(djpeg gray-islow "-dct;int"
    testout_gray_islow.ppm testout_gray_islow.jpg
    ${MD5_PPM_GRAY_ISLOW} cjpeg-${libtype}-gray-islow)

  # CC: Gray->RGB  SAMP: fullsize  IDCT: islow  ENT: huff
  add_bittest(djpeg gray-islow-rgb "-dct;int;-rgb"
    testout_gray_islow_rgb.ppm testout_gray_islow.jpg
    ${MD5_PPM_GRAY_ISLOW_RGB} cjpeg-${libtype}-gray-islow)

  if(NOT WITH_12BIT)
    # CC: Gray->RGB565  SAMP: fullsize  IDCT: islow  ENT: huff
    add_bittest(djpeg gray-islow-565 "-dct;int;-rgb565;-dither;none;-bmp"
      testout_gray_islow_565.bmp testout_gray_islow.jpg
      ${MD5_BMP_GRAY_ISLOW_565} cjpeg-${libtype}-gray-islow)

    # CC: Gray->RGB565 (dithered)  SAMP: fullsize  IDCT: islow  ENT: huff
    add_bittest(djpeg gray-islow-565D "-dct;int;-rgb565;-bmp"
      testout_gray_islow_565D.bmp testout_gray_islow.jpg
      ${MD5_BMP_GRAY_ISLOW_565D} cjpeg-${libtype}-gray-islow)
  endif()

  # CC: RGB->YCC  SAMP: fullsize smooth/h2v2 smooth  FDCT: islow
  # ENT: 2-pass huff
  add_bittest(cjpeg 420s-ifast-opt "-sample;2x2;-smooth;1;-dct;int;-opt"
    testout_420s_ifast_opt.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_420S_IFAST_OPT})

  if(FLOATTEST)
    # CC: RGB->YCC  SAMP: fullsize/int  FDCT: float  ENT: prog huff
    add_bittest(cjpeg 3x2-float-prog "-sample;3x2;-dct;float;-prog"
      testout_3x2_float_prog.jpg ${TESTIMAGES}/testorig.ppm
      ${MD5_JPEG_3x2_FLOAT_PROG_${FLOATTEST_UC}})

    # CC: YCC->RGB  SAMP: fullsize/int  IDCT: float  ENT: prog huff
    add_bittest(djpeg 3x2-float-prog "-dct;float"
      testout_3x2_float.ppm testout_3x2_float_prog.jpg
      ${MD5_PPM_3x2_FLOAT_${FLOATTEST_UC}} cjpeg-${libtype}-3x2-float-prog)
  endif()

    # CC: RGB->YCC  SAMP: fullsize/int  FDCT: ifast  ENT: prog huff
  add_bittest(cjpeg 3x2-ifast-prog "-sample;3x2;-dct;fast;-prog"
    testout_3x2_ifast_prog.jpg ${TESTIMAGES}/testorig.ppm
    ${MD5_JPEG_3x2_IFAST_PROG})

  # CC: YCC->RGB  SAMP: fullsize/int  IDCT: ifast  ENT: prog huff
  add_bittest(djpeg 3x2-ifast-prog "-dct;fast"
    testout_3x2_ifast.ppm testout_3x2_ifast_prog.jpg
    ${MD5_PPM_3x2_IFAST} cjpeg-${libtype}-3x2-ifast-prog)

  if(WITH_ARITH_ENC)
    # CC: YCC->RGB  SAMP: fullsize/h2v2  FDCT: islow  ENT: arith
    add_bittest(cjpeg 420-islow-ari "-dct;int;-arithmetic"
      testout_420_islow_ari.jpg ${TESTIMAGES}/testorig.ppm
      ${MD5_JPEG_420_ISLOW_ARI})

    add_bittest(jpegtran 420-islow-ari "-arithmetic"
      testout_420_islow_ari2.jpg ${TESTIMAGES}/testimgint.jpg
      ${MD5_JPEG_420_ISLOW_ARI})

    # CC: YCC->RGB  SAMP: fullsize  FDCT: islow  ENT: prog arith
    add_bittest(cjpeg 444-islow-progari
      "-sample;1x1;-dct;int;-prog;-arithmetic"
      testout_444_islow_progari.jpg ${TESTIMAGES}/testorig.ppm
      ${MD5_JPEG_444_ISLOW_PROGARI})
  endif()

  if(WITH_ARITH_DEC)
    # CC: RGB->YCC  SAMP: h2v2 merged  IDCT: ifast  ENT: arith
    add_bittest(djpeg 420m-ifast-ari "-fast;-ppm"
      testout_420m_ifast_ari.ppm ${TESTIMAGES}/testimgari.jpg
      ${MD5_PPM_420M_IFAST_ARI})

    add_bittest(jpegtran 420-islow ""
      testout_420_islow.jpg ${TESTIMAGES}/testimgari.jpg
      ${MD5_JPEG_420_ISLOW})
  endif()

  # 2/1--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 16x16 islow  ENT: huff
  # 15/8--  CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 15x15 islow  ENT: huff
  # 13/8--  CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 13x13 islow  ENT: huff
  # 11/8--  CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 11x11 islow  ENT: huff
  # 9/8--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 9x9 islow  ENT: huff
  # 7/8--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 7x7 islow/14x14 islow
  #         ENT: huff
  # 3/4--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 6x6 islow/12x12 islow
  #         ENT: huff
  # 5/8--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 5x5 islow/10x10 islow
  #         ENT: huff
  # 1/2--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 4x4 islow/8x8 islow
  #         ENT: huff
  # 3/8--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 3x3 islow/6x6 islow
  #         ENT: huff
  # 1/4--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 2x2 islow/4x4 islow
  #         ENT: huff
  # 1/8--   CC: YCC->RGB  SAMP: h2v2 merged  IDCT: 1x1 islow/2x2 islow
  #         ENT: huff
  foreach(scale 2_1 15_8 13_8 11_8 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8)
    string(REGEX REPLACE "_" "/" scalearg ${scale})
    add_bittest(djpeg 420m-islow-${scale}
      "-dct;int;-scale;${scalearg};-nosmooth;-ppm"
      testout_420m_islow_${scale}.ppm ${TESTIMAGES}/${TESTORIG}
      ${MD5_PPM_420M_ISLOW_${scale}})
  endforeach()

  if(NOT WITH_12BIT)
    # CC: YCC->RGB (dithered)  SAMP: h2v2 fancy  IDCT: islow  ENT: huff
    add_bittest(djpeg 420-islow-256 "-dct;int;-colors;256;-bmp"
      testout_420_islow_256.bmp ${TESTIMAGES}/${TESTORIG}
      ${MD5_BMP_420_ISLOW_256})

    # CC: YCC->RGB565  SAMP: h2v2 fancy  IDCT: islow  ENT: huff
    add_bittest(djpeg 420-islow-565 "-dct;int;-rgb565;-dither;none;-bmp"
      testout_420_islow_565.bmp ${TESTIMAGES}/${TESTORIG}
      ${MD5_BMP_420_ISLOW_565})

    # CC: YCC->RGB565 (dithered)  SAMP: h2v2 fancy  IDCT: islow  ENT: huff
    add_bittest(djpeg 420-islow-565D "-dct;int;-rgb565;-bmp"
      testout_420_islow_565D.bmp ${TESTIMAGES}/${TESTORIG}
      ${MD5_BMP_420_ISLOW_565D})

    # CC: YCC->RGB565  SAMP: h2v2 merged  IDCT: islow  ENT: huff
    add_bittest(djpeg 420m-islow-565
      "-dct;int;-nosmooth;-rgb565;-dither;none;-bmp"
      testout_420m_islow_565.bmp ${TESTIMAGES}/${TESTORIG}
      ${MD5_BMP_420M_ISLOW_565})

    # CC: YCC->RGB565 (dithered)  SAMP: h2v2 merged  IDCT: islow  ENT: huff
    add_bittest(djpeg 420m-islow-565D "-dct;int;-nosmooth;-rgb565;-bmp"
      testout_420m_islow_565D.bmp ${TESTIMAGES}/${TESTORIG}
      ${MD5_BMP_420M_ISLOW_565D})
  endif()

  # Partial decode tests.  These tests are designed to cover all of the
  # possible code paths in jpeg_skip_scanlines().

  # Context rows: Yes  Intra-iMCU row: Yes  iMCU row prefetch: No   ENT: huff
  add_bittest(djpeg 420-islow-skip15_31 "-dct;int;-skip;15,31;-ppm"
    testout_420_islow_skip15,31.ppm ${TESTIMAGES}/${TESTORIG}
    ${MD5_PPM_420_ISLOW_SKIP15_31})

  # Context rows: Yes  Intra-iMCU row: No   iMCU row prefetch: Yes  ENT: arith
  if(WITH_ARITH_DEC)
    add_bittest(djpeg 420-islow-ari-skip16_139 "-dct;int;-skip;16,139;-ppm"
      testout_420_islow_ari_skip16,139.ppm ${TESTIMAGES}/testimgari.jpg
      ${MD5_PPM_420_ISLOW_ARI_SKIP16_139})
  endif()

  # Context rows: Yes  Intra-iMCU row: No   iMCU row prefetch: No   ENT: prog huff
  add_test(cjpeg-${libtype}-420-islow-prog
    cjpeg${suffix} -dct int -prog
      -outfile testout_420_islow_prog.jpg ${TESTIMAGES}/testorig.ppm)
  add_bittest(djpeg 420-islow-prog-crop62x62_71_71
    "-dct;int;-crop;62x62+71+71;-ppm"
    testout_420_islow_prog_crop62x62,71,71.ppm testout_420_islow_prog.jpg
    ${MD5_PPM_420_ISLOW_PROG_CROP62x62_71_71} cjpeg-${libtype}-420-islow-prog)

  # Context rows: Yes  Intra-iMCU row: No   iMCU row prefetch: No   ENT: arith
  if(WITH_ARITH_DEC)
    add_bittest(djpeg 420-islow-ari-crop53x53_4_4
      "-dct;int;-crop;53x53+4+4;-ppm"
      testout_420_islow_ari_crop53x53,4,4.ppm ${TESTIMAGES}/testimgari.jpg
      ${MD5_PPM_420_ISLOW_ARI_CROP53x53_4_4})
  endif()

  # Context rows: No   Intra-iMCU row: Yes  ENT: huff
  add_test(cjpeg-${libtype}-444-islow
    cjpeg${suffix} -dct int -sample 1x1
      -outfile testout_444_islow.jpg ${TESTIMAGES}/testorig.ppm)
  add_bittest(djpeg 444-islow-skip1_6 "-dct;int;-skip;1,6;-ppm"
    testout_444_islow_skip1,6.ppm testout_444_islow.jpg
    ${MD5_PPM_444_ISLOW_SKIP1_6} cjpeg-${libtype}-444-islow)

  # Context rows: No   Intra-iMCU row: No   ENT: prog huff
  add_test(cjpeg-${libtype}-444-islow-prog
    cjpeg${suffix} -dct int -prog -sample 1x1
      -outfile testout_444_islow_prog.jpg ${TESTIMAGES}/testorig.ppm)
  add_bittest(djpeg 444-islow-prog-crop98x98_13_13
    "-dct;int;-crop;98x98+13+13;-ppm"
    testout_444_islow_prog_crop98x98,13,13.ppm testout_444_islow_prog.jpg
    ${MD5_PPM_444_ISLOW_PROG_CROP98x98_13_13} cjpeg-${libtype}-444-islow-prog)

  # Context rows: No   Intra-iMCU row: No   ENT: arith
  if(WITH_ARITH_ENC)
    add_test(cjpeg-${libtype}-444-islow-ari
      cjpeg${suffix} -dct int -arithmetic -sample 1x1
        -outfile testout_444_islow_ari.jpg ${TESTIMAGES}/testorig.ppm)
    if(WITH_ARITH_DEC)
      add_bittest(djpeg 444-islow-ari-crop37x37_0_0
        "-dct;int;-crop;37x37+0+0;-ppm"
        testout_444_islow_ari_crop37x37,0,0.ppm testout_444_islow_ari.jpg
        ${MD5_PPM_444_ISLOW_ARI_CROP37x37_0_0} cjpeg-${libtype}-444-islow-ari)
    endif()
  endif()

  add_bittest(jpegtran crop "-crop;120x90+20+50;-transpose;-perfect"
    testout_crop.jpg ${TESTIMAGES}/${TESTORIG}
    ${MD5_JPEG_CROP})

endforeach()

add_custom_target(testclean COMMAND ${CMAKE_COMMAND} -P
  ${CMAKE_CURRENT_SOURCE_DIR}/cmakescripts/testclean.cmake)

if(WITH_TURBOJPEG)
  configure_file(tjbenchtest.in tjbenchtest @ONLY)
  configure_file(tjexampletest.in tjexampletest @ONLY)
  if(WIN32)
    set(BASH bash)
  endif()
  if(WITH_JAVA)
    configure_file(tjbenchtest.java.in tjbenchtest.java @ONLY)
    configure_file(tjexampletest.java.in tjexampletest.java @ONLY)
    add_custom_target(tjtest
      COMMAND echo tjbenchtest
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest
      COMMAND echo tjbenchtest -alloc
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -alloc
      COMMAND echo tjbenchtest -yuv
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -yuv
      COMMAND echo tjbenchtest -yuv -alloc
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -yuv -alloc
      COMMAND echo tjbenchtest -progressive
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -progressive
      COMMAND echo tjexampletest
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjexampletest
      COMMAND echo tjbenchtest.java
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest.java
      COMMAND echo tjbenchtest.java -yuv
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest.java -yuv
      COMMAND echo tjbenchtest.java -progressive
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest.java -progressive
      COMMAND echo tjexampletest.java
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjexampletest.java
      DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest
        ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest.java
        ${CMAKE_CURRENT_BINARY_DIR}/tjexampletest)
  else()
    add_custom_target(tjtest
      COMMAND echo tjbenchtest
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest
      COMMAND echo tjbenchtest -alloc
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -alloc
      COMMAND echo tjbenchtest -yuv
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -yuv
      COMMAND echo tjbenchtest -yuv -alloc
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest -yuv -alloc
      COMMAND echo tjexampletest
      COMMAND ${BASH} ${CMAKE_CURRENT_BINARY_DIR}/tjexampletest
      DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/tjbenchtest)
  endif()
endif()


###############################################################################
# INSTALLATION
###############################################################################

set(EXE ${CMAKE_EXECUTABLE_SUFFIX})

if(WITH_TURBOJPEG)
  if(ENABLE_SHARED)
    install(TARGETS turbojpeg tjbench
      ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
      LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
      RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
  endif()
  if(ENABLE_STATIC)
    install(TARGETS turbojpeg-static ARCHIVE
      DESTINATION ${CMAKE_INSTALL_LIBDIR})
    if(NOT ENABLE_SHARED)
      install(PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/tjbench-static${EXE}
        DESTINATION ${CMAKE_INSTALL_BINDIR} RENAME tjbench${EXE})
    endif()
  endif()
  install(FILES ${CMAKE_CURRENT_SOURCE_DIR}/turbojpeg.h
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
endif()

if(ENABLE_STATIC)
  install(TARGETS jpeg-static ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR})
  if(NOT ENABLE_SHARED)
    install(PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/cjpeg-static${EXE}
      DESTINATION ${CMAKE_INSTALL_BINDIR} RENAME cjpeg${EXE})
    install(PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/djpeg-static${EXE}
      DESTINATION ${CMAKE_INSTALL_BINDIR} RENAME djpeg${EXE})
    install(PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/jpegtran-static${EXE}
      DESTINATION ${CMAKE_INSTALL_BINDIR} RENAME jpegtran${EXE})
  endif()
endif()

install(TARGETS rdjpgcom wrjpgcom RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})

install(FILES ${CMAKE_CURRENT_SOURCE_DIR}/README.ijg
  ${CMAKE_CURRENT_SOURCE_DIR}/README.md ${CMAKE_CURRENT_SOURCE_DIR}/example.txt
  ${CMAKE_CURRENT_SOURCE_DIR}/tjexample.c
  ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg.txt
  ${CMAKE_CURRENT_SOURCE_DIR}/structure.txt
  ${CMAKE_CURRENT_SOURCE_DIR}/usage.txt ${CMAKE_CURRENT_SOURCE_DIR}/wizard.txt
  ${CMAKE_CURRENT_SOURCE_DIR}/LICENSE.md DESTINATION ${CMAKE_INSTALL_DOCDIR})
if(WITH_JAVA)
  install(FILES ${CMAKE_CURRENT_SOURCE_DIR}/java/TJExample.java
    DESTINATION ${CMAKE_INSTALL_DOCDIR})
endif()

if(UNIX OR MINGW)
  install(FILES ${CMAKE_CURRENT_SOURCE_DIR}/cjpeg.1
    ${CMAKE_CURRENT_SOURCE_DIR}/djpeg.1 ${CMAKE_CURRENT_SOURCE_DIR}/jpegtran.1
    ${CMAKE_CURRENT_SOURCE_DIR}/rdjpgcom.1
    ${CMAKE_CURRENT_SOURCE_DIR}/wrjpgcom.1
    DESTINATION ${CMAKE_INSTALL_MANDIR}/man1)
  install(FILES ${CMAKE_CURRENT_BINARY_DIR}/pkgscripts/libjpeg.pc
    ${CMAKE_CURRENT_BINARY_DIR}/pkgscripts/libturbojpeg.pc
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/pkgconfig)
endif()

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/jconfig.h
  ${CMAKE_CURRENT_SOURCE_DIR}/jerror.h ${CMAKE_CURRENT_SOURCE_DIR}/jmorecfg.h
  ${CMAKE_CURRENT_SOURCE_DIR}/jpeglib.h
  DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})

include(cmakescripts/BuildPackages.cmake)

configure_file("${CMAKE_CURRENT_SOURCE_DIR}/cmakescripts/cmake_uninstall.cmake.in"
  "cmake_uninstall.cmake" IMMEDIATE @ONLY)

add_custom_target(uninstall COMMAND ${CMAKE_COMMAND} -P cmake_uninstall.cmake)

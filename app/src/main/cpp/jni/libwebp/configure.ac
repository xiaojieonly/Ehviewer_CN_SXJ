AC_INIT([libwebp], [1.4.0],
        [https://issues.webmproject.org],,
        [https://developers.google.com/speed/webp])
AC_CANONICAL_HOST
AC_PREREQ([2.60])
AM_INIT_AUTOMAKE([-Wall foreign subdir-objects])

dnl === automake >= 1.12 requires this for 'unusual archivers' support.
dnl === it must occur before LT_INIT (AC_PROG_LIBTOOL).
m4_ifdef([AM_PROG_AR], [AM_PROG_AR])

dnl === AC_PROG_LIBTOOL is deprecated.
m4_ifdef([LT_INIT], [LT_INIT], [AC_PROG_LIBTOOL])
AC_PROG_SED
AM_PROG_CC_C_O

dnl === Enable less verbose output when building.
m4_ifdef([AM_SILENT_RULES], [AM_SILENT_RULES([yes])])

dnl == test endianness
AC_C_BIGENDIAN

dnl === SET_IF_UNSET(shell_var, value)
dnl ===   Set the shell variable 'shell_var' to 'value' if it is unset.
AC_DEFUN([SET_IF_UNSET], [test "${$1+set}" = "set" || $1=$2])

AC_ARG_ENABLE([everything],
              AS_HELP_STRING([--enable-everything],
                             [Enable all optional targets. These can still be
                              disabled with --disable-target]),
              [SET_IF_UNSET([enable_libsharpyuv], [$enableval])
               SET_IF_UNSET([enable_libwebpdecoder], [$enableval])
               SET_IF_UNSET([enable_libwebpdemux], [$enableval])
               SET_IF_UNSET([enable_libwebpextras], [$enableval])
               SET_IF_UNSET([enable_libwebpmux], [$enableval])])

dnl === Check whether libwebpmux should be built
AC_MSG_CHECKING(whether libwebpmux is to be built)
AC_ARG_ENABLE([libwebpmux],
              AS_HELP_STRING([--disable-libwebpmux],
                             [Disable libwebpmux @<:@default=no@:>@]),
              [], [enable_libwebpmux=yes])
AC_MSG_RESULT(${enable_libwebpmux-no})
AM_CONDITIONAL([BUILD_MUX], [test "$enable_libwebpmux" = "yes"])

dnl === Check whether libwebpdemux should be built
AC_MSG_CHECKING(whether libwebpdemux is to be built)
AC_ARG_ENABLE([libwebpdemux],
              AS_HELP_STRING([--disable-libwebpdemux],
                             [Disable libwebpdemux @<:@default=no@:>@]),
              [], [enable_libwebpdemux=yes])
AC_MSG_RESULT(${enable_libwebpdemux-no})
AM_CONDITIONAL([BUILD_DEMUX], [test "$enable_libwebpdemux" = "yes"])

dnl === Check whether decoder library should be built.
AC_MSG_CHECKING(whether decoder library is to be built)
AC_ARG_ENABLE([libwebpdecoder],
              AS_HELP_STRING([--enable-libwebpdecoder],
                             [Build libwebpdecoder @<:@default=no@:>@]))
AC_MSG_RESULT(${enable_libwebpdecoder-no})
AM_CONDITIONAL([BUILD_LIBWEBPDECODER], [test "$enable_libwebpdecoder" = "yes"])

dnl === Check whether libwebpextras should be built
AC_MSG_CHECKING(whether libwebpextras is to be built)
AC_ARG_ENABLE([libwebpextras],
              AS_HELP_STRING([--enable-libwebpextras],
                             [Build libwebpextras @<:@default=no@:>@]))
AC_MSG_RESULT(${enable_libwebpextras-no})
AM_CONDITIONAL([BUILD_EXTRAS], [test "$enable_libwebpextras" = "yes"])

dnl === If --enable-asserts is not defined, define NDEBUG

AC_MSG_CHECKING(whether asserts are enabled)
AC_ARG_ENABLE([asserts],
              AS_HELP_STRING([--enable-asserts],
                             [Enable assert checks]))
if test "x${enable_asserts-no}" = "xno"; then
  AM_CPPFLAGS="${AM_CPPFLAGS} -DNDEBUG"
fi
AC_MSG_RESULT(${enable_asserts-no})
AC_SUBST([AM_CPPFLAGS])

AC_ARG_WITH([pkgconfigdir], AS_HELP_STRING([--with-pkgconfigdir=DIR],
            [Path to the pkgconfig directory @<:@LIBDIR/pkgconfig@:>@]),
            [pkgconfigdir="$withval"], [pkgconfigdir='${libdir}/pkgconfig'])
AC_SUBST([pkgconfigdir])

dnl === TEST_AND_ADD_CFLAGS(var, flag)
dnl ===   Checks whether $CC supports 'flag' and adds it to 'var'
dnl ===   on success.
AC_DEFUN([TEST_AND_ADD_CFLAGS],
         [SAVED_CFLAGS="$CFLAGS"
          CFLAGS="-Werror $2"
          AC_MSG_CHECKING([whether $CC supports $2])
          dnl Note AC_LANG_PROGRAM([]) uses an old-style main definition.
          AC_COMPILE_IFELSE([AC_LANG_SOURCE([int main(void) { return 0; }])],
                            [AC_MSG_RESULT([yes])]
                            dnl Simply append the variable avoiding a
                            dnl compatibility ifdef for AS_VAR_APPEND as this
                            dnl variable shouldn't grow all that large.
                            [$1="${$1} $2"],
                            [AC_MSG_RESULT([no])])
          CFLAGS="$SAVED_CFLAGS"])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-fvisibility=hidden])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wall])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wconstant-conversion])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wdeclaration-after-statement])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wextra])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wextra-semi-stmt])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wfloat-conversion])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wformat -Wformat-nonliteral])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wformat -Wformat-security])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wmissing-declarations])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wmissing-prototypes])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wold-style-definition])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wparentheses-equality])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wshadow])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wshorten-64-to-32])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wstrict-prototypes])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wundef])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wunreachable-code-aggressive])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wunreachable-code])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wunused-but-set-variable])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wunused])
TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wvla])
# https://gcc.gnu.org/bugzilla/show_bug.cgi?id=62040
# https://gcc.gnu.org/bugzilla/show_bug.cgi?id=61622
AS_IF([test "$GCC" = "yes" ], [
       gcc_version=`$CC -dumpversion`
       gcc_wht_bug=""
       case "$host_cpu" in
         aarch64|arm64)
          case "$gcc_version" in
            4.9|4.9.0|4.9.1) gcc_wht_bug=yes ;;
          esac
       esac
       AS_IF([test "$gcc_wht_bug" = "yes"], [
              TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-frename-registers])])])
# Use -flax-vector-conversions, if available, when building intrinsics with
# older versions of gcc. The flag appeared in 4.3.x, but if backported, and
# -fno-lax-vector-conversions is set, errors may occur with the intrinsics
# files along with the older system includes, e.g., emmintrin.h.
# Originally observed with cc (GCC) 4.2.1 20070831 patched [FreeBSD] (9.3).
# https://bugs.chromium.org/p/webp/issues/detail?id=274
AS_IF([test "$GCC" = "yes" ], [
       case "$host_cpu" in
         amd64|i?86|x86_64)
           AC_COMPILE_IFELSE(
             dnl only check for -flax-vector-conversions with older gcc, skip
             dnl clang as it reports itself as 4.2.1, but the flag isn't needed.
             [AC_LANG_SOURCE([#if !defined(__clang__) && defined(__GNUC__) && \
                                  ((__GNUC__ << 8) | __GNUC_MINOR__) < 0x403
                              #error old gcc
                              #endif
                              int main(void) { return 0; }
                             ])],,
              [TEST_AND_ADD_CFLAGS([INTRINSICS_CFLAGS],
                                   [-flax-vector-conversions])])
           ;;
       esac])
AC_SUBST([AM_CFLAGS])

dnl === Check for machine specific flags
AC_ARG_ENABLE([sse4.1],
              AS_HELP_STRING([--disable-sse4.1],
                             [Disable detection of SSE4.1 support
                              @<:@default=auto@:>@]))

AS_IF([test "x$enable_sse4_1" != "xno" -a "x$enable_sse2" != "xno"], [
  SSE41_FLAGS="$INTRINSICS_CFLAGS $SSE41_FLAGS"
  TEST_AND_ADD_CFLAGS([SSE41_FLAGS], [-msse4.1])
  AS_IF([test -n "$SSE41_FLAGS"], [
    SAVED_CFLAGS=$CFLAGS
    CFLAGS="$CFLAGS $SSE41_FLAGS"
    AC_CHECK_HEADER([smmintrin.h],
                    [AC_DEFINE(WEBP_HAVE_SSE41, [1],
                     [Set to 1 if SSE4.1 is supported])],
                    [SSE41_FLAGS=""])
    CFLAGS=$SAVED_CFLAGS])
  AC_SUBST([SSE41_FLAGS])])

AC_ARG_ENABLE([sse2],
              AS_HELP_STRING([--disable-sse2],
                             [Disable detection of SSE2 support
                              @<:@default=auto@:>@]))

AS_IF([test "x$enable_sse2" != "xno"], [
  SSE2_FLAGS="$INTRINSICS_CFLAGS $SSE2_FLAGS"
  TEST_AND_ADD_CFLAGS([SSE2_FLAGS], [-msse2])
  AS_IF([test -n "$SSE2_FLAGS"], [
    SAVED_CFLAGS=$CFLAGS
    CFLAGS="$CFLAGS $SSE2_FLAGS"
    AC_CHECK_HEADER([emmintrin.h],
                    [AC_DEFINE(WEBP_HAVE_SSE2, [1],
                     [Set to 1 if SSE2 is supported])],
                    [SSE2_FLAGS=""])
    CFLAGS=$SAVED_CFLAGS])
  AC_SUBST([SSE2_FLAGS])])

AC_ARG_ENABLE([neon],
              AS_HELP_STRING([--disable-neon],
                             [Disable detection of NEON support
                              @<:@default=auto@:>@]))

AC_ARG_ENABLE([neon_rtcd],
              AS_HELP_STRING([--disable-neon-rtcd],
                             [Disable runtime detection of NEON support via
                              /proc/cpuinfo on Linux hosts
                              @<:@default=auto@:>@]))
# For ARM(7) hosts:
# Both NEON flags unset and NEON support detected = build all modules with NEON
# NEON detected with the use of -mfpu=neon = build only NEON modules with NEON
AS_IF([test "x$enable_neon" != "xno"], [
  case "$host_cpu" in
    arm|armv7*)
      # Test for NEON support without flags before falling back to -mfpu=neon
      for flag in '' '-mfpu=neon'; do
        LOCAL_NEON_FLAGS="$INTRINSICS_CFLAGS $NEON_FLAGS"
        TEST_AND_ADD_CFLAGS([LOCAL_NEON_FLAGS], [$flag])
        SAVED_CFLAGS=$CFLAGS
        CFLAGS="$CFLAGS $LOCAL_NEON_FLAGS"

        dnl Note AC_LANG_PROGRAM([]) uses an old-style main definition.
        AC_COMPILE_IFELSE([AC_LANG_SOURCE([
          #include <arm_neon.h>
          int main(void) {
            int8x8_t v = vdup_n_s8(0);
            (void)v;
            return 0;
          }])],
          [NEON_FLAGS="$(echo $LOCAL_NEON_FLAGS | $SED 's/^ *//')"
           AS_IF([test -n "$NEON_FLAGS"], [
             AS_IF([test "${host_os%%-*}" = "linux" -o \
                         "x$enable_neon_rtcd" = "xno"], [
               CFLAGS=$SAVED_CFLAGS
               AC_DEFINE(WEBP_HAVE_NEON, [1], [Set to 1 if NEON is supported])
               break
             ],[
               AC_MSG_WARN(m4_normalize([NEON runtime cpu-detection is
                                         unavailable for ${host_os%%-*}. Force
                                         with CFLAGS=-mfpu=neon or
                                         --disable-neon-rtcd.]))
               enable_neon_rtcd=no
               NEON_FLAGS=""
             ])
           ],[
             CFLAGS=$SAVED_CFLAGS
             AC_DEFINE(WEBP_HAVE_NEON, [1], [Set to 1 if NEON is supported])
             break
           ])])
        CFLAGS=$SAVED_CFLAGS
      done

      AS_IF([test -n "$NEON_FLAGS"], [
        # If NEON is available and rtcd is disabled apply NEON_FLAGS globally.
        AS_IF([test "x$enable_neon_rtcd" = "xno"], [
          AM_CFLAGS="$AM_CFLAGS $NEON_FLAGS"
          NEON_FLAGS=""],
          [AC_DEFINE(WEBP_HAVE_NEON_RTCD, [1],
                     [Set to 1 if runtime detection of NEON is enabled])])])

      case "$host_os" in
        *android*) AC_CHECK_HEADERS([cpu-features.h]) ;;
      esac
      ;;
    aarch64*|arm64*)
      AC_DEFINE(WEBP_HAVE_NEON, [1], [Set to 1 if NEON is supported])
      ;;
  esac
  AC_SUBST([NEON_FLAGS])])

dnl === CLEAR_LIBVARS([var_pfx])
dnl ===   Clears <var_pfx>_{INCLUDES,LIBS}.
AC_DEFUN([CLEAR_LIBVARS], [$1_INCLUDES=""; $1_LIBS=""])

dnl === WITHLIB_OPTION([opt_pfx], [outvar_pfx])
dnl ===   Defines --with-<opt_pfx>{include,lib}dir options which set
dnl ===   the variables <outvar_pfx>_{INCLUDES,LIBS}.
AC_DEFUN([WITHLIB_OPTION],
  [AC_ARG_WITH([$1includedir],
               AS_HELP_STRING([--with-$1includedir=DIR],
                              [use $2 includes from DIR]),
               $2_INCLUDES="-I$withval")
   AC_ARG_WITH([$1libdir],
               AS_HELP_STRING([--with-$1libdir=DIR],
                              [use $2 libraries from DIR]),
               [$2_LIBS="-L$withval"])])

dnl === LIBCHECK_PROLOGUE([var_pfx])
dnl ===   Caches the current values of CPPFLAGS/LIBS in SAVED_* then
dnl ===   prepends the current values with <var_pfx>_{INCLUDES,LIBS}.
AC_DEFUN([LIBCHECK_PROLOGUE],
         [SAVED_CPPFLAGS=$CPPFLAGS
          SAVED_LIBS=$LIBS
          CPPFLAGS="$$1_INCLUDES $CPPFLAGS"
          LIBS="$$1_LIBS $LIBS"])

dnl === LIBCHECK_EPILOGUE([var_pfx])
dnl ===   Restores the values of CPPFLAGS/LIBS from SAVED_* and exports
dnl ===   <var_pfx>_{INCLUDES,LIBS} with AC_SUBST.
AC_DEFUN([LIBCHECK_EPILOGUE],
         [AC_SUBST($1_LIBS)
          AC_SUBST($1_INCLUDES)
          CPPFLAGS=$SAVED_CPPFLAGS
          LIBS=$SAVED_LIBS])

dnl === Check for gcc builtins

dnl === CHECK_FOR_BUILTIN([builtin], [param], [define])
dnl ===   links a C AC_LANG_PROGRAM, with <builtin>(<param>)
dnl ===   AC_DEFINE'ing <define> if successful.
AC_DEFUN([CHECK_FOR_BUILTIN],
         [AC_LANG_PUSH([C])
          AC_MSG_CHECKING([for $1])
          AC_LINK_IFELSE([AC_LANG_PROGRAM([], [(void)$1($2)])],
                         [AC_MSG_RESULT([yes])
                          AC_DEFINE([$3], [1],
                                    [Set to 1 if $1 is available])],
                         [AC_MSG_RESULT([no])]),
          AC_LANG_POP])

dnl AC_CHECK_FUNC doesn't work with builtin's.
CHECK_FOR_BUILTIN([__builtin_bswap16], [1u << 15], [HAVE_BUILTIN_BSWAP16])
CHECK_FOR_BUILTIN([__builtin_bswap32], [1u << 31], [HAVE_BUILTIN_BSWAP32])
CHECK_FOR_BUILTIN([__builtin_bswap64], [1ull << 63], [HAVE_BUILTIN_BSWAP64])

dnl === Check for pthread support
AC_ARG_ENABLE([threading],
              AS_HELP_STRING([--disable-threading],
                             [Disable detection of thread support]),,
              [enable_threading=yes])
if test "$enable_threading" = "yes"; then
  AC_MSG_NOTICE([checking for threading support...])
  AX_PTHREAD([AC_DEFINE([WEBP_USE_THREAD], [1],
                        [Undefine this to disable thread support.])
              LIBS="$PTHREAD_LIBS $LIBS"
              CFLAGS="$CFLAGS $PTHREAD_CFLAGS"
              CC="$PTHREAD_CC"
             ],
             [AC_CHECK_FUNC([_beginthreadex],
                            [AC_DEFINE([WEBP_USE_THREAD], [1],
                                       [Undefine this to disable thread
                                        support.])],
                            [enable_threading=no])])
fi
AC_MSG_NOTICE([checking if threading is enabled... ${enable_threading-no}])

dnl === check for OpenGL/GLUT support ===

AC_ARG_ENABLE([gl], AS_HELP_STRING([--disable-gl],
                                   [Disable detection of OpenGL support
                                    @<:@default=auto@:>@]))
AS_IF([test "x$enable_gl" != "xno"], [
  CLEAR_LIBVARS([GL])
  WITHLIB_OPTION([gl], [GL])

  LIBCHECK_PROLOGUE([GL])

  glut_cflags="none"
  glut_ldflags="none"
  case $host_os in
    darwin*)
      # Special case for OSX builds. Append these to give the user a chance to
      # override with --with-gl*
      glut_cflags="$glut_cflags|-framework GLUT -framework OpenGL"
      glut_ldflags="$glut_ldflags|-framework GLUT -framework OpenGL"
      # quiet deprecation warnings for glut
      TEST_AND_ADD_CFLAGS([AM_CFLAGS], [-Wno-deprecated-declarations])
      ;;
  esac

  GLUT_SAVED_CPPFLAGS="$CPPFLAGS"
  SAVED_IFS="$IFS"
  IFS="|"
  for flag in $glut_cflags; do
    # restore IFS immediately as the autoconf macros may need the default.
    IFS="$SAVED_IFS"
    unset ac_cv_header_GL_glut_h
    unset ac_cv_header_OpenGL_glut_h

    case $flag in
      none) ;;
      *) CPPFLAGS="$flag $CPPFLAGS";;
    esac
    AC_CHECK_HEADERS([GL/glut.h GLUT/glut.h OpenGL/glut.h],
                     [glut_headers=yes;
                      test "$flag" = "none" || GL_INCLUDES="$CPPFLAGS";
                      break])
    CPPFLAGS="$GLUT_SAVED_CPPFLAGS"
    test "$glut_headers" = "yes" && break
  done
  IFS="$SAVED_IFS"

  if test "$glut_headers" = "yes"; then
    AC_LANG_PUSH([C])
    GLUT_SAVED_LDFLAGS="$LDFLAGS"
    SAVED_IFS="$IFS"
    IFS="|"
    for flag in $glut_ldflags; do
      # restore IFS immediately as the autoconf macros may need the default.
      IFS="$SAVED_IFS"
      unset ac_cv_search_glBegin

      case $flag in
        none) ;;
        *) LDFLAGS="$flag $LDFLAGS";;
      esac

      # find libGL
      GL_SAVED_LIBS="$LIBS"
      AC_SEARCH_LIBS([glBegin], [GL OpenGL opengl32])
      LIBS="$GL_SAVED_LIBS"

      # A direct link to libGL may not be necessary on e.g., linux.
      GLUT_SAVED_LIBS="$LIBS"
      for lib in "" "-lglut" "-lglut $ac_cv_search_glBegin"; do
        LIBS="$lib"
        AC_LINK_IFELSE(
          [AC_LANG_PROGRAM([
             #ifdef __cplusplus
             # define EXTERN_C extern "C"
             #else
             # define EXTERN_C
             #endif
             EXTERN_C char glOrtho();
             EXTERN_C char glutMainLoop();
            ],[
             glOrtho();
             glutMainLoop();
            ])
          ],
          AC_DEFINE(WEBP_HAVE_GL, [1],
                    [Set to 1 if OpenGL is supported])
          [glut_support=yes], []
        )
        if test "$glut_support" = "yes"; then
          GL_LIBS="$LDFLAGS $lib"
          break
        fi
      done
      LIBS="$GLUT_SAVED_LIBS"
      LDFLAGS="$GLUT_SAVED_LDFLAGS"
      test "$glut_support" = "yes" && break
    done
    IFS="$SAVED_IFS"
    AC_LANG_POP
  fi

  LIBCHECK_EPILOGUE([GL])

  if test "$glut_support" = "yes" -a "$enable_libwebpdemux" = "yes"; then
    build_vwebp=yes
  else
    AC_MSG_NOTICE(
      m4_normalize([Not building vwebp.
                    OpenGL libraries and --enable-libwebpdemux are required.]))
  fi
])
AM_CONDITIONAL([BUILD_VWEBP], [test "$build_vwebp" = "yes"])

dnl === check for SDL support ===

AC_ARG_ENABLE([sdl],
              AS_HELP_STRING([--disable-sdl],
                             [Disable detection of SDL support
                              @<:@default=auto@:>@]))
AS_IF([test "x$enable_sdl" != "xno"], [
  CLEAR_LIBVARS([SDL])
  AC_PATH_PROGS([LIBSDL_CONFIG], [sdl2-config])
  if test -n "$LIBSDL_CONFIG"; then
    SDL_INCLUDES=`$LIBSDL_CONFIG --cflags`
    SDL_LIBS="`$LIBSDL_CONFIG --libs`"
  fi

  WITHLIB_OPTION([sdl], [SDL])

  sdl_header="no"
  LIBCHECK_PROLOGUE([SDL])
  AC_CHECK_HEADER([SDL2/SDL.h], [sdl_header="SDL2/SDL.h"],
                  [AC_MSG_WARN(SDL2 library not available - no SDL.h)])
  if test x"$sdl_header" != "xno"; then
    AC_LANG_PUSH(C)
    SDL_SAVED_LIBS="$LIBS"
    for lib in "" "-lSDL2" "-lSDL2main -lSDL2"; do
      LIBS="$SDL_SAVED_LIBS $lib"
      # Perform a full link to ensure SDL_main is resolved if needed.
      AC_LINK_IFELSE(
        [AC_LANG_SOURCE([
           #include <$sdl_header>
           int main(int argc, char** argv) {
             SDL_Init(0);
             return 0;
           }])],
        [SDL_LIBS="$LDFLAGS $LIBS"
         SDL_INCLUDES="$SDL_INCLUDES -DWEBP_HAVE_SDL"
         AC_DEFINE(WEBP_HAVE_SDL, [1],
                   [Set to 1 if SDL library is installed])
         sdl_support=yes]
      )
      if test x"$sdl_support" = "xyes"; then
        break
      fi
    done
    # LIBS is restored by LIBCHECK_EPILOGUE
    AC_LANG_POP
    if test x"$sdl_header" = "xSDL.h"; then
      SDL_INCLUDES="$SDL_INCLUDES -DWEBP_HAVE_JUST_SDL_H"
    fi
  fi
  LIBCHECK_EPILOGUE([SDL])

  if test x"$sdl_support" = "xyes"; then
    build_vwebp_sdl=yes
  else
    AC_MSG_NOTICE([Not building vwebp-sdl. SDL library is required.])
  fi
])

AM_CONDITIONAL([BUILD_VWEBP_SDL], [test "$build_vwebp_sdl" = "yes"])

dnl === check for PNG support ===

AC_ARG_ENABLE([png], AS_HELP_STRING([--disable-png],
                                    [Disable detection of PNG format support
                                     @<:@default=auto@:>@]))
AS_IF([test "x$enable_png" != "xno"], [
  CLEAR_LIBVARS([PNG])
  AC_PATH_PROGS([LIBPNG_CONFIG],
                [libpng-config libpng16-config libpng15-config libpng14-config \
                 libpng12-config])
  if test -n "$LIBPNG_CONFIG"; then
    PNG_INCLUDES=`$LIBPNG_CONFIG --cflags`
    PNG_LIBS="`$LIBPNG_CONFIG --ldflags`"
  fi

  WITHLIB_OPTION([png], [PNG])

  LIBCHECK_PROLOGUE([PNG])
  AC_CHECK_HEADER(png.h,
    AC_SEARCH_LIBS(png_get_libpng_ver, [png],
                   [test "$ac_cv_search_png_get_libpng_ver" = "none required" \
                      || PNG_LIBS="$PNG_LIBS $ac_cv_search_png_get_libpng_ver"
                    PNG_INCLUDES="$PNG_INCLUDES -DWEBP_HAVE_PNG"
                    AC_DEFINE(WEBP_HAVE_PNG, [1],
                              [Set to 1 if PNG library is installed])
                    png_support=yes
                   ],
                   [AC_MSG_WARN(Optional png library not found)
                    PNG_LIBS=""
                    PNG_INCLUDES=""
                   ],
                   [$MATH_LIBS]),
    [AC_MSG_WARN(png library not available - no png.h)
     PNG_LIBS=""
     PNG_INCLUDES=""
    ],
  )
  LIBCHECK_EPILOGUE([PNG])
])

dnl === check for JPEG support ===

AC_ARG_ENABLE([jpeg],
              AS_HELP_STRING([--disable-jpeg],
                             [Disable detection of JPEG format support
                              @<:@default=auto@:>@]))
AS_IF([test "x$enable_jpeg" != "xno"], [
  CLEAR_LIBVARS([JPEG])
  WITHLIB_OPTION([jpeg], [JPEG])

  LIBCHECK_PROLOGUE([JPEG])
  AC_CHECK_HEADER(jpeglib.h,
    AC_CHECK_LIB(jpeg, jpeg_set_defaults,
                 [JPEG_LIBS="$JPEG_LIBS -ljpeg"
                  JPEG_INCLUDES="$JPEG_INCLUDES -DWEBP_HAVE_JPEG"
                  AC_DEFINE(WEBP_HAVE_JPEG, [1],
                            [Set to 1 if JPEG library is installed])
                  jpeg_support=yes
                 ],
                 AC_MSG_WARN(Optional jpeg library not found),
                 [$MATH_LIBS]),
    AC_MSG_WARN(jpeg library not available - no jpeglib.h)
  )
  LIBCHECK_EPILOGUE([JPEG])
])

dnl === check for TIFF support ===

AC_ARG_ENABLE([tiff],
              AS_HELP_STRING([--disable-tiff],
                             [Disable detection of TIFF format support
                              @<:@default=auto@:>@]))
AS_IF([test "x$enable_tiff" != "xno"], [
  CLEAR_LIBVARS([TIFF])
  WITHLIB_OPTION([tiff], [TIFF])

  LIBCHECK_PROLOGUE([TIFF])
  AC_CHECK_HEADER(tiffio.h,
    AC_CHECK_LIB(tiff, TIFFGetVersion,
                 [TIFF_LIBS="$TIFF_LIBS -ltiff"
                  TIFF_INCLUDES="$TIFF_INCLUDES -DWEBP_HAVE_TIFF"
                  AC_DEFINE(WEBP_HAVE_TIFF, [1],
                            [Set to 1 if TIFF library is installed])
                  tiff_support=yes
                 ],
                 AC_MSG_WARN(Optional tiff library not found),
                 [$MATH_LIBS]),
    AC_MSG_WARN(tiff library not available - no tiffio.h)
  )
  LIBCHECK_EPILOGUE([TIFF])
])

dnl === check for GIF support ===

AC_ARG_ENABLE([gif], AS_HELP_STRING([--disable-gif],
                                    [Disable detection of GIF format support
                                     @<:@default=auto@:>@]))
AS_IF([test "x$enable_gif" != "xno"], [
  CLEAR_LIBVARS([GIF])
  WITHLIB_OPTION([gif], [GIF])

  LIBCHECK_PROLOGUE([GIF])
  AC_CHECK_HEADER(gif_lib.h,
    AC_CHECK_LIB([gif], [DGifOpenFileHandle],
                 [GIF_LIBS="$GIF_LIBS -lgif"
                  AC_DEFINE(WEBP_HAVE_GIF, [1],
                            [Set to 1 if GIF library is installed])
                  gif_support=yes
                 ],
                 AC_MSG_WARN(Optional gif library not found),
                 [$MATH_LIBS]),
    AC_MSG_WARN(gif library not available - no gif_lib.h)
  )
  LIBCHECK_EPILOGUE([GIF])

  if test "$gif_support" = "yes" -a \
          "$enable_libwebpdemux" = "yes"; then
    build_anim_diff=yes
  else
    AC_MSG_NOTICE(
      [Not building anim_diff. libgif and --enable-libwebpdemux are required.])
  fi

  if test "$gif_support" = "yes" -a \
          "$enable_libwebpmux" = "yes"; then
    build_gif2webp=yes
  else
    AC_MSG_NOTICE(
      [Not building gif2webp. libgif and --enable-libwebpmux are required.])
  fi
])
AM_CONDITIONAL([BUILD_ANIMDIFF], [test "${build_anim_diff}" = "yes"])
AM_CONDITIONAL([BUILD_GIF2WEBP], [test "${build_gif2webp}" = "yes"])

if test "$enable_libwebpdemux" = "yes" -a "$enable_libwebpmux" = "yes"; then
  build_img2webp=yes
else
  AC_MSG_NOTICE(
    m4_normalize([Not building img2webp.
                  --enable-libwebpdemux & --enable-libwebpmux are required.]))
fi
AM_CONDITIONAL([BUILD_IMG2WEBP], [test "${build_img2webp}" = "yes"])

if test "$enable_libwebpmux" = "yes"; then
  build_webpinfo=yes
else
  AC_MSG_NOTICE([Not building webpinfo. --enable-libwebpdemux is required.])
fi
AM_CONDITIONAL([BUILD_WEBPINFO], [test "${build_webpinfo}" = "yes"])

dnl === check for WIC support ===

AC_ARG_ENABLE([wic],
              AS_HELP_STRING([--disable-wic],
                             [Disable Windows Imaging Component (WIC) detection.
                              @<:@default=auto@:>@]),,
              [enable_wic=yes])

case $host_os in
mingw*)
if test "$enable_wic" = "yes"; then
  AC_CHECK_HEADERS([wincodec.h shlwapi.h windows.h])
  if test "$ac_cv_header_wincodec_h" = "yes"; then
    AC_MSG_CHECKING(for Windows Imaging Component support)
    SAVED_LIBS=$LIBS
    LIBS="-lshlwapi -lole32 $LIBS"
    # match include structure from [cd]webp.c
    wic_headers="
      #define INITGUID
      #define CINTERFACE
      #define COBJMACROS
      #define _WIN32_IE 0x500

      #include <shlwapi.h>
      #include <windows.h>
      #include <wincodec.h>
      "
    # test for functions from each lib and the GUID is created properly
    wic_main="
      int main(void) {
        CLSID_WICImagingFactory;
        CoInitialize(NULL);
        SHCreateStreamOnFile(NULL, 0, NULL);
        return 0;
      }
      "
    AC_LANG_PUSH(C)
    AC_LINK_IFELSE(
      [AC_LANG_SOURCE([
         $wic_headers
         $wic_main])],
      [wic_support=yes],
      [wic_support=no]
    )
    AC_LANG_POP

    test "$wic_support" = "yes" || LIBS=$SAVED_LIBS
    AC_MSG_RESULT(${wic_support-no})
  fi
fi
esac

dnl === If --enable-swap-16bit-csp is defined, add -DWEBP_SWAP_16BIT_CSP=1

USE_SWAP_16BIT_CSP=""
AC_MSG_CHECKING(if --enable-swap-16bit-csp option is specified)
AC_ARG_ENABLE([swap-16bit-csp],
              AS_HELP_STRING([--enable-swap-16bit-csp],
                             [Enable byte swap for 16 bit colorspaces]))
if test "$enable_swap_16bit_csp" = "yes"; then
  USE_SWAP_16BIT_CSP="-DWEBP_SWAP_16BIT_CSP=1"
fi
AC_MSG_RESULT(${enable_swap_16bit_csp-no})
AC_SUBST(USE_SWAP_16BIT_CSP)

dnl === If --disable-near-lossless is defined, add -DWEBP_NEAR_LOSSLESS=0

AC_DEFINE(WEBP_NEAR_LOSSLESS, [1], [Enable near lossless encoding])
AC_MSG_CHECKING(if --disable-near-lossless option is specified)
AC_ARG_ENABLE([near_lossless],
              AS_HELP_STRING([--disable-near-lossless],
                             [Disable near lossless encoding]),
              [], [enable_near_lossless=yes])
if test "$enable_near_lossless" = "no"; then
  AC_DEFINE(WEBP_NEAR_LOSSLESS, [0], [Enable near lossless encoding])
  AC_MSG_RESULT([yes])
else
  AC_MSG_RESULT([no])
fi

dnl =========================

dnl Add an empty webp_libname_prefix variable for use in *.pc.in.
AC_SUBST([webp_libname_prefix])
AC_CONFIG_MACRO_DIR([m4])
AC_CONFIG_HEADERS([src/webp/config.h])
AC_CONFIG_FILES([Makefile src/Makefile man/Makefile \
                 examples/Makefile extras/Makefile imageio/Makefile \
                 sharpyuv/Makefile sharpyuv/libsharpyuv.pc \
                 src/dec/Makefile src/enc/Makefile src/dsp/Makefile \
                 src/demux/Makefile src/mux/Makefile \
                 src/utils/Makefile \
                 src/libwebp.pc src/libwebpdecoder.pc \
                 src/demux/libwebpdemux.pc src/mux/libwebpmux.pc])

dnl fix exports from MinGW builds
AC_CONFIG_COMMANDS_POST([$SED -i 's/-DDLL_EXPORT/-DWEBP_DLL/' config.status])
AC_OUTPUT

AC_MSG_NOTICE([
WebP Configuration Summary
--------------------------

Shared libraries: ${enable_shared}
Static libraries: ${enable_static}
Threading support: ${enable_threading-no}
libwebp: yes
libwebpdecoder: ${enable_libwebpdecoder-no}
libwebpdemux: ${enable_libwebpdemux-no}
libwebpmux: ${enable_libwebpmux-no}
libwebpextras: ${enable_libwebpextras-no}

Tools:
cwebp : ${enable_libwebpdemux-no}
  Input format support
  ====================
  JPEG : ${jpeg_support-no}
  PNG  : ${png_support-no}
  TIFF : ${tiff_support-no}
  WIC  : ${wic_support-no}
dwebp : ${enable_libwebpdemux-no}
  Output format support
  =====================
  PNG  : ${png_support-no}
  WIC  : ${wic_support-no}
GIF support : ${gif_support-no}
anim_diff   : ${build_anim_diff-no}
gif2webp    : ${build_gif2webp-no}
img2webp    : ${build_img2webp-no}
webpmux     : ${enable_libwebpmux-no}
vwebp       : ${build_vwebp-no}
webpinfo    : ${build_webpinfo-no}
SDL support : ${sdl_support-no}
vwebp_sdl   : ${build_vwebp_sdl-no}
])

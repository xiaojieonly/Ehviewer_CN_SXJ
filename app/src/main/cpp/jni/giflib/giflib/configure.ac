dnl Process this file with autoconf to produce a configure script.
AC_INIT(giflib, [5.1.4], [esr@thyrsus.com], giflib)
AC_CONFIG_MACRO_DIR([m4])
AC_CONFIG_SRCDIR([lib/dgif_lib.c])
AM_INIT_AUTOMAKE([gnu dist-bzip2 -Wall])
dnl Make builds less verbose.  Shuts off Makefile portability checks.
m4_ifdef([AM_SILENT_RULES], [AM_SILENT_RULES([yes])])

dnl Note: config.h is not used in the current build
dnl We leave this in place only to suppress an error message at autogen time
AM_CONFIG_HEADER(config.h)

dnl Checks for programs.
AM_PROG_AR
AC_PROG_LIBTOOL
AC_PROG_CC
AC_PROG_CPP
AC_PROG_INSTALL
AC_PROG_LN_S
AC_PROG_MAKE_SET
AM_PROG_CC_C_O

dnl Allow partial building on systems without xmlto
AC_CHECK_PROG([have_xmlto], [xmlto], ["yes"],["no"])
AM_CONDITIONAL([BUILD_DOC], [test "x${have_xmlto}" = "xyes"])

dnl Shared-library version
SHLIBVERSION=7:0:0
AC_SUBST(SHLIBVERSION)

dnl Define warnings if we're compiling with gcc.
dnl Set it in CFLAGS so the end-user can disable it if they wish.
if test "x$GCC" = "xyes" ; then
  CFLAGS="$CFLAGS -Wall"
fi

AC_CHECK_HEADERS(limits.h, ,
                 AC_MSG_ERROR([giflib needs limits.h to compile]))
AC_CHECK_HEADERS(fcntl.h, ,
		 AC_MSG_ERROR([giflib needs fcntl.h to compile]))
AC_CHECK_HEADERS(stdint.h, ,
		 AC_MSG_ERROR([giflib needs stdint.h to compile]))
AC_CHECK_HEADERS(stdarg.h, ,
		 AC_MSG_ERROR([giflib needs stdarg.h to compile]))

AC_CONFIG_FILES([
util/Makefile
lib/Makefile
Makefile
doc/Makefile
pic/Makefile
])
AC_OUTPUT

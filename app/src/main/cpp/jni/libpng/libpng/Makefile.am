# Makefile.am, the source file for Makefile.in (and hence Makefile), is
#
# Copyright (c) 2018 Cosmin Truta
# Copyright (c) 2004-2016 Glenn Randers-Pehrson
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

PNGLIB_BASENAME= libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@

ACLOCAL_AMFLAGS = -I scripts

# test programs - run on make check, make distcheck
check_PROGRAMS= pngtest pngunknown pngstest pngvalid pngimage pngcp
if HAVE_CLOCK_GETTIME
check_PROGRAMS += timepng
endif

# Utilities - installed
bin_PROGRAMS= pngfix png-fix-itxt

# This ensures that pnglibconf.h gets built at the start of 'make all' or
# 'make check', but it does not add dependencies to the individual programs,
# this is done below.
#
# IMPORTANT: always add the object modules of new programs to the list below
# because otherwise the sequence 'configure; make new-program' will *sometimes*
# result in the installed (system) pnglibconf.h being used and the result is
# always wrong and always very confusing.
BUILT_SOURCES = pnglibconf.h

pngtest_SOURCES = pngtest.c
pngtest_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

pngvalid_SOURCES = contrib/libtests/pngvalid.c
pngvalid_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

pngstest_SOURCES = contrib/libtests/pngstest.c
pngstest_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

pngunknown_SOURCES = contrib/libtests/pngunknown.c
pngunknown_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

pngimage_SOURCES = contrib/libtests/pngimage.c
pngimage_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

timepng_SOURCES = contrib/libtests/timepng.c
timepng_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

pngfix_SOURCES = contrib/tools/pngfix.c
pngfix_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

png_fix_itxt_SOURCES = contrib/tools/png-fix-itxt.c

pngcp_SOURCES = contrib/tools/pngcp.c
pngcp_LDADD = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la

# Generally these are single line shell scripts to run a test with a particular
# set of parameters:
TESTS =\
   tests/pngtest\
   tests/pngtest-badpngs\
   tests/pngvalid-gamma-16-to-8 tests/pngvalid-gamma-alpha-mode\
   tests/pngvalid-gamma-background tests/pngvalid-gamma-expand16-alpha-mode\
   tests/pngvalid-gamma-expand16-background\
   tests/pngvalid-gamma-expand16-transform tests/pngvalid-gamma-sbit\
   tests/pngvalid-gamma-threshold tests/pngvalid-gamma-transform\
   tests/pngvalid-progressive-size\
   tests/pngvalid-progressive-interlace-standard\
   tests/pngvalid-transform\
   tests/pngvalid-progressive-standard tests/pngvalid-standard\
   tests/pngstest-1.8 tests/pngstest-1.8-alpha tests/pngstest-linear\
   tests/pngstest-linear-alpha tests/pngstest-none tests/pngstest-none-alpha\
   tests/pngstest-sRGB tests/pngstest-sRGB-alpha tests/pngunknown-IDAT\
   tests/pngunknown-discard tests/pngunknown-if-safe tests/pngunknown-sAPI\
   tests/pngunknown-sTER tests/pngunknown-save tests/pngunknown-vpAg\
   tests/pngimage-quick tests/pngimage-full

# man pages
dist_man_MANS= libpng.3 libpngpf.3 png.5

# generate the -config scripts if required
binconfigs= libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@-config
EXTRA_SCRIPTS= libpng-config libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@-config
bin_SCRIPTS= @binconfigs@

# rules to build libpng, only build the old library on request
lib_LTLIBRARIES=libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.la
# EXTRA_LTLIBRARIES= libpng.la
libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES = png.c pngerror.c\
	pngget.c pngmem.c pngpread.c pngread.c pngrio.c pngrtran.c pngrutil.c\
	pngset.c pngtrans.c pngwio.c pngwrite.c pngwtran.c pngwutil.c\
	png.h pngconf.h pngdebug.h pnginfo.h pngpriv.h pngstruct.h pngusr.dfa

if PNG_ARM_NEON
libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES += arm/arm_init.c\
	arm/filter_neon.S arm/filter_neon_intrinsics.c \
	arm/palette_neon_intrinsics.c
endif

if PNG_MIPS_MSA
libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES += mips/mips_init.c\
	mips/filter_msa_intrinsics.c
endif

if PNG_INTEL_SSE
libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES += intel/intel_init.c\
	intel/filter_sse2_intrinsics.c
endif

if PNG_POWERPC_VSX
libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES += powerpc/powerpc_init.c\
        powerpc/filter_vsx_intrinsics.c
endif

nodist_libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_SOURCES = pnglibconf.h

libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_LDFLAGS = -no-undefined -export-dynamic \
	-version-number @PNGLIB_MAJOR@@PNGLIB_MINOR@:@PNGLIB_RELEASE@:0

if HAVE_LD_VERSION_SCRIPT
#   Versioned symbols and restricted exports
if HAVE_SOLARIS_LD
  libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_LDFLAGS += -Wl,-M -Wl,libpng.vers
else
  libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_LDFLAGS += -Wl,--version-script=libpng.vers
endif

  libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_DEPENDENCIES = libpng.vers
else
#   Only restricted exports when possible
  libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_LDFLAGS += -export-symbols libpng.sym
  libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_DEPENDENCIES = libpng.sym
endif

#distribute headers in /usr/include/libpng/*
pkgincludedir= $(includedir)/$(PNGLIB_BASENAME)
pkginclude_HEADERS= png.h pngconf.h
nodist_pkginclude_HEADERS= pnglibconf.h

# pkg-config stuff, note that libpng.pc is always required in order
# to get the correct library
pkgconfigdir = @pkgconfigdir@
pkgconfig_DATA = libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.pc

# Extra source distribution files, '${srcdir}' is used below to stop build files
# from those directories being included.  This only works if the configure is
# not done in the source directory!
EXTRA_DIST= \
	ANNOUNCE AUTHORS CHANGES INSTALL LICENSE README TODO TRADEMARK \
	pngtest.png pngbar.png pngnow.png pngbar.jpg autogen.sh \
	${srcdir}/contrib ${srcdir}/projects ${srcdir}/scripts \
	$(TESTS) $(XFAIL_TESTS) tests/pngstest \
	CMakeLists.txt example.c libpng-manual.txt

SCRIPT_CLEANFILES=scripts/*.out scripts/*.chk

CLEANFILES= *.tf? pngout.png libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@.pc \
	libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@-config libpng.vers libpng.sym \
	check.new pnglibconf.h pngprefix.h symbols.new pngtest-log.txt \
	pnglibconf.out pnglibconf.c pnglibconf.pre pnglibconf.dfn \
	$(SCRIPT_CLEANFILES)

MAINTAINERCLEANFILES = Makefile.in aclocal.m4 config.guess config.h.in \
config.sub configure depcomp install-sh ltmain.sh missing

# PNG_COPTS give extra options for the C compiler to be used on all compilation
# steps (unless targe_CFLAGS is specified; that will take precedence over
# AM_CFLAGS)
PNG_COPTS = @PNG_COPTS@
AM_CFLAGS = ${PNG_COPTS}

# DFNCPP is normally just CPP - the C preprocessor - but on Solaris and maybe
# other operating systems (NeXT?) the C preprocessor selected by configure
# checks input tokens for validity - effectively it performs part of the ANSI-C
# parsing - and therefore fails with the .df files.  configure.ac has special
# checks for this and sets DFNCPP appropriately.
DFNCPP = @DFNCPP@

SUFFIXES = .chk .out

$(PNGLIB_BASENAME).pc: libpng.pc
	cp libpng.pc $@

$(PNGLIB_BASENAME)-config: libpng-config
	cp libpng-config $@

scripts/sym.out scripts/vers.out: png.h pngconf.h pnglibconf.h
scripts/prefix.out: png.h pngconf.h pnglibconf.out
scripts/symbols.out: png.h pngconf.h $(srcdir)/scripts/pnglibconf.h.prebuilt
scripts/intprefix.out: pnglibconf.h

libpng.sym: scripts/sym.out
	rm -f $@
	cp $? $@
libpng.vers: scripts/vers.out
	rm -f $@
	cp $? $@

if DO_PNG_PREFIX
# Rename functions in scripts/prefix.out with a PNG_PREFIX prefix.
# Rename macros in scripts/macro.lst from PNG_PREFIXpng_ to PNG_ (the actual
# implementation of the macro).
pnglibconf.h: pnglibconf.out scripts/prefix.out scripts/macro.lst
	rm -f $@
	$(AWK) 's==0 && NR>1{print prev}\
	   s==0{prev=$$0}\
	   s==1{print "#define", $$1, "@PNG_PREFIX@" $$1}\
	   s==2{print "#define @PNG_PREFIX@png_" $$1, "PNG_" $$1}\
	   END{print prev}' s=0 pnglibconf.out s=1 scripts/prefix.out\
	   s=2 ${srcdir}/scripts/macro.lst >pnglibconf.tf8
	mv pnglibconf.tf8 $@

pngprefix.h: scripts/intprefix.out
	rm -f pngprefix.tf1
	$(AWK) '{print "#define", $$1, "@PNG_PREFIX@" $$1}' $? >pngprefix.tf1
	mv pngprefix.tf1 $@
else
pnglibconf.h: pnglibconf.out
	rm -f $@
	cp $? $@

pngprefix.h: # is empty
	:>$@
endif

$(srcdir)/scripts/pnglibconf.h.prebuilt:
	@echo "Attempting to build $@" >&2
	@echo "This is a machine generated file, but if you want to make" >&2
	@echo "a new one simply make 'scripts/pnglibconf.out', copy that" >&2
	@echo "AND set PNG_ZLIB_VERNUM to 0 (you MUST do this)" >&2
	@exit 1

# The following is necessary to ensure that the local pnglibconf.h is used, not
# an installed one (this can happen immediately after on a clean system if
# 'make test' is the first thing the user does.)  Only files which include
# one of the png source files (typically png.h or pngpriv.h) need to be listed
# here:
pngtest.o: pnglibconf.h

contrib/libtests/makepng.o: pnglibconf.h
contrib/libtests/pngstest.o: pnglibconf.h
contrib/libtests/pngunknown.o: pnglibconf.h
contrib/libtests/pngimage.o: pnglibconf.h
contrib/libtests/pngvalid.o: pnglibconf.h
contrib/libtests/readpng.o: pnglibconf.h
contrib/libtests/tarith.o: pnglibconf.h
contrib/libtests/timepng.o: pnglibconf.h

contrib/tools/makesRGB.o: pnglibconf.h
contrib/tools/pngfix.o: pnglibconf.h
contrib/tools/pngcp.o: pnglibconf.h

# We must use -DPNG_NO_USE_READ_MACROS here even when the library may actually
# be built with PNG_USE_READ_MACROS; this prevents the read macros from
# interfering with the symbol file format.
SYMBOL_CFLAGS = -DPNGLIB_LIBNAME='PNG@PNGLIB_MAJOR@@PNGLIB_MINOR@_0'\
		-DPNGLIB_VERSION='@PNGLIB_VERSION@'\
		-DSYMBOL_PREFIX='$(SYMBOL_PREFIX)'\
		-DPNG_NO_USE_READ_MACROS -DPNG_BUILDING_SYMBOL_TABLE

if DO_PNG_PREFIX
SYMBOL_CFLAGS += -DPNG_PREFIX='@PNG_PREFIX@'
endif

.c.out:
	rm -f $@ $*.tf[12]
	test -d scripts || mkdir scripts || test -d scripts
	$(DFNCPP) $(DEFS) $(DEFAULT_INCLUDES) $(INCLUDES)\
	    $(CPPFLAGS) $(SYMBOL_CFLAGS) $< > $*.tf1
	$(AWK) -f "${srcdir}/scripts/dfn.awk" out="$*.tf2" $*.tf1 1>&2
	rm -f $*.tf1
	mv $*.tf2 $@

# The .c file for pnglibconf.h is machine generated
pnglibconf.c: scripts/pnglibconf.dfa scripts/options.awk pngconf.h pngusr.dfa $(DFA_XTRA)
	rm -f $@ $*.tf[45]
	$(AWK) -f ${srcdir}/scripts/options.awk out=$*.tf4 version=search\
	    ${srcdir}/pngconf.h ${srcdir}/scripts/pnglibconf.dfa\
	    ${srcdir}/pngusr.dfa $(DFA_XTRA) 1>&2
	$(AWK) -f ${srcdir}/scripts/options.awk out=$*.tf5 $*.tf4 1>&2
	rm $*.tf4
	mv $*.tf5 $@

# Symbol checks (.def and .out files should match)
scripts/symbols.chk: scripts/checksym.awk scripts/symbols.def scripts/symbols.out

.out.chk:
	rm -f $@ $*.new
	$(AWK) -f ${srcdir}/scripts/checksym.awk ${srcdir}/scripts/${*F}.def\
	    of="$*.new" $< >&2
	mv $*.new $@

# used on demand to regenerate the standard header, CPPFLAGS should
# be empty - no non-standard defines
scripts/pnglibconf.c: scripts/pnglibconf.dfa scripts/options.awk pngconf.h
	rm -f $@ pnglibconf.tf[67]
	test -z "$(CPPFLAGS)"
	echo "com @PNGLIB_VERSION@ STANDARD API DEFINITION" |\
	$(AWK) -f ${srcdir}/scripts/options.awk out=pnglibconf.tf6\
	    logunsupported=1 version=search ${srcdir}/pngconf.h -\
	    ${srcdir}/scripts/pnglibconf.dfa 1>&2
	$(AWK) -f ${srcdir}/scripts/options.awk out=pnglibconf.tf7\
	    pnglibconf.tf6 1>&2
	rm pnglibconf.tf6
	mv pnglibconf.tf7 $@

$(libpng@PNGLIB_MAJOR@@PNGLIB_MINOR@_la_OBJECTS): png.h pngconf.h \
	pnglibconf.h pngpriv.h pngdebug.h pnginfo.h pngstruct.h pngprefix.h

test: check-am

# Extra checks
check: scripts/symbols.chk

# Don't distribute the generated script files
dist-hook:
	cd '$(top_distdir)'; rm -f $(SCRIPT_CLEANFILES)

# Make links between installed files with release-specific names and the generic
# file names.  If this install rule is run the generic names will be deleted and
# recreated - this has obvious issues for systems with multiple installations.

install-header-links:
	@set -ex; cd '$(DESTDIR)$(includedir)'; for f in $(HEADERS); do \
	   rm -f "$$f"; $(LN_S) "$(PNGLIB_BASENAME)/$$f" "$$f"; done

uninstall-header-links:
	cd '$(DESTDIR)$(includedir)'; rm -f $(HEADERS)

install-libpng-pc:
	@set -ex; cd '$(DESTDIR)$(pkgconfigdir)'; rm -f libpng.pc; \
	   $(LN_S) '$(PNGLIB_BASENAME).pc' libpng.pc

uninstall-libpng-pc:
	rm -f '$(DESTDIR)$(pkgconfigdir)/libpng.pc'

# EXT_LIST is a list of the possibly library directory extensions, this exists
# because we can't find a good way of discovering the file extensions that are
# actually installed on a given system, so instead we check for every extension
# we have seen.

EXT_LIST = a dll.a so so.@PNGLIB_MAJOR@@PNGLIB_MINOR@.@PNGLIB_RELEASE@ la sl dylib

install-library-links:
	@set -x; cd '$(DESTDIR)$(libdir)';\
	for ext in $(EXT_LIST); do\
	   rm -f "libpng.$$ext";\
           if test -f "$(PNGLIB_BASENAME).$$ext"; then\
              $(LN_S) "$(PNGLIB_BASENAME).$$ext" "libpng.$$ext" || exit 1;\
           fi;\
	done

uninstall-library-links:
	@set -x; cd '$(DESTDIR)$(libdir)'; for ext in $(EXT_LIST); do\
	   rm -f "libpng.$$ext"; done

install-libpng-config:
	@set -ex; cd '$(DESTDIR)$(bindir)'; rm -f libpng-config; \
	   $(LN_S) '$(PNGLIB_BASENAME)-config' libpng-config

uninstall-libpng-config:
	rm -f '$(DESTDIR)$(bindir)/libpng-config'

if DO_INSTALL_LINKS
# If --enable-unversioned-links is specified the header and lib file links
# will be automatically made on a 'make install':

install-data-hook: install-header-links
uninstall-hook: uninstall-header-links
install-exec-hook: install-library-links
uninstall-hook: uninstall-library-links
endif

if DO_INSTALL_LIBPNG_PC
# Likewise, --install-pc causes libpng.pc to be constructed:

install-data-hook: install-libpng-pc
uninstall-hook: uninstall-libpng-pc
endif

if DO_INSTALL_LIBPNG_CONFIG
# And --install-config:

install-exec-hook: install-libpng-config
uninstall-hook: uninstall-libpng-config
endif

# The following addition ensures that 'make all' always builds the test programs
# too.  It used to, but some change either in libpng or configure stopped this
# working.
all-am: $(check_PROGRAMS)

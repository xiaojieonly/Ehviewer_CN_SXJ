# This is an OpenWatcom make file which builds pnglibconf.h - the libpng
# configuration header.  You can ignore this file if you don't need to
# configure libpng; a default configuration will be built.
#
# For more information build libpng.wpj under the IDE and then read the
# generated files:
#
#    config.inf: Basic configuration information for a standard build.
#    pngconfig.dfa: Advanced configuration for non-standard libpng builds.
#
DELETE=rm -f
ECHO=echo
COPY=copy
#
# If your configuration needs to test compiler flags when building
# pnglibconf.h you may need to override the following on the wmake command
# line:
CFLAGS=
CC=wcl386
CPP=$(CC) -pw0
#
# Read awk from the environment if set, else it can be set on the command
# line (the default approach is to set the %awk% environment variable in the
# IDE libpng.wpj 'before' rule - this setting is local.)
!ifdef %awk
AWK=$(%awk)
!endif
#
# pnglibconf.h must exist in the source directory, this is the final rule
# which copies the local built version (and this is the default target for
# this makefile.)
..\..\pnglibconf.h: pnglibconf.h
 $(COPY) pnglibconf.h $@

!ifdef AWK
# CPPFLAGS should contain the options to control the result,
# but DEFS and CFLAGS are also supported here, override
# as appropriate
DFNFLAGS = $(DEFS) $(CPPFLAGS) $(CFLAGS)

pnglibconf.h: pnglibconf.dfn
 $(DELETE) $@ dfn.c dfn1.out dfn2.out
 $(ECHO) $#include "pnglibconf.dfn" >dfn.c
 $(CPP) $(DFNFLAGS) dfn.c >dfn1.out
 $(AWK) -f << dfn1.out >dfn2.out
/^.*PNG_DEFN_MAGIC-.*-PNG_DEFN_END.*$$/{
 sub(/^.*PNG_DEFN_MAGIC-/, "")
 sub(/ *-PNG_DEFN_END.*$$/, "")
 gsub(/ *@@@ */, "")
 print
}
<<
 $(COPY) dfn2.out $@
 @type << >pngconfig.inf
This is a locally configurable build of libpng.lib; for configuration
instructions consult and edit projects/openwatcom/pngconfig.dfa
<<
 $(DELETE) dfn.c dfn1.out dfn2.out

pnglibconf.dfn: ..\..\scripts\pnglibconf.dfa ..\..\scripts\options.awk pngconfig.dfa ..\..\pngconf.h
 $(DELETE) $@ dfn1.out dfn2.out
 $(AWK) -f ..\..\scripts\options.awk out=dfn1.out version=search ..\..\pngconf.h ..\..\scripts\pnglibconf.dfa pngconfig.dfa $(DFA_XTRA) 1>&2
 $(AWK) -f ..\..\scripts\options.awk out=dfn2.out dfn1.out 1>&2
 $(COPY) dfn2.out $@
 $(DELETE) dfn1.out dfn2.out

!else
# The following lines are used to copy scripts\pnglibconf.h.prebuilt and make
# the required change to the calling convention.
#
# By default libpng is built to use the __cdecl calling convention on
# Windows.  This gives compatibility with MSVC and GCC.  Unfortunately it
# does not work with OpenWatcom because OpenWatcom implements longjmp using
# the __watcall convention (compared with both MSVC and GCC which use __cdecl
# for library functions.)
#
# Thus the default must be changed to build on OpenWatcom and, once changed,
# the result will not be compatible with applications built using other
# compilers (in fact attempts to build will fail at compile time.)
#
pnglibconf.h: ..\..\scripts\pnglibconf.h.prebuilt .existsonly
 @$(ECHO) .
 @$(ECHO) .
 @$(ECHO) $$(AWK) NOT AVAILABLE: COPYING scripts\pnglibconf.h.prebuilt
 @$(ECHO) .
 @$(ECHO) .
 vi -q -k ":1,$$s/PNG_API_RULE 0$$/PNG_API_RULE 2/\n:w! $@\n:q!\n" ..\..\scripts\pnglibconf.h.prebuilt
 @$(ECHO) .
 @$(ECHO) .
 @$(ECHO) YOU HAVE A DEFAULT CONFIGURATION BECAUSE YOU DO NOT HAVE AWK!
 @$(ECHO) .
 @$(ECHO) .
 @type << >pngconfig.inf
This is the default configuration of libpng.lib, if you wish to
change the configuration please consult the instructions in
projects/owatcom/pngconfig.dfa.
<<

!endif

# Make the default files
defaults: .symbolic
 @$(COPY) << config.inf
$# The libpng project is incompletely configured.  To complete configuration
$# please complete the following steps:
$#
$#   1) Edit the 'before' rule of libpng.wpj (from the IDE) to define the
$#      locations of the zlib include file zlib.h and the built zlib library,
$#      zlib.lib.
$#
$#   2) If you want to change libpng to a non-standard configuration also
$#      change the definition of 'awk' in the before rule to the name of your
$#      awk command.  For more instructions on configuration read
$#      pngconfig.dfa.
$#
$#   3) Delete this file (config.inf).
<<
 @$(COPY) << pngconfig.dfa
$# pngconfig.dfa: this file contains configuration options for libpng.
$# If empty the standard configuration will be built.  For this file to be
$# used a working version of the program 'awk' is required and the program
$# must be identified in the 'before' rule of the project.
$#
$# If you don't already have 'awk', or the version of awk you have seems not
$# to work, download Brian Kernighan's awk (Brian Kernighan is the author of
$# awk.)  You can find source code and a built executable (called awk95.exe)
$# here:
$#
$#     https://www.cs.princeton.edu/~bwk/btl.mirror/
$#
$# The executable works just fine.
$#
$# If build issues errors after a change to pngconfig.dfa you have entered
$# inconsistent feature requests, or even malformed requests, in
$# pngconfig.dfa.  The error messages from awk should be comprehensible, but
$# if not simply go back to the start (nothing but comments in this file) and
$# enter configuration lines one by one until one produces an error.  (Or, of
$# course, do the standard binary chop.)
$#
$# You need to rebuild everything after a change to pnglibconf.dfa - i.e. you
$# must do Actions/Mark All Targets for Remake.  This is because the compiler
$# generated dependency information (as of OpenWatcom 1.9) does not record the
$# dependency on pnglibconf.h correctly.
$#
$# If awk isn't set then this file is bypassed.  If you just want the standard
$# configuration it is automatically produced from the distributed version
$# (scripts\pnglibconf.h.prebuilt) by editing PNG_API_RULE to 2 (to force use
$# of the OpenWatcom library calling convention.)
$#
<<

clean:: .symbolic
 $(DELETE) ..\..\pnglibconf.h pnglibconf.* dfn.c *.out pngconfig.inf
 $(DELETE) *.obj *.mbr *.sym *.err *.pch libpng.mk
 $(DELETE) libpng.lib libpng.lbr libpng.lb1 libpng.cbr libpng.mk1
 $(DELETE) pngtest.exe pngtest.map pngtest.lk1 pngtest.mk1
 $(DELETE) pngvalid.exe pngvalid.map pngvalid.lk1 pngvalid.mk1

distclean:: clean .symbolic
 $(DELETE) zlib.inf awk.inf config.inf pngconfig.dfa

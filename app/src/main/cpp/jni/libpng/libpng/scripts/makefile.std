# makefile for libpng
# Copyright (C) 2002, 2006, 2014 Glenn Randers-Pehrson
# Copyright (C) 1995 Guy Eric Schalnat, Group 42, Inc.
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

# where make install puts libpng.a and png.h
prefix=/usr/local
INCPATH=$(prefix)/include
LIBPATH=$(prefix)/lib

# override DESTDIR= on the make install command line to easily support
# installing into a temporary location.  Example:
#
#    make install DESTDIR=/tmp/build/libpng
#
# If you're going to install into a temporary location
# via DESTDIR, $(DESTDIR)$(prefix) must already exist before
# you execute make install.
DESTDIR=

# Where the zlib library and include files are located
#ZLIBLIB=/usr/local/lib
#ZLIBINC=/usr/local/include
ZLIBLIB=../zlib
ZLIBINC=../zlib

CC = cc
AR_RC = ar rc
MKDIR_P = mkdir
LN_SF = ln -sf
RANLIB = ranlib
CP = cp
RM_F = rm -f
AWK = awk
SED = sed
CPP = $(CC) -E
ECHO = echo

DFNFLAGS = # DFNFLAGS contains -D options to use in the libpng build
DFA_EXTRA = # extra files that can be used to control configuration
CPPFLAGS = -I$(ZLIBINC) # -DPNG_DEBUG=5
CFLAGS = -O # -g
LDFLAGS = -L. -L$(ZLIBLIB) -lpng -lz -lm

# Pre-built configuration
# See scripts/pnglibconf.mak for more options
PNGLIBCONF_H_PREBUILT = scripts/pnglibconf.h.prebuilt

OBJS = png.o pngset.o pngget.o pngrutil.o pngtrans.o pngwutil.o \
	pngread.o pngrio.o pngwio.o pngwrite.o pngrtran.o \
	pngwtran.o pngmem.o pngerror.o pngpread.o

.c.o:
	$(CC) -c $(CPPFLAGS) $(CFLAGS) -o $@ $<

all: libpng.a pngtest

# The standard pnglibconf.h exists as scripts/pnglibconf.h.prebuilt,
# copy this if the following doesn't work.
pnglibconf.h: pnglibconf.dfn
	$(RM_F) $@ pnglibconf.c pnglibconf.out pnglibconf.tmp
	$(ECHO) '#include "pnglibconf.dfn"' >pnglibconf.c
	$(ECHO) "If '$(CC) -E' crashes try /lib/cpp (e.g. CPP='/lib/cpp')" >&2
	$(CPP) $(DFNFLAGS) pnglibconf.c >pnglibconf.out
	$(AWK) -f "scripts/dfn.awk" out="pnglibconf.tmp" pnglibconf.out 1>&2
	mv pnglibconf.tmp $@

pnglibconf.dfn: scripts/pnglibconf.dfa scripts/options.awk pngconf.h pngusr.dfa $(DFA_XTRA)
	$(RM_F) $@ pnglibconf.pre pnglibconf.tmp
	$(ECHO) "Calling $(AWK) from scripts/pnglibconf.mak" >&2
	$(ECHO) "If 'awk' crashes try a better awk (e.g. AWK='nawk')" >&2
	$(AWK) -f scripts/options.awk out="pnglibconf.pre"\
	    version=search pngconf.h scripts/pnglibconf.dfa\
	    pngusr.dfa $(DFA_XTRA) 1>&2
	$(AWK) -f scripts/options.awk out="pnglibconf.tmp" pnglibconf.pre 1>&2
	mv pnglibconf.tmp $@

libpng.a: $(OBJS)
	$(AR_RC) $@  $(OBJS)
	$(RANLIB) $@

pngtest: pngtest.o libpng.a
	$(CC) -o pngtest $(CFLAGS) pngtest.o $(LDFLAGS)

test: pngtest
	./pngtest

install: libpng.a pnglibconf.h
	-@$(MKDIR_P) $(DESTDIR)$(INCPATH)
	-@$(MKDIR_P) $(DESTDIR)$(INCPATH)/libpng
	-@$(MKDIR_P) $(DESTDIR)$(LIBPATH)
	-@$(RM_F) $(DESTDIR)$(INCPATH)/png.h
	-@$(RM_F) $(DESTDIR)$(INCPATH)/pngconf.h
	-@$(RM_F) $(DESTDIR)$(INCPATH)/pnglibconf.h
	cp png.h $(DESTDIR)$(INCPATH)/libpng
	cp pngconf.h $(DESTDIR)$(INCPATH)/libpng
	cp pnglibconf.h $(DESTDIR)$(INCPATH)/libpng
	chmod 644 $(DESTDIR)$(INCPATH)/libpng/png.h
	chmod 644 $(DESTDIR)$(INCPATH)/libpng/pngconf.h
	chmod 644 $(DESTDIR)$(INCPATH)/libpng/pnglibconf.h
	(cd $(DESTDIR)$(INCPATH); ln -f -s libpng/* .)
	cp libpng.a $(DESTDIR)$(LIBPATH)
	chmod 644 $(DESTDIR)$(LIBPATH)/libpng.a

clean:
	$(RM_F) *.o libpng.a pngtest pngout.png pnglibconf.h pnglibconf.c \
	pnglibconf.out

DOCS = ANNOUNCE CHANGES INSTALL KNOWNBUG LICENSE README TODO
writelock:
	chmod a-w *.[ch35] $(DOCS) scripts/*

# DO NOT DELETE THIS LINE -- make depend depends on it.

png.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngerror.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrio.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwio.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngmem.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngset.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngget.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngpread.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngread.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrtran.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrutil.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngtrans.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwrite.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwtran.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwutil.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h

pngtest.o: png.h pngconf.h pnglibconf.h

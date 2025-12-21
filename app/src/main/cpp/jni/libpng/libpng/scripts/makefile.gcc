# makefile for libpng using gcc (generic, static library)
# Copyright (C) 2008, 2014 Glenn Randers-Pehrson
# Copyright (C) 2000 Cosmin Truta
# Copyright (C) 1995 Guy Eric Schalnat, Group 42, Inc.
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

# Location of the zlib library and include files
ZLIBINC = ../zlib
ZLIBLIB = ../zlib

# Compiler, linker, lib and other tools
CC = gcc
LD = $(CC)
AR_RC = ar rcs
RANLIB = ranlib
CP = cp
RM_F = rm -f

WARNMORE = -Wwrite-strings -Wpointer-arith -Wshadow \
	-Wmissing-declarations -Wtraditional -Wcast-align \
	-Wstrict-prototypes -Wmissing-prototypes # -Wconversion
CPPFLAGS = -I$(ZLIBINC) # -DPNG_DEBUG=5
CFLAGS = -W -Wall -O2 # $(WARNMORE) -g
LDFLAGS =
LIBS = -lz -lm

# File extensions
EXEEXT =

# Pre-built configuration
# See scripts/pnglibconf.mak for more options
PNGLIBCONF_H_PREBUILT = scripts/pnglibconf.h.prebuilt

# Variables
OBJS =  png.o pngerror.o pngget.o pngmem.o pngpread.o \
	pngread.o pngrio.o pngrtran.o pngrutil.o pngset.o \
	pngtrans.o pngwio.o pngwrite.o pngwtran.o pngwutil.o

# Targets
all: static

pnglibconf.h: $(PNGLIBCONF_H_PREBUILT)
	$(CP) $(PNGLIBCONF_H_PREBUILT) $@

.c.o:
	$(CC) -c $(CPPFLAGS) $(CFLAGS) -o $@ $<

static: libpng.a pngtest$(EXEEXT)

shared:
	@echo This is a generic makefile that cannot create shared libraries.
	@echo Please use a configuration that is specific to your platform.
	@false

libpng.a: $(OBJS)
	$(AR_RC) $@ $(OBJS)
	$(RANLIB) $@

test: pngtest$(EXEEXT)
	./pngtest$(EXEEXT)

pngtest$(EXEEXT): pngtest.o libpng.a
	$(LD) $(LDFLAGS) -L$(ZLIBLIB) -o $@ pngtest.o libpng.a $(LIBS)

clean:
	$(RM_F) *.o libpng.a pngtest$(EXEEXT) pngout.png pnglibconf.h

png.o:      png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngerror.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngget.o:   png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngmem.o:   png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngpread.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngread.o:  png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrio.o:   png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrtran.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngrutil.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngset.o:   png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngtrans.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwio.o:   png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwrite.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwtran.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h
pngwutil.o: png.h pngconf.h pnglibconf.h pngpriv.h pngstruct.h pnginfo.h pngdebug.h

pngtest.o:  png.h pngconf.h pnglibconf.h

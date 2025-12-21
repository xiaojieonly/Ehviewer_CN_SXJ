# Makefile for PngMinus (pngm2pnm)
# Linux / Unix

#CC=cc
CC=gcc
LD=$(CC)

# If awk fails try
# make AWK=nawk

# If cpp fails try
# make CPP=/lib/cpp

RM=rm -f
COPY=cp

CPPFLAGS=-I. -DPNG_USER_CONFIG -DNO_GZCOMPRESS -DZ_SOLO -DNO_GZIP
CFLAGS=-O1 -Wall

C=.c
O=.o
L=.a
E=

# Where to find the source code:
PNGSRC =../../..
ZLIBSRC=$(PNGSRC)/../zlib
PROGSRC=$(PNGSRC)/contrib/pngminus

# Zlib (minimal inflate requirements - crc32 is used by libpng)
# zutil can be eliminated if you provide your own zcalloc and zcfree
ZSRCS  = adler32$(C) crc32$(C) \
	 inffast$(C) inflate$(C) inftrees$(C) \
	 zutil$(C)

# Standard headers
ZH     = zlib.h crc32.h inffast.h inffixed.h \
	 inflate.h inftrees.h zutil.h

# Machine generated headers
ZCONF  = zconf.h

# Headers callers use
ZINC   = zlib.h $(ZCONF)

# Headers the Zlib source uses
ZHDRS  = $(ZH) $(ZCONF)

ZOBJS  = adler32$(O) crc32$(O) \
	 inffast$(O) inflate$(O) inftrees$(O) \
	 zutil$(O)

# libpng
PNGSRCS=png$(C) pngerror$(C) pngget$(C) pngmem$(C) \
	pngread$(C) pngrio$(C) pngrtran$(C) pngrutil$(C) \
	pngset$(C) pngtrans$(C)
	
# Standard headers
PNGH   =png.h pngconf.h pngdebug.h pnginfo.h pngpriv.h pngstruct.h

# Machine generated headers
PNGCONF=pnglibconf.h

# Headers callers use
PNGINC= png.h pngconf.h pngusr.h $(PNGCONF)

# Headers the PNG library uses
PNGHDRS=$(PNGH) $(PNGCONF) pngusr.h

PNGOBJS=png$(O) pngerror$(O) pngget$(O) pngmem$(O) \
	pngread$(O) pngrio$(O) pngrtran$(O) pngrutil$(O) \
	pngset$(O) pngtrans$(O)

PROGSRCS= pngm2pnm$(C)
PROGHDRS=
PROGDOCS=
PROGOBJS= pngm2pnm$(O)

OBJS    = $(PROGOBJS) $(PNGOBJS) $(ZOBJS)

# implicit make rules -------------------------------------------------------

# note: dependencies do not work on implicit rule lines
.c$(O):
	$(CC) -c $(CPPFLAGS) $(CFLAGS) $<

# dependencies

all: pngm2pnm$(E)

pngm2pnm$(E): $(OBJS)
	$(LD) -o pngm2pnm$(E) $(OBJS)

# The DFA_XTRA setting turns all libpng options off then
# turns on those required for this minimal build.
# The CPP_FLAGS setting causes pngusr.h to be included in
# both the build of pnglibconf.h and, subsequently, when
# building libpng itself.
$(PNGCONF): $(PNGSRC)/scripts/pnglibconf.mak $(ZH)\
	$(PNGSRC)/scripts/pnglibconf.dfa \
	$(PNGSRC)/scripts/options.awk pngusr.h pngusr.dfa
	$(RM) pnglibconf.h pnglibconf.dfn
	$(MAKE) -f $(PNGSRC)/scripts/pnglibconf.mak $(MAKEFLAGS)\
	    srcdir=$(PNGSRC) CPPFLAGS="-DPNG_USER_CONFIG -I."\
	    DFA_XTRA="pngusr.dfa" $@

clean:
	$(MAKE) -f $(PNGSRC)/scripts/pnglibconf.mak $(MAKEFLAGS)\
	    srcdir=$(PNGSRC) clean
	$(RM) pngm2pnm$(O)
	$(RM) pngm2pnm$(E)
	$(RM) $(OBJS)

# distclean also removes the copied source and headers
distclean: clean
	$(RM) -r scripts # historical reasons
	$(RM) $(PNGSRCS) $(PNGH)
	$(RM) $(ZSRCS) $(ZH) $(ZCONF)
	$(RM) $(PROGSRCS) $(PROGHDRS) $(PROGDOCS)

# Header file dependencies:
$(PROGOBJS): $(PROGHDRS) $(PNGINC) $(ZINC)
$(PNGOBJS): $(PNGHDRS) $(ZINC)
$(ZOBJS): $(ZHDRS)

# Gather the source code from the respective directories
$(PNGSRCS) $(PNGH): $(PNGSRC)/$@
	$(RM) $@
	$(COPY) $(PNGSRC)/$@ $@

# No dependency on the ZLIBSRC target so that it only needs
# to be specified once.
$(ZSRCS) $(ZH):
	$(RM) $@
	$(COPY) $(ZLIBSRC)/$@ $@

# The unconfigured zconf.h varies in name according to the
# zlib release
$(ZCONF):
	$(RM) $@
	@for f in zconf.h.in zconf.in.h zconf.h; do\
	    test -r $(ZLIBSRC)/$$f &&\
	    echo $(COPY) $(ZLIBSRC)/$$f $@ &&\
	    $(COPY) $(ZLIBSRC)/$$f $@ && exit 0;\
	done; echo copy: $(ZLIBSRC)/zconf.h not found; exit 1

pngm2pnm.c: $(PROGSRC)/png2pnm.c
	$(RM) $@
	$(COPY) $(PROGSRC)/png2pnm.c $@

# End of makefile for pngm2pnm

# GIFLIB utilities makefile

# The no-install programs are test pattern generators and example code.
noinst_PROGRAMS = gifbg gifcolor giffilter gifhisto gifsponge gifwedge

noinst_LIBRARIES = libgetarg.a

bin_PROGRAMS = \
	gif2rgb \
	gifbuild \
	gifecho \
	giffix \
	gifinto \
	giftext \
	giftool \
	gifclrmp

EXTRA_DIST=Makefile.unx $(bin_SCRIPTS)

LDADD = libgetarg.a ../lib/libgif.la

AM_CPPFLAGS = -I$(srcdir) -I$(top_srcdir)/lib

libgetarg_a_SOURCES = getarg.c getarg.h \
                    qprintf.c

gifclrmp_SOURCES = gifclrmp.c
gifclrmp_LDADD = $(LDADD) -lm

gif2rgb_SOURCES = gif2rgb.c
gifbg_SOURCES = gifbg.c
gifbuild_SOURCES = gifbuild.c
gifcolor_SOURCES = gifcolor.c
gifecho_SOURCES = gifecho.c
giffilter_SOURCES = giffilter.c
giffix_SOURCES = giffix.c
gifhisto_SOURCES = gifhisto.c
gifinto_SOURCES = gifinto.c
gifsponge_SOURCES = gifsponge.c
giftext_SOURCES = giftext.c
giftool_SOURCES = giftool.c
gifwedge_SOURCES = gifwedge.c

AM_CPPFLAGS += -I$(top_builddir) -I$(top_srcdir)
AM_CPPFLAGS += -I$(top_builddir)/src -I$(top_srcdir)/src
noinst_LTLIBRARIES = libwebpextras.la

noinst_HEADERS =
noinst_HEADERS += ../src/webp/types.h

libwebpextras_la_SOURCES =
libwebpextras_la_SOURCES += extras.c extras.h quality_estimate.c
libwebpextras_la_SOURCES += sharpyuv_risk_table.c sharpyuv_risk_table.h

libwebpextras_la_CPPFLAGS = $(AM_CPPFLAGS)
libwebpextras_la_LDFLAGS = -lm
libwebpextras_la_LIBADD = ../src/libwebp.la

noinst_PROGRAMS =
noinst_PROGRAMS += webp_quality
if BUILD_DEMUX
  noinst_PROGRAMS += get_disto
endif
if BUILD_VWEBP_SDL
  noinst_PROGRAMS += vwebp_sdl
endif

get_disto_SOURCES  = get_disto.c
get_disto_CPPFLAGS = $(AM_CPPFLAGS)
get_disto_LDADD =
get_disto_LDADD += ../imageio/libimageio_util.la
get_disto_LDADD += ../imageio/libimagedec.la
get_disto_LDADD += ../src/libwebp.la
get_disto_LDADD += $(PNG_LIBS) $(JPEG_LIBS) $(TIFF_LIBS)

webp_quality_SOURCES  = webp_quality.c
webp_quality_CPPFLAGS = $(AM_CPPFLAGS)
webp_quality_LDADD =
webp_quality_LDADD += ../imageio/libimageio_util.la
webp_quality_LDADD += libwebpextras.la
webp_quality_LDADD += ../src/libwebp.la

vwebp_sdl_SOURCES  = vwebp_sdl.c webp_to_sdl.c webp_to_sdl.h
vwebp_sdl_CPPFLAGS = $(AM_CPPFLAGS) $(SDL_INCLUDES)
vwebp_sdl_LDADD =
vwebp_sdl_LDADD += ../imageio/libimageio_util.la
vwebp_sdl_LDADD += ../src/libwebp.la
vwebp_sdl_LDADD += $(SDL_LIBS)

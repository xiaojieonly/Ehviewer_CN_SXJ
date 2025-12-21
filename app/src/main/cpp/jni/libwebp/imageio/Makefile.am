AM_CPPFLAGS += -I$(top_builddir)/src -I$(top_srcdir)/src
noinst_LTLIBRARIES =
noinst_LTLIBRARIES += libimageio_util.la
if BUILD_DEMUX
  noinst_LTLIBRARIES += libimagedec.la
endif
noinst_LTLIBRARIES += libimageenc.la

noinst_HEADERS =
noinst_HEADERS += ../src/webp/decode.h
noinst_HEADERS += ../src/webp/types.h

libimageio_util_la_SOURCES =
libimageio_util_la_SOURCES += imageio_util.c imageio_util.h

libimagedec_la_SOURCES  =
libimagedec_la_SOURCES += image_dec.c image_dec.h
libimagedec_la_SOURCES += jpegdec.c jpegdec.h
libimagedec_la_SOURCES += metadata.c metadata.h
libimagedec_la_SOURCES += pngdec.c pngdec.h
libimagedec_la_SOURCES += pnmdec.c pnmdec.h
libimagedec_la_SOURCES += tiffdec.c tiffdec.h
libimagedec_la_SOURCES += webpdec.c webpdec.h
libimagedec_la_SOURCES += wicdec.c wicdec.h
libimagedec_la_CPPFLAGS = $(JPEG_INCLUDES) $(PNG_INCLUDES) $(TIFF_INCLUDES)
libimagedec_la_CPPFLAGS += $(AM_CPPFLAGS)
libimagedec_la_LIBADD = ../src/demux/libwebpdemux.la

libimageenc_la_SOURCES  =
libimageenc_la_SOURCES += image_enc.c image_enc.h
libimageenc_la_CPPFLAGS = $(JPEG_INCLUDES) $(PNG_INCLUDES) $(TIFF_INCLUDES)
libimageenc_la_CPPFLAGS += $(AM_CPPFLAGS)

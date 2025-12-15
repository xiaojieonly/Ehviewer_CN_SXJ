#############################################################################
# Makefile for building giflib with Visual C++ on Windows
#############################################################################

####### Compiler, tools and options

CC      = cl
CFLAGS  = -nologo -Zm200 -W3 -MD -O1 -GX -DHAVE_STDARG_H -D_OPEN_BINARY -DHAVE_IO_H -DHAVE_FCNTL_H -Fdgiflib.pdb
INCPATH = -I"." 
LIB     = lib /NOLOGO
DEL_FILE= del

####### Files

HEADERS = getarg.h \
		gif_hash.h \
		gif_lib.h \
		gif_lib_private.h
SOURCES = dgif_lib.c \
		egif_lib.c \
		getarg.c \
		gif_err.c \
		gif_font.c \
		gif_hash.c \
		gifalloc.c \
		qprintf.c \
		quantize.c \
		openbsd-reallocarray

OBJECTS = dgif_lib.obj \
		egif_lib.obj \
		getarg.obj \
		gif_err.obj \
		gif_font.obj \
		gif_hash.obj \
		gifalloc.obj \
		qprintf.obj \
		quantize.obj \
		openbsd-reallocarray.obj

TARGET  = giflib.lib

.SUFFIXES: .c 

{.}.c{}.obj::
	$(CC) -c $(CFLAGS) $(INCPATH) -Fo @<<
	$<
<<

all: $(TARGET)

$(TARGET):   $(OBJECTS) 
	$(LIB) /OUT:$(TARGET) @<<
	  $(OBJECTS) 
<<

clean: 	
	-$(DEL_FILE) $(TARGET)
	-$(DEL_FILE) dgif_lib.obj
	-$(DEL_FILE) egif_lib.obj
	-$(DEL_FILE) getarg.obj
	-$(DEL_FILE) gif_err.obj
	-$(DEL_FILE) gif_font.obj
	-$(DEL_FILE) gif_hash.obj
	-$(DEL_FILE) gifalloc.obj
	-$(DEL_FILE) qprintf.obj
	-$(DEL_FILE) quantize.obj

dgif_lib.obj: dgif_lib.c gif_lib.h \
		gif_lib_private.h \
		gif_hash.h

egif_lib.obj: egif_lib.c gif_lib.h \
		gif_lib_private.h \
		gif_hash.h

getarg.obj: getarg.c getarg.h

gif_err.obj: gif_err.c gif_lib.h

gif_font.obj: gif_font.c gif_lib.h

gif_hash.obj: gif_hash.c gif_lib.h \
		gif_hash.h \
		gif_lib_private.h

gifalloc.obj: gifalloc.c gif_lib.h

qprintf.obj: qprintf.c gif_lib.h

quantize.obj: quantize.c gif_lib.h \
		gif_lib_private.h \
		gif_hash.h

openbsd-reallocarray.obj: openbsd-reallocarray.c
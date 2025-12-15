# Prevent "Cannot find missing dependency..." warnings while compiling
# pngwin.rc (PRJ0041).

all: $(IntDir)\alloc.h \
	 $(IntDir)\fp.h \
	 $(IntDir)\m68881.h \
	 $(IntDir)\mem.h \
	 $(IntDir)\pngusr.h \
	 $(IntDir)\strings.h \
	 $(IntDir)\unistd.h \
	 $(IntDir)\unixio.h

$(IntDir)\alloc.h \
$(IntDir)\fp.h \
$(IntDir)\m68881.h \
$(IntDir)\mem.h \
$(IntDir)\pngusr.h \
$(IntDir)\strings.h \
$(IntDir)\unistd.h \
$(IntDir)\unixio.h:
	@!echo.>$@

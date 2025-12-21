
cc_defs = /inc=$(ZLIBSRC)
c_deb =

.ifdef __DECC__
pref = /prefix=all
.endif



OBJS = png.obj, pngset.obj, pngget.obj, pngrutil.obj, pngtrans.obj,\
	pngwutil.obj, pngread.obj, pngmem.obj, pngwrite.obj, pngrtran.obj,\
	pngwtran.obj, pngrio.obj, pngwio.obj, pngerror.obj, pngpread.obj


CFLAGS= $(C_DEB) $(CC_DEFS) $(PREF)

all : pngtest.exe libpng.olb
	@ write sys$output " pngtest available"

libpng.olb : libpng.olb($(OBJS))
	@ write sys$output " libpng available"


pngtest.exe : pngtest.obj libpng.olb
              link pngtest,libpng.olb/lib,$(ZLIBSRC)libz.olb/lib

test : pngtest.exe
   run pngtest

clean :
	delete *.obj;*,*.exe;


# Other dependencies.
png.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngpread.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngset.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngget.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngread.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngrtran.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngrutil.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngerror.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngmem.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngrio.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngwio.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngtrans.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngwrite.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngwtran.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h
pngwutil.obj : png.h, pngconf.h, pnglibconf.h, pngpriv.h, pngstruct.h,pnginfo.h, pngdebug.h

pngtest.obj : png.h, pngconf.h, pnglibconf.h

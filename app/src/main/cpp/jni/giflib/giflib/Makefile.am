SUBDIRS = lib util pic

# Pacify autoreconf.  We don't actually use this.
ACLOCAL_AMFLAGS = -I m4

# Avoid trouble if your system doesn't have xmlto.
if BUILD_DOC
SUBDIRS += doc
endif

EXTRA_DIST = Makefile.unx \
	     autogen.sh \
	     test.sh \
	     tests \
	     TODO \
	     BUGS \
	     build.asc \
	     history.asc \
	     control \
	     doc/whatsinagif \
	     $(WINDOWS)

TESTS=test.sh

# Auditing tools.

# cppcheck should run clean
cppcheck:
	cppcheck -Ilib --inline-suppr --template gcc --enable=all --suppress=unusedStructMember --suppress=unusedFunction --force lib/*.[ch] util/*.[ch]

# splint is nowhere near running clean
SPLINT_SUPPRESSIONS = -boolops -nullret
splint: 
	splint +quiet +posixlib $(SPLINT_SUPPRESSIONS) lib/*.[ch]

# release using the shipper tool, if distcheck passes
release: distcheck
	cd doc; make website
	shipper version=@VERSION@ | sh -e -x
	rm -fr doc/staging

# Refresh the wbsite
refresh:
	cd doc; make website
	shipper -w version=@VERSION@ | sh -e -x
	rm -fr doc/staging

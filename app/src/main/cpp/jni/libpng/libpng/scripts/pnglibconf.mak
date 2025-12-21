#!/usr/bin/make -f
# pnglibconf.mak - standard make lines for pnglibconf.h
#
# These lines are copied from Makefile.am, they illustrate
# how to automate the build of pnglibconf.h from scripts/pnglibconf.dfa
# given just 'awk', a C preprocessor and standard command line utilities

# Override as appropriate, these definitions can be overridden on
# the make command line (AWK='nawk' for example).
AWK = gawk
AWK = mawk
AWK = nawk
AWK = one-true-awk
AWK = awk  # Crashes on SunOS 5.10 - use 'nawk'
CPP = $(CC) -E # On SUN OS 5.10 if this causes problems use /lib/cpp

MOVE = mv
DELETE = rm -f
ECHO = echo
DFA_XTRA = # Put your configuration file here, see scripts/pnglibconf.dfa.  Eg:
# DFA_XTRA = pngusr.dfa

# CPPFLAGS should contain the options to control the result,
# but DEFS and CFLAGS are also supported here, override
# as appropriate
DFNFLAGS = $(DEFS) $(CPPFLAGS) $(CFLAGS)

# srcdir is a defacto standard for the location of the source
srcdir = .

# The standard pnglibconf.h exists as scripts/pnglibconf.h.prebuilt,
# copy this if the following doesn't work.
pnglibconf.h: pnglibconf.dfn
	$(DELETE) $@ pnglibconf.c pnglibconf.out pnglibconf.tmp
	$(ECHO) '#include "pnglibconf.dfn"' >pnglibconf.c
	$(ECHO) "If '$(CC) -E' crashes try /lib/cpp (e.g. CPP='/lib/cpp')" >&2
	$(CPP) $(DFNFLAGS) pnglibconf.c >pnglibconf.out
	$(AWK) -f "$(srcdir)/scripts/dfn.awk" out="pnglibconf.tmp" pnglibconf.out 1>&2
	$(MOVE) pnglibconf.tmp $@

pnglibconf.dfn: $(srcdir)/scripts/pnglibconf.dfa $(srcdir)/scripts/options.awk $(srcdir)/pngconf.h $(srcdir)/pngusr.dfa $(DFA_XTRA)
	$(DELETE) $@ pnglibconf.pre pnglibconf.tmp
	$(ECHO) "Calling $(AWK) from scripts/pnglibconf.mak" >&2
	$(ECHO) "If 'awk' crashes try a better awk (e.g. AWK='nawk')" >&2
	$(AWK) -f $(srcdir)/scripts/options.awk out="pnglibconf.pre"\
	    version=search $(srcdir)/pngconf.h $(srcdir)/scripts/pnglibconf.dfa\
	    $(srcdir)/pngusr.dfa $(DFA_XTRA) 1>&2
	$(AWK) -f $(srcdir)/scripts/options.awk out="pnglibconf.tmp" pnglibconf.pre 1>&2
	$(MOVE) pnglibconf.tmp $@

clean-pnglibconf:
	$(DELETE) pnglibconf.h pnglibconf.c pnglibconf.out pnglibconf.pre \
	pnglibconf.dfn

clean: clean-pnglibconf

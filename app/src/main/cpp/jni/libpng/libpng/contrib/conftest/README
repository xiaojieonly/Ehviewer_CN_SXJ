This directory contains test configuration files, currently always '.dfa' files
intended to be used in the build by setting the make macro DFA_XTRA to the name
of the file.

These files are used in release validation of the 'configure' builds of libpng
by building 'make check', or 'make all-am' for cross-builds, with each .dfa
file.

The files in this directory may change between minor releases, however
contributions describing specific builds of libpng are welcomed.  There is no
guarantee that libpng will continue to build with such configurations; support
for given configurations can be, and has been, dropped between successive minor
releases.  However if a .dfa file describing a configuration is not in this
directory it is very unlikely that it will be tested before a minor release!

You can use these .dfa files as the basis of new configurations.  Files in this
directory should not have any use restrictions or restrictive licenses.

This directory is not included in the .zip and .7z distributions, which do
not contain 'configure' scripts.

DOCUMENTATION
=============

Examples:
   ${srcdir}/pngusr.dfa
   ${srcdir}/contrib/pngminim/*/pngusr.dfa

Documentation of the options:
   ${srcdir}/scripts/pnglibconf.dfa

Documentation of the file format:
   ${srcdir}/scripts/options.awk

FILE NAMING
===========

File names in this directory may NOT contain any of the five characters:

   - , + * ?

Neither may they contain any space character.

While other characters may be used it is strongly suggested that file names be
limited to lower case Latiin alphabetic characters (a-z), digits (0-9) and, if
necessary the underscore (_) character.  File names should be about 8 characters
long (excluding the .dfa extension).  Submitted .dfa files should have names
between 7 and 16 characters long, shorter names (6 characters or less) are
reserved for standard tests.

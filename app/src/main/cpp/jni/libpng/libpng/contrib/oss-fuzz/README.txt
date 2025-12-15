Last changed in libpng 1.6.33 [September 28, 2017]
Copyright (c) 2017 Glenn Randers-Pehrson

This code is released under the libpng license.
For conditions of distribution and use, see the disclaimer
and license in png.h

Files in this directory are used by the oss-fuzz project
(https://github.com/google/oss-fuzz/tree/master/projects/libpng).
for "fuzzing" libpng.

They were licensed by Google Inc, using the BSD-like Chromium license,
which may be found at https://cs.chromium.org/chromium/src/LICENSE, or, if
noted in the source, under the Apache-2.0 license, which may
be found at http://www.apache.org/licenses/LICENSE-2.0 .
If they have been modified, the derivatives are copyright Glenn Randers-Pehrson
and are released under the same licenses as the originals.  Several of
the original files (libpng_read_fuzzer.options, png.dict, project.yaml)
had no licensing information; we assumed that these were under the Chromium
license. Any new files are released under the libpng license (see png.h).

The files are
                            Original
 Filename                   or derived   Copyright          License
 =========================  ==========   ================   ==========
 Dockerfile*                derived      2017, Glenn R-P    Apache 2.0
 build.sh                   derived      2017, Glenn R-P    Apache 2.0
 libpng_read_fuzzer.cc      derived      2017, Glenn R-P    Chromium
 libpng_read_fuzzer.options original     2015, Chrome Devs  Chromium
 png.dict                   original     2015, Chrome Devs  Chromium
 README.txt (this file)     original     2017, Glenn R-P    libpng

 * Dockerfile is a copy of the file used by oss-fuzz. build.sh,
   png.dict and libpng_read_fuzzer.* are the actual files used by oss-fuzz,
   which retrieves them from the libpng repository at Github.

To do: exercise the progressive reader and the png encoder.

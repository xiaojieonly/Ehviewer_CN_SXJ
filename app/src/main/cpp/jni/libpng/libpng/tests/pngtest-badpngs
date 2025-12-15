#!/bin/sh

# various crashers
# using --relaxed because some come from fuzzers that don't maintain CRC's

./pngtest --relaxed ${srcdir}/contrib/testpngs/crashers/badcrc.png
./pngtest --relaxed ${srcdir}/contrib/testpngs/crashers/badadler.png
./pngtest --xfail ${srcdir}/contrib/testpngs/crashers/bad_iCCP.png
./pngtest --xfail ${srcdir}/contrib/testpngs/crashers/empty_ancillary_chunks.png
./pngtest --xfail ${srcdir}/contrib/testpngs/crashers/huge_*_chunk.png \
    ${srcdir}/contrib/testpngs/crashers/huge_*safe_to_copy.png

exec ./pngtest --xfail ${srcdir}/contrib/testpngs/crashers/huge_IDAT.png

#!/bin/sh

# reindent a libpng C source

# COPYRIGHT: Written by Glenn Randers-Pehrson, 2016.
# To the extent possible under law, the author has waived all copyright and
# related or neighboring rights to this work.  This work is published from:
# United States.

# Usage:
# reindent inputtabsize outputtabsize inputcontinuestring outputcontinuestring
#
# Assumes that continued lines begin with indentation plus one space, and
# that continued comments begin with indentation plus " *".
#
# eg, to change libpng coding style from 3-space indentation with 4-space
# continuations to 4-space indentation with 2-space continuations:
#
#  reindent 3 4 "\t " "  " < example.c > example.c_4_2
# and to restore the file back to libpng coding style
#  reindent 4 3 "  " "    " < example.c_4_2 > example.c_3_4

unexpand --first-only --t $1 | \
   sed -e "/^	*$3[^\*]/{s/$3/$4/}" | \
   expand -t $2

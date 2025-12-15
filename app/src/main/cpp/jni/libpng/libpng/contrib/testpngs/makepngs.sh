#!/bin/sh
#
# Make a set of test PNG files, MAKEPNG is the name of the makepng executable
# built from contrib/libtests/makepng.c

# Copyright (c) 2015 John Cunningham Bowler

# Last changed in libpng 1.6.20 [December 3, 2015]

# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

# The arguments say whether to build all the files or whether just to build the
# ones that extend the code-coverage of libpng from the existing test files in
# contrib/pngsuite.
test -n "$MAKEPNG" || MAKEPNG=./makepng
opts=

mp(){
   ${MAKEPNG} $opts $1 "$3" "$4" "$3-$4$2.png"
}

mpg(){
   if test "$1" = "none"
   then
      mp "" "" "$2" "$3"
   else
      mp "--$1" "-$1" "$2" "$3"
   fi
}

mptrans(){
   if test "$1" = "none"
   then
      mp "--tRNS" "-tRNS" "$2" "$3"
   else
      mp "--tRNS --$1" "-$1-tRNS" "$2" "$3"
   fi
}

case "$1" in
   --small)
      opts="--small";;&

   --all|--small)
      for g in none sRGB linear 1.8
      do
         for c in gray palette
         do
            for b in 1 2 4
            do
               mpg "$g" "$c" "$b"
               mptrans "$g" "$c" "$b"
            done
         done

         mpg "$g" palette 8
         mptrans "$g" palette 8

         for b in 8 16
         do
            for c in gray gray-alpha rgb rgb-alpha
            do
               mpg "$g" "$c" "$b"
            done
            for c in gray rgb
            do
               mptrans "$g" "$c" "$b"
            done
         done
      done;;

   --coverage)
      # Comments below indicate cases known to be required and not duplicated
      # in other (required) cases; the aim is to get a minimal set that gives
      # the maximum code coverage.
      mpg none gray-alpha 8 # required: code coverage, sRGB opaque component
      mpg none palette 8 # required: basic palette read
      mpg 1.8 gray 2 # required: tests gamma threshold code
      mpg 1.8 palette 2 # required: code coverage
      mpg 1.8 palette 4 # required: code coverage
      mpg 1.8 palette 8 # error limits only
      mpg linear palette 8 # error limits only
      mpg linear rgb-alpha 16 # error limits only
      mpg sRGB palette 1 # required: code coverage
      mpg sRGB rgb-alpha 16 # required: code coverage: pngread.c:2422 untested
      :;;

   *)
      echo "$0 $1: unknown argument, usage:" >&2
      echo "  $0 [--all|--coverage|--small]" >&2
      exit 1
esac

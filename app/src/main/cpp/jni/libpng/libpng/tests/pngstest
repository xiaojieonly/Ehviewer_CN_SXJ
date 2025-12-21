#!/bin/sh
#
# Usage:
#
#  tests/pngstest gamma alpha
#
# Run ./pngstest on the PNG files in $srcdir/contrib/testpngs which have the
# given gamma and opacity:
#
#  gamma: one of; linear, 1.8, sRGB, none.
#  alpha: one of; opaque, tRNS, alpha, none.  'none' is equivalent to !alpha
#
# NOTE: the temporary files pngstest generates have the base name gamma-alpha to
# avoid issues with make -j
#
gamma="$1"
shift
alpha="$1"
shift
args=
LC_ALL="C" # fix glob sort order to ASCII:
for f in "${srcdir}/contrib/testpngs/"*.png
do
   g=
   case "$f" in
      *-linear[.-]*)
         test "$gamma" = "linear" && g="$f";;

      *-sRGB[.-]*)
         test "$gamma" = "sRGB" && g="$f";;

      *-1.8[.-]*)
         test "$gamma" = "1.8" && g="$f";;

      *)
         test "$gamma" = "none" && g="$f";;
   esac

   case "$g" in
      "")
         :;;

      *-alpha[-.]*)
         test "$alpha" = "alpha" && args="$args $g";;

      *-tRNS[-.]*)
         test "$alpha" = "tRNS" -o "$alpha" = "none" && args="$args $g";;

      *)
         test "$alpha" = "opaque" -o "$alpha" = "none" && args="$args $g";;
   esac
done
# This only works if the arguments don't contain spaces; they don't.
exec ./pngstest --tmpfile "${gamma}-${alpha}-" --log ${1+"$@"} $args

#!/bin/sh
#
# intgamma.sh
#
# Last changed in libpng 1.6.0 [February 14, 2013]
#
# COPYRIGHT: Written by John Cunningham Bowler, 2013.
# To the extent possible under law, the author has waived all copyright and
# related or neighboring rights to this work.  This work is published from:
# United States.
#
# Shell script to generate png.c 8-bit and 16-bit log tables (see the code in
# png.c for details).
#
# This script uses the "bc" arbitrary precision calculator to calculate 32-bit
# fixed point values of logarithms appropriate to finding the log of an 8-bit
# (0..255) value and a similar table for the exponent calculation.
#
# "bc" must be on the path when the script is executed, and the math library
# (-lm) must be available
#
# function to print out a list of numbers as integers; the function truncates
# the integers which must be one-per-line
function print(){
   awk 'BEGIN{
      str = ""
   }
   {
      sub("\\.[0-9]*$", "")
      if ($0 == "")
         $0 = "0"

      if (str == "")
         t = "   " $0 "U"
      else
         t = str ", " $0 "U"

      if (length(t) >= 80) {
         print str ","
         str = "   " $0 "U"
      } else
         str = t
   }
   END{
      print str
   }'
}
#
# The logarithm table.
cat <<END
/* 8-bit log table: png_8bit_l2[128]
 * This is a table of -log(value/255)/log(2) for 'value' in the range 128 to
 * 255, so it's the base 2 logarithm of a normalized 8-bit floating point
 * mantissa.  The numbers are 32-bit fractions.
 */
static const png_uint_32
png_8bit_l2[128] =
{
END
#
bc -lqws <<END | print
f=65536*65536/l(2)
for (i=128;i<256;++i) { .5 - l(i/255)*f; }
END
echo '};'
echo
#
# The exponent table.
cat <<END
/* The 'exp()' case must invert the above, taking a 20-bit fixed point
 * logarithmic value and returning a 16 or 8-bit number as appropriate.  In
 * each case only the low 16 bits are relevant - the fraction - since the
 * integer bits (the top 4) simply determine a shift.
 *
 * The worst case is the 16-bit distinction between 65535 and 65534; this
 * requires perhaps spurious accuracy in the decoding of the logarithm to
 * distinguish log2(65535/65534.5) - 10^-5 or 17 bits.  There is little chance
 * of getting this accuracy in practice.
 *
 * To deal with this the following exp() function works out the exponent of the
 * frational part of the logarithm by using an accurate 32-bit value from the
 * top four fractional bits then multiplying in the remaining bits.
 */
static const png_uint_32
png_32bit_exp[16] =
{
END
#
bc -lqws <<END | print
f=l(2)/16
for (i=0;i<16;++i) {
   x = .5 + e(-i*f)*2^32;
   if (x >= 2^32) x = 2^32-1;
   x;
}
END
echo '};'
echo
#
# And the table of adjustment values.
cat <<END
/* Adjustment table; provided to explain the numbers in the code below. */
#if 0
END
bc -lqws <<END | awk '{ printf "%5d %s\n", 12-NR, $0 }'
for (i=11;i>=0;--i){
   (1 - e(-(2^i)/65536*l(2))) * 2^(32-i)
}
END
echo '#endif'

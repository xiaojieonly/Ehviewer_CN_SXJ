/* contrib/arm-neon/android-ndk.c
 *
 * Copyright (c) 2014 Glenn Randers-Pehrson
 * Written by John Bowler, 2014.
 * Last changed in libpng 1.6.10 [March 6, 2014]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * SEE contrib/arm-neon/README before reporting bugs
 *
 * STATUS: COMPILED, UNTESTED
 * BUG REPORTS: png-mng-implement@sourceforge.net
 *
 * png_have_neon implemented for the Android NDK, see:
 *
 * Documentation:
 *    http://www.kandroid.org/ndk/docs/CPU-ARM-NEON.html
 *    https://code.google.com/p/android/issues/detail?id=49065
 *
 * NOTE: this requires that libpng is built against the Android NDK and linked
 * with an implementation of the Android ARM 'cpu-features' library.  The code
 * has been compiled only, not linked: no version of the library has been found,
 * only the header files exist in the NDK.
 */
#include <cpu-features.h>

static int
png_have_neon(png_structp png_ptr)
{
   /* This is a whole lot easier than the linux code, however it is probably
    * implemented as below, therefore it is better to cache the result (these
    * function calls may be slow!)
    */
   PNG_UNUSED(png_ptr)
   return android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM &&
      (android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON) != 0;
}

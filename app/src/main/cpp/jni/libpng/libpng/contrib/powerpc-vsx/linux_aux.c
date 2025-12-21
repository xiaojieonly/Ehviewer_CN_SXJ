/* contrib/powerpc-vsx/linux_aux.c
 *
 * Copyright (c) 2017 Glenn Randers-Pehrson
 * Written by Vadim Barkov, 2017.
 * Last changed in libpng 1.6.29 [March 16, 2017]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * STATUS: TESTED
 * BUG REPORTS: png-mng-implement@sourceforge.net
 *
 * png_have_vsx implemented for Linux by using the auxiliary vector mechanism.
 *
 * This code is strict ANSI-C and is probably moderately portable; it does
 * however use <stdio.h> and it assumes that /proc/cpuinfo is never localized.
 */

#include "sys/auxv.h"
#include "png.h"

static int
png_have_vsx(png_structp png_ptr)
{
   unsigned long auxv = getauxval(AT_HWCAP);

   PNG_UNUSED(png_ptr)

   if(auxv & (PPC_FEATURE_HAS_ALTIVEC|PPC_FEATURE_HAS_VSX))
      return 1;
   else
      return 0;
}

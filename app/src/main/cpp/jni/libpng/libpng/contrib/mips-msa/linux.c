/* contrib/mips-msa/linux.c
 *
 * Copyright (c) 2016 Glenn Randers-Pehrson
 * Written by Mandar Sahastrabuddhe, 2016.
 * Last changed in libpng 1.6.25beta03 [August 29, 2016]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * SEE contrib/mips-msa/README before reporting bugs
 *
 * STATUS: SUPPORTED
 * BUG REPORTS: png-mng-implement@sourceforge.net
 *
 * png_have_msa implemented for Linux by reading the widely available
 * pseudo-file /proc/cpuinfo.
 *
 * This code is strict ANSI-C and is probably moderately portable; it does
 * however use <stdio.h> and it assumes that /proc/cpuinfo is never localized.
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

static int
png_have_msa(png_structp png_ptr)
{
   FILE *f = fopen("/proc/cpuinfo", "rb");

   char *string = "msa";
   char word[10];

   if (f != NULL)
   {
      while(!feof(f))
      {
         int ch = fgetc(f);
         static int i = 0;

         while(!(ch <= 32))
         {
            word[i++] = ch;
            ch = fgetc(f);
         }

         int val = strcmp(string, word);

         if (val == 0)
            return 1;

         i = 0;
         memset(word, 0, 10);
      }

      fclose(f);
   }
#ifdef PNG_WARNINGS_SUPPORTED
   else
      png_warning(png_ptr, "/proc/cpuinfo open failed");
#endif
   return 0;
}

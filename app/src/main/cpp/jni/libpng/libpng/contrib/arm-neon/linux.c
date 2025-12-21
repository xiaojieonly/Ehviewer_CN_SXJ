/* contrib/arm-neon/linux.c
 *
 * Last changed in libpng 1.6.31 [July 27, 2017]
 * Copyright (c) 2014, 2017 Glenn Randers-Pehrson
 * Written by John Bowler, 2014, 2017.
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * SEE contrib/arm-neon/README before reporting bugs
 *
 * STATUS: SUPPORTED
 * BUG REPORTS: png-mng-implement@sourceforge.net
 *
 * png_have_neon implemented for Linux by reading the widely available
 * pseudo-file /proc/cpuinfo.
 *
 * This code is strict ANSI-C and is probably moderately portable; it does
 * however use <stdio.h> and it assumes that /proc/cpuinfo is never localized.
 */
#include <stdio.h>

static int
png_have_neon(png_structp png_ptr)
{
   FILE *f = fopen("/proc/cpuinfo", "rb");

   if (f != NULL)
   {
      /* This is a simple state machine which reads the input byte-by-byte until
       * it gets a match on the 'neon' feature or reaches the end of the stream.
       */
      static const char ch_feature[] = { 70, 69, 65, 84, 85, 82, 69, 83 };
      static const char ch_neon[] = { 78, 69, 79, 78 };

      enum
      {
         StartLine, Feature, Colon, StartTag, Neon, HaveNeon, SkipTag, SkipLine
      }  state;
      int counter;

      for (state=StartLine, counter=0;;)
      {
         int ch = fgetc(f);

         if (ch == EOF)
         {
            /* EOF means error or end-of-file, return false; neon at EOF is
             * assumed to be a mistake.
             */
            fclose(f);
            return 0;
         }

         switch (state)
         {
            case StartLine:
               /* Match spaces at the start of line */
               if (ch <= 32) /* skip control characters and space */
                  break;

               counter=0;
               state = Feature;
               /* FALLTHROUGH */

            case Feature:
               /* Match 'FEATURE', ASCII case insensitive. */
               if ((ch & ~0x20) == ch_feature[counter])
               {
                  if (++counter == (sizeof ch_feature))
                     state = Colon;
                  break;
               }

               /* did not match 'feature' */
               state = SkipLine;
               /* FALLTHROUGH */

            case SkipLine:
            skipLine:
               /* Skip everything until we see linefeed or carriage return */
               if (ch != 10 && ch != 13)
                  break;

               state = StartLine;
               break;

            case Colon:
               /* Match any number of space or tab followed by ':' */
               if (ch == 32 || ch == 9)
                  break;

               if (ch == 58) /* i.e. ':' */
               {
                  state = StartTag;
                  break;
               }

               /* Either a bad line format or a 'feature' prefix followed by
                * other characters.
                */
               state = SkipLine;
               goto skipLine;

            case StartTag:
               /* Skip space characters before a tag */
               if (ch == 32 || ch == 9)
                  break;

               state = Neon;
               counter = 0;
               /* FALLTHROUGH */

            case Neon:
               /* Look for 'neon' tag */
               if ((ch & ~0x20) == ch_neon[counter])
               {
                  if (++counter == (sizeof ch_neon))
                     state = HaveNeon;
                  break;
               }

               state = SkipTag;
               /* FALLTHROUGH */

            case SkipTag:
               /* Skip non-space characters */
               if (ch == 10 || ch == 13)
                  state = StartLine;

               else if (ch == 32 || ch == 9)
                  state = StartTag;
               break;

            case HaveNeon:
               /* Have seen a 'neon' prefix, but there must be a space or new
                * line character to terminate it.
                */
               if (ch == 10 || ch == 13 || ch == 32 || ch == 9)
               {
                  fclose(f);
                  return 1;
               }

               state = SkipTag;
               break;

            default:
               png_error(png_ptr, "png_have_neon: internal error (bug)");
         }
      }
   }

#ifdef PNG_WARNINGS_SUPPORTED
   else
      png_warning(png_ptr, "/proc/cpuinfo open failed");
#endif

   return 0;
}

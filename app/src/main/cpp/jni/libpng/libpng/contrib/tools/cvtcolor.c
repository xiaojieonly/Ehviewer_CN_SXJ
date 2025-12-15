/*-
 * convert.c
 *
 * Last changed in libpng 1.6.0 [February 14, 2013]
 *
 * COPYRIGHT: Written by John Cunningham Bowler, 2013.
 * To the extent possible under law, the author has waived all copyright and
 * related or neighboring rights to this work.  This work is published from:
 * United States.
 *
 * Convert 8-bit sRGB or 16-bit linear values to another format.
 */
#define _ISOC99_SOURCE 1

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>

#include <fenv.h>

#include "sRGB.h"

static void
usage(const char *prog)
{
   fprintf(stderr,
      "%s: usage: %s [-linear|-sRGB] [-gray|-color] component{1,4}\n",
      prog, prog);
   exit(1);
}

unsigned long
component(const char *prog, const char *arg, int issRGB)
{
   char *ep;
   unsigned long c = strtoul(arg, &ep, 0);

   if (ep <= arg || *ep || c > 65535 || (issRGB && c > 255))
   {
      fprintf(stderr, "%s: %s: invalid component value (%lu)\n", prog, arg, c);
      usage(prog);
   }

   return c;
}

int
main(int argc, const char **argv)
{
   const char *prog = *argv++;
   int to_linear = 0, to_gray = 0, to_color = 0;
   int channels = 0;
   double c[4];

   /* FE_TONEAREST is the IEEE754 round to nearest, preferring even, mode; i.e.
    * everything rounds to the nearest value except that '.5' rounds to the
    * nearest even value.
    */
   fesetround(FE_TONEAREST);

   c[3] = c[2] = c[1] = c[0] = 0;

   while (--argc > 0 && **argv == '-')
   {
      const char *arg = 1+*argv++;

      if (strcmp(arg, "sRGB") == 0)
         to_linear = 0;

      else if (strcmp(arg, "linear") == 0)
         to_linear = 1;

      else if (strcmp(arg, "gray") == 0)
         to_gray = 1, to_color = 0;

      else if (strcmp(arg, "color") == 0)
         to_gray = 0, to_color = 1;

      else
         usage(prog);
   }

   switch (argc)
   {
      default:
         usage(prog);
         break;

      case 4:
         c[3] = component(prog, argv[3], to_linear);
         ++channels;
      case 3:
         c[2] = component(prog, argv[2], to_linear);
         ++channels;
      case 2:
         c[1] = component(prog, argv[1], to_linear);
         ++channels;
      case 1:
         c[0] = component(prog, argv[0], to_linear);
         ++channels;
         break;
      }

   if (to_linear)
   {
      int i;
      int components = channels;

      if ((components & 1) == 0)
         --components;

      for (i=0; i<components; ++i) c[i] = linear_from_sRGB(c[i] / 255);
      if (components < channels)
         c[components] = c[components] / 255;
   }

   else
   {
      int i;
      for (i=0; i<4; ++i) c[i] /= 65535;

      if ((channels & 1) == 0)
      {
         double alpha = c[channels-1];

         if (alpha > 0)
            for (i=0; i<channels-1; ++i) c[i] /= alpha;
         else
            for (i=0; i<channels-1; ++i) c[i] = 1;
      }
   }

   if (to_gray)
   {
      if (channels < 3)
      {
         fprintf(stderr, "%s: too few channels (%d) for -gray\n",
            prog, channels);
         usage(prog);
      }

      c[0] = YfromRGB(c[0], c[1], c[2]);
      channels -= 2;
   }

   if (to_color)
   {
      if (channels > 2)
      {
         fprintf(stderr, "%s: too many channels (%d) for -color\n",
            prog, channels);
         usage(prog);
      }

      c[3] = c[1]; /* alpha, if present */
      c[2] = c[1] = c[0];
   }

   if (to_linear)
   {
      int i;
      if ((channels & 1) == 0)
      {
         double alpha = c[channels-1];
         for (i=0; i<channels-1; ++i) c[i] *= alpha;
      }

      for (i=0; i<channels; ++i) c[i] = nearbyint(c[i] * 65535);
   }

   else /* to sRGB */
   {
      int i = (channels+1)&~1;
      while (--i >= 0)
         c[i] = sRGB_from_linear(c[i]);

      for (i=0; i<channels; ++i) c[i] = nearbyint(c[i] * 255);
   }

   {
      int i;
      for (i=0; i<channels; ++i) printf(" %g", c[i]);
   }
   printf("\n");

   return 0;
}

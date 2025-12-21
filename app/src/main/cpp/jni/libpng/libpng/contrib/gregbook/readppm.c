/*---------------------------------------------------------------------------

   rpng - simple PNG display program                              readppm.c

  ---------------------------------------------------------------------------

   This is a special-purpose replacement for readpng.c that allows binary
   PPM files to be used in place of PNG images.

  ---------------------------------------------------------------------------

      Copyright (c) 1998-2007,2017 Greg Roelofs.  All rights reserved.

      This software is provided "as is," without warranty of any kind,
      express or implied.  In no event shall the author or contributors
      be held liable for any damages arising in any way from the use of
      this software.

      The contents of this file are DUAL-LICENSED.  You may modify and/or
      redistribute this software according to the terms of one of the
      following two licenses (at your option):


      LICENSE 1 ("BSD-like with advertising clause"):

      Permission is granted to anyone to use this software for any purpose,
      including commercial applications, and to alter it and redistribute
      it freely, subject to the following restrictions:

      1. Redistributions of source code must retain the above copyright
         notice, disclaimer, and this list of conditions.
      2. Redistributions in binary form must reproduce the above copyright
         notice, disclaimer, and this list of conditions in the documenta-
         tion and/or other materials provided with the distribution.
      3. All advertising materials mentioning features or use of this
         software must display the following acknowledgment:

            This product includes software developed by Greg Roelofs
            and contributors for the book, "PNG: The Definitive Guide,"
            published by O'Reilly and Associates.


      LICENSE 2 (GNU GPL v2 or later):

      This program is free software; you can redistribute it and/or modify
      it under the terms of the GNU General Public License as published by
      the Free Software Foundation; either version 2 of the License, or
      (at your option) any later version.

      This program is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.

      You should have received a copy of the GNU General Public License
      along with this program; if not, write to the Free Software Foundation,
      Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  ---------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>

#include "readpng.h"    /* typedefs, common macros, public prototypes */


ulg  width, height;
int  bit_depth, color_type, channels;
uch  *image_data = NULL;
FILE *saved_infile;


void readpng_version_info()
{
    fprintf(stderr, "   Compiled without libpng, zlib or PBMPLUS/NetPBM.\n");
}


/* return value = 0 for success, 1 for bad sig, 2 for bad IHDR, 4 for no mem */

int readpng_init(FILE *infile, ulg *pWidth, ulg *pHeight)
{
    static uch ppmline[256];
    int maxval;


    saved_infile = infile;

    fgets(ppmline, 256, infile);
    if (ppmline[0] != 'P' || ppmline[1] != '6') {
        fprintf(stderr, "ERROR:  not a PPM file\n");
        return 1;
    }
    /* possible color types:  P5 = grayscale (0), P6 = RGB (2), P8 = RGBA (6) */
    if (ppmline[1] == '6') {
        color_type = 2;
        channels = 3;
    } else if (ppmline[1] == '8') {
        color_type = 6;
        channels = 4;
    } else /* if (ppmline[1] == '5') */ {
        color_type = 0;
        channels = 1;
    }

    do {
        fgets(ppmline, 256, infile);
    } while (ppmline[0] == '#');
    sscanf(ppmline, "%lu %lu", &width, &height);

    do {
        fgets(ppmline, 256, infile);
    } while (ppmline[0] == '#');
    sscanf(ppmline, "%d", &maxval);
    if (maxval != 255) {
        fprintf(stderr, "ERROR:  maxval = %d\n", maxval);
        return 2;
    }
    bit_depth = 8;

    *pWidth = width;
    *pHeight = height;

    return 0;
}




/* returns 0 if succeeds, 1 if fails due to no bKGD chunk, 2 if libpng error;
 * scales values to 8-bit if necessary */

int readpng_get_bgcolor(uch *red, uch *green, uch *blue)
{
    return 1;
}




/* display_exponent == LUT_exponent * CRT_exponent */

uch *readpng_get_image(double display_exponent, int *pChannels, ulg *pRowbytes)
{
    ulg  rowbytes;


    /* expand palette images to RGB, low-bit-depth grayscale images to 8 bits,
     * transparency chunks to full alpha channel; strip 16-bit-per-sample
     * images to 8 bits per sample; and convert grayscale to RGB[A] */

    /* GRR WARNING:  grayscale needs to be expanded and channels reset! */

    *pRowbytes = rowbytes = channels*width;
    *pChannels = channels;

    Trace((stderr, "readpng_get_image:  rowbytes = %ld, height = %ld\n", rowbytes, height));

    /* Guard against integer overflow */
    if (height > ((size_t)(-1))/rowbytes) {
        fprintf(stderr, PROGNAME ":  image_data buffer would be too large\n",
        return NULL;
    }

    if ((image_data = (uch *)malloc(rowbytes*height)) == NULL) {
        return NULL;
    }

    /* now we can go ahead and just read the whole image */

    if (fread(image_data, 1L, rowbytes*height, saved_infile) <
       rowbytes*height) {
        free (image_data);
        image_data = NULL;
        return NULL;
    }

    return image_data;
}


void readpng_cleanup(int free_image_data)
{
    if (free_image_data && image_data) {
        free(image_data);
        image_data = NULL;
    }
}

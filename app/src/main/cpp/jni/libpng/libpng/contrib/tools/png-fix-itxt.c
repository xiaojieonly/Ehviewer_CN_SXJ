
/* png-fix-itxt version 1.0.0
 *
 * Copyright 2015 Glenn Randers-Pehrson
 * Last changed in libpng 1.6.18 [July 23, 2015]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Usage:
 *
 *     png-fix-itxt.exe < bad.png > good.png
 *
 * Fixes a PNG file written with libpng-1.6.0 or 1.6.1 that has one or more
 * uncompressed iTXt chunks.  Assumes that the actual length is greater
 * than or equal to the value in the length byte, and that the CRC is
 * correct for the actual length.  This program hunts for the CRC and
 * adjusts the length byte accordingly.  It is not an error to process a
 * PNG file that has no iTXt chunks or one that has valid iTXt chunks;
 * such files will simply be copied.
 *
 * Requires zlib (for crc32 and Z_NULL); build with
 *
 *     gcc -O -o png-fix-itxt png-fix-itxt.c -lz
 *
 * If you need to handle iTXt chunks larger than 500000 kbytes you must
 * rebuild png-fix-itxt with a larger values of MAX_LENGTH (or a smaller value
 * if you know you will never encounter such huge iTXt chunks).
 */

#include <stdio.h>
#include <zlib.h>

#define MAX_LENGTH 500000

/* Read one character (inchar), also return octet (c), break if EOF */
#define GETBREAK inchar=getchar(); \
                 c=(inchar & 0xffU);\
                 if (inchar != c) break
int
main(void)
{
   unsigned int i;
   unsigned char buf[MAX_LENGTH];
   unsigned long crc;
   unsigned char c;
   int inchar;

/* Skip 8-byte signature */
   for (i=8; i; i--)
   {
      GETBREAK;
      putchar(c);
   }

if (inchar == c) /* !EOF */
for (;;)
 {
   /* Read the length */
   unsigned long length; /* must be 32 bits! */
   GETBREAK; buf[0] = c; length  = c; length <<= 8;
   GETBREAK; buf[1] = c; length += c; length <<= 8;
   GETBREAK; buf[2] = c; length += c; length <<= 8;
   GETBREAK; buf[3] = c; length += c;

   /* Read the chunkname */
   GETBREAK; buf[4] = c;
   GETBREAK; buf[5] = c;
   GETBREAK; buf[6] = c;
   GETBREAK; buf[7] = c;


   /* The iTXt chunk type expressed as integers is (105, 84, 88, 116) */
   if (buf[4] == 105 && buf[5] == 84 && buf[6] == 88 && buf[7] == 116)
   {
      if (length >= MAX_LENGTH-12)
         break;  /* To do: handle this more gracefully */

      /* Initialize the CRC */
      crc = crc32(0, Z_NULL, 0);

      /* Copy the data bytes */
      for (i=8; i < length + 12; i++)
      {
         GETBREAK; buf[i] = c;
      }

      if (inchar != c) /* EOF */
         break;

      /* Calculate the CRC */
      crc = crc32(crc, buf+4, (uInt)length+4);

      for (;;)
      {
        /* Check the CRC */
        if (((crc >> 24) & 0xffU) == buf[length+8] &&
            ((crc >> 16) & 0xffU) == buf[length+9] &&
            ((crc >>  8) & 0xffU) == buf[length+10] &&
            ((crc      ) & 0xffU) == buf[length+11])
           break;

        length++;

        if (length >= MAX_LENGTH-12)
           break;

        GETBREAK;
        buf[length+11] = c;

        /* Update the CRC */
        crc = crc32(crc, buf+7+length, 1);
      }

      if (inchar != c) /* EOF */
         break;

      /* Update length bytes */
      buf[0] = (unsigned char)((length >> 24) & 0xffU);
      buf[1] = (unsigned char)((length >> 16) & 0xffU);
      buf[2] = (unsigned char)((length >>  8) & 0xffU);
      buf[3] = (unsigned char)((length      ) & 0xffU);

      /* Write the fixed iTXt chunk (length, name, data, crc) */
      for (i=0; i<length+12; i++)
         putchar(buf[i]);
   }

   else
   {
      if (inchar != c) /* EOF */
         break;

      /* Copy bytes that were already read (length and chunk name) */
      for (i=0; i<8; i++)
         putchar(buf[i]);

      /* Copy data bytes and CRC */
      for (i=8; i< length+12; i++)
      {
         GETBREAK;
         putchar(c);
      }

      if (inchar != c) /* EOF */
      {
         break;
      }

   /* The IEND chunk type expressed as integers is (73, 69, 78, 68) */
      if (buf[4] == 73 && buf[5] == 69 && buf[6] == 78 && buf[7] == 68)
         break;
   }

   if (inchar != c) /* EOF */
      break;

   if (buf[4] == 73 && buf[5] == 69 && buf[6] == 78 && buf[7] == 68)
     break;
 }

 return 0;
}

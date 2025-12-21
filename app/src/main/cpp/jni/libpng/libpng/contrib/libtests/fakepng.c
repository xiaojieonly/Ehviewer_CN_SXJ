/* Fake a PNG - just write it out directly.
 *
 * COPYRIGHT: Written by John Cunningham Bowler, 2014.
 * To the extent possible under law, the author has waived all copyright and
 * related or neighboring rights to this work.  This work is published from:
 * United States.
 *
 */

#include <stdio.h>
#include <zlib.h> /* for crc32 */

void
put_uLong(uLong val)
{
   putchar(val >> 24);
   putchar(val >> 16);
   putchar(val >>  8);
   putchar(val >>  0);
}

void
put_chunk(const unsigned char *chunk, uInt length)
{
   uLong crc;

   put_uLong(length-4); /* Exclude the tag */

   fwrite(chunk, length, 1, stdout);

   crc = crc32(0, Z_NULL, 0);
   put_uLong(crc32(crc, chunk, length));
}

const unsigned char signature[] =
{
   137, 80, 78, 71, 13, 10, 26, 10
};

const unsigned char IHDR[] =
{
   73, 72, 68, 82, /* IHDR */
   0, 0, 0, 1, /* width */
   0, 0, 0, 1, /* height */
   1, /* bit depth */
   0, /* color type: greyscale */
   0, /* compression method */
   0, /* filter method */
   0  /* interlace method: none */
};

const unsigned char unknown[] =
{
   'u', 'n', 'K', 'n' /* "unKn" - private safe to copy */
};

int
main(void)
{
   fwrite(signature, sizeof signature, 1, stdout);
   put_chunk(IHDR, sizeof IHDR);

   for (;;)
      put_chunk(unknown, sizeof unknown);
}

/* pngfix.c
 *
 * Last changed in libpng 1.6.31 [July 27, 2017]
 * Copyright (c) 2014-2017 John Cunningham Bowler
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Tool to check and fix the zlib inflate 'too far back' problem.
 * See the usage message for more information.
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <limits.h>
#include <errno.h>
#include <assert.h>

#define implies(x,y) assert(!(x) || (y))

#ifdef __GNUC__
   /* This is used to fix the error:
    *
    * pngfix.c:
    * In function 'zlib_advance':
    * pngfix.c:181:13: error: assuming signed overflow does not
    *   occur when simplifying conditional to constant [-Werror=strict-overflow]
    */
#  define FIX_GCC volatile
#else
#  define FIX_GCC
#endif

#define PROGRAM_NAME "pngfix"

/* Define the following to use this program against your installed libpng,
 * rather than the one being built here:
 */
#ifdef PNG_FREESTANDING_TESTS
#  include <png.h>
#else
#  include "../../png.h"
#endif

#if PNG_LIBPNG_VER < 10603 /* 1.6.3 */
#  error "pngfix will not work with libpng prior to 1.6.3"
#endif

#ifdef PNG_SETJMP_SUPPORTED
#include <setjmp.h>

#if defined(PNG_READ_SUPPORTED) && defined(PNG_EASY_ACCESS_SUPPORTED) &&\
   (defined(PNG_READ_DEINTERLACE_SUPPORTED) ||\
    defined(PNG_READ_INTERLACING_SUPPORTED))

/* zlib.h defines the structure z_stream, an instance of which is included
 * in this structure and is required for decompressing the LZ compressed
 * data in PNG files.
 */
#ifndef ZLIB_CONST
   /* We must ensure that zlib uses 'const' in declarations. */
#  define ZLIB_CONST
#endif
#include <zlib.h>
#ifdef const
   /* zlib.h sometimes #defines const to nothing, undo this. */
#  undef const
#endif

/* zlib.h has mediocre z_const use before 1.2.6, this stuff is for compatibility
 * with older builds.
 */
#if ZLIB_VERNUM < 0x1260
#  define PNGZ_MSG_CAST(s) constcast(char*,s)
#  define PNGZ_INPUT_CAST(b) constcast(png_bytep,b)
#else
#  define PNGZ_MSG_CAST(s) (s)
#  define PNGZ_INPUT_CAST(b) (b)
#endif

#ifndef PNG_MAXIMUM_INFLATE_WINDOW
#  error "pngfix not supported in this libpng version"
#endif

#if ZLIB_VERNUM >= 0x1240

/* Copied from pngpriv.h */
#ifdef __cplusplus
#  define voidcast(type, value) static_cast<type>(value)
#  define constcast(type, value) const_cast<type>(value)
#  define aligncast(type, value) \
   static_cast<type>(static_cast<void*>(value))
#  define aligncastconst(type, value) \
   static_cast<type>(static_cast<const void*>(value))
#else
#  define voidcast(type, value) (value)
#  define constcast(type, value) ((type)(value))
#  define aligncast(type, value) ((void*)(value))
#  define aligncastconst(type, value) ((const void*)(value))
#endif /* __cplusplus */

#if PNG_LIBPNG_VER < 10700
/* Chunk tags (copied from pngpriv.h) */
#define PNG_32b(b,s) ((png_uint_32)(b) << (s))
#define PNG_U32(b1,b2,b3,b4) \
   (PNG_32b(b1,24) | PNG_32b(b2,16) | PNG_32b(b3,8) | PNG_32b(b4,0))

/* Constants for known chunk types. */
#define png_IDAT PNG_U32( 73,  68,  65,  84)
#define png_IEND PNG_U32( 73,  69,  78,  68)
#define png_IHDR PNG_U32( 73,  72,  68,  82)
#define png_PLTE PNG_U32( 80,  76,  84,  69)
#define png_bKGD PNG_U32( 98,  75,  71,  68)
#define png_cHRM PNG_U32( 99,  72,  82,  77)
#define png_fRAc PNG_U32(102,  82,  65,  99) /* registered, not defined */
#define png_gAMA PNG_U32(103,  65,  77,  65)
#define png_gIFg PNG_U32(103,  73,  70, 103)
#define png_gIFt PNG_U32(103,  73,  70, 116) /* deprecated */
#define png_gIFx PNG_U32(103,  73,  70, 120)
#define png_hIST PNG_U32(104,  73,  83,  84)
#define png_iCCP PNG_U32(105,  67,  67,  80)
#define png_iTXt PNG_U32(105,  84,  88, 116)
#define png_oFFs PNG_U32(111,  70,  70, 115)
#define png_pCAL PNG_U32(112,  67,  65,  76)
#define png_pHYs PNG_U32(112,  72,  89, 115)
#define png_sBIT PNG_U32(115,  66,  73,  84)
#define png_sCAL PNG_U32(115,  67,  65,  76)
#define png_sPLT PNG_U32(115,  80,  76,  84)
#define png_sRGB PNG_U32(115,  82,  71,  66)
#define png_sTER PNG_U32(115,  84,  69,  82)
#define png_tEXt PNG_U32(116,  69,  88, 116)
#define png_tIME PNG_U32(116,  73,  77,  69)
#define png_tRNS PNG_U32(116,  82,  78,  83)
#define png_zTXt PNG_U32(122,  84,  88, 116)
#endif

/* The 8-byte signature as a pair of 32-bit quantities */
#define sig1 PNG_U32(137,  80,  78,  71)
#define sig2 PNG_U32( 13,  10,  26,  10)

/* Is the chunk critical? */
#define CRITICAL(chunk) (((chunk) & PNG_U32(32,0,0,0)) == 0)

/* Is it safe to copy? */
#define SAFE_TO_COPY(chunk) (((chunk) & PNG_U32(0,0,0,32)) != 0)

/* Fix ups for builds with limited read support */
#ifndef PNG_ERROR_TEXT_SUPPORTED
#  define png_error(a,b) png_err(a)
#endif

/********************************* UTILITIES **********************************/
/* UNREACHED is a value to cause an assert to fail. Because of the way the
 * assert macro is written the string "UNREACHED" is produced in the error
 * message.
 */
#define UNREACHED 0

/* 80-bit number handling - a PNG image can be up to (2^31-1)x(2^31-1) 8-byte
 * (16-bit RGBA) pixels in size; that's less than 2^65 bytes or 2^68 bits, so
 * arithmetic of 80-bit numbers is sufficient.  This representation uses an
 * arbitrary length array of png_uint_16 digits (0..65535).  The representation
 * is little endian.
 *
 * The arithmetic functions take zero to two uarb values together with the
 * number of digits in those values and write the result to the given uarb
 * (always the first argument) returning the number of digits in the result.
 * If the result is negative the return value is also negative (this would
 * normally be an error).
 */
typedef png_uint_16  udigit; /* A 'unum' is an array of these */
typedef png_uint_16p uarb;
typedef png_const_uint_16p uarbc;

#define UDIGITS(unum) ((sizeof unum)/(sizeof (udigit))
   /* IMPORTANT: only apply this to an array, applied to a pointer the result
    * will typically be '2', which is not useful.
    */

static int
uarb_set(uarb result, png_alloc_size_t val)
   /* Set (initialize) 'result' to 'val'.  The size required for 'result' must
    * be determined by the caller from a knowledge of the maximum for 'val'.
    */
{
   int ndigits = 0;

   while (val > 0)
   {
      result[ndigits++] = (png_uint_16)(val & 0xffff);
      val >>= 16;
   }

   return ndigits;
}

static int
uarb_copy(uarb to, uarb from, int idigits)
   /* Copy a uarb, may reduce the digit count */
{
   int d, odigits;

   for (d=odigits=0; d<idigits; ++d)
      if ((to[d] = from[d]) != 0)
         odigits = d+1;

   return odigits;
}

static int
uarb_inc(uarb num, int in_digits, png_int_32 add)
   /* This is a signed 32-bit add, except that to avoid overflow the value added
    * or subtracted must be no more than 2^31-65536.  A negative result
    * indicates a negative number (which is an error below).  The size of
    * 'num' should be max(in_digits+1,2) for arbitrary 'add' but can be just
    * in_digits+1 if add is known to be in the range -65535..65535.
    */
{
   FIX_GCC int out_digits = 0;

   while (out_digits < in_digits)
   {
      add += num[out_digits];
      num[out_digits++] = (png_uint_16)(add & 0xffff);
      add >>= 16;
   }

   while (add != 0 && add != (-1))
   {
      num[out_digits++] = (png_uint_16)(add & 0xffff);
      add >>= 16;
   }

   if (add == 0)
   {
      while (out_digits > 0 && num[out_digits-1] == 0)
         --out_digits;
      return out_digits; /* may be 0 */
   }

   else /* negative result */
   {
      while (out_digits > 1 && num[out_digits-1] == 0xffff)
         --out_digits;

      return -out_digits;
   }
}

static int
uarb_add32(uarb num, int in_digits, png_uint_32 add)
   /* As above but this works with any 32-bit value and only does 'add' */
{
   if (in_digits > 0)
   {
      in_digits = uarb_inc(num, in_digits, add & 0xffff);
      return uarb_inc(num+1, in_digits-1, add >> 16)+1;
   }

   return uarb_set(num, add);
}

static int
uarb_mult_digit(uarb acc, int a_digits, uarb num, FIX_GCC int n_digits,
   png_uint_16 val)
   /* Primitive one-digit multiply - 'val' must be 0..65535. Note that this
    * primitive is a multiply and accumulate - the result of *num * val is added
    * to *acc.
    *
    * This is a one-digit multiply, so the product may be up to one digit longer
    * than 'num', however the add to 'acc' means that the caller must ensure
    * that 'acc' is at least one digit longer than this *and* at least one digit
    * longer than the current length of 'acc'.  (Or the caller must otherwise
    * ensure 'adigits' is adequate from knowledge of the values.)
    */
{
   /* The digits in *acc, *num and val are in the range 0..65535, so the
    * result below is at most (65535*65535)+2*65635 = 65535*(65535+2), which is
    * exactly 0xffffffff.
    */
   if (val > 0 && n_digits > 0) /* Else the product is 0 */
   {
      png_uint_32 carry = 0;
      int out_digits = 0;

      while (out_digits < n_digits || carry > 0)
      {
         if (out_digits < a_digits)
            carry += acc[out_digits];

         if (out_digits < n_digits)
            carry += (png_uint_32)num[out_digits] * val;

         acc[out_digits++] = (png_uint_16)(carry & 0xffff);
         carry >>= 16;
      }

      /* So carry is 0 and all the input digits have been consumed. This means
       * that it is possible to skip any remaining digits in acc.
       */
      if (out_digits > a_digits)
         return out_digits;
   }

   return a_digits;
}

static int
uarb_mult32(uarb acc, int a_digits, uarb num, int n_digits, png_uint_32 val)
   /* calculate acc += num * val, 'val' may be any 32-bit value, 'acc' and 'num'
    * may be any value, returns the number of digits in 'acc'.
    */
{
   if (n_digits > 0 && val > 0)
   {
      a_digits = uarb_mult_digit(acc, a_digits, num, n_digits,
         (png_uint_16)(val & 0xffff));

      val >>= 16;
      if (val > 0)
         a_digits = uarb_mult_digit(acc+1, a_digits-1, num, n_digits,
            (png_uint_16)val) + 1;

      /* Because n_digits and val are >0 the following must be true: */
      assert(a_digits > 0);
   }

   return a_digits;
}

static int
uarb_shift(uarb inout, int ndigits, unsigned int right_shift)
   /* Shift inout right by right_shift bits, right_shift must be in the range
    * 1..15
    */
{
   FIX_GCC int i = ndigits;
   png_uint_16 carry = 0;

   assert(right_shift >= 1 && right_shift <= 15);

   while (--i >= 0)
   {
      png_uint_16 temp = (png_uint_16)(carry | (inout[i] >> right_shift));

      /* Bottom bits to top bits of carry */
      carry = (png_uint_16)((inout[i] << (16-right_shift)) & 0xffff);

      inout[i] = temp;

      /* The shift may reduce ndigits */
      if (i == ndigits-1 && temp == 0)
         ndigits = i;
   }

   return ndigits;
}

static int
uarb_cmp(uarb a, int adigits, uarb b, int bdigits)
   /* Return -1/0/+1 according as a<b/a==b/a>b */
{
   if (adigits < bdigits)
      return -1;

   if (adigits > bdigits)
      return 1;

   while (adigits-- > 0)
      if (a[adigits] < b[adigits])
         return -1;

      else if (a[adigits] > b[adigits])
         return 1;

   return 0;
}

#if 0 /*UNUSED*/
static int
uarb_eq32(uarb num, int digits, png_uint_32 val)
   /* Return true if the uarb is equal to 'val' */
{
   switch (digits)
   {
      case 0:  return val == 0;
      case 1:  return val == num[0];
      case 2:  return (val & 0xffff) == num[0] && (val >> 16) == num[1];
      default: return 0;
   }
}
#endif

static void
uarb_printx(uarb num, int digits, FILE *out)
   /* Print 'num' as a hexadecimal number (easier than decimal!) */
{
   while (digits > 0)
      if (num[--digits] > 0)
      {
         fprintf(out, "0x%x", num[digits]);

         while (digits > 0)
            fprintf(out, "%.4x", num[--digits]);
      }

      else if (digits == 0) /* the number is 0 */
         fputs("0x0", out);
}

static void
uarb_print(uarb num, int digits, FILE *out)
   /* Prints 'num' as a decimal if it will fit in an unsigned long, else as a
    * hexadecimal number.  Notice that the results vary for images over 4GByte
    * in a system dependent way, and the hexadecimal form doesn't work very well
    * in awk script input.
    *
    *
    * TODO: write uarb_div10
    */
{
   if (digits * sizeof (udigit) > sizeof (unsigned long))
      uarb_printx(num, digits, out);

   else
   {
      unsigned long n = 0;

      while (digits > 0)
         n = (n << 16) + num[--digits];

      fprintf(out, "%lu", n);
   }
}

/* Generate random bytes.  This uses a boring repeatable algorithm and it
 * is implemented here so that it gives the same set of numbers on every
 * architecture.  It's a linear congruential generator (Knuth or Sedgewick
 * "Algorithms") but it comes from the 'feedback taps' table in Horowitz and
 * Hill, "The Art of Electronics" (Pseudo-Random Bit Sequences and Noise
 * Generation.)
 *
 * (Copied from contrib/libtests/pngvalid.c)
 */
static void
make_random_bytes(png_uint_32* seed, void* pv, size_t size)
{
   png_uint_32 u0 = seed[0], u1 = seed[1];
   png_bytep bytes = voidcast(png_bytep, pv);

   /* There are thirty-three bits; the next bit in the sequence is bit-33 XOR
    * bit-20.  The top 1 bit is in u1, the bottom 32 are in u0.
    */
   size_t i;
   for (i=0; i<size; ++i)
   {
      /* First generate 8 new bits then shift them in at the end. */
      png_uint_32 u = ((u0 >> (20-8)) ^ ((u1 << 7) | (u0 >> (32-7)))) & 0xff;
      u1 <<= 8;
      u1 |= u0 >> 24;
      u0 <<= 8;
      u0 |= u;
      *bytes++ = (png_byte)u;
   }

   seed[0] = u0;
   seed[1] = u1;
}

/* Clear an object to a random value. */
static void
clear(void *pv, size_t size)
{
   static png_uint_32 clear_seed[2] = { 0x12345678, 0x9abcdef0 };
   make_random_bytes(clear_seed, pv, size);
}

#define CLEAR(object) clear(&(object), sizeof (object))

/* Copied from unreleased 1.7 code.
 *
 * CRC checking uses a local pre-built implementation of the Ethernet CRC32.
 * This is to avoid a function call to the zlib DLL and to optimize the
 * byte-by-byte case.
 */
static png_uint_32 crc_table[256] =
{
   0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419,
   0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832, 0x79dcb8a4,
   0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07,
   0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de,
   0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7, 0x136c9856,
   0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9,
   0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e, 0xd56041e4,
   0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
   0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3,
   0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac, 0x51de003a,
   0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599,
   0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924,
   0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d, 0x76dc4190,
   0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f,
   0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934, 0x9609a88e,
   0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
   0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed,
   0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6, 0x12b7e950,
   0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3,
   0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2,
   0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb, 0x4369e96a,
   0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5,
   0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa, 0xbe0b1010,
   0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
   0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17,
   0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320, 0x9abfb3b6,
   0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615,
   0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8,
   0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1, 0xf00f9344,
   0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb,
   0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0, 0x10da7a5a,
   0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
   0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1,
   0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda, 0xaf0a1b4c,
   0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef,
   0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236,
   0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f, 0xc5ba3bbe,
   0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31,
   0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226, 0x756aa39c,
   0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
   0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b,
   0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4, 0xf1d4e242,
   0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1,
   0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c,
   0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45, 0xa00ae278,
   0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7,
   0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc, 0x40df0b66,
   0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
   0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605,
   0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e, 0xc4614ab8,
   0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b,
   0x2d02ef8d
};

/* The CRC calculated here *IS* conditioned, the corresponding value used by
 * zlib and the result value is obtained by XORing with CRC_INIT, which is also
 * the first value that must be passed in (for the first byte) to crc_one_byte.
 */
#define CRC_INIT 0xffffffff

static png_uint_32
crc_one_byte(png_uint_32 crc, int b)
{
   return crc_table[(crc ^ b) & 0xff] ^ (crc >> 8);
}

static png_uint_32
crc_init_4(png_uint_32 value)
{
   /* This is an alternative to the algorithm used in zlib, which requires four
    * separate tables to parallelize the four byte operations, it only works for
    * a CRC of the first four bytes of the stream, but this is what happens in
    * the parser below where length+chunk-name is read and chunk-name used to
    * initialize the CRC.  Notice that the calculation here avoids repeated
    * conditioning (xor with 0xffffffff) by storing the conditioned value.
    */
   png_uint_32 crc = crc_table[(~value >> 24)] ^ 0xffffff;

   crc = crc_table[(crc ^ (value >> 16)) & 0xff] ^ (crc >> 8);
   crc = crc_table[(crc ^ (value >> 8)) & 0xff] ^ (crc >> 8);
   return crc_table[(crc ^ value) & 0xff] ^ (crc >> 8);
}

static int
chunk_type_valid(png_uint_32 c)
   /* Bit whacking approach to chunk name validation that is intended to avoid
    * branches.  The cost is that it uses a lot of 32-bit constants, which might
    * be bad on some architectures.
    */
{
   png_uint_32 t;

   /* Remove bit 5 from all but the reserved byte; this means every
    * 8-bit unit must be in the range 65-90 to be valid.  So bit 5
    * must be zero, bit 6 must be set and bit 7 zero.
    */
   c &= ~PNG_U32(32,32,0,32);
   t = (c & ~0x1f1f1f1f) ^ 0x40404040;

   /* Subtract 65 for each 8-bit quantity, this must not overflow
    * and each byte must then be in the range 0-25.
    */
   c -= PNG_U32(65,65,65,65);
   t |=c ;

   /* Subtract 26, handling the overflow which should set the top
    * three bits of each byte.
    */
   c -= PNG_U32(25,25,25,26);
   t |= ~c;

   return (t & 0xe0e0e0e0) == 0;
}

/**************************** CONTROL INFORMATION *****************************/

/* Information about a sequence of IDAT chunks, the chunks have been re-synced
 * using sync_stream below and the new lengths are recorded here.  Because the
 * number of chunks is unlimited this is handled using a linked list of these
 * structures.
 */
struct IDAT_list
{
   struct IDAT_list *next;     /* Linked list */
   unsigned int      length;   /* Actual length of the array below */
   unsigned int      count;    /* Number of entries that are valid */
#     define IDAT_INIT_LENGTH 16
   png_uint_32       lengths[IDAT_INIT_LENGTH];
};

static void
IDAT_list_init(struct IDAT_list *list)
{
   CLEAR(*list);

   list->next = NULL;
   list->length = IDAT_INIT_LENGTH;
}

static size_t
IDAT_list_size(struct IDAT_list *list, unsigned int length)
   /* Return the size in bytes of an IDAT_list of the given length. */
{
   if (list != NULL)
      length = list->length;

   return sizeof *list - sizeof list->lengths +
      length * sizeof list->lengths[0];
}

static void
IDAT_list_end(struct IDAT_list *IDAT_list)
{
   struct IDAT_list *list = IDAT_list->next;

   CLEAR(*IDAT_list);

   while (list != NULL)
   {
      struct IDAT_list *next = list->next;

      clear(list, IDAT_list_size(list, 0));
      free(list);
      list = next;
   }
}

static struct IDAT_list *
IDAT_list_extend(struct IDAT_list *tail)
{
   /* Use the previous cached value if available. */
   struct IDAT_list *next = tail->next;

   if (next == NULL)
   {
      /* Insert a new, malloc'ed, block of IDAT information buffers, this
       * one twice as large as the previous one:
       */
      unsigned int length = 2 * tail->length;

      if (length < tail->length) /* arithmetic overflow */
         length = tail->length;

      next = voidcast(IDAT_list*, malloc(IDAT_list_size(NULL, length)));
      CLEAR(*next);

      /* The caller must handle this: */
      if (next == NULL)
         return NULL;

      next->next = NULL;
      next->length = length;
      tail->next = next;
   }

   return next;
}

/* GLOBAL CONTROL STRUCTURE */
struct global
{
   /* PUBLIC GLOBAL VARIABLES: OWNER INITIALIZE */
   unsigned int   errors        :1; /* print file errors to stderr */
   unsigned int   warnings      :1; /* print libpng warnings to stderr */
   unsigned int   optimize_zlib :1; /* Run optimization search */
   unsigned int   quiet         :2; /* don't output summaries */
   unsigned int   verbose       :3; /* various internal tracking */
   unsigned int   skip          :3; /* Non-critical chunks to skip */
#     define SKIP_NONE      0
#     define SKIP_BAD_CRC   1    /* Chunks with a bad CRC */
#     define SKIP_UNSAFE    2    /* Chunks not safe to copy */
#     define SKIP_UNUSED    3    /* Chunks not used by libpng */
#     define SKIP_TRANSFORM 4    /* Chunks only used in transforms */
#     define SKIP_COLOR     5    /* Everything but tRNS, sBIT, gAMA and sRGB */
#     define SKIP_ALL       6    /* Everything but tRNS and sBIT */

   png_uint_32    idat_max;         /* 0 to perform no re-chunking */

   int            status_code;      /* Accumulated status code */
#     define TOO_FAR_BACK   0x01 /* found a too-far-back error */
#     define CRC_ERROR      0x02 /* fixed an invalid CRC */
#     define STREAM_ERROR   0x04 /* damaged PNG stream (may be fixable) */
#     define TRUNCATED      0x08 /* truncated but still readable */
#     define FILE_ERROR     0x10 /* could not read the file */
#     define WRITE_ERROR    0x20 /* write error (this terminates the read) */
#     define INTERNAL_ERROR 0x40 /* internal limits/errors encountered */

   /* PUBLIC GLOBAL VARIABLES: USED INTERNALLY BY IDAT READ CODE */
   struct IDAT_list idat_cache;  /* Cache of file IDAT information buffers */
      /* The structure is shared across all uses of this global control
       * structure to avoid reallocation between IDAT streams.
       */
};

static int
global_end(struct global *global)
{

   int rc;

   IDAT_list_end(&global->idat_cache);
   rc = global->status_code;
   CLEAR(*global);
   return rc;
}

static void
global_init(struct global *global)
   /* Call this once (and only once) to initialize the control */
{
   CLEAR(*global);

   /* Globals */
   global->errors        = 0;
   global->warnings      = 0;
   global->quiet         = 0;
   global->verbose       = 0;
   global->idat_max      = 0;         /* no re-chunking of IDAT */
   global->optimize_zlib = 0;
   global->skip          = SKIP_NONE;
   global->status_code   = 0;

   IDAT_list_init(&global->idat_cache);
}

static int
skip_chunk_type(const struct global *global, png_uint_32 type)
   /* Return true if this chunk is to be skipped according to the --strip
    * option.  This code needs to recognize all known ancillary chunks in order
    * to handle the --strip=unsafe option.
    */
{
   /* Never strip critical chunks: */
   if (CRITICAL(type))
      return 0;

   switch (type)
   {
      /* Chunks that are treated as, effectively, critical because they affect
       * correct interpretation of the pixel values:
       */
      case png_tRNS: case png_sBIT:
         return 0;

      /* Chunks that specify gamma encoding which should therefore only be
       * removed if the user insists:
       */
      case png_gAMA: case png_sRGB:
         if (global->skip >= SKIP_ALL)
            return 1;
         return 0;

      /* Chunks that affect color interpretation - not used by libpng and rarely
       * used by applications, but technically still required for correct
       * interpretation of the image data:
       */
      case png_cHRM: case png_iCCP:
         if (global->skip >= SKIP_COLOR)
            return 1;
         return 0;

      /* Other chunks that are used by libpng in image transformations (as
       * opposed to known chunks that have get/set APIs but are not otherwise
       * used.)
       */
      case png_bKGD:
         if (global->skip >= SKIP_TRANSFORM)
            return 1;
         return 0;

      /* All other chunks that libpng knows about and affect neither image
       * interpretation nor libpng transforms - chunks that are effectively
       * unused by libpng even though libpng might recognize and store them.
       */
      case png_fRAc: case png_gIFg: case png_gIFt: case png_gIFx: case png_hIST:
      case png_iTXt: case png_oFFs: case png_pCAL: case png_pHYs: case png_sCAL:
      case png_sPLT: case png_sTER: case png_tEXt: case png_tIME: case png_zTXt:
         if (global->skip >= SKIP_UNUSED)
            return 1;
         return 0;

      /* Chunks that libpng does not know about (notice that this depends on the
       * list above including all known chunks!)  The decision here depends on
       * whether the safe-to-copy bit is set in the chunk type.
       */
      default:
         if (SAFE_TO_COPY(type))
         {
            if (global->skip >= SKIP_UNUSED) /* as above */
               return 1;
         }

         else if (global->skip >= SKIP_UNSAFE)
            return 1;

         return 0;
   }
}

/* PER-FILE CONTROL STRUCTURE */
struct chunk;
struct IDAT;
struct file
{
   /* ANCESTORS */
   struct global *global;

   /* PUBLIC PER-FILE VARIABLES: CALLER INITIALIZE */
   const char *   file_name;
   const char *   out_name;      /* Name of output file (if required) */

   /* PUBLIC PER-FILE VARIABLES: SET BY PNG READ CODE */
   /* File specific result codes */
   int            status_code;   /* Set to a bit mask of the following: */
   int            read_errno;    /* Records a read error errno */
   int            write_errno;   /* Records a write error errno */

   /* IHDR information */
   png_uint_32    width;
   png_uint_32    height;
   png_byte       bit_depth;
   png_byte       color_type;
   png_byte       compression_method;
   png_byte       filter_method;
   png_byte       interlace_method;

   udigit         image_bytes[5];
   int            image_digits;

   /* PROTECTED PER-FILE VARIABLES: USED BY THE READ CODE */
   FILE *         file;          /* Original PNG file */
   FILE *         out;           /* If a new one is being written */
   jmp_buf        jmpbuf;        /* Set while reading a PNG */

   /* PROTECTED CHUNK SPECIFIC VARIABLES: USED BY CHUNK CODE */
   /* The following variables are used during reading to record the length, type
    * and data position of the *next* chunk or, right at the start, the
    * signature (in length,type).
    *
    * When a chunk control structure is instantiated these values are copied
    * into the structure and can then be overritten with the data for the next
    * chunk.
    */
   fpos_t         data_pos;      /* Position of first byte of chunk data */
   png_uint_32    length;        /* First word (length or signature start) */
   png_uint_32    type;          /* Second word (type or signature end) */
   png_uint_32    crc;           /* Running chunk CRC (used by read_chunk) */

   /* These counts are maintained by the read and write routines below and are
    * reset by the chunk handling code.  They record the total number of bytes
    * read or written for the chunk, including the header (length,type) bytes.
    */
   png_uint_32    read_count;    /* Count of bytes read (in the chunk) */
   png_uint_32    write_count;   /* Count of bytes written (in the chunk) */
   int            state;         /* As defined here: */
#     define STATE_SIGNATURE  0  /* The signature is being written */
#     define STATE_CHUNKS     1  /* Non-IDAT chunks are being written */
#     define STATE_IDAT       2  /* An IDAT stream is being written */

   /* Two pointers used to enable clean-up in the event of fatal errors and to
    * hold state about the parser process (only one of each at present.)
    */
   struct chunk * chunk;
   struct IDAT *  idat;

   /* Interface to allocate a new chunk or IDAT control structure.  The result
    * is returned by setting one or other of the above variables.  Note that the
    * relevant initializer is called by the allocator function.  The alloc_ptr
    * is used only by the implementation of the allocate function.
    */
   void *         alloc_ptr;
   void         (*alloc)(struct file*,int idat);
                                  /* idat: allocate IDAT not chunk */
};

/* Valid longjmp (stop) codes are: */
#define LIBPNG_WARNING_CODE   1 /* generic png_error */
#define LIBPNG_ERROR_CODE     2 /* generic png_error */
#define ZLIB_ERROR_CODE       3 /* generic zlib error */
#define INVALID_ERROR_CODE    4 /* detected an invalid PNG */
#define READ_ERROR_CODE       5 /* read failed */
#define WRITE_ERROR_CODE      6 /* error in write */
#define UNEXPECTED_ERROR_CODE 7 /* unexpected (internal?) error */

static void
emit_string(const char *str, FILE *out)
   /* Print a string with spaces replaced by '_' and non-printing characters by
    * an octal escape.
    */
{
   for (; *str; ++str)
      if (isgraph(UCHAR_MAX & *str))
         putc(*str, out);

      else if (isspace(UCHAR_MAX & *str))
         putc('_', out);

      else
         fprintf(out, "\\%.3o", *str);
}

static const char *
strcode(int code)
{
   switch (code)
   {
      case LIBPNG_WARNING_CODE:   return "warning";
      case LIBPNG_ERROR_CODE:     return "libpng";
      case ZLIB_ERROR_CODE:       return "zlib";
      case INVALID_ERROR_CODE:    return "invalid";
      case READ_ERROR_CODE:       return "read";
      case WRITE_ERROR_CODE:      return "write";
      case UNEXPECTED_ERROR_CODE: return "unexpected";
      default:                    return "INVALID";
   }
}

static void
emit_error(struct file *file, int code, const char *what)
   /* Generic error message routine, takes a 'stop' code but can be used
    * elsewhere.  Always outputs a message.
    */
{
   const char *reason;
   int err = 0;

   switch (code)
   {
      case LIBPNG_WARNING_CODE:   reason = "libpng warning:"; break;
      case LIBPNG_ERROR_CODE:     reason = "libpng error:"; break;
      case ZLIB_ERROR_CODE:       reason = "zlib error:"; break;
      case INVALID_ERROR_CODE:    reason = "invalid"; break;
      case READ_ERROR_CODE:       reason = "read failure:";
                                  err = file->read_errno;
                                  break;
      case WRITE_ERROR_CODE:      reason = "write error";
                                  err = file->write_errno;
                                  break;
      case UNEXPECTED_ERROR_CODE: reason = "unexpected error:";
                                  err = file->read_errno;
                                  if (err == 0)
                                     err = file->write_errno;
                                  break;
      default:                    reason = "INVALID (internal error):"; break;
   }

   if (err != 0)
      fprintf(stderr, "%s: %s %s [%s]\n", file->file_name, reason, what,
         strerror(err));

   else
      fprintf(stderr, "%s: %s %s\n", file->file_name, reason, what);
}

static void chunk_end(struct chunk **);
static void IDAT_end(struct IDAT **);

static int
file_end(struct file *file)
{
   int rc;

   /* If either of the chunk pointers are set end them here, the IDAT structure
    * must be deallocated first as it may deallocate the chunk structure.
    */
   if (file->idat != NULL)
      IDAT_end(&file->idat);

   if (file->chunk != NULL)
      chunk_end(&file->chunk);

   rc = file->status_code;

   if (file->file != NULL)
      (void)fclose(file->file);

   if (file->out != NULL)
   {
      /* NOTE: this is bitwise |, all the following functions must execute and
       * must succeed.
       */
      if (ferror(file->out) | fflush(file->out) | fclose(file->out))
      {
         perror(file->out_name);
         emit_error(file, READ_ERROR_CODE, "output write error");
         rc |= WRITE_ERROR;
      }
   }

   /* Accumulate the result codes */
   file->global->status_code |= rc;

   CLEAR(*file);

   return rc; /* status code: non-zero on read or write error */
}

static int
file_init(struct file *file, struct global *global, const char *file_name,
   const char *out_name, void *alloc_ptr, void (*alloc)(struct file*,int))
   /* Initialize a file control structure.  This will open the given files as
    * well.  The status code returned is 0 on success, non zero (using the flags
    * above) on a file open error.
    */
{
   CLEAR(*file);
   file->global = global;

   file->file_name = file_name;
   file->out_name = out_name;
   file->status_code = 0;
   file->read_errno = 0;
   file->write_errno = 0;

   file->file = NULL;
   file->out = NULL;
   /* jmpbuf is garbage: must be set by read_png */

   file->read_count = 0;
   file->state = STATE_SIGNATURE;

   file->chunk = NULL;
   file->idat = NULL;

   file->alloc_ptr = alloc_ptr;
   file->alloc = alloc;

   /* Open the files: */
   assert(file_name != NULL);
   file->file = fopen(file_name, "rb");

   if (file->file == NULL)
   {
      file->read_errno = errno;
      file->status_code |= FILE_ERROR;
      /* Always output: please give a readable file! */
      perror(file_name);
      return FILE_ERROR;
   }

   if (out_name != NULL)
   {
      file->out = fopen(out_name, "wb");

      if (file->out == NULL)
      {
         file->write_errno = errno;
         file->status_code |= WRITE_ERROR;
         perror(out_name);
         return WRITE_ERROR;
      }
   }

   return 0;
}

static void
log_error(struct file *file, int code, const char *what)
   /* Like emit_error but checks the global 'errors' flag */
{
   if (file->global->errors)
      emit_error(file, code, what);
}

static char
type_char(png_uint_32 v)
{
   /* In fact because chunk::chunk_type is validated prior to any call to this
    * function it will always return a-zA-Z, but the extra codes are just there
    * to help in finding internal (programming) errors.  Note that the code only
    * ever considers the low 7 bits of the value (so it is not necessary for the
    * type_name function to mask of the byte.)
    */
   if (v & 32)
      return "!abcdefghijklmnopqrstuvwxyz56789"[(v-96)&31];

   else
      return "@ABCDEFGHIJKLMNOPQRSTUVWXYZ01234"[(v-64)&31];
}

static void
type_name(png_uint_32 type, FILE *out)
{
   putc(type_char(type >> 24), out);
   putc(type_char(type >> 16), out);
   putc(type_char(type >>  8), out);
   putc(type_char(type      ), out);
}

static void
type_sep(FILE *out)
{
   putc(':', out);
   putc(' ', out);
}

static png_uint_32 current_type(struct file *file, int code);

PNG_NORETURN static void
stop(struct file *file, int code, const char *what)
   /* Return control when a PNG file cannot be read. This outputs an 'ERR'
    * summary line too.
    */
{
   log_error(file, code, what);

   /* The chunk being read is typically identified by file->chunk or, if this is
    * NULL, by file->type.  This may be wrong if libpng reads ahead, but this
    * only happens with IDAT where libpng reads the header then jumps around
    * finding errors in the previous chunks.  We know that is happening because
    * we are at the start of the IDAT (i.e. no IDAT data has yet been written.)
    *
    * SUMMARY FORMAT (stop):
    *
    * IDAT ERR status code read-errno write-errno message file
    *
    * 'uncompressed' will be 0 if there was a problem in the IHDR.  The errno
    * values are emit_string(strerror(errno)).
    */
   if (file->global->quiet < 2) /* need two quiets to stop this. */
   {
      png_uint_32 type;

      if (file->chunk != NULL)
         type = current_type(file, code); /* Gropes in struct chunk and IDAT */

      else
         type = file->type;

      if (type)
         type_name(type, stdout);

      else /* magic: an IDAT header, produces bogons for too many IDATs */
         fputs("HEAD", stdout); /* not a registered chunk! */

      printf(" ERR %.2x %s ", file->status_code, strcode(code));
      /* This only works one strerror at a time, because of the way strerror is
       * implemented.
       */
      emit_string(strerror(file->read_errno), stdout);
      putc(' ', stdout);
      emit_string(strerror(file->write_errno), stdout);
      putc(' ', stdout);
      emit_string(what, stdout);
      putc(' ', stdout);
      fputs(file->file_name, stdout);
      putc('\n', stdout);
   }

   file->status_code |= FILE_ERROR;
   longjmp(file->jmpbuf, code);
}

PNG_NORETURN static void
stop_invalid(struct file *file, const char *what)
{
   stop(file, INVALID_ERROR_CODE, what);
}

static void
type_message(struct file *file, png_uint_32 type, const char *what)
   /* Error message for a chunk; the chunk name comes from 'type' */
{
   if (file->global->errors)
   {
      fputs(file->file_name, stderr);
      type_sep(stderr);
      type_name(type, stderr);
      type_sep(stderr);
      fputs(what, stderr);
      putc('\n', stderr);
   }
}

/* Input file positioning - we jump around in the input file while reading
 * stuff, these wrappers deal with the error handling.
 */
static void
file_getpos(struct file *file, fpos_t *pos)
{
   if (fgetpos(file->file, pos))
   {
      /* This is unexpected, so perror it */
      perror(file->file_name);
      stop(file, READ_ERROR_CODE, "fgetpos");
   }
}

static void
file_setpos(struct file *file, const fpos_t *pos)
{
   if (fsetpos(file->file, pos))
   {
      perror(file->file_name);
      stop(file, READ_ERROR_CODE, "fsetpos");
   }
}

static void
getpos(struct file *file)
   /* Get the current position and store it in 'data_pos'.  The corresponding
    * setpos() function is chunk specific because it uses the copy of the
    * position for the specific chunk.
    */
{
   file_getpos(file, &file->data_pos);
}


/* Read utility - read a single byte, returns a value in the range 0..255 or EOF
 * on a read error.  In the latter case status_code and read_errno are updated
 * appropriately.
 */
static int
read_byte(struct file *file)
{
   int ch = getc(file->file);

   if (ch >= 0 && ch <= 255)
   {
      ++(file->read_count);
      return ch;
   }

   else if (ch != EOF)
   {
      file->status_code |= INTERNAL_ERROR;
      file->read_errno = ERANGE; /* out of range character */

      /* This is very unexpected; an error message is always output: */
      emit_error(file, UNEXPECTED_ERROR_CODE, "file read");
   }

#  ifdef EINTR
      else if (errno == EINTR) /* Interrupted, try again */
      {
         errno = 0;
         return read_byte(file);
      }
#  endif

   else
   {
      /* An error, it doesn't really matter what the error is but it gets
       * recorded anyway.
       */
      if (ferror(file->file))
         file->read_errno = errno;

      else if (feof(file->file))
         file->read_errno = 0; /* I.e. a regular EOF, no error */

      else /* unexpected */
         file->read_errno = EDOM;
   }

   /* 'TRUNCATED' is used for all cases of failure to read a byte, because of
    * the way libpng works a byte read is never attempted unless the byte is
    * expected to be there, so EOF should not occur.
    */
   file->status_code |= TRUNCATED;
   return EOF;
}

static png_byte
reread_byte(struct file *file)
   /* Read a byte when an error is not expected to happen because the byte has
    * been read before without error.
    */
{
   int ch = getc(file->file);

   if (errno != 0)
      file->read_errno = errno;

   if (ch < 0 || ch > 255)
      stop(file, UNEXPECTED_ERROR_CODE, "reread");

   return (png_byte)ch;
}

static png_uint_32
reread_4(struct file *file)
   /* The same but for a four byte quantity */
{
   png_uint_32 result = 0;
   int i = 0;

   while (++i <= 4)
      result = (result << 8) + reread_byte(file);

   return result;
}

static void
skip_12(struct file *file)
   /* Skip exactly 12 bytes in the input stream - used to skip a CRC and chunk
    * header that has been read before.
    */
{
   /* Since the chunks were read before this shouldn't fail: */
   if (fseek(file->file, 12, SEEK_CUR) != 0)
   {
      if (errno != 0)
         file->read_errno = errno;

      stop(file, UNEXPECTED_ERROR_CODE, "reskip");
   }
}

static void
write_byte(struct file *file, int b)
   /* Write one byte to the output - this causes a fatal error if the write
    * fails and the read of this PNG file immediately terminates.  Just
    * increments the write count if there is no output file.
    */
{
   if (file->out != NULL)
   {
      if (putc(b, file->out) != b)
      {
         file->write_errno = errno;
         file->status_code |= WRITE_ERROR;
         stop(file, WRITE_ERROR_CODE, "write byte");
      }
   }

   ++(file->write_count);
}

/* Derivatives of the read/write functions. */
static unsigned int
read_4(struct file *file, png_uint_32 *pu)
   /* Read four bytes, returns the number of bytes read successfully and, if all
    * four bytes are read, assigns the result to *pu.
    */
{
   unsigned int i = 0;
   png_uint_32 val = 0;

   do
   {
      int ch = read_byte(file);

      if (ch == EOF)
         return i;

      val = (val << 8) + ch;
   } while (++i < 4);

   *pu = val;
   return i;
}

/* CRC handling - read but calculate the CRC while doing so. */
static int
crc_read_many(struct file *file, png_uint_32 length)
   /* Reads 'length' bytes and updates the CRC, returns true on success, false
    * if the input is truncated.
    */
{
   if (length > 0)
   {
      png_uint_32 crc = file->crc;

      do
      {
         int ch = read_byte(file);

         if (ch == EOF)
            return 0; /* Truncated */

         crc = crc_one_byte(crc, ch);
      }
      while (--length > 0);

      file->crc = crc;
   }

   return 1; /* OK */
}

static int
calc_image_size(struct file *file)
   /* Fill in the image_bytes field given the IHDR information, calls stop on
    * error.
    */
{
   png_uint_16 pd = file->bit_depth;

   switch (file->color_type)
   {
      default:
         stop_invalid(file, "IHDR: colour type");

      invalid_bit_depth:
         stop_invalid(file, "IHDR: bit depth");

      case 0: /* g */
         if (pd != 1 && pd != 2 && pd != 4 && pd != 8 && pd != 16)
            goto invalid_bit_depth;
         break;

      case 3:
         if (pd != 1 && pd != 2 && pd != 4 && pd != 8)
            goto invalid_bit_depth;
         break;

      case 2: /* rgb */
         if (pd != 8 && pd != 16)
            goto invalid_bit_depth;

         pd = (png_uint_16)(pd * 3);
         break;

      case 4: /* ga */
         if (pd != 8 && pd != 16)
            goto invalid_bit_depth;

         pd = (png_uint_16)(pd * 2);
         break;

      case 6: /* rgba */
         if (pd != 8 && pd != 16)
            goto invalid_bit_depth;

         pd = (png_uint_16)(pd * 4);
         break;
   }

   if (file->width < 1 || file->width > 0x7fffffff)
      stop_invalid(file, "IHDR: width");

   else if (file->height < 1 || file->height > 0x7fffffff)
      stop_invalid(file, "IHDR: height");

   else if (file->compression_method != 0)
      stop_invalid(file, "IHDR: compression method");

   else if (file->filter_method != 0)
      stop_invalid(file, "IHDR: filter method");

   else switch (file->interlace_method)
   {
      case PNG_INTERLACE_ADAM7:
         /* Interlacing makes the image larger because of the replication of
          * both the filter byte and the padding to a byte boundary.
          */
         {
            int pass;
            int image_digits = 0;
            udigit row_width[2], row_bytes[3];

            for (pass=0; pass<=6; ++pass)
            {
               png_uint_32 pw = PNG_PASS_COLS(file->width, pass);

               if (pw > 0)
               {
                  int  digits;

                  /* calculate 1+((pw*pd+7)>>3) in row_bytes */
                  digits = uarb_mult_digit(row_bytes, uarb_set(row_bytes, 7),
                     row_width, uarb_set(row_width, pw), pd);
                  digits = uarb_shift(row_bytes, digits, 3);
                  digits = uarb_inc(row_bytes, digits, 1);

                  /* Add row_bytes * pass-height to the file image_bytes field
                   */
                  image_digits = uarb_mult32(file->image_bytes, image_digits,
                     row_bytes, digits,
                     PNG_PASS_ROWS(file->height, pass));
               }
            }

            file->image_digits = image_digits;
         }
         break;

      case PNG_INTERLACE_NONE:
         {
            int  digits;
            udigit row_width[2], row_bytes[3];

            /* As above, but use image_width in place of the pass width: */
            digits = uarb_mult_digit(row_bytes, uarb_set(row_bytes, 7),
               row_width, uarb_set(row_width, file->width), pd);
            digits = uarb_shift(row_bytes, digits, 3);
            digits = uarb_inc(row_bytes, digits, 1);

            /* Set row_bytes * image-height to the file image_bytes field */
            file->image_digits = uarb_mult32(file->image_bytes, 0,
               row_bytes, digits, file->height);
         }
         break;

      default:
         stop_invalid(file, "IHDR: interlace method");
   }

   assert(file->image_digits >= 1 && file->image_digits <= 5);
   return 1;
}

/* PER-CHUNK CONTROL STRUCTURE
 * This structure is instantiated for each chunk, except for the IDAT chunks
 * where one chunk control structure is used for the whole of a single stream of
 * IDAT chunks (see the IDAT control structure below).
 */
struct chunk
{
   /* ANCESTORS */
   struct file *         file;
   struct global *       global;

   /* PUBLIC IDAT INFORMATION: SET BY THE ZLIB CODE */
   udigit         uncompressed_bytes[5];
   int            uncompressed_digits;
   udigit         compressed_bytes[5];
   int            compressed_digits;

   /* PUBLIC PER-CHUNK INFORMATION: USED BY CHUNK READ CODE */
   /* This information is filled in by chunk_init from the data in the file
    * control structure, but chunk_length may be changed later.
    */
   fpos_t         chunk_data_pos;    /* Position of first byte of chunk data */
   png_uint_32    chunk_length;      /* From header (or modified below) */
   png_uint_32    chunk_type;        /* From header */

   /* PUBLIC PER-CHUNK INFORMATION: FOR THE CHUNK WRITE CODE */
   png_uint_32    write_crc;         /* Output CRC (may differ from read_crc) */
   png_uint_32    rewrite_offset;    /* Count of bytes before rewrite. */
   int            rewrite_length;    /* Number of bytes left to change */
   png_byte       rewrite_buffer[2]; /* Buffer of new byte values */
};

static void
chunk_message(struct chunk *chunk, const char *message)
{
   type_message(chunk->file, chunk->chunk_type, message);
}

static void
chunk_end(struct chunk **chunk_var)
{
   struct chunk *chunk = *chunk_var;

   *chunk_var = NULL;
   CLEAR(*chunk);
}

static void
chunk_init(struct chunk * const chunk, struct file * const file)
   /* When a chunk is initialized the file length/type/pos are copied into the
    * corresponding chunk fields and the new chunk is registered in the file
    * structure.  There can only be one chunk at a time.
    *
    * NOTE: this routine must onely be called from the file alloc routine!
    */
{
   assert(file->chunk == NULL);

   CLEAR(*chunk);

   chunk->file = file;
   chunk->global = file->global;

   chunk->chunk_data_pos = file->data_pos;
   chunk->chunk_length = file->length;
   chunk->chunk_type = file->type;

   /* Compresssed/uncompressed size information (from the zlib control structure
    * that is used to check the compressed data in a chunk.)
    */
   chunk->uncompressed_digits = 0;
   chunk->compressed_digits = 0;

   file->chunk = chunk;
}

static png_uint_32
current_type(struct file *file, int code)
   /* Guess the actual chunk type that causes a stop() */
{
   /* This may return png_IDAT for errors detected (late) in the header; that
    * includes any inter-chunk consistency check that libpng performs.  Assume
    * that if the chunk_type is png_IDAT and the file write count is 8 this is
    * what is happening.
    */
   if (file->chunk != NULL)
   {
      png_uint_32 type = file->chunk->chunk_type;

      /* This is probably wrong for the excess IDATs case, because then libpng
       * whines about too many of them (apparently in some cases erroneously)
       * when the header is read.
       */
      if (code <= LIBPNG_ERROR_CODE && type == png_IDAT &&
         file->write_count == 8)
         type = 0; /* magic */

      return type;
   }

   else
      return file->type;
}

static void
setpos(struct chunk *chunk)
   /* Reset the position to 'chunk_data_pos' - the start of the data for this
    * chunk.  As a side effect the read_count in the file is reset to 8, just
    * after the length/type header.
    */
{
   chunk->file->read_count = 8;
   file_setpos(chunk->file, &chunk->chunk_data_pos);
}

/* Specific chunk handling - called for each chunk header, all special chunk
 * processing is initiated in these functions.
 */
/* The next functions handle special processing for those chunks with LZ data,
 * the data is identified and checked for validity.  If there are problems which
 * cannot be corrected the routines return false, otherwise true (although
 * modification to the zlib header may be required.)
 *
 * The compressed data is in zlib format (RFC1950) and consequently has a
 * minimum length of 7 bytes.
 */
static int zlib_check(struct file *file, png_uint_32 offset);

static int
process_zTXt_iCCP(struct file *file)
   /* zTXt and iCCP have exactly the same form - keyword, null, compression
    * method then compressed data.
    */
{
   struct chunk *chunk = file->chunk;
   png_uint_32 length;
   png_uint_32 index = 0;

   assert(chunk != NULL && file->idat == NULL);
   length = chunk->chunk_length;
   setpos(chunk);

   while (length >= 9)
   {
      --length;
      ++index;
      if (reread_byte(file) == 0) /* keyword null terminator */
      {
         --length;
         ++index;
         (void)reread_byte(file); /* compression method */
         return zlib_check(file, index);
      }
   }

   chunk_message(chunk, "too short");
   return 0; /* skip */
}

static int
process_iTXt(struct file *file)
{
   /* Like zTXt but more fields. */
   struct chunk *chunk = file->chunk;
   png_uint_32 length;
   png_uint_32 index = 0;

   assert(chunk != NULL && file->idat == NULL);
   length = chunk->chunk_length;
   setpos(chunk);

   while (length >= 5)
   {
      --length;
      ++index;
      if (reread_byte(file) == 0) /* keyword null terminator */
      {
         --length;
         ++index;
         if (reread_byte(file) == 0) /* uncompressed text */
            return 1; /* nothing to check */

         --length;
         ++index;
         (void)reread_byte(file); /* compression method */

         /* Skip the language tag (null terminated). */
         while (length >= 9)
         {
            --length;
            ++index;
            if (reread_byte(file) == 0) /* terminator */
            {
               /* Skip the translated keyword */
               while (length >= 8)
               {
                  --length;
                  ++index;
                  if (reread_byte(file) == 0) /* terminator */
                     return zlib_check(file, index);
               }
            }
         }

         /* Ran out of bytes in the compressed case. */
         break;
      }
   }

   log_error(file, INVALID_ERROR_CODE, "iTXt chunk length");

   return 0; /* skip */
}

/* IDAT READ/WRITE CONTROL STRUCTURE */
struct IDAT
{
   /* ANCESTORS */
   struct file *         file;
   struct global *       global;

   /* PROTECTED IDAT INFORMATION: SET BY THE IDAT READ CODE */
   struct IDAT_list *idat_list_head; /* START of the list of IDAT information */
   struct IDAT_list *idat_list_tail; /* *END* of the list of IDAT information */

   /* PROTECTED IDAT INFORMATION: USED BY THE IDAT WRITE CODE */
   struct IDAT_list *idat_cur;       /* Current list entry */
   unsigned int      idat_count;     /* And the *current* index into the list */
   png_uint_32       idat_index;     /* Index of *next* input byte to write */
   png_uint_32       idat_length;    /* Cache of current chunk length */
};

/* NOTE: there is currently no IDAT_reset, so a stream cannot contain more than
 * one IDAT sequence (i.e. MNG is not supported).
 */

static void
IDAT_end(struct IDAT **idat_var)
{
   struct IDAT *idat = *idat_var;
   struct file *file = idat->file;

   *idat_var = NULL;

   CLEAR(*idat);

   assert(file->chunk != NULL);
   chunk_end(&file->chunk);

   /* Regardless of why the IDAT was killed set the state back to CHUNKS (it may
    * already be CHUNKS because the state isn't changed until process_IDAT
    * returns; a stop will cause IDAT_end to be entered in state CHUNKS!)
    */
   file->state = STATE_CHUNKS;
}

static void
IDAT_init(struct IDAT * const idat, struct file * const file)
   /* When the chunk is png_IDAT instantiate an IDAT control structure in place
    * of a chunk control structure.  The IDAT will instantiate a chunk control
    * structure using the file alloc routine.
    *
    * NOTE: this routine must only be called from the file alloc routine!
    */
{
   assert(file->chunk == NULL);
   assert(file->idat == NULL);

   CLEAR(*idat);

   idat->file = file;
   idat->global = file->global;

   /* Initialize the tail to the pre-allocated buffer and set the count to 0
    * (empty.)
    */
   idat->global->idat_cache.count = 0;
   idat->idat_list_head = idat->idat_list_tail = &idat->global->idat_cache;

   /* Now the chunk.  The allocator calls the initializer of the new chunk and
    * stores the result in file->chunk:
    */
   file->alloc(file, 0/*chunk*/);
   assert(file->chunk != NULL);

   /* And store this for cleanup (and to check for double alloc or failure to
    * free.)
    */
   file->idat = idat;
}

static png_uint_32
rechunk_length(struct IDAT *idat, int start)
   /* Return the length for the next IDAT chunk, taking into account
    * rechunking.
    */
{
   png_uint_32 len = idat->global->idat_max;

   if (len == 0) /* use original chunk lengths */
   {
      const struct IDAT_list *cur;
      unsigned int count;

      if (start)
         return idat->idat_length; /* use the cache */

      /* Otherwise rechunk_length is called at the end of a chunk for the length
       * of the next one.
       */
      cur = idat->idat_cur;
      count = idat->idat_count;

      assert(idat->idat_index == idat->idat_length &&
         idat->idat_length == cur->lengths[count]);

      /* Return length of the *next* chunk */
      if (++count < cur->count)
         return cur->lengths[count];

      /* End of this list */
      assert(cur != idat->idat_list_tail);
      cur = cur->next;
      assert(cur != NULL && cur->count > 0);
      return cur->lengths[0];
   }

   else /* rechunking */
   {
      /* The chunk size is the lesser of file->idat_max and the number
       * of remaining bytes.
       */
      png_uint_32 have = idat->idat_length - idat->idat_index;

      if (len > have)
      {
         struct IDAT_list *cur = idat->idat_cur;
         unsigned int j = idat->idat_count+1; /* the next IDAT in the list */

         do
         {
            /* Add up the remaining bytes.  This can't overflow because the
             * individual lengths are always <= 0x7fffffff, so when we add two
             * of them overflow is not possible.
             */
            assert(cur != NULL);

            for (;;)
            {
               /* NOTE: IDAT_list::count here, not IDAT_list::length */
               for (; j < cur->count; ++j)
               {
                  have += cur->lengths[j];
                  if (len <= have)
                     return len;
               }

               /* If this was the end return the count of the available bytes */
               if (cur == idat->idat_list_tail)
                  return have;

               cur = cur->next;
               j = 0;
            }
         }
         while (len > have);
      }

      return len;
   }
}

static int
process_IDAT(struct file *file)
   /* Process the IDAT stream, this is the more complex than the preceding
    * cases because the compressed data is spread across multiple IDAT chunks
    * (typically).  Rechunking of the data is not handled here; all this
    * function does is establish whether the zlib header needs to be modified.
    *
    * Initially the function returns false, indicating that the chunk should not
    * be written.  It does this until the last IDAT chunk is passed in, then it
    * checks the zlib data and returns true.
    *
    * It does not return false on a fatal error; it calls stop instead.
    *
    * The caller must have an instantiated (IDAT) control structure and it must
    * have extent over the whole read of the IDAT stream.  For a PNG this means
    * the whole PNG read, for MNG it could have lesser extent.
    */
{
   struct IDAT_list *list;

   assert(file->idat != NULL && file->chunk != NULL);

   /* We need to first check the entire sequence of IDAT chunks to ensure the
    * stream is in sync.  Do this by building a list of all the chunks and
    * recording the length of each because the length may have been fixed up by
    * sync_stream below.
    *
    * At the end of the list of chunks, where the type of the next chunk is not
    * png_IDAT, process the whole stream using the list data to check validity
    * then return control to the start and rewrite everything.
    */
   list = file->idat->idat_list_tail;

   if (list->count == list->length)
   {
      list = IDAT_list_extend(list);

      if (list == NULL)
         stop(file, READ_ERROR_CODE, "out of memory");

      /* Move to the next block */
      list->count = 0;
      file->idat->idat_list_tail = list;
   }

   /* And fill in the next IDAT information buffer. */
   list->lengths[(list->count)++] = file->chunk->chunk_length;

   /* The type of the next chunk was recorded in the file control structure by
    * the caller, if this is png_IDAT return 'skip' to the caller.
    */
   if (file->type == png_IDAT)
      return 0; /* skip this for the moment */

   /* This is the final IDAT chunk, so run the tests to check for the too far
    * back error and possibly optimize the window bits.  This means going back
    * to the start of the first chunk data, which is stored in the original
    * chunk allocation.
    */
   setpos(file->chunk);

   if (zlib_check(file, 0))
   {
      struct IDAT *idat;
      int cmp;

      /* The IDAT stream was successfully uncompressed; see whether it
       * contained the correct number of bytes of image data.
       */
      cmp = uarb_cmp(file->image_bytes, file->image_digits,
         file->chunk->uncompressed_bytes, file->chunk->uncompressed_digits);

      if (cmp < 0)
         type_message(file, png_IDAT, "extra uncompressed data");

      else if (cmp > 0)
         stop(file, LIBPNG_ERROR_CODE, "IDAT: uncompressed data too small");

      /* Return the stream to the start of the first IDAT chunk; the length
       * is set in the write case below but the input chunk variables must be
       * set (once) here:
       */
      setpos(file->chunk);

      idat = file->idat;
      idat->idat_cur = idat->idat_list_head;
      idat->idat_length = idat->idat_cur->lengths[0];
      idat->idat_count = 0; /* Count of chunks read in current list */
      idat->idat_index = 0; /* Index into chunk data */

      /* Update the chunk length to the correct value for the IDAT chunk: */
      file->chunk->chunk_length = rechunk_length(idat, 1/*start*/);

      /* Change the state to writing IDAT chunks */
      file->state = STATE_IDAT;

      return 1;
   }

   else /* Failure to decompress the IDAT stream; give up. */
      stop(file, ZLIB_ERROR_CODE, "could not uncompress IDAT");
}

/* ZLIB CONTROL STRUCTURE */
struct zlib
{
   /* ANCESTORS */
   struct IDAT *  idat;          /* NOTE: May be NULL */
   struct chunk * chunk;
   struct file *  file;
   struct global *global;

   /* GLOBAL ZLIB INFORMATION: SET BY THE CALLER */
   png_uint_32    rewrite_offset;

   /* GLOBAL ZLIB INFORMATION: SET BY THE ZLIB READ CODE */
   udigit         compressed_bytes[5];
   int            compressed_digits;
   udigit         uncompressed_bytes[5];
   int            uncompressed_digits;
   int            file_bits;             /* window bits from the file */
   int            ok_bits;               /* Set <16 on a successful read */
   int            cksum;                 /* Set on a checksum error */

   /* PROTECTED ZLIB INFORMATION: USED BY THE ZLIB ROUTINES */
   z_stream       z;
   png_uint_32    extra_bytes;   /* Count of extra compressed bytes */
   int            state;
   int            rc;            /* Last return code */
   int            window_bits;   /* 0 if no change */
   png_byte       header[2];
};

static const char *
zlib_flevel(struct zlib *zlib)
{
   switch (zlib->header[1] >> 6)
   {
      case 0:  return "supfast";
      case 1:  return "stdfast";
      case 2:  return "default";
      case 3:  return "maximum";
      default: assert(UNREACHED);
   }

   return "COMPILER BUG";
}

static const char *
zlib_rc(struct zlib *zlib)
   /* Return a string for the zlib return code */
{
   switch (zlib->rc)
   {
      case Z_OK:              return "Z_OK";
      case Z_STREAM_END:      return "Z_STREAM_END";
      case Z_NEED_DICT:       return "Z_NEED_DICT";
      case Z_ERRNO:           return "Z_ERRNO";
      case Z_STREAM_ERROR:    return "Z_STREAM_ERROR";
      case Z_DATA_ERROR:      return "Z_DATA_ERROR";
      case Z_MEM_ERROR:       return "Z_MEM_ERROR";
      case Z_BUF_ERROR:       return "Z_BUF_ERROR";
      case Z_VERSION_ERROR:   return "Z_VERSION_ERROR";
      default:                return "Z_*INVALID_RC*";
   }
}

static void
zlib_message(struct zlib *zlib, int unexpected)
   /* Output a message given a zlib rc */
{
   if (zlib->global->errors)
   {
      const char *reason = zlib->z.msg;

      if (reason == NULL)
         reason = "[no message]";

      fputs(zlib->file->file_name, stderr);
      type_sep(stderr);
      type_name(zlib->chunk->chunk_type, stderr);
      fprintf(stderr, ": %szlib error: %d (%s) (%s)\n",
         unexpected ? "unexpected " : "", zlib->rc, zlib_rc(zlib), reason);
   }
}

static void
zlib_end(struct zlib *zlib)
{
   /* Output the summary line now; this ensures a summary line always gets
    * output regardless of the manner of exit.
    */
   if (!zlib->global->quiet)
   {
      if (zlib->ok_bits < 16) /* stream was read ok */
      {
         const char *reason;

         if (zlib->cksum)
            reason = "CHK"; /* checksum error */

         else if (zlib->ok_bits > zlib->file_bits)
            reason = "TFB"; /* fixing a too-far-back error */

         else if (zlib->ok_bits == zlib->file_bits)
            reason = "OK ";

         else
            reason = "OPT"; /* optimizing window bits */

         /* SUMMARY FORMAT (for a successful zlib inflate):
          *
          * IDAT reason flevel file-bits ok-bits compressed uncompressed file
          */
         type_name(zlib->chunk->chunk_type, stdout);
         printf(" %s %s %d %d ", reason, zlib_flevel(zlib), zlib->file_bits,
            zlib->ok_bits);
         uarb_print(zlib->compressed_bytes, zlib->compressed_digits, stdout);
         putc(' ', stdout);
         uarb_print(zlib->uncompressed_bytes, zlib->uncompressed_digits,
            stdout);
         putc(' ', stdout);
         fputs(zlib->file->file_name, stdout);
         putc('\n', stdout);
      }

      else
      {
         /* This is a zlib read error; the chunk will be skipped.  For an IDAT
          * stream this will also cause a fatal read error (via stop()).
          *
          * SUMMARY FORMAT:
          *
          * IDAT SKP flevel file-bits z-rc compressed message file
          *
          * z-rc is the zlib failure code; message is the error message with
          * spaces replaced by '-'.  The compressed byte count indicates where
          * in the zlib stream the error occurred.
          */
         type_name(zlib->chunk->chunk_type, stdout);
         printf(" SKP %s %d %s ", zlib_flevel(zlib), zlib->file_bits,
            zlib_rc(zlib));
         uarb_print(zlib->compressed_bytes, zlib->compressed_digits, stdout);
         putc(' ', stdout);
         emit_string(zlib->z.msg ? zlib->z.msg : "[no_message]", stdout);
         putc(' ', stdout);
         fputs(zlib->file->file_name, stdout);
         putc('\n', stdout);
      }
   }

   if (zlib->state >= 0)
   {
      zlib->rc = inflateEnd(&zlib->z);

      if (zlib->rc != Z_OK)
         zlib_message(zlib, 1/*unexpected*/);
   }

   CLEAR(*zlib);
}

static int
zlib_reset(struct zlib *zlib, int window_bits)
   /* Reinitializes a zlib with a different window_bits */
{
   assert(zlib->state >= 0); /* initialized by zlib_init */

   zlib->z.next_in = Z_NULL;
   zlib->z.avail_in = 0;
   zlib->z.next_out = Z_NULL;
   zlib->z.avail_out = 0;

   zlib->window_bits = window_bits;
   zlib->compressed_digits = 0;
   zlib->uncompressed_digits = 0;

   zlib->state = 0; /* initialized, once */
   zlib->rc = inflateReset2(&zlib->z, 0);
   if (zlib->rc != Z_OK)
   {
      zlib_message(zlib, 1/*unexpected*/);
      return 0;
   }

   return 1;
}

static int
zlib_init(struct zlib *zlib, struct IDAT *idat, struct chunk *chunk,
   int window_bits, png_uint_32 offset)
   /* Initialize a zlib_control; the result is true/false */
{
   CLEAR(*zlib);

   zlib->idat = idat;
   zlib->chunk = chunk;
   zlib->file = chunk->file;
   zlib->global = chunk->global;
   zlib->rewrite_offset = offset; /* never changed for this zlib */

   /* *_out does not need to be set: */
   zlib->z.next_in = Z_NULL;
   zlib->z.avail_in = 0;
   zlib->z.zalloc = Z_NULL;
   zlib->z.zfree = Z_NULL;
   zlib->z.opaque = Z_NULL;

   zlib->state = -1;
   zlib->window_bits = window_bits;

   zlib->compressed_digits = 0;
   zlib->uncompressed_digits = 0;

   /* These values are sticky across reset (in addition to the stuff in the
    * first block, which is actually constant.)
    */
   zlib->file_bits = 24;
   zlib->ok_bits = 16; /* unset */
   zlib->cksum = 0; /* set when a checksum error is detected */

   /* '0' means use the header; inflateInit2 should always succeed because it
    * does nothing apart from allocating the internal zstate.
    */
   zlib->rc = inflateInit2(&zlib->z, 0);
   if (zlib->rc != Z_OK)
   {
      zlib_message(zlib, 1/*unexpected*/);
      return 0;
   }

   else
   {
      zlib->state = 0; /* initialized */
      return 1;
   }
}

static int
max_window_bits(uarbc size, int ndigits)
   /* Return the zlib stream window bits required for data of the given size. */
{
   png_uint_16 cb;

   if (ndigits > 1)
      return 15;

   cb = size[0];

   if (cb > 16384) return 15;
   if (cb >  8192) return 14;
   if (cb >  4096) return 13;
   if (cb >  2048) return 12;
   if (cb >  1024) return 11;
   if (cb >   512) return 10;
   if (cb >   256) return  9;
   return 8;
}

static int
zlib_advance(struct zlib *zlib, png_uint_32 nbytes)
   /* Read nbytes compressed bytes; the stream will be initialized if required.
    * Bytes are always being reread and errors are fatal.  The return code is as
    * follows:
    *
    *    -1: saw the "too far back" error
    *     0: ok, keep going
    *     1: saw Z_STREAM_END (zlib->extra_bytes indicates too much data)
    *     2: a zlib error that cannot be corrected (error message already
    *        output if required.)
    */
#  define ZLIB_TOO_FAR_BACK (-1)
#  define ZLIB_OK           0
#  define ZLIB_STREAM_END   1
#  define ZLIB_FATAL        2
{
   int state = zlib->state;
   int endrc = ZLIB_OK;
   png_uint_32 in_bytes = 0;
   struct file *file = zlib->file;

   assert(state >= 0);

   while (in_bytes < nbytes && endrc == ZLIB_OK)
   {
      png_uint_32 out_bytes;
      int flush;
      png_byte bIn = reread_byte(file);
      png_byte bOut;

      switch (state)
      {
         case 0: /* first header byte */
            {
               int file_bits = 8+(bIn >> 4);
               int new_bits = zlib->window_bits;

               zlib->file_bits = file_bits;

               /* Check against the existing value - it may not need to be
                * changed.  Note that a bogus file_bits is allowed through once,
                * to see if it works, but the window_bits value is set to 15,
                * the maximum.
                */
               if (new_bits == 0) /* no change */
                  zlib->window_bits = ((file_bits > 15) ? 15 : file_bits);

               else if (new_bits != file_bits) /* rewrite required */
                  bIn = (png_byte)((bIn & 0xf) + ((new_bits-8) << 4));
            }

            zlib->header[0] = bIn;
            zlib->state = state = 1;
            break;

         case 1: /* second header byte */
            {
               int b2 = bIn & 0xe0; /* top 3 bits */

               /* The checksum calculation, on the first 11 bits: */
               b2 += 0x1f - ((zlib->header[0] << 8) + b2) % 0x1f;

               /* Update the checksum byte if required: */
               if (bIn != b2)
               {
                  /* If the first byte wasn't changed this indicates an error in
                   * the checksum calculation; signal this by setting 'cksum'.
                   */
                  if (zlib->file_bits == zlib->window_bits)
                     zlib->cksum = 1;

                  bIn = (png_byte)b2;
               }
            }

            zlib->header[1] = bIn;
            zlib->state = state = 2;
            break;

         default: /* After the header bytes */
            break;
      }

      /* For some streams, perhaps only those compressed with 'superfast
       * compression' (which results in a lot of copying) Z_BUF_ERROR can happen
       * immediately after all output has been flushed on the next input byte.
       * This is handled below when Z_BUF_ERROR is detected by adding an output
       * byte.
       */
      zlib->z.next_in = &bIn;
      zlib->z.avail_in = 1;
      zlib->z.next_out = &bOut;
      zlib->z.avail_out = 0;     /* Initially */

      /* Initially use Z_NO_FLUSH in an attempt to persuade zlib to look at this
       * byte without confusing what is going on with output.
       */
      flush = Z_NO_FLUSH;
      out_bytes = 0;

      /* NOTE: expression 3 is only evaluated on 'continue', because of the
       * 'break' at the end of this loop below.
       */
      for (;endrc == ZLIB_OK;
         flush = Z_SYNC_FLUSH,
         zlib->z.next_out = &bOut,
         zlib->z.avail_out = 1,
         ++out_bytes)
      {
         zlib->rc = inflate(&zlib->z, flush);
         out_bytes -= zlib->z.avail_out;

         switch (zlib->rc)
         {
            case Z_BUF_ERROR:
               if (zlib->z.avail_out == 0)
                  continue; /* Try another output byte. */

               if (zlib->z.avail_in == 0)
                  break; /* Try another input byte */

               /* Both avail_out and avail_in are 1 yet zlib returned a code
                * indicating no progress was possible.  This is unexpected.
                */
               zlib_message(zlib, 1/*unexpected*/);
               endrc = ZLIB_FATAL; /* stop processing */
               break;

            case Z_OK:
               /* Zlib is supposed to have made progress: */
               assert(zlib->z.avail_out == 0 || zlib->z.avail_in == 0);
               continue;

            case Z_STREAM_END:
               /* This is the successful end. */
               zlib->state = 3; /* end of stream */
               endrc = ZLIB_STREAM_END;
               break;

            case Z_NEED_DICT:
               zlib_message(zlib, 0/*stream error*/);
               endrc = ZLIB_FATAL;
               break;

            case Z_DATA_ERROR:
               /* The too far back error can be corrected, others cannot: */
               if (zlib->z.msg != NULL &&
                  strcmp(zlib->z.msg, "invalid distance too far back") == 0)
               {
                  endrc = ZLIB_TOO_FAR_BACK;
                  break;
               }
               /* FALLTHROUGH */

            default:
               zlib_message(zlib, 0/*stream error*/);
               endrc = ZLIB_FATAL;
               break;
         } /* switch (inflate rc) */

         /* Control gets here when further output is not possible; endrc may
          * still be ZLIB_OK if more input is required.
          */
         break;
      } /* for (output bytes) */

      /* Keep a running count of output byte produced: */
      zlib->uncompressed_digits = uarb_add32(zlib->uncompressed_bytes,
         zlib->uncompressed_digits, out_bytes);

      /* Keep going, the loop will terminate when endrc is no longer set to
       * ZLIB_OK or all the input bytes have been consumed; meanwhile keep
       * adding input bytes.
       */
      assert(zlib->z.avail_in == 0 || endrc != ZLIB_OK);

      in_bytes += 1 - zlib->z.avail_in;
   } /* while (input bytes) */

   assert(in_bytes == nbytes || endrc != ZLIB_OK);

   /* Update the running total of input bytes consumed */
   zlib->compressed_digits = uarb_add32(zlib->compressed_bytes,
      zlib->compressed_digits, in_bytes - zlib->z.avail_in);

   /* At the end of the stream update the chunk with the accumulated
    * information if it is an improvement:
    */
   if (endrc == ZLIB_STREAM_END && zlib->window_bits < zlib->ok_bits)
   {
      struct chunk *chunk = zlib->chunk;

      chunk->uncompressed_digits = uarb_copy(chunk->uncompressed_bytes,
         zlib->uncompressed_bytes, zlib->uncompressed_digits);
      chunk->compressed_digits = uarb_copy(chunk->compressed_bytes,
         zlib->compressed_bytes, zlib->compressed_digits);
      chunk->rewrite_buffer[0] = zlib->header[0];
      chunk->rewrite_buffer[1] = zlib->header[1];

      if (zlib->window_bits != zlib->file_bits || zlib->cksum)
      {
         /* A rewrite is required */
         chunk->rewrite_offset = zlib->rewrite_offset;
         chunk->rewrite_length = 2;
      }

      else
      {
         chunk->rewrite_offset = 0;
         chunk->rewrite_length = 0;
      }

      if (in_bytes < nbytes)
         chunk_message(chunk, "extra compressed data");

      zlib->extra_bytes = nbytes - in_bytes;
      zlib->ok_bits = zlib->window_bits;
   }

   return endrc;
}

static int
zlib_run(struct zlib *zlib)
   /* Like zlib_advance but also handles a stream of IDAT chunks. */
{
   /* The 'extra_bytes' field is set by zlib_advance if there is extra
    * compressed data in the chunk it handles (if it sees Z_STREAM_END before
    * all the input data has been used.)  This function uses the value to update
    * the correct chunk length, so the problem should only ever be detected once
    * for each chunk.  zlib_advance outputs the error message, though see the
    * IDAT specific check below.
    */
   zlib->extra_bytes = 0;

   if (zlib->idat != NULL)
   {
      struct IDAT_list *list = zlib->idat->idat_list_head;
      struct IDAT_list *last = zlib->idat->idat_list_tail;
      int        skip = 0;

      /* 'rewrite_offset' is the offset of the LZ data within the chunk, for
       * IDAT it should be 0:
       */
      assert(zlib->rewrite_offset == 0);

      /* Process each IDAT_list in turn; the caller has left the stream
       * positioned at the start of the first IDAT chunk data.
       */
      for (;;)
      {
         unsigned int count = list->count;
         unsigned int i;

         for (i = 0; i<count; ++i)
         {
            int rc;

            if (skip > 0) /* Skip CRC and next IDAT header */
               skip_12(zlib->file);

            skip = 12; /* for the next time */

            rc = zlib_advance(zlib, list->lengths[i]);

            switch (rc)
            {
               case ZLIB_OK: /* keep going */
                  break;

               case ZLIB_STREAM_END: /* stop */
                  /* There may be extra chunks; if there are and one of them is
                   * not zero length output the 'extra data' message.  Only do
                   * this check if errors are being output.
                   */
                  if (zlib->global->errors && zlib->extra_bytes == 0)
                  {
                     struct IDAT_list *check = list;
                     int j = i+1, jcount = count;

                     for (;;)
                     {
                        for (; j<jcount; ++j)
                           if (check->lengths[j] > 0)
                           {
                              chunk_message(zlib->chunk,
                                 "extra compressed data");
                              goto end_check;
                           }

                        if (check == last)
                           break;

                        check = check->next;
                        jcount = check->count;
                        j = 0;
                     }
                  }

               end_check:
                  /* Terminate the list at the current position, reducing the
                   * length of the last IDAT too if required.
                   */
                  list->lengths[i] -= zlib->extra_bytes;
                  list->count = i+1;
                  zlib->idat->idat_list_tail = list;
                  /* FALLTHROUGH */

               default:
                  return rc;
            }
         }

         /* At the end of the compressed data and Z_STREAM_END was not seen. */
         if (list == last)
            return ZLIB_OK;

         list = list->next;
      }
   }

   else
   {
      struct chunk *chunk = zlib->chunk;
      int rc;

      assert(zlib->rewrite_offset < chunk->chunk_length);

      rc = zlib_advance(zlib, chunk->chunk_length - zlib->rewrite_offset);

      /* The extra bytes in the chunk are handled now by adjusting the chunk
       * length to exclude them; the zlib data is always stored at the end of
       * the PNG chunk (although clearly this is not necessary.)  zlib_advance
       * has already output a warning message.
       */
      chunk->chunk_length -= zlib->extra_bytes;
      return rc;
   }
}

static int /* global function; not a member function */
zlib_check(struct file *file, png_uint_32 offset)
   /* Check the stream of zlib compressed data in either idat (if given) or (if
    * not) chunk.  In fact it is zlib_run that handles the difference in reading
    * a single chunk and a list of IDAT chunks.
    *
    * In either case the input file must be positioned at the first byte of zlib
    * compressed data (the first header byte).
    *
    * The return value is true on success, including the case where the zlib
    * header may need to be rewritten, and false on an unrecoverable error.
    *
    * In the case of IDAT chunks 'offset' should be 0.
    */
{
   fpos_t start_pos;
   struct zlib zlib;

   /* Record the start of the LZ data to allow a re-read. */
   file_getpos(file, &start_pos);

   /* First test the existing (file) window bits: */
   if (zlib_init(&zlib, file->idat, file->chunk, 0/*window bits*/, offset))
   {
      int min_bits, max_bits, rc;

      /* The first run using the existing window bits. */
      rc = zlib_run(&zlib);

      switch (rc)
      {
         case ZLIB_TOO_FAR_BACK:
            /* too far back error */
            file->status_code |= TOO_FAR_BACK;
            min_bits = zlib.window_bits + 1;
            max_bits = 15;
            break;

         case ZLIB_STREAM_END:
            if (!zlib.global->optimize_zlib &&
               zlib.window_bits == zlib.file_bits && !zlib.cksum)
            {
               /* The trivial case where the stream is ok and optimization was
                * not requested.
                */
               zlib_end(&zlib);
               return 1;
            }

            max_bits = max_window_bits(zlib.uncompressed_bytes,
               zlib.uncompressed_digits);
            if (zlib.ok_bits < max_bits)
               max_bits = zlib.ok_bits;
            min_bits = 8;

            /* cksum is set if there is an error in the zlib header checksum
             * calculation in the original file (and this may be the only reason
             * a rewrite is required).  We can't rely on the file window bits in
             * this case, so do the optimization anyway.
             */
            if (zlib.cksum)
               chunk_message(zlib.chunk, "zlib checksum");
            break;


         case ZLIB_OK:
            /* Truncated stream; unrecoverable, gets converted to ZLIB_FATAL */
            zlib.z.msg = PNGZ_MSG_CAST("[truncated]");
            zlib_message(&zlib, 0/*expected*/);
            /* FALLTHROUGH */

         default:
            /* Unrecoverable error; skip the chunk; a zlib_message has already
             * been output.
             */
            zlib_end(&zlib);
            return 0;
      }

      /* Optimize window bits or fix a too-far-back error.  min_bits and
       * max_bits have been set appropriately, ok_bits records the bit value
       * known to work.
       */
      while (min_bits < max_bits || max_bits < zlib.ok_bits/*if 16*/)
      {
         int test_bits = (min_bits + max_bits) >> 1;

         if (zlib_reset(&zlib, test_bits))
         {
            file_setpos(file, &start_pos);
            rc = zlib_run(&zlib);

            switch (rc)
            {
               case ZLIB_TOO_FAR_BACK:
                  min_bits = test_bits+1;
                  if (min_bits > max_bits)
                  {
                     /* This happens when the stream really is damaged and it
                      * contains a distance code that addresses bytes before
                      * the start of the uncompressed data.
                      */
                     assert(test_bits == 15);

                     /* Output the error that wasn't output before: */
                     if (zlib.z.msg == NULL)
                        zlib.z.msg = PNGZ_MSG_CAST(
                           "invalid distance too far back");
                     zlib_message(&zlib, 0/*stream error*/);
                     zlib_end(&zlib);
                     return 0;
                  }
                  break;

               case ZLIB_STREAM_END: /* success */
                  max_bits = test_bits;
                  break;

               default:
                  /* A fatal error; this happens if a too-far-back error was
                   * hiding a more serious error, zlib_advance has already
                   * output a zlib_message.
                   */
                  zlib_end(&zlib);
                  return 0;
            }
         }

         else /* inflateReset2 failed */
         {
            zlib_end(&zlib);
            return 0;
         }
      }

      /* The loop guarantees this */
      assert(zlib.ok_bits == max_bits);
      zlib_end(&zlib);
      return 1;
   }

   else /* zlib initialization failed - skip the chunk */
   {
      zlib_end(&zlib);
      return 0;
   }
}

/***************************** LIBPNG CALLBACKS *******************************/
/* The strategy here is to run a regular libpng PNG file read but examine the
 * input data (from the file) before passing it to libpng so as to be aware of
 * the state we expect libpng to be in.  Warning and error callbacks are also
 * intercepted so that they can be quieted and interpreted.  Interpretation
 * depends on a somewhat risky string match for known error messages; let us
 * hope that this can be fixed in the next version of libpng.
 *
 * The control structure is pointed to by the libpng error pointer.  It contains
 * that set of structures which must persist across multiple read callbacks,
 * which is pretty much everything except the 'zlib' control structure.
 *
 * The file structure is instantiated in the caller of the per-file routine, but
 * the per-file routine contains the chunk and IDAT control structures.
 */
/* The three routines read_chunk, process_chunk and sync_stream can only be
 * called via a call to read_chunk and only exit at a return from process_chunk.
 * These routines could have been written as one confusing large routine,
 * instead this code relies on the compiler to do tail call elimination.  The
 * possible calls are as follows:
 *
 * read_chunk
 *    -> sync_stream
 *       -> process_chunk
 *    -> process_chunk
 *       -> read_chunk
 *       returns
 */
static void read_chunk(struct file *file);
static void
process_chunk(struct file *file, png_uint_32 file_crc, png_uint_32 next_length,
   png_uint_32 next_type)
   /* Called when the chunk data has been read, next_length and next_type
    * will be set for the next chunk (or 0 if this is IEND).
    *
    * When this routine returns, chunk_length and chunk_type will be set for the
    * next chunk to write because if a chunk is skipped this return calls back
    * to read_chunk.
    */
{
   png_uint_32 type = file->type;

   if (file->global->verbose > 1)
   {
      fputs("  ", stderr);
      type_name(file->type, stderr);
      fprintf(stderr, " %lu 0x%.8x 0x%.8x\n", (unsigned long)file->length,
         file->crc ^ 0xffffffff, file_crc);
   }

   /* The basic structure seems correct but the CRC may not match, in this
    * case assume that it is simply a bad CRC, either wrongly calculated or
    * because of damaged stream data.
    */
   if ((file->crc ^ 0xffffffff) != file_crc)
   {
      /* The behavior is set by the 'skip' setting; if it is anything other
       * than SKIP_BAD_CRC ignore the bad CRC and return the chunk, with a
       * corrected CRC and possibly processed, to libpng.  Otherwise skip the
       * chunk, which will result in a fatal error if the chunk is critical.
       */
      file->status_code |= CRC_ERROR;

      /* Ignore the bad CRC  */
      if (file->global->skip != SKIP_BAD_CRC)
         type_message(file, type, "bad CRC");

      /* This will cause an IEND with a bad CRC to stop */
      else if (CRITICAL(type))
         stop(file, READ_ERROR_CODE, "bad CRC in critical chunk");

      else
      {
         type_message(file, type, "skipped: bad CRC");

         /* NOTE: this cannot be reached for IEND because it is critical. */
         goto skip_chunk;
      }
   }

   /* Check for other 'skip' cases and handle these; these only apply to
    * ancillary chunks (and not tRNS, which should probably have been a critical
    * chunk.)
    */
   if (skip_chunk_type(file->global, type))
      goto skip_chunk;

   /* The chunk may still be skipped if problems are detected in the LZ data,
    * however the LZ data check requires a chunk.  Handle this by instantiating
    * a chunk unless an IDAT is already instantiated (IDAT control structures
    * instantiate their own chunk.)
    */
   if (type != png_IDAT)
      file->alloc(file, 0/*chunk*/);

   else if (file->idat == NULL)
      file->alloc(file, 1/*IDAT*/);

   else
   {
      /* The chunk length must be updated for process_IDAT */
      assert(file->chunk != NULL);
      assert(file->chunk->chunk_type == png_IDAT);
      file->chunk->chunk_length = file->length;
   }

   /* Record the 'next' information too, now that the original values for
    * this chunk have been copied.  Notice that the IDAT chunks only make a
    * copy of the position of the first chunk, this is fine - process_IDAT does
    * not need the position of this chunk.
    */
   file->length = next_length;
   file->type = next_type;
   getpos(file);

   /* Do per-type processing, note that if this code does not return from the
    * function the chunk will be skipped.  The rewrite is cancelled here so that
    * it can be set in the per-chunk processing.
    */
   file->chunk->rewrite_length = 0;
   file->chunk->rewrite_offset = 0;
   switch (type)
   {
      default:
         return;

      case png_IHDR:
         /* Read this now and update the control structure with the information
          * it contains.  The header is validated completely to ensure this is a
          * PNG.
          */
         {
            struct chunk *chunk = file->chunk;

            if (chunk->chunk_length != 13)
               stop_invalid(file, "IHDR length");

            /* Read all the IHDR information and validate it. */
            setpos(chunk);
            file->width = reread_4(file);
            file->height = reread_4(file);
            file->bit_depth = reread_byte(file);
            file->color_type = reread_byte(file);
            file->compression_method = reread_byte(file);
            file->filter_method = reread_byte(file);
            file->interlace_method = reread_byte(file);

            /* This validates all the fields, and calls stop_invalid if
             * there is a problem.
             */
            calc_image_size(file);
         }
         return;

         /* Ancillary chunks that require further processing: */
      case png_zTXt: case png_iCCP:
         if (process_zTXt_iCCP(file))
            return;
         chunk_end(&file->chunk);
         file_setpos(file, &file->data_pos);
         break;

      case png_iTXt:
         if (process_iTXt(file))
            return;
         chunk_end(&file->chunk);
         file_setpos(file, &file->data_pos);
         break;

      case png_IDAT:
         if (process_IDAT(file))
            return;
         /* First pass: */
         assert(next_type == png_IDAT);
         break;
   }

   /* Control reaches this point if the chunk must be skipped.  For chunks other
    * than IDAT this means that the zlib compressed data is fatally damanged and
    * the chunk will not be passed to libpng.  For IDAT it means that the end of
    * the IDAT stream has not yet been reached and we must handle the next
    * (IDAT) chunk.  If the LZ data in an IDAT stream cannot be read 'stop' must
    * be used to halt parsing of the PNG.
    */
   read_chunk(file);
   return;

   /* This is the generic code to skip the current chunk; simply jump to the
    * next one.
    */
skip_chunk:
   file->length = next_length;
   file->type = next_type;
   getpos(file);
   read_chunk(file);
}

static png_uint_32
get32(png_bytep buffer, int offset)
   /* Read a 32-bit value from an 8-byte circular buffer (used only below).
    */
{
   return
      (buffer[ offset    & 7] << 24) +
      (buffer[(offset+1) & 7] << 16) +
      (buffer[(offset+2) & 7] <<  8) +
      (buffer[(offset+3) & 7]      );
}

static void
sync_stream(struct file *file)
   /* The stream seems to be messed up, attempt to resync from the current chunk
    * header.  Executes stop on a fatal error, otherwise calls process_chunk.
    */
{
   png_uint_32 file_crc;

   file->status_code |= STREAM_ERROR;

   if (file->global->verbose)
   {
      fputs(" SYNC ", stderr);
      type_name(file->type, stderr);
      putc('\n', stderr);
   }

   /* Return to the start of the chunk data */
   file_setpos(file, &file->data_pos);
   file->read_count = 8;

   if (read_4(file, &file_crc) == 4) /* else completely truncated */
   {
      /* Ignore the recorded chunk length, proceed through the data looking for
       * a leading sequence of bytes that match the CRC in the following four
       * bytes.  Each time a match is found check the next 8 bytes for a valid
       * length, chunk-type pair.
       */
      png_uint_32 length;
      png_uint_32 type = file->type;
      png_uint_32 crc = crc_init_4(type);
      png_byte buffer[8];
      unsigned int nread = 0, nused = 0;

      for (length=0; length <= 0x7fffffff; ++length)
      {
         int ch;

         if ((crc ^ 0xffffffff) == file_crc)
         {
            /* A match on the CRC; for IEND this is sufficient, but for anything
             * else expect a following chunk header.
             */
            if (type == png_IEND)
            {
               file->length = length;
               process_chunk(file, file_crc, 0, 0);
               return;
            }

            else
            {
               /* Need 8 bytes */
               while (nread < 8+nused)
               {
                  ch = read_byte(file);
                  if (ch == EOF)
                     goto truncated;
                  buffer[(nread++) & 7] = (png_byte)ch;
               }

               /* Prevent overflow */
               nread -= nused & ~7;
               nused -= nused & ~7; /* or, nused &= 7 ;-) */

               /* Examine the 8 bytes for a valid chunk header. */
               {
                  png_uint_32 next_length = get32(buffer, nused);

                  if (next_length < 0x7fffffff)
                  {
                     png_uint_32 next_type = get32(buffer, nused+4);

                     if (chunk_type_valid(next_type))
                     {
                        file->read_count -= 8;
                        process_chunk(file, file_crc, next_length, next_type);
                        return;
                     }
                  }

                  /* Not valid, keep going. */
               }
            }
         }

         /* This catches up with the circular buffer which gets filled above
          * while checking a chunk header.  This code is slightly tricky - if
          * the chunk_type is IEND the buffer will never be used, if it is not
          * the code will always read ahead exactly 8 bytes and pass this on to
          * process_chunk.  So the invariant that IEND leaves the file position
          * after the IEND CRC and other chunk leave it after the *next* chunk
          * header is not broken.
          */
         if (nread <= nused)
         {
            ch = read_byte(file);

            if (ch == EOF)
               goto truncated;
         }

         else
            ch = buffer[(++nused) & 7];

         crc = crc_one_byte(crc, file_crc >> 24);
         file_crc = (file_crc << 8) + ch;
      }

      /* Control gets to here if when 0x7fffffff bytes (plus 8) have been read,
       * ok, treat this as a damaged stream too:
       */
   }

truncated:
   stop(file, READ_ERROR_CODE, "damaged PNG stream");
}

static void
read_chunk(struct file *file)
   /* On entry file::data_pos must be set to the position of the first byte
    * of the chunk data *and* the input file must be at this position.  This
    * routine (via process_chunk) instantiates a chunk or IDAT control structure
    * based on file::length and file::type and also resets these fields and
    * file::data_pos for the chunk after this one.  For an IDAT chunk the whole
    * stream of IDATs will be read, until something other than an IDAT is
    * encountered, and the file fields will be set for the chunk after the end
    * of the stream of IDATs.
    *
    * For IEND the file::type field will be set to 0, and nothing beyond the end
    * of the IEND chunk will have been read.
    */
{
   png_uint_32 length = file->length;
   png_uint_32 type = file->type;

   /* After IEND file::type is set to 0, if libpng attempts to read
    * more data at this point this is a bug in libpng.
    */
   if (type == 0)
      stop(file, UNEXPECTED_ERROR_CODE, "read beyond IEND");

   if (file->global->verbose > 2)
   {
      fputs("   ", stderr);
      type_name(type, stderr);
      fprintf(stderr, " %lu\n", (unsigned long)length);
   }

   /* Start the read_crc calculation with the chunk type, then read to the end
    * of the chunk data (without processing it in any way) to check that it is
    * all there and calculate the CRC.
    */
   file->crc = crc_init_4(type);
   if (crc_read_many(file, length)) /* else it was truncated */
   {
      png_uint_32 file_crc; /* CRC read from file */
      unsigned int nread = read_4(file, &file_crc);

      if (nread == 4)
      {
         if (type != png_IEND) /* do not read beyond IEND */
         {
            png_uint_32 next_length;

            nread += read_4(file, &next_length);
            if (nread == 8 && next_length <= 0x7fffffff)
            {
               png_uint_32 next_type;

               nread += read_4(file, &next_type);

               if (nread == 12 && chunk_type_valid(next_type))
               {
                  /* Adjust the read count back to the correct value for this
                   * chunk.
                   */
                  file->read_count -= 8;
                  process_chunk(file, file_crc, next_length, next_type);
                  return;
               }
            }
         }

         else /* IEND */
         {
            process_chunk(file, file_crc, 0, 0);
            return;
         }
      }
   }

   /* Control gets to here if the stream seems invalid or damaged in some
    * way.  Either there was a problem reading all the expected data (this
    * chunk's data, its CRC and the length and type of the next chunk) or the
    * next chunk length/type are invalid.  Notice that the cases that end up
    * here all correspond to cases that would otherwise terminate the read of
    * the PNG file.
    */
   sync_stream(file);
}

/* This returns a file* from a png_struct in an implementation specific way. */
static struct file *get_control(png_const_structrp png_ptr);

static void PNGCBAPI
error_handler(png_structp png_ptr, png_const_charp message)
{
   stop(get_control(png_ptr),  LIBPNG_ERROR_CODE, message);
}

static void PNGCBAPI
warning_handler(png_structp png_ptr, png_const_charp message)
{
   struct file *file = get_control(png_ptr);

   if (file->global->warnings)
      emit_error(file, LIBPNG_WARNING_CODE, message);
}

/* Read callback - this is where the work gets done to check the stream before
 * passing it to libpng
 */
static void PNGCBAPI
read_callback(png_structp png_ptr, png_bytep buffer, size_t count)
   /* Return 'count' bytes to libpng in 'buffer' */
{
   struct file *file = get_control(png_ptr);
   png_uint_32 type, length; /* For the chunk be *WRITTEN* */
   struct chunk *chunk;

   /* libpng should always ask for at least one byte */
   if (count == 0)
      stop(file, UNEXPECTED_ERROR_CODE, "read callback for 0 bytes");

   /* The callback always reads ahead by 8 bytes - the signature or chunk header
    * - these bytes are stored in chunk_length and chunk_type.  This block is
    * executed once for the signature and once for the first chunk right at the
    * start.
    */
   if (file->read_count < 8)
   {
      assert(file->read_count == 0);
      assert((file->status_code & TRUNCATED) == 0);

      (void)read_4(file, &file->length);

      if (file->read_count == 4)
         (void)read_4(file, &file->type);

      if (file->read_count < 8)
      {
         assert((file->status_code & TRUNCATED) != 0);
         stop(file, READ_ERROR_CODE, "not a PNG (too short)");
      }

      if (file->state == STATE_SIGNATURE)
      {
         if (file->length != sig1 || file->type != sig2)
            stop(file, LIBPNG_ERROR_CODE, "not a PNG (signature)");

         /* Else write it (this is the initialization of write_count, prior to
          * this it contains CLEAR garbage.)
          */
         file->write_count = 0;
      }

      else
      {
         assert(file->state == STATE_CHUNKS);

         /* The first chunk must be a well formed IHDR (this could be relaxed to
          * use the checks in process_chunk, but that seems unnecessary.)
          */
         if (file->length != 13 || file->type != png_IHDR)
            stop(file, LIBPNG_ERROR_CODE, "not a PNG (IHDR)");

         /* The position of the data must be stored too */
         getpos(file);
      }
   }

   /* Retrieve previous state (because the read callbacks are made pretty much
    * byte-by-byte in the sequential reader prior to 1.7).
    */
   chunk = file->chunk;

   if (chunk != NULL)
   {
      length = chunk->chunk_length;
      type = chunk->chunk_type;
   }

   else
   {
      /* This is the signature case; for IDAT and other chunks these values will
       * be overwritten when read_chunk is called below.
       */
      length = file->length;
      type = file->type;
   }

   do
   {
      png_uint_32 b;

      /* Complete the read of a chunk; as a side effect this also instantiates
       * a chunk control structure and sets the file length/type/data_pos fields
       * for the *NEXT* chunk header.
       *
       * NOTE: at an IDAT any following IDAT chunks will also be read and the
       * next_ fields will refer to the chunk after the last IDAT.
       *
       * NOTE: read_chunk only returns when it has read a chunk that must now be
       * written.
       */
      if (file->state != STATE_SIGNATURE && chunk == NULL)
      {
         assert(file->read_count == 8);
         assert(file->idat == NULL);
         read_chunk(file);
         chunk = file->chunk;
         assert(chunk != NULL);

         /* Do the initialization that was not done before. */
         length = chunk->chunk_length;
         type = chunk->chunk_type;

         /* And start writing the new chunk. */
         file->write_count = 0;
      }

      /* The chunk_ fields describe a chunk that must be written, or hold the
       * signature.  Write the header first.  In the signature case this
       * rewrites the signature.
       */
      switch (file->write_count)
      {
         case 0: b = length >> 24; break;
         case 1: b = length >> 16; break;
         case 2: b = length >>  8; break;
         case 3: b = length      ; break;

         case 4: b = type >> 24; break;
         case 5: b = type >> 16; break;
         case 6: b = type >>  8; break;
         case 7: b = type      ; break;

         case 8:
            /* The header has been written.  If this is really the signature
             * that's all that is required and we can go to normal chunk
             * processing.
             */
            if (file->state == STATE_SIGNATURE)
            {
               /* The signature has been written, the tail call to read_callback
                * below (it's just a goto to the start with a decent compiler)
                * will read the IHDR header ahead and validate it.
                */
               assert(length == sig1 && type == sig2);
               file->read_count = 0; /* Forces a header read */
               file->state = STATE_CHUNKS; /* IHDR: checked above */
               read_callback(png_ptr, buffer, count);
               return;
            }

            else
            {
               assert(chunk != NULL);

               /* Set up for write, notice that repositioning the input stream
                * is only necessary if something is to be read from it.  Also
                * notice that for the IDAT stream this must only happen once -
                * on the first IDAT - to get back to the start of the list and
                * this is done inside process_IDAT:
                */
               chunk->write_crc = crc_init_4(type);
               if (file->state != STATE_IDAT && length > 0)
                  setpos(chunk);
            }
            /* FALLTHROUGH */

         default:
            assert(chunk != NULL);

            /* NOTE: the arithmetic below overflows and gives a large positive
             * png_uint_32 value until the whole chunk data has been written.
             */
            switch (file->write_count - length)
            {
               /* Write the chunk data, normally this just comes from
                * the file.  The only exception is for that part of a
                * chunk which is zlib data and which must be rewritten,
                * and IDAT chunks which can be completely
                * reconstructed.
                */
               default:
                  if (file->state == STATE_IDAT)
                  {
                     struct IDAT *idat = file->idat;

                     assert(idat != NULL);

                     /* Read an IDAT byte from the input stream of IDAT chunks.
                      * Because the IDAT stream can be re-chunked this stream is
                      * held in the struct IDAT members.  The chunk members, in
                      * particular chunk_length (and therefore the length local)
                      * refer to the output chunk.
                      */
                     while (idat->idat_index >= idat->idat_length)
                     {
                        /* Advance one chunk */
                        struct IDAT_list *cur = idat->idat_cur;

                        assert(idat->idat_index == idat->idat_length);
                        assert(cur != NULL && cur->count > 0);

                        /* NOTE: IDAT_list::count here, not IDAT_list::length */
                        if (++(idat->idat_count) >= cur->count)
                        {
                           assert(idat->idat_count == cur->count);

                           /* Move on to the next IDAT_list: */
                           cur = cur->next;

                           /* This is an internal error - read beyond the end of
                            * the pre-calculated stream.
                            */
                           if (cur == NULL || cur->count == 0)
                              stop(file, UNEXPECTED_ERROR_CODE,
                                 "read beyond end of IDAT");

                           idat->idat_count = 0;
                           idat->idat_cur = cur;
                        }

                        idat->idat_index = 0;
                        /* Zero length IDAT chunks are permitted, so the length
                         * here may be 0.
                         */
                        idat->idat_length = cur->lengths[idat->idat_count];

                        /* And skip 12 bytes to the next chunk data */
                        skip_12(file);
                     }

                     /* The index is always that of the next byte, the rest of
                      * the information is always the current IDAT chunk and the
                      * current list.
                      */
                     ++(idat->idat_index);
                  }

                  /* Read the byte from the stream. */
                  b = reread_byte(file);

                  /* If the byte must be rewritten handle that here */
                  if (chunk->rewrite_length > 0)
                  {
                     if (chunk->rewrite_offset > 0)
                        --(chunk->rewrite_offset);

                     else
                     {
                        b = chunk->rewrite_buffer[0];
                        memmove(chunk->rewrite_buffer, chunk->rewrite_buffer+1,
                           (sizeof chunk->rewrite_buffer)-
                              (sizeof chunk->rewrite_buffer[0]));

                        --(chunk->rewrite_length);
                     }
                  }

                  chunk->write_crc = crc_one_byte(chunk->write_crc, b);
                  break;

               /* The CRC is written at:
                *
                *    chunk_write == chunk_length+8..chunk_length+11
                *
                * so 8 to 11.  The CRC is not (yet) conditioned.
                */
               case  8: b = chunk->write_crc >> 24; goto write_crc;
               case  9: b = chunk->write_crc >> 16; goto write_crc;
               case 10: b = chunk->write_crc >>  8; goto write_crc;
               case 11:
                  /* This must happen before the chunk_end below: */
                  b = chunk->write_crc;

                  if (file->global->verbose > 2)
                  {
                     fputs("   ", stderr);
                     type_name(type, stderr);
                     fprintf(stderr, " %lu 0x%.8x\n", (unsigned long)length,
                        chunk->write_crc ^ 0xffffffff);
                  }

                  /* The IDAT stream is written without a call to read_chunk
                   * until the end is reached.  rechunk_length() calculates the
                   * length of the output chunks.  Control gets to this point at
                   * the end of an *output* chunk - the length calculated by
                   * rechunk_length.  If this corresponds to the end of the
                   * input stream stop writing IDAT chunks, otherwise continue.
                   */
                  if (file->state == STATE_IDAT &&
                     (file->idat->idat_index < file->idat->idat_length ||
                      1+file->idat->idat_count < file->idat->idat_cur->count ||
                      file->idat->idat_cur != file->idat->idat_list_tail))
                  {
                     /* Write another IDAT chunk.  Call rechunk_length to
                      * calculate the length required.
                      */
                     length = chunk->chunk_length =
                         rechunk_length(file->idat, 0/*end*/);
                     assert(type == png_IDAT);
                     file->write_count = 0; /* for the new chunk */
                     --(file->write_count); /* fake out the increment below */
                  }

                  else
                  {
                     /* Entered at the end of a non-IDAT chunk and at the end of
                      * the IDAT stream.  The rewrite should have been cleared.
                      */
                     if (chunk->rewrite_length > 0 || chunk->rewrite_offset > 0)
                        stop(file, UNEXPECTED_ERROR_CODE, "pending rewrite");

                     /* This is the last byte so reset chunk_read for the next
                      * chunk and move the input file to the position after the
                      * *next* chunk header if required.
                      */
                     file->read_count = 8;
                     file_setpos(file, &file->data_pos);

                     if (file->idat == NULL)
                        chunk_end(&file->chunk);

                     else
                        IDAT_end(&file->idat);
                  }

               write_crc:
                  b ^= 0xff; /* conditioning */
                  break;
            }
            break;
      }

      /* Write one byte */
      b &= 0xff;
      *buffer++ = (png_byte)b;
      --count;
      write_byte(file, (png_byte)b); /* increments chunk_write */
   }
   while (count > 0);
}

/* Bundle the file and an uninitialized chunk and IDAT control structure
 * together to allow implementation of the chunk/IDAT allocate routine.
 */
struct control
{
   struct file  file;
   struct chunk chunk;
   struct IDAT  idat;
};

static int
control_end(struct control *control)
{
   return file_end(&control->file);
}

static struct file *
get_control(png_const_structrp png_ptr)
{
   /* This just returns the (file*).  The chunk and idat control structures
    * don't always exist.
    */
   struct control *control = voidcast(struct control*,
      png_get_error_ptr(png_ptr));
   return &control->file;
}

static void
allocate(struct file *file, int allocate_idat)
{
   struct control *control = voidcast(struct control*, file->alloc_ptr);

   if (allocate_idat)
   {
      assert(file->idat == NULL);
      IDAT_init(&control->idat, file);
   }

   else /* chunk */
   {
      assert(file->chunk == NULL);
      chunk_init(&control->chunk, file);
   }
}

static int
control_init(struct control *control, struct global *global,
   const char *file_name, const char *out_name)
   /* This wraps file_init(&control::file) and simply returns the result from
    * file_init.
    */
{
   return file_init(&control->file, global, file_name, out_name, control,
      allocate);
}

static int
read_png(struct control *control)
   /* Read a PNG, return 0 on success else an error (status) code; a bit mask as
    * defined for file::status_code as above.
    */
{
   png_structp png_ptr;
   png_infop info_ptr = NULL;
   volatile int rc;

   png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, control,
      error_handler, warning_handler);

   if (png_ptr == NULL)
   {
      /* This is not really expected. */
      log_error(&control->file, LIBPNG_ERROR_CODE, "OOM allocating png_struct");
      control->file.status_code |= INTERNAL_ERROR;
      return LIBPNG_ERROR_CODE;
   }

   rc = setjmp(control->file.jmpbuf);
   if (rc == 0)
   {
#     ifdef PNG_SET_USER_LIMITS_SUPPORTED
         /* Remove any limits on the size of PNG files that can be read,
          * without this we may reject files based on built-in safety
          * limits.
          */
         png_set_user_limits(png_ptr, 0x7fffffff, 0x7fffffff);
         png_set_chunk_cache_max(png_ptr, 0);
         png_set_chunk_malloc_max(png_ptr, 0);
#     endif

      png_set_read_fn(png_ptr, control, read_callback);

      info_ptr = png_create_info_struct(png_ptr);
      if (info_ptr == NULL)
         png_error(png_ptr, "OOM allocating info structure");

      if (control->file.global->verbose)
         fprintf(stderr, " INFO\n");

      png_read_info(png_ptr, info_ptr);

      {
        png_uint_32 height = png_get_image_height(png_ptr, info_ptr);
        int passes = png_set_interlace_handling(png_ptr);
        int pass;

        png_start_read_image(png_ptr);

        for (pass = 0; pass < passes; ++pass)
        {
           png_uint_32 y = height;

           /* NOTE: this skips asking libpng to return either version of
            * the image row, but libpng still reads the rows.
            */
           while (y-- > 0)
              png_read_row(png_ptr, NULL, NULL);
        }
      }

      if (control->file.global->verbose)
         fprintf(stderr, " END\n");

      /* Make sure to read to the end of the file: */
      png_read_end(png_ptr, info_ptr);
   }

   png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
   return rc;
}

static int
one_file(struct global *global, const char *file_name, const char *out_name)
{
   int rc;
   struct control control;

   if (global->verbose)
      fprintf(stderr, "FILE %s -> %s\n", file_name,
         out_name ? out_name : "<none>");

   /* Although control_init can return a failure code the structure is always
    * initialized, so control_end can be used to accumulate any status codes.
    */
   rc = control_init(&control, global, file_name, out_name);

   if (rc == 0)
      rc = read_png(&control);

   rc |= control_end(&control);

   return rc;
}

static void
usage(const char *prog)
{
   /* ANSI C-90 limits strings to 509 characters, so use a string array: */
   size_t i;
   static const char *usage_string[] = {
"  Tests, optimizes and optionally fixes the zlib header in PNG files.",
"  Optionally, when fixing, strips ancillary chunks from the file.",
0,
"OPTIONS",
"  OPERATION",
"      By default files are just checked for readability with a summary of the",
"      of zlib issues founds for each compressed chunk and the IDAT stream in",
"      the file.",
"    --optimize (-o):",
"      Find the smallest deflate window size for the compressed data.",
"    --strip=[none|crc|unsafe|unused|transform|color|all]:",
"        none (default):   Retain all chunks.",
"        crc:    Remove chunks with a bad CRC.",
"        unsafe: Remove chunks that may be unsafe to retain if the image data",
"                is modified.  This is set automatically if --max is given but",
"                may be cancelled by a later --strip=none.",
"        unused: Remove chunks not used by libpng when decoding an image.",
"                This retains any chunks that might be used by libpng image",
"                transformations.",
"        transform: unused+bKGD.",
"        color:  transform+iCCP and cHRM.",
"        all:    color+gAMA and sRGB.",
"      Only ancillary chunks are ever removed.  In addition the tRNS and sBIT",
"      chunks are never removed as they affect exact interpretation of the",
"      image pixel values.  The following known chunks are treated specially",
"      by the above options:",
"        gAMA, sRGB [all]: These specify the gamma encoding used for the pixel",
"            values.",
"        cHRM, iCCP [color]: These specify how colors are encoded.  iCCP also",
"            specifies the exact encoding of a pixel value; however, in",
"            practice most programs will ignore it.",
"        bKGD [transform]: This is used by libpng transforms."
"    --max=<number>:",
"      Use IDAT chunks sized <number>.  If no number is given the IDAT",
"      chunks will be the maximum size permitted; 2^31-1 bytes.  If the option",
"      is omitted the original chunk sizes will not be changed.  When the",
"      option is given --strip=unsafe is set automatically. This may be",
"      cancelled if you know that all unknown unsafe-to-copy chunks really are",
"      safe to copy across an IDAT size change.  This is true of all chunks",
"      that have ever been formally proposed as PNG extensions.",
"  MESSAGES",
"      By default the program only outputs summaries for each file.",
"    --quiet (-q):",
"      Do not output the summaries except for files that cannot be read. With",
"      two --quiets these are not output either.",
"    --errors (-e):",
"      Output errors from libpng and the program (except too-far-back).",
"    --warnings (-w):",
"      Output warnings from libpng.",
"  OUTPUT",
"      By default nothing is written.",
"    --out=<file>:",
"      Write the optimized/corrected version of the next PNG to <file>.  This",
"      overrides the following two options",
"    --suffix=<suffix>:",
"      Set --out=<name><suffix> for all following files unless overridden on",
"      a per-file basis by explicit --out.",
"    --prefix=<prefix>:",
"      Set --out=<prefix><name> for all the following files unless overridden",
"      on a per-file basis by explicit --out.",
"      These two options can be used together to produce a suffix and prefix.",
"  INTERNAL OPTIONS",
#if 0 /*NYI*/
#ifdef PNG_MAXIMUM_INFLATE_WINDOW
"    --test:",
"      Test the PNG_MAXIMUM_INFLATE_WINDOW option.  Setting this disables",
"      output as this would produce a broken file.",
#endif
#endif
0,
"EXIT CODES",
"  *** SUBJECT TO CHANGE ***",
"  The program exit code is value in the range 0..127 holding a bit mask of",
"  the following codes.  Notice that the results for each file are combined",
"  together - check one file at a time to get a meaningful error code!",
"    0x01: The zlib too-far-back error existed in at least one chunk.",
"    0x02: At least one chunk had a CRC error.",
"    0x04: A chunk length was incorrect.",
"    0x08: The file was truncated.",
"  Errors less than 16 are potentially recoverable, for a single file if the",
"  exit code is less than 16 the file could be read (with corrections if a",
"  non-zero code is returned).",
"    0x10: The file could not be read, even with corrections.",
"    0x20: The output file could not be written.",
"    0x40: An unexpected, potentially internal, error occurred.",
"  If the command line arguments are incorrect the program exits with exit",
"  255.  Some older operating systems only support 7-bit exit codes, on those",
"  systems it is suggested that this program is first tested by supplying",
"  invalid arguments.",
0,
"DESCRIPTION",
"  " PROGRAM_NAME ":",
"  checks each PNG file on the command line for errors.  By default errors are",
"  not output and the program just returns an exit code and prints a summary.",
"  With the --quiet (-q) option the summaries are suppressed too and the",
"  program only outputs unexpected errors (internal errors and file open",
"  errors).",
"  Various known problems in PNG files are fixed while the file is being read",
"  The exit code says what problems were fixed.  In particular the zlib error:",
0,
"        \"invalid distance too far back\"",
0,
"  caused by an incorrect optimization of a zlib stream is fixed in any",
"  compressed chunk in which it is encountered.  An integrity problem of the",
"  PNG stream caused by a bug in libpng which wrote an incorrect chunk length",
"  is also fixed.  Chunk CRC errors are automatically fixed up.",
0,
"  Setting one of the \"OUTPUT\" options causes the possibly modified file to",
"  be written to a new file.",
0,
"  Notice that some PNG files with the zlib optimization problem can still be",
"  read by libpng under some circumstances.  This program will still detect",
"  and, if requested, correct the error.",
0,
"  The program will reliably process all files on the command line unless",
"  either an invalid argument causes the usage message (this message) to be",
"  produced or the program crashes.",
0,
"  The summary lines describe issues encountered with the zlib compressed",
"  stream of a chunk.  They have the following format, which is SUBJECT TO",
"  CHANGE in the future:",
0,
"     chunk reason comp-level p1 p2 p3 p4 file",
0,
"  p1 through p4 vary according to the 'reason'.  There are always 8 space",
"  separated fields.  Reasons specific formats are:",
0,
"     chunk ERR status code read-errno write-errno message file",
"     chunk SKP comp-level file-bits zlib-rc compressed message file",
"     chunk ??? comp-level file-bits ok-bits compressed uncompress file",
0,
"  The various fields are",
0,
"$1 chunk:      The chunk type of a chunk in the file or 'HEAD' if a problem",
"               is reported by libpng at the start of the IDAT stream.",
"$2 reason:     One of:",
"          CHK: A zlib header checksum was detected and fixed.",
"          TFB: The zlib too far back error was detected and fixed.",
"          OK : No errors were detected in the zlib stream and optimization",
"               was not requested, or was not possible.",
"          OPT: The zlib stream window bits value could be improved (and was).",
"          SKP: The chunk was skipped because of a zlib issue (zlib-rc) with",
"               explanation 'message'",
"          ERR: The read of the file was aborted.  The parameters explain why.",
"$3 status:     For 'ERR' the accumulated status code from 'EXIT CODES' above.",
"               This is printed as a 2 digit hexadecimal value",
"   comp-level: The recorded compression level (FLEVEL) of a zlib stream",
"               expressed as a string {supfast,stdfast,default,maximum}",
"$4 code:       The file exit code; where stop was called, as a fairly terse",
"               string {warning,libpng,zlib,invalid,read,write,unexpected}.",
"   file-bits:  The zlib window bits recorded in the file.",
"$5 read-errno: A system errno value from a read translated by strerror(3).",
"   zlib-rc:    A zlib return code as a string (see zlib.h).",
"   ok-bits:    The smallest zlib window bits value that works.",
"$6 write-errno:A system errno value from a write translated by strerror(3).",
"   compressed: The count of compressed bytes in the zlib stream, when the",
"               reason is 'SKP'; this is a count of the bytes read from the",
"               stream when the fatal error was encountered.",
"$7 message:    An error message (spaces replaced by _, as in all parameters),",
"   uncompress: The count of bytes from uncompressing the zlib stream; this",
"               may not be the same as the number of bytes in the image.",
"$8 file:       The name of the file (this may contain spaces).",
};

   fprintf(stderr, "Usage: %s {[options] png-file}\n", prog);

   for (i=0; i < (sizeof usage_string)/(sizeof usage_string[0]); ++i)
   {
      if (usage_string[i] != 0)
         fputs(usage_string[i], stderr);

      fputc('\n', stderr);
   }

   exit(255);
}

int
main(int argc, const char **argv)
{
   char temp_name[FILENAME_MAX+1];
   const char *  prog = *argv;
   const char *  outfile = NULL;
   const char *  suffix = NULL;
   const char *  prefix = NULL;
   int           done = 0; /* if at least one file is processed */
   struct global global;

   global_init(&global);

   while (--argc > 0)
   {
      ++argv;

      if (strcmp(*argv, "--debug") == 0)
      {
         /* To help debugging problems: */
         global.errors = global.warnings = 1;
         global.quiet = 0;
         global.verbose = 7;
      }

      else if (strncmp(*argv, "--max=", 6) == 0)
      {
         global.idat_max = (png_uint_32)atol(6+*argv);

         if (global.skip < SKIP_UNSAFE)
            global.skip = SKIP_UNSAFE;
      }

      else if (strcmp(*argv, "--max") == 0)
      {
         global.idat_max = 0x7fffffff;

         if (global.skip < SKIP_UNSAFE)
            global.skip = SKIP_UNSAFE;
      }

      else if (strcmp(*argv, "--optimize") == 0 || strcmp(*argv, "-o") == 0)
         global.optimize_zlib = 1;

      else if (strncmp(*argv, "--out=", 6) == 0)
         outfile = 6+*argv;

      else if (strncmp(*argv, "--suffix=", 9) == 0)
         suffix = 9+*argv;

      else if (strncmp(*argv, "--prefix=", 9) == 0)
         prefix = 9+*argv;

      else if (strcmp(*argv, "--strip=none") == 0)
         global.skip = SKIP_NONE;

      else if (strcmp(*argv, "--strip=crc") == 0)
         global.skip = SKIP_BAD_CRC;

      else if (strcmp(*argv, "--strip=unsafe") == 0)
         global.skip = SKIP_UNSAFE;

      else if (strcmp(*argv, "--strip=unused") == 0)
         global.skip = SKIP_UNUSED;

      else if (strcmp(*argv, "--strip=transform") == 0)
         global.skip = SKIP_TRANSFORM;

      else if (strcmp(*argv, "--strip=color") == 0)
         global.skip = SKIP_COLOR;

      else if (strcmp(*argv, "--strip=all") == 0)
         global.skip = SKIP_ALL;

      else if (strcmp(*argv, "--errors") == 0 || strcmp(*argv, "-e") == 0)
         global.errors = 1;

      else if (strcmp(*argv, "--warnings") == 0 || strcmp(*argv, "-w") == 0)
         global.warnings = 1;

      else if (strcmp(*argv, "--quiet") == 0 || strcmp(*argv, "-q") == 0)
      {
         if (global.quiet)
            global.quiet = 2;

         else
            global.quiet = 1;
      }

      else if (strcmp(*argv, "--verbose") == 0 || strcmp(*argv, "-v") == 0)
         ++global.verbose;

#if 0
      /* NYI */
#     ifdef PNG_MAXIMUM_INFLATE_WINDOW
         else if (strcmp(*argv, "--test") == 0)
            ++set_option;
#     endif
#endif

      else if ((*argv)[0] == '-')
         usage(prog);

      else
      {
         size_t outlen = strlen(*argv);

         if (outfile == NULL) /* else this takes precedence */
         {
            /* Consider the prefix/suffix options */
            if (prefix != NULL)
            {
               size_t prefixlen = strlen(prefix);

               if (prefixlen+outlen > FILENAME_MAX)
               {
                  fprintf(stderr, "%s: output file name too long: %s%s%s\n",
                     prog, prefix, *argv, suffix ? suffix : "");
                  global.status_code |= WRITE_ERROR;
                  continue;
               }

               memcpy(temp_name, prefix, prefixlen);
               memcpy(temp_name+prefixlen, *argv, outlen);
               outlen += prefixlen;
               outfile = temp_name;
            }

            else if (suffix != NULL)
               memcpy(temp_name, *argv, outlen);

            temp_name[outlen] = 0;

            if (suffix != NULL)
            {
               size_t suffixlen = strlen(suffix);

               if (outlen+suffixlen > FILENAME_MAX)
               {
                  fprintf(stderr, "%s: output file name too long: %s%s\n",
                     prog, *argv, suffix);
                  global.status_code |= WRITE_ERROR;
                  continue;
               }

               memcpy(temp_name+outlen, suffix, suffixlen);
               outlen += suffixlen;
               temp_name[outlen] = 0;
               outfile = temp_name;
            }
         }

         (void)one_file(&global, *argv, outfile);
         ++done;
         outfile = NULL;
      }
   }

   if (!done)
      usage(prog);

   return global_end(&global);
}

#else /* ZLIB_VERNUM < 0x1240 */
int
main(void)
{
   fprintf(stderr,
      "pngfix needs libpng with a zlib >=1.2.4 (not 0x%x)\n",
      ZLIB_VERNUM);
   return 77;
}
#endif /* ZLIB_VERNUM */

#else /* No read support */

int
main(void)
{
   fprintf(stderr, "pngfix does not work without read deinterlace support\n");
   return 77;
}
#endif /* PNG_READ_SUPPORTED && PNG_EASY_ACCESS_SUPPORTED */
#else /* No setjmp support */
int
main(void)
{
   fprintf(stderr, "pngfix does not work without setjmp support\n");
   return 77;
}
#endif /* PNG_SETJMP_SUPPORTED */


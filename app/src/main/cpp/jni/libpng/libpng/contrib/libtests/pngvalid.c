
/* pngvalid.c - validate libpng by constructing then reading png files.
 *
 * Last changed in libpng 1.6.31 [July 27, 2017]
 * Copyright (c) 2014-2017 John Cunningham Bowler
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * NOTES:
 *   This is a C program that is intended to be linked against libpng.  It
 *   generates bitmaps internally, stores them as PNG files (using the
 *   sequential write code) then reads them back (using the sequential
 *   read code) and validates that the result has the correct data.
 *
 *   The program can be modified and extended to test the correctness of
 *   transformations performed by libpng.
 */

#define _POSIX_SOURCE 1
#define _ISOC99_SOURCE 1 /* For floating point */
#define _GNU_SOURCE 1 /* For the floating point exception extension */
#define _BSD_SOURCE 1 /* For the floating point exception extension */

#include <signal.h>
#include <stdio.h>

#if defined(HAVE_CONFIG_H) && !defined(PNG_NO_CONFIG_H)
#  include <config.h>
#endif

#ifdef HAVE_FEENABLEEXCEPT /* from config.h, if included */
#  include <fenv.h>
#endif

#ifndef FE_DIVBYZERO
#  define FE_DIVBYZERO 0
#endif
#ifndef FE_INVALID
#  define FE_INVALID 0
#endif
#ifndef FE_OVERFLOW
#  define FE_OVERFLOW 0
#endif

/* Define the following to use this test against your installed libpng, rather
 * than the one being built here:
 */
#ifdef PNG_FREESTANDING_TESTS
#  include <png.h>
#else
#  include "../../png.h"
#endif

#ifdef PNG_ZLIB_HEADER
#  include PNG_ZLIB_HEADER
#else
#  include <zlib.h>   /* For crc32 */
#endif

/* 1.6.1 added support for the configure test harness, which uses 77 to indicate
 * a skipped test, in earlier versions we need to succeed on a skipped test, so:
 */
#if PNG_LIBPNG_VER >= 10601 && defined(HAVE_CONFIG_H)
#  define SKIP 77
#else
#  define SKIP 0
#endif

/* pngvalid requires write support and one of the fixed or floating point APIs.
 */
#if defined(PNG_WRITE_SUPPORTED) &&\
   (defined(PNG_FIXED_POINT_SUPPORTED) || defined(PNG_FLOATING_POINT_SUPPORTED))

#if PNG_LIBPNG_VER < 10500
/* This deliberately lacks the const. */
typedef png_byte *png_const_bytep;

/* This is copied from 1.5.1 png.h: */
#define PNG_INTERLACE_ADAM7_PASSES 7
#define PNG_PASS_START_ROW(pass) (((1U&~(pass))<<(3-((pass)>>1)))&7)
#define PNG_PASS_START_COL(pass) (((1U& (pass))<<(3-(((pass)+1)>>1)))&7)
#define PNG_PASS_ROW_SHIFT(pass) ((pass)>2?(8-(pass))>>1:3)
#define PNG_PASS_COL_SHIFT(pass) ((pass)>1?(7-(pass))>>1:3)
#define PNG_PASS_ROWS(height, pass) (((height)+(((1<<PNG_PASS_ROW_SHIFT(pass))\
   -1)-PNG_PASS_START_ROW(pass)))>>PNG_PASS_ROW_SHIFT(pass))
#define PNG_PASS_COLS(width, pass) (((width)+(((1<<PNG_PASS_COL_SHIFT(pass))\
   -1)-PNG_PASS_START_COL(pass)))>>PNG_PASS_COL_SHIFT(pass))
#define PNG_ROW_FROM_PASS_ROW(yIn, pass) \
   (((yIn)<<PNG_PASS_ROW_SHIFT(pass))+PNG_PASS_START_ROW(pass))
#define PNG_COL_FROM_PASS_COL(xIn, pass) \
   (((xIn)<<PNG_PASS_COL_SHIFT(pass))+PNG_PASS_START_COL(pass))
#define PNG_PASS_MASK(pass,off) ( \
   ((0x110145AFU>>(((7-(off))-(pass))<<2)) & 0xFU) | \
   ((0x01145AF0U>>(((7-(off))-(pass))<<2)) & 0xF0U))
#define PNG_ROW_IN_INTERLACE_PASS(y, pass) \
   ((PNG_PASS_MASK(pass,0) >> ((y)&7)) & 1)
#define PNG_COL_IN_INTERLACE_PASS(x, pass) \
   ((PNG_PASS_MASK(pass,1) >> ((x)&7)) & 1)

/* These are needed too for the default build: */
#define PNG_WRITE_16BIT_SUPPORTED
#define PNG_READ_16BIT_SUPPORTED

/* This comes from pnglibconf.h after 1.5: */
#define PNG_FP_1 100000
#define PNG_GAMMA_THRESHOLD_FIXED\
   ((png_fixed_point)(PNG_GAMMA_THRESHOLD * PNG_FP_1))
#endif

#if PNG_LIBPNG_VER < 10600
   /* 1.6.0 constifies many APIs, the following exists to allow pngvalid to be
    * compiled against earlier versions.
    */
#  define png_const_structp png_structp
#endif

#ifndef RELEASE_BUILD
   /* RELEASE_BUILD is true for releases and release candidates: */
#  define RELEASE_BUILD (PNG_LIBPNG_BUILD_BASE_TYPE >= PNG_LIBPNG_BUILD_RC)
#endif
#if RELEASE_BUILD
#   define debugonly(something)
#else /* !RELEASE_BUILD */
#   define debugonly(something) something
#endif /* !RELEASE_BUILD */

#include <float.h>  /* For floating point constants */
#include <stdlib.h> /* For malloc */
#include <string.h> /* For memcpy, memset */
#include <math.h>   /* For floor */

/* Convenience macros. */
#define CHUNK(a,b,c,d) (((a)<<24)+((b)<<16)+((c)<<8)+(d))
#define CHUNK_IHDR CHUNK(73,72,68,82)
#define CHUNK_PLTE CHUNK(80,76,84,69)
#define CHUNK_IDAT CHUNK(73,68,65,84)
#define CHUNK_IEND CHUNK(73,69,78,68)
#define CHUNK_cHRM CHUNK(99,72,82,77)
#define CHUNK_gAMA CHUNK(103,65,77,65)
#define CHUNK_sBIT CHUNK(115,66,73,84)
#define CHUNK_sRGB CHUNK(115,82,71,66)

/* Unused formal parameter errors are removed using the following macro which is
 * expected to have no bad effects on performance.
 */
#ifndef UNUSED
#  if defined(__GNUC__) || defined(_MSC_VER)
#     define UNUSED(param) (void)param;
#  else
#     define UNUSED(param)
#  endif
#endif

/***************************** EXCEPTION HANDLING *****************************/
#ifdef PNG_FREESTANDING_TESTS
#  include <cexcept.h>
#else
#  include "../visupng/cexcept.h"
#endif

#ifdef __cplusplus
#  define this not_the_cpp_this
#  define new not_the_cpp_new
#  define voidcast(type, value) static_cast<type>(value)
#else
#  define voidcast(type, value) (value)
#endif /* __cplusplus */

struct png_store;
define_exception_type(struct png_store*);

/* The following are macros to reduce typing everywhere where the well known
 * name 'the_exception_context' must be defined.
 */
#define anon_context(ps) struct exception_context *the_exception_context = \
   &(ps)->exception_context
#define context(ps,fault) anon_context(ps); png_store *fault

/* This macro returns the number of elements in an array as an (unsigned int),
 * it is necessary to avoid the inability of certain versions of GCC to use
 * the value of a compile-time constant when performing range checks.  It must
 * be passed an array name.
 */
#define ARRAY_SIZE(a) ((unsigned int)((sizeof (a))/(sizeof (a)[0])))

/* GCC BUG 66447 (https://gcc.gnu.org/bugzilla/show_bug.cgi?id=66447) requires
 * some broken GCC versions to be fixed up to avoid invalid whining about auto
 * variables that are *not* changed within the scope of a setjmp being changed.
 *
 * Feel free to extend the list of broken versions.
 */
#define is_gnu(major,minor)\
   (defined __GNUC__) && __GNUC__ == (major) && __GNUC_MINOR__ == (minor)
#define is_gnu_patch(major,minor,patch)\
   is_gnu(major,minor) && __GNUC_PATCHLEVEL__ == 0
/* For the moment just do it always; all versions of GCC seem to be broken: */
#ifdef __GNUC__
   const void * volatile make_volatile_for_gnu;
#  define gnu_volatile(x) make_volatile_for_gnu = &x;
#else /* !GNUC broken versions */
#  define gnu_volatile(x)
#endif /* !GNUC broken versions */

/******************************* UTILITIES ************************************/
/* Error handling is particularly problematic in production code - error
 * handlers often themselves have bugs which lead to programs that detect
 * minor errors crashing.  The following functions deal with one very
 * common class of errors in error handlers - attempting to format error or
 * warning messages into buffers that are too small.
 */
static size_t safecat(char *buffer, size_t bufsize, size_t pos,
   const char *cat)
{
   while (pos < bufsize && cat != NULL && *cat != 0)
      buffer[pos++] = *cat++;

   if (pos >= bufsize)
      pos = bufsize-1;

   buffer[pos] = 0;
   return pos;
}

static size_t safecatn(char *buffer, size_t bufsize, size_t pos, int n)
{
   char number[64];
   sprintf(number, "%d", n);
   return safecat(buffer, bufsize, pos, number);
}

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
static size_t safecatd(char *buffer, size_t bufsize, size_t pos, double d,
    int precision)
{
   char number[64];
   sprintf(number, "%.*f", precision, d);
   return safecat(buffer, bufsize, pos, number);
}
#endif

static const char invalid[] = "invalid";
static const char sep[] = ": ";

static const char *colour_types[8] =
{
   "grayscale", invalid, "truecolour", "indexed-colour",
   "grayscale with alpha", invalid, "truecolour with alpha", invalid
};

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
/* Convert a double precision value to fixed point. */
static png_fixed_point
fix(double d)
{
   d = floor(d * PNG_FP_1 + .5);
   return (png_fixed_point)d;
}
#endif /* PNG_READ_SUPPORTED */

/* Generate random bytes.  This uses a boring repeatable algorithm and it
 * is implemented here so that it gives the same set of numbers on every
 * architecture.  It's a linear congruential generator (Knuth or Sedgewick
 * "Algorithms") but it comes from the 'feedback taps' table in Horowitz and
 * Hill, "The Art of Electronics" (Pseudo-Random Bit Sequences and Noise
 * Generation.)
 */
static void
make_random_bytes(png_uint_32* seed, void* pv, size_t size)
{
   png_uint_32 u0 = seed[0], u1 = seed[1];
   png_bytep bytes = voidcast(png_bytep, pv);

   /* There are thirty three bits, the next bit in the sequence is bit-33 XOR
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

static void
make_four_random_bytes(png_uint_32* seed, png_bytep bytes)
{
   make_random_bytes(seed, bytes, 4);
}

#if defined PNG_READ_SUPPORTED || defined PNG_WRITE_tRNS_SUPPORTED ||\
    defined PNG_WRITE_FILTER_SUPPORTED
static void
randomize(void *pv, size_t size)
{
   static png_uint_32 random_seed[2] = {0x56789abc, 0xd};
   make_random_bytes(random_seed, pv, size);
}

#define R8(this) randomize(&(this), sizeof (this))

#ifdef PNG_READ_SUPPORTED
static png_byte
random_byte(void)
{
   unsigned char b1[1];
   randomize(b1, sizeof b1);
   return b1[0];
}
#endif /* READ */

static png_uint_16
random_u16(void)
{
   unsigned char b2[2];
   randomize(b2, sizeof b2);
   return png_get_uint_16(b2);
}

#if defined PNG_READ_RGB_TO_GRAY_SUPPORTED ||\
    defined PNG_READ_FILLER_SUPPORTED
static png_uint_32
random_u32(void)
{
   unsigned char b4[4];
   randomize(b4, sizeof b4);
   return png_get_uint_32(b4);
}
#endif /* READ_FILLER || READ_RGB_TO_GRAY */

#endif /* READ || WRITE_tRNS || WRITE_FILTER */

#if defined PNG_READ_TRANSFORMS_SUPPORTED ||\
    defined PNG_WRITE_FILTER_SUPPORTED
static unsigned int
random_mod(unsigned int max)
{
   return random_u16() % max; /* 0 .. max-1 */
}
#endif /* READ_TRANSFORMS || WRITE_FILTER */

#if (defined PNG_READ_RGB_TO_GRAY_SUPPORTED) ||\
    (defined PNG_READ_FILLER_SUPPORTED)
static int
random_choice(void)
{
   return random_byte() & 1;
}
#endif /* READ_RGB_TO_GRAY || READ_FILLER */

/* A numeric ID based on PNG file characteristics.  The 'do_interlace' field
 * simply records whether pngvalid did the interlace itself or whether it
 * was done by libpng.  Width and height must be less than 256.  'palette' is an
 * index of the palette to use for formats with a palette otherwise a boolean
 * indicating if a tRNS chunk was generated.
 */
#define FILEID(col, depth, palette, interlace, width, height, do_interlace) \
   ((png_uint_32)((col) + ((depth)<<3) + ((palette)<<8) + ((interlace)<<13) + \
    (((do_interlace)!=0)<<15) + ((width)<<16) + ((height)<<24)))

#define COL_FROM_ID(id) ((png_byte)((id)& 0x7U))
#define DEPTH_FROM_ID(id) ((png_byte)(((id) >> 3) & 0x1fU))
#define PALETTE_FROM_ID(id) (((id) >> 8) & 0x1f)
#define INTERLACE_FROM_ID(id) ((png_byte)(((id) >> 13) & 0x3))
#define DO_INTERLACE_FROM_ID(id) ((int)(((id)>>15) & 1))
#define WIDTH_FROM_ID(id) (((id)>>16) & 0xff)
#define HEIGHT_FROM_ID(id) (((id)>>24) & 0xff)

/* Utility to construct a standard name for a standard image. */
static size_t
standard_name(char *buffer, size_t bufsize, size_t pos, png_byte colour_type,
    int bit_depth, unsigned int npalette, int interlace_type,
    png_uint_32 w, png_uint_32 h, int do_interlace)
{
   pos = safecat(buffer, bufsize, pos, colour_types[colour_type]);
   if (colour_type == 3) /* must have a palette */
   {
      pos = safecat(buffer, bufsize, pos, "[");
      pos = safecatn(buffer, bufsize, pos, npalette);
      pos = safecat(buffer, bufsize, pos, "]");
   }

   else if (npalette != 0)
      pos = safecat(buffer, bufsize, pos, "+tRNS");

   pos = safecat(buffer, bufsize, pos, " ");
   pos = safecatn(buffer, bufsize, pos, bit_depth);
   pos = safecat(buffer, bufsize, pos, " bit");

   if (interlace_type != PNG_INTERLACE_NONE)
   {
      pos = safecat(buffer, bufsize, pos, " interlaced");
      if (do_interlace)
         pos = safecat(buffer, bufsize, pos, "(pngvalid)");
      else
         pos = safecat(buffer, bufsize, pos, "(libpng)");
   }

   if (w > 0 || h > 0)
   {
      pos = safecat(buffer, bufsize, pos, " ");
      pos = safecatn(buffer, bufsize, pos, w);
      pos = safecat(buffer, bufsize, pos, "x");
      pos = safecatn(buffer, bufsize, pos, h);
   }

   return pos;
}

static size_t
standard_name_from_id(char *buffer, size_t bufsize, size_t pos, png_uint_32 id)
{
   return standard_name(buffer, bufsize, pos, COL_FROM_ID(id),
      DEPTH_FROM_ID(id), PALETTE_FROM_ID(id), INTERLACE_FROM_ID(id),
      WIDTH_FROM_ID(id), HEIGHT_FROM_ID(id), DO_INTERLACE_FROM_ID(id));
}

/* Convenience API and defines to list valid formats.  Note that 16 bit read and
 * write support is required to do 16 bit read tests (we must be able to make a
 * 16 bit image to test!)
 */
#ifdef PNG_WRITE_16BIT_SUPPORTED
#  define WRITE_BDHI 4
#  ifdef PNG_READ_16BIT_SUPPORTED
#     define READ_BDHI 4
#     define DO_16BIT
#  endif
#else
#  define WRITE_BDHI 3
#endif
#ifndef DO_16BIT
#  define READ_BDHI 3
#endif

/* The following defines the number of different palettes to generate for
 * each log bit depth of a colour type 3 standard image.
 */
#define PALETTE_COUNT(bit_depth) ((bit_depth) > 4 ? 1U : 16U)

static int
next_format(png_bytep colour_type, png_bytep bit_depth,
   unsigned int* palette_number, int low_depth_gray, int tRNS)
{
   if (*bit_depth == 0)
   {
      *colour_type = 0;
      if (low_depth_gray)
         *bit_depth = 1;
      else
         *bit_depth = 8;
      *palette_number = 0;
      return 1;
   }

   if  (*colour_type < 4/*no alpha channel*/)
   {
      /* Add multiple palettes for colour type 3, one image with tRNS
       * and one without for other non-alpha formats:
       */
      unsigned int pn = ++*palette_number;
      png_byte ct = *colour_type;

      if (((ct == 0/*GRAY*/ || ct/*RGB*/ == 2) && tRNS && pn < 2) ||
          (ct == 3/*PALETTE*/ && pn < PALETTE_COUNT(*bit_depth)))
         return 1;

      /* No: next bit depth */
      *palette_number = 0;
   }

   *bit_depth = (png_byte)(*bit_depth << 1);

   /* Palette images are restricted to 8 bit depth */
   if (*bit_depth <= 8
#ifdef DO_16BIT
         || (*colour_type != 3 && *bit_depth <= 16)
#endif
      )
      return 1;

   /* Move to the next color type, or return 0 at the end. */
   switch (*colour_type)
   {
      case 0:
         *colour_type = 2;
         *bit_depth = 8;
         return 1;

      case 2:
         *colour_type = 3;
         *bit_depth = 1;
         return 1;

      case 3:
         *colour_type = 4;
         *bit_depth = 8;
         return 1;

      case 4:
         *colour_type = 6;
         *bit_depth = 8;
         return 1;

      default:
         return 0;
   }
}

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
static unsigned int
sample(png_const_bytep row, png_byte colour_type, png_byte bit_depth,
    png_uint_32 x, unsigned int sample_index, int swap16, int littleendian)
{
   png_uint_32 bit_index, result;

   /* Find a sample index for the desired sample: */
   x *= bit_depth;
   bit_index = x;

   if ((colour_type & 1) == 0) /* !palette */
   {
      if (colour_type & 2)
         bit_index *= 3;

      if (colour_type & 4)
         bit_index += x; /* Alpha channel */

      /* Multiple channels; select one: */
      if (colour_type & (2+4))
         bit_index += sample_index * bit_depth;
   }

   /* Return the sample from the row as an integer. */
   row += bit_index >> 3;
   result = *row;

   if (bit_depth == 8)
      return result;

   else if (bit_depth > 8)
   {
      if (swap16)
         return (*++row << 8) + result;
      else
         return (result << 8) + *++row;
   }

   /* Less than 8 bits per sample.  By default PNG has the big end of
    * the egg on the left of the screen, but if littleendian is set
    * then the big end is on the right.
    */
   bit_index &= 7;

   if (!littleendian)
      bit_index = 8-bit_index-bit_depth;

   return (result >> bit_index) & ((1U<<bit_depth)-1);
}
#endif /* PNG_READ_TRANSFORMS_SUPPORTED */

/* Copy a single pixel, of a given size, from one buffer to another -
 * while this is basically bit addressed there is an implicit assumption
 * that pixels 8 or more bits in size are byte aligned and that pixels
 * do not otherwise cross byte boundaries.  (This is, so far as I know,
 * universally true in bitmap computer graphics.  [JCB 20101212])
 *
 * NOTE: The to and from buffers may be the same.
 */
static void
pixel_copy(png_bytep toBuffer, png_uint_32 toIndex,
   png_const_bytep fromBuffer, png_uint_32 fromIndex, unsigned int pixelSize,
   int littleendian)
{
   /* Assume we can multiply by 'size' without overflow because we are
    * just working in a single buffer.
    */
   toIndex *= pixelSize;
   fromIndex *= pixelSize;
   if (pixelSize < 8) /* Sub-byte */
   {
      /* Mask to select the location of the copied pixel: */
      unsigned int destMask = ((1U<<pixelSize)-1) <<
         (littleendian ? toIndex&7 : 8-pixelSize-(toIndex&7));
      /* The following read the entire pixels and clears the extra: */
      unsigned int destByte = toBuffer[toIndex >> 3] & ~destMask;
      unsigned int sourceByte = fromBuffer[fromIndex >> 3];

      /* Don't rely on << or >> supporting '0' here, just in case: */
      fromIndex &= 7;
      if (littleendian)
      {
         if (fromIndex > 0) sourceByte >>= fromIndex;
         if ((toIndex & 7) > 0) sourceByte <<= toIndex & 7;
      }

      else
      {
         if (fromIndex > 0) sourceByte <<= fromIndex;
         if ((toIndex & 7) > 0) sourceByte >>= toIndex & 7;
      }

      toBuffer[toIndex >> 3] = (png_byte)(destByte | (sourceByte & destMask));
   }
   else /* One or more bytes */
      memmove(toBuffer+(toIndex>>3), fromBuffer+(fromIndex>>3), pixelSize>>3);
}

#ifdef PNG_READ_SUPPORTED
/* Copy a complete row of pixels, taking into account potential partial
 * bytes at the end.
 */
static void
row_copy(png_bytep toBuffer, png_const_bytep fromBuffer, unsigned int bitWidth,
      int littleendian)
{
   memcpy(toBuffer, fromBuffer, bitWidth >> 3);

   if ((bitWidth & 7) != 0)
   {
      unsigned int mask;

      toBuffer += bitWidth >> 3;
      fromBuffer += bitWidth >> 3;
      if (littleendian)
         mask = 0xff << (bitWidth & 7);
      else
         mask = 0xff >> (bitWidth & 7);
      *toBuffer = (png_byte)((*toBuffer & mask) | (*fromBuffer & ~mask));
   }
}

/* Compare pixels - they are assumed to start at the first byte in the
 * given buffers.
 */
static int
pixel_cmp(png_const_bytep pa, png_const_bytep pb, png_uint_32 bit_width)
{
#if PNG_LIBPNG_VER < 10506
   if (memcmp(pa, pb, bit_width>>3) == 0)
   {
      png_uint_32 p;

      if ((bit_width & 7) == 0) return 0;

      /* Ok, any differences? */
      p = pa[bit_width >> 3];
      p ^= pb[bit_width >> 3];

      if (p == 0) return 0;

      /* There are, but they may not be significant, remove the bits
       * after the end (the low order bits in PNG.)
       */
      bit_width &= 7;
      p >>= 8-bit_width;

      if (p == 0) return 0;
   }
#else
   /* From libpng-1.5.6 the overwrite should be fixed, so compare the trailing
    * bits too:
    */
   if (memcmp(pa, pb, (bit_width+7)>>3) == 0)
      return 0;
#endif

   /* Return the index of the changed byte. */
   {
      png_uint_32 where = 0;

      while (pa[where] == pb[where]) ++where;
      return 1+where;
   }
}
#endif /* PNG_READ_SUPPORTED */

/*************************** BASIC PNG FILE WRITING ***************************/
/* A png_store takes data from the sequential writer or provides data
 * to the sequential reader.  It can also store the result of a PNG
 * write for later retrieval.
 */
#define STORE_BUFFER_SIZE 500 /* arbitrary */
typedef struct png_store_buffer
{
   struct png_store_buffer*  prev;    /* NOTE: stored in reverse order */
   png_byte                  buffer[STORE_BUFFER_SIZE];
} png_store_buffer;

#define FILE_NAME_SIZE 64

typedef struct store_palette_entry /* record of a single palette entry */
{
   png_byte red;
   png_byte green;
   png_byte blue;
   png_byte alpha;
} store_palette_entry, store_palette[256];

typedef struct png_store_file
{
   struct png_store_file*  next;      /* as many as you like... */
   char                    name[FILE_NAME_SIZE];
   unsigned int            IDAT_bits; /* Number of bits in IDAT size */
   png_uint_32             IDAT_size; /* Total size of IDAT data */
   png_uint_32             id;        /* must be correct (see FILEID) */
   size_t                  datacount; /* In this (the last) buffer */
   png_store_buffer        data;      /* Last buffer in file */
   int                     npalette;  /* Number of entries in palette */
   store_palette_entry*    palette;   /* May be NULL */
} png_store_file;

/* The following is a pool of memory allocated by a single libpng read or write
 * operation.
 */
typedef struct store_pool
{
   struct png_store    *store;   /* Back pointer */
   struct store_memory *list;    /* List of allocated memory */
   png_byte             mark[4]; /* Before and after data */

   /* Statistics for this run. */
   png_alloc_size_t     max;     /* Maximum single allocation */
   png_alloc_size_t     current; /* Current allocation */
   png_alloc_size_t     limit;   /* Highest current allocation */
   png_alloc_size_t     total;   /* Total allocation */

   /* Overall statistics (retained across successive runs). */
   png_alloc_size_t     max_max;
   png_alloc_size_t     max_limit;
   png_alloc_size_t     max_total;
} store_pool;

typedef struct png_store
{
   /* For cexcept.h exception handling - simply store one of these;
    * the context is a self pointer but it may point to a different
    * png_store (in fact it never does in this program.)
    */
   struct exception_context
                      exception_context;

   unsigned int       verbose :1;
   unsigned int       treat_warnings_as_errors :1;
   unsigned int       expect_error :1;
   unsigned int       expect_warning :1;
   unsigned int       saw_warning :1;
   unsigned int       speed :1;
   unsigned int       progressive :1; /* use progressive read */
   unsigned int       validated :1;   /* used as a temporary flag */
   int                nerrors;
   int                nwarnings;
   int                noptions;       /* number of options below: */
   struct {
      unsigned char   option;         /* option number, 0..30 */
      unsigned char   setting;        /* setting (unset,invalid,on,off) */
   }                  options[16];
   char               test[128];      /* Name of test */
   char               error[256];

   /* Share fields */
   png_uint_32        chunklen; /* Length of chunk+overhead (chunkpos >= 8) */
   png_uint_32        chunktype;/* Type of chunk (valid if chunkpos >= 4) */
   png_uint_32        chunkpos; /* Position in chunk */
   png_uint_32        IDAT_size;/* Accumulated IDAT size in .new */
   unsigned int       IDAT_bits;/* Cache of the file store value */

   /* Read fields */
   png_structp        pread;    /* Used to read a saved file */
   png_infop          piread;
   png_store_file*    current;  /* Set when reading */
   png_store_buffer*  next;     /* Set when reading */
   size_t             readpos;  /* Position in *next */
   png_byte*          image;    /* Buffer for reading interlaced images */
   size_t             cb_image; /* Size of this buffer */
   size_t             cb_row;   /* Row size of the image(s) */
   uLong              IDAT_crc;
   png_uint_32        IDAT_len; /* Used when re-chunking IDAT chunks */
   png_uint_32        IDAT_pos; /* Used when re-chunking IDAT chunks */
   png_uint_32        image_h;  /* Number of rows in a single image */
   store_pool         read_memory_pool;

   /* Write fields */
   png_store_file*    saved;
   png_structp        pwrite;   /* Used when writing a new file */
   png_infop          piwrite;
   size_t             writepos; /* Position in .new */
   char               wname[FILE_NAME_SIZE];
   png_store_buffer   new;      /* The end of the new PNG file being written. */
   store_pool         write_memory_pool;
   store_palette_entry* palette;
   int                  npalette;
} png_store;

/* Initialization and cleanup */
static void
store_pool_mark(png_bytep mark)
{
   static png_uint_32 store_seed[2] = { 0x12345678, 1};

   make_four_random_bytes(store_seed, mark);
}

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
/* Use this for random 32 bit values; this function makes sure the result is
 * non-zero.
 */
static png_uint_32
random_32(void)
{

   for (;;)
   {
      png_byte mark[4];
      png_uint_32 result;

      store_pool_mark(mark);
      result = png_get_uint_32(mark);

      if (result != 0)
         return result;
   }
}
#endif /* PNG_READ_SUPPORTED */

static void
store_pool_init(png_store *ps, store_pool *pool)
{
   memset(pool, 0, sizeof *pool);

   pool->store = ps;
   pool->list = NULL;
   pool->max = pool->current = pool->limit = pool->total = 0;
   pool->max_max = pool->max_limit = pool->max_total = 0;
   store_pool_mark(pool->mark);
}

static void
store_init(png_store* ps)
{
   memset(ps, 0, sizeof *ps);
   init_exception_context(&ps->exception_context);
   store_pool_init(ps, &ps->read_memory_pool);
   store_pool_init(ps, &ps->write_memory_pool);
   ps->verbose = 0;
   ps->treat_warnings_as_errors = 0;
   ps->expect_error = 0;
   ps->expect_warning = 0;
   ps->saw_warning = 0;
   ps->speed = 0;
   ps->progressive = 0;
   ps->validated = 0;
   ps->nerrors = ps->nwarnings = 0;
   ps->pread = NULL;
   ps->piread = NULL;
   ps->saved = ps->current = NULL;
   ps->next = NULL;
   ps->readpos = 0;
   ps->image = NULL;
   ps->cb_image = 0;
   ps->cb_row = 0;
   ps->image_h = 0;
   ps->pwrite = NULL;
   ps->piwrite = NULL;
   ps->writepos = 0;
   ps->chunkpos = 8;
   ps->chunktype = 0;
   ps->chunklen = 16;
   ps->IDAT_size = 0;
   ps->IDAT_bits = 0;
   ps->new.prev = NULL;
   ps->palette = NULL;
   ps->npalette = 0;
   ps->noptions = 0;
}

static void
store_freebuffer(png_store_buffer* psb)
{
   if (psb->prev)
   {
      store_freebuffer(psb->prev);
      free(psb->prev);
      psb->prev = NULL;
   }
}

static void
store_freenew(png_store *ps)
{
   store_freebuffer(&ps->new);
   ps->writepos = 0;
   ps->chunkpos = 8;
   ps->chunktype = 0;
   ps->chunklen = 16;
   ps->IDAT_size = 0;
   ps->IDAT_bits = 0;
   if (ps->palette != NULL)
   {
      free(ps->palette);
      ps->palette = NULL;
      ps->npalette = 0;
   }
}

static void
store_storenew(png_store *ps)
{
   png_store_buffer *pb;

   pb = voidcast(png_store_buffer*, malloc(sizeof *pb));

   if (pb == NULL)
      png_error(ps->pwrite, "store new: OOM");

   *pb = ps->new;
   ps->new.prev = pb;
   ps->writepos = 0;
}

static void
store_freefile(png_store_file **ppf)
{
   if (*ppf != NULL)
   {
      store_freefile(&(*ppf)->next);

      store_freebuffer(&(*ppf)->data);
      (*ppf)->datacount = 0;
      if ((*ppf)->palette != NULL)
      {
         free((*ppf)->palette);
         (*ppf)->palette = NULL;
         (*ppf)->npalette = 0;
      }
      free(*ppf);
      *ppf = NULL;
   }
}

static unsigned int
bits_of(png_uint_32 num)
{
   /* Return the number of bits in 'num' */
   unsigned int b = 0;

   if (num & 0xffff0000U)  b += 16U, num >>= 16;
   if (num & 0xff00U)      b += 8U, num >>= 8;
   if (num & 0xf0U)        b += 4U, num >>= 4;
   if (num & 0xcU)         b += 2U, num >>= 2;
   if (num & 0x2U)         ++b, num >>= 1;
   if (num)                ++b;

   return b; /* 0..32 */
}

/* Main interface to file storage, after writing a new PNG file (see the API
 * below) call store_storefile to store the result with the given name and id.
 */
static void
store_storefile(png_store *ps, png_uint_32 id)
{
   png_store_file *pf;

   if (ps->chunkpos != 0U || ps->chunktype != 0U || ps->chunklen != 0U ||
       ps->IDAT_size == 0)
      png_error(ps->pwrite, "storefile: incomplete write");

   pf = voidcast(png_store_file*, malloc(sizeof *pf));
   if (pf == NULL)
      png_error(ps->pwrite, "storefile: OOM");
   safecat(pf->name, sizeof pf->name, 0, ps->wname);
   pf->id = id;
   pf->data = ps->new;
   pf->datacount = ps->writepos;
   pf->IDAT_size = ps->IDAT_size;
   pf->IDAT_bits = bits_of(ps->IDAT_size);
   /* Because the IDAT always has zlib header stuff this must be true: */
   if (pf->IDAT_bits == 0U)
      png_error(ps->pwrite, "storefile: 0 sized IDAT");
   ps->new.prev = NULL;
   ps->writepos = 0;
   ps->chunkpos = 8;
   ps->chunktype = 0;
   ps->chunklen = 16;
   ps->IDAT_size = 0;
   pf->palette = ps->palette;
   pf->npalette = ps->npalette;
   ps->palette = 0;
   ps->npalette = 0;

   /* And save it. */
   pf->next = ps->saved;
   ps->saved = pf;
}

/* Generate an error message (in the given buffer) */
static size_t
store_message(png_store *ps, png_const_structp pp, char *buffer, size_t bufsize,
   size_t pos, const char *msg)
{
   if (pp != NULL && pp == ps->pread)
   {
      /* Reading a file */
      pos = safecat(buffer, bufsize, pos, "read: ");

      if (ps->current != NULL)
      {
         pos = safecat(buffer, bufsize, pos, ps->current->name);
         pos = safecat(buffer, bufsize, pos, sep);
      }
   }

   else if (pp != NULL && pp == ps->pwrite)
   {
      /* Writing a file */
      pos = safecat(buffer, bufsize, pos, "write: ");
      pos = safecat(buffer, bufsize, pos, ps->wname);
      pos = safecat(buffer, bufsize, pos, sep);
   }

   else
   {
      /* Neither reading nor writing (or a memory error in struct delete) */
      pos = safecat(buffer, bufsize, pos, "pngvalid: ");
   }

   if (ps->test[0] != 0)
   {
      pos = safecat(buffer, bufsize, pos, ps->test);
      pos = safecat(buffer, bufsize, pos, sep);
   }
   pos = safecat(buffer, bufsize, pos, msg);
   return pos;
}

/* Verbose output to the error stream: */
static void
store_verbose(png_store *ps, png_const_structp pp, png_const_charp prefix,
   png_const_charp message)
{
   char buffer[512];

   if (prefix)
      fputs(prefix, stderr);

   (void)store_message(ps, pp, buffer, sizeof buffer, 0, message);
   fputs(buffer, stderr);
   fputc('\n', stderr);
}

/* Log an error or warning - the relevant count is always incremented. */
static void
store_log(png_store* ps, png_const_structp pp, png_const_charp message,
   int is_error)
{
   /* The warning is copied to the error buffer if there are no errors and it is
    * the first warning.  The error is copied to the error buffer if it is the
    * first error (overwriting any prior warnings).
    */
   if (is_error ? (ps->nerrors)++ == 0 :
       (ps->nwarnings)++ == 0 && ps->nerrors == 0)
      store_message(ps, pp, ps->error, sizeof ps->error, 0, message);

   if (ps->verbose)
      store_verbose(ps, pp, is_error ? "error: " : "warning: ", message);
}

#ifdef PNG_READ_SUPPORTED
/* Internal error function, called with a png_store but no libpng stuff. */
static void
internal_error(png_store *ps, png_const_charp message)
{
   store_log(ps, NULL, message, 1 /* error */);

   /* And finally throw an exception. */
   {
      struct exception_context *the_exception_context = &ps->exception_context;
      Throw ps;
   }
}
#endif /* PNG_READ_SUPPORTED */

/* Functions to use as PNG callbacks. */
static void PNGCBAPI
store_error(png_structp ppIn, png_const_charp message) /* PNG_NORETURN */
{
   png_const_structp pp = ppIn;
   png_store *ps = voidcast(png_store*, png_get_error_ptr(pp));

   if (!ps->expect_error)
      store_log(ps, pp, message, 1 /* error */);

   /* And finally throw an exception. */
   {
      struct exception_context *the_exception_context = &ps->exception_context;
      Throw ps;
   }
}

static void PNGCBAPI
store_warning(png_structp ppIn, png_const_charp message)
{
   png_const_structp pp = ppIn;
   png_store *ps = voidcast(png_store*, png_get_error_ptr(pp));

   if (!ps->expect_warning)
      store_log(ps, pp, message, 0 /* warning */);
   else
      ps->saw_warning = 1;
}

/* These somewhat odd functions are used when reading an image to ensure that
 * the buffer is big enough, the png_structp is for errors.
 */
/* Return a single row from the correct image. */
static png_bytep
store_image_row(const png_store* ps, png_const_structp pp, int nImage,
   png_uint_32 y)
{
   size_t coffset = (nImage * ps->image_h + y) * (ps->cb_row + 5) + 2;

   if (ps->image == NULL)
      png_error(pp, "no allocated image");

   if (coffset + ps->cb_row + 3 > ps->cb_image)
      png_error(pp, "image too small");

   return ps->image + coffset;
}

static void
store_image_free(png_store *ps, png_const_structp pp)
{
   if (ps->image != NULL)
   {
      png_bytep image = ps->image;

      if (image[-1] != 0xed || image[ps->cb_image] != 0xfe)
      {
         if (pp != NULL)
            png_error(pp, "png_store image overwrite (1)");
         else
            store_log(ps, NULL, "png_store image overwrite (2)", 1);
      }

      ps->image = NULL;
      ps->cb_image = 0;
      --image;
      free(image);
   }
}

static void
store_ensure_image(png_store *ps, png_const_structp pp, int nImages,
   size_t cbRow, png_uint_32 cRows)
{
   size_t cb = nImages * cRows * (cbRow + 5);

   if (ps->cb_image < cb)
   {
      png_bytep image;

      store_image_free(ps, pp);

      /* The buffer is deliberately mis-aligned. */
      image = voidcast(png_bytep, malloc(cb+2));
      if (image == NULL)
      {
         /* Called from the startup - ignore the error for the moment. */
         if (pp == NULL)
            return;

         png_error(pp, "OOM allocating image buffer");
      }

      /* These magic tags are used to detect overwrites above. */
      ++image;
      image[-1] = 0xed;
      image[cb] = 0xfe;

      ps->image = image;
      ps->cb_image = cb;
   }

   /* We have an adequate sized image; lay out the rows.  There are 2 bytes at
    * the start and three at the end of each (this ensures that the row
    * alignment starts out odd - 2+1 and changes for larger images on each row.)
    */
   ps->cb_row = cbRow;
   ps->image_h = cRows;

   /* For error checking, the whole buffer is set to 10110010 (0xb2 - 178).
    * This deliberately doesn't match the bits in the size test image which are
    * outside the image; these are set to 0xff (all 1).  To make the row
    * comparison work in the 'size' test case the size rows are pre-initialized
    * to the same value prior to calling 'standard_row'.
    */
   memset(ps->image, 178, cb);

   /* Then put in the marks. */
   while (--nImages >= 0)
   {
      png_uint_32 y;

      for (y=0; y<cRows; ++y)
      {
         png_bytep row = store_image_row(ps, pp, nImages, y);

         /* The markers: */
         row[-2] = 190;
         row[-1] = 239;
         row[cbRow] = 222;
         row[cbRow+1] = 173;
         row[cbRow+2] = 17;
      }
   }
}

#ifdef PNG_READ_SUPPORTED
static void
store_image_check(const png_store* ps, png_const_structp pp, int iImage)
{
   png_const_bytep image = ps->image;

   if (image[-1] != 0xed || image[ps->cb_image] != 0xfe)
      png_error(pp, "image overwrite");
   else
   {
      size_t cbRow = ps->cb_row;
      png_uint_32 rows = ps->image_h;

      image += iImage * (cbRow+5) * ps->image_h;

      image += 2; /* skip image first row markers */

      for (; rows > 0; --rows)
      {
         if (image[-2] != 190 || image[-1] != 239)
            png_error(pp, "row start overwritten");

         if (image[cbRow] != 222 || image[cbRow+1] != 173 ||
            image[cbRow+2] != 17)
            png_error(pp, "row end overwritten");

         image += cbRow+5;
      }
   }
}
#endif /* PNG_READ_SUPPORTED */

static int
valid_chunktype(png_uint_32 chunktype)
{
   /* Each byte in the chunk type must be in one of the ranges 65..90, 97..122
    * (both inclusive), so:
    */
   unsigned int i;

   for (i=0; i<4; ++i)
   {
      unsigned int c = chunktype & 0xffU;

      if (!((c >= 65U && c <= 90U) || (c >= 97U && c <= 122U)))
         return 0;

      chunktype >>= 8;
   }

   return 1; /* It's valid */
}

static void PNGCBAPI
store_write(png_structp ppIn, png_bytep pb, size_t st)
{
   png_const_structp pp = ppIn;
   png_store *ps = voidcast(png_store*, png_get_io_ptr(pp));
   size_t writepos = ps->writepos;
   png_uint_32 chunkpos = ps->chunkpos;
   png_uint_32 chunktype = ps->chunktype;
   png_uint_32 chunklen = ps->chunklen;

   if (ps->pwrite != pp)
      png_error(pp, "store state damaged");

   /* Technically this is legal, but in practice libpng never writes more than
    * the maximum chunk size at once so if it happens something weird has
    * changed inside libpng (probably).
    */
   if (st > 0x7fffffffU)
      png_error(pp, "unexpected write size");

   /* Now process the bytes to be written.  Do this in units of the space in the
    * output (write) buffer or, at the start 4 bytes for the chunk type and
    * length limited in any case by the amount of data.
    */
   while (st > 0)
   {
      if (writepos >= STORE_BUFFER_SIZE)
         store_storenew(ps), writepos = 0;

      if (chunkpos < 4)
      {
         png_byte b = *pb++;
         --st;
         chunklen = (chunklen << 8) + b;
         ps->new.buffer[writepos++] = b;
         ++chunkpos;
      }

      else if (chunkpos < 8)
      {
         png_byte b = *pb++;
         --st;
         chunktype = (chunktype << 8) + b;
         ps->new.buffer[writepos++] = b;

         if (++chunkpos == 8)
         {
            chunklen &= 0xffffffffU;
            if (chunklen > 0x7fffffffU)
               png_error(pp, "chunk length too great");

            chunktype &= 0xffffffffU;
            if (chunktype == CHUNK_IDAT)
            {
               if (chunklen > ~ps->IDAT_size)
                  png_error(pp, "pngvalid internal image too large");

               ps->IDAT_size += chunklen;
            }

            else if (!valid_chunktype(chunktype))
               png_error(pp, "invalid chunk type");

            chunklen += 12; /* for header and CRC */
         }
      }

      else /* chunkpos >= 8 */
      {
         size_t cb = st;

         if (cb > STORE_BUFFER_SIZE - writepos)
            cb = STORE_BUFFER_SIZE - writepos;

         if (cb  > chunklen - chunkpos/* bytes left in chunk*/)
            cb = (size_t)/*SAFE*/(chunklen - chunkpos);

         memcpy(ps->new.buffer + writepos, pb, cb);
         chunkpos += (png_uint_32)/*SAFE*/cb;
         pb += cb;
         writepos += cb;
         st -= cb;

         if (chunkpos >= chunklen) /* must be equal */
            chunkpos = chunktype = chunklen = 0;
      }
   } /* while (st > 0) */

   ps->writepos = writepos;
   ps->chunkpos = chunkpos;
   ps->chunktype = chunktype;
   ps->chunklen = chunklen;
}

static void PNGCBAPI
store_flush(png_structp ppIn)
{
   UNUSED(ppIn) /*DOES NOTHING*/
}

#ifdef PNG_READ_SUPPORTED
static size_t
store_read_buffer_size(png_store *ps)
{
   /* Return the bytes available for read in the current buffer. */
   if (ps->next != &ps->current->data)
      return STORE_BUFFER_SIZE;

   return ps->current->datacount;
}

/* Return total bytes available for read. */
static size_t
store_read_buffer_avail(png_store *ps)
{
   if (ps->current != NULL && ps->next != NULL)
   {
      png_store_buffer *next = &ps->current->data;
      size_t cbAvail = ps->current->datacount;

      while (next != ps->next && next != NULL)
      {
         next = next->prev;
         cbAvail += STORE_BUFFER_SIZE;
      }

      if (next != ps->next)
         png_error(ps->pread, "buffer read error");

      if (cbAvail > ps->readpos)
         return cbAvail - ps->readpos;
   }

   return 0;
}

static int
store_read_buffer_next(png_store *ps)
{
   png_store_buffer *pbOld = ps->next;
   png_store_buffer *pbNew = &ps->current->data;
   if (pbOld != pbNew)
   {
      while (pbNew != NULL && pbNew->prev != pbOld)
         pbNew = pbNew->prev;

      if (pbNew != NULL)
      {
         ps->next = pbNew;
         ps->readpos = 0;
         return 1;
      }

      png_error(ps->pread, "buffer lost");
   }

   return 0; /* EOF or error */
}

/* Need separate implementation and callback to allow use of the same code
 * during progressive read, where the io_ptr is set internally by libpng.
 */
static void
store_read_imp(png_store *ps, png_bytep pb, size_t st)
{
   if (ps->current == NULL || ps->next == NULL)
      png_error(ps->pread, "store state damaged");

   while (st > 0)
   {
      size_t cbAvail = store_read_buffer_size(ps) - ps->readpos;

      if (cbAvail > 0)
      {
         if (cbAvail > st) cbAvail = st;
         memcpy(pb, ps->next->buffer + ps->readpos, cbAvail);
         st -= cbAvail;
         pb += cbAvail;
         ps->readpos += cbAvail;
      }

      else if (!store_read_buffer_next(ps))
         png_error(ps->pread, "read beyond end of file");
   }
}

static size_t
store_read_chunk(png_store *ps, png_bytep pb, size_t max, size_t min)
{
   png_uint_32 chunklen = ps->chunklen;
   png_uint_32 chunktype = ps->chunktype;
   png_uint_32 chunkpos = ps->chunkpos;
   size_t st = max;

   if (st > 0) do
   {
      if (chunkpos >= chunklen) /* end of last chunk */
      {
         png_byte buffer[8];

         /* Read the header of the next chunk: */
         store_read_imp(ps, buffer, 8U);
         chunklen = png_get_uint_32(buffer) + 12U;
         chunktype = png_get_uint_32(buffer+4U);
         chunkpos = 0U; /* Position read so far */
      }

      if (chunktype == CHUNK_IDAT)
      {
         png_uint_32 IDAT_pos = ps->IDAT_pos;
         png_uint_32 IDAT_len = ps->IDAT_len;
         png_uint_32 IDAT_size = ps->IDAT_size;

         /* The IDAT headers are constructed here; skip the input header. */
         if (chunkpos < 8U)
            chunkpos = 8U;

         if (IDAT_pos == IDAT_len)
         {
            png_byte random = random_byte();

            /* Make a new IDAT chunk, if IDAT_len is 0 this is the first IDAT,
             * if IDAT_size is 0 this is the end.  At present this is set up
             * using a random number so that there is a 25% chance before
             * the start of the first IDAT chunk being 0 length.
             */
            if (IDAT_len == 0U) /* First IDAT */
            {
               switch (random & 3U)
               {
                  case 0U: IDAT_len = 12U; break; /* 0 bytes */
                  case 1U: IDAT_len = 13U; break; /* 1 byte */
                  default: IDAT_len = random_u32();
                           IDAT_len %= IDAT_size;
                           IDAT_len += 13U; /* 1..IDAT_size bytes */
                           break;
               }
            }

            else if (IDAT_size == 0U) /* all IDAT data read */
            {
               /* The last (IDAT) chunk should be positioned at the CRC now: */
               if (chunkpos != chunklen-4U)
                  png_error(ps->pread, "internal: IDAT size mismatch");

               /* The only option here is to add a zero length IDAT, this
                * happens 25% of the time.  Because of the check above
                * chunklen-4U-chunkpos must be zero, we just need to skip the
                * CRC now.
                */
               if ((random & 3U) == 0U)
                  IDAT_len = 12U; /* Output another 0 length IDAT */

               else
               {
                  /* End of IDATs, skip the CRC to make the code above load the
                   * next chunk header next time round.
                   */
                  png_byte buffer[4];

                  store_read_imp(ps, buffer, 4U);
                  chunkpos += 4U;
                  ps->IDAT_pos = IDAT_pos;
                  ps->IDAT_len = IDAT_len;
                  ps->IDAT_size = 0U;
                  continue; /* Read the next chunk */
               }
            }

            else
            {
               /* Middle of IDATs, use 'random' to determine the number of bits
                * to use in the IDAT length.
                */
               IDAT_len = random_u32();
               IDAT_len &= (1U << (1U + random % ps->IDAT_bits)) - 1U;
               if (IDAT_len > IDAT_size)
                  IDAT_len = IDAT_size;
               IDAT_len += 12U; /* zero bytes may occur */
            }

            IDAT_pos = 0U;
            ps->IDAT_crc = 0x35af061e; /* Ie: crc32(0UL, "IDAT", 4) */
         } /* IDAT_pos == IDAT_len */

         if (IDAT_pos < 8U) /* Return the header */ do
         {
            png_uint_32 b;
            unsigned int shift;

            if (IDAT_pos < 4U)
               b = IDAT_len - 12U;

            else
               b = CHUNK_IDAT;

            shift = 3U & IDAT_pos;
            ++IDAT_pos;

            if (shift < 3U)
               b >>= 8U*(3U-shift);

            *pb++ = 0xffU & b;
         }
         while (--st > 0 && IDAT_pos < 8);

         else if (IDAT_pos < IDAT_len - 4U) /* I.e not the CRC */
         {
            if (chunkpos < chunklen-4U)
            {
               uInt avail = (uInt)-1;

               if (avail > (IDAT_len-4U) - IDAT_pos)
                  avail = (uInt)/*SAFE*/((IDAT_len-4U) - IDAT_pos);

               if (avail > st)
                  avail = (uInt)/*SAFE*/st;

               if (avail > (chunklen-4U) - chunkpos)
                  avail = (uInt)/*SAFE*/((chunklen-4U) - chunkpos);

               store_read_imp(ps, pb, avail);
               ps->IDAT_crc = crc32(ps->IDAT_crc, pb, avail);
               pb += (size_t)/*SAFE*/avail;
               st -= (size_t)/*SAFE*/avail;
               chunkpos += (png_uint_32)/*SAFE*/avail;
               IDAT_size -= (png_uint_32)/*SAFE*/avail;
               IDAT_pos += (png_uint_32)/*SAFE*/avail;
            }

            else /* skip the input CRC */
            {
               png_byte buffer[4];

               store_read_imp(ps, buffer, 4U);
               chunkpos += 4U;
            }
         }

         else /* IDAT crc */ do
         {
            uLong b = ps->IDAT_crc;
            unsigned int shift = (IDAT_len - IDAT_pos); /* 4..1 */
            ++IDAT_pos;

            if (shift > 1U)
               b >>= 8U*(shift-1U);

            *pb++ = 0xffU & b;
         }
         while (--st > 0 && IDAT_pos < IDAT_len);

         ps->IDAT_pos = IDAT_pos;
         ps->IDAT_len = IDAT_len;
         ps->IDAT_size = IDAT_size;
      }

      else /* !IDAT */
      {
         /* If there is still some pending IDAT data after the IDAT chunks have
          * been processed there is a problem:
          */
         if (ps->IDAT_len > 0 && ps->IDAT_size > 0)
            png_error(ps->pread, "internal: missing IDAT data");

         if (chunktype == CHUNK_IEND && ps->IDAT_len == 0U)
            png_error(ps->pread, "internal: missing IDAT");

         if (chunkpos < 8U) /* Return the header */ do
         {
            png_uint_32 b;
            unsigned int shift;

            if (chunkpos < 4U)
               b = chunklen - 12U;

            else
               b = chunktype;

            shift = 3U & chunkpos;
            ++chunkpos;

            if (shift < 3U)
               b >>= 8U*(3U-shift);

            *pb++ = 0xffU & b;
         }
         while (--st > 0 && chunkpos < 8);

         else /* Return chunk bytes, including the CRC */
         {
            size_t avail = st;

            if (avail > chunklen - chunkpos)
               avail = (size_t)/*SAFE*/(chunklen - chunkpos);

            store_read_imp(ps, pb, avail);
            pb += avail;
            st -= avail;
            chunkpos += (png_uint_32)/*SAFE*/avail;

            /* Check for end of chunk and end-of-file; don't try to read a new
             * chunk header at this point unless instructed to do so by 'min'.
             */
            if (chunkpos >= chunklen && max-st >= min &&
                     store_read_buffer_avail(ps) == 0)
               break;
         }
      } /* !IDAT */
   }
   while (st > 0);

   ps->chunklen = chunklen;
   ps->chunktype = chunktype;
   ps->chunkpos = chunkpos;

   return st; /* space left */
}

static void PNGCBAPI
store_read(png_structp ppIn, png_bytep pb, size_t st)
{
   png_const_structp pp = ppIn;
   png_store *ps = voidcast(png_store*, png_get_io_ptr(pp));

   if (ps == NULL || ps->pread != pp)
      png_error(pp, "bad store read call");

   store_read_chunk(ps, pb, st, st);
}

static void
store_progressive_read(png_store *ps, png_structp pp, png_infop pi)
{
   if (ps->pread != pp || ps->current == NULL || ps->next == NULL)
      png_error(pp, "store state damaged (progressive)");

   /* This is another Horowitz and Hill random noise generator.  In this case
    * the aim is to stress the progressive reader with truly horrible variable
    * buffer sizes in the range 1..500, so a sequence of 9 bit random numbers
    * is generated.  We could probably just count from 1 to 32767 and get as
    * good a result.
    */
   while (store_read_buffer_avail(ps) > 0)
   {
      static png_uint_32 noise = 2;
      size_t cb;
      png_byte buffer[512];

      /* Generate 15 more bits of stuff: */
      noise = (noise << 9) | ((noise ^ (noise >> (9-5))) & 0x1ff);
      cb = noise & 0x1ff;
      cb -= store_read_chunk(ps, buffer, cb, 1);
      png_process_data(pp, pi, buffer, cb);
   }
}
#endif /* PNG_READ_SUPPORTED */

/* The caller must fill this in: */
static store_palette_entry *
store_write_palette(png_store *ps, int npalette)
{
   if (ps->pwrite == NULL)
      store_log(ps, NULL, "attempt to write palette without write stream", 1);

   if (ps->palette != NULL)
      png_error(ps->pwrite, "multiple store_write_palette calls");

   /* This function can only return NULL if called with '0'! */
   if (npalette > 0)
   {
      ps->palette = voidcast(store_palette_entry*, malloc(npalette *
         sizeof *ps->palette));

      if (ps->palette == NULL)
         png_error(ps->pwrite, "store new palette: OOM");

      ps->npalette = npalette;
   }

   return ps->palette;
}

#ifdef PNG_READ_SUPPORTED
static store_palette_entry *
store_current_palette(png_store *ps, int *npalette)
{
   /* This is an internal error (the call has been made outside a read
    * operation.)
    */
   if (ps->current == NULL)
   {
      store_log(ps, ps->pread, "no current stream for palette", 1);
      return NULL;
   }

   /* The result may be null if there is no palette. */
   *npalette = ps->current->npalette;
   return ps->current->palette;
}
#endif /* PNG_READ_SUPPORTED */

/***************************** MEMORY MANAGEMENT*** ***************************/
#ifdef PNG_USER_MEM_SUPPORTED
/* A store_memory is simply the header for an allocated block of memory.  The
 * pointer returned to libpng is just after the end of the header block, the
 * allocated memory is followed by a second copy of the 'mark'.
 */
typedef struct store_memory
{
   store_pool          *pool;    /* Originating pool */
   struct store_memory *next;    /* Singly linked list */
   png_alloc_size_t     size;    /* Size of memory allocated */
   png_byte             mark[4]; /* ID marker */
} store_memory;

/* Handle a fatal error in memory allocation.  This calls png_error if the
 * libpng struct is non-NULL, else it outputs a message and returns.  This means
 * that a memory problem while libpng is running will abort (png_error) the
 * handling of particular file while one in cleanup (after the destroy of the
 * struct has returned) will simply keep going and free (or attempt to free)
 * all the memory.
 */
static void
store_pool_error(png_store *ps, png_const_structp pp, const char *msg)
{
   if (pp != NULL)
      png_error(pp, msg);

   /* Else we have to do it ourselves.  png_error eventually calls store_log,
    * above.  store_log accepts a NULL png_structp - it just changes what gets
    * output by store_message.
    */
   store_log(ps, pp, msg, 1 /* error */);
}

static void
store_memory_free(png_const_structp pp, store_pool *pool, store_memory *memory)
{
   /* Note that pp may be NULL (see store_pool_delete below), the caller has
    * found 'memory' in pool->list *and* unlinked this entry, so this is a valid
    * pointer (for sure), but the contents may have been trashed.
    */
   if (memory->pool != pool)
      store_pool_error(pool->store, pp, "memory corrupted (pool)");

   else if (memcmp(memory->mark, pool->mark, sizeof memory->mark) != 0)
      store_pool_error(pool->store, pp, "memory corrupted (start)");

   /* It should be safe to read the size field now. */
   else
   {
      png_alloc_size_t cb = memory->size;

      if (cb > pool->max)
         store_pool_error(pool->store, pp, "memory corrupted (size)");

      else if (memcmp((png_bytep)(memory+1)+cb, pool->mark, sizeof pool->mark)
         != 0)
         store_pool_error(pool->store, pp, "memory corrupted (end)");

      /* Finally give the library a chance to find problems too: */
      else
         {
         pool->current -= cb;
         free(memory);
         }
   }
}

static void
store_pool_delete(png_store *ps, store_pool *pool)
{
   if (pool->list != NULL)
   {
      fprintf(stderr, "%s: %s %s: memory lost (list follows):\n", ps->test,
         pool == &ps->read_memory_pool ? "read" : "write",
         pool == &ps->read_memory_pool ? (ps->current != NULL ?
            ps->current->name : "unknown file") : ps->wname);
      ++ps->nerrors;

      do
      {
         store_memory *next = pool->list;
         pool->list = next->next;
         next->next = NULL;

         fprintf(stderr, "\t%lu bytes @ %p\n",
             (unsigned long)next->size, (const void*)(next+1));
         /* The NULL means this will always return, even if the memory is
          * corrupted.
          */
         store_memory_free(NULL, pool, next);
      }
      while (pool->list != NULL);
   }

   /* And reset the other fields too for the next time. */
   if (pool->max > pool->max_max) pool->max_max = pool->max;
   pool->max = 0;
   if (pool->current != 0) /* unexpected internal error */
      fprintf(stderr, "%s: %s %s: memory counter mismatch (internal error)\n",
         ps->test, pool == &ps->read_memory_pool ? "read" : "write",
         pool == &ps->read_memory_pool ? (ps->current != NULL ?
            ps->current->name : "unknown file") : ps->wname);
   pool->current = 0;

   if (pool->limit > pool->max_limit)
      pool->max_limit = pool->limit;

   pool->limit = 0;

   if (pool->total > pool->max_total)
      pool->max_total = pool->total;

   pool->total = 0;

   /* Get a new mark too. */
   store_pool_mark(pool->mark);
}

/* The memory callbacks: */
static png_voidp PNGCBAPI
store_malloc(png_structp ppIn, png_alloc_size_t cb)
{
   png_const_structp pp = ppIn;
   store_pool *pool = voidcast(store_pool*, png_get_mem_ptr(pp));
   store_memory *new = voidcast(store_memory*, malloc(cb + (sizeof *new) +
      (sizeof pool->mark)));

   if (new != NULL)
   {
      if (cb > pool->max)
         pool->max = cb;

      pool->current += cb;

      if (pool->current > pool->limit)
         pool->limit = pool->current;

      pool->total += cb;

      new->size = cb;
      memcpy(new->mark, pool->mark, sizeof new->mark);
      memcpy((png_byte*)(new+1) + cb, pool->mark, sizeof pool->mark);
      new->pool = pool;
      new->next = pool->list;
      pool->list = new;
      ++new;
   }

   else
   {
      /* NOTE: the PNG user malloc function cannot use the png_ptr it is passed
       * other than to retrieve the allocation pointer!  libpng calls the
       * store_malloc callback in two basic cases:
       *
       * 1) From png_malloc; png_malloc will do a png_error itself if NULL is
       *    returned.
       * 2) From png_struct or png_info structure creation; png_malloc is
       *    to return so cleanup can be performed.
       *
       * To handle this store_malloc can log a message, but can't do anything
       * else.
       */
      store_log(pool->store, pp, "out of memory", 1 /* is_error */);
   }

   return new;
}

static void PNGCBAPI
store_free(png_structp ppIn, png_voidp memory)
{
   png_const_structp pp = ppIn;
   store_pool *pool = voidcast(store_pool*, png_get_mem_ptr(pp));
   store_memory *this = voidcast(store_memory*, memory), **test;

   /* Because libpng calls store_free with a dummy png_struct when deleting
    * png_struct or png_info via png_destroy_struct_2 it is necessary to check
    * the passed in png_structp to ensure it is valid, and not pass it to
    * png_error if it is not.
    */
   if (pp != pool->store->pread && pp != pool->store->pwrite)
      pp = NULL;

   /* First check that this 'memory' really is valid memory - it must be in the
    * pool list.  If it is, use the shared memory_free function to free it.
    */
   --this;
   for (test = &pool->list; *test != this; test = &(*test)->next)
   {
      if (*test == NULL)
      {
         store_pool_error(pool->store, pp, "bad pointer to free");
         return;
      }
   }

   /* Unlink this entry, *test == this. */
   *test = this->next;
   this->next = NULL;
   store_memory_free(pp, pool, this);
}
#endif /* PNG_USER_MEM_SUPPORTED */

/* Setup functions. */
/* Cleanup when aborting a write or after storing the new file. */
static void
store_write_reset(png_store *ps)
{
   if (ps->pwrite != NULL)
   {
      anon_context(ps);

      Try
         png_destroy_write_struct(&ps->pwrite, &ps->piwrite);

      Catch_anonymous
      {
         /* memory corruption: continue. */
      }

      ps->pwrite = NULL;
      ps->piwrite = NULL;
   }

   /* And make sure that all the memory has been freed - this will output
    * spurious errors in the case of memory corruption above, but this is safe.
    */
#  ifdef PNG_USER_MEM_SUPPORTED
      store_pool_delete(ps, &ps->write_memory_pool);
#  endif

   store_freenew(ps);
}

/* The following is the main write function, it returns a png_struct and,
 * optionally, a png_info suitable for writiing a new PNG file.  Use
 * store_storefile above to record this file after it has been written.  The
 * returned libpng structures as destroyed by store_write_reset above.
 */
static png_structp
set_store_for_write(png_store *ps, png_infopp ppi, const char *name)
{
   anon_context(ps);

   Try
   {
      if (ps->pwrite != NULL)
         png_error(ps->pwrite, "write store already in use");

      store_write_reset(ps);
      safecat(ps->wname, sizeof ps->wname, 0, name);

      /* Don't do the slow memory checks if doing a speed test, also if user
       * memory is not supported we can't do it anyway.
       */
#     ifdef PNG_USER_MEM_SUPPORTED
         if (!ps->speed)
            ps->pwrite = png_create_write_struct_2(PNG_LIBPNG_VER_STRING,
               ps, store_error, store_warning, &ps->write_memory_pool,
               store_malloc, store_free);

         else
#     endif
         ps->pwrite = png_create_write_struct(PNG_LIBPNG_VER_STRING,
            ps, store_error, store_warning);

      png_set_write_fn(ps->pwrite, ps, store_write, store_flush);

#     ifdef PNG_SET_OPTION_SUPPORTED
         {
            int opt;
            for (opt=0; opt<ps->noptions; ++opt)
               if (png_set_option(ps->pwrite, ps->options[opt].option,
                  ps->options[opt].setting) == PNG_OPTION_INVALID)
                  png_error(ps->pwrite, "png option invalid");
         }
#     endif

      if (ppi != NULL)
         *ppi = ps->piwrite = png_create_info_struct(ps->pwrite);
   }

   Catch_anonymous
      return NULL;

   return ps->pwrite;
}

/* Cleanup when finished reading (either due to error or in the success case).
 * This routine exists even when there is no read support to make the code
 * tidier (avoid a mass of ifdefs) and so easier to maintain.
 */
static void
store_read_reset(png_store *ps)
{
#  ifdef PNG_READ_SUPPORTED
      if (ps->pread != NULL)
      {
         anon_context(ps);

         Try
            png_destroy_read_struct(&ps->pread, &ps->piread, NULL);

         Catch_anonymous
         {
            /* error already output: continue */
         }

         ps->pread = NULL;
         ps->piread = NULL;
      }
#  endif

#  ifdef PNG_USER_MEM_SUPPORTED
      /* Always do this to be safe. */
      store_pool_delete(ps, &ps->read_memory_pool);
#  endif

   ps->current = NULL;
   ps->next = NULL;
   ps->readpos = 0;
   ps->validated = 0;

   ps->chunkpos = 8;
   ps->chunktype = 0;
   ps->chunklen = 16;
   ps->IDAT_size = 0;
}

#ifdef PNG_READ_SUPPORTED
static void
store_read_set(png_store *ps, png_uint_32 id)
{
   png_store_file *pf = ps->saved;

   while (pf != NULL)
   {
      if (pf->id == id)
      {
         ps->current = pf;
         ps->next = NULL;
         ps->IDAT_size = pf->IDAT_size;
         ps->IDAT_bits = pf->IDAT_bits; /* just a cache */
         ps->IDAT_len = 0;
         ps->IDAT_pos = 0;
         ps->IDAT_crc = 0UL;
         store_read_buffer_next(ps);
         return;
      }

      pf = pf->next;
   }

   {
      size_t pos;
      char msg[FILE_NAME_SIZE+64];

      pos = standard_name_from_id(msg, sizeof msg, 0, id);
      pos = safecat(msg, sizeof msg, pos, ": file not found");
      png_error(ps->pread, msg);
   }
}

/* The main interface for reading a saved file - pass the id number of the file
 * to retrieve.  Ids must be unique or the earlier file will be hidden.  The API
 * returns a png_struct and, optionally, a png_info.  Both of these will be
 * destroyed by store_read_reset above.
 */
static png_structp
set_store_for_read(png_store *ps, png_infopp ppi, png_uint_32 id,
   const char *name)
{
   /* Set the name for png_error */
   safecat(ps->test, sizeof ps->test, 0, name);

   if (ps->pread != NULL)
      png_error(ps->pread, "read store already in use");

   store_read_reset(ps);

   /* Both the create APIs can return NULL if used in their default mode
    * (because there is no other way of handling an error because the jmp_buf
    * by default is stored in png_struct and that has not been allocated!)
    * However, given that store_error works correctly in these circumstances
    * we don't ever expect NULL in this program.
    */
#  ifdef PNG_USER_MEM_SUPPORTED
      if (!ps->speed)
         ps->pread = png_create_read_struct_2(PNG_LIBPNG_VER_STRING, ps,
             store_error, store_warning, &ps->read_memory_pool, store_malloc,
             store_free);

      else
#  endif
   ps->pread = png_create_read_struct(PNG_LIBPNG_VER_STRING, ps, store_error,
      store_warning);

   if (ps->pread == NULL)
   {
      struct exception_context *the_exception_context = &ps->exception_context;

      store_log(ps, NULL, "png_create_read_struct returned NULL (unexpected)",
         1 /*error*/);

      Throw ps;
   }

#  ifdef PNG_SET_OPTION_SUPPORTED
      {
         int opt;
         for (opt=0; opt<ps->noptions; ++opt)
            if (png_set_option(ps->pread, ps->options[opt].option,
               ps->options[opt].setting) == PNG_OPTION_INVALID)
                  png_error(ps->pread, "png option invalid");
      }
#  endif

   store_read_set(ps, id);

   if (ppi != NULL)
      *ppi = ps->piread = png_create_info_struct(ps->pread);

   return ps->pread;
}
#endif /* PNG_READ_SUPPORTED */

/* The overall cleanup of a store simply calls the above then removes all the
 * saved files.  This does not delete the store itself.
 */
static void
store_delete(png_store *ps)
{
   store_write_reset(ps);
   store_read_reset(ps);
   store_freefile(&ps->saved);
   store_image_free(ps, NULL);
}

/*********************** PNG FILE MODIFICATION ON READ ************************/
/* Files may be modified on read.  The following structure contains a complete
 * png_store together with extra members to handle modification and a special
 * read callback for libpng.  To use this the 'modifications' field must be set
 * to a list of png_modification structures that actually perform the
 * modification, otherwise a png_modifier is functionally equivalent to a
 * png_store.  There is a special read function, set_modifier_for_read, which
 * replaces set_store_for_read.
 */
typedef enum modifier_state
{
   modifier_start,                        /* Initial value */
   modifier_signature,                    /* Have a signature */
   modifier_IHDR                          /* Have an IHDR */
} modifier_state;

typedef struct CIE_color
{
   /* A single CIE tristimulus value, representing the unique response of a
    * standard observer to a variety of light spectra.  The observer recognizes
    * all spectra that produce this response as the same color, therefore this
    * is effectively a description of a color.
    */
   double X, Y, Z;
} CIE_color;

typedef struct color_encoding
{
   /* A description of an (R,G,B) encoding of color (as defined above); this
    * includes the actual colors of the (R,G,B) triples (1,0,0), (0,1,0) and
    * (0,0,1) plus an encoding value that is used to encode the linear
    * components R, G and B to give the actual values R^gamma, G^gamma and
    * B^gamma that are stored.
    */
   double    gamma;            /* Encoding (file) gamma of space */
   CIE_color red, green, blue; /* End points */
} color_encoding;

#ifdef PNG_READ_SUPPORTED
#if defined PNG_READ_TRANSFORMS_SUPPORTED && defined PNG_READ_cHRM_SUPPORTED
static double
chromaticity_x(CIE_color c)
{
   return c.X / (c.X + c.Y + c.Z);
}

static double
chromaticity_y(CIE_color c)
{
   return c.Y / (c.X + c.Y + c.Z);
}

static CIE_color
white_point(const color_encoding *encoding)
{
   CIE_color white;

   white.X = encoding->red.X + encoding->green.X + encoding->blue.X;
   white.Y = encoding->red.Y + encoding->green.Y + encoding->blue.Y;
   white.Z = encoding->red.Z + encoding->green.Z + encoding->blue.Z;

   return white;
}
#endif /* READ_TRANSFORMS && READ_cHRM */

#ifdef PNG_READ_RGB_TO_GRAY_SUPPORTED
static void
normalize_color_encoding(color_encoding *encoding)
{
   const double whiteY = encoding->red.Y + encoding->green.Y +
      encoding->blue.Y;

   if (whiteY != 1)
   {
      encoding->red.X /= whiteY;
      encoding->red.Y /= whiteY;
      encoding->red.Z /= whiteY;
      encoding->green.X /= whiteY;
      encoding->green.Y /= whiteY;
      encoding->green.Z /= whiteY;
      encoding->blue.X /= whiteY;
      encoding->blue.Y /= whiteY;
      encoding->blue.Z /= whiteY;
   }
}
#endif

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
static size_t
safecat_color_encoding(char *buffer, size_t bufsize, size_t pos,
   const color_encoding *e, double encoding_gamma)
{
   if (e != 0)
   {
      if (encoding_gamma != 0)
         pos = safecat(buffer, bufsize, pos, "(");
      pos = safecat(buffer, bufsize, pos, "R(");
      pos = safecatd(buffer, bufsize, pos, e->red.X, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->red.Y, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->red.Z, 4);
      pos = safecat(buffer, bufsize, pos, "),G(");
      pos = safecatd(buffer, bufsize, pos, e->green.X, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->green.Y, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->green.Z, 4);
      pos = safecat(buffer, bufsize, pos, "),B(");
      pos = safecatd(buffer, bufsize, pos, e->blue.X, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->blue.Y, 4);
      pos = safecat(buffer, bufsize, pos, ",");
      pos = safecatd(buffer, bufsize, pos, e->blue.Z, 4);
      pos = safecat(buffer, bufsize, pos, ")");
      if (encoding_gamma != 0)
         pos = safecat(buffer, bufsize, pos, ")");
   }

   if (encoding_gamma != 0)
   {
      pos = safecat(buffer, bufsize, pos, "^");
      pos = safecatd(buffer, bufsize, pos, encoding_gamma, 5);
   }

   return pos;
}
#endif /* READ_TRANSFORMS */
#endif /* PNG_READ_SUPPORTED */

typedef struct png_modifier
{
   png_store               this;             /* I am a png_store */
   struct png_modification *modifications;   /* Changes to make */

   modifier_state           state;           /* My state */

   /* Information from IHDR: */
   png_byte                 bit_depth;       /* From IHDR */
   png_byte                 colour_type;     /* From IHDR */

   /* While handling PLTE, IDAT and IEND these chunks may be pended to allow
    * other chunks to be inserted.
    */
   png_uint_32              pending_len;
   png_uint_32              pending_chunk;

   /* Test values */
   double                   *gammas;
   unsigned int              ngammas;
   unsigned int              ngamma_tests;     /* Number of gamma tests to run*/
   double                    current_gamma;    /* 0 if not set */
   const color_encoding *encodings;
   unsigned int              nencodings;
   const color_encoding *current_encoding; /* If an encoding has been set */
   unsigned int              encoding_counter; /* For iteration */
   int                       encoding_ignored; /* Something overwrote it */

   /* Control variables used to iterate through possible encodings, the
    * following must be set to 0 and tested by the function that uses the
    * png_modifier because the modifier only sets it to 1 (true.)
    */
   unsigned int              repeat :1;   /* Repeat this transform test. */
   unsigned int              test_uses_encoding :1;

   /* Lowest sbit to test (pre-1.7 libpng fails for sbit < 8) */
   png_byte                 sbitlow;

   /* Error control - these are the limits on errors accepted by the gamma tests
    * below.
    */
   double                   maxout8;  /* Maximum output value error */
   double                   maxabs8;  /* Absolute sample error 0..1 */
   double                   maxcalc8; /* Absolute sample error 0..1 */
   double                   maxpc8;   /* Percentage sample error 0..100% */
   double                   maxout16; /* Maximum output value error */
   double                   maxabs16; /* Absolute sample error 0..1 */
   double                   maxcalc16;/* Absolute sample error 0..1 */
   double                   maxcalcG; /* Absolute sample error 0..1 */
   double                   maxpc16;  /* Percentage sample error 0..100% */

   /* This is set by transforms that need to allow a higher limit, it is an
    * internal check on pngvalid to ensure that the calculated error limits are
    * not ridiculous; without this it is too easy to make a mistake in pngvalid
    * that allows any value through.
    *
    * NOTE: this is not checked in release builds.
    */
   double                   limit;    /* limit on error values, normally 4E-3 */

   /* Log limits - values above this are logged, but not necessarily
    * warned.
    */
   double                   log8;     /* Absolute error in 8 bits to log */
   double                   log16;    /* Absolute error in 16 bits to log */

   /* Logged 8 and 16 bit errors ('output' values): */
   double                   error_gray_2;
   double                   error_gray_4;
   double                   error_gray_8;
   double                   error_gray_16;
   double                   error_color_8;
   double                   error_color_16;
   double                   error_indexed;

   /* Flags: */
   /* Whether to call png_read_update_info, not png_read_start_image, and how
    * many times to call it.
    */
   int                      use_update_info;

   /* Whether or not to interlace. */
   int                      interlace_type :9; /* int, but must store '1' */

   /* Run the standard tests? */
   unsigned int             test_standard :1;

   /* Run the odd-sized image and interlace read/write tests? */
   unsigned int             test_size :1;

   /* Run tests on reading with a combination of transforms, */
   unsigned int             test_transform :1;
   unsigned int             test_tRNS :1; /* Includes tRNS images */

   /* When to use the use_input_precision option, this controls the gamma
    * validation code checks.  If set any value that is within the transformed
    * range input-.5 to input+.5 will be accepted, otherwise the value must be
    * within the normal limits.  It should not be necessary to set this; the
    * result should always be exact within the permitted error limits.
    */
   unsigned int             use_input_precision :1;
   unsigned int             use_input_precision_sbit :1;
   unsigned int             use_input_precision_16to8 :1;

   /* If set assume that the calculation bit depth is set by the input
    * precision, not the output precision.
    */
   unsigned int             calculations_use_input_precision :1;

   /* If set assume that the calculations are done in 16 bits even if the sample
    * depth is 8 bits.
    */
   unsigned int             assume_16_bit_calculations :1;

   /* Which gamma tests to run: */
   unsigned int             test_gamma_threshold :1;
   unsigned int             test_gamma_transform :1; /* main tests */
   unsigned int             test_gamma_sbit :1;
   unsigned int             test_gamma_scale16 :1;
   unsigned int             test_gamma_background :1;
   unsigned int             test_gamma_alpha_mode :1;
   unsigned int             test_gamma_expand16 :1;
   unsigned int             test_exhaustive :1;

   /* Whether or not to run the low-bit-depth grayscale tests.  This fails on
    * gamma images in some cases because of gross inaccuracies in the grayscale
    * gamma handling for low bit depth.
    */
   unsigned int             test_lbg :1;
   unsigned int             test_lbg_gamma_threshold :1;
   unsigned int             test_lbg_gamma_transform :1;
   unsigned int             test_lbg_gamma_sbit :1;
   unsigned int             test_lbg_gamma_composition :1;

   unsigned int             log :1;   /* Log max error */

   /* Buffer information, the buffer size limits the size of the chunks that can
    * be modified - they must fit (including header and CRC) into the buffer!
    */
   size_t                   flush;           /* Count of bytes to flush */
   size_t                   buffer_count;    /* Bytes in buffer */
   size_t                   buffer_position; /* Position in buffer */
   png_byte                 buffer[1024];
} png_modifier;

/* This returns true if the test should be stopped now because it has already
 * failed and it is running silently.
  */
static int fail(png_modifier *pm)
{
   return !pm->log && !pm->this.verbose && (pm->this.nerrors > 0 ||
       (pm->this.treat_warnings_as_errors && pm->this.nwarnings > 0));
}

static void
modifier_init(png_modifier *pm)
{
   memset(pm, 0, sizeof *pm);
   store_init(&pm->this);
   pm->modifications = NULL;
   pm->state = modifier_start;
   pm->sbitlow = 1U;
   pm->ngammas = 0;
   pm->ngamma_tests = 0;
   pm->gammas = 0;
   pm->current_gamma = 0;
   pm->encodings = 0;
   pm->nencodings = 0;
   pm->current_encoding = 0;
   pm->encoding_counter = 0;
   pm->encoding_ignored = 0;
   pm->repeat = 0;
   pm->test_uses_encoding = 0;
   pm->maxout8 = pm->maxpc8 = pm->maxabs8 = pm->maxcalc8 = 0;
   pm->maxout16 = pm->maxpc16 = pm->maxabs16 = pm->maxcalc16 = 0;
   pm->maxcalcG = 0;
   pm->limit = 4E-3;
   pm->log8 = pm->log16 = 0; /* Means 'off' */
   pm->error_gray_2 = pm->error_gray_4 = pm->error_gray_8 = 0;
   pm->error_gray_16 = pm->error_color_8 = pm->error_color_16 = 0;
   pm->error_indexed = 0;
   pm->use_update_info = 0;
   pm->interlace_type = PNG_INTERLACE_NONE;
   pm->test_standard = 0;
   pm->test_size = 0;
   pm->test_transform = 0;
#  ifdef PNG_WRITE_tRNS_SUPPORTED
      pm->test_tRNS = 1;
#  else
      pm->test_tRNS = 0;
#  endif
   pm->use_input_precision = 0;
   pm->use_input_precision_sbit = 0;
   pm->use_input_precision_16to8 = 0;
   pm->calculations_use_input_precision = 0;
   pm->assume_16_bit_calculations = 0;
   pm->test_gamma_threshold = 0;
   pm->test_gamma_transform = 0;
   pm->test_gamma_sbit = 0;
   pm->test_gamma_scale16 = 0;
   pm->test_gamma_background = 0;
   pm->test_gamma_alpha_mode = 0;
   pm->test_gamma_expand16 = 0;
   pm->test_lbg = 1;
   pm->test_lbg_gamma_threshold = 1;
   pm->test_lbg_gamma_transform = 1;
   pm->test_lbg_gamma_sbit = 1;
   pm->test_lbg_gamma_composition = 1;
   pm->test_exhaustive = 0;
   pm->log = 0;

   /* Rely on the memset for all the other fields - there are no pointers */
}

#ifdef PNG_READ_TRANSFORMS_SUPPORTED

/* This controls use of checks that explicitly know how libpng digitizes the
 * samples in calculations; setting this circumvents simple error limit checking
 * in the rgb_to_gray check, replacing it with an exact copy of the libpng 1.5
 * algorithm.
 */
#define DIGITIZE PNG_LIBPNG_VER < 10700

/* If pm->calculations_use_input_precision is set then operations will happen
 * with the precision of the input, not the precision of the output depth.
 *
 * If pm->assume_16_bit_calculations is set then even 8 bit calculations use 16
 * bit precision.  This only affects those of the following limits that pertain
 * to a calculation - not a digitization operation - unless the following API is
 * called directly.
 */
#ifdef PNG_READ_RGB_TO_GRAY_SUPPORTED
#if DIGITIZE
static double digitize(double value, int depth, int do_round)
{
   /* 'value' is in the range 0 to 1, the result is the same value rounded to a
    * multiple of the digitization factor - 8 or 16 bits depending on both the
    * sample depth and the 'assume' setting.  Digitization is normally by
    * rounding and 'do_round' should be 1, if it is 0 the digitized value will
    * be truncated.
    */
   unsigned int digitization_factor = (1U << depth) - 1;

   /* Limiting the range is done as a convenience to the caller - it's easier to
    * do it once here than every time at the call site.
    */
   if (value <= 0)
      value = 0;

   else if (value >= 1)
      value = 1;

   value *= digitization_factor;
   if (do_round) value += .5;
   return floor(value)/digitization_factor;
}
#endif
#endif /* RGB_TO_GRAY */

#ifdef PNG_READ_GAMMA_SUPPORTED
static double abserr(const png_modifier *pm, int in_depth, int out_depth)
{
   /* Absolute error permitted in linear values - affected by the bit depth of
    * the calculations.
    */
   if (pm->assume_16_bit_calculations ||
      (pm->calculations_use_input_precision ? in_depth : out_depth) == 16)
      return pm->maxabs16;
   else
      return pm->maxabs8;
}

static double calcerr(const png_modifier *pm, int in_depth, int out_depth)
{
   /* Error in the linear composition arithmetic - only relevant when
    * composition actually happens (0 < alpha < 1).
    */
   if ((pm->calculations_use_input_precision ? in_depth : out_depth) == 16)
      return pm->maxcalc16;
   else if (pm->assume_16_bit_calculations)
      return pm->maxcalcG;
   else
      return pm->maxcalc8;
}

static double pcerr(const png_modifier *pm, int in_depth, int out_depth)
{
   /* Percentage error permitted in the linear values.  Note that the specified
    * value is a percentage but this routine returns a simple number.
    */
   if (pm->assume_16_bit_calculations ||
      (pm->calculations_use_input_precision ? in_depth : out_depth) == 16)
      return pm->maxpc16 * .01;
   else
      return pm->maxpc8 * .01;
}

/* Output error - the error in the encoded value.  This is determined by the
 * digitization of the output so can be +/-0.5 in the actual output value.  In
 * the expand_16 case with the current code in libpng the expand happens after
 * all the calculations are done in 8 bit arithmetic, so even though the output
 * depth is 16 the output error is determined by the 8 bit calculation.
 *
 * This limit is not determined by the bit depth of internal calculations.
 *
 * The specified parameter does *not* include the base .5 digitization error but
 * it is added here.
 */
static double outerr(const png_modifier *pm, int in_depth, int out_depth)
{
   /* There is a serious error in the 2 and 4 bit grayscale transform because
    * the gamma table value (8 bits) is simply shifted, not rounded, so the
    * error in 4 bit grayscale gamma is up to the value below.  This is a hack
    * to allow pngvalid to succeed:
    *
    * TODO: fix this in libpng
    */
   if (out_depth == 2)
      return .73182-.5;

   if (out_depth == 4)
      return .90644-.5;

   if ((pm->calculations_use_input_precision ? in_depth : out_depth) == 16)
      return pm->maxout16;

   /* This is the case where the value was calculated at 8-bit precision then
    * scaled to 16 bits.
    */
   else if (out_depth == 16)
      return pm->maxout8 * 257;

   else
      return pm->maxout8;
}

/* This does the same thing as the above however it returns the value to log,
 * rather than raising a warning.  This is useful for debugging to track down
 * exactly what set of parameters cause high error values.
 */
static double outlog(const png_modifier *pm, int in_depth, int out_depth)
{
   /* The command line parameters are either 8 bit (0..255) or 16 bit (0..65535)
    * and so must be adjusted for low bit depth grayscale:
    */
   if (out_depth <= 8)
   {
      if (pm->log8 == 0) /* switched off */
         return 256;

      if (out_depth < 8)
         return pm->log8 / 255 * ((1<<out_depth)-1);

      return pm->log8;
   }

   if ((pm->calculations_use_input_precision ? in_depth : out_depth) == 16)
   {
      if (pm->log16 == 0)
         return 65536;

      return pm->log16;
   }

   /* This is the case where the value was calculated at 8-bit precision then
    * scaled to 16 bits.
    */
   if (pm->log8 == 0)
      return 65536;

   return pm->log8 * 257;
}

/* This complements the above by providing the appropriate quantization for the
 * final value.  Normally this would just be quantization to an integral value,
 * but in the 8 bit calculation case it's actually quantization to a multiple of
 * 257!
 */
static int output_quantization_factor(const png_modifier *pm, int in_depth,
   int out_depth)
{
   if (out_depth == 16 && in_depth != 16 &&
      pm->calculations_use_input_precision)
      return 257;
   else
      return 1;
}
#endif /* PNG_READ_GAMMA_SUPPORTED */

/* One modification structure must be provided for each chunk to be modified (in
 * fact more than one can be provided if multiple separate changes are desired
 * for a single chunk.)  Modifications include adding a new chunk when a
 * suitable chunk does not exist.
 *
 * The caller of modify_fn will reset the CRC of the chunk and record 'modified'
 * or 'added' as appropriate if the modify_fn returns 1 (true).  If the
 * modify_fn is NULL the chunk is simply removed.
 */
typedef struct png_modification
{
   struct png_modification *next;
   png_uint_32              chunk;

   /* If the following is NULL all matching chunks will be removed: */
   int                    (*modify_fn)(struct png_modifier *pm,
                               struct png_modification *me, int add);

   /* If the following is set to PLTE, IDAT or IEND and the chunk has not been
    * found and modified (and there is a modify_fn) the modify_fn will be called
    * to add the chunk before the relevant chunk.
    */
   png_uint_32              add;
   unsigned int             modified :1;     /* Chunk was modified */
   unsigned int             added    :1;     /* Chunk was added */
   unsigned int             removed  :1;     /* Chunk was removed */
} png_modification;

static void
modification_reset(png_modification *pmm)
{
   if (pmm != NULL)
   {
      pmm->modified = 0;
      pmm->added = 0;
      pmm->removed = 0;
      modification_reset(pmm->next);
   }
}

static void
modification_init(png_modification *pmm)
{
   memset(pmm, 0, sizeof *pmm);
   pmm->next = NULL;
   pmm->chunk = 0;
   pmm->modify_fn = NULL;
   pmm->add = 0;
   modification_reset(pmm);
}

#ifdef PNG_READ_RGB_TO_GRAY_SUPPORTED
static void
modifier_current_encoding(const png_modifier *pm, color_encoding *ce)
{
   if (pm->current_encoding != 0)
      *ce = *pm->current_encoding;

   else
      memset(ce, 0, sizeof *ce);

   ce->gamma = pm->current_gamma;
}
#endif

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
static size_t
safecat_current_encoding(char *buffer, size_t bufsize, size_t pos,
   const png_modifier *pm)
{
   pos = safecat_color_encoding(buffer, bufsize, pos, pm->current_encoding,
      pm->current_gamma);

   if (pm->encoding_ignored)
      pos = safecat(buffer, bufsize, pos, "[overridden]");

   return pos;
}
#endif

/* Iterate through the usefully testable color encodings.  An encoding is one
 * of:
 *
 * 1) Nothing (no color space, no gamma).
 * 2) Just a gamma value from the gamma array (including 1.0)
 * 3) A color space from the encodings array with the corresponding gamma.
 * 4) The same, but with gamma 1.0 (only really useful with 16 bit calculations)
 *
 * The iterator selects these in turn, the randomizer selects one at random,
 * which is used depends on the setting of the 'test_exhaustive' flag.  Notice
 * that this function changes the colour space encoding so it must only be
 * called on completion of the previous test.  This is what 'modifier_reset'
 * does, below.
 *
 * After the function has been called the 'repeat' flag will still be set; the
 * caller of modifier_reset must reset it at the start of each run of the test!
 */
static unsigned int
modifier_total_encodings(const png_modifier *pm)
{
   return 1 +                 /* (1) nothing */
      pm->ngammas +           /* (2) gamma values to test */
      pm->nencodings +        /* (3) total number of encodings */
      /* The following test only works after the first time through the
       * png_modifier code because 'bit_depth' is set when the IHDR is read.
       * modifier_reset, below, preserves the setting until after it has called
       * the iterate function (also below.)
       *
       * For this reason do not rely on this function outside a call to
       * modifier_reset.
       */
      ((pm->bit_depth == 16 || pm->assume_16_bit_calculations) ?
         pm->nencodings : 0); /* (4) encodings with gamma == 1.0 */
}

static void
modifier_encoding_iterate(png_modifier *pm)
{
   if (!pm->repeat && /* Else something needs the current encoding again. */
      pm->test_uses_encoding) /* Some transform is encoding dependent */
   {
      if (pm->test_exhaustive)
      {
         if (++pm->encoding_counter >= modifier_total_encodings(pm))
            pm->encoding_counter = 0; /* This will stop the repeat */
      }

      else
      {
         /* Not exhaustive - choose an encoding at random; generate a number in
          * the range 1..(max-1), so the result is always non-zero:
          */
         if (pm->encoding_counter == 0)
            pm->encoding_counter = random_mod(modifier_total_encodings(pm)-1)+1;
         else
            pm->encoding_counter = 0;
      }

      if (pm->encoding_counter > 0)
         pm->repeat = 1;
   }

   else if (!pm->repeat)
      pm->encoding_counter = 0;
}

static void
modifier_reset(png_modifier *pm)
{
   store_read_reset(&pm->this);
   pm->limit = 4E-3;
   pm->pending_len = pm->pending_chunk = 0;
   pm->flush = pm->buffer_count = pm->buffer_position = 0;
   pm->modifications = NULL;
   pm->state = modifier_start;
   modifier_encoding_iterate(pm);
   /* The following must be set in the next run.  In particular
    * test_uses_encodings must be set in the _ini function of each transform
    * that looks at the encodings.  (Not the 'add' function!)
    */
   pm->test_uses_encoding = 0;
   pm->current_gamma = 0;
   pm->current_encoding = 0;
   pm->encoding_ignored = 0;
   /* These only become value after IHDR is read: */
   pm->bit_depth = pm->colour_type = 0;
}

/* The following must be called before anything else to get the encoding set up
 * on the modifier.  In particular it must be called before the transform init
 * functions are called.
 */
static void
modifier_set_encoding(png_modifier *pm)
{
   /* Set the encoding to the one specified by the current encoding counter,
    * first clear out all the settings - this corresponds to an encoding_counter
    * of 0.
    */
   pm->current_gamma = 0;
   pm->current_encoding = 0;
   pm->encoding_ignored = 0; /* not ignored yet - happens in _ini functions. */

   /* Now, if required, set the gamma and encoding fields. */
   if (pm->encoding_counter > 0)
   {
      /* The gammas[] array is an array of screen gammas, not encoding gammas,
       * so we need the inverse:
       */
      if (pm->encoding_counter <= pm->ngammas)
         pm->current_gamma = 1/pm->gammas[pm->encoding_counter-1];

      else
      {
         unsigned int i = pm->encoding_counter - pm->ngammas;

         if (i >= pm->nencodings)
         {
            i %= pm->nencodings;
            pm->current_gamma = 1; /* Linear, only in the 16 bit case */
         }

         else
            pm->current_gamma = pm->encodings[i].gamma;

         pm->current_encoding = pm->encodings + i;
      }
   }
}

/* Enquiry functions to find out what is set.  Notice that there is an implicit
 * assumption below that the first encoding in the list is the one for sRGB.
 */
static int
modifier_color_encoding_is_sRGB(const png_modifier *pm)
{
   return pm->current_encoding != 0 && pm->current_encoding == pm->encodings &&
      pm->current_encoding->gamma == pm->current_gamma;
}

static int
modifier_color_encoding_is_set(const png_modifier *pm)
{
   return pm->current_gamma != 0;
}

/* The guts of modification are performed during a read. */
static void
modifier_crc(png_bytep buffer)
{
   /* Recalculate the chunk CRC - a complete chunk must be in
    * the buffer, at the start.
    */
   uInt datalen = png_get_uint_32(buffer);
   uLong crc = crc32(0, buffer+4, datalen+4);
   /* The cast to png_uint_32 is safe because a crc32 is always a 32 bit value.
    */
   png_save_uint_32(buffer+datalen+8, (png_uint_32)crc);
}

static void
modifier_setbuffer(png_modifier *pm)
{
   modifier_crc(pm->buffer);
   pm->buffer_count = png_get_uint_32(pm->buffer)+12;
   pm->buffer_position = 0;
}

/* Separate the callback into the actual implementation (which is passed the
 * png_modifier explicitly) and the callback, which gets the modifier from the
 * png_struct.
 */
static void
modifier_read_imp(png_modifier *pm, png_bytep pb, size_t st)
{
   while (st > 0)
   {
      size_t cb;
      png_uint_32 len, chunk;
      png_modification *mod;

      if (pm->buffer_position >= pm->buffer_count) switch (pm->state)
      {
         static png_byte sign[8] = { 137, 80, 78, 71, 13, 10, 26, 10 };
         case modifier_start:
            store_read_chunk(&pm->this, pm->buffer, 8, 8); /* signature. */
            pm->buffer_count = 8;
            pm->buffer_position = 0;

            if (memcmp(pm->buffer, sign, 8) != 0)
               png_error(pm->this.pread, "invalid PNG file signature");
            pm->state = modifier_signature;
            break;

         case modifier_signature:
            store_read_chunk(&pm->this, pm->buffer, 13+12, 13+12); /* IHDR */
            pm->buffer_count = 13+12;
            pm->buffer_position = 0;

            if (png_get_uint_32(pm->buffer) != 13 ||
                png_get_uint_32(pm->buffer+4) != CHUNK_IHDR)
               png_error(pm->this.pread, "invalid IHDR");

            /* Check the list of modifiers for modifications to the IHDR. */
            mod = pm->modifications;
            while (mod != NULL)
            {
               if (mod->chunk == CHUNK_IHDR && mod->modify_fn &&
                   (*mod->modify_fn)(pm, mod, 0))
                  {
                  mod->modified = 1;
                  modifier_setbuffer(pm);
                  }

               /* Ignore removal or add if IHDR! */
               mod = mod->next;
            }

            /* Cache information from the IHDR (the modified one.) */
            pm->bit_depth = pm->buffer[8+8];
            pm->colour_type = pm->buffer[8+8+1];

            pm->state = modifier_IHDR;
            pm->flush = 0;
            break;

         case modifier_IHDR:
         default:
            /* Read a new chunk and process it until we see PLTE, IDAT or
             * IEND.  'flush' indicates that there is still some data to
             * output from the preceding chunk.
             */
            if ((cb = pm->flush) > 0)
            {
               if (cb > st) cb = st;
               pm->flush -= cb;
               store_read_chunk(&pm->this, pb, cb, cb);
               pb += cb;
               st -= cb;
               if (st == 0) return;
            }

            /* No more bytes to flush, read a header, or handle a pending
             * chunk.
             */
            if (pm->pending_chunk != 0)
            {
               png_save_uint_32(pm->buffer, pm->pending_len);
               png_save_uint_32(pm->buffer+4, pm->pending_chunk);
               pm->pending_len = 0;
               pm->pending_chunk = 0;
            }
            else
               store_read_chunk(&pm->this, pm->buffer, 8, 8);

            pm->buffer_count = 8;
            pm->buffer_position = 0;

            /* Check for something to modify or a terminator chunk. */
            len = png_get_uint_32(pm->buffer);
            chunk = png_get_uint_32(pm->buffer+4);

            /* Terminators first, they may have to be delayed for added
             * chunks
             */
            if (chunk == CHUNK_PLTE || chunk == CHUNK_IDAT ||
                chunk == CHUNK_IEND)
            {
               mod = pm->modifications;

               while (mod != NULL)
               {
                  if ((mod->add == chunk ||
                      (mod->add == CHUNK_PLTE && chunk == CHUNK_IDAT)) &&
                      mod->modify_fn != NULL && !mod->modified && !mod->added)
                  {
                     /* Regardless of what the modify function does do not run
                      * this again.
                      */
                     mod->added = 1;

                     if ((*mod->modify_fn)(pm, mod, 1 /*add*/))
                     {
                        /* Reset the CRC on a new chunk */
                        if (pm->buffer_count > 0)
                           modifier_setbuffer(pm);

                        else
                           {
                           pm->buffer_position = 0;
                           mod->removed = 1;
                           }

                        /* The buffer has been filled with something (we assume)
                         * so output this.  Pend the current chunk.
                         */
                        pm->pending_len = len;
                        pm->pending_chunk = chunk;
                        break; /* out of while */
                     }
                  }

                  mod = mod->next;
               }

               /* Don't do any further processing if the buffer was modified -
                * otherwise the code will end up modifying a chunk that was
                * just added.
                */
               if (mod != NULL)
                  break; /* out of switch */
            }

            /* If we get to here then this chunk may need to be modified.  To
             * do this it must be less than 1024 bytes in total size, otherwise
             * it just gets flushed.
             */
            if (len+12 <= sizeof pm->buffer)
            {
               size_t s = len+12-pm->buffer_count;
               store_read_chunk(&pm->this, pm->buffer+pm->buffer_count, s, s);
               pm->buffer_count = len+12;

               /* Check for a modification, else leave it be. */
               mod = pm->modifications;
               while (mod != NULL)
               {
                  if (mod->chunk == chunk)
                  {
                     if (mod->modify_fn == NULL)
                     {
                        /* Remove this chunk */
                        pm->buffer_count = pm->buffer_position = 0;
                        mod->removed = 1;
                        break; /* Terminate the while loop */
                     }

                     else if ((*mod->modify_fn)(pm, mod, 0))
                     {
                        mod->modified = 1;
                        /* The chunk may have been removed: */
                        if (pm->buffer_count == 0)
                        {
                           pm->buffer_position = 0;
                           break;
                        }
                        modifier_setbuffer(pm);
                     }
                  }

                  mod = mod->next;
               }
            }

            else
               pm->flush = len+12 - pm->buffer_count; /* data + crc */

            /* Take the data from the buffer (if there is any). */
            break;
      }

      /* Here to read from the modifier buffer (not directly from
       * the store, as in the flush case above.)
       */
      cb = pm->buffer_count - pm->buffer_position;

      if (cb > st)
         cb = st;

      memcpy(pb, pm->buffer + pm->buffer_position, cb);
      st -= cb;
      pb += cb;
      pm->buffer_position += cb;
   }
}

/* The callback: */
static void PNGCBAPI
modifier_read(png_structp ppIn, png_bytep pb, size_t st)
{
   png_const_structp pp = ppIn;
   png_modifier *pm = voidcast(png_modifier*, png_get_io_ptr(pp));

   if (pm == NULL || pm->this.pread != pp)
      png_error(pp, "bad modifier_read call");

   modifier_read_imp(pm, pb, st);
}

/* Like store_progressive_read but the data is getting changed as we go so we
 * need a local buffer.
 */
static void
modifier_progressive_read(png_modifier *pm, png_structp pp, png_infop pi)
{
   if (pm->this.pread != pp || pm->this.current == NULL ||
       pm->this.next == NULL)
      png_error(pp, "store state damaged (progressive)");

   /* This is another Horowitz and Hill random noise generator.  In this case
    * the aim is to stress the progressive reader with truly horrible variable
    * buffer sizes in the range 1..500, so a sequence of 9 bit random numbers
    * is generated.  We could probably just count from 1 to 32767 and get as
    * good a result.
    */
   for (;;)
   {
      static png_uint_32 noise = 1;
      size_t cb, cbAvail;
      png_byte buffer[512];

      /* Generate 15 more bits of stuff: */
      noise = (noise << 9) | ((noise ^ (noise >> (9-5))) & 0x1ff);
      cb = noise & 0x1ff;

      /* Check that this number of bytes are available (in the current buffer.)
       * (This doesn't quite work - the modifier might delete a chunk; unlikely
       * but possible, it doesn't happen at present because the modifier only
       * adds chunks to standard images.)
       */
      cbAvail = store_read_buffer_avail(&pm->this);
      if (pm->buffer_count > pm->buffer_position)
         cbAvail += pm->buffer_count - pm->buffer_position;

      if (cb > cbAvail)
      {
         /* Check for EOF: */
         if (cbAvail == 0)
            break;

         cb = cbAvail;
      }

      modifier_read_imp(pm, buffer, cb);
      png_process_data(pp, pi, buffer, cb);
   }

   /* Check the invariants at the end (if this fails it's a problem in this
    * file!)
    */
   if (pm->buffer_count > pm->buffer_position ||
       pm->this.next != &pm->this.current->data ||
       pm->this.readpos < pm->this.current->datacount)
      png_error(pp, "progressive read implementation error");
}

/* Set up a modifier. */
static png_structp
set_modifier_for_read(png_modifier *pm, png_infopp ppi, png_uint_32 id,
    const char *name)
{
   /* Do this first so that the modifier fields are cleared even if an error
    * happens allocating the png_struct.  No allocation is done here so no
    * cleanup is required.
    */
   pm->state = modifier_start;
   pm->bit_depth = 0;
   pm->colour_type = 255;

   pm->pending_len = 0;
   pm->pending_chunk = 0;
   pm->flush = 0;
   pm->buffer_count = 0;
   pm->buffer_position = 0;

   return set_store_for_read(&pm->this, ppi, id, name);
}


/******************************** MODIFICATIONS *******************************/
/* Standard modifications to add chunks.  These do not require the _SUPPORTED
 * macros because the chunks can be there regardless of whether this specific
 * libpng supports them.
 */
typedef struct gama_modification
{
   png_modification this;
   png_fixed_point  gamma;
} gama_modification;

static int
gama_modify(png_modifier *pm, png_modification *me, int add)
{
   UNUSED(add)
   /* This simply dumps the given gamma value into the buffer. */
   png_save_uint_32(pm->buffer, 4);
   png_save_uint_32(pm->buffer+4, CHUNK_gAMA);
   png_save_uint_32(pm->buffer+8, ((gama_modification*)me)->gamma);
   return 1;
}

static void
gama_modification_init(gama_modification *me, png_modifier *pm, double gammad)
{
   double g;

   modification_init(&me->this);
   me->this.chunk = CHUNK_gAMA;
   me->this.modify_fn = gama_modify;
   me->this.add = CHUNK_PLTE;
   g = fix(gammad);
   me->gamma = (png_fixed_point)g;
   me->this.next = pm->modifications;
   pm->modifications = &me->this;
}

typedef struct chrm_modification
{
   png_modification          this;
   const color_encoding *encoding;
   png_fixed_point           wx, wy, rx, ry, gx, gy, bx, by;
} chrm_modification;

static int
chrm_modify(png_modifier *pm, png_modification *me, int add)
{
   UNUSED(add)
   /* As with gAMA this just adds the required cHRM chunk to the buffer. */
   png_save_uint_32(pm->buffer   , 32);
   png_save_uint_32(pm->buffer+ 4, CHUNK_cHRM);
   png_save_uint_32(pm->buffer+ 8, ((chrm_modification*)me)->wx);
   png_save_uint_32(pm->buffer+12, ((chrm_modification*)me)->wy);
   png_save_uint_32(pm->buffer+16, ((chrm_modification*)me)->rx);
   png_save_uint_32(pm->buffer+20, ((chrm_modification*)me)->ry);
   png_save_uint_32(pm->buffer+24, ((chrm_modification*)me)->gx);
   png_save_uint_32(pm->buffer+28, ((chrm_modification*)me)->gy);
   png_save_uint_32(pm->buffer+32, ((chrm_modification*)me)->bx);
   png_save_uint_32(pm->buffer+36, ((chrm_modification*)me)->by);
   return 1;
}

static void
chrm_modification_init(chrm_modification *me, png_modifier *pm,
   const color_encoding *encoding)
{
   CIE_color white = white_point(encoding);

   /* Original end points: */
   me->encoding = encoding;

   /* Chromaticities (in fixed point): */
   me->wx = fix(chromaticity_x(white));
   me->wy = fix(chromaticity_y(white));

   me->rx = fix(chromaticity_x(encoding->red));
   me->ry = fix(chromaticity_y(encoding->red));
   me->gx = fix(chromaticity_x(encoding->green));
   me->gy = fix(chromaticity_y(encoding->green));
   me->bx = fix(chromaticity_x(encoding->blue));
   me->by = fix(chromaticity_y(encoding->blue));

   modification_init(&me->this);
   me->this.chunk = CHUNK_cHRM;
   me->this.modify_fn = chrm_modify;
   me->this.add = CHUNK_PLTE;
   me->this.next = pm->modifications;
   pm->modifications = &me->this;
}

typedef struct srgb_modification
{
   png_modification this;
   png_byte         intent;
} srgb_modification;

static int
srgb_modify(png_modifier *pm, png_modification *me, int add)
{
   UNUSED(add)
   /* As above, ignore add and just make a new chunk */
   png_save_uint_32(pm->buffer, 1);
   png_save_uint_32(pm->buffer+4, CHUNK_sRGB);
   pm->buffer[8] = ((srgb_modification*)me)->intent;
   return 1;
}

static void
srgb_modification_init(srgb_modification *me, png_modifier *pm, png_byte intent)
{
   modification_init(&me->this);
   me->this.chunk = CHUNK_sBIT;

   if (intent <= 3) /* if valid, else *delete* sRGB chunks */
   {
      me->this.modify_fn = srgb_modify;
      me->this.add = CHUNK_PLTE;
      me->intent = intent;
   }

   else
   {
      me->this.modify_fn = 0;
      me->this.add = 0;
      me->intent = 0;
   }

   me->this.next = pm->modifications;
   pm->modifications = &me->this;
}

#ifdef PNG_READ_GAMMA_SUPPORTED
typedef struct sbit_modification
{
   png_modification this;
   png_byte         sbit;
} sbit_modification;

static int
sbit_modify(png_modifier *pm, png_modification *me, int add)
{
   png_byte sbit = ((sbit_modification*)me)->sbit;
   if (pm->bit_depth > sbit)
   {
      int cb = 0;
      switch (pm->colour_type)
      {
         case 0:
            cb = 1;
            break;

         case 2:
         case 3:
            cb = 3;
            break;

         case 4:
            cb = 2;
            break;

         case 6:
            cb = 4;
            break;

         default:
            png_error(pm->this.pread,
               "unexpected colour type in sBIT modification");
      }

      png_save_uint_32(pm->buffer, cb);
      png_save_uint_32(pm->buffer+4, CHUNK_sBIT);

      while (cb > 0)
         (pm->buffer+8)[--cb] = sbit;

      return 1;
   }
   else if (!add)
   {
      /* Remove the sBIT chunk */
      pm->buffer_count = pm->buffer_position = 0;
      return 1;
   }
   else
      return 0; /* do nothing */
}

static void
sbit_modification_init(sbit_modification *me, png_modifier *pm, png_byte sbit)
{
   modification_init(&me->this);
   me->this.chunk = CHUNK_sBIT;
   me->this.modify_fn = sbit_modify;
   me->this.add = CHUNK_PLTE;
   me->sbit = sbit;
   me->this.next = pm->modifications;
   pm->modifications = &me->this;
}
#endif /* PNG_READ_GAMMA_SUPPORTED */
#endif /* PNG_READ_TRANSFORMS_SUPPORTED */

/***************************** STANDARD PNG FILES *****************************/
/* Standard files - write and save standard files. */
/* There are two basic forms of standard images.  Those which attempt to have
 * all the possible pixel values (not possible for 16bpp images, but a range of
 * values are produced) and those which have a range of image sizes.  The former
 * are used for testing transforms, in particular gamma correction and bit
 * reduction and increase.  The latter are reserved for testing the behavior of
 * libpng with respect to 'odd' image sizes - particularly small images where
 * rows become 1 byte and interlace passes disappear.
 *
 * The first, most useful, set are the 'transform' images, the second set of
 * small images are the 'size' images.
 *
 * The transform files are constructed with rows which fit into a 1024 byte row
 * buffer.  This makes allocation easier below.  Further regardless of the file
 * format every row has 128 pixels (giving 1024 bytes for 64bpp formats).
 *
 * Files are stored with no gAMA or sBIT chunks, with a PLTE only when needed
 * and with an ID derived from the colour type, bit depth and interlace type
 * as above (FILEID).  The width (128) and height (variable) are not stored in
 * the FILEID - instead the fields are set to 0, indicating a transform file.
 *
 * The size files ar constructed with rows a maximum of 128 bytes wide, allowing
 * a maximum width of 16 pixels (for the 64bpp case.)  They also have a maximum
 * height of 16 rows.  The width and height are stored in the FILEID and, being
 * non-zero, indicate a size file.
 *
 * Because the PNG filter code is typically the largest CPU consumer within
 * libpng itself there is a tendency to attempt to optimize it.  This results in
 * special case code which needs to be validated.  To cause this to happen the
 * 'size' images are made to use each possible filter, in so far as this is
 * possible for smaller images.
 *
 * For palette image (colour type 3) multiple transform images are stored with
 * the same bit depth to allow testing of more colour combinations -
 * particularly important for testing the gamma code because libpng uses a
 * different code path for palette images.  For size images a single palette is
 * used.
 */

/* Make a 'standard' palette.  Because there are only 256 entries in a palette
 * (maximum) this actually makes a random palette in the hope that enough tests
 * will catch enough errors.  (Note that the same palette isn't produced every
 * time for the same test - it depends on what previous tests have been run -
 * but a given set of arguments to pngvalid will always produce the same palette
 * at the same test!  This is why pseudo-random number generators are useful for
 * testing.)
 *
 * The store must be open for write when this is called, otherwise an internal
 * error will occur.  This routine contains its own magic number seed, so the
 * palettes generated don't change if there are intervening errors (changing the
 * calls to the store_mark seed.)
 */
static store_palette_entry *
make_standard_palette(png_store* ps, int npalette, int do_tRNS)
{
   static png_uint_32 palette_seed[2] = { 0x87654321, 9 };

   int i = 0;
   png_byte values[256][4];

   /* Always put in black and white plus the six primary and secondary colors.
    */
   for (; i<8; ++i)
   {
      values[i][1] = (png_byte)((i&1) ? 255U : 0U);
      values[i][2] = (png_byte)((i&2) ? 255U : 0U);
      values[i][3] = (png_byte)((i&4) ? 255U : 0U);
   }

   /* Then add 62 grays (one quarter of the remaining 256 slots). */
   {
      int j = 0;
      png_byte random_bytes[4];
      png_byte need[256];

      need[0] = 0; /*got black*/
      memset(need+1, 1, (sizeof need)-2); /*need these*/
      need[255] = 0; /*but not white*/

      while (i<70)
      {
         png_byte b;

         if (j==0)
         {
            make_four_random_bytes(palette_seed, random_bytes);
            j = 4;
         }

         b = random_bytes[--j];
         if (need[b])
         {
            values[i][1] = b;
            values[i][2] = b;
            values[i++][3] = b;
         }
      }
   }

   /* Finally add 192 colors at random - don't worry about matches to things we
    * already have, chance is less than 1/65536.  Don't worry about grays,
    * chance is the same, so we get a duplicate or extra gray less than 1 time
    * in 170.
    */
   for (; i<256; ++i)
      make_four_random_bytes(palette_seed, values[i]);

   /* Fill in the alpha values in the first byte.  Just use all possible values
    * (0..255) in an apparently random order:
    */
   {
      store_palette_entry *palette;
      png_byte selector[4];

      make_four_random_bytes(palette_seed, selector);

      if (do_tRNS)
         for (i=0; i<256; ++i)
            values[i][0] = (png_byte)(i ^ selector[0]);

      else
         for (i=0; i<256; ++i)
            values[i][0] = 255; /* no transparency/tRNS chunk */

      /* 'values' contains 256 ARGB values, but we only need 'npalette'.
       * 'npalette' will always be a power of 2: 2, 4, 16 or 256.  In the low
       * bit depth cases select colors at random, else it is difficult to have
       * a set of low bit depth palette test with any chance of a reasonable
       * range of colors.  Do this by randomly permuting values into the low
       * 'npalette' entries using an XOR mask generated here.  This also
       * permutes the npalette == 256 case in a potentially useful way (there is
       * no relationship between palette index and the color value therein!)
       */
      palette = store_write_palette(ps, npalette);

      for (i=0; i<npalette; ++i)
      {
         palette[i].alpha = values[i ^ selector[1]][0];
         palette[i].red   = values[i ^ selector[1]][1];
         palette[i].green = values[i ^ selector[1]][2];
         palette[i].blue  = values[i ^ selector[1]][3];
      }

      return palette;
   }
}

/* Initialize a standard palette on a write stream.  The 'do_tRNS' argument
 * indicates whether or not to also set the tRNS chunk.
 */
/* TODO: the png_structp here can probably be 'const' in the future */
static void
init_standard_palette(png_store *ps, png_structp pp, png_infop pi, int npalette,
   int do_tRNS)
{
   store_palette_entry *ppal = make_standard_palette(ps, npalette, do_tRNS);

   {
      int i;
      png_color palette[256];

      /* Set all entries to detect overread errors. */
      for (i=0; i<npalette; ++i)
      {
         palette[i].red = ppal[i].red;
         palette[i].green = ppal[i].green;
         palette[i].blue = ppal[i].blue;
      }

      /* Just in case fill in the rest with detectable values: */
      for (; i<256; ++i)
         palette[i].red = palette[i].green = palette[i].blue = 42;

      png_set_PLTE(pp, pi, palette, npalette);
   }

   if (do_tRNS)
   {
      int i, j;
      png_byte tRNS[256];

      /* Set all the entries, but skip trailing opaque entries */
      for (i=j=0; i<npalette; ++i)
         if ((tRNS[i] = ppal[i].alpha) < 255)
            j = i+1;

      /* Fill in the remainder with a detectable value: */
      for (; i<256; ++i)
         tRNS[i] = 24;

#ifdef PNG_WRITE_tRNS_SUPPORTED
      if (j > 0)
         png_set_tRNS(pp, pi, tRNS, j, 0/*color*/);
#endif
   }
}

#ifdef PNG_WRITE_tRNS_SUPPORTED
static void
set_random_tRNS(png_structp pp, png_infop pi, png_byte colour_type,
   int bit_depth)
{
   /* To make this useful the tRNS color needs to match at least one pixel.
    * Random values are fine for gray, including the 16-bit case where we know
    * that the test image contains all the gray values.  For RGB we need more
    * method as only 65536 different RGB values are generated.
    */
   png_color_16 tRNS;
   png_uint_16 mask = (png_uint_16)((1U << bit_depth)-1);

   R8(tRNS); /* makes unset fields random */

   if (colour_type & 2/*RGB*/)
   {
      if (bit_depth == 8)
      {
         tRNS.red = random_u16();
         tRNS.green = random_u16();
         tRNS.blue = tRNS.red ^ tRNS.green;
         tRNS.red &= mask;
         tRNS.green &= mask;
         tRNS.blue &= mask;
      }

      else /* bit_depth == 16 */
      {
         tRNS.red = random_u16();
         tRNS.green = (png_uint_16)(tRNS.red * 257);
         tRNS.blue = (png_uint_16)(tRNS.green * 17);
      }
   }

   else
   {
      tRNS.gray = random_u16();
      tRNS.gray &= mask;
   }

   png_set_tRNS(pp, pi, NULL, 0, &tRNS);
}
#endif

/* The number of passes is related to the interlace type. There was no libpng
 * API to determine this prior to 1.5, so we need an inquiry function:
 */
static int
npasses_from_interlace_type(png_const_structp pp, int interlace_type)
{
   switch (interlace_type)
   {
   default:
      png_error(pp, "invalid interlace type");

   case PNG_INTERLACE_NONE:
      return 1;

   case PNG_INTERLACE_ADAM7:
      return PNG_INTERLACE_ADAM7_PASSES;
   }
}

static unsigned int
bit_size(png_const_structp pp, png_byte colour_type, png_byte bit_depth)
{
   switch (colour_type)
   {
      default: png_error(pp, "invalid color type");

      case 0:  return bit_depth;

      case 2:  return 3*bit_depth;

      case 3:  return bit_depth;

      case 4:  return 2*bit_depth;

      case 6:  return 4*bit_depth;
   }
}

#define TRANSFORM_WIDTH  128U
#define TRANSFORM_ROWMAX (TRANSFORM_WIDTH*8U)
#define SIZE_ROWMAX (16*8U) /* 16 pixels, max 8 bytes each - 128 bytes */
#define STANDARD_ROWMAX TRANSFORM_ROWMAX /* The larger of the two */
#define SIZE_HEIGHTMAX 16 /* Maximum range of size images */

static size_t
transform_rowsize(png_const_structp pp, png_byte colour_type,
   png_byte bit_depth)
{
   return (TRANSFORM_WIDTH * bit_size(pp, colour_type, bit_depth)) / 8;
}

/* transform_width(pp, colour_type, bit_depth) current returns the same number
 * every time, so just use a macro:
 */
#define transform_width(pp, colour_type, bit_depth) TRANSFORM_WIDTH

static png_uint_32
transform_height(png_const_structp pp, png_byte colour_type, png_byte bit_depth)
{
   switch (bit_size(pp, colour_type, bit_depth))
   {
      case 1:
      case 2:
      case 4:
         return 1;   /* Total of 128 pixels */

      case 8:
         return 2;   /* Total of 256 pixels/bytes */

      case 16:
         return 512; /* Total of 65536 pixels */

      case 24:
      case 32:
         return 512; /* 65536 pixels */

      case 48:
      case 64:
         return 2048;/* 4 x 65536 pixels. */
#        define TRANSFORM_HEIGHTMAX 2048

      default:
         return 0;   /* Error, will be caught later */
   }
}

#ifdef PNG_READ_SUPPORTED
/* The following can only be defined here, now we have the definitions
 * of the transform image sizes.
 */
static png_uint_32
standard_width(png_const_structp pp, png_uint_32 id)
{
   png_uint_32 width = WIDTH_FROM_ID(id);
   UNUSED(pp)

   if (width == 0)
      width = transform_width(pp, COL_FROM_ID(id), DEPTH_FROM_ID(id));

   return width;
}

static png_uint_32
standard_height(png_const_structp pp, png_uint_32 id)
{
   png_uint_32 height = HEIGHT_FROM_ID(id);

   if (height == 0)
      height = transform_height(pp, COL_FROM_ID(id), DEPTH_FROM_ID(id));

   return height;
}

static png_uint_32
standard_rowsize(png_const_structp pp, png_uint_32 id)
{
   png_uint_32 width = standard_width(pp, id);

   /* This won't overflow: */
   width *= bit_size(pp, COL_FROM_ID(id), DEPTH_FROM_ID(id));
   return (width + 7) / 8;
}
#endif /* PNG_READ_SUPPORTED */

static void
transform_row(png_const_structp pp, png_byte buffer[TRANSFORM_ROWMAX],
   png_byte colour_type, png_byte bit_depth, png_uint_32 y)
{
   png_uint_32 v = y << 7;
   png_uint_32 i = 0;

   switch (bit_size(pp, colour_type, bit_depth))
   {
      case 1:
         while (i<128/8) buffer[i] = (png_byte)(v & 0xff), v += 17, ++i;
         return;

      case 2:
         while (i<128/4) buffer[i] = (png_byte)(v & 0xff), v += 33, ++i;
         return;

      case 4:
         while (i<128/2) buffer[i] = (png_byte)(v & 0xff), v += 65, ++i;
         return;

      case 8:
         /* 256 bytes total, 128 bytes in each row set as follows: */
         while (i<128) buffer[i] = (png_byte)(v & 0xff), ++v, ++i;
         return;

      case 16:
         /* Generate all 65536 pixel values in order, which includes the 8 bit
          * GA case as well as the 16 bit G case.
          */
         while (i<128)
         {
            buffer[2*i] = (png_byte)((v>>8) & 0xff);
            buffer[2*i+1] = (png_byte)(v & 0xff);
            ++v;
            ++i;
         }

         return;

      case 24:
         /* 65535 pixels, but rotate the values. */
         while (i<128)
         {
            /* Three bytes per pixel, r, g, b, make b by r^g */
            buffer[3*i+0] = (png_byte)((v >> 8) & 0xff);
            buffer[3*i+1] = (png_byte)(v & 0xff);
            buffer[3*i+2] = (png_byte)(((v >> 8) ^ v) & 0xff);
            ++v;
            ++i;
         }

         return;

      case 32:
         /* 65535 pixels, r, g, b, a; just replicate */
         while (i<128)
         {
            buffer[4*i+0] = (png_byte)((v >> 8) & 0xff);
            buffer[4*i+1] = (png_byte)(v & 0xff);
            buffer[4*i+2] = (png_byte)((v >> 8) & 0xff);
            buffer[4*i+3] = (png_byte)(v & 0xff);
            ++v;
            ++i;
         }

         return;

      case 48:
         /* y is maximum 2047, giving 4x65536 pixels, make 'r' increase by 1 at
          * each pixel, g increase by 257 (0x101) and 'b' by 0x1111:
          */
         while (i<128)
         {
            png_uint_32 t = v++;
            buffer[6*i+0] = (png_byte)((t >> 8) & 0xff);
            buffer[6*i+1] = (png_byte)(t & 0xff);
            t *= 257;
            buffer[6*i+2] = (png_byte)((t >> 8) & 0xff);
            buffer[6*i+3] = (png_byte)(t & 0xff);
            t *= 17;
            buffer[6*i+4] = (png_byte)((t >> 8) & 0xff);
            buffer[6*i+5] = (png_byte)(t & 0xff);
            ++i;
         }

         return;

      case 64:
         /* As above in the 32 bit case. */
         while (i<128)
         {
            png_uint_32 t = v++;
            buffer[8*i+0] = (png_byte)((t >> 8) & 0xff);
            buffer[8*i+1] = (png_byte)(t & 0xff);
            buffer[8*i+4] = (png_byte)((t >> 8) & 0xff);
            buffer[8*i+5] = (png_byte)(t & 0xff);
            t *= 257;
            buffer[8*i+2] = (png_byte)((t >> 8) & 0xff);
            buffer[8*i+3] = (png_byte)(t & 0xff);
            buffer[8*i+6] = (png_byte)((t >> 8) & 0xff);
            buffer[8*i+7] = (png_byte)(t & 0xff);
            ++i;
         }
         return;

      default:
         break;
   }

   png_error(pp, "internal error");
}

/* This is just to do the right cast - could be changed to a function to check
 * 'bd' but there isn't much point.
 */
#define DEPTH(bd) ((png_byte)(1U << (bd)))

/* This is just a helper for compiling on minimal systems with no write
 * interlacing support.  If there is no write interlacing we can't generate test
 * cases with interlace:
 */
#ifdef PNG_WRITE_INTERLACING_SUPPORTED
#  define INTERLACE_LAST PNG_INTERLACE_LAST
#  define check_interlace_type(type) ((void)(type))
#  define set_write_interlace_handling(pp,type) png_set_interlace_handling(pp)
#  define do_own_interlace 0
#elif PNG_LIBPNG_VER < 10700
#  define set_write_interlace_handling(pp,type) (1)
static void
check_interlace_type(int const interlace_type)
{
   /* Prior to 1.7.0 libpng does not support the write of an interlaced image
    * unless PNG_WRITE_INTERLACING_SUPPORTED, even with do_interlace so the
    * code here does the pixel interlace itself, so:
    */
   if (interlace_type != PNG_INTERLACE_NONE)
   {
      /* This is an internal error - --interlace tests should be skipped, not
       * attempted.
       */
      fprintf(stderr, "pngvalid: no interlace support\n");
      exit(99);
   }
}
#  define INTERLACE_LAST (PNG_INTERLACE_NONE+1)
#  define do_own_interlace 0
#else /* libpng 1.7+ */
#  define set_write_interlace_handling(pp,type)\
      npasses_from_interlace_type(pp,type)
#  define check_interlace_type(type) ((void)(type))
#  define INTERLACE_LAST PNG_INTERLACE_LAST
#  define do_own_interlace 1
#endif /* WRITE_INTERLACING tests */

#if PNG_LIBPNG_VER >= 10700 || defined PNG_WRITE_INTERLACING_SUPPORTED
#   define CAN_WRITE_INTERLACE 1
#else
#   define CAN_WRITE_INTERLACE 0
#endif

/* Do the same thing for read interlacing; this controls whether read tests do
 * their own de-interlace or use libpng.
 */
#ifdef PNG_READ_INTERLACING_SUPPORTED
#  define do_read_interlace 0
#else /* no libpng read interlace support */
#  define do_read_interlace 1
#endif
/* The following two routines use the PNG interlace support macros from
 * png.h to interlace or deinterlace rows.
 */
static void
interlace_row(png_bytep buffer, png_const_bytep imageRow,
   unsigned int pixel_size, png_uint_32 w, int pass, int littleendian)
{
   png_uint_32 xin, xout, xstep;

   /* Note that this can, trivially, be optimized to a memcpy on pass 7, the
    * code is presented this way to make it easier to understand.  In practice
    * consult the code in the libpng source to see other ways of doing this.
    *
    * It is OK for buffer and imageRow to be identical, because 'xin' moves
    * faster than 'xout' and we copy up.
    */
   xin = PNG_PASS_START_COL(pass);
   xstep = 1U<<PNG_PASS_COL_SHIFT(pass);

   for (xout=0; xin<w; xin+=xstep)
   {
      pixel_copy(buffer, xout, imageRow, xin, pixel_size, littleendian);
      ++xout;
   }
}

#ifdef PNG_READ_SUPPORTED
static void
deinterlace_row(png_bytep buffer, png_const_bytep row,
   unsigned int pixel_size, png_uint_32 w, int pass, int littleendian)
{
   /* The inverse of the above, 'row' is part of row 'y' of the output image,
    * in 'buffer'.  The image is 'w' wide and this is pass 'pass', distribute
    * the pixels of row into buffer and return the number written (to allow
    * this to be checked).
    */
   png_uint_32 xin, xout, xstep;

   xout = PNG_PASS_START_COL(pass);
   xstep = 1U<<PNG_PASS_COL_SHIFT(pass);

   for (xin=0; xout<w; xout+=xstep)
   {
      pixel_copy(buffer, xout, row, xin, pixel_size, littleendian);
      ++xin;
   }
}
#endif /* PNG_READ_SUPPORTED */

/* Make a standardized image given an image colour type, bit depth and
 * interlace type.  The standard images have a very restricted range of
 * rows and heights and are used for testing transforms rather than image
 * layout details.  See make_size_images below for a way to make images
 * that test odd sizes along with the libpng interlace handling.
 */
#ifdef PNG_WRITE_FILTER_SUPPORTED
static void
choose_random_filter(png_structp pp, int start)
{
   /* Choose filters randomly except that on the very first row ensure that
    * there is at least one previous row filter.
    */
   int filters = PNG_ALL_FILTERS & random_mod(256U);

   /* There may be no filters; skip the setting. */
   if (filters != 0)
   {
      if (start && filters < PNG_FILTER_UP)
         filters |= PNG_FILTER_UP;

      png_set_filter(pp, 0/*method*/, filters);
   }
}
#else /* !WRITE_FILTER */
#  define choose_random_filter(pp, start) ((void)0)
#endif /* !WRITE_FILTER */

static void
make_transform_image(png_store* const ps, png_byte const colour_type,
    png_byte const bit_depth, unsigned int palette_number,
    int interlace_type, png_const_charp name)
{
   context(ps, fault);

   check_interlace_type(interlace_type);

   Try
   {
      png_infop pi;
      png_structp pp = set_store_for_write(ps, &pi, name);
      png_uint_32 h, w;

      /* In the event of a problem return control to the Catch statement below
       * to do the clean up - it is not possible to 'return' directly from a Try
       * block.
       */
      if (pp == NULL)
         Throw ps;

      w = transform_width(pp, colour_type, bit_depth);
      h = transform_height(pp, colour_type, bit_depth);

      png_set_IHDR(pp, pi, w, h, bit_depth, colour_type, interlace_type,
         PNG_COMPRESSION_TYPE_BASE, PNG_FILTER_TYPE_BASE);

#ifdef PNG_TEXT_SUPPORTED
#  if defined(PNG_READ_zTXt_SUPPORTED) && defined(PNG_WRITE_zTXt_SUPPORTED)
#     define TEXT_COMPRESSION PNG_TEXT_COMPRESSION_zTXt
#  else
#     define TEXT_COMPRESSION PNG_TEXT_COMPRESSION_NONE
#  endif
      {
         static char key[] = "image name"; /* must be writeable */
         size_t pos;
         png_text text;
         char copy[FILE_NAME_SIZE];

         /* Use a compressed text string to test the correct interaction of text
          * compression and IDAT compression.
          */
         text.compression = TEXT_COMPRESSION;
         text.key = key;
         /* Yuck: the text must be writable! */
         pos = safecat(copy, sizeof copy, 0, ps->wname);
         text.text = copy;
         text.text_length = pos;
         text.itxt_length = 0;
         text.lang = 0;
         text.lang_key = 0;

         png_set_text(pp, pi, &text, 1);
      }
#endif

      if (colour_type == 3) /* palette */
         init_standard_palette(ps, pp, pi, 1U << bit_depth, 1/*do tRNS*/);

#     ifdef PNG_WRITE_tRNS_SUPPORTED
         else if (palette_number)
            set_random_tRNS(pp, pi, colour_type, bit_depth);
#     endif

      png_write_info(pp, pi);

      if (png_get_rowbytes(pp, pi) !=
          transform_rowsize(pp, colour_type, bit_depth))
         png_error(pp, "transform row size incorrect");

      else
      {
         /* Somewhat confusingly this must be called *after* png_write_info
          * because if it is called before, the information in *pp has not been
          * updated to reflect the interlaced image.
          */
         int npasses = set_write_interlace_handling(pp, interlace_type);
         int pass;

         if (npasses != npasses_from_interlace_type(pp, interlace_type))
            png_error(pp, "write: png_set_interlace_handling failed");

         for (pass=0; pass<npasses; ++pass)
         {
            png_uint_32 y;

            /* do_own_interlace is a pre-defined boolean (a #define) which is
             * set if we have to work out the interlaced rows here.
             */
            for (y=0; y<h; ++y)
            {
               png_byte buffer[TRANSFORM_ROWMAX];

               transform_row(pp, buffer, colour_type, bit_depth, y);

#              if do_own_interlace
                  /* If do_own_interlace *and* the image is interlaced we need a
                   * reduced interlace row; this may be reduced to empty.
                   */
                  if (interlace_type == PNG_INTERLACE_ADAM7)
                  {
                     /* The row must not be written if it doesn't exist, notice
                      * that there are two conditions here, either the row isn't
                      * ever in the pass or the row would be but isn't wide
                      * enough to contribute any pixels.  In fact the wPass test
                      * can be used to skip the whole y loop in this case.
                      */
                     if (PNG_ROW_IN_INTERLACE_PASS(y, pass) &&
                         PNG_PASS_COLS(w, pass) > 0)
                        interlace_row(buffer, buffer,
                              bit_size(pp, colour_type, bit_depth), w, pass,
                              0/*data always bigendian*/);
                     else
                        continue;
                  }
#              endif /* do_own_interlace */

               choose_random_filter(pp, pass == 0 && y == 0);
               png_write_row(pp, buffer);
            }
         }
      }

#ifdef PNG_TEXT_SUPPORTED
      {
         static char key[] = "end marker";
         static char comment[] = "end";
         png_text text;

         /* Use a compressed text string to test the correct interaction of text
          * compression and IDAT compression.
          */
         text.compression = TEXT_COMPRESSION;
         text.key = key;
         text.text = comment;
         text.text_length = (sizeof comment)-1;
         text.itxt_length = 0;
         text.lang = 0;
         text.lang_key = 0;

         png_set_text(pp, pi, &text, 1);
      }
#endif

      png_write_end(pp, pi);

      /* And store this under the appropriate id, then clean up. */
      store_storefile(ps, FILEID(colour_type, bit_depth, palette_number,
         interlace_type, 0, 0, 0));

      store_write_reset(ps);
   }

   Catch(fault)
   {
      /* Use the png_store returned by the exception. This may help the compiler
       * because 'ps' is not used in this branch of the setjmp.  Note that fault
       * and ps will always be the same value.
       */
      store_write_reset(fault);
   }
}

static void
make_transform_images(png_modifier *pm)
{
   png_byte colour_type = 0;
   png_byte bit_depth = 0;
   unsigned int palette_number = 0;

   /* This is in case of errors. */
   safecat(pm->this.test, sizeof pm->this.test, 0, "make standard images");

   /* Use next_format to enumerate all the combinations we test, including
    * generating multiple low bit depth palette images. Non-A images (palette
    * and direct) are created with and without tRNS chunks.
    */
   while (next_format(&colour_type, &bit_depth, &palette_number, 1, 1))
   {
      int interlace_type;

      for (interlace_type = PNG_INTERLACE_NONE;
           interlace_type < INTERLACE_LAST; ++interlace_type)
      {
         char name[FILE_NAME_SIZE];

         standard_name(name, sizeof name, 0, colour_type, bit_depth,
            palette_number, interlace_type, 0, 0, do_own_interlace);
         make_transform_image(&pm->this, colour_type, bit_depth, palette_number,
            interlace_type, name);
      }
   }
}

/* Build a single row for the 'size' test images; this fills in only the
 * first bit_width bits of the sample row.
 */
static void
size_row(png_byte buffer[SIZE_ROWMAX], png_uint_32 bit_width, png_uint_32 y)
{
   /* height is in the range 1 to 16, so: */
   y = ((y & 1) << 7) + ((y & 2) << 6) + ((y & 4) << 5) + ((y & 8) << 4);
   /* the following ensures bits are set in small images: */
   y ^= 0xA5;

   while (bit_width >= 8)
      *buffer++ = (png_byte)y++, bit_width -= 8;

   /* There may be up to 7 remaining bits, these go in the most significant
    * bits of the byte.
    */
   if (bit_width > 0)
   {
      png_uint_32 mask = (1U<<(8-bit_width))-1;
      *buffer = (png_byte)((*buffer & mask) | (y & ~mask));
   }
}

static void
make_size_image(png_store* const ps, png_byte const colour_type,
    png_byte const bit_depth, int const interlace_type,
    png_uint_32 const w, png_uint_32 const h,
    int const do_interlace)
{
   context(ps, fault);

   check_interlace_type(interlace_type);

   Try
   {
      png_infop pi;
      png_structp pp;
      unsigned int pixel_size;

      /* Make a name and get an appropriate id for the store: */
      char name[FILE_NAME_SIZE];
      png_uint_32 id = FILEID(colour_type, bit_depth, 0/*palette*/,
         interlace_type, w, h, do_interlace);

      standard_name_from_id(name, sizeof name, 0, id);
      pp = set_store_for_write(ps, &pi, name);

      /* In the event of a problem return control to the Catch statement below
       * to do the clean up - it is not possible to 'return' directly from a Try
       * block.
       */
      if (pp == NULL)
         Throw ps;

      png_set_IHDR(pp, pi, w, h, bit_depth, colour_type, interlace_type,
         PNG_COMPRESSION_TYPE_BASE, PNG_FILTER_TYPE_BASE);

#ifdef PNG_TEXT_SUPPORTED
      {
         static char key[] = "image name"; /* must be writeable */
         size_t pos;
         png_text text;
         char copy[FILE_NAME_SIZE];

         /* Use a compressed text string to test the correct interaction of text
          * compression and IDAT compression.
          */
         text.compression = TEXT_COMPRESSION;
         text.key = key;
         /* Yuck: the text must be writable! */
         pos = safecat(copy, sizeof copy, 0, ps->wname);
         text.text = copy;
         text.text_length = pos;
         text.itxt_length = 0;
         text.lang = 0;
         text.lang_key = 0;

         png_set_text(pp, pi, &text, 1);
      }
#endif

      if (colour_type == 3) /* palette */
         init_standard_palette(ps, pp, pi, 1U << bit_depth, 0/*do tRNS*/);

      png_write_info(pp, pi);

      /* Calculate the bit size, divide by 8 to get the byte size - this won't
       * overflow because we know the w values are all small enough even for
       * a system where 'unsigned int' is only 16 bits.
       */
      pixel_size = bit_size(pp, colour_type, bit_depth);
      if (png_get_rowbytes(pp, pi) != ((w * pixel_size) + 7) / 8)
         png_error(pp, "size row size incorrect");

      else
      {
         int npasses = npasses_from_interlace_type(pp, interlace_type);
         png_uint_32 y;
         int pass;
         png_byte image[16][SIZE_ROWMAX];

         /* To help consistent error detection make the parts of this buffer
          * that aren't set below all '1':
          */
         memset(image, 0xff, sizeof image);

         if (!do_interlace &&
             npasses != set_write_interlace_handling(pp, interlace_type))
            png_error(pp, "write: png_set_interlace_handling failed");

         /* Prepare the whole image first to avoid making it 7 times: */
         for (y=0; y<h; ++y)
            size_row(image[y], w * pixel_size, y);

         for (pass=0; pass<npasses; ++pass)
         {
            /* The following two are for checking the macros: */
            png_uint_32 wPass = PNG_PASS_COLS(w, pass);

            /* If do_interlace is set we don't call png_write_row for every
             * row because some of them are empty.  In fact, for a 1x1 image,
             * most of them are empty!
             */
            for (y=0; y<h; ++y)
            {
               png_const_bytep row = image[y];
               png_byte tempRow[SIZE_ROWMAX];

               /* If do_interlace *and* the image is interlaced we
                * need a reduced interlace row; this may be reduced
                * to empty.
                */
               if (do_interlace && interlace_type == PNG_INTERLACE_ADAM7)
               {
                  /* The row must not be written if it doesn't exist, notice
                   * that there are two conditions here, either the row isn't
                   * ever in the pass or the row would be but isn't wide
                   * enough to contribute any pixels.  In fact the wPass test
                   * can be used to skip the whole y loop in this case.
                   */
                  if (PNG_ROW_IN_INTERLACE_PASS(y, pass) && wPass > 0)
                  {
                     /* Set to all 1's for error detection (libpng tends to
                      * set unset things to 0).
                      */
                     memset(tempRow, 0xff, sizeof tempRow);
                     interlace_row(tempRow, row, pixel_size, w, pass,
                           0/*data always bigendian*/);
                     row = tempRow;
                  }
                  else
                     continue;
               }

#           ifdef PNG_WRITE_FILTER_SUPPORTED
               /* Only get to here if the row has some pixels in it, set the
                * filters to 'all' for the very first row and thereafter to a
                * single filter.  It isn't well documented, but png_set_filter
                * does accept a filter number (per the spec) as well as a bit
                * mask.
                *
                * The code now uses filters at random, except that on the first
                * row of an image it ensures that a previous row filter is in
                * the set so that libpng allocates the row buffer.
                */
               {
                  int filters = 8 << random_mod(PNG_FILTER_VALUE_LAST);

                  if (pass == 0 && y == 0 &&
                      (filters < PNG_FILTER_UP || w == 1U))
                     filters |= PNG_FILTER_UP;

                  png_set_filter(pp, 0/*method*/, filters);
               }
#           endif

               png_write_row(pp, row);
            }
         }
      }

#ifdef PNG_TEXT_SUPPORTED
      {
         static char key[] = "end marker";
         static char comment[] = "end";
         png_text text;

         /* Use a compressed text string to test the correct interaction of text
          * compression and IDAT compression.
          */
         text.compression = TEXT_COMPRESSION;
         text.key = key;
         text.text = comment;
         text.text_length = (sizeof comment)-1;
         text.itxt_length = 0;
         text.lang = 0;
         text.lang_key = 0;

         png_set_text(pp, pi, &text, 1);
      }
#endif

      png_write_end(pp, pi);

      /* And store this under the appropriate id, then clean up. */
      store_storefile(ps, id);

      store_write_reset(ps);
   }

   Catch(fault)
   {
      /* Use the png_store returned by the exception. This may help the compiler
       * because 'ps' is not used in this branch of the setjmp.  Note that fault
       * and ps will always be the same value.
       */
      store_write_reset(fault);
   }
}

static void
make_size(png_store* const ps, png_byte const colour_type, int bdlo,
    int const bdhi)
{
   for (; bdlo <= bdhi; ++bdlo)
   {
      png_uint_32 width;

      for (width = 1; width <= 16; ++width)
      {
         png_uint_32 height;

         for (height = 1; height <= 16; ++height)
         {
            /* The four combinations of DIY interlace and interlace or not -
             * no interlace + DIY should be identical to no interlace with
             * libpng doing it.
             */
            make_size_image(ps, colour_type, DEPTH(bdlo), PNG_INTERLACE_NONE,
               width, height, 0);
            make_size_image(ps, colour_type, DEPTH(bdlo), PNG_INTERLACE_NONE,
               width, height, 1);
#        ifdef PNG_WRITE_INTERLACING_SUPPORTED
            make_size_image(ps, colour_type, DEPTH(bdlo), PNG_INTERLACE_ADAM7,
               width, height, 0);
#        endif
#        if CAN_WRITE_INTERLACE
            /* 1.7.0 removes the hack that prevented app write of an interlaced
             * image if WRITE_INTERLACE was not supported
             */
            make_size_image(ps, colour_type, DEPTH(bdlo), PNG_INTERLACE_ADAM7,
               width, height, 1);
#        endif
         }
      }
   }
}

static void
make_size_images(png_store *ps)
{
   /* This is in case of errors. */
   safecat(ps->test, sizeof ps->test, 0, "make size images");

   /* Arguments are colour_type, low bit depth, high bit depth
    */
   make_size(ps, 0, 0, WRITE_BDHI);
   make_size(ps, 2, 3, WRITE_BDHI);
   make_size(ps, 3, 0, 3 /*palette: max 8 bits*/);
   make_size(ps, 4, 3, WRITE_BDHI);
   make_size(ps, 6, 3, WRITE_BDHI);
}

#ifdef PNG_READ_SUPPORTED
/* Return a row based on image id and 'y' for checking: */
static void
standard_row(png_const_structp pp, png_byte std[STANDARD_ROWMAX],
   png_uint_32 id, png_uint_32 y)
{
   if (WIDTH_FROM_ID(id) == 0)
      transform_row(pp, std, COL_FROM_ID(id), DEPTH_FROM_ID(id), y);
   else
      size_row(std, WIDTH_FROM_ID(id) * bit_size(pp, COL_FROM_ID(id),
         DEPTH_FROM_ID(id)), y);
}
#endif /* PNG_READ_SUPPORTED */

/* Tests - individual test cases */
/* Like 'make_standard' but errors are deliberately introduced into the calls
 * to ensure that they get detected - it should not be possible to write an
 * invalid image with libpng!
 */
/* TODO: the 'set' functions can probably all be made to take a
 * png_const_structp rather than a modifiable one.
 */
#ifdef PNG_WARNINGS_SUPPORTED
static void
sBIT0_error_fn(png_structp pp, png_infop pi)
{
   /* 0 is invalid... */
   png_color_8 bad;
   bad.red = bad.green = bad.blue = bad.gray = bad.alpha = 0;
   png_set_sBIT(pp, pi, &bad);
}

static void
sBIT_error_fn(png_structp pp, png_infop pi)
{
   png_byte bit_depth;
   png_color_8 bad;

   if (png_get_color_type(pp, pi) == PNG_COLOR_TYPE_PALETTE)
      bit_depth = 8;

   else
      bit_depth = png_get_bit_depth(pp, pi);

   /* Now we know the bit depth we can easily generate an invalid sBIT entry */
   bad.red = bad.green = bad.blue = bad.gray = bad.alpha =
      (png_byte)(bit_depth+1);
   png_set_sBIT(pp, pi, &bad);
}

static const struct
{
   void          (*fn)(png_structp, png_infop);
   const char *msg;
   unsigned int    warning :1; /* the error is a warning... */
} error_test[] =
    {
       /* no warnings makes these errors undetectable prior to 1.7.0 */
       { sBIT0_error_fn, "sBIT(0): failed to detect error",
         PNG_LIBPNG_VER < 10700 },

       { sBIT_error_fn, "sBIT(too big): failed to detect error",
         PNG_LIBPNG_VER < 10700 },
    };

static void
make_error(png_store* const ps, png_byte const colour_type,
    png_byte bit_depth, int interlace_type, int test, png_const_charp name)
{
   context(ps, fault);

   check_interlace_type(interlace_type);

   Try
   {
      png_infop pi;
      png_structp pp = set_store_for_write(ps, &pi, name);
      png_uint_32 w, h;
      gnu_volatile(pp)

      if (pp == NULL)
         Throw ps;

      w = transform_width(pp, colour_type, bit_depth);
      gnu_volatile(w)
      h = transform_height(pp, colour_type, bit_depth);
      gnu_volatile(h)
      png_set_IHDR(pp, pi, w, h, bit_depth, colour_type, interlace_type,
            PNG_COMPRESSION_TYPE_BASE, PNG_FILTER_TYPE_BASE);

      if (colour_type == 3) /* palette */
         init_standard_palette(ps, pp, pi, 1U << bit_depth, 0/*do tRNS*/);

      /* Time for a few errors; these are in various optional chunks, the
       * standard tests test the standard chunks pretty well.
       */
#     define exception__prev exception_prev_1
#     define exception__env exception_env_1
      Try
      {
         gnu_volatile(exception__prev)

         /* Expect this to throw: */
         ps->expect_error = !error_test[test].warning;
         ps->expect_warning = error_test[test].warning;
         ps->saw_warning = 0;
         error_test[test].fn(pp, pi);

         /* Normally the error is only detected here: */
         png_write_info(pp, pi);

         /* And handle the case where it was only a warning: */
         if (ps->expect_warning && ps->saw_warning)
            Throw ps;

         /* If we get here there is a problem, we have success - no error or
          * no warning - when we shouldn't have success.  Log an error.
          */
         store_log(ps, pp, error_test[test].msg, 1 /*error*/);
      }

      Catch (fault)
      { /* expected exit */
      }
#undef exception__prev
#undef exception__env

      /* And clear these flags */
      ps->expect_warning = 0;

      if (ps->expect_error)
         ps->expect_error = 0;

      else
      {
         /* Now write the whole image, just to make sure that the detected, or
          * undetected, error has not created problems inside libpng.  This
          * doesn't work if there was a png_error in png_write_info because that
          * can abort before PLTE was written.
          */
         if (png_get_rowbytes(pp, pi) !=
             transform_rowsize(pp, colour_type, bit_depth))
            png_error(pp, "row size incorrect");

         else
         {
            int npasses = set_write_interlace_handling(pp, interlace_type);
            int pass;

            if (npasses != npasses_from_interlace_type(pp, interlace_type))
               png_error(pp, "write: png_set_interlace_handling failed");

            for (pass=0; pass<npasses; ++pass)
            {
               png_uint_32 y;

               for (y=0; y<h; ++y)
               {
                  png_byte buffer[TRANSFORM_ROWMAX];

                  transform_row(pp, buffer, colour_type, bit_depth, y);

#                 if do_own_interlace
                     /* If do_own_interlace *and* the image is interlaced we
                      * need a reduced interlace row; this may be reduced to
                      * empty.
                      */
                     if (interlace_type == PNG_INTERLACE_ADAM7)
                     {
                        /* The row must not be written if it doesn't exist,
                         * notice that there are two conditions here, either the
                         * row isn't ever in the pass or the row would be but
                         * isn't wide enough to contribute any pixels.  In fact
                         * the wPass test can be used to skip the whole y loop
                         * in this case.
                         */
                        if (PNG_ROW_IN_INTERLACE_PASS(y, pass) &&
                            PNG_PASS_COLS(w, pass) > 0)
                           interlace_row(buffer, buffer,
                                 bit_size(pp, colour_type, bit_depth), w, pass,
                                 0/*data always bigendian*/);
                        else
                           continue;
                     }
#                 endif /* do_own_interlace */

                  png_write_row(pp, buffer);
               }
            }
         } /* image writing */

         png_write_end(pp, pi);
      }

      /* The following deletes the file that was just written. */
      store_write_reset(ps);
   }

   Catch(fault)
   {
      store_write_reset(fault);
   }
}

static int
make_errors(png_modifier* const pm, png_byte const colour_type,
    int bdlo, int const bdhi)
{
   for (; bdlo <= bdhi; ++bdlo)
   {
      int interlace_type;

      for (interlace_type = PNG_INTERLACE_NONE;
           interlace_type < INTERLACE_LAST; ++interlace_type)
      {
         unsigned int test;
         char name[FILE_NAME_SIZE];

         standard_name(name, sizeof name, 0, colour_type, 1<<bdlo, 0,
            interlace_type, 0, 0, do_own_interlace);

         for (test=0; test<ARRAY_SIZE(error_test); ++test)
         {
            make_error(&pm->this, colour_type, DEPTH(bdlo), interlace_type,
               test, name);

            if (fail(pm))
               return 0;
         }
      }
   }

   return 1; /* keep going */
}
#endif /* PNG_WARNINGS_SUPPORTED */

static void
perform_error_test(png_modifier *pm)
{
#ifdef PNG_WARNINGS_SUPPORTED /* else there are no cases that work! */
   /* Need to do this here because we just write in this test. */
   safecat(pm->this.test, sizeof pm->this.test, 0, "error test");

   if (!make_errors(pm, 0, 0, WRITE_BDHI))
      return;

   if (!make_errors(pm, 2, 3, WRITE_BDHI))
      return;

   if (!make_errors(pm, 3, 0, 3))
      return;

   if (!make_errors(pm, 4, 3, WRITE_BDHI))
      return;

   if (!make_errors(pm, 6, 3, WRITE_BDHI))
      return;
#else
   UNUSED(pm)
#endif
}

/* This is just to validate the internal PNG formatting code - if this fails
 * then the warning messages the library outputs will probably be garbage.
 */
static void
perform_formatting_test(png_store *ps)
{
#ifdef PNG_TIME_RFC1123_SUPPORTED
   /* The handle into the formatting code is the RFC1123 support; this test does
    * nothing if that is compiled out.
    */
   context(ps, fault);

   Try
   {
      png_const_charp correct = "29 Aug 2079 13:53:60 +0000";
      png_const_charp result;
#     if PNG_LIBPNG_VER >= 10600
         char timestring[29];
#     endif
      png_structp pp;
      png_time pt;

      pp = set_store_for_write(ps, NULL, "libpng formatting test");

      if (pp == NULL)
         Throw ps;


      /* Arbitrary settings: */
      pt.year = 2079;
      pt.month = 8;
      pt.day = 29;
      pt.hour = 13;
      pt.minute = 53;
      pt.second = 60; /* a leap second */

#     if PNG_LIBPNG_VER < 10600
         result = png_convert_to_rfc1123(pp, &pt);
#     else
         if (png_convert_to_rfc1123_buffer(timestring, &pt))
            result = timestring;

         else
            result = NULL;
#     endif

      if (result == NULL)
         png_error(pp, "png_convert_to_rfc1123 failed");

      if (strcmp(result, correct) != 0)
      {
         size_t pos = 0;
         char msg[128];

         pos = safecat(msg, sizeof msg, pos, "png_convert_to_rfc1123(");
         pos = safecat(msg, sizeof msg, pos, correct);
         pos = safecat(msg, sizeof msg, pos, ") returned: '");
         pos = safecat(msg, sizeof msg, pos, result);
         pos = safecat(msg, sizeof msg, pos, "'");

         png_error(pp, msg);
      }

      store_write_reset(ps);
   }

   Catch(fault)
   {
      store_write_reset(fault);
   }
#else
   UNUSED(ps)
#endif
}

#ifdef PNG_READ_SUPPORTED
/* Because we want to use the same code in both the progressive reader and the
 * sequential reader it is necessary to deal with the fact that the progressive
 * reader callbacks only have one parameter (png_get_progressive_ptr()), so this
 * must contain all the test parameters and all the local variables directly
 * accessible to the sequential reader implementation.
 *
 * The technique adopted is to reinvent part of what Dijkstra termed a
 * 'display'; an array of pointers to the stack frames of enclosing functions so
 * that a nested function definition can access the local (C auto) variables of
 * the functions that contain its definition.  In fact C provides the first
 * pointer (the local variables - the stack frame pointer) and the last (the
 * global variables - the BCPL global vector typically implemented as global
 * addresses), this code requires one more pointer to make the display - the
 * local variables (and function call parameters) of the function that actually
 * invokes either the progressive or sequential reader.
 *
 * Perhaps confusingly this technique is confounded with classes - the
 * 'standard_display' defined here is sub-classed as the 'gamma_display' below.
 * A gamma_display is a standard_display, taking advantage of the ANSI-C
 * requirement that the pointer to the first member of a structure must be the
 * same as the pointer to the structure.  This allows us to reuse standard_
 * functions in the gamma test code; something that could not be done with
 * nested functions!
 */
typedef struct standard_display
{
   png_store*  ps;             /* Test parameters (passed to the function) */
   png_byte    colour_type;
   png_byte    bit_depth;
   png_byte    red_sBIT;       /* Input data sBIT values. */
   png_byte    green_sBIT;
   png_byte    blue_sBIT;
   png_byte    alpha_sBIT;
   png_byte    interlace_type;
   png_byte    filler;         /* Output has a filler */
   png_uint_32 id;             /* Calculated file ID */
   png_uint_32 w;              /* Width of image */
   png_uint_32 h;              /* Height of image */
   int         npasses;        /* Number of interlaced passes */
   png_uint_32 pixel_size;     /* Width of one pixel in bits */
   png_uint_32 bit_width;      /* Width of output row in bits */
   size_t      cbRow;          /* Bytes in a row of the output image */
   int         do_interlace;   /* Do interlacing internally */
   int         littleendian;   /* App (row) data is little endian */
   int         is_transparent; /* Transparency information was present. */
   int         has_tRNS;       /* color type GRAY or RGB with a tRNS chunk. */
   int         speed;          /* Doing a speed test */
   int         use_update_info;/* Call update_info, not start_image */
   struct
   {
      png_uint_16 red;
      png_uint_16 green;
      png_uint_16 blue;
   }           transparent;    /* The transparent color, if set. */
   int         npalette;       /* Number of entries in the palette. */
   store_palette
               palette;
} standard_display;

static void
standard_display_init(standard_display *dp, png_store* ps, png_uint_32 id,
   int do_interlace, int use_update_info)
{
   memset(dp, 0, sizeof *dp);

   dp->ps = ps;
   dp->colour_type = COL_FROM_ID(id);
   dp->bit_depth = DEPTH_FROM_ID(id);
   if (dp->bit_depth < 1 || dp->bit_depth > 16)
      internal_error(ps, "internal: bad bit depth");
   if (dp->colour_type == 3)
      dp->red_sBIT = dp->blue_sBIT = dp->green_sBIT = dp->alpha_sBIT = 8;
   else
      dp->red_sBIT = dp->blue_sBIT = dp->green_sBIT = dp->alpha_sBIT =
         dp->bit_depth;
   dp->interlace_type = INTERLACE_FROM_ID(id);
   check_interlace_type(dp->interlace_type);
   dp->id = id;
   /* All the rest are filled in after the read_info: */
   dp->w = 0;
   dp->h = 0;
   dp->npasses = 0;
   dp->pixel_size = 0;
   dp->bit_width = 0;
   dp->cbRow = 0;
   dp->do_interlace = do_interlace;
   dp->littleendian = 0;
   dp->is_transparent = 0;
   dp->speed = ps->speed;
   dp->use_update_info = use_update_info;
   dp->npalette = 0;
   /* Preset the transparent color to black: */
   memset(&dp->transparent, 0, sizeof dp->transparent);
   /* Preset the palette to full intensity/opaque throughout: */
   memset(dp->palette, 0xff, sizeof dp->palette);
}

/* Initialize the palette fields - this must be done later because the palette
 * comes from the particular png_store_file that is selected.
 */
static void
standard_palette_init(standard_display *dp)
{
   store_palette_entry *palette = store_current_palette(dp->ps, &dp->npalette);

   /* The remaining entries remain white/opaque. */
   if (dp->npalette > 0)
   {
      int i = dp->npalette;
      memcpy(dp->palette, palette, i * sizeof *palette);

      /* Check for a non-opaque palette entry: */
      while (--i >= 0)
         if (palette[i].alpha < 255)
            break;

#     ifdef __GNUC__
         /* GCC can't handle the more obviously optimizable version. */
         if (i >= 0)
            dp->is_transparent = 1;
         else
            dp->is_transparent = 0;
#     else
         dp->is_transparent = (i >= 0);
#     endif
   }
}

/* Utility to read the palette from the PNG file and convert it into
 * store_palette format.  This returns 1 if there is any transparency in the
 * palette (it does not check for a transparent colour in the non-palette case.)
 */
static int
read_palette(store_palette palette, int *npalette, png_const_structp pp,
   png_infop pi)
{
   png_colorp pal;
   png_bytep trans_alpha;
   int num;

   pal = 0;
   *npalette = -1;

   if (png_get_PLTE(pp, pi, &pal, npalette) & PNG_INFO_PLTE)
   {
      int i = *npalette;

      if (i <= 0 || i > 256)
         png_error(pp, "validate: invalid PLTE count");

      while (--i >= 0)
      {
         palette[i].red = pal[i].red;
         palette[i].green = pal[i].green;
         palette[i].blue = pal[i].blue;
      }

      /* Mark the remainder of the entries with a flag value (other than
       * white/opaque which is the flag value stored above.)
       */
      memset(palette + *npalette, 126, (256-*npalette) * sizeof *palette);
   }

   else /* !png_get_PLTE */
   {
      if (*npalette != (-1))
         png_error(pp, "validate: invalid PLTE result");
      /* But there is no palette, so record this: */
      *npalette = 0;
      memset(palette, 113, sizeof (store_palette));
   }

   trans_alpha = 0;
   num = 2; /* force error below */
   if ((png_get_tRNS(pp, pi, &trans_alpha, &num, 0) & PNG_INFO_tRNS) != 0 &&
      (trans_alpha != NULL || num != 1/*returns 1 for a transparent color*/) &&
      /* Oops, if a palette tRNS gets expanded png_read_update_info (at least so
       * far as 1.5.4) does not remove the trans_alpha pointer, only num_trans,
       * so in the above call we get a success, we get a pointer (who knows what
       * to) and we get num_trans == 0:
       */
      !(trans_alpha != NULL && num == 0)) /* TODO: fix this in libpng. */
   {
      int i;

      /* Any of these are crash-worthy - given the implementation of
       * png_get_tRNS up to 1.5 an app won't crash if it just checks the
       * result above and fails to check that the variables it passed have
       * actually been filled in!  Note that if the app were to pass the
       * last, png_color_16p, variable too it couldn't rely on this.
       */
      if (trans_alpha == NULL || num <= 0 || num > 256 || num > *npalette)
         png_error(pp, "validate: unexpected png_get_tRNS (palette) result");

      for (i=0; i<num; ++i)
         palette[i].alpha = trans_alpha[i];

      for (num=*npalette; i<num; ++i)
         palette[i].alpha = 255;

      for (; i<256; ++i)
         palette[i].alpha = 33; /* flag value */

      return 1; /* transparency */
   }

   else
   {
      /* No palette transparency - just set the alpha channel to opaque. */
      int i;

      for (i=0, num=*npalette; i<num; ++i)
         palette[i].alpha = 255;

      for (; i<256; ++i)
         palette[i].alpha = 55; /* flag value */

      return 0; /* no transparency */
   }
}

/* Utility to validate the palette if it should not have changed (the
 * non-transform case).
 */
static void
standard_palette_validate(standard_display *dp, png_const_structp pp,
   png_infop pi)
{
   int npalette;
   store_palette palette;

   if (read_palette(palette, &npalette, pp, pi) != dp->is_transparent)
      png_error(pp, "validate: palette transparency changed");

   if (npalette != dp->npalette)
   {
      size_t pos = 0;
      char msg[64];

      pos = safecat(msg, sizeof msg, pos, "validate: palette size changed: ");
      pos = safecatn(msg, sizeof msg, pos, dp->npalette);
      pos = safecat(msg, sizeof msg, pos, " -> ");
      pos = safecatn(msg, sizeof msg, pos, npalette);
      png_error(pp, msg);
   }

   {
      int i = npalette; /* npalette is aliased */

      while (--i >= 0)
         if (palette[i].red != dp->palette[i].red ||
            palette[i].green != dp->palette[i].green ||
            palette[i].blue != dp->palette[i].blue ||
            palette[i].alpha != dp->palette[i].alpha)
            png_error(pp, "validate: PLTE or tRNS chunk changed");
   }
}

/* By passing a 'standard_display' the progressive callbacks can be used
 * directly by the sequential code, the functions suffixed "_imp" are the
 * implementations, the functions without the suffix are the callbacks.
 *
 * The code for the info callback is split into two because this callback calls
 * png_read_update_info or png_start_read_image and what gets called depends on
 * whether the info needs updating (we want to test both calls in pngvalid.)
 */
static void
standard_info_part1(standard_display *dp, png_structp pp, png_infop pi)
{
   if (png_get_bit_depth(pp, pi) != dp->bit_depth)
      png_error(pp, "validate: bit depth changed");

   if (png_get_color_type(pp, pi) != dp->colour_type)
      png_error(pp, "validate: color type changed");

   if (png_get_filter_type(pp, pi) != PNG_FILTER_TYPE_BASE)
      png_error(pp, "validate: filter type changed");

   if (png_get_interlace_type(pp, pi) != dp->interlace_type)
      png_error(pp, "validate: interlacing changed");

   if (png_get_compression_type(pp, pi) != PNG_COMPRESSION_TYPE_BASE)
      png_error(pp, "validate: compression type changed");

   dp->w = png_get_image_width(pp, pi);

   if (dp->w != standard_width(pp, dp->id))
      png_error(pp, "validate: image width changed");

   dp->h = png_get_image_height(pp, pi);

   if (dp->h != standard_height(pp, dp->id))
      png_error(pp, "validate: image height changed");

   /* Record (but don't check at present) the input sBIT according to the colour
    * type information.
    */
   {
      png_color_8p sBIT = 0;

      if (png_get_sBIT(pp, pi, &sBIT) & PNG_INFO_sBIT)
      {
         int sBIT_invalid = 0;

         if (sBIT == 0)
            png_error(pp, "validate: unexpected png_get_sBIT result");

         if (dp->colour_type & PNG_COLOR_MASK_COLOR)
         {
            if (sBIT->red == 0 || sBIT->red > dp->bit_depth)
               sBIT_invalid = 1;
            else
               dp->red_sBIT = sBIT->red;

            if (sBIT->green == 0 || sBIT->green > dp->bit_depth)
               sBIT_invalid = 1;
            else
               dp->green_sBIT = sBIT->green;

            if (sBIT->blue == 0 || sBIT->blue > dp->bit_depth)
               sBIT_invalid = 1;
            else
               dp->blue_sBIT = sBIT->blue;
         }

         else /* !COLOR */
         {
            if (sBIT->gray == 0 || sBIT->gray > dp->bit_depth)
               sBIT_invalid = 1;
            else
               dp->blue_sBIT = dp->green_sBIT = dp->red_sBIT = sBIT->gray;
         }

         /* All 8 bits in tRNS for a palette image are significant - see the
          * spec.
          */
         if (dp->colour_type & PNG_COLOR_MASK_ALPHA)
         {
            if (sBIT->alpha == 0 || sBIT->alpha > dp->bit_depth)
               sBIT_invalid = 1;
            else
               dp->alpha_sBIT = sBIT->alpha;
         }

         if (sBIT_invalid)
            png_error(pp, "validate: sBIT value out of range");
      }
   }

   /* Important: this is validating the value *before* any transforms have been
    * put in place.  It doesn't matter for the standard tests, where there are
    * no transforms, but it does for other tests where rowbytes may change after
    * png_read_update_info.
    */
   if (png_get_rowbytes(pp, pi) != standard_rowsize(pp, dp->id))
      png_error(pp, "validate: row size changed");

   /* Validate the colour type 3 palette (this can be present on other color
    * types.)
    */
   standard_palette_validate(dp, pp, pi);

   /* In any case always check for a transparent color (notice that the
    * colour type 3 case must not give a successful return on the get_tRNS call
    * with these arguments!)
    */
   {
      png_color_16p trans_color = 0;

      if (png_get_tRNS(pp, pi, 0, 0, &trans_color) & PNG_INFO_tRNS)
      {
         if (trans_color == 0)
            png_error(pp, "validate: unexpected png_get_tRNS (color) result");

         switch (dp->colour_type)
         {
         case 0:
            dp->transparent.red = dp->transparent.green = dp->transparent.blue =
               trans_color->gray;
            dp->has_tRNS = 1;
            break;

         case 2:
            dp->transparent.red = trans_color->red;
            dp->transparent.green = trans_color->green;
            dp->transparent.blue = trans_color->blue;
            dp->has_tRNS = 1;
            break;

         case 3:
            /* Not expected because it should result in the array case
             * above.
             */
            png_error(pp, "validate: unexpected png_get_tRNS result");
            break;

         default:
            png_error(pp, "validate: invalid tRNS chunk with alpha image");
         }
      }
   }

   /* Read the number of passes - expected to match the value used when
    * creating the image (interlaced or not).  This has the side effect of
    * turning on interlace handling (if do_interlace is not set.)
    */
   dp->npasses = npasses_from_interlace_type(pp, dp->interlace_type);
   if (!dp->do_interlace)
   {
#     ifdef PNG_READ_INTERLACING_SUPPORTED
         if (dp->npasses != png_set_interlace_handling(pp))
            png_error(pp, "validate: file changed interlace type");
#     else /* !READ_INTERLACING */
         /* This should never happen: the relevant tests (!do_interlace) should
          * not be run.
          */
         if (dp->npasses > 1)
            png_error(pp, "validate: no libpng interlace support");
#     endif /* !READ_INTERLACING */
   }

   /* Caller calls png_read_update_info or png_start_read_image now, then calls
    * part2.
    */
}

/* This must be called *after* the png_read_update_info call to get the correct
 * 'rowbytes' value, otherwise png_get_rowbytes will refer to the untransformed
 * image.
 */
static void
standard_info_part2(standard_display *dp, png_const_structp pp,
    png_const_infop pi, int nImages)
{
   /* Record cbRow now that it can be found. */
   {
      png_byte ct = png_get_color_type(pp, pi);
      png_byte bd = png_get_bit_depth(pp, pi);

      if (bd >= 8 && (ct == PNG_COLOR_TYPE_RGB || ct == PNG_COLOR_TYPE_GRAY) &&
          dp->filler)
          ct |= 4; /* handle filler as faked alpha channel */

      dp->pixel_size = bit_size(pp, ct, bd);
   }
   dp->bit_width = png_get_image_width(pp, pi) * dp->pixel_size;
   dp->cbRow = png_get_rowbytes(pp, pi);

   /* Validate the rowbytes here again. */
   if (dp->cbRow != (dp->bit_width+7)/8)
      png_error(pp, "bad png_get_rowbytes calculation");

   /* Then ensure there is enough space for the output image(s). */
   store_ensure_image(dp->ps, pp, nImages, dp->cbRow, dp->h);
}

static void
standard_info_imp(standard_display *dp, png_structp pp, png_infop pi,
    int nImages)
{
   /* Note that the validation routine has the side effect of turning on
    * interlace handling in the subsequent code.
    */
   standard_info_part1(dp, pp, pi);

   /* And the info callback has to call this (or png_read_update_info - see
    * below in the png_modifier code for that variant.
    */
   if (dp->use_update_info)
   {
      /* For debugging the effect of multiple calls: */
      int i = dp->use_update_info;
      while (i-- > 0)
         png_read_update_info(pp, pi);
   }

   else
      png_start_read_image(pp);

   /* Validate the height, width and rowbytes plus ensure that sufficient buffer
    * exists for decoding the image.
    */
   standard_info_part2(dp, pp, pi, nImages);
}

static void PNGCBAPI
standard_info(png_structp pp, png_infop pi)
{
   standard_display *dp = voidcast(standard_display*,
      png_get_progressive_ptr(pp));

   /* Call with nImages==1 because the progressive reader can only produce one
    * image.
    */
   standard_info_imp(dp, pp, pi, 1 /*only one image*/);
}

static void PNGCBAPI
progressive_row(png_structp ppIn, png_bytep new_row, png_uint_32 y, int pass)
{
   png_const_structp pp = ppIn;
   const standard_display *dp = voidcast(standard_display*,
      png_get_progressive_ptr(pp));

   /* When handling interlacing some rows will be absent in each pass, the
    * callback still gets called, but with a NULL pointer.  This is checked
    * in the 'else' clause below.  We need our own 'cbRow', but we can't call
    * png_get_rowbytes because we got no info structure.
    */
   if (new_row != NULL)
   {
      png_bytep row;

      /* In the case where the reader doesn't do the interlace it gives
       * us the y in the sub-image:
       */
      if (dp->do_interlace && dp->interlace_type == PNG_INTERLACE_ADAM7)
      {
#ifdef PNG_USER_TRANSFORM_INFO_SUPPORTED
         /* Use this opportunity to validate the png 'current' APIs: */
         if (y != png_get_current_row_number(pp))
            png_error(pp, "png_get_current_row_number is broken");

         if (pass != png_get_current_pass_number(pp))
            png_error(pp, "png_get_current_pass_number is broken");
#endif /* USER_TRANSFORM_INFO */

         y = PNG_ROW_FROM_PASS_ROW(y, pass);
      }

      /* Validate this just in case. */
      if (y >= dp->h)
         png_error(pp, "invalid y to progressive row callback");

      row = store_image_row(dp->ps, pp, 0, y);

      /* Combine the new row into the old: */
#ifdef PNG_READ_INTERLACING_SUPPORTED
      if (dp->do_interlace)
#endif /* READ_INTERLACING */
      {
         if (dp->interlace_type == PNG_INTERLACE_ADAM7)
            deinterlace_row(row, new_row, dp->pixel_size, dp->w, pass,
                  dp->littleendian);
         else
            row_copy(row, new_row, dp->pixel_size * dp->w, dp->littleendian);
      }
#ifdef PNG_READ_INTERLACING_SUPPORTED
      else
         png_progressive_combine_row(pp, row, new_row);
#endif /* PNG_READ_INTERLACING_SUPPORTED */
   }

   else if (dp->interlace_type == PNG_INTERLACE_ADAM7 &&
       PNG_ROW_IN_INTERLACE_PASS(y, pass) &&
       PNG_PASS_COLS(dp->w, pass) > 0)
      png_error(pp, "missing row in progressive de-interlacing");
}

static void
sequential_row(standard_display *dp, png_structp pp, png_infop pi,
    int iImage, int iDisplay)
{
   int npasses = dp->npasses;
   int do_interlace = dp->do_interlace &&
      dp->interlace_type == PNG_INTERLACE_ADAM7;
   png_uint_32 height = standard_height(pp, dp->id);
   png_uint_32 width = standard_width(pp, dp->id);
   const png_store* ps = dp->ps;
   int pass;

   for (pass=0; pass<npasses; ++pass)
   {
      png_uint_32 y;
      png_uint_32 wPass = PNG_PASS_COLS(width, pass);

      for (y=0; y<height; ++y)
      {
         if (do_interlace)
         {
            /* wPass may be zero or this row may not be in this pass.
             * png_read_row must not be called in either case.
             */
            if (wPass > 0 && PNG_ROW_IN_INTERLACE_PASS(y, pass))
            {
               /* Read the row into a pair of temporary buffers, then do the
                * merge here into the output rows.
                */
               png_byte row[STANDARD_ROWMAX], display[STANDARD_ROWMAX];

               /* The following aids (to some extent) error detection - we can
                * see where png_read_row wrote.  Use opposite values in row and
                * display to make this easier.  Don't use 0xff (which is used in
                * the image write code to fill unused bits) or 0 (which is a
                * likely value to overwrite unused bits with).
                */
               memset(row, 0xc5, sizeof row);
               memset(display, 0x5c, sizeof display);

               png_read_row(pp, row, display);

               if (iImage >= 0)
                  deinterlace_row(store_image_row(ps, pp, iImage, y), row,
                     dp->pixel_size, dp->w, pass, dp->littleendian);

               if (iDisplay >= 0)
                  deinterlace_row(store_image_row(ps, pp, iDisplay, y), display,
                     dp->pixel_size, dp->w, pass, dp->littleendian);
            }
         }
         else
            png_read_row(pp,
               iImage >= 0 ? store_image_row(ps, pp, iImage, y) : NULL,
               iDisplay >= 0 ? store_image_row(ps, pp, iDisplay, y) : NULL);
      }
   }

   /* And finish the read operation (only really necessary if the caller wants
    * to find additional data in png_info from chunks after the last IDAT.)
    */
   png_read_end(pp, pi);
}

#ifdef PNG_TEXT_SUPPORTED
static void
standard_check_text(png_const_structp pp, png_const_textp tp,
   png_const_charp keyword, png_const_charp text)
{
   char msg[1024];
   size_t pos = safecat(msg, sizeof msg, 0, "text: ");
   size_t ok;

   pos = safecat(msg, sizeof msg, pos, keyword);
   pos = safecat(msg, sizeof msg, pos, ": ");
   ok = pos;

   if (tp->compression != TEXT_COMPRESSION)
   {
      char buf[64];

      sprintf(buf, "compression [%d->%d], ", TEXT_COMPRESSION,
         tp->compression);
      pos = safecat(msg, sizeof msg, pos, buf);
   }

   if (tp->key == NULL || strcmp(tp->key, keyword) != 0)
   {
      pos = safecat(msg, sizeof msg, pos, "keyword \"");
      if (tp->key != NULL)
      {
         pos = safecat(msg, sizeof msg, pos, tp->key);
         pos = safecat(msg, sizeof msg, pos, "\", ");
      }

      else
         pos = safecat(msg, sizeof msg, pos, "null, ");
   }

   if (tp->text == NULL)
      pos = safecat(msg, sizeof msg, pos, "text lost, ");

   else
   {
      if (tp->text_length != strlen(text))
      {
         char buf[64];
         sprintf(buf, "text length changed[%lu->%lu], ",
            (unsigned long)strlen(text), (unsigned long)tp->text_length);
         pos = safecat(msg, sizeof msg, pos, buf);
      }

      if (strcmp(tp->text, text) != 0)
      {
         pos = safecat(msg, sizeof msg, pos, "text becomes \"");
         pos = safecat(msg, sizeof msg, pos, tp->text);
         pos = safecat(msg, sizeof msg, pos, "\" (was \"");
         pos = safecat(msg, sizeof msg, pos, text);
         pos = safecat(msg, sizeof msg, pos, "\"), ");
      }
   }

   if (tp->itxt_length != 0)
      pos = safecat(msg, sizeof msg, pos, "iTXt length set, ");

   if (tp->lang != NULL)
   {
      pos = safecat(msg, sizeof msg, pos, "iTXt language \"");
      pos = safecat(msg, sizeof msg, pos, tp->lang);
      pos = safecat(msg, sizeof msg, pos, "\", ");
   }

   if (tp->lang_key != NULL)
   {
      pos = safecat(msg, sizeof msg, pos, "iTXt keyword \"");
      pos = safecat(msg, sizeof msg, pos, tp->lang_key);
      pos = safecat(msg, sizeof msg, pos, "\", ");
   }

   if (pos > ok)
   {
      msg[pos-2] = '\0'; /* Remove the ", " at the end */
      png_error(pp, msg);
   }
}

static void
standard_text_validate(standard_display *dp, png_const_structp pp,
   png_infop pi, int check_end)
{
   png_textp tp = NULL;
   png_uint_32 num_text = png_get_text(pp, pi, &tp, NULL);

   if (num_text == 2 && tp != NULL)
   {
      standard_check_text(pp, tp, "image name", dp->ps->current->name);

      /* This exists because prior to 1.5.18 the progressive reader left the
       * png_struct z_stream unreset at the end of the image, so subsequent
       * attempts to use it simply returns Z_STREAM_END.
       */
      if (check_end)
         standard_check_text(pp, tp+1, "end marker", "end");
   }

   else
   {
      char msg[64];

      sprintf(msg, "expected two text items, got %lu",
         (unsigned long)num_text);
      png_error(pp, msg);
   }
}
#else
#  define standard_text_validate(dp,pp,pi,check_end) ((void)0)
#endif

static void
standard_row_validate(standard_display *dp, png_const_structp pp,
   int iImage, int iDisplay, png_uint_32 y)
{
   int where;
   png_byte std[STANDARD_ROWMAX];

   /* The row must be pre-initialized to the magic number here for the size
    * tests to pass:
    */
   memset(std, 178, sizeof std);
   standard_row(pp, std, dp->id, y);

   /* At the end both the 'row' and 'display' arrays should end up identical.
    * In earlier passes 'row' will be partially filled in, with only the pixels
    * that have been read so far, but 'display' will have those pixels
    * replicated to fill the unread pixels while reading an interlaced image.
    */
   if (iImage >= 0 &&
      (where = pixel_cmp(std, store_image_row(dp->ps, pp, iImage, y),
            dp->bit_width)) != 0)
   {
      char msg[64];
      sprintf(msg, "PNG image row[%lu][%d] changed from %.2x to %.2x",
         (unsigned long)y, where-1, std[where-1],
         store_image_row(dp->ps, pp, iImage, y)[where-1]);
      png_error(pp, msg);
   }

   if (iDisplay >= 0 &&
      (where = pixel_cmp(std, store_image_row(dp->ps, pp, iDisplay, y),
         dp->bit_width)) != 0)
   {
      char msg[64];
      sprintf(msg, "display row[%lu][%d] changed from %.2x to %.2x",
         (unsigned long)y, where-1, std[where-1],
         store_image_row(dp->ps, pp, iDisplay, y)[where-1]);
      png_error(pp, msg);
   }
}

static void
standard_image_validate(standard_display *dp, png_const_structp pp, int iImage,
    int iDisplay)
{
   png_uint_32 y;

   if (iImage >= 0)
      store_image_check(dp->ps, pp, iImage);

   if (iDisplay >= 0)
      store_image_check(dp->ps, pp, iDisplay);

   for (y=0; y<dp->h; ++y)
      standard_row_validate(dp, pp, iImage, iDisplay, y);

   /* This avoids false positives if the validation code is never called! */
   dp->ps->validated = 1;
}

static void PNGCBAPI
standard_end(png_structp ppIn, png_infop pi)
{
   png_const_structp pp = ppIn;
   standard_display *dp = voidcast(standard_display*,
      png_get_progressive_ptr(pp));

   UNUSED(pi)

   /* Validate the image - progressive reading only produces one variant for
    * interlaced images.
    */
   standard_text_validate(dp, pp, pi,
      PNG_LIBPNG_VER >= 10518/*check_end: see comments above*/);
   standard_image_validate(dp, pp, 0, -1);
}

/* A single test run checking the standard image to ensure it is not damaged. */
static void
standard_test(png_store* const psIn, png_uint_32 const id,
   int do_interlace, int use_update_info)
{
   standard_display d;
   context(psIn, fault);

   /* Set up the display (stack frame) variables from the arguments to the
    * function and initialize the locals that are filled in later.
    */
   standard_display_init(&d, psIn, id, do_interlace, use_update_info);

   /* Everything is protected by a Try/Catch.  The functions called also
    * typically have local Try/Catch blocks.
    */
   Try
   {
      png_structp pp;
      png_infop pi;

      /* Get a png_struct for reading the image. This will throw an error if it
       * fails, so we don't need to check the result.
       */
      pp = set_store_for_read(d.ps, &pi, d.id,
         d.do_interlace ?  (d.ps->progressive ?
            "pngvalid progressive deinterlacer" :
            "pngvalid sequential deinterlacer") : (d.ps->progressive ?
               "progressive reader" : "sequential reader"));

      /* Initialize the palette correctly from the png_store_file. */
      standard_palette_init(&d);

      /* Introduce the correct read function. */
      if (d.ps->progressive)
      {
         png_set_progressive_read_fn(pp, &d, standard_info, progressive_row,
            standard_end);

         /* Now feed data into the reader until we reach the end: */
         store_progressive_read(d.ps, pp, pi);
      }
      else
      {
         /* Note that this takes the store, not the display. */
         png_set_read_fn(pp, d.ps, store_read);

         /* Check the header values: */
         png_read_info(pp, pi);

         /* The code tests both versions of the images that the sequential
          * reader can produce.
          */
         standard_info_imp(&d, pp, pi, 2 /*images*/);

         /* Need the total bytes in the image below; we can't get to this point
          * unless the PNG file values have been checked against the expected
          * values.
          */
         {
            sequential_row(&d, pp, pi, 0, 1);

            /* After the last pass loop over the rows again to check that the
             * image is correct.
             */
            if (!d.speed)
            {
               standard_text_validate(&d, pp, pi, 1/*check_end*/);
               standard_image_validate(&d, pp, 0, 1);
            }
            else
               d.ps->validated = 1;
         }
      }

      /* Check for validation. */
      if (!d.ps->validated)
         png_error(pp, "image read failed silently");

      /* Successful completion. */
   }

   Catch(fault)
      d.ps = fault; /* make sure this hasn't been clobbered. */

   /* In either case clean up the store. */
   store_read_reset(d.ps);
}

static int
test_standard(png_modifier* const pm, png_byte const colour_type,
    int bdlo, int const bdhi)
{
   for (; bdlo <= bdhi; ++bdlo)
   {
      int interlace_type;

      for (interlace_type = PNG_INTERLACE_NONE;
           interlace_type < INTERLACE_LAST; ++interlace_type)
      {
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            interlace_type, 0, 0, 0), do_read_interlace, pm->use_update_info);

         if (fail(pm))
            return 0;
      }
   }

   return 1; /* keep going */
}

static void
perform_standard_test(png_modifier *pm)
{
   /* Test each colour type over the valid range of bit depths (expressed as
    * log2(bit_depth) in turn, stop as soon as any error is detected.
    */
   if (!test_standard(pm, 0, 0, READ_BDHI))
      return;

   if (!test_standard(pm, 2, 3, READ_BDHI))
      return;

   if (!test_standard(pm, 3, 0, 3))
      return;

   if (!test_standard(pm, 4, 3, READ_BDHI))
      return;

   if (!test_standard(pm, 6, 3, READ_BDHI))
      return;
}


/********************************** SIZE TESTS ********************************/
static int
test_size(png_modifier* const pm, png_byte const colour_type,
    int bdlo, int const bdhi)
{
   /* Run the tests on each combination.
    *
    * NOTE: on my 32 bit x86 each of the following blocks takes
    * a total of 3.5 seconds if done across every combo of bit depth
    * width and height.  This is a waste of time in practice, hence the
    * hinc and winc stuff:
    */
   static const png_byte hinc[] = {1, 3, 11, 1, 5};
   static const png_byte winc[] = {1, 9, 5, 7, 1};
   int save_bdlo = bdlo;

   for (; bdlo <= bdhi; ++bdlo)
   {
      png_uint_32 h, w;

      for (h=1; h<=16; h+=hinc[bdlo]) for (w=1; w<=16; w+=winc[bdlo])
      {
         /* First test all the 'size' images against the sequential
          * reader using libpng to deinterlace (where required.)  This
          * validates the write side of libpng.  There are four possibilities
          * to validate.
          */
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_NONE, w, h, 0), 0/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;

         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_NONE, w, h, 1), 0/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;

         /* Now validate the interlaced read side - do_interlace true,
          * in the progressive case this does actually make a difference
          * to the code used in the non-interlaced case too.
          */
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_NONE, w, h, 0), 1/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;

#     if CAN_WRITE_INTERLACE
         /* Validate the pngvalid code itself: */
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_ADAM7, w, h, 1), 1/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;
#     endif
      }
   }

   /* Now do the tests of libpng interlace handling, after we have made sure
    * that the pngvalid version works:
    */
   for (bdlo = save_bdlo; bdlo <= bdhi; ++bdlo)
   {
      png_uint_32 h, w;

      for (h=1; h<=16; h+=hinc[bdlo]) for (w=1; w<=16; w+=winc[bdlo])
      {
#     ifdef PNG_READ_INTERLACING_SUPPORTED
         /* Test with pngvalid generated interlaced images first; we have
          * already verify these are ok (unless pngvalid has self-consistent
          * read/write errors, which is unlikely), so this detects errors in the
          * read side first:
          */
#     if CAN_WRITE_INTERLACE
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_ADAM7, w, h, 1), 0/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;
#     endif
#     endif /* READ_INTERLACING */

#     ifdef PNG_WRITE_INTERLACING_SUPPORTED
         /* Test the libpng write side against the pngvalid read side: */
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_ADAM7, w, h, 0), 1/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;
#     endif

#     ifdef PNG_READ_INTERLACING_SUPPORTED
#     ifdef PNG_WRITE_INTERLACING_SUPPORTED
         /* Test both together: */
         standard_test(&pm->this, FILEID(colour_type, DEPTH(bdlo), 0/*palette*/,
            PNG_INTERLACE_ADAM7, w, h, 0), 0/*do_interlace*/,
            pm->use_update_info);

         if (fail(pm))
            return 0;
#     endif
#     endif /* READ_INTERLACING */
      }
   }

   return 1; /* keep going */
}

static void
perform_size_test(png_modifier *pm)
{
   /* Test each colour type over the valid range of bit depths (expressed as
    * log2(bit_depth) in turn, stop as soon as any error is detected.
    */
   if (!test_size(pm, 0, 0, READ_BDHI))
      return;

   if (!test_size(pm, 2, 3, READ_BDHI))
      return;

   /* For the moment don't do the palette test - it's a waste of time when
    * compared to the grayscale test.
    */
#if 0
   if (!test_size(pm, 3, 0, 3))
      return;
#endif

   if (!test_size(pm, 4, 3, READ_BDHI))
      return;

   if (!test_size(pm, 6, 3, READ_BDHI))
      return;
}


/******************************* TRANSFORM TESTS ******************************/
#ifdef PNG_READ_TRANSFORMS_SUPPORTED
/* A set of tests to validate libpng image transforms.  The possibilities here
 * are legion because the transforms can be combined in a combinatorial
 * fashion.  To deal with this some measure of restraint is required, otherwise
 * the tests would take forever.
 */
typedef struct image_pixel
{
   /* A local (pngvalid) representation of a PNG pixel, in all its
    * various forms.
    */
   unsigned int red, green, blue, alpha; /* For non-palette images. */
   unsigned int palette_index;           /* For a palette image. */
   png_byte     colour_type;             /* As in the spec. */
   png_byte     bit_depth;               /* Defines bit size in row */
   png_byte     sample_depth;            /* Scale of samples */
   unsigned int have_tRNS :1;            /* tRNS chunk may need processing */
   unsigned int swap_rgb :1;             /* RGB swapped to BGR */
   unsigned int alpha_first :1;          /* Alpha at start, not end */
   unsigned int alpha_inverted :1;       /* Alpha channel inverted */
   unsigned int mono_inverted :1;        /* Gray channel inverted */
   unsigned int swap16 :1;               /* Byte swap 16-bit components */
   unsigned int littleendian :1;         /* High bits on right */
   unsigned int sig_bits :1;             /* Pixel shifted (sig bits only) */

   /* For checking the code calculates double precision floating point values
    * along with an error value, accumulated from the transforms.  Because an
    * sBIT setting allows larger error bounds (indeed, by the spec, apparently
    * up to just less than +/-1 in the scaled value) the *lowest* sBIT for each
    * channel is stored.  This sBIT value is folded in to the stored error value
    * at the end of the application of the transforms to the pixel.
    *
    * If sig_bits is set above the red, green, blue and alpha values have been
    * scaled so they only contain the significant bits of the component values.
    */
   double   redf, greenf, bluef, alphaf;
   double   rede, greene, bluee, alphae;
   png_byte red_sBIT, green_sBIT, blue_sBIT, alpha_sBIT;
} image_pixel;

/* Shared utility function, see below. */
static void
image_pixel_setf(image_pixel *this, unsigned int rMax, unsigned int gMax,
        unsigned int bMax, unsigned int aMax)
{
   this->redf = this->red / (double)rMax;
   this->greenf = this->green / (double)gMax;
   this->bluef = this->blue / (double)bMax;
   this->alphaf = this->alpha / (double)aMax;

   if (this->red < rMax)
      this->rede = this->redf * DBL_EPSILON;
   else
      this->rede = 0;
   if (this->green < gMax)
      this->greene = this->greenf * DBL_EPSILON;
   else
      this->greene = 0;
   if (this->blue < bMax)
      this->bluee = this->bluef * DBL_EPSILON;
   else
      this->bluee = 0;
   if (this->alpha < aMax)
      this->alphae = this->alphaf * DBL_EPSILON;
   else
      this->alphae = 0;
}

/* Initialize the structure for the next pixel - call this before doing any
 * transforms and call it for each pixel since all the fields may need to be
 * reset.
 */
static void
image_pixel_init(image_pixel *this, png_const_bytep row, png_byte colour_type,
    png_byte bit_depth, png_uint_32 x, store_palette palette,
    const image_pixel *format /*from pngvalid transform of input*/)
{
   png_byte sample_depth =
      (png_byte)(colour_type == PNG_COLOR_TYPE_PALETTE ? 8 : bit_depth);
   unsigned int max = (1U<<sample_depth)-1;
   int swap16 = (format != 0 && format->swap16);
   int littleendian = (format != 0 && format->littleendian);
   int sig_bits = (format != 0 && format->sig_bits);

   /* Initially just set everything to the same number and the alpha to opaque.
    * Note that this currently assumes a simple palette where entry x has colour
    * rgb(x,x,x)!
    */
   this->palette_index = this->red = this->green = this->blue =
      sample(row, colour_type, bit_depth, x, 0, swap16, littleendian);
   this->alpha = max;
   this->red_sBIT = this->green_sBIT = this->blue_sBIT = this->alpha_sBIT =
      sample_depth;

   /* Then override as appropriate: */
   if (colour_type == 3) /* palette */
   {
      /* This permits the caller to default to the sample value. */
      if (palette != 0)
      {
         unsigned int i = this->palette_index;

         this->red = palette[i].red;
         this->green = palette[i].green;
         this->blue = palette[i].blue;
         this->alpha = palette[i].alpha;
      }
   }

   else /* not palette */
   {
      unsigned int i = 0;

      if ((colour_type & 4) != 0 && format != 0 && format->alpha_first)
      {
         this->alpha = this->red;
         /* This handles the gray case for 'AG' pixels */
         this->palette_index = this->red = this->green = this->blue =
            sample(row, colour_type, bit_depth, x, 1, swap16, littleendian);
         i = 1;
      }

      if (colour_type & 2)
      {
         /* Green is second for both BGR and RGB: */
         this->green = sample(row, colour_type, bit_depth, x, ++i, swap16,
                 littleendian);

         if (format != 0 && format->swap_rgb) /* BGR */
             this->red = sample(row, colour_type, bit_depth, x, ++i, swap16,
                     littleendian);
         else
             this->blue = sample(row, colour_type, bit_depth, x, ++i, swap16,
                     littleendian);
      }

      else /* grayscale */ if (format != 0 && format->mono_inverted)
         this->red = this->green = this->blue = this->red ^ max;

      if ((colour_type & 4) != 0) /* alpha */
      {
         if (format == 0 || !format->alpha_first)
             this->alpha = sample(row, colour_type, bit_depth, x, ++i, swap16,
                     littleendian);

         if (format != 0 && format->alpha_inverted)
            this->alpha ^= max;
      }
   }

   /* Calculate the scaled values, these are simply the values divided by
    * 'max' and the error is initialized to the double precision epsilon value
    * from the header file.
    */
   image_pixel_setf(this,
      sig_bits ? (1U << format->red_sBIT)-1 : max,
      sig_bits ? (1U << format->green_sBIT)-1 : max,
      sig_bits ? (1U << format->blue_sBIT)-1 : max,
      sig_bits ? (1U << format->alpha_sBIT)-1 : max);

   /* Store the input information for use in the transforms - these will
    * modify the information.
    */
   this->colour_type = colour_type;
   this->bit_depth = bit_depth;
   this->sample_depth = sample_depth;
   this->have_tRNS = 0;
   this->swap_rgb = 0;
   this->alpha_first = 0;
   this->alpha_inverted = 0;
   this->mono_inverted = 0;
   this->swap16 = 0;
   this->littleendian = 0;
   this->sig_bits = 0;
}

#if defined PNG_READ_EXPAND_SUPPORTED || defined PNG_READ_GRAY_TO_RGB_SUPPORTED\
   || defined PNG_READ_EXPAND_SUPPORTED || defined PNG_READ_EXPAND_16_SUPPORTED\
   || defined PNG_READ_BACKGROUND_SUPPORTED
/* Convert a palette image to an rgb image.  This necessarily converts the tRNS
 * chunk at the same time, because the tRNS will be in palette form.  The way
 * palette validation works means that the original palette is never updated,
 * instead the image_pixel value from the row contains the RGB of the
 * corresponding palette entry and *this* is updated.  Consequently this routine
 * only needs to change the colour type information.
 */
static void
image_pixel_convert_PLTE(image_pixel *this)
{
   if (this->colour_type == PNG_COLOR_TYPE_PALETTE)
   {
      if (this->have_tRNS)
      {
         this->colour_type = PNG_COLOR_TYPE_RGB_ALPHA;
         this->have_tRNS = 0;
      }
      else
         this->colour_type = PNG_COLOR_TYPE_RGB;

      /* The bit depth of the row changes at this point too (notice that this is
       * the row format, not the sample depth, which is separate.)
       */
      this->bit_depth = 8;
   }
}

/* Add an alpha channel; this will import the tRNS information because tRNS is
 * not valid in an alpha image.  The bit depth will invariably be set to at
 * least 8 prior to 1.7.0.  Palette images will be converted to alpha (using
 * the above API).  With png_set_background the alpha channel is never expanded
 * but this routine is used by pngvalid to simplify code; 'for_background'
 * records this.
 */
static void
image_pixel_add_alpha(image_pixel *this, const standard_display *display,
   int for_background)
{
   if (this->colour_type == PNG_COLOR_TYPE_PALETTE)
      image_pixel_convert_PLTE(this);

   if ((this->colour_type & PNG_COLOR_MASK_ALPHA) == 0)
   {
      if (this->colour_type == PNG_COLOR_TYPE_GRAY)
      {
#        if PNG_LIBPNG_VER < 10700
            if (!for_background && this->bit_depth < 8)
               this->bit_depth = this->sample_depth = 8;
#        endif

         if (this->have_tRNS)
         {
            /* After 1.7 the expansion of bit depth only happens if there is a
             * tRNS chunk to expand at this point.
             */
#           if PNG_LIBPNG_VER >= 10700
               if (!for_background && this->bit_depth < 8)
                  this->bit_depth = this->sample_depth = 8;
#           endif

            this->have_tRNS = 0;

            /* Check the input, original, channel value here against the
             * original tRNS gray chunk valie.
             */
            if (this->red == display->transparent.red)
               this->alphaf = 0;
            else
               this->alphaf = 1;
         }
         else
            this->alphaf = 1;

         this->colour_type = PNG_COLOR_TYPE_GRAY_ALPHA;
      }

      else if (this->colour_type == PNG_COLOR_TYPE_RGB)
      {
         if (this->have_tRNS)
         {
            this->have_tRNS = 0;

            /* Again, check the exact input values, not the current transformed
             * value!
             */
            if (this->red == display->transparent.red &&
               this->green == display->transparent.green &&
               this->blue == display->transparent.blue)
               this->alphaf = 0;
            else
               this->alphaf = 1;
         }
         else
            this->alphaf = 1;

         this->colour_type = PNG_COLOR_TYPE_RGB_ALPHA;
      }

      /* The error in the alpha is zero and the sBIT value comes from the
       * original sBIT data (actually it will always be the original bit depth).
       */
      this->alphae = 0;
      this->alpha_sBIT = display->alpha_sBIT;
   }
}
#endif /* transforms that need image_pixel_add_alpha */

struct transform_display;
typedef struct image_transform
{
   /* The name of this transform: a string. */
   const char *name;

   /* Each transform can be disabled from the command line: */
   int enable;

   /* The global list of transforms; read only. */
   struct image_transform *const list;

   /* The global count of the number of times this transform has been set on an
    * image.
    */
   unsigned int global_use;

   /* The local count of the number of times this transform has been set. */
   unsigned int local_use;

   /* The next transform in the list, each transform must call its own next
    * transform after it has processed the pixel successfully.
    */
   const struct image_transform *next;

   /* A single transform for the image, expressed as a series of function
    * callbacks and some space for values.
    *
    * First a callback to add any required modifications to the png_modifier;
    * this gets called just before the modifier is set up for read.
    */
   void (*ini)(const struct image_transform *this,
      struct transform_display *that);

   /* And a callback to set the transform on the current png_read_struct:
    */
   void (*set)(const struct image_transform *this,
      struct transform_display *that, png_structp pp, png_infop pi);

   /* Then a transform that takes an input pixel in one PNG format or another
    * and modifies it by a pngvalid implementation of the transform (thus
    * duplicating the libpng intent without, we hope, duplicating the bugs
    * in the libpng implementation!)  The png_structp is solely to allow error
    * reporting via png_error and png_warning.
    */
   void (*mod)(const struct image_transform *this, image_pixel *that,
      png_const_structp pp, const struct transform_display *display);

   /* Add this transform to the list and return true if the transform is
    * meaningful for this colour type and bit depth - if false then the
    * transform should have no effect on the image so there's not a lot of
    * point running it.
    */
   int (*add)(struct image_transform *this,
      const struct image_transform **that, png_byte colour_type,
      png_byte bit_depth);
} image_transform;

typedef struct transform_display
{
   standard_display this;

   /* Parameters */
   png_modifier*              pm;
   const image_transform* transform_list;
   unsigned int max_gamma_8;

   /* Local variables */
   png_byte output_colour_type;
   png_byte output_bit_depth;
   png_byte unpacked;

   /* Modifications (not necessarily used.) */
   gama_modification gama_mod;
   chrm_modification chrm_mod;
   srgb_modification srgb_mod;
} transform_display;

/* Set sRGB, cHRM and gAMA transforms as required by the current encoding. */
static void
transform_set_encoding(transform_display *this)
{
   /* Set up the png_modifier '_current' fields then use these to determine how
    * to add appropriate chunks.
    */
   png_modifier *pm = this->pm;

   modifier_set_encoding(pm);

   if (modifier_color_encoding_is_set(pm))
   {
      if (modifier_color_encoding_is_sRGB(pm))
         srgb_modification_init(&this->srgb_mod, pm, PNG_sRGB_INTENT_ABSOLUTE);

      else
      {
         /* Set gAMA and cHRM separately. */
         gama_modification_init(&this->gama_mod, pm, pm->current_gamma);

         if (pm->current_encoding != 0)
            chrm_modification_init(&this->chrm_mod, pm, pm->current_encoding);
      }
   }
}

/* Three functions to end the list: */
static void
image_transform_ini_end(const image_transform *this,
   transform_display *that)
{
   UNUSED(this)
   UNUSED(that)
}

static void
image_transform_set_end(const image_transform *this,
   transform_display *that, png_structp pp, png_infop pi)
{
   UNUSED(this)
   UNUSED(that)
   UNUSED(pp)
   UNUSED(pi)
}

/* At the end of the list recalculate the output image pixel value from the
 * double precision values set up by the preceding 'mod' calls:
 */
static unsigned int
sample_scale(double sample_value, unsigned int scale)
{
   sample_value = floor(sample_value * scale + .5);

   /* Return NaN as 0: */
   if (!(sample_value > 0))
      sample_value = 0;
   else if (sample_value > scale)
      sample_value = scale;

   return (unsigned int)sample_value;
}

static void
image_transform_mod_end(const image_transform *this, image_pixel *that,
    png_const_structp pp, const transform_display *display)
{
   unsigned int scale = (1U<<that->sample_depth)-1;
   int sig_bits = that->sig_bits;

   UNUSED(this)
   UNUSED(pp)
   UNUSED(display)

   /* At the end recalculate the digitized red green and blue values according
    * to the current sample_depth of the pixel.
    *
    * The sample value is simply scaled to the maximum, checking for over
    * and underflow (which can both happen for some image transforms,
    * including simple size scaling, though libpng doesn't do that at present.
    */
   that->red = sample_scale(that->redf, scale);

   /* This is a bit bogus; really the above calculation should use the red_sBIT
    * value, not sample_depth, but because libpng does png_set_shift by just
    * shifting the bits we get errors if we don't do it the same way.
    */
   if (sig_bits && that->red_sBIT < that->sample_depth)
      that->red >>= that->sample_depth - that->red_sBIT;

   /* The error value is increased, at the end, according to the lowest sBIT
    * value seen.  Common sense tells us that the intermediate integer
    * representations are no more accurate than +/- 0.5 in the integral values,
    * the sBIT allows the implementation to be worse than this.  In addition the
    * PNG specification actually permits any error within the range (-1..+1),
    * but that is ignored here.  Instead the final digitized value is compared,
    * below to the digitized value of the error limits - this has the net effect
    * of allowing (almost) +/-1 in the output value.  It's difficult to see how
    * any algorithm that digitizes intermediate results can be more accurate.
    */
   that->rede += 1./(2*((1U<<that->red_sBIT)-1));

   if (that->colour_type & PNG_COLOR_MASK_COLOR)
   {
      that->green = sample_scale(that->greenf, scale);
      if (sig_bits && that->green_sBIT < that->sample_depth)
         that->green >>= that->sample_depth - that->green_sBIT;

      that->blue = sample_scale(that->bluef, scale);
      if (sig_bits && that->blue_sBIT < that->sample_depth)
         that->blue >>= that->sample_depth - that->blue_sBIT;

      that->greene += 1./(2*((1U<<that->green_sBIT)-1));
      that->bluee += 1./(2*((1U<<that->blue_sBIT)-1));
   }
   else
   {
      that->blue = that->green = that->red;
      that->bluef = that->greenf = that->redf;
      that->bluee = that->greene = that->rede;
   }

   if ((that->colour_type & PNG_COLOR_MASK_ALPHA) ||
      that->colour_type == PNG_COLOR_TYPE_PALETTE)
   {
      that->alpha = sample_scale(that->alphaf, scale);
      that->alphae += 1./(2*((1U<<that->alpha_sBIT)-1));
   }
   else
   {
      that->alpha = scale; /* opaque */
      that->alphaf = 1;    /* Override this. */
      that->alphae = 0;    /* It's exact ;-) */
   }

   if (sig_bits && that->alpha_sBIT < that->sample_depth)
      that->alpha >>= that->sample_depth - that->alpha_sBIT;
}

/* Static 'end' structure: */
static image_transform image_transform_end =
{
   "(end)", /* name */
   1, /* enable */
   0, /* list */
   0, /* global_use */
   0, /* local_use */
   0, /* next */
   image_transform_ini_end,
   image_transform_set_end,
   image_transform_mod_end,
   0 /* never called, I want it to crash if it is! */
};

/* Reader callbacks and implementations, where they differ from the standard
 * ones.
 */
static void
transform_display_init(transform_display *dp, png_modifier *pm, png_uint_32 id,
    const image_transform *transform_list)
{
   memset(dp, 0, sizeof *dp);

   /* Standard fields */
   standard_display_init(&dp->this, &pm->this, id, do_read_interlace,
      pm->use_update_info);

   /* Parameter fields */
   dp->pm = pm;
   dp->transform_list = transform_list;
   dp->max_gamma_8 = 16;

   /* Local variable fields */
   dp->output_colour_type = 255; /* invalid */
   dp->output_bit_depth = 255;  /* invalid */
   dp->unpacked = 0; /* not unpacked */
}

static void
transform_info_imp(transform_display *dp, png_structp pp, png_infop pi)
{
   /* Reuse the standard stuff as appropriate. */
   standard_info_part1(&dp->this, pp, pi);

   /* Now set the list of transforms. */
   dp->transform_list->set(dp->transform_list, dp, pp, pi);

   /* Update the info structure for these transforms: */
   {
      int i = dp->this.use_update_info;
      /* Always do one call, even if use_update_info is 0. */
      do
         png_read_update_info(pp, pi);
      while (--i > 0);
   }

   /* And get the output information into the standard_display */
   standard_info_part2(&dp->this, pp, pi, 1/*images*/);

   /* Plus the extra stuff we need for the transform tests: */
   dp->output_colour_type = png_get_color_type(pp, pi);
   dp->output_bit_depth = png_get_bit_depth(pp, pi);

   /* If png_set_filler is in action then fake the output color type to include
    * an alpha channel where appropriate.
    */
   if (dp->output_bit_depth >= 8 &&
       (dp->output_colour_type == PNG_COLOR_TYPE_RGB ||
        dp->output_colour_type == PNG_COLOR_TYPE_GRAY) && dp->this.filler)
       dp->output_colour_type |= 4;

   /* Validate the combination of colour type and bit depth that we are getting
    * out of libpng; the semantics of something not in the PNG spec are, at
    * best, unclear.
    */
   switch (dp->output_colour_type)
   {
   case PNG_COLOR_TYPE_PALETTE:
      if (dp->output_bit_depth > 8) goto error;
      /* FALLTHROUGH */
   case PNG_COLOR_TYPE_GRAY:
      if (dp->output_bit_depth == 1 || dp->output_bit_depth == 2 ||
         dp->output_bit_depth == 4)
         break;
      /* FALLTHROUGH */
   default:
      if (dp->output_bit_depth == 8 || dp->output_bit_depth == 16)
         break;
      /* FALLTHROUGH */
   error:
      {
         char message[128];
         size_t pos;

         pos = safecat(message, sizeof message, 0,
            "invalid final bit depth: colour type(");
         pos = safecatn(message, sizeof message, pos, dp->output_colour_type);
         pos = safecat(message, sizeof message, pos, ") with bit depth: ");
         pos = safecatn(message, sizeof message, pos, dp->output_bit_depth);

         png_error(pp, message);
      }
   }

   /* Use a test pixel to check that the output agrees with what we expect -
    * this avoids running the whole test if the output is unexpected.  This also
    * checks for internal errors.
    */
   {
      image_pixel test_pixel;

      memset(&test_pixel, 0, sizeof test_pixel);
      test_pixel.colour_type = dp->this.colour_type; /* input */
      test_pixel.bit_depth = dp->this.bit_depth;
      if (test_pixel.colour_type == PNG_COLOR_TYPE_PALETTE)
         test_pixel.sample_depth = 8;
      else
         test_pixel.sample_depth = test_pixel.bit_depth;
      /* Don't need sBIT here, but it must be set to non-zero to avoid
       * arithmetic overflows.
       */
      test_pixel.have_tRNS = dp->this.is_transparent != 0;
      test_pixel.red_sBIT = test_pixel.green_sBIT = test_pixel.blue_sBIT =
         test_pixel.alpha_sBIT = test_pixel.sample_depth;

      dp->transform_list->mod(dp->transform_list, &test_pixel, pp, dp);

      if (test_pixel.colour_type != dp->output_colour_type)
      {
         char message[128];
         size_t pos = safecat(message, sizeof message, 0, "colour type ");

         pos = safecatn(message, sizeof message, pos, dp->output_colour_type);
         pos = safecat(message, sizeof message, pos, " expected ");
         pos = safecatn(message, sizeof message, pos, test_pixel.colour_type);

         png_error(pp, message);
      }

      if (test_pixel.bit_depth != dp->output_bit_depth)
      {
         char message[128];
         size_t pos = safecat(message, sizeof message, 0, "bit depth ");

         pos = safecatn(message, sizeof message, pos, dp->output_bit_depth);
         pos = safecat(message, sizeof message, pos, " expected ");
         pos = safecatn(message, sizeof message, pos, test_pixel.bit_depth);

         png_error(pp, message);
      }

      /* If both bit depth and colour type are correct check the sample depth.
       */
      if (test_pixel.colour_type == PNG_COLOR_TYPE_PALETTE &&
          test_pixel.sample_depth != 8) /* oops - internal error! */
         png_error(pp, "pngvalid: internal: palette sample depth not 8");
      else if (dp->unpacked && test_pixel.bit_depth != 8)
         png_error(pp, "pngvalid: internal: bad unpacked pixel depth");
      else if (!dp->unpacked && test_pixel.colour_type != PNG_COLOR_TYPE_PALETTE
              && test_pixel.bit_depth != test_pixel.sample_depth)
      {
         char message[128];
         size_t pos = safecat(message, sizeof message, 0,
            "internal: sample depth ");

         /* Because unless something has set 'unpacked' or the image is palette
          * mapped we expect the transform to keep sample depth and bit depth
          * the same.
          */
         pos = safecatn(message, sizeof message, pos, test_pixel.sample_depth);
         pos = safecat(message, sizeof message, pos, " expected ");
         pos = safecatn(message, sizeof message, pos, test_pixel.bit_depth);

         png_error(pp, message);
      }
      else if (test_pixel.bit_depth != dp->output_bit_depth)
      {
         /* This could be a libpng error too; libpng has not produced what we
          * expect for the output bit depth.
          */
         char message[128];
         size_t pos = safecat(message, sizeof message, 0,
            "internal: bit depth ");

         pos = safecatn(message, sizeof message, pos, dp->output_bit_depth);
         pos = safecat(message, sizeof message, pos, " expected ");
         pos = safecatn(message, sizeof message, pos, test_pixel.bit_depth);

         png_error(pp, message);
      }
   }
}

static void PNGCBAPI
transform_info(png_structp pp, png_infop pi)
{
   transform_info_imp(voidcast(transform_display*, png_get_progressive_ptr(pp)),
      pp, pi);
}

static void
transform_range_check(png_const_structp pp, unsigned int r, unsigned int g,
   unsigned int b, unsigned int a, unsigned int in_digitized, double in,
   unsigned int out, png_byte sample_depth, double err, double limit,
   const char *name, double digitization_error)
{
   /* Compare the scaled, digitzed, values of our local calculation (in+-err)
    * with the digitized values libpng produced;  'sample_depth' is the actual
    * digitization depth of the libpng output colors (the bit depth except for
    * palette images where it is always 8.)  The check on 'err' is to detect
    * internal errors in pngvalid itself.
    */
   unsigned int max = (1U<<sample_depth)-1;
   double in_min = ceil((in-err)*max - digitization_error);
   double in_max = floor((in+err)*max + digitization_error);
   if (debugonly(err > limit ||) !(out >= in_min && out <= in_max))
   {
      char message[256];
      size_t pos;

      pos = safecat(message, sizeof message, 0, name);
      pos = safecat(message, sizeof message, pos, " output value error: rgba(");
      pos = safecatn(message, sizeof message, pos, r);
      pos = safecat(message, sizeof message, pos, ",");
      pos = safecatn(message, sizeof message, pos, g);
      pos = safecat(message, sizeof message, pos, ",");
      pos = safecatn(message, sizeof message, pos, b);
      pos = safecat(message, sizeof message, pos, ",");
      pos = safecatn(message, sizeof message, pos, a);
      pos = safecat(message, sizeof message, pos, "): ");
      pos = safecatn(message, sizeof message, pos, out);
      pos = safecat(message, sizeof message, pos, " expected: ");
      pos = safecatn(message, sizeof message, pos, in_digitized);
      pos = safecat(message, sizeof message, pos, " (");
      pos = safecatd(message, sizeof message, pos, (in-err)*max, 3);
      pos = safecat(message, sizeof message, pos, "..");
      pos = safecatd(message, sizeof message, pos, (in+err)*max, 3);
      pos = safecat(message, sizeof message, pos, ")");

      png_error(pp, message);
   }

   UNUSED(limit)
}

static void
transform_image_validate(transform_display *dp, png_const_structp pp,
   png_infop pi)
{
   /* Constants for the loop below: */
   const png_store* const ps = dp->this.ps;
   png_byte in_ct = dp->this.colour_type;
   png_byte in_bd = dp->this.bit_depth;
   png_uint_32 w = dp->this.w;
   png_uint_32 h = dp->this.h;
   png_byte out_ct = dp->output_colour_type;
   png_byte out_bd = dp->output_bit_depth;
   png_byte sample_depth =
      (png_byte)(out_ct == PNG_COLOR_TYPE_PALETTE ? 8 : out_bd);
   png_byte red_sBIT = dp->this.red_sBIT;
   png_byte green_sBIT = dp->this.green_sBIT;
   png_byte blue_sBIT = dp->this.blue_sBIT;
   png_byte alpha_sBIT = dp->this.alpha_sBIT;
   int have_tRNS = dp->this.is_transparent;
   double digitization_error;

   store_palette out_palette;
   png_uint_32 y;

   UNUSED(pi)

   /* Check for row overwrite errors */
   store_image_check(dp->this.ps, pp, 0);

   /* Read the palette corresponding to the output if the output colour type
    * indicates a palette, otherwise set out_palette to garbage.
    */
   if (out_ct == PNG_COLOR_TYPE_PALETTE)
   {
      /* Validate that the palette count itself has not changed - this is not
       * expected.
       */
      int npalette = (-1);

      (void)read_palette(out_palette, &npalette, pp, pi);
      if (npalette != dp->this.npalette)
         png_error(pp, "unexpected change in palette size");

      digitization_error = .5;
   }
   else
   {
      png_byte in_sample_depth;

      memset(out_palette, 0x5e, sizeof out_palette);

      /* use-input-precision means assume that if the input has 8 bit (or less)
       * samples and the output has 16 bit samples the calculations will be done
       * with 8 bit precision, not 16.
       */
      if (in_ct == PNG_COLOR_TYPE_PALETTE || in_bd < 16)
         in_sample_depth = 8;
      else
         in_sample_depth = in_bd;

      if (sample_depth != 16 || in_sample_depth > 8 ||
         !dp->pm->calculations_use_input_precision)
         digitization_error = .5;

      /* Else calculations are at 8 bit precision, and the output actually
       * consists of scaled 8-bit values, so scale .5 in 8 bits to the 16 bits:
       */
      else
         digitization_error = .5 * 257;
   }

   for (y=0; y<h; ++y)
   {
      png_const_bytep const pRow = store_image_row(ps, pp, 0, y);
      png_uint_32 x;

      /* The original, standard, row pre-transforms. */
      png_byte std[STANDARD_ROWMAX];

      transform_row(pp, std, in_ct, in_bd, y);

      /* Go through each original pixel transforming it and comparing with what
       * libpng did to the same pixel.
       */
      for (x=0; x<w; ++x)
      {
         image_pixel in_pixel, out_pixel;
         unsigned int r, g, b, a;

         /* Find out what we think the pixel should be: */
         image_pixel_init(&in_pixel, std, in_ct, in_bd, x, dp->this.palette,
                 NULL);

         in_pixel.red_sBIT = red_sBIT;
         in_pixel.green_sBIT = green_sBIT;
         in_pixel.blue_sBIT = blue_sBIT;
         in_pixel.alpha_sBIT = alpha_sBIT;
         in_pixel.have_tRNS = have_tRNS != 0;

         /* For error detection, below. */
         r = in_pixel.red;
         g = in_pixel.green;
         b = in_pixel.blue;
         a = in_pixel.alpha;

         /* This applies the transforms to the input data, including output
          * format operations which must be used when reading the output
          * pixel that libpng produces.
          */
         dp->transform_list->mod(dp->transform_list, &in_pixel, pp, dp);

         /* Read the output pixel and compare it to what we got, we don't
          * use the error field here, so no need to update sBIT.  in_pixel
          * says whether we expect libpng to change the output format.
          */
         image_pixel_init(&out_pixel, pRow, out_ct, out_bd, x, out_palette,
                 &in_pixel);

         /* We don't expect changes to the index here even if the bit depth is
          * changed.
          */
         if (in_ct == PNG_COLOR_TYPE_PALETTE &&
            out_ct == PNG_COLOR_TYPE_PALETTE)
         {
            if (in_pixel.palette_index != out_pixel.palette_index)
               png_error(pp, "unexpected transformed palette index");
         }

         /* Check the colours for palette images too - in fact the palette could
          * be separately verified itself in most cases.
          */
         if (in_pixel.red != out_pixel.red)
            transform_range_check(pp, r, g, b, a, in_pixel.red, in_pixel.redf,
               out_pixel.red, sample_depth, in_pixel.rede,
               dp->pm->limit + 1./(2*((1U<<in_pixel.red_sBIT)-1)), "red/gray",
               digitization_error);

         if ((out_ct & PNG_COLOR_MASK_COLOR) != 0 &&
            in_pixel.green != out_pixel.green)
            transform_range_check(pp, r, g, b, a, in_pixel.green,
               in_pixel.greenf, out_pixel.green, sample_depth, in_pixel.greene,
               dp->pm->limit + 1./(2*((1U<<in_pixel.green_sBIT)-1)), "green",
               digitization_error);

         if ((out_ct & PNG_COLOR_MASK_COLOR) != 0 &&
            in_pixel.blue != out_pixel.blue)
            transform_range_check(pp, r, g, b, a, in_pixel.blue, in_pixel.bluef,
               out_pixel.blue, sample_depth, in_pixel.bluee,
               dp->pm->limit + 1./(2*((1U<<in_pixel.blue_sBIT)-1)), "blue",
               digitization_error);

         if ((out_ct & PNG_COLOR_MASK_ALPHA) != 0 &&
            in_pixel.alpha != out_pixel.alpha)
            transform_range_check(pp, r, g, b, a, in_pixel.alpha,
               in_pixel.alphaf, out_pixel.alpha, sample_depth, in_pixel.alphae,
               dp->pm->limit + 1./(2*((1U<<in_pixel.alpha_sBIT)-1)), "alpha",
               digitization_error);
      } /* pixel (x) loop */
   } /* row (y) loop */

   /* Record that something was actually checked to avoid a false positive. */
   dp->this.ps->validated = 1;
}

static void PNGCBAPI
transform_end(png_structp ppIn, png_infop pi)
{
   png_const_structp pp = ppIn;
   transform_display *dp = voidcast(transform_display*,
      png_get_progressive_ptr(pp));

   if (!dp->this.speed)
      transform_image_validate(dp, pp, pi);
   else
      dp->this.ps->validated = 1;
}

/* A single test run. */
static void
transform_test(png_modifier *pmIn, png_uint_32 idIn,
    const image_transform* transform_listIn, const char * const name)
{
   transform_display d;
   context(&pmIn->this, fault);

   transform_display_init(&d, pmIn, idIn, transform_listIn);

   Try
   {
      size_t pos = 0;
      png_structp pp;
      png_infop pi;
      char full_name[256];

      /* Make sure the encoding fields are correct and enter the required
       * modifications.
       */
      transform_set_encoding(&d);

      /* Add any modifications required by the transform list. */
      d.transform_list->ini(d.transform_list, &d);

      /* Add the color space information, if any, to the name. */
      pos = safecat(full_name, sizeof full_name, pos, name);
      pos = safecat_current_encoding(full_name, sizeof full_name, pos, d.pm);

      /* Get a png_struct for reading the image. */
      pp = set_modifier_for_read(d.pm, &pi, d.this.id, full_name);
      standard_palette_init(&d.this);

#     if 0
         /* Logging (debugging only) */
         {
            char buffer[256];

            (void)store_message(&d.pm->this, pp, buffer, sizeof buffer, 0,
               "running test");

            fprintf(stderr, "%s\n", buffer);
         }
#     endif

      /* Introduce the correct read function. */
      if (d.pm->this.progressive)
      {
         /* Share the row function with the standard implementation. */
         png_set_progressive_read_fn(pp, &d, transform_info, progressive_row,
            transform_end);

         /* Now feed data into the reader until we reach the end: */
         modifier_progressive_read(d.pm, pp, pi);
      }
      else
      {
         /* modifier_read expects a png_modifier* */
         png_set_read_fn(pp, d.pm, modifier_read);

         /* Check the header values: */
         png_read_info(pp, pi);

         /* Process the 'info' requirements. Only one image is generated */
         transform_info_imp(&d, pp, pi);

         sequential_row(&d.this, pp, pi, -1, 0);

         if (!d.this.speed)
            transform_image_validate(&d, pp, pi);
         else
            d.this.ps->validated = 1;
      }

      modifier_reset(d.pm);
   }

   Catch(fault)
   {
      modifier_reset(voidcast(png_modifier*,(void*)fault));
   }
}

/* The transforms: */
#define ITSTRUCT(name) image_transform_##name
#define ITDATA(name) image_transform_data_##name
#define image_transform_ini image_transform_default_ini
#define IT(name)\
static image_transform ITSTRUCT(name) =\
{\
   #name,\
   1, /*enable*/\
   &PT, /*list*/\
   0, /*global_use*/\
   0, /*local_use*/\
   0, /*next*/\
   image_transform_ini,\
   image_transform_png_set_##name##_set,\
   image_transform_png_set_##name##_mod,\
   image_transform_png_set_##name##_add\
}
#define PT ITSTRUCT(end) /* stores the previous transform */

/* To save code: */
extern void image_transform_default_ini(const image_transform *this,
   transform_display *that); /* silence GCC warnings */

void /* private, but almost always needed */
image_transform_default_ini(const image_transform *this,
    transform_display *that)
{
   this->next->ini(this->next, that);
}

#ifdef PNG_READ_BACKGROUND_SUPPORTED
static int
image_transform_default_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return 1;
}
#endif

#ifdef PNG_READ_EXPAND_SUPPORTED
/* png_set_palette_to_rgb */
static void
image_transform_png_set_palette_to_rgb_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_palette_to_rgb(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_palette_to_rgb_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type == PNG_COLOR_TYPE_PALETTE)
      image_pixel_convert_PLTE(that);

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_palette_to_rgb_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return colour_type == PNG_COLOR_TYPE_PALETTE;
}

IT(palette_to_rgb);
#undef PT
#define PT ITSTRUCT(palette_to_rgb)
#endif /* PNG_READ_EXPAND_SUPPORTED */

#ifdef PNG_READ_EXPAND_SUPPORTED
/* png_set_tRNS_to_alpha */
static void
image_transform_png_set_tRNS_to_alpha_set(const image_transform *this,
   transform_display *that, png_structp pp, png_infop pi)
{
   png_set_tRNS_to_alpha(pp);

   /* If there was a tRNS chunk that would get expanded and add an alpha
    * channel is_transparent must be updated:
    */
   if (that->this.has_tRNS)
      that->this.is_transparent = 1;

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_tRNS_to_alpha_mod(const image_transform *this,
   image_pixel *that, png_const_structp pp,
   const transform_display *display)
{
#if PNG_LIBPNG_VER < 10700
   /* LIBPNG BUG: this always forces palette images to RGB. */
   if (that->colour_type == PNG_COLOR_TYPE_PALETTE)
      image_pixel_convert_PLTE(that);
#endif

   /* This effectively does an 'expand' only if there is some transparency to
    * convert to an alpha channel.
    */
   if (that->have_tRNS)
#     if PNG_LIBPNG_VER >= 10700
         if (that->colour_type != PNG_COLOR_TYPE_PALETTE &&
             (that->colour_type & PNG_COLOR_MASK_ALPHA) == 0)
#     endif
      image_pixel_add_alpha(that, &display->this, 0/*!for background*/);

#if PNG_LIBPNG_VER < 10700
   /* LIBPNG BUG: otherwise libpng still expands to 8 bits! */
   else
   {
      if (that->bit_depth < 8)
         that->bit_depth =8;
      if (that->sample_depth < 8)
         that->sample_depth = 8;
   }
#endif

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_tRNS_to_alpha_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   /* We don't know yet whether there will be a tRNS chunk, but we know that
    * this transformation should do nothing if there already is an alpha
    * channel.  In addition, after the bug fix in 1.7.0, there is no longer
    * any action on a palette image.
    */
   return
#  if PNG_LIBPNG_VER >= 10700
      colour_type != PNG_COLOR_TYPE_PALETTE &&
#  endif
   (colour_type & PNG_COLOR_MASK_ALPHA) == 0;
}

IT(tRNS_to_alpha);
#undef PT
#define PT ITSTRUCT(tRNS_to_alpha)
#endif /* PNG_READ_EXPAND_SUPPORTED */

#ifdef PNG_READ_GRAY_TO_RGB_SUPPORTED
/* png_set_gray_to_rgb */
static void
image_transform_png_set_gray_to_rgb_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_gray_to_rgb(pp);
   /* NOTE: this doesn't result in tRNS expansion. */
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_gray_to_rgb_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* NOTE: we can actually pend the tRNS processing at this point because we
    * can correctly recognize the original pixel value even though we have
    * mapped the one gray channel to the three RGB ones, but in fact libpng
    * doesn't do this, so we don't either.
    */
   if ((that->colour_type & PNG_COLOR_MASK_COLOR) == 0 && that->have_tRNS)
      image_pixel_add_alpha(that, &display->this, 0/*!for background*/);

   /* Simply expand the bit depth and alter the colour type as required. */
   if (that->colour_type == PNG_COLOR_TYPE_GRAY)
   {
      /* RGB images have a bit depth at least equal to '8' */
      if (that->bit_depth < 8)
         that->sample_depth = that->bit_depth = 8;

      /* And just changing the colour type works here because the green and blue
       * channels are being maintained in lock-step with the red/gray:
       */
      that->colour_type = PNG_COLOR_TYPE_RGB;
   }

   else if (that->colour_type == PNG_COLOR_TYPE_GRAY_ALPHA)
      that->colour_type = PNG_COLOR_TYPE_RGB_ALPHA;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_gray_to_rgb_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return (colour_type & PNG_COLOR_MASK_COLOR) == 0;
}

IT(gray_to_rgb);
#undef PT
#define PT ITSTRUCT(gray_to_rgb)
#endif /* PNG_READ_GRAY_TO_RGB_SUPPORTED */

#ifdef PNG_READ_EXPAND_SUPPORTED
/* png_set_expand */
static void
image_transform_png_set_expand_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_expand(pp);

   if (that->this.has_tRNS)
      that->this.is_transparent = 1;

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_expand_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* The general expand case depends on what the colour type is: */
   if (that->colour_type == PNG_COLOR_TYPE_PALETTE)
      image_pixel_convert_PLTE(that);
   else if (that->bit_depth < 8) /* grayscale */
      that->sample_depth = that->bit_depth = 8;

   if (that->have_tRNS)
      image_pixel_add_alpha(that, &display->this, 0/*!for background*/);

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_expand_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   /* 'expand' should do nothing for RGBA or GA input - no tRNS and the bit
    * depth is at least 8 already.
    */
   return (colour_type & PNG_COLOR_MASK_ALPHA) == 0;
}

IT(expand);
#undef PT
#define PT ITSTRUCT(expand)
#endif /* PNG_READ_EXPAND_SUPPORTED */

#ifdef PNG_READ_EXPAND_SUPPORTED
/* png_set_expand_gray_1_2_4_to_8
 * Pre 1.7.0 LIBPNG BUG: this just does an 'expand'
 */
static void
image_transform_png_set_expand_gray_1_2_4_to_8_set(
    const image_transform *this, transform_display *that, png_structp pp,
    png_infop pi)
{
   png_set_expand_gray_1_2_4_to_8(pp);
   /* NOTE: don't expect this to expand tRNS */
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_expand_gray_1_2_4_to_8_mod(
    const image_transform *this, image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
#if PNG_LIBPNG_VER < 10700
   image_transform_png_set_expand_mod(this, that, pp, display);
#else
   /* Only expand grayscale of bit depth less than 8: */
   if (that->colour_type == PNG_COLOR_TYPE_GRAY &&
       that->bit_depth < 8)
      that->sample_depth = that->bit_depth = 8;

   this->next->mod(this->next, that, pp, display);
#endif /* 1.7 or later */
}

static int
image_transform_png_set_expand_gray_1_2_4_to_8_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
#if PNG_LIBPNG_VER < 10700
   return image_transform_png_set_expand_add(this, that, colour_type,
      bit_depth);
#else
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   /* This should do nothing unless the color type is gray and the bit depth is
    * less than 8:
    */
   return colour_type == PNG_COLOR_TYPE_GRAY && bit_depth < 8;
#endif /* 1.7 or later */
}

IT(expand_gray_1_2_4_to_8);
#undef PT
#define PT ITSTRUCT(expand_gray_1_2_4_to_8)
#endif /* PNG_READ_EXPAND_SUPPORTED */

#ifdef PNG_READ_EXPAND_16_SUPPORTED
/* png_set_expand_16 */
static void
image_transform_png_set_expand_16_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_expand_16(pp);

   /* NOTE: prior to 1.7 libpng does SET_EXPAND as well, so tRNS is expanded. */
#  if PNG_LIBPNG_VER < 10700
      if (that->this.has_tRNS)
         that->this.is_transparent = 1;
#  endif

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_expand_16_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* Expect expand_16 to expand everything to 16 bits as a result of also
    * causing 'expand' to happen.
    */
   if (that->colour_type == PNG_COLOR_TYPE_PALETTE)
      image_pixel_convert_PLTE(that);

   if (that->have_tRNS)
      image_pixel_add_alpha(that, &display->this, 0/*!for background*/);

   if (that->bit_depth < 16)
      that->sample_depth = that->bit_depth = 16;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_expand_16_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   /* expand_16 does something unless the bit depth is already 16. */
   return bit_depth < 16;
}

IT(expand_16);
#undef PT
#define PT ITSTRUCT(expand_16)
#endif /* PNG_READ_EXPAND_16_SUPPORTED */

#ifdef PNG_READ_SCALE_16_TO_8_SUPPORTED  /* API added in 1.5.4 */
/* png_set_scale_16 */
static void
image_transform_png_set_scale_16_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_scale_16(pp);
#  if PNG_LIBPNG_VER < 10700
      /* libpng will limit the gamma table size: */
      that->max_gamma_8 = PNG_MAX_GAMMA_8;
#  endif
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_scale_16_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth == 16)
   {
      that->sample_depth = that->bit_depth = 8;
      if (that->red_sBIT > 8) that->red_sBIT = 8;
      if (that->green_sBIT > 8) that->green_sBIT = 8;
      if (that->blue_sBIT > 8) that->blue_sBIT = 8;
      if (that->alpha_sBIT > 8) that->alpha_sBIT = 8;
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_scale_16_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   return bit_depth > 8;
}

IT(scale_16);
#undef PT
#define PT ITSTRUCT(scale_16)
#endif /* PNG_READ_SCALE_16_TO_8_SUPPORTED (1.5.4 on) */

#ifdef PNG_READ_16_TO_8_SUPPORTED /* the default before 1.5.4 */
/* png_set_strip_16 */
static void
image_transform_png_set_strip_16_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_strip_16(pp);
#  if PNG_LIBPNG_VER < 10700
      /* libpng will limit the gamma table size: */
      that->max_gamma_8 = PNG_MAX_GAMMA_8;
#  endif
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_strip_16_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth == 16)
   {
      that->sample_depth = that->bit_depth = 8;
      if (that->red_sBIT > 8) that->red_sBIT = 8;
      if (that->green_sBIT > 8) that->green_sBIT = 8;
      if (that->blue_sBIT > 8) that->blue_sBIT = 8;
      if (that->alpha_sBIT > 8) that->alpha_sBIT = 8;

      /* Prior to 1.5.4 png_set_strip_16 would use an 'accurate' method if this
       * configuration option is set.  From 1.5.4 the flag is never set and the
       * 'scale' API (above) must be used.
       */
#     ifdef PNG_READ_ACCURATE_SCALE_SUPPORTED
#        if PNG_LIBPNG_VER >= 10504
#           error PNG_READ_ACCURATE_SCALE should not be set
#        endif

         /* The strip 16 algorithm drops the low 8 bits rather than calculating
          * 1/257, so we need to adjust the permitted errors appropriately:
          * Notice that this is only relevant prior to the addition of the
          * png_set_scale_16 API in 1.5.4 (but 1.5.4+ always defines the above!)
          */
         {
            const double d = (255-128.5)/65535;
            that->rede += d;
            that->greene += d;
            that->bluee += d;
            that->alphae += d;
         }
#     endif
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_strip_16_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   return bit_depth > 8;
}

IT(strip_16);
#undef PT
#define PT ITSTRUCT(strip_16)
#endif /* PNG_READ_16_TO_8_SUPPORTED */

#ifdef PNG_READ_STRIP_ALPHA_SUPPORTED
/* png_set_strip_alpha */
static void
image_transform_png_set_strip_alpha_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_strip_alpha(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_strip_alpha_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type == PNG_COLOR_TYPE_GRAY_ALPHA)
      that->colour_type = PNG_COLOR_TYPE_GRAY;
   else if (that->colour_type == PNG_COLOR_TYPE_RGB_ALPHA)
      that->colour_type = PNG_COLOR_TYPE_RGB;

   that->have_tRNS = 0;
   that->alphaf = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_strip_alpha_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return (colour_type & PNG_COLOR_MASK_ALPHA) != 0;
}

IT(strip_alpha);
#undef PT
#define PT ITSTRUCT(strip_alpha)
#endif /* PNG_READ_STRIP_ALPHA_SUPPORTED */

#ifdef PNG_READ_RGB_TO_GRAY_SUPPORTED
/* png_set_rgb_to_gray(png_structp, int err_action, double red, double green)
 * png_set_rgb_to_gray_fixed(png_structp, int err_action, png_fixed_point red,
 *    png_fixed_point green)
 * png_get_rgb_to_gray_status
 *
 * The 'default' test here uses values known to be used inside libpng prior to
 * 1.7.0:
 *
 *   red:    6968
 *   green: 23434
 *   blue:   2366
 *
 * These values are being retained for compatibility, along with the somewhat
 * broken truncation calculation in the fast-and-inaccurate code path.  Older
 * versions of libpng will fail the accuracy tests below because they use the
 * truncation algorithm everywhere.
 */
#define data ITDATA(rgb_to_gray)
static struct
{
   double gamma;      /* File gamma to use in processing */

   /* The following are the parameters for png_set_rgb_to_gray: */
#  ifdef PNG_FLOATING_POINT_SUPPORTED
      double red_to_set;
      double green_to_set;
#  else
      png_fixed_point red_to_set;
      png_fixed_point green_to_set;
#  endif

   /* The actual coefficients: */
   double red_coefficient;
   double green_coefficient;
   double blue_coefficient;

   /* Set if the coeefficients have been overridden. */
   int coefficients_overridden;
} data;

#undef image_transform_ini
#define image_transform_ini image_transform_png_set_rgb_to_gray_ini
static void
image_transform_png_set_rgb_to_gray_ini(const image_transform *this,
    transform_display *that)
{
   png_modifier *pm = that->pm;
   const color_encoding *e = pm->current_encoding;

   UNUSED(this)

   /* Since we check the encoding this flag must be set: */
   pm->test_uses_encoding = 1;

   /* If 'e' is not NULL chromaticity information is present and either a cHRM
    * or an sRGB chunk will be inserted.
    */
   if (e != 0)
   {
      /* Coefficients come from the encoding, but may need to be normalized to a
       * white point Y of 1.0
       */
      const double whiteY = e->red.Y + e->green.Y + e->blue.Y;

      data.red_coefficient = e->red.Y;
      data.green_coefficient = e->green.Y;
      data.blue_coefficient = e->blue.Y;

      if (whiteY != 1)
      {
         data.red_coefficient /= whiteY;
         data.green_coefficient /= whiteY;
         data.blue_coefficient /= whiteY;
      }
   }

   else
   {
      /* The default (built in) coeffcients, as above: */
#     if PNG_LIBPNG_VER < 10700
         data.red_coefficient = 6968 / 32768.;
         data.green_coefficient = 23434 / 32768.;
         data.blue_coefficient = 2366 / 32768.;
#     else
         data.red_coefficient = .2126;
         data.green_coefficient = .7152;
         data.blue_coefficient = .0722;
#     endif
   }

   data.gamma = pm->current_gamma;

   /* If not set then the calculations assume linear encoding (implicitly): */
   if (data.gamma == 0)
      data.gamma = 1;

   /* The arguments to png_set_rgb_to_gray can override the coefficients implied
    * by the color space encoding.  If doing exhaustive checks do the override
    * in each case, otherwise do it randomly.
    */
   if (pm->test_exhaustive)
   {
      /* First time in coefficients_overridden is 0, the following sets it to 1,
       * so repeat if it is set.  If a test fails this may mean we subsequently
       * skip a non-override test, ignore that.
       */
      data.coefficients_overridden = !data.coefficients_overridden;
      pm->repeat = data.coefficients_overridden != 0;
   }

   else
      data.coefficients_overridden = random_choice();

   if (data.coefficients_overridden)
   {
      /* These values override the color encoding defaults, simply use random
       * numbers.
       */
      png_uint_32 ru;
      double total;

      ru = random_u32();
      data.green_coefficient = total = (ru & 0xffff) / 65535.;
      ru >>= 16;
      data.red_coefficient = (1 - total) * (ru & 0xffff) / 65535.;
      total += data.red_coefficient;
      data.blue_coefficient = 1 - total;

#     ifdef PNG_FLOATING_POINT_SUPPORTED
         data.red_to_set = data.red_coefficient;
         data.green_to_set = data.green_coefficient;
#     else
         data.red_to_set = fix(data.red_coefficient);
         data.green_to_set = fix(data.green_coefficient);
#     endif

      /* The following just changes the error messages: */
      pm->encoding_ignored = 1;
   }

   else
   {
      data.red_to_set = -1;
      data.green_to_set = -1;
   }

   /* Adjust the error limit in the png_modifier because of the larger errors
    * produced in the digitization during the gamma handling.
    */
   if (data.gamma != 1) /* Use gamma tables */
   {
      if (that->this.bit_depth == 16 || pm->assume_16_bit_calculations)
      {
         /* The computations have the form:
          *
          *    r * rc + g * gc + b * bc
          *
          *  Each component of which is +/-1/65535 from the gamma_to_1 table
          *  lookup, resulting in a base error of +/-6.  The gamma_from_1
          *  conversion adds another +/-2 in the 16-bit case and
          *  +/-(1<<(15-PNG_MAX_GAMMA_8)) in the 8-bit case.
          */
#        if PNG_LIBPNG_VER < 10700
            if (that->this.bit_depth < 16)
               that->max_gamma_8 = PNG_MAX_GAMMA_8;
#        endif
         that->pm->limit += pow(
            (that->this.bit_depth == 16 || that->max_gamma_8 > 14 ?
               8. :
               6. + (1<<(15-that->max_gamma_8))
            )/65535, data.gamma);
      }

      else
      {
         /* Rounding to 8 bits in the linear space causes massive errors which
          * will trigger the error check in transform_range_check.  Fix that
          * here by taking the gamma encoding into account.
          *
          * When DIGITIZE is set because a pre-1.7 version of libpng is being
          * tested allow a bigger slack.
          *
          * NOTE: this number only affects the internal limit check in pngvalid,
          * it has no effect on the limits applied to the libpng values.
          */
#if DIGITIZE
          that->pm->limit += pow( 2.0/255, data.gamma);
#else
          that->pm->limit += pow( 1.0/255, data.gamma);
#endif
      }
   }

   else
   {
      /* With no gamma correction a large error comes from the truncation of the
       * calculation in the 8 bit case, allow for that here.
       */
      if (that->this.bit_depth != 16 && !pm->assume_16_bit_calculations)
         that->pm->limit += 4E-3;
   }
}

static void
image_transform_png_set_rgb_to_gray_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   int error_action = 1; /* no error, no defines in png.h */

#  ifdef PNG_FLOATING_POINT_SUPPORTED
      png_set_rgb_to_gray(pp, error_action, data.red_to_set, data.green_to_set);
#  else
      png_set_rgb_to_gray_fixed(pp, error_action, data.red_to_set,
         data.green_to_set);
#  endif

#  ifdef PNG_READ_cHRM_SUPPORTED
      if (that->pm->current_encoding != 0)
      {
         /* We have an encoding so a cHRM chunk may have been set; if so then
          * check that the libpng APIs give the correct (X,Y,Z) values within
          * some margin of error for the round trip through the chromaticity
          * form.
          */
#        ifdef PNG_FLOATING_POINT_SUPPORTED
#           define API_function png_get_cHRM_XYZ
#           define API_form "FP"
#           define API_type double
#           define API_cvt(x) (x)
#        else
#           define API_function png_get_cHRM_XYZ_fixed
#           define API_form "fixed"
#           define API_type png_fixed_point
#           define API_cvt(x) ((double)(x)/PNG_FP_1)
#        endif

         API_type rX, gX, bX;
         API_type rY, gY, bY;
         API_type rZ, gZ, bZ;

         if ((API_function(pp, pi, &rX, &rY, &rZ, &gX, &gY, &gZ, &bX, &bY, &bZ)
               & PNG_INFO_cHRM) != 0)
         {
            double maxe;
            const char *el;
            color_encoding e, o;

            /* Expect libpng to return a normalized result, but the original
             * color space encoding may not be normalized.
             */
            modifier_current_encoding(that->pm, &o);
            normalize_color_encoding(&o);

            /* Sanity check the pngvalid code - the coefficients should match
             * the normalized Y values of the encoding unless they were
             * overridden.
             */
            if (data.red_to_set == -1 && data.green_to_set == -1 &&
               (fabs(o.red.Y - data.red_coefficient) > DBL_EPSILON ||
               fabs(o.green.Y - data.green_coefficient) > DBL_EPSILON ||
               fabs(o.blue.Y - data.blue_coefficient) > DBL_EPSILON))
               png_error(pp, "internal pngvalid cHRM coefficient error");

            /* Generate a colour space encoding. */
            e.gamma = o.gamma; /* not used */
            e.red.X = API_cvt(rX);
            e.red.Y = API_cvt(rY);
            e.red.Z = API_cvt(rZ);
            e.green.X = API_cvt(gX);
            e.green.Y = API_cvt(gY);
            e.green.Z = API_cvt(gZ);
            e.blue.X = API_cvt(bX);
            e.blue.Y = API_cvt(bY);
            e.blue.Z = API_cvt(bZ);

            /* This should match the original one from the png_modifier, within
             * the range permitted by the libpng fixed point representation.
             */
            maxe = 0;
            el = "-"; /* Set to element name with error */

#           define CHECK(col,x)\
            {\
               double err = fabs(o.col.x - e.col.x);\
               if (err > maxe)\
               {\
                  maxe = err;\
                  el = #col "(" #x ")";\
               }\
            }

            CHECK(red,X)
            CHECK(red,Y)
            CHECK(red,Z)
            CHECK(green,X)
            CHECK(green,Y)
            CHECK(green,Z)
            CHECK(blue,X)
            CHECK(blue,Y)
            CHECK(blue,Z)

            /* Here in both fixed and floating cases to check the values read
             * from the cHRm chunk.  PNG uses fixed point in the cHRM chunk, so
             * we can't expect better than +/-.5E-5 on the result, allow 1E-5.
             */
            if (maxe >= 1E-5)
            {
               size_t pos = 0;
               char buffer[256];

               pos = safecat(buffer, sizeof buffer, pos, API_form);
               pos = safecat(buffer, sizeof buffer, pos, " cHRM ");
               pos = safecat(buffer, sizeof buffer, pos, el);
               pos = safecat(buffer, sizeof buffer, pos, " error: ");
               pos = safecatd(buffer, sizeof buffer, pos, maxe, 7);
               pos = safecat(buffer, sizeof buffer, pos, " ");
               /* Print the color space without the gamma value: */
               pos = safecat_color_encoding(buffer, sizeof buffer, pos, &o, 0);
               pos = safecat(buffer, sizeof buffer, pos, " -> ");
               pos = safecat_color_encoding(buffer, sizeof buffer, pos, &e, 0);

               png_error(pp, buffer);
            }
         }
      }
#  endif /* READ_cHRM */

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_rgb_to_gray_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if ((that->colour_type & PNG_COLOR_MASK_COLOR) != 0)
   {
      double gray, err;

#     if PNG_LIBPNG_VER < 10700
         if (that->colour_type == PNG_COLOR_TYPE_PALETTE)
            image_pixel_convert_PLTE(that);
#     endif

      /* Image now has RGB channels... */
#  if DIGITIZE
      {
         png_modifier *pm = display->pm;
         unsigned int sample_depth = that->sample_depth;
         unsigned int calc_depth = (pm->assume_16_bit_calculations ? 16 :
            sample_depth);
         unsigned int gamma_depth =
            (sample_depth == 16 ?
               display->max_gamma_8 :
               (pm->assume_16_bit_calculations ?
                  display->max_gamma_8 :
                  sample_depth));
         int isgray;
         double r, g, b;
         double rlo, rhi, glo, ghi, blo, bhi, graylo, grayhi;

         /* Do this using interval arithmetic, otherwise it is too difficult to
          * handle the errors correctly.
          *
          * To handle the gamma correction work out the upper and lower bounds
          * of the digitized value.  Assume rounding here - normally the values
          * will be identical after this operation if there is only one
          * transform, feel free to delete the png_error checks on this below in
          * the future (this is just me trying to ensure it works!)
          *
          * Interval arithmetic is exact, but to implement it it must be
          * possible to control the floating point implementation rounding mode.
          * This cannot be done in ANSI-C, so instead I reduce the 'lo' values
          * by DBL_EPSILON and increase the 'hi' values by the same.
          */
#        define DD(v,d,r) (digitize(v*(1-DBL_EPSILON), d, r) * (1-DBL_EPSILON))
#        define DU(v,d,r) (digitize(v*(1+DBL_EPSILON), d, r) * (1+DBL_EPSILON))

         r = rlo = rhi = that->redf;
         rlo -= that->rede;
         rlo = DD(rlo, calc_depth, 1/*round*/);
         rhi += that->rede;
         rhi = DU(rhi, calc_depth, 1/*round*/);

         g = glo = ghi = that->greenf;
         glo -= that->greene;
         glo = DD(glo, calc_depth, 1/*round*/);
         ghi += that->greene;
         ghi = DU(ghi, calc_depth, 1/*round*/);

         b = blo = bhi = that->bluef;
         blo -= that->bluee;
         blo = DD(blo, calc_depth, 1/*round*/);
         bhi += that->bluee;
         bhi = DU(bhi, calc_depth, 1/*round*/);

         isgray = r==g && g==b;

         if (data.gamma != 1)
         {
            const double power = 1/data.gamma;
            const double abse = .5/(sample_depth == 16 ? 65535 : 255);

            /* If a gamma calculation is done it is done using lookup tables of
             * precision gamma_depth, so the already digitized value above may
             * need to be further digitized here.
             */
            if (gamma_depth != calc_depth)
            {
               rlo = DD(rlo, gamma_depth, 0/*truncate*/);
               rhi = DU(rhi, gamma_depth, 0/*truncate*/);
               glo = DD(glo, gamma_depth, 0/*truncate*/);
               ghi = DU(ghi, gamma_depth, 0/*truncate*/);
               blo = DD(blo, gamma_depth, 0/*truncate*/);
               bhi = DU(bhi, gamma_depth, 0/*truncate*/);
            }

            /* 'abse' is the error in the gamma table calculation itself. */
            r = pow(r, power);
            rlo = DD(pow(rlo, power)-abse, calc_depth, 1);
            rhi = DU(pow(rhi, power)+abse, calc_depth, 1);

            g = pow(g, power);
            glo = DD(pow(glo, power)-abse, calc_depth, 1);
            ghi = DU(pow(ghi, power)+abse, calc_depth, 1);

            b = pow(b, power);
            blo = DD(pow(blo, power)-abse, calc_depth, 1);
            bhi = DU(pow(bhi, power)+abse, calc_depth, 1);
         }

         /* Now calculate the actual gray values.  Although the error in the
          * coefficients depends on whether they were specified on the command
          * line (in which case truncation to 15 bits happened) or not (rounding
          * was used) the maximum error in an individual coefficient is always
          * 2/32768, because even in the rounding case the requirement that
          * coefficients add up to 32768 can cause a larger rounding error.
          *
          * The only time when rounding doesn't occur in 1.5.5 and later is when
          * the non-gamma code path is used for less than 16 bit data.
          */
         gray = r * data.red_coefficient + g * data.green_coefficient +
            b * data.blue_coefficient;

         {
            int do_round = data.gamma != 1 || calc_depth == 16;
            const double ce = 2. / 32768;

            graylo = DD(rlo * (data.red_coefficient-ce) +
               glo * (data.green_coefficient-ce) +
               blo * (data.blue_coefficient-ce), calc_depth, do_round);
            if (graylo > gray) /* always accept the right answer */
               graylo = gray;

            grayhi = DU(rhi * (data.red_coefficient+ce) +
               ghi * (data.green_coefficient+ce) +
               bhi * (data.blue_coefficient+ce), calc_depth, do_round);
            if (grayhi < gray)
               grayhi = gray;
         }

         /* And invert the gamma. */
         if (data.gamma != 1)
         {
            const double power = data.gamma;

            /* And this happens yet again, shifting the values once more. */
            if (gamma_depth != sample_depth)
            {
               rlo = DD(rlo, gamma_depth, 0/*truncate*/);
               rhi = DU(rhi, gamma_depth, 0/*truncate*/);
               glo = DD(glo, gamma_depth, 0/*truncate*/);
               ghi = DU(ghi, gamma_depth, 0/*truncate*/);
               blo = DD(blo, gamma_depth, 0/*truncate*/);
               bhi = DU(bhi, gamma_depth, 0/*truncate*/);
            }

            gray = pow(gray, power);
            graylo = DD(pow(graylo, power), sample_depth, 1);
            grayhi = DU(pow(grayhi, power), sample_depth, 1);
         }

#        undef DD
#        undef DU

         /* Now the error can be calculated.
          *
          * If r==g==b because there is no overall gamma correction libpng
          * currently preserves the original value.
          */
         if (isgray)
            err = (that->rede + that->greene + that->bluee)/3;

         else
         {
            err = fabs(grayhi-gray);

            if (fabs(gray - graylo) > err)
               err = fabs(graylo-gray);

#if !RELEASE_BUILD
            /* Check that this worked: */
            if (err > pm->limit)
            {
               size_t pos = 0;
               char buffer[128];

               pos = safecat(buffer, sizeof buffer, pos, "rgb_to_gray error ");
               pos = safecatd(buffer, sizeof buffer, pos, err, 6);
               pos = safecat(buffer, sizeof buffer, pos, " exceeds limit ");
               pos = safecatd(buffer, sizeof buffer, pos, pm->limit, 6);
               png_warning(pp, buffer);
               pm->limit = err;
            }
#endif /* !RELEASE_BUILD */
         }
      }
#  else  /* !DIGITIZE */
      {
         double r = that->redf;
         double re = that->rede;
         double g = that->greenf;
         double ge = that->greene;
         double b = that->bluef;
         double be = that->bluee;

#        if PNG_LIBPNG_VER < 10700
            /* The true gray case involves no math in earlier versions (not
             * true, there was some if gamma correction was happening too.)
             */
            if (r == g && r == b)
            {
               gray = r;
               err = re;
               if (err < ge) err = ge;
               if (err < be) err = be;
            }

            else
#        endif /* before 1.7 */
         if (data.gamma == 1)
         {
            /* There is no need to do the conversions to and from linear space,
             * so the calculation should be a lot more accurate.  There is a
             * built in error in the coefficients because they only have 15 bits
             * and are adjusted to make sure they add up to 32768.  This
             * involves a integer calculation with truncation of the form:
             *
             *     ((int)(coefficient * 100000) * 32768)/100000
             *
             * This is done to the red and green coefficients (the ones
             * provided to the API) then blue is calculated from them so the
             * result adds up to 32768.  In the worst case this can result in
             * a -1 error in red and green and a +2 error in blue.  Consequently
             * the worst case in the calculation below is 2/32768 error.
             *
             * TODO: consider fixing this in libpng by rounding the calculation
             * limiting the error to 1/32768.
             *
             * Handling this by adding 2/32768 here avoids needing to increase
             * the global error limits to take this into account.)
             */
            gray = r * data.red_coefficient + g * data.green_coefficient +
               b * data.blue_coefficient;
            err = re * data.red_coefficient + ge * data.green_coefficient +
               be * data.blue_coefficient + 2./32768 + gray * 5 * DBL_EPSILON;
         }

         else
         {
            /* The calculation happens in linear space, and this produces much
             * wider errors in the encoded space.  These are handled here by
             * factoring the errors in to the calculation.  There are two table
             * lookups in the calculation and each introduces a quantization
             * error defined by the table size.
             */
            png_modifier *pm = display->pm;
            double in_qe = (that->sample_depth > 8 ? .5/65535 : .5/255);
            double out_qe = (that->sample_depth > 8 ? .5/65535 :
               (pm->assume_16_bit_calculations ? .5/(1<<display->max_gamma_8) :
               .5/255));
            double rhi, ghi, bhi, grayhi;
            double g1 = 1/data.gamma;

            rhi = r + re + in_qe; if (rhi > 1) rhi = 1;
            r -= re + in_qe; if (r < 0) r = 0;
            ghi = g + ge + in_qe; if (ghi > 1) ghi = 1;
            g -= ge + in_qe; if (g < 0) g = 0;
            bhi = b + be + in_qe; if (bhi > 1) bhi = 1;
            b -= be + in_qe; if (b < 0) b = 0;

            r = pow(r, g1)*(1-DBL_EPSILON); rhi = pow(rhi, g1)*(1+DBL_EPSILON);
            g = pow(g, g1)*(1-DBL_EPSILON); ghi = pow(ghi, g1)*(1+DBL_EPSILON);
            b = pow(b, g1)*(1-DBL_EPSILON); bhi = pow(bhi, g1)*(1+DBL_EPSILON);

            /* Work out the lower and upper bounds for the gray value in the
             * encoded space, then work out an average and error.  Remove the
             * previously added input quantization error at this point.
             */
            gray = r * data.red_coefficient + g * data.green_coefficient +
               b * data.blue_coefficient - 2./32768 - out_qe;
            if (gray <= 0)
               gray = 0;
            else
            {
               gray *= (1 - 6 * DBL_EPSILON);
               gray = pow(gray, data.gamma) * (1-DBL_EPSILON);
            }

            grayhi = rhi * data.red_coefficient + ghi * data.green_coefficient +
               bhi * data.blue_coefficient + 2./32768 + out_qe;
            grayhi *= (1 + 6 * DBL_EPSILON);
            if (grayhi >= 1)
               grayhi = 1;
            else
               grayhi = pow(grayhi, data.gamma) * (1+DBL_EPSILON);

            err = (grayhi - gray) / 2;
            gray = (grayhi + gray) / 2;

            if (err <= in_qe)
               err = gray * DBL_EPSILON;

            else
               err -= in_qe;

#if !RELEASE_BUILD
            /* Validate that the error is within limits (this has caused
             * problems before, it's much easier to detect them here.)
             */
            if (err > pm->limit)
            {
               size_t pos = 0;
               char buffer[128];

               pos = safecat(buffer, sizeof buffer, pos, "rgb_to_gray error ");
               pos = safecatd(buffer, sizeof buffer, pos, err, 6);
               pos = safecat(buffer, sizeof buffer, pos, " exceeds limit ");
               pos = safecatd(buffer, sizeof buffer, pos, pm->limit, 6);
               png_warning(pp, buffer);
               pm->limit = err;
            }
#endif /* !RELEASE_BUILD */
         }
      }
#  endif /* !DIGITIZE */

      that->bluef = that->greenf = that->redf = gray;
      that->bluee = that->greene = that->rede = err;

      /* The sBIT is the minimum of the three colour channel sBITs. */
      if (that->red_sBIT > that->green_sBIT)
         that->red_sBIT = that->green_sBIT;
      if (that->red_sBIT > that->blue_sBIT)
         that->red_sBIT = that->blue_sBIT;
      that->blue_sBIT = that->green_sBIT = that->red_sBIT;

      /* And remove the colour bit in the type: */
      if (that->colour_type == PNG_COLOR_TYPE_RGB)
         that->colour_type = PNG_COLOR_TYPE_GRAY;
      else if (that->colour_type == PNG_COLOR_TYPE_RGB_ALPHA)
         that->colour_type = PNG_COLOR_TYPE_GRAY_ALPHA;
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_rgb_to_gray_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return (colour_type & PNG_COLOR_MASK_COLOR) != 0;
}

#undef data
IT(rgb_to_gray);
#undef PT
#define PT ITSTRUCT(rgb_to_gray)
#undef image_transform_ini
#define image_transform_ini image_transform_default_ini
#endif /* PNG_READ_RGB_TO_GRAY_SUPPORTED */

#ifdef PNG_READ_BACKGROUND_SUPPORTED
/* png_set_background(png_structp, png_const_color_16p background_color,
 *    int background_gamma_code, int need_expand, double background_gamma)
 * png_set_background_fixed(png_structp, png_const_color_16p background_color,
 *    int background_gamma_code, int need_expand,
 *    png_fixed_point background_gamma)
 *
 * This ignores the gamma (at present.)
*/
#define data ITDATA(background)
static image_pixel data;

static void
image_transform_png_set_background_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_byte colour_type, bit_depth;
   png_byte random_bytes[8]; /* 8 bytes - 64 bits - the biggest pixel */
   int expand;
   png_color_16 back;

   /* We need a background colour, because we don't know exactly what transforms
    * have been set we have to supply the colour in the original file format and
    * so we need to know what that is!  The background colour is stored in the
    * transform_display.
    */
   R8(random_bytes);

   /* Read the random value, for colour type 3 the background colour is actually
    * expressed as a 24bit rgb, not an index.
    */
   colour_type = that->this.colour_type;
   if (colour_type == 3)
   {
      colour_type = PNG_COLOR_TYPE_RGB;
      bit_depth = 8;
      expand = 0; /* passing in an RGB not a pixel index */
   }

   else
   {
      if (that->this.has_tRNS)
         that->this.is_transparent = 1;

      bit_depth = that->this.bit_depth;
      expand = 1;
   }

   image_pixel_init(&data, random_bytes, colour_type,
      bit_depth, 0/*x*/, 0/*unused: palette*/, NULL/*format*/);

   /* Extract the background colour from this image_pixel, but make sure the
    * unused fields of 'back' are garbage.
    */
   R8(back);

   if (colour_type & PNG_COLOR_MASK_COLOR)
   {
      back.red = (png_uint_16)data.red;
      back.green = (png_uint_16)data.green;
      back.blue = (png_uint_16)data.blue;
   }

   else
      back.gray = (png_uint_16)data.red;

#ifdef PNG_FLOATING_POINT_SUPPORTED
   png_set_background(pp, &back, PNG_BACKGROUND_GAMMA_FILE, expand, 0);
#else
   png_set_background_fixed(pp, &back, PNG_BACKGROUND_GAMMA_FILE, expand, 0);
#endif

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_background_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* Check for tRNS first: */
   if (that->have_tRNS && that->colour_type != PNG_COLOR_TYPE_PALETTE)
      image_pixel_add_alpha(that, &display->this, 1/*for background*/);

   /* This is only necessary if the alpha value is less than 1. */
   if (that->alphaf < 1)
   {
      /* Now we do the background calculation without any gamma correction. */
      if (that->alphaf <= 0)
      {
         that->redf = data.redf;
         that->greenf = data.greenf;
         that->bluef = data.bluef;

         that->rede = data.rede;
         that->greene = data.greene;
         that->bluee = data.bluee;

         that->red_sBIT= data.red_sBIT;
         that->green_sBIT= data.green_sBIT;
         that->blue_sBIT= data.blue_sBIT;
      }

      else /* 0 < alpha < 1 */
      {
         double alf = 1 - that->alphaf;

         that->redf = that->redf * that->alphaf + data.redf * alf;
         that->rede = that->rede * that->alphaf + data.rede * alf +
            DBL_EPSILON;
         that->greenf = that->greenf * that->alphaf + data.greenf * alf;
         that->greene = that->greene * that->alphaf + data.greene * alf +
            DBL_EPSILON;
         that->bluef = that->bluef * that->alphaf + data.bluef * alf;
         that->bluee = that->bluee * that->alphaf + data.bluee * alf +
            DBL_EPSILON;
      }

      /* Remove the alpha type and set the alpha (not in that order.) */
      that->alphaf = 1;
      that->alphae = 0;
   }

   if (that->colour_type == PNG_COLOR_TYPE_RGB_ALPHA)
      that->colour_type = PNG_COLOR_TYPE_RGB;
   else if (that->colour_type == PNG_COLOR_TYPE_GRAY_ALPHA)
      that->colour_type = PNG_COLOR_TYPE_GRAY;
   /* PNG_COLOR_TYPE_PALETTE is not changed */

   this->next->mod(this->next, that, pp, display);
}

#define image_transform_png_set_background_add image_transform_default_add

#undef data
IT(background);
#undef PT
#define PT ITSTRUCT(background)
#endif /* PNG_READ_BACKGROUND_SUPPORTED */

/* png_set_quantize(png_structp, png_colorp palette, int num_palette,
 *    int maximum_colors, png_const_uint_16p histogram, int full_quantize)
 *
 * Very difficult to validate this!
 */
/*NOTE: TBD NYI */

/* The data layout transforms are handled by swapping our own channel data,
 * necessarily these need to happen at the end of the transform list because the
 * semantic of the channels changes after these are executed.  Some of these,
 * like set_shift and set_packing, can't be done at present because they change
 * the layout of the data at the sub-sample level so sample() won't get the
 * right answer.
 */
/* png_set_invert_alpha */
#ifdef PNG_READ_INVERT_ALPHA_SUPPORTED
/* Invert the alpha channel
 *
 *  png_set_invert_alpha(png_structrp png_ptr)
 */
static void
image_transform_png_set_invert_alpha_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_invert_alpha(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_invert_alpha_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type & 4)
      that->alpha_inverted = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_invert_alpha_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   /* Only has an effect on pixels with alpha: */
   return (colour_type & 4) != 0;
}

IT(invert_alpha);
#undef PT
#define PT ITSTRUCT(invert_alpha)

#endif /* PNG_READ_INVERT_ALPHA_SUPPORTED */

/* png_set_bgr */
#ifdef PNG_READ_BGR_SUPPORTED
/* Swap R,G,B channels to order B,G,R.
 *
 *  png_set_bgr(png_structrp png_ptr)
 *
 * This only has an effect on RGB and RGBA pixels.
 */
static void
image_transform_png_set_bgr_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_bgr(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_bgr_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type == PNG_COLOR_TYPE_RGB ||
       that->colour_type == PNG_COLOR_TYPE_RGBA)
       that->swap_rgb = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_bgr_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return colour_type == PNG_COLOR_TYPE_RGB ||
       colour_type == PNG_COLOR_TYPE_RGBA;
}

IT(bgr);
#undef PT
#define PT ITSTRUCT(bgr)

#endif /* PNG_READ_BGR_SUPPORTED */

/* png_set_swap_alpha */
#ifdef PNG_READ_SWAP_ALPHA_SUPPORTED
/* Put the alpha channel first.
 *
 *  png_set_swap_alpha(png_structrp png_ptr)
 *
 * This only has an effect on GA and RGBA pixels.
 */
static void
image_transform_png_set_swap_alpha_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_swap_alpha(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_swap_alpha_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type == PNG_COLOR_TYPE_GA ||
       that->colour_type == PNG_COLOR_TYPE_RGBA)
      that->alpha_first = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_swap_alpha_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return colour_type == PNG_COLOR_TYPE_GA ||
       colour_type == PNG_COLOR_TYPE_RGBA;
}

IT(swap_alpha);
#undef PT
#define PT ITSTRUCT(swap_alpha)

#endif /* PNG_READ_SWAP_ALPHA_SUPPORTED */

/* png_set_swap */
#ifdef PNG_READ_SWAP_SUPPORTED
/* Byte swap 16-bit components.
 *
 *  png_set_swap(png_structrp png_ptr)
 */
static void
image_transform_png_set_swap_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_swap(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_swap_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth == 16)
      that->swap16 = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_swap_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   return bit_depth == 16;
}

IT(swap);
#undef PT
#define PT ITSTRUCT(swap)

#endif /* PNG_READ_SWAP_SUPPORTED */

#ifdef PNG_READ_FILLER_SUPPORTED
/* Add a filler byte to 8-bit Gray or 24-bit RGB images.
 *
 *  png_set_filler, (png_structp png_ptr, png_uint_32 filler, int flags));
 *
 * Flags:
 *
 *  PNG_FILLER_BEFORE
 *  PNG_FILLER_AFTER
 */
#define data ITDATA(filler)
static struct
{
   png_uint_32 filler;
   int         flags;
} data;

static void
image_transform_png_set_filler_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   /* Need a random choice for 'before' and 'after' as well as for the
    * filler.  The 'filler' value has all 32 bits set, but only bit_depth
    * will be used.  At this point we don't know bit_depth.
    */
   data.filler = random_u32();
   data.flags = random_choice();

   png_set_filler(pp, data.filler, data.flags);

   /* The standard display handling stuff also needs to know that
    * there is a filler, so set that here.
    */
   that->this.filler = 1;

   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_filler_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth >= 8 &&
       (that->colour_type == PNG_COLOR_TYPE_RGB ||
        that->colour_type == PNG_COLOR_TYPE_GRAY))
   {
      unsigned int max = (1U << that->bit_depth)-1;
      that->alpha = data.filler & max;
      that->alphaf = ((double)that->alpha) / max;
      that->alphae = 0;

      /* The filler has been stored in the alpha channel, we must record
       * that this has been done for the checking later on, the color
       * type is faked to have an alpha channel, but libpng won't report
       * this; the app has to know the extra channel is there and this
       * was recording in standard_display::filler above.
       */
      that->colour_type |= 4; /* alpha added */
      that->alpha_first = data.flags == PNG_FILLER_BEFORE;
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_filler_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   this->next = *that;
   *that = this;

   return bit_depth >= 8 && (colour_type == PNG_COLOR_TYPE_RGB ||
           colour_type == PNG_COLOR_TYPE_GRAY);
}

#undef data
IT(filler);
#undef PT
#define PT ITSTRUCT(filler)

/* png_set_add_alpha, (png_structp png_ptr, png_uint_32 filler, int flags)); */
/* Add an alpha byte to 8-bit Gray or 24-bit RGB images. */
#define data ITDATA(add_alpha)
static struct
{
   png_uint_32 filler;
   int         flags;
} data;

static void
image_transform_png_set_add_alpha_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   /* Need a random choice for 'before' and 'after' as well as for the
    * filler.  The 'filler' value has all 32 bits set, but only bit_depth
    * will be used.  At this point we don't know bit_depth.
    */
   data.filler = random_u32();
   data.flags = random_choice();

   png_set_add_alpha(pp, data.filler, data.flags);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_add_alpha_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth >= 8 &&
       (that->colour_type == PNG_COLOR_TYPE_RGB ||
        that->colour_type == PNG_COLOR_TYPE_GRAY))
   {
      unsigned int max = (1U << that->bit_depth)-1;
      that->alpha = data.filler & max;
      that->alphaf = ((double)that->alpha) / max;
      that->alphae = 0;

      that->colour_type |= 4; /* alpha added */
      that->alpha_first = data.flags == PNG_FILLER_BEFORE;
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_add_alpha_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   this->next = *that;
   *that = this;

   return bit_depth >= 8 && (colour_type == PNG_COLOR_TYPE_RGB ||
           colour_type == PNG_COLOR_TYPE_GRAY);
}

#undef data
IT(add_alpha);
#undef PT
#define PT ITSTRUCT(add_alpha)

#endif /* PNG_READ_FILLER_SUPPORTED */

/* png_set_packing */
#ifdef PNG_READ_PACK_SUPPORTED
/* Use 1 byte per pixel in 1, 2, or 4-bit depth files.
 *
 *  png_set_packing(png_structrp png_ptr)
 *
 * This should only affect grayscale and palette images with less than 8 bits
 * per pixel.
 */
static void
image_transform_png_set_packing_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_packing(pp);
   that->unpacked = 1;
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_packing_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* The general expand case depends on what the colour type is,
    * low bit-depth pixel values are unpacked into bytes without
    * scaling, so sample_depth is not changed.
    */
   if (that->bit_depth < 8) /* grayscale or palette */
      that->bit_depth = 8;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_packing_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   /* Nothing should happen unless the bit depth is less than 8: */
   return bit_depth < 8;
}

IT(packing);
#undef PT
#define PT ITSTRUCT(packing)

#endif /* PNG_READ_PACK_SUPPORTED */

/* png_set_packswap */
#ifdef PNG_READ_PACKSWAP_SUPPORTED
/* Swap pixels packed into bytes; reverses the order on screen so that
 * the high order bits correspond to the rightmost pixels.
 *
 *  png_set_packswap(png_structrp png_ptr)
 */
static void
image_transform_png_set_packswap_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_packswap(pp);
   that->this.littleendian = 1;
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_packswap_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->bit_depth < 8)
      that->littleendian = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_packswap_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(colour_type)

   this->next = *that;
   *that = this;

   return bit_depth < 8;
}

IT(packswap);
#undef PT
#define PT ITSTRUCT(packswap)

#endif /* PNG_READ_PACKSWAP_SUPPORTED */


/* png_set_invert_mono */
#ifdef PNG_READ_INVERT_MONO_SUPPORTED
/* Invert the gray channel
 *
 *  png_set_invert_mono(png_structrp png_ptr)
 */
static void
image_transform_png_set_invert_mono_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_invert_mono(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_invert_mono_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   if (that->colour_type & 4)
      that->mono_inverted = 1;

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_invert_mono_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   /* Only has an effect on pixels with no colour: */
   return (colour_type & 2) == 0;
}

IT(invert_mono);
#undef PT
#define PT ITSTRUCT(invert_mono)

#endif /* PNG_READ_INVERT_MONO_SUPPORTED */

#ifdef PNG_READ_SHIFT_SUPPORTED
/* png_set_shift(png_structp, png_const_color_8p true_bits)
 *
 * The output pixels will be shifted by the given true_bits
 * values.
 */
#define data ITDATA(shift)
static png_color_8 data;

static void
image_transform_png_set_shift_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   /* Get a random set of shifts.  The shifts need to do something
    * to test the transform, so they are limited to the bit depth
    * of the input image.  Notice that in the following the 'gray'
    * field is randomized independently.  This acts as a check that
    * libpng does use the correct field.
    */
   unsigned int depth = that->this.bit_depth;

   data.red = (png_byte)/*SAFE*/(random_mod(depth)+1);
   data.green = (png_byte)/*SAFE*/(random_mod(depth)+1);
   data.blue = (png_byte)/*SAFE*/(random_mod(depth)+1);
   data.gray = (png_byte)/*SAFE*/(random_mod(depth)+1);
   data.alpha = (png_byte)/*SAFE*/(random_mod(depth)+1);

   png_set_shift(pp, &data);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_shift_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   /* Copy the correct values into the sBIT fields, libpng does not do
    * anything to palette data:
    */
   if (that->colour_type != PNG_COLOR_TYPE_PALETTE)
   {
       that->sig_bits = 1;

       /* The sBIT fields are reset to the values previously sent to
        * png_set_shift according to the colour type.
        * does.
        */
       if (that->colour_type & 2) /* RGB channels */
       {
          that->red_sBIT = data.red;
          that->green_sBIT = data.green;
          that->blue_sBIT = data.blue;
       }

       else /* One grey channel */
          that->red_sBIT = that->green_sBIT = that->blue_sBIT = data.gray;

       that->alpha_sBIT = data.alpha;
   }

   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_shift_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   UNUSED(bit_depth)

   this->next = *that;
   *that = this;

   return colour_type != PNG_COLOR_TYPE_PALETTE;
}

IT(shift);
#undef PT
#define PT ITSTRUCT(shift)

#endif /* PNG_READ_SHIFT_SUPPORTED */

#ifdef THIS_IS_THE_PROFORMA
static void
image_transform_png_set_@_set(const image_transform *this,
    transform_display *that, png_structp pp, png_infop pi)
{
   png_set_@(pp);
   this->next->set(this->next, that, pp, pi);
}

static void
image_transform_png_set_@_mod(const image_transform *this,
    image_pixel *that, png_const_structp pp,
    const transform_display *display)
{
   this->next->mod(this->next, that, pp, display);
}

static int
image_transform_png_set_@_add(image_transform *this,
    const image_transform **that, png_byte colour_type, png_byte bit_depth)
{
   this->next = *that;
   *that = this;

   return 1;
}

IT(@);
#endif


/* This may just be 'end' if all the transforms are disabled! */
static image_transform *const image_transform_first = &PT;

static void
transform_enable(const char *name)
{
   /* Everything starts out enabled, so if we see an 'enable' disabled
    * everything else the first time round.
    */
   static int all_disabled = 0;
   int found_it = 0;
   image_transform *list = image_transform_first;

   while (list != &image_transform_end)
   {
      if (strcmp(list->name, name) == 0)
      {
         list->enable = 1;
         found_it = 1;
      }
      else if (!all_disabled)
         list->enable = 0;

      list = list->list;
   }

   all_disabled = 1;

   if (!found_it)
   {
      fprintf(stderr, "pngvalid: --transform-enable=%s: unknown transform\n",
         name);
      exit(99);
   }
}

static void
transform_disable(const char *name)
{
   image_transform *list = image_transform_first;

   while (list != &image_transform_end)
   {
      if (strcmp(list->name, name) == 0)
      {
         list->enable = 0;
         return;
      }

      list = list->list;
   }

   fprintf(stderr, "pngvalid: --transform-disable=%s: unknown transform\n",
      name);
   exit(99);
}

static void
image_transform_reset_count(void)
{
   image_transform *next = image_transform_first;
   int count = 0;

   while (next != &image_transform_end)
   {
      next->local_use = 0;
      next->next = 0;
      next = next->list;
      ++count;
   }

   /* This can only happen if we every have more than 32 transforms (excluding
    * the end) in the list.
    */
   if (count > 32) abort();
}

static int
image_transform_test_counter(png_uint_32 counter, unsigned int max)
{
   /* Test the list to see if there is any point contining, given a current
    * counter and a 'max' value.
    */
   image_transform *next = image_transform_first;

   while (next != &image_transform_end)
   {
      /* For max 0 or 1 continue until the counter overflows: */
      counter >>= 1;

      /* Continue if any entry hasn't reacked the max. */
      if (max > 1 && next->local_use < max)
         return 1;
      next = next->list;
   }

   return max <= 1 && counter == 0;
}

static png_uint_32
image_transform_add(const image_transform **this, unsigned int max,
   png_uint_32 counter, char *name, size_t sizeof_name, size_t *pos,
   png_byte colour_type, png_byte bit_depth)
{
   for (;;) /* until we manage to add something */
   {
      png_uint_32 mask;
      image_transform *list;

      /* Find the next counter value, if the counter is zero this is the start
       * of the list.  This routine always returns the current counter (not the
       * next) so it returns 0 at the end and expects 0 at the beginning.
       */
      if (counter == 0) /* first time */
      {
         image_transform_reset_count();
         if (max <= 1)
            counter = 1;
         else
            counter = random_32();
      }
      else /* advance the counter */
      {
         switch (max)
         {
            case 0:  ++counter; break;
            case 1:  counter <<= 1; break;
            default: counter = random_32(); break;
         }
      }

      /* Now add all these items, if possible */
      *this = &image_transform_end;
      list = image_transform_first;
      mask = 1;

      /* Go through the whole list adding anything that the counter selects: */
      while (list != &image_transform_end)
      {
         if ((counter & mask) != 0 && list->enable &&
             (max == 0 || list->local_use < max))
         {
            /* Candidate to add: */
            if (list->add(list, this, colour_type, bit_depth) || max == 0)
            {
               /* Added, so add to the name too. */
               *pos = safecat(name, sizeof_name, *pos, " +");
               *pos = safecat(name, sizeof_name, *pos, list->name);
            }

            else
            {
               /* Not useful and max>0, so remove it from *this: */
               *this = list->next;
               list->next = 0;

               /* And, since we know it isn't useful, stop it being added again
                * in this run:
                */
               list->local_use = max;
            }
         }

         mask <<= 1;
         list = list->list;
      }

      /* Now if anything was added we have something to do. */
      if (*this != &image_transform_end)
         return counter;

      /* Nothing added, but was there anything in there to add? */
      if (!image_transform_test_counter(counter, max))
         return 0;
   }
}

static void
perform_transform_test(png_modifier *pm)
{
   png_byte colour_type = 0;
   png_byte bit_depth = 0;
   unsigned int palette_number = 0;

   while (next_format(&colour_type, &bit_depth, &palette_number, pm->test_lbg,
            pm->test_tRNS))
   {
      png_uint_32 counter = 0;
      size_t base_pos;
      char name[64];

      base_pos = safecat(name, sizeof name, 0, "transform:");

      for (;;)
      {
         size_t pos = base_pos;
         const image_transform *list = 0;

         /* 'max' is currently hardwired to '1'; this should be settable on the
          * command line.
          */
         counter = image_transform_add(&list, 1/*max*/, counter,
            name, sizeof name, &pos, colour_type, bit_depth);

         if (counter == 0)
            break;

         /* The command line can change this to checking interlaced images. */
         do
         {
            pm->repeat = 0;
            transform_test(pm, FILEID(colour_type, bit_depth, palette_number,
               pm->interlace_type, 0, 0, 0), list, name);

            if (fail(pm))
               return;
         }
         while (pm->repeat);
      }
   }
}
#endif /* PNG_READ_TRANSFORMS_SUPPORTED */

/********************************* GAMMA TESTS ********************************/
#ifdef PNG_READ_GAMMA_SUPPORTED
/* Reader callbacks and implementations, where they differ from the standard
 * ones.
 */
typedef struct gamma_display
{
   standard_display this;

   /* Parameters */
   png_modifier*    pm;
   double           file_gamma;
   double           screen_gamma;
   double           background_gamma;
   png_byte         sbit;
   int              threshold_test;
   int              use_input_precision;
   int              scale16;
   int              expand16;
   int              do_background;
   png_color_16     background_color;

   /* Local variables */
   double       maxerrout;
   double       maxerrpc;
   double       maxerrabs;
} gamma_display;

#define ALPHA_MODE_OFFSET 4

static void
gamma_display_init(gamma_display *dp, png_modifier *pm, png_uint_32 id,
    double file_gamma, double screen_gamma, png_byte sbit, int threshold_test,
    int use_input_precision, int scale16, int expand16,
    int do_background, const png_color_16 *pointer_to_the_background_color,
    double background_gamma)
{
   /* Standard fields */
   standard_display_init(&dp->this, &pm->this, id, do_read_interlace,
      pm->use_update_info);

   /* Parameter fields */
   dp->pm = pm;
   dp->file_gamma = file_gamma;
   dp->screen_gamma = screen_gamma;
   dp->background_gamma = background_gamma;
   dp->sbit = sbit;
   dp->threshold_test = threshold_test;
   dp->use_input_precision = use_input_precision;
   dp->scale16 = scale16;
   dp->expand16 = expand16;
   dp->do_background = do_background;
   if (do_background && pointer_to_the_background_color != 0)
      dp->background_color = *pointer_to_the_background_color;
   else
      memset(&dp->background_color, 0, sizeof dp->background_color);

   /* Local variable fields */
   dp->maxerrout = dp->maxerrpc = dp->maxerrabs = 0;
}

static void
gamma_info_imp(gamma_display *dp, png_structp pp, png_infop pi)
{
   /* Reuse the standard stuff as appropriate. */
   standard_info_part1(&dp->this, pp, pi);

   /* If requested strip 16 to 8 bits - this is handled automagically below
    * because the output bit depth is read from the library.  Note that there
    * are interactions with sBIT but, internally, libpng makes sbit at most
    * PNG_MAX_GAMMA_8 prior to 1.7 when doing the following.
    */
   if (dp->scale16)
#     ifdef PNG_READ_SCALE_16_TO_8_SUPPORTED
         png_set_scale_16(pp);
#     else
         /* The following works both in 1.5.4 and earlier versions: */
#        ifdef PNG_READ_16_TO_8_SUPPORTED
            png_set_strip_16(pp);
#        else
            png_error(pp, "scale16 (16 to 8 bit conversion) not supported");
#        endif
#     endif

   if (dp->expand16)
#     ifdef PNG_READ_EXPAND_16_SUPPORTED
         png_set_expand_16(pp);
#     else
         png_error(pp, "expand16 (8 to 16 bit conversion) not supported");
#     endif

   if (dp->do_background >= ALPHA_MODE_OFFSET)
   {
#     ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      {
         /* This tests the alpha mode handling, if supported. */
         int mode = dp->do_background - ALPHA_MODE_OFFSET;

         /* The gamma value is the output gamma, and is in the standard,
          * non-inverted, representation.  It provides a default for the PNG file
          * gamma, but since the file has a gAMA chunk this does not matter.
          */
         const double sg = dp->screen_gamma;
#        ifndef PNG_FLOATING_POINT_SUPPORTED
            png_fixed_point g = fix(sg);
#        endif

#        ifdef PNG_FLOATING_POINT_SUPPORTED
            png_set_alpha_mode(pp, mode, sg);
#        else
            png_set_alpha_mode_fixed(pp, mode, g);
#        endif

         /* However, for the standard Porter-Duff algorithm the output defaults
          * to be linear, so if the test requires non-linear output it must be
          * corrected here.
          */
         if (mode == PNG_ALPHA_STANDARD && sg != 1)
         {
#           ifdef PNG_FLOATING_POINT_SUPPORTED
               png_set_gamma(pp, sg, dp->file_gamma);
#           else
               png_fixed_point f = fix(dp->file_gamma);
               png_set_gamma_fixed(pp, g, f);
#           endif
         }
      }
#     else
         png_error(pp, "alpha mode handling not supported");
#     endif
   }

   else
   {
      /* Set up gamma processing. */
#     ifdef PNG_FLOATING_POINT_SUPPORTED
         png_set_gamma(pp, dp->screen_gamma, dp->file_gamma);
#     else
      {
         png_fixed_point s = fix(dp->screen_gamma);
         png_fixed_point f = fix(dp->file_gamma);
         png_set_gamma_fixed(pp, s, f);
      }
#     endif

      if (dp->do_background)
      {
#     ifdef PNG_READ_BACKGROUND_SUPPORTED
         /* NOTE: this assumes the caller provided the correct background gamma!
          */
         const double bg = dp->background_gamma;
#        ifndef PNG_FLOATING_POINT_SUPPORTED
            png_fixed_point g = fix(bg);
#        endif

#        ifdef PNG_FLOATING_POINT_SUPPORTED
            png_set_background(pp, &dp->background_color, dp->do_background,
               0/*need_expand*/, bg);
#        else
            png_set_background_fixed(pp, &dp->background_color,
               dp->do_background, 0/*need_expand*/, g);
#        endif
#     else
         png_error(pp, "png_set_background not supported");
#     endif
      }
   }

   {
      int i = dp->this.use_update_info;
      /* Always do one call, even if use_update_info is 0. */
      do
         png_read_update_info(pp, pi);
      while (--i > 0);
   }

   /* Now we may get a different cbRow: */
   standard_info_part2(&dp->this, pp, pi, 1 /*images*/);
}

static void PNGCBAPI
gamma_info(png_structp pp, png_infop pi)
{
   gamma_info_imp(voidcast(gamma_display*, png_get_progressive_ptr(pp)), pp,
      pi);
}

/* Validate a single component value - the routine gets the input and output
 * sample values as unscaled PNG component values along with a cache of all the
 * information required to validate the values.
 */
typedef struct validate_info
{
   png_const_structp  pp;
   gamma_display *dp;
   png_byte sbit;
   int use_input_precision;
   int do_background;
   int scale16;
   unsigned int sbit_max;
   unsigned int isbit_shift;
   unsigned int outmax;

   double gamma_correction; /* Overall correction required. */
   double file_inverse;     /* Inverse of file gamma. */
   double screen_gamma;
   double screen_inverse;   /* Inverse of screen gamma. */

   double background_red;   /* Linear background value, red or gray. */
   double background_green;
   double background_blue;

   double maxabs;
   double maxpc;
   double maxcalc;
   double maxout;
   double maxout_total;     /* Total including quantization error */
   double outlog;
   int    outquant;
}
validate_info;

static void
init_validate_info(validate_info *vi, gamma_display *dp, png_const_structp pp,
    int in_depth, int out_depth)
{
   unsigned int outmax = (1U<<out_depth)-1;

   vi->pp = pp;
   vi->dp = dp;

   if (dp->sbit > 0 && dp->sbit < in_depth)
   {
      vi->sbit = dp->sbit;
      vi->isbit_shift = in_depth - dp->sbit;
   }

   else
   {
      vi->sbit = (png_byte)in_depth;
      vi->isbit_shift = 0;
   }

   vi->sbit_max = (1U << vi->sbit)-1;

   /* This mimics the libpng threshold test, '0' is used to prevent gamma
    * correction in the validation test.
    */
   vi->screen_gamma = dp->screen_gamma;
   if (fabs(vi->screen_gamma-1) < PNG_GAMMA_THRESHOLD)
      vi->screen_gamma = vi->screen_inverse = 0;
   else
      vi->screen_inverse = 1/vi->screen_gamma;

   vi->use_input_precision = dp->use_input_precision;
   vi->outmax = outmax;
   vi->maxabs = abserr(dp->pm, in_depth, out_depth);
   vi->maxpc = pcerr(dp->pm, in_depth, out_depth);
   vi->maxcalc = calcerr(dp->pm, in_depth, out_depth);
   vi->maxout = outerr(dp->pm, in_depth, out_depth);
   vi->outquant = output_quantization_factor(dp->pm, in_depth, out_depth);
   vi->maxout_total = vi->maxout + vi->outquant * .5;
   vi->outlog = outlog(dp->pm, in_depth, out_depth);

   if ((dp->this.colour_type & PNG_COLOR_MASK_ALPHA) != 0 ||
      (dp->this.colour_type == 3 && dp->this.is_transparent) ||
      ((dp->this.colour_type == 0 || dp->this.colour_type == 2) &&
       dp->this.has_tRNS))
   {
      vi->do_background = dp->do_background;

      if (vi->do_background != 0)
      {
         const double bg_inverse = 1/dp->background_gamma;
         double r, g, b;

         /* Caller must at least put the gray value into the red channel */
         r = dp->background_color.red; r /= outmax;
         g = dp->background_color.green; g /= outmax;
         b = dp->background_color.blue; b /= outmax;

#     if 0
         /* libpng doesn't do this optimization, if we do pngvalid will fail.
          */
         if (fabs(bg_inverse-1) >= PNG_GAMMA_THRESHOLD)
#     endif
         {
            r = pow(r, bg_inverse);
            g = pow(g, bg_inverse);
            b = pow(b, bg_inverse);
         }

         vi->background_red = r;
         vi->background_green = g;
         vi->background_blue = b;
      }
   }
   else /* Do not expect any background processing */
      vi->do_background = 0;

   if (vi->do_background == 0)
      vi->background_red = vi->background_green = vi->background_blue = 0;

   vi->gamma_correction = 1/(dp->file_gamma*dp->screen_gamma);
   if (fabs(vi->gamma_correction-1) < PNG_GAMMA_THRESHOLD)
      vi->gamma_correction = 0;

   vi->file_inverse = 1/dp->file_gamma;
   if (fabs(vi->file_inverse-1) < PNG_GAMMA_THRESHOLD)
      vi->file_inverse = 0;

   vi->scale16 = dp->scale16;
}

/* This function handles composition of a single non-alpha component.  The
 * argument is the input sample value, in the range 0..1, and the alpha value.
 * The result is the composed, linear, input sample.  If alpha is less than zero
 * this is the alpha component and the function should not be called!
 */
static double
gamma_component_compose(int do_background, double input_sample, double alpha,
   double background, int *compose)
{
   switch (do_background)
   {
#ifdef PNG_READ_BACKGROUND_SUPPORTED
      case PNG_BACKGROUND_GAMMA_SCREEN:
      case PNG_BACKGROUND_GAMMA_FILE:
      case PNG_BACKGROUND_GAMMA_UNIQUE:
         /* Standard PNG background processing. */
         if (alpha < 1)
         {
            if (alpha > 0)
            {
               input_sample = input_sample * alpha + background * (1-alpha);
               if (compose != NULL)
                  *compose = 1;
            }

            else
               input_sample = background;
         }
         break;
#endif

#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      case ALPHA_MODE_OFFSET + PNG_ALPHA_STANDARD:
      case ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN:
         /* The components are premultiplied in either case and the output is
          * gamma encoded (to get standard Porter-Duff we expect the output
          * gamma to be set to 1.0!)
          */
      case ALPHA_MODE_OFFSET + PNG_ALPHA_OPTIMIZED:
         /* The optimization is that the partial-alpha entries are linear
          * while the opaque pixels are gamma encoded, but this only affects the
          * output encoding.
          */
         if (alpha < 1)
         {
            if (alpha > 0)
            {
               input_sample *= alpha;
               if (compose != NULL)
                  *compose = 1;
            }

            else
               input_sample = 0;
         }
         break;
#endif

      default:
         /* Standard cases where no compositing is done (so the component
          * value is already correct.)
          */
         UNUSED(alpha)
         UNUSED(background)
         UNUSED(compose)
         break;
   }

   return input_sample;
}

/* This API returns the encoded *input* component, in the range 0..1 */
static double
gamma_component_validate(const char *name, const validate_info *vi,
    unsigned int id, unsigned int od,
    const double alpha /* <0 for the alpha channel itself */,
    const double background /* component background value */)
{
   unsigned int isbit = id >> vi->isbit_shift;
   unsigned int sbit_max = vi->sbit_max;
   unsigned int outmax = vi->outmax;
   int do_background = vi->do_background;

   double i;

   /* First check on the 'perfect' result obtained from the digitized input
    * value, id, and compare this against the actual digitized result, 'od'.
    * 'i' is the input result in the range 0..1:
    */
   i = isbit; i /= sbit_max;

   /* Check for the fast route: if we don't do any background composition or if
    * this is the alpha channel ('alpha' < 0) or if the pixel is opaque then
    * just use the gamma_correction field to correct to the final output gamma.
    */
   if (alpha == 1 /* opaque pixel component */ || !do_background
#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      || do_background == ALPHA_MODE_OFFSET + PNG_ALPHA_PNG
#endif
      || (alpha < 0 /* alpha channel */
#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      && do_background != ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN
#endif
      ))
   {
      /* Then get the gamma corrected version of 'i' and compare to 'od', any
       * error less than .5 is insignificant - just quantization of the output
       * value to the nearest digital value (nevertheless the error is still
       * recorded - it's interesting ;-)
       */
      double encoded_sample = i;
      double encoded_error;

      /* alpha less than 0 indicates the alpha channel, which is always linear
       */
      if (alpha >= 0 && vi->gamma_correction > 0)
         encoded_sample = pow(encoded_sample, vi->gamma_correction);
      encoded_sample *= outmax;

      encoded_error = fabs(od-encoded_sample);

      if (encoded_error > vi->dp->maxerrout)
         vi->dp->maxerrout = encoded_error;

      if (encoded_error < vi->maxout_total && encoded_error < vi->outlog)
         return i;
   }

   /* The slow route - attempt to do linear calculations. */
   /* There may be an error, or background processing is required, so calculate
    * the actual sample values - unencoded light intensity values.  Note that in
    * practice these are not completely unencoded because they include a
    * 'viewing correction' to decrease or (normally) increase the perceptual
    * contrast of the image.  There's nothing we can do about this - we don't
    * know what it is - so assume the unencoded value is perceptually linear.
    */
   {
      double input_sample = i; /* In range 0..1 */
      double output, error, encoded_sample, encoded_error;
      double es_lo, es_hi;
      int compose = 0;           /* Set to one if composition done */
      int output_is_encoded;     /* Set if encoded to screen gamma */
      int log_max_error = 1;     /* Check maximum error values */
      png_const_charp pass = 0;  /* Reason test passes (or 0 for fail) */

      /* Convert to linear light (with the above caveat.)  The alpha channel is
       * already linear.
       */
      if (alpha >= 0)
      {
         int tcompose;

         if (vi->file_inverse > 0)
            input_sample = pow(input_sample, vi->file_inverse);

         /* Handle the compose processing: */
         tcompose = 0;
         input_sample = gamma_component_compose(do_background, input_sample,
            alpha, background, &tcompose);

         if (tcompose)
            compose = 1;
      }

      /* And similarly for the output value, but we need to check the background
       * handling to linearize it correctly.
       */
      output = od;
      output /= outmax;

      output_is_encoded = vi->screen_gamma > 0;

      if (alpha < 0) /* The alpha channel */
      {
#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
         if (do_background != ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN)
#endif
         {
            /* In all other cases the output alpha channel is linear already,
             * don't log errors here, they are much larger in linear data.
             */
            output_is_encoded = 0;
            log_max_error = 0;
         }
      }

#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      else /* A component */
      {
         if (do_background == ALPHA_MODE_OFFSET + PNG_ALPHA_OPTIMIZED &&
            alpha < 1) /* the optimized case - linear output */
         {
            if (alpha > 0) log_max_error = 0;
            output_is_encoded = 0;
         }
      }
#endif

      if (output_is_encoded)
         output = pow(output, vi->screen_gamma);

      /* Calculate (or recalculate) the encoded_sample value and repeat the
       * check above (unnecessary if we took the fast route, but harmless.)
       */
      encoded_sample = input_sample;
      if (output_is_encoded)
         encoded_sample = pow(encoded_sample, vi->screen_inverse);
      encoded_sample *= outmax;

      encoded_error = fabs(od-encoded_sample);

      /* Don't log errors in the alpha channel, or the 'optimized' case,
       * neither are significant to the overall perception.
       */
      if (log_max_error && encoded_error > vi->dp->maxerrout)
         vi->dp->maxerrout = encoded_error;

      if (encoded_error < vi->maxout_total)
      {
         if (encoded_error < vi->outlog)
            return i;

         /* Test passed but error is bigger than the log limit, record why the
          * test passed:
          */
         pass = "less than maxout:\n";
      }

      /* i: the original input value in the range 0..1
       *
       * pngvalid calculations:
       *  input_sample: linear result; i linearized and composed, range 0..1
       *  encoded_sample: encoded result; input_sample scaled to output bit depth
       *
       * libpng calculations:
       *  output: linear result; od scaled to 0..1 and linearized
       *  od: encoded result from libpng
       */

      /* Now we have the numbers for real errors, both absolute values as as a
       * percentage of the correct value (output):
       */
      error = fabs(input_sample-output);

      if (log_max_error && error > vi->dp->maxerrabs)
         vi->dp->maxerrabs = error;

      /* The following is an attempt to ignore the tendency of quantization to
       * dominate the percentage errors for lower result values:
       */
      if (log_max_error && input_sample > .5)
      {
         double percentage_error = error/input_sample;
         if (percentage_error > vi->dp->maxerrpc)
            vi->dp->maxerrpc = percentage_error;
      }

      /* Now calculate the digitization limits for 'encoded_sample' using the
       * 'max' values.  Note that maxout is in the encoded space but maxpc and
       * maxabs are in linear light space.
       *
       * First find the maximum error in linear light space, range 0..1:
       */
      {
         double tmp = input_sample * vi->maxpc;
         if (tmp < vi->maxabs) tmp = vi->maxabs;
         /* If 'compose' is true the composition was done in linear space using
          * integer arithmetic.  This introduces an extra error of +/- 0.5 (at
          * least) in the integer space used.  'maxcalc' records this, taking
          * into account the possibility that even for 16 bit output 8 bit space
          * may have been used.
          */
         if (compose && tmp < vi->maxcalc) tmp = vi->maxcalc;

         /* The 'maxout' value refers to the encoded result, to compare with
          * this encode input_sample adjusted by the maximum error (tmp) above.
          */
         es_lo = encoded_sample - vi->maxout;

         if (es_lo > 0 && input_sample-tmp > 0)
         {
            double low_value = input_sample-tmp;
            if (output_is_encoded)
               low_value = pow(low_value, vi->screen_inverse);
            low_value *= outmax;
            if (low_value < es_lo) es_lo = low_value;

            /* Quantize this appropriately: */
            es_lo = ceil(es_lo / vi->outquant - .5) * vi->outquant;
         }

         else
            es_lo = 0;

         es_hi = encoded_sample + vi->maxout;

         if (es_hi < outmax && input_sample+tmp < 1)
         {
            double high_value = input_sample+tmp;
            if (output_is_encoded)
               high_value = pow(high_value, vi->screen_inverse);
            high_value *= outmax;
            if (high_value > es_hi) es_hi = high_value;

            es_hi = floor(es_hi / vi->outquant + .5) * vi->outquant;
         }

         else
            es_hi = outmax;
      }

      /* The primary test is that the final encoded value returned by the
       * library should be between the two limits (inclusive) that were
       * calculated above.
       */
      if (od >= es_lo && od <= es_hi)
      {
         /* The value passes, but we may need to log the information anyway. */
         if (encoded_error < vi->outlog)
            return i;

         if (pass == 0)
            pass = "within digitization limits:\n";
      }

      {
         /* There has been an error in processing, or we need to log this
          * value.
          */
         double is_lo, is_hi;

         /* pass is set at this point if either of the tests above would have
          * passed.  Don't do these additional tests here - just log the
          * original [es_lo..es_hi] values.
          */
         if (pass == 0 && vi->use_input_precision && vi->dp->sbit)
         {
            /* Ok, something is wrong - this actually happens in current libpng
             * 16-to-8 processing.  Assume that the input value (id, adjusted
             * for sbit) can be anywhere between value-.5 and value+.5 - quite a
             * large range if sbit is low.
             *
             * NOTE: at present because the libpng gamma table stuff has been
             * changed to use a rounding algorithm to correct errors in 8-bit
             * calculations the precise sbit calculation (a shift) has been
             * lost.  This can result in up to a +/-1 error in the presence of
             * an sbit less than the bit depth.
             */
#           if PNG_LIBPNG_VER < 10700
#              define SBIT_ERROR .5
#           else
#              define SBIT_ERROR 1.
#           endif
            double tmp = (isbit - SBIT_ERROR)/sbit_max;

            if (tmp <= 0)
               tmp = 0;

            else if (alpha >= 0 && vi->file_inverse > 0 && tmp < 1)
               tmp = pow(tmp, vi->file_inverse);

            tmp = gamma_component_compose(do_background, tmp, alpha, background,
               NULL);

            if (output_is_encoded && tmp > 0 && tmp < 1)
               tmp = pow(tmp, vi->screen_inverse);

            is_lo = ceil(outmax * tmp - vi->maxout_total);

            if (is_lo < 0)
               is_lo = 0;

            tmp = (isbit + SBIT_ERROR)/sbit_max;

            if (tmp >= 1)
               tmp = 1;

            else if (alpha >= 0 && vi->file_inverse > 0 && tmp < 1)
               tmp = pow(tmp, vi->file_inverse);

            tmp = gamma_component_compose(do_background, tmp, alpha, background,
               NULL);

            if (output_is_encoded && tmp > 0 && tmp < 1)
               tmp = pow(tmp, vi->screen_inverse);

            is_hi = floor(outmax * tmp + vi->maxout_total);

            if (is_hi > outmax)
               is_hi = outmax;

            if (!(od < is_lo || od > is_hi))
            {
               if (encoded_error < vi->outlog)
                  return i;

               pass = "within input precision limits:\n";
            }

            /* One last chance.  If this is an alpha channel and the 16to8
             * option has been used and 'inaccurate' scaling is used then the
             * bit reduction is obtained by simply using the top 8 bits of the
             * value.
             *
             * This is only done for older libpng versions when the 'inaccurate'
             * (chop) method of scaling was used.
             */
#           ifndef PNG_READ_16_TO_8_ACCURATE_SCALE_SUPPORTED
#              if PNG_LIBPNG_VER < 10504
                  /* This may be required for other components in the future,
                   * but at present the presence of gamma correction effectively
                   * prevents the errors in the component scaling (I don't quite
                   * understand why, but since it's better this way I care not
                   * to ask, JB 20110419.)
                   */
                  if (pass == 0 && alpha < 0 && vi->scale16 && vi->sbit > 8 &&
                     vi->sbit + vi->isbit_shift == 16)
                  {
                     tmp = ((id >> 8) - .5)/255;

                     if (tmp > 0)
                     {
                        is_lo = ceil(outmax * tmp - vi->maxout_total);
                        if (is_lo < 0) is_lo = 0;
                     }

                     else
                        is_lo = 0;

                     tmp = ((id >> 8) + .5)/255;

                     if (tmp < 1)
                     {
                        is_hi = floor(outmax * tmp + vi->maxout_total);
                        if (is_hi > outmax) is_hi = outmax;
                     }

                     else
                        is_hi = outmax;

                     if (!(od < is_lo || od > is_hi))
                     {
                        if (encoded_error < vi->outlog)
                           return i;

                        pass = "within 8 bit limits:\n";
                     }
                  }
#              endif
#           endif
         }
         else /* !use_input_precision */
            is_lo = es_lo, is_hi = es_hi;

         /* Attempt to output a meaningful error/warning message: the message
          * output depends on the background/composite operation being performed
          * because this changes what parameters were actually used above.
          */
         {
            size_t pos = 0;
            /* Need either 1/255 or 1/65535 precision here; 3 or 6 decimal
             * places.  Just use outmax to work out which.
             */
            int precision = (outmax >= 1000 ? 6 : 3);
            int use_input=1, use_background=0, do_compose=0;
            char msg[256];

            if (pass != 0)
               pos = safecat(msg, sizeof msg, pos, "\n\t");

            /* Set up the various flags, the output_is_encoded flag above
             * is also used below.  do_compose is just a double check.
             */
            switch (do_background)
            {
#           ifdef PNG_READ_BACKGROUND_SUPPORTED
               case PNG_BACKGROUND_GAMMA_SCREEN:
               case PNG_BACKGROUND_GAMMA_FILE:
               case PNG_BACKGROUND_GAMMA_UNIQUE:
                  use_background = (alpha >= 0 && alpha < 1);
#           endif
#           ifdef PNG_READ_ALPHA_MODE_SUPPORTED
               /* FALLTHROUGH */
               case ALPHA_MODE_OFFSET + PNG_ALPHA_STANDARD:
               case ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN:
               case ALPHA_MODE_OFFSET + PNG_ALPHA_OPTIMIZED:
#           endif /* ALPHA_MODE_SUPPORTED */
               do_compose = (alpha > 0 && alpha < 1);
               use_input = (alpha != 0);
               break;

            default:
               break;
            }

            /* Check the 'compose' flag */
            if (compose != do_compose)
               png_error(vi->pp, "internal error (compose)");

            /* 'name' is the component name */
            pos = safecat(msg, sizeof msg, pos, name);
            pos = safecat(msg, sizeof msg, pos, "(");
            pos = safecatn(msg, sizeof msg, pos, id);
            if (use_input || pass != 0/*logging*/)
            {
               if (isbit != id)
               {
                  /* sBIT has reduced the precision of the input: */
                  pos = safecat(msg, sizeof msg, pos, ", sbit(");
                  pos = safecatn(msg, sizeof msg, pos, vi->sbit);
                  pos = safecat(msg, sizeof msg, pos, "): ");
                  pos = safecatn(msg, sizeof msg, pos, isbit);
               }
               pos = safecat(msg, sizeof msg, pos, "/");
               /* The output is either "id/max" or "id sbit(sbit): isbit/max" */
               pos = safecatn(msg, sizeof msg, pos, vi->sbit_max);
            }
            pos = safecat(msg, sizeof msg, pos, ")");

            /* A component may have been multiplied (in linear space) by the
             * alpha value, 'compose' says whether this is relevant.
             */
            if (compose || pass != 0)
            {
               /* If any form of composition is being done report our
                * calculated linear value here (the code above doesn't record
                * the input value before composition is performed, so what
                * gets reported is the value after composition.)
                */
               if (use_input || pass != 0)
               {
                  if (vi->file_inverse > 0)
                  {
                     pos = safecat(msg, sizeof msg, pos, "^");
                     pos = safecatd(msg, sizeof msg, pos, vi->file_inverse, 2);
                  }

                  else
                     pos = safecat(msg, sizeof msg, pos, "[linear]");

                  pos = safecat(msg, sizeof msg, pos, "*(alpha)");
                  pos = safecatd(msg, sizeof msg, pos, alpha, precision);
               }

               /* Now record the *linear* background value if it was used
                * (this function is not passed the original, non-linear,
                * value but it is contained in the test name.)
                */
               if (use_background)
               {
                  pos = safecat(msg, sizeof msg, pos, use_input ? "+" : " ");
                  pos = safecat(msg, sizeof msg, pos, "(background)");
                  pos = safecatd(msg, sizeof msg, pos, background, precision);
                  pos = safecat(msg, sizeof msg, pos, "*");
                  pos = safecatd(msg, sizeof msg, pos, 1-alpha, precision);
               }
            }

            /* Report the calculated value (input_sample) and the linearized
             * libpng value (output) unless this is just a component gamma
             * correction.
             */
            if (compose || alpha < 0 || pass != 0)
            {
               pos = safecat(msg, sizeof msg, pos,
                  pass != 0 ? " =\n\t" : " = ");
               pos = safecatd(msg, sizeof msg, pos, input_sample, precision);
               pos = safecat(msg, sizeof msg, pos, " (libpng: ");
               pos = safecatd(msg, sizeof msg, pos, output, precision);
               pos = safecat(msg, sizeof msg, pos, ")");

               /* Finally report the output gamma encoding, if any. */
               if (output_is_encoded)
               {
                  pos = safecat(msg, sizeof msg, pos, " ^");
                  pos = safecatd(msg, sizeof msg, pos, vi->screen_inverse, 2);
                  pos = safecat(msg, sizeof msg, pos, "(to screen) =");
               }

               else
                  pos = safecat(msg, sizeof msg, pos, " [screen is linear] =");
            }

            if ((!compose && alpha >= 0) || pass != 0)
            {
               if (pass != 0) /* logging */
                  pos = safecat(msg, sizeof msg, pos, "\n\t[overall:");

               /* This is the non-composition case, the internal linear
                * values are irrelevant (though the log below will reveal
                * them.)  Output a much shorter warning/error message and report
                * the overall gamma correction.
                */
               if (vi->gamma_correction > 0)
               {
                  pos = safecat(msg, sizeof msg, pos, " ^");
                  pos = safecatd(msg, sizeof msg, pos, vi->gamma_correction, 2);
                  pos = safecat(msg, sizeof msg, pos, "(gamma correction) =");
               }

               else
                  pos = safecat(msg, sizeof msg, pos,
                     " [no gamma correction] =");

               if (pass != 0)
                  pos = safecat(msg, sizeof msg, pos, "]");
            }

            /* This is our calculated encoded_sample which should (but does
             * not) match od:
             */
            pos = safecat(msg, sizeof msg, pos, pass != 0 ? "\n\t" : " ");
            pos = safecatd(msg, sizeof msg, pos, is_lo, 1);
            pos = safecat(msg, sizeof msg, pos, " < ");
            pos = safecatd(msg, sizeof msg, pos, encoded_sample, 1);
            pos = safecat(msg, sizeof msg, pos, " (libpng: ");
            pos = safecatn(msg, sizeof msg, pos, od);
            pos = safecat(msg, sizeof msg, pos, ")");
            pos = safecat(msg, sizeof msg, pos, "/");
            pos = safecatn(msg, sizeof msg, pos, outmax);
            pos = safecat(msg, sizeof msg, pos, " < ");
            pos = safecatd(msg, sizeof msg, pos, is_hi, 1);

            if (pass == 0) /* The error condition */
            {
#              ifdef PNG_WARNINGS_SUPPORTED
                  png_warning(vi->pp, msg);
#              else
                  store_warning(vi->pp, msg);
#              endif
            }

            else /* logging this value */
               store_verbose(&vi->dp->pm->this, vi->pp, pass, msg);
         }
      }
   }

   return i;
}

static void
gamma_image_validate(gamma_display *dp, png_const_structp pp,
   png_infop pi)
{
   /* Get some constants derived from the input and output file formats: */
   const png_store* const ps = dp->this.ps;
   png_byte in_ct = dp->this.colour_type;
   png_byte in_bd = dp->this.bit_depth;
   png_uint_32 w = dp->this.w;
   png_uint_32 h = dp->this.h;
   const size_t cbRow = dp->this.cbRow;
   png_byte out_ct = png_get_color_type(pp, pi);
   png_byte out_bd = png_get_bit_depth(pp, pi);

   /* There are three sources of error, firstly the quantization in the
    * file encoding, determined by sbit and/or the file depth, secondly
    * the output (screen) gamma and thirdly the output file encoding.
    *
    * Since this API receives the screen and file gamma in double
    * precision it is possible to calculate an exact answer given an input
    * pixel value.  Therefore we assume that the *input* value is exact -
    * sample/maxsample - calculate the corresponding gamma corrected
    * output to the limits of double precision arithmetic and compare with
    * what libpng returns.
    *
    * Since the library must quantize the output to 8 or 16 bits there is
    * a fundamental limit on the accuracy of the output of +/-.5 - this
    * quantization limit is included in addition to the other limits
    * specified by the parameters to the API.  (Effectively, add .5
    * everywhere.)
    *
    * The behavior of the 'sbit' parameter is defined by section 12.5
    * (sample depth scaling) of the PNG spec.  That section forces the
    * decoder to assume that the PNG values have been scaled if sBIT is
    * present:
    *
    *     png-sample = floor( input-sample * (max-out/max-in) + .5);
    *
    * This means that only a subset of the possible PNG values should
    * appear in the input. However, the spec allows the encoder to use a
    * variety of approximations to the above and doesn't require any
    * restriction of the values produced.
    *
    * Nevertheless the spec requires that the upper 'sBIT' bits of the
    * value stored in a PNG file be the original sample bits.
    * Consequently the code below simply scales the top sbit bits by
    * (1<<sbit)-1 to obtain an original sample value.
    *
    * Because there is limited precision in the input it is arguable that
    * an acceptable result is any valid result from input-.5 to input+.5.
    * The basic tests below do not do this, however if 'use_input_precision'
    * is set a subsequent test is performed above.
    */
   unsigned int samples_per_pixel = (out_ct & 2U) ? 3U : 1U;
   int processing;
   png_uint_32 y;
   const store_palette_entry *in_palette = dp->this.palette;
   int in_is_transparent = dp->this.is_transparent;
   int process_tRNS;
   int out_npalette = -1;
   int out_is_transparent = 0; /* Just refers to the palette case */
   store_palette out_palette;
   validate_info vi;

   /* Check for row overwrite errors */
   store_image_check(dp->this.ps, pp, 0);

   /* Supply the input and output sample depths here - 8 for an indexed image,
    * otherwise the bit depth.
    */
   init_validate_info(&vi, dp, pp, in_ct==3?8:in_bd, out_ct==3?8:out_bd);

   processing = (vi.gamma_correction > 0 && !dp->threshold_test)
      || in_bd != out_bd || in_ct != out_ct || vi.do_background;
   process_tRNS = dp->this.has_tRNS && vi.do_background;

   /* TODO: FIX THIS: MAJOR BUG!  If the transformations all happen inside
    * the palette there is no way of finding out, because libpng fails to
    * update the palette on png_read_update_info.  Indeed, libpng doesn't
    * even do the required work until much later, when it doesn't have any
    * info pointer.  Oops.  For the moment 'processing' is turned off if
    * out_ct is palette.
    */
   if (in_ct == 3 && out_ct == 3)
      processing = 0;

   if (processing && out_ct == 3)
      out_is_transparent = read_palette(out_palette, &out_npalette, pp, pi);

   for (y=0; y<h; ++y)
   {
      png_const_bytep pRow = store_image_row(ps, pp, 0, y);
      png_byte std[STANDARD_ROWMAX];

      transform_row(pp, std, in_ct, in_bd, y);

      if (processing)
      {
         unsigned int x;

         for (x=0; x<w; ++x)
         {
            double alpha = 1; /* serves as a flag value */

            /* Record the palette index for index images. */
            unsigned int in_index =
               in_ct == 3 ? sample(std, 3, in_bd, x, 0, 0, 0) : 256;
            unsigned int out_index =
               out_ct == 3 ? sample(std, 3, out_bd, x, 0, 0, 0) : 256;

            /* Handle input alpha - png_set_background will cause the output
             * alpha to disappear so there is nothing to check.
             */
            if ((in_ct & PNG_COLOR_MASK_ALPHA) != 0 ||
                (in_ct == 3 && in_is_transparent))
            {
               unsigned int input_alpha = in_ct == 3 ?
                  dp->this.palette[in_index].alpha :
                  sample(std, in_ct, in_bd, x, samples_per_pixel, 0, 0);

               unsigned int output_alpha = 65536 /* as a flag value */;

               if (out_ct == 3)
               {
                  if (out_is_transparent)
                     output_alpha = out_palette[out_index].alpha;
               }

               else if ((out_ct & PNG_COLOR_MASK_ALPHA) != 0)
                  output_alpha = sample(pRow, out_ct, out_bd, x,
                     samples_per_pixel, 0, 0);

               if (output_alpha != 65536)
                  alpha = gamma_component_validate("alpha", &vi, input_alpha,
                     output_alpha, -1/*alpha*/, 0/*background*/);

               else /* no alpha in output */
               {
                  /* This is a copy of the calculation of 'i' above in order to
                   * have the alpha value to use in the background calculation.
                   */
                  alpha = input_alpha >> vi.isbit_shift;
                  alpha /= vi.sbit_max;
               }
            }

            else if (process_tRNS)
            {
               /* alpha needs to be set appropriately for this pixel, it is
                * currently 1 and needs to be 0 for an input pixel which matches
                * the values in tRNS.
                */
               switch (in_ct)
               {
                  case 0: /* gray */
                     if (sample(std, in_ct, in_bd, x, 0, 0, 0) ==
                           dp->this.transparent.red)
                        alpha = 0;
                     break;

                  case 2: /* RGB */
                     if (sample(std, in_ct, in_bd, x, 0, 0, 0) ==
                           dp->this.transparent.red &&
                         sample(std, in_ct, in_bd, x, 1, 0, 0) ==
                           dp->this.transparent.green &&
                         sample(std, in_ct, in_bd, x, 2, 0, 0) ==
                           dp->this.transparent.blue)
                        alpha = 0;
                     break;

                  default:
                     break;
               }
            }

            /* Handle grayscale or RGB components. */
            if ((in_ct & PNG_COLOR_MASK_COLOR) == 0) /* grayscale */
               (void)gamma_component_validate("gray", &vi,
                  sample(std, in_ct, in_bd, x, 0, 0, 0),
                  sample(pRow, out_ct, out_bd, x, 0, 0, 0),
                  alpha/*component*/, vi.background_red);
            else /* RGB or palette */
            {
               (void)gamma_component_validate("red", &vi,
                  in_ct == 3 ? in_palette[in_index].red :
                     sample(std, in_ct, in_bd, x, 0, 0, 0),
                  out_ct == 3 ? out_palette[out_index].red :
                     sample(pRow, out_ct, out_bd, x, 0, 0, 0),
                  alpha/*component*/, vi.background_red);

               (void)gamma_component_validate("green", &vi,
                  in_ct == 3 ? in_palette[in_index].green :
                     sample(std, in_ct, in_bd, x, 1, 0, 0),
                  out_ct == 3 ? out_palette[out_index].green :
                     sample(pRow, out_ct, out_bd, x, 1, 0, 0),
                  alpha/*component*/, vi.background_green);

               (void)gamma_component_validate("blue", &vi,
                  in_ct == 3 ? in_palette[in_index].blue :
                     sample(std, in_ct, in_bd, x, 2, 0, 0),
                  out_ct == 3 ? out_palette[out_index].blue :
                     sample(pRow, out_ct, out_bd, x, 2, 0, 0),
                  alpha/*component*/, vi.background_blue);
            }
         }
      }

      else if (memcmp(std, pRow, cbRow) != 0)
      {
         char msg[64];

         /* No transform is expected on the threshold tests. */
         sprintf(msg, "gamma: below threshold row %lu changed",
            (unsigned long)y);

         png_error(pp, msg);
      }
   } /* row (y) loop */

   dp->this.ps->validated = 1;
}

static void PNGCBAPI
gamma_end(png_structp ppIn, png_infop pi)
{
   png_const_structp pp = ppIn;
   gamma_display *dp = voidcast(gamma_display*, png_get_progressive_ptr(pp));

   if (!dp->this.speed)
      gamma_image_validate(dp, pp, pi);
   else
      dp->this.ps->validated = 1;
}

/* A single test run checking a gamma transformation.
 *
 * maxabs: maximum absolute error as a fraction
 * maxout: maximum output error in the output units
 * maxpc:  maximum percentage error (as a percentage)
 */
static void
gamma_test(png_modifier *pmIn, png_byte colour_typeIn,
    png_byte bit_depthIn, int palette_numberIn,
    int interlace_typeIn,
    const double file_gammaIn, const double screen_gammaIn,
    png_byte sbitIn, int threshold_testIn,
    const char *name,
    int use_input_precisionIn, int scale16In,
    int expand16In, int do_backgroundIn,
    const png_color_16 *bkgd_colorIn, double bkgd_gammaIn)
{
   gamma_display d;
   context(&pmIn->this, fault);

   gamma_display_init(&d, pmIn, FILEID(colour_typeIn, bit_depthIn,
      palette_numberIn, interlace_typeIn, 0, 0, 0),
      file_gammaIn, screen_gammaIn, sbitIn,
      threshold_testIn, use_input_precisionIn, scale16In,
      expand16In, do_backgroundIn, bkgd_colorIn, bkgd_gammaIn);

   Try
   {
      png_structp pp;
      png_infop pi;
      gama_modification gama_mod;
      srgb_modification srgb_mod;
      sbit_modification sbit_mod;

      /* For the moment don't use the png_modifier support here. */
      d.pm->encoding_counter = 0;
      modifier_set_encoding(d.pm); /* Just resets everything */
      d.pm->current_gamma = d.file_gamma;

      /* Make an appropriate modifier to set the PNG file gamma to the
       * given gamma value and the sBIT chunk to the given precision.
       */
      d.pm->modifications = NULL;
      gama_modification_init(&gama_mod, d.pm, d.file_gamma);
      srgb_modification_init(&srgb_mod, d.pm, 127 /*delete*/);
      if (d.sbit > 0)
         sbit_modification_init(&sbit_mod, d.pm, d.sbit);

      modification_reset(d.pm->modifications);

      /* Get a png_struct for reading the image. */
      pp = set_modifier_for_read(d.pm, &pi, d.this.id, name);
      standard_palette_init(&d.this);

      /* Introduce the correct read function. */
      if (d.pm->this.progressive)
      {
         /* Share the row function with the standard implementation. */
         png_set_progressive_read_fn(pp, &d, gamma_info, progressive_row,
            gamma_end);

         /* Now feed data into the reader until we reach the end: */
         modifier_progressive_read(d.pm, pp, pi);
      }
      else
      {
         /* modifier_read expects a png_modifier* */
         png_set_read_fn(pp, d.pm, modifier_read);

         /* Check the header values: */
         png_read_info(pp, pi);

         /* Process the 'info' requirements. Only one image is generated */
         gamma_info_imp(&d, pp, pi);

         sequential_row(&d.this, pp, pi, -1, 0);

         if (!d.this.speed)
            gamma_image_validate(&d, pp, pi);
         else
            d.this.ps->validated = 1;
      }

      modifier_reset(d.pm);

      if (d.pm->log && !d.threshold_test && !d.this.speed)
         fprintf(stderr, "%d bit %s %s: max error %f (%.2g, %2g%%)\n",
            d.this.bit_depth, colour_types[d.this.colour_type], name,
            d.maxerrout, d.maxerrabs, 100*d.maxerrpc);

      /* Log the summary values too. */
      if (d.this.colour_type == 0 || d.this.colour_type == 4)
      {
         switch (d.this.bit_depth)
         {
         case 1:
            break;

         case 2:
            if (d.maxerrout > d.pm->error_gray_2)
               d.pm->error_gray_2 = d.maxerrout;

            break;

         case 4:
            if (d.maxerrout > d.pm->error_gray_4)
               d.pm->error_gray_4 = d.maxerrout;

            break;

         case 8:
            if (d.maxerrout > d.pm->error_gray_8)
               d.pm->error_gray_8 = d.maxerrout;

            break;

         case 16:
            if (d.maxerrout > d.pm->error_gray_16)
               d.pm->error_gray_16 = d.maxerrout;

            break;

         default:
            png_error(pp, "bad bit depth (internal: 1)");
         }
      }

      else if (d.this.colour_type == 2 || d.this.colour_type == 6)
      {
         switch (d.this.bit_depth)
         {
         case 8:

            if (d.maxerrout > d.pm->error_color_8)
               d.pm->error_color_8 = d.maxerrout;

            break;

         case 16:

            if (d.maxerrout > d.pm->error_color_16)
               d.pm->error_color_16 = d.maxerrout;

            break;

         default:
            png_error(pp, "bad bit depth (internal: 2)");
         }
      }

      else if (d.this.colour_type == 3)
      {
         if (d.maxerrout > d.pm->error_indexed)
            d.pm->error_indexed = d.maxerrout;
      }
   }

   Catch(fault)
      modifier_reset(voidcast(png_modifier*,(void*)fault));
}

static void gamma_threshold_test(png_modifier *pm, png_byte colour_type,
    png_byte bit_depth, int interlace_type, double file_gamma,
    double screen_gamma)
{
   size_t pos = 0;
   char name[64];
   pos = safecat(name, sizeof name, pos, "threshold ");
   pos = safecatd(name, sizeof name, pos, file_gamma, 3);
   pos = safecat(name, sizeof name, pos, "/");
   pos = safecatd(name, sizeof name, pos, screen_gamma, 3);

   (void)gamma_test(pm, colour_type, bit_depth, 0/*palette*/, interlace_type,
      file_gamma, screen_gamma, 0/*sBIT*/, 1/*threshold test*/, name,
      0 /*no input precision*/,
      0 /*no scale16*/, 0 /*no expand16*/, 0 /*no background*/, 0 /*hence*/,
      0 /*no background gamma*/);
}

static void
perform_gamma_threshold_tests(png_modifier *pm)
{
   png_byte colour_type = 0;
   png_byte bit_depth = 0;
   unsigned int palette_number = 0;

   /* Don't test more than one instance of each palette - it's pointless, in
    * fact this test is somewhat excessive since libpng doesn't make this
    * decision based on colour type or bit depth!
    *
    * CHANGED: now test two palettes and, as a side effect, images with and
    * without tRNS.
    */
   while (next_format(&colour_type, &bit_depth, &palette_number,
                      pm->test_lbg_gamma_threshold, pm->test_tRNS))
      if (palette_number < 2)
   {
      double test_gamma = 1.0;
      while (test_gamma >= .4)
      {
         /* There's little point testing the interlacing vs non-interlacing,
          * but this can be set from the command line.
          */
         gamma_threshold_test(pm, colour_type, bit_depth, pm->interlace_type,
            test_gamma, 1/test_gamma);
         test_gamma *= .95;
      }

      /* And a special test for sRGB */
      gamma_threshold_test(pm, colour_type, bit_depth, pm->interlace_type,
          .45455, 2.2);

      if (fail(pm))
         return;
   }
}

static void gamma_transform_test(png_modifier *pm,
   png_byte colour_type, png_byte bit_depth,
   int palette_number,
   int interlace_type, const double file_gamma,
   const double screen_gamma, png_byte sbit,
   int use_input_precision, int scale16)
{
   size_t pos = 0;
   char name[64];

   if (sbit != bit_depth && sbit != 0)
   {
      pos = safecat(name, sizeof name, pos, "sbit(");
      pos = safecatn(name, sizeof name, pos, sbit);
      pos = safecat(name, sizeof name, pos, ") ");
   }

   else
      pos = safecat(name, sizeof name, pos, "gamma ");

   if (scale16)
      pos = safecat(name, sizeof name, pos, "16to8 ");

   pos = safecatd(name, sizeof name, pos, file_gamma, 3);
   pos = safecat(name, sizeof name, pos, "->");
   pos = safecatd(name, sizeof name, pos, screen_gamma, 3);

   gamma_test(pm, colour_type, bit_depth, palette_number, interlace_type,
      file_gamma, screen_gamma, sbit, 0, name, use_input_precision,
      scale16, pm->test_gamma_expand16, 0 , 0, 0);
}

static void perform_gamma_transform_tests(png_modifier *pm)
{
   png_byte colour_type = 0;
   png_byte bit_depth = 0;
   unsigned int palette_number = 0;

   while (next_format(&colour_type, &bit_depth, &palette_number,
                      pm->test_lbg_gamma_transform, pm->test_tRNS))
   {
      unsigned int i, j;

      for (i=0; i<pm->ngamma_tests; ++i) for (j=0; j<pm->ngamma_tests; ++j)
         if (i != j)
         {
            gamma_transform_test(pm, colour_type, bit_depth, palette_number,
               pm->interlace_type, 1/pm->gammas[i], pm->gammas[j], 0/*sBIT*/,
               pm->use_input_precision, 0 /*do not scale16*/);

            if (fail(pm))
               return;
         }
   }
}

static void perform_gamma_sbit_tests(png_modifier *pm)
{
   png_byte sbit;

   /* The only interesting cases are colour and grayscale, alpha is ignored here
    * for overall speed.  Only bit depths where sbit is less than the bit depth
    * are tested.
    */
   for (sbit=pm->sbitlow; sbit<(1<<READ_BDHI); ++sbit)
   {
      png_byte colour_type = 0, bit_depth = 0;
      unsigned int npalette = 0;

      while (next_format(&colour_type, &bit_depth, &npalette,
                         pm->test_lbg_gamma_sbit, pm->test_tRNS))
         if ((colour_type & PNG_COLOR_MASK_ALPHA) == 0 &&
            ((colour_type == 3 && sbit < 8) ||
            (colour_type != 3 && sbit < bit_depth)))
      {
         unsigned int i;

         for (i=0; i<pm->ngamma_tests; ++i)
         {
            unsigned int j;

            for (j=0; j<pm->ngamma_tests; ++j) if (i != j)
            {
               gamma_transform_test(pm, colour_type, bit_depth, npalette,
                  pm->interlace_type, 1/pm->gammas[i], pm->gammas[j],
                  sbit, pm->use_input_precision_sbit, 0 /*scale16*/);

               if (fail(pm))
                  return;
            }
         }
      }
   }
}

/* Note that this requires a 16 bit source image but produces 8 bit output, so
 * we only need the 16bit write support, but the 16 bit images are only
 * generated if DO_16BIT is defined.
 */
#ifdef DO_16BIT
static void perform_gamma_scale16_tests(png_modifier *pm)
{
#  ifndef PNG_MAX_GAMMA_8
#     define PNG_MAX_GAMMA_8 11
#  endif
#  if defined PNG_MAX_GAMMA_8 || PNG_LIBPNG_VER < 10700
#     define SBIT_16_TO_8 PNG_MAX_GAMMA_8
#  else
#     define SBIT_16_TO_8 16
#  endif
   /* Include the alpha cases here. Note that sbit matches the internal value
    * used by the library - otherwise we will get spurious errors from the
    * internal sbit style approximation.
    *
    * The threshold test is here because otherwise the 16 to 8 conversion will
    * proceed *without* gamma correction, and the tests above will fail (but not
    * by much) - this could be fixed, it only appears with the -g option.
    */
   unsigned int i, j;
   for (i=0; i<pm->ngamma_tests; ++i)
   {
      for (j=0; j<pm->ngamma_tests; ++j)
      {
         if (i != j &&
             fabs(pm->gammas[j]/pm->gammas[i]-1) >= PNG_GAMMA_THRESHOLD)
         {
            gamma_transform_test(pm, 0, 16, 0, pm->interlace_type,
               1/pm->gammas[i], pm->gammas[j], SBIT_16_TO_8,
               pm->use_input_precision_16to8, 1 /*scale16*/);

            if (fail(pm))
               return;

            gamma_transform_test(pm, 2, 16, 0, pm->interlace_type,
               1/pm->gammas[i], pm->gammas[j], SBIT_16_TO_8,
               pm->use_input_precision_16to8, 1 /*scale16*/);

            if (fail(pm))
               return;

            gamma_transform_test(pm, 4, 16, 0, pm->interlace_type,
               1/pm->gammas[i], pm->gammas[j], SBIT_16_TO_8,
               pm->use_input_precision_16to8, 1 /*scale16*/);

            if (fail(pm))
               return;

            gamma_transform_test(pm, 6, 16, 0, pm->interlace_type,
               1/pm->gammas[i], pm->gammas[j], SBIT_16_TO_8,
               pm->use_input_precision_16to8, 1 /*scale16*/);

            if (fail(pm))
               return;
         }
      }
   }
}
#endif /* 16 to 8 bit conversion */

#if defined(PNG_READ_BACKGROUND_SUPPORTED) ||\
   defined(PNG_READ_ALPHA_MODE_SUPPORTED)
static void gamma_composition_test(png_modifier *pm,
   png_byte colour_type, png_byte bit_depth,
   int palette_number,
   int interlace_type, const double file_gamma,
   const double screen_gamma,
   int use_input_precision, int do_background,
   int expand_16)
{
   size_t pos = 0;
   png_const_charp base;
   double bg;
   char name[128];
   png_color_16 background;

   /* Make up a name and get an appropriate background gamma value. */
   switch (do_background)
   {
      default:
         base = "";
         bg = 4; /* should not be used */
         break;
      case PNG_BACKGROUND_GAMMA_SCREEN:
         base = " bckg(Screen):";
         bg = 1/screen_gamma;
         break;
      case PNG_BACKGROUND_GAMMA_FILE:
         base = " bckg(File):";
         bg = file_gamma;
         break;
      case PNG_BACKGROUND_GAMMA_UNIQUE:
         base = " bckg(Unique):";
         /* This tests the handling of a unique value, the math is such that the
          * value tends to be <1, but is neither screen nor file (even if they
          * match!)
          */
         bg = (file_gamma + screen_gamma) / 3;
         break;
#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
      case ALPHA_MODE_OFFSET + PNG_ALPHA_PNG:
         base = " alpha(PNG)";
         bg = 4; /* should not be used */
         break;
      case ALPHA_MODE_OFFSET + PNG_ALPHA_STANDARD:
         base = " alpha(Porter-Duff)";
         bg = 4; /* should not be used */
         break;
      case ALPHA_MODE_OFFSET + PNG_ALPHA_OPTIMIZED:
         base = " alpha(Optimized)";
         bg = 4; /* should not be used */
         break;
      case ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN:
         base = " alpha(Broken)";
         bg = 4; /* should not be used */
         break;
#endif
   }

   /* Use random background values - the background is always presented in the
    * output space (8 or 16 bit components).
    */
   if (expand_16 || bit_depth == 16)
   {
      png_uint_32 r = random_32();

      background.red = (png_uint_16)r;
      background.green = (png_uint_16)(r >> 16);
      r = random_32();
      background.blue = (png_uint_16)r;
      background.gray = (png_uint_16)(r >> 16);

      /* In earlier libpng versions, those where DIGITIZE is set, any background
       * gamma correction in the expand16 case was done using 8-bit gamma
       * correction tables, resulting in larger errors.  To cope with those
       * cases use a 16-bit background value which will handle this gamma
       * correction.
       */
#     if DIGITIZE
         if (expand_16 && (do_background == PNG_BACKGROUND_GAMMA_UNIQUE ||
                           do_background == PNG_BACKGROUND_GAMMA_FILE) &&
            fabs(bg*screen_gamma-1) > PNG_GAMMA_THRESHOLD)
         {
            /* The background values will be looked up in an 8-bit table to do
             * the gamma correction, so only select values which are an exact
             * match for the 8-bit table entries:
             */
            background.red = (png_uint_16)((background.red >> 8) * 257);
            background.green = (png_uint_16)((background.green >> 8) * 257);
            background.blue = (png_uint_16)((background.blue >> 8) * 257);
            background.gray = (png_uint_16)((background.gray >> 8) * 257);
         }
#     endif
   }

   else /* 8 bit colors */
   {
      png_uint_32 r = random_32();

      background.red = (png_byte)r;
      background.green = (png_byte)(r >> 8);
      background.blue = (png_byte)(r >> 16);
      background.gray = (png_byte)(r >> 24);
   }

   background.index = 193; /* rgb(193,193,193) to detect errors */

   if (!(colour_type & PNG_COLOR_MASK_COLOR))
   {
      /* Because, currently, png_set_background is always called with
       * 'need_expand' false in this case and because the gamma test itself
       * doesn't cause an expand to 8-bit for lower bit depths the colour must
       * be reduced to the correct range.
       */
      if (bit_depth < 8)
         background.gray &= (png_uint_16)((1U << bit_depth)-1);

      /* Grayscale input, we do not convert to RGB (TBD), so we must set the
       * background to gray - else libpng seems to fail.
       */
      background.red = background.green = background.blue = background.gray;
   }

   pos = safecat(name, sizeof name, pos, "gamma ");
   pos = safecatd(name, sizeof name, pos, file_gamma, 3);
   pos = safecat(name, sizeof name, pos, "->");
   pos = safecatd(name, sizeof name, pos, screen_gamma, 3);

   pos = safecat(name, sizeof name, pos, base);
   if (do_background < ALPHA_MODE_OFFSET)
   {
      /* Include the background color and gamma in the name: */
      pos = safecat(name, sizeof name, pos, "(");
      /* This assumes no expand gray->rgb - the current code won't handle that!
       */
      if (colour_type & PNG_COLOR_MASK_COLOR)
      {
         pos = safecatn(name, sizeof name, pos, background.red);
         pos = safecat(name, sizeof name, pos, ",");
         pos = safecatn(name, sizeof name, pos, background.green);
         pos = safecat(name, sizeof name, pos, ",");
         pos = safecatn(name, sizeof name, pos, background.blue);
      }
      else
         pos = safecatn(name, sizeof name, pos, background.gray);
      pos = safecat(name, sizeof name, pos, ")^");
      pos = safecatd(name, sizeof name, pos, bg, 3);
   }

   gamma_test(pm, colour_type, bit_depth, palette_number, interlace_type,
      file_gamma, screen_gamma, 0/*sBIT*/, 0, name, use_input_precision,
      0/*strip 16*/, expand_16, do_background, &background, bg);
}


static void
perform_gamma_composition_tests(png_modifier *pm, int do_background,
   int expand_16)
{
   png_byte colour_type = 0;
   png_byte bit_depth = 0;
   unsigned int palette_number = 0;

   /* Skip the non-alpha cases - there is no setting of a transparency colour at
    * present.
    *
    * TODO: incorrect; the palette case sets tRNS and, now RGB and gray do,
    * however the palette case fails miserably so is commented out below.
    */
   while (next_format(&colour_type, &bit_depth, &palette_number,
                      pm->test_lbg_gamma_composition, pm->test_tRNS))
      if ((colour_type & PNG_COLOR_MASK_ALPHA) != 0
#if 0 /* TODO: FIXME */
          /*TODO: FIXME: this should work */
          || colour_type == 3
#endif
          || (colour_type != 3 && palette_number != 0))
   {
      unsigned int i, j;

      /* Don't skip the i==j case here - it's relevant. */
      for (i=0; i<pm->ngamma_tests; ++i) for (j=0; j<pm->ngamma_tests; ++j)
      {
         gamma_composition_test(pm, colour_type, bit_depth, palette_number,
            pm->interlace_type, 1/pm->gammas[i], pm->gammas[j],
            pm->use_input_precision, do_background, expand_16);

         if (fail(pm))
            return;
      }
   }
}
#endif /* READ_BACKGROUND || READ_ALPHA_MODE */

static void
init_gamma_errors(png_modifier *pm)
{
   /* Use -1 to catch tests that were not actually run */
   pm->error_gray_2 = pm->error_gray_4 = pm->error_gray_8 = -1.;
   pm->error_color_8 = -1.;
   pm->error_indexed = -1.;
   pm->error_gray_16 = pm->error_color_16 = -1.;
}

static void
print_one(const char *leader, double err)
{
   if (err != -1.)
      printf(" %s %.5f\n", leader, err);
}

static void
summarize_gamma_errors(png_modifier *pm, png_const_charp who, int low_bit_depth,
   int indexed)
{
   fflush(stderr);

   if (who)
      printf("\nGamma correction with %s:\n", who);

   else
      printf("\nBasic gamma correction:\n");

   if (low_bit_depth)
   {
      print_one(" 2 bit gray: ", pm->error_gray_2);
      print_one(" 4 bit gray: ", pm->error_gray_4);
      print_one(" 8 bit gray: ", pm->error_gray_8);
      print_one(" 8 bit color:", pm->error_color_8);
      if (indexed)
         print_one(" indexed:    ", pm->error_indexed);
   }

   print_one("16 bit gray: ", pm->error_gray_16);
   print_one("16 bit color:", pm->error_color_16);

   fflush(stdout);
}

static void
perform_gamma_test(png_modifier *pm, int summary)
{
   /*TODO: remove this*/
   /* Save certain values for the temporary overrides below. */
   unsigned int calculations_use_input_precision =
      pm->calculations_use_input_precision;
#  ifdef PNG_READ_BACKGROUND_SUPPORTED
      double maxout8 = pm->maxout8;
#  endif

   /* First some arbitrary no-transform tests: */
   if (!pm->this.speed && pm->test_gamma_threshold)
   {
      perform_gamma_threshold_tests(pm);

      if (fail(pm))
         return;
   }

   /* Now some real transforms. */
   if (pm->test_gamma_transform)
   {
      if (summary)
      {
         fflush(stderr);
         printf("Gamma correction error summary\n\n");
         printf("The printed value is the maximum error in the pixel values\n");
         printf("calculated by the libpng gamma correction code.  The error\n");
         printf("is calculated as the difference between the output pixel\n");
         printf("value (always an integer) and the ideal value from the\n");
         printf("libpng specification (typically not an integer).\n\n");

         printf("Expect this value to be less than .5 for 8 bit formats,\n");
         printf("less than 1 for formats with fewer than 8 bits and a small\n");
         printf("number (typically less than 5) for the 16 bit formats.\n");
         printf("For performance reasons the value for 16 bit formats\n");
         printf("increases when the image file includes an sBIT chunk.\n");
         fflush(stdout);
      }

      init_gamma_errors(pm);
      /*TODO: remove this.  Necessary because the current libpng
       * implementation works in 8 bits:
       */
      if (pm->test_gamma_expand16)
         pm->calculations_use_input_precision = 1;
      perform_gamma_transform_tests(pm);
      if (!calculations_use_input_precision)
         pm->calculations_use_input_precision = 0;

      if (summary)
         summarize_gamma_errors(pm, 0/*who*/, 1/*low bit depth*/, 1/*indexed*/);

      if (fail(pm))
         return;
   }

   /* The sbit tests produce much larger errors: */
   if (pm->test_gamma_sbit)
   {
      init_gamma_errors(pm);
      perform_gamma_sbit_tests(pm);

      if (summary)
         summarize_gamma_errors(pm, "sBIT", pm->sbitlow < 8U, 1/*indexed*/);

      if (fail(pm))
         return;
   }

#ifdef DO_16BIT /* Should be READ_16BIT_SUPPORTED */
   if (pm->test_gamma_scale16)
   {
      /* The 16 to 8 bit strip operations: */
      init_gamma_errors(pm);
      perform_gamma_scale16_tests(pm);

      if (summary)
      {
         fflush(stderr);
         printf("\nGamma correction with 16 to 8 bit reduction:\n");
         printf(" 16 bit gray:  %.5f\n", pm->error_gray_16);
         printf(" 16 bit color: %.5f\n", pm->error_color_16);
         fflush(stdout);
      }

      if (fail(pm))
         return;
   }
#endif

#ifdef PNG_READ_BACKGROUND_SUPPORTED
   if (pm->test_gamma_background)
   {
      init_gamma_errors(pm);

      /*TODO: remove this.  Necessary because the current libpng
       * implementation works in 8 bits:
       */
      if (pm->test_gamma_expand16)
      {
         pm->calculations_use_input_precision = 1;
         pm->maxout8 = .499; /* because the 16 bit background is smashed */
      }
      perform_gamma_composition_tests(pm, PNG_BACKGROUND_GAMMA_UNIQUE,
         pm->test_gamma_expand16);
      if (!calculations_use_input_precision)
         pm->calculations_use_input_precision = 0;
      pm->maxout8 = maxout8;

      if (summary)
         summarize_gamma_errors(pm, "background", 1, 0/*indexed*/);

      if (fail(pm))
         return;
   }
#endif

#ifdef PNG_READ_ALPHA_MODE_SUPPORTED
   if (pm->test_gamma_alpha_mode)
   {
      int do_background;

      init_gamma_errors(pm);

      /*TODO: remove this.  Necessary because the current libpng
       * implementation works in 8 bits:
       */
      if (pm->test_gamma_expand16)
         pm->calculations_use_input_precision = 1;
      for (do_background = ALPHA_MODE_OFFSET + PNG_ALPHA_STANDARD;
         do_background <= ALPHA_MODE_OFFSET + PNG_ALPHA_BROKEN && !fail(pm);
         ++do_background)
         perform_gamma_composition_tests(pm, do_background,
            pm->test_gamma_expand16);
      if (!calculations_use_input_precision)
         pm->calculations_use_input_precision = 0;

      if (summary)
         summarize_gamma_errors(pm, "alpha mode", 1, 0/*indexed*/);

      if (fail(pm))
         return;
   }
#endif
}
#endif /* PNG_READ_GAMMA_SUPPORTED */
#endif /* PNG_READ_SUPPORTED */

/* INTERLACE MACRO VALIDATION */
/* This is copied verbatim from the specification, it is simply the pass
 * number in which each pixel in each 8x8 tile appears.  The array must
 * be indexed adam7[y][x] and notice that the pass numbers are based at
 * 1, not 0 - the base libpng uses.
 */
static const
png_byte adam7[8][8] =
{
   { 1,6,4,6,2,6,4,6 },
   { 7,7,7,7,7,7,7,7 },
   { 5,6,5,6,5,6,5,6 },
   { 7,7,7,7,7,7,7,7 },
   { 3,6,4,6,3,6,4,6 },
   { 7,7,7,7,7,7,7,7 },
   { 5,6,5,6,5,6,5,6 },
   { 7,7,7,7,7,7,7,7 }
};

/* This routine validates all the interlace support macros in png.h for
 * a variety of valid PNG widths and heights.  It uses a number of similarly
 * named internal routines that feed off the above array.
 */
static png_uint_32
png_pass_start_row(int pass)
{
   int x, y;
   ++pass;
   for (y=0; y<8; ++y) for (x=0; x<8; ++x) if (adam7[y][x] == pass)
      return y;
   return 0xf;
}

static png_uint_32
png_pass_start_col(int pass)
{
   int x, y;
   ++pass;
   for (x=0; x<8; ++x) for (y=0; y<8; ++y) if (adam7[y][x] == pass)
      return x;
   return 0xf;
}

static int
png_pass_row_shift(int pass)
{
   int x, y, base=(-1), inc=8;
   ++pass;
   for (y=0; y<8; ++y) for (x=0; x<8; ++x) if (adam7[y][x] == pass)
   {
      if (base == (-1))
         base = y;
      else if (base == y)
         {}
      else if (inc == y-base)
         base=y;
      else if (inc == 8)
         inc = y-base, base=y;
      else if (inc != y-base)
         return 0xff; /* error - more than one 'inc' value! */
   }

   if (base == (-1)) return 0xfe; /* error - no row in pass! */

   /* The shift is always 1, 2 or 3 - no pass has all the rows! */
   switch (inc)
   {
case 2: return 1;
case 4: return 2;
case 8: return 3;
default: break;
   }

   /* error - unrecognized 'inc' */
   return (inc << 8) + 0xfd;
}

static int
png_pass_col_shift(int pass)
{
   int x, y, base=(-1), inc=8;
   ++pass;
   for (x=0; x<8; ++x) for (y=0; y<8; ++y) if (adam7[y][x] == pass)
   {
      if (base == (-1))
         base = x;
      else if (base == x)
         {}
      else if (inc == x-base)
         base=x;
      else if (inc == 8)
         inc = x-base, base=x;
      else if (inc != x-base)
         return 0xff; /* error - more than one 'inc' value! */
   }

   if (base == (-1)) return 0xfe; /* error - no row in pass! */

   /* The shift is always 1, 2 or 3 - no pass has all the rows! */
   switch (inc)
   {
case 1: return 0; /* pass 7 has all the columns */
case 2: return 1;
case 4: return 2;
case 8: return 3;
default: break;
   }

   /* error - unrecognized 'inc' */
   return (inc << 8) + 0xfd;
}

static png_uint_32
png_row_from_pass_row(png_uint_32 yIn, int pass)
{
   /* By examination of the array: */
   switch (pass)
   {
case 0: return yIn * 8;
case 1: return yIn * 8;
case 2: return yIn * 8 + 4;
case 3: return yIn * 4;
case 4: return yIn * 4 + 2;
case 5: return yIn * 2;
case 6: return yIn * 2 + 1;
default: break;
   }

   return 0xff; /* bad pass number */
}

static png_uint_32
png_col_from_pass_col(png_uint_32 xIn, int pass)
{
   /* By examination of the array: */
   switch (pass)
   {
case 0: return xIn * 8;
case 1: return xIn * 8 + 4;
case 2: return xIn * 4;
case 3: return xIn * 4 + 2;
case 4: return xIn * 2;
case 5: return xIn * 2 + 1;
case 6: return xIn;
default: break;
   }

   return 0xff; /* bad pass number */
}

static int
png_row_in_interlace_pass(png_uint_32 y, int pass)
{
   /* Is row 'y' in pass 'pass'? */
   int x;
   y &= 7;
   ++pass;
   for (x=0; x<8; ++x) if (adam7[y][x] == pass)
      return 1;

   return 0;
}

static int
png_col_in_interlace_pass(png_uint_32 x, int pass)
{
   /* Is column 'x' in pass 'pass'? */
   int y;
   x &= 7;
   ++pass;
   for (y=0; y<8; ++y) if (adam7[y][x] == pass)
      return 1;

   return 0;
}

static png_uint_32
png_pass_rows(png_uint_32 height, int pass)
{
   png_uint_32 tiles = height>>3;
   png_uint_32 rows = 0;
   unsigned int x, y;

   height &= 7;
   ++pass;
   for (y=0; y<8; ++y) for (x=0; x<8; ++x) if (adam7[y][x] == pass)
   {
      rows += tiles;
      if (y < height) ++rows;
      break; /* i.e. break the 'x', column, loop. */
   }

   return rows;
}

static png_uint_32
png_pass_cols(png_uint_32 width, int pass)
{
   png_uint_32 tiles = width>>3;
   png_uint_32 cols = 0;
   unsigned int x, y;

   width &= 7;
   ++pass;
   for (x=0; x<8; ++x) for (y=0; y<8; ++y) if (adam7[y][x] == pass)
   {
      cols += tiles;
      if (x < width) ++cols;
      break; /* i.e. break the 'y', row, loop. */
   }

   return cols;
}

static void
perform_interlace_macro_validation(void)
{
   /* The macros to validate, first those that depend only on pass:
    *
    * PNG_PASS_START_ROW(pass)
    * PNG_PASS_START_COL(pass)
    * PNG_PASS_ROW_SHIFT(pass)
    * PNG_PASS_COL_SHIFT(pass)
    */
   int pass;

   for (pass=0; pass<7; ++pass)
   {
      png_uint_32 m, f, v;

      m = PNG_PASS_START_ROW(pass);
      f = png_pass_start_row(pass);
      if (m != f)
      {
         fprintf(stderr, "PNG_PASS_START_ROW(%d) = %u != %x\n", pass, m, f);
         exit(99);
      }

      m = PNG_PASS_START_COL(pass);
      f = png_pass_start_col(pass);
      if (m != f)
      {
         fprintf(stderr, "PNG_PASS_START_COL(%d) = %u != %x\n", pass, m, f);
         exit(99);
      }

      m = PNG_PASS_ROW_SHIFT(pass);
      f = png_pass_row_shift(pass);
      if (m != f)
      {
         fprintf(stderr, "PNG_PASS_ROW_SHIFT(%d) = %u != %x\n", pass, m, f);
         exit(99);
      }

      m = PNG_PASS_COL_SHIFT(pass);
      f = png_pass_col_shift(pass);
      if (m != f)
      {
         fprintf(stderr, "PNG_PASS_COL_SHIFT(%d) = %u != %x\n", pass, m, f);
         exit(99);
      }

      /* Macros that depend on the image or sub-image height too:
       *
       * PNG_PASS_ROWS(height, pass)
       * PNG_PASS_COLS(width, pass)
       * PNG_ROW_FROM_PASS_ROW(yIn, pass)
       * PNG_COL_FROM_PASS_COL(xIn, pass)
       * PNG_ROW_IN_INTERLACE_PASS(y, pass)
       * PNG_COL_IN_INTERLACE_PASS(x, pass)
       */
      for (v=0;;)
      {
         /* The first two tests overflow if the pass row or column is outside
          * the possible range for a 32-bit result.  In fact the values should
          * never be outside the range for a 31-bit result, but checking for 32
          * bits here ensures that if an app uses a bogus pass row or column
          * (just so long as it fits in a 32 bit integer) it won't get a
          * possibly dangerous overflow.
          */
         /* First the base 0 stuff: */
         if (v < png_pass_rows(0xFFFFFFFFU, pass))
         {
            m = PNG_ROW_FROM_PASS_ROW(v, pass);
            f = png_row_from_pass_row(v, pass);
            if (m != f)
            {
               fprintf(stderr, "PNG_ROW_FROM_PASS_ROW(%u, %d) = %u != %x\n",
                  v, pass, m, f);
               exit(99);
            }
         }

         if (v < png_pass_cols(0xFFFFFFFFU, pass))
         {
            m = PNG_COL_FROM_PASS_COL(v, pass);
            f = png_col_from_pass_col(v, pass);
            if (m != f)
            {
               fprintf(stderr, "PNG_COL_FROM_PASS_COL(%u, %d) = %u != %x\n",
                  v, pass, m, f);
               exit(99);
            }
         }

         m = PNG_ROW_IN_INTERLACE_PASS(v, pass);
         f = png_row_in_interlace_pass(v, pass);
         if (m != f)
         {
            fprintf(stderr, "PNG_ROW_IN_INTERLACE_PASS(%u, %d) = %u != %x\n",
               v, pass, m, f);
            exit(99);
         }

         m = PNG_COL_IN_INTERLACE_PASS(v, pass);
         f = png_col_in_interlace_pass(v, pass);
         if (m != f)
         {
            fprintf(stderr, "PNG_COL_IN_INTERLACE_PASS(%u, %d) = %u != %x\n",
               v, pass, m, f);
            exit(99);
         }

         /* Then the base 1 stuff: */
         ++v;
         m = PNG_PASS_ROWS(v, pass);
         f = png_pass_rows(v, pass);
         if (m != f)
         {
            fprintf(stderr, "PNG_PASS_ROWS(%u, %d) = %u != %x\n",
               v, pass, m, f);
            exit(99);
         }

         m = PNG_PASS_COLS(v, pass);
         f = png_pass_cols(v, pass);
         if (m != f)
         {
            fprintf(stderr, "PNG_PASS_COLS(%u, %d) = %u != %x\n",
               v, pass, m, f);
            exit(99);
         }

         /* Move to the next v - the stepping algorithm starts skipping
          * values above 1024.
          */
         if (v > 1024)
         {
            if (v == PNG_UINT_31_MAX)
               break;

            v = (v << 1) ^ v;
            if (v >= PNG_UINT_31_MAX)
               v = PNG_UINT_31_MAX-1;
         }
      }
   }
}

/* Test color encodings. These values are back-calculated from the published
 * chromaticities.  The values are accurate to about 14 decimal places; 15 are
 * given.  These values are much more accurate than the ones given in the spec,
 * which typically don't exceed 4 decimal places.  This allows testing of the
 * libpng code to its theoretical accuracy of 4 decimal places.  (If pngvalid
 * used the published errors the 'slack' permitted would have to be +/-.5E-4 or
 * more.)
 *
 * The png_modifier code assumes that encodings[0] is sRGB and treats it
 * specially: do not change the first entry in this list!
 */
static const color_encoding test_encodings[] =
{
/* sRGB: must be first in this list! */
/*gamma:*/ { 1/2.2,
/*red:  */ { 0.412390799265959, 0.212639005871510, 0.019330818715592 },
/*green:*/ { 0.357584339383878, 0.715168678767756, 0.119194779794626 },
/*blue: */ { 0.180480788401834, 0.072192315360734, 0.950532152249660} },
/* Kodak ProPhoto (wide gamut) */
/*gamma:*/ { 1/1.6 /*approximate: uses 1.8 power law compared to sRGB 2.4*/,
/*red:  */ { 0.797760489672303, 0.288071128229293, 0.000000000000000 },
/*green:*/ { 0.135185837175740, 0.711843217810102, 0.000000000000000 },
/*blue: */ { 0.031349349581525, 0.000085653960605, 0.825104602510460} },
/* Adobe RGB (1998) */
/*gamma:*/ { 1/(2+51./256),
/*red:  */ { 0.576669042910131, 0.297344975250536, 0.027031361386412 },
/*green:*/ { 0.185558237906546, 0.627363566255466, 0.070688852535827 },
/*blue: */ { 0.188228646234995, 0.075291458493998, 0.991337536837639} },
/* Adobe Wide Gamut RGB */
/*gamma:*/ { 1/(2+51./256),
/*red:  */ { 0.716500716779386, 0.258728243040113, 0.000000000000000 },
/*green:*/ { 0.101020574397477, 0.724682314948566, 0.051211818965388 },
/*blue: */ { 0.146774385252705, 0.016589442011321, 0.773892783545073} },
/* Fake encoding which selects just the green channel */
/*gamma:*/ { 1.45/2.2, /* the 'Mac' gamma */
/*red:  */ { 0.716500716779386, 0.000000000000000, 0.000000000000000 },
/*green:*/ { 0.101020574397477, 1.000000000000000, 0.051211818965388 },
/*blue: */ { 0.146774385252705, 0.000000000000000, 0.773892783545073} },
};

/* signal handler
 *
 * This attempts to trap signals and escape without crashing.  It needs a
 * context pointer so that it can throw an exception (call longjmp) to recover
 * from the condition; this is handled by making the png_modifier used by 'main'
 * into a global variable.
 */
static png_modifier pm;

static void signal_handler(int signum)
{

   size_t pos = 0;
   char msg[64];

   pos = safecat(msg, sizeof msg, pos, "caught signal: ");

   switch (signum)
   {
      case SIGABRT:
         pos = safecat(msg, sizeof msg, pos, "abort");
         break;

      case SIGFPE:
         pos = safecat(msg, sizeof msg, pos, "floating point exception");
         break;

      case SIGILL:
         pos = safecat(msg, sizeof msg, pos, "illegal instruction");
         break;

      case SIGINT:
         pos = safecat(msg, sizeof msg, pos, "interrupt");
         break;

      case SIGSEGV:
         pos = safecat(msg, sizeof msg, pos, "invalid memory access");
         break;

      case SIGTERM:
         pos = safecat(msg, sizeof msg, pos, "termination request");
         break;

      default:
         pos = safecat(msg, sizeof msg, pos, "unknown ");
         pos = safecatn(msg, sizeof msg, pos, signum);
         break;
   }

   store_log(&pm.this, NULL/*png_structp*/, msg, 1/*error*/);

   /* And finally throw an exception so we can keep going, unless this is
    * SIGTERM in which case stop now.
    */
   if (signum != SIGTERM)
   {
      struct exception_context *the_exception_context =
         &pm.this.exception_context;

      Throw &pm.this;
   }

   else
      exit(1);
}

/* main program */
int main(int argc, char **argv)
{
   int summary = 1;  /* Print the error summary at the end */
   int memstats = 0; /* Print memory statistics at the end */

   /* Create the given output file on success: */
   const char *touch = NULL;

   /* This is an array of standard gamma values (believe it or not I've seen
    * every one of these mentioned somewhere.)
    *
    * In the following list the most useful values are first!
    */
   static double
      gammas[]={2.2, 1.0, 2.2/1.45, 1.8, 1.5, 2.4, 2.5, 2.62, 2.9};

   /* This records the command and arguments: */
   size_t cp = 0;
   char command[1024];

   anon_context(&pm.this);

   gnu_volatile(summary)
   gnu_volatile(memstats)
   gnu_volatile(touch)

   /* Add appropriate signal handlers, just the ANSI specified ones: */
   signal(SIGABRT, signal_handler);
   signal(SIGFPE, signal_handler);
   signal(SIGILL, signal_handler);
   signal(SIGINT, signal_handler);
   signal(SIGSEGV, signal_handler);
   signal(SIGTERM, signal_handler);

#ifdef HAVE_FEENABLEEXCEPT
   /* Only required to enable FP exceptions on platforms where they start off
    * disabled; this is not necessary but if it is not done pngvalid will likely
    * end up ignoring FP conditions that other platforms fault.
    */
   feenableexcept(FE_DIVBYZERO | FE_INVALID | FE_OVERFLOW);
#endif

   modifier_init(&pm);

   /* Preallocate the image buffer, because we know how big it needs to be,
    * note that, for testing purposes, it is deliberately mis-aligned by tag
    * bytes either side.  All rows have an additional five bytes of padding for
    * overwrite checking.
    */
   store_ensure_image(&pm.this, NULL, 2, TRANSFORM_ROWMAX, TRANSFORM_HEIGHTMAX);

   /* Don't give argv[0], it's normally some horrible libtool string: */
   cp = safecat(command, sizeof command, cp, "pngvalid");

   /* Default to error on warning: */
   pm.this.treat_warnings_as_errors = 1;

   /* Default assume_16_bit_calculations appropriately; this tells the checking
    * code that 16-bit arithmetic is used for 8-bit samples when it would make a
    * difference.
    */
   pm.assume_16_bit_calculations = PNG_LIBPNG_VER >= 10700;

   /* Currently 16 bit expansion happens at the end of the pipeline, so the
    * calculations are done in the input bit depth not the output.
    *
    * TODO: fix this
    */
   pm.calculations_use_input_precision = 1U;

   /* Store the test gammas */
   pm.gammas = gammas;
   pm.ngammas = ARRAY_SIZE(gammas);
   pm.ngamma_tests = 0; /* default to off */

   /* Low bit depth gray images don't do well in the gamma tests, until
    * this is fixed turn them off for some gamma cases:
    */
#  ifdef PNG_WRITE_tRNS_SUPPORTED
      pm.test_tRNS = 1;
#  endif
   pm.test_lbg = PNG_LIBPNG_VER >= 10600;
   pm.test_lbg_gamma_threshold = 1;
   pm.test_lbg_gamma_transform = PNG_LIBPNG_VER >= 10600;
   pm.test_lbg_gamma_sbit = 1;
   pm.test_lbg_gamma_composition = PNG_LIBPNG_VER >= 10700;

   /* And the test encodings */
   pm.encodings = test_encodings;
   pm.nencodings = ARRAY_SIZE(test_encodings);

#  if PNG_LIBPNG_VER < 10700
      pm.sbitlow = 8U; /* because libpng doesn't do sBIT below 8! */
#  else
      pm.sbitlow = 1U;
#  endif

   /* The following allows results to pass if they correspond to anything in the
    * transformed range [input-.5,input+.5]; this is is required because of the
    * way libpng treates the 16_TO_8 flag when building the gamma tables in
    * releases up to 1.6.0.
    *
    * TODO: review this
    */
   pm.use_input_precision_16to8 = 1U;
   pm.use_input_precision_sbit = 1U; /* because libpng now rounds sBIT */

   /* Some default values (set the behavior for 'make check' here).
    * These values simply control the maximum error permitted in the gamma
    * transformations.  The practical limits for human perception are described
    * below (the setting for maxpc16), however for 8 bit encodings it isn't
    * possible to meet the accepted capabilities of human vision - i.e. 8 bit
    * images can never be good enough, regardless of encoding.
    */
   pm.maxout8 = .1;     /* Arithmetic error in *encoded* value */
   pm.maxabs8 = .00005; /* 1/20000 */
   pm.maxcalc8 = 1./255;  /* +/-1 in 8 bits for compose errors */
   pm.maxpc8 = .499;    /* I.e., .499% fractional error */
   pm.maxout16 = .499;  /* Error in *encoded* value */
   pm.maxabs16 = .00005;/* 1/20000 */
   pm.maxcalc16 =1./65535;/* +/-1 in 16 bits for compose errors */
#  if PNG_LIBPNG_VER < 10700
      pm.maxcalcG = 1./((1<<PNG_MAX_GAMMA_8)-1);
#  else
      pm.maxcalcG = 1./((1<<16)-1);
#  endif

   /* NOTE: this is a reasonable perceptual limit. We assume that humans can
    * perceive light level differences of 1% over a 100:1 range, so we need to
    * maintain 1 in 10000 accuracy (in linear light space), which is what the
    * following guarantees.  It also allows significantly higher errors at
    * higher 16 bit values, which is important for performance.  The actual
    * maximum 16 bit error is about +/-1.9 in the fixed point implementation but
    * this is only allowed for values >38149 by the following:
    */
   pm.maxpc16 = .005;   /* I.e., 1/200% - 1/20000 */

   /* Now parse the command line options. */
   while (--argc >= 1)
   {
      int catmore = 0; /* Set if the argument has an argument. */

      /* Record each argument for posterity: */
      cp = safecat(command, sizeof command, cp, " ");
      cp = safecat(command, sizeof command, cp, *++argv);

      if (strcmp(*argv, "-v") == 0)
         pm.this.verbose = 1;

      else if (strcmp(*argv, "-l") == 0)
         pm.log = 1;

      else if (strcmp(*argv, "-q") == 0)
         summary = pm.this.verbose = pm.log = 0;

      else if (strcmp(*argv, "-w") == 0 ||
               strcmp(*argv, "--strict") == 0)
         pm.this.treat_warnings_as_errors = 1; /* NOTE: this is the default! */

      else if (strcmp(*argv, "--nostrict") == 0)
         pm.this.treat_warnings_as_errors = 0;

      else if (strcmp(*argv, "--speed") == 0)
         pm.this.speed = 1, pm.ngamma_tests = pm.ngammas, pm.test_standard = 0,
            summary = 0;

      else if (strcmp(*argv, "--memory") == 0)
         memstats = 1;

      else if (strcmp(*argv, "--size") == 0)
         pm.test_size = 1;

      else if (strcmp(*argv, "--nosize") == 0)
         pm.test_size = 0;

      else if (strcmp(*argv, "--standard") == 0)
         pm.test_standard = 1;

      else if (strcmp(*argv, "--nostandard") == 0)
         pm.test_standard = 0;

      else if (strcmp(*argv, "--transform") == 0)
         pm.test_transform = 1;

      else if (strcmp(*argv, "--notransform") == 0)
         pm.test_transform = 0;

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
      else if (strncmp(*argv, "--transform-disable=",
         sizeof "--transform-disable") == 0)
         {
         pm.test_transform = 1;
         transform_disable(*argv + sizeof "--transform-disable");
         }

      else if (strncmp(*argv, "--transform-enable=",
         sizeof "--transform-enable") == 0)
         {
         pm.test_transform = 1;
         transform_enable(*argv + sizeof "--transform-enable");
         }
#endif /* PNG_READ_TRANSFORMS_SUPPORTED */

      else if (strcmp(*argv, "--gamma") == 0)
         {
         /* Just do two gamma tests here (2.2 and linear) for speed: */
         pm.ngamma_tests = 2U;
         pm.test_gamma_threshold = 1;
         pm.test_gamma_transform = 1;
         pm.test_gamma_sbit = 1;
         pm.test_gamma_scale16 = 1;
         pm.test_gamma_background = 1; /* composition */
         pm.test_gamma_alpha_mode = 1;
         }

      else if (strcmp(*argv, "--nogamma") == 0)
         pm.ngamma_tests = 0;

      else if (strcmp(*argv, "--gamma-threshold") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_threshold = 1;

      else if (strcmp(*argv, "--nogamma-threshold") == 0)
         pm.test_gamma_threshold = 0;

      else if (strcmp(*argv, "--gamma-transform") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_transform = 1;

      else if (strcmp(*argv, "--nogamma-transform") == 0)
         pm.test_gamma_transform = 0;

      else if (strcmp(*argv, "--gamma-sbit") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_sbit = 1;

      else if (strcmp(*argv, "--nogamma-sbit") == 0)
         pm.test_gamma_sbit = 0;

      else if (strcmp(*argv, "--gamma-16-to-8") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_scale16 = 1;

      else if (strcmp(*argv, "--nogamma-16-to-8") == 0)
         pm.test_gamma_scale16 = 0;

      else if (strcmp(*argv, "--gamma-background") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_background = 1;

      else if (strcmp(*argv, "--nogamma-background") == 0)
         pm.test_gamma_background = 0;

      else if (strcmp(*argv, "--gamma-alpha-mode") == 0)
         pm.ngamma_tests = 2U, pm.test_gamma_alpha_mode = 1;

      else if (strcmp(*argv, "--nogamma-alpha-mode") == 0)
         pm.test_gamma_alpha_mode = 0;

      else if (strcmp(*argv, "--expand16") == 0)
         pm.test_gamma_expand16 = 1;

      else if (strcmp(*argv, "--noexpand16") == 0)
         pm.test_gamma_expand16 = 0;

      else if (strcmp(*argv, "--low-depth-gray") == 0)
         pm.test_lbg = pm.test_lbg_gamma_threshold =
            pm.test_lbg_gamma_transform = pm.test_lbg_gamma_sbit =
            pm.test_lbg_gamma_composition = 1;

      else if (strcmp(*argv, "--nolow-depth-gray") == 0)
         pm.test_lbg = pm.test_lbg_gamma_threshold =
            pm.test_lbg_gamma_transform = pm.test_lbg_gamma_sbit =
            pm.test_lbg_gamma_composition = 0;

#     ifdef PNG_WRITE_tRNS_SUPPORTED
         else if (strcmp(*argv, "--tRNS") == 0)
            pm.test_tRNS = 1;
#     endif

      else if (strcmp(*argv, "--notRNS") == 0)
         pm.test_tRNS = 0;

      else if (strcmp(*argv, "--more-gammas") == 0)
         pm.ngamma_tests = 3U;

      else if (strcmp(*argv, "--all-gammas") == 0)
         pm.ngamma_tests = pm.ngammas;

      else if (strcmp(*argv, "--progressive-read") == 0)
         pm.this.progressive = 1;

      else if (strcmp(*argv, "--use-update-info") == 0)
         ++pm.use_update_info; /* Can call multiple times */

      else if (strcmp(*argv, "--interlace") == 0)
      {
#        if CAN_WRITE_INTERLACE
            pm.interlace_type = PNG_INTERLACE_ADAM7;
#        else /* !CAN_WRITE_INTERLACE */
            fprintf(stderr, "pngvalid: no write interlace support\n");
            return SKIP;
#        endif /* !CAN_WRITE_INTERLACE */
      }

      else if (strcmp(*argv, "--use-input-precision") == 0)
         pm.use_input_precision = 1U;

      else if (strcmp(*argv, "--use-calculation-precision") == 0)
         pm.use_input_precision = 0;

      else if (strcmp(*argv, "--calculations-use-input-precision") == 0)
         pm.calculations_use_input_precision = 1U;

      else if (strcmp(*argv, "--assume-16-bit-calculations") == 0)
         pm.assume_16_bit_calculations = 1U;

      else if (strcmp(*argv, "--calculations-follow-bit-depth") == 0)
         pm.calculations_use_input_precision =
            pm.assume_16_bit_calculations = 0;

      else if (strcmp(*argv, "--exhaustive") == 0)
         pm.test_exhaustive = 1;

      else if (argc > 1 && strcmp(*argv, "--sbitlow") == 0)
         --argc, pm.sbitlow = (png_byte)atoi(*++argv), catmore = 1;

      else if (argc > 1 && strcmp(*argv, "--touch") == 0)
         --argc, touch = *++argv, catmore = 1;

      else if (argc > 1 && strncmp(*argv, "--max", 5) == 0)
      {
         --argc;

         if (strcmp(5+*argv, "abs8") == 0)
            pm.maxabs8 = atof(*++argv);

         else if (strcmp(5+*argv, "abs16") == 0)
            pm.maxabs16 = atof(*++argv);

         else if (strcmp(5+*argv, "calc8") == 0)
            pm.maxcalc8 = atof(*++argv);

         else if (strcmp(5+*argv, "calc16") == 0)
            pm.maxcalc16 = atof(*++argv);

         else if (strcmp(5+*argv, "out8") == 0)
            pm.maxout8 = atof(*++argv);

         else if (strcmp(5+*argv, "out16") == 0)
            pm.maxout16 = atof(*++argv);

         else if (strcmp(5+*argv, "pc8") == 0)
            pm.maxpc8 = atof(*++argv);

         else if (strcmp(5+*argv, "pc16") == 0)
            pm.maxpc16 = atof(*++argv);

         else
         {
            fprintf(stderr, "pngvalid: %s: unknown 'max' option\n", *argv);
            exit(99);
         }

         catmore = 1;
      }

      else if (strcmp(*argv, "--log8") == 0)
         --argc, pm.log8 = atof(*++argv), catmore = 1;

      else if (strcmp(*argv, "--log16") == 0)
         --argc, pm.log16 = atof(*++argv), catmore = 1;

#ifdef PNG_SET_OPTION_SUPPORTED
      else if (strncmp(*argv, "--option=", 9) == 0)
      {
         /* Syntax of the argument is <option>:{on|off} */
         const char *arg = 9+*argv;
         unsigned char option=0, setting=0;

#ifdef PNG_ARM_NEON
         if (strncmp(arg, "arm-neon:", 9) == 0)
            option = PNG_ARM_NEON, arg += 9;

         else
#endif
#ifdef PNG_EXTENSIONS
         if (strncmp(arg, "extensions:", 11) == 0)
            option = PNG_EXTENSIONS, arg += 11;

         else
#endif
#ifdef PNG_MAXIMUM_INFLATE_WINDOW
         if (strncmp(arg, "max-inflate-window:", 19) == 0)
            option = PNG_MAXIMUM_INFLATE_WINDOW, arg += 19;

         else
#endif
         {
            fprintf(stderr, "pngvalid: %s: %s: unknown option\n", *argv, arg);
            exit(99);
         }

         if (strcmp(arg, "off") == 0)
            setting = PNG_OPTION_OFF;

         else if (strcmp(arg, "on") == 0)
            setting = PNG_OPTION_ON;

         else
         {
            fprintf(stderr,
               "pngvalid: %s: %s: unknown setting (use 'on' or 'off')\n",
               *argv, arg);
            exit(99);
         }

         pm.this.options[pm.this.noptions].option = option;
         pm.this.options[pm.this.noptions++].setting = setting;
      }
#endif /* PNG_SET_OPTION_SUPPORTED */

      else
      {
         fprintf(stderr, "pngvalid: %s: unknown argument\n", *argv);
         exit(99);
      }

      if (catmore) /* consumed an extra *argv */
      {
         cp = safecat(command, sizeof command, cp, " ");
         cp = safecat(command, sizeof command, cp, *argv);
      }
   }

   /* If pngvalid is run with no arguments default to a reasonable set of the
    * tests.
    */
   if (pm.test_standard == 0 && pm.test_size == 0 && pm.test_transform == 0 &&
      pm.ngamma_tests == 0)
   {
      /* Make this do all the tests done in the test shell scripts with the same
       * parameters, where possible.  The limitation is that all the progressive
       * read and interlace stuff has to be done in separate runs, so only the
       * basic 'standard' and 'size' tests are done.
       */
      pm.test_standard = 1;
      pm.test_size = 1;
      pm.test_transform = 1;
      pm.ngamma_tests = 2U;
   }

   if (pm.ngamma_tests > 0 &&
      pm.test_gamma_threshold == 0 && pm.test_gamma_transform == 0 &&
      pm.test_gamma_sbit == 0 && pm.test_gamma_scale16 == 0 &&
      pm.test_gamma_background == 0 && pm.test_gamma_alpha_mode == 0)
   {
      pm.test_gamma_threshold = 1;
      pm.test_gamma_transform = 1;
      pm.test_gamma_sbit = 1;
      pm.test_gamma_scale16 = 1;
      pm.test_gamma_background = 1;
      pm.test_gamma_alpha_mode = 1;
   }

   else if (pm.ngamma_tests == 0)
   {
      /* Nothing to test so turn everything off: */
      pm.test_gamma_threshold = 0;
      pm.test_gamma_transform = 0;
      pm.test_gamma_sbit = 0;
      pm.test_gamma_scale16 = 0;
      pm.test_gamma_background = 0;
      pm.test_gamma_alpha_mode = 0;
   }

   Try
   {
      /* Make useful base images */
      make_transform_images(&pm);

      /* Perform the standard and gamma tests. */
      if (pm.test_standard)
      {
         perform_interlace_macro_validation();
         perform_formatting_test(&pm.this);
#        ifdef PNG_READ_SUPPORTED
            perform_standard_test(&pm);
#        endif
         perform_error_test(&pm);
      }

      /* Various oddly sized images: */
      if (pm.test_size)
      {
         make_size_images(&pm.this);
#        ifdef PNG_READ_SUPPORTED
            perform_size_test(&pm);
#        endif
      }

#ifdef PNG_READ_TRANSFORMS_SUPPORTED
      /* Combinatorial transforms: */
      if (pm.test_transform)
         perform_transform_test(&pm);
#endif /* PNG_READ_TRANSFORMS_SUPPORTED */

#ifdef PNG_READ_GAMMA_SUPPORTED
      if (pm.ngamma_tests > 0)
         perform_gamma_test(&pm, summary);
#endif
   }

   Catch_anonymous
   {
      fprintf(stderr, "pngvalid: test aborted (probably failed in cleanup)\n");
      if (!pm.this.verbose)
      {
         if (pm.this.error[0] != 0)
            fprintf(stderr, "pngvalid: first error: %s\n", pm.this.error);

         fprintf(stderr, "pngvalid: run with -v to see what happened\n");
      }
      exit(1);
   }

   if (summary)
   {
      printf("%s: %s (%s point arithmetic)\n",
         (pm.this.nerrors || (pm.this.treat_warnings_as_errors &&
            pm.this.nwarnings)) ? "FAIL" : "PASS",
         command,
#if defined(PNG_FLOATING_ARITHMETIC_SUPPORTED) || PNG_LIBPNG_VER < 10500
         "floating"
#else
         "fixed"
#endif
         );
   }

   if (memstats)
   {
      printf("Allocated memory statistics (in bytes):\n"
         "\tread  %lu maximum single, %lu peak, %lu total\n"
         "\twrite %lu maximum single, %lu peak, %lu total\n",
         (unsigned long)pm.this.read_memory_pool.max_max,
         (unsigned long)pm.this.read_memory_pool.max_limit,
         (unsigned long)pm.this.read_memory_pool.max_total,
         (unsigned long)pm.this.write_memory_pool.max_max,
         (unsigned long)pm.this.write_memory_pool.max_limit,
         (unsigned long)pm.this.write_memory_pool.max_total);
   }

   /* Do this here to provoke memory corruption errors in memory not directly
    * allocated by libpng - not a complete test, but better than nothing.
    */
   store_delete(&pm.this);

   /* Error exit if there are any errors, and maybe if there are any
    * warnings.
    */
   if (pm.this.nerrors || (pm.this.treat_warnings_as_errors &&
       pm.this.nwarnings))
   {
      if (!pm.this.verbose)
         fprintf(stderr, "pngvalid: %s\n", pm.this.error);

      fprintf(stderr, "pngvalid: %d errors, %d warnings\n", pm.this.nerrors,
          pm.this.nwarnings);

      exit(1);
   }

   /* Success case. */
   if (touch != NULL)
   {
      FILE *fsuccess = fopen(touch, "wt");

      if (fsuccess != NULL)
      {
         int error = 0;
         fprintf(fsuccess, "PNG validation succeeded\n");
         fflush(fsuccess);
         error = ferror(fsuccess);

         if (fclose(fsuccess) || error)
         {
            fprintf(stderr, "%s: write failed\n", touch);
            exit(1);
         }
      }

      else
      {
         fprintf(stderr, "%s: open failed\n", touch);
         exit(1);
      }
   }

   /* This is required because some very minimal configurations do not use it:
    */
   UNUSED(fail)
   return 0;
}
#else /* write or low level APIs not supported */
int main(void)
{
   fprintf(stderr,
      "pngvalid: no low level write support in libpng, all tests skipped\n");
   /* So the test is skipped: */
   return SKIP;
}
#endif

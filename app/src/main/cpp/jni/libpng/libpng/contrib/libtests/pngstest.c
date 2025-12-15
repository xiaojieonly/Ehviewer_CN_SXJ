/*-
 * pngstest.c
 *
 * Last changed in libpng 1.6.31 [July 27, 2017]
 * Copyright (c) 2013-2017 John Cunningham Bowler
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Test for the PNG 'simplified' APIs.
 */
#define _ISOC90_SOURCE 1
#define MALLOC_CHECK_ 2/*glibc facility: turn on debugging*/

#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <ctype.h>
#include <math.h>

#if defined(HAVE_CONFIG_H) && !defined(PNG_NO_CONFIG_H)
#  include <config.h>
#endif

/* Define the following to use this test against your installed libpng, rather
 * than the one being built here:
 */
#ifdef PNG_FREESTANDING_TESTS
#  include <png.h>
#else
#  include "../../png.h"
#endif

/* 1.6.1 added support for the configure test harness, which uses 77 to indicate
 * a skipped test, in earlier versions we need to succeed on a skipped test, so:
 */
#if PNG_LIBPNG_VER >= 10601 && defined(HAVE_CONFIG_H)
#  define SKIP 77
#else
#  define SKIP 0
#endif

#ifdef PNG_SIMPLIFIED_READ_SUPPORTED /* Else nothing can be done */
#include "../tools/sRGB.h"

/* KNOWN ISSUES
 *
 * These defines switch on alternate algorithms for format conversions to match
 * the current libpng implementation; they are set to allow pngstest to pass
 * even though libpng is producing answers that are not as correct as they
 * should be.
 */
#define ALLOW_UNUSED_GPC 0
   /* If true include unused static GPC functions and declare an external array
    * of them to hide the fact that they are unused.  This is for development
    * use while testing the correct function to use to take into account libpng
    * misbehavior, such as using a simple power law to correct sRGB to linear.
    */

/* The following is to support direct compilation of this file as C++ */
#ifdef __cplusplus
#  define voidcast(type, value) static_cast<type>(value)
#  define aligncastconst(type, value) \
      static_cast<type>(static_cast<const void*>(value))
#else
#  define voidcast(type, value) (value)
#  define aligncastconst(type, value) ((const void*)(value))
#endif /* __cplusplus */

/* During parallel runs of pngstest each temporary file needs a unique name,
 * this is used to permit uniqueness using a command line argument which can be
 * up to 22 characters long.
 */
static char tmpf[23] = "TMP";

/* Generate random bytes.  This uses a boring repeatable algorithm and it
 * is implemented here so that it gives the same set of numbers on every
 * architecture.  It's a linear congruential generator (Knuth or Sedgewick
 * "Algorithms") but it comes from the 'feedback taps' table in Horowitz and
 * Hill, "The Art of Electronics".
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

static png_uint_32 color_seed[2];

static void
reseed(void)
{
   color_seed[0] = 0x12345678U;
   color_seed[1] = 0x9abcdefU;
}

static void
random_color(png_colorp color)
{
   make_random_bytes(color_seed, color, sizeof *color);
}

/* Math support - neither Cygwin nor Visual Studio have C99 support and we need
 * a predictable rounding function, so make one here:
 */
static double
closestinteger(double x)
{
   return floor(x + .5);
}

/* Cast support: remove GCC whines. */
static png_byte
u8d(double d)
{
   d = closestinteger(d);
   return (png_byte)d;
}

static png_uint_16
u16d(double d)
{
   d = closestinteger(d);
   return (png_uint_16)d;
}

/* sRGB support: use exact calculations rounded to the nearest int, see the
 * fesetround() call in main().  sRGB_to_d optimizes the 8 to 16-bit conversion.
 */
static double sRGB_to_d[256];
static double g22_to_d[256];

static void
init_sRGB_to_d(void)
{
   int i;

   sRGB_to_d[0] = 0;
   for (i=1; i<255; ++i)
      sRGB_to_d[i] = linear_from_sRGB(i/255.);
   sRGB_to_d[255] = 1;

   g22_to_d[0] = 0;
   for (i=1; i<255; ++i)
      g22_to_d[i] = pow(i/255., 1/.45455);
   g22_to_d[255] = 1;
}

static png_byte
sRGB(double linear /*range 0.0 .. 1.0*/)
{
   return u8d(255 * sRGB_from_linear(linear));
}

static png_byte
isRGB(int fixed_linear)
{
   return sRGB(fixed_linear / 65535.);
}

#if 0 /* not used */
static png_byte
unpremultiply(int component, int alpha)
{
   if (alpha <= component)
      return 255; /* Arbitrary, but consistent with the libpng code */

   else if (alpha >= 65535)
      return isRGB(component);

   else
      return sRGB((double)component / alpha);
}
#endif

static png_uint_16
ilinear(int fixed_srgb)
{
   return u16d(65535 * sRGB_to_d[fixed_srgb]);
}

static png_uint_16
ilineara(int fixed_srgb, int alpha)
{
   return u16d((257 * alpha) * sRGB_to_d[fixed_srgb]);
}

static png_uint_16
ilinear_g22(int fixed_srgb)
{
   return u16d(65535 * g22_to_d[fixed_srgb]);
}

#if ALLOW_UNUSED_GPC
static png_uint_16
ilineara_g22(int fixed_srgb, int alpha)
{
   return u16d((257 * alpha) * g22_to_d[fixed_srgb]);
}
#endif

static double
YfromRGBint(int ir, int ig, int ib)
{
   double r = ir;
   double g = ig;
   double b = ib;
   return YfromRGB(r, g, b);
}

#if 0 /* unused */
/* The error that results from using a 2.2 power law in place of the correct
 * sRGB transform, given an 8-bit value which might be either sRGB or power-law.
 */
static int
power_law_error8(int value)
{
   if (value > 0 && value < 255)
   {
      double vd = value / 255.;
      double e = fabs(
         pow(sRGB_to_d[value], 1/2.2) - sRGB_from_linear(pow(vd, 2.2)));

      /* Always allow an extra 1 here for rounding errors */
      e = 1+floor(255 * e);
      return (int)e;
   }

   return 0;
}

static int error_in_sRGB_roundtrip = 56; /* by experiment */
static int
power_law_error16(int value)
{
   if (value > 0 && value < 65535)
   {
      /* Round trip the value through an 8-bit representation but using
       * non-matching to/from conversions.
       */
      double vd = value / 65535.;
      double e = fabs(
         pow(sRGB_from_linear(vd), 2.2) - linear_from_sRGB(pow(vd, 1/2.2)));

      /* Always allow an extra 1 here for rounding errors */
      e = error_in_sRGB_roundtrip+floor(65535 * e);
      return (int)e;
   }

   return 0;
}

static int
compare_8bit(int v1, int v2, int error_limit, int multiple_algorithms)
{
   int e = abs(v1-v2);
   int ev1, ev2;

   if (e <= error_limit)
      return 1;

   if (!multiple_algorithms)
      return 0;

   ev1 = power_law_error8(v1);
   if (e <= ev1)
      return 1;

   ev2 = power_law_error8(v2);
   if (e <= ev2)
      return 1;

   return 0;
}

static int
compare_16bit(int v1, int v2, int error_limit, int multiple_algorithms)
{
   int e = abs(v1-v2);
   int ev1, ev2;

   if (e <= error_limit)
      return 1;

   /* "multiple_algorithms" in this case means that a color-map has been
    * involved somewhere, so we can deduce that the values were forced to 8-bit
    * (like the via_linear case for 8-bit.)
    */
   if (!multiple_algorithms)
      return 0;

   ev1 = power_law_error16(v1);
   if (e <= ev1)
      return 1;

   ev2 = power_law_error16(v2);
   if (e <= ev2)
      return 1;

   return 0;
}
#endif /* unused */

#define USE_FILE 1       /* else memory */
#define USE_STDIO 2      /* else use file name */
#define STRICT 4         /* fail on warnings too */
#define VERBOSE 8
#define KEEP_TMPFILES 16 /* else delete temporary files */
#define KEEP_GOING 32
#define ACCUMULATE 64
#define FAST_WRITE 128
#define sRGB_16BIT 256
#define NO_RESEED  512   /* do not reseed on each new file */
#define GBG_ERROR 1024   /* do not ignore the gamma+background_rgb_to_gray
                          * libpng warning. */

static void
print_opts(png_uint_32 opts)
{
   if (opts & USE_FILE)
      printf(" --file");
   if (opts & USE_STDIO)
      printf(" --stdio");
   if (!(opts & STRICT))
      printf(" --nostrict");
   if (opts & VERBOSE)
      printf(" --verbose");
   if (opts & KEEP_TMPFILES)
      printf(" --preserve");
   if (opts & KEEP_GOING)
      printf(" --keep-going");
   if (opts & ACCUMULATE)
      printf(" --accumulate");
   if (!(opts & FAST_WRITE)) /* --fast is currently the default */
      printf(" --slow");
   if (opts & sRGB_16BIT)
      printf(" --sRGB-16bit");
   if (opts & NO_RESEED)
      printf(" --noreseed");
#if PNG_LIBPNG_VER < 10700 /* else on by default */
   if (opts & GBG_ERROR)
      printf(" --fault-gbg-warning");
#endif
}

#define FORMAT_NO_CHANGE 0x80000000 /* additional flag */

/* A name table for all the formats - defines the format of the '+' arguments to
 * pngstest.
 */
#define FORMAT_COUNT 64
#define FORMAT_MASK 0x3f
static const char * const format_names[FORMAT_COUNT] =
{
   "sRGB-gray",
   "sRGB-gray+alpha",
   "sRGB-rgb",
   "sRGB-rgb+alpha",
   "linear-gray",
   "linear-gray+alpha",
   "linear-rgb",
   "linear-rgb+alpha",

   "color-mapped-sRGB-gray",
   "color-mapped-sRGB-gray+alpha",
   "color-mapped-sRGB-rgb",
   "color-mapped-sRGB-rgb+alpha",
   "color-mapped-linear-gray",
   "color-mapped-linear-gray+alpha",
   "color-mapped-linear-rgb",
   "color-mapped-linear-rgb+alpha",

   "sRGB-gray",
   "sRGB-gray+alpha",
   "sRGB-bgr",
   "sRGB-bgr+alpha",
   "linear-gray",
   "linear-gray+alpha",
   "linear-bgr",
   "linear-bgr+alpha",

   "color-mapped-sRGB-gray",
   "color-mapped-sRGB-gray+alpha",
   "color-mapped-sRGB-bgr",
   "color-mapped-sRGB-bgr+alpha",
   "color-mapped-linear-gray",
   "color-mapped-linear-gray+alpha",
   "color-mapped-linear-bgr",
   "color-mapped-linear-bgr+alpha",

   "sRGB-gray",
   "alpha+sRGB-gray",
   "sRGB-rgb",
   "alpha+sRGB-rgb",
   "linear-gray",
   "alpha+linear-gray",
   "linear-rgb",
   "alpha+linear-rgb",

   "color-mapped-sRGB-gray",
   "color-mapped-alpha+sRGB-gray",
   "color-mapped-sRGB-rgb",
   "color-mapped-alpha+sRGB-rgb",
   "color-mapped-linear-gray",
   "color-mapped-alpha+linear-gray",
   "color-mapped-linear-rgb",
   "color-mapped-alpha+linear-rgb",

   "sRGB-gray",
   "alpha+sRGB-gray",
   "sRGB-bgr",
   "alpha+sRGB-bgr",
   "linear-gray",
   "alpha+linear-gray",
   "linear-bgr",
   "alpha+linear-bgr",

   "color-mapped-sRGB-gray",
   "color-mapped-alpha+sRGB-gray",
   "color-mapped-sRGB-bgr",
   "color-mapped-alpha+sRGB-bgr",
   "color-mapped-linear-gray",
   "color-mapped-alpha+linear-gray",
   "color-mapped-linear-bgr",
   "color-mapped-alpha+linear-bgr",
};

/* Decode an argument to a format number. */
static png_uint_32
formatof(const char *arg)
{
   char *ep;
   unsigned long format = strtoul(arg, &ep, 0);

   if (ep > arg && *ep == 0 && format < FORMAT_COUNT)
      return (png_uint_32)format;

   else for (format=0; format < FORMAT_COUNT; ++format)
   {
      if (strcmp(format_names[format], arg) == 0)
         return (png_uint_32)format;
   }

   fprintf(stderr, "pngstest: format name '%s' invalid\n", arg);
   return FORMAT_COUNT;
}

/* Bitset/test functions for formats */
#define FORMAT_SET_COUNT (FORMAT_COUNT / 32)
typedef struct
{
   png_uint_32 bits[FORMAT_SET_COUNT];
}
format_list;

static void format_init(format_list *pf)
{
   int i;
   for (i=0; i<FORMAT_SET_COUNT; ++i)
      pf->bits[i] = 0; /* All off */
}

#if 0 /* currently unused */
static void format_clear(format_list *pf)
{
   int i;
   for (i=0; i<FORMAT_SET_COUNT; ++i)
      pf->bits[i] = 0;
}
#endif

static int format_is_initial(format_list *pf)
{
   int i;
   for (i=0; i<FORMAT_SET_COUNT; ++i)
      if (pf->bits[i] != 0)
         return 0;

   return 1;
}

static int format_set(format_list *pf, png_uint_32 format)
{
   if (format < FORMAT_COUNT)
      return pf->bits[format >> 5] |= ((png_uint_32)1) << (format & 31);

   return 0;
}

#if 0 /* currently unused */
static int format_unset(format_list *pf, png_uint_32 format)
{
   if (format < FORMAT_COUNT)
      return pf->bits[format >> 5] &= ~((png_uint_32)1) << (format & 31);

   return 0;
}
#endif

static int format_isset(format_list *pf, png_uint_32 format)
{
   return format < FORMAT_COUNT &&
      (pf->bits[format >> 5] & (((png_uint_32)1) << (format & 31))) != 0;
}

static void format_default(format_list *pf, int redundant)
{
   if (redundant)
   {
      int i;

      /* set everything, including flags that are pointless */
      for (i=0; i<FORMAT_SET_COUNT; ++i)
         pf->bits[i] = ~(png_uint_32)0;
   }

   else
   {
      png_uint_32 f;

      for (f=0; f<FORMAT_COUNT; ++f)
      {
         /* Eliminate redundant and unsupported settings. */
#        ifdef PNG_FORMAT_BGR_SUPPORTED
            /* BGR is meaningless if no color: */
            if ((f & PNG_FORMAT_FLAG_COLOR) == 0 &&
               (f & PNG_FORMAT_FLAG_BGR) != 0)
#        else
            if ((f & 0x10U/*HACK: fixed value*/) != 0)
#        endif
            continue;

         /* AFIRST is meaningless if no alpha: */
#        ifdef PNG_FORMAT_AFIRST_SUPPORTED
            if ((f & PNG_FORMAT_FLAG_ALPHA) == 0 &&
               (f & PNG_FORMAT_FLAG_AFIRST) != 0)
#        else
            if ((f & 0x20U/*HACK: fixed value*/) != 0)
#        endif
            continue;

         format_set(pf, f);
      }
   }
}

/* THE Image STRUCTURE */
/* The super-class of a png_image, contains the decoded image plus the input
 * data necessary to re-read the file with a different format.
 */
typedef struct
{
   png_image   image;
   png_uint_32 opts;
   const char *file_name;
   int         stride_extra;
   FILE       *input_file;
   png_voidp   input_memory;
   size_t      input_memory_size;
   png_bytep   buffer;
   ptrdiff_t   stride;
   size_t      bufsize;
   size_t      allocsize;
   char        tmpfile_name[32];
   png_uint_16 colormap[256*4];
}
Image;

/* Initializer: also sets the permitted error limit for 16-bit operations. */
static void
newimage(Image *image)
{
   memset(image, 0, sizeof *image);
}

/* Reset the image to be read again - only needs to rewind the FILE* at present.
 */
static void
resetimage(Image *image)
{
   if (image->input_file != NULL)
      rewind(image->input_file);
}

/* Free the image buffer; the buffer is re-used on a re-read, this is just for
 * cleanup.
 */
static void
freebuffer(Image *image)
{
   if (image->buffer) free(image->buffer);
   image->buffer = NULL;
   image->bufsize = 0;
   image->allocsize = 0;
}

/* Delete function; cleans out all the allocated data and the temporary file in
 * the image.
 */
static void
freeimage(Image *image)
{
   freebuffer(image);
   png_image_free(&image->image);

   if (image->input_file != NULL)
   {
      fclose(image->input_file);
      image->input_file = NULL;
   }

   if (image->input_memory != NULL)
   {
      free(image->input_memory);
      image->input_memory = NULL;
      image->input_memory_size = 0;
   }

   if (image->tmpfile_name[0] != 0 && (image->opts & KEEP_TMPFILES) == 0)
   {
      (void)remove(image->tmpfile_name);
      image->tmpfile_name[0] = 0;
   }
}

/* This is actually a re-initializer; allows an image structure to be re-used by
 * freeing everything that relates to an old image.
 */
static void initimage(Image *image, png_uint_32 opts, const char *file_name,
   int stride_extra)
{
   freeimage(image);
   memset(&image->image, 0, sizeof image->image);
   image->opts = opts;
   image->file_name = file_name;
   image->stride_extra = stride_extra;
}

/* Make sure the image buffer is big enough; allows re-use of the buffer if the
 * image is re-read.
 */
#define BUFFER_INIT8 73
static void
allocbuffer(Image *image)
{
   size_t size = PNG_IMAGE_BUFFER_SIZE(image->image, image->stride);

   if (size+32 > image->bufsize)
   {
      freebuffer(image);
      image->buffer = voidcast(png_bytep, malloc(size+32));
      if (image->buffer == NULL)
      {
         fflush(stdout);
         fprintf(stderr,
            "simpletest: out of memory allocating %lu(+32) byte buffer\n",
            (unsigned long)size);
         exit(1);
      }
      image->bufsize = size+32;
   }

   memset(image->buffer, 95, image->bufsize);
   memset(image->buffer+16, BUFFER_INIT8, size);
   image->allocsize = size;
}

/* Make sure 16 bytes match the given byte. */
static int
check16(png_const_bytep bp, int b)
{
   int i = 16;

   do
      if (*bp != b) return 1;
   while (--i);

   return 0;
}

/* Check for overwrite in the image buffer. */
static void
checkbuffer(Image *image, const char *arg)
{
   if (check16(image->buffer, 95))
   {
      fflush(stdout);
      fprintf(stderr, "%s: overwrite at start of image buffer\n", arg);
      exit(1);
   }

   if (check16(image->buffer+16+image->allocsize, 95))
   {
      fflush(stdout);
      fprintf(stderr, "%s: overwrite at end of image buffer\n", arg);
      exit(1);
   }
}

/* ERROR HANDLING */
/* Log a terminal error, also frees the libpng part of the image if necessary.
 */
static int
logerror(Image *image, const char *a1, const char *a2, const char *a3)
{
   fflush(stdout);
   if (image->image.warning_or_error)
      fprintf(stderr, "%s%s%s: %s\n", a1, a2, a3, image->image.message);

   else
      fprintf(stderr, "%s%s%s\n", a1, a2, a3);

   if (image->image.opaque != NULL)
   {
      fprintf(stderr, "%s: image opaque pointer non-NULL on error\n",
         image->file_name);
      png_image_free(&image->image);
   }

   return 0;
}

/* Log an error and close a file (just a utility to do both things in one
 * function call.)
 */
static int
logclose(Image *image, FILE *f, const char *name, const char *operation)
{
   int e = errno;

   fclose(f);
   return logerror(image, name, operation, strerror(e));
}

/* Make sure the png_image has been freed - validates that libpng is doing what
 * the spec says and freeing the image.
 */
static int
checkopaque(Image *image)
{
   if (image->image.opaque != NULL)
   {
      png_image_free(&image->image);
      return logerror(image, image->file_name, ": opaque not NULL", "");
   }

   /* Separate out the gamma+background_rgb_to_gray warning because it may
    * produce opaque component errors:
    */
   else if (image->image.warning_or_error != 0 &&
            (strcmp(image->image.message,
               "libpng does not support gamma+background+rgb_to_gray") == 0 ?
                  (image->opts & GBG_ERROR) != 0 : (image->opts & STRICT) != 0))
      return logerror(image, image->file_name, (image->opts & GBG_ERROR) != 0 ?
                      " --fault-gbg-warning" : " --strict", "");

   else
      return 1;
}

/* IMAGE COMPARISON/CHECKING */
/* Compare the pixels of two images, which should be the same but aren't.  The
 * images must have been checked for a size match.
 */
typedef struct
{
   /* The components, for grayscale images the gray value is in 'g' and if alpha
    * is not present 'a' is set to 255 or 65535 according to format.
    */
   int         r, g, b, a;
} Pixel;

typedef struct
{
   /* The background as the original sRGB 8-bit value converted to the final
    * integer format and as a double precision linear value in the range 0..1
    * for with partially transparent pixels.
    */
   int ir, ig, ib;
   double dr, dg, db; /* linear r,g,b scaled to 0..1 */
} Background;

/* Basic image formats; control the data but not the layout thereof. */
#define BASE_FORMATS\
   (PNG_FORMAT_FLAG_ALPHA|PNG_FORMAT_FLAG_COLOR|PNG_FORMAT_FLAG_LINEAR)

/* Read a Pixel from a buffer.  The code below stores the correct routine for
 * the format in a function pointer, these are the routines:
 */
static void
gp_g8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = p->g = p->b = pp[0];
   p->a = 255;
}

static void
gp_ga8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = p->g = p->b = pp[0];
   p->a = pp[1];
}

#ifdef PNG_FORMAT_AFIRST_SUPPORTED
static void
gp_ag8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = p->g = p->b = pp[1];
   p->a = pp[0];
}
#endif

static void
gp_rgb8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[0];
   p->g = pp[1];
   p->b = pp[2];
   p->a = 255;
}

#ifdef PNG_FORMAT_BGR_SUPPORTED
static void
gp_bgr8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[2];
   p->g = pp[1];
   p->b = pp[0];
   p->a = 255;
}
#endif

static void
gp_rgba8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[0];
   p->g = pp[1];
   p->b = pp[2];
   p->a = pp[3];
}

#ifdef PNG_FORMAT_BGR_SUPPORTED
static void
gp_bgra8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[2];
   p->g = pp[1];
   p->b = pp[0];
   p->a = pp[3];
}
#endif

#ifdef PNG_FORMAT_AFIRST_SUPPORTED
static void
gp_argb8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[1];
   p->g = pp[2];
   p->b = pp[3];
   p->a = pp[0];
}
#endif

#if defined(PNG_FORMAT_AFIRST_SUPPORTED) && defined(PNG_FORMAT_BGR_SUPPORTED)
static void
gp_abgr8(Pixel *p, png_const_voidp pb)
{
   png_const_bytep pp = voidcast(png_const_bytep, pb);

   p->r = pp[3];
   p->g = pp[2];
   p->b = pp[1];
   p->a = pp[0];
}
#endif

static void
gp_g16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = p->g = p->b = pp[0];
   p->a = 65535;
}

static void
gp_ga16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = p->g = p->b = pp[0];
   p->a = pp[1];
}

#ifdef PNG_FORMAT_AFIRST_SUPPORTED
static void
gp_ag16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = p->g = p->b = pp[1];
   p->a = pp[0];
}
#endif

static void
gp_rgb16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[0];
   p->g = pp[1];
   p->b = pp[2];
   p->a = 65535;
}

#ifdef PNG_FORMAT_BGR_SUPPORTED
static void
gp_bgr16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[2];
   p->g = pp[1];
   p->b = pp[0];
   p->a = 65535;
}
#endif

static void
gp_rgba16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[0];
   p->g = pp[1];
   p->b = pp[2];
   p->a = pp[3];
}

#ifdef PNG_FORMAT_BGR_SUPPORTED
static void
gp_bgra16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[2];
   p->g = pp[1];
   p->b = pp[0];
   p->a = pp[3];
}
#endif

#ifdef PNG_FORMAT_AFIRST_SUPPORTED
static void
gp_argb16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[1];
   p->g = pp[2];
   p->b = pp[3];
   p->a = pp[0];
}
#endif

#if defined(PNG_FORMAT_AFIRST_SUPPORTED) && defined(PNG_FORMAT_BGR_SUPPORTED)
static void
gp_abgr16(Pixel *p, png_const_voidp pb)
{
   png_const_uint_16p pp = voidcast(png_const_uint_16p, pb);

   p->r = pp[3];
   p->g = pp[2];
   p->b = pp[1];
   p->a = pp[0];
}
#endif

/* Given a format, return the correct one of the above functions. */
static void (*
get_pixel(png_uint_32 format))(Pixel *p, png_const_voidp pb)
{
   /* The color-map flag is irrelevant here - the caller of the function
    * returned must either pass the buffer or, for a color-mapped image, the
    * correct entry in the color-map.
    */
   if (format & PNG_FORMAT_FLAG_LINEAR)
   {
      if (format & PNG_FORMAT_FLAG_COLOR)
      {
#        ifdef PNG_FORMAT_BGR_SUPPORTED
            if (format & PNG_FORMAT_FLAG_BGR)
            {
               if (format & PNG_FORMAT_FLAG_ALPHA)
               {
#                 ifdef PNG_FORMAT_AFIRST_SUPPORTED
                     if (format & PNG_FORMAT_FLAG_AFIRST)
                        return gp_abgr16;

                     else
#                 endif
                     return gp_bgra16;
               }

               else
                  return gp_bgr16;
            }

            else
#        endif
         {
            if (format & PNG_FORMAT_FLAG_ALPHA)
            {
#              ifdef PNG_FORMAT_AFIRST_SUPPORTED
                  if (format & PNG_FORMAT_FLAG_AFIRST)
                     return gp_argb16;

                  else
#              endif
                  return gp_rgba16;
            }

            else
               return gp_rgb16;
         }
      }

      else
      {
         if (format & PNG_FORMAT_FLAG_ALPHA)
         {
#           ifdef PNG_FORMAT_AFIRST_SUPPORTED
               if (format & PNG_FORMAT_FLAG_AFIRST)
                  return gp_ag16;

               else
#           endif
               return gp_ga16;
         }

         else
            return gp_g16;
      }
   }

   else
   {
      if (format & PNG_FORMAT_FLAG_COLOR)
      {
#        ifdef PNG_FORMAT_BGR_SUPPORTED
            if (format & PNG_FORMAT_FLAG_BGR)
            {
               if (format & PNG_FORMAT_FLAG_ALPHA)
               {
#                 ifdef PNG_FORMAT_AFIRST_SUPPORTED
                     if (format & PNG_FORMAT_FLAG_AFIRST)
                        return gp_abgr8;

                     else
#                 endif
                     return gp_bgra8;
               }

               else
                  return gp_bgr8;
            }

            else
#        endif
         {
            if (format & PNG_FORMAT_FLAG_ALPHA)
            {
#              ifdef PNG_FORMAT_AFIRST_SUPPORTED
                  if (format & PNG_FORMAT_FLAG_AFIRST)
                     return gp_argb8;

                  else
#              endif
                  return gp_rgba8;
            }

            else
               return gp_rgb8;
         }
      }

      else
      {
         if (format & PNG_FORMAT_FLAG_ALPHA)
         {
#           ifdef PNG_FORMAT_AFIRST_SUPPORTED
               if (format & PNG_FORMAT_FLAG_AFIRST)
                  return gp_ag8;

               else
#           endif
               return gp_ga8;
         }

         else
            return gp_g8;
      }
   }
}

/* Conversion between pixel formats.  The code above effectively eliminates the
 * component ordering changes leaving three basic changes:
 *
 * 1) Remove an alpha channel by pre-multiplication or compositing on a
 *    background color.  (Adding an alpha channel is a no-op.)
 *
 * 2) Remove color by mapping to grayscale.  (Grayscale to color is a no-op.)
 *
 * 3) Convert between 8-bit and 16-bit components.  (Both directtions are
 *    relevant.)
 *
 * This gives the following base format conversion matrix:
 *
 *   OUT:    ----- 8-bit -----    ----- 16-bit -----
 *   IN     G    GA   RGB  RGBA  G    GA   RGB  RGBA
 *  8 G     .    .    .    .     lin  lin  lin  lin
 *  8 GA    bckg .    bckc .     pre' pre  pre' pre
 *  8 RGB   g8   g8   .    .     glin glin lin  lin
 *  8 RGBA  g8b  g8   bckc .     gpr' gpre pre' pre
 * 16 G     sRGB sRGB sRGB sRGB  .    .    .    .
 * 16 GA    b16g unpg b16c unpc  A    .    A    .
 * 16 RGB   sG   sG   sRGB sRGB  g16  g16  .    .
 * 16 RGBA  gb16 sGp  cb16 sCp   g16  g16' A    .
 *
 *  8-bit to 8-bit:
 * bckg: composite on gray background
 * bckc: composite on color background
 * g8:   convert sRGB components to sRGB grayscale
 * g8b:  convert sRGB components to grayscale and composite on gray background
 *
 *  8-bit to 16-bit:
 * lin:  make sRGB components linear, alpha := 65535
 * pre:  make sRGB components linear and premultiply by alpha  (scale alpha)
 * pre': as 'pre' but alpha := 65535
 * glin: make sRGB components linear, convert to grayscale, alpha := 65535
 * gpre: make sRGB components grayscale and linear and premultiply by alpha
 * gpr': as 'gpre' but alpha := 65535
 *
 *  16-bit to 8-bit:
 * sRGB: convert linear components to sRGB, alpha := 255
 * unpg: unpremultiply gray component and convert to sRGB (scale alpha)
 * unpc: unpremultiply color components and convert to sRGB (scale alpha)
 * b16g: composite linear onto gray background and convert the result to sRGB
 * b16c: composite linear onto color background and convert the result to sRGB
 * sG:   convert linear RGB to sRGB grayscale
 * sGp:  unpremultiply RGB then convert to sRGB grayscale
 * sCp:  unpremultiply RGB then convert to sRGB
 * gb16: composite linear onto background and convert to sRGB grayscale
 *       (order doesn't matter, the composite and grayscale operations permute)
 * cb16: composite linear onto background and convert to sRGB
 *
 *  16-bit to 16-bit:
 * A:    set alpha to 65535
 * g16:  convert linear RGB to linear grayscale (alpha := 65535)
 * g16': as 'g16' but alpha is unchanged
 */
/* Simple copy: */
static void
gpc_noop(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   out->r = in->r;
   out->g = in->g;
   out->b = in->b;
   out->a = in->a;
}

#if ALLOW_UNUSED_GPC
static void
gpc_nop8(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   if (in->a == 0)
      out->r = out->g = out->b = 255;

   else
   {
      out->r = in->r;
      out->g = in->g;
      out->b = in->b;
   }

   out->a = in->a;
}
#endif

#if ALLOW_UNUSED_GPC
static void
gpc_nop6(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   if (in->a == 0)
      out->r = out->g = out->b = 65535;

   else
   {
      out->r = in->r;
      out->g = in->g;
      out->b = in->b;
   }

   out->a = in->a;
}
#endif

/* 8-bit to 8-bit conversions */
/* bckg: composite on gray background */
static void
gpc_bckg(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
      out->r = out->g = out->b = back->ig;

   else if (in->a >= 255)
      out->r = out->g = out->b = in->g;

   else
   {
      double a = in->a / 255.;

      out->r = out->g = out->b = sRGB(sRGB_to_d[in->g] * a + back->dg * (1-a));
   }

   out->a = 255;
}

/* bckc: composite on color background */
static void
gpc_bckc(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
   {
      out->r = back->ir;
      out->g = back->ig;
      out->b = back->ib;
   }

   else if (in->a >= 255)
   {
      out->r = in->r;
      out->g = in->g;
      out->b = in->b;
   }

   else
   {
      double a = in->a / 255.;

      out->r = sRGB(sRGB_to_d[in->r] * a + back->dr * (1-a));
      out->g = sRGB(sRGB_to_d[in->g] * a + back->dg * (1-a));
      out->b = sRGB(sRGB_to_d[in->b] * a + back->db * (1-a));
   }

   out->a = 255;
}

/* g8: convert sRGB components to sRGB grayscale */
static void
gpc_g8(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = in->g;

   else
      out->r = out->g = out->b =
         sRGB(YfromRGB(sRGB_to_d[in->r], sRGB_to_d[in->g], sRGB_to_d[in->b]));

   out->a = in->a;
}

/* g8b: convert sRGB components to grayscale and composite on gray background */
static void
gpc_g8b(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
      out->r = out->g = out->b = back->ig;

   else if (in->a >= 255)
   {
      if (in->r == in->g && in->g == in->b)
         out->r = out->g = out->b = in->g;

      else
         out->r = out->g = out->b = sRGB(YfromRGB(
            sRGB_to_d[in->r], sRGB_to_d[in->g], sRGB_to_d[in->b]));
   }

   else
   {
      double a = in->a/255.;

      out->r = out->g = out->b = sRGB(a * YfromRGB(sRGB_to_d[in->r],
         sRGB_to_d[in->g], sRGB_to_d[in->b]) + back->dg * (1-a));
   }

   out->a = 255;
}

/* 8-bit to 16-bit conversions */
/* lin: make sRGB components linear, alpha := 65535 */
static void
gpc_lin(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilinear(in->r);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilinear(in->b);
   }

   else
   {
      out->g = ilinear(in->g);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilinear(in->b);
   }

   out->a = 65535;
}

/* pre: make sRGB components linear and premultiply by alpha (scale alpha) */
static void
gpc_pre(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilineara(in->r, in->a);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilineara(in->b, in->a);
   }

   else
   {
      out->g = ilineara(in->g, in->a);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilineara(in->b, in->a);
   }

   out->a = in->a * 257;
}

/* pre': as 'pre' but alpha := 65535 */
static void
gpc_preq(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilineara(in->r, in->a);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilineara(in->b, in->a);
   }

   else
   {
      out->g = ilineara(in->g, in->a);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilineara(in->b, in->a);
   }

   out->a = 65535;
}

/* glin: make sRGB components linear, convert to grayscale, alpha := 65535 */
static void
gpc_glin(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilinear(in->g);

   else
      out->r = out->g = out->b = u16d(65535 *
         YfromRGB(sRGB_to_d[in->r], sRGB_to_d[in->g], sRGB_to_d[in->b]));

   out->a = 65535;
}

/* gpre: make sRGB components grayscale and linear and premultiply by alpha */
static void
gpc_gpre(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilineara(in->g, in->a);

   else
      out->r = out->g = out->b = u16d(in->a * 257 *
         YfromRGB(sRGB_to_d[in->r], sRGB_to_d[in->g], sRGB_to_d[in->b]));

   out->a = 257 * in->a;
}

/* gpr': as 'gpre' but alpha := 65535 */
static void
gpc_gprq(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilineara(in->g, in->a);

   else
      out->r = out->g = out->b = u16d(in->a * 257 *
         YfromRGB(sRGB_to_d[in->r], sRGB_to_d[in->g], sRGB_to_d[in->b]));

   out->a = 65535;
}

/* 8-bit to 16-bit conversions for gAMA 45455 encoded values */
/* Lin: make gAMA 45455 components linear, alpha := 65535 */
static void
gpc_Lin(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilinear_g22(in->r);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilinear_g22(in->b);
   }

   else
   {
      out->g = ilinear_g22(in->g);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilinear_g22(in->b);
   }

   out->a = 65535;
}

#if ALLOW_UNUSED_GPC
/* Pre: make gAMA 45455 components linear and premultiply by alpha (scale alpha)
 */
static void
gpc_Pre(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilineara_g22(in->r, in->a);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilineara_g22(in->b, in->a);
   }

   else
   {
      out->g = ilineara_g22(in->g, in->a);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilineara_g22(in->b, in->a);
   }

   out->a = in->a * 257;
}
#endif

#if ALLOW_UNUSED_GPC
/* Pre': as 'Pre' but alpha := 65535 */
static void
gpc_Preq(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = ilineara_g22(in->r, in->a);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = ilineara_g22(in->b, in->a);
   }

   else
   {
      out->g = ilineara_g22(in->g, in->a);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = ilineara_g22(in->b, in->a);
   }

   out->a = 65535;
}
#endif

#if ALLOW_UNUSED_GPC
/* Glin: make gAMA 45455 components linear, convert to grayscale, alpha := 65535
 */
static void
gpc_Glin(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilinear_g22(in->g);

   else
      out->r = out->g = out->b = u16d(65535 *
         YfromRGB(g22_to_d[in->r], g22_to_d[in->g], g22_to_d[in->b]));

   out->a = 65535;
}
#endif

#if ALLOW_UNUSED_GPC
/* Gpre: make gAMA 45455 components grayscale and linear and premultiply by
 * alpha.
 */
static void
gpc_Gpre(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilineara_g22(in->g, in->a);

   else
      out->r = out->g = out->b = u16d(in->a * 257 *
         YfromRGB(g22_to_d[in->r], g22_to_d[in->g], g22_to_d[in->b]));

   out->a = 257 * in->a;
}
#endif

#if ALLOW_UNUSED_GPC
/* Gpr': as 'Gpre' but alpha := 65535 */
static void
gpc_Gprq(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->r == in->g && in->g == in->b)
      out->r = out->g = out->b = ilineara_g22(in->g, in->a);

   else
      out->r = out->g = out->b = u16d(in->a * 257 *
         YfromRGB(g22_to_d[in->r], g22_to_d[in->g], g22_to_d[in->b]));

   out->a = 65535;
}
#endif

/* 16-bit to 8-bit conversions */
/* sRGB: convert linear components to sRGB, alpha := 255 */
static void
gpc_sRGB(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = isRGB(in->r);

   if (in->g == in->r)
   {
      out->g = out->r;

      if (in->b == in->r)
         out->b = out->r;

      else
         out->b = isRGB(in->b);
   }

   else
   {
      out->g = isRGB(in->g);

      if (in->b == in->r)
         out->b = out->r;

      else if (in->b == in->g)
         out->b = out->g;

      else
         out->b = isRGB(in->b);
   }

   out->a = 255;
}

/* unpg: unpremultiply gray component and convert to sRGB (scale alpha) */
static void
gpc_unpg(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->a <= 128)
   {
      out->r = out->g = out->b = 255;
      out->a = 0;
   }

   else
   {
      out->r = out->g = out->b = sRGB((double)in->g / in->a);
      out->a = u8d(in->a / 257.);
   }
}

/* unpc: unpremultiply color components and convert to sRGB (scale alpha) */
static void
gpc_unpc(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->a <= 128)
   {
      out->r = out->g = out->b = 255;
      out->a = 0;
   }

   else
   {
      out->r = sRGB((double)in->r / in->a);
      out->g = sRGB((double)in->g / in->a);
      out->b = sRGB((double)in->b / in->a);
      out->a = u8d(in->a / 257.);
   }
}

/* b16g: composite linear onto gray background and convert the result to sRGB */
static void
gpc_b16g(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
      out->r = out->g = out->b = back->ig;

   else
   {
      double a = in->a/65535.;
      double a1 = 1-a;

      a /= 65535;
      out->r = out->g = out->b = sRGB(in->g * a + back->dg * a1);
   }

   out->a = 255;
}

/* b16c: composite linear onto color background and convert the result to sRGB*/
static void
gpc_b16c(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
   {
      out->r = back->ir;
      out->g = back->ig;
      out->b = back->ib;
   }

   else
   {
      double a = in->a/65535.;
      double a1 = 1-a;

      a /= 65535;
      out->r = sRGB(in->r * a + back->dr * a1);
      out->g = sRGB(in->g * a + back->dg * a1);
      out->b = sRGB(in->b * a + back->db * a1);
   }

   out->a = 255;
}

/* sG: convert linear RGB to sRGB grayscale */
static void
gpc_sG(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   out->r = out->g = out->b = sRGB(YfromRGBint(in->r, in->g, in->b)/65535);
   out->a = 255;
}

/* sGp: unpremultiply RGB then convert to sRGB grayscale */
static void
gpc_sGp(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->a <= 128)
   {
      out->r = out->g = out->b = 255;
      out->a = 0;
   }

   else
   {
      out->r = out->g = out->b = sRGB(YfromRGBint(in->r, in->g, in->b)/in->a);
      out->a = u8d(in->a / 257.);
   }
}

/* sCp: unpremultiply RGB then convert to sRGB */
static void
gpc_sCp(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;

   if (in->a <= 128)
   {
      out->r = out->g = out->b = 255;
      out->a = 0;
   }

   else
   {
      out->r = sRGB((double)in->r / in->a);
      out->g = sRGB((double)in->g / in->a);
      out->b = sRGB((double)in->b / in->a);
      out->a = u8d(in->a / 257.);
   }
}

/* gb16: composite linear onto background and convert to sRGB grayscale */
/*  (order doesn't matter, the composite and grayscale operations permute) */
static void
gpc_gb16(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
      out->r = out->g = out->b = back->ig;

   else if (in->a >= 65535)
      out->r = out->g = out->b = isRGB(in->g);

   else
   {
      double a = in->a / 65535.;
      double a1 = 1-a;

      a /= 65535;
      out->r = out->g = out->b = sRGB(in->g * a + back->dg * a1);
   }

   out->a = 255;
}

/* cb16: composite linear onto background and convert to sRGB */
static void
gpc_cb16(Pixel *out, const Pixel *in, const Background *back)
{
   if (in->a <= 0)
   {
      out->r = back->ir;
      out->g = back->ig;
      out->b = back->ib;
   }

   else if (in->a >= 65535)
   {
      out->r = isRGB(in->r);
      out->g = isRGB(in->g);
      out->b = isRGB(in->b);
   }

   else
   {
      double a = in->a / 65535.;
      double a1 = 1-a;

      a /= 65535;
      out->r = sRGB(in->r * a + back->dr * a1);
      out->g = sRGB(in->g * a + back->dg * a1);
      out->b = sRGB(in->b * a + back->db * a1);
   }

   out->a = 255;
}

/* 16-bit to 16-bit conversions */
/* A:    set alpha to 65535 */
static void
gpc_A(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   out->r = in->r;
   out->g = in->g;
   out->b = in->b;
   out->a = 65535;
}

/* g16:  convert linear RGB to linear grayscale (alpha := 65535) */
static void
gpc_g16(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   out->r = out->g = out->b = u16d(YfromRGBint(in->r, in->g, in->b));
   out->a = 65535;
}

/* g16': as 'g16' but alpha is unchanged */
static void
gpc_g16q(Pixel *out, const Pixel *in, const Background *back)
{
   (void)back;
   out->r = out->g = out->b = u16d(YfromRGBint(in->r, in->g, in->b));
   out->a = in->a;
}

#if ALLOW_UNUSED_GPC
/* Unused functions (to hide them from GCC unused function warnings) */
void (* const gpc_unused[])
   (Pixel *out, const Pixel *in, const Background *back) =
{
   gpc_Pre, gpc_Preq, gpc_Glin, gpc_Gpre, gpc_Gprq, gpc_nop8, gpc_nop6
};
#endif

/*   OUT:    ----- 8-bit -----    ----- 16-bit -----
 *   IN     G    GA   RGB  RGBA  G    GA   RGB  RGBA
 *  8 G     .    .    .    .     lin  lin  lin  lin
 *  8 GA    bckg .    bckc .     pre' pre  pre' pre
 *  8 RGB   g8   g8   .    .     glin glin lin  lin
 *  8 RGBA  g8b  g8   bckc .     gpr' gpre pre' pre
 * 16 G     sRGB sRGB sRGB sRGB  .    .    .    .
 * 16 GA    b16g unpg b16c unpc  A    .    A    .
 * 16 RGB   sG   sG   sRGB sRGB  g16  g16  .    .
 * 16 RGBA  gb16 sGp  cb16 sCp   g16  g16' A    .
 *
 * The matrix is held in an array indexed thus:
 *
 *   gpc_fn[out_format & BASE_FORMATS][in_format & BASE_FORMATS];
 */
/* This will produce a compile time error if the FORMAT_FLAG values don't
 * match the above matrix!
 */
#if PNG_FORMAT_FLAG_ALPHA == 1 && PNG_FORMAT_FLAG_COLOR == 2 &&\
   PNG_FORMAT_FLAG_LINEAR == 4
static void (* const gpc_fn[8/*in*/][8/*out*/])
   (Pixel *out, const Pixel *in, const Background *back) =
{
/*out: G-8     GA-8     RGB-8    RGBA-8    G-16     GA-16   RGB-16  RGBA-16 */
   {gpc_noop,gpc_noop,gpc_noop,gpc_noop, gpc_Lin, gpc_Lin, gpc_Lin, gpc_Lin },
   {gpc_bckg,gpc_noop,gpc_bckc,gpc_noop, gpc_preq,gpc_pre, gpc_preq,gpc_pre },
   {gpc_g8,  gpc_g8,  gpc_noop,gpc_noop, gpc_glin,gpc_glin,gpc_lin, gpc_lin },
   {gpc_g8b, gpc_g8,  gpc_bckc,gpc_noop, gpc_gprq,gpc_gpre,gpc_preq,gpc_pre },
   {gpc_sRGB,gpc_sRGB,gpc_sRGB,gpc_sRGB, gpc_noop,gpc_noop,gpc_noop,gpc_noop},
   {gpc_b16g,gpc_unpg,gpc_b16c,gpc_unpc, gpc_A,   gpc_noop,gpc_A,   gpc_noop},
   {gpc_sG,  gpc_sG,  gpc_sRGB,gpc_sRGB, gpc_g16, gpc_g16, gpc_noop,gpc_noop},
   {gpc_gb16,gpc_sGp, gpc_cb16,gpc_sCp,  gpc_g16, gpc_g16q,gpc_A,   gpc_noop}
};

/* The array is repeated for the cases where both the input and output are color
 * mapped because then different algorithms are used.
 */
static void (* const gpc_fn_colormapped[8/*in*/][8/*out*/])
   (Pixel *out, const Pixel *in, const Background *back) =
{
/*out: G-8     GA-8     RGB-8    RGBA-8    G-16     GA-16   RGB-16  RGBA-16 */
   {gpc_noop,gpc_noop,gpc_noop,gpc_noop, gpc_lin, gpc_lin, gpc_lin, gpc_lin },
   {gpc_bckg,gpc_noop,gpc_bckc,gpc_noop, gpc_preq,gpc_pre, gpc_preq,gpc_pre },
   {gpc_g8,  gpc_g8,  gpc_noop,gpc_noop, gpc_glin,gpc_glin,gpc_lin, gpc_lin },
   {gpc_g8b, gpc_g8,  gpc_bckc,gpc_noop, gpc_gprq,gpc_gpre,gpc_preq,gpc_pre },
   {gpc_sRGB,gpc_sRGB,gpc_sRGB,gpc_sRGB, gpc_noop,gpc_noop,gpc_noop,gpc_noop},
   {gpc_b16g,gpc_unpg,gpc_b16c,gpc_unpc, gpc_A,   gpc_noop,gpc_A,   gpc_noop},
   {gpc_sG,  gpc_sG,  gpc_sRGB,gpc_sRGB, gpc_g16, gpc_g16, gpc_noop,gpc_noop},
   {gpc_gb16,gpc_sGp, gpc_cb16,gpc_sCp,  gpc_g16, gpc_g16q,gpc_A,   gpc_noop}
};

/* The error arrays record the error in the same matrix; 64 entries, however
 * the different algorithms used in libpng for colormap and direct conversions
 * mean that four separate matrices are used (for each combination of
 * colormapped and direct.)
 *
 * In some cases the conversion between sRGB formats goes via a linear
 * intermediate; an sRGB to linear conversion (as above) is followed by a simple
 * linear to sRGB step with no other conversions.  This is done by a separate
 * error array from an arbitrary 'in' format to one of the four basic outputs
 * (since final output is always sRGB not colormapped).
 *
 * These arrays may be modified if the --accumulate flag is set during the run;
 * then instead of logging errors they are simply added in.
 *
 * The three entries are currently for transparent, partially transparent and
 * opaque input pixel values.  Notice that alpha should be exact in each case.
 *
 * Errors in alpha should only occur when converting from a direct format
 * to a colormapped format, when alpha is effectively smashed (so large
 * errors can occur.)  There should be no error in the '0' and 'opaque'
 * values.  The fourth entry in the array is used for the alpha error (and it
 * should always be zero for the 'via linear' case since this is never color
 * mapped.)
 *
 * Mapping to a colormap smashes the colors, it is necessary to have separate
 * values for these cases because they are much larger; it is very much
 * impossible to obtain a reasonable result, these are held in
 * gpc_error_to_colormap.
 */
#if PNG_FORMAT_FLAG_COLORMAP == 8 /* extra check also required */
#  include "pngstest-errors.h" /* machine generated */
#endif /* COLORMAP flag check */
#endif /* flag checks */

typedef struct
{
   /* Basic pixel information: */
   Image*       in_image;   /* Input image */
   const Image* out_image;  /* Output image */

   /* 'background' is the value passed to the gpc_ routines, it may be NULL if
    * it should not be used (*this* program has an error if it crashes as a
    * result!)
    */
   Background        background_color;
   const Background* background;

   /* Precalculated values: */
   int          in_opaque;   /* Value of input alpha that is opaque */
   int          is_palette;  /* Sample values come from the palette */
   int          accumulate;  /* Accumulate component errors (don't log) */
   int          output_8bit; /* Output is 8-bit (else 16-bit) */

   void (*in_gp)(Pixel*, png_const_voidp);
   void (*out_gp)(Pixel*, png_const_voidp);

   void (*transform)(Pixel *out, const Pixel *in, const Background *back);
      /* A function to perform the required transform */

   void (*from_linear)(Pixel *out, const Pixel *in, const Background *back);
      /* For 'via_linear' transforms the final, from linear, step, else NULL */

   png_uint_16 error[4];
      /* Three error values for transparent, partially transparent and opaque
       * input pixels (in turn).
       */

   png_uint_16 *error_ptr;
      /* Where these are stored in the static array (for 'accumulate') */
}
Transform;

/* Return a 'transform' as above for the given format conversion. */
static void
transform_from_formats(Transform *result, Image *in_image,
   const Image *out_image, png_const_colorp background, int via_linear)
{
   png_uint_32 in_format, out_format;
   png_uint_32 in_base, out_base;

   memset(result, 0, sizeof *result);

   /* Store the original images for error messages */
   result->in_image = in_image;
   result->out_image = out_image;

   in_format = in_image->image.format;
   out_format = out_image->image.format;

   if (in_format & PNG_FORMAT_FLAG_LINEAR)
      result->in_opaque = 65535;
   else
      result->in_opaque = 255;

   result->output_8bit = (out_format & PNG_FORMAT_FLAG_LINEAR) == 0;

   result->is_palette = 0; /* set by caller if required */
   result->accumulate = (in_image->opts & ACCUMULATE) != 0;

   /* The loaders (which need the ordering information) */
   result->in_gp = get_pixel(in_format);
   result->out_gp = get_pixel(out_format);

   /* Remove the ordering information: */
   in_format &= BASE_FORMATS | PNG_FORMAT_FLAG_COLORMAP;
   in_base = in_format & BASE_FORMATS;
   out_format &= BASE_FORMATS | PNG_FORMAT_FLAG_COLORMAP;
   out_base = out_format & BASE_FORMATS;

   if (via_linear)
   {
      /* Check for an error in this program: */
      if (out_format & (PNG_FORMAT_FLAG_LINEAR|PNG_FORMAT_FLAG_COLORMAP))
      {
         fprintf(stderr, "internal transform via linear error 0x%x->0x%x\n",
            in_format, out_format);
         exit(1);
      }

      result->transform = gpc_fn[in_base][out_base | PNG_FORMAT_FLAG_LINEAR];
      result->from_linear = gpc_fn[out_base | PNG_FORMAT_FLAG_LINEAR][out_base];
      result->error_ptr = gpc_error_via_linear[in_format][out_format];
   }

   else if (~in_format & out_format & PNG_FORMAT_FLAG_COLORMAP)
   {
      /* The input is not colormapped but the output is, the errors will
       * typically be large (only the grayscale-no-alpha case permits preserving
       * even 8-bit values.)
       */
      result->transform = gpc_fn[in_base][out_base];
      result->from_linear = NULL;
      result->error_ptr = gpc_error_to_colormap[in_base][out_base];
   }

   else
   {
      /* The caller handles the colormap->pixel value conversion, so the
       * transform function just gets a pixel value, however because libpng
       * currently contains a different implementation for mapping a colormap if
       * both input and output are colormapped we need different conversion
       * functions to deal with errors in the libpng implementation.
       */
      if (in_format & out_format & PNG_FORMAT_FLAG_COLORMAP)
         result->transform = gpc_fn_colormapped[in_base][out_base];
      else
         result->transform = gpc_fn[in_base][out_base];
      result->from_linear = NULL;
      result->error_ptr = gpc_error[in_format][out_format];
   }

   /* Follow the libpng simplified API rules to work out what to pass to the gpc
    * routines as a background value, if one is not required pass NULL so that
    * this program crashes in the even of a programming error.
    */
   result->background = NULL; /* default: not required */

   /* Rule 1: background only need be supplied if alpha is to be removed */
   if (in_format & ~out_format & PNG_FORMAT_FLAG_ALPHA)
   {
      /* The input value is 'NULL' to use the background and (otherwise) an sRGB
       * background color (to use a solid color).  The code above uses a fixed
       * byte value, BUFFER_INIT8, for buffer even for 16-bit output.  For
       * linear (16-bit) output the sRGB background color is ignored; the
       * composition is always on the background (so BUFFER_INIT8 * 257), except
       * that for the colormap (i.e. linear colormapped output) black is used.
       */
      result->background = &result->background_color;

      if (out_format & PNG_FORMAT_FLAG_LINEAR || via_linear)
      {
         if (out_format & PNG_FORMAT_FLAG_COLORMAP)
         {
            result->background_color.ir =
               result->background_color.ig =
               result->background_color.ib = 0;
            result->background_color.dr =
               result->background_color.dg =
               result->background_color.db = 0;
         }

         else
         {
            result->background_color.ir =
               result->background_color.ig =
               result->background_color.ib = BUFFER_INIT8 * 257;
            result->background_color.dr =
               result->background_color.dg =
               result->background_color.db = 0;
         }
      }

      else /* sRGB output */
      {
         if (background != NULL)
         {
            if (out_format & PNG_FORMAT_FLAG_COLOR)
            {
               result->background_color.ir = background->red;
               result->background_color.ig = background->green;
               result->background_color.ib = background->blue;
               /* TODO: sometimes libpng uses the power law conversion here, how
                * to handle this?
                */
               result->background_color.dr = sRGB_to_d[background->red];
               result->background_color.dg = sRGB_to_d[background->green];
               result->background_color.db = sRGB_to_d[background->blue];
            }

            else /* grayscale: libpng only looks at 'g' */
            {
               result->background_color.ir =
                  result->background_color.ig =
                  result->background_color.ib = background->green;
               /* TODO: sometimes libpng uses the power law conversion here, how
                * to handle this?
                */
               result->background_color.dr =
                  result->background_color.dg =
                  result->background_color.db = sRGB_to_d[background->green];
            }
         }

         else if ((out_format & PNG_FORMAT_FLAG_COLORMAP) == 0)
         {
            result->background_color.ir =
               result->background_color.ig =
               result->background_color.ib = BUFFER_INIT8;
            /* TODO: sometimes libpng uses the power law conversion here, how
             * to handle this?
             */
            result->background_color.dr =
               result->background_color.dg =
               result->background_color.db = sRGB_to_d[BUFFER_INIT8];
         }

         /* Else the output is colormapped and a background color must be
          * provided; if pngstest crashes then that is a bug in this program
          * (though libpng should png_error as well.)
          */
         else
            result->background = NULL;
      }
   }

   if (result->background == NULL)
   {
      result->background_color.ir =
         result->background_color.ig =
         result->background_color.ib = -1; /* not used */
      result->background_color.dr =
         result->background_color.dg =
         result->background_color.db = 1E30; /* not used */
   }


   /* Copy the error values into the Transform: */
   result->error[0] = result->error_ptr[0];
   result->error[1] = result->error_ptr[1];
   result->error[2] = result->error_ptr[2];
   result->error[3] = result->error_ptr[3];
}


/* Compare two pixels.
 *
 * OLD error values:
static int error_to_linear = 811; * by experiment *
static int error_to_linear_grayscale = 424; * by experiment *
static int error_to_sRGB = 6; * by experiment *
static int error_to_sRGB_grayscale = 17; * libpng error by calculation +
                                            2 by experiment *
static int error_in_compose = 2; * by experiment *
static int error_in_premultiply = 1;
 *
 * The following is *just* the result of a round trip from 8-bit sRGB to linear
 * then back to 8-bit sRGB when it is done by libpng.  There are two problems:
 *
 * 1) libpng currently uses a 2.2 power law with no linear segment, this results
 * in instability in the low values and even with 16-bit precision sRGB(1) ends
 * up mapping to sRGB(0) as a result of rounding in the 16-bit representation.
 * This gives an error of 1 in the handling of value 1 only.
 *
 * 2) libpng currently uses an intermediate 8-bit linear value in gamma
 * correction of 8-bit values.  This results in many more errors, the worse of
 * which is mapping sRGB(14) to sRGB(0).
 *
 * The general 'error_via_linear' is more complex because of pre-multiplication,
 * this compounds the 8-bit errors according to the alpha value of the pixel.
 * As a result 256 values are pre-calculated for error_via_linear.
 */
#if 0
static int error_in_libpng_gamma;
static int error_via_linear[256]; /* Indexed by 8-bit alpha */

static void
init_error_via_linear(void)
{
   int alpha;

   error_via_linear[0] = 255; /* transparent pixel */

   for (alpha=1; alpha<=255; ++alpha)
   {
      /* 16-bit values less than 128.5 get rounded to 8-bit 0 and so the worst
       * case error arises with 16-bit 128.5, work out what sRGB
       * (non-associated) value generates 128.5; any value less than this is
       * going to map to 0, so the worst error is floor(value).
       *
       * Note that errors are considerably higher (more than a factor of 2)
       * because libpng uses a simple power law for sRGB data at present.
       *
       * Add .1 for arithmetic errors inside libpng.
       */
      double v = floor(255*pow(.5/*(128.5 * 255 / 65535)*/ / alpha, 1/2.2)+.1);

      error_via_linear[alpha] = (int)v;
   }

   /* This is actually 14.99, but, despite the closeness to 15, 14 seems to work
    * ok in this case.
    */
   error_in_libpng_gamma = 14;
}
#endif

static void
print_pixel(char string[64], const Pixel *pixel, png_uint_32 format)
{
   switch (format & (PNG_FORMAT_FLAG_ALPHA|PNG_FORMAT_FLAG_COLOR))
   {
      case 0:
         sprintf(string, "%s(%d)", format_names[format], pixel->g);
         break;

      case PNG_FORMAT_FLAG_ALPHA:
         sprintf(string, "%s(%d,%d)", format_names[format], pixel->g,
            pixel->a);
         break;

      case PNG_FORMAT_FLAG_COLOR:
         sprintf(string, "%s(%d,%d,%d)", format_names[format],
            pixel->r, pixel->g, pixel->b);
         break;

      case PNG_FORMAT_FLAG_COLOR|PNG_FORMAT_FLAG_ALPHA:
         sprintf(string, "%s(%d,%d,%d,%d)", format_names[format],
            pixel->r, pixel->g, pixel->b, pixel->a);
         break;

      default:
         sprintf(string, "invalid-format");
         break;
   }
}

static int
logpixel(const Transform *transform, png_uint_32 x, png_uint_32 y,
   const Pixel *in, const Pixel *calc, const Pixel *out, const char *reason)
{
   png_uint_32 in_format = transform->in_image->image.format;
   png_uint_32 out_format = transform->out_image->image.format;

   png_uint_32 back_format = out_format & ~PNG_FORMAT_FLAG_ALPHA;
   const char *via_linear = "";

   char pixel_in[64], pixel_calc[64], pixel_out[64], pixel_loc[64];
   char background_info[100];

   print_pixel(pixel_in, in, in_format);
   print_pixel(pixel_calc, calc, out_format);
   print_pixel(pixel_out, out, out_format);

   if (transform->is_palette)
      sprintf(pixel_loc, "palette: %lu", (unsigned long)y);
   else
      sprintf(pixel_loc, "%lu,%lu", (unsigned long)x, (unsigned long)y);

   if (transform->from_linear != NULL)
   {
      via_linear = " (via linear)";
      /* And as a result the *read* format which did any background processing
       * was itself linear, so the background color information is also
       * linear.
       */
      back_format |= PNG_FORMAT_FLAG_LINEAR;
   }

   if (transform->background != NULL)
   {
      Pixel back;
      char pixel_back[64];

      back.r = transform->background->ir;
      back.g = transform->background->ig;
      back.b = transform->background->ib;
      back.a = -1; /* not used */

      print_pixel(pixel_back, &back, back_format);
      sprintf(background_info, " on background %s", pixel_back);
   }

   else
      background_info[0] = 0;

   if (transform->in_image->file_name != transform->out_image->file_name)
   {
      char error_buffer[512];
      sprintf(error_buffer,
         "(%s) %s error%s:\n %s%s ->\n       %s\n  not: %s.\n"
         "Use --preserve and examine: ", pixel_loc, reason, via_linear,
         pixel_in, background_info, pixel_out, pixel_calc);
      return logerror(transform->in_image, transform->in_image->file_name,
         error_buffer, transform->out_image->file_name);
   }

   else
   {
      char error_buffer[512];
      sprintf(error_buffer,
         "(%s) %s error%s:\n %s%s ->\n       %s\n  not: %s.\n"
         " The error happened when reading the original file with this format.",
         pixel_loc, reason, via_linear, pixel_in, background_info, pixel_out,
         pixel_calc);
      return logerror(transform->in_image, transform->in_image->file_name,
         error_buffer, "");
   }
}

static int
cmppixel(Transform *transform, png_const_voidp in, png_const_voidp out,
   png_uint_32 x, png_uint_32 y/*or palette index*/)
{
   int maxerr;
   png_const_charp errmsg;
   Pixel pixel_in, pixel_calc, pixel_out;

   transform->in_gp(&pixel_in, in);

   if (transform->from_linear == NULL)
      transform->transform(&pixel_calc, &pixel_in, transform->background);

   else
   {
      transform->transform(&pixel_out, &pixel_in, transform->background);
      transform->from_linear(&pixel_calc, &pixel_out, NULL);
   }

   transform->out_gp(&pixel_out, out);

   /* Eliminate the case where the input and output values match exactly. */
   if (pixel_calc.a == pixel_out.a && pixel_calc.r == pixel_out.r &&
      pixel_calc.g == pixel_out.g && pixel_calc.b == pixel_out.b)
      return 1;

   /* Eliminate the case where the output pixel is transparent and the output
    * is 8-bit - any component values are valid.  Don't check the input alpha
    * here to also skip the 16-bit small alpha cases.
    */
   if (transform->output_8bit && pixel_calc.a == 0 && pixel_out.a == 0)
      return 1;

   /* Check for alpha errors first; an alpha error can damage the components too
    * so avoid spurious checks on components if one is found.
    */
   errmsg = NULL;
   {
      int err_a = abs(pixel_calc.a-pixel_out.a);

      if (err_a > transform->error[3])
      {
         /* If accumulating check the components too */
         if (transform->accumulate)
            transform->error[3] = (png_uint_16)err_a;

         else
            errmsg = "alpha";
      }
   }

   /* Now if *either* of the output alphas are 0 but alpha is within tolerance
    * eliminate the 8-bit component comparison.
    */
   if (errmsg == NULL && transform->output_8bit &&
      (pixel_calc.a == 0 || pixel_out.a == 0))
      return 1;

   if (errmsg == NULL) /* else just signal an alpha error */
   {
      int err_r = abs(pixel_calc.r - pixel_out.r);
      int err_g = abs(pixel_calc.g - pixel_out.g);
      int err_b = abs(pixel_calc.b - pixel_out.b);
      int limit;

      if ((err_r | err_g | err_b) == 0)
         return 1; /* exact match */

      /* Mismatch on a component, check the input alpha */
      if (pixel_in.a >= transform->in_opaque)
      {
         errmsg = "opaque component";
         limit = 2; /* opaque */
      }

      else if (pixel_in.a > 0)
      {
         errmsg = "alpha component";
         limit = 1; /* partially transparent */
      }

      else
      {
         errmsg = "transparent component (background)";
         limit = 0; /* transparent */
      }

      maxerr = err_r;
      if (maxerr < err_g) maxerr = err_g;
      if (maxerr < err_b) maxerr = err_b;

      if (maxerr <= transform->error[limit])
         return 1; /* within the error limits */

      /* Handle a component mis-match; log it, just return an error code, or
       * accumulate it.
       */
      if (transform->accumulate)
      {
         transform->error[limit] = (png_uint_16)maxerr;
         return 1; /* to cause the caller to keep going */
      }
   }

   /* Failure to match and not accumulating, so the error must be logged. */
   return logpixel(transform, x, y, &pixel_in, &pixel_calc, &pixel_out, errmsg);
}

static png_byte
component_loc(png_byte loc[4], png_uint_32 format)
{
   /* Given a format return the number of channels and the location of
    * each channel.
    *
    * The mask 'loc' contains the component offset of the channels in the
    * following order.  Note that if 'format' is grayscale the entries 1-3 must
    * all contain the location of the gray channel.
    *
    * 0: alpha
    * 1: red or gray
    * 2: green or gray
    * 3: blue or gray
    */
   png_byte channels;

   if (format & PNG_FORMAT_FLAG_COLOR)
   {
      channels = 3;

      loc[2] = 1;

#     ifdef PNG_FORMAT_BGR_SUPPORTED
         if (format & PNG_FORMAT_FLAG_BGR)
         {
            loc[1] = 2;
            loc[3] = 0;
         }

         else
#     endif
      {
         loc[1] = 0;
         loc[3] = 2;
      }
   }

   else
   {
      channels = 1;
      loc[1] = loc[2] = loc[3] = 0;
   }

   if (format & PNG_FORMAT_FLAG_ALPHA)
   {
#     ifdef PNG_FORMAT_AFIRST_SUPPORTED
         if (format & PNG_FORMAT_FLAG_AFIRST)
         {
            loc[0] = 0;
            ++loc[1];
            ++loc[2];
            ++loc[3];
         }

         else
#     endif
         loc[0] = channels;

      ++channels;
   }

   else
      loc[0] = 4; /* not present */

   return channels;
}

/* Compare two images, the original 'a', which was written out then read back in
 * to * give image 'b'.  The formats may have been changed.
 */
static int
compare_two_images(Image *a, Image *b, int via_linear,
   png_const_colorp background)
{
   ptrdiff_t stridea = a->stride;
   ptrdiff_t strideb = b->stride;
   png_const_bytep rowa = a->buffer+16;
   png_const_bytep rowb = b->buffer+16;
   png_uint_32 width = a->image.width;
   png_uint_32 height = a->image.height;
   png_uint_32 formata = a->image.format;
   png_uint_32 formatb = b->image.format;
   unsigned int a_sample = PNG_IMAGE_SAMPLE_SIZE(formata);
   unsigned int b_sample = PNG_IMAGE_SAMPLE_SIZE(formatb);
   int alpha_added, alpha_removed;
   int bchannels;
   png_uint_32 y;
   Transform tr;
   int btoa[4]={0,0,0,0};

   /* This should never happen: */
   if (width != b->image.width || height != b->image.height)
      return logerror(a, a->file_name, ": width x height changed: ",
         b->file_name);

   /* Set up the background and the transform */
   transform_from_formats(&tr, a, b, background, via_linear);

   /* Find the first row and inter-row space. */
   if (!(formata & PNG_FORMAT_FLAG_COLORMAP) &&
      (formata & PNG_FORMAT_FLAG_LINEAR))
      stridea *= 2;

   if (!(formatb & PNG_FORMAT_FLAG_COLORMAP) &&
      (formatb & PNG_FORMAT_FLAG_LINEAR))
      strideb *= 2;

   if (stridea < 0) rowa += (height-1) * (-stridea);
   if (strideb < 0) rowb += (height-1) * (-strideb);

   /* First shortcut the two colormap case by comparing the image data; if it
    * matches then we expect the colormaps to match, although this is not
    * absolutely necessary for an image match.  If the colormaps fail to match
    * then there is a problem in libpng.
    */
   if (formata & formatb & PNG_FORMAT_FLAG_COLORMAP)
   {
      /* Only check colormap entries that actually exist; */
      png_const_bytep ppa, ppb;
      int match;
      png_byte in_use[256], amax = 0, bmax = 0;

      memset(in_use, 0, sizeof in_use);

      ppa = rowa;
      ppb = rowb;

      /* Do this the slow way to accumulate the 'in_use' flags, don't break out
       * of the loop until the end; this validates the color-mapped data to
       * ensure all pixels are valid color-map indexes.
       */
      for (y=0, match=1; y<height && match; ++y, ppa += stridea, ppb += strideb)
      {
         png_uint_32 x;

         for (x=0; x<width; ++x)
         {
            png_byte bval = ppb[x];
            png_byte aval = ppa[x];

            if (bval > bmax)
               bmax = bval;

            if (bval != aval)
               match = 0;

            in_use[aval] = 1;
            if (aval > amax)
               amax = aval;
         }
      }

      /* If the buffers match then the colormaps must too. */
      if (match)
      {
         /* Do the color-maps match, entry by entry?  Only check the 'in_use'
          * entries.  An error here should be logged as a color-map error.
          */
         png_const_bytep a_cmap = (png_const_bytep)a->colormap;
         png_const_bytep b_cmap = (png_const_bytep)b->colormap;
         int result = 1; /* match by default */

         /* This is used in logpixel to get the error message correct. */
         tr.is_palette = 1;

         for (y=0; y<256; ++y, a_cmap += a_sample, b_cmap += b_sample)
            if (in_use[y])
         {
            /* The colormap entries should be valid, but because libpng doesn't
             * do any checking at present the original image may contain invalid
             * pixel values.  These cause an error here (at present) unless
             * accumulating errors in which case the program just ignores them.
             */
            if (y >= a->image.colormap_entries)
            {
               if ((a->opts & ACCUMULATE) == 0)
               {
                  char pindex[9];
                  sprintf(pindex, "%lu[%lu]", (unsigned long)y,
                     (unsigned long)a->image.colormap_entries);
                  logerror(a, a->file_name, ": bad pixel index: ", pindex);
               }
               result = 0;
            }

            else if (y >= b->image.colormap_entries)
            {
               if ((b->opts & ACCUMULATE) == 0)
                  {
                  char pindex[9];
                  sprintf(pindex, "%lu[%lu]", (unsigned long)y,
                     (unsigned long)b->image.colormap_entries);
                  logerror(b, b->file_name, ": bad pixel index: ", pindex);
                  }
               result = 0;
            }

            /* All the mismatches are logged here; there can only be 256! */
            else if (!cmppixel(&tr, a_cmap, b_cmap, 0, y))
               result = 0;
         }

         /* If requested, copy the error values back from the Transform. */
         if (a->opts & ACCUMULATE)
         {
            tr.error_ptr[0] = tr.error[0];
            tr.error_ptr[1] = tr.error[1];
            tr.error_ptr[2] = tr.error[2];
            tr.error_ptr[3] = tr.error[3];
            result = 1; /* force a continue */
         }

         return result;
      }

      /* else the image buffers don't match pixel-wise so compare sample values
       * instead, but first validate that the pixel indexes are in range (but
       * only if not accumulating, when the error is ignored.)
       */
      else if ((a->opts & ACCUMULATE) == 0)
      {
#        ifdef __GNUC__
#           define BYTE_CHARS 20 /* 2^32: GCC sprintf warning */
#        else
#           define BYTE_CHARS 3 /* 2^8: real maximum value */
#        endif
         /* Check the original image first,
          * TODO: deal with input images with bad pixel values?
          */
         if (amax >= a->image.colormap_entries)
         {
            char pindex[3+2*BYTE_CHARS];
            sprintf(pindex, "%d[%u]", amax,
               (png_byte)/*SAFE*/a->image.colormap_entries);
            return logerror(a, a->file_name, ": bad pixel index: ", pindex);
         }

         else if (bmax >= b->image.colormap_entries)
         {
            char pindex[3+2*BYTE_CHARS];
            sprintf(pindex, "%d[%u]", bmax,
               (png_byte)/*SAFE*/b->image.colormap_entries);
            return logerror(b, b->file_name, ": bad pixel index: ", pindex);
         }
      }
   }

   /* We can directly compare pixel values without the need to use the read
    * or transform support (i.e. a memory compare) if:
    *
    * 1) The bit depth has not changed.
    * 2) RGB to grayscale has not been done (the reverse is ok; we just compare
    *    the three RGB values to the original grayscale.)
    * 3) An alpha channel has not been removed from an 8-bit format, or the
    *    8-bit alpha value of the pixel was 255 (opaque).
    *
    * If an alpha channel has been *added* then it must have the relevant opaque
    * value (255 or 65535).
    *
    * The fist two the tests (in the order given above) (using the boolean
    * equivalence !a && !b == !(a || b))
    */
   if (!(((formata ^ formatb) & PNG_FORMAT_FLAG_LINEAR) |
      (formata & (formatb ^ PNG_FORMAT_FLAG_COLOR) & PNG_FORMAT_FLAG_COLOR)))
   {
      /* Was an alpha channel changed? */
      png_uint_32 alpha_changed = (formata ^ formatb) & PNG_FORMAT_FLAG_ALPHA;

      /* Was an alpha channel removed?  (The third test.)  If so the direct
       * comparison is only possible if the input alpha is opaque.
       */
      alpha_removed = (formata & alpha_changed) != 0;

      /* Was an alpha channel added? */
      alpha_added = (formatb & alpha_changed) != 0;

      /* The channels may have been moved between input and output, this finds
       * out how, recording the result in the btoa array, which says where in
       * 'a' to find each channel of 'b'.  If alpha was added then btoa[alpha]
       * ends up as 4 (and is not used.)
       */
      {
         int i;
         png_byte aloc[4];
         png_byte bloc[4];

         /* The following are used only if the formats match, except that
          * 'bchannels' is a flag for matching formats.  btoa[x] says, for each
          * channel in b, where to find the corresponding value in a, for the
          * bchannels.  achannels may be different for a gray to rgb transform
          * (a will be 1 or 2, b will be 3 or 4 channels.)
          */
         (void)component_loc(aloc, formata);
         bchannels = component_loc(bloc, formatb);

         /* Hence the btoa array. */
         for (i=0; i<4; ++i) if (bloc[i] < 4)
            btoa[bloc[i]] = aloc[i]; /* may be '4' for alpha */

         if (alpha_added)
            alpha_added = bloc[0]; /* location of alpha channel in image b */

         else
            alpha_added = 4; /* Won't match an image b channel */

         if (alpha_removed)
            alpha_removed = aloc[0]; /* location of alpha channel in image a */

         else
            alpha_removed = 4;
      }
   }

   else
   {
      /* Direct compare is not possible, cancel out all the corresponding local
       * variables.
       */
      bchannels = 0;
      alpha_removed = alpha_added = 4;
      btoa[3] = btoa[2] = btoa[1] = btoa[0] = 4; /* 4 == not present */
   }

   for (y=0; y<height; ++y, rowa += stridea, rowb += strideb)
   {
      png_const_bytep ppa, ppb;
      png_uint_32 x;

      for (x=0, ppa=rowa, ppb=rowb; x<width; ++x)
      {
         png_const_bytep psa, psb;

         if (formata & PNG_FORMAT_FLAG_COLORMAP)
            psa = (png_const_bytep)a->colormap + a_sample * *ppa++;
         else
            psa = ppa, ppa += a_sample;

         if (formatb & PNG_FORMAT_FLAG_COLORMAP)
            psb = (png_const_bytep)b->colormap + b_sample * *ppb++;
         else
            psb = ppb, ppb += b_sample;

         /* Do the fast test if possible. */
         if (bchannels)
         {
            /* Check each 'b' channel against either the corresponding 'a'
             * channel or the opaque alpha value, as appropriate.  If
             * alpha_removed value is set (not 4) then also do this only if the
             * 'a' alpha channel (alpha_removed) is opaque; only relevant for
             * the 8-bit case.
             */
            if (formatb & PNG_FORMAT_FLAG_LINEAR) /* 16-bit checks */
            {
               png_const_uint_16p pua = aligncastconst(png_const_uint_16p, psa);
               png_const_uint_16p pub = aligncastconst(png_const_uint_16p, psb);

               switch (bchannels)
               {
                  case 4:
                     if (pua[btoa[3]] != pub[3]) break;
                     /* FALLTHROUGH */
                  case 3:
                     if (pua[btoa[2]] != pub[2]) break;
                     /* FALLTHROUGH */
                  case 2:
                     if (pua[btoa[1]] != pub[1]) break;
                     /* FALLTHROUGH */
                  case 1:
                     if (pua[btoa[0]] != pub[0]) break;
                     if (alpha_added != 4 && pub[alpha_added] != 65535) break;
                     continue; /* x loop */
                  default:
                     break; /* impossible */
               }
            }

            else if (alpha_removed == 4 || psa[alpha_removed] == 255)
            {
               switch (bchannels)
               {
                  case 4:
                     if (psa[btoa[3]] != psb[3]) break;
                     /* FALLTHROUGH */
                  case 3:
                     if (psa[btoa[2]] != psb[2]) break;
                     /* FALLTHROUGH */
                  case 2:
                     if (psa[btoa[1]] != psb[1]) break;
                     /* FALLTHROUGH */
                  case 1:
                     if (psa[btoa[0]] != psb[0]) break;
                     if (alpha_added != 4 && psb[alpha_added] != 255) break;
                     continue; /* x loop */
                  default:
                     break; /* impossible */
               }
            }
         }

         /* If we get to here the fast match failed; do the slow match for this
          * pixel.
          */
         if (!cmppixel(&tr, psa, psb, x, y) && (a->opts & KEEP_GOING) == 0)
            return 0; /* error case */
      }
   }

   /* If requested, copy the error values back from the Transform. */
   if (a->opts & ACCUMULATE)
   {
      tr.error_ptr[0] = tr.error[0];
      tr.error_ptr[1] = tr.error[1];
      tr.error_ptr[2] = tr.error[2];
      tr.error_ptr[3] = tr.error[3];
   }

   return 1;
}

/* Read the file; how the read gets done depends on which of input_file and
 * input_memory have been set.
 */
static int
read_file(Image *image, png_uint_32 format, png_const_colorp background)
{
   memset(&image->image, 0, sizeof image->image);
   image->image.version = PNG_IMAGE_VERSION;

   if (image->input_memory != NULL)
   {
      if (!png_image_begin_read_from_memory(&image->image, image->input_memory,
         image->input_memory_size))
         return logerror(image, "memory init: ", image->file_name, "");
   }

#  ifdef PNG_STDIO_SUPPORTED
      else if (image->input_file != NULL)
      {
         if (!png_image_begin_read_from_stdio(&image->image, image->input_file))
            return logerror(image, "stdio init: ", image->file_name, "");
      }

      else
      {
         if (!png_image_begin_read_from_file(&image->image, image->file_name))
            return logerror(image, "file init: ", image->file_name, "");
      }
#  else
      else
      {
         return logerror(image, "unsupported file/stdio init: ",
            image->file_name, "");
      }
#  endif

   /* This must be set after the begin_read call: */
   if (image->opts & sRGB_16BIT)
      image->image.flags |= PNG_IMAGE_FLAG_16BIT_sRGB;

   /* Have an initialized image with all the data we need plus, maybe, an
    * allocated file (myfile) or buffer (mybuffer) that need to be freed.
    */
   {
      int result;
      png_uint_32 image_format;

      /* Print both original and output formats. */
      image_format = image->image.format;

      if (image->opts & VERBOSE)
      {
         printf("%s %lu x %lu %s -> %s", image->file_name,
            (unsigned long)image->image.width,
            (unsigned long)image->image.height,
            format_names[image_format & FORMAT_MASK],
            (format & FORMAT_NO_CHANGE) != 0 || image->image.format == format
            ? "no change" : format_names[format & FORMAT_MASK]);

         if (background != NULL)
            printf(" background(%d,%d,%d)\n", background->red,
               background->green, background->blue);
         else
            printf("\n");

         fflush(stdout);
      }

      /* 'NO_CHANGE' combined with the color-map flag forces the base format
       * flags to be set on read to ensure that the original representation is
       * not lost in the pass through a colormap format.
       */
      if ((format & FORMAT_NO_CHANGE) != 0)
      {
         if ((format & PNG_FORMAT_FLAG_COLORMAP) != 0 &&
            (image_format & PNG_FORMAT_FLAG_COLORMAP) != 0)
            format = (image_format & ~BASE_FORMATS) | (format & BASE_FORMATS);

         else
            format = image_format;
      }

      image->image.format = format;

      image->stride = PNG_IMAGE_ROW_STRIDE(image->image) + image->stride_extra;
      allocbuffer(image);

      result = png_image_finish_read(&image->image, background,
         image->buffer+16, (png_int_32)image->stride, image->colormap);

      checkbuffer(image, image->file_name);

      if (result)
         return checkopaque(image);

      else
         return logerror(image, image->file_name, ": image read failed", "");
   }
}

/* Reads from a filename, which must be in image->file_name, but uses
 * image->opts to choose the method.  The file is always read in its native
 * format (the one the simplified API suggests).
 */
static int
read_one_file(Image *image)
{
   if (!(image->opts & USE_FILE) || (image->opts & USE_STDIO))
   {
      /* memory or stdio. */
      FILE *f = fopen(image->file_name, "rb");

      if (f != NULL)
      {
         if (image->opts & USE_FILE)
            image->input_file = f;

         else /* memory */
         {
            if (fseek(f, 0, SEEK_END) == 0)
            {
               long int cb = ftell(f);

               if (cb > 0)
               {
#ifndef __COVERITY__
                  if ((unsigned long int)cb <= (size_t)~(size_t)0)
#endif
                  {
                     png_bytep b = voidcast(png_bytep, malloc((size_t)cb));

                     if (b != NULL)
                     {
                        rewind(f);

                        if (fread(b, (size_t)cb, 1, f) == 1)
                        {
                           fclose(f);
                           image->input_memory_size = cb;
                           image->input_memory = b;
                        }

                        else
                        {
                           free(b);
                           return logclose(image, f, image->file_name,
                              ": read failed: ");
                        }
                     }

                     else
                        return logclose(image, f, image->file_name,
                           ": out of memory: ");
                  }

                  else
                     return logclose(image, f, image->file_name,
                        ": file too big for this architecture: ");
                     /* cb is the length of the file as a (long) and
                      * this is greater than the maximum amount of
                      * memory that can be requested from malloc.
                      */
               }

               else if (cb == 0)
                  return logclose(image, f, image->file_name,
                     ": zero length: ");

               else
                  return logclose(image, f, image->file_name,
                     ": tell failed: ");
            }

            else
               return logclose(image, f, image->file_name, ": seek failed: ");
         }
      }

      else
         return logerror(image, image->file_name, ": open failed: ",
            strerror(errno));
   }

   return read_file(image, FORMAT_NO_CHANGE, NULL);
}

#ifdef PNG_SIMPLIFIED_WRITE_SUPPORTED
static int
write_one_file(Image *output, Image *image, int convert_to_8bit)
{
   if (image->opts & FAST_WRITE)
      image->image.flags |= PNG_IMAGE_FLAG_FAST;

   if (image->opts & USE_STDIO)
   {
#ifdef PNG_SIMPLIFIED_WRITE_STDIO_SUPPORTED
#ifndef __COVERITY__
      FILE *f = tmpfile();
#else
      /* Experimental. Coverity says tmpfile() is insecure because it
       * generates predictable names.
       *
       * It is possible to satisfy Coverity by using mkstemp(); however,
       * any platform supporting mkstemp() undoubtedly has a secure tmpfile()
       * implementation as well, and doesn't need the fix.  Note that
       * the fix won't work on platforms that don't support mkstemp().
       *
       * https://www.securecoding.cert.org/confluence/display/c/
       * FIO21-C.+Do+not+create+temporary+files+in+shared+directories
       * says that most historic implementations of tmpfile() provide
       * only a limited number of possible temporary file names
       * (usually 26) before file names are recycled. That article also
       * provides a secure solution that unfortunately depends upon mkstemp().
       */
      char tmpfile[] = "pngstest-XXXXXX";
      int filedes;
      FILE *f;
      umask(0177);
      filedes = mkstemp(tmpfile);
      if (filedes < 0)
        f = NULL;
      else
      {
        f = fdopen(filedes,"w+");
        /* Hide the filename immediately and ensure that the file does
         * not exist after the program ends
         */
        (void) unlink(tmpfile);
      }
#endif

      if (f != NULL)
      {
         if (png_image_write_to_stdio(&image->image, f, convert_to_8bit,
            image->buffer+16, (png_int_32)image->stride, image->colormap))
         {
            if (fflush(f) == 0)
            {
               rewind(f);
               initimage(output, image->opts, "tmpfile", image->stride_extra);
               output->input_file = f;
               if (!checkopaque(image))
                  return 0;
            }

            else
               return logclose(image, f, "tmpfile", ": flush: ");
         }

         else
         {
            fclose(f);
            return logerror(image, "tmpfile", ": write failed", "");
         }
      }

      else
         return logerror(image, "tmpfile", ": open: ", strerror(errno));
#else /* SIMPLIFIED_WRITE_STDIO */
      return logerror(image, "tmpfile", ": open: unsupported", "");
#endif /* SIMPLIFIED_WRITE_STDIO */
   }

   else if (image->opts & USE_FILE)
   {
#ifdef PNG_SIMPLIFIED_WRITE_STDIO_SUPPORTED
      static int counter = 0;
      char name[32];

      sprintf(name, "%s%d.png", tmpf, ++counter);

      if (png_image_write_to_file(&image->image, name, convert_to_8bit,
         image->buffer+16, (png_int_32)image->stride, image->colormap))
      {
         initimage(output, image->opts, output->tmpfile_name,
            image->stride_extra);
         /* Afterwards, or freeimage will delete it! */
         strcpy(output->tmpfile_name, name);

         if (!checkopaque(image))
            return 0;
      }

      else
         return logerror(image, name, ": write failed", "");
#else /* SIMPLIFIED_WRITE_STDIO */
      return logerror(image, "stdio", ": open: unsupported", "");
#endif /* SIMPLIFIED_WRITE_STDIO */
   }

   else /* use memory */
   {
      png_alloc_size_t size;

      if (png_image_write_get_memory_size(image->image, size, convert_to_8bit,
               image->buffer+16, (png_int_32)image->stride, image->colormap))
      {
         /* This is non-fatal but ignoring it was causing serious problems in
          * the macro to be ignored:
          */
         if (size > PNG_IMAGE_PNG_SIZE_MAX(image->image))
            return logerror(image, "memory", ": PNG_IMAGE_SIZE_MAX wrong", "");

         initimage(output, image->opts, "memory", image->stride_extra);
         output->input_memory = malloc(size);

         if (output->input_memory != NULL)
         {
            output->input_memory_size = size;

            if (png_image_write_to_memory(&image->image, output->input_memory,
                  &output->input_memory_size, convert_to_8bit, image->buffer+16,
                  (png_int_32)image->stride, image->colormap))
            {
               /* This is also non-fatal but it safes safer to error out anyway:
                */
               if (size != output->input_memory_size)
                  return logerror(image, "memory", ": memory size wrong", "");
            }

            else
               return logerror(image, "memory", ": write failed", "");
         }

         else
            return logerror(image, "memory", ": out of memory", "");
      }

      else
         return logerror(image, "memory", ": get size:", "");
   }

   /* 'output' has an initialized temporary image, read this back in and compare
    * this against the original: there should be no change since the original
    * format was written unmodified unless 'convert_to_8bit' was specified.
    * However, if the original image was color-mapped, a simple read will zap
    * the linear, color and maybe alpha flags, this will cause spurious failures
    * under some circumstances.
    */
   if (read_file(output, image->image.format | FORMAT_NO_CHANGE, NULL))
   {
      png_uint_32 original_format = image->image.format;

      if (convert_to_8bit)
         original_format &= ~PNG_FORMAT_FLAG_LINEAR;

      if ((output->image.format & BASE_FORMATS) !=
         (original_format & BASE_FORMATS))
         return logerror(image, image->file_name, ": format changed on read: ",
            output->file_name);

      return compare_two_images(image, output, 0/*via linear*/, NULL);
   }

   else
      return logerror(output, output->tmpfile_name,
         ": read of new file failed", "");
}
#endif

static int
testimage(Image *image, png_uint_32 opts, format_list *pf)
{
   int result;
   Image copy;

   /* Copy the original data, stealing it from 'image' */
   checkopaque(image);
   copy = *image;

   copy.opts = opts;
   copy.buffer = NULL;
   copy.bufsize = 0;
   copy.allocsize = 0;

   image->input_file = NULL;
   image->input_memory = NULL;
   image->input_memory_size = 0;
   image->tmpfile_name[0] = 0;

   {
      png_uint_32 counter;
      Image output;

      newimage(&output);

      result = 1;

      /* Use the low bit of 'counter' to indicate whether or not to do alpha
       * removal with a background color or by composting onto the image; this
       * step gets skipped if it isn't relevant
       */
      for (counter=0; counter<2*FORMAT_COUNT; ++counter)
         if (format_isset(pf, counter >> 1))
      {
         png_uint_32 format = counter >> 1;

         png_color background_color;
         png_colorp background = NULL;

         /* If there is a format change that removes the alpha channel then
          * the background is relevant.  If the output is 8-bit color-mapped
          * then a background color *must* be provided, otherwise there are
          * two tests to do - one with a color, the other with NULL.  The
          * NULL test happens second.
          */
         if ((counter & 1) == 0)
         {
            if ((format & PNG_FORMAT_FLAG_ALPHA) == 0 &&
               (image->image.format & PNG_FORMAT_FLAG_ALPHA) != 0)
            {
               /* Alpha/transparency will be removed, the background is
                * relevant: make it a color the first time
                */
               random_color(&background_color);
               background = &background_color;

               /* BUT if the output is to a color-mapped 8-bit format then
                * the background must always be a color, so increment 'counter'
                * to skip the NULL test.
                */
               if ((format & PNG_FORMAT_FLAG_COLORMAP) != 0 &&
                  (format & PNG_FORMAT_FLAG_LINEAR) == 0)
                  ++counter;
            }

            /* Otherwise an alpha channel is not being eliminated, just leave
             * background NULL and skip the (counter & 1) NULL test.
             */
            else
               ++counter;
         }
         /* else just use NULL for background */

         resetimage(&copy);
         copy.opts = opts; /* in case read_file needs to change it */

         result = read_file(&copy, format, background);
         if (!result)
            break;

         /* Make sure the file just read matches the original file. */
         result = compare_two_images(image, &copy, 0/*via linear*/, background);
         if (!result)
            break;

#        ifdef PNG_SIMPLIFIED_WRITE_SUPPORTED
            /* Write the *copy* just made to a new file to make sure the write
             * side works ok.  Check the conversion to sRGB if the copy is
             * linear.
             */
            output.opts = opts;
            result = write_one_file(&output, &copy, 0/*convert to 8bit*/);
            if (!result)
               break;

            /* Validate against the original too; the background is needed here
             * as well so that compare_two_images knows what color was used.
             */
            result = compare_two_images(image, &output, 0, background);
            if (!result)
               break;

            if ((format & PNG_FORMAT_FLAG_LINEAR) != 0 &&
               (format & PNG_FORMAT_FLAG_COLORMAP) == 0)
            {
               /* 'output' is linear, convert to the corresponding sRGB format.
                */
               output.opts = opts;
               result = write_one_file(&output, &copy, 1/*convert to 8bit*/);
               if (!result)
                  break;

               /* This may involve a conversion via linear; in the ideal world
                * this would round-trip correctly, but libpng 1.5.7 is not the
                * ideal world so allow a drift (error_via_linear).
                *
                * 'image' has an alpha channel but 'output' does not then there
                * will a strip-alpha-channel operation (because 'output' is
                * linear), handle this by composing on black when doing the
                * comparison.
                */
               result = compare_two_images(image, &output, 1/*via_linear*/,
                  background);
               if (!result)
                  break;
            }
#        endif /* PNG_SIMPLIFIED_WRITE_SUPPORTED */
      }

      freeimage(&output);
   }

   freeimage(&copy);

   return result;
}

static int
test_one_file(const char *file_name, format_list *formats, png_uint_32 opts,
   int stride_extra, int log_pass)
{
   int result;
   Image image;

   if (!(opts & NO_RESEED))
      reseed(); /* ensure that the random numbers don't depend on file order */
   newimage(&image);
   initimage(&image, opts, file_name, stride_extra);
   result = read_one_file(&image);
   if (result)
      result = testimage(&image, opts, formats);
   freeimage(&image);

   /* Ensure that stderr is flushed into any log file */
   fflush(stderr);

   if (log_pass)
   {
      if (result)
         printf("PASS:");

      else
         printf("FAIL:");

#     ifndef PNG_SIMPLIFIED_WRITE_SUPPORTED
         printf(" (no write)");
#     endif

      print_opts(opts);
      printf(" %s\n", file_name);
      /* stdout may not be line-buffered if it is piped to a file, so: */
      fflush(stdout);
   }

   else if (!result)
      exit(1);

   return result;
}

int
main(int argc, char **argv)
{
   png_uint_32 opts = FAST_WRITE | STRICT;
   format_list formats;
   const char *touch = NULL;
   int log_pass = 0;
   int redundant = 0;
   int stride_extra = 0;
   int retval = 0;
   int c;

#if PNG_LIBPNG_VER >= 10700
      /* This error should not exist in 1.7 or later: */
      opts |= GBG_ERROR;
#endif

   init_sRGB_to_d();
#if 0
   init_error_via_linear();
#endif
   format_init(&formats);
   reseed(); /* initialize random number seeds */

   for (c=1; c<argc; ++c)
   {
      const char *arg = argv[c];

      if (strcmp(arg, "--log") == 0)
         log_pass = 1;
      else if (strcmp(arg, "--fresh") == 0)
      {
         memset(gpc_error, 0, sizeof gpc_error);
         memset(gpc_error_via_linear, 0, sizeof gpc_error_via_linear);
      }
      else if (strcmp(arg, "--file") == 0)
#        ifdef PNG_STDIO_SUPPORTED
            opts |= USE_FILE;
#        else
            return SKIP; /* skipped: no support */
#        endif
      else if (strcmp(arg, "--memory") == 0)
         opts &= ~USE_FILE;
      else if (strcmp(arg, "--stdio") == 0)
#        ifdef PNG_STDIO_SUPPORTED
            opts |= USE_STDIO;
#        else
            return SKIP; /* skipped: no support */
#        endif
      else if (strcmp(arg, "--name") == 0)
         opts &= ~USE_STDIO;
      else if (strcmp(arg, "--verbose") == 0)
         opts |= VERBOSE;
      else if (strcmp(arg, "--quiet") == 0)
         opts &= ~VERBOSE;
      else if (strcmp(arg, "--preserve") == 0)
         opts |= KEEP_TMPFILES;
      else if (strcmp(arg, "--nopreserve") == 0)
         opts &= ~KEEP_TMPFILES;
      else if (strcmp(arg, "--keep-going") == 0)
         opts |= KEEP_GOING;
      else if (strcmp(arg, "--fast") == 0)
         opts |= FAST_WRITE;
      else if (strcmp(arg, "--slow") == 0)
         opts &= ~FAST_WRITE;
      else if (strcmp(arg, "--accumulate") == 0)
         opts |= ACCUMULATE;
      else if (strcmp(arg, "--redundant") == 0)
         redundant = 1;
      else if (strcmp(arg, "--stop") == 0)
         opts &= ~KEEP_GOING;
      else if (strcmp(arg, "--strict") == 0)
         opts |= STRICT;
      else if (strcmp(arg, "--nostrict") == 0)
         opts &= ~STRICT;
      else if (strcmp(arg, "--sRGB-16bit") == 0)
         opts |= sRGB_16BIT;
      else if (strcmp(arg, "--linear-16bit") == 0)
         opts &= ~sRGB_16BIT;
      else if (strcmp(arg, "--noreseed") == 0)
         opts |= NO_RESEED;
      else if (strcmp(arg, "--fault-gbg-warning") == 0)
         opts |= GBG_ERROR;
      else if (strcmp(arg, "--tmpfile") == 0)
      {
         if (c+1 < argc)
         {
            if (strlen(argv[++c]) >= sizeof tmpf)
            {
               fflush(stdout);
               fprintf(stderr, "%s: %s is too long for a temp file prefix\n",
                  argv[0], argv[c]);
               exit(99);
            }

            /* Safe: checked above */
            strncpy(tmpf, argv[c], sizeof (tmpf)-1);
         }

         else
         {
            fflush(stdout);
            fprintf(stderr, "%s: %s requires a temporary file prefix\n",
               argv[0], arg);
            exit(99);
         }
      }
      else if (strcmp(arg, "--touch") == 0)
      {
         if (c+1 < argc)
            touch = argv[++c];

         else
         {
            fflush(stdout);
            fprintf(stderr, "%s: %s requires a file name argument\n",
               argv[0], arg);
            exit(99);
         }
      }
      else if (arg[0] == '+')
      {
         png_uint_32 format = formatof(arg+1);

         if (format > FORMAT_COUNT)
            exit(99);

         format_set(&formats, format);
      }
      else if (arg[0] == '-' && arg[1] != 0 && (arg[1] != '0' || arg[2] != 0))
      {
         fflush(stdout);
         fprintf(stderr, "%s: unknown option: %s\n", argv[0], arg);
         exit(99);
      }
      else
      {
         if (format_is_initial(&formats))
            format_default(&formats, redundant);

         if (arg[0] == '-')
         {
            int term = (arg[1] == '0' ? 0 : '\n');
            unsigned int ich = 0;

            /* Loop reading files, use a static buffer to simplify this and just
             * stop if the name gets to long.
             */
            static char buffer[4096];

            do
            {
               int ch = getchar();

               /* Don't allow '\0' in file names, and terminate with '\n' or,
                * for -0, just '\0' (use -print0 to find to make this work!)
                */
               if (ch == EOF || ch == term || ch == 0)
               {
                  buffer[ich] = 0;

                  if (ich > 0 && !test_one_file(buffer, &formats, opts,
                     stride_extra, log_pass))
                     retval = 1;

                  if (ch == EOF)
                     break;

                  ich = 0;
                  --ich; /* so that the increment below sets it to 0 again */
               }

               else
                  buffer[ich] = (char)ch;
            } while (++ich < sizeof buffer);

            if (ich)
            {
               buffer[32] = 0;
               buffer[4095] = 0;
               fprintf(stderr, "%s...%s: file name too long\n", buffer,
                  buffer+(4096-32));
               exit(99);
            }
         }

         else if (!test_one_file(arg, &formats, opts, stride_extra, log_pass))
            retval = 1;
      }
   }

   if (opts & ACCUMULATE)
   {
      unsigned int in;

      printf("/* contrib/libtests/pngstest-errors.h\n");
      printf(" *\n");
      printf(" * BUILT USING:" PNG_HEADER_VERSION_STRING);
      printf(" *\n");
      printf(" * This code is released under the libpng license.\n");
      printf(" * For conditions of distribution and use, see the disclaimer\n");
      printf(" * and license in png.h\n");
      printf(" *\n");
      printf(" * THIS IS A MACHINE GENERATED FILE: do not edit it directly!\n");
      printf(" * Instead run:\n");
      printf(" *\n");
      printf(" *    pngstest --accumulate\n");
      printf(" *\n");
      printf(" * on as many PNG files as possible; at least PNGSuite and\n");
      printf(" * contrib/libtests/testpngs.\n");
      printf(" */\n");

      printf("static png_uint_16 gpc_error[16/*in*/][16/*out*/][4/*a*/] =\n");
      printf("{\n");
      for (in=0; in<16; ++in)
      {
         unsigned int out;
         printf(" { /* input: %s */\n ", format_names[in]);
         for (out=0; out<16; ++out)
         {
            unsigned int alpha;
            printf(" {");
            for (alpha=0; alpha<4; ++alpha)
            {
               printf(" %d", gpc_error[in][out][alpha]);
               if (alpha < 3) putchar(',');
            }
            printf(" }");
            if (out < 15)
            {
               putchar(',');
               if (out % 4 == 3) printf("\n ");
            }
         }
         printf("\n }");

         if (in < 15)
            putchar(',');
         else
            putchar('\n');
      }
      printf("};\n");

      printf("static png_uint_16 gpc_error_via_linear[16][4/*out*/][4] =\n");
      printf("{\n");
      for (in=0; in<16; ++in)
      {
         unsigned int out;
         printf(" { /* input: %s */\n ", format_names[in]);
         for (out=0; out<4; ++out)
         {
            unsigned int alpha;
            printf(" {");
            for (alpha=0; alpha<4; ++alpha)
            {
               printf(" %d", gpc_error_via_linear[in][out][alpha]);
               if (alpha < 3) putchar(',');
            }
            printf(" }");
            if (out < 3)
               putchar(',');
         }
         printf("\n }");

         if (in < 15)
            putchar(',');
         else
            putchar('\n');
      }
      printf("};\n");

      printf("static png_uint_16 gpc_error_to_colormap[8/*i*/][8/*o*/][4] =\n");
      printf("{\n");
      for (in=0; in<8; ++in)
      {
         unsigned int out;
         printf(" { /* input: %s */\n ", format_names[in]);
         for (out=0; out<8; ++out)
         {
            unsigned int alpha;
            printf(" {");
            for (alpha=0; alpha<4; ++alpha)
            {
               printf(" %d", gpc_error_to_colormap[in][out][alpha]);
               if (alpha < 3) putchar(',');
            }
            printf(" }");
            if (out < 7)
            {
               putchar(',');
               if (out % 4 == 3) printf("\n ");
            }
         }
         printf("\n }");

         if (in < 7)
            putchar(',');
         else
            putchar('\n');
      }
      printf("};\n");
      printf("/* END MACHINE GENERATED */\n");
   }

   if (retval == 0 && touch != NULL)
   {
      FILE *fsuccess = fopen(touch, "wt");

      if (fsuccess != NULL)
      {
         int error = 0;
         fprintf(fsuccess, "PNG simple API tests succeeded\n");
         fflush(fsuccess);
         error = ferror(fsuccess);

         if (fclose(fsuccess) || error)
         {
            fflush(stdout);
            fprintf(stderr, "%s: write failed\n", touch);
            exit(99);
         }
      }

      else
      {
         fflush(stdout);
         fprintf(stderr, "%s: open failed\n", touch);
         exit(99);
      }
   }

   return retval;
}

#else /* !PNG_SIMPLIFIED_READ_SUPPORTED */
int main(void)
{
   fprintf(stderr, "pngstest: no read support in libpng, test skipped\n");
   /* So the test is skipped: */
   return SKIP;
}
#endif /* PNG_SIMPLIFIED_READ_SUPPORTED */

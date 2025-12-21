
/* pngunknown.c - test the read side unknown chunk handling
 *
 * Last changed in libpng 1.6.32 [August 24, 2017]
 * Copyright (c) 2015,2017 Glenn Randers-Pehrson
 * Written by John Cunningham Bowler
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * NOTES:
 *   This is a C program that is intended to be linked against libpng.  It
 *   allows the libpng unknown handling code to be tested by interpreting
 *   arguments to save or discard combinations of chunks.  The program is
 *   currently just a minimal validation for the built-in libpng facilities.
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <setjmp.h>

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


/* Since this program tests the ability to change the unknown chunk handling
 * these must be defined:
 */
#if defined(PNG_SET_UNKNOWN_CHUNKS_SUPPORTED) &&\
   defined(PNG_STDIO_SUPPORTED) &&\
   defined(PNG_READ_SUPPORTED)

/* One of these must be defined to allow us to find out what happened.  It is
 * still useful to set unknown chunk handling without either of these in order
 * to cause *known* chunks to be discarded.  This can be a significant
 * efficiency gain, but it can't really be tested here.
 */
#if defined(PNG_READ_USER_CHUNKS_SUPPORTED) ||\
   defined(PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED)

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
   /* 1.6.0 constifies many APIs. The following exists to allow pngvalid to be
    * compiled against earlier versions.
    */
#  define png_const_structp png_structp
#endif

#if PNG_LIBPNG_VER < 10700
   /* Copied from libpng 1.7.0 png.h */
#define PNG_u2(b1, b2) (((unsigned int)(b1) << 8) + (b2))

#define PNG_U16(b1, b2) ((png_uint_16)PNG_u2(b1, b2))
#define PNG_U32(b1, b2, b3, b4)\
   (((png_uint_32)PNG_u2(b1, b2) << 16) + PNG_u2(b3, b4))

/* Constants for known chunk types.
 */
#define png_IDAT PNG_U32( 73,  68,  65,  84)
#define png_IEND PNG_U32( 73,  69,  78,  68)
#define png_IHDR PNG_U32( 73,  72,  68,  82)
#define png_PLTE PNG_U32( 80,  76,  84,  69)
#define png_bKGD PNG_U32( 98,  75,  71,  68)
#define png_cHRM PNG_U32( 99,  72,  82,  77)
#define png_eXIf PNG_U32(101,  88,  73, 102) /* registered July 2017 */
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

/* Test on flag values as defined in the spec (section 5.4): */
#define PNG_CHUNK_ANCILLARY(c)    (1 & ((c) >> 29))
#define PNG_CHUNK_CRITICAL(c)     (!PNG_CHUNK_ANCILLARY(c))
#define PNG_CHUNK_PRIVATE(c)      (1 & ((c) >> 21))
#define PNG_CHUNK_RESERVED(c)     (1 & ((c) >> 13))
#define PNG_CHUNK_SAFE_TO_COPY(c) (1 & ((c) >>  5))

#endif /* PNG_LIBPNG_VER < 10700 */

#ifdef __cplusplus
#  define this not_the_cpp_this
#  define new not_the_cpp_new
#  define voidcast(type, value) static_cast<type>(value)
#else
#  define voidcast(type, value) (value)
#endif /* __cplusplus */

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

/* Types of chunks not known to libpng */
#define png_vpAg PNG_U32(118, 112, 65, 103)

/* Chunk information */
#define PNG_INFO_tEXt 0x10000000U
#define PNG_INFO_iTXt 0x20000000U
#define PNG_INFO_zTXt 0x40000000U

#define PNG_INFO_sTER 0x01000000U
#define PNG_INFO_vpAg 0x02000000U

#define ABSENT  0
#define START   1
#define END     2

static struct
{
   char        name[5];
   png_uint_32 flag;
   png_uint_32 tag;
   int         unknown;    /* Chunk not known to libpng */
   int         all;        /* Chunk set by the '-1' option */
   int         position;   /* position in pngtest.png */
   int         keep;       /* unknown handling setting */
} chunk_info[] = {
   /* Critical chunks */
   { "IDAT", PNG_INFO_IDAT, png_IDAT, 0, 0,  START, 0 }, /* must be [0] */
   { "PLTE", PNG_INFO_PLTE, png_PLTE, 0, 0, ABSENT, 0 },

   /* Non-critical chunks that libpng handles */
   /* This is a mess but it seems to be the only way to do it - there is no way
    * to check for a definition outside a #if.
    */
   { "bKGD", PNG_INFO_bKGD, png_bKGD,
#     ifdef PNG_READ_bKGD_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "cHRM", PNG_INFO_cHRM, png_cHRM,
#     ifdef PNG_READ_cHRM_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "eXIf", PNG_INFO_eXIf, png_eXIf,
#     ifdef PNG_READ_eXIf_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  END, 0 },
   { "gAMA", PNG_INFO_gAMA, png_gAMA,
#     ifdef PNG_READ_gAMA_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "hIST", PNG_INFO_hIST, png_hIST,
#     ifdef PNG_READ_hIST_SUPPORTED
         0,
#     else
         1,
#     endif
      1, ABSENT, 0 },
   { "iCCP", PNG_INFO_iCCP, png_iCCP,
#     ifdef PNG_READ_iCCP_SUPPORTED
         0,
#     else
         1,
#     endif
      1, ABSENT, 0 },
   { "iTXt", PNG_INFO_iTXt, png_iTXt,
#     ifdef PNG_READ_iTXt_SUPPORTED
         0,
#     else
         1,
#     endif
      1, ABSENT, 0 },
   { "oFFs", PNG_INFO_oFFs, png_oFFs,
#     ifdef PNG_READ_oFFs_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "pCAL", PNG_INFO_pCAL, png_pCAL,
#     ifdef PNG_READ_pCAL_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "pHYs", PNG_INFO_pHYs, png_pHYs,
#     ifdef PNG_READ_pHYs_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "sBIT", PNG_INFO_sBIT, png_sBIT,
#     ifdef PNG_READ_sBIT_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "sCAL", PNG_INFO_sCAL, png_sCAL,
#     ifdef PNG_READ_sCAL_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "sPLT", PNG_INFO_sPLT, png_sPLT,
#     ifdef PNG_READ_sPLT_SUPPORTED
         0,
#     else
         1,
#     endif
      1, ABSENT, 0 },
   { "sRGB", PNG_INFO_sRGB, png_sRGB,
#     ifdef PNG_READ_sRGB_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "tEXt", PNG_INFO_tEXt, png_tEXt,
#     ifdef PNG_READ_tEXt_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "tIME", PNG_INFO_tIME, png_tIME,
#     ifdef PNG_READ_tIME_SUPPORTED
         0,
#     else
         1,
#     endif
      1,  START, 0 },
   { "tRNS", PNG_INFO_tRNS, png_tRNS,
#     ifdef PNG_READ_tRNS_SUPPORTED
         0,
#     else
         1,
#     endif
      0, ABSENT, 0 },
   { "zTXt", PNG_INFO_zTXt, png_zTXt,
#     ifdef PNG_READ_zTXt_SUPPORTED
         0,
#     else
         1,
#     endif
      1,    END, 0 },

   /* No libpng handling */
   { "sTER", PNG_INFO_sTER, png_sTER, 1, 1,  START, 0 },
   { "vpAg", PNG_INFO_vpAg, png_vpAg, 1, 0,  START, 0 },
};

#define NINFO ((int)((sizeof chunk_info)/(sizeof chunk_info[0])))

static void
clear_keep(void)
{
   int i = NINFO;
   while (--i >= 0)
      chunk_info[i].keep = 0;
}

static int
find(const char *name)
{
   int i = NINFO;
   while (--i >= 0)
   {
      if (memcmp(chunk_info[i].name, name, 4) == 0)
         break;
   }

   return i;
}

static int
findb(const png_byte *name)
{
   int i = NINFO;
   while (--i >= 0)
   {
      if (memcmp(chunk_info[i].name, name, 4) == 0)
         break;
   }

   return i;
}

static int
find_by_flag(png_uint_32 flag)
{
   int i = NINFO;

   while (--i >= 0) if (chunk_info[i].flag == flag) return i;

   fprintf(stderr, "pngunknown: internal error\n");
   exit(4);
}

static int
ancillary(const char *name)
{
   return PNG_CHUNK_ANCILLARY(PNG_U32(name[0], name[1], name[2], name[3]));
}

#ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
static int
ancillaryb(const png_byte *name)
{
   return PNG_CHUNK_ANCILLARY(PNG_U32(name[0], name[1], name[2], name[3]));
}
#endif

/* Type of an error_ptr */
typedef struct
{
   jmp_buf     error_return;
   png_structp png_ptr;
   png_infop   info_ptr, end_ptr;
   png_uint_32 before_IDAT;
   png_uint_32 after_IDAT;
   int         error_count;
   int         warning_count;
   int         keep; /* the default value */
   const char *program;
   const char *file;
   const char *test;
} display;

static const char init[] = "initialization";
static const char cmd[] = "command line";

static void
init_display(display *d, const char *program)
{
   memset(d, 0, sizeof *d);
   d->png_ptr = NULL;
   d->info_ptr = d->end_ptr = NULL;
   d->error_count = d->warning_count = 0;
   d->program = program;
   d->file = program;
   d->test = init;
}

static void
clean_display(display *d)
{
   png_destroy_read_struct(&d->png_ptr, &d->info_ptr, &d->end_ptr);

   /* This must not happen - it might cause an app crash */
   if (d->png_ptr != NULL || d->info_ptr != NULL || d->end_ptr != NULL)
   {
      fprintf(stderr, "%s(%s): png_destroy_read_struct error\n", d->file,
         d->test);
      exit(1);
   }
}

PNG_FUNCTION(void, display_exit, (display *d), static PNG_NORETURN)
{
   ++(d->error_count);

   if (d->png_ptr != NULL)
      clean_display(d);

   /* During initialization and if this is a single command line argument set
    * exit now - there is only one test, otherwise longjmp to do the next test.
    */
   if (d->test == init || d->test == cmd)
      exit(1);

   longjmp(d->error_return, 1);
}

static int
display_rc(const display *d, int strict)
{
   return d->error_count + (strict ? d->warning_count : 0);
}

/* libpng error and warning callbacks */
PNG_FUNCTION(void, (PNGCBAPI error), (png_structp png_ptr, const char *message),
   static PNG_NORETURN)
{
   display *d = (display*)png_get_error_ptr(png_ptr);

   fprintf(stderr, "%s(%s): libpng error: %s\n", d->file, d->test, message);
   display_exit(d);
}

static void PNGCBAPI
warning(png_structp png_ptr, const char *message)
{
   display *d = (display*)png_get_error_ptr(png_ptr);

   fprintf(stderr, "%s(%s): libpng warning: %s\n", d->file, d->test, message);
   ++(d->warning_count);
}

static png_uint_32
get_valid(display *d, png_infop info_ptr)
{
   png_uint_32 flags = png_get_valid(d->png_ptr, info_ptr, (png_uint_32)~0);

   /* Map the text chunks back into the flags */
   {
      png_textp text;
      png_uint_32 ntext = png_get_text(d->png_ptr, info_ptr, &text, NULL);

      while (ntext > 0) switch (text[--ntext].compression)
      {
         case -1:
            flags |= PNG_INFO_tEXt;
            break;
         case 0:
            flags |= PNG_INFO_zTXt;
            break;
         case 1:
         case 2:
            flags |= PNG_INFO_iTXt;
            break;
         default:
            fprintf(stderr, "%s(%s): unknown text compression %d\n", d->file,
               d->test, text[ntext].compression);
            display_exit(d);
      }
   }

   return flags;
}

#ifdef PNG_READ_USER_CHUNKS_SUPPORTED
static int PNGCBAPI
read_callback(png_structp pp, png_unknown_chunkp pc)
{
   /* This function mimics the behavior of png_set_keep_unknown_chunks by
    * returning '0' to keep the chunk and '1' to discard it.
    */
   display *d = voidcast(display*, png_get_user_chunk_ptr(pp));
   int chunk = findb(pc->name);
   int keep, discard;

   if (chunk < 0) /* not one in our list, so not a known chunk */
      keep = d->keep;

   else
   {
      keep = chunk_info[chunk].keep;
      if (keep == PNG_HANDLE_CHUNK_AS_DEFAULT)
      {
         /* See the comments in png.h - use the default for unknown chunks,
          * do not keep known chunks.
          */
         if (chunk_info[chunk].unknown)
            keep = d->keep;

         else
            keep = PNG_HANDLE_CHUNK_NEVER;
      }
   }

   switch (keep)
   {
      default:
         fprintf(stderr, "%s(%s): %d: unrecognized chunk option\n", d->file,
            d->test, chunk_info[chunk].keep);
         display_exit(d);

      case PNG_HANDLE_CHUNK_AS_DEFAULT:
      case PNG_HANDLE_CHUNK_NEVER:
         discard = 1/*handled; discard*/;
         break;

      case PNG_HANDLE_CHUNK_IF_SAFE:
      case PNG_HANDLE_CHUNK_ALWAYS:
         discard = 0/*not handled; keep*/;
         break;
   }

   /* Also store information about this chunk in the display, the relevant flag
    * is set if the chunk is to be kept ('not handled'.)
    */
   if (chunk >= 0) if (!discard) /* stupidity to stop a GCC warning */
   {
      png_uint_32 flag = chunk_info[chunk].flag;

      if (pc->location & PNG_AFTER_IDAT)
         d->after_IDAT |= flag;

      else
         d->before_IDAT |= flag;
   }

   /* However if there is no support to store unknown chunks don't ask libpng to
    * do it; there will be an png_error.
    */
#  ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
      return discard;
#  else
      return 1; /*handled; discard*/
#  endif
}
#endif /* READ_USER_CHUNKS_SUPPORTED */

#ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
static png_uint_32
get_unknown(display *d, png_infop info_ptr, int after_IDAT)
{
   /* Create corresponding 'unknown' flags */
   png_uint_32 flags = 0;

   UNUSED(after_IDAT)

   {
      png_unknown_chunkp unknown;
      int num_unknown = png_get_unknown_chunks(d->png_ptr, info_ptr, &unknown);

      while (--num_unknown >= 0)
      {
         int chunk = findb(unknown[num_unknown].name);

         /* Chunks not known to pngunknown must be validated here; since they
          * must also be unknown to libpng the 'display->keep' behavior should
          * have been used.
          */
         if (chunk < 0) switch (d->keep)
         {
            default: /* impossible */
            case PNG_HANDLE_CHUNK_AS_DEFAULT:
            case PNG_HANDLE_CHUNK_NEVER:
               fprintf(stderr, "%s(%s): %s: %s: unknown chunk saved\n",
                  d->file, d->test, d->keep ? "discard" : "default",
                  unknown[num_unknown].name);
               ++(d->error_count);
               break;

            case PNG_HANDLE_CHUNK_IF_SAFE:
               if (!ancillaryb(unknown[num_unknown].name))
               {
                  fprintf(stderr,
                     "%s(%s): if-safe: %s: unknown critical chunk saved\n",
                     d->file, d->test, unknown[num_unknown].name);
                  ++(d->error_count);
                  break;
               }
               /* FALLTHROUGH */ /* (safe) */
            case PNG_HANDLE_CHUNK_ALWAYS:
               break;
         }

         else
            flags |= chunk_info[chunk].flag;
      }
   }

   return flags;
}
#else /* SAVE_UNKNOWN_CHUNKS */
static png_uint_32
get_unknown(display *d, png_infop info_ptr, int after_IDAT)
   /* Otherwise this will return the cached values set by any user callback */
{
   UNUSED(info_ptr);

   if (after_IDAT)
      return d->after_IDAT;

   else
      return d->before_IDAT;
}

#  ifndef PNG_READ_USER_CHUNKS_SUPPORTED
      /* The #defines above should mean this is never reached, it's just here as
       * a check to ensure the logic is correct.
       */
#     error No store support and no user chunk support, this will not work
#  endif /* READ_USER_CHUNKS */
#endif /* SAVE_UNKNOWN_CHUNKS */

static int
check(FILE *fp, int argc, const char **argv, png_uint_32p flags/*out*/,
   display *d, int set_callback)
{
   int i, npasses, ipass;
   png_uint_32 height;

   d->keep = PNG_HANDLE_CHUNK_AS_DEFAULT;
   d->before_IDAT = 0;
   d->after_IDAT = 0;

   /* Some of these errors are permanently fatal and cause an exit here, others
    * are per-test and cause an error return.
    */
   d->png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, d, error,
      warning);
   if (d->png_ptr == NULL)
   {
      fprintf(stderr, "%s(%s): could not allocate png struct\n", d->file,
         d->test);
      /* Terminate here, this error is not test specific. */
      exit(1);
   }

   d->info_ptr = png_create_info_struct(d->png_ptr);
   d->end_ptr = png_create_info_struct(d->png_ptr);
   if (d->info_ptr == NULL || d->end_ptr == NULL)
   {
      fprintf(stderr, "%s(%s): could not allocate png info\n", d->file,
         d->test);
      clean_display(d);
      exit(1);
   }

   png_init_io(d->png_ptr, fp);

#  ifdef PNG_READ_USER_CHUNKS_SUPPORTED
      /* This is only done if requested by the caller; it interferes with the
       * standard store/save mechanism.
       */
      if (set_callback)
         png_set_read_user_chunk_fn(d->png_ptr, d, read_callback);
#  else
      UNUSED(set_callback)
#  endif

   /* Handle each argument in turn; multiple settings are possible for the same
    * chunk and multiple calls will occur (the last one should override all
    * preceding ones).
    */
   for (i=0; i<argc; ++i)
   {
      const char *equals = strchr(argv[i], '=');

      if (equals != NULL)
      {
         int chunk, option;

         if (strcmp(equals+1, "default") == 0)
            option = PNG_HANDLE_CHUNK_AS_DEFAULT;
         else if (strcmp(equals+1, "discard") == 0)
            option = PNG_HANDLE_CHUNK_NEVER;
         else if (strcmp(equals+1, "if-safe") == 0)
            option = PNG_HANDLE_CHUNK_IF_SAFE;
         else if (strcmp(equals+1, "save") == 0)
            option = PNG_HANDLE_CHUNK_ALWAYS;
         else
         {
            fprintf(stderr, "%s(%s): %s: unrecognized chunk option\n", d->file,
               d->test, argv[i]);
            display_exit(d);
         }

         switch (equals - argv[i])
         {
            case 4: /* chunk name */
               chunk = find(argv[i]);

               if (chunk >= 0)
               {
                  /* These #if tests have the effect of skipping the arguments
                   * if SAVE support is unavailable - we can't do a useful test
                   * in this case, so we just check the arguments!  This could
                   * be improved in the future by using the read callback.
                   */
#                 if PNG_LIBPNG_VER >= 10700 &&\
                     !defined(PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED)
                     if (option < PNG_HANDLE_CHUNK_IF_SAFE)
#                 endif /* 1.7+ SAVE_UNKNOWN_CHUNKS */
                  {
                     png_byte name[5];

                     memcpy(name, chunk_info[chunk].name, 5);
                     png_set_keep_unknown_chunks(d->png_ptr, option, name, 1);
                     chunk_info[chunk].keep = option;
                  }
                  continue;
               }

               break;

            case 7: /* default */
               if (memcmp(argv[i], "default", 7) == 0)
               {
#                 if PNG_LIBPNG_VER >= 10700 &&\
                     !defined(PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED)
                     if (option < PNG_HANDLE_CHUNK_IF_SAFE)
#                 endif /* 1.7+ SAVE_UNKNOWN_CHUNKS */
                     png_set_keep_unknown_chunks(d->png_ptr, option, NULL, 0);

                  d->keep = option;
                  continue;
               }

               break;

            case 3: /* all */
               if (memcmp(argv[i], "all", 3) == 0)
               {
#                 if PNG_LIBPNG_VER >= 10700 &&\
                     !defined(PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED)
                     if (option < PNG_HANDLE_CHUNK_IF_SAFE)
#                 endif /* 1.7+ SAVE_UNKNOWN_CHUNKS */
                     png_set_keep_unknown_chunks(d->png_ptr, option, NULL, -1);

                  d->keep = option;

                  for (chunk = 0; chunk < NINFO; ++chunk)
                     if (chunk_info[chunk].all)
                        chunk_info[chunk].keep = option;
                  continue;
               }

               break;

            default: /* some misplaced = */

               break;
         }
      }

      fprintf(stderr, "%s(%s): %s: unrecognized chunk argument\n", d->file,
         d->test, argv[i]);
      display_exit(d);
   }

   png_read_info(d->png_ptr, d->info_ptr);

   switch (png_get_interlace_type(d->png_ptr, d->info_ptr))
   {
      case PNG_INTERLACE_NONE:
         npasses = 1;
         break;

      case PNG_INTERLACE_ADAM7:
         npasses = PNG_INTERLACE_ADAM7_PASSES;
         break;

      default:
         /* Hard error because it is not test specific */
         fprintf(stderr, "%s(%s): invalid interlace type\n", d->file, d->test);
         clean_display(d);
         exit(1);
   }

   /* Skip the image data, if IDAT is not being handled then don't do this
    * because it will cause a CRC error.
    */
   if (chunk_info[0/*IDAT*/].keep == PNG_HANDLE_CHUNK_AS_DEFAULT)
   {
      png_start_read_image(d->png_ptr);
      height = png_get_image_height(d->png_ptr, d->info_ptr);

      if (npasses > 1)
      {
         png_uint_32 width = png_get_image_width(d->png_ptr, d->info_ptr);

         for (ipass=0; ipass<npasses; ++ipass)
         {
            png_uint_32 wPass = PNG_PASS_COLS(width, ipass);

            if (wPass > 0)
            {
               png_uint_32 y;

               for (y=0; y<height; ++y) if (PNG_ROW_IN_INTERLACE_PASS(y, ipass))
                  png_read_row(d->png_ptr, NULL, NULL);
            }
         }
      } /* interlaced */

      else /* not interlaced */
      {
         png_uint_32 y;

         for (y=0; y<height; ++y)
            png_read_row(d->png_ptr, NULL, NULL);
      }
   }

   png_read_end(d->png_ptr, d->end_ptr);

   flags[0] = get_valid(d, d->info_ptr);
   flags[1] = get_unknown(d, d->info_ptr, 0/*before IDAT*/);

   /* Only png_read_png sets PNG_INFO_IDAT! */
   flags[chunk_info[0/*IDAT*/].keep != PNG_HANDLE_CHUNK_AS_DEFAULT] |=
      PNG_INFO_IDAT;

   flags[2] = get_valid(d, d->end_ptr);
   flags[3] = get_unknown(d, d->end_ptr, 1/*after IDAT*/);

   clean_display(d);

   return d->keep;
}

static void
check_error(display *d, png_uint_32 flags, const char *message)
{
   while (flags)
   {
      png_uint_32 flag = flags & -(png_int_32)flags;
      int i = find_by_flag(flag);

      fprintf(stderr, "%s(%s): chunk %s: %s\n", d->file, d->test,
         chunk_info[i].name, message);
      ++(d->error_count);

      flags &= ~flag;
   }
}

static void
check_handling(display *d, int def, png_uint_32 chunks, png_uint_32 known,
   png_uint_32 unknown, const char *position, int set_callback)
{
   while (chunks)
   {
      png_uint_32 flag = chunks & -(png_int_32)chunks;
      int i = find_by_flag(flag);
      int keep = chunk_info[i].keep;
      const char *type;
      const char *errorx = NULL;

      if (chunk_info[i].unknown)
      {
         if (keep == PNG_HANDLE_CHUNK_AS_DEFAULT)
         {
            type = "UNKNOWN (default)";
            keep = def;
         }

         else
            type = "UNKNOWN (specified)";

         if (flag & known)
            errorx = "chunk processed";

         else switch (keep)
         {
            case PNG_HANDLE_CHUNK_AS_DEFAULT:
               if (flag & unknown)
                  errorx = "DEFAULT: unknown chunk saved";
               break;

            case PNG_HANDLE_CHUNK_NEVER:
               if (flag & unknown)
                  errorx = "DISCARD: unknown chunk saved";
               break;

            case PNG_HANDLE_CHUNK_IF_SAFE:
               if (ancillary(chunk_info[i].name))
               {
                  if (!(flag & unknown))
                     errorx = "IF-SAFE: unknown ancillary chunk lost";
               }

               else if (flag & unknown)
                  errorx = "IF-SAFE: unknown critical chunk saved";
               break;

            case PNG_HANDLE_CHUNK_ALWAYS:
               if (!(flag & unknown))
                  errorx = "SAVE: unknown chunk lost";
               break;

            default:
               errorx = "internal error: bad keep";
               break;
         }
      } /* unknown chunk */

      else /* known chunk */
      {
         type = "KNOWN";

         if (flag & known)
         {
            /* chunk was processed, it won't have been saved because that is
             * caught below when checking for inconsistent processing.
             */
            if (keep != PNG_HANDLE_CHUNK_AS_DEFAULT)
               errorx = "!DEFAULT: known chunk processed";
         }

         else /* not processed */ switch (keep)
         {
            case PNG_HANDLE_CHUNK_AS_DEFAULT:
               errorx = "DEFAULT: known chunk not processed";
               break;

            case PNG_HANDLE_CHUNK_NEVER:
               if (flag & unknown)
                  errorx = "DISCARD: known chunk saved";
               break;

            case PNG_HANDLE_CHUNK_IF_SAFE:
               if (ancillary(chunk_info[i].name))
               {
                  if (!(flag & unknown))
                     errorx = "IF-SAFE: known ancillary chunk lost";
               }

               else if (flag & unknown)
                  errorx = "IF-SAFE: known critical chunk saved";
               break;

            case PNG_HANDLE_CHUNK_ALWAYS:
               if (!(flag & unknown))
                  errorx = "SAVE: known chunk lost";
               break;

            default:
               errorx = "internal error: bad keep (2)";
               break;
         }
      }

      if (errorx != NULL)
      {
         ++(d->error_count);
         fprintf(stderr, "%s(%s%s): %s %s %s: %s\n", d->file, d->test,
            set_callback ? ",callback" : "",
            type, chunk_info[i].name, position, errorx);
      }

      chunks &= ~flag;
   }
}

static void
perform_one_test(FILE *fp, int argc, const char **argv,
   png_uint_32 *default_flags, display *d, int set_callback)
{
   int def;
   png_uint_32 flags[2][4];

   rewind(fp);
   clear_keep();
   memcpy(flags[0], default_flags, sizeof flags[0]);

   def = check(fp, argc, argv, flags[1], d, set_callback);

   /* If IDAT is being handled as unknown the image read is skipped and all the
    * IDATs after the first end up in the end info struct, so in this case add
    * IDAT to the list of unknowns.  (Do this after 'check' above sets the
    * chunk_info 'keep' fields.)
    *
    * Note that the flag setting has to be in the 'known' field to avoid
    * triggering the consistency check below and the flag must only be set if
    * there are multiple IDATs, so if the check above did find an unknown IDAT
    * after IDAT.
    */
   if (chunk_info[0/*IDAT*/].keep != PNG_HANDLE_CHUNK_AS_DEFAULT &&
       (flags[1][3] & PNG_INFO_IDAT) != 0)
      flags[0][2] |= PNG_INFO_IDAT;

   /* Chunks should either be known or unknown, never both and this should apply
    * whether the chunk is before or after the IDAT (actually, the app can
    * probably change this by swapping the handling after the image, but this
    * test does not do that.)
    */
   check_error(d, (flags[0][0]|flags[0][2]) & (flags[0][1]|flags[0][3]),
      "chunk handled inconsistently in count tests");
   check_error(d, (flags[1][0]|flags[1][2]) & (flags[1][1]|flags[1][3]),
      "chunk handled inconsistently in option tests");

   /* Now find out what happened to each chunk before and after the IDAT and
    * determine if the behavior was correct.  First some basic sanity checks,
    * any known chunk should be known in the original count, any unknown chunk
    * should be either known or unknown in the original.
    */
   {
      png_uint_32 test;

      test = flags[1][0] & ~flags[0][0];
      check_error(d, test, "new known chunk before IDAT");
      test = flags[1][1] & ~(flags[0][0] | flags[0][1]);
      check_error(d, test, "new unknown chunk before IDAT");
      test = flags[1][2] & ~flags[0][2];
      check_error(d, test, "new known chunk after IDAT");
      test = flags[1][3] & ~(flags[0][2] | flags[0][3]);
      check_error(d, test, "new unknown chunk after IDAT");
   }

   /* Now each chunk in the original list should have been handled according to
    * the options set for that chunk, regardless of whether libpng knows about
    * it or not.
    */
   check_handling(d, def, flags[0][0] | flags[0][1], flags[1][0], flags[1][1],
      "before IDAT", set_callback);
   check_handling(d, def, flags[0][2] | flags[0][3], flags[1][2], flags[1][3],
      "after IDAT", set_callback);
}

static void
perform_one_test_safe(FILE *fp, int argc, const char **argv,
   png_uint_32 *default_flags, display *d, const char *test)
{
   if (setjmp(d->error_return) == 0)
   {
      d->test = test; /* allow use of d->error_return */
#     ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
         perform_one_test(fp, argc, argv, default_flags, d, 0);
#     endif
#     ifdef PNG_READ_USER_CHUNKS_SUPPORTED
         perform_one_test(fp, argc, argv, default_flags, d, 1);
#     endif
      d->test = init; /* prevent use of d->error_return */
   }
}

static const char *standard_tests[] =
{
 "discard", "default=discard", 0,
 "save", "default=save", 0,
 "if-safe", "default=if-safe", 0,
 "vpAg", "vpAg=if-safe", 0,
 "sTER", "sTER=if-safe", 0,
 "IDAT", "default=discard", "IDAT=save", 0,
 "sAPI", "bKGD=save", "cHRM=save", "gAMA=save", "all=discard", "iCCP=save",
   "sBIT=save", "sRGB=save", "eXIf=save", 0,
 0/*end*/
};

static PNG_NORETURN void
usage(const char *program, const char *reason)
{
   fprintf(stderr, "pngunknown: %s: usage:\n %s [--strict] "
      "--default|{(CHNK|default|all)=(default|discard|if-safe|save)} "
      "testfile.png\n", reason, program);
   exit(99);
}

int
main(int argc, const char **argv)
{
   FILE *fp;
   png_uint_32 default_flags[4/*valid,unknown{before,after}*/];
   int strict = 0, default_tests = 0;
   const char *count_argv = "default=save";
   const char *touch_file = NULL;
   display d;

   init_display(&d, argv[0]);

   while (++argv, --argc > 0)
   {
      if (strcmp(*argv, "--strict") == 0)
         strict = 1;

      else if (strcmp(*argv, "--default") == 0)
         default_tests = 1;

      else if (strcmp(*argv, "--touch") == 0)
      {
         if (argc > 1)
            touch_file = *++argv, --argc;

         else
            usage(d.program, "--touch: missing file name");
      }

      else
         break;
   }

   /* A file name is required, but there should be no other arguments if
    * --default was specified.
    */
   if (argc <= 0)
      usage(d.program, "missing test file");

   /* GCC BUG: if (default_tests && argc != 1) triggers some weird GCC argc
    * optimization which causes warnings with -Wstrict-overflow!
    */
   else if (default_tests) if (argc != 1)
      usage(d.program, "extra arguments");

   /* The name of the test file is the last argument; remove it. */
   d.file = argv[--argc];

   fp = fopen(d.file, "rb");
   if (fp == NULL)
   {
      perror(d.file);
      exit(99);
   }

   /* First find all the chunks, known and unknown, in the test file, a failure
    * here aborts the whole test.
    *
    * If 'save' is supported then the normal saving method should happen,
    * otherwise if 'read' is supported then the read callback will do the
    * same thing.  If both are supported the 'read' callback won't be
    * instantiated by default.  If 'save' is *not* supported then a user
    * callback is required even though we can call png_get_unknown_chunks.
    */
   if (check(fp, 1, &count_argv, default_flags, &d,
#     ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
         0
#     else
         1
#     endif
      ) != PNG_HANDLE_CHUNK_ALWAYS)
   {
      fprintf(stderr, "%s: %s: internal error\n", d.program, d.file);
      exit(99);
   }

   /* Now find what the various supplied options cause to change: */
   if (!default_tests)
   {
      d.test = cmd; /* acts as a flag to say exit, do not longjmp */
#     ifdef PNG_SAVE_UNKNOWN_CHUNKS_SUPPORTED
         perform_one_test(fp, argc, argv, default_flags, &d, 0);
#     endif
#     ifdef PNG_READ_USER_CHUNKS_SUPPORTED
         perform_one_test(fp, argc, argv, default_flags, &d, 1);
#     endif
      d.test = init;
   }

   else
   {
      const char **test = standard_tests;

      /* Set the exit_test pointer here so we can continue after a libpng error.
       * NOTE: this leaks memory because the png_struct data from the failing
       * test is never freed.
       */
      while (*test)
      {
         const char *this_test = *test++;
         const char **next = test;
         int count = display_rc(&d, strict), new_count;
         const char *result;
         int arg_count = 0;

         while (*next) ++next, ++arg_count;

         perform_one_test_safe(fp, arg_count, test, default_flags, &d,
            this_test);

         new_count = display_rc(&d, strict);

         if (new_count == count)
            result = "PASS";

         else
            result = "FAIL";

         printf("%s: %s %s\n", result, d.program, this_test);

         test = next+1;
      }
   }

   fclose(fp);

   if (display_rc(&d, strict) == 0)
   {
      /* Success, touch the success file if appropriate */
      if (touch_file != NULL)
      {
         FILE *fsuccess = fopen(touch_file, "wt");

         if (fsuccess != NULL)
         {
            int err = 0;
            fprintf(fsuccess, "PNG unknown tests succeeded\n");
            fflush(fsuccess);
            err = ferror(fsuccess);

            if (fclose(fsuccess) || err)
            {
               fprintf(stderr, "%s: write failed\n", touch_file);
               exit(99);
            }
         }

         else
         {
            fprintf(stderr, "%s: open failed\n", touch_file);
            exit(99);
         }
      }

      return 0;
   }

   return 1;
}

#else /* !(READ_USER_CHUNKS || SAVE_UNKNOWN_CHUNKS) */
int
main(void)
{
   fprintf(stderr,
      " test ignored: no support to find out about unknown chunks\n");
   /* So the test is skipped: */
   return SKIP;
}
#endif /* READ_USER_CHUNKS || SAVE_UNKNOWN_CHUNKS */

#else /* !(SET_UNKNOWN_CHUNKS && READ) */
int
main(void)
{
   fprintf(stderr,
      " test ignored: no support to modify unknown chunk handling\n");
   /* So the test is skipped: */
   return SKIP;
}
#endif /* SET_UNKNOWN_CHUNKS && READ*/

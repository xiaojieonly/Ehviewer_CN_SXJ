/* pngimage.c
 *
 * Copyright (c) 2015,2016 John Cunningham Bowler
 *
 * Last changed in libpng 1.6.24 [August 4, 2016]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Test the png_read_png and png_write_png interfaces.  Given a PNG file load it
 * using png_read_png and then write with png_write_png.  Test all possible
 * transforms.
 */
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#include <assert.h>

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

#ifndef PNG_SETJMP_SUPPORTED
#  include <setjmp.h> /* because png.h did *not* include this */
#endif

/* 1.6.1 added support for the configure test harness, which uses 77 to indicate
 * a skipped test, in earlier versions we need to succeed on a skipped test, so:
 */
#if PNG_LIBPNG_VER >= 10601 && defined(HAVE_CONFIG_H)
#  define SKIP 77
#else
#  define SKIP 0
#endif

#if PNG_LIBPNG_VER < 10700
   /* READ_PNG and WRITE_PNG were not defined, so: */
#  ifdef PNG_INFO_IMAGE_SUPPORTED
#     ifdef PNG_SEQUENTIAL_READ_SUPPORTED
#        define PNG_READ_PNG_SUPPORTED
#     endif /* SEQUENTIAL_READ */
#     ifdef PNG_WRITE_SUPPORTED
#        define PNG_WRITE_PNG_SUPPORTED
#     endif /* WRITE */
#  endif /* INFO_IMAGE */
#endif /* pre 1.7.0 */

#ifdef PNG_READ_PNG_SUPPORTED
/* If a transform is valid on both read and write this implies that if the
 * transform is applied to read it must also be applied on write to produce
 * meaningful data.  This is because these transforms when performed on read
 * produce data with a memory format that does not correspond to a PNG format.
 *
 * Most of these transforms are invertible; after applying the transform on
 * write the result is the original PNG data that would have would have been
 * read if no transform were applied.
 *
 * The exception is _SHIFT, which destroys the low order bits marked as not
 * significant in a PNG with the sBIT chunk.
 *
 * The following table lists, for each transform, the conditions under which it
 * is expected to do anything.  Conditions are defined as follows:
 *
 * 1) Color mask bits required - simply a mask to AND with color_type; one of
 *    these must be present for the transform to fire, except that 0 means
 *    'always'.
 * 2) Color mask bits which must be absent - another mask - none of these must
 *    be present.
 * 3) Bit depths - a mask of component bit depths for the transform to fire.
 * 4) 'read' - the transform works in png_read_png.
 * 5) 'write' - the transform works in png_write_png.
 * 6) PNG_INFO_chunk; a mask of the chunks that must be present for the
 *    transform to fire.  All must be present - the requirement is that
 *    png_get_valid() & mask == mask, so if mask is 0 there is no requirement.
 *
 * The condition refers to the original image state - if multiple transforms are
 * used together it is possible to cause a transform that wouldn't fire on the
 * original image to fire.
 */
static struct transform_info
{
   const char *name;
   int         transform;
   png_uint_32 valid_chunks;
#     define CHUNK_NONE 0
#     define CHUNK_sBIT PNG_INFO_sBIT
#     define CHUNK_tRNS PNG_INFO_tRNS
   png_byte    color_mask_required;
   png_byte    color_mask_absent;
#     define COLOR_MASK_X   0
#     define COLOR_MASK_P   PNG_COLOR_MASK_PALETTE
#     define COLOR_MASK_C   PNG_COLOR_MASK_COLOR
#     define COLOR_MASK_A   PNG_COLOR_MASK_ALPHA
#     define COLOR_MASK_ALL (PALETTE+COLOR+ALPHA)  /* absent = gray, no alpha */
   png_byte    bit_depths;
#     define BD_ALL  (1 + 2 + 4 + 8 + 16)
#     define BD_PAL  (1 + 2 + 4 + 8)
#     define BD_LOW  (1 + 2 + 4)
#     define BD_16   16
#     define BD_TRUE (8+16) /* i.e. true-color depths */
   png_byte    when;
#     define TRANSFORM_R  1
#     define TRANSFORM_W  2
#     define TRANSFORM_RW 3
   png_byte    tested; /* the transform was tested somewhere */
} transform_info[] =
{
   /* List ALL the PNG_TRANSFORM_ macros here.  Check for support using the READ
    * macros; even if the transform is supported on write it cannot be tested
    * without the read support.
    */
#  define T(name,chunk,cm_required,cm_absent,bd,when)\
   {  #name, PNG_TRANSFORM_ ## name, CHUNK_ ## chunk,\
      COLOR_MASK_ ## cm_required, COLOR_MASK_ ## cm_absent, BD_ ## bd,\
      TRANSFORM_ ## when, 0/*!tested*/ }

#ifdef PNG_READ_STRIP_16_TO_8_SUPPORTED
   T(STRIP_16,            NONE, X,   X,   16,  R),
      /* drops the bottom 8 bits when bit depth is 16 */
#endif
#ifdef PNG_READ_STRIP_ALPHA_SUPPORTED
   T(STRIP_ALPHA,         NONE, A,   X,  ALL,  R),
      /* removes the alpha channel if present */
#endif
#ifdef PNG_WRITE_PACK_SUPPORTED
#  define TRANSFORM_RW_PACK TRANSFORM_RW
#else
#  define TRANSFORM_RW_PACK TRANSFORM_R
#endif
#ifdef PNG_READ_PACK_SUPPORTED
   T(PACKING,             NONE, X,   X,  LOW, RW_PACK),
      /* unpacks low-bit-depth components into 1 byte per component on read,
       * reverses this on write.
       */
#endif
#ifdef PNG_WRITE_PACKSWAP_SUPPORTED
#  define TRANSFORM_RW_PACKSWAP TRANSFORM_RW
#else
#  define TRANSFORM_RW_PACKSWAP TRANSFORM_R
#endif
#ifdef PNG_READ_PACKSWAP_SUPPORTED
   T(PACKSWAP,            NONE, X,   X,  LOW, RW_PACKSWAP),
      /* reverses the order of low-bit-depth components packed into a byte */
#endif
#ifdef PNG_READ_EXPAND_SUPPORTED
   T(EXPAND,              NONE, P,   X,  ALL,  R),
      /* expands PLTE PNG files to RGB (no tRNS) or RGBA (tRNS) *
       * Note that the 'EXPAND' transform does lots of different things: */
   T(EXPAND,              NONE, X,   C,  ALL,  R),
      /* expands grayscale PNG files to RGB, or RGBA */
   T(EXPAND,              tRNS, X,   A,  ALL,  R),
      /* expands the tRNS chunk in files without alpha */
#endif
#ifdef PNG_WRITE_INVERT_SUPPORTED
#  define TRANSFORM_RW_INVERT TRANSFORM_RW
#else
#  define TRANSFORM_RW_INVERT TRANSFORM_R
#endif
#ifdef PNG_READ_INVERT_SUPPORTED
   T(INVERT_MONO,         NONE, X,   C,  ALL, RW_INVERT),
      /* converts gray-scale components to 1..0 from 0..1 */
#endif
#ifdef PNG_WRITE_SHIFT_SUPPORTED
#  define TRANSFORM_RW_SHIFT TRANSFORM_RW
#else
#  define TRANSFORM_RW_SHIFT TRANSFORM_R
#endif
#ifdef PNG_READ_SHIFT_SUPPORTED
   T(SHIFT,               sBIT, X,   X,  ALL, RW_SHIFT),
      /* reduces component values to the original range based on the sBIT chunk,
       * this is only partially reversible - the low bits are lost and cannot be
       * recovered on write.  In fact write code replicates the bits to generate
       * new low-order bits.
       */
#endif
#ifdef PNG_WRITE_BGR_SUPPORTED
#  define TRANSFORM_RW_BGR TRANSFORM_RW
#else
#  define TRANSFORM_RW_BGR TRANSFORM_R
#endif
#ifdef PNG_READ_BGR_SUPPORTED
   T(BGR,                 NONE, C,   P, TRUE, RW_BGR),
      /* reverses the rgb component values of true-color pixels */
#endif
#ifdef PNG_WRITE_SWAP_ALPHA_SUPPORTED
#  define TRANSFORM_RW_SWAP_ALPHA TRANSFORM_RW
#else
#  define TRANSFORM_RW_SWAP_ALPHA TRANSFORM_R
#endif
#ifdef PNG_READ_SWAP_ALPHA_SUPPORTED
   T(SWAP_ALPHA,          NONE, A,   X, TRUE, RW_SWAP_ALPHA),
      /* swaps the alpha channel of RGBA or GA pixels to the front - ARGB or
       * AG, on write reverses the process.
       */
#endif
#ifdef PNG_WRITE_SWAP_SUPPORTED
#  define TRANSFORM_RW_SWAP TRANSFORM_RW
#else
#  define TRANSFORM_RW_SWAP TRANSFORM_R
#endif
#ifdef PNG_READ_SWAP_SUPPORTED
   T(SWAP_ENDIAN,         NONE, X,   P,   16, RW_SWAP),
      /* byte-swaps 16-bit component values */
#endif
#ifdef PNG_WRITE_INVERT_ALPHA_SUPPORTED
#  define TRANSFORM_RW_INVERT_ALPHA TRANSFORM_RW
#else
#  define TRANSFORM_RW_INVERT_ALPHA TRANSFORM_R
#endif
#ifdef PNG_READ_INVERT_ALPHA_SUPPORTED
   T(INVERT_ALPHA,        NONE, A,   X, TRUE, RW_INVERT_ALPHA),
      /* converts an alpha channel from 0..1 to 1..0 */
#endif
#ifdef PNG_WRITE_FILLER_SUPPORTED
   T(STRIP_FILLER_BEFORE, NONE, A,   P, TRUE,  W), /* 'A' for a filler! */
      /* on write skips a leading filler channel; testing requires data with a
       * filler channel so this is produced from RGBA or GA images by removing
       * the 'alpha' flag from the color type in place.
       */
   T(STRIP_FILLER_AFTER,  NONE, A,   P, TRUE,  W),
      /* on write strips a trailing filler channel */
#endif
#ifdef PNG_READ_GRAY_TO_RGB_SUPPORTED
   T(GRAY_TO_RGB,         NONE, X,   C,  ALL,  R),
      /* expands grayscale images to RGB, also causes the palette part of
       * 'EXPAND' to happen.  Low bit depth grayscale images are expanded to
       * 8-bits per component and no attempt is made to convert the image to a
       * palette image.  While this transform is partially reversible
       * png_write_png does not currently support this.
       */
   T(GRAY_TO_RGB,         NONE, P,   X,  ALL,  R),
      /* The 'palette' side effect mentioned above; a bit bogus but this is the
       * way the libpng code works.
       */
#endif
#ifdef PNG_READ_EXPAND_16_SUPPORTED
   T(EXPAND_16,           NONE, X,   X,  PAL,  R),
      /* expands images to 16-bits per component, as a side effect expands
       * palette images to RGB and expands the tRNS chunk if present, so it can
       * modify 16-bit per component images as well:
       */
   T(EXPAND_16,           tRNS, X,   A,   16,  R),
      /* side effect of EXPAND_16 - expands the tRNS chunk in an RGB or G 16-bit
       * image.
       */
#endif
#ifdef PNG_READ_SCALE_16_TO_8_SUPPORTED
   T(SCALE_16,            NONE, X,   X,   16,  R),
      /* scales 16-bit components to 8-bits. */
#endif

   { NULL /*name*/, 0, 0, 0, 0, 0, 0, 0/*!tested*/ }

#undef T
};

#define ARRAY_SIZE(a) ((sizeof a)/(sizeof a[0]))
#define TTABLE_SIZE ARRAY_SIZE(transform_info)

/* Some combinations of options that should be reversible are not; these cases
 * are bugs.
 */
static int known_bad_combos[][2] =
{
   /* problem, antidote */
   { PNG_TRANSFORM_SHIFT | PNG_TRANSFORM_INVERT_ALPHA, 0/*antidote*/ }
};

static int
is_combo(int transforms)
{
   return transforms & (transforms-1); /* non-zero if more than one set bit */
}

static int
first_transform(int transforms)
{
   return transforms & -transforms; /* lowest set bit */
}

static int
is_bad_combo(int transforms)
{
   unsigned int i;

   for (i=0; i<ARRAY_SIZE(known_bad_combos); ++i)
   {
      int combo = known_bad_combos[i][0];

      if ((combo & transforms) == combo &&
         (transforms & known_bad_combos[i][1]) == 0)
         return 1;
   }

   return 0; /* combo is ok */
}

static const char *
transform_name(int t)
   /* The name, if 't' has multiple bits set the name of the lowest set bit is
    * returned.
    */
{
   unsigned int i;

   t &= -t; /* first set bit */

   for (i=0; i<TTABLE_SIZE; ++i) if (transform_info[i].name != NULL)
   {
      if ((transform_info[i].transform & t) != 0)
         return transform_info[i].name;
   }

   return "invalid transform";
}

/* Variables calculated by validate_T below and used to record all the supported
 * transforms.  Need (unsigned int) here because of the places where these
 * values are used (unsigned compares in the 'exhaustive' iterator.)
 */
static unsigned int read_transforms, write_transforms, rw_transforms;

static void
validate_T(void)
   /* Validate the above table - this just builds the above values */
{
   unsigned int i;

   for (i=0; i<TTABLE_SIZE; ++i) if (transform_info[i].name != NULL)
   {
      if (transform_info[i].when & TRANSFORM_R)
         read_transforms |= transform_info[i].transform;

      if (transform_info[i].when & TRANSFORM_W)
         write_transforms |= transform_info[i].transform;
   }

   /* Reversible transforms are those which are supported on both read and
    * write.
    */
   rw_transforms = read_transforms & write_transforms;
}

/* FILE DATA HANDLING
 *    The original file is cached in memory.  During write the output file is
 *    written to memory.
 *
 *    In both cases the file data is held in a linked list of buffers - not all
 *    of these are in use at any time.
 */
#define NEW(type) ((type *)malloc(sizeof (type)))
#define DELETE(ptr) (free(ptr))

struct buffer_list
{
   struct buffer_list *next;         /* next buffer in list */
   png_byte            buffer[1024]; /* the actual buffer */
};

struct buffer
{
   struct buffer_list  *last;       /* last buffer in use */
   size_t               end_count;  /* bytes in the last buffer */
   struct buffer_list  *current;    /* current buffer being read */
   size_t               read_count; /* count of bytes read from current */
   struct buffer_list   first;      /* the very first buffer */
};

static void
buffer_init(struct buffer *buffer)
   /* Call this only once for a given buffer */
{
   buffer->first.next = NULL;
   buffer->last = NULL;
   buffer->current = NULL;
}

static void
buffer_destroy_list(struct buffer_list *list)
{
   if (list != NULL)
   {
      struct buffer_list *next = list->next;
      DELETE(list);
      buffer_destroy_list(next);
   }
}

static void
buffer_destroy(struct buffer *buffer)
{
   struct buffer_list *list = buffer->first.next;
   buffer_init(buffer);
   buffer_destroy_list(list);
}

#ifdef PNG_WRITE_PNG_SUPPORTED
static void
buffer_start_write(struct buffer *buffer)
{
   buffer->last = &buffer->first;
   buffer->end_count = 0;
   buffer->current = NULL;
}
#endif

static void
buffer_start_read(struct buffer *buffer)
{
   buffer->current = &buffer->first;
   buffer->read_count = 0;
}

#ifdef ENOMEM /* required by POSIX 1003.1 */
#  define MEMORY ENOMEM
#else
#  define MEMORY ERANGE /* required by ANSI-C */
#endif
static struct buffer *
get_buffer(png_structp pp)
   /* Used from libpng callbacks to get the current buffer */
{
   return (struct buffer*)png_get_io_ptr(pp);
}

static struct buffer_list *
buffer_extend(struct buffer_list *current)
{
   struct buffer_list *add;

   assert(current->next == NULL);

   add = NEW(struct buffer_list);
   if (add == NULL)
      return NULL;

   add->next = NULL;
   current->next = add;

   return add;
}

/* Load a buffer from a file; does the equivalent of buffer_start_write.  On a
 * read error returns an errno value, else returns 0.
 */
static int
buffer_from_file(struct buffer *buffer, FILE *fp)
{
   struct buffer_list *last = &buffer->first;
   size_t count = 0;

   for (;;)
   {
      size_t r = fread(last->buffer+count, 1/*size*/,
         (sizeof last->buffer)-count, fp);

      if (r > 0)
      {
         count += r;

         if (count >= sizeof last->buffer)
         {
            assert(count == sizeof last->buffer);
            count = 0;

            if (last->next == NULL)
            {
               last = buffer_extend(last);
               if (last == NULL)
                  return MEMORY;
            }

            else
               last = last->next;
         }
      }

      else /* fread failed - probably end of file */
      {
         if (feof(fp))
         {
            buffer->last = last;
            buffer->end_count = count;
            return 0; /* no error */
         }

         /* Some kind of funky error; errno should be non-zero */
         return errno == 0 ? ERANGE : errno;
      }
   }
}

/* This structure is used to control the test of a single file. */
typedef enum
{
   VERBOSE,        /* switches on all messages */
   INFORMATION,
   WARNINGS,       /* switches on warnings */
   LIBPNG_WARNING,
   APP_WARNING,
   ERRORS,         /* just errors */
   APP_FAIL,       /* continuable error - no need to longjmp */
   LIBPNG_ERROR,   /* this and higher cause a longjmp */
   LIBPNG_BUG,     /* erroneous behavior in libpng */
   APP_ERROR,      /* such as out-of-memory in a callback */
   QUIET,          /* no normal messages */
   USER_ERROR,     /* such as file-not-found */
   INTERNAL_ERROR
} error_level;
#define LEVEL_MASK      0xf   /* where the level is in 'options' */

#define EXHAUSTIVE      0x010 /* Test all combinations of active options */
#define STRICT          0x020 /* Fail on warnings as well as errors */
#define LOG             0x040 /* Log pass/fail to stdout */
#define CONTINUE        0x080 /* Continue on APP_FAIL errors */
#define SKIP_BUGS       0x100 /* Skip over known bugs */
#define LOG_SKIPPED     0x200 /* Log skipped bugs */
#define FIND_BAD_COMBOS 0x400 /* Attempt to deduce bad combos */
#define LIST_COMBOS     0x800 /* List combos by name */

/* Result masks apply to the result bits in the 'results' field below; these
 * bits are simple 1U<<error_level.  A pass requires either nothing worse than
 * warnings (--relaxes) or nothing worse than information (--strict)
 */
#define RESULT_STRICT(r)   (((r) & ~((1U<<WARNINGS)-1)) == 0)
#define RESULT_RELAXED(r)  (((r) & ~((1U<<ERRORS)-1)) == 0)

struct display
{
   jmp_buf        error_return;      /* Where to go to on error */

   const char    *filename;          /* The name of the original file */
   const char    *operation;         /* Operation being performed */
   int            transforms;        /* Transform used in operation */
   png_uint_32    options;           /* See display_log below */
   png_uint_32    results;           /* A mask of errors seen */


   png_structp    original_pp;       /* used on the original read */
   png_infop      original_ip;       /* set by the original read */

   size_t         original_rowbytes; /* of the original rows: */
   png_bytepp     original_rows;     /* from the original read */

   /* Original chunks valid */
   png_uint_32    chunks;

   /* Original IHDR information */
   png_uint_32    width;
   png_uint_32    height;
   int            bit_depth;
   int            color_type;
   int            interlace_method;
   int            compression_method;
   int            filter_method;

   /* Derived information for the original image. */
   int            active_transforms;  /* transforms that do something on read */
   int            ignored_transforms; /* transforms that should do nothing */

   /* Used on a read, both the original read and when validating a written
    * image.
    */
   png_structp    read_pp;
   png_infop      read_ip;

#  ifdef PNG_WRITE_PNG_SUPPORTED
      /* Used to write a new image (the original info_ptr is used) */
      png_structp   write_pp;
      struct buffer written_file;   /* where the file gets written */
#  endif

   struct buffer  original_file;     /* Data read from the original file */
};

static void
display_init(struct display *dp)
   /* Call this only once right at the start to initialize the control
    * structure, the (struct buffer) lists are maintained across calls - the
    * memory is not freed.
    */
{
   memset(dp, 0, sizeof *dp);
   dp->options = WARNINGS; /* default to !verbose, !quiet */
   dp->filename = NULL;
   dp->operation = NULL;
   dp->original_pp = NULL;
   dp->original_ip = NULL;
   dp->original_rows = NULL;
   dp->read_pp = NULL;
   dp->read_ip = NULL;
   buffer_init(&dp->original_file);

#  ifdef PNG_WRITE_PNG_SUPPORTED
      dp->write_pp = NULL;
      buffer_init(&dp->written_file);
#  endif
}

static void
display_clean_read(struct display *dp)
{
   if (dp->read_pp != NULL)
      png_destroy_read_struct(&dp->read_pp, &dp->read_ip, NULL);
}

#ifdef PNG_WRITE_PNG_SUPPORTED
static void
display_clean_write(struct display *dp)
{
      if (dp->write_pp != NULL)
         png_destroy_write_struct(&dp->write_pp, NULL);
}
#endif

static void
display_clean(struct display *dp)
{
#  ifdef PNG_WRITE_PNG_SUPPORTED
      display_clean_write(dp);
#  endif
   display_clean_read(dp);

   dp->original_rowbytes = 0;
   dp->original_rows = NULL;
   dp->chunks = 0;

   png_destroy_read_struct(&dp->original_pp, &dp->original_ip, NULL);
   /* leave the filename for error detection */
   dp->results = 0; /* reset for next time */
}

static void
display_destroy(struct display *dp)
{
    /* Release any memory held in the display. */
#  ifdef PNG_WRITE_PNG_SUPPORTED
      buffer_destroy(&dp->written_file);
#  endif

   buffer_destroy(&dp->original_file);
}

static struct display *
get_dp(png_structp pp)
   /* The display pointer is always stored in the png_struct error pointer */
{
   struct display *dp = (struct display*)png_get_error_ptr(pp);

   if (dp == NULL)
   {
      fprintf(stderr, "pngimage: internal error (no display)\n");
      exit(99); /* prevents a crash */
   }

   return dp;
}

/* error handling */
#ifdef __GNUC__
#  define VGATTR __attribute__((__format__ (__printf__,3,4)))
   /* Required to quiet GNUC warnings when the compiler sees a stdarg function
    * that calls one of the stdio v APIs.
    */
#else
#  define VGATTR
#endif
static void VGATTR
display_log(struct display *dp, error_level level, const char *fmt, ...)
   /* 'level' is as above, fmt is a stdio style format string.  This routine
    * does not return if level is above LIBPNG_WARNING
    */
{
   dp->results |= 1U << level;

   if (level > (error_level)(dp->options & LEVEL_MASK))
   {
      const char *lp;
      va_list ap;

      switch (level)
      {
         case INFORMATION:    lp = "information"; break;
         case LIBPNG_WARNING: lp = "warning(libpng)"; break;
         case APP_WARNING:    lp = "warning(pngimage)"; break;
         case APP_FAIL:       lp = "error(continuable)"; break;
         case LIBPNG_ERROR:   lp = "error(libpng)"; break;
         case LIBPNG_BUG:     lp = "bug(libpng)"; break;
         case APP_ERROR:      lp = "error(pngimage)"; break;
         case USER_ERROR:     lp = "error(user)"; break;

         case INTERNAL_ERROR: /* anything unexpected is an internal error: */
         case VERBOSE: case WARNINGS: case ERRORS: case QUIET:
         default:             lp = "bug(pngimage)"; break;
      }

      fprintf(stderr, "%s: %s: %s",
         dp->filename != NULL ? dp->filename : "<stdin>", lp, dp->operation);

      if (dp->transforms != 0)
      {
         int tr = dp->transforms;

         if (is_combo(tr))
         {
            if (dp->options & LIST_COMBOS)
            {
               int trx = tr;

               fprintf(stderr, "(");
               if (trx)
               {
                  int start = 0;

                  while (trx)
                  {
                     int trz = trx & -trx;

                     if (start) fprintf(stderr, "+");
                     fprintf(stderr, "%s", transform_name(trz));
                     start = 1;
                     trx &= ~trz;
                  }
               }

               else
                  fprintf(stderr, "-");
               fprintf(stderr, ")");
            }

            else
               fprintf(stderr, "(0x%x)", tr);
         }

         else
            fprintf(stderr, "(%s)", transform_name(tr));
      }

      fprintf(stderr, ": ");

      va_start(ap, fmt);
      vfprintf(stderr, fmt, ap);
      va_end(ap);

      fputc('\n', stderr);
   }
   /* else do not output any message */

   /* Errors cause this routine to exit to the fail code */
   if (level > APP_FAIL || (level > ERRORS && !(dp->options & CONTINUE)))
      longjmp(dp->error_return, level);
}

/* error handler callbacks for libpng */
static void PNGCBAPI
display_warning(png_structp pp, png_const_charp warning)
{
   display_log(get_dp(pp), LIBPNG_WARNING, "%s", warning);
}

static void PNGCBAPI
display_error(png_structp pp, png_const_charp error)
{
   struct display *dp = get_dp(pp);

   display_log(dp, LIBPNG_ERROR, "%s", error);
}

static void
display_cache_file(struct display *dp, const char *filename)
   /* Does the initial cache of the file. */
{
   FILE *fp;
   int ret;

   dp->filename = filename;

   if (filename != NULL)
   {
      fp = fopen(filename, "rb");
      if (fp == NULL)
         display_log(dp, USER_ERROR, "open failed: %s", strerror(errno));
   }

   else
      fp = stdin;

   ret = buffer_from_file(&dp->original_file, fp);

   fclose(fp);

   if (ret != 0)
      display_log(dp, APP_ERROR, "read failed: %s", strerror(ret));
}

static void
buffer_read(struct display *dp, struct buffer *bp, png_bytep data,
   size_t size)
{
   struct buffer_list *last = bp->current;
   size_t read_count = bp->read_count;

   while (size > 0)
   {
      size_t avail;

      if (last == NULL ||
         (last == bp->last && read_count >= bp->end_count))
      {
         display_log(dp, USER_ERROR, "file truncated (%lu bytes)",
            (unsigned long)size);
         /*NOTREACHED*/
         break;
      }

      else if (read_count >= sizeof last->buffer)
      {
         /* Move to the next buffer: */
         last = last->next;
         read_count = 0;
         bp->current = last; /* Avoid update outside the loop */

         /* And do a sanity check (the EOF case is caught above) */
         if (last == NULL)
         {
            display_log(dp, INTERNAL_ERROR, "damaged buffer list");
            /*NOTREACHED*/
            break;
         }
      }

      avail = (sizeof last->buffer) - read_count;
      if (avail > size)
         avail = size;

      memcpy(data, last->buffer + read_count, avail);
      read_count += avail;
      size -= avail;
      data += avail;
   }

   bp->read_count = read_count;
}

static void PNGCBAPI
read_function(png_structp pp, png_bytep data, size_t size)
{
   buffer_read(get_dp(pp), get_buffer(pp), data, size);
}

static void
read_png(struct display *dp, struct buffer *bp, const char *operation,
   int transforms)
{
   png_structp pp;
   png_infop   ip;

   /* This cleans out any previous read and sets operation and transforms to
    * empty.
    */
   display_clean_read(dp);

   if (operation != NULL) /* else this is a verify and do not overwrite info */
   {
      dp->operation = operation;
      dp->transforms = transforms;
   }

   dp->read_pp = pp = png_create_read_struct(PNG_LIBPNG_VER_STRING, dp,
      display_error, display_warning);
   if (pp == NULL)
      display_log(dp, LIBPNG_ERROR, "failed to create read struct");

   /* The png_read_png API requires us to make the info struct, but it does the
    * call to png_read_info.
    */
   dp->read_ip = ip = png_create_info_struct(pp);
   if (ip == NULL)
      display_log(dp, LIBPNG_ERROR, "failed to create info struct");

#  ifdef PNG_SET_USER_LIMITS_SUPPORTED
      /* Remove the user limits, if any */
      png_set_user_limits(pp, 0x7fffffff, 0x7fffffff);
#  endif

   /* Set the IO handling */
   buffer_start_read(bp);
   png_set_read_fn(pp, bp, read_function);

   png_read_png(pp, ip, transforms, NULL/*params*/);

#if 0 /* crazy debugging */
   {
      png_bytep pr = png_get_rows(pp, ip)[0];
      size_t rb = png_get_rowbytes(pp, ip);
      size_t cb;
      char c = ' ';

      fprintf(stderr, "%.4x %2d (%3lu bytes):", transforms, png_get_bit_depth(pp,ip), (unsigned long)rb);

      for (cb=0; cb<rb; ++cb)
         fputc(c, stderr), fprintf(stderr, "%.2x", pr[cb]), c='.';

      fputc('\n', stderr);
   }
#endif
}

static void
update_display(struct display *dp)
   /* called once after the first read to update all the info, original_pp and
    * original_ip must have been filled in.
    */
{
   png_structp pp;
   png_infop   ip;

   /* Now perform the initial read with a 0 transform. */
   read_png(dp, &dp->original_file, "original read", 0/*no transform*/);

   /* Move the result to the 'original' fields */
   dp->original_pp = pp = dp->read_pp, dp->read_pp = NULL;
   dp->original_ip = ip = dp->read_ip, dp->read_ip = NULL;

   dp->original_rowbytes = png_get_rowbytes(pp, ip);
   if (dp->original_rowbytes == 0)
      display_log(dp, LIBPNG_BUG, "png_get_rowbytes returned 0");

   dp->chunks = png_get_valid(pp, ip, 0xffffffff);
   if ((dp->chunks & PNG_INFO_IDAT) == 0) /* set by png_read_png */
      display_log(dp, LIBPNG_BUG, "png_read_png did not set IDAT flag");

   dp->original_rows = png_get_rows(pp, ip);
   if (dp->original_rows == NULL)
      display_log(dp, LIBPNG_BUG, "png_read_png did not create row buffers");

   if (!png_get_IHDR(pp, ip,
      &dp->width, &dp->height, &dp->bit_depth, &dp->color_type,
      &dp->interlace_method, &dp->compression_method, &dp->filter_method))
      display_log(dp, LIBPNG_BUG, "png_get_IHDR failed");

   /* 'active' transforms are discovered based on the original image format;
    * running one active transform can activate others.  At present the code
    * does not attempt to determine the closure.
    */
   {
      png_uint_32 chunks = dp->chunks;
      int active = 0, inactive = 0;
      int ct = dp->color_type;
      int bd = dp->bit_depth;
      unsigned int i;

      for (i=0; i<TTABLE_SIZE; ++i) if (transform_info[i].name != NULL)
      {
         int transform = transform_info[i].transform;

         if ((transform_info[i].valid_chunks == 0 ||
               (transform_info[i].valid_chunks & chunks) != 0) &&
            (transform_info[i].color_mask_required & ct) ==
               transform_info[i].color_mask_required &&
            (transform_info[i].color_mask_absent & ct) == 0 &&
            (transform_info[i].bit_depths & bd) != 0 &&
            (transform_info[i].when & TRANSFORM_R) != 0)
            active |= transform;

         else if ((transform_info[i].when & TRANSFORM_R) != 0)
            inactive |= transform;
      }

      /* Some transforms appear multiple times in the table; the 'active' status
       * is the logical OR of these and the inactive status must be adjusted to
       * take this into account.
       */
      inactive &= ~active;

      dp->active_transforms = active;
      dp->ignored_transforms = inactive; /* excluding write-only transforms */
   }
}

static int
compare_read(struct display *dp, int applied_transforms)
{
   /* Compare the png_info from read_ip with original_info */
   size_t rowbytes;
   png_uint_32 width, height;
   int bit_depth, color_type;
   int interlace_method, compression_method, filter_method;
   const char *e = NULL;

   png_get_IHDR(dp->read_pp, dp->read_ip, &width, &height, &bit_depth,
      &color_type, &interlace_method, &compression_method, &filter_method);

#  define C(item) if (item != dp->item) \
      display_log(dp, APP_WARNING, "IHDR " #item "(%lu) changed to %lu",\
         (unsigned long)dp->item, (unsigned long)item), e = #item

   /* The IHDR should be identical: */
   C(width);
   C(height);
   C(bit_depth);
   C(color_type);
   C(interlace_method);
   C(compression_method);
   C(filter_method);

   /* 'e' remains set to the name of the last thing changed: */
   if (e)
      display_log(dp, APP_ERROR, "IHDR changed (%s)", e);

   /* All the chunks from the original PNG should be preserved in the output PNG
    * because the PNG format has not been changed.
    */
   {
      unsigned long chunks =
         png_get_valid(dp->read_pp, dp->read_ip, 0xffffffff);

      if (chunks != dp->chunks)
         display_log(dp, APP_FAIL, "PNG chunks changed from 0x%lx to 0x%lx",
            (unsigned long)dp->chunks, chunks);
   }

   /* rowbytes should be the same */
   rowbytes = png_get_rowbytes(dp->read_pp, dp->read_ip);

   /* NOTE: on 64-bit systems this may trash the top bits of rowbytes,
    * which could lead to weird error messages.
    */
   if (rowbytes != dp->original_rowbytes)
      display_log(dp, APP_ERROR, "PNG rowbytes changed from %lu to %lu",
         (unsigned long)dp->original_rowbytes, (unsigned long)rowbytes);

   /* The rows should be the same too, unless the applied transforms includes
    * the shift transform, in which case low bits may have been lost.
    */
   {
      png_bytepp rows = png_get_rows(dp->read_pp, dp->read_ip);
      unsigned int mask;  /* mask (if not zero) for the final byte */

      if (bit_depth < 8)
      {
         /* Need the stray bits at the end, this depends only on the low bits
          * of the image width; overflow does not matter.  If the width is an
          * exact multiple of 8 bits this gives a mask of 0, not 0xff.
          */
         mask = 0xff & (0xff00 >> ((bit_depth * width) & 7));
      }

      else
         mask = 0;

      if (rows == NULL)
         display_log(dp, LIBPNG_BUG, "png_get_rows returned NULL");

      if ((applied_transforms & PNG_TRANSFORM_SHIFT) == 0 ||
         (dp->active_transforms & PNG_TRANSFORM_SHIFT) == 0 ||
         color_type == PNG_COLOR_TYPE_PALETTE)
      {
         unsigned long y;

         for (y=0; y<height; ++y)
         {
            png_bytep row = rows[y];
            png_bytep orig = dp->original_rows[y];

            if (memcmp(row, orig, rowbytes-(mask != 0)) != 0 || (mask != 0 &&
               ((row[rowbytes-1] & mask) != (orig[rowbytes-1] & mask))))
            {
               size_t x;

               /* Find the first error */
               for (x=0; x<rowbytes-1; ++x) if (row[x] != orig[x])
                  break;

               display_log(dp, APP_FAIL,
                  "byte(%lu,%lu) changed 0x%.2x -> 0x%.2x",
                  (unsigned long)x, (unsigned long)y, orig[x], row[x]);
               return 0; /* don't keep reporting failed rows on 'continue' */
            }
         }
      }

      else
#     ifdef PNG_sBIT_SUPPORTED
      {
         unsigned long y;
         int bpp;   /* bits-per-pixel then bytes-per-pixel */
         /* components are up to 8 bytes in size */
         png_byte sig_bits[8];
         png_color_8p sBIT;

         if (png_get_sBIT(dp->read_pp, dp->read_ip, &sBIT) != PNG_INFO_sBIT)
            display_log(dp, INTERNAL_ERROR,
               "active shift transform but no sBIT in file");

         switch (color_type)
         {
            case PNG_COLOR_TYPE_GRAY:
               sig_bits[0] = sBIT->gray;
               bpp = bit_depth;
               break;

            case PNG_COLOR_TYPE_GA:
               sig_bits[0] = sBIT->gray;
               sig_bits[1] = sBIT->alpha;
               bpp = 2 * bit_depth;
               break;

            case PNG_COLOR_TYPE_RGB:
               sig_bits[0] = sBIT->red;
               sig_bits[1] = sBIT->green;
               sig_bits[2] = sBIT->blue;
               bpp = 3 * bit_depth;
               break;

            case PNG_COLOR_TYPE_RGBA:
               sig_bits[0] = sBIT->red;
               sig_bits[1] = sBIT->green;
               sig_bits[2] = sBIT->blue;
               sig_bits[3] = sBIT->alpha;
               bpp = 4 * bit_depth;
               break;

            default:
               display_log(dp, LIBPNG_ERROR, "invalid colour type %d",
                  color_type);
               /*NOTREACHED*/
               bpp = 0;
               break;
         }

         {
            int b;

            for (b=0; 8*b<bpp; ++b)
            {
               /* libpng should catch this; if not there is a security issue
                * because an app (like this one) may overflow an array. In fact
                * libpng doesn't catch this at present.
                */
               if (sig_bits[b] == 0 || sig_bits[b] > bit_depth/*!palette*/)
                  display_log(dp, LIBPNG_BUG,
                     "invalid sBIT[%u]  value %d returned for PNG bit depth %d",
                     b, sig_bits[b], bit_depth);
            }
         }

         if (bpp < 8 && bpp != bit_depth)
         {
            /* sanity check; this is a grayscale PNG; something is wrong in the
             * code above.
             */
            display_log(dp, INTERNAL_ERROR, "invalid bpp %u for bit_depth %u",
               bpp, bit_depth);
         }

         switch (bit_depth)
         {
            int b;

            case 16: /* Two bytes per component, big-endian */
               for (b = (bpp >> 4); b > 0; --b)
               {
                  unsigned int sig = (unsigned int)(0xffff0000 >> sig_bits[b]);

                  sig_bits[2*b+1] = (png_byte)sig;
                  sig_bits[2*b+0] = (png_byte)(sig >> 8); /* big-endian */
               }
               break;

            case 8: /* One byte per component */
               for (b=0; b*8 < bpp; ++b)
                  sig_bits[b] = (png_byte)(0xff00 >> sig_bits[b]);
               break;

            case 1: /* allowed, but dumb */
               /* Value is 1 */
               sig_bits[0] = 0xff;
               break;

            case 2: /* Replicate 4 times */
               /* Value is 1 or 2 */
               b = 0x3 & ((0x3<<2) >> sig_bits[0]);
               b |= b << 2;
               b |= b << 4;
               sig_bits[0] = (png_byte)b;
               break;

            case 4: /* Relicate twice */
               /* Value is 1, 2, 3 or 4 */
               b = 0xf & ((0xf << 4) >> sig_bits[0]);
               b |= b << 4;
               sig_bits[0] = (png_byte)b;
               break;

            default:
               display_log(dp, LIBPNG_BUG, "invalid bit depth %d", bit_depth);
               break;
         }

         /* Convert bpp to bytes; this gives '1' for low-bit depth grayscale,
          * where there are multiple pixels per byte.
          */
         bpp = (bpp+7) >> 3;

         /* The mask can be combined with sig_bits[0] */
         if (mask != 0)
         {
            mask &= sig_bits[0];

            if (bpp != 1 || mask == 0)
               display_log(dp, INTERNAL_ERROR, "mask calculation error %u, %u",
                  bpp, mask);
         }

         for (y=0; y<height; ++y)
         {
            png_bytep row = rows[y];
            png_bytep orig = dp->original_rows[y];
            unsigned long x;

            for (x=0; x<(width-(mask!=0)); ++x)
            {
               int b;

               for (b=0; b<bpp; ++b)
               {
                  if ((*row++ & sig_bits[b]) != (*orig++ & sig_bits[b]))
                  {
                     display_log(dp, APP_FAIL,
                        "significant bits at (%lu[%u],%lu) changed %.2x->%.2x",
                        x, b, y, orig[-1], row[-1]);
                     return 0;
                  }
               }
            }

            if (mask != 0 && (*row & mask) != (*orig & mask))
            {
               display_log(dp, APP_FAIL,
                  "significant bits at (%lu[end],%lu) changed", x, y);
               return 0;
            }
         } /* for y */
      }
#     else /* !sBIT */
         display_log(dp, INTERNAL_ERROR,
               "active shift transform but no sBIT support");
#     endif /* !sBIT */
   }

   return 1; /* compare succeeded */
}

#ifdef PNG_WRITE_PNG_SUPPORTED
static void
buffer_write(struct display *dp, struct buffer *buffer, png_bytep data,
   size_t size)
   /* Generic write function used both from the write callback provided to
    * libpng and from the generic read code.
    */
{
   /* Write the data into the buffer, adding buffers as required */
   struct buffer_list *last = buffer->last;
   size_t end_count = buffer->end_count;

   while (size > 0)
   {
      size_t avail;

      if (end_count >= sizeof last->buffer)
      {
         if (last->next == NULL)
         {
            last = buffer_extend(last);

            if (last == NULL)
               display_log(dp, APP_ERROR, "out of memory saving file");
         }

         else
            last = last->next;

         buffer->last = last; /* avoid the need to rewrite every time */
         end_count = 0;
      }

      avail = (sizeof last->buffer) - end_count;
      if (avail > size)
         avail = size;

      memcpy(last->buffer + end_count, data, avail);
      end_count += avail;
      size -= avail;
      data += avail;
   }

   buffer->end_count = end_count;
}

static void PNGCBAPI
write_function(png_structp pp, png_bytep data, size_t size)
{
   buffer_write(get_dp(pp), get_buffer(pp), data, size);
}

static void
write_png(struct display *dp, png_infop ip, int transforms)
{
   display_clean_write(dp); /* safety */

   buffer_start_write(&dp->written_file);
   dp->operation = "write";
   dp->transforms = transforms;

   dp->write_pp = png_create_write_struct(PNG_LIBPNG_VER_STRING, dp,
      display_error, display_warning);

   if (dp->write_pp == NULL)
      display_log(dp, APP_ERROR, "failed to create write png_struct");

   png_set_write_fn(dp->write_pp, &dp->written_file, write_function,
      NULL/*flush*/);

#  ifdef PNG_SET_USER_LIMITS_SUPPORTED
      /* Remove the user limits, if any */
      png_set_user_limits(dp->write_pp, 0x7fffffff, 0x7fffffff);
#  endif

   /* Certain transforms require the png_info to be zapped to allow the
    * transform to work correctly.
    */
   if (transforms & (PNG_TRANSFORM_PACKING|
                     PNG_TRANSFORM_STRIP_FILLER|
                     PNG_TRANSFORM_STRIP_FILLER_BEFORE))
   {
      int ct = dp->color_type;

      if (transforms & (PNG_TRANSFORM_STRIP_FILLER|
                        PNG_TRANSFORM_STRIP_FILLER_BEFORE))
         ct &= ~PNG_COLOR_MASK_ALPHA;

      png_set_IHDR(dp->write_pp, ip, dp->width, dp->height, dp->bit_depth, ct,
         dp->interlace_method, dp->compression_method, dp->filter_method);
   }

   png_write_png(dp->write_pp, ip, transforms, NULL/*params*/);

   /* Clean it on the way out - if control returns to the caller then the
    * written_file contains the required data.
    */
   display_clean_write(dp);
}
#endif /* WRITE_PNG */

static int
skip_transform(struct display *dp, int tr)
   /* Helper to test for a bad combo and log it if it is skipped */
{
   if ((dp->options & SKIP_BUGS) != 0 && is_bad_combo(tr))
   {
      /* Log this to stdout if logging is on, otherwise just do an information
       * display_log.
       */
      if ((dp->options & LOG_SKIPPED) != 0)
      {
         printf("SKIP: %s transforms ", dp->filename);

         while (tr != 0)
         {
            int next = first_transform(tr);
            tr &= ~next;

            printf("%s", transform_name(next));
            if (tr != 0)
               putchar('+');
         }

         putchar('\n');
      }

      else
         display_log(dp, INFORMATION, "%s: skipped known bad combo 0x%x",
            dp->filename, tr);

      return 1; /* skip */
   }

   return 0; /* don't skip */
}

static void
test_one_file(struct display *dp, const char *filename)
{
   /* First cache the file and update the display original file
    * information for the new file.
    */
   dp->operation = "cache file";
   dp->transforms = 0;
   display_cache_file(dp, filename);
   update_display(dp);

   /* First test: if there are options that should be ignored for this file
    * verify that they really are ignored.
    */
   if (dp->ignored_transforms != 0)
   {
      read_png(dp, &dp->original_file, "ignored transforms",
         dp->ignored_transforms);

      /* The result should be identical to the original_rows */
      if (!compare_read(dp, 0/*transforms applied*/))
         return; /* no point testing more */
   }

#ifdef PNG_WRITE_PNG_SUPPORTED
   /* Second test: write the original PNG data out to a new file (to test the
    * write side) then read the result back in and make sure that it hasn't
    * changed.
    */
   dp->operation = "write";
   write_png(dp, dp->original_ip, 0/*transforms*/);
   read_png(dp, &dp->written_file, NULL, 0/*transforms*/);
   if (!compare_read(dp, 0/*transforms applied*/))
      return;
#endif

   /* Third test: the active options.  Test each in turn, or, with the
    * EXHAUSTIVE option, test all possible combinations.
    */
   {
      /* Use unsigned int here because the code below to increment through all
       * the possibilities exhaustively has to use a compare and that must be
       * unsigned, because some transforms are negative on a 16-bit system.
       */
      unsigned int active = dp->active_transforms;
      int exhaustive = (dp->options & EXHAUSTIVE) != 0;
      unsigned int current = first_transform(active);
      unsigned int bad_transforms = 0;
      unsigned int bad_combo = ~0U;    /* bitwise AND of failing transforms */
      unsigned int bad_combo_list = 0; /* bitwise OR of failures */

      for (;;)
      {
         read_png(dp, &dp->original_file, "active transforms", current);

         /* If this involved any irreversible transformations then if we write
          * it out with just the reversible transformations and read it in again
          * with the same transforms we should get the same thing.  At present
          * this isn't done - it just seems like a waste of time and it would
          * require two sets of read png_struct/png_info.
          *
          * If there were no irreversible transformations then if we write it
          * out and read it back in again (without the reversible transforms)
          * we should get back to the place where we started.
          */
#ifdef PNG_WRITE_PNG_SUPPORTED
         if ((current & write_transforms) == current)
         {
            /* All transforms reversible: write the PNG with the transformations
             * reversed, then read it back in with no transformations.  The
             * result should be the same as the original apart from the loss of
             * low order bits because of the SHIFT/sBIT transform.
             */
            dp->operation = "reversible transforms";
            write_png(dp, dp->read_ip, current);

            /* And if this is read back in, because all the transformations were
             * reversible, the result should be the same.
             */
            read_png(dp, &dp->written_file, NULL, 0);
            if (!compare_read(dp, current/*for the SHIFT/sBIT transform*/))
            {
               /* This set of transforms failed.  If a single bit is set - if
                * there is just one transform - don't include this in further
                * 'exhaustive' tests.  Notice that each transform is tested on
                * its own before testing combos in the exhaustive case.
                */
               if (is_combo(current))
               {
                  bad_combo &= current;
                  bad_combo_list |= current;
               }

               else
                  bad_transforms |= current;
            }
         }
#endif

         /* Now move to the next transform */
         if (exhaustive) /* all combinations */
         {
            unsigned int next = current;

            do
            {
               if (next == read_transforms) /* Everything tested */
                  goto combo;

               ++next;
            }  /* skip known bad combos if the relevant option is set; skip
                * combos involving known bad single transforms in all cases.
                */
            while (  (next & read_transforms) <= current
                  || (next & active) == 0 /* skip cases that do nothing */
                  || (next & bad_transforms) != 0
                  || skip_transform(dp, next));

            assert((next & read_transforms) == next);
            current = next;
         }

         else /* one at a time */
         {
            active &= ~current;

            if (active == 0)
               goto combo;

            current = first_transform(active);
         }
      }

combo:
      if (dp->options & FIND_BAD_COMBOS)
      {
         /* bad_combos identifies the combos that occur in all failing cases;
          * bad_combo_list identifies transforms that do not prevent the
          * failure.
          */
         if (bad_combo != ~0U)
            printf("%s[0x%x]: PROBLEM: 0x%x[0x%x] ANTIDOTE: 0x%x\n",
               dp->filename, active, bad_combo, bad_combo_list,
               rw_transforms & ~bad_combo_list);

         else
            printf("%s: no %sbad combos found\n", dp->filename,
               (dp->options & SKIP_BUGS) ? "additional " : "");
      }
   }
}

static int
do_test(struct display *dp, const char *file)
   /* Exists solely to isolate the setjmp clobbers */
{
   int ret = setjmp(dp->error_return);

   if (ret == 0)
   {
      test_one_file(dp, file);
      return 0;
   }

   else if (ret < ERRORS) /* shouldn't longjmp on warnings */
      display_log(dp, INTERNAL_ERROR, "unexpected return code %d", ret);

   return ret;
}

int
main(int argc, char **argv)
{
   /* For each file on the command line test it with a range of transforms */
   int option_end, ilog = 0;
   struct display d;

   validate_T();
   display_init(&d);

   for (option_end=1; option_end<argc; ++option_end)
   {
      const char *name = argv[option_end];

      if (strcmp(name, "--verbose") == 0)
         d.options = (d.options & ~LEVEL_MASK) | VERBOSE;

      else if (strcmp(name, "--warnings") == 0)
         d.options = (d.options & ~LEVEL_MASK) | WARNINGS;

      else if (strcmp(name, "--errors") == 0)
         d.options = (d.options & ~LEVEL_MASK) | ERRORS;

      else if (strcmp(name, "--quiet") == 0)
         d.options = (d.options & ~LEVEL_MASK) | QUIET;

      else if (strcmp(name, "--exhaustive") == 0)
         d.options |= EXHAUSTIVE;

      else if (strcmp(name, "--fast") == 0)
         d.options &= ~EXHAUSTIVE;

      else if (strcmp(name, "--strict") == 0)
         d.options |= STRICT;

      else if (strcmp(name, "--relaxed") == 0)
         d.options &= ~STRICT;

      else if (strcmp(name, "--log") == 0)
      {
         ilog = option_end; /* prevent display */
         d.options |= LOG;
      }

      else if (strcmp(name, "--nolog") == 0)
         d.options &= ~LOG;

      else if (strcmp(name, "--continue") == 0)
         d.options |= CONTINUE;

      else if (strcmp(name, "--stop") == 0)
         d.options &= ~CONTINUE;

      else if (strcmp(name, "--skip-bugs") == 0)
         d.options |= SKIP_BUGS;

      else if (strcmp(name, "--test-all") == 0)
         d.options &= ~SKIP_BUGS;

      else if (strcmp(name, "--log-skipped") == 0)
         d.options |= LOG_SKIPPED;

      else if (strcmp(name, "--nolog-skipped") == 0)
         d.options &= ~LOG_SKIPPED;

      else if (strcmp(name, "--find-bad-combos") == 0)
         d.options |= FIND_BAD_COMBOS;

      else if (strcmp(name, "--nofind-bad-combos") == 0)
         d.options &= ~FIND_BAD_COMBOS;

      else if (strcmp(name, "--list-combos") == 0)
         d.options |= LIST_COMBOS;

      else if (strcmp(name, "--nolist-combos") == 0)
         d.options &= ~LIST_COMBOS;

      else if (name[0] == '-' && name[1] == '-')
      {
         fprintf(stderr, "pngimage: %s: unknown option\n", name);
         return 99;
      }

      else
         break; /* Not an option */
   }

   {
      int i;
      int errors = 0;

      for (i=option_end; i<argc; ++i)
      {
         {
            int ret = do_test(&d, argv[i]);

            if (ret > QUIET) /* abort on user or internal error */
               return 99;
         }

         /* Here on any return, including failures, except user/internal issues
          */
         {
            int pass = (d.options & STRICT) ?
               RESULT_STRICT(d.results) : RESULT_RELAXED(d.results);

            if (!pass)
               ++errors;

            if (d.options & LOG)
            {
               int j;

               printf("%s: pngimage ", pass ? "PASS" : "FAIL");

               for (j=1; j<option_end; ++j) if (j != ilog)
                  printf("%s ", argv[j]);

               printf("%s\n", d.filename);
            }
         }

         display_clean(&d);
      }

      /* Release allocated memory */
      display_destroy(&d);

      return errors != 0;
   }
}
#else /* !READ_PNG */
int
main(void)
{
   fprintf(stderr, "pngimage: no support for png_read/write_image\n");
   return SKIP;
}
#endif

/* pngcp.c
 *
 * Copyright (c) 2016 John Cunningham Bowler
 *
 * Last changed in libpng 1.6.24 [August 4, 2016]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * This is an example of copying a PNG without changes using the png_read_png
 * and png_write_png interfaces.  A considerable number of options are provided
 * to manipulate the compression of the PNG data and other compressed chunks.
 *
 * For a more extensive example that uses the transforms see
 * contrib/libtests/pngimage.c in the libpng distribution.
 */
#include "pnglibconf.h" /* To find how libpng was configured. */

#ifdef PNG_PNGCP_TIMING_SUPPORTED
   /* WARNING:
    *
    * This test is here to allow POSIX.1b extensions to be used if enabled in
    * the compile; specifically the code requires_POSIX_C_SOURCE support of
    * 199309L or later to enable clock_gettime use.
    *
    * IF this causes problems THEN compile with a strict ANSI C compiler and let
    * this code turn on the POSIX features that it minimally requires.
    *
    * IF this does not work there is probably a bug in your ANSI C compiler or
    * your POSIX implementation.
    */
#  define _POSIX_C_SOURCE 199309L
#else /* No timing support required */
#  define _POSIX_SOURCE 1
#endif

#if defined(HAVE_CONFIG_H) && !defined(PNG_NO_CONFIG_H)
#  include <config.h>
#endif

#include <stdio.h>

/* Define the following to use this test against your installed libpng, rather
 * than the one being built here:
 */
#ifdef PNG_FREESTANDING_TESTS
#  include <png.h>
#else
#  include "../../png.h"
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

#if (defined(PNG_READ_PNG_SUPPORTED)) && (defined(PNG_WRITE_PNG_SUPPORTED))
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <assert.h>

#include <unistd.h>
#include <sys/stat.h>

#include <zlib.h>

#ifndef PNG_SETJMP_SUPPORTED
#  include <setjmp.h> /* because png.h did *not* include this */
#endif

#ifdef __cplusplus
#  define voidcast(type, value) static_cast<type>(value)
#else
#  define voidcast(type, value) (value)
#endif /* __cplusplus */

#ifdef __GNUC__
   /* Many versions of GCC erroneously report that local variables unmodified
    * within the scope of a setjmp may be clobbered.  This hacks round the
    * problem (sometimes) without harming other compilers.
    */
#  define gv volatile
#else
#  define gv
#endif

/* 'CLOCK_PROCESS_CPUTIME_ID' is one of the clock timers for clock_gettime.  It
 * need not be supported even when clock_gettime is available.  It returns the
 * 'CPU' time the process has consumed.  'CPU' time is assumed to include time
 * when the CPU is actually blocked by a pending cache fill but not time
 * waiting for page faults.  The attempt is to get a measure of the actual time
 * the implementation takes to read a PNG ignoring the potentially very large IO
 * overhead.
 */
#ifdef PNG_PNGCP_TIMING_SUPPORTED
#  include <time.h>   /* clock_gettime and associated definitions */
#  ifndef CLOCK_PROCESS_CPUTIME_ID
      /* Prevent inclusion of the spurious code: */
#     undef PNG_PNGCP_TIMING_SUPPORTED
#  endif
#endif /* PNGCP_TIMING */

/* So if the timing feature has been activated: */

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

#define STRICT          0x010 /* Fail on warnings as well as errors */
#define LOG             0x020 /* Log pass/fail to stdout */
#define CONTINUE        0x040 /* Continue on APP_FAIL errors */
#define SIZES           0x080 /* Report input and output sizes */
#define SEARCH          0x100 /* Search IDAT compression options */
#define NOWRITE         0x200 /* Do not write an output file */
#ifdef PNG_CHECK_FOR_INVALID_INDEX_SUPPORTED
#  define IGNORE_INDEX  0x400 /* Ignore out of range palette indices (BAD!) */
#  ifdef PNG_GET_PALETTE_MAX_SUPPORTED
#     define FIX_INDEX  0x800 /* 'Fix' out of range palette indices (OK) */
#  endif /* GET_PALETTE_MAX */
#endif /* CHECK_FOR_INVALID_INDEX */
#define OPTION     0x80000000 /* Used for handling options */
#define LIST       0x80000001 /* Used for handling options */

/* Result masks apply to the result bits in the 'results' field below; these
 * bits are simple 1U<<error_level.  A pass requires either nothing worse than
 * warnings (--relaxes) or nothing worse than information (--strict)
 */
#define RESULT_STRICT(r)   (((r) & ~((1U<<WARNINGS)-1)) == 0)
#define RESULT_RELAXED(r)  (((r) & ~((1U<<ERRORS)-1)) == 0)

/* OPTION DEFINITIONS */
static const char range_lo[] = "low";
static const char range_hi[] = "high";
static const char all[] = "all";
#define RANGE(lo,hi) { range_lo, lo }, { range_hi, hi }
typedef struct value_list
{
   const char *name;  /* the command line name of the value */
   int         value; /* the actual value to use */
}  value_list;

static const value_list
#ifdef PNG_SW_COMPRESS_png_level
vl_compression[] =
{
   /* Overall compression control.  The order controls the search order for
    * 'all'.  Since the search is for the smallest the order used is low memory
    * then high speed.
    */
   { "low-memory",      PNG_COMPRESSION_LOW_MEMORY },
   { "high-speed",      PNG_COMPRESSION_HIGH_SPEED },
   { "high-read-speed", PNG_COMPRESSION_HIGH_READ_SPEED },
   { "low",             PNG_COMPRESSION_LOW },
   { "medium",          PNG_COMPRESSION_MEDIUM },
   { "old",             PNG_COMPRESSION_COMPAT },
   { "high",            PNG_COMPRESSION_HIGH },
   { all, 0 }
},
#endif /* SW_COMPRESS_png_level */

#if defined(PNG_WRITE_CUSTOMIZE_COMPRESSION_SUPPORTED) ||\
    defined(PNG_WRITE_CUSTOMIZE_ZTXT_COMPRESSION_SUPPORTED)
vl_strategy[] =
{
   /* This controls the order of search. */
   { "huffman", Z_HUFFMAN_ONLY },
   { "RLE", Z_RLE },
   { "fixed", Z_FIXED }, /* the remainder do window searches */
   { "filtered", Z_FILTERED },
   { "default", Z_DEFAULT_STRATEGY },
   { all, 0 }
},
#ifdef PNG_WRITE_CUSTOMIZE_ZTXT_COMPRESSION_SUPPORTED
vl_windowBits_text[] =
{
   { "default", MAX_WBITS/*from zlib*/ },
   { "minimum", 8 },
   RANGE(8, MAX_WBITS/*from zlib*/),
   { all, 0 }
},
#endif /* text compression */
vl_level[] =
{
   { "default", Z_DEFAULT_COMPRESSION /* this is -1 */ },
   { "none", Z_NO_COMPRESSION },
   { "speed", Z_BEST_SPEED },
   { "best", Z_BEST_COMPRESSION },
   { "0", Z_NO_COMPRESSION },
   RANGE(1, 9), /* this deliberately excludes '0' */
   { all, 0 }
},
vl_memLevel[] =
{
   { "max", MAX_MEM_LEVEL }, /* zlib maximum */
   { "1", 1 }, /* zlib minimum */
   { "default", 8 }, /* zlib default */
   { "2", 2 },
   { "3", 3 },
   { "4", 4 },
   { "5", 5 }, /* for explicit testing */
   RANGE(6, MAX_MEM_LEVEL/*zlib*/), /* exclude 5 and below: zlib bugs */
   { all, 0 }
},
#endif /* WRITE_CUSTOMIZE_*COMPRESSION */
#ifdef PNG_WRITE_FILTER_SUPPORTED
vl_filter[] =
{
   { all,      PNG_ALL_FILTERS   },
   { "off",    PNG_NO_FILTERS    },
   { "none",   PNG_FILTER_NONE   },
   { "sub",    PNG_FILTER_SUB    },
   { "up",     PNG_FILTER_UP     },
   { "avg",    PNG_FILTER_AVG    },
   { "paeth",  PNG_FILTER_PAETH  }
},
#endif /* WRITE_FILTER */
#ifdef PNG_PNGCP_TIMING_SUPPORTED
#  define PNGCP_TIME_READ  1
#  define PNGCP_TIME_WRITE 2
vl_time[] =
{
   { "both",  PNGCP_TIME_READ+PNGCP_TIME_WRITE },
   { "off",   0 },
   { "read",  PNGCP_TIME_READ },
   { "write", PNGCP_TIME_WRITE }
},
#endif /* PNGCP_TIMING */
vl_IDAT_size[] = /* for png_set_IDAT_size */
{
   { "default", 0x7FFFFFFF },
   { "minimal", 1 },
   RANGE(1, 0x7FFFFFFF)
},
#ifndef PNG_SW_IDAT_size
   /* Pre 1.7 API: */
#  define png_set_IDAT_size(p,v) png_set_compression_buffer_size(p, v)
#endif /* !SW_IDAT_size */
#define SL 8 /* stack limit in display, below */
vl_log_depth[] = { { "on", 1 }, { "off", 0 }, RANGE(0, SL) },
vl_on_off[] = { { "on", 1 }, { "off", 0 } };

#ifdef PNG_WRITE_CUSTOMIZE_COMPRESSION_SUPPORTED
static value_list
vl_windowBits_IDAT[] =
{
   { "default", MAX_WBITS },
   { "small", 9 },
   RANGE(8, MAX_WBITS), /* modified by set_windowBits_hi */
   { all, 0 }
};
#endif /* IDAT compression */

typedef struct option
{
   const char       *name;         /* name of the option */
   png_uint_32       opt;          /* an option, or OPTION or LIST */
   png_byte          search;       /* Search on --search */
   png_byte          value_count;  /* length of the list of values: */
   const value_list *values;       /* values for OPTION or LIST */
}  option;

static const option options[] =
{
   /* struct display options, these are set when the command line is read */
#  define S(n,v) { #n, v, 0, 2, vl_on_off },
   S(verbose,  VERBOSE)
   S(warnings, WARNINGS)
   S(errors,   ERRORS)
   S(quiet,    QUIET)
   S(strict,   STRICT)
   S(log,      LOG)
   S(continue, CONTINUE)
   S(sizes,    SIZES)
   S(search,   SEARCH)
   S(nowrite,  NOWRITE)
#  ifdef IGNORE_INDEX
      S(ignore-palette-index, IGNORE_INDEX)
#  endif /* IGNORE_INDEX */
#  ifdef FIX_INDEX
      S(fix-palette-index, FIX_INDEX)
#  endif /* FIX_INDEX */
#  undef S

   /* OPTION settings, these and LIST settings are read on demand */
#  define VLNAME(name) vl_ ## name
#  define VLSIZE(name) voidcast(png_byte,\
                           (sizeof VLNAME(name))/(sizeof VLNAME(name)[0]))
#  define VL(oname, name, type, search)\
   { oname, type, search, VLSIZE(name), VLNAME(name) },
#  define VLO(oname, name, search) VL(oname, name, OPTION, search)

#  ifdef PNG_WRITE_CUSTOMIZE_COMPRESSION_SUPPORTED
#     define VLCIDAT(name) VLO(#name, name, 1/*search*/)
#     ifdef PNG_SW_COMPRESS_level
#        define VLCiCCP(name) VLO("ICC-profile-" #name, name, 0/*search*/)
#     else
#        define VLCiCCP(name)
#     endif
#  else
#     define VLCIDAT(name)
#     define VLCiCCP(name)
#  endif /* WRITE_CUSTOMIZE_COMPRESSION */

#  ifdef PNG_WRITE_CUSTOMIZE_ZTXT_COMPRESSION_SUPPORTED
#     define VLCzTXt(name) VLO("text-" #name, name, 0/*search*/)
#  else
#     define VLCzTXt(name)
#  endif /* WRITE_CUSTOMIZE_ZTXT_COMPRESSION */

#  define VLC(name) VLCIDAT(name) VLCiCCP(name) VLCzTXt(name)

#  ifdef PNG_SW_COMPRESS_png_level
      /* The libpng compression level isn't searched because it justs sets the
       * other things that are searched!
       */
      VLO("compression", compression, 0)
      VLO("text-compression", compression, 0)
      VLO("ICC-profile-compression", compression, 0)
#  endif /* SW_COMPRESS_png_level */
   VLC(strategy)
   VLO("windowBits", windowBits_IDAT, 1)
#  ifdef PNG_SW_COMPRESS_windowBits
      VLO("ICC-profile-windowBits", windowBits_text/*sic*/, 0)
#  endif
   VLO("text-windowBits", windowBits_text, 0)
   VLC(level)
   VLC(memLevel)
   VLO("IDAT-size", IDAT_size, 0)
   VLO("log-depth", log_depth, 0)

#  undef VLO

   /* LIST settings */
#  define VLL(name, search) VL(#name, name, LIST, search)
#ifdef PNG_WRITE_FILTER_SUPPORTED
   VLL(filter, 0)
#endif /* WRITE_FILTER */
#ifdef PNG_PNGCP_TIMING_SUPPORTED
   VLL(time, 0)
#endif /* PNGCP_TIMING */
#  undef VLL
#  undef VL
};

#ifdef __cplusplus
   static const size_t option_count((sizeof options)/(sizeof options[0]));
#else /* !__cplusplus */
#  define option_count ((sizeof options)/(sizeof options[0]))
#endif /* !__cplusplus */

static const char *
cts(int ct)
{
   switch (ct)
   {
      case PNG_COLOR_TYPE_PALETTE:     return "P";
      case PNG_COLOR_TYPE_GRAY:        return "G";
      case PNG_COLOR_TYPE_GRAY_ALPHA:  return "GA";
      case PNG_COLOR_TYPE_RGB:         return "RGB";
      case PNG_COLOR_TYPE_RGB_ALPHA:   return "RGBA";
      default:                         return "INVALID";
   }
}

struct display
{
   jmp_buf          error_return;      /* Where to go to on error */
   unsigned int     errset;            /* error_return is set */

   const char      *operation;         /* What is happening */
   const char      *filename;          /* The name of the original file */
   const char      *output_file;       /* The name of the output file */

   /* Used on both read and write: */
   FILE            *fp;

   /* Used on a read, both the original read and when validating a written
    * image.
    */
   png_alloc_size_t read_size;
   png_structp      read_pp;
   png_infop        ip;
#  if PNG_LIBPNG_VER < 10700 && defined PNG_TEXT_SUPPORTED
      png_textp     text_ptr; /* stash of text chunks */
      int           num_text;
      int           text_stashed;
#  endif /* pre 1.7 */

#  ifdef PNG_PNGCP_TIMING_SUPPORTED
      struct timespec   read_time;
      struct timespec   read_time_total;
      struct timespec   write_time;
      struct timespec   write_time_total;
#  endif /* PNGCP_TIMING */

   /* Used to write a new image (the original info_ptr is used) */
#  define MAX_SIZE ((png_alloc_size_t)(-1))
   png_alloc_size_t write_size;
   png_alloc_size_t best_size;
   png_structp      write_pp;

   /* Base file information */
   png_alloc_size_t size;
   png_uint_32      w;
   png_uint_32      h;
   int              bpp;
   png_byte         ct;
   int              no_warnings;       /* Do not output libpng warnings */
   int              min_windowBits;    /* The windowBits range is 8..8 */

   /* Options handling */
   png_uint_32      results;             /* A mask of errors seen */
   png_uint_32      options;             /* See display_log below */
   png_byte         entry[option_count]; /* The selected entry+1 of an option
                                          * that appears on the command line, or
                                          * 0 if it was not given. */
   int              value[option_count]; /* Corresponding value */

   /* Compression exhaustive testing */
   /* Temporary variables used only while testing a single collection of
    * settings:
    */
   unsigned int     csp;               /* next stack entry to use */
   unsigned int     nsp;               /* highest active entry+1 found so far */

   /* Values used while iterating through all the combinations of settings for a
    * single file:
    */
   unsigned int     tsp;               /* nsp from the last run; this is the
                                        * index+1 of the highest active entry on
                                        * this run; this entry will be advanced.
                                        */
   int              opt_string_start;  /* Position in buffer for the first
                                        * searched option; non-zero if earlier
                                        * options were set on the command line.
                                        */
   struct stack
   {
      png_alloc_size_t best_size;      /* Best so far for this option */
      png_alloc_size_t lo_size;
      png_alloc_size_t hi_size;
      int              lo, hi;         /* For binary chop of a range */
      int              best_val;       /* Best value found so far */
      int              opt_string_end; /* End of the option string in 'curr' */
      png_byte         opt;            /* The option being tested */
      png_byte         entry;          /* The next value entry to be tested */
      png_byte         end;            /* This is the last entry */
   }                stack[SL];         /* Stack of entries being tested */
   char             curr[32*SL];       /* current options being tested */
   char             best[32*SL];       /* best options */

   char             namebuf[FILENAME_MAX]; /* output file name */
};

static void
display_init(struct display *dp)
   /* Call this only once right at the start to initialize the control
    * structure, the (struct buffer) lists are maintained across calls - the
    * memory is not freed.
    */
{
   memset(dp, 0, sizeof *dp);
   dp->operation = "internal error";
   dp->filename = "command line";
   dp->output_file = "no output file";
   dp->options = WARNINGS; /* default to !verbose, !quiet */
   dp->fp = NULL;
   dp->read_pp = NULL;
   dp->ip = NULL;
   dp->write_pp = NULL;
   dp->min_windowBits = -1; /* this is an OPTIND, so -1 won't match anything */
#  if PNG_LIBPNG_VER < 10700 && defined PNG_TEXT_SUPPORTED
      dp->text_ptr = NULL;
      dp->num_text = 0;
      dp->text_stashed = 0;
#  endif /* pre 1.7 */
}

static void
display_clean_read(struct display *dp)
{
   if (dp->read_pp != NULL)
      png_destroy_read_struct(&dp->read_pp, NULL, NULL);

   if (dp->fp != NULL)
   {
      FILE *fp = dp->fp;
      dp->fp = NULL;
      (void)fclose(fp);
   }
}

static void
display_clean_write(struct display *dp)
{
   if (dp->fp != NULL)
   {
      FILE *fp = dp->fp;
      dp->fp = NULL;
      (void)fclose(fp);
   }

   if (dp->write_pp != NULL)
      png_destroy_write_struct(&dp->write_pp, dp->tsp > 0 ? NULL : &dp->ip);
}

static void
display_clean(struct display *dp)
{
   display_clean_read(dp);
   display_clean_write(dp);
   dp->output_file = NULL;

#  if PNG_LIBPNG_VER < 10700 && defined PNG_TEXT_SUPPORTED
      /* This is actually created and used by the write code, but only
       * once; it has to be retained for subsequent writes of the same file.
       */
      if (dp->text_stashed)
      {
         dp->text_stashed = 0;
         dp->num_text = 0;
         free(dp->text_ptr);
         dp->text_ptr = NULL;
      }
#  endif /* pre 1.7 */

   /* leave the filename for error detection */
   dp->results = 0; /* reset for next time */
}

static void
display_destroy(struct display *dp)
{
   /* Release any memory held in the display. */
   display_clean(dp);
}

static struct display *
get_dp(png_structp pp)
   /* The display pointer is always stored in the png_struct error pointer */
{
   struct display *dp = (struct display*)png_get_error_ptr(pp);

   if (dp == NULL)
   {
      fprintf(stderr, "pngcp: internal error (no display)\n");
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
         case APP_WARNING:    lp = "warning(pngcp)"; break;
         case APP_FAIL:       lp = "error(continuable)"; break;
         case LIBPNG_ERROR:   lp = "error(libpng)"; break;
         case LIBPNG_BUG:     lp = "bug(libpng)"; break;
         case APP_ERROR:      lp = "error(pngcp)"; break;
         case USER_ERROR:     lp = "error(user)"; break;

         case INTERNAL_ERROR: /* anything unexpected is an internal error: */
         case VERBOSE: case WARNINGS: case ERRORS: case QUIET:
         default:             lp = "bug(pngcp)"; break;
      }

      fprintf(stderr, "%s: %s: %s",
         dp->filename != NULL ? dp->filename : "<stdin>", lp, dp->operation);

      fprintf(stderr, ": ");

      va_start(ap, fmt);
      vfprintf(stderr, fmt, ap);
      va_end(ap);

      fputc('\n', stderr);
   }
   /* else do not output any message */

   /* Errors cause this routine to exit to the fail code */
   if (level > APP_FAIL || (level > ERRORS && !(dp->options & CONTINUE)))
   {
      if (dp->errset)
         longjmp(dp->error_return, level);

      else
         exit(99);
   }
}

#if PNG_LIBPNG_VER < 10700 && defined PNG_TEXT_SUPPORTED
static void
text_stash(struct display *dp)
{
   /* libpng 1.6 and earlier fixed a bug whereby text chunks were written
    * multiple times by png_write_png; the issue was that png_write_png passed
    * the same png_info to both png_write_info and png_write_end.  Rather than
    * fixing it by recording the information in the png_struct, or by recording
    * where to write the chunks, the fix made was to change the 'compression'
    * field of the chunk to invalid values, rendering the png_info somewhat
    * useless.
    *
    * The only fix for this given that we use the png_info more than once is to
    * make a copy of the text chunks and png_set_text it each time.  This adds a
    * text chunks, so they get replicated, but only the new set gets written
    * each time.  This uses memory like crazy but there is no way to delete the
    * useless chunks from the png_info.
    *
    * To make this slightly more efficient only the top level structure is
    * copied; since the old strings are actually preserved (in 1.6 and earlier)
    * this happens to work.
    */
   png_textp chunks = NULL;

   dp->num_text = png_get_text(dp->write_pp, dp->ip, &chunks, NULL);

   if (dp->num_text > 0)
   {
      dp->text_ptr = voidcast(png_textp, malloc(dp->num_text * sizeof *chunks));

      if (dp->text_ptr == NULL)
         display_log(dp, APP_ERROR, "text chunks: stash malloc failed");

      else
         memcpy(dp->text_ptr, chunks, dp->num_text * sizeof *chunks);
   }

   dp->text_stashed = 1; /* regardless of whether there are chunks or not */
}

#define text_stash(dp) if (!dp->text_stashed) text_stash(dp)

static void
text_restore(struct display *dp)
{
   /* libpng makes a copy, so this is fine: */
   if (dp->text_ptr != NULL)
      png_set_text(dp->write_pp, dp->ip, dp->text_ptr, dp->num_text);
}

#define text_restore(dp) if (dp->text_stashed) text_restore(dp)

#else
#define text_stash(dp) ((void)0)
#define text_restore(dp) ((void)0)
#endif /* pre 1.7 */

/* OPTIONS:
 *
 * The command handles options of the forms:
 *
 *    --option
 *       Turn an option on (Option)
 *    --no-option
 *       Turn an option off (Option)
 *    --option=value
 *       Set an option to a value (Value)
 *    --option=val1,val2,val3
 *       Set an option to a bitmask constructed from the values (List)
 */
static png_byte
option_index(struct display *dp, const char *opt, size_t len)
   /* Return the index (in options[]) of the given option, outputs an error if
    * it does not exist.  Takes the name of the option and a length (number of
    * characters in the name).
    */
{
   png_byte j;

   for (j=0; j<option_count; ++j)
      if (strncmp(options[j].name, opt, len) == 0 && options[j].name[len] == 0)
         return j;

   /* If the setjmp buffer is set the code is asking for an option index; this
    * is bad.  Otherwise this is the command line option parsing.
    */
   display_log(dp, dp->errset ? INTERNAL_ERROR : USER_ERROR,
         "%.*s: unknown option", (int)/*SAFE*/len, opt);
   abort(); /* NOT REACHED */
}

/* This works for an option name (no quotes): */
#define OPTIND(dp, name) option_index(dp, #name, (sizeof #name)-1)

static int
get_option(struct display *dp, const char *opt, int *value)
{
   png_byte i = option_index(dp, opt, strlen(opt));

   if (dp->entry[i]) /* option was set on command line */
   {
      *value = dp->value[i];
      return 1;
   }

   else
      return 0;
}

static int
set_opt_string_(struct display *dp, unsigned int sp, png_byte opt,
      const char *entry_name)
   /* Add the appropriate option string to dp->curr. */
{
   int offset, add;

   if (sp > 0)
      offset = dp->stack[sp-1].opt_string_end;

   else
      offset = dp->opt_string_start;

   if (entry_name == range_lo)
      add = sprintf(dp->curr+offset, " --%s=%d", options[opt].name,
            dp->value[opt]);

   else
      add = sprintf(dp->curr+offset, " --%s=%s", options[opt].name, entry_name);

   if (add < 0)
      display_log(dp, INTERNAL_ERROR, "sprintf failed");

   assert(offset+add < (int)/*SAFE*/sizeof dp->curr);
   return offset+add;
}

static void
set_opt_string(struct display *dp, unsigned int sp)
   /* Add the appropriate option string to dp->curr. */
{
   dp->stack[sp].opt_string_end = set_opt_string_(dp, sp, dp->stack[sp].opt, 
      options[dp->stack[sp].opt].values[dp->stack[sp].entry].name);
}

static void
record_opt(struct display *dp, png_byte opt, const char *entry_name)
   /* Record this option in dp->curr; called for an option not being searched,
    * the caller passes in the name of the value, or range_lo to use the
    * numerical value.
    */
{
   unsigned int sp = dp->csp; /* stack entry of next searched option */

   if (sp >= dp->tsp)
   {
      /* At top of stack; add the opt string for this entry to the previous
       * searched entry or the start of the dp->curr buffer if there is nothing
       * on the stack yet (sp == 0).
       */
      int offset = set_opt_string_(dp, sp, opt, entry_name);

      if (sp > 0)
         dp->stack[sp-1].opt_string_end = offset;

      else
         dp->opt_string_start = offset;
   }

   /* else do nothing: option already recorded */
}

static int
opt_list_end(struct display *dp, png_byte opt, png_byte entry)
{
   if (options[opt].values[entry].name == range_lo)
      return entry+1U >= options[opt].value_count /* missing range_hi */ ||
         options[opt].values[entry+1U].name != range_hi /* likewise */ ||
         options[opt].values[entry+1U].value <= dp->value[opt] /* range end */;

   else
      return entry+1U >= options[opt].value_count /* missing 'all' */ ||
         options[opt].values[entry+1U].name == all /* last entry */;
}

static void
push_opt(struct display *dp, unsigned int sp, png_byte opt, int search)
   /* Push a new option onto the stack, initializing the new stack entry
    * appropriately; this does all the work of next_opt (setting end/nsp) for
    * the first entry in the list.
    */
{
   png_byte entry;
   const char *entry_name;

   assert(sp == dp->tsp && sp < SL);

   /* The starting entry is entry 0 unless there is a range in which case it is
    * the entry corresponding to range_lo:
    */
   entry = options[opt].value_count;
   assert(entry > 0U);

   do
   {
      entry_name = options[opt].values[--entry].name;
      if (entry_name == range_lo)
         break;
   }
   while (entry > 0U);

   dp->tsp = sp+1U;
   dp->stack[sp].best_size =
      dp->stack[sp].lo_size =
      dp->stack[sp].hi_size = MAX_SIZE;

   if (search && entry_name == range_lo) /* search this range */
   {
      dp->stack[sp].lo = options[opt].values[entry].value;
      /* check for a mal-formed RANGE above: */
      assert(entry+1 < options[opt].value_count &&
             options[opt].values[entry+1].name == range_hi);
      dp->stack[sp].hi = options[opt].values[entry+1].value;
   }

   else
   {
      /* next_opt will just iterate over the range. */
      dp->stack[sp].lo = INT_MAX;
      dp->stack[sp].hi = INT_MIN; /* Prevent range chop */
   }

   dp->stack[sp].opt = opt;
   dp->stack[sp].entry = entry;
   dp->stack[sp].best_val = dp->value[opt] = options[opt].values[entry].value;

   set_opt_string(dp, sp);

   /* This works for the search case too; if the range has only one entry 'end'
    * will be marked here.
    */
   if (opt_list_end(dp, opt, entry))
   {
      dp->stack[sp].end = 1;
      /* Skip the warning if pngcp did this itself.  See the code in
       * set_windowBits_hi.
       */
      if (opt != dp->min_windowBits)
         display_log(dp, APP_WARNING, "%s: only testing one value",
               options[opt].name);
   }

   else
   {
      dp->stack[sp].end = 0;
      dp->nsp = dp->tsp;
   }

   /* Do a lazy cache of the text chunks for libpng 1.6 and earlier; this is
    * because they can only be written once(!) so if we are going to re-use the
    * png_info we need a copy.
    */
   text_stash(dp);
}

static void
next_opt(struct display *dp, unsigned int sp)
   /* Return the next value for this option.  When called 'sp' is expected to be
    * the topmost stack entry - only the topmost entry changes each time round -
    * and there must be a valid entry to return.  next_opt will set dp->nsp to
    * sp+1 if more entries are available, otherwise it will not change it and
    * set dp->stack[s].end to true.
    */
{
   int search = 0;
   png_byte entry, opt;
   const char *entry_name;

   /* dp->stack[sp] must be the top stack entry and it must be active: */
   assert(sp+1U == dp->tsp && !dp->stack[sp].end);

   opt = dp->stack[sp].opt;
   entry = dp->stack[sp].entry;
   assert(entry+1U < options[opt].value_count);
   entry_name = options[opt].values[entry].name;
   assert(entry_name != NULL);

   /* For ranges increment the value but don't change the entry, for all other
    * cases move to the next entry and load its value:
    */
   if (entry_name == range_lo) /* a range */
   {
      /* A range can be iterated over or searched.  The default iteration option
       * is indicated by hi < lo on the stack, otherwise the range being search
       * is [lo..hi] (inclusive).
       */
      if (dp->stack[sp].lo > dp->stack[sp].hi)
         dp->value[opt]++;

      else
      {
         /* This is the best size found for this option value: */
         png_alloc_size_t best_size = dp->stack[sp].best_size;
         int lo = dp->stack[sp].lo;
         int hi = dp->stack[sp].hi;
         int val = dp->value[opt];

         search = 1; /* end is determined here */
         assert(best_size < MAX_SIZE);

         if (val == lo)
         {
            /* Finding the best for the low end of the range: */
            dp->stack[sp].lo_size = best_size;
            assert(hi > val);

            if (hi == val+1) /* only 2 entries */
               dp->stack[sp].end = 1;

            val = hi;
         }

         else if (val == hi)
         {
            dp->stack[sp].hi_size = best_size;
            assert(val > lo+1); /* else 'end' set above */

            if (val == lo+2) /* only three entries to test */
               dp->stack[sp].end = 1;

            val = (lo + val)/2;
         }

         else
         {
            png_alloc_size_t lo_size = dp->stack[sp].lo_size;
            png_alloc_size_t hi_size = dp->stack[sp].hi_size;

            /* lo and hi should have been tested. */
            assert(lo_size < MAX_SIZE && hi_size < MAX_SIZE);

            /* These cases arise with the 'probe' handling below when there is a
             * dip or peak in the size curve.
             */
            if (val < lo) /* probing a new lo */
            {
               /* Swap lo and val: */
               dp->stack[sp].lo = val;
               dp->stack[sp].lo_size = best_size;
               val = lo;
               best_size = lo_size;
               lo = dp->stack[sp].lo;
               lo_size = dp->stack[sp].lo_size;
            }

            else if (val > hi) /* probing a new hi */
            {
               /* Swap hi and val: */
               dp->stack[sp].hi = val;
               dp->stack[sp].hi_size = best_size;
               val = hi;
               best_size = hi_size;
               hi = dp->stack[sp].hi;
               hi_size = dp->stack[sp].hi_size;
            }

            /* The following should be true or something got messed up above. */
            assert(lo < val && val < hi);

            /* If there are only four entries (lo, val, hi plus one more) just
             * test the remaining entry.
             */
            if (hi == lo+3)
            {
               /* Because of the 'probe' code val can either be lo+1 or hi-1; we
                * need to test the other.
                */
               val = lo + ((val == lo+1) ? 2 : 1);
               assert(lo < val && val < hi);
               dp->stack[sp].end = 1;
            }

            else
            {
               /* There are at least 2 entries still untested between lo and hi,
                * i.e. hi >= lo+4.  'val' is the midpoint +/- 0.5
                *
                * Separate out the four easy cases when lo..val..hi are
                * monotonically decreased or (more weird) increasing:
                */
               assert(hi > lo+3);

               if (lo_size <= best_size && best_size <= hi_size)
               {
                  /* Select the low range; testing this first favours the low
                   * range over the high range when everything comes out equal.
                   * Because of the probing 'val' may be lo+1.  In that case end
                   * the search and set 'val' to lo+2.
                   */
                  if (val == lo+1)
                  {
                     ++val;
                     dp->stack[sp].end = 1;
                  }

                  else
                  {
                     dp->stack[sp].hi = hi = val;
                     dp->stack[sp].hi_size = best_size;
                     val = (lo + val) / 2;
                  }
               }

               else if (lo_size >= best_size && best_size >= hi_size)
               {
                  /* Monotonically decreasing size; this is the expected case.
                   * Select the high end of the range.  As above, val may be
                   * hi-1.
                   */
                  if (val == hi-1)
                  {
                     --val;
                     dp->stack[sp].end = 1;
                  }

                  else
                  {
                     dp->stack[sp].lo = lo = val;
                     dp->stack[sp].lo_size = best_size;
                     val = (val + hi) / 2;
                  }
               }

               /* If both those tests failed 'best_size' is either greater than
                * or less than both lo_size and hi_size.  There is a peak or dip
                * in the curve of sizes from lo to hi and val is on the peak or
                * dip.
                *
                * Because the ranges being searched as so small (level is 1..9,
                * windowBits 8..15, memLevel 1..9) there will only be at most
                * three untested values between lo..val and val..hi, so solve
                * the problem by probing down from hi or up from lo, whichever
                * is the higher.
                *
                * This is the place where 'val' is set to outside the range
                * lo..hi, described as 'probing', though maybe 'narrowing' would
                * be more accurate.
                */
               else if (lo_size <= hi_size) /* down from hi */
               {
                  dp->stack[sp].hi = val;
                  dp->stack[sp].hi_size = best_size;
                  val = --hi;
               }

               else /* up from low */
               {
                  dp->stack[sp].lo = val;
                  dp->stack[sp].lo_size = best_size;
                  val = ++lo;
               }

               /* lo and hi are still the true range limits, check for the end
                * condition.
                */
               assert(hi > lo+1);
               if (hi <= lo+2)
                  dp->stack[sp].end = 1;
            }
         }

         assert(val != dp->stack[sp].best_val); /* should be a new value */
         dp->value[opt] = val;
         dp->stack[sp].best_size = MAX_SIZE;
      }
   }

   else
   {
      /* Increment 'entry' */
      dp->value[opt] = options[opt].values[++entry].value;
      dp->stack[sp].entry = entry;
   }

   set_opt_string(dp, sp);

   if (!search && opt_list_end(dp, opt, entry)) /* end of list */
      dp->stack[sp].end = 1;

   else if (!dp->stack[sp].end) /* still active after all these tests */
      dp->nsp = dp->tsp;
}

static int
compare_option(const struct display *dp, unsigned int sp)
{
   int opt = dp->stack[sp].opt;

   /* If the best so far is numerically less than the current value the
    * current set of options is invariably worse.
    */
   if (dp->stack[sp].best_val < dp->value[opt])
      return -1;

   /* Lists of options are searched out of numerical order (currently only
    * strategy), so only return +1 here when a range is being searched.
    */
   else if (dp->stack[sp].best_val > dp->value[opt])
   {
      if (dp->stack[sp].lo <= dp->stack[sp].hi /*searching*/)
         return 1;

      else
         return -1;
   }

   else
      return 0; /* match; current value is the best one */
}

static int
advance_opt(struct display *dp, png_byte opt, int search)
{
   unsigned int sp = dp->csp++; /* my stack entry */

   assert(sp >= dp->nsp); /* nsp starts off zero */

   /* If the entry was active in the previous run dp->stack[sp] is already
    * set up and dp->tsp will be greater than sp, otherwise a new entry
    * needs to be created.
    *
    * dp->nsp is handled this way:
    *
    * 1) When an option is pushed onto the stack dp->nsp and dp->tsp are
    *    both set (by push_opt) to the next stack entry *unless* there is
    *    only one entry in the new list, in which case dp->stack[sp].end
    *    is set.
    *
    * 2) For the top stack entry next_opt is called.  The entry must be
    *    active (dp->stack[sp].end is not set) and either 'nsp' or 'end'
    *    will be updated as appropriate.
    *
    * 3) For lower stack entries nsp is set unless the stack entry is
    *    already at the end.  This means that when all the higher entries
    *    are popped this entry will be too.
    */
   if (sp >= dp->tsp)
   {
      push_opt(dp, sp, opt, search); /* This sets tsp to sp+1 */
      return 1; /* initialized */
   }

   else
   {
      int ret = 0; /* unchanged */

      /* An option that is already on the stack; update best_size and best_val
       * if appropriate.  On the first run there are no previous values and
       * dp->write_size will be MAX_SIZE, however on the first run dp->tsp
       * starts off as 0.
       */
      assert(dp->write_size > 0U && dp->write_size < MAX_SIZE);

      if (dp->stack[sp].best_size > dp->write_size ||
          (dp->stack[sp].best_size == dp->write_size &&
           compare_option(dp, sp) > 0))
      {
         dp->stack[sp].best_size = dp->write_size;
         dp->stack[sp].best_val = dp->value[opt];
      }

      if (sp+1U >= dp->tsp)
      {
         next_opt(dp, sp);
         ret = 1; /* advanced */
      }

      else if (!dp->stack[sp].end) /* Active, not at top of stack */
         dp->nsp = sp+1U;

      return ret; /* advanced || unchanged */
   }
}

static int
getallopts_(struct display *dp, png_byte opt, int *value, int record)
   /* Like getop but iterate over all the values if the option was set to "all".
    */
{
   if (dp->entry[opt]) /* option was set on command line */
   {
      /* Simple, single value, entries don't have a stack frame and have a fixed
       * value (it doesn't change once set on the command line).  Otherwise the
       * value (entry) selected from the command line is 'all':
       */
      const char *entry_name = options[opt].values[dp->entry[opt]-1].name;

      if (entry_name == all)
         (void)advance_opt(dp, opt, 0/*do not search; iterate*/);

      else if (record)
         record_opt(dp, opt, entry_name);

      *value = dp->value[opt];
      return 1; /* set */
   }

   else
      return 0; /* not set */
}

static int
getallopts(struct display *dp, const char *opt_str, int *value)
{
   return getallopts_(dp, option_index(dp, opt_str, strlen(opt_str)), value, 0);
}

static int
getsearchopts(struct display *dp, const char *opt_str, int *value)
   /* As above except that if the option was not set try a search */
{
   png_byte istrat;
   png_byte opt = option_index(dp, opt_str, strlen(opt_str));
   int record = options[opt].search;
   const char *entry_name;

   /* If it was set on the command line honour the setting, including 'all'
    * which will override the built in search:
    */
   if (getallopts_(dp, opt, value, record))
      return 1;

   else if (!record) /* not a search option */
      return 0; /* unset and not searched */

   /* Otherwise decide what to do here. */
   istrat = OPTIND(dp, strategy);
   entry_name = range_lo; /* record the value, not the name */

   if (opt == istrat) /* search all strategies */
      (void)advance_opt(dp, opt, 0/*iterate*/), record=0;

   else if (opt == OPTIND(dp, level))
   {
      /* Both RLE and HUFFMAN don't benefit from level increases */
      if (dp->value[istrat] == Z_RLE || dp->value[istrat] == Z_HUFFMAN_ONLY)
         dp->value[opt] = 1;

      else /* fixed, filtered or default */
         (void)advance_opt(dp, opt, 1/*search*/), record=0;
   }

   else if (opt == OPTIND(dp, windowBits))
   {
      /* Changing windowBits for strategies that do not search the window is
       * pointless.  Huffman-only does not search, RLE only searches backwards
       * one byte, so given that the maximum string length is 258, a windowBits
       * of 9 is always sufficient.
       */
      if (dp->value[istrat] == Z_HUFFMAN_ONLY)
         dp->value[opt] = 8;

      else if (dp->value[istrat] == Z_RLE)
         dp->value[opt] = 9;

      else /* fixed, filtered or default */
         (void)advance_opt(dp, opt, 1/*search*/), record=0;
   }

   else if (opt == OPTIND(dp, memLevel))
   {
#     if 0
         (void)advance_opt(dp, opt, 0/*all*/), record=0;
#     else
         dp->value[opt] = MAX_MEM_LEVEL;
#     endif
   }

   else /* something else */
      assert(0=="reached");

   if (record)
      record_opt(dp, opt, entry_name);

   /* One of the above searched options: */
   *value = dp->value[opt];
   return 1;
}

static int
find_val(struct display *dp, png_byte opt, const char *str, size_t len)
   /* Like option_index but sets (index+i) of the entry in options[opt] that
    * matches str[0..len-1] into dp->entry[opt] as well as returning the actual
    * value.
    */
{
   int rlo = INT_MAX, rhi = INT_MIN;
   png_byte j, irange = 0;

   for (j=1U; j<=options[opt].value_count; ++j)
   {
      if (strncmp(options[opt].values[j-1U].name, str, len) == 0 &&
          options[opt].values[j-1U].name[len] == 0)
      {
         dp->entry[opt] = j;
         return options[opt].values[j-1U].value;
      }
      else if (options[opt].values[j-1U].name == range_lo)
         rlo = options[opt].values[j-1U].value, irange = j;
      else if (options[opt].values[j-1U].name == range_hi)
         rhi = options[opt].values[j-1U].value;
   }

   /* No match on the name, but there may be a range. */
   if (irange > 0)
   {
      char *ep = NULL;
      long l = strtol(str, &ep, 0);

      if (ep == str+len && l >= rlo && l <= rhi)
      {
         dp->entry[opt] = irange; /* range_lo */
         return (int)/*SAFE*/l;
      }
   }

   display_log(dp, dp->errset ? INTERNAL_ERROR : USER_ERROR,
         "%s: unknown value setting '%.*s'", options[opt].name,
         (int)/*SAFE*/len, str);
   abort(); /* NOT REACHED */
}

static int
opt_check(struct display *dp, const char *arg)
{
   assert(dp->errset == 0);

   if (arg != NULL && arg[0] == '-' && arg[1] == '-')
   {
      int i = 0, negate = (strncmp(arg+2, "no-", 3) == 0), val;
      png_byte j;

      if (negate)
         arg += 5; /* --no- */

      else
         arg += 2; /* -- */

      /* Find the length (expect arg\0 or arg=) */
      while (arg[i] != 0 && arg[i] != '=') ++i;

      /* So arg[0..i-1] is the argument name, this does not return if this isn't
       * a valid option name.
       */
      j = option_index(dp, arg, i);

      /* It matcheth an option; check the remainder. */
      if (arg[i] == 0) /* no specified value, use the default */
      {
         val = options[j].values[negate].value;
         dp->entry[j] = (png_byte)/*SAFE*/(negate + 1U);
      }

      else
      {
         const char *list = arg + (i+1);

         /* Expect a single value here unless this is a list, in which case
          * multiple values are combined.
          */
         if (options[j].opt != LIST)
         {
            /* find_val sets 'dp->entry[j]' to a non-zero value: */
            val = find_val(dp, j, list, strlen(list));

            if (negate)
            {
               if (options[j].opt < OPTION)
                  val = !val;

               else
               {
                  display_log(dp, USER_ERROR,
                        "%.*s: option=arg cannot be negated", i, arg);
                  abort(); /* NOT REACHED */
               }
            }
         }

         else /* multiple options separated by ',' characters */
         {
            /* --no-option negates list values from the default, which should
             * therefore be 'all'.  Notice that if the option list is empty in
             * this case nothing will be removed and therefore --no-option= is
             * the same as --option.
             */
            if (negate)
               val = options[j].values[0].value;

            else
               val = 0;

            while (*list != 0) /* allows option= which sets 0 */
            {
               /* A value is terminated by the end of the list or a ','
                * character.
                */
               int v, iv;

               iv = 0; /* an index into 'list' */
               while (list[++iv] != 0 && list[iv] != ',') {}

               v = find_val(dp, j, list, iv);

               if (negate)
                  val &= ~v;

               else
                  val |= v;

               list += iv;
               if (*list != 0)
                  ++list; /* skip the ',' */
            }
         }
      }

      /* 'val' is the new value, store it for use later and debugging: */
      dp->value[j] = val;

      if (options[j].opt < LEVEL_MASK)
      {
         /* The handling for error levels is to set the level. */
         if (val) /* Set this level */
            dp->options = (dp->options & ~LEVEL_MASK) | options[j].opt;

         else
            display_log(dp, USER_ERROR,
      "%.*s: messages cannot be turned off individually; set a message level",
                  i, arg);
      }

      else if (options[j].opt < OPTION)
      {
         if (val)
            dp->options |= options[j].opt;

         else
            dp->options &= ~options[j].opt;
      }

      return 1; /* this is an option */
   }

   else
      return 0; /* not an option */
}

#ifdef PNG_PNGCP_TIMING_SUPPORTED
static void
set_timer(struct display *dp, struct timespec *timer)
{
   /* Do the timing using clock_gettime and the per-process timer. */
   if (clock_gettime(CLOCK_PROCESS_CPUTIME_ID, timer))
   {
      display_log(dp, APP_ERROR,
            "CLOCK_PROCESS_CPUTIME_ID: %s: timing disabled\n", strerror(errno));
      dp->value[OPTIND(dp,time)] = 0; /* i.e. off */
   }
}

static void
start_timer(struct display *dp, int what)
{
   if ((dp->value[OPTIND(dp,time)] & what) != 0)
      set_timer(dp, what == PNGCP_TIME_READ ? &dp->read_time : &dp->write_time);
}

static void
end_timer(struct display *dp, int what)
{
   if ((dp->value[OPTIND(dp,time)] & what) != 0)
   {
      struct timespec t, tmp;

      set_timer(dp, &t);

      if (what == PNGCP_TIME_READ)
         tmp = dp->read_time;

      else
         tmp = dp->write_time;

      t.tv_sec -= tmp.tv_sec;
      t.tv_nsec -= tmp.tv_nsec;

      if (t.tv_nsec < 0)
      {
         --(t.tv_sec);
         t.tv_nsec += 1000000000L;
      }

      if (what == PNGCP_TIME_READ)
         dp->read_time = t, tmp = dp->read_time_total;

      else
         dp->write_time = t, tmp = dp->write_time_total;

      tmp.tv_sec += t.tv_sec;
      tmp.tv_nsec += t.tv_nsec;

      if (tmp.tv_nsec >= 1000000000L)
      {
         ++(tmp.tv_sec);
         tmp.tv_nsec -= 1000000000L;
      }

      if (what == PNGCP_TIME_READ)
         dp->read_time_total = tmp;

      else
         dp->write_time_total = tmp;
   }
}

static void
print_time(const char *what, struct timespec t)
{
   printf("%s %.2lu.%.9ld", what, (unsigned long)t.tv_sec, t.tv_nsec);
}
#else /* !PNGCP_TIMING */
#define start_timer(dp, what) ((void)0)
#define end_timer(dp, what) ((void)0)
#endif /* !PNGCP_TIMING */

/* The following is used in main to verify that the final argument is a
 * directory:
 */
static int
checkdir(const char *pathname)
{
   struct stat buf;
   return stat(pathname, &buf) == 0 && S_ISDIR(buf.st_mode);
}

/* Work out whether a path is valid (if not a display_log occurs), a directory
 * (1 is returned) or a file *or* non-existent (0 is returned).
 *
 * Used for a write path.
 */
static int
isdir(struct display *dp, const char *pathname)
{
   if (pathname == NULL)
      return 0; /* stdout */

   else if (pathname[0] == 0)
      return 1; /* empty string */

   else
   {
      struct stat buf;
      int ret = stat(pathname, &buf);

      if (ret == 0) /* the entry exists */
      {
         if (S_ISDIR(buf.st_mode))
            return 1;

         /* Else expect an object that exists and can be written: */
         if (access(pathname, W_OK) != 0)
            display_log(dp, USER_ERROR, "%s: cannot be written (%s)", pathname,
                  strerror(errno));

         return 0; /* file (exists, can be written) */
      }

      else /* an error */
      {
         /* Non-existence is fine, other errors are not: */
         if (errno != ENOENT)
            display_log(dp, USER_ERROR, "%s: invalid output name (%s)",
                  pathname, strerror(errno));

         return 0; /* file (does not exist) */
      }
   }
}

static void
makename(struct display *dp, const char *dir, const char *infile)
{
   /* Make a name for an output file (and check it). */
   dp->namebuf[0] = 0;

   if (dir == NULL || infile == NULL)
      display_log(dp, INTERNAL_ERROR, "NULL name to makename");

   else
   {
      size_t dsize = strlen(dir);

      if (dsize <= (sizeof dp->namebuf)-2) /* Allow for name + '/' + '\0' */
      {
         size_t isize = strlen(infile);
         size_t istart = isize-1;

         /* This should fail before here: */
         if (infile[istart] == '/')
            display_log(dp, INTERNAL_ERROR, "infile with trailing /");

         memcpy(dp->namebuf, dir, dsize);
         if (dsize > 0 && dp->namebuf[dsize-1] != '/')
            dp->namebuf[dsize++] = '/';

         /* Find the rightmost non-/ character: */
         while (istart > 0 && infile[istart-1] != '/')
            --istart;

         isize -= istart;
         infile += istart;

         if (dsize+isize < (sizeof dp->namebuf)) /* dsize + infile + '\0' */
         {
            memcpy(dp->namebuf+dsize, infile, isize+1);

            if (isdir(dp, dp->namebuf))
               display_log(dp, USER_ERROR, "%s: output file is a directory",
                     dp->namebuf);
         }

         else
         {
            dp->namebuf[dsize] = 0; /* allowed for: -2 at start */
            display_log(dp, USER_ERROR, "%s%s: output file name too long",
                  dp->namebuf, infile);
         }
      }

      else
         display_log(dp, USER_ERROR, "%s: output directory name too long", dir);
   }
}

/* error handler callbacks for libpng */
static void PNGCBAPI
display_warning(png_structp pp, png_const_charp warning)
{
   struct display *dp = get_dp(pp);

   /* This is used to prevent repeated warnings while searching */
   if (!dp->no_warnings)
      display_log(get_dp(pp), LIBPNG_WARNING, "%s", warning);
}

static void PNGCBAPI
display_error(png_structp pp, png_const_charp error)
{
   struct display *dp = get_dp(pp);

   display_log(dp, LIBPNG_ERROR, "%s", error);
}

static void
display_start_read(struct display *dp, const char *filename)
{
   if (filename != NULL)
   {
      dp->filename = filename;
      dp->fp = fopen(filename, "rb");
   }

   else
   {
      dp->filename = "<stdin>";
      dp->fp = stdin;
   }

   dp->w = dp->h = 0U;
   dp->bpp = 0U;
   dp->size = 0U;
   dp->read_size = 0U;

   if (dp->fp == NULL)
      display_log(dp, USER_ERROR, "file open failed (%s)", strerror(errno));
}

static void PNGCBAPI
read_function(png_structp pp, png_bytep data, size_t size)
{
   struct display *dp = get_dp(pp);

   if (size == 0U || fread(data, size, 1U, dp->fp) == 1U)
      dp->read_size += size;

   else
   {
      if (feof(dp->fp))
         display_log(dp, LIBPNG_ERROR, "PNG file truncated");
      else
         display_log(dp, LIBPNG_ERROR, "PNG file read failed (%s)",
               strerror(errno));
   }
}

static void
read_png(struct display *dp, const char *filename)
{
   display_clean_read(dp); /* safety */
   display_start_read(dp, filename);

   dp->read_pp = png_create_read_struct(PNG_LIBPNG_VER_STRING, dp,
      display_error, display_warning);
   if (dp->read_pp == NULL)
      display_log(dp, LIBPNG_ERROR, "failed to create read struct");

#  ifdef PNG_BENIGN_ERRORS_SUPPORTED
      png_set_benign_errors(dp->read_pp, 1/*allowed*/);
#  endif /* BENIGN_ERRORS */

#  ifdef FIX_INDEX
      if ((dp->options & FIX_INDEX) != 0)
         png_set_check_for_invalid_index(dp->read_pp, 1/*on, no warning*/);
#     ifdef IGNORE_INDEX
         else
#     endif /* IGNORE_INDEX */
#  endif /* FIX_INDEX */
#  ifdef IGNORE_INDEX
      if ((dp->options & IGNORE_INDEX) != 0) /* DANGEROUS */
         png_set_check_for_invalid_index(dp->read_pp, -1/*off completely*/);
#  endif /* IGNORE_INDEX */

   /* The png_read_png API requires us to make the info struct, but it does the
    * call to png_read_info.
    */
   dp->ip = png_create_info_struct(dp->read_pp);
   if (dp->ip == NULL)
      png_error(dp->read_pp, "failed to create info struct");

   /* Set the IO handling */
   png_set_read_fn(dp->read_pp, dp, read_function);

#  ifdef PNG_HANDLE_AS_UNKNOWN_SUPPORTED
      png_set_keep_unknown_chunks(dp->read_pp, PNG_HANDLE_CHUNK_ALWAYS, NULL,
            0);
#  endif /* HANDLE_AS_UNKNOWN */

#  ifdef PNG_SET_USER_LIMITS_SUPPORTED
      /* Remove the user limits, if any */
      png_set_user_limits(dp->read_pp, 0x7fffffff, 0x7fffffff);
#  endif /* SET_USER_LIMITS */

   /* Now read the PNG. */
   start_timer(dp, PNGCP_TIME_READ);
   png_read_png(dp->read_pp, dp->ip, 0U/*transforms*/, NULL/*params*/);
   end_timer(dp, PNGCP_TIME_READ);
   dp->w = png_get_image_width(dp->read_pp, dp->ip);
   dp->h = png_get_image_height(dp->read_pp, dp->ip);
   dp->ct = png_get_color_type(dp->read_pp, dp->ip);
   dp->bpp = png_get_bit_depth(dp->read_pp, dp->ip) *
             png_get_channels(dp->read_pp, dp->ip);
   {
      /* png_get_rowbytes should never return 0 because the value is set by the
       * first call to png_set_IHDR, which should have happened by now, but just
       * in case:
       */
      png_alloc_size_t rb = png_get_rowbytes(dp->read_pp, dp->ip);

      if (rb == 0)
         png_error(dp->read_pp, "invalid row byte count from libpng");

      /* The size calc can overflow. */
      if ((MAX_SIZE-dp->h)/rb < dp->h)
         png_error(dp->read_pp, "image too large");

      dp->size = rb * dp->h + dp->h/*filter byte*/;
   }

#ifdef FIX_INDEX
   if (dp->ct == PNG_COLOR_TYPE_PALETTE && (dp->options & FIX_INDEX) != 0)
   {
      int max = png_get_palette_max(dp->read_pp, dp->ip);
      png_colorp palette = NULL;
      int num = -1;

      if (png_get_PLTE(dp->read_pp, dp->ip, &palette, &num) != PNG_INFO_PLTE
          || max < 0 || num <= 0 || palette == NULL)
         display_log(dp, LIBPNG_ERROR, "invalid png_get_PLTE result");

      if (max >= num)
      {
         /* 'Fix' the palette. */
         int i;
         png_color newpal[256];

         for (i=0; i<num; ++i)
            newpal[i] = palette[i];

         /* Fill in any remainder with a warning color: */
         for (; i<=max; ++i)
         {
            newpal[i].red = 0xbe;
            newpal[i].green = 0xad;
            newpal[i].blue = 0xed;
         }

         png_set_PLTE(dp->read_pp, dp->ip, newpal, i);
      }
   }
#endif /* FIX_INDEX */

   display_clean_read(dp);
   dp->operation = "none";
}

static void
display_start_write(struct display *dp, const char *filename)
{
   assert(dp->fp == NULL);

   if ((dp->options & NOWRITE) != 0)
      dp->output_file = "<no write>";

   else
   {
      if (filename != NULL)
      {
         dp->output_file = filename;
         dp->fp = fopen(filename, "wb");
      }

      else
      {
         dp->output_file = "<stdout>";
         dp->fp = stdout;
      }

      if (dp->fp == NULL)
         display_log(dp, USER_ERROR, "%s: file open failed (%s)",
               dp->output_file, strerror(errno));
   }
}

static void PNGCBAPI
write_function(png_structp pp, png_bytep data, size_t size)
{
   struct display *dp = get_dp(pp);

   /* The write fail is classed as a USER_ERROR, so --quiet does not turn it
    * off, this seems more likely to be correct.
    */
   if (dp->fp == NULL || fwrite(data, size, 1U, dp->fp) == 1U)
   {
      dp->write_size += size;
      if (dp->write_size < size || dp->write_size == MAX_SIZE)
         png_error(pp, "IDAT size overflow");
   }

   else
      display_log(dp, USER_ERROR, "%s: PNG file write failed (%s)",
            dp->output_file, strerror(errno));
}

/* Compression option, 'method' is never set: there is no choice.
 *
 * IMPORTANT: the order of the entries in this macro determines the preference
 * order when two different combos of two of these options produce an IDAT of
 * the same size.  The logic here is to put the things that affect the decoding
 * of the PNG image ahead of those that are relevant only to the encoding.
 */
#define SET_COMPRESSION\
   SET(strategy, strategy);\
   SET(windowBits, window_bits);\
   SET(level, level);\
   SET(memLevel, mem_level);

#ifdef PNG_WRITE_CUSTOMIZE_COMPRESSION_SUPPORTED
static void
search_compression(struct display *dp)
{
   /* Like set_compression below but use a more restricted search than 'all' */
   int val;

#  define SET(name, func) if (getsearchopts(dp, #name, &val))\
      png_set_compression_ ## func(dp->write_pp, val);
   SET_COMPRESSION
#  undef SET
}

static void
set_compression(struct display *dp)
{
   int val;

#  define SET(name, func) if (getallopts(dp, #name, &val))\
      png_set_compression_ ## func(dp->write_pp, val);
   SET_COMPRESSION
#  undef SET
}

#ifdef PNG_SW_COMPRESS_level /* 1.7.0+ */
static void
set_ICC_profile_compression(struct display *dp)
{
   int val;

#  define SET(name, func) if (getallopts(dp, "ICC-profile-" #name, &val))\
      png_set_ICC_profile_compression_ ## func(dp->write_pp, val);
   SET_COMPRESSION
#  undef SET
}
#else
#  define set_ICC_profile_compression(dp) ((void)0)
#endif
#else
#  define search_compression(dp) ((void)0)
#  define set_compression(dp) ((void)0)
#  define set_ICC_profile_compression(dp) ((void)0)
#endif /* WRITE_CUSTOMIZE_COMPRESSION */

#ifdef PNG_WRITE_CUSTOMIZE_ZTXT_COMPRESSION_SUPPORTED
static void
set_text_compression(struct display *dp)
{
   int val;

#  define SET(name, func) if (getallopts(dp, "text-" #name, &val))\
      png_set_text_compression_ ## func(dp->write_pp, val);
   SET_COMPRESSION
#  undef SET
}
#else
#  define set_text_compression(dp) ((void)0)
#endif /* WRITE_CUSTOMIZE_ZTXT_COMPRESSION */

static void
write_png(struct display *dp, const char *destname)
{
   display_clean_write(dp); /* safety */
   display_start_write(dp, destname);

   dp->write_pp = png_create_write_struct(PNG_LIBPNG_VER_STRING, dp,
      display_error, display_warning);

   if (dp->write_pp == NULL)
      display_log(dp, LIBPNG_ERROR, "failed to create write png_struct");

#  ifdef PNG_BENIGN_ERRORS_SUPPORTED
      png_set_benign_errors(dp->write_pp, 1/*allowed*/);
#  endif /* BENIGN_ERRORS */

   png_set_write_fn(dp->write_pp, dp, write_function, NULL/*flush*/);

#ifdef IGNORE_INDEX
   if ((dp->options & IGNORE_INDEX) != 0) /* DANGEROUS */
      png_set_check_for_invalid_index(dp->write_pp, -1/*off completely*/);
#endif /* IGNORE_INDEX */

   /* Restore the text chunks when using libpng 1.6 or less; this is a macro
    * which expands to nothing in 1.7+  In earlier versions it tests
    * dp->text_stashed, which is only set (below) *after* the first write.
    */
   text_restore(dp);

#  ifdef PNG_HANDLE_AS_UNKNOWN_SUPPORTED
      png_set_keep_unknown_chunks(dp->write_pp, PNG_HANDLE_CHUNK_ALWAYS, NULL,
            0);
#  endif /* HANDLE_AS_UNKNOWN */

#  ifdef PNG_SET_USER_LIMITS_SUPPORTED
      /* Remove the user limits, if any */
      png_set_user_limits(dp->write_pp, 0x7fffffff, 0x7fffffff);
#  endif

   /* OPTION HANDLING */
   /* compression outputs, IDAT and zTXt/iTXt: */
   dp->tsp = dp->nsp;
   dp->nsp = dp->csp = 0;
#  ifdef PNG_SW_COMPRESS_png_level
      {
         int val;

         /* This sets everything, but then the following options just override
          * the specific settings for ICC profiles and text.
          */
         if (getallopts(dp, "compression", &val))
            png_set_compression(dp->write_pp, val);

         if (getallopts(dp, "ICC-profile-compression", &val))
            png_set_ICC_profile_compression(dp->write_pp, val);

         if (getallopts(dp, "text-compression", &val))
            png_set_text_compression(dp->write_pp, val);
      }
#  endif /* png_level support */
   if (dp->options & SEARCH)
      search_compression(dp);
   else
      set_compression(dp);
   set_ICC_profile_compression(dp);
   set_text_compression(dp);

   {
      int val;

      /* The permitted range is 1..0x7FFFFFFF, so the cast is safe */
      if (get_option(dp, "IDAT-size", &val))
         png_set_IDAT_size(dp->write_pp, val);
   }

   /* filter handling */
#  ifdef PNG_WRITE_FILTER_SUPPORTED
      {
         int val;

         if (get_option(dp, "filter", &val))
            png_set_filter(dp->write_pp, PNG_FILTER_TYPE_BASE, val);
      }
#  endif /* WRITE_FILTER */

   /* This just uses the 'read' info_struct directly, it contains the image. */
   dp->write_size = 0U;
   start_timer(dp, PNGCP_TIME_WRITE);
   png_write_png(dp->write_pp, dp->ip, 0U/*transforms*/, NULL/*params*/);
   end_timer(dp, PNGCP_TIME_WRITE);

   /* Make sure the file was written ok: */
   if (dp->fp != NULL)
   {
      FILE *fp = dp->fp;
      dp->fp = NULL;
      if (fclose(fp))
         display_log(dp, APP_ERROR, "%s: write failed (%s)",
               destname == NULL ? "stdout" : destname, strerror(errno));
   }

   /* Clean it on the way out - if control returns to the caller then the
    * written_file contains the required data.
    */
   display_clean_write(dp);
   dp->operation = "none";
}

static void
set_windowBits_hi(struct display *dp)
{
   /* windowBits is in the range 8..15 but zlib maps '8' to '9' so it is only
    * worth using if the data size is 256 byte or less.
    */
   int wb = MAX_WBITS; /* for large images */
   int i = VLSIZE(windowBits_IDAT);

   while (wb > 8 && dp->size <= 1U<<(wb-1)) --wb;

   while (--i >= 0) if (VLNAME(windowBits_IDAT)[i].name == range_hi) break;

   assert(i > 1); /* vl_windowBits_IDAT always has a RANGE() */
   VLNAME(windowBits_IDAT)[i].value = wb;

   assert(VLNAME(windowBits_IDAT)[--i].name == range_lo);
   VLNAME(windowBits_IDAT)[i].value = wb > 8 ? 9 : 8;

   /* If wb == 8 then any search has been restricted to just one windowBits
    * entry.  Record that here to avoid producing a spurious app-level warning
    * above.
    */
   if (wb == 8)
      dp->min_windowBits = OPTIND(dp, windowBits);
}

static int
better_options(const struct display *dp)
{
   /* Are these options better than the best found so far?  Normally the
    * options are tested in preference order, best first, however when doing a
    * search operation on a range the range values are tested out of order.  In
    * that case preferable options will get tested later.
    *
    * This function looks through the stack from the bottom up looking for an
    * option that does not match the current best value.  When it finds one it
    * checks to see if it is more or less desirable and returns true or false
    * as appropriate.
    *
    * Notice that this means that the order options are pushed onto the stack
    * conveys a priority; lower/earlier options are more important than later
    * ones.
    */
   unsigned int sp;

   for (sp=0; sp<dp->csp; ++sp)
   {
      int c = compare_option(dp, sp);

      if (c < 0)
         return 0; /* worse */

      else if (c > 0)
         return 1; /* better */
   }

   assert(0 && "unreached");
}

static void
print_search_results(struct display *dp)
{
   assert(dp->filename != NULL);
   printf("%s [%ld x %ld %d bpp %s, %lu bytes] %lu -> %lu with '%s'\n",
      dp->filename, (unsigned long)dp->w, (unsigned long)dp->h, dp->bpp,
      cts(dp->ct), (unsigned long)dp->size, (unsigned long)dp->read_size,
      (unsigned long)dp->best_size, dp->best);
   fflush(stdout);
}

static void
log_search(struct display *dp, unsigned int log_depth)
{
   /* Log, and reset, the search so far: */
   if (dp->nsp/*next entry to change*/ <= log_depth)
   {
      print_search_results(dp);
      /* Start again with this entry: */
      dp->best_size = MAX_SIZE;
   }
}

static void
cp_one_file(struct display *dp, const char *filename, const char *destname)
{
   unsigned int log_depth;

   dp->filename = filename;
   dp->operation = "read";
   dp->no_warnings = 0;

   /* Read it then write it: */
   if (filename != NULL && access(filename, R_OK) != 0)
      display_log(dp, USER_ERROR, "%s: invalid file name (%s)",
            filename, strerror(errno));

   read_png(dp, filename);

   /* But 'destname' may be a directory. */
   dp->operation = "write";

   /* Limit the upper end of the windowBits range for this file */
   set_windowBits_hi(dp);

   /* For logging, depth to log: */
   {
      int val;

      if (get_option(dp, "log-depth", &val) && val >= 0)
         log_depth = (unsigned int)/*SAFE*/val;

      else
         log_depth = 0U;
   }

   if (destname != NULL) /* else stdout */
   {
      if (isdir(dp, destname))
      {
         makename(dp, destname, filename);
         destname = dp->namebuf;
      }

      else if (access(destname, W_OK) != 0 && errno != ENOENT)
         display_log(dp, USER_ERROR, "%s: invalid output name (%s)", destname,
               strerror(errno));
   }

   dp->nsp = 0;
   dp->curr[0] = 0; /* acts as a flag for the caller */
   dp->opt_string_start = 0;
   dp->best[0] = 0; /* safety */
   dp->best_size = MAX_SIZE;
   write_png(dp, destname);

   /* Initialize the 'best' fields: */
   strcpy(dp->best, dp->curr);
   dp->best_size = dp->write_size;

   if (dp->nsp > 0) /* iterating over lists */
   {
      char *tmpname, tmpbuf[(sizeof dp->namebuf) + 4];
      assert(dp->curr[0] == ' ' && dp->tsp > 0);

      /* Cancel warnings on subsequent writes */
      log_search(dp, log_depth);
      dp->no_warnings = 1;

      /* Make a temporary name for the subsequent tests: */
      if (destname != NULL)
      {
         strcpy(tmpbuf, destname);
         strcat(tmpbuf, ".tmp"); /* space for .tmp allocated above */
         tmpname = tmpbuf;
      }

      else
         tmpname = NULL; /* stdout */

      /* Loop to find the best option. */
      do
      {
         write_png(dp, tmpname);

         /* And compare the sizes (the write function makes sure write_size
          * doesn't overflow.)
          */
         assert(dp->csp > 0);

         if (dp->write_size < dp->best_size ||
             (dp->write_size == dp->best_size && better_options(dp)))
         {
            if (destname != NULL && rename(tmpname, destname) != 0)
               display_log(dp, APP_ERROR, "rename %s %s failed (%s)", tmpname,
                     destname, strerror(errno));

            strcpy(dp->best, dp->curr);
            dp->best_size = dp->write_size;
         }

         else if (tmpname != NULL && unlink(tmpname) != 0)
            display_log(dp, APP_WARNING, "unlink %s failed (%s)", tmpname,
                  strerror(errno));

         log_search(dp, log_depth);
      }
      while (dp->nsp > 0);

      /* Do this for the 'sizes' option so that it reports the correct size. */
      dp->write_size = dp->best_size;
   }
}

static int
cppng(struct display *dp, const char *file, const char *gv dest)
   /* Exists solely to isolate the setjmp clobbers which some versions of GCC
    * erroneously generate.
    */
{
   int ret = setjmp(dp->error_return);

   if (ret == 0)
   {
      dp->errset = 1;
      cp_one_file(dp, file, dest);
      dp->errset = 0;
      return 0;
   }

   else
   {
      dp->errset = 0;

      if (ret < ERRORS) /* shouldn't longjmp on warnings */
         display_log(dp, INTERNAL_ERROR, "unexpected return code %d", ret);

      return ret;
   }
}

int
main(int argc, char **argv)
{
   /* For each file on the command line test it with a range of transforms */
   int option_end;
   struct display d;

   display_init(&d);

   d.operation = "options";
   for (option_end = 1;
        option_end < argc && opt_check(&d, argv[option_end]);
        ++option_end)
   {
   }

   /* Do a quick check on the directory target case; when there are more than
    * two arguments the last one must be a directory.
    */
   if (!(d.options & NOWRITE) && option_end+2 < argc && !checkdir(argv[argc-1]))
   {
      fprintf(stderr,
            "pngcp: %s: directory required with more than two arguments\n",
            argv[argc-1]);
      return 99;
   }

   {
      int errors = 0;
      int i = option_end;

      /* Do this at least once; if there are no arguments stdin/stdout are used.
       */
      d.operation = "files";
      do
      {
         const char *infile = NULL;
         const char *outfile = NULL;
         int ret;

         if (i < argc)
         {
            infile = argv[i++];
            if (!(d.options & NOWRITE) && i < argc)
               outfile = argv[argc-1];
         }

         ret = cppng(&d, infile, outfile);

         if (ret)
         {
            if (ret > QUIET) /* abort on user or internal error */
               return 99;

            /* An error: the output is meaningless */
         }

         else if (d.best[0] != 0)
         {
            /* This result may already have been output, in which case best_size
             * has been reset.
             */
            if (d.best_size < MAX_SIZE)
               print_search_results(&d);
         }

         else if (d.options & SIZES)
         {
            printf("%s [%ld x %ld %d bpp %s, %lu bytes] %lu -> %lu [0x%lx]\n",
                  infile, (unsigned long)d.w, (unsigned long)d.h, d.bpp,
                  cts(d.ct), (unsigned long)d.size, (unsigned long)d.read_size,
                  (unsigned long)d.write_size, (unsigned long)d.results);
            fflush(stdout);
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

               printf("%s: pngcp", pass ? "PASS" : "FAIL");

               for (j=1; j<option_end; ++j)
                  printf(" %s", argv[j]);

               if (infile != NULL)
                  printf(" %s", infile);

#              ifdef PNG_PNGCP_TIMING_SUPPORTED
                  /* When logging output the files for each file, if enabled. */
                  if ((d.value[OPTIND(&d,time)] & PNGCP_TIME_READ) != 0)
                     print_time(" read", d.read_time);

                  if ((d.value[OPTIND(&d,time)] & PNGCP_TIME_WRITE) != 0)
                     print_time(" write", d.write_time);
#              endif /* PNGCP_TIMING */

               printf("\n");
               fflush(stdout);
            }
         }

         display_clean(&d);
      }
      while (i+!(d.options & NOWRITE) < argc);
         /* I.e. for write cases after the first time through the loop require
          * there to be at least two arguments left and for the last one to be a
          * directory (this was checked above).
          */

      /* Release allocated memory */
      display_destroy(&d);

#     ifdef PNG_PNGCP_TIMING_SUPPORTED
         {
            int output = 0;

            if ((d.value[OPTIND(&d,time)] & PNGCP_TIME_READ) != 0)
               print_time("read", d.read_time_total), output = 1;

            if ((d.value[OPTIND(&d,time)] & PNGCP_TIME_WRITE) != 0)
            {
               if (output) putchar(' ');
               print_time("write", d.write_time_total);
               output = 1;
            }

            if (output) putchar('\n');
         }
#     endif /* PNGCP_TIMING */

      return errors != 0;
   }
}
#else /* !READ_PNG || !WRITE_PNG */
int
main(void)
{
   fprintf(stderr, "pngcp: no support for png_read/write_image\n");
   return 77;
}
#endif /* !READ_PNG || !WRITE_PNG */

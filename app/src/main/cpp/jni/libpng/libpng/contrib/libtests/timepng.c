/* timepng.c
 *
 * Copyright (c) 2013,2016 John Cunningham Bowler
 *
 * Last changed in libpng 1.6.22 [May 26, 2016]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Load an arbitrary number of PNG files (from the command line, or, if there
 * are no arguments on the command line, from stdin) then run a time test by
 * reading each file by row or by image (possibly with transforms in the latter
 * case).  The only output is a time as a floating point number of seconds with
 * 9 decimal digits.
 */
#define _POSIX_C_SOURCE 199309L /* for clock_gettime */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <limits.h>

#include <time.h>

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

/* The following is to support direct compilation of this file as C++ */
#ifdef __cplusplus
#  define voidcast(type, value) static_cast<type>(value)
#else
#  define voidcast(type, value) (value)
#endif /* __cplusplus */

/* 'CLOCK_PROCESS_CPUTIME_ID' is one of the clock timers for clock_gettime.  It
 * need not be supported even when clock_gettime is available.  It returns the
 * 'CPU' time the process has consumed.  'CPU' time is assumed to include time
 * when the CPU is actually blocked by a pending cache fill but not time
 * waiting for page faults.  The attempt is to get a measure of the actual time
 * the implementation takes to read a PNG ignoring the potentially very large IO
 * overhead.
 */
#if defined (CLOCK_PROCESS_CPUTIME_ID) && defined(PNG_STDIO_SUPPORTED) &&\
    defined(PNG_EASY_ACCESS_SUPPORTED) &&\
    (PNG_LIBPNG_VER >= 10700 ? defined(PNG_READ_PNG_SUPPORTED) :\
     defined (PNG_SEQUENTIAL_READ_SUPPORTED) &&\
     defined(PNG_INFO_IMAGE_SUPPORTED))

typedef struct
{
   FILE *input;
   FILE *output;
}  io_data;

static PNG_CALLBACK(void, read_and_copy,
      (png_structp png_ptr, png_bytep buffer, size_t cb))
{
   io_data *io = (io_data*)png_get_io_ptr(png_ptr);

   if (fread(buffer, cb, 1, io->input) != 1)
      png_error(png_ptr, strerror(errno));

   if (fwrite(buffer, cb, 1, io->output) != 1)
   {
      perror("temporary file");
      fprintf(stderr, "temporary file PNG write failed\n");
      exit(1);
   }
}

static void read_by_row(png_structp png_ptr, png_infop info_ptr,
      FILE *write_ptr, FILE *read_ptr)
{
   /* These don't get freed on error, this is fine; the program immediately
    * exits.
    */
   png_bytep row = NULL, display = NULL;
   io_data io_copy;

   if (write_ptr != NULL)
   {
      /* Set up for a copy to the temporary file: */
      io_copy.input = read_ptr;
      io_copy.output = write_ptr;
      png_set_read_fn(png_ptr, &io_copy, read_and_copy);
   }

   png_read_info(png_ptr, info_ptr);

   {
      size_t rowbytes = png_get_rowbytes(png_ptr, info_ptr);

      row = voidcast(png_bytep,malloc(rowbytes));
      display = voidcast(png_bytep,malloc(rowbytes));

      if (row == NULL || display == NULL)
         png_error(png_ptr, "OOM allocating row buffers");

      {
         png_uint_32 height = png_get_image_height(png_ptr, info_ptr);
         int passes = png_set_interlace_handling(png_ptr);
         int pass;

         png_start_read_image(png_ptr);

         for (pass = 0; pass < passes; ++pass)
         {
            png_uint_32 y = height;

            /* NOTE: this trashes the row each time; interlace handling won't
             * work, but this avoids memory thrashing for speed testing and is
             * somewhat representative of an application that works row-by-row.
             */
            while (y-- > 0)
               png_read_row(png_ptr, row, display);
         }
      }
   }

   /* Make sure to read to the end of the file: */
   png_read_end(png_ptr, info_ptr);

   /* Free this up: */
   free(row);
   free(display);
}

static PNG_CALLBACK(void, no_warnings, (png_structp png_ptr,
         png_const_charp warning))
{
   (void)png_ptr;
   (void)warning;
}

static int read_png(FILE *fp, png_int_32 transforms, FILE *write_file)
{
   png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING,0,0,
         no_warnings);
   png_infop info_ptr = NULL;

   if (png_ptr == NULL)
      return 0;

   if (setjmp(png_jmpbuf(png_ptr)))
   {
      png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
      return 0;
   }

#  ifdef PNG_BENIGN_ERRORS_SUPPORTED
      png_set_benign_errors(png_ptr, 1/*allowed*/);
#  endif
   png_init_io(png_ptr, fp);

   info_ptr = png_create_info_struct(png_ptr);

   if (info_ptr == NULL)
      png_error(png_ptr, "OOM allocating info structure");

   if (transforms < 0)
      read_by_row(png_ptr, info_ptr, write_file, fp);

   else
      png_read_png(png_ptr, info_ptr, transforms, NULL/*params*/);

   png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
   return 1;
}

static int mytime(struct timespec *t)
{
   /* Do the timing using clock_gettime and the per-process timer. */
   if (!clock_gettime(CLOCK_PROCESS_CPUTIME_ID, t))
      return 1;

   perror("CLOCK_PROCESS_CPUTIME_ID");
   fprintf(stderr, "timepng: could not get the time\n");
   return 0;
}

static int perform_one_test(FILE *fp, int nfiles, png_int_32 transforms)
{
   int i;
   struct timespec before, after;

   /* Clear out all errors: */
   rewind(fp);

   if (mytime(&before))
   {
      for (i=0; i<nfiles; ++i)
      {
         if (read_png(fp, transforms, NULL/*write*/))
         {
            if (ferror(fp))
            {
               perror("temporary file");
               fprintf(stderr, "file %d: error reading PNG data\n", i);
               return 0;
            }
         }

         else
         {
            perror("temporary file");
            fprintf(stderr, "file %d: error from libpng\n", i);
            return 0;
         }
      }
   }

   else
      return 0;

   if (mytime(&after))
   {
      /* Work out the time difference and print it - this is the only output,
       * so flush it immediately.
       */
      unsigned long s = after.tv_sec - before.tv_sec;
      long ns = after.tv_nsec - before.tv_nsec;

      if (ns < 0)
      {
         --s;
         ns += 1000000000;

         if (ns < 0)
         {
            fprintf(stderr, "timepng: bad clock from kernel\n");
            return 0;
         }
      }

      printf("%lu.%.9ld\n", s, ns);
      fflush(stdout);
      if (ferror(stdout))
      {
         fprintf(stderr, "timepng: error writing output\n");
         return 0;
      }

      /* Successful return */
      return 1;
   }

   else
      return 0;
}

static int add_one_file(FILE *fp, char *name)
{
   FILE *ip = fopen(name, "rb");

   if (ip != NULL)
   {
      /* Read the file using libpng; this detects errors and also deals with
       * files which contain data beyond the end of the file.
       */
      int ok = 0;
      fpos_t pos;

      if (fgetpos(fp, &pos))
      {
         /* Fatal error reading the start: */
         perror("temporary file");
         fprintf(stderr, "temporary file fgetpos error\n");
         exit(1);
      }

      if (read_png(ip, -1/*by row*/, fp/*output*/))
      {
         if (ferror(ip))
         {
            perror(name);
            fprintf(stderr, "%s: read error\n", name);
         }

         else
            ok = 1; /* read ok */
      }

      else
         fprintf(stderr, "%s: file not added\n", name);

      (void)fclose(ip);

      /* An error in the output is fatal; exit immediately: */
      if (ferror(fp))
      {
         perror("temporary file");
         fprintf(stderr, "temporary file write error\n");
         exit(1);
      }

      if (ok)
         return 1;

      /* Did not read the file successfully, simply rewind the temporary
       * file.  This must happen after the ferror check above to avoid clearing
       * the error.
       */
      if (fsetpos(fp, &pos))
      {
         perror("temporary file");
         fprintf(stderr, "temporary file fsetpos error\n");
         exit(1);
      }
   }

   else
   {
      /* file open error: */
      perror(name);
      fprintf(stderr, "%s: open failed\n", name);
   }

   return 0; /* file not added */
}

static void
usage(FILE *fp)
{
   if (fp != NULL) fclose(fp);

   fprintf(stderr,
"Usage:\n"
" timepng --assemble <assembly> {files}\n"
"  Read the files into <assembly>, output the count.  Options are ignored.\n"
" timepng --dissemble <assembly> <count> [options]\n"
"  Time <count> files from <assembly>, additional files may not be given.\n"
" Otherwise:\n"
"  Read the files into a temporary file and time the decode\n"
"Transforms:\n"
"  --by-image: read by image with png_read_png\n"
"  --<transform>: implies by-image, use PNG_TRANSFORM_<transform>\n"
"  Otherwise: read by row using png_read_row (to a single row buffer)\n"
   /* ISO C90 string length max 509 */);fprintf(stderr,
"{files}:\n"
"  PNG files to copy into the assembly and time.  Invalid files are skipped\n"
"  with appropriate error messages.  If no files are given the list of files\n"
"  is read from stdin with each file name terminated by a newline\n"
"Output:\n"
"  For --assemble the output is the name of the assembly file followed by the\n"
"  count of the files it contains; the arguments for --dissemble.  Otherwise\n"
"  the output is the total decode time in seconds.\n");

   exit(99);
}

int main(int argc, char **argv)
{
   int ok = 0;
   int err = 0;
   int nfiles = 0;
   int transforms = -1; /* by row */
   const char *assembly = NULL;
   FILE *fp;

   if (argc > 2 && strcmp(argv[1], "--assemble") == 0)
   {
      /* Just build the test file, argv[2] is the file name. */
      assembly = argv[2];
      fp = fopen(assembly, "wb");
      if (fp == NULL)
      {
         perror(assembly);
         fprintf(stderr, "timepng --assemble %s: could not open for write\n",
               assembly);
         usage(NULL);
      }

      argv += 2;
      argc -= 2;
   }

   else if (argc > 3 && strcmp(argv[1], "--dissemble") == 0)
   {
      fp = fopen(argv[2], "rb");

      if (fp == NULL)
      {
         perror(argv[2]);
         fprintf(stderr, "timepng --dissemble %s: could not open for read\n",
               argv[2]);
         usage(NULL);
      }

      nfiles = atoi(argv[3]);
      if (nfiles <= 0)
      {
         fprintf(stderr,
               "timepng --dissemble <file> <count>: %s is not a count\n",
               argv[3]);
         exit(99);
      }
#ifdef __COVERITY__
      else
      {
         nfiles &= PNG_UINT_31_MAX;
      }
#endif

      argv += 3;
      argc -= 3;
   }

   else /* Else use a temporary file */
   {
#ifndef __COVERITY__
      fp = tmpfile();
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
      char tmpfile[] = "timepng-XXXXXX";
      int filedes;
      umask(0177);
      filedes = mkstemp(tmpfile);
      if (filedes < 0)
        fp = NULL;
      else
      {
        fp = fdopen(filedes,"w+");
        /* Hide the filename immediately and ensure that the file does
         * not exist after the program ends
         */
        (void) unlink(tmpfile);
      }
#endif

      if (fp == NULL)
      {
         perror("tmpfile");
         fprintf(stderr, "timepng: could not open the temporary file\n");
         exit(1); /* not a user error */
      }
   }

   /* Handle the transforms: */
   while (argc > 1 && argv[1][0] == '-' && argv[1][1] == '-')
   {
      const char *opt = *++argv + 2;

      --argc;

      /* Transforms turn on the by-image processing and maybe set some
       * transforms:
       */
      if (transforms == -1)
         transforms = PNG_TRANSFORM_IDENTITY;

      if (strcmp(opt, "by-image") == 0)
      {
         /* handled above */
      }

#        define OPT(name) else if (strcmp(opt, #name) == 0)\
         transforms |= PNG_TRANSFORM_ ## name

      OPT(STRIP_16);
      OPT(STRIP_ALPHA);
      OPT(PACKING);
      OPT(PACKSWAP);
      OPT(EXPAND);
      OPT(INVERT_MONO);
      OPT(SHIFT);
      OPT(BGR);
      OPT(SWAP_ALPHA);
      OPT(SWAP_ENDIAN);
      OPT(INVERT_ALPHA);
      OPT(STRIP_FILLER);
      OPT(STRIP_FILLER_BEFORE);
      OPT(STRIP_FILLER_AFTER);
      OPT(GRAY_TO_RGB);
      OPT(EXPAND_16);
      OPT(SCALE_16);

      else
      {
         fprintf(stderr, "timepng %s: unrecognized transform\n", opt);
         usage(fp);
      }
   }

   /* Handle the files: */
   if (argc > 1 && nfiles > 0)
      usage(fp); /* Additional files not valid with --dissemble */

   else if (argc > 1)
   {
      int i;

      for (i=1; i<argc; ++i)
      {
         if (nfiles == INT_MAX)
         {
            fprintf(stderr, "%s: skipped, too many files\n", argv[i]);
            break;
         }

         else if (add_one_file(fp, argv[i]))
            ++nfiles;
      }
   }

   else if (nfiles == 0) /* Read from stdin withoout --dissemble */
   {
      char filename[FILENAME_MAX+1];

      while (fgets(filename, FILENAME_MAX+1, stdin))
      {
         size_t len = strlen(filename);

         if (filename[len-1] == '\n')
         {
            filename[len-1] = 0;
            if (nfiles == INT_MAX)
            {
               fprintf(stderr, "%s: skipped, too many files\n", filename);
               break;
            }

            else if (add_one_file(fp, filename))
               ++nfiles;
         }

         else
         {
            fprintf(stderr, "timepng: file name too long: ...%s\n",
               filename+len-32);
            err = 1;
            break;
         }
      }

      if (ferror(stdin))
      {
         fprintf(stderr, "timepng: stdin: read error\n");
         err = 1;
      }
   }

   /* Perform the test, or produce the --assemble output: */
   if (!err)
   {
      if (nfiles > 0)
      {
         if (assembly != NULL)
         {
            if (fflush(fp) && !ferror(fp) && fclose(fp))
            {
               perror(assembly);
               fprintf(stderr, "%s: close failed\n", assembly);
            }

            else
            {
               printf("%s %d\n", assembly, nfiles);
               fflush(stdout);
               ok = !ferror(stdout);
            }
         }

         else
         {
            ok = perform_one_test(fp, nfiles, transforms);
            (void)fclose(fp);
         }
      }

      else
         usage(fp);
   }

   else
      (void)fclose(fp);

   /* Exit code 0 on success. */
   return ok == 0;
}
#else /* !sufficient support */
int main(void) { return 77; }
#endif /* !sufficient support */

/*- iccfrompng
 *
 * COPYRIGHT: Written by John Cunningham Bowler, 2011.
 * To the extent possible under law, the author has waived all copyright and
 * related or neighboring rights to this work.  This work is published from:
 * United States.
 *
 * Extract any icc profiles found in the given PNG files.  This is a simple
 * example of a program that extracts information from the header of a PNG file
 * without processing the image.  Notice that some header information may occur
 * after the image data. Textual data and comments are an example; the approach
 * in this file won't work reliably for such data because it only looks for the
 * information in the section of the file that precedes the image data.
 *
 * Compile and link against libpng and zlib, plus anything else required on the
 * system you use.
 *
 * To use supply a list of PNG files containing iCCP chunks, the chunks will be
 * extracted to a similarly named file with the extension replaced by 'icc',
 * which will be overwritten without warning.
 */
#include <stdlib.h>
#include <setjmp.h>
#include <string.h>
#include <stdio.h>

#include <png.h>

#if defined(PNG_READ_SUPPORTED) && defined(PNG_STDIO_SUPPORTED) && \
    defined (PNG_iCCP_SUPPORTED)


static int verbose = 1;
static png_byte no_profile[] = "no profile";

static png_bytep
extract(FILE *fp, png_uint_32 *proflen)
{
   png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING,0,0,0);
   png_infop info_ptr = NULL;
   png_bytep result = NULL;

   /* Initialize for error or no profile: */
   *proflen = 0;

   if (png_ptr == NULL)
   {
      fprintf(stderr, "iccfrompng: version library mismatch?\n");
      return 0;
   }

   if (setjmp(png_jmpbuf(png_ptr)))
   {
      png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
      return 0;
   }

   png_init_io(png_ptr, fp);

   info_ptr = png_create_info_struct(png_ptr);
   if (info_ptr == NULL)
      png_error(png_ptr, "OOM allocating info structure");

   png_read_info(png_ptr, info_ptr);

   {
      png_charp name;
      int compression_type;
      png_bytep profile;

      if (png_get_iCCP(png_ptr, info_ptr, &name, &compression_type, &profile,
         proflen) & PNG_INFO_iCCP)
      {
         result = malloc(*proflen);
         if (result != NULL)
            memcpy(result, profile, *proflen);

         else
            png_error(png_ptr, "OOM allocating profile buffer");
      }

      else
	result = no_profile;
   }

   png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
   return result;
}

static int
extract_one_file(const char *filename)
{
   int result = 0;
   FILE *fp = fopen(filename, "rb");

   if (fp != NULL)
   {
      png_uint_32 proflen = 0;
      png_bytep profile = extract(fp, &proflen);

      if (profile != NULL && profile != no_profile)
      {
         size_t len;
         char *output;

         {
            const char *ep = strrchr(filename, '.');

            if (ep != NULL)
               len = ep-filename;

            else
               len = strlen(filename);
         }

         output = malloc(len + 5);
         if (output != NULL)
         {
            FILE *of;

            memcpy(output, filename, len);
            strcpy(output+len, ".icc");

            of = fopen(output, "wb");
            if (of != NULL)
            {
               if (fwrite(profile, proflen, 1, of) == 1 &&
                  fflush(of) == 0 &&
                  fclose(of) == 0)
               {
                  if (verbose)
                     printf("%s -> %s\n", filename, output);
                  /* Success return */
                  result = 1;
               }

               else
               {
                  fprintf(stderr, "%s: error writing profile\n", output);
                  if (remove(output))
                     fprintf(stderr, "%s: could not remove file\n", output);
               }
            }

            else
               fprintf(stderr, "%s: failed to open output file\n", output);

            free(output);
         }

         else
            fprintf(stderr, "%s: OOM allocating string!\n", filename);

         free(profile);
      }

      else if (verbose && profile == no_profile)
	printf("%s has no profile\n", filename);
   }

   else
      fprintf(stderr, "%s: could not open file\n", filename);

   return result;
}

int
main(int argc, char **argv)
{
   int i;
   int extracted = 0;

   for (i=1; i<argc; ++i)
   {
      if (strcmp(argv[i], "-q") == 0)
         verbose = 0;

      else if (extract_one_file(argv[i]))
         extracted = 1;
   }

   /* Exit code is true if any extract succeeds */
   return extracted == 0;
}
#endif /* READ && STDIO && iCCP */

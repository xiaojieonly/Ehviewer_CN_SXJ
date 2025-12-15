/*- simpleover
 *
 * COPYRIGHT: Written by John Cunningham Bowler, 2015.
 * To the extent possible under law, the author has waived all copyright and
 * related or neighboring rights to this work.  This work is published from:
 * United States.
 *
 * Read several PNG files, which should have an alpha channel or transparency
 * information, and composite them together to produce one or more 16-bit linear
 * RGBA intermediates.  This involves doing the correct 'over' composition to
 * combine the alpha channels and corresponding data.
 *
 * Finally read an output (background) PNG using the 24-bit RGB format (the
 * PNG will be composited on green (#00ff00) by default if it has an alpha
 * channel), and apply the intermediate image generated above to specified
 * locations in the image.
 *
 * The command line has the general format:
 *
 *    simpleover <background.png> [output.png]
 *        {--sprite=width,height,name {[--at=x,y] {sprite.png}}}
 *        {--add=name {x,y}}
 *
 * The --sprite and --add options may occur multiple times. They are executed
 * in order.  --add may refer to any sprite already read.
 *
 * This code is intended to show how to composite multiple images together
 * correctly.  Apart from the libpng Simplified API the only work done in here
 * is to combine multiple input PNG images into a single sprite; this involves
 * a Porter-Duff 'over' operation and the input PNG images may, as a result,
 * be regarded as being layered one on top of the other with the first (leftmost
 * on the command line) being at the bottom and the last on the top.
 */
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>

/* Normally use <png.h> here to get the installed libpng, but this is done to
 * ensure the code picks up the local libpng implementation, so long as this
 * file is linked against a sufficiently recent libpng (1.6+) it is ok to
 * change this to <png.h>:
 */
#include "../../png.h"

#ifdef PNG_SIMPLIFIED_READ_SUPPORTED

#define sprite_name_chars 15
struct sprite {
   FILE         *file;
   png_uint_16p  buffer;
   unsigned int  width;
   unsigned int  height;
   char          name[sprite_name_chars+1];
};

#if 0 /* div by 65535 test program */
#include <math.h>
#include <stdio.h>

int main(void) {
   double err = 0;
   unsigned int xerr = 0;
   unsigned int r = 32769;
   {
      unsigned int x = 0;

      do {
         unsigned int t = x + (x >> 16) /*+ (x >> 31)*/ + r;
         double v = x, errtest;

         if (t < x) {
            fprintf(stderr, "overflow: %u+%u -> %u\n", x, r, t);
            return 1;
         }

         v /= 65535;
         errtest = v;
         t >>= 16;
         errtest -= t;

         if (errtest > err) {
            err = errtest;
            xerr = x;

            if (errtest >= .5) {
               fprintf(stderr, "error: %u/65535 = %f, not %u, error %f\n",
                     x, v, t, errtest);
               return 0;
            }
         }
      } while (++x <= 65535U*65535U);
   }

   printf("error %f @ %u\n", err, xerr);

   return 0;
}
#endif /* div by 65535 test program */

static void
sprite_op(const struct sprite *sprite, int x_offset, int y_offset,
   png_imagep image, const png_uint_16 *buffer)
{
   /* This is where the Porter-Duff 'Over' operator is evaluated; change this
    * code to change the operator (this could be parameterized).  Any other
    * image processing operation could be used here.
    */


   /* Check for an x or y offset that pushes any part of the image beyond the
    * right or bottom of the sprite:
    */
   if ((y_offset < 0 || (unsigned)/*SAFE*/y_offset < sprite->height) &&
       (x_offset < 0 || (unsigned)/*SAFE*/x_offset < sprite->width))
   {
      unsigned int y = 0;

      if (y_offset < 0)
         y = -y_offset; /* Skip to first visible row */

      do
      {
         unsigned int x = 0;

         if (x_offset < 0)
            x = -x_offset;

         do
         {
            /* In and out are RGBA values, so: */
            const png_uint_16 *in_pixel = buffer + (y * image->width + x)*4;
            png_uint_32 in_alpha = in_pixel[3];

            /* This is the optimized Porter-Duff 'Over' operation, when the
             * input alpha is 0 the output is not changed.
             */
            if (in_alpha > 0)
            {
               png_uint_16 *out_pixel = sprite->buffer +
                  ((y+y_offset) * sprite->width + (x+x_offset))*4;

               /* This is the weight to apply to the output: */
               in_alpha = 65535-in_alpha;

               if (in_alpha > 0)
               {
                  /* The input must be composed onto the output. This means
                   * multiplying the current output pixel value by the inverse
                   * of the input alpha (1-alpha). A division is required but
                   * it is by the constant 65535.  Approximate this as:
                   *
                   *     (x + (x >> 16) + 32769) >> 16;
                   *
                   * This is exact (and does not overflow) for all values of
                   * x in the range 0..65535*65535.  (Note that the calculation
                   * produces the closest integer; the maximum error is <0.5).
                   */
                  png_uint_32 tmp;

#                 define compose(c)\
                     tmp = out_pixel[c] * in_alpha;\
                     tmp = (tmp + (tmp >> 16) + 32769) >> 16;\
                     out_pixel[c] = tmp + in_pixel[c]

                  /* The following is very vectorizable... */
                  compose(0);
                  compose(1);
                  compose(2);
                  compose(3);
               }

               else
                  out_pixel[0] = in_pixel[0],
                  out_pixel[1] = in_pixel[1],
                  out_pixel[2] = in_pixel[2],
                  out_pixel[3] = in_pixel[3];
            }
         }
         while (++x < image->width);
      }
      while (++y < image->height);
   }
}

static int
create_sprite(struct sprite *sprite, int *argc, const char ***argv)
{
   /* Read the arguments and create this sprite. The sprite buffer has already
    * been allocated. This reads the input PNGs one by one in linear format,
    * composes them onto the sprite buffer (the code in the function above)
    * then saves the result, converting it on the fly to PNG RGBA 8-bit format.
    */
   while (*argc > 0)
   {
      char tombstone;
      int x = 0, y = 0;

      if ((*argv)[0][0] == '-' && (*argv)[0][1] == '-')
      {
         /* The only supported option is --at. */
         if (sscanf((*argv)[0], "--at=%d,%d%c", &x, &y, &tombstone) != 2)
            break; /* success; caller will parse this option */

         ++*argv, --*argc;
      }

      else
      {
         /* The argument has to be a file name */
         png_image image;

         image.version = PNG_IMAGE_VERSION;
         image.opaque = NULL;

         if (png_image_begin_read_from_file(&image, (*argv)[0]))
         {
            png_uint_16p buffer;

            image.format = PNG_FORMAT_LINEAR_RGB_ALPHA;

            buffer = malloc(PNG_IMAGE_SIZE(image));

            if (buffer != NULL)
            {
               if (png_image_finish_read(&image, NULL/*background*/, buffer,
                  0/*row_stride*/,
                  NULL/*colormap for PNG_FORMAT_FLAG_COLORMAP*/))
               {
                  /* This is the place where the Porter-Duff 'Over' operator
                   * needs to be done by this code.  In fact, any image
                   * processing required can be done here; the data is in
                   * the correct format (linear, 16-bit) and source and
                   * destination are in memory.
                   */
                  sprite_op(sprite, x, y, &image, buffer);
                  free(buffer);
                  ++*argv, --*argc;
                  /* And continue to the next argument */
                  continue;
               }

               else
               {
                  free(buffer);
                  fprintf(stderr, "simpleover: read %s: %s\n", (*argv)[0],
                      image.message);
               }
            }

            else
            {
               fprintf(stderr, "simpleover: out of memory: %lu bytes\n",
                  (unsigned long)PNG_IMAGE_SIZE(image));

               /* png_image_free must be called if we abort the Simplified API
                * read because of a problem detected in this code.  If problems
                * are detected in the Simplified API it cleans up itself.
                */
               png_image_free(&image);
            }
         }

         else
         {
            /* Failed to read the first argument: */
            fprintf(stderr, "simpleover: %s: %s\n", (*argv)[0], image.message);
         }

         return 0; /* failure */
      }
   }

   /* All the sprite operations have completed successfully. Save the RGBA
    * buffer as a PNG using the simplified write API.
    */
   sprite->file = tmpfile();

   if (sprite->file != NULL)
   {
      png_image save;

      memset(&save, 0, sizeof save);
      save.version = PNG_IMAGE_VERSION;
      save.opaque = NULL;
      save.width = sprite->width;
      save.height = sprite->height;
      save.format = PNG_FORMAT_LINEAR_RGB_ALPHA;
      save.flags = PNG_IMAGE_FLAG_FAST;
      save.colormap_entries = 0;

      if (png_image_write_to_stdio(&save, sprite->file, 1/*convert_to_8_bit*/,
          sprite->buffer, 0/*row_stride*/, NULL/*colormap*/))
      {
         /* Success; the buffer is no longer needed: */
         free(sprite->buffer);
         sprite->buffer = NULL;
         return 1; /* ok */
      }

      else
         fprintf(stderr, "simpleover: write sprite %s: %s\n", sprite->name,
            save.message);
   }

   else
      fprintf(stderr, "simpleover: sprite %s: could not allocate tmpfile: %s\n",
         sprite->name, strerror(errno));

   return 0; /* fail */
}

static int
add_sprite(png_imagep output, png_bytep out_buf, struct sprite *sprite,
   int *argc, const char ***argv)
{
   /* Given a --add argument naming this sprite, perform the operations listed
    * in the following arguments.  The arguments are expected to have the form
    * (x,y), which is just an offset at which to add the sprite to the
    * output.
    */
   while (*argc > 0)
   {
      char tombstone;
      int x, y;

      if ((*argv)[0][0] == '-' && (*argv)[0][1] == '-')
         return 1; /* success */

      if (sscanf((*argv)[0], "%d,%d%c", &x, &y, &tombstone) == 2)
      {
         /* Now add the new image into the sprite data, but only if it
          * will fit.
          */
         if (x < 0 || y < 0 ||
             (unsigned)/*SAFE*/x >= output->width ||
             (unsigned)/*SAFE*/y >= output->height ||
             sprite->width > output->width-x ||
             sprite->height > output->height-y)
         {
            fprintf(stderr, "simpleover: sprite %s @ (%d,%d) outside image\n",
               sprite->name, x, y);
            /* Could just skip this, but for the moment it is an error */
            return 0; /* error */
         }

         else
         {
            /* Since we know the sprite fits we can just read it into the
             * output using the simplified API.
             */
            png_image in;

            in.version = PNG_IMAGE_VERSION;
            rewind(sprite->file);

            if (png_image_begin_read_from_stdio(&in, sprite->file))
            {
               in.format = PNG_FORMAT_RGB; /* force compose */

               if (png_image_finish_read(&in, NULL/*background*/,
                  out_buf + (y*output->width + x)*3/*RGB*/,
                  output->width*3/*row_stride*/,
                  NULL/*colormap for PNG_FORMAT_FLAG_COLORMAP*/))
               {
                  ++*argv, --*argc;
                  continue;
               }
            }

            /* The read failed: */
            fprintf(stderr, "simpleover: add sprite %s: %s\n", sprite->name,
                in.message);
            return 0; /* error */
         }
      }

      else
      {
         fprintf(stderr, "simpleover: --add='%s': invalid position %s\n",
               sprite->name, (*argv)[0]);
         return 0; /* error */
      }
   }

   return 1; /* ok */
}

static int
simpleover_process(png_imagep output, png_bytep out_buf, int argc,
   const char **argv)
{
   int result = 1; /* success */
#  define csprites 10/*limit*/
#  define str(a) #a
   int nsprites = 0;
   struct sprite sprites[csprites];

   while (argc > 0)
   {
      result = 0; /* fail */

      if (strncmp(argv[0], "--sprite=", 9) == 0)
      {
         char tombstone;

         if (nsprites < csprites)
         {
            int n;

            sprites[nsprites].width = sprites[nsprites].height = 0;
            sprites[nsprites].name[0] = 0;

            n = sscanf(argv[0], "--sprite=%u,%u,%" str(sprite_name_chars) "s%c",
                &sprites[nsprites].width, &sprites[nsprites].height,
                sprites[nsprites].name, &tombstone);

            if ((n == 2 || n == 3) &&
                sprites[nsprites].width > 0 && sprites[nsprites].height > 0)
            {
               size_t buf_size, tmp;

               /* Default a name if not given. */
               if (sprites[nsprites].name[0] == 0)
                  sprintf(sprites[nsprites].name, "sprite-%d", nsprites+1);

               /* Allocate a buffer for the sprite and calculate the buffer
                * size:
                */
               buf_size = sizeof (png_uint_16 [4]);
               buf_size *= sprites[nsprites].width;
               buf_size *= sprites[nsprites].height;

               /* This can overflow a (size_t); check for this: */
               tmp = buf_size;
               tmp /= sprites[nsprites].width;
               tmp /= sprites[nsprites].height;

               if (tmp == sizeof (png_uint_16 [4]))
               {
                  sprites[nsprites].buffer = malloc(buf_size);
                  /* This buffer must be initialized to transparent: */
                  memset(sprites[nsprites].buffer, 0, buf_size);

                  if (sprites[nsprites].buffer != NULL)
                  {
                     sprites[nsprites].file = NULL;
                     ++argv, --argc;

                     if (create_sprite(sprites+nsprites++, &argc, &argv))
                     {
                        result = 1; /* still ok */
                        continue;
                     }

                     break; /* error */
                  }
               }

               /* Overflow, or OOM */
               fprintf(stderr, "simpleover: %s: sprite too large\n", argv[0]);
               break;
            }

            else
            {
               fprintf(stderr, "simpleover: %s: invalid sprite (%u,%u)\n",
                  argv[0], sprites[nsprites].width, sprites[nsprites].height);
               break;
            }
         }

         else
         {
            fprintf(stderr, "simpleover: %s: too many sprites\n", argv[0]);
            break;
         }
      }

      else if (strncmp(argv[0], "--add=", 6) == 0)
      {
         const char *name = argv[0]+6;
         int isprite = nsprites;

         ++argv, --argc;

         while (--isprite >= 0)
         {
            if (strcmp(sprites[isprite].name, name) == 0)
            {
               if (!add_sprite(output, out_buf, sprites+isprite, &argc, &argv))
                  goto out; /* error in add_sprite */

               break;
            }
         }

         if (isprite < 0) /* sprite not found */
         {
            fprintf(stderr, "simpleover: --add='%s': sprite not found\n", name);
            break;
         }
      }

      else
      {
         fprintf(stderr, "simpleover: %s: unrecognized operation\n", argv[0]);
         break;
      }

      result = 1; /* ok  */
   }

   /* Clean up the cache of sprites: */
out:
   while (--nsprites >= 0)
   {
      if (sprites[nsprites].buffer != NULL)
         free(sprites[nsprites].buffer);

      if (sprites[nsprites].file != NULL)
         (void)fclose(sprites[nsprites].file);
   }

   return result;
}

int main(int argc, const char **argv)
{
   int result = 1; /* default to fail */

   if (argc >= 2)
   {
      int argi = 2;
      const char *output = NULL;
      png_image image;

      if (argc > 2 && argv[2][0] != '-'/*an operation*/)
      {
         output = argv[2];
         argi = 3;
      }

      image.version = PNG_IMAGE_VERSION;
      image.opaque = NULL;

      if (png_image_begin_read_from_file(&image, argv[1]))
      {
         png_bytep buffer;

         image.format = PNG_FORMAT_RGB; /* 24-bit RGB */

         buffer = malloc(PNG_IMAGE_SIZE(image));

         if (buffer != NULL)
         {
            png_color background = {0, 0xff, 0}; /* fully saturated green */

            if (png_image_finish_read(&image, &background, buffer,
               0/*row_stride*/, NULL/*colormap for PNG_FORMAT_FLAG_COLORMAP */))
            {
               /* At this point png_image_finish_read has cleaned up the
                * allocated data in png_image, and only the buffer needs to be
                * freed.
                *
                * Perform the remaining operations:
                */
               if (simpleover_process(&image, buffer, argc-argi, argv+argi))
               {
                  /* Write the output: */
                  if ((output != NULL &&
                       png_image_write_to_file(&image, output,
                        0/*convert_to_8bit*/, buffer, 0/*row_stride*/,
                        NULL/*colormap*/)) ||
                      (output == NULL &&
                       png_image_write_to_stdio(&image, stdout,
                        0/*convert_to_8bit*/, buffer, 0/*row_stride*/,
                        NULL/*colormap*/)))
                     result = 0;

                  else
                     fprintf(stderr, "simpleover: write %s: %s\n",
                        output == NULL ? "stdout" : output, image.message);
               }

               /* else simpleover_process writes an error message */
            }

            else
               fprintf(stderr, "simpleover: read %s: %s\n", argv[1],
                   image.message);

            free(buffer);
         }

         else
         {
            fprintf(stderr, "simpleover: out of memory: %lu bytes\n",
               (unsigned long)PNG_IMAGE_SIZE(image));

            /* This is the only place where a 'free' is required; libpng does
             * the cleanup on error and success, but in this case we couldn't
             * complete the read because of running out of memory.
             */
            png_image_free(&image);
         }
      }

      else
      {
         /* Failed to read the first argument: */
         fprintf(stderr, "simpleover: %s: %s\n", argv[1], image.message);
      }
   }

   else
   {
      /* Usage message */
      fprintf(stderr,
         "simpleover: usage: simpleover background.png [output.png]\n"
         "  Output 'background.png' as a 24-bit RGB PNG file in 'output.png'\n"
         "   or, if not given, stdout.  'background.png' will be composited\n"
         "   on fully saturated green.\n"
         "\n"
         "  Optionally, before output, process additional PNG files:\n"
         "\n"
         "   --sprite=width,height,name {[--at=x,y] {sprite.png}}\n"
         "    Produce a transparent sprite of size (width,height) and with\n"
         "     name 'name'.\n"
         "    For each sprite.png composite it using a Porter-Duff 'Over'\n"
         "     operation at offset (x,y) in the sprite (defaulting to (0,0)).\n"
         "     Input PNGs will be truncated to the area of the sprite.\n"
         "\n"
         "   --add='name' {x,y}\n"
         "    Optionally, before output, composite a sprite, 'name', which\n"
         "     must have been previously produced using --sprite, at each\n"
         "     offset (x,y) in the output image.  Each sprite must fit\n"
         "     completely within the output image.\n"
         "\n"
         "  PNG files are processed in the order they occur on the command\n"
         "  line and thus the first PNG processed appears as the bottommost\n"
         "  in the output image.\n");
   }

   return result;
}
#endif /* SIMPLIFIED_READ */

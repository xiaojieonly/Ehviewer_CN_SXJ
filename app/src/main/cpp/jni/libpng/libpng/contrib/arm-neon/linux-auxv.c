/* contrib/arm-neon/linux-auxv.c
 *
 * Copyright (c) 2014 Glenn Randers-Pehrson
 * Written by Mans Rullgard, 2011.
 * Last changed in libpng 1.6.10 [March 6, 2014]
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * SEE contrib/arm-neon/README before reporting bugs
 *
 * STATUS: COMPILED, TESTED
 * BUG REPORTS: png-mng-implement@sourceforge.net
 *
 * png_have_neon implemented for Linux versions which allow access to
 * /proc/self/auxv.  This is probably faster, cleaner and safer than the code to
 * read /proc/cpuinfo in contrib/arm-neon/linux, however it is yet another piece
 * of potentially untested code and has more complex dependencies than the code
 * to read cpuinfo.
 *
 * This generic __linux__ implementation requires reading /proc/self/auxv and
 * looking at each element for one that records NEON capabilities.
 */
#include <unistd.h> /* for POSIX 1003.1 */
#include <errno.h>  /* for EINTR */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <elf.h>
#include <asm/hwcap.h>

/* A read call may be interrupted, in which case it returns -1 and sets errno to
 * EINTR if nothing was done, otherwise (if something was done) a partial read
 * may result.
 */
static size_t
safe_read(png_structp png_ptr, int fd, void *buffer_in, size_t nbytes)
{
   size_t ntotal = 0;
   char *buffer = png_voidcast(char*, buffer_in);

   while (nbytes > 0)
   {
      unsigned int nread;
      int iread;

      /* Passing nread > INT_MAX to read is implementation defined in POSIX
       * 1003.1, therefore despite the unsigned argument portable code must
       * limit the value to INT_MAX!
       */
      if (nbytes > INT_MAX)
         nread = INT_MAX;

      else
         nread = (unsigned int)/*SAFE*/nbytes;

      iread = read(fd, buffer, nread);

      if (iread == -1)
      {
         /* This is the devil in the details, a read can terminate early with 0
          * bytes read because of EINTR, yet it still returns -1 otherwise end
          * of file cannot be distinguished.
          */
         if (errno != EINTR)
         {
            png_warning(png_ptr, "/proc read failed");
            return 0; /* I.e., a permanent failure */
         }
      }

      else if (iread < 0)
      {
         /* Not a valid 'read' result: */
         png_warning(png_ptr, "OS /proc read bug");
         return 0;
      }

      else if (iread > 0)
      {
         /* Continue reading until a permanent failure, or EOF */
         buffer += iread;
         nbytes -= (unsigned int)/*SAFE*/iread;
         ntotal += (unsigned int)/*SAFE*/iread;
      }

      else
         return ntotal;
   }

   return ntotal; /* nbytes == 0 */
}

static int
png_have_neon(png_structp png_ptr)
{
   int fd = open("/proc/self/auxv", O_RDONLY);
   Elf32_auxv_t aux;

   /* Failsafe: failure to open means no NEON */
   if (fd == -1)
   {
      png_warning(png_ptr, "/proc/self/auxv open failed");
      return 0;
   }

   while (safe_read(png_ptr, fd, &aux, sizeof aux) == sizeof aux)
   {
      if (aux.a_type == AT_HWCAP && (aux.a_un.a_val & HWCAP_NEON) != 0)
      {
         close(fd);
         return 1;
      }
   }

   close(fd);
   return 0;
}

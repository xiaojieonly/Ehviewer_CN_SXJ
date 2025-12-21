/*
 * jccolext.c
 *
 * This file was part of the Independent JPEG Group's software:
 * Copyright (C) 1991-1996, Thomas G. Lane.
 * libjpeg-turbo Modifications:
 * Copyright (C) 2009-2012, 2015, D. R. Commander.
 * For conditions of distribution and use, see the accompanying README.ijg
 * file.
 *
 * This file contains input colorspace conversion routines.
 */


/* This file is included by jccolor.c */


/*
 * Convert some rows of samples to the JPEG colorspace.
 *
 * Note that we change from the application's interleaved-pixel format
 * to our internal noninterleaved, one-plane-per-component format.
 * The input buffer is therefore three times as wide as the output buffer.
 *
 * A starting row offset is provided only for the output buffer.  The caller
 * can easily adjust the passed input_buf value to accommodate any row
 * offset required on that side.
 */

INLINE
LOCAL(void)
rgb_ycc_convert_internal(j_compress_ptr cinfo, JSAMPARRAY input_buf,
                         JSAMPIMAGE output_buf, JDIMENSION output_row,
                         int num_rows)
{
  my_cconvert_ptr cconvert = (my_cconvert_ptr)cinfo->cconvert;
  register int r, g, b;
  register JLONG *ctab = cconvert->rgb_ycc_tab;
  register JSAMPROW inptr;
  register JSAMPROW outptr0, outptr1, outptr2;
  register JDIMENSION col;
  JDIMENSION num_cols = cinfo->image_width;

  while (--num_rows >= 0) {
    inptr = *input_buf++;
    outptr0 = output_buf[0][output_row];
    outptr1 = output_buf[1][output_row];
    outptr2 = output_buf[2][output_row];
    output_row++;
    for (col = 0; col < num_cols; col++) {
      r = GETJSAMPLE(inptr[RGB_RED]);
      g = GETJSAMPLE(inptr[RGB_GREEN]);
      b = GETJSAMPLE(inptr[RGB_BLUE]);
      inptr += RGB_PIXELSIZE;
      /* If the inputs are 0..MAXJSAMPLE, the outputs of these equations
       * must be too; we do not need an explicit range-limiting operation.
       * Hence the value being shifted is never negative, and we don't
       * need the general RIGHT_SHIFT macro.
       */
      /* Y */
      outptr0[col] = (JSAMPLE)((ctab[r + R_Y_OFF] + ctab[g + G_Y_OFF] +
                                ctab[b + B_Y_OFF]) >> SCALEBITS);
      /* Cb */
      outptr1[col] = (JSAMPLE)((ctab[r + R_CB_OFF] + ctab[g + G_CB_OFF] +
                                ctab[b + B_CB_OFF]) >> SCALEBITS);
      /* Cr */
      outptr2[col] = (JSAMPLE)((ctab[r + R_CR_OFF] + ctab[g + G_CR_OFF] +
                                ctab[b + B_CR_OFF]) >> SCALEBITS);
    }
  }
}


/**************** Cases other than RGB -> YCbCr **************/


/*
 * Convert some rows of samples to the JPEG colorspace.
 * This version handles RGB->grayscale conversion, which is the same
 * as the RGB->Y portion of RGB->YCbCr.
 * We assume rgb_ycc_start has been called (we only use the Y tables).
 */

INLINE
LOCAL(void)
rgb_gray_convert_internal(j_compress_ptr cinfo, JSAMPARRAY input_buf,
                          JSAMPIMAGE output_buf, JDIMENSION output_row,
                          int num_rows)
{
  my_cconvert_ptr cconvert = (my_cconvert_ptr)cinfo->cconvert;
  register int r, g, b;
  register JLONG *ctab = cconvert->rgb_ycc_tab;
  register JSAMPROW inptr;
  register JSAMPROW outptr;
  register JDIMENSION col;
  JDIMENSION num_cols = cinfo->image_width;

  while (--num_rows >= 0) {
    inptr = *input_buf++;
    outptr = output_buf[0][output_row];
    output_row++;
    for (col = 0; col < num_cols; col++) {
      r = GETJSAMPLE(inptr[RGB_RED]);
      g = GETJSAMPLE(inptr[RGB_GREEN]);
      b = GETJSAMPLE(inptr[RGB_BLUE]);
      inptr += RGB_PIXELSIZE;
      /* Y */
      outptr[col] = (JSAMPLE)((ctab[r + R_Y_OFF] + ctab[g + G_Y_OFF] +
                               ctab[b + B_Y_OFF]) >> SCALEBITS);
    }
  }
}


/*
 * Convert some rows of samples to the JPEG colorspace.
 * This version handles extended RGB->plain RGB conversion
 */

INLINE
LOCAL(void)
rgb_rgb_convert_internal(j_compress_ptr cinfo, JSAMPARRAY input_buf,
                         JSAMPIMAGE output_buf, JDIMENSION output_row,
                         int num_rows)
{
  register JSAMPROW inptr;
  register JSAMPROW outptr0, outptr1, outptr2;
  register JDIMENSION col;
  JDIMENSION num_cols = cinfo->image_width;

  while (--num_rows >= 0) {
    inptr = *input_buf++;
    outptr0 = output_buf[0][output_row];
    outptr1 = output_buf[1][output_row];
    outptr2 = output_buf[2][output_row];
    output_row++;
    for (col = 0; col < num_cols; col++) {
      outptr0[col] = GETJSAMPLE(inptr[RGB_RED]);
      outptr1[col] = GETJSAMPLE(inptr[RGB_GREEN]);
      outptr2[col] = GETJSAMPLE(inptr[RGB_BLUE]);
      inptr += RGB_PIXELSIZE;
    }
  }
}


/*
 * Convert some rows of samples to the JPEG colorspace.
 * This version handles extended RGB->CMYK conversion
 */

INLINE
LOCAL(void)
rgb_cmyk_convert_internal (j_compress_ptr cinfo,
                           JSAMPARRAY input_buf, JSAMPIMAGE output_buf,
                           JDIMENSION output_row, int num_rows)
{
  register int r, g, b;
  register int c, m, y, k;
  register JSAMPROW inptr;
  register JSAMPROW outptr0, outptr1, outptr2, outptr3;
  register JDIMENSION col;
  JDIMENSION num_cols = cinfo->image_width;

  while (--num_rows >= 0) {
    inptr = *input_buf++;
    outptr0 = output_buf[0][output_row];
    outptr1 = output_buf[1][output_row];
    outptr2 = output_buf[2][output_row];
    outptr3 = output_buf[3][output_row];
    output_row++;
    for (col = 0; col < num_cols; col++) {
      r = GETJSAMPLE(inptr[RGB_RED]);
      g = GETJSAMPLE(inptr[RGB_GREEN]);
      b = GETJSAMPLE(inptr[RGB_BLUE]);
      inptr += RGB_PIXELSIZE;

      k = MAX(r, g);
      k = MAX(b, k);
      if (k == 0) {
        c = 0;
        m = 0;
        y = 0;
      } else {
        c = r * MAXJSAMPLE / k;
        m = g * MAXJSAMPLE / k;
        y = b * MAXJSAMPLE / k;
      }

      outptr0[col] = c;
      outptr1[col] = m;
      outptr2[col] = y;
      outptr3[col] = k;
    }
  }
}


/*
 * Convert some rows of samples to the JPEG colorspace.
 * This version handles extended RGB->YCCK conversion
 */

INLINE
LOCAL(void)
rgb_ycck_convert_internal (j_compress_ptr cinfo,
                           JSAMPARRAY input_buf, JSAMPIMAGE output_buf,
                           JDIMENSION output_row, int num_rows)
{
  my_cconvert_ptr cconvert = (my_cconvert_ptr) cinfo->cconvert;
  register int r, g, b;
  register int y, cb, cr, k;
  register JLONG * ctab = cconvert->rgb_ycc_tab;
  register JSAMPROW inptr;
  register JSAMPROW outptr0, outptr1, outptr2, outptr3;
  register JDIMENSION col;
  JDIMENSION num_cols = cinfo->image_width;

  while (--num_rows >= 0) {
    inptr = *input_buf++;
    outptr0 = output_buf[0][output_row];
    outptr1 = output_buf[1][output_row];
    outptr2 = output_buf[2][output_row];
    outptr3 = output_buf[3][output_row];
    output_row++;
    for (col = 0; col < num_cols; col++) {
      r = GETJSAMPLE(inptr[RGB_RED]);
      g = GETJSAMPLE(inptr[RGB_GREEN]);
      b = GETJSAMPLE(inptr[RGB_BLUE]);
      inptr += RGB_PIXELSIZE;

      k = MAX(r, g);
      k = MAX(b, k);
      if (k == 0) {
        y  = 0;
        cb = 0;
        cr = 0;
      } else {
        r = MAXJSAMPLE - (r * MAXJSAMPLE / k);
        g = MAXJSAMPLE - (g * MAXJSAMPLE / k);
        b = MAXJSAMPLE - (b * MAXJSAMPLE / k);
        /* Avoid negative value */
        if (r < 0) r = 0;
        if (g < 0) g = 0;
        if (b < 0) b = 0;
        /* If the inputs are 0..MAXJSAMPLE, the outputs of these equations
         * must be too; we do not need an explicit range-limiting operation.
         * Hence the value being shifted is never negative, and we don't
         * need the general RIGHT_SHIFT macro.
         */
        y  = (JSAMPLE)
                 ((ctab[r+R_Y_OFF] + ctab[g+G_Y_OFF] + ctab[b+B_Y_OFF])
                  >> SCALEBITS);
        cb = (JSAMPLE)
                 ((ctab[r+R_CB_OFF] + ctab[g+G_CB_OFF] + ctab[b+B_CB_OFF])
                  >> SCALEBITS);
        cr = (JSAMPLE)
                 ((ctab[r+R_CR_OFF] + ctab[g+G_CR_OFF] + ctab[b+B_CR_OFF])
                  >> SCALEBITS);
      }

      outptr0[col] = y;
      outptr1[col] = cb;
      outptr2[col] = cr;
      outptr3[col] = k;
    }
  }
}

/*
 * Copyright (C)2011-2015, 2018 D. R. Commander.  All Rights Reserved.
 * Copyright (C)2015 Viktor Szathmáry.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of the libjpeg-turbo Project nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS",
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.libjpegturbo.turbojpeg;

import java.awt.image.*;
import java.nio.*;
import java.io.*;

/**
 * TurboJPEG decompressor
 */
public class TJDecompressor implements Closeable {

  private static final String NO_ASSOC_ERROR =
    "No JPEG image is associated with this instance";

  /**
   * Create a TurboJPEG decompresssor instance.
   */
  public TJDecompressor() throws TJException {
    init();
  }

  /**
   * Create a TurboJPEG decompressor instance and associate the JPEG source
   * image stored in <code>jpegImage</code> with the newly created instance.
   *
   * @param jpegImage JPEG image buffer (size of the JPEG image is assumed to
   * be the length of the array.)  This buffer is not modified.
   */
  public TJDecompressor(byte[] jpegImage) throws TJException {
    init();
    setSourceImage(jpegImage, jpegImage.length);
  }

  /**
   * Create a TurboJPEG decompressor instance and associate the JPEG source
   * image of length <code>imageSize</code> bytes stored in
   * <code>jpegImage</code> with the newly created instance.
   *
   * @param jpegImage JPEG image buffer.  This buffer is not modified.
   *
   * @param imageSize size of the JPEG image (in bytes)
   */
  public TJDecompressor(byte[] jpegImage, int imageSize) throws TJException {
    init();
    setSourceImage(jpegImage, imageSize);
  }

  /**
   * Create a TurboJPEG decompressor instance and associate the YUV planar
   * source image stored in <code>yuvImage</code> with the newly created
   * instance.
   *
   * @param yuvImage {@link YUVImage} instance containing a YUV planar
   * image to be decoded.  This image is not modified.
   */
  @SuppressWarnings("checkstyle:HiddenField")
  public TJDecompressor(YUVImage yuvImage) throws TJException {
    init();
    setSourceImage(yuvImage);
  }

  /**
   * Associate the JPEG image of length <code>imageSize</code> bytes stored in
   * <code>jpegImage</code> with this decompressor instance.  This image will
   * be used as the source image for subsequent decompress operations.
   *
   * @param jpegImage JPEG image buffer.  This buffer is not modified.
   *
   * @param imageSize size of the JPEG image (in bytes)
   */
  public void setSourceImage(byte[] jpegImage, int imageSize)
                             throws TJException {
    if (jpegImage == null || imageSize < 1)
      throw new IllegalArgumentException("Invalid argument in setSourceImage()");
    jpegBuf = jpegImage;
    jpegBufSize = imageSize;
    decompressHeader(jpegBuf, jpegBufSize);
    yuvImage = null;
  }

  /**
   * @deprecated Use {@link #setSourceImage(byte[], int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void setJPEGImage(byte[] jpegImage, int imageSize)
                           throws TJException {
    setSourceImage(jpegImage, imageSize);
  }

  /**
   * Associate the specified YUV planar source image with this decompressor
   * instance.  Subsequent decompress operations will decode this image into an
   * RGB or grayscale destination image.
   *
   * @param srcImage {@link YUVImage} instance containing a YUV planar image to
   * be decoded.  This image is not modified.
   */
  public void setSourceImage(YUVImage srcImage) {
    if (srcImage == null)
      throw new IllegalArgumentException("Invalid argument in setSourceImage()");
    yuvImage = srcImage;
    jpegBuf = null;
    jpegBufSize = 0;
  }


  /**
   * Returns the width of the source image (JPEG or YUV) associated with this
   * decompressor instance.
   *
   * @return the width of the source image (JPEG or YUV) associated with this
   * decompressor instance.
   */
  public int getWidth() {
    if (yuvImage != null)
      return yuvImage.getWidth();
    if (jpegWidth < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return jpegWidth;
  }

  /**
   * Returns the height of the source image (JPEG or YUV) associated with this
   * decompressor instance.
   *
   * @return the height of the source image (JPEG or YUV) associated with this
   * decompressor instance.
   */
  public int getHeight() {
    if (yuvImage != null)
      return yuvImage.getHeight();
    if (jpegHeight < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return jpegHeight;
  }

  /**
   * Returns the level of chrominance subsampling used in the source image
   * (JPEG or YUV) associated with this decompressor instance.  See
   * {@link TJ#SAMP_444 TJ.SAMP_*}.
   *
   * @return the level of chrominance subsampling used in the source image
   * (JPEG or YUV) associated with this decompressor instance.
   */
  public int getSubsamp() {
    if (yuvImage != null)
      return yuvImage.getSubsamp();
    if (jpegSubsamp < 0)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (jpegSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException("JPEG header information is invalid");
    return jpegSubsamp;
  }

  /**
   * Returns the colorspace used in the source image (JPEG or YUV) associated
   * with this decompressor instance.  See {@link TJ#CS_RGB TJ.CS_*}.  If the
   * source image is YUV, then this always returns {@link TJ#CS_YCbCr}.
   *
   * @return the colorspace used in the source image (JPEG or YUV) associated
   * with this decompressor instance.
   */
  public int getColorspace() {
    if (yuvImage != null)
      return TJ.CS_YCbCr;
    if (jpegColorspace < 0)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (jpegColorspace >= TJ.NUMCS)
      throw new IllegalStateException("JPEG header information is invalid");
    return jpegColorspace;
  }

  /**
   * Returns the JPEG image buffer associated with this decompressor instance.
   *
   * @return the JPEG image buffer associated with this decompressor instance.
   */
  public byte[] getJPEGBuf() {
    if (jpegBuf == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return jpegBuf;
  }

  /**
   * Returns the size of the JPEG image (in bytes) associated with this
   * decompressor instance.
   *
   * @return the size of the JPEG image (in bytes) associated with this
   * decompressor instance.
   */
  public int getJPEGSize() {
    if (jpegBufSize < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return jpegBufSize;
  }

  /**
   * Returns the width of the largest scaled-down image that the TurboJPEG
   * decompressor can generate without exceeding the desired image width and
   * height.
   *
   * @param desiredWidth desired width (in pixels) of the decompressed image.
   * Setting this to 0 is the same as setting it to the width of the JPEG image
   * (in other words, the width will not be considered when determining the
   * scaled image size.)
   *
   * @param desiredHeight desired height (in pixels) of the decompressed image.
   * Setting this to 0 is the same as setting it to the height of the JPEG
   * image (in other words, the height will not be considered when determining
   * the scaled image size.)
   *
   * @return the width of the largest scaled-down image that the TurboJPEG
   * decompressor can generate without exceeding the desired image width and
   * height.
   */
  public int getScaledWidth(int desiredWidth, int desiredHeight) {
    if (jpegWidth < 1 || jpegHeight < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (desiredWidth < 0 || desiredHeight < 0)
      throw new IllegalArgumentException("Invalid argument in getScaledWidth()");
    TJScalingFactor[] sf = TJ.getScalingFactors();
    if (desiredWidth == 0)
      desiredWidth = jpegWidth;
    if (desiredHeight == 0)
      desiredHeight = jpegHeight;
    int scaledWidth = jpegWidth, scaledHeight = jpegHeight;
    for (int i = 0; i < sf.length; i++) {
      scaledWidth = sf[i].getScaled(jpegWidth);
      scaledHeight = sf[i].getScaled(jpegHeight);
      if (scaledWidth <= desiredWidth && scaledHeight <= desiredHeight)
        break;
    }
    if (scaledWidth > desiredWidth || scaledHeight > desiredHeight)
      throw new IllegalArgumentException("Could not scale down to desired image dimensions");
    return scaledWidth;
  }

  /**
   * Returns the height of the largest scaled-down image that the TurboJPEG
   * decompressor can generate without exceeding the desired image width and
   * height.
   *
   * @param desiredWidth desired width (in pixels) of the decompressed image.
   * Setting this to 0 is the same as setting it to the width of the JPEG image
   * (in other words, the width will not be considered when determining the
   * scaled image size.)
   *
   * @param desiredHeight desired height (in pixels) of the decompressed image.
   * Setting this to 0 is the same as setting it to the height of the JPEG
   * image (in other words, the height will not be considered when determining
   * the scaled image size.)
   *
   * @return the height of the largest scaled-down image that the TurboJPEG
   * decompressor can generate without exceeding the desired image width and
   * height.
   */
  public int getScaledHeight(int desiredWidth, int desiredHeight) {
    if (jpegWidth < 1 || jpegHeight < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (desiredWidth < 0 || desiredHeight < 0)
      throw new IllegalArgumentException("Invalid argument in getScaledHeight()");
    TJScalingFactor[] sf = TJ.getScalingFactors();
    if (desiredWidth == 0)
      desiredWidth = jpegWidth;
    if (desiredHeight == 0)
      desiredHeight = jpegHeight;
    int scaledWidth = jpegWidth, scaledHeight = jpegHeight;
    for (int i = 0; i < sf.length; i++) {
      scaledWidth = sf[i].getScaled(jpegWidth);
      scaledHeight = sf[i].getScaled(jpegHeight);
      if (scaledWidth <= desiredWidth && scaledHeight <= desiredHeight)
        break;
    }
    if (scaledWidth > desiredWidth || scaledHeight > desiredHeight)
      throw new IllegalArgumentException("Could not scale down to desired image dimensions");
    return scaledHeight;
  }

  /**
   * Decompress the JPEG source image or decode the YUV source image associated
   * with this decompressor instance and output a grayscale, RGB, or CMYK image
   * to the given destination buffer.
   * <p>
   * NOTE: The output image is fully recoverable if this method throws a
   * non-fatal {@link TJException} (unless
   * {@link TJ#FLAG_STOPONWARNING TJ.FLAG_STOPONWARNING} is specified.)
   *
   * @param dstBuf buffer that will receive the decompressed/decoded image.
   * If the source image is a JPEG image, then this buffer should normally be
   * <code>pitch * scaledHeight</code> bytes in size, where
   * <code>scaledHeight</code> can be determined by calling <code>
   * scalingFactor.{@link TJScalingFactor#getScaled getScaled}(jpegHeight)
   * </code> with one of the scaling factors returned from {@link
   * TJ#getScalingFactors} or by calling {@link #getScaledHeight}.  If the
   * source image is a YUV image, then this buffer should normally be
   * <code>pitch * height</code> bytes in size, where <code>height</code> is
   * the height of the YUV image.  However, the buffer may also be larger than
   * the dimensions of the source image, in which case the <code>x</code>,
   * <code>y</code>, and <code>pitch</code> parameters can be used to specify
   * the region into which the source image should be decompressed/decoded.
   *
   * @param x x offset (in pixels) of the region in the destination image into
   * which the source image should be decompressed/decoded
   *
   * @param y y offset (in pixels) of the region in the destination image into
   * which the source image should be decompressed/decoded
   *
   * @param desiredWidth If the source image is a JPEG image, then this
   * specifies the desired width (in pixels) of the decompressed image (or
   * image region.)  If the desired destination image dimensions are different
   * than the source image dimensions, then TurboJPEG will use scaling in the
   * JPEG decompressor to generate the largest possible image that will fit
   * within the desired dimensions.  Setting this to 0 is the same as setting
   * it to the width of the JPEG image (in other words, the width will not be
   * considered when determining the scaled image size.)  This parameter is
   * ignored if the source image is a YUV image.
   *
   * @param pitch bytes per line of the destination image.  Normally, this
   * should be set to <code>scaledWidth * TJ.pixelSize(pixelFormat)</code> if
   * the destination image is unpadded, but you can use this to, for instance,
   * pad each line of the destination image to a 4-byte boundary or to
   * decompress/decode the source image into a region of a larger image.  NOTE:
   * if the source image is a JPEG image, then <code>scaledWidth</code> can be
   * determined by calling <code>
   * scalingFactor.{@link TJScalingFactor#getScaled getScaled}(jpegWidth)
   * </code> or by calling {@link #getScaledWidth}.  If the source image is a
   * YUV image, then <code>scaledWidth</code> is the width of the YUV image.
   * Setting this parameter to 0 is the equivalent of setting it to
   * <code>scaledWidth * TJ.pixelSize(pixelFormat)</code>.
   *
   * @param desiredHeight If the source image is a JPEG image, then this
   * specifies the desired height (in pixels) of the decompressed image (or
   * image region.)  If the desired destination image dimensions are different
   * than the source image dimensions, then TurboJPEG will use scaling in the
   * JPEG decompressor to generate the largest possible image that will fit
   * within the desired dimensions.  Setting this to 0 is the same as setting
   * it to the height of the JPEG image (in other words, the height will not be
   * considered when determining the scaled image size.)  This parameter is
   * ignored if the source image is a YUV image.
   *
   * @param pixelFormat pixel format of the decompressed/decoded image (one of
   * {@link TJ#PF_RGB TJ.PF_*})
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void decompress(byte[] dstBuf, int x, int y, int desiredWidth,
                         int pitch, int desiredHeight, int pixelFormat,
                         int flags) throws TJException {
    if (jpegBuf == null && yuvImage == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (dstBuf == null || x < 0 || y < 0 || pitch < 0 ||
        (yuvImage != null && (desiredWidth < 0 || desiredHeight < 0)) ||
        pixelFormat < 0 || pixelFormat >= TJ.NUMPF || flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompress()");
    if (yuvImage != null)
      decodeYUV(yuvImage.getPlanes(), yuvImage.getOffsets(),
                yuvImage.getStrides(), yuvImage.getSubsamp(), dstBuf, x, y,
                yuvImage.getWidth(), pitch, yuvImage.getHeight(), pixelFormat,
                flags);
    else {
      if (x > 0 || y > 0)
        decompress(jpegBuf, jpegBufSize, dstBuf, x, y, desiredWidth, pitch,
                   desiredHeight, pixelFormat, flags);
      else
        decompress(jpegBuf, jpegBufSize, dstBuf, desiredWidth, pitch,
                   desiredHeight, pixelFormat, flags);
    }
  }

  /**
   * @deprecated Use
   * {@link #decompress(byte[], int, int, int, int, int, int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void decompress(byte[] dstBuf, int desiredWidth, int pitch,
                         int desiredHeight, int pixelFormat, int flags)
                         throws TJException {
    decompress(dstBuf, 0, 0, desiredWidth, pitch, desiredHeight, pixelFormat,
               flags);
  }

  /**
   * Decompress the JPEG source image associated with this decompressor
   * instance and return a buffer containing the decompressed image.
   *
   * @param desiredWidth see
   * {@link #decompress(byte[], int, int, int, int, int, int, int)}
   * for description
   *
   * @param pitch see
   * {@link #decompress(byte[], int, int, int, int, int, int, int)}
   * for description
   *
   * @param desiredHeight see
   * {@link #decompress(byte[], int, int, int, int, int, int, int)}
   * for description
   *
   * @param pixelFormat pixel format of the decompressed image (one of
   * {@link TJ#PF_RGB TJ.PF_*})
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a buffer containing the decompressed image.
   */
  public byte[] decompress(int desiredWidth, int pitch, int desiredHeight,
                           int pixelFormat, int flags) throws TJException {
    if (pitch < 0 ||
        (yuvImage == null && (desiredWidth < 0 || desiredHeight < 0)) ||
        pixelFormat < 0 || pixelFormat >= TJ.NUMPF || flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompress()");
    int pixelSize = TJ.getPixelSize(pixelFormat);
    int scaledWidth = getScaledWidth(desiredWidth, desiredHeight);
    int scaledHeight = getScaledHeight(desiredWidth, desiredHeight);
    if (pitch == 0)
      pitch = scaledWidth * pixelSize;
    byte[] buf = new byte[pitch * scaledHeight];
    decompress(buf, desiredWidth, pitch, desiredHeight, pixelFormat, flags);
    return buf;
  }

  /**
   * Decompress the JPEG source image associated with this decompressor
   * instance into a YUV planar image and store it in the given
   * <code>YUVImage</code> instance.  This method performs JPEG decompression
   * but leaves out the color conversion step, so a planar YUV image is
   * generated instead of an RGB or grayscale image.  This method cannot be
   * used to decompress JPEG source images with the CMYK or YCCK colorspace.
   * <p>
   * NOTE: The YUV planar output image is fully recoverable if this method
   * throws a non-fatal {@link TJException} (unless
   * {@link TJ#FLAG_STOPONWARNING TJ.FLAG_STOPONWARNING} is specified.)
   *
   * @param dstImage {@link YUVImage} instance that will receive the YUV planar
   * image.  The level of subsampling specified in this <code>YUVImage</code>
   * instance must match that of the JPEG image, and the width and height
   * specified in the <code>YUVImage</code> instance must match one of the
   * scaled image sizes that TurboJPEG is capable of generating from the JPEG
   * source image.
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void decompressToYUV(YUVImage dstImage, int flags)
                              throws TJException {
    if (jpegBuf == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (dstImage == null || flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompressToYUV()");
    int scaledWidth = getScaledWidth(dstImage.getWidth(),
                                     dstImage.getHeight());
    int scaledHeight = getScaledHeight(dstImage.getWidth(),
                                       dstImage.getHeight());
    if (scaledWidth != dstImage.getWidth() ||
        scaledHeight != dstImage.getHeight())
      throw new IllegalArgumentException("YUVImage dimensions do not match one of the scaled image sizes that TurboJPEG is capable of generating.");
    if (jpegSubsamp != dstImage.getSubsamp())
      throw new IllegalArgumentException("YUVImage subsampling level does not match that of the JPEG image");

    decompressToYUV(jpegBuf, jpegBufSize, dstImage.getPlanes(),
                    dstImage.getOffsets(), dstImage.getWidth(),
                    dstImage.getStrides(), dstImage.getHeight(), flags);
  }

  /**
   * @deprecated Use {@link #decompressToYUV(YUVImage, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void decompressToYUV(byte[] dstBuf, int flags) throws TJException {
    YUVImage dstYUVImage = new YUVImage(dstBuf, jpegWidth, 4, jpegHeight,
                                        jpegSubsamp);
    decompressToYUV(dstYUVImage, flags);
  }

  /**
   * Decompress the JPEG source image associated with this decompressor
   * instance into a set of Y, U (Cb), and V (Cr) image planes and return a
   * <code>YUVImage</code> instance containing the decompressed image planes.
   * This method performs JPEG decompression but leaves out the color
   * conversion step, so a planar YUV image is generated instead of an RGB or
   * grayscale image.  This method cannot be used to decompress JPEG source
   * images with the CMYK or YCCK colorspace.
   *
   * @param desiredWidth desired width (in pixels) of the YUV image.  If the
   * desired image dimensions are different than the dimensions of the JPEG
   * image being decompressed, then TurboJPEG will use scaling in the JPEG
   * decompressor to generate the largest possible image that will fit within
   * the desired dimensions.  Setting this to 0 is the same as setting it to
   * the width of the JPEG image (in other words, the width will not be
   * considered when determining the scaled image size.)
   *
   * @param strides an array of integers, each specifying the number of bytes
   * per line in the corresponding plane of the output image.  Setting the
   * stride for any plane to 0 is the same as setting it to the scaled
   * component width of the plane.  If <tt>strides</tt> is NULL, then the
   * strides for all planes will be set to their respective scaled component
   * widths.  You can adjust the strides in order to add an arbitrary amount of
   * line padding to each plane.
   *
   * @param desiredHeight desired height (in pixels) of the YUV image.  If the
   * desired image dimensions are different than the dimensions of the JPEG
   * image being decompressed, then TurboJPEG will use scaling in the JPEG
   * decompressor to generate the largest possible image that will fit within
   * the desired dimensions.  Setting this to 0 is the same as setting it to
   * the height of the JPEG image (in other words, the height will not be
   * considered when determining the scaled image size.)
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a YUV planar image.
   */
  public YUVImage decompressToYUV(int desiredWidth, int[] strides,
                                  int desiredHeight,
                                  int flags) throws TJException {
    if (flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompressToYUV()");
    if (jpegWidth < 1 || jpegHeight < 1 || jpegSubsamp < 0)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (jpegSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException("JPEG header information is invalid");
    if (yuvImage != null)
      throw new IllegalStateException("Source image is the wrong type");

    int scaledWidth = getScaledWidth(desiredWidth, desiredHeight);
    int scaledHeight = getScaledHeight(desiredWidth, desiredHeight);
    YUVImage dstYUVImage = new YUVImage(scaledWidth, null, scaledHeight,
                                        jpegSubsamp);
    decompressToYUV(dstYUVImage, flags);
    return dstYUVImage;
  }

  /**
   * Decompress the JPEG source image associated with this decompressor
   * instance into a unified YUV planar image buffer and return a
   * <code>YUVImage</code> instance containing the decompressed image.  This
   * method performs JPEG decompression but leaves out the color conversion
   * step, so a planar YUV image is generated instead of an RGB or grayscale
   * image.  This method cannot be used to decompress JPEG source images with
   * the CMYK or YCCK colorspace.
   *
   * @param desiredWidth desired width (in pixels) of the YUV image.  If the
   * desired image dimensions are different than the dimensions of the JPEG
   * image being decompressed, then TurboJPEG will use scaling in the JPEG
   * decompressor to generate the largest possible image that will fit within
   * the desired dimensions.  Setting this to 0 is the same as setting it to
   * the width of the JPEG image (in other words, the width will not be
   * considered when determining the scaled image size.)
   *
   * @param pad the width of each line in each plane of the YUV image will be
   * padded to the nearest multiple of this number of bytes (must be a power of
   * 2.)
   *
   * @param desiredHeight desired height (in pixels) of the YUV image.  If the
   * desired image dimensions are different than the dimensions of the JPEG
   * image being decompressed, then TurboJPEG will use scaling in the JPEG
   * decompressor to generate the largest possible image that will fit within
   * the desired dimensions.  Setting this to 0 is the same as setting it to
   * the height of the JPEG image (in other words, the height will not be
   * considered when determining the scaled image size.)
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a YUV planar image.
   */
  public YUVImage decompressToYUV(int desiredWidth, int pad, int desiredHeight,
                                  int flags) throws TJException {
    if (flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompressToYUV()");
    if (jpegWidth < 1 || jpegHeight < 1 || jpegSubsamp < 0)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (jpegSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException("JPEG header information is invalid");
    if (yuvImage != null)
      throw new IllegalStateException("Source image is the wrong type");

    int scaledWidth = getScaledWidth(desiredWidth, desiredHeight);
    int scaledHeight = getScaledHeight(desiredWidth, desiredHeight);
    YUVImage dstYUVImage = new YUVImage(scaledWidth, pad, scaledHeight,
                                        jpegSubsamp);
    decompressToYUV(dstYUVImage, flags);
    return dstYUVImage;
  }

  /**
   * @deprecated Use {@link #decompressToYUV(int, int, int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public byte[] decompressToYUV(int flags) throws TJException {
    YUVImage dstYUVImage = new YUVImage(jpegWidth, 4, jpegHeight, jpegSubsamp);
    decompressToYUV(dstYUVImage, flags);
    return dstYUVImage.getBuf();
  }

  /**
   * Decompress the JPEG source image or decode the YUV source image associated
   * with this decompressor instance and output a grayscale, RGB, or CMYK image
   * to the given destination buffer.
   * <p>
   * NOTE: The output image is fully recoverable if this method throws a
   * non-fatal {@link TJException} (unless
   * {@link TJ#FLAG_STOPONWARNING TJ.FLAG_STOPONWARNING} is specified.)
   *
   * @param dstBuf buffer that will receive the decompressed/decoded image.
   * If the source image is a JPEG image, then this buffer should normally be
   * <code>stride * scaledHeight</code> pixels in size, where
   * <code>scaledHeight</code> can be determined by calling <code>
   * scalingFactor.{@link TJScalingFactor#getScaled getScaled}(jpegHeight)
   * </code> with one of the scaling factors returned from {@link
   * TJ#getScalingFactors} or by calling {@link #getScaledHeight}.  If the
   * source image is a YUV image, then this buffer should normally be
   * <code>stride * height</code> pixels in size, where <code>height</code> is
   * the height of the YUV image.  However, the buffer may also be larger than
   * the dimensions of the JPEG image, in which case the <code>x</code>,
   * <code>y</code>, and <code>stride</code> parameters can be used to specify
   * the region into which the source image should be decompressed.
   *
   * @param x x offset (in pixels) of the region in the destination image into
   * which the source image should be decompressed/decoded
   *
   * @param y y offset (in pixels) of the region in the destination image into
   * which the source image should be decompressed/decoded
   *
   * @param desiredWidth If the source image is a JPEG image, then this
   * specifies the desired width (in pixels) of the decompressed image (or
   * image region.)  If the desired destination image dimensions are different
   * than the source image dimensions, then TurboJPEG will use scaling in the
   * JPEG decompressor to generate the largest possible image that will fit
   * within the desired dimensions.  Setting this to 0 is the same as setting
   * it to the width of the JPEG image (in other words, the width will not be
   * considered when determining the scaled image size.)  This parameter is
   * ignored if the source image is a YUV image.
   *
   * @param stride pixels per line of the destination image.  Normally, this
   * should be set to <code>scaledWidth</code>, but you can use this to, for
   * instance, decompress the JPEG image into a region of a larger image.
   * NOTE: if the source image is a JPEG image, then <code>scaledWidth</code>
   * can be determined by calling <code>
   * scalingFactor.{@link TJScalingFactor#getScaled getScaled}(jpegWidth)
   * </code> or by calling {@link #getScaledWidth}.  If the source image is a
   * YUV image, then <code>scaledWidth</code> is the width of the YUV image.
   * Setting this parameter to 0 is the equivalent of setting it to
   * <code>scaledWidth</code>.
   *
   * @param desiredHeight If the source image is a JPEG image, then this
   * specifies the desired height (in pixels) of the decompressed image (or
   * image region.)  If the desired destination image dimensions are different
   * than the source image dimensions, then TurboJPEG will use scaling in the
   * JPEG decompressor to generate the largest possible image that will fit
   * within the desired dimensions.  Setting this to 0 is the same as setting
   * it to the height of the JPEG image (in other words, the height will not be
   * considered when determining the scaled image size.)  This parameter is
   * ignored if the source image is a YUV image.
   *
   * @param pixelFormat pixel format of the decompressed image (one of
   * {@link TJ#PF_RGB TJ.PF_*})
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void decompress(int[] dstBuf, int x, int y, int desiredWidth,
                         int stride, int desiredHeight, int pixelFormat,
                         int flags) throws TJException {
    if (jpegBuf == null && yuvImage == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (dstBuf == null || x < 0 || y < 0 || stride < 0 ||
        (yuvImage != null && (desiredWidth < 0 || desiredHeight < 0)) ||
        pixelFormat < 0 || pixelFormat >= TJ.NUMPF || flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompress()");
    if (yuvImage != null)
      decodeYUV(yuvImage.getPlanes(), yuvImage.getOffsets(),
                yuvImage.getStrides(), yuvImage.getSubsamp(), dstBuf, x, y,
                yuvImage.getWidth(), stride, yuvImage.getHeight(), pixelFormat,
                flags);
    else
      decompress(jpegBuf, jpegBufSize, dstBuf, x, y, desiredWidth, stride,
                 desiredHeight, pixelFormat, flags);
  }

  /**
   * Decompress the JPEG source image or decode the YUV source image associated
   * with this decompressor instance and output a decompressed/decoded image to
   * the given <code>BufferedImage</code> instance.
   * <p>
   * NOTE: The output image is fully recoverable if this method throws a
   * non-fatal {@link TJException} (unless
   * {@link TJ#FLAG_STOPONWARNING TJ.FLAG_STOPONWARNING} is specified.)
   *
   * @param dstImage a <code>BufferedImage</code> instance that will receive
   * the decompressed/decoded image.  If the source image is a JPEG image, then
   * the width and height of the <code>BufferedImage</code> instance must match
   * one of the scaled image sizes that TurboJPEG is capable of generating from
   * the JPEG image.  If the source image is a YUV image, then the width and
   * height of the <code>BufferedImage</code> instance must match the width and
   * height of the YUV image.
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void decompress(BufferedImage dstImage, int flags)
                         throws TJException {
    if (dstImage == null || flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompress()");
    int desiredWidth = dstImage.getWidth();
    int desiredHeight = dstImage.getHeight();
    int scaledWidth, scaledHeight;

    if (yuvImage != null) {
      if (desiredWidth != yuvImage.getWidth() ||
          desiredHeight != yuvImage.getHeight())
        throw new IllegalArgumentException("BufferedImage dimensions do not match the dimensions of the source image.");
      scaledWidth = yuvImage.getWidth();
      scaledHeight = yuvImage.getHeight();
    } else {
      scaledWidth = getScaledWidth(desiredWidth, desiredHeight);
      scaledHeight = getScaledHeight(desiredWidth, desiredHeight);
      if (scaledWidth != desiredWidth || scaledHeight != desiredHeight)
        throw new IllegalArgumentException("BufferedImage dimensions do not match one of the scaled image sizes that TurboJPEG is capable of generating.");
    }
    int pixelFormat;  boolean intPixels = false;
    if (byteOrder == null)
      byteOrder = ByteOrder.nativeOrder();
    switch (dstImage.getType()) {
    case BufferedImage.TYPE_3BYTE_BGR:
      pixelFormat = TJ.PF_BGR;  break;
    case BufferedImage.TYPE_4BYTE_ABGR:
    case BufferedImage.TYPE_4BYTE_ABGR_PRE:
      pixelFormat = TJ.PF_XBGR;  break;
    case BufferedImage.TYPE_BYTE_GRAY:
      pixelFormat = TJ.PF_GRAY;  break;
    case BufferedImage.TYPE_INT_BGR:
      if (byteOrder == ByteOrder.BIG_ENDIAN)
        pixelFormat = TJ.PF_XBGR;
      else
        pixelFormat = TJ.PF_RGBX;
      intPixels = true;  break;
    case BufferedImage.TYPE_INT_RGB:
      if (byteOrder == ByteOrder.BIG_ENDIAN)
        pixelFormat = TJ.PF_XRGB;
      else
        pixelFormat = TJ.PF_BGRX;
      intPixels = true;  break;
    case BufferedImage.TYPE_INT_ARGB:
    case BufferedImage.TYPE_INT_ARGB_PRE:
      if (byteOrder == ByteOrder.BIG_ENDIAN)
        pixelFormat = TJ.PF_ARGB;
      else
        pixelFormat = TJ.PF_BGRA;
      intPixels = true;  break;
    default:
      throw new IllegalArgumentException("Unsupported BufferedImage format");
    }
    WritableRaster wr = dstImage.getRaster();
    if (intPixels) {
      SinglePixelPackedSampleModel sm =
        (SinglePixelPackedSampleModel)dstImage.getSampleModel();
      int stride = sm.getScanlineStride();
      DataBufferInt db = (DataBufferInt)wr.getDataBuffer();
      int[] buf = db.getData();
      if (yuvImage != null)
        decodeYUV(yuvImage.getPlanes(), yuvImage.getOffsets(),
                  yuvImage.getStrides(), yuvImage.getSubsamp(), buf, 0, 0,
                  yuvImage.getWidth(), stride, yuvImage.getHeight(),
                  pixelFormat, flags);
      else {
        if (jpegBuf == null)
          throw new IllegalStateException(NO_ASSOC_ERROR);
        decompress(jpegBuf, jpegBufSize, buf, 0, 0, scaledWidth, stride,
                   scaledHeight, pixelFormat, flags);
      }
    } else {
      ComponentSampleModel sm =
        (ComponentSampleModel)dstImage.getSampleModel();
      int pixelSize = sm.getPixelStride();
      if (pixelSize != TJ.getPixelSize(pixelFormat))
        throw new IllegalArgumentException("Inconsistency between pixel format and pixel size in BufferedImage");
      int pitch = sm.getScanlineStride();
      DataBufferByte db = (DataBufferByte)wr.getDataBuffer();
      byte[] buf = db.getData();
      decompress(buf, 0, 0, scaledWidth, pitch, scaledHeight, pixelFormat,
                 flags);
    }
  }

  /**
   * Decompress the JPEG source image or decode the YUV source image associated
   * with this decompressor instance and return a <code>BufferedImage</code>
   * instance containing the decompressed/decoded image.
   *
   * @param desiredWidth see
   * {@link #decompress(byte[], int, int, int, int, int, int, int)} for
   * description
   *
   * @param desiredHeight see
   * {@link #decompress(byte[], int, int, int, int, int, int, int)} for
   * description
   *
   * @param bufferedImageType the image type of the <code>BufferedImage</code>
   * instance that will be created (for instance,
   * <code>BufferedImage.TYPE_INT_RGB</code>)
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a <code>BufferedImage</code> instance containing the
   * decompressed/decoded image.
   */
  public BufferedImage decompress(int desiredWidth, int desiredHeight,
                                  int bufferedImageType, int flags)
                                  throws TJException {
    if ((yuvImage == null && (desiredWidth < 0 || desiredHeight < 0)) ||
        flags < 0)
      throw new IllegalArgumentException("Invalid argument in decompress()");
    int scaledWidth = getScaledWidth(desiredWidth, desiredHeight);
    int scaledHeight = getScaledHeight(desiredWidth, desiredHeight);
    BufferedImage img = new BufferedImage(scaledWidth, scaledHeight,
                                          bufferedImageType);
    decompress(img, flags);
    return img;
  }

  /**
   * Free the native structures associated with this decompressor instance.
   */
  @Override
  public void close() throws TJException {
    if (handle != 0)
      destroy();
  }

  @SuppressWarnings("checkstyle:DesignForExtension")
  @Override
  protected void finalize() throws Throwable {
    try {
      close();
    } catch (TJException e) {
    } finally {
      super.finalize();
    }
  };

  private native void init() throws TJException;

  private native void destroy() throws TJException;

  private native void decompressHeader(byte[] srcBuf, int size)
    throws TJException;

  @Deprecated
  private native void decompress(byte[] srcBuf, int size, byte[] dstBuf,
    int desiredWidth, int pitch, int desiredHeight, int pixelFormat, int flags)
    throws TJException;

  private native void decompress(byte[] srcBuf, int size, byte[] dstBuf, int x,
    int y, int desiredWidth, int pitch, int desiredHeight, int pixelFormat,
    int flags) throws TJException;

  @Deprecated
  private native void decompress(byte[] srcBuf, int size, int[] dstBuf,
    int desiredWidth, int stride, int desiredHeight, int pixelFormat,
    int flags) throws TJException;

  private native void decompress(byte[] srcBuf, int size, int[] dstBuf, int x,
    int y, int desiredWidth, int stride, int desiredHeight, int pixelFormat,
    int flags) throws TJException;

  @Deprecated
  private native void decompressToYUV(byte[] srcBuf, int size, byte[] dstBuf,
    int flags) throws TJException;

  private native void decompressToYUV(byte[] srcBuf, int size,
    byte[][] dstPlanes, int[] dstOffsets, int desiredWidth, int[] dstStrides,
    int desiredheight, int flags) throws TJException;

  private native void decodeYUV(byte[][] srcPlanes, int[] srcOffsets,
    int[] srcStrides, int subsamp, byte[] dstBuf, int x, int y, int width,
    int pitch, int height, int pixelFormat, int flags) throws TJException;

  private native void decodeYUV(byte[][] srcPlanes, int[] srcOffsets,
    int[] srcStrides, int subsamp, int[] dstBuf, int x, int y, int width,
    int stride, int height, int pixelFormat, int flags) throws TJException;

  static {
    TJLoader.load();
  }

  protected long handle = 0;
  protected byte[] jpegBuf = null;
  protected int jpegBufSize = 0;
  protected YUVImage yuvImage = null;
  protected int jpegWidth = 0;
  protected int jpegHeight = 0;
  protected int jpegSubsamp = -1;
  protected int jpegColorspace = -1;
  private ByteOrder byteOrder = null;
}

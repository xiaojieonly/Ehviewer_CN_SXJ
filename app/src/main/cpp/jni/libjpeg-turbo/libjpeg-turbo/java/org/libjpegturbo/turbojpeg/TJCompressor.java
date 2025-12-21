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
 * TurboJPEG compressor
 */
public class TJCompressor implements Closeable {

  private static final String NO_ASSOC_ERROR =
    "No source image is associated with this instance";

  /**
   * Create a TurboJPEG compressor instance.
   */
  public TJCompressor() throws TJException {
    init();
  }

  /**
   * Create a TurboJPEG compressor instance and associate the uncompressed
   * source image stored in <code>srcImage</code> with the newly created
   * instance.
   *
   * @param srcImage see {@link #setSourceImage} for description
   *
   * @param x see {@link #setSourceImage} for description
   *
   * @param y see {@link #setSourceImage} for description
   *
   * @param width see {@link #setSourceImage} for description
   *
   * @param pitch see {@link #setSourceImage} for description
   *
   * @param height see {@link #setSourceImage} for description
   *
   * @param pixelFormat pixel format of the source image (one of
   * {@link TJ#PF_RGB TJ.PF_*})
   */
  public TJCompressor(byte[] srcImage, int x, int y, int width, int pitch,
                      int height, int pixelFormat) throws TJException {
    setSourceImage(srcImage, x, y, width, pitch, height, pixelFormat);
  }

  /**
   * @deprecated Use
   * {@link #TJCompressor(byte[], int, int, int, int, int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public TJCompressor(byte[] srcImage, int width, int pitch, int height,
                      int pixelFormat) throws TJException {
    setSourceImage(srcImage, width, pitch, height, pixelFormat);
  }

  /**
   * Create a TurboJPEG compressor instance and associate the uncompressed
   * source image stored in <code>srcImage</code> with the newly created
   * instance.
   *
   * @param srcImage see
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} for description
   *
   * @param x see
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} for description
   *
   * @param y see
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} for description
   *
   * @param width see
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} for description
   *
   * @param height see
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} for description
   */
  public TJCompressor(BufferedImage srcImage, int x, int y, int width,
                      int height) throws TJException {
    setSourceImage(srcImage, x, y, width, height);
  }

  /**
   * Associate an uncompressed RGB, grayscale, or CMYK source image with this
   * compressor instance.
   *
   * @param srcImage image buffer containing RGB, grayscale, or CMYK pixels to
   * be compressed or encoded.  This buffer is not modified.
   *
   * @param x x offset (in pixels) of the region in the source image from which
   * the JPEG or YUV image should be compressed/encoded
   *
   * @param y y offset (in pixels) of the region in the source image from which
   * the JPEG or YUV image should be compressed/encoded
   *
   * @param width width (in pixels) of the region in the source image from
   * which the JPEG or YUV image should be compressed/encoded
   *
   * @param pitch bytes per line of the source image.  Normally, this should be
   * <code>width * TJ.pixelSize(pixelFormat)</code> if the source image is
   * unpadded, but you can use this parameter to, for instance, specify that
   * the scanlines in the source image are padded to a 4-byte boundary or to
   * compress/encode a JPEG or YUV image from a region of a larger source
   * image.  You can also be clever and use this parameter to skip lines, etc.
   * Setting this parameter to 0 is the equivalent of setting it to
   * <code>width * TJ.pixelSize(pixelFormat)</code>.
   *
   * @param height height (in pixels) of the region in the source image from
   * which the JPEG or YUV image should be compressed/encoded
   *
   * @param pixelFormat pixel format of the source image (one of
   * {@link TJ#PF_RGB TJ.PF_*})
   */
  public void setSourceImage(byte[] srcImage, int x, int y, int width,
                             int pitch, int height, int pixelFormat)
                             throws TJException {
    if (handle == 0) init();
    if (srcImage == null || x < 0 || y < 0 || width < 1 || height < 1 ||
        pitch < 0 || pixelFormat < 0 || pixelFormat >= TJ.NUMPF)
      throw new IllegalArgumentException("Invalid argument in setSourceImage()");
    srcBuf = srcImage;
    srcWidth = width;
    if (pitch == 0)
      srcPitch = width * TJ.getPixelSize(pixelFormat);
    else
      srcPitch = pitch;
    srcHeight = height;
    srcPixelFormat = pixelFormat;
    srcX = x;
    srcY = y;
    srcBufInt = null;
    srcYUVImage = null;
  }

  /**
   * @deprecated Use
   * {@link #setSourceImage(byte[], int, int, int, int, int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void setSourceImage(byte[] srcImage, int width, int pitch,
                             int height, int pixelFormat) throws TJException {
    setSourceImage(srcImage, 0, 0, width, pitch, height, pixelFormat);
    srcX = srcY = -1;
  }

  /**
   * Associate an uncompressed RGB or grayscale source image with this
   * compressor instance.
   *
   * @param srcImage a <code>BufferedImage</code> instance containing RGB or
   * grayscale pixels to be compressed or encoded.  This image is not modified.
   *
   * @param x x offset (in pixels) of the region in the source image from which
   * the JPEG or YUV image should be compressed/encoded
   *
   * @param y y offset (in pixels) of the region in the source image from which
   * the JPEG or YUV image should be compressed/encoded
   *
   * @param width width (in pixels) of the region in the source image from
   * which the JPEG or YUV image should be compressed/encoded (0 = use the
   * width of the source image)
   *
   * @param height height (in pixels) of the region in the source image from
   * which the JPEG or YUV image should be compressed/encoded (0 = use the
   * height of the source image)
   */
  public void setSourceImage(BufferedImage srcImage, int x, int y, int width,
                             int height) throws TJException {
    if (handle == 0) init();
    if (srcImage == null || x < 0 || y < 0 || width < 0 || height < 0)
      throw new IllegalArgumentException("Invalid argument in setSourceImage()");
    srcX = x;
    srcY = y;
    srcWidth = (width == 0) ? srcImage.getWidth() : width;
    srcHeight = (height == 0) ? srcImage.getHeight() : height;
    if (x + width > srcImage.getWidth() || y + height > srcImage.getHeight())
      throw new IllegalArgumentException("Compression region exceeds the bounds of the source image");

    int pixelFormat;
    boolean intPixels = false;
    if (byteOrder == null)
      byteOrder = ByteOrder.nativeOrder();
    switch (srcImage.getType()) {
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
    case BufferedImage.TYPE_INT_ARGB:
    case BufferedImage.TYPE_INT_ARGB_PRE:
      if (byteOrder == ByteOrder.BIG_ENDIAN)
        pixelFormat = TJ.PF_XRGB;
      else
        pixelFormat = TJ.PF_BGRX;
      intPixels = true;  break;
    default:
      throw new IllegalArgumentException("Unsupported BufferedImage format");
    }
    srcPixelFormat = pixelFormat;

    WritableRaster wr = srcImage.getRaster();
    if (intPixels) {
      SinglePixelPackedSampleModel sm =
        (SinglePixelPackedSampleModel)srcImage.getSampleModel();
      srcStride = sm.getScanlineStride();
      DataBufferInt db = (DataBufferInt)wr.getDataBuffer();
      srcBufInt = db.getData();
      srcBuf = null;
    } else {
      ComponentSampleModel sm =
        (ComponentSampleModel)srcImage.getSampleModel();
      int pixelSize = sm.getPixelStride();
      if (pixelSize != TJ.getPixelSize(pixelFormat))
        throw new IllegalArgumentException("Inconsistency between pixel format and pixel size in BufferedImage");
      srcPitch = sm.getScanlineStride();
      DataBufferByte db = (DataBufferByte)wr.getDataBuffer();
      srcBuf = db.getData();
      srcBufInt = null;
    }
    srcYUVImage = null;
  }

  /**
   * Associate an uncompressed YUV planar source image with this compressor
   * instance.
   *
   * @param srcImage YUV planar image to be compressed.  This image is not
   * modified.
   */
  public void setSourceImage(YUVImage srcImage) throws TJException {
    if (handle == 0) init();
    if (srcImage == null)
      throw new IllegalArgumentException("Invalid argument in setSourceImage()");
    srcYUVImage = srcImage;
    srcBuf = null;
    srcBufInt = null;
  }

  /**
   * Set the level of chrominance subsampling for subsequent compress/encode
   * operations.  When pixels are converted from RGB to YCbCr (see
   * {@link TJ#CS_YCbCr}) or from CMYK to YCCK (see {@link TJ#CS_YCCK}) as part
   * of the JPEG compression process, some of the Cb and Cr (chrominance)
   * components can be discarded or averaged together to produce a smaller
   * image with little perceptible loss of image clarity (the human eye is more
   * sensitive to small changes in brightness than to small changes in color.)
   * This is called "chrominance subsampling".
   * <p>
   * NOTE: This method has no effect when compressing a JPEG image from a YUV
   * planar source.  In that case, the level of chrominance subsampling in
   * the JPEG image is determined by the source.  Furthermore, this method has
   * no effect when encoding to a pre-allocated {@link YUVImage} instance.  In
   * that case, the level of chrominance subsampling is determined by the
   * destination.
   *
   * @param newSubsamp the level of chrominance subsampling to use in
   * subsequent compress/encode oeprations (one of
   * {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public void setSubsamp(int newSubsamp) {
    if (newSubsamp < 0 || newSubsamp >= TJ.NUMSAMP)
      throw new IllegalArgumentException("Invalid argument in setSubsamp()");
    subsamp = newSubsamp;
  }

  /**
   * Set the JPEG image quality level for subsequent compress operations.
   *
   * @param quality the new JPEG image quality level (1 to 100, 1 = worst,
   * 100 = best)
   */
  public void setJPEGQuality(int quality) {
    if (quality < 1 || quality > 100)
      throw new IllegalArgumentException("Invalid argument in setJPEGQuality()");
    jpegQuality = quality;
  }

  /**
   * Compress the uncompressed source image associated with this compressor
   * instance and output a JPEG image to the given destination buffer.
   *
   * @param dstBuf buffer that will receive the JPEG image.  Use
   * {@link TJ#bufSize} to determine the maximum size for this buffer based on
   * the source image's width and height and the desired level of chrominance
   * subsampling.
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void compress(byte[] dstBuf, int flags) throws TJException {
    if (dstBuf == null || flags < 0)
      throw new IllegalArgumentException("Invalid argument in compress()");
    if (srcBuf == null && srcBufInt == null && srcYUVImage == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (jpegQuality < 0)
      throw new IllegalStateException("JPEG Quality not set");
    if (subsamp < 0 && srcYUVImage == null)
      throw new IllegalStateException("Subsampling level not set");

    if (srcYUVImage != null)
      compressedSize = compressFromYUV(srcYUVImage.getPlanes(),
                                       srcYUVImage.getOffsets(),
                                       srcYUVImage.getWidth(),
                                       srcYUVImage.getStrides(),
                                       srcYUVImage.getHeight(),
                                       srcYUVImage.getSubsamp(),
                                       dstBuf, jpegQuality, flags);
    else if (srcBuf != null) {
      if (srcX >= 0 && srcY >= 0)
        compressedSize = compress(srcBuf, srcX, srcY, srcWidth, srcPitch,
                                  srcHeight, srcPixelFormat, dstBuf, subsamp,
                                  jpegQuality, flags);
      else
        compressedSize = compress(srcBuf, srcWidth, srcPitch, srcHeight,
                                  srcPixelFormat, dstBuf, subsamp, jpegQuality,
                                  flags);
    } else if (srcBufInt != null) {
      if (srcX >= 0 && srcY >= 0)
        compressedSize = compress(srcBufInt, srcX, srcY, srcWidth, srcStride,
                                  srcHeight, srcPixelFormat, dstBuf, subsamp,
                                  jpegQuality, flags);
      else
        compressedSize = compress(srcBufInt, srcWidth, srcStride, srcHeight,
                                  srcPixelFormat, dstBuf, subsamp, jpegQuality,
                                  flags);
    }
  }

  /**
   * Compress the uncompressed source image associated with this compressor
   * instance and return a buffer containing a JPEG image.
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a buffer containing a JPEG image.  The length of this buffer will
   * not be equal to the size of the JPEG image.  Use {@link
   * #getCompressedSize} to obtain the size of the JPEG image.
   */
  public byte[] compress(int flags) throws TJException {
    checkSourceImage();
    byte[] buf = new byte[TJ.bufSize(srcWidth, srcHeight, subsamp)];
    compress(buf, flags);
    return buf;
  }

  /**
   * @deprecated Use
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} and
   * {@link #compress(byte[], int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void compress(BufferedImage srcImage, byte[] dstBuf, int flags)
                       throws TJException {
    setSourceImage(srcImage, 0, 0, 0, 0);
    compress(dstBuf, flags);
  }

  /**
   * @deprecated Use
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} and
   * {@link #compress(int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public byte[] compress(BufferedImage srcImage, int flags)
                         throws TJException {
    setSourceImage(srcImage, 0, 0, 0, 0);
    return compress(flags);
  }

  /**
   * Encode the uncompressed source image associated with this compressor
   * instance into a YUV planar image and store it in the given
   * <code>YUVImage</code> instance.   This method uses the accelerated color
   * conversion routines in TurboJPEG's underlying codec but does not execute
   * any of the other steps in the JPEG compression process.  Encoding
   * CMYK source images to YUV is not supported.
   *
   * @param dstImage {@link YUVImage} instance that will receive the YUV planar
   * image
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void encodeYUV(YUVImage dstImage, int flags) throws TJException {
    if (dstImage == null || flags < 0)
      throw new IllegalArgumentException("Invalid argument in encodeYUV()");
    if (srcBuf == null && srcBufInt == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (srcYUVImage != null)
      throw new IllegalStateException("Source image is not correct type");
    checkSubsampling();
    if (srcWidth != dstImage.getWidth() || srcHeight != dstImage.getHeight())
      throw new IllegalStateException("Destination image is the wrong size");

    if (srcBufInt != null) {
      encodeYUV(srcBufInt, srcX, srcY, srcWidth, srcStride, srcHeight,
                srcPixelFormat, dstImage.getPlanes(), dstImage.getOffsets(),
                dstImage.getStrides(), dstImage.getSubsamp(), flags);
    } else {
      encodeYUV(srcBuf, srcX, srcY, srcWidth, srcPitch, srcHeight,
                srcPixelFormat, dstImage.getPlanes(), dstImage.getOffsets(),
                dstImage.getStrides(), dstImage.getSubsamp(), flags);
    }
    compressedSize = 0;
  }

  /**
   * @deprecated Use {@link #encodeYUV(YUVImage, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void encodeYUV(byte[] dstBuf, int flags) throws TJException {
    if (dstBuf == null)
      throw new IllegalArgumentException("Invalid argument in encodeYUV()");
    checkSourceImage();
    checkSubsampling();
    YUVImage dstYUVImage = new YUVImage(dstBuf, srcWidth, 4, srcHeight,
                                        subsamp);
    encodeYUV(dstYUVImage, flags);
  }

  /**
   * Encode the uncompressed source image associated with this compressor
   * instance into a unified YUV planar image buffer and return a
   * <code>YUVImage</code> instance containing the encoded image.  This method
   * uses the accelerated color conversion routines in TurboJPEG's underlying
   * codec but does not execute any of the other steps in the JPEG compression
   * process.  Encoding CMYK source images to YUV is not supported.
   *
   * @param pad the width of each line in each plane of the YUV image will be
   * padded to the nearest multiple of this number of bytes (must be a power of
   * 2.)
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a YUV planar image.
   */
  public YUVImage encodeYUV(int pad, int flags) throws TJException {
    checkSourceImage();
    checkSubsampling();
    if (pad < 1 || ((pad & (pad - 1)) != 0))
      throw new IllegalStateException("Invalid argument in encodeYUV()");
    YUVImage dstYUVImage = new YUVImage(srcWidth, pad, srcHeight, subsamp);
    encodeYUV(dstYUVImage, flags);
    return dstYUVImage;
  }

  /**
   * Encode the uncompressed source image associated with this compressor
   * instance into separate Y, U (Cb), and V (Cr) image planes and return a
   * <code>YUVImage</code> instance containing the encoded image planes.  This
   * method uses the accelerated color conversion routines in TurboJPEG's
   * underlying codec but does not execute any of the other steps in the JPEG
   * compression process.  Encoding CMYK source images to YUV is not supported.
   *
   * @param strides an array of integers, each specifying the number of bytes
   * per line in the corresponding plane of the output image.  Setting the
   * stride for any plane to 0 is the same as setting it to the component width
   * of the plane.  If <code>strides</code> is null, then the strides for all
   * planes will be set to their respective component widths.  You can adjust
   * the strides in order to add an arbitrary amount of line padding to each
   * plane.
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return a YUV planar image.
   */
  public YUVImage encodeYUV(int[] strides, int flags) throws TJException {
    checkSourceImage();
    checkSubsampling();
    YUVImage dstYUVImage = new YUVImage(srcWidth, strides, srcHeight, subsamp);
    encodeYUV(dstYUVImage, flags);
    return dstYUVImage;
  }

  /**
   * @deprecated Use {@link #encodeYUV(int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public byte[] encodeYUV(int flags) throws TJException {
    checkSourceImage();
    checkSubsampling();
    YUVImage dstYUVImage = new YUVImage(srcWidth, 4, srcHeight, subsamp);
    encodeYUV(dstYUVImage, flags);
    return dstYUVImage.getBuf();
  }

  /**
   * @deprecated Use
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} and
   * {@link #encodeYUV(byte[], int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public void encodeYUV(BufferedImage srcImage, byte[] dstBuf, int flags)
                        throws TJException {
    setSourceImage(srcImage, 0, 0, 0, 0);
    encodeYUV(dstBuf, flags);
  }

  /**
   * @deprecated Use
   * {@link #setSourceImage(BufferedImage, int, int, int, int)} and
   * {@link #encodeYUV(int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public byte[] encodeYUV(BufferedImage srcImage, int flags)
                          throws TJException {
    setSourceImage(srcImage, 0, 0, 0, 0);
    return encodeYUV(flags);
  }

  /**
   * Returns the size of the image (in bytes) generated by the most recent
   * compress operation.
   *
   * @return the size of the image (in bytes) generated by the most recent
   * compress operation.
   */
  public int getCompressedSize() {
    return compressedSize;
  }

  /**
   * Free the native structures associated with this compressor instance.
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

  // JPEG size in bytes is returned
  @SuppressWarnings("checkstyle:HiddenField")
  @Deprecated
  private native int compress(byte[] srcBuf, int width, int pitch,
    int height, int pixelFormat, byte[] jpegBuf, int jpegSubsamp, int jpegQual,
    int flags) throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  private native int compress(byte[] srcBuf, int x, int y, int width,
    int pitch, int height, int pixelFormat, byte[] jpegBuf, int jpegSubsamp,
    int jpegQual, int flags) throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  @Deprecated
  private native int compress(int[] srcBuf, int width, int stride,
    int height, int pixelFormat, byte[] jpegBuf, int jpegSubsamp, int jpegQual,
    int flags) throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  private native int compress(int[] srcBuf, int x, int y, int width,
    int stride, int height, int pixelFormat, byte[] jpegBuf, int jpegSubsamp,
    int jpegQual, int flags) throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  private native int compressFromYUV(byte[][] srcPlanes, int[] srcOffsets,
    int width, int[] srcStrides, int height, int subsamp, byte[] jpegBuf,
    int jpegQual, int flags)
    throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  @Deprecated
  private native void encodeYUV(byte[] srcBuf, int width, int pitch,
    int height, int pixelFormat, byte[] dstBuf, int subsamp, int flags)
    throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  private native void encodeYUV(byte[] srcBuf, int x, int y, int width,
    int pitch, int height, int pixelFormat, byte[][] dstPlanes,
    int[] dstOffsets, int[] dstStrides, int subsamp, int flags)
    throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  @Deprecated
  private native void encodeYUV(int[] srcBuf, int width, int stride,
    int height, int pixelFormat, byte[] dstBuf, int subsamp, int flags)
    throws TJException;

  @SuppressWarnings("checkstyle:HiddenField")
  private native void encodeYUV(int[] srcBuf, int x, int y, int width,
    int srcStride, int height, int pixelFormat, byte[][] dstPlanes,
    int[] dstOffsets, int[] dstStrides, int subsamp, int flags)
    throws TJException;

  static {
    TJLoader.load();
  }

  private void checkSourceImage() {
    if (srcWidth < 1 || srcHeight < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
  }

  private void checkSubsampling() {
    if (subsamp < 0)
      throw new IllegalStateException("Subsampling level not set");
  }

  private long handle = 0;
  private byte[] srcBuf = null;
  private int[] srcBufInt = null;
  private int srcWidth = 0;
  private int srcHeight = 0;
  private int srcX = -1;
  private int srcY = -1;
  private int srcPitch = 0;
  private int srcStride = 0;
  private int srcPixelFormat = -1;
  private YUVImage srcYUVImage = null;
  private int subsamp = -1;
  private int jpegQuality = -1;
  private int compressedSize = 0;
  private int yuvPad = 4;
  private ByteOrder byteOrder = null;
}

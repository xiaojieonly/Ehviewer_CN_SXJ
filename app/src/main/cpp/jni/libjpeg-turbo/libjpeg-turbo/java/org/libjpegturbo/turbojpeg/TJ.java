/*
 * Copyright (C)2011-2013, 2017-2018 D. R. Commander.  All Rights Reserved.
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

/**
 * TurboJPEG utility class (cannot be instantiated)
 */
public final class TJ {

  private TJ() {}

  /**
   * The number of chrominance subsampling options
   */
  public static final int NUMSAMP   = 6;
  /**
   * 4:4:4 chrominance subsampling (no chrominance subsampling).  The JPEG
   * or YUV image will contain one chrominance component for every pixel in the
   * source image.
   */
  public static final int SAMP_444  = 0;
  /**
   * 4:2:2 chrominance subsampling.  The JPEG or YUV image will contain one
   * chrominance component for every 2x1 block of pixels in the source image.
   */
  public static final int SAMP_422  = 1;
  /**
   * 4:2:0 chrominance subsampling.  The JPEG or YUV image will contain one
   * chrominance component for every 2x2 block of pixels in the source image.
   */
  public static final int SAMP_420  = 2;
  /**
   * Grayscale.  The JPEG or YUV image will contain no chrominance components.
   */
  public static final int SAMP_GRAY = 3;
  /**
   * 4:4:0 chrominance subsampling.  The JPEG or YUV image will contain one
   * chrominance component for every 1x2 block of pixels in the source image.
   * Note that 4:4:0 subsampling is not fully accelerated in libjpeg-turbo.
   */
  public static final int SAMP_440  = 4;
  /**
   * 4:1:1 chrominance subsampling.  The JPEG or YUV image will contain one
   * chrominance component for every 4x1 block of pixels in the source image.
   * JPEG images compressed with 4:1:1 subsampling will be almost exactly the
   * same size as those compressed with 4:2:0 subsampling, and in the
   * aggregate, both subsampling methods produce approximately the same
   * perceptual quality.  However, 4:1:1 is better able to reproduce sharp
   * horizontal features.  Note that 4:1:1 subsampling is not fully accelerated
   * in libjpeg-turbo.
   */
  public static final int SAMP_411  = 5;


  /**
   * Returns the MCU block width for the given level of chrominance
   * subsampling.
   *
   * @param subsamp the level of chrominance subsampling (one of
   * <code>SAMP_*</code>)
   *
   * @return the MCU block width for the given level of chrominance
   * subsampling.
   */
  public static int getMCUWidth(int subsamp) {
    checkSubsampling(subsamp);
    return MCU_WIDTH[subsamp];
  }

  private static final int[] MCU_WIDTH = {
    8, 16, 16, 8, 8, 32
  };


  /**
   * Returns the MCU block height for the given level of chrominance
   * subsampling.
   *
   * @param subsamp the level of chrominance subsampling (one of
   * <code>SAMP_*</code>)
   *
   * @return the MCU block height for the given level of chrominance
   * subsampling.
   */
  public static int getMCUHeight(int subsamp) {
    checkSubsampling(subsamp);
    return MCU_HEIGHT[subsamp];
  }

  private static final int[] MCU_HEIGHT = {
    8, 8, 16, 8, 16, 8
  };


  /**
   * The number of pixel formats
   */
  public static final int NUMPF   = 12;
  /**
   * RGB pixel format.  The red, green, and blue components in the image are
   * stored in 3-byte pixels in the order R, G, B from lowest to highest byte
   * address within each pixel.
   */
  public static final int PF_RGB  = 0;
  /**
   * BGR pixel format.  The red, green, and blue components in the image are
   * stored in 3-byte pixels in the order B, G, R from lowest to highest byte
   * address within each pixel.
   */
  public static final int PF_BGR  = 1;
  /**
   * RGBX pixel format.  The red, green, and blue components in the image are
   * stored in 4-byte pixels in the order R, G, B from lowest to highest byte
   * address within each pixel.  The X component is ignored when compressing
   * and undefined when decompressing.
   */
  public static final int PF_RGBX = 2;
  /**
   * BGRX pixel format.  The red, green, and blue components in the image are
   * stored in 4-byte pixels in the order B, G, R from lowest to highest byte
   * address within each pixel.  The X component is ignored when compressing
   * and undefined when decompressing.
   */
  public static final int PF_BGRX = 3;
  /**
   * XBGR pixel format.  The red, green, and blue components in the image are
   * stored in 4-byte pixels in the order R, G, B from highest to lowest byte
   * address within each pixel.  The X component is ignored when compressing
   * and undefined when decompressing.
   */
  public static final int PF_XBGR = 4;
  /**
   * XRGB pixel format.  The red, green, and blue components in the image are
   * stored in 4-byte pixels in the order B, G, R from highest to lowest byte
   * address within each pixel.  The X component is ignored when compressing
   * and undefined when decompressing.
   */
  public static final int PF_XRGB = 5;
  /**
   * Grayscale pixel format.  Each 1-byte pixel represents a luminance
   * (brightness) level from 0 to 255.
   */
  public static final int PF_GRAY = 6;
  /**
   * RGBA pixel format.  This is the same as {@link #PF_RGBX}, except that when
   * decompressing, the X byte is guaranteed to be 0xFF, which can be
   * interpreted as an opaque alpha channel.
   */
  public static final int PF_RGBA = 7;
  /**
   * BGRA pixel format.  This is the same as {@link #PF_BGRX}, except that when
   * decompressing, the X byte is guaranteed to be 0xFF, which can be
   * interpreted as an opaque alpha channel.
   */
  public static final int PF_BGRA = 8;
  /**
   * ABGR pixel format.  This is the same as {@link #PF_XBGR}, except that when
   * decompressing, the X byte is guaranteed to be 0xFF, which can be
   * interpreted as an opaque alpha channel.
   */
  public static final int PF_ABGR = 9;
  /**
   * ARGB pixel format.  This is the same as {@link #PF_XRGB}, except that when
   * decompressing, the X byte is guaranteed to be 0xFF, which can be
   * interpreted as an opaque alpha channel.
   */
  public static final int PF_ARGB = 10;
  /**
   * CMYK pixel format.  Unlike RGB, which is an additive color model used
   * primarily for display, CMYK (Cyan/Magenta/Yellow/Key) is a subtractive
   * color model used primarily for printing.  In the CMYK color model, the
   * value of each color component typically corresponds to an amount of cyan,
   * magenta, yellow, or black ink that is applied to a white background.  In
   * order to convert between CMYK and RGB, it is necessary to use a color
   * management system (CMS.)  A CMS will attempt to map colors within the
   * printer's gamut to perceptually similar colors in the display's gamut and
   * vice versa, but the mapping is typically not 1:1 or reversible, nor can it
   * be defined with a simple formula.  Thus, such a conversion is out of scope
   * for a codec library.  However, the TurboJPEG API allows for compressing
   * CMYK pixels into a YCCK JPEG image (see {@link #CS_YCCK}) and
   * decompressing YCCK JPEG images into CMYK pixels.
   */
  public static final int PF_CMYK = 11;


  /**
   * Returns the pixel size (in bytes) for the given pixel format.
   *
   * @param pixelFormat the pixel format (one of <code>PF_*</code>)
   *
   * @return the pixel size (in bytes) for the given pixel format.
   */
  public static int getPixelSize(int pixelFormat) {
    checkPixelFormat(pixelFormat);
    return PIXEL_SIZE[pixelFormat];
  }

  private static final int[] PIXEL_SIZE = {
    3, 3, 4, 4, 4, 4, 1, 4, 4, 4, 4, 4
  };


  /**
   * For the given pixel format, returns the number of bytes that the red
   * component is offset from the start of the pixel.  For instance, if a pixel
   * of format <code>TJ.PF_BGRX</code> is stored in <code>char pixel[]</code>,
   * then the red component will be
   * <code>pixel[TJ.getRedOffset(TJ.PF_BGRX)]</code>.
   *
   * @param pixelFormat the pixel format (one of <code>PF_*</code>)
   *
   * @return the red offset for the given pixel format, or -1 if the pixel
   * format does not have a red component.
   */
  public static int getRedOffset(int pixelFormat) {
    checkPixelFormat(pixelFormat);
    return RED_OFFSET[pixelFormat];
  }

  private static final int[] RED_OFFSET = {
    0, 2, 0, 2, 3, 1, -1, 0, 2, 3, 1, -1
  };


  /**
   * For the given pixel format, returns the number of bytes that the green
   * component is offset from the start of the pixel.  For instance, if a pixel
   * of format <code>TJ.PF_BGRX</code> is stored in <code>char pixel[]</code>,
   * then the green component will be
   * <code>pixel[TJ.getGreenOffset(TJ.PF_BGRX)]</code>.
   *
   * @param pixelFormat the pixel format (one of <code>PF_*</code>)
   *
   * @return the green offset for the given pixel format, or -1 if the pixel
   * format does not have a green component.
   */
  public static int getGreenOffset(int pixelFormat) {
    checkPixelFormat(pixelFormat);
    return GREEN_OFFSET[pixelFormat];
  }

  private static final int[] GREEN_OFFSET = {
    1, 1, 1, 1, 2, 2, -1, 1, 1, 2, 2, -1
  };


  /**
   * For the given pixel format, returns the number of bytes that the blue
   * component is offset from the start of the pixel.  For instance, if a pixel
   * of format <code>TJ.PF_BGRX</code> is stored in <code>char pixel[]</code>,
   * then the blue component will be
   * <code>pixel[TJ.getBlueOffset(TJ.PF_BGRX)]</code>.
   *
   * @param pixelFormat the pixel format (one of <code>PF_*</code>)
   *
   * @return the blue offset for the given pixel format, or -1 if the pixel
   * format does not have a blue component.
   */
  public static int getBlueOffset(int pixelFormat) {
    checkPixelFormat(pixelFormat);
    return BLUE_OFFSET[pixelFormat];
  }

  private static final int[] BLUE_OFFSET = {
    2, 0, 2, 0, 1, 3, -1, 2, 0, 1, 3, -1
  };


  /**
   * For the given pixel format, returns the number of bytes that the alpha
   * component is offset from the start of the pixel.  For instance, if a pixel
   * of format <code>TJ.PF_BGRA</code> is stored in <code>char pixel[]</code>,
   * then the alpha component will be
   * <code>pixel[TJ.getAlphaOffset(TJ.PF_BGRA)]</code>.
   *
   * @param pixelFormat the pixel format (one of <code>PF_*</code>)
   *
   * @return the alpha offset for the given pixel format, or -1 if the pixel
   * format does not have a alpha component.
   */
  public static int getAlphaOffset(int pixelFormat) {
    checkPixelFormat(pixelFormat);
    return ALPHA_OFFSET[pixelFormat];
  }

  private static final int[] ALPHA_OFFSET = {
    -1, -1, -1, -1, -1, -1, -1, 3, 3, 0, 0, -1
  };


  /**
   * The number of JPEG colorspaces
   */
  public static final int NUMCS = 5;
  /**
   * RGB colorspace.  When compressing the JPEG image, the R, G, and B
   * components in the source image are reordered into image planes, but no
   * colorspace conversion or subsampling is performed.  RGB JPEG images can be
   * decompressed to any of the extended RGB pixel formats or grayscale, but
   * they cannot be decompressed to YUV images.
   */
  public static final int CS_RGB = 0;
  /**
   * YCbCr colorspace.  YCbCr is not an absolute colorspace but rather a
   * mathematical transformation of RGB designed solely for storage and
   * transmission.  YCbCr images must be converted to RGB before they can
   * actually be displayed.  In the YCbCr colorspace, the Y (luminance)
   * component represents the black & white portion of the original image, and
   * the Cb and Cr (chrominance) components represent the color portion of the
   * original image.  Originally, the analog equivalent of this transformation
   * allowed the same signal to drive both black & white and color televisions,
   * but JPEG images use YCbCr primarily because it allows the color data to be
   * optionally subsampled for the purposes of reducing bandwidth or disk
   * space.  YCbCr is the most common JPEG colorspace, and YCbCr JPEG images
   * can be compressed from and decompressed to any of the extended RGB pixel
   * formats or grayscale, or they can be decompressed to YUV planar images.
   */
  @SuppressWarnings("checkstyle:ConstantName")
  public static final int CS_YCbCr = 1;
  /**
   * Grayscale colorspace.  The JPEG image retains only the luminance data (Y
   * component), and any color data from the source image is discarded.
   * Grayscale JPEG images can be compressed from and decompressed to any of
   * the extended RGB pixel formats or grayscale, or they can be decompressed
   * to YUV planar images.
   */
  public static final int CS_GRAY = 2;
  /**
   * CMYK colorspace.  When compressing the JPEG image, the C, M, Y, and K
   * components in the source image are reordered into image planes, but no
   * colorspace conversion or subsampling is performed.  CMYK JPEG images can
   * only be decompressed to CMYK pixels.
   */
  public static final int CS_CMYK = 3;
  /**
   * YCCK colorspace.  YCCK (AKA "YCbCrK") is not an absolute colorspace but
   * rather a mathematical transformation of CMYK designed solely for storage
   * and transmission.  It is to CMYK as YCbCr is to RGB.  CMYK pixels can be
   * reversibly transformed into YCCK, and as with YCbCr, the chrominance
   * components in the YCCK pixels can be subsampled without incurring major
   * perceptual loss.  YCCK JPEG images can only be compressed from and
   * decompressed to CMYK pixels.
   */
  public static final int CS_YCCK = 4;


  /**
   * The uncompressed source/destination image is stored in bottom-up (Windows,
   * OpenGL) order, not top-down (X11) order.
   */
  public static final int FLAG_BOTTOMUP      = 2;

  @SuppressWarnings("checkstyle:JavadocVariable")
  @Deprecated
  public static final int FLAG_FORCEMMX      = 8;
  @SuppressWarnings("checkstyle:JavadocVariable")
  @Deprecated
  public static final int FLAG_FORCESSE      = 16;
  @SuppressWarnings("checkstyle:JavadocVariable")
  @Deprecated
  public static final int FLAG_FORCESSE2     = 32;
  @SuppressWarnings("checkstyle:JavadocVariable")
  @Deprecated
  public static final int FLAG_FORCESSE3     = 128;

  /**
   * When decompressing an image that was compressed using chrominance
   * subsampling, use the fastest chrominance upsampling algorithm available in
   * the underlying codec.  The default is to use smooth upsampling, which
   * creates a smooth transition between neighboring chrominance components in
   * order to reduce upsampling artifacts in the decompressed image.
   */
  public static final int FLAG_FASTUPSAMPLE  = 256;
  /**
   * Use the fastest DCT/IDCT algorithm available in the underlying codec.  The
   * default if this flag is not specified is implementation-specific.  For
   * example, the implementation of TurboJPEG for libjpeg[-turbo] uses the fast
   * algorithm by default when compressing, because this has been shown to have
   * only a very slight effect on accuracy, but it uses the accurate algorithm
   * when decompressing, because this has been shown to have a larger effect.
   */
  public static final int FLAG_FASTDCT       = 2048;
  /**
   * Use the most accurate DCT/IDCT algorithm available in the underlying
   * codec.  The default if this flag is not specified is
   * implementation-specific.  For example, the implementation of TurboJPEG for
   * libjpeg[-turbo] uses the fast algorithm by default when compressing,
   * because this has been shown to have only a very slight effect on accuracy,
   * but it uses the accurate algorithm when decompressing, because this has
   * been shown to have a larger effect.
   */
  public static final int FLAG_ACCURATEDCT   = 4096;
  /**
   * Immediately discontinue the current compression/decompression/transform
   * operation if the underlying codec throws a warning (non-fatal error).  The
   * default behavior is to allow the operation to complete unless a fatal
   * error is encountered.
   * <p>
   * NOTE: due to the design of the TurboJPEG Java API, only certain methods
   * (specifically, {@link TJDecompressor TJDecompressor.decompress*()} methods
   * with a void return type) will complete and leave the output image in a
   * fully recoverable state after a non-fatal error occurs.
   */
  public static final int FLAG_STOPONWARNING = 8192;
  /**
   * Use progressive entropy coding in JPEG images generated by compression and
   * transform operations.  Progressive entropy coding will generally improve
   * compression relative to baseline entropy coding (the default), but it will
   * reduce compression and decompression performance considerably.
   */
  public static final int FLAG_PROGRESSIVE   = 16384;


  /**
   * The number of error codes
   */
  public static final int NUMERR = 2;
  /**
   * The error was non-fatal and recoverable, but the image may still be
   * corrupt.
   * <p>
   * NOTE: due to the design of the TurboJPEG Java API, only certain methods
   * (specifically, {@link TJDecompressor TJDecompressor.decompress*()} methods
   * with a void return type) will complete and leave the output image in a
   * fully recoverable state after a non-fatal error occurs.
   */
  public static final int ERR_WARNING = 0;
  /**
   * The error was fatal and non-recoverable.
   */
  public static final int ERR_FATAL = 1;


  /**
   * Returns the maximum size of the buffer (in bytes) required to hold a JPEG
   * image with the given width, height, and level of chrominance subsampling.
   *
   * @param width the width (in pixels) of the JPEG image
   *
   * @param height the height (in pixels) of the JPEG image
   *
   * @param jpegSubsamp the level of chrominance subsampling to be used when
   * generating the JPEG image (one of {@link TJ TJ.SAMP_*})
   *
   * @return the maximum size of the buffer (in bytes) required to hold a JPEG
   * image with the given width, height, and level of chrominance subsampling.
   */
  public static native int bufSize(int width, int height, int jpegSubsamp);

  /**
   * Returns the size of the buffer (in bytes) required to hold a YUV planar
   * image with the given width, height, and level of chrominance subsampling.
   *
   * @param width the width (in pixels) of the YUV image
   *
   * @param pad the width of each line in each plane of the image is padded to
   * the nearest multiple of this number of bytes (must be a power of 2.)
   *
   * @param height the height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ TJ.SAMP_*})
   *
   * @return the size of the buffer (in bytes) required to hold a YUV planar
   * image with the given width, height, and level of chrominance subsampling.
   */
  public static native int bufSizeYUV(int width, int pad, int height,
                                      int subsamp);

  /**
   * @deprecated Use {@link #bufSizeYUV(int, int, int, int)} instead.
   */
  @SuppressWarnings("checkstyle:JavadocMethod")
  @Deprecated
  public static native int bufSizeYUV(int width, int height, int subsamp);

  /**
   * Returns the size of the buffer (in bytes) required to hold a YUV image
   * plane with the given parameters.
   *
   * @param componentID ID number of the image plane (0 = Y, 1 = U/Cb,
   * 2 = V/Cr)
   *
   * @param width width (in pixels) of the YUV image.  NOTE: this is the width
   * of the whole image, not the plane width.
   *
   * @param stride bytes per line in the image plane.
   *
   * @param height height (in pixels) of the YUV image.  NOTE: this is the
   * height of the whole image, not the plane height.
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ TJ.SAMP_*})
   *
   * @return the size of the buffer (in bytes) required to hold a YUV planar
   * image with the given parameters.
   */
  public static native int planeSizeYUV(int componentID, int width, int stride,
                                        int height, int subsamp);

  /**
   * Returns the plane width of a YUV image plane with the given parameters.
   * Refer to {@link YUVImage YUVImage} for a description of plane width.
   *
   * @param componentID ID number of the image plane (0 = Y, 1 = U/Cb,
   * 2 = V/Cr)
   *
   * @param width width (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling used in the YUV image
   * (one of {@link TJ TJ.SAMP_*})
   *
   * @return the plane width of a YUV image plane with the given parameters.
   */
  public static native int planeWidth(int componentID, int width, int subsamp);

  /**
   * Returns the plane height of a YUV image plane with the given parameters.
   * Refer to {@link YUVImage YUVImage} for a description of plane height.
   *
   * @param componentID ID number of the image plane (0 = Y, 1 = U/Cb,
   * 2 = V/Cr)
   *
   * @param height height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling used in the YUV image
   * (one of {@link TJ TJ.SAMP_*})
   *
   * @return the plane height of a YUV image plane with the given parameters.
   */
  public static native int planeHeight(int componentID, int height,
                                       int subsamp);

  /**
   * Returns a list of fractional scaling factors that the JPEG decompressor in
   * this implementation of TurboJPEG supports.
   *
   * @return a list of fractional scaling factors that the JPEG decompressor in
   * this implementation of TurboJPEG supports.
   */
  public static native TJScalingFactor[] getScalingFactors();

  static {
    TJLoader.load();
  }

  private static void checkPixelFormat(int pixelFormat) {
    if (pixelFormat < 0 || pixelFormat >= NUMPF)
      throw new IllegalArgumentException("Invalid pixel format");
  }

  private static void checkSubsampling(int subsamp) {
    if (subsamp < 0 || subsamp >= NUMSAMP)
      throw new IllegalArgumentException("Invalid subsampling type");
  }

}

/*
 * Copyright (C)2014, 2017 D. R. Commander.  All Rights Reserved.
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
 * This class encapsulates a YUV planar image and the metadata
 * associated with it.  The TurboJPEG API allows both the JPEG compression and
 * decompression pipelines to be split into stages:  YUV encode, compress from
 * YUV, decompress to YUV, and YUV decode.  A <code>YUVImage</code> instance
 * serves as the destination image for YUV encode and decompress-to-YUV
 * operations and as the source image for compress-from-YUV and YUV decode
 * operations.
 * <p>
 * Technically, the JPEG format uses the YCbCr colorspace (which technically is
 * not a "colorspace" but rather a "color transform"), but per the convention
 * of the digital video community, the TurboJPEG API uses "YUV" to refer to an
 * image format consisting of Y, Cb, and Cr image planes.
 * <p>
 * Each plane is simply a 2D array of bytes, each byte representing the value
 * of one of the components (Y, Cb, or Cr) at a particular location in the
 * image.  The width and height of each plane are determined by the image
 * width, height, and level of chrominance subsampling.  The luminance plane
 * width is the image width padded to the nearest multiple of the horizontal
 * subsampling factor (2 in the case of 4:2:0 and 4:2:2, 4 in the case of
 * 4:1:1, 1 in the case of 4:4:4 or grayscale.)  Similarly, the luminance plane
 * height is the image height padded to the nearest multiple of the vertical
 * subsampling factor (2 in the case of 4:2:0 or 4:4:0, 1 in the case of 4:4:4
 * or grayscale.)  The chrominance plane width is equal to the luminance plane
 * width divided by the horizontal subsampling factor, and the chrominance
 * plane height is equal to the luminance plane height divided by the vertical
 * subsampling factor.
 * <p>
 * For example, if the source image is 35 x 35 pixels and 4:2:2 subsampling is
 * used, then the luminance plane would be 36 x 35 bytes, and each of the
 * chrominance planes would be 18 x 35 bytes.  If you specify a line padding of
 * 4 bytes on top of this, then the luminance plane would be 36 x 35 bytes, and
 * each of the chrominance planes would be 20 x 35 bytes.
 */
public class YUVImage {

  private static final String NO_ASSOC_ERROR =
    "No image data is associated with this instance";

  /**
   * Create a new <code>YUVImage</code> instance backed by separate image
   * planes, and allocate memory for the image planes.
   *
   * @param width width (in pixels) of the YUV image
   *
   * @param strides an array of integers, each specifying the number of bytes
   * per line in the corresponding plane of the YUV image.  Setting the stride
   * for any plane to 0 is the same as setting it to the plane width (see
   * {@link YUVImage above}.)  If <code>strides</code> is null, then the
   * strides for all planes will be set to their respective plane widths.  When
   * using this constructor, the stride for each plane must be equal to or
   * greater than the plane width.
   *
   * @param height height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling to be used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public YUVImage(int width, int[] strides, int height, int subsamp) {
    setBuf(null, null, width, strides, height, subsamp, true);
  }

  /**
   * Create a new <code>YUVImage</code> instance backed by a unified image
   * buffer, and allocate memory for the image buffer.
   *
   * @param width width (in pixels) of the YUV image
   *
   * @param pad Each line of each plane in the YUV image buffer will be padded
   * to this number of bytes (must be a power of 2.)
   *
   * @param height height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling to be used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public YUVImage(int width, int pad, int height, int subsamp) {
    setBuf(new byte[TJ.bufSizeYUV(width, pad, height, subsamp)], width, pad,
           height, subsamp);
  }

  /**
   * Create a new <code>YUVImage</code> instance from a set of existing image
   * planes.
   *
   * @param planes an array of buffers representing the Y, U (Cb), and V (Cr)
   * image planes (or just the Y plane, if the image is grayscale.)   These
   * planes can be contiguous or non-contiguous in memory.  Plane
   * <code>i</code> should be at least <code>offsets[i] +
   * {@link TJ#planeSizeYUV TJ.planeSizeYUV}(i, width, strides[i], height, subsamp)</code>
   * bytes in size.
   *
   * @param offsets If this <code>YUVImage</code> instance represents a
   * subregion of a larger image, then <code>offsets[i]</code> specifies the
   * offset (in bytes) of the subregion within plane <code>i</code> of the
   * larger image.  Setting this to null is the same as setting the offsets for
   * all planes to 0.
   *
   * @param width width (in pixels) of the new YUV image (or subregion)
   *
   * @param strides an array of integers, each specifying the number of bytes
   * per line in the corresponding plane of the YUV image.  Setting the stride
   * for any plane to 0 is the same as setting it to the plane width (see
   * {@link YUVImage above}.)  If <code>strides</code> is null, then the
   * strides for all planes will be set to their respective plane widths.  You
   * can adjust the strides in order to add an arbitrary amount of line padding
   * to each plane or to specify that this <code>YUVImage</code> instance is a
   * subregion of a larger image (in which case, <code>strides[i]</code> should
   * be set to the plane width of plane <code>i</code> in the larger image.)
   *
   * @param height height (in pixels) of the new YUV image (or subregion)
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public YUVImage(byte[][] planes, int[] offsets, int width, int[] strides,
                  int height, int subsamp) {
    setBuf(planes, offsets, width, strides, height, subsamp, false);
  }

  /**
   * Create a new <code>YUVImage</code> instance from an existing unified image
   * buffer.
   *
   * @param yuvImage image buffer that contains or will contain YUV planar
   * image data.  Use {@link TJ#bufSizeYUV} to determine the minimum size for
   * this buffer.  The Y, U (Cb), and V (Cr) image planes are stored
   * sequentially in the buffer (see {@link YUVImage above} for a description
   * of the image format.)
   *
   * @param width width (in pixels) of the YUV image
   *
   * @param pad the line padding used in the YUV image buffer.  For
   * instance, if each line in each plane of the buffer is padded to the
   * nearest multiple of 4 bytes, then <code>pad</code> should be set to 4.
   *
   * @param height height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public YUVImage(byte[] yuvImage, int width, int pad, int height,
                  int subsamp) {
    setBuf(yuvImage, width, pad, height, subsamp);
  }

  /**
   * Assign a set of image planes to this <code>YUVImage</code> instance.
   *
   * @param planes an array of buffers representing the Y, U (Cb), and V (Cr)
   * image planes (or just the Y plane, if the image is grayscale.)  These
   * planes can be contiguous or non-contiguous in memory.  Plane
   * <code>i</code> should be at least <code>offsets[i] +
   * {@link TJ#planeSizeYUV TJ.planeSizeYUV}(i, width, strides[i], height, subsamp)</code>
   * bytes in size.
   *
   * @param offsets If this <code>YUVImage</code> instance represents a
   * subregion of a larger image, then <code>offsets[i]</code> specifies the
   * offset (in bytes) of the subregion within plane <code>i</code> of the
   * larger image.  Setting this to null is the same as setting the offsets for
   * all planes to 0.
   *
   * @param width width (in pixels) of the YUV image (or subregion)
   *
   * @param strides an array of integers, each specifying the number of bytes
   * per line in the corresponding plane of the YUV image.  Setting the stride
   * for any plane to 0 is the same as setting it to the plane width (see
   * {@link YUVImage above}.)  If <code>strides</code> is null, then the
   * strides for all planes will be set to their respective plane widths.  You
   * can adjust the strides in order to add an arbitrary amount of line padding
   * to each plane or to specify that this <code>YUVImage</code> image is a
   * subregion of a larger image (in which case, <code>strides[i]</code> should
   * be set to the plane width of plane <code>i</code> in the larger image.)
   *
   * @param height height (in pixels) of the YUV image (or subregion)
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public void setBuf(byte[][] planes, int[] offsets, int width, int[] strides,
                     int height, int subsamp) {
    setBuf(planes, offsets, width, strides, height, subsamp, false);
  }

  private void setBuf(byte[][] planes, int[] offsets, int width, int[] strides,
                     int height, int subsamp, boolean alloc) {
    if ((planes == null && !alloc) || width < 1 || height < 1 || subsamp < 0 ||
        subsamp >= TJ.NUMSAMP)
      throw new IllegalArgumentException("Invalid argument in YUVImage::setBuf()");

    int nc = (subsamp == TJ.SAMP_GRAY ? 1 : 3);
    if ((planes != null && planes.length != nc) ||
        (offsets != null && offsets.length != nc) ||
        (strides != null && strides.length != nc))
      throw new IllegalArgumentException("YUVImage::setBuf(): planes, offsets, or strides array is the wrong size");

    if (planes == null)
      planes = new byte[nc][];
    if (offsets == null)
      offsets = new int[nc];
    if (strides == null)
      strides = new int[nc];

    for (int i = 0; i < nc; i++) {
      int pw = TJ.planeWidth(i, width, subsamp);
      int ph = TJ.planeHeight(i, height, subsamp);
      int planeSize = TJ.planeSizeYUV(i, width, strides[i], height, subsamp);

      if (strides[i] == 0)
        strides[i] = pw;
      if (alloc) {
        if (strides[i] < pw)
          throw new IllegalArgumentException("Stride must be >= plane width when allocating a new YUV image");
        planes[i] = new byte[strides[i] * ph];
      }
      if (planes[i] == null || offsets[i] < 0)
        throw new IllegalArgumentException("Invalid argument in YUVImage::setBuf()");
      if (strides[i] < 0 && offsets[i] - planeSize + pw < 0)
        throw new IllegalArgumentException("Stride for plane " + i +
                                           " would cause memory to be accessed below plane boundary");
      if (planes[i].length < offsets[i] + planeSize)
        throw new IllegalArgumentException("Image plane " + i +
                                           " is not large enough");
    }

    yuvPlanes = planes;
    yuvOffsets = offsets;
    yuvWidth = width;
    yuvStrides = strides;
    yuvHeight = height;
    yuvSubsamp = subsamp;
  }

  /**
   * Assign a unified image buffer to this <code>YUVImage</code> instance.
   *
   * @param yuvImage image buffer that contains or will contain YUV planar
   * image data.  Use {@link TJ#bufSizeYUV} to determine the minimum size for
   * this buffer.  The Y, U (Cb), and V (Cr) image planes are stored
   * sequentially in the buffer (see {@link YUVImage above} for a description
   * of the image format.)
   *
   * @param width width (in pixels) of the YUV image
   *
   * @param pad the line padding used in the YUV image buffer.  For
   * instance, if each line in each plane of the buffer is padded to the
   * nearest multiple of 4 bytes, then <code>pad</code> should be set to 4.
   *
   * @param height height (in pixels) of the YUV image
   *
   * @param subsamp the level of chrominance subsampling used in the YUV
   * image (one of {@link TJ#SAMP_444 TJ.SAMP_*})
   */
  public void setBuf(byte[] yuvImage, int width, int pad, int height,
                     int subsamp) {
    if (yuvImage == null || width < 1 || pad < 1 || ((pad & (pad - 1)) != 0) ||
        height < 1 || subsamp < 0 || subsamp >= TJ.NUMSAMP)
      throw new IllegalArgumentException("Invalid argument in YUVImage::setBuf()");
    if (yuvImage.length < TJ.bufSizeYUV(width, pad, height, subsamp))
      throw new IllegalArgumentException("YUV image buffer is not large enough");

    int nc = (subsamp == TJ.SAMP_GRAY ? 1 : 3);
    byte[][] planes = new byte[nc][];
    int[] strides = new int[nc];
    int[] offsets = new int[nc];

    planes[0] = yuvImage;
    strides[0] = pad(TJ.planeWidth(0, width, subsamp), pad);
    if (subsamp != TJ.SAMP_GRAY) {
      strides[1] = strides[2] = pad(TJ.planeWidth(1, width, subsamp), pad);
      planes[1] = planes[2] = yuvImage;
      offsets[1] = offsets[0] +
        strides[0] * TJ.planeHeight(0, height, subsamp);
      offsets[2] = offsets[1] +
        strides[1] * TJ.planeHeight(1, height, subsamp);
    }

    yuvPad = pad;
    setBuf(planes, offsets, width, strides, height, subsamp);
  }

  /**
   * Returns the width of the YUV image (or subregion.)
   *
   * @return the width of the YUV image (or subregion)
   */
  public int getWidth() {
    if (yuvWidth < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvWidth;
  }

  /**
   * Returns the height of the YUV image (or subregion.)
   *
   * @return the height of the YUV image (or subregion)
   */
  public int getHeight() {
    if (yuvHeight < 1)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvHeight;
  }

  /**
   * Returns the line padding used in the YUV image buffer (if this image is
   * stored in a unified buffer rather than separate image planes.)
   *
   * @return the line padding used in the YUV image buffer
   */
  public int getPad() {
    if (yuvPlanes == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    if (yuvPad < 1 || ((yuvPad & (yuvPad - 1)) != 0))
      throw new IllegalStateException("Image is not stored in a unified buffer");
    return yuvPad;
  }

  /**
   * Returns the number of bytes per line of each plane in the YUV image.
   *
   * @return the number of bytes per line of each plane in the YUV image
   */
  public int[] getStrides() {
    if (yuvStrides == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvStrides;
  }

  /**
   * Returns the offsets (in bytes) of each plane within the planes of a larger
   * YUV image.
   *
   * @return the offsets (in bytes) of each plane within the planes of a larger
   * YUV image
   */
  public int[] getOffsets() {
    if (yuvOffsets == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvOffsets;
  }

  /**
   * Returns the level of chrominance subsampling used in the YUV image.  See
   * {@link TJ#SAMP_444 TJ.SAMP_*}.
   *
   * @return the level of chrominance subsampling used in the YUV image
   */
  public int getSubsamp() {
    if (yuvSubsamp < 0 || yuvSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvSubsamp;
  }

  /**
   * Returns the YUV image planes.  If the image is stored in a unified buffer,
   * then all image planes will point to that buffer.
   *
   * @return the YUV image planes
   */
  public byte[][] getPlanes() {
    if (yuvPlanes == null)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    return yuvPlanes;
  }

  /**
   * Returns the YUV image buffer (if this image is stored in a unified
   * buffer rather than separate image planes.)
   *
   * @return the YUV image buffer
   */
  public byte[] getBuf() {
    if (yuvPlanes == null || yuvSubsamp < 0 || yuvSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    int nc = (yuvSubsamp == TJ.SAMP_GRAY ? 1 : 3);
    for (int i = 1; i < nc; i++) {
      if (yuvPlanes[i] != yuvPlanes[0])
        throw new IllegalStateException("Image is not stored in a unified buffer");
    }
    return yuvPlanes[0];
  }

  /**
   * Returns the size (in bytes) of the YUV image buffer (if this image is
   * stored in a unified buffer rather than separate image planes.)
   *
   * @return the size (in bytes) of the YUV image buffer
   */
  public int getSize() {
    if (yuvPlanes == null || yuvSubsamp < 0 || yuvSubsamp >= TJ.NUMSAMP)
      throw new IllegalStateException(NO_ASSOC_ERROR);
    int nc = (yuvSubsamp == TJ.SAMP_GRAY ? 1 : 3);
    if (yuvPad < 1)
      throw new IllegalStateException("Image is not stored in a unified buffer");
    for (int i = 1; i < nc; i++) {
      if (yuvPlanes[i] != yuvPlanes[0])
        throw new IllegalStateException("Image is not stored in a unified buffer");
    }
    return TJ.bufSizeYUV(yuvWidth, yuvPad, yuvHeight, yuvSubsamp);
  }

  private static int pad(int v, int p) {
    return (v + p - 1) & (~(p - 1));
  }

  protected long handle = 0;
  protected byte[][] yuvPlanes = null;
  protected int[] yuvOffsets = null;
  protected int[] yuvStrides = null;
  protected int yuvPad = 0;
  protected int yuvWidth = 0;
  protected int yuvHeight = 0;
  protected int yuvSubsamp = -1;
}

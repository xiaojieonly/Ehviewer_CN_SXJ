/*
 * Copyright (C)2011, 2013-2015 D. R. Commander.  All Rights Reserved.
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
 * TurboJPEG lossless transformer
 */
public class TJTransformer extends TJDecompressor {

  /**
   * Create a TurboJPEG lossless transformer instance.
   */
  public TJTransformer() throws TJException {
    init();
  }

  /**
   * Create a TurboJPEG lossless transformer instance and associate the JPEG
   * image stored in <code>jpegImage</code> with the newly created instance.
   *
   * @param jpegImage JPEG image buffer (size of the JPEG image is assumed to
   * be the length of the array.)  This buffer is not modified.
   */
  public TJTransformer(byte[] jpegImage) throws TJException {
    init();
    setSourceImage(jpegImage, jpegImage.length);
  }

  /**
   * Create a TurboJPEG lossless transformer instance and associate the JPEG
   * image of length <code>imageSize</code> bytes stored in
   * <code>jpegImage</code> with the newly created instance.
   *
   * @param jpegImage JPEG image buffer.  This buffer is not modified.
   *
   * @param imageSize size of the JPEG image (in bytes)
   */
  public TJTransformer(byte[] jpegImage, int imageSize) throws TJException {
    init();
    setSourceImage(jpegImage, imageSize);
  }

  /**
   * Losslessly transform the JPEG image associated with this transformer
   * instance into one or more JPEG images stored in the given destination
   * buffers.  Lossless transforms work by moving the raw coefficients from one
   * JPEG image structure to another without altering the values of the
   * coefficients.  While this is typically faster than decompressing the
   * image, transforming it, and re-compressing it, lossless transforms are not
   * free.  Each lossless transform requires reading and performing Huffman
   * decoding on all of the coefficients in the source image, regardless of the
   * size of the destination image.  Thus, this method provides a means of
   * generating multiple transformed images from the same source or of applying
   * multiple transformations simultaneously, in order to eliminate the need to
   * read the source coefficients multiple times.
   *
   * @param dstBufs an array of image buffers.  <code>dstbufs[i]</code> will
   * receive a JPEG image that has been transformed using the parameters in
   * <code>transforms[i]</code>.  Use {@link TJ#bufSize} to determine the
   * maximum size for each buffer based on the transformed or cropped width and
   * height and the level of subsampling used in the source image.
   *
   * @param transforms an array of {@link TJTransform} instances, each of
   * which specifies the transform parameters and/or cropping region for the
   * corresponding transformed output image
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   */
  public void transform(byte[][] dstBufs, TJTransform[] transforms,
                        int flags) throws TJException {
    if (jpegBuf == null)
      throw new IllegalStateException("JPEG buffer not initialized");
    transformedSizes = transform(jpegBuf, jpegBufSize, dstBufs, transforms,
                                 flags);
  }

  /**
   * Losslessly transform the JPEG image associated with this transformer
   * instance and return an array of {@link TJDecompressor} instances, each of
   * which has a transformed JPEG image associated with it.
   *
   * @param transforms an array of {@link TJTransform} instances, each of
   * which specifies the transform parameters and/or cropping region for the
   * corresponding transformed output image
   *
   * @param flags the bitwise OR of one or more of
   * {@link TJ#FLAG_BOTTOMUP TJ.FLAG_*}
   *
   * @return an array of {@link TJDecompressor} instances, each of
   * which has a transformed JPEG image associated with it.
   */
  public TJDecompressor[] transform(TJTransform[] transforms, int flags)
                                    throws TJException {
    byte[][] dstBufs = new byte[transforms.length][];
    if (jpegWidth < 1 || jpegHeight < 1)
      throw new IllegalStateException("JPEG buffer not initialized");
    for (int i = 0; i < transforms.length; i++) {
      int w = jpegWidth, h = jpegHeight;
      if ((transforms[i].options & TJTransform.OPT_CROP) != 0) {
        if (transforms[i].width != 0) w = transforms[i].width;
        if (transforms[i].height != 0) h = transforms[i].height;
      }
      dstBufs[i] = new byte[TJ.bufSize(w, h, jpegSubsamp)];
    }
    TJDecompressor[] tjd = new TJDecompressor[transforms.length];
    transform(dstBufs, transforms, flags);
    for (int i = 0; i < transforms.length; i++)
      tjd[i] = new TJDecompressor(dstBufs[i], transformedSizes[i]);
    return tjd;
  }

  /**
   * Returns an array containing the sizes of the transformed JPEG images
   * generated by the most recent transform operation.
   *
   * @return an array containing the sizes of the transformed JPEG images
   * generated by the most recent transform operation.
   */
  public int[] getTransformedSizes() {
    if (transformedSizes == null)
      throw new IllegalStateException("No image has been transformed yet");
    return transformedSizes;
  }

  private native void init() throws TJException;

  private native int[] transform(byte[] srcBuf, int srcSize, byte[][] dstBufs,
    TJTransform[] transforms, int flags) throws TJException;

  static {
    TJLoader.load();
  }

  private int[] transformedSizes = null;
}

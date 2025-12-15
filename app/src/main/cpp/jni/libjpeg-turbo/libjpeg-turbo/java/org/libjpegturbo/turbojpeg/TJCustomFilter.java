/*
 * Copyright (C)2011, 2013 D. R. Commander.  All Rights Reserved.
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

import java.awt.*;
import java.nio.*;

/**
 * Custom filter callback interface
 */
public interface TJCustomFilter {

  /**
   * A callback function that can be used to modify the DCT coefficients after
   * they are losslessly transformed but before they are transcoded to a new
   * JPEG image.  This allows for custom filters or other transformations to be
   * applied in the frequency domain.
   *
   * @param coeffBuffer a buffer containing transformed DCT coefficients.
   * (NOTE: this buffer is not guaranteed to be valid once the callback
   * returns, so applications wishing to hand off the DCT coefficients to
   * another function or library should make a copy of them within the body of
   * the callback.)
   *
   * @param bufferRegion rectangle containing the width and height of
   * <code>coeffBuffer</code> as well as its offset relative to the component
   * plane.  TurboJPEG implementations may choose to split each component plane
   * into multiple DCT coefficient buffers and call the callback function once
   * for each buffer.
   *
   * @param planeRegion rectangle containing the width and height of the
   * component plane to which <code>coeffBuffer</code> belongs
   *
   * @param componentID ID number of the component plane to which
   * <code>coeffBuffer</code> belongs (Y, Cb, and Cr have, respectively, ID's
   * of 0, 1, and 2 in typical JPEG images.)
   *
   * @param transformID ID number of the transformed image to which
   * <code>coeffBuffer</code> belongs.  This is the same as the index of the
   * transform in the <code>transforms</code> array that was passed to {@link
   * TJTransformer#transform TJTransformer.transform()}.
   *
   * @param transform a {@link TJTransform} instance that specifies the
   * parameters and/or cropping region for this transform
   */
  void customFilter(ShortBuffer coeffBuffer, Rectangle bufferRegion,
                    Rectangle planeRegion, int componentID, int transformID,
                    TJTransform transform)
    throws TJException;
}

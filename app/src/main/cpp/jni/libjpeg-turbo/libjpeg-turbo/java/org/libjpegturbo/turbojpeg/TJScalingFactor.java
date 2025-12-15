/*
 * Copyright (C)2011, 2018 D. R. Commander.  All Rights Reserved.
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
 * Fractional scaling factor
 */
public class TJScalingFactor {

  /**
   * Create a TurboJPEG scaling factor instance.
   *
   * @param num numerator
   * @param denom denominator
   */
  @SuppressWarnings("checkstyle:HiddenField")
  public TJScalingFactor(int num, int denom) {
    if (num < 1 || denom < 1)
      throw new IllegalArgumentException("Numerator and denominator must be >= 1");
    this.num = num;
    this.denom = denom;
  }

  /**
   * Returns numerator
   *
   * @return numerator
   */
  public int getNum() {
    return num;
  }

  /**
   * Returns denominator
   *
   * @return denominator
   */
  public int getDenom() {
    return denom;
  }

  /**
   * Returns the scaled value of <code>dimension</code>.  This function
   * performs the integer equivalent of
   * <code>ceil(dimension * scalingFactor)</code>.
   *
   * @param dimension width or height to multiply by this scaling factor
   *
   * @return the scaled value of <code>dimension</code>.
   */
  public int getScaled(int dimension) {
    return (dimension * num + denom - 1) / denom;
  }

  /**
   * Returns true or false, depending on whether this instance and
   * <code>other</code> have the same numerator and denominator.
   *
   * @param other the scaling factor against which to compare this one
   *
   * @return true or false, depending on whether this instance and
   * <code>other</code> have the same numerator and denominator.
   */
  public boolean equals(TJScalingFactor other) {
    return this.num == other.num && this.denom == other.denom;
  }

  /**
   * Returns true or false, depending on whether this instance is equal to
   * 1/1.
   *
   * @return true or false, depending on whether this instance is equal to
   * 1/1.
   */
  public boolean isOne() {
    return num == 1 && denom == 1;
  }

  /**
   * Numerator
   */
  private int num = 1;

  /**
   * Denominator
   */
  private int denom = 1;
}

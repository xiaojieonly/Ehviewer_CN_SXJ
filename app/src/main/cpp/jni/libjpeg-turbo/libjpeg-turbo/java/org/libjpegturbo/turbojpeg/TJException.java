/*
 * Copyright (C)2015 Viktor Szathmáry.  All Rights Reserved.
 * Copyright (C)2017-2018 D. R. Commander.  All Rights Reserved.
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

import java.io.IOException;

@SuppressWarnings("checkstyle:JavadocType")
public class TJException extends IOException {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("checkstyle:JavadocMethod")
  public TJException() {
    super();
  }

  @SuppressWarnings("checkstyle:JavadocMethod")
  public TJException(String message, Throwable cause) {
    super(message, cause);
  }

  @SuppressWarnings("checkstyle:JavadocMethod")
  public TJException(String message) {
    super(message);
  }

  @SuppressWarnings("checkstyle:JavadocMethod")
  public TJException(String message, int code) {
    super(message);
    if (errorCode >= 0 && errorCode < TJ.NUMERR)
      errorCode = code;
  }

  @SuppressWarnings("checkstyle:JavadocMethod")
  public TJException(Throwable cause) {
    super(cause);
  }

  /**
   * Returns a code (one of {@link TJ TJ.ERR_*}) indicating the severity of the
   * last error.
   *
   * @return a code (one of {@link TJ TJ.ERR_*}) indicating the severity of the
   * last error.
   */
  public int getErrorCode() {
    return errorCode;
  }

  private int errorCode = TJ.ERR_FATAL;
}

// Copyright 2018 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// giflib doesn't have a Unicode DGifOpenFileName(). Let's make one.
//
// Author: Yannis Guyon (yguyon@google.com)

#ifndef WEBP_EXAMPLES_UNICODE_GIF_H_
#define WEBP_EXAMPLES_UNICODE_GIF_H_

#include "./unicode.h"
#ifdef HAVE_CONFIG_H
#include "webp/config.h"  // For WEBP_HAVE_GIF
#endif

#if defined(WEBP_HAVE_GIF)

#ifdef _WIN32
#include <fcntl.h>  // Not standard, needed for _topen and flags.
#include <io.h>
#endif

#include <gif_lib.h>
#include <string.h>
#include "./gifdec.h"

#if !defined(STDIN_FILENO)
#define STDIN_FILENO 0
#endif

static GifFileType* DGifOpenFileUnicode(const W_CHAR* file_name, int* error) {
  if (!WSTRCMP(file_name, "-")) {
#if LOCAL_GIF_PREREQ(5, 0)
    return DGifOpenFileHandle(STDIN_FILENO, error);
#else
    (void)error;
    return DGifOpenFileHandle(STDIN_FILENO);
#endif
  }

#if defined(_WIN32) && defined(_UNICODE)
  {
    int file_handle = _wopen(file_name, _O_RDONLY | _O_BINARY);
    if (file_handle == -1) {
      if (error != NULL) *error = D_GIF_ERR_OPEN_FAILED;
      return NULL;
    }

#if LOCAL_GIF_PREREQ(5, 0)
    return DGifOpenFileHandle(file_handle, error);
#else
    return DGifOpenFileHandle(file_handle);
#endif
  }

#else

#if LOCAL_GIF_PREREQ(5, 0)
  return DGifOpenFileName(file_name, error);
#else
  return DGifOpenFileName(file_name);
#endif

#endif  // defined(_WIN32) && defined(_UNICODE)
  // DGifCloseFile() is called later.
}

#endif  // defined(WEBP_HAVE_GIF)

#endif  // WEBP_EXAMPLES_UNICODE_GIF_H_

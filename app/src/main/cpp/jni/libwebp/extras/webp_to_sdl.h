// Copyright 2017 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Simple WebP-to-SDL wrapper. Useful for emscripten.
//
// Author: James Zern (jzern@google.com)

#ifndef WEBP_EXTRAS_WEBP_TO_SDL_H_
#define WEBP_EXTRAS_WEBP_TO_SDL_H_

// Exports the method WebPToSDL(const char* data, int data_size) which decodes
// a WebP bitstream into an RGBA SDL surface.
// Return false on failure.
extern int WebPToSDL(const char* data, unsigned int data_size);

#endif  // WEBP_EXTRAS_WEBP_TO_SDL_H_

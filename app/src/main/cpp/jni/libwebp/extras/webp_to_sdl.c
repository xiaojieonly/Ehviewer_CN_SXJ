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

#ifdef HAVE_CONFIG_H
#include "../webp/config.h"
#endif

#if defined(WEBP_HAVE_SDL)

#include "webp_to_sdl.h"

#include <stdio.h>

#include "../src/webp/decode.h"

#if defined(WEBP_HAVE_JUST_SDL_H)
#include <SDL.h>
#else
#include <SDL2/SDL.h>
#endif

static int init_ok = 0;
int WebPToSDL(const char* data, unsigned int data_size) {
  int ok = 0;
  VP8StatusCode status;
  WebPBitstreamFeatures input;
  uint8_t* output = NULL;
  SDL_Window* window = NULL;
  SDL_Renderer* renderer = NULL;
  SDL_Texture* texture = NULL;
  int width, height;

  if (!init_ok) {
    SDL_Init(SDL_INIT_VIDEO);
    init_ok = 1;
  }

  status = WebPGetFeatures((uint8_t*)data, (size_t)data_size, &input);
  if (status != VP8_STATUS_OK) goto Error;
  width = input.width;
  height = input.height;

  SDL_CreateWindowAndRenderer(width, height, 0, &window, &renderer);
  if (window == NULL || renderer == NULL) {
    fprintf(stderr, "Unable to create window or renderer!\n");
    goto Error;
  }
  SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY,
              "linear");  // make the scaled rendering look smoother.
  SDL_RenderSetLogicalSize(renderer, width, height);

  texture = SDL_CreateTexture(renderer, SDL_PIXELFORMAT_ABGR8888,
                              SDL_TEXTUREACCESS_STREAMING, width, height);
  if (texture == NULL) {
    fprintf(stderr, "Unable to create %dx%d RGBA texture!\n", width, height);
    goto Error;
  }

#if SDL_BYTEORDER == SDL_BIG_ENDIAN
  output = WebPDecodeBGRA((const uint8_t*)data, (size_t)data_size, &width,
                          &height);
#else
  output = WebPDecodeRGBA((const uint8_t*)data, (size_t)data_size, &width,
                          &height);
#endif
  if (output == NULL) {
    fprintf(stderr, "Error decoding image (%d)\n", status);
    goto Error;
  }

  SDL_UpdateTexture(texture, NULL, output, width * sizeof(uint32_t));
  SDL_RenderClear(renderer);
  SDL_RenderCopy(renderer, texture, NULL, NULL);
  SDL_RenderPresent(renderer);
  ok = 1;

 Error:
  // We should call SDL_DestroyWindow(window) but that makes .js fail.
  SDL_DestroyRenderer(renderer);
  SDL_DestroyTexture(texture);
  WebPFree(output);
  return ok;
}

//------------------------------------------------------------------------------

#endif  // WEBP_HAVE_SDL

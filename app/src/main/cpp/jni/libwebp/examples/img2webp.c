// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  generate an animated WebP out of a sequence of images
//  (PNG, JPEG, ...)
//
//  Example usage:
//     img2webp -o out.webp -q 40 -mixed -duration 40 input??.png
//
// Author: skal@google.com (Pascal Massimino)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include "../examples/example_util.h"
#include "../imageio/image_dec.h"
#include "../imageio/imageio_util.h"
#include "./stopwatch.h"
#include "./unicode.h"
#include "../sharpyuv/sharpyuv.h
#include "webp/encode.h"
#include "webp/mux.h"

//------------------------------------------------------------------------------

static void Help(void) {
  printf("Usage:\n\n");
  printf("  img2webp [file_options] [[frame_options] frame_file]...");
  printf(" [-o webp_file]\n\n");

  printf("File-level options (only used at the start of compression):\n");
  printf(" -min_size ............ minimize size\n");
  printf(" -kmax <int> .......... maximum number of frame between key-frames\n"
         "                        (0=only keyframes)\n");
  printf(" -kmin <int> .......... minimum number of frame between key-frames\n"
         "                        (0=disable key-frames altogether)\n");
  printf(" -mixed ............... use mixed lossy/lossless automatic mode\n");
  printf(" -near_lossless <int> . use near-lossless image preprocessing\n"
         "                        (0..100=off), default=100\n");
  printf(" -sharp_yuv ........... use sharper (and slower) RGB->YUV "
                                  "conversion\n                        "
                                  "(lossy only)\n");
  printf(" -loop <int> .......... loop count (default: 0, = infinite loop)\n");
  printf(" -v ................... verbose mode\n");
  printf(" -h ................... this help\n");
  printf(" -version ............. print version number and exit\n");
  printf("\n");

  printf("Per-frame options (only used for subsequent images input):\n");
  printf(" -d <int> ............. frame duration in ms (default: 100)\n");
  printf(" -lossless ............ use lossless mode (default)\n");
  printf(" -lossy ............... use lossy mode\n");
  printf(" -q <float> ........... quality\n");
  printf(" -m <int> ............. method to use\n");
  printf(" -exact, -noexact ..... preserve or alter RGB values in transparent "
                                  "area\n"
         "                        (default: -noexact, may cause artifacts\n"
         "                                  with lossy animations)\n");

  printf("\n");
  printf("example: img2webp -loop 2 in0.png -lossy in1.jpg\n"
         "                  -d 80 in2.tiff -o out.webp\n");
  printf("\nNote: if a single file name is passed as the argument, the "
         "arguments will be\n");
  printf("tokenized from this file. The file name must not start with "
         "the character '-'.\n");
  printf("\nSupported input formats:\n  %s\n",
         WebPGetEnabledInputFileFormats());
}

//------------------------------------------------------------------------------

static int ReadImage(const char filename[], WebPPicture* const pic) {
  const uint8_t* data = NULL;
  size_t data_size = 0;
  WebPImageReader reader;
  int ok;
#ifdef HAVE_WINCODEC_H
  // Try to decode the file using WIC falling back to the other readers for
  // e.g., WebP.
  ok = ReadPictureWithWIC(filename, pic, 1, NULL);
  if (ok) return 1;
#endif
  if (!ImgIoUtilReadFile(filename, &data, &data_size)) return 0;
  reader = WebPGuessImageReader(data, data_size);
  ok = reader(data, data_size, pic, 1, NULL);
  WebPFree((void*)data);
  return ok;
}

static int SetLoopCount(int loop_count, WebPData* const webp_data) {
  int ok = 1;
  WebPMuxError err;
  uint32_t features;
  WebPMuxAnimParams new_params;
  WebPMux* const mux = WebPMuxCreate(webp_data, 1);
  if (mux == NULL) return 0;

  err = WebPMuxGetFeatures(mux, &features);
  ok = (err == WEBP_MUX_OK);
  if (!ok || !(features & ANIMATION_FLAG)) goto End;

  err = WebPMuxGetAnimationParams(mux, &new_params);
  ok = (err == WEBP_MUX_OK);
  if (ok) {
    new_params.loop_count = loop_count;
    err = WebPMuxSetAnimationParams(mux, &new_params);
    ok = (err == WEBP_MUX_OK);
  }
  if (ok) {
    WebPDataClear(webp_data);
    err = WebPMuxAssemble(mux, webp_data);
    ok = (err == WEBP_MUX_OK);
  }

 End:
  WebPMuxDelete(mux);
  if (!ok) {
    fprintf(stderr, "Error during loop-count setting\n");
  }
  return ok;
}

//------------------------------------------------------------------------------

// Returns EXIT_SUCCESS on success, EXIT_FAILURE on failure.
int main(int argc, const char* argv[]) {
  const char* output = NULL;
  WebPAnimEncoder* enc = NULL;
  int verbose = 0;
  int pic_num = 0;
  int duration = 100;
  int timestamp_ms = 0;
  int loop_count = 0;
  int width = 0, height = 0;
  WebPAnimEncoderOptions anim_config;
  WebPConfig config;
  WebPPicture pic;
  WebPData webp_data;
  int c;
  int have_input = 0;
  CommandLineArguments cmd_args;
  int ok;

  INIT_WARGV(argc, argv);

  ok = ExUtilInitCommandLineArguments(argc - 1, argv + 1, &cmd_args);
  if (!ok) FREE_WARGV_AND_RETURN(EXIT_FAILURE);

  argc = cmd_args.argc_;
  argv = cmd_args.argv_;

  WebPDataInit(&webp_data);
  if (!WebPAnimEncoderOptionsInit(&anim_config) ||
      !WebPConfigInit(&config) ||
      !WebPPictureInit(&pic)) {
    fprintf(stderr, "Library version mismatch!\n");
    ok = 0;
    goto End;
  }

  // 1st pass of option parsing
  for (c = 0; ok && c < argc; ++c) {
    if (argv[c][0] == '-') {
      int parse_error = 0;
      if (!strcmp(argv[c], "-o") && c + 1 < argc) {
        argv[c] = NULL;
        output = (const char*)GET_WARGV_SHIFTED(argv, ++c);
      } else if (!strcmp(argv[c], "-kmin") && c + 1 < argc) {
        argv[c] = NULL;
        anim_config.kmin = ExUtilGetInt(argv[++c], 0, &parse_error);
      } else if (!strcmp(argv[c], "-kmax") && c + 1 < argc) {
        argv[c] = NULL;
        anim_config.kmax = ExUtilGetInt(argv[++c], 0, &parse_error);
      } else if (!strcmp(argv[c], "-loop") && c + 1 < argc) {
        argv[c] = NULL;
        loop_count = ExUtilGetInt(argv[++c], 0, &parse_error);
        if (loop_count < 0) {
          fprintf(stderr, "Invalid non-positive loop-count (%d)\n", loop_count);
          parse_error = 1;
        }
      } else if (!strcmp(argv[c], "-min_size")) {
        anim_config.minimize_size = 1;
      } else if (!strcmp(argv[c], "-mixed")) {
        anim_config.allow_mixed = 1;
        config.lossless = 0;
      } else if (!strcmp(argv[c], "-near_lossless") && c + 1 < argc) {
        argv[c] = NULL;
        config.near_lossless = ExUtilGetInt(argv[++c], 0, &parse_error);
      } else if (!strcmp(argv[c], "-sharp_yuv")) {
        config.use_sharp_yuv = 1;
      } else if (!strcmp(argv[c], "-v")) {
        verbose = 1;
      } else if (!strcmp(argv[c], "-h") || !strcmp(argv[c], "-help")) {
        Help();
        FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
      } else if (!strcmp(argv[c], "-version")) {
        const int enc_version = WebPGetEncoderVersion();
        const int mux_version = WebPGetMuxVersion();
        const int sharpyuv_version = SharpYuvGetVersion();
        printf("WebP Encoder version: %d.%d.%d\nWebP Mux version: %d.%d.%d\n",
               (enc_version >> 16) & 0xff, (enc_version >> 8) & 0xff,
               enc_version & 0xff, (mux_version >> 16) & 0xff,
               (mux_version >> 8) & 0xff, mux_version & 0xff);
        printf("libsharpyuv: %d.%d.%d\n", (sharpyuv_version >> 24) & 0xff,
               (sharpyuv_version >> 16) & 0xffff, sharpyuv_version & 0xff);
        goto End;
      } else {
        continue;
      }
      ok = !parse_error;
      if (!ok) goto End;
      argv[c] = NULL;   // mark option as 'parsed' during 1st pass
    } else {
      have_input |= 1;
    }
  }
  if (!have_input) {
    fprintf(stderr, "No input file(s) for generating animation!\n");
    goto End;
  }

  // image-reading pass
  pic_num = 0;
  config.lossless = 1;
  for (c = 0; ok && c < argc; ++c) {
    if (argv[c] == NULL) continue;
    if (argv[c][0] == '-') {    // parse local options
      int parse_error = 0;
      if (!strcmp(argv[c], "-lossy")) {
        if (!anim_config.allow_mixed) config.lossless = 0;
      } else if (!strcmp(argv[c], "-lossless")) {
        if (!anim_config.allow_mixed) config.lossless = 1;
      } else if (!strcmp(argv[c], "-q") && c + 1 < argc) {
        config.quality = ExUtilGetFloat(argv[++c], &parse_error);
      } else if (!strcmp(argv[c], "-m") && c + 1 < argc) {
        config.method = ExUtilGetInt(argv[++c], 0, &parse_error);
      } else if (!strcmp(argv[c], "-d") && c + 1 < argc) {
        duration = ExUtilGetInt(argv[++c], 0, &parse_error);
        if (duration <= 0) {
          fprintf(stderr, "Invalid negative duration (%d)\n", duration);
          parse_error = 1;
        }
      } else if (!strcmp(argv[c], "-exact")) {
        config.exact = 1;
      } else if (!strcmp(argv[c], "-noexact")) {
        config.exact = 0;
      } else {
        parse_error = 1;   // shouldn't be here.
        fprintf(stderr, "Unknown option [%s]\n", argv[c]);
      }
      ok = !parse_error;
      if (!ok) goto End;
      continue;
    }

    if (ok) {
      ok = WebPValidateConfig(&config);
      if (!ok) {
        fprintf(stderr, "Invalid configuration.\n");
        goto End;
      }
    }

    // read next input image
    pic.use_argb = 1;
    ok = ReadImage((const char*)GET_WARGV_SHIFTED(argv, c), &pic);
    if (!ok) goto End;

    if (enc == NULL) {
      width  = pic.width;
      height = pic.height;
      enc = WebPAnimEncoderNew(width, height, &anim_config);
      ok = (enc != NULL);
      if (!ok) {
        fprintf(stderr, "Could not create WebPAnimEncoder object.\n");
      }
    }

    if (ok) {
      ok = (width == pic.width && height == pic.height);
      if (!ok) {
        fprintf(stderr, "Frame #%d dimension mismatched! "
                        "Got %d x %d. Was expecting %d x %d.\n",
                pic_num, pic.width, pic.height, width, height);
      }
    }

    if (ok) {
      ok = WebPAnimEncoderAdd(enc, &pic, timestamp_ms, &config);
      if (!ok) {
        fprintf(stderr, "Error while adding frame #%d\n", pic_num);
      }
    }
    WebPPictureFree(&pic);
    if (!ok) goto End;

    if (verbose) {
      WFPRINTF(stderr, "Added frame #%3d at time %4d (file: %s)\n",
               pic_num, timestamp_ms, GET_WARGV_SHIFTED(argv, c));
    }
    timestamp_ms += duration;
    ++pic_num;
  }

  // add a last fake frame to signal the last duration
  ok = ok && WebPAnimEncoderAdd(enc, NULL, timestamp_ms, NULL);
  ok = ok && WebPAnimEncoderAssemble(enc, &webp_data);
  if (!ok) {
    fprintf(stderr, "Error during final animation assembly.\n");
  }

 End:
  // free resources
  WebPAnimEncoderDelete(enc);

  if (ok && loop_count > 0) {  // Re-mux to add loop count.
    ok = SetLoopCount(loop_count, &webp_data);
  }

  if (ok) {
    if (output != NULL) {
      ok = ImgIoUtilWriteFile(output, webp_data.bytes, webp_data.size);
      if (ok) WFPRINTF(stderr, "output file: %s     ", (const W_CHAR*)output);
    } else {
      fprintf(stderr, "[no output file specified]   ");
    }
  }

  if (ok) {
    fprintf(stderr, "[%d frames, %u bytes].\n",
            pic_num, (unsigned int)webp_data.size);
  }
  WebPDataClear(&webp_data);
  ExUtilDeleteCommandLineArguments(&cmd_args);
  FREE_WARGV_AND_RETURN(ok ? EXIT_SUCCESS : EXIT_FAILURE);
}

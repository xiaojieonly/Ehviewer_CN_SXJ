// Copyright 2011 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Simple command-line to create a WebP container file and to extract or strip
//  relevant data from the container file.
//
// Authors: Vikas (vikaas.arora@gmail.com),
//          Urvang (urvang@google.com)

/*  Usage examples:

  Create container WebP file:
    webpmux -frame anim_1.webp +100+10+10   \
            -frame anim_2.webp +100+25+25+1 \
            -frame anim_3.webp +100+50+50+1 \
            -frame anim_4.webp +100         \
            -loop 10 -bgcolor 128,255,255,255 \
            -o out_animation_container.webp

    webpmux -set icc image_profile.icc in.webp -o out_icc_container.webp
    webpmux -set exif image_metadata.exif in.webp -o out_exif_container.webp
    webpmux -set xmp image_metadata.xmp in.webp -o out_xmp_container.webp
    webpmux -set loop 1 in.webp -o out_looped.webp

  Extract relevant data from WebP container file:
    webpmux -get frame n in.webp -o out_frame.webp
    webpmux -get icc in.webp -o image_profile.icc
    webpmux -get exif in.webp -o image_metadata.exif
    webpmux -get xmp in.webp -o image_metadata.xmp

  Strip data from WebP Container file:
    webpmux -strip icc in.webp -o out.webp
    webpmux -strip exif in.webp -o out.webp
    webpmux -strip xmp in.webp -o out.webp

  Change duration of frame intervals:
    webpmux -duration 150 in.webp -o out.webp
    webpmux -duration 33,2 in.webp -o out.webp
    webpmux -duration 200,10,0 -duration 150,6,50 in.webp -o out.webp

  Misc:
    webpmux -info in.webp
    webpmux [ -h | -help ]
    webpmux -version
    webpmux argument_file_name
*/

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "webp/decode.h"
#include "webp/mux.h"
#include "../examples/example_util.h"
#include "../imageio/imageio_util.h"
#include "./unicode.h"

//------------------------------------------------------------------------------
// Config object to parse command-line arguments.

typedef enum {
  NIL_ACTION = 0,
  ACTION_GET,
  ACTION_SET,
  ACTION_STRIP,
  ACTION_INFO,
  ACTION_HELP,
  ACTION_DURATION
} ActionType;

typedef enum {
  NIL_SUBTYPE = 0,
  SUBTYPE_ANMF,
  SUBTYPE_LOOP,
  SUBTYPE_BGCOLOR
} FeatureSubType;

typedef struct {
  FeatureSubType subtype_;
  const char* filename_;
  const char* params_;
} FeatureArg;

typedef enum {
  NIL_FEATURE = 0,
  FEATURE_EXIF,
  FEATURE_XMP,
  FEATURE_ICCP,
  FEATURE_ANMF,
  FEATURE_DURATION,
  FEATURE_LOOP,
  FEATURE_BGCOLOR,
  LAST_FEATURE
} FeatureType;

static const char* const kFourccList[LAST_FEATURE] = {
  NULL, "EXIF", "XMP ", "ICCP", "ANMF"
};

static const char* const kDescriptions[LAST_FEATURE] = {
  NULL, "EXIF metadata", "XMP metadata", "ICC profile",
  "Animation frame"
};

typedef struct {
  CommandLineArguments cmd_args_;

  ActionType action_type_;
  const char* input_;
  const char* output_;
  FeatureType type_;
  FeatureArg* args_;
  int arg_count_;
} Config;

//------------------------------------------------------------------------------
// Helper functions.

static int CountOccurrences(const CommandLineArguments* const args,
                            const char* const arg) {
  int i;
  int num_occurences = 0;

  for (i = 0; i < args->argc_; ++i) {
    if (!strcmp(args->argv_[i], arg)) {
      ++num_occurences;
    }
  }
  return num_occurences;
}

static const char* const kErrorMessages[-WEBP_MUX_NOT_ENOUGH_DATA + 1] = {
  "WEBP_MUX_NOT_FOUND", "WEBP_MUX_INVALID_ARGUMENT", "WEBP_MUX_BAD_DATA",
  "WEBP_MUX_MEMORY_ERROR", "WEBP_MUX_NOT_ENOUGH_DATA"
};

static const char* ErrorString(WebPMuxError err) {
  assert(err <= WEBP_MUX_NOT_FOUND && err >= WEBP_MUX_NOT_ENOUGH_DATA);
  return kErrorMessages[-err];
}

#define RETURN_IF_ERROR(ERR_MSG)                                     \
  do {                                                               \
    if (err != WEBP_MUX_OK) {                                        \
      fprintf(stderr, ERR_MSG);                                      \
      return err;                                                    \
    }                                                                \
  } while (0)

#define RETURN_IF_ERROR3(ERR_MSG, FORMAT_STR1, FORMAT_STR2)          \
  do {                                                               \
    if (err != WEBP_MUX_OK) {                                        \
      fprintf(stderr, ERR_MSG, FORMAT_STR1, FORMAT_STR2);            \
      return err;                                                    \
    }                                                                \
  } while (0)

#define ERROR_GOTO1(ERR_MSG, LABEL)                                  \
  do {                                                               \
    fprintf(stderr, ERR_MSG);                                        \
    ok = 0;                                                          \
    goto LABEL;                                                      \
  } while (0)

#define ERROR_GOTO2(ERR_MSG, FORMAT_STR, LABEL)                      \
  do {                                                               \
    fprintf(stderr, ERR_MSG, FORMAT_STR);                            \
    ok = 0;                                                          \
    goto LABEL;                                                      \
  } while (0)

#define ERROR_GOTO3(ERR_MSG, FORMAT_STR1, FORMAT_STR2, LABEL)        \
  do {                                                               \
    fprintf(stderr, ERR_MSG, FORMAT_STR1, FORMAT_STR2);              \
    ok = 0;                                                          \
    goto LABEL;                                                      \
  } while (0)

static WebPMuxError DisplayInfo(const WebPMux* mux) {
  int width, height;
  uint32_t flag;

  WebPMuxError err = WebPMuxGetCanvasSize(mux, &width, &height);
  assert(err == WEBP_MUX_OK);  // As WebPMuxCreate() was successful earlier.
  printf("Canvas size: %d x %d\n", width, height);

  err = WebPMuxGetFeatures(mux, &flag);
  RETURN_IF_ERROR("Failed to retrieve features\n");

  if (flag == 0) {
    printf("No features present.\n");
    return err;
  }

  // Print the features present.
  printf("Features present:");
  if (flag & ANIMATION_FLAG) printf(" animation");
  if (flag & ICCP_FLAG)      printf(" ICC profile");
  if (flag & EXIF_FLAG)      printf(" EXIF metadata");
  if (flag & XMP_FLAG)       printf(" XMP metadata");
  if (flag & ALPHA_FLAG)     printf(" transparency");
  printf("\n");

  if (flag & ANIMATION_FLAG) {
    const WebPChunkId id = WEBP_CHUNK_ANMF;
    const char* const type_str = "frame";
    int nFrames;

    WebPMuxAnimParams params;
    err = WebPMuxGetAnimationParams(mux, &params);
    assert(err == WEBP_MUX_OK);
    printf("Background color : 0x%.8X  Loop Count : %d\n",
           params.bgcolor, params.loop_count);

    err = WebPMuxNumChunks(mux, id, &nFrames);
    assert(err == WEBP_MUX_OK);

    printf("Number of %ss: %d\n", type_str, nFrames);
    if (nFrames > 0) {
      int i;
      printf("No.: width height alpha x_offset y_offset ");
      printf("duration   dispose blend ");
      printf("image_size  compression\n");
      for (i = 1; i <= nFrames; i++) {
        WebPMuxFrameInfo frame;
        err = WebPMuxGetFrame(mux, i, &frame);
        if (err == WEBP_MUX_OK) {
          WebPBitstreamFeatures features;
          const VP8StatusCode status = WebPGetFeatures(
              frame.bitstream.bytes, frame.bitstream.size, &features);
          assert(status == VP8_STATUS_OK);  // Checked by WebPMuxCreate().
          (void)status;
          printf("%3d: %5d %5d %5s %8d %8d ", i, features.width,
                 features.height, features.has_alpha ? "yes" : "no",
                 frame.x_offset, frame.y_offset);
          {
            const char* const dispose =
                (frame.dispose_method == WEBP_MUX_DISPOSE_NONE) ? "none"
                                                                : "background";
            const char* const blend =
                (frame.blend_method == WEBP_MUX_BLEND) ? "yes" : "no";
            printf("%8d %10s %5s ", frame.duration, dispose, blend);
          }
          printf("%10d %11s\n", (int)frame.bitstream.size,
                 (features.format == 1) ? "lossy" :
                 (features.format == 2) ? "lossless" :
                                          "undefined");
        }
        WebPDataClear(&frame.bitstream);
        RETURN_IF_ERROR3("Failed to retrieve %s#%d\n", type_str, i);
      }
    }
  }

  if (flag & ICCP_FLAG) {
    WebPData icc_profile;
    err = WebPMuxGetChunk(mux, "ICCP", &icc_profile);
    assert(err == WEBP_MUX_OK);
    printf("Size of the ICC profile data: %d\n", (int)icc_profile.size);
  }

  if (flag & EXIF_FLAG) {
    WebPData exif;
    err = WebPMuxGetChunk(mux, "EXIF", &exif);
    assert(err == WEBP_MUX_OK);
    printf("Size of the EXIF metadata: %d\n", (int)exif.size);
  }

  if (flag & XMP_FLAG) {
    WebPData xmp;
    err = WebPMuxGetChunk(mux, "XMP ", &xmp);
    assert(err == WEBP_MUX_OK);
    printf("Size of the XMP metadata: %d\n", (int)xmp.size);
  }

  if ((flag & ALPHA_FLAG) && !(flag & ANIMATION_FLAG)) {
    WebPMuxFrameInfo image;
    err = WebPMuxGetFrame(mux, 1, &image);
    if (err == WEBP_MUX_OK) {
      printf("Size of the image (with alpha): %d\n", (int)image.bitstream.size);
    }
    WebPDataClear(&image.bitstream);
    RETURN_IF_ERROR("Failed to retrieve the image\n");
  }

  return WEBP_MUX_OK;
}

static void PrintHelp(void) {
  printf("Usage: webpmux -get GET_OPTIONS INPUT -o OUTPUT\n");
  printf("       webpmux -set SET_OPTIONS INPUT -o OUTPUT\n");
  printf("       webpmux -duration DURATION_OPTIONS [-duration ...]\n");
  printf("               INPUT -o OUTPUT\n");
  printf("       webpmux -strip STRIP_OPTIONS INPUT -o OUTPUT\n");
  printf("       webpmux -frame FRAME_OPTIONS [-frame...] [-loop LOOP_COUNT]"
         "\n");
  printf("               [-bgcolor BACKGROUND_COLOR] -o OUTPUT\n");
  printf("       webpmux -info INPUT\n");
  printf("       webpmux [-h|-help]\n");
  printf("       webpmux -version\n");
  printf("       webpmux argument_file_name\n");

  printf("\n");
  printf("GET_OPTIONS:\n");
  printf(" Extract relevant data:\n");
  printf("   icc       get ICC profile\n");
  printf("   exif      get EXIF metadata\n");
  printf("   xmp       get XMP metadata\n");
  printf("   frame n   get nth frame\n");

  printf("\n");
  printf("SET_OPTIONS:\n");
  printf(" Set color profile/metadata/parameters:\n");
  printf("   loop LOOP_COUNT            set the loop count\n");
  printf("   bgcolor BACKGROUND_COLOR   set the animation background color\n");
  printf("   icc  file.icc              set ICC profile\n");
  printf("   exif file.exif             set EXIF metadata\n");
  printf("   xmp  file.xmp              set XMP metadata\n");
  printf("   where:    'file.icc' contains the ICC profile to be set,\n");
  printf("             'file.exif' contains the EXIF metadata to be set\n");
  printf("             'file.xmp' contains the XMP metadata to be set\n");

  printf("\n");
  printf("DURATION_OPTIONS:\n");
  printf(" Set duration of selected frames:\n");
  printf("   duration            set duration for all frames\n");
  printf("   duration,frame      set duration of a particular frame\n");
  printf("   duration,start,end  set duration of frames in the\n");
  printf("                        interval [start,end])\n");
  printf("   where: 'duration' is the duration in milliseconds\n");
  printf("          'start' is the start frame index\n");
  printf("          'end' is the inclusive end frame index\n");
  printf("           The special 'end' value '0' means: last frame.\n");

  printf("\n");
  printf("STRIP_OPTIONS:\n");
  printf(" Strip color profile/metadata:\n");
  printf("   icc       strip ICC profile\n");
  printf("   exif      strip EXIF metadata\n");
  printf("   xmp       strip XMP metadata\n");

  printf("\n");
  printf("FRAME_OPTIONS(i):\n");
  printf(" Create animation:\n");
  printf("   file_i +di[+xi+yi[+mi[bi]]]\n");
  printf("   where:    'file_i' is the i'th animation frame (WebP format),\n");
  printf("             'di' is the pause duration before next frame,\n");
  printf("             'xi','yi' specify the image offset for this frame,\n");
  printf("             'mi' is the dispose method for this frame (0 or 1),\n");
  printf("             'bi' is the blending method for this frame (+b or -b)"
         "\n");

  printf("\n");
  printf("LOOP_COUNT:\n");
  printf(" Number of times to repeat the animation.\n");
  printf(" Valid range is 0 to 65535 [Default: 0 (infinite)].\n");

  printf("\n");
  printf("BACKGROUND_COLOR:\n");
  printf(" Background color of the canvas.\n");
  printf("  A,R,G,B\n");
  printf("  where:    'A', 'R', 'G' and 'B' are integers in the range 0 to 255 "
         "specifying\n");
  printf("            the Alpha, Red, Green and Blue component values "
         "respectively\n");
  printf("            [Default: 255,255,255,255]\n");

  printf("\nINPUT & OUTPUT are in WebP format.\n");

  printf("\nNote: The nature of EXIF, XMP and ICC data is not checked");
  printf(" and is assumed to be\nvalid.\n");
  printf("\nNote: if a single file name is passed as the argument, the "
         "arguments will be\n");
  printf("tokenized from this file. The file name must not start with "
         "the character '-'.\n");
}

static void WarnAboutOddOffset(const WebPMuxFrameInfo* const info) {
  if ((info->x_offset | info->y_offset) & 1) {
    fprintf(stderr, "Warning: odd offsets will be snapped to even values"
            " (%d, %d) -> (%d, %d)\n", info->x_offset, info->y_offset,
            info->x_offset & ~1, info->y_offset & ~1);
  }
}

static int CreateMux(const char* const filename, WebPMux** mux) {
  WebPData bitstream;
  assert(mux != NULL);
  if (!ExUtilReadFileToWebPData(filename, &bitstream)) return 0;
  *mux = WebPMuxCreate(&bitstream, 1);
  WebPDataClear(&bitstream);
  if (*mux != NULL) return 1;
  WFPRINTF(stderr, "Failed to create mux object from file %s.\n",
           (const W_CHAR*)filename);
  return 0;
}

static int WriteData(const char* filename, const WebPData* const webpdata) {
  int ok = 0;
  FILE* fout = WSTRCMP(filename, "-") ? WFOPEN(filename, "wb")
                                      : ImgIoUtilSetBinaryMode(stdout);
  if (fout == NULL) {
    WFPRINTF(stderr, "Error opening output WebP file %s!\n",
             (const W_CHAR*)filename);
    return 0;
  }
  if (fwrite(webpdata->bytes, webpdata->size, 1, fout) != 1) {
    WFPRINTF(stderr, "Error writing file %s!\n", (const W_CHAR*)filename);
  } else {
    WFPRINTF(stderr, "Saved file %s (%d bytes)\n",
             (const W_CHAR*)filename, (int)webpdata->size);
    ok = 1;
  }
  if (fout != stdout) fclose(fout);
  return ok;
}

static int WriteWebP(WebPMux* const mux, const char* filename) {
  int ok;
  WebPData webp_data;
  const WebPMuxError err = WebPMuxAssemble(mux, &webp_data);
  if (err != WEBP_MUX_OK) {
    fprintf(stderr, "Error (%s) assembling the WebP file.\n", ErrorString(err));
    return 0;
  }
  ok = WriteData(filename, &webp_data);
  WebPDataClear(&webp_data);
  return ok;
}

static WebPMux* DuplicateMuxHeader(const WebPMux* const mux) {
  WebPMux* new_mux = WebPMuxNew();
  WebPMuxAnimParams p;
  WebPMuxError err;
  int i;
  int ok = 1;

  if (new_mux == NULL) return NULL;

  err = WebPMuxGetAnimationParams(mux, &p);
  if (err == WEBP_MUX_OK) {
    err = WebPMuxSetAnimationParams(new_mux, &p);
    if (err != WEBP_MUX_OK) {
      ERROR_GOTO2("Error (%s) handling animation params.\n",
                  ErrorString(err), End);
    }
  } else {
    /* it might not be an animation. Just keep moving. */
  }

  for (i = 1; i <= 3; ++i) {
    WebPData metadata;
    err = WebPMuxGetChunk(mux, kFourccList[i], &metadata);
    if (err == WEBP_MUX_OK && metadata.size > 0) {
      err = WebPMuxSetChunk(new_mux, kFourccList[i], &metadata, 1);
      if (err != WEBP_MUX_OK) {
        ERROR_GOTO1("Error transferring metadata in DuplicateMuxHeader().",
                    End);
      }
    }
  }

 End:
  if (!ok) {
    WebPMuxDelete(new_mux);
    new_mux = NULL;
  }
  return new_mux;
}

static int ParseFrameArgs(const char* args, WebPMuxFrameInfo* const info) {
  int dispose_method, unused;
  char plus_minus, blend_method;
  const int num_args = sscanf(args, "+%d+%d+%d+%d%c%c+%d", &info->duration,
                              &info->x_offset, &info->y_offset, &dispose_method,
                              &plus_minus, &blend_method, &unused);
  switch (num_args) {
    case 1:
      info->x_offset = info->y_offset = 0;  // fall through
    case 3:
      dispose_method = 0;  // fall through
    case 4:
      plus_minus = '+';
      blend_method = 'b';  // fall through
    case 6:
      break;
    case 2:
    case 5:
    default:
      return 0;
  }

  WarnAboutOddOffset(info);

  // Note: The validity of the following conversion is checked by
  // WebPMuxPushFrame().
  info->dispose_method = (WebPMuxAnimDispose)dispose_method;

  if (blend_method != 'b') return 0;
  if (plus_minus != '-' && plus_minus != '+') return 0;
  info->blend_method =
      (plus_minus == '+') ? WEBP_MUX_BLEND : WEBP_MUX_NO_BLEND;
  return 1;
}

static int ParseBgcolorArgs(const char* args, uint32_t* const bgcolor) {
  uint32_t a, r, g, b;
  if (sscanf(args, "%u,%u,%u,%u", &a, &r, &g, &b) != 4) return 0;
  if (a >= 256 || r >= 256 || g >= 256 || b >= 256) return 0;
  *bgcolor = (a << 24) | (r << 16) | (g << 8) | (b << 0);
  return 1;
}

//------------------------------------------------------------------------------
// Clean-up.

static void DeleteConfig(Config* const config) {
  if (config != NULL) {
    free(config->args_);
    ExUtilDeleteCommandLineArguments(&config->cmd_args_);
    memset(config, 0, sizeof(*config));
  }
}

//------------------------------------------------------------------------------
// Parsing.

// Basic syntactic checks on the command-line arguments.
// Returns 1 on valid, 0 otherwise.
// Also fills up num_feature_args to be number of feature arguments given.
// (e.g. if there are 4 '-frame's and 1 '-loop', then num_feature_args = 5).
static int ValidateCommandLine(const CommandLineArguments* const cmd_args,
                               int* num_feature_args) {
  int num_frame_args;
  int num_loop_args;
  int num_bgcolor_args;
  int num_durations_args;
  int ok = 1;

  assert(num_feature_args != NULL);
  *num_feature_args = 0;

  // Simple checks.
  if (CountOccurrences(cmd_args, "-get") > 1) {
    ERROR_GOTO1("ERROR: Multiple '-get' arguments specified.\n", ErrValidate);
  }
  if (CountOccurrences(cmd_args, "-set") > 1) {
    ERROR_GOTO1("ERROR: Multiple '-set' arguments specified.\n", ErrValidate);
  }
  if (CountOccurrences(cmd_args, "-strip") > 1) {
    ERROR_GOTO1("ERROR: Multiple '-strip' arguments specified.\n", ErrValidate);
  }
  if (CountOccurrences(cmd_args, "-info") > 1) {
    ERROR_GOTO1("ERROR: Multiple '-info' arguments specified.\n", ErrValidate);
  }
  if (CountOccurrences(cmd_args, "-o") > 1) {
    ERROR_GOTO1("ERROR: Multiple output files specified.\n", ErrValidate);
  }

  // Compound checks.
  num_frame_args = CountOccurrences(cmd_args, "-frame");
  num_loop_args = CountOccurrences(cmd_args, "-loop");
  num_bgcolor_args = CountOccurrences(cmd_args, "-bgcolor");
  num_durations_args = CountOccurrences(cmd_args, "-duration");

  if (num_loop_args > 1) {
    ERROR_GOTO1("ERROR: Multiple loop counts specified.\n", ErrValidate);
  }
  if (num_bgcolor_args > 1) {
    ERROR_GOTO1("ERROR: Multiple background colors specified.\n", ErrValidate);
  }

  if ((num_frame_args == 0) && (num_loop_args + num_bgcolor_args > 0)) {
    ERROR_GOTO1("ERROR: Loop count and background color are relevant only in "
                "case of animation.\n", ErrValidate);
  }
  if (num_durations_args > 0 && num_frame_args != 0) {
    ERROR_GOTO1("ERROR: Can not combine -duration and -frame commands.\n",
                ErrValidate);
  }

  assert(ok == 1);
  if (num_durations_args > 0) {
    *num_feature_args = num_durations_args;
  } else if (num_frame_args == 0) {
    // Single argument ('set' action for ICCP/EXIF/XMP, OR a 'get' action).
    *num_feature_args = 1;
  } else {
    // Multiple arguments ('set' action for animation)
    *num_feature_args = num_frame_args + num_loop_args + num_bgcolor_args;
  }

 ErrValidate:
  return ok;
}

#define ACTION_IS_NIL (config->action_type_ == NIL_ACTION)

#define FEATURETYPE_IS_NIL (config->type_ == NIL_FEATURE)

#define CHECK_NUM_ARGS_AT_LEAST(NUM, LABEL)                              \
  do {                                                                   \
    if (argc < i + (NUM)) {                                              \
      fprintf(stderr, "ERROR: Too few arguments for '%s'.\n", argv[i]);  \
      goto LABEL;                                                        \
    }                                                                    \
  } while (0)

#define CHECK_NUM_ARGS_AT_MOST(NUM, LABEL)                               \
  do {                                                                   \
    if (argc > i + (NUM)) {                                              \
      fprintf(stderr, "ERROR: Too many arguments for '%s'.\n", argv[i]); \
      goto LABEL;                                                        \
    }                                                                    \
  } while (0)

#define CHECK_NUM_ARGS_EXACTLY(NUM, LABEL)                               \
  do {                                                                   \
    CHECK_NUM_ARGS_AT_LEAST(NUM, LABEL);                                 \
    CHECK_NUM_ARGS_AT_MOST(NUM, LABEL);                                  \
  } while (0)

// Parses command-line arguments to fill up config object. Also performs some
// semantic checks. unicode_argv contains wchar_t arguments or is null.
static int ParseCommandLine(Config* config, const W_CHAR** const unicode_argv) {
  int i = 0;
  int feature_arg_index = 0;
  int ok = 1;
  int argc = config->cmd_args_.argc_;
  const char* const* argv = config->cmd_args_.argv_;
  // Unicode file paths will be used if available.
  const char* const* wargv =
      (unicode_argv != NULL) ? (const char**)(unicode_argv + 1) : argv;

  while (i < argc) {
    FeatureArg* const arg = &config->args_[feature_arg_index];
    if (argv[i][0] == '-') {  // One of the action types or output.
      if (!strcmp(argv[i], "-set")) {
        if (ACTION_IS_NIL) {
          config->action_type_ = ACTION_SET;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        ++i;
      } else if (!strcmp(argv[i], "-duration")) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        if (ACTION_IS_NIL || config->action_type_ == ACTION_DURATION) {
          config->action_type_ = ACTION_DURATION;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        if (FEATURETYPE_IS_NIL || config->type_ == FEATURE_DURATION) {
          config->type_ = FEATURE_DURATION;
        } else {
          ERROR_GOTO1("ERROR: Multiple features specified.\n", ErrParse);
        }
        arg->params_ = argv[i + 1];
        ++feature_arg_index;
        i += 2;
      } else if (!strcmp(argv[i], "-get")) {
        if (ACTION_IS_NIL) {
          config->action_type_ = ACTION_GET;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        ++i;
      } else if (!strcmp(argv[i], "-strip")) {
        if (ACTION_IS_NIL) {
          config->action_type_ = ACTION_STRIP;
          config->arg_count_ = 0;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        ++i;
      } else if (!strcmp(argv[i], "-frame")) {
        CHECK_NUM_ARGS_AT_LEAST(3, ErrParse);
        if (ACTION_IS_NIL || config->action_type_ == ACTION_SET) {
          config->action_type_ = ACTION_SET;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        if (FEATURETYPE_IS_NIL || config->type_ == FEATURE_ANMF) {
          config->type_ = FEATURE_ANMF;
        } else {
          ERROR_GOTO1("ERROR: Multiple features specified.\n", ErrParse);
        }
        arg->subtype_ = SUBTYPE_ANMF;
        arg->filename_ = wargv[i + 1];
        arg->params_ = argv[i + 2];
        ++feature_arg_index;
        i += 3;
      } else if (!strcmp(argv[i], "-loop") || !strcmp(argv[i], "-bgcolor")) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        if (ACTION_IS_NIL || config->action_type_ == ACTION_SET) {
          config->action_type_ = ACTION_SET;
        } else {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        }
        if (FEATURETYPE_IS_NIL || config->type_ == FEATURE_ANMF) {
          config->type_ = FEATURE_ANMF;
        } else {
          ERROR_GOTO1("ERROR: Multiple features specified.\n", ErrParse);
        }
        arg->subtype_ =
            !strcmp(argv[i], "-loop") ? SUBTYPE_LOOP : SUBTYPE_BGCOLOR;
        arg->params_ = argv[i + 1];
        ++feature_arg_index;
        i += 2;
      } else if (!strcmp(argv[i], "-o")) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        config->output_ = wargv[i + 1];
        i += 2;
      } else if (!strcmp(argv[i], "-info")) {
        CHECK_NUM_ARGS_EXACTLY(2, ErrParse);
        if (config->action_type_ != NIL_ACTION) {
          ERROR_GOTO1("ERROR: Multiple actions specified.\n", ErrParse);
        } else {
          config->action_type_ = ACTION_INFO;
          config->arg_count_ = 0;
          config->input_ = wargv[i + 1];
        }
        i += 2;
      } else if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "-help")) {
        PrintHelp();
        DeleteConfig(config);
        LOCAL_FREE((W_CHAR** const)unicode_argv);
        exit(0);
      } else if (!strcmp(argv[i], "-version")) {
        const int version = WebPGetMuxVersion();
        printf("%d.%d.%d\n",
               (version >> 16) & 0xff, (version >> 8) & 0xff, version & 0xff);
        DeleteConfig(config);
        LOCAL_FREE((W_CHAR** const)unicode_argv);
        exit(0);
      } else if (!strcmp(argv[i], "--")) {
        if (i < argc - 1) {
          ++i;
          if (config->input_ == NULL) {
            config->input_ = wargv[i];
          } else {
            ERROR_GOTO2("ERROR at '%s': Multiple input files specified.\n",
                        argv[i], ErrParse);
          }
        }
        break;
      } else {
        ERROR_GOTO2("ERROR: Unknown option: '%s'.\n", argv[i], ErrParse);
      }
    } else {  // One of the feature types or input.
      if (ACTION_IS_NIL) {
        ERROR_GOTO1("ERROR: Action must be specified before other arguments.\n",
                    ErrParse);
      }
      if (!strcmp(argv[i], "icc") || !strcmp(argv[i], "exif") ||
          !strcmp(argv[i], "xmp")) {
        if (FEATURETYPE_IS_NIL) {
          config->type_ = (!strcmp(argv[i], "icc")) ? FEATURE_ICCP :
              (!strcmp(argv[i], "exif")) ? FEATURE_EXIF : FEATURE_XMP;
        } else {
          ERROR_GOTO1("ERROR: Multiple features specified.\n", ErrParse);
        }
        if (config->action_type_ == ACTION_SET) {
          CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
          arg->filename_ = wargv[i + 1];
          ++feature_arg_index;
          i += 2;
        } else {
          ++i;
        }
      } else if (!strcmp(argv[i], "frame") &&
                 (config->action_type_ == ACTION_GET)) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        config->type_ = FEATURE_ANMF;
        arg->params_ = argv[i + 1];
        ++feature_arg_index;
        i += 2;
      } else if (!strcmp(argv[i], "loop") &&
                 (config->action_type_ == ACTION_SET)) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        config->type_ = FEATURE_LOOP;
        arg->params_ = argv[i + 1];
        ++feature_arg_index;
        i += 2;
      } else if (!strcmp(argv[i], "bgcolor") &&
                 (config->action_type_ == ACTION_SET)) {
        CHECK_NUM_ARGS_AT_LEAST(2, ErrParse);
        config->type_ = FEATURE_BGCOLOR;
        arg->params_ = argv[i + 1];
        ++feature_arg_index;
        i += 2;
      } else {  // Assume input file.
        if (config->input_ == NULL) {
          config->input_ = wargv[i];
        } else {
          ERROR_GOTO2("ERROR at '%s': Multiple input files specified.\n",
                      argv[i], ErrParse);
        }
        ++i;
      }
    }
  }
 ErrParse:
  return ok;
}

// Additional checks after config is filled.
static int ValidateConfig(Config* const config) {
  int ok = 1;

  // Action.
  if (ACTION_IS_NIL) {
    ERROR_GOTO1("ERROR: No action specified.\n", ErrValidate2);
  }

  // Feature type.
  if (FEATURETYPE_IS_NIL && config->action_type_ != ACTION_INFO) {
    ERROR_GOTO1("ERROR: No feature specified.\n", ErrValidate2);
  }

  // Input file.
  if (config->input_ == NULL) {
    if (config->action_type_ != ACTION_SET) {
      ERROR_GOTO1("ERROR: No input file specified.\n", ErrValidate2);
    } else if (config->type_ != FEATURE_ANMF) {
      ERROR_GOTO1("ERROR: No input file specified.\n", ErrValidate2);
    }
  }

  // Output file.
  if (config->output_ == NULL && config->action_type_ != ACTION_INFO) {
    ERROR_GOTO1("ERROR: No output file specified.\n", ErrValidate2);
  }

 ErrValidate2:
  return ok;
}

// Create config object from command-line arguments.
static int InitializeConfig(int argc, const char* argv[], Config* const config,
                            const W_CHAR** const unicode_argv) {
  int num_feature_args = 0;
  int ok;

  memset(config, 0, sizeof(*config));

  ok = ExUtilInitCommandLineArguments(argc, argv, &config->cmd_args_);
  if (!ok) return 0;

  // Validate command-line arguments.
  if (!ValidateCommandLine(&config->cmd_args_, &num_feature_args)) {
    ERROR_GOTO1("Exiting due to command-line parsing error.\n", Err1);
  }

  config->arg_count_ = num_feature_args;
  config->args_ = (FeatureArg*)calloc(num_feature_args, sizeof(*config->args_));
  if (config->args_ == NULL) {
    ERROR_GOTO1("ERROR: Memory allocation error.\n", Err1);
  }

  // Parse command-line.
  if (!ParseCommandLine(config, unicode_argv) || !ValidateConfig(config)) {
    ERROR_GOTO1("Exiting due to command-line parsing error.\n", Err1);
  }

 Err1:
  return ok;
}

#undef ACTION_IS_NIL
#undef FEATURETYPE_IS_NIL
#undef CHECK_NUM_ARGS_AT_LEAST
#undef CHECK_NUM_ARGS_AT_MOST
#undef CHECK_NUM_ARGS_EXACTLY

//------------------------------------------------------------------------------
// Processing.

static int GetFrame(const WebPMux* mux, const Config* config) {
  WebPMuxError err = WEBP_MUX_OK;
  WebPMux* mux_single = NULL;
  int num = 0;
  int ok = 1;
  int parse_error = 0;
  const WebPChunkId id = WEBP_CHUNK_ANMF;
  WebPMuxFrameInfo info;
  WebPDataInit(&info.bitstream);

  num = ExUtilGetInt(config->args_[0].params_, 10, &parse_error);
  if (num < 0) {
    ERROR_GOTO1("ERROR: Frame/Fragment index must be non-negative.\n", ErrGet);
  }
  if (parse_error) goto ErrGet;

  err = WebPMuxGetFrame(mux, num, &info);
  if (err == WEBP_MUX_OK && info.id != id) err = WEBP_MUX_NOT_FOUND;
  if (err != WEBP_MUX_OK) {
    ERROR_GOTO3("ERROR (%s): Could not get frame %d.\n",
                ErrorString(err), num, ErrGet);
  }

  mux_single = WebPMuxNew();
  if (mux_single == NULL) {
    err = WEBP_MUX_MEMORY_ERROR;
    ERROR_GOTO2("ERROR (%s): Could not allocate a mux object.\n",
                ErrorString(err), ErrGet);
  }
  err = WebPMuxSetImage(mux_single, &info.bitstream, 1);
  if (err != WEBP_MUX_OK) {
    ERROR_GOTO2("ERROR (%s): Could not create single image mux object.\n",
                ErrorString(err), ErrGet);
  }

  ok = WriteWebP(mux_single, config->output_);

 ErrGet:
  WebPDataClear(&info.bitstream);
  WebPMuxDelete(mux_single);
  return ok && !parse_error;
}

// Read and process config.
static int Process(const Config* config) {
  WebPMux* mux = NULL;
  WebPData chunk;
  WebPMuxError err = WEBP_MUX_OK;
  int ok = 1;

  switch (config->action_type_) {
    case ACTION_GET: {
      ok = CreateMux(config->input_, &mux);
      if (!ok) goto Err2;
      switch (config->type_) {
        case FEATURE_ANMF:
          ok = GetFrame(mux, config);
          break;

        case FEATURE_ICCP:
        case FEATURE_EXIF:
        case FEATURE_XMP:
          err = WebPMuxGetChunk(mux, kFourccList[config->type_], &chunk);
          if (err != WEBP_MUX_OK) {
            ERROR_GOTO3("ERROR (%s): Could not get the %s.\n",
                        ErrorString(err), kDescriptions[config->type_], Err2);
          }
          ok = WriteData(config->output_, &chunk);
          break;

        default:
          ERROR_GOTO1("ERROR: Invalid feature for action 'get'.\n", Err2);
          break;
      }
      break;
    }
    case ACTION_SET: {
      switch (config->type_) {
        case FEATURE_ANMF: {
          int i;
          WebPMuxAnimParams params = { 0xFFFFFFFF, 0 };
          mux = WebPMuxNew();
          if (mux == NULL) {
            ERROR_GOTO2("ERROR (%s): Could not allocate a mux object.\n",
                        ErrorString(WEBP_MUX_MEMORY_ERROR), Err2);
          }
          for (i = 0; i < config->arg_count_; ++i) {
            switch (config->args_[i].subtype_) {
              case SUBTYPE_BGCOLOR: {
                uint32_t bgcolor;
                ok = ParseBgcolorArgs(config->args_[i].params_, &bgcolor);
                if (!ok) {
                  ERROR_GOTO1("ERROR: Could not parse the background color \n",
                              Err2);
                }
                params.bgcolor = bgcolor;
                break;
              }
              case SUBTYPE_LOOP: {
                int parse_error = 0;
                const int loop_count =
                    ExUtilGetInt(config->args_[i].params_, 10, &parse_error);
                if (loop_count < 0 || loop_count > 65535) {
                  // Note: This is only a 'necessary' condition for loop_count
                  // to be valid. The 'sufficient' conditioned in checked in
                  // WebPMuxSetAnimationParams() method called later.
                  ERROR_GOTO1("ERROR: Loop count must be in the range 0 to "
                              "65535.\n", Err2);
                }
                ok = !parse_error;
                if (!ok) goto Err2;
                params.loop_count = loop_count;
                break;
              }
              case SUBTYPE_ANMF: {
                WebPMuxFrameInfo frame;
                frame.id = WEBP_CHUNK_ANMF;
                ok = ExUtilReadFileToWebPData(config->args_[i].filename_,
                                              &frame.bitstream);
                if (!ok) goto Err2;
                ok = ParseFrameArgs(config->args_[i].params_, &frame);
                if (!ok) {
                  WebPDataClear(&frame.bitstream);
                  ERROR_GOTO1("ERROR: Could not parse frame properties.\n",
                              Err2);
                }
                err = WebPMuxPushFrame(mux, &frame, 1);
                WebPDataClear(&frame.bitstream);
                if (err != WEBP_MUX_OK) {
                  ERROR_GOTO3("ERROR (%s): Could not add a frame at index %d."
                              "\n", ErrorString(err), i, Err2);
                }
                break;
              }
              default: {
                ERROR_GOTO1("ERROR: Invalid subtype for 'frame'", Err2);
                break;
              }
            }
          }
          err = WebPMuxSetAnimationParams(mux, &params);
          if (err != WEBP_MUX_OK) {
            ERROR_GOTO2("ERROR (%s): Could not set animation parameters.\n",
                        ErrorString(err), Err2);
          }
          break;
        }

        case FEATURE_ICCP:
        case FEATURE_EXIF:
        case FEATURE_XMP: {
          ok = CreateMux(config->input_, &mux);
          if (!ok) goto Err2;
          ok = ExUtilReadFileToWebPData(config->args_[0].filename_, &chunk);
          if (!ok) goto Err2;
          err = WebPMuxSetChunk(mux, kFourccList[config->type_], &chunk, 1);
          WebPDataClear(&chunk);
          if (err != WEBP_MUX_OK) {
            ERROR_GOTO3("ERROR (%s): Could not set the %s.\n",
                        ErrorString(err), kDescriptions[config->type_], Err2);
          }
          break;
        }
        case FEATURE_LOOP: {
          WebPMuxAnimParams params = { 0xFFFFFFFF, 0 };
          int parse_error = 0;
          const int loop_count =
              ExUtilGetInt(config->args_[0].params_, 10, &parse_error);
          if (loop_count < 0 || loop_count > 65535 || parse_error) {
            ERROR_GOTO1("ERROR: Loop count must be in the range 0 to 65535.\n",
                        Err2);
          }
          ok = CreateMux(config->input_, &mux);
          if (!ok) goto Err2;
          ok = (WebPMuxGetAnimationParams(mux, &params) == WEBP_MUX_OK);
          if (!ok) {
            ERROR_GOTO1("ERROR: input file does not seem to be an animation.\n",
                        Err2);
          }
          params.loop_count = loop_count;
          err = WebPMuxSetAnimationParams(mux, &params);
          ok = (err == WEBP_MUX_OK);
          if (!ok) {
            ERROR_GOTO2("ERROR (%s): Could not set animation parameters.\n",
                        ErrorString(err), Err2);
          }
          break;
        }
        case FEATURE_BGCOLOR: {
          WebPMuxAnimParams params = { 0xFFFFFFFF, 0 };
          uint32_t bgcolor;
          ok = ParseBgcolorArgs(config->args_[0].params_, &bgcolor);
          if (!ok) {
            ERROR_GOTO1("ERROR: Could not parse the background color.\n",
                        Err2);
          }
          ok = CreateMux(config->input_, &mux);
          if (!ok) goto Err2;
          ok = (WebPMuxGetAnimationParams(mux, &params) == WEBP_MUX_OK);
          if (!ok) {
            ERROR_GOTO1("ERROR: input file does not seem to be an animation.\n",
                        Err2);
          }
          params.bgcolor = bgcolor;
          err = WebPMuxSetAnimationParams(mux, &params);
          ok = (err == WEBP_MUX_OK);
          if (!ok) {
            ERROR_GOTO2("ERROR (%s): Could not set animation parameters.\n",
                        ErrorString(err), Err2);
          }
          break;
        }
        default: {
          ERROR_GOTO1("ERROR: Invalid feature for action 'set'.\n", Err2);
          break;
        }
      }
      ok = WriteWebP(mux, config->output_);
      break;
    }
    case ACTION_DURATION: {
      int num_frames;
      ok = CreateMux(config->input_, &mux);
      if (!ok) goto Err2;
      err = WebPMuxNumChunks(mux, WEBP_CHUNK_ANMF, &num_frames);
      ok = (err == WEBP_MUX_OK);
      if (!ok) {
        ERROR_GOTO1("ERROR: can not parse the number of frames.\n", Err2);
      }
      if (num_frames == 0) {
        fprintf(stderr, "Doesn't look like the source is animated. "
                        "Skipping duration setting.\n");
        ok = WriteWebP(mux, config->output_);
        if (!ok) goto Err2;
      } else {
        int i;
        int* durations = NULL;
        WebPMux* new_mux = DuplicateMuxHeader(mux);
        if (new_mux == NULL) goto Err2;
        durations = (int*)WebPMalloc((size_t)num_frames * sizeof(*durations));
        if (durations == NULL) goto Err2;
        for (i = 0; i < num_frames; ++i) durations[i] = -1;

        // Parse intervals to process.
        for (i = 0; i < config->arg_count_; ++i) {
          int k;
          int args[3];
          int duration, start, end;
          const int nb_args = ExUtilGetInts(config->args_[i].params_,
                                            10, 3, args);
          ok = (nb_args >= 1);
          if (!ok) goto Err3;
          duration = args[0];
          if (duration < 0) {
            ERROR_GOTO1("ERROR: duration must be strictly positive.\n", Err3);
          }

          if (nb_args == 1) {   // only duration is present -> use full interval
            start = 1;
            end = num_frames;
          } else {
            start = args[1];
            if (start <= 0) {
              start = 1;
            } else if (start > num_frames) {
              start = num_frames;
            }
            end = (nb_args >= 3) ? args[2] : start;
            if (end == 0 || end > num_frames) end = num_frames;
          }

          for (k = start; k <= end; ++k) {
            assert(k >= 1 && k <= num_frames);
            durations[k - 1] = duration;
          }
        }

        // Apply non-negative durations to their destination frames.
        for (i = 1; i <= num_frames; ++i) {
          WebPMuxFrameInfo frame;
          err = WebPMuxGetFrame(mux, i, &frame);
          if (err != WEBP_MUX_OK || frame.id != WEBP_CHUNK_ANMF) {
            ERROR_GOTO2("ERROR: can not retrieve frame #%d.\n", i, Err3);
          }
          if (durations[i - 1] >= 0) frame.duration = durations[i - 1];
          err = WebPMuxPushFrame(new_mux, &frame, 1);
          if (err != WEBP_MUX_OK) {
            ERROR_GOTO2("ERROR: error push frame data #%d\n", i, Err3);
          }
          WebPDataClear(&frame.bitstream);
        }
        WebPMuxDelete(mux);
        ok = WriteWebP(new_mux, config->output_);
        mux = new_mux;  // transfer for the WebPMuxDelete() call
        new_mux = NULL;

 Err3:
        WebPFree(durations);
        WebPMuxDelete(new_mux);
        if (!ok) goto Err2;
      }
      break;
    }
    case ACTION_STRIP: {
      ok = CreateMux(config->input_, &mux);
      if (!ok) goto Err2;
      if (config->type_ == FEATURE_ICCP || config->type_ == FEATURE_EXIF ||
          config->type_ == FEATURE_XMP) {
        err = WebPMuxDeleteChunk(mux, kFourccList[config->type_]);
        if (err != WEBP_MUX_OK) {
          ERROR_GOTO3("ERROR (%s): Could not strip the %s.\n",
                      ErrorString(err), kDescriptions[config->type_], Err2);
        }
      } else {
        ERROR_GOTO1("ERROR: Invalid feature for action 'strip'.\n", Err2);
        break;
      }
      ok = WriteWebP(mux, config->output_);
      break;
    }
    case ACTION_INFO: {
      ok = CreateMux(config->input_, &mux);
      if (!ok) goto Err2;
      ok = (DisplayInfo(mux) == WEBP_MUX_OK);
      break;
    }
    default: {
      assert(0);  // Invalid action.
      break;
    }
  }

 Err2:
  WebPMuxDelete(mux);
  return ok;
}

//------------------------------------------------------------------------------
// Main.

// Returns EXIT_SUCCESS on success, EXIT_FAILURE on failure.
int main(int argc, const char* argv[]) {
  Config config;
  int ok;

  INIT_WARGV(argc, argv);

  ok = InitializeConfig(argc - 1, argv + 1, &config, GET_WARGV_OR_NULL());
  if (ok) {
    ok = Process(&config);
  } else {
    PrintHelp();
  }
  DeleteConfig(&config);
  FREE_WARGV_AND_RETURN(ok ? EXIT_SUCCESS : EXIT_FAILURE);
}

//------------------------------------------------------------------------------

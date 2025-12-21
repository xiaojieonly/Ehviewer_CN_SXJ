// Copyright 2015 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Checks if given pair of animated GIF/WebP images are identical:
// That is: their reconstructed canvases match pixel-by-pixel and their other
// animation properties (loop count etc) also match.
//
// example: anim_diff foo.gif bar.webp

#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>  // for 'strcmp'.

#include "./anim_util.h"
#include "./example_util.h"
#include "./unicode.h"

#if defined(_MSC_VER) && _MSC_VER < 1900
#define snprintf _snprintf
#endif

// Returns true if 'a + b' will overflow.
static int AdditionWillOverflow(int a, int b) {
  return (b > 0) && (a > INT_MAX - b);
}

static int FramesAreEqual(const uint8_t* const rgba1,
                          const uint8_t* const rgba2, int width, int height) {
  const int stride = width * 4;  // Always true for 'DecodedFrame.rgba'.
  return !memcmp(rgba1, rgba2, stride * height);
}

static WEBP_INLINE int PixelsAreSimilar(uint32_t src, uint32_t dst,
                                        int max_allowed_diff) {
  const int src_a = (src >> 24) & 0xff;
  const int src_r = (src >> 16) & 0xff;
  const int src_g = (src >> 8) & 0xff;
  const int src_b = (src >> 0) & 0xff;
  const int dst_a = (dst >> 24) & 0xff;
  const int dst_r = (dst >> 16) & 0xff;
  const int dst_g = (dst >> 8) & 0xff;
  const int dst_b = (dst >> 0) & 0xff;

  return (abs(src_r * src_a - dst_r * dst_a) <= (max_allowed_diff * 255)) &&
         (abs(src_g * src_a - dst_g * dst_a) <= (max_allowed_diff * 255)) &&
         (abs(src_b * src_a - dst_b * dst_a) <= (max_allowed_diff * 255)) &&
         (abs(src_a - dst_a) <= max_allowed_diff);
}

static int FramesAreSimilar(const uint8_t* const rgba1,
                            const uint8_t* const rgba2,
                            int width, int height, int max_allowed_diff) {
  int i, j;
  assert(max_allowed_diff > 0);
  for (j = 0; j < height; ++j) {
    for (i = 0; i < width; ++i) {
      const int stride = width * 4;
      const size_t offset = j * stride + i;
      if (!PixelsAreSimilar(rgba1[offset], rgba2[offset], max_allowed_diff)) {
        return 0;
      }
    }
  }
  return 1;
}

// Minimize number of frames by combining successive frames that have at max
// 'max_diff' difference per channel between corresponding pixels.
static void MinimizeAnimationFrames(AnimatedImage* const img, int max_diff) {
  uint32_t i;
  for (i = 1; i < img->num_frames; ++i) {
    DecodedFrame* const frame1 = &img->frames[i - 1];
    DecodedFrame* const frame2 = &img->frames[i];
    const uint8_t* const rgba1 = frame1->rgba;
    const uint8_t* const rgba2 = frame2->rgba;
    int should_merge_frames = 0;
    // If merging frames will result in integer overflow for 'duration',
    // skip merging.
    if (AdditionWillOverflow(frame1->duration, frame2->duration)) continue;
    if (max_diff > 0) {
      should_merge_frames = FramesAreSimilar(rgba1, rgba2, img->canvas_width,
                                             img->canvas_height, max_diff);
    } else {
      should_merge_frames =
          FramesAreEqual(rgba1, rgba2, img->canvas_width, img->canvas_height);
    }
    if (should_merge_frames) {  // Merge 'i+1'th frame into 'i'th frame.
      frame1->duration += frame2->duration;
      if (i + 1 < img->num_frames) {
        memmove(&img->frames[i], &img->frames[i + 1],
                (img->num_frames - i - 1) * sizeof(*img->frames));
      }
      --img->num_frames;
      --i;
    }
  }
}

static int CompareValues(uint32_t a, uint32_t b, const char* output_str) {
  if (a != b) {
    fprintf(stderr, "%s: %d vs %d\n", output_str, a, b);
    return 0;
  }
  return 1;
}

static int CompareBackgroundColor(uint32_t bg1, uint32_t bg2, int premultiply) {
  if (premultiply) {
    const int alpha1 = (bg1 >> 24) & 0xff;
    const int alpha2 = (bg2 >> 24) & 0xff;
    if (alpha1 == 0 && alpha2 == 0) return 1;
  }
  if (bg1 != bg2) {
    fprintf(stderr, "Background color mismatch: 0x%08x vs 0x%08x\n",
            bg1, bg2);
    return 0;
  }
  return 1;
}

// Note: As long as frame durations and reconstructed frames are identical, it
// is OK for other aspects like offsets, dispose/blend method to vary.
static int CompareAnimatedImagePair(const AnimatedImage* const img1,
                                    const AnimatedImage* const img2,
                                    int premultiply,
                                    double min_psnr) {
  int ok = 1;
  const int is_multi_frame_image = (img1->num_frames > 1);
  uint32_t i;

  ok = CompareValues(img1->canvas_width, img2->canvas_width,
                     "Canvas width mismatch") && ok;
  ok = CompareValues(img1->canvas_height, img2->canvas_height,
                     "Canvas height mismatch") && ok;
  ok = CompareValues(img1->num_frames, img2->num_frames,
                     "Frame count mismatch") && ok;
  if (!ok) return 0;  // These are fatal failures, can't proceed.

  if (is_multi_frame_image) {  // Checks relevant for multi-frame images only.
    int max_loop_count_workaround = 0;
    // Transcodes to webp increase the gif loop count by 1 for compatibility.
    // When the gif has the maximum value the webp value will be off by one.
    if ((img1->format == ANIM_GIF && img1->loop_count == 65536 &&
         img2->format == ANIM_WEBP && img2->loop_count == 65535) ||
        (img1->format == ANIM_WEBP && img1->loop_count == 65535 &&
         img2->format == ANIM_GIF && img2->loop_count == 65536)) {
      max_loop_count_workaround = 1;
    }
    ok = (max_loop_count_workaround ||
          CompareValues(img1->loop_count, img2->loop_count,
                        "Loop count mismatch")) && ok;
    ok = CompareBackgroundColor(img1->bgcolor, img2->bgcolor,
                                premultiply) && ok;
  }

  for (i = 0; i < img1->num_frames; ++i) {
    // Pixel-by-pixel comparison.
    const uint8_t* const rgba1 = img1->frames[i].rgba;
    const uint8_t* const rgba2 = img2->frames[i].rgba;
    int max_diff;
    double psnr;
    if (is_multi_frame_image) {  // Check relevant for multi-frame images only.
      const char format[] = "Frame #%d, duration mismatch";
      char tmp[sizeof(format) + 8];
      ok = ok && (snprintf(tmp, sizeof(tmp), format, i) >= 0);
      ok = ok && CompareValues(img1->frames[i].duration,
                               img2->frames[i].duration, tmp);
    }
    GetDiffAndPSNR(rgba1, rgba2, img1->canvas_width, img1->canvas_height,
                   premultiply, &max_diff, &psnr);
    if (min_psnr > 0.) {
      if (psnr < min_psnr) {
        fprintf(stderr, "Frame #%d, psnr = %.2lf (min_psnr = %f)\n", i,
                psnr, min_psnr);
        ok = 0;
      }
    } else {
      if (max_diff != 0) {
        fprintf(stderr, "Frame #%d, max pixel diff: %d\n", i, max_diff);
        ok = 0;
      }
    }
  }
  return ok;
}

static void Help(void) {
  printf("Usage: anim_diff <image1> <image2> [options]\n");
  printf("\nOptions:\n");
  printf("  -dump_frames <folder> dump decoded frames in PAM format\n");
  printf("  -min_psnr <float> ... minimum per-frame PSNR\n");
  printf("  -raw_comparison ..... if this flag is not used, RGB is\n");
  printf("                        premultiplied before comparison\n");
  printf("  -max_diff <int> ..... maximum allowed difference per channel\n"
         "                        between corresponding pixels in subsequent\n"
         "                        frames\n");
  printf("  -h .................. this help\n");
  printf("  -version ............ print version number and exit\n");
}

// Returns 0 on success, 1 if animation files differ, and 2 for any error.
int main(int argc, const char* argv[]) {
  int return_code = 2;
  int dump_frames = 0;
  const char* dump_folder = NULL;
  double min_psnr = 0.;
  int got_input1 = 0;
  int got_input2 = 0;
  int premultiply = 1;
  int max_diff = 0;
  int i, c;
  const char* files[2] = { NULL, NULL };
  AnimatedImage images[2];

  INIT_WARGV(argc, argv);

  for (c = 1; c < argc; ++c) {
    int parse_error = 0;
    if (!strcmp(argv[c], "-dump_frames")) {
      if (c < argc - 1) {
        dump_frames = 1;
        dump_folder = (const char*)GET_WARGV(argv, ++c);
      } else {
        parse_error = 1;
      }
    } else if (!strcmp(argv[c], "-min_psnr")) {
      if (c < argc - 1) {
        min_psnr = ExUtilGetFloat(argv[++c], &parse_error);
      } else {
        parse_error = 1;
      }
    } else if (!strcmp(argv[c], "-raw_comparison")) {
      premultiply = 0;
    } else if (!strcmp(argv[c], "-max_diff")) {
      if (c < argc - 1) {
        max_diff = ExUtilGetInt(argv[++c], 0, &parse_error);
      } else {
        parse_error = 1;
      }
    } else if (!strcmp(argv[c], "-h") || !strcmp(argv[c], "-help")) {
      Help();
      FREE_WARGV_AND_RETURN(0);
    } else if (!strcmp(argv[c], "-version")) {
      int dec_version, demux_version;
      GetAnimatedImageVersions(&dec_version, &demux_version);
      printf("WebP Decoder version: %d.%d.%d\nWebP Demux version: %d.%d.%d\n",
             (dec_version >> 16) & 0xff, (dec_version >> 8) & 0xff,
             (dec_version >> 0) & 0xff,
             (demux_version >> 16) & 0xff, (demux_version >> 8) & 0xff,
             (demux_version >> 0) & 0xff);
      FREE_WARGV_AND_RETURN(0);
    } else {
      if (!got_input1) {
        files[0] = (const char*)GET_WARGV(argv, c);
        got_input1 = 1;
      } else if (!got_input2) {
        files[1] = (const char*)GET_WARGV(argv, c);
        got_input2 = 1;
      } else {
        parse_error = 1;
      }
    }
    if (parse_error) {
      Help();
      FREE_WARGV_AND_RETURN(return_code);
    }
  }
  if (argc < 3) {
    Help();
    FREE_WARGV_AND_RETURN(return_code);
  }


  if (!got_input2) {
    Help();
    FREE_WARGV_AND_RETURN(return_code);
  }

  if (dump_frames) {
    WPRINTF("Dumping decoded frames in: %s\n", (const W_CHAR*)dump_folder);
  }

  memset(images, 0, sizeof(images));
  for (i = 0; i < 2; ++i) {
    WPRINTF("Decoding file: %s\n", (const W_CHAR*)files[i]);
    if (!ReadAnimatedImage(files[i], &images[i], dump_frames, dump_folder)) {
      WFPRINTF(stderr, "Error decoding file: %s\n Aborting.\n",
               (const W_CHAR*)files[i]);
      return_code = 2;
      goto End;
    } else {
      MinimizeAnimationFrames(&images[i], max_diff);
    }
  }

  if (!CompareAnimatedImagePair(&images[0], &images[1],
                                premultiply, min_psnr)) {
    WFPRINTF(stderr, "\nFiles %s and %s differ.\n", (const W_CHAR*)files[0],
             (const W_CHAR*)files[1]);
    return_code = 1;
  } else {
    WPRINTF("\nFiles %s and %s are identical.\n", (const W_CHAR*)files[0],
            (const W_CHAR*)files[1]);
    return_code = 0;
  }
 End:
  ClearAnimatedImage(&images[0]);
  ClearAnimatedImage(&images[1]);
  FREE_WARGV_AND_RETURN(return_code);
}

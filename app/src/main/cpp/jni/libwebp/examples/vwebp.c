// Copyright 2011 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Simple OpenGL-based WebP file viewer.
//
// Author: Skal (pascal.massimino@gmail.com)
#ifdef HAVE_CONFIG_H
#include "../webp/config.h"
#endif

#if defined(__unix__) || defined(__CYGWIN__)
#define _POSIX_C_SOURCE 200112L  // for setenv
#endif

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(WEBP_HAVE_GL)

#if defined(HAVE_GLUT_GLUT_H)
#include <GLUT/glut.h>
#else
#include <GL/glut.h>
#ifdef FREEGLUT
#include <GL/freeglut.h>
#endif
#endif

#ifdef WEBP_HAVE_QCMS
#include <qcms.h>
#endif

#include "webp/decode.h"
#include "webp/demux.h"

#include "../examples/example_util.h"
#include "../imageio/imageio_util.h"
#include "./unicode.h"

#if defined(_MSC_VER) && _MSC_VER < 1900
#define snprintf _snprintf
#endif

// Unfortunate global variables. Gathered into a struct for comfort.
static struct {
  int has_animation;
  int has_color_profile;
  int done;
  int decoding_error;
  int print_info;
  int only_deltas;
  int use_color_profile;
  int draw_anim_background_color;

  int canvas_width, canvas_height;
  int loop_count;
  uint32_t bg_color;

  const char* file_name;
  WebPData data;
  WebPDecoderConfig config;
  const WebPDecBuffer* pic;
  WebPDemuxer* dmux;
  WebPIterator curr_frame;
  WebPIterator prev_frame;
  WebPChunkIterator iccp;
  int viewport_width, viewport_height;
} kParams;

static void ClearPreviousPic(void) {
  WebPFreeDecBuffer((WebPDecBuffer*)kParams.pic);
  kParams.pic = NULL;
}

static void ClearParams(void) {
  ClearPreviousPic();
  WebPDataClear(&kParams.data);
  WebPDemuxReleaseIterator(&kParams.curr_frame);
  WebPDemuxReleaseIterator(&kParams.prev_frame);
  WebPDemuxReleaseChunkIterator(&kParams.iccp);
  WebPDemuxDelete(kParams.dmux);
  kParams.dmux = NULL;
}

// Sets the previous frame to the dimensions of the canvas and has it dispose
// to background to cause the canvas to be cleared.
static void ClearPreviousFrame(void) {
  WebPIterator* const prev = &kParams.prev_frame;
  prev->width = kParams.canvas_width;
  prev->height = kParams.canvas_height;
  prev->x_offset = prev->y_offset = 0;
  prev->dispose_method = WEBP_MUX_DISPOSE_BACKGROUND;
}

// -----------------------------------------------------------------------------
// Color profile handling
static int ApplyColorProfile(const WebPData* const profile,
                             WebPDecBuffer* const rgba) {
#ifdef WEBP_HAVE_QCMS
  int i, ok = 0;
  uint8_t* line;
  uint8_t major_revision;
  qcms_profile* input_profile = NULL;
  qcms_profile* output_profile = NULL;
  qcms_transform* transform = NULL;
  const qcms_data_type input_type = QCMS_DATA_RGBA_8;
  const qcms_data_type output_type = QCMS_DATA_RGBA_8;
  const qcms_intent intent = QCMS_INTENT_DEFAULT;

  if (profile == NULL || rgba == NULL) return 0;
  if (profile->bytes == NULL || profile->size < 10) return 1;
  major_revision = profile->bytes[8];

  qcms_enable_iccv4();
  input_profile = qcms_profile_from_memory(profile->bytes, profile->size);
  // qcms_profile_is_bogus() is broken with ICCv4.
  if (input_profile == NULL ||
      (major_revision < 4 && qcms_profile_is_bogus(input_profile))) {
    fprintf(stderr, "Color profile is bogus!\n");
    goto Error;
  }

  output_profile = qcms_profile_sRGB();
  if (output_profile == NULL) {
    fprintf(stderr, "Error creating output color profile!\n");
    goto Error;
  }

  qcms_profile_precache_output_transform(output_profile);
  transform = qcms_transform_create(input_profile, input_type,
                                    output_profile, output_type,
                                    intent);
  if (transform == NULL) {
    fprintf(stderr, "Error creating color transform!\n");
    goto Error;
  }

  line = rgba->u.RGBA.rgba;
  for (i = 0; i < rgba->height; ++i, line += rgba->u.RGBA.stride) {
    qcms_transform_data(transform, line, line, rgba->width);
  }
  ok = 1;

 Error:
  if (input_profile != NULL) qcms_profile_release(input_profile);
  if (output_profile != NULL) qcms_profile_release(output_profile);
  if (transform != NULL) qcms_transform_release(transform);
  return ok;
#else
  (void)profile;
  (void)rgba;
  return 1;
#endif  // WEBP_HAVE_QCMS
}

//------------------------------------------------------------------------------
// File decoding

static int Decode(void) {   // Fills kParams.curr_frame
  const WebPIterator* const curr = &kParams.curr_frame;
  WebPDecoderConfig* const config = &kParams.config;
  WebPDecBuffer* const output_buffer = &config->output;
  int ok = 0;

  ClearPreviousPic();
  output_buffer->colorspace = MODE_RGBA;
  ok = (WebPDecode(curr->fragment.bytes, curr->fragment.size,
                   config) == VP8_STATUS_OK);
  if (!ok) {
    fprintf(stderr, "Decoding of frame #%d failed!\n", curr->frame_num);
  } else {
    kParams.pic = output_buffer;
    if (kParams.use_color_profile) {
      ok = ApplyColorProfile(&kParams.iccp.chunk, output_buffer);
      if (!ok) {
        fprintf(stderr, "Applying color profile to frame #%d failed!\n",
                curr->frame_num);
      }
    }
  }
  return ok;
}

static void decode_callback(int what) {
  if (what == 0 && !kParams.done) {
    int duration = 0;
    if (kParams.dmux != NULL) {
      WebPIterator* const curr = &kParams.curr_frame;
      if (!WebPDemuxNextFrame(curr)) {
        WebPDemuxReleaseIterator(curr);
        if (WebPDemuxGetFrame(kParams.dmux, 1, curr)) {
          --kParams.loop_count;
          kParams.done = (kParams.loop_count == 0);
          if (kParams.done) return;
          ClearPreviousFrame();
        } else {
          kParams.decoding_error = 1;
          kParams.done = 1;
          return;
        }
      }
      duration = curr->duration;
      // Behavior copied from Chrome, cf:
      // https://cs.chromium.org/chromium/src/third_party/WebKit/Source/
      // platform/graphics/DeferredImageDecoder.cpp?
      // rcl=b4c33049f096cd283f32be9a58b9a9e768227c26&l=246
      if (duration <= 10) duration = 100;
    }
    if (!Decode()) {
      kParams.decoding_error = 1;
      kParams.done = 1;
    } else {
      glutPostRedisplay();
      glutTimerFunc(duration, decode_callback, what);
    }
  }
}

//------------------------------------------------------------------------------
// Callbacks

static void HandleKey(unsigned char key, int pos_x, int pos_y) {
  // Note: rescaling the window or toggling some features during an animation
  // generates visual artifacts. This is not fixed because refreshing the frame
  // may require rendering the whole animation from start till current frame.
  (void)pos_x;
  (void)pos_y;
  if (key == 'q' || key == 'Q' || key == 27 /* Esc */) {
#ifdef FREEGLUT
    glutLeaveMainLoop();
#else
    ClearParams();
    exit(0);
#endif
  } else if (key == 'c') {
    if (kParams.has_color_profile && !kParams.decoding_error) {
      kParams.use_color_profile = 1 - kParams.use_color_profile;

      if (kParams.has_animation) {
        // Restart the completed animation to pickup the color profile change.
        if (kParams.done && kParams.loop_count == 0) {
          kParams.loop_count =
              (int)WebPDemuxGetI(kParams.dmux, WEBP_FF_LOOP_COUNT) + 1;
          kParams.done = 0;
          // Start the decode loop immediately.
          glutTimerFunc(0, decode_callback, 0);
        }
      } else {
        Decode();
        glutPostRedisplay();
      }
    }
  } else if (key == 'b') {
    kParams.draw_anim_background_color = 1 - kParams.draw_anim_background_color;
    if (!kParams.has_animation) ClearPreviousFrame();
    glutPostRedisplay();
  } else if (key == 'i') {
    kParams.print_info = 1 - kParams.print_info;
    if (!kParams.has_animation) ClearPreviousFrame();
    glutPostRedisplay();
  } else if (key == 'd') {
    kParams.only_deltas = 1 - kParams.only_deltas;
    glutPostRedisplay();
  }
}

static void HandleReshape(int width, int height) {
  // Note: reshape doesn't preserve aspect ratio, and might
  // be handling larger-than-screen pictures incorrectly.
  glViewport(0, 0, width, height);
  glMatrixMode(GL_PROJECTION);
  glLoadIdentity();
  glMatrixMode(GL_MODELVIEW);
  glLoadIdentity();
  kParams.viewport_width = width;
  kParams.viewport_height = height;
  if (!kParams.has_animation) ClearPreviousFrame();
}

static void PrintString(const char* const text) {
  void* const font = GLUT_BITMAP_9_BY_15;
  int i;
  for (i = 0; text[i]; ++i) {
    glutBitmapCharacter(font, text[i]);
  }
}

static void PrintStringW(const char* const text) {
#if defined(_WIN32) && defined(_UNICODE)
  void* const font = GLUT_BITMAP_9_BY_15;
  const W_CHAR* const wtext = (const W_CHAR*)text;
  int i;
  for (i = 0; wtext[i]; ++i) {
    glutBitmapCharacter(font, wtext[i]);
  }
#else
  PrintString(text);
#endif
}

static float GetColorf(uint32_t color, int shift) {
  return ((color >> shift) & 0xff) / 255.f;
}

static void DrawCheckerBoard(void) {
  const int square_size = 8;  // must be a power of 2
  int x, y;
  GLint viewport[4];  // x, y, width, height

  glPushMatrix();

  glGetIntegerv(GL_VIEWPORT, viewport);
  // shift to integer coordinates with (0,0) being top-left.
  glOrtho(0, viewport[2], viewport[3], 0, -1, 1);
  for (y = 0; y < viewport[3]; y += square_size) {
    for (x = 0; x < viewport[2]; x += square_size) {
      const GLubyte color = 128 + 64 * (!((x + y) & square_size));
      glColor3ub(color, color, color);
      glRecti(x, y, x + square_size, y + square_size);
    }
  }
  glPopMatrix();
}

static void DrawBackground(void) {
  // Whole window cleared with clear color, checkerboard rendered on top of it.
  glClear(GL_COLOR_BUFFER_BIT);
  DrawCheckerBoard();

  // ANIM background color rendered (blend) on top. Default is white for still
  // images (without ANIM chunk). glClear() can't be used for that (no blend).
  if (kParams.draw_anim_background_color) {
    glPushMatrix();
    glLoadIdentity();
    glColor4f(GetColorf(kParams.bg_color, 16),  // BGRA from spec
              GetColorf(kParams.bg_color, 8),
              GetColorf(kParams.bg_color, 0),
              GetColorf(kParams.bg_color, 24));
    glRecti(-1, -1, +1, +1);
    glPopMatrix();
  }
}

// Draw background in a scissored rectangle.
static void DrawBackgroundScissored(int window_x, int window_y, int frame_w,
                                    int frame_h) {
  // Only update the requested area, not the whole canvas.
  window_x = window_x * kParams.viewport_width / kParams.canvas_width;
  window_y = window_y * kParams.viewport_height / kParams.canvas_height;
  frame_w = frame_w * kParams.viewport_width / kParams.canvas_width;
  frame_h = frame_h * kParams.viewport_height / kParams.canvas_height;

  // glScissor() takes window coordinates (0,0 at bottom left).
  window_y = kParams.viewport_height - window_y - frame_h;

  glEnable(GL_SCISSOR_TEST);
  glScissor(window_x, window_y, frame_w, frame_h);
  DrawBackground();
  glDisable(GL_SCISSOR_TEST);
}

static void HandleDisplay(void) {
  const WebPDecBuffer* const pic = kParams.pic;
  const WebPIterator* const curr = &kParams.curr_frame;
  WebPIterator* const prev = &kParams.prev_frame;
  GLfloat xoff, yoff;
  if (pic == NULL) return;
  glPushMatrix();
  glPixelZoom((GLfloat)(+1. / kParams.canvas_width * kParams.viewport_width),
              (GLfloat)(-1. / kParams.canvas_height * kParams.viewport_height));
  xoff = (GLfloat)(2. * curr->x_offset / kParams.canvas_width);
  yoff = (GLfloat)(2. * curr->y_offset / kParams.canvas_height);
  glRasterPos2f(-1.f + xoff, 1.f - yoff);
  glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
  glPixelStorei(GL_UNPACK_ROW_LENGTH, pic->u.RGBA.stride / 4);

  if (kParams.only_deltas) {
    DrawBackground();
  } else {
    // The rectangle of the previous frame might be different than the current
    // frame, so we may need to DrawBackgroundScissored for both.
    if (prev->dispose_method == WEBP_MUX_DISPOSE_BACKGROUND) {
      // Clear the previous frame rectangle.
      DrawBackgroundScissored(prev->x_offset, prev->y_offset, prev->width,
                              prev->height);
    }
    if (curr->blend_method == WEBP_MUX_NO_BLEND) {
      // We simulate no-blending behavior by first clearing the current frame
      // rectangle and then alpha-blending against it.
      DrawBackgroundScissored(curr->x_offset, curr->y_offset, curr->width,
                              curr->height);
    }
  }

  *prev = *curr;

  glDrawPixels(pic->width, pic->height,
               GL_RGBA, GL_UNSIGNED_BYTE,
               (GLvoid*)pic->u.RGBA.rgba);
  if (kParams.print_info) {
    char tmp[32];

    glColor4f(0.90f, 0.0f, 0.90f, 1.0f);
    glRasterPos2f(-0.95f, 0.90f);
    PrintStringW(kParams.file_name);

    snprintf(tmp, sizeof(tmp), "Dimension:%d x %d", pic->width, pic->height);
    glColor4f(0.90f, 0.0f, 0.90f, 1.0f);
    glRasterPos2f(-0.95f, 0.80f);
    PrintString(tmp);
    if (curr->x_offset != 0 || curr->y_offset != 0) {
      snprintf(tmp, sizeof(tmp), " (offset:%d,%d)",
               curr->x_offset, curr->y_offset);
      glRasterPos2f(-0.95f, 0.70f);
      PrintString(tmp);
    }
  }
  glPopMatrix();
#if defined(__APPLE__) || defined(_WIN32)
  glFlush();
#else
  glutSwapBuffers();
#endif
}

static void StartDisplay(const char* filename) {
  int width = kParams.canvas_width;
  int height = kParams.canvas_height;
  int screen_width, screen_height;
  const char viewername[] = " - WebP viewer";
  // max linux file len + viewername string
  char title[4096 + sizeof(viewername)] = "";
  // TODO(webp:365) GLUT_DOUBLE results in flickering / old frames to be
  // partially displayed with animated webp + alpha.
#if defined(__APPLE__) || defined(_WIN32)
  glutInitDisplayMode(GLUT_RGBA);
#else
  glutInitDisplayMode(GLUT_DOUBLE | GLUT_RGBA);
#endif
  screen_width = glutGet(GLUT_SCREEN_WIDTH);
  screen_height = glutGet(GLUT_SCREEN_HEIGHT);
  if (width > screen_width || height > screen_height) {
    if (width > screen_width) {
      height = (height * screen_width + width - 1) / width;
      width = screen_width;
    }
    if (height > screen_height) {
      width = (width * screen_height + height - 1) / height;
      height = screen_height;
    }
  }
  snprintf(title, sizeof(title), "%s%s", filename, viewername);
  glutInitWindowSize(width, height);
  glutCreateWindow(title);
  glutDisplayFunc(HandleDisplay);
  glutReshapeFunc(HandleReshape);
  glutIdleFunc(NULL);
  glutKeyboardFunc(HandleKey);
  glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
  glEnable(GL_BLEND);
  glClearColor(0, 0, 0, 0);  // window will be cleared to black (no blend)
  DrawBackground();
}

//------------------------------------------------------------------------------
// Main

static void Help(void) {
  printf(
      "Usage: vwebp in_file [options]\n\n"
      "Decodes the WebP image file and visualize it using OpenGL\n"
      "Options are:\n"
      "  -version ..... print version number and exit\n"
      "  -noicc ....... don't use the icc profile if present\n"
      "  -nofancy ..... don't use the fancy YUV420 upscaler\n"
      "  -nofilter .... disable in-loop filtering\n"
      "  -dither <int>  dithering strength (0..100), default=50\n"
      "  -noalphadither disable alpha plane dithering\n"
      "  -usebgcolor .. display background color\n"
      "  -mt .......... use multi-threading\n"
      "  -info ........ print info\n"
      "  -h ........... this help message\n"
      "\n"
      "Keyboard shortcuts:\n"
      "  'c' ................ toggle use of color profile\n"
      "  'b' ................ toggle background color display\n"
      "  'i' ................ overlay file information\n"
      "  'd' ................ disable blending & disposal (debug)\n"
      "  'q' / 'Q' / ESC .... quit\n");
}

int main(int argc, char* argv[]) {
  int c, file_name_argv_index = 1;
  WebPDecoderConfig* const config = &kParams.config;
  WebPIterator* const curr = &kParams.curr_frame;

  INIT_WARGV(argc, argv);

  if (!WebPInitDecoderConfig(config)) {
    fprintf(stderr, "Library version mismatch!\n");
    FREE_WARGV_AND_RETURN(EXIT_FAILURE);
  }
  config->options.dithering_strength = 50;
  config->options.alpha_dithering_strength = 100;
  kParams.use_color_profile = 1;
  // Background color hidden by default to see transparent areas.
  kParams.draw_anim_background_color = 0;

  for (c = 1; c < argc; ++c) {
    int parse_error = 0;
    if (!strcmp(argv[c], "-h") || !strcmp(argv[c], "-help")) {
      Help();
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else if (!strcmp(argv[c], "-noicc")) {
      kParams.use_color_profile = 0;
    } else if (!strcmp(argv[c], "-nofancy")) {
      config->options.no_fancy_upsampling = 1;
    } else if (!strcmp(argv[c], "-nofilter")) {
      config->options.bypass_filtering = 1;
    } else if (!strcmp(argv[c], "-noalphadither")) {
      config->options.alpha_dithering_strength = 0;
    } else if (!strcmp(argv[c], "-usebgcolor")) {
      kParams.draw_anim_background_color = 1;
    } else if (!strcmp(argv[c], "-dither") && c + 1 < argc) {
      config->options.dithering_strength =
          ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-info")) {
      kParams.print_info = 1;
    } else if (!strcmp(argv[c], "-version")) {
      const int dec_version = WebPGetDecoderVersion();
      const int dmux_version = WebPGetDemuxVersion();
      printf("WebP Decoder version: %d.%d.%d\nWebP Demux version: %d.%d.%d\n",
             (dec_version >> 16) & 0xff, (dec_version >> 8) & 0xff,
             dec_version & 0xff, (dmux_version >> 16) & 0xff,
             (dmux_version >> 8) & 0xff, dmux_version & 0xff);
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else if (!strcmp(argv[c], "-mt")) {
      config->options.use_threads = 1;
    } else if (!strcmp(argv[c], "--")) {
      if (c < argc - 1) {
        kParams.file_name = (const char*)GET_WARGV(argv, ++c);
        file_name_argv_index = c;
      }
      break;
    } else if (argv[c][0] == '-') {
      printf("Unknown option '%s'\n", argv[c]);
      Help();
      FREE_WARGV_AND_RETURN(EXIT_FAILURE);
    } else {
      kParams.file_name = (const char*)GET_WARGV(argv, c);
      file_name_argv_index = c;
    }

    if (parse_error) {
      Help();
      FREE_WARGV_AND_RETURN(EXIT_FAILURE);
    }
  }

  if (kParams.file_name == NULL) {
    printf("missing input file!!\n");
    Help();
    FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
  }

  if (!ImgIoUtilReadFile(kParams.file_name,
                         &kParams.data.bytes, &kParams.data.size)) {
    goto Error;
  }

  if (!WebPGetInfo(kParams.data.bytes, kParams.data.size, NULL, NULL)) {
    fprintf(stderr, "Input file doesn't appear to be WebP format.\n");
    goto Error;
  }

  kParams.dmux = WebPDemux(&kParams.data);
  if (kParams.dmux == NULL) {
    fprintf(stderr, "Could not create demuxing object!\n");
    goto Error;
  }

  kParams.canvas_width = WebPDemuxGetI(kParams.dmux, WEBP_FF_CANVAS_WIDTH);
  kParams.canvas_height = WebPDemuxGetI(kParams.dmux, WEBP_FF_CANVAS_HEIGHT);
  if (kParams.print_info) {
    printf("Canvas: %d x %d\n", kParams.canvas_width, kParams.canvas_height);
  }

  ClearPreviousFrame();

  memset(&kParams.iccp, 0, sizeof(kParams.iccp));
  kParams.has_color_profile =
      !!(WebPDemuxGetI(kParams.dmux, WEBP_FF_FORMAT_FLAGS) & ICCP_FLAG);
  if (kParams.has_color_profile) {
#ifdef WEBP_HAVE_QCMS
    if (!WebPDemuxGetChunk(kParams.dmux, "ICCP", 1, &kParams.iccp)) goto Error;
    printf("VP8X: Found color profile\n");
#else
    fprintf(stderr, "Warning: color profile present, but qcms is unavailable!\n"
            "Build libqcms from Mozilla or Chromium and define WEBP_HAVE_QCMS "
            "before building.\n");
#endif
  }

  if (!WebPDemuxGetFrame(kParams.dmux, 1, curr)) goto Error;

  kParams.has_animation = (curr->num_frames > 1);
  kParams.loop_count = (int)WebPDemuxGetI(kParams.dmux, WEBP_FF_LOOP_COUNT);
  kParams.bg_color = WebPDemuxGetI(kParams.dmux, WEBP_FF_BACKGROUND_COLOR);
  printf("VP8X: Found %d images in file (loop count = %d)\n",
         curr->num_frames, kParams.loop_count);

  // Decode first frame
  if (!Decode()) goto Error;

  // Position iterator to last frame. Next call to HandleDisplay will wrap over.
  // We take this into account by bumping up loop_count.
  if (!WebPDemuxGetFrame(kParams.dmux, 0, curr)) goto Error;
  if (kParams.loop_count) ++kParams.loop_count;

#if defined(__unix__) || defined(__CYGWIN__)
  // Work around GLUT compositor bug.
  // https://bugs.launchpad.net/ubuntu/+source/freeglut/+bug/369891
  setenv("XLIB_SKIP_ARGB_VISUALS", "1", 1);
#endif

  // Start display (and timer)
  glutInit(&argc, argv);
#ifdef FREEGLUT
  glutSetOption(GLUT_ACTION_ON_WINDOW_CLOSE, GLUT_ACTION_CONTINUE_EXECUTION);
#endif
  StartDisplay(argv[file_name_argv_index]);

  if (kParams.has_animation) glutTimerFunc(0, decode_callback, 0);
  glutMainLoop();

  // Should only be reached when using FREEGLUT:
  ClearParams();
  FREE_WARGV_AND_RETURN(EXIT_SUCCESS);

 Error:
  ClearParams();
  FREE_WARGV_AND_RETURN(EXIT_FAILURE);
}

#else   // !WEBP_HAVE_GL

int main(int argc, const char* argv[]) {
  fprintf(stderr, "OpenGL support not enabled in %s.\n", argv[0]);
  (void)argc;
  return EXIT_FAILURE;
}

#endif

//------------------------------------------------------------------------------

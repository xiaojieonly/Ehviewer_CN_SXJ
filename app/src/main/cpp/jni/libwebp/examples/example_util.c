// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Utility functions used by the example programs.
//

#include "./example_util.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "webp/mux_types.h"
#include "../imageio/imageio_util.h"

//------------------------------------------------------------------------------
// String parsing

uint32_t ExUtilGetUInt(const char* const v, int base, int* const error) {
  char* end = NULL;
  const uint32_t n = (v != NULL) ? (uint32_t)strtoul(v, &end, base) : 0u;
  if (end == v && error != NULL && !*error) {
    *error = 1;
    fprintf(stderr, "Error! '%s' is not an integer.\n",
            (v != NULL) ? v : "(null)");
  }
  return n;
}

int ExUtilGetInt(const char* const v, int base, int* const error) {
  return (int)ExUtilGetUInt(v, base, error);
}

int ExUtilGetInts(const char* v, int base, int max_output, int output[]) {
  int n, error = 0;
  for (n = 0; v != NULL && n < max_output; ++n) {
    const int value = ExUtilGetInt(v, base, &error);
    if (error) return -1;
    output[n] = value;
    v = strchr(v, ',');
    if (v != NULL) ++v;   // skip over the trailing ','
  }
  return n;
}

float ExUtilGetFloat(const char* const v, int* const error) {
  char* end = NULL;
  const float f = (v != NULL) ? (float)strtod(v, &end) : 0.f;
  if (end == v && error != NULL && !*error) {
    *error = 1;
    fprintf(stderr, "Error! '%s' is not a floating point number.\n",
            (v != NULL) ? v : "(null)");
  }
  return f;
}

//------------------------------------------------------------------------------

static void ResetCommandLineArguments(int argc, const char* argv[],
                                      CommandLineArguments* const args) {
  assert(args != NULL);
  args->argc_ = argc;
  args->argv_ = argv;
  args->own_argv_ = 0;
  WebPDataInit(&args->argv_data_);
}

void ExUtilDeleteCommandLineArguments(CommandLineArguments* const args) {
  if (args != NULL) {
    if (args->own_argv_) {
      WebPFree((void*)args->argv_);
      WebPDataClear(&args->argv_data_);
    }
    ResetCommandLineArguments(0, NULL, args);
  }
}

#define MAX_ARGC 16384
int ExUtilInitCommandLineArguments(int argc, const char* argv[],
                                   CommandLineArguments* const args) {
  if (args == NULL || argv == NULL) return 0;
  ResetCommandLineArguments(argc, argv, args);
  if (argc == 1 && argv[0][0] != '-') {
    char* cur;
    const char sep[] = " \t\r\n\f\v";

#if defined(_WIN32) && defined(_UNICODE)
    fprintf(stderr,
            "Error: Reading arguments from a file is a feature unavailable "
            "with Unicode binaries.\n");
    return 0;
#endif

    if (!ExUtilReadFileToWebPData(argv[0], &args->argv_data_)) {
      return 0;
    }
    args->own_argv_ = 1;
    args->argv_ = (const char**)WebPMalloc(MAX_ARGC * sizeof(*args->argv_));
    if (args->argv_ == NULL) {
      ExUtilDeleteCommandLineArguments(args);
      return 0;
    }

    argc = 0;
    for (cur = strtok((char*)args->argv_data_.bytes, sep);
         cur != NULL;
         cur = strtok(NULL, sep)) {
      if (argc == MAX_ARGC) {
        fprintf(stderr, "ERROR: Arguments limit %d reached\n", MAX_ARGC);
        ExUtilDeleteCommandLineArguments(args);
        return 0;
      }
      assert(strlen(cur) != 0);
      args->argv_[argc++] = cur;
    }
    args->argc_ = argc;
  }
  return 1;
}

//------------------------------------------------------------------------------

int ExUtilReadFileToWebPData(const char* const filename,
                             WebPData* const webp_data) {
  const uint8_t* data;
  size_t size;
  if (webp_data == NULL) return 0;
  if (!ImgIoUtilReadFile(filename, &data, &size)) return 0;
  webp_data->bytes = data;
  webp_data->size = size;
  return 1;
}

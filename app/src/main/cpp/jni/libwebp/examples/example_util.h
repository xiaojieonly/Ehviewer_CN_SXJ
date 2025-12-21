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

#ifndef WEBP_EXAMPLES_EXAMPLE_UTIL_H_
#define WEBP_EXAMPLES_EXAMPLE_UTIL_H_

#include "webp/types.h"
#include "webp/mux_types.h"

#ifdef __cplusplus
extern "C" {
#endif

//------------------------------------------------------------------------------
// String parsing

// Parses 'v' using strto(ul|l|d)(). If error is non-NULL, '*error' is set to
// true on failure while on success it is left unmodified to allow chaining of
// calls. An error is only printed on the first occurrence.
uint32_t ExUtilGetUInt(const char* const v, int base, int* const error);
int ExUtilGetInt(const char* const v, int base, int* const error);
float ExUtilGetFloat(const char* const v, int* const error);

// This variant of ExUtilGetInt() will parse multiple integers from a
// comma-separated list. Up to 'max_output' integers are parsed.
// The result is placed in the output[] array, and the number of integers
// actually parsed is returned, or -1 if an error occurred.
int ExUtilGetInts(const char* v, int base, int max_output, int output[]);

// Reads a file named 'filename' into a WebPData structure. The content of
// webp_data is overwritten. Returns false in case of error.
int ExUtilReadFileToWebPData(const char* const filename,
                             WebPData* const webp_data);

//------------------------------------------------------------------------------
// Command-line arguments

typedef struct {
  int argc_;
  const char** argv_;
  WebPData argv_data_;
  int own_argv_;
} CommandLineArguments;

// Initializes the structure from the command-line parameters. If there is
// only one parameter and it does not start with a '-', then it is assumed to
// be a file name. This file will be read and tokenized into command-line
// arguments. The content of 'args' is overwritten.
// Returns false in case of error (memory allocation failure, non
// existing file, too many arguments, ...).
int ExUtilInitCommandLineArguments(int argc, const char* argv[],
                                   CommandLineArguments* const args);

// Deallocate all memory and reset 'args'.
void ExUtilDeleteCommandLineArguments(CommandLineArguments* const args);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_EXAMPLES_EXAMPLE_UTIL_H_

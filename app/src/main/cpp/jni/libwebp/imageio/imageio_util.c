// Copyright 2016 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Utility functions used by the image decoders.
//

#include "./imageio_util.h"

#if defined(_WIN32)
#include <fcntl.h>   // for _O_BINARY
#include <io.h>      // for _setmode()
#endif
#include <stdlib.h>
#include <string.h>
#include "../examples/unicode.h"

// -----------------------------------------------------------------------------
// File I/O

FILE* ImgIoUtilSetBinaryMode(FILE* file) {
#if defined(_WIN32)
  if (_setmode(_fileno(file), _O_BINARY) == -1) {
    fprintf(stderr, "Failed to reopen file in O_BINARY mode.\n");
    return NULL;
  }
#endif
  return file;
}

int ImgIoUtilReadFromStdin(const uint8_t** data, size_t* data_size) {
  static const size_t kBlockSize = 16384;  // default initial size
  size_t max_size = 0;
  size_t size = 0;
  uint8_t* input = NULL;

  if (data == NULL || data_size == NULL) return 0;
  *data = NULL;
  *data_size = 0;

  if (!ImgIoUtilSetBinaryMode(stdin)) return 0;

  while (!feof(stdin)) {
    // We double the buffer size each time and read as much as possible.
    const size_t extra_size = (max_size == 0) ? kBlockSize : max_size;
    // we allocate one extra byte for the \0 terminator
    void* const new_data = realloc(input, max_size + extra_size + 1);
    if (new_data == NULL) goto Error;
    input = (uint8_t*)new_data;
    max_size += extra_size;
    size += fread(input + size, 1, extra_size, stdin);
    if (size < max_size) break;
  }
  if (ferror(stdin)) goto Error;
  if (input != NULL) input[size] = '\0';  // convenient 0-terminator
  *data = input;
  *data_size = size;
  return 1;

 Error:
  free(input);
  fprintf(stderr, "Could not read from stdin\n");
  return 0;
}

int ImgIoUtilReadFile(const char* const file_name,
                      const uint8_t** data, size_t* data_size) {
  int ok;
  uint8_t* file_data;
  size_t file_size;
  FILE* in;
  const int from_stdin = (file_name == NULL) || !WSTRCMP(file_name, "-");

  if (from_stdin) return ImgIoUtilReadFromStdin(data, data_size);

  if (data == NULL || data_size == NULL) return 0;
  *data = NULL;
  *data_size = 0;

  in = WFOPEN(file_name, "rb");
  if (in == NULL) {
    WFPRINTF(stderr, "cannot open input file '%s'\n", (const W_CHAR*)file_name);
    return 0;
  }
  fseek(in, 0, SEEK_END);
  file_size = ftell(in);
  if (file_size == (size_t)-1) {
    fclose(in);
    WFPRINTF(stderr, "error getting size of '%s'\n", (const W_CHAR*)file_name);
    return 0;
  }
  fseek(in, 0, SEEK_SET);
  // we allocate one extra byte for the \0 terminator
  file_data = (uint8_t*)WebPMalloc(file_size + 1);
  if (file_data == NULL) {
    fclose(in);
    WFPRINTF(stderr, "memory allocation failure when reading file %s\n",
             (const W_CHAR*)file_name);
    return 0;
  }
  ok = (fread(file_data, file_size, 1, in) == 1);
  fclose(in);

  if (!ok) {
    WFPRINTF(stderr, "Could not read %d bytes of data from file %s\n",
             (int)file_size, (const W_CHAR*)file_name);
    WebPFree(file_data);
    return 0;
  }
  file_data[file_size] = '\0';  // convenient 0-terminator
  *data = file_data;
  *data_size = file_size;
  return 1;
}

// -----------------------------------------------------------------------------

int ImgIoUtilWriteFile(const char* const file_name,
                       const uint8_t* data, size_t data_size) {
  int ok;
  FILE* out;
  const int to_stdout = (file_name == NULL) || !WSTRCMP(file_name, "-");

  if (data == NULL) {
    return 0;
  }
  out = to_stdout ? ImgIoUtilSetBinaryMode(stdout) : WFOPEN(file_name, "wb");
  if (out == NULL) {
    WFPRINTF(stderr, "Error! Cannot open output file '%s'\n",
             (const W_CHAR*)file_name);
    return 0;
  }
  ok = (fwrite(data, data_size, 1, out) == 1);
  if (out != stdout) fclose(out);
  return ok;
}

// -----------------------------------------------------------------------------

void ImgIoUtilCopyPlane(const uint8_t* src, int src_stride,
                        uint8_t* dst, int dst_stride, int width, int height) {
  while (height-- > 0) {
    memcpy(dst, src, width * sizeof(*dst));
    src += src_stride;
    dst += dst_stride;
  }
}

// -----------------------------------------------------------------------------

int ImgIoUtilCheckSizeArgumentsOverflow(uint64_t stride, size_t height) {
  const uint64_t total_size = stride * height;
  int ok = (total_size == (size_t)total_size);
  // check that 'stride' is representable as int:
  ok = ok && ((uint64_t)(int)stride == stride);
#if defined(WEBP_MAX_IMAGE_SIZE)
  ok = ok && (total_size <= (uint64_t)WEBP_MAX_IMAGE_SIZE);
#endif
  return ok;
}

// -----------------------------------------------------------------------------

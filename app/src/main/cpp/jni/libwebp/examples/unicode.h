// Copyright 2018 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Unicode support for Windows. The main idea is to maintain an array of Unicode
// arguments (wargv) and use it only for file paths. The regular argv is used
// for everything else.
//
// Author: Yannis Guyon (yguyon@google.com)

#ifndef WEBP_EXAMPLES_UNICODE_H_
#define WEBP_EXAMPLES_UNICODE_H_

#include <stdio.h>

#if defined(_WIN32) && defined(_UNICODE)

// wchar_t is used instead of TCHAR because we only perform additional work when
// Unicode is enabled and because the output of CommandLineToArgvW() is wchar_t.

#include <fcntl.h>
#include <io.h>
#include <wchar.h>
#include <windows.h>
#include <shellapi.h>

// Create a wchar_t array containing Unicode parameters.
#define INIT_WARGV(ARGC, ARGV)                                                \
  int wargc;                                                                  \
  const W_CHAR** const wargv =                                                \
      (const W_CHAR**)CommandLineToArgvW(GetCommandLineW(), &wargc);          \
  do {                                                                        \
    if (wargv == NULL || wargc != (ARGC)) {                                   \
      fprintf(stderr, "Error: Unable to get Unicode arguments.\n");           \
      FREE_WARGV_AND_RETURN(-1);                                              \
    }                                                                         \
  } while (0)

// Use this to get a Unicode argument (e.g. file path).
#define GET_WARGV(UNUSED, C) wargv[C]
// For cases where argv is shifted by one compared to wargv.
#define GET_WARGV_SHIFTED(UNUSED, C) wargv[(C) + 1]
#define GET_WARGV_OR_NULL() wargv

// Release resources. LocalFree() is needed after CommandLineToArgvW().
#define FREE_WARGV() LOCAL_FREE((W_CHAR** const)wargv)
#define LOCAL_FREE(WARGV)                  \
  do {                                     \
    if ((WARGV) != NULL) LocalFree(WARGV); \
  } while (0)

#define W_CHAR wchar_t  // WCHAR without underscore might already be defined.
#define TO_W_CHAR(STR) (L##STR)

#define WFOPEN(ARG, OPT) _wfopen((const W_CHAR*)ARG, TO_W_CHAR(OPT))

#define WFPRINTF(STREAM, STR, ...)                    \
  do {                                                \
    int prev_mode;                                    \
    fflush(STREAM);                                   \
    prev_mode = _setmode(_fileno(STREAM), _O_U8TEXT); \
    fwprintf(STREAM, TO_W_CHAR(STR), __VA_ARGS__);    \
    fflush(STREAM);                                   \
    (void)_setmode(_fileno(STREAM), prev_mode);       \
  } while (0)
#define WPRINTF(STR, ...) WFPRINTF(stdout, STR, __VA_ARGS__)

#define WSTRLEN(FILENAME) wcslen((const W_CHAR*)FILENAME)
#define WSTRCMP(FILENAME, STR) wcscmp((const W_CHAR*)FILENAME, TO_W_CHAR(STR))
#define WSTRRCHR(FILENAME, STR) wcsrchr((const W_CHAR*)FILENAME, TO_W_CHAR(STR))
#define WSNPRINTF(A, B, STR, ...) _snwprintf(A, B, TO_W_CHAR(STR), __VA_ARGS__)

#else

#include <string.h>

// Unicode file paths work as is on Unix platforms, and no extra work is done on
// Windows either if Unicode is disabled.

#define INIT_WARGV(ARGC, ARGV)

#define GET_WARGV(ARGV, C) (ARGV)[C]
#define GET_WARGV_SHIFTED(ARGV, C) (ARGV)[C]
#define GET_WARGV_OR_NULL() NULL

#define FREE_WARGV()
#define LOCAL_FREE(WARGV)

#define W_CHAR char
#define TO_W_CHAR(STR) (STR)

#define WFOPEN(ARG, OPT) fopen(ARG, OPT)

#define WPRINTF(STR, ...) printf(STR, __VA_ARGS__)
#define WFPRINTF(STREAM, STR, ...) fprintf(STREAM, STR, __VA_ARGS__)

#define WSTRLEN(FILENAME) strlen(FILENAME)
#define WSTRCMP(FILENAME, STR) strcmp(FILENAME, STR)
#define WSTRRCHR(FILENAME, STR) strrchr(FILENAME, STR)
#define WSNPRINTF(A, B, STR, ...) snprintf(A, B, STR, __VA_ARGS__)

#endif  // defined(_WIN32) && defined(_UNICODE)

// Don't forget to free wargv before returning (e.g. from main).
#define FREE_WARGV_AND_RETURN(VALUE) \
  do {                               \
    FREE_WARGV();                    \
    return (VALUE);                  \
  } while (0)

#endif  // WEBP_EXAMPLES_UNICODE_H_

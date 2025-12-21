// Copyright 2011 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Helper functions to measure elapsed time.
//
// Author: Mikolaj Zalewski (mikolajz@google.com)

#ifndef WEBP_EXAMPLES_STOPWATCH_H_
#define WEBP_EXAMPLES_STOPWATCH_H_

#include "webp/types.h"

#if defined _WIN32 && !defined __GNUC__
#include <windows.h>

typedef LARGE_INTEGER Stopwatch;

static WEBP_INLINE void StopwatchReset(Stopwatch* watch) {
  QueryPerformanceCounter(watch);
}

static WEBP_INLINE double StopwatchReadAndReset(Stopwatch* watch) {
  const LARGE_INTEGER old_value = *watch;
  LARGE_INTEGER freq;
  if (!QueryPerformanceCounter(watch))
    return 0.0;
  if (!QueryPerformanceFrequency(&freq))
    return 0.0;
  if (freq.QuadPart == 0)
    return 0.0;
  return (watch->QuadPart - old_value.QuadPart) / (double)freq.QuadPart;
}


#else    /* !_WIN32 */
#include <string.h>  // memcpy
#include <sys/time.h>

typedef struct timeval Stopwatch;

static WEBP_INLINE void StopwatchReset(Stopwatch* watch) {
  gettimeofday(watch, NULL);
}

static WEBP_INLINE double StopwatchReadAndReset(Stopwatch* watch) {
  struct timeval old_value;
  double delta_sec, delta_usec;
  memcpy(&old_value, watch, sizeof(old_value));
  gettimeofday(watch, NULL);
  delta_sec = (double)watch->tv_sec - old_value.tv_sec;
  delta_usec = (double)watch->tv_usec - old_value.tv_usec;
  return delta_sec + delta_usec / 1000000.0;
}

#endif   /* _WIN32 */

#endif  // WEBP_EXAMPLES_STOPWATCH_H_

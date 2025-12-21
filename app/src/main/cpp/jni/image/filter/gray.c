/*
 * Copyright 2019 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "gray.h"
#include "pixel.h"

bool IMAGE_is_gray(void* data, int width, int height, int error) {
  PIXEL* p = data;

  for (int i = 0, n = width * height; i < n; i++, p++) {
    int e1 = p->c1 - p->c2;
    int e2 = p->c1 - p->c3;
    int e3 = p->c2 - p->c3;

    if (e1 > error || e1 < -error
        || e2 > error || e2 < -error
        || e3 > error || e3 < -error) {
      return false;
    }
  }

  return true;
}

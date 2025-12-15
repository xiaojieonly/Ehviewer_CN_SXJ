/*
 * Copyright 2018 Hippo Seven
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

#include "clahe.h"
#include "pixel.h"
#include "../../utils.h"
#include "../../log.h"

#define TILE_BLOCK 8
#define LIMIT_MULTIPLE 4
#define CL_MAX_LOOP_NUMBER 4

static BYTE clamp(int t) {
  t = t > 255 ? 255 : t;
  t = t < 0 ? 0 : t;
  return (BYTE) t;
}

void IMAGE_clahe(void* data, int width, int height, bool to_gray) {
  if (width < TILE_BLOCK || height < TILE_BLOCK) {
    return;
  }

  PIXEL* pixels = data;

  if (to_gray) {
    for (int i = 0; i < width * height; ++i) {
      PIXEL* p = pixels + i;
      int t1 = (int) (p->c1 * 0.299f + p->c2 * 0.587f + p->c3 * 0.114f);
      p->c1 = clamp(t1);
    }
  } else {
    // RGBA to YUVA
    for (int i = 0; i < width * height; ++i) {
      PIXEL* p = pixels + i;
      int t1 = (int) (p->c1 * 0.299f + p->c2 * 0.587f + p->c3 * 0.114f);
      int t2 = (int) (p->c1 * -0.169f + p->c2 * -0.331f + p->c3 * 0.5f + 128);
      int t3 = (int) (p->c1 * 0.5f + p->c2 * -0.419f + p->c3 * -0.081f + 128);
      p->c1 = clamp(t1);
      p->c2 = clamp(t2);
      p->c3 = clamp(t3);
    }
  }

  int tile_width = width / TILE_BLOCK;
  int tile_height = height / TILE_BLOCK;
  int histogram[TILE_BLOCK * TILE_BLOCK][256] = {0};
  float accumulation[TILE_BLOCK * TILE_BLOCK][256] = {0.0f};

  // Build histogram and accumulation
  for (int i = 0; i < TILE_BLOCK; i++) {
    for (int j = 0; j < TILE_BLOCK; j++) {
      int x_start = tile_width * i;
      int x_end = i == TILE_BLOCK - 1 ? width : x_start + tile_width;
      int y_start = tile_height * j;
      int y_end = j == TILE_BLOCK - 1 ? height : y_start + tile_height;
      int tile_size = (x_end - x_start) * (y_end - y_start);
      int tile_index = i + TILE_BLOCK * j;

      // Count
      for(int x = x_start; x < x_end; x++) {
        for(int y = y_start; y < y_end ; y++) {
          PIXEL* p = pixels + x + y * width;
          histogram[tile_index][p->c1] += 1;
        }
      }

      // CL
      int limit = tile_size * LIMIT_MULTIPLE / 256;
      for (int m = 0; m < CL_MAX_LOOP_NUMBER; m++) {
        int steal = 0;
        for (int n = 0; n < 256; n++) {
          if (histogram[tile_index][n] > limit) {
            steal += histogram[tile_index][n] - limit;
            histogram[tile_index][n] = limit;
          }
        }
        int bonus = steal / 256;
        if (bonus == 0 && steal != 0 && m == 0) {
          bonus = 1;
        }
        if (bonus == 0) {
          break;
        }
        for (int n = 0; n < 256; n++) {
          histogram[tile_index][n] += bonus;
        }
      }

      // accumulation
      for (int n = 0; n < 256; n++) {
        accumulation[tile_index][n] = (float) histogram[tile_index][n] / tile_size;
        if (n != 0) {
          accumulation[tile_index][n] += accumulation[tile_index][n - 1];
        }
      }
    }
  }

  // Interpolation
  for (int x = 0; x < width; x++) {
    for (int y = 0; y < height; y++) {
      PIXEL* p = pixels + x + y * width;
      // top-left corner
      if (x <= tile_width / 2 && y <= tile_height / 2) {
        p->c1 = clamp((int) (accumulation[0][p->c1] * 255));
        continue;
      }
      // top-right corner
      if (x > width - tile_width / 2 && y <= tile_height / 2) {
        p->c1 = clamp((int) (accumulation[TILE_BLOCK - 1][p->c1] * 255));
        continue;
      }
      // bottom-left corner
      if (x <= tile_width / 2 && y > height - tile_height / 2) {
        p->c1 = clamp((int) (accumulation[TILE_BLOCK * (TILE_BLOCK - 1)][p->c1] * 255));
        continue;
      }
      // bottom-right corner
      if (x > width - tile_width / 2 && y > height - tile_height / 2) {
        p->c1 = clamp((int) (accumulation[TILE_BLOCK * TILE_BLOCK - 1][p->c1] * 255));
        continue;
      }

      int tile_x_index = (x - tile_width / 2) / tile_width;
      int tile_y_index = (y - tile_height / 2) / tile_height;
      int tile_index1 = tile_x_index + TILE_BLOCK * tile_y_index;
      int tile_index2 = tile_index1 + 1;
      float u = (float) (x - (tile_x_index * tile_width + tile_width / 2)) / tile_width;
      // top side and bottom side
      if (y <= tile_height / 2 || y > height - tile_height / 2) {
        p->c1 = clamp((int) (255 * (accumulation[tile_index1][p->c1] * (1 - u) + accumulation[tile_index2][p->c1] * u)));
        continue;
      }

      int tile_index3 = tile_index1 + TILE_BLOCK;
      float v = (float) (y - (tile_y_index * tile_height + tile_height / 2)) / tile_height;
      // left side and right side
      if (x <= tile_width / 2 || x > width - tile_width / 2) {
        p->c1 = clamp((int) (255 * (accumulation[tile_index1][p->c1] * (1 - v) + accumulation[tile_index3][p->c1] * v)));
        continue;
      }

      int tile_index4 = tile_index2 + TILE_BLOCK;
      p->c1 = clamp((int) (255 * (
          accumulation[tile_index1][p->c1] * (1 - u) * (1 - v)
              + accumulation[tile_index2][p->c1] * u * (1 - v)
              + accumulation[tile_index3][p->c1] * (1 - u) * v
              + accumulation[tile_index4][p->c1] * u * v)));
    }
  }

  if (to_gray) {
    for (int i = 0; i < width * height; ++i) {
      PIXEL* p = pixels + i;
      p->c2 = p->c1;
      p->c3 = p->c1;
    }
  } else {
    // YUVA to RGBA
    for (int i = 0; i < width * height; ++i) {
      PIXEL* p = pixels + i;
      int t1 = (int) (p->c1 + (p->c2 - 128) * -0.00093f + (p->c3 - 128) * 1.401687f);
      int t2 = (int) (p->c1 + (p->c2 - 128) * -0.3437f + (p->c3 - 128) * -0.71417f);
      int t3 = (int) (p->c1 + (p->c2 - 128) * 1.77216f + (p->c3 - 128) * 0.00099f);
      p->c1 = clamp(t1);
      p->c2 = clamp(t2);
      p->c3 = clamp(t3);
    }
  }
}

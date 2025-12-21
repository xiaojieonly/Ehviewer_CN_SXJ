//
// Created by Hippo on 12/27/2015.
//

#ifndef IMAGE_IMAGE_UTILS_H
#define IMAGE_IMAGE_UTILS_H

#include <stdbool.h>

void copy_pixels(const void* src, int src_w, int src_h, int src_x, int src_y,
    void* dst, int dst_w, int dst_h, int dst_x, int dst_y,
    int width, int height, bool fill_blank, int default_color);

#endif //IMAGE_IMAGE_UTILS_H

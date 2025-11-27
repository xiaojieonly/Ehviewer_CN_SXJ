/*
 * Copyright 2015 Hippo Seven
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

//
// Created by Hippo on 12/27/2015.
//

#include "image.h"
#include "image_plain.h"
#include "image_jpeg.h"
#include "image_png.h"
#include "image_gif.h"
#include "filter/clahe.h"
#include "filter/gray.h"
#include "../log.h"
#include "image_webp.h"

static int get_format(JNIEnv *env, InputStream *stream) {
    unsigned char temp[2];
    int read = read_input_stream(env, stream, temp, 0, 2);

    if (read == 2) {
#ifdef IMAGE_SUPPORT_JPEG
        if (temp[0] == IMAGE_JPEG_MAGIC_NUMBER_0 && temp[1] == IMAGE_JPEG_MAGIC_NUMBER_1) {
            return IMAGE_FORMAT_JPEG;
        }
#endif
#ifdef IMAGE_SUPPORT_PNG
        if (temp[0] == IMAGE_PNG_MAGIC_NUMBER_0 && temp[1] == IMAGE_PNG_MAGIC_NUMBER_1) {
            return IMAGE_FORMAT_PNG;
        }
#endif
#ifdef IMAGE_SUPPORT_GIF
        if (temp[0] == IMAGE_GIF_MAGIC_NUMBER_0 && temp[1] == IMAGE_GIF_MAGIC_NUMBER_1) {
            return IMAGE_FORMAT_GIF;
        }
#endif
#ifdef IMAGE_SUPPORT_WEBP
        if (temp[0] == IMAGE_WEBP_MAGIC_NUMBER_0 && temp[1] == IMAGE_WEBP_MAGIC_NUMBER_1) {
            return IMAGE_FORMAT_WEBP;
        }
        if (temp[0] == IMAGE_WEBP_MAGIC_NUMBER_00 && temp[1] == IMAGE_WEBP_MAGIC_NUMBER_11) {
            return IMAGE_FORMAT_WEBP;
        }
#endif
        LOGE(MSG("Can't recognize the two magic number: %d, %d"), temp[0], temp[1]);
    } else {
        LOGE(MSG("Can't read two magic number from stream"));
    }

    return IMAGE_FORMAT_UNKNOWN;
}

void *decode(JNIEnv *env, InputStream *stream, bool partially, int *format) {
    unsigned char magic_numbers[2];
    PatchHeadInputStream *patch_head_input_stream;

    *format = get_format(env, stream);

    switch (*format) {
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            magic_numbers[0] = IMAGE_JPEG_MAGIC_NUMBER_0;
            magic_numbers[1] = IMAGE_JPEG_MAGIC_NUMBER_1;
            break;
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            magic_numbers[0] = IMAGE_PNG_MAGIC_NUMBER_0;
            magic_numbers[1] = IMAGE_PNG_MAGIC_NUMBER_1;
            break;
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            magic_numbers[0] = IMAGE_GIF_MAGIC_NUMBER_0;
            magic_numbers[1] = IMAGE_GIF_MAGIC_NUMBER_1;
            break;
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
//            两种格式不知道用哪种
            magic_numbers[0] = IMAGE_WEBP_MAGIC_NUMBER_0;
            magic_numbers[1] = IMAGE_WEBP_MAGIC_NUMBER_1;
            break;
#endif
        default:
            LOGE(MSG("Can't detect format %d"), *format);
            destroy_input_stream(env, &stream);
            return NULL;
    }

    patch_head_input_stream = create_patch_head_input_stream(stream, magic_numbers, 2);
    if (patch_head_input_stream == NULL) {
        WTF_OM;
        destroy_input_stream(env, &stream);
        return NULL;
    }

    switch (*format) {
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_decode(env, patch_head_input_stream, partially);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_decode(env, patch_head_input_stream, partially);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_decode(env, patch_head_input_stream, partially);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_decode(env, patch_head_input_stream, partially);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), *format);
            close_patch_head_input_stream(env, patch_head_input_stream);
            destroy_patch_head_input_stream(env, &patch_head_input_stream);
            return NULL;
    }
}

void *create(unsigned int width, unsigned int height, const void *data) {
#ifdef IMAGE_SUPPORT_PLAIN
    return PLAIN_create(width, height, data);
#else
    return NULL;
#endif
}

bool complete(JNIEnv *env, void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_complete((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_complete((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_complete(env, (PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_complete(env, (GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_complete(env, (WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return false;
    }
}

bool is_completed(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_is_completed((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_is_completed((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_is_completed((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_is_completed((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_is_completed((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return false;
    }
}

int get_width(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_get_width((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_get_width((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_get_width((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_get_width((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_get_width((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return -1;
    }
}

int get_height(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_get_height((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_get_height((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_get_height((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_get_height((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_get_height((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return -1;
    }
}

int get_byte_count(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_get_byte_count((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_get_byte_count((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_get_byte_count((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_get_byte_count((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_get_byte_count((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return -1;
    }
}

void render(void *image, int format, int src_x, int src_y,
            void *dst, int dst_w, int dst_h, int dst_x, int dst_y,
            int width, int height, bool fill_blank, int default_color) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            PLAIN_render((PLAIN *) image, src_x, src_y,
                         dst, dst_w, dst_h, dst_x, dst_y,
                         width, height, fill_blank, default_color);
            break;
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            JPEG_render((JPEG *) image, src_x, src_y,
                        dst, dst_w, dst_h, dst_x, dst_y,
                        width, height, fill_blank, default_color);
            break;
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            PNG_render((PNG *) image, src_x, src_y,
                       dst, dst_w, dst_h, dst_x, dst_y,
                       width, height, fill_blank, default_color);
            break;
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            GIF_render((GIF *) image, src_x, src_y,
                       dst, dst_w, dst_h, dst_x, dst_y,
                       width, height, fill_blank, default_color);
            break;
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            WEBP_render((WEBP *) image, src_x, src_y,
                        dst, dst_w, dst_h, dst_x, dst_y,
                        width, height, fill_blank, default_color);
            break;
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            break;
    }
}

void advance(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            PLAIN_advance((PLAIN *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            JPEG_advance((JPEG *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            PNG_advance((PNG *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            GIF_advance((GIF *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            WEBP_advance((WEBP *) image);
            break;
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
    }
}

int get_delay(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_get_delay((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_get_delay((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_get_delay((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_get_delay((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_get_delay((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return false;
    }
}

int get_frame_count(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_get_frame_count((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_get_frame_count((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_get_frame_count((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_get_frame_count((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_get_frame_count((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return false;
    }
}

bool is_opaque(void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            return PLAIN_is_opaque((PLAIN *) image);
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return JPEG_is_opaque((JPEG *) image);
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return PNG_is_opaque((PNG *) image);
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return GIF_is_opaque((GIF *) image);
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return WEBP_is_opaque((WEBP *) image);
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
            return false;
    }
}

static void get_image_data(void *image, int format, void **pixel, int *width, int *height) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN: {
            PLAIN *plain = (PLAIN *) image;
            *pixel = PLAIN_get_pixels(plain);
            *width = PLAIN_get_width(plain);
            *height = PLAIN_get_height(plain);
            break;
        }
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG: {
            JPEG *jpeg = (JPEG *) image;
            *pixel = JPEG_get_pixels(jpeg);
            *width = JPEG_get_width(jpeg);
            *height = JPEG_get_height(jpeg);
            break;
        }
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG: {
            PNG *png = (PNG *) image;
            *pixel = PNG_get_pixels(png);
            *width = PNG_get_width(png);
            *height = PNG_get_height(png);
            break;
        }
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF: {
            GIF *gif = (GIF *) image;
            *pixel = GIF_get_pixels(gif);
            *width = GIF_get_width(gif);
            *height = GIF_get_height(gif);
            break;
        }
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP: {
            WEBP *webp = (WEBP *) image;
            *pixel = WEBP_get_pixels(webp);
            *width = WEBP_get_width(webp);
            *height = WEBP_get_height(webp);
            break;
        }
#endif
        default:
            *pixel = NULL;
            *width = 0;
            *height = 0;
            break;
    }
}

bool is_gray(void *image, int format, int error) {
    void *pixel = NULL;
    int width = 0;
    int height = 0;

    get_image_data(image, format, &pixel, &width, &height);

    if (pixel == NULL || width == 0 || height == 0) {
        return false;
    }

    return IMAGE_is_gray(pixel, width, height, error);
}

void clahe(void *image, int format, bool to_gray) {
    void *pixel = NULL;
    int width = 0;
    int height = 0;

    get_image_data(image, format, &pixel, &width, &height);

    if (pixel == NULL || width == 0 || height == 0) {
        return;
    }

    IMAGE_clahe(pixel, width, height, to_gray);
}

void recycle(JNIEnv *env, void *image, int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_PLAIN
        case IMAGE_FORMAT_PLAIN:
            PLAIN_recycle((PLAIN *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            JPEG_recycle((JPEG *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            PNG_recycle(env, (PNG *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            GIF_recycle(env, (GIF *) image);
            break;
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            WEBP_recycle(env, (WEBP *) image);
            break;
#endif
        default:
            LOGE(MSG("Can't detect format %d"), format);
    }
}

int get_supported_formats(int *formats) {
    int i = 0;
#ifdef IMAGE_SUPPORT_JPEG
    formats[i++] = IMAGE_FORMAT_JPEG;
#endif
#ifdef IMAGE_SUPPORT_PNG
    formats[i++] = IMAGE_FORMAT_PNG;
#endif
#ifdef IMAGE_SUPPORT_GIF
    formats[i++] = IMAGE_FORMAT_GIF;
#endif
#ifdef IMAGE_SUPPORT_WEBP
    formats[i++] = IMAGE_FORMAT_WEBP;
#endif
    return i;
}

const char *get_decoder_description(int format) {
    switch (format) {
#ifdef IMAGE_SUPPORT_JPEG
        case IMAGE_FORMAT_JPEG:
            return IMAGE_JPEG_DECODER_DESCRIPTION;
#endif
#ifdef IMAGE_SUPPORT_PNG
        case IMAGE_FORMAT_PNG:
            return IMAGE_PNG_DECODER_DESCRIPTION;
#endif
#ifdef IMAGE_SUPPORT_GIF
        case IMAGE_FORMAT_GIF:
            return IMAGE_GIF_DECODER_DESCRIPTION;
#endif
#ifdef IMAGE_SUPPORT_WEBP
        case IMAGE_FORMAT_WEBP:
            return IMAGE_WEBP_DECODER_DESCRIPTION;
#endif
        default:
            return NULL;
    }
}

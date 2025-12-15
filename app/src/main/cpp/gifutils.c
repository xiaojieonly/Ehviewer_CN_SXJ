
/*
 * Copyright 2024 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * EhViewer. If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <string.h>
#include <stdbool.h>
#include <android/log.h>
#include <sys/stat.h>
#include <sys/mman.h>

#define LOG_TAG "gifUtils"

#include "ehviewer.h"

#define GIF_HEADER_87A "GIF87a"
#define GIF_HEADER_89A "GIF89a"

static int FRAME_DELAY_START_MARKER = 0x0021F904;

typedef signed char byte;

#define FRAME_DELAY_START_MARKER ((byte*)(&FRAME_DELAY_START_MARKER))
#define MINIMUM_FRAME_DELAY 2
#define DEFAULT_FRAME_DELAY 10

static inline bool isGif(void *addr) {
    return !memcmp(addr, GIF_HEADER_87A, 6) || !memcmp(addr, GIF_HEADER_89A, 6);
}

static void doRewrite(byte *addr, size_t size) {
    if (size < 7 || !isGif(addr)) return;
    for (size_t i = 0; i < size - 8; i++) {
        // TODO: Optimize this hex find with SIMD?
        if (addr[i] == FRAME_DELAY_START_MARKER[3] && addr[i + 1] == FRAME_DELAY_START_MARKER[2] &&
            addr[i + 2] == FRAME_DELAY_START_MARKER[1] &&
            addr[i + 3] == FRAME_DELAY_START_MARKER[0]) {
            byte *end = addr + i + 4;
            if (end[4] != 0) continue;
            int frameDelay = end[2] << 8 | end[1];
            if (frameDelay > MINIMUM_FRAME_DELAY)
                break; // Quit if the first block looks normal, for performance
            end[1] = DEFAULT_FRAME_DELAY;
            end[2] = 0;
        }
    }
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_ImageKt_rewriteGifSource(JNIEnv *env, jclass clazz, jobject buffer) {
    byte *addr = (*env)->GetDirectBufferAddress(env, buffer);
    size_t size = (*env)->GetDirectBufferCapacity(env, buffer);
    doRewrite(addr, size);
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_ImageKt_rewriteGifSource2(JNIEnv *env, jclass clazz, jint fd) {
    struct stat64 st;
    fstat64(fd, &st);
    size_t size = st.st_size;
    byte *addr = mmap64(0, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (addr == MAP_FAILED) return;
    doRewrite(addr, size);
    munmap(addr, size);
}

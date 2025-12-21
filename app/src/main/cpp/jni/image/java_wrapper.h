//
// Created by Hippo on 12/28/2015.
//

#ifndef IMAGE_JAVA_WRAPPER_H
#define IMAGE_JAVA_WRAPPER_H

#include <jni.h>
#include <stdbool.h>

#define IMAGE_TILE_MAX_SIZE (512 * 512)

JNIEnv *obtain_env(bool *attach);

void release_env();

#endif // IMAGE_JAVA_WRAPPER_H

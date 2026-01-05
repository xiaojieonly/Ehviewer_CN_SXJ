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

#include <stdlib.h>
#include <android/bitmap.h>
#include <GLES2/gl2.h>

#include "java_wrapper.h"
#include "com_hippo_lib_image_Image.h"
#include "input_stream.h"
#include "image.h"
#include "../log.h"

static JavaVM* jvm;

static void* tile_buffer;

JNIEnv *obtain_env(bool *attach)
{
  JNIEnv *env;
  switch ((*jvm)->GetEnv(jvm, (void**) &env, JNI_VERSION_1_6)) {
    case JNI_EDETACHED:
      if ((*jvm)->AttachCurrentThread(jvm, &env, NULL) == JNI_OK) {
        *attach = true;
        return env;
      } else {
        return NULL;
      }
    case JNI_OK:
      *attach = false;
      return env;
    default:
    case JNI_EVERSION:
      return NULL;
  }
}

void release_env()
{
  (*jvm)->DetachCurrentThread(jvm);
}

jobject create_image_object(JNIEnv* env, void* ptr, int format, int width, int height)
{
  jclass image_clazz;
  jmethodID constructor;

  image_clazz = (*env)->FindClass(env, "com/hippo/lib/image/Image1");
  constructor = (*env)->GetMethodID(env, image_clazz, "<init>", "(JIII)V");
  if (constructor == 0) {
    LOGE(MSG("Can't find Image object constructor"));
    return NULL;
  } else {
    return (*env)->NewObject(env, image_clazz, constructor,
        (jlong) (uintptr_t) ptr, (jint) format, (jint) width, (jint) height);
  }
}

JNIEXPORT jobject JNICALL
Java_com_hippo_lib_image_Image1_nativeDecode(JNIEnv* env,
                                             jclass clazz, jobject is, jboolean partially)
{
  InputStream* input_stream;
  int format;
  void* image;
  jobject image_object;

  input_stream = create_input_stream(env, is);
  if (input_stream == NULL) {
    return NULL;
  }

  image = decode(env, input_stream, partially, &format);
  if (image == NULL) {
    return NULL;
  }

  image_object = create_image_object(env, image, format,
      get_width(image, format), get_height(image, format));
  if (image_object == NULL) {
    recycle(env, image, format);
    return NULL;
  } else {
    return image_object;
  }
}

JNIEXPORT jobject JNICALL
Java_com_hippo_lib_image_Image1_nativeCreate(JNIEnv* env,
                                             jclass clazz, jobject bitmap)
{
#ifdef IMAGE_SUPPORT_PLAIN
  AndroidBitmapInfo info;
  void *pixels = NULL;
  void* image = NULL;
  jobject image_object;

  AndroidBitmap_getInfo(env, bitmap, &info);
  AndroidBitmap_lockPixels(env, bitmap, &pixels);
  if (pixels == NULL) {
    LOGE(MSG("Can't lock bitmap pixels"));
    return NULL;
  }

  image = create(info.width, info.height, pixels);

  AndroidBitmap_unlockPixels(env, bitmap);

  if (image == NULL) {
    return NULL;
  }

  image_object = create_image_object(env, image, IMAGE_FORMAT_PLAIN,
      info.width, info.height);
  if (image_object == NULL) {
    recycle(env, image, IMAGE_FORMAT_PLAIN);
    return NULL;
  } else {
    return image_object;
  }
#else
  return NULL;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_hippo_lib_image_Image1_nativeComplete(JNIEnv* env,
                                               jclass clazz, jlong ptr, jint format)
{
  return (jboolean) complete(env, (void*) (intptr_t) ptr, format);
}

JNIEXPORT jboolean JNICALL
Java_com_hippo_lib_image_Image1_nativeIsCompleted(JNIEnv* env,
                                                  jclass clazz, jlong ptr, jint format)
{
  return (jboolean) is_completed((void*) (intptr_t) ptr, format);
}

JNIEXPORT jint JNICALL
Java_com_hippo_lib_image_Image1_nativeGetByteCount(JNIEnv* env,
                                                   jclass clazz, jlong ptr, jint format)
{
  return (jint) get_byte_count((void*) (intptr_t) ptr, format);
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_Image1_nativeRender(JNIEnv* env,
                                             jclass clazz, jlong ptr, jint format,
                                             jint src_x, jint src_y, jobject dst, jint dst_x, jint dst_y,
                                             jint width, jint height, jboolean fill_blank, jint default_color)
{
  AndroidBitmapInfo info;
  void *pixels = NULL;

  AndroidBitmap_getInfo(env, dst, &info);
  AndroidBitmap_lockPixels(env, dst, &pixels);
  if (pixels == NULL) {
    LOGE(MSG("Can't lock bitmap pixels"));
    return;
  }

  render((void*) (intptr_t) ptr, format, src_x, src_y,
      pixels, info.width, info.height, dst_x, dst_y,
      width, height, fill_blank, default_color);

  AndroidBitmap_unlockPixels(env, dst);

  return;
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_Image1_nativeTexImage(JNIEnv* env,
                                               jclass clazz, jlong ptr, jint format, jboolean init,
                                               jint src_x, jint src_y, jint width, jint height)
{
  // Check tile_buffer NULL
  if (NULL == tile_buffer) {
    return;
  }
  // Check render size
  if (width * height > IMAGE_TILE_MAX_SIZE) {
    return;
  }

  render((void*) (intptr_t) ptr, format, src_x, src_y,
      tile_buffer, width, height, 0, 0,
      width, height, false, 0);

  if (init) {
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height,
        0, GL_RGBA, GL_UNSIGNED_BYTE, tile_buffer);
  } else {
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
        GL_RGBA, GL_UNSIGNED_BYTE, tile_buffer);
  }
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_Image1_nativeAdvance(JNIEnv* env,
                                              jclass clazz, jlong ptr, jint format)
{
  advance((void*) (intptr_t) ptr, format);
}

JNIEXPORT jint JNICALL
Java_com_hippo_lib_image_Image1_nativeGetDelay(JNIEnv* env,
                                               jclass clazz, jlong ptr, jint format)
{
  return (jint) get_delay((void*) (intptr_t) ptr, format);
}

JNIEXPORT jint JNICALL
Java_com_hippo_lib_image_Image1_nativeFrameCount(JNIEnv* env,
                                                 jclass clazz, jlong ptr, jint format)
{
  return (jint) get_frame_count((void*) (intptr_t) ptr, format);
}

JNIEXPORT jboolean JNICALL
Java_com_hippo_lib_image_Image1_nativeIsOpaque(JNIEnv* env,
                                               jclass clazz, jlong ptr, jint format)
{
  return (jboolean) is_opaque((void*) (intptr_t) ptr, format);
}

JNIEXPORT jboolean JNICALL
Java_com_hippo_lib_image_Image1_nativeIsGray(JNIEnv* env,
                                             jclass clazz, jlong ptr, jint format, jint error)
{
  return (jboolean) is_gray((void*) (intptr_t) ptr, format, error);
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_Image1_nativeClahe(JNIEnv* env,
                                            jclass clazz, jlong ptr, jint format, jboolean to_gray)
{
  clahe((void*) (intptr_t) ptr, format, to_gray);
}

JNIEXPORT void JNICALL
Java_com_hippo_lib_image_Image1_nativeRecycle(JNIEnv* env,
                                              jclass clazz, jlong ptr, jint format)
{
  recycle(env, (void*) (intptr_t) ptr, format);
}

JNIEXPORT jintArray JNICALL
Java_com_hippo_lib_image_Image1_nativeGetSupportedImageFormats(
    JNIEnv *env, jclass clazz)
{
  int formats[IMAGE_MAX_SUPPORTED_FORMAT_COUNT];
  int count = get_supported_formats(formats);
  jintArray array = (*env)->NewIntArray(env, count);
  if (array == NULL) {
    return NULL;
  }
  (*env)->SetIntArrayRegion(env, array, 0, count, formats);
  return array;
}

JNIEXPORT jstring JNICALL
Java_com_hippo_lib_image_Image1_nativeGetDecoderDescription(
    JNIEnv *env, jclass clazz, jint format)
{
  const char *description = get_decoder_description(format);
  if (description == NULL) {
    return NULL;
  } else {
    return (*env)->NewStringUTF(env, description);
  }
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
  JNIEnv* env;
  if ((*vm)->GetEnv(vm, (void**) (&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  jvm = vm;

  tile_buffer = malloc(IMAGE_TILE_MAX_SIZE * 4);

  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved)
{
  free(tile_buffer);
  tile_buffer = NULL;
}

JNIEXPORT jobject JNICALL
Java_com_hippo_lib_image_Image_nativeDecode3(JNIEnv *env, jclass clazz, jobject is,
                                             jboolean partially) {
    InputStream* input_stream;
    int format;
    void* image;
    jobject image_object;

    input_stream = create_input_stream(env, is);
    if (input_stream == NULL) {
        return NULL;
    }

    image = decode(env, input_stream, partially, &format);
    if (image == NULL) {
        return NULL;
    }

    image_object = create_image_object(env, image, format,
                                       get_width(image, format), get_height(image, format));
    if (image_object == NULL) {
        recycle(env, image, format);
        return NULL;
    } else {
        return image_object;
    }
}

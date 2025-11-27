# Copyright 2015 Hippo Seven
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

SUPPORT_FORMAT := plain jpeg png gif webp

LOCAL_MODULE := image
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../stream
LOCAL_SRC_FILES := \
    image.c \
    image_utils.c \
    java_wrapper.c
LOCAL_LDLIBS := -llog -ljnigraphics -lGLESv2
LOCAL_STATIC_LIBRARIES := stream

ifeq ($(filter plain, $(SUPPORT_FORMAT)), plain)
  LOCAL_SRC_FILES += image_plain.c
else
  LOCAL_CFLAGS += -DIMAGE_NOT_SUPPORT_PLAIN
endif

ifeq ($(filter jpeg, $(SUPPORT_FORMAT)), jpeg)
  LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libjpeg-turbo
  LOCAL_SRC_FILES += image_jpeg.c
  LOCAL_STATIC_LIBRARIES += jpeg-turbo
else
  LOCAL_CFLAGS += -DIMAGE_NOT_SUPPORT_JPEG
endif

ifeq ($(filter png, $(SUPPORT_FORMAT)), png)
  LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libpng
  LOCAL_SRC_FILES += image_png.c
  LOCAL_STATIC_LIBRARIES += png
else
  LOCAL_CFLAGS += -DIMAGE_NOT_SUPPORT_PNG
endif

ifeq ($(filter gif, $(SUPPORT_FORMAT)), gif)
  LOCAL_C_INCLUDES += $(LOCAL_PATH)/../giflib
  LOCAL_SRC_FILES += image_gif.c
  LOCAL_STATIC_LIBRARIES += gif
else
  LOCAL_CFLAGS += -DIMAGE_NOT_SUPPORT_GIF
endif

ifeq ($(filter webp, $(SUPPORT_FORMAT)), webp)
  LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libwebp/src
  LOCAL_SRC_FILES += image_webp.c
  LOCAL_STATIC_LIBRARIES += webp webpdemux
else
  LOCAL_CFLAGS += -DIMAGE_NOT_SUPPORT_WEBP
endif

include $(BUILD_SHARED_LIBRARY)

# Ignore this file during non-NDK builds.
ifdef NDK_ROOT
LOCAL_PATH := $(call my-dir)

################################################################################
# libexample_util

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    example_util.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../src

LOCAL_MODULE := example_util

include $(BUILD_STATIC_LIBRARY)

################################################################################
# cwebp

include $(CLEAR_VARS)

# Note: to enable jpeg/png encoding the sources from AOSP can be used with
# minor modification to their Android.mk files.
LOCAL_SRC_FILES := \
    cwebp.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_STATIC_LIBRARIES := example_util imageio_util imagedec webpdemux webp

LOCAL_MODULE := cwebp

include $(BUILD_EXECUTABLE)

################################################################################
# dwebp

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    dwebp.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_STATIC_LIBRARIES := example_util imagedec imageenc webpdemux webp
LOCAL_MODULE := dwebp

include $(BUILD_EXECUTABLE)

################################################################################
# webpmux

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    webpmux.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_STATIC_LIBRARIES := example_util imageio_util webpmux webp

LOCAL_MODULE := webpmux_example

include $(BUILD_EXECUTABLE)

################################################################################
# img2webp

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    img2webp.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_STATIC_LIBRARIES := example_util imageio_util imagedec webpmux webpdemux \
                          webp

LOCAL_MODULE := img2webp_example

include $(BUILD_EXECUTABLE)

################################################################################
# webpinfo

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    webpinfo.c \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_STATIC_LIBRARIES := example_util imageio_util webp

LOCAL_MODULE := webpinfo_example

include $(BUILD_EXECUTABLE)
endif  # NDK_ROOT

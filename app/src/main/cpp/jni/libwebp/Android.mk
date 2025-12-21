# Ignore this file during non-NDK builds.
ifdef NDK_ROOT
LOCAL_PATH := $(call my-dir)

WEBP_CFLAGS := -Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD
WEBP_CFLAGS += -fvisibility=hidden

ifeq ($(APP_OPTIM),release)
  WEBP_CFLAGS += -finline-functions -ffast-math \
                 -ffunction-sections -fdata-sections
  ifeq ($(findstring clang,$(NDK_TOOLCHAIN_VERSION)),)
    WEBP_CFLAGS += -frename-registers -s
  endif
endif

# mips32 fails to build with clang from r14b
# https://bugs.chromium.org/p/webp/issues/detail?id=343
ifeq ($(findstring clang,$(NDK_TOOLCHAIN_VERSION)),clang)
  ifeq ($(TARGET_ARCH),mips)
    clang_version := $(shell $(TARGET_CC) --version)
    ifneq ($(findstring clang version 3,$(clang_version)),)
      WEBP_CFLAGS += -no-integrated-as
    endif
  endif
endif

ifneq ($(findstring armeabi-v7a, $(TARGET_ARCH_ABI)),)
  # Setting LOCAL_ARM_NEON will enable -mfpu=neon which may cause illegal
  # instructions to be generated for armv7a code. Instead target the neon code
  # specifically.
  NEON := c.neon
  USE_CPUFEATURES := yes
  WEBP_CFLAGS += -DHAVE_CPU_FEATURES_H
else
  NEON := c
endif

sharpyuv_srcs := \
    sharpyuv/sharpyuv.c \
    sharpyuv/sharpyuv_cpu.c \
    sharpyuv/sharpyuv_csp.c \
    sharpyuv/sharpyuv_dsp.c \
    sharpyuv/sharpyuv_gamma.c \
    sharpyuv/sharpyuv_neon.$(NEON) \
    sharpyuv/sharpyuv_sse2.c \

dec_srcs := \
    src/dec/alpha_dec.c \
    src/dec/buffer_dec.c \
    src/dec/frame_dec.c \
    src/dec/idec_dec.c \
    src/dec/io_dec.c \
    src/dec/quant_dec.c \
    src/dec/tree_dec.c \
    src/dec/vp8_dec.c \
    src/dec/vp8l_dec.c \
    src/dec/webp_dec.c \

demux_srcs := \
    src/demux/anim_decode.c \
    src/demux/demux.c \

dsp_dec_srcs := \
    src/dsp/alpha_processing.c \
    src/dsp/alpha_processing_mips_dsp_r2.c \
    src/dsp/alpha_processing_neon.$(NEON) \
    src/dsp/alpha_processing_sse2.c \
    src/dsp/alpha_processing_sse41.c \
    src/dsp/cpu.c \
    src/dsp/dec.c \
    src/dsp/dec_clip_tables.c \
    src/dsp/dec_mips32.c \
    src/dsp/dec_mips_dsp_r2.c \
    src/dsp/dec_msa.c \
    src/dsp/dec_neon.$(NEON) \
    src/dsp/dec_sse2.c \
    src/dsp/dec_sse41.c \
    src/dsp/filters.c \
    src/dsp/filters_mips_dsp_r2.c \
    src/dsp/filters_msa.c \
    src/dsp/filters_neon.$(NEON) \
    src/dsp/filters_sse2.c \
    src/dsp/lossless.c \
    src/dsp/lossless_mips_dsp_r2.c \
    src/dsp/lossless_msa.c \
    src/dsp/lossless_neon.$(NEON) \
    src/dsp/lossless_sse2.c \
    src/dsp/lossless_sse41.c \
    src/dsp/rescaler.c \
    src/dsp/rescaler_mips32.c \
    src/dsp/rescaler_mips_dsp_r2.c \
    src/dsp/rescaler_msa.c \
    src/dsp/rescaler_neon.$(NEON) \
    src/dsp/rescaler_sse2.c \
    src/dsp/upsampling.c \
    src/dsp/upsampling_mips_dsp_r2.c \
    src/dsp/upsampling_msa.c \
    src/dsp/upsampling_neon.$(NEON) \
    src/dsp/upsampling_sse2.c \
    src/dsp/upsampling_sse41.c \
    src/dsp/yuv.c \
    src/dsp/yuv_mips32.c \
    src/dsp/yuv_mips_dsp_r2.c \
    src/dsp/yuv_neon.$(NEON) \
    src/dsp/yuv_sse2.c \
    src/dsp/yuv_sse41.c \

dsp_enc_srcs := \
    src/dsp/cost.c \
    src/dsp/cost_mips32.c \
    src/dsp/cost_mips_dsp_r2.c \
    src/dsp/cost_neon.$(NEON) \
    src/dsp/cost_sse2.c \
    src/dsp/enc.c \
    src/dsp/enc_mips32.c \
    src/dsp/enc_mips_dsp_r2.c \
    src/dsp/enc_msa.c \
    src/dsp/enc_neon.$(NEON) \
    src/dsp/enc_sse2.c \
    src/dsp/enc_sse41.c \
    src/dsp/lossless_enc.c \
    src/dsp/lossless_enc_mips32.c \
    src/dsp/lossless_enc_mips_dsp_r2.c \
    src/dsp/lossless_enc_msa.c \
    src/dsp/lossless_enc_neon.$(NEON) \
    src/dsp/lossless_enc_sse2.c \
    src/dsp/lossless_enc_sse41.c \
    src/dsp/ssim.c \
    src/dsp/ssim_sse2.c \

enc_srcs := \
    src/enc/alpha_enc.c \
    src/enc/analysis_enc.c \
    src/enc/backward_references_cost_enc.c \
    src/enc/backward_references_enc.c \
    src/enc/config_enc.c \
    src/enc/cost_enc.c \
    src/enc/filter_enc.c \
    src/enc/frame_enc.c \
    src/enc/histogram_enc.c \
    src/enc/iterator_enc.c \
    src/enc/near_lossless_enc.c \
    src/enc/picture_enc.c \
    src/enc/picture_csp_enc.c \
    src/enc/picture_psnr_enc.c \
    src/enc/picture_rescale_enc.c \
    src/enc/picture_tools_enc.c \
    src/enc/predictor_enc.c \
    src/enc/quant_enc.c \
    src/enc/syntax_enc.c \
    src/enc/token_enc.c \
    src/enc/tree_enc.c \
    src/enc/vp8l_enc.c \
    src/enc/webp_enc.c \

mux_srcs := \
    src/mux/anim_encode.c \
    src/mux/muxedit.c \
    src/mux/muxinternal.c \
    src/mux/muxread.c \

utils_dec_srcs := \
    src/utils/bit_reader_utils.c \
    src/utils/color_cache_utils.c \
    src/utils/filters_utils.c \
    src/utils/huffman_utils.c \
    src/utils/palette.c \
    src/utils/quant_levels_dec_utils.c \
    src/utils/random_utils.c \
    src/utils/rescaler_utils.c \
    src/utils/thread_utils.c \
    src/utils/utils.c \

utils_enc_srcs := \
    src/utils/bit_writer_utils.c \
    src/utils/huffman_encode_utils.c \
    src/utils/quant_levels_utils.c \

################################################################################
# libwebpdecoder

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(dec_srcs) \
    $(dsp_dec_srcs) \
    $(utils_dec_srcs) \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_EXPORT_C_INCLUDES += $(LOCAL_PATH)/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

ifeq ($(USE_CPUFEATURES),yes)
  LOCAL_STATIC_LIBRARIES := cpufeatures
endif

LOCAL_MODULE := webpdecoder_static

include $(BUILD_STATIC_LIBRARY)

ifeq ($(ENABLE_SHARED),1)
include $(CLEAR_VARS)

LOCAL_WHOLE_STATIC_LIBRARIES := webpdecoder_static

LOCAL_MODULE := webpdecoder

include $(BUILD_SHARED_LIBRARY)
endif  # ENABLE_SHARED=1

################################################################################
# libwebp

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(sharpyuv_srcs) \
    $(dsp_enc_srcs) \
    $(enc_srcs) \
    $(utils_enc_srcs) \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_EXPORT_C_INCLUDES += $(LOCAL_PATH)/src $(LOCAL_PATH)

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

LOCAL_WHOLE_STATIC_LIBRARIES := webpdecoder_static

LOCAL_MODULE := webp

ifeq ($(ENABLE_SHARED),1)
  include $(BUILD_SHARED_LIBRARY)
else
  include $(BUILD_STATIC_LIBRARY)
endif

################################################################################
# libwebpdemux

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(demux_srcs)

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_EXPORT_C_INCLUDES += $(LOCAL_PATH)/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

LOCAL_MODULE := webpdemux

ifeq ($(ENABLE_SHARED),1)
  LOCAL_SHARED_LIBRARIES := webp
  include $(BUILD_SHARED_LIBRARY)
else
  LOCAL_STATIC_LIBRARIES := webp
  include $(BUILD_STATIC_LIBRARY)
endif

################################################################################
# libwebpmux

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(mux_srcs)

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_EXPORT_C_INCLUDES += $(LOCAL_PATH)/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

LOCAL_MODULE := webpmux

ifeq ($(ENABLE_SHARED),1)
  LOCAL_SHARED_LIBRARIES := webp
  include $(BUILD_SHARED_LIBRARY)
else
  LOCAL_STATIC_LIBRARIES := webp
  include $(BUILD_STATIC_LIBRARY)
endif

################################################################################

WEBP_SRC_PATH := $(LOCAL_PATH)
include $(WEBP_SRC_PATH)/imageio/Android.mk
include $(WEBP_SRC_PATH)/examples/Android.mk

ifeq ($(USE_CPUFEATURES),yes)
  $(call import-module,android/cpufeatures)
endif
endif  # NDK_ROOT

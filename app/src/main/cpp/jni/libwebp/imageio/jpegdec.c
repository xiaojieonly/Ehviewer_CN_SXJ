// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// JPEG decode.

#include "./jpegdec.h"

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include <stdio.h>

#ifdef WEBP_HAVE_JPEG
#include <jpeglib.h>
#include <jerror.h>
#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#include "webp/encode.h"
#include "./imageio_util.h"
#include "./metadata.h"

// -----------------------------------------------------------------------------
// Metadata processing

#ifndef JPEG_APP1
# define JPEG_APP1 (JPEG_APP0 + 1)
#endif
#ifndef JPEG_APP2
# define JPEG_APP2 (JPEG_APP0 + 2)
#endif

typedef struct {
  const uint8_t* data;
  size_t data_length;
  int seq;  // this segment's sequence number [1, 255] for use in reassembly.
} ICCPSegment;

static void SaveMetadataMarkers(j_decompress_ptr dinfo) {
  const unsigned int max_marker_length = 0xffff;
  jpeg_save_markers(dinfo, JPEG_APP1, max_marker_length);  // Exif/XMP
  jpeg_save_markers(dinfo, JPEG_APP2, max_marker_length);  // ICC profile
}

static int CompareICCPSegments(const void* a, const void* b) {
  const ICCPSegment* s1 = (const ICCPSegment*)a;
  const ICCPSegment* s2 = (const ICCPSegment*)b;
  return s1->seq - s2->seq;
}

// Extract ICC profile segments from the marker list in 'dinfo', reassembling
// and storing them in 'iccp'.
// Returns true on success and false for memory errors and corrupt profiles.
static int StoreICCP(j_decompress_ptr dinfo, MetadataPayload* const iccp) {
  // ICC.1:2010-12 (4.3.0.0) Annex B.4 Embedding ICC Profiles in JPEG files
  static const char kICCPSignature[] = "ICC_PROFILE";
  static const size_t kICCPSignatureLength = 12;  // signature includes '\0'
  static const size_t kICCPSkipLength = 14;  // signature + seq & count
  int expected_count = 0;
  int actual_count = 0;
  int seq_max = 0;
  size_t total_size = 0;
  ICCPSegment iccp_segments[255];
  jpeg_saved_marker_ptr marker;

  memset(iccp_segments, 0, sizeof(iccp_segments));
  for (marker = dinfo->marker_list; marker != NULL; marker = marker->next) {
    if (marker->marker == JPEG_APP2 &&
        marker->data_length > kICCPSkipLength &&
        !memcmp(marker->data, kICCPSignature, kICCPSignatureLength)) {
      // ICC_PROFILE\0<seq><count>; 'seq' starts at 1.
      const int seq = marker->data[kICCPSignatureLength];
      const int count = marker->data[kICCPSignatureLength + 1];
      const size_t segment_size = marker->data_length - kICCPSkipLength;
      ICCPSegment* segment;

      if (segment_size == 0 || count == 0 || seq == 0) {
        fprintf(stderr, "[ICCP] size (%d) / count (%d) / sequence number (%d)"
                        " cannot be 0!\n",
                (int)segment_size, seq, count);
        return 0;
      }

      if (expected_count == 0) {
        expected_count = count;
      } else if (expected_count != count) {
        fprintf(stderr, "[ICCP] Inconsistent segment count (%d / %d)!\n",
                expected_count, count);
        return 0;
      }

      segment = iccp_segments + seq - 1;
      if (segment->data_length != 0) {
        fprintf(stderr, "[ICCP] Duplicate segment number (%d)!\n" , seq);
        return 0;
      }

      segment->data = marker->data + kICCPSkipLength;
      segment->data_length = segment_size;
      segment->seq = seq;
      total_size += segment_size;
      if (seq > seq_max) seq_max = seq;
      ++actual_count;
    }
  }

  if (actual_count == 0) return 1;
  if (seq_max != actual_count) {
    fprintf(stderr, "[ICCP] Discontinuous segments, expected: %d actual: %d!\n",
            actual_count, seq_max);
    return 0;
  }
  if (expected_count != actual_count) {
    fprintf(stderr, "[ICCP] Segment count: %d does not match expected: %d!\n",
            actual_count, expected_count);
    return 0;
  }

  // The segments may appear out of order in the file, sort them based on
  // sequence number before assembling the payload.
  qsort(iccp_segments, actual_count, sizeof(*iccp_segments),
        CompareICCPSegments);

  iccp->bytes = (uint8_t*)malloc(total_size);
  if (iccp->bytes == NULL) return 0;
  iccp->size = total_size;

  {
    int i;
    size_t offset = 0;
    for (i = 0; i < seq_max; ++i) {
      memcpy(iccp->bytes + offset,
             iccp_segments[i].data, iccp_segments[i].data_length);
      offset += iccp_segments[i].data_length;
    }
  }
  return 1;
}

// Returns true on success and false for memory errors and corrupt profiles.
// The caller must use MetadataFree() on 'metadata' in all cases.
static int ExtractMetadataFromJPEG(j_decompress_ptr dinfo,
                                   Metadata* const metadata) {
  static const struct {
    int marker;
    const char* signature;
    size_t signature_length;
    size_t storage_offset;
  } kJPEGMetadataMap[] = {
    // Exif 2.2 Section 4.7.2 Interoperability Structure of APP1 ...
    { JPEG_APP1, "Exif\0",                        6, METADATA_OFFSET(exif) },
    // XMP Specification Part 3 Section 3 Embedding XMP Metadata ... #JPEG
    // TODO(jzern) Add support for 'ExtendedXMP'
    { JPEG_APP1, "http://ns.adobe.com/xap/1.0/", 29, METADATA_OFFSET(xmp) },
    { 0, NULL, 0, 0 },
  };
  jpeg_saved_marker_ptr marker;
  // Treat ICC profiles separately as they may be segmented and out of order.
  if (!StoreICCP(dinfo, &metadata->iccp)) return 0;

  for (marker = dinfo->marker_list; marker != NULL; marker = marker->next) {
    int i;
    for (i = 0; kJPEGMetadataMap[i].marker != 0; ++i) {
      if (marker->marker == kJPEGMetadataMap[i].marker &&
          marker->data_length > kJPEGMetadataMap[i].signature_length &&
          !memcmp(marker->data, kJPEGMetadataMap[i].signature,
                  kJPEGMetadataMap[i].signature_length)) {
        MetadataPayload* const payload =
            (MetadataPayload*)((uint8_t*)metadata +
                               kJPEGMetadataMap[i].storage_offset);

        if (payload->bytes == NULL) {
          const char* marker_data = (const char*)marker->data +
                                    kJPEGMetadataMap[i].signature_length;
          const size_t marker_data_length =
              marker->data_length - kJPEGMetadataMap[i].signature_length;
          if (!MetadataCopy(marker_data, marker_data_length, payload)) return 0;
        } else {
          fprintf(stderr, "Ignoring additional '%s' marker\n",
                  kJPEGMetadataMap[i].signature);
        }
      }
    }
  }
  return 1;
}

#undef JPEG_APP1
#undef JPEG_APP2

// -----------------------------------------------------------------------------
// JPEG decoding

struct my_error_mgr {
  struct jpeg_error_mgr pub;
  jmp_buf setjmp_buffer;
};

static void my_error_exit(j_common_ptr dinfo) {
  struct my_error_mgr* myerr = (struct my_error_mgr*)dinfo->err;
  // The following code is disabled in fuzzing mode because:
  // - the logs can be flooded due to invalid JPEG files
  // - msg_code is wrongfully seen as uninitialized by msan when the libjpeg
  //   dependency is not built with sanitizers enabled
#ifndef FUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
  const int msg_code = myerr->pub.msg_code;
  fprintf(stderr, "libjpeg error: ");
  dinfo->err->output_message(dinfo);
  if (msg_code == JERR_INPUT_EOF || msg_code == JERR_FILE_READ) {
    fprintf(stderr, "`jpegtran -copy all` MAY be able to process this file.\n");
  }
#endif
  longjmp(myerr->setjmp_buffer, 1);
}

typedef struct {
  struct jpeg_source_mgr pub;
  const uint8_t* data;
  size_t data_size;
} JPEGReadContext;

static void ContextInit(j_decompress_ptr cinfo) {
  JPEGReadContext* const ctx = (JPEGReadContext*)cinfo->src;
  ctx->pub.next_input_byte = ctx->data;
  ctx->pub.bytes_in_buffer = ctx->data_size;
}

static boolean ContextFill(j_decompress_ptr cinfo) {
  // we shouldn't get here.
  ERREXIT(cinfo, JERR_FILE_READ);
  return FALSE;
}

static void ContextSkip(j_decompress_ptr cinfo, long jump_size) {
  JPEGReadContext* const ctx = (JPEGReadContext*)cinfo->src;
  size_t jump = (size_t)jump_size;
  if (jump > ctx->pub.bytes_in_buffer) {  // Don't overflow the buffer.
    jump = ctx->pub.bytes_in_buffer;
  }
  ctx->pub.bytes_in_buffer -= jump;
  ctx->pub.next_input_byte += jump;
}

static void ContextTerm(j_decompress_ptr cinfo) {
  (void)cinfo;
}

static void ContextSetup(volatile struct jpeg_decompress_struct* const cinfo,
                         JPEGReadContext* const ctx) {
  cinfo->src = (struct jpeg_source_mgr*)ctx;
  ctx->pub.init_source = ContextInit;
  ctx->pub.fill_input_buffer = ContextFill;
  ctx->pub.skip_input_data = ContextSkip;
  ctx->pub.resync_to_restart = jpeg_resync_to_restart;
  ctx->pub.term_source = ContextTerm;
  ctx->pub.bytes_in_buffer = 0;
  ctx->pub.next_input_byte = NULL;
}

int ReadJPEG(const uint8_t* const data, size_t data_size,
             WebPPicture* const pic, int keep_alpha,
             Metadata* const metadata) {
  volatile int ok = 0;
  int width, height;
  int64_t stride;
  volatile struct jpeg_decompress_struct dinfo;
  struct my_error_mgr jerr;
  uint8_t* volatile rgb = NULL;
  JSAMPROW buffer[1];
  JPEGReadContext ctx;

  if (data == NULL || data_size == 0 || pic == NULL) return 0;

  (void)keep_alpha;
  memset(&ctx, 0, sizeof(ctx));
  ctx.data = data;
  ctx.data_size = data_size;

  memset((j_decompress_ptr)&dinfo, 0, sizeof(dinfo));   // for setjmp safety
  dinfo.err = jpeg_std_error(&jerr.pub);
  jerr.pub.error_exit = my_error_exit;

  if (setjmp(jerr.setjmp_buffer)) {
 Error:
    MetadataFree(metadata);
    jpeg_destroy_decompress((j_decompress_ptr)&dinfo);
    goto End;
  }

  jpeg_create_decompress((j_decompress_ptr)&dinfo);
  ContextSetup(&dinfo, &ctx);
  if (metadata != NULL) SaveMetadataMarkers((j_decompress_ptr)&dinfo);
  jpeg_read_header((j_decompress_ptr)&dinfo, TRUE);

  dinfo.out_color_space = JCS_RGB;
  dinfo.do_fancy_upsampling = TRUE;

  jpeg_start_decompress((j_decompress_ptr)&dinfo);

  if (dinfo.output_components != 3) {
    goto Error;
  }

  width = dinfo.output_width;
  height = dinfo.output_height;
  stride = (int64_t)dinfo.output_width * dinfo.output_components * sizeof(*rgb);

  if (stride != (int)stride ||
      !ImgIoUtilCheckSizeArgumentsOverflow(stride, height)) {
    goto Error;
  }

  rgb = (uint8_t*)malloc((size_t)stride * height);
  if (rgb == NULL) {
    goto Error;
  }
  buffer[0] = (JSAMPLE*)rgb;

  while (dinfo.output_scanline < dinfo.output_height) {
    if (jpeg_read_scanlines((j_decompress_ptr)&dinfo, buffer, 1) != 1) {
      goto Error;
    }
    buffer[0] += stride;
  }

  if (metadata != NULL) {
    ok = ExtractMetadataFromJPEG((j_decompress_ptr)&dinfo, metadata);
    if (!ok) {
      fprintf(stderr, "Error extracting JPEG metadata!\n");
      goto Error;
    }
  }

  jpeg_finish_decompress((j_decompress_ptr)&dinfo);
  jpeg_destroy_decompress((j_decompress_ptr)&dinfo);

  // WebP conversion.
  pic->width = width;
  pic->height = height;
  ok = WebPPictureImportRGB(pic, rgb, (int)stride);
  if (!ok) {
    pic->width = 0;   // WebPPictureImportRGB() barely touches 'pic' on failure.
    pic->height = 0;  // Just reset dimensions but keep any 'custom_ptr' etc.
    MetadataFree(metadata);  // In case the caller forgets to free it on error.
  }

 End:
  free(rgb);
  return ok;
}
#else  // !WEBP_HAVE_JPEG
int ReadJPEG(const uint8_t* const data, size_t data_size,
             struct WebPPicture* const pic, int keep_alpha,
             struct Metadata* const metadata) {
  (void)data;
  (void)data_size;
  (void)pic;
  (void)keep_alpha;
  (void)metadata;
  fprintf(stderr, "JPEG support not compiled. Please install the libjpeg "
          "development package before building.\n");
  return 0;
}
#endif  // WEBP_HAVE_JPEG

// -----------------------------------------------------------------------------

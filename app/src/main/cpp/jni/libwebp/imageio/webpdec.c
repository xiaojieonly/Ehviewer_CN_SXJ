// Copyright 2014 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// WebP decode.

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include "./webpdec.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include "../src/webp/decode.h"
#include "../src/webp/demux.h"
#include "../src/webp/encode.h"
#include "../examples/unicode.h"
#include "./imageio_util.h"
#include "./metadata.h"

//------------------------------------------------------------------------------
// WebP decoding

static const char* const kStatusMessages[VP8_STATUS_NOT_ENOUGH_DATA + 1] = {
  "OK", "OUT_OF_MEMORY", "INVALID_PARAM", "BITSTREAM_ERROR",
  "UNSUPPORTED_FEATURE", "SUSPENDED", "USER_ABORT", "NOT_ENOUGH_DATA"
};

static void PrintAnimationWarning(const WebPDecoderConfig* const config) {
  if (config->input.has_animation) {
    fprintf(stderr,
            "Error! Decoding of an animated WebP file is not supported.\n"
            "       Use webpmux to extract the individual frames or\n"
            "       vwebp to view this image.\n");
  }
}

void PrintWebPError(const char* const in_file, int status) {
  WFPRINTF(stderr, "Decoding of %s failed.\n", (const W_CHAR*)in_file);
  fprintf(stderr, "Status: %d", status);
  if (status >= VP8_STATUS_OK && status <= VP8_STATUS_NOT_ENOUGH_DATA) {
    fprintf(stderr, "(%s)", kStatusMessages[status]);
  }
  fprintf(stderr, "\n");
}

int LoadWebP(const char* const in_file,
             const uint8_t** data, size_t* data_size,
             WebPBitstreamFeatures* bitstream) {
  VP8StatusCode status;
  WebPBitstreamFeatures local_features;
  if (!ImgIoUtilReadFile(in_file, data, data_size)) return 0;

  if (bitstream == NULL) {
    bitstream = &local_features;
  }

  status = WebPGetFeatures(*data, *data_size, bitstream);
  if (status != VP8_STATUS_OK) {
    WebPFree((void*)*data);
    *data = NULL;
    *data_size = 0;
    PrintWebPError(in_file, status);
    return 0;
  }
  return 1;
}

//------------------------------------------------------------------------------

VP8StatusCode DecodeWebP(const uint8_t* const data, size_t data_size,
                         WebPDecoderConfig* const config) {
  if (config == NULL) return VP8_STATUS_INVALID_PARAM;
  PrintAnimationWarning(config);
  return WebPDecode(data, data_size, config);
}

VP8StatusCode DecodeWebPIncremental(
    const uint8_t* const data, size_t data_size,
    WebPDecoderConfig* const config) {
  VP8StatusCode status = VP8_STATUS_OK;
  if (config == NULL) return VP8_STATUS_INVALID_PARAM;

  PrintAnimationWarning(config);

  // Decoding call.
  {
    WebPIDecoder* const idec = WebPIDecode(data, data_size, config);
    if (idec == NULL) {
      fprintf(stderr, "Failed during WebPIDecode().\n");
      return VP8_STATUS_OUT_OF_MEMORY;
    } else {
      status = WebPIUpdate(idec, data, data_size);
      WebPIDelete(idec);
    }
  }
  return status;
}

// -----------------------------------------------------------------------------
// Metadata

static int ExtractMetadata(const uint8_t* const data, size_t data_size,
                           Metadata* const metadata) {
  WebPData webp_data = { data, data_size };
  WebPDemuxer* const demux = WebPDemux(&webp_data);
  WebPChunkIterator chunk_iter;
  uint32_t flags;

  if (demux == NULL) return 0;
  assert(metadata != NULL);

  flags = WebPDemuxGetI(demux, WEBP_FF_FORMAT_FLAGS);

  if ((flags & ICCP_FLAG) && WebPDemuxGetChunk(demux, "ICCP", 1, &chunk_iter)) {
    MetadataCopy((const char*)chunk_iter.chunk.bytes, chunk_iter.chunk.size,
                 &metadata->iccp);
    WebPDemuxReleaseChunkIterator(&chunk_iter);
  }
  if ((flags & EXIF_FLAG) && WebPDemuxGetChunk(demux, "EXIF", 1, &chunk_iter)) {
    MetadataCopy((const char*)chunk_iter.chunk.bytes, chunk_iter.chunk.size,
                 &metadata->exif);
    WebPDemuxReleaseChunkIterator(&chunk_iter);
  }
  if ((flags & XMP_FLAG) && WebPDemuxGetChunk(demux, "XMP ", 1, &chunk_iter)) {
    MetadataCopy((const char*)chunk_iter.chunk.bytes, chunk_iter.chunk.size,
                 &metadata->xmp);
    WebPDemuxReleaseChunkIterator(&chunk_iter);
  }
  WebPDemuxDelete(demux);
  return 1;
}

// -----------------------------------------------------------------------------

int ReadWebP(const uint8_t* const data, size_t data_size,
             WebPPicture* const pic,
             int keep_alpha, Metadata* const metadata) {
  int ok = 0;
  VP8StatusCode status = VP8_STATUS_OK;
  WebPDecoderConfig config;
  WebPDecBuffer* const output_buffer = &config.output;
  WebPBitstreamFeatures* const bitstream = &config.input;

  if (data == NULL || data_size == 0 || pic == NULL) return 0;

  if (!WebPInitDecoderConfig(&config)) {
    fprintf(stderr, "Library version mismatch!\n");
    return 0;
  }

  status = WebPGetFeatures(data, data_size, bitstream);
  if (status != VP8_STATUS_OK) {
    PrintWebPError("input data", status);
    return 0;
  }

  do {
    const int has_alpha = keep_alpha && bitstream->has_alpha;
    uint64_t stride;
    pic->width = bitstream->width;
    pic->height = bitstream->height;
    if (pic->use_argb) {
      stride = (uint64_t)bitstream->width * 4;
    } else {
      stride = (uint64_t)bitstream->width * (has_alpha ? 5 : 3) / 2;
      pic->colorspace = has_alpha ? WEBP_YUV420A : WEBP_YUV420;
    }

    if (!ImgIoUtilCheckSizeArgumentsOverflow(stride, bitstream->height)) {
      status = VP8_STATUS_OUT_OF_MEMORY;
      break;
    }

    ok = WebPPictureAlloc(pic);
    if (!ok) {
      status = VP8_STATUS_OUT_OF_MEMORY;
      break;
    }
    if (pic->use_argb) {
#ifdef WORDS_BIGENDIAN
      output_buffer->colorspace = MODE_ARGB;
#else
      output_buffer->colorspace = MODE_BGRA;
#endif
      output_buffer->u.RGBA.rgba = (uint8_t*)pic->argb;
      output_buffer->u.RGBA.stride = pic->argb_stride * sizeof(uint32_t);
      output_buffer->u.RGBA.size = output_buffer->u.RGBA.stride * pic->height;
    } else {
      output_buffer->colorspace = has_alpha ? MODE_YUVA : MODE_YUV;
      output_buffer->u.YUVA.y = pic->y;
      output_buffer->u.YUVA.u = pic->u;
      output_buffer->u.YUVA.v = pic->v;
      output_buffer->u.YUVA.a = has_alpha ? pic->a : NULL;
      output_buffer->u.YUVA.y_stride = pic->y_stride;
      output_buffer->u.YUVA.u_stride = pic->uv_stride;
      output_buffer->u.YUVA.v_stride = pic->uv_stride;
      output_buffer->u.YUVA.a_stride = has_alpha ? pic->a_stride : 0;
      output_buffer->u.YUVA.y_size = pic->height * pic->y_stride;
      output_buffer->u.YUVA.u_size = (pic->height + 1) / 2 * pic->uv_stride;
      output_buffer->u.YUVA.v_size = (pic->height + 1) / 2 * pic->uv_stride;
      output_buffer->u.YUVA.a_size = pic->height * pic->a_stride;
    }
    output_buffer->is_external_memory = 1;

    status = DecodeWebP(data, data_size, &config);
    ok = (status == VP8_STATUS_OK);
    if (ok && !keep_alpha && pic->use_argb) {
      // Need to wipe out the alpha value, as requested.
      int x, y;
      uint32_t* argb = pic->argb;
      for (y = 0; y < pic->height; ++y) {
        for (x = 0; x < pic->width; ++x) argb[x] |= 0xff000000u;
        argb += pic->argb_stride;
      }
    }
  } while (0);   // <- so we can 'break' out of the loop

  if (status != VP8_STATUS_OK) {
    PrintWebPError("input data", status);
    ok = 0;
  }

  WebPFreeDecBuffer(output_buffer);

  if (ok && metadata != NULL) {
    ok = ExtractMetadata(data, data_size, metadata);
    if (!ok) {
      PrintWebPError("metadata", VP8_STATUS_BITSTREAM_ERROR);
    }
  }
  if (!ok) WebPPictureFree(pic);
  return ok;
}

// -----------------------------------------------------------------------------

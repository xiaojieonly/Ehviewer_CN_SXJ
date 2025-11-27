// Copyright 2011 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  simple command line calling the WebPEncode function.
//  Encodes a raw .YUV into WebP bitstream
//
// Author: Skal (pascal.massimino@gmail.com)

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include "../examples/example_util.h"
#include "../imageio/image_dec.h"
#include "../imageio/imageio_util.h"
#include "../imageio/webpdec.h"
#include "./stopwatch.h"
#include "./unicode.h"
#include "../sharpyuv/sharpyuv.h
#include "webp/encode.h"

#ifndef WEBP_DLL
#ifdef __cplusplus
extern "C" {
#endif

extern void* VP8GetCPUInfo;   // opaque forward declaration.

#ifdef __cplusplus
}    // extern "C"
#endif
#endif  // WEBP_DLL

//------------------------------------------------------------------------------

static int verbose = 0;

static int ReadYUV(const uint8_t* const data, size_t data_size,
                   WebPPicture* const pic) {
  const int use_argb = pic->use_argb;
  const int uv_width = (pic->width + 1) / 2;
  const int uv_height = (pic->height + 1) / 2;
  const int y_plane_size = pic->width * pic->height;
  const int uv_plane_size = uv_width * uv_height;
  const size_t expected_data_size = y_plane_size + 2 * uv_plane_size;

  if (data_size != expected_data_size) {
    fprintf(stderr,
            "input data doesn't have the expected size (%d instead of %d)\n",
            (int)data_size, (int)expected_data_size);
    return 0;
  }

  pic->use_argb = 0;
  if (!WebPPictureAlloc(pic)) return 0;
  ImgIoUtilCopyPlane(data, pic->width, pic->y, pic->y_stride,
                     pic->width, pic->height);
  ImgIoUtilCopyPlane(data + y_plane_size, uv_width,
                     pic->u, pic->uv_stride, uv_width, uv_height);
  ImgIoUtilCopyPlane(data + y_plane_size + uv_plane_size, uv_width,
                     pic->v, pic->uv_stride, uv_width, uv_height);
  return use_argb ? WebPPictureYUVAToARGB(pic) : 1;
}

#ifdef HAVE_WINCODEC_H

static int ReadPicture(const char* const filename, WebPPicture* const pic,
                       int keep_alpha, Metadata* const metadata) {
  int ok = 0;
  const uint8_t* data = NULL;
  size_t data_size = 0;
  if (pic->width != 0 && pic->height != 0) {
    ok = ImgIoUtilReadFile(filename, &data, &data_size);
    ok = ok && ReadYUV(data, data_size, pic);
  } else {
    // If no size specified, try to decode it using WIC.
    ok = ReadPictureWithWIC(filename, pic, keep_alpha, metadata);
    if (!ok) {
      ok = ImgIoUtilReadFile(filename, &data, &data_size);
      ok = ok && ReadWebP(data, data_size, pic, keep_alpha, metadata);
    }
  }
  if (!ok) {
    WFPRINTF(stderr, "Error! Could not process file %s\n",
             (const W_CHAR*)filename);
  }
  WebPFree((void*)data);
  return ok;
}

#else  // !HAVE_WINCODEC_H

static int ReadPicture(const char* const filename, WebPPicture* const pic,
                       int keep_alpha, Metadata* const metadata) {
  const uint8_t* data = NULL;
  size_t data_size = 0;
  int ok = 0;

  ok = ImgIoUtilReadFile(filename, &data, &data_size);
  if (!ok) goto End;

  if (pic->width == 0 || pic->height == 0) {
    WebPImageReader reader = WebPGuessImageReader(data, data_size);
    ok = reader(data, data_size, pic, keep_alpha, metadata);
  } else {
    // If image size is specified, infer it as YUV format.
    ok = ReadYUV(data, data_size, pic);
  }
 End:
  if (!ok) {
    WFPRINTF(stderr, "Error! Could not process file %s\n",
             (const W_CHAR*)filename);
  }
  WebPFree((void*)data);
  return ok;
}

#endif  // !HAVE_WINCODEC_H

static void AllocExtraInfo(WebPPicture* const pic) {
  const int mb_w = (pic->width + 15) / 16;
  const int mb_h = (pic->height + 15) / 16;
  pic->extra_info =
      (uint8_t*)WebPMalloc(mb_w * mb_h * sizeof(*pic->extra_info));
}

static void PrintByteCount(const int bytes[4], int total_size,
                           int* const totals) {
  int s;
  int total = 0;
  for (s = 0; s < 4; ++s) {
    fprintf(stderr, "| %7d ", bytes[s]);
    total += bytes[s];
    if (totals) totals[s] += bytes[s];
  }
  fprintf(stderr, "| %7d  (%.1f%%)\n", total, 100.f * total / total_size);
}

static void PrintPercents(const int counts[4]) {
  int s;
  const int total = counts[0] + counts[1] + counts[2] + counts[3];
  for (s = 0; s < 4; ++s) {
    fprintf(stderr, "|     %3d%%", (int)(100. * counts[s] / total + .5));
  }
  fprintf(stderr, "| %7d\n", total);
}

static void PrintValues(const int values[4]) {
  int s;
  for (s = 0; s < 4; ++s) {
    fprintf(stderr, "| %7d ", values[s]);
  }
  fprintf(stderr, "|\n");
}

static void PrintFullLosslessInfo(const WebPAuxStats* const stats,
                                  const char* const description) {
  fprintf(stderr, "Lossless-%s compressed size: %d bytes\n",
          description, stats->lossless_size);
  fprintf(stderr, "  * Header size: %d bytes, image data size: %d\n",
          stats->lossless_hdr_size, stats->lossless_data_size);
  if (stats->lossless_features) {
    fprintf(stderr, "  * Lossless features used:");
    if (stats->lossless_features & 1) fprintf(stderr, " PREDICTION");
    if (stats->lossless_features & 2) fprintf(stderr, " CROSS-COLOR-TRANSFORM");
    if (stats->lossless_features & 4) fprintf(stderr, " SUBTRACT-GREEN");
    if (stats->lossless_features & 8) fprintf(stderr, " PALETTE");
    fprintf(stderr, "\n");
  }
  fprintf(stderr, "  * Precision Bits: histogram=%d", stats->histogram_bits);
  if (stats->lossless_features & 1) {
    fprintf(stderr, " prediction=%d", stats->transform_bits);
  }
  if (stats->lossless_features & 2) {
    fprintf(stderr, " cross-color=%d", stats->cross_color_transform_bits);
  }
  fprintf(stderr, " cache=%d\n", stats->cache_bits);
  if (stats->palette_size > 0) {
    fprintf(stderr, "  * Palette size:   %d\n", stats->palette_size);
  }
}

static void PrintExtraInfoLossless(const WebPPicture* const pic,
                                   int short_output,
                                   const char* const file_name) {
  const WebPAuxStats* const stats = pic->stats;
  if (short_output) {
    fprintf(stderr, "%7d %2.2f\n", stats->coded_size, stats->PSNR[3]);
  } else {
    WFPRINTF(stderr, "File:      %s\n", (const W_CHAR*)file_name);
    fprintf(stderr, "Dimension: %d x %d\n", pic->width, pic->height);
    fprintf(stderr, "Output:    %d bytes (%.2f bpp)\n", stats->coded_size,
            8.f * stats->coded_size / pic->width / pic->height);
    PrintFullLosslessInfo(stats, "ARGB");
  }
}

static void PrintExtraInfoLossy(const WebPPicture* const pic, int short_output,
                                int full_details,
                                const char* const file_name) {
  const WebPAuxStats* const stats = pic->stats;
  if (short_output) {
    fprintf(stderr, "%7d %2.2f\n", stats->coded_size, stats->PSNR[3]);
  } else {
    const int num_i4 = stats->block_count[0];
    const int num_i16 = stats->block_count[1];
    const int num_skip = stats->block_count[2];
    const int total = num_i4 + num_i16;
    WFPRINTF(stderr, "File:      %s\n", (const W_CHAR*)file_name);
    fprintf(stderr, "Dimension: %d x %d%s\n",
            pic->width, pic->height,
            stats->alpha_data_size ? " (with alpha)" : "");
    fprintf(stderr, "Output:    "
            "%d bytes Y-U-V-All-PSNR %2.2f %2.2f %2.2f   %2.2f dB\n"
            "           (%.2f bpp)\n",
            stats->coded_size,
            stats->PSNR[0], stats->PSNR[1], stats->PSNR[2], stats->PSNR[3],
            8.f * stats->coded_size / pic->width / pic->height);
    if (total > 0) {
      int totals[4] = { 0, 0, 0, 0 };
      fprintf(stderr, "block count:  intra4:     %6d  (%.2f%%)\n"
                      "              intra16:    %6d  (%.2f%%)\n"
                      "              skipped:    %6d  (%.2f%%)\n",
              num_i4, 100.f * num_i4 / total,
              num_i16, 100.f * num_i16 / total,
              num_skip, 100.f * num_skip / total);
      fprintf(stderr, "bytes used:  header:         %6d  (%.1f%%)\n"
                      "             mode-partition: %6d  (%.1f%%)\n",
              stats->header_bytes[0],
              100.f * stats->header_bytes[0] / stats->coded_size,
              stats->header_bytes[1],
              100.f * stats->header_bytes[1] / stats->coded_size);
      if (stats->alpha_data_size > 0) {
        fprintf(stderr, "             transparency:   %6d (%.1f dB)\n",
                stats->alpha_data_size, stats->PSNR[4]);
      }
      fprintf(stderr, " Residuals bytes  "
                      "|segment 1|segment 2|segment 3"
                      "|segment 4|  total\n");
      if (full_details) {
        fprintf(stderr, "  intra4-coeffs:  ");
        PrintByteCount(stats->residual_bytes[0], stats->coded_size, totals);
        fprintf(stderr, " intra16-coeffs:  ");
        PrintByteCount(stats->residual_bytes[1], stats->coded_size, totals);
        fprintf(stderr, "  chroma coeffs:  ");
        PrintByteCount(stats->residual_bytes[2], stats->coded_size, totals);
      }
      fprintf(stderr, "    macroblocks:  ");
      PrintPercents(stats->segment_size);
      fprintf(stderr, "      quantizer:  ");
      PrintValues(stats->segment_quant);
      fprintf(stderr, "   filter level:  ");
      PrintValues(stats->segment_level);
      if (full_details) {
        fprintf(stderr, "------------------+---------");
        fprintf(stderr, "+---------+---------+---------+-----------------\n");
        fprintf(stderr, " segments total:  ");
        PrintByteCount(totals, stats->coded_size, NULL);
      }
    }
    if (stats->lossless_size > 0) {
      PrintFullLosslessInfo(stats, "alpha");
    }
  }
}

static void PrintMapInfo(const WebPPicture* const pic) {
  if (pic->extra_info != NULL) {
    const int mb_w = (pic->width + 15) / 16;
    const int mb_h = (pic->height + 15) / 16;
    const int type = pic->extra_info_type;
    int x, y;
    for (y = 0; y < mb_h; ++y) {
      for (x = 0; x < mb_w; ++x) {
        const int c = pic->extra_info[x + y * mb_w];
        if (type == 1) {   // intra4/intra16
          fprintf(stderr, "%c", "+."[c]);
        } else if (type == 2) {    // segments
          fprintf(stderr, "%c", ".-*X"[c]);
        } else if (type == 3) {    // quantizers
          fprintf(stderr, "%.2d ", c);
        } else if (type == 6 || type == 7) {
          fprintf(stderr, "%3d ", c);
        } else {
          fprintf(stderr, "0x%.2x ", c);
        }
      }
      fprintf(stderr, "\n");
    }
  }
}

//------------------------------------------------------------------------------

static int MyWriter(const uint8_t* data, size_t data_size,
                    const WebPPicture* const pic) {
  FILE* const out = (FILE*)pic->custom_ptr;
  return data_size ? (fwrite(data, data_size, 1, out) == 1) : 1;
}

// Dumps a picture as a PGM file using the IMC4 layout.
static int DumpPicture(const WebPPicture* const picture, const char* PGM_name) {
  int y;
  int ok = 0;
  const int uv_width = (picture->width + 1) / 2;
  const int uv_height = (picture->height + 1) / 2;
  const int stride = (picture->width + 1) & ~1;
  const uint8_t* src_y = picture->y;
  const uint8_t* src_u = picture->u;
  const uint8_t* src_v = picture->v;
  const uint8_t* src_a = picture->a;
  const int alpha_height =
      WebPPictureHasTransparency(picture) ? picture->height : 0;
  const int height = picture->height + uv_height + alpha_height;
  FILE* const f = WFOPEN(PGM_name, "wb");
  if (f == NULL) return 0;
  fprintf(f, "P5\n%d %d\n255\n", stride, height);
  for (y = 0; y < picture->height; ++y) {
    if (fwrite(src_y, picture->width, 1, f) != 1) goto Error;
    if (picture->width & 1) fputc(0, f);  // pad
    src_y += picture->y_stride;
  }
  for (y = 0; y < uv_height; ++y) {
    if (fwrite(src_u, uv_width, 1, f) != 1) goto Error;
    if (fwrite(src_v, uv_width, 1, f) != 1) goto Error;
    src_u += picture->uv_stride;
    src_v += picture->uv_stride;
  }
  for (y = 0; y < alpha_height; ++y) {
    if (fwrite(src_a, picture->width, 1, f) != 1) goto Error;
    if (picture->width & 1) fputc(0, f);  // pad
    src_a += picture->a_stride;
  }
  ok = 1;

 Error:
  fclose(f);
  return ok;
}

// -----------------------------------------------------------------------------
// Metadata writing.

enum {
  METADATA_EXIF = (1 << 0),
  METADATA_ICC  = (1 << 1),
  METADATA_XMP  = (1 << 2),
  METADATA_ALL  = METADATA_EXIF | METADATA_ICC | METADATA_XMP
};

static const int kChunkHeaderSize = 8;
static const int kTagSize = 4;

static void PrintMetadataInfo(const Metadata* const metadata,
                              int metadata_written) {
  if (metadata == NULL || metadata_written == 0) return;

  fprintf(stderr, "Metadata:\n");
  if (metadata_written & METADATA_ICC) {
    fprintf(stderr, "  * ICC profile:  %6d bytes\n", (int)metadata->iccp.size);
  }
  if (metadata_written & METADATA_EXIF) {
    fprintf(stderr, "  * EXIF data:    %6d bytes\n", (int)metadata->exif.size);
  }
  if (metadata_written & METADATA_XMP) {
    fprintf(stderr, "  * XMP data:     %6d bytes\n", (int)metadata->xmp.size);
  }
}

// Outputs, in little endian, 'num' bytes from 'val' to 'out'.
static int WriteLE(FILE* const out, uint32_t val, int num) {
  uint8_t buf[4];
  int i;
  for (i = 0; i < num; ++i) {
    buf[i] = (uint8_t)(val & 0xff);
    val >>= 8;
  }
  return (fwrite(buf, num, 1, out) == 1);
}

static int WriteLE24(FILE* const out, uint32_t val) {
  return WriteLE(out, val, 3);
}

static int WriteLE32(FILE* const out, uint32_t val) {
  return WriteLE(out, val, 4);
}

static int WriteMetadataChunk(FILE* const out, const char fourcc[4],
                              const MetadataPayload* const payload) {
  const uint8_t zero = 0;
  const size_t need_padding = payload->size & 1;
  int ok = (fwrite(fourcc, kTagSize, 1, out) == 1);
  ok = ok && WriteLE32(out, (uint32_t)payload->size);
  ok = ok && (fwrite(payload->bytes, payload->size, 1, out) == 1);
  return ok && (fwrite(&zero, need_padding, need_padding, out) == need_padding);
}

// Sets 'flag' in 'vp8x_flags' and updates 'metadata_size' with the size of the
// chunk if there is metadata and 'keep' is true.
static int UpdateFlagsAndSize(const MetadataPayload* const payload,
                              int keep, int flag,
                              uint32_t* vp8x_flags, uint64_t* metadata_size) {
  if (keep && payload->bytes != NULL && payload->size > 0) {
    *vp8x_flags |= flag;
    *metadata_size += kChunkHeaderSize + payload->size + (payload->size & 1);
    return 1;
  }
  return 0;
}

// Writes a WebP file using the image contained in 'memory_writer' and the
// metadata from 'metadata'. Metadata is controlled by 'keep_metadata' and the
// availability in 'metadata'. Returns true on success.
// For details see doc/webp-container-spec.txt#extended-file-format.
static int WriteWebPWithMetadata(FILE* const out,
                                 const WebPPicture* const picture,
                                 const WebPMemoryWriter* const memory_writer,
                                 const Metadata* const metadata,
                                 int keep_metadata,
                                 int* const metadata_written) {
  const char kVP8XHeader[] = "VP8X\x0a\x00\x00\x00";
  const int kAlphaFlag = 0x10;
  const int kEXIFFlag  = 0x08;
  const int kICCPFlag  = 0x20;
  const int kXMPFlag   = 0x04;
  const size_t kRiffHeaderSize = 12;
  const size_t kMaxChunkPayload = ~0 - kChunkHeaderSize - 1;
  const size_t kMinSize = kRiffHeaderSize + kChunkHeaderSize;
  uint32_t flags = 0;
  uint64_t metadata_size = 0;
  const int write_exif = UpdateFlagsAndSize(&metadata->exif,
                                            !!(keep_metadata & METADATA_EXIF),
                                            kEXIFFlag, &flags, &metadata_size);
  const int write_iccp = UpdateFlagsAndSize(&metadata->iccp,
                                            !!(keep_metadata & METADATA_ICC),
                                            kICCPFlag, &flags, &metadata_size);
  const int write_xmp  = UpdateFlagsAndSize(&metadata->xmp,
                                            !!(keep_metadata & METADATA_XMP),
                                            kXMPFlag, &flags, &metadata_size);
  uint8_t* webp = memory_writer->mem;
  size_t webp_size = memory_writer->size;

  *metadata_written = 0;

  if (webp_size < kMinSize) return 0;
  if (webp_size - kChunkHeaderSize + metadata_size > kMaxChunkPayload) {
    fprintf(stderr, "Error! Addition of metadata would exceed "
                    "container size limit.\n");
    return 0;
  }

  if (metadata_size > 0) {
    const int kVP8XChunkSize = 18;
    const int has_vp8x = !memcmp(webp + kRiffHeaderSize, "VP8X", kTagSize);
    const uint32_t riff_size = (uint32_t)(webp_size - kChunkHeaderSize +
                                          (has_vp8x ? 0 : kVP8XChunkSize) +
                                          metadata_size);
    // RIFF
    int ok = (fwrite(webp, kTagSize, 1, out) == 1);
    // RIFF size (file header size is not recorded)
    ok = ok && WriteLE32(out, riff_size);
    webp += kChunkHeaderSize;
    webp_size -= kChunkHeaderSize;
    // WEBP
    ok = ok && (fwrite(webp, kTagSize, 1, out) == 1);
    webp += kTagSize;
    webp_size -= kTagSize;
    if (has_vp8x) {  // update the existing VP8X flags
      webp[kChunkHeaderSize] |= (uint8_t)(flags & 0xff);
      ok = ok && (fwrite(webp, kVP8XChunkSize, 1, out) == 1);
      webp += kVP8XChunkSize;
      webp_size -= kVP8XChunkSize;
    } else {
      const int is_lossless = !memcmp(webp, "VP8L", kTagSize);
      if (is_lossless) {
        // Presence of alpha is stored in the 37th bit (29th after the
        // signature) of VP8L data.
        if (webp[kChunkHeaderSize + 4] & (1 << 4)) flags |= kAlphaFlag;
      }
      ok = ok && (fwrite(kVP8XHeader, kChunkHeaderSize, 1, out) == 1);
      ok = ok && WriteLE32(out, flags);
      ok = ok && WriteLE24(out, picture->width - 1);
      ok = ok && WriteLE24(out, picture->height - 1);
    }
    if (write_iccp) {
      ok = ok && WriteMetadataChunk(out, "ICCP", &metadata->iccp);
      *metadata_written |= METADATA_ICC;
    }
    // Image
    ok = ok && (fwrite(webp, webp_size, 1, out) == 1);
    if (write_exif) {
      ok = ok && WriteMetadataChunk(out, "EXIF", &metadata->exif);
      *metadata_written |= METADATA_EXIF;
    }
    if (write_xmp) {
      ok = ok && WriteMetadataChunk(out, "XMP ", &metadata->xmp);
      *metadata_written |= METADATA_XMP;
    }
    return ok;
  }

  // No metadata, just write the original image file.
  return (fwrite(webp, webp_size, 1, out) == 1);
}

//------------------------------------------------------------------------------

static int ProgressReport(int percent, const WebPPicture* const picture) {
  fprintf(stderr, "[%s]: %3d %%      \r",
          (char*)picture->user_data, percent);
  return 1;  // all ok
}

//------------------------------------------------------------------------------

static void HelpShort(void) {
  printf("Usage:\n\n");
  printf("   cwebp [options] -q quality input.png -o output.webp\n\n");
  printf("where quality is between 0 (poor) to 100 (very good).\n");
  printf("Typical value is around 80.\n\n");
  printf("Try -longhelp for an exhaustive list of advanced options.\n");
}

static void HelpLong(void) {
  printf("Usage:\n");
  printf(" cwebp [-preset <...>] [options] in_file [-o out_file]\n\n");
  printf("If input size (-s) for an image is not specified, it is\n"
         "assumed to be a PNG, JPEG, TIFF or WebP file.\n");
  printf("Note: Animated PNG and WebP files are not supported.\n");
#ifdef HAVE_WINCODEC_H
  printf("Windows builds can take as input any of the files handled by WIC.\n");
#endif
  printf("\nOptions:\n");
  printf("  -h / -help ............. short help\n");
  printf("  -H / -longhelp ......... long help\n");
  printf("  -q <float> ............. quality factor (0:small..100:big), "
         "default=75\n");
  printf("  -alpha_q <int> ......... transparency-compression quality (0..100),"
         "\n                           default=100\n");
  printf("  -preset <string> ....... preset setting, one of:\n");
  printf("                            default, photo, picture,\n");
  printf("                            drawing, icon, text\n");
  printf("     -preset must come first, as it overwrites other parameters\n");
  printf("  -z <int> ............... activates lossless preset with given\n"
         "                           level in [0:fast, ..., 9:slowest]\n");
  printf("\n");
  printf("  -m <int> ............... compression method (0=fast, 6=slowest), "
         "default=4\n");
  printf("  -segments <int> ........ number of segments to use (1..4), "
         "default=4\n");
  printf("  -size <int> ............ target size (in bytes)\n");
  printf("  -psnr <float> .......... target PSNR (in dB. typically: 42)\n");
  printf("\n");
  printf("  -s <int> <int> ......... input size (width x height) for YUV\n");
  printf("  -sns <int> ............. spatial noise shaping (0:off, 100:max), "
         "default=50\n");
  printf("  -f <int> ............... filter strength (0=off..100), "
         "default=60\n");
  printf("  -sharpness <int> ....... "
         "filter sharpness (0:most .. 7:least sharp), default=0\n");
  printf("  -strong ................ use strong filter instead "
                                     "of simple (default)\n");
  printf("  -nostrong .............. use simple filter instead of strong\n");
  printf("  -sharp_yuv ............. use sharper (and slower) RGB->YUV "
                                     "conversion\n");
  printf("  -partition_limit <int> . limit quality to fit the 512k limit on\n");
  printf("                           "
         "the first partition (0=no degradation ... 100=full)\n");
  printf("  -pass <int> ............ analysis pass number (1..10)\n");
  printf("  -qrange <min> <max> .... specifies the permissible quality range\n"
         "                           (default: 0 100)\n");
  printf("  -crop <x> <y> <w> <h> .. crop picture with the given rectangle\n");
  printf("  -resize <w> <h> ........ resize picture (*after* any cropping)\n");
  printf("  -mt .................... use multi-threading if available\n");
  printf("  -low_memory ............ reduce memory usage (slower encoding)\n");
  printf("  -map <int> ............. print map of extra info\n");
  printf("  -print_psnr ............ prints averaged PSNR distortion\n");
  printf("  -print_ssim ............ prints averaged SSIM distortion\n");
  printf("  -print_lsim ............ prints local-similarity distortion\n");
  printf("  -d <file.pgm> .......... dump the compressed output (PGM file)\n");
  printf("  -alpha_method <int> .... transparency-compression method (0..1), "
         "default=1\n");
  printf("  -alpha_filter <string> . predictive filtering for alpha plane,\n");
  printf("                           one of: none, fast (default) or best\n");
  printf("  -exact ................. preserve RGB values in transparent area, "
         "default=off\n");
  printf("  -blend_alpha <hex> ..... blend colors against background color\n"
         "                           expressed as RGB values written in\n"
         "                           hexadecimal, e.g. 0xc0e0d0 for red=0xc0\n"
         "                           green=0xe0 and blue=0xd0\n");
  printf("  -noalpha ............... discard any transparency information\n");
  printf("  -lossless .............. encode image losslessly, default=off\n");
  printf("  -near_lossless <int> ... use near-lossless image preprocessing\n"
         "                           (0..100=off), default=100\n");
  printf("  -hint <string> ......... specify image characteristics hint,\n");
  printf("                           one of: photo, picture or graph\n");

  printf("\n");
  printf("  -metadata <string> ..... comma separated list of metadata to\n");
  printf("                           ");
  printf("copy from the input to the output if present.\n");
  printf("                           "
         "Valid values: all, none (default), exif, icc, xmp\n");

  printf("\n");
  printf("  -short ................. condense printed message\n");
  printf("  -quiet ................. don't print anything\n");
  printf("  -version ............... print version number and exit\n");
#ifndef WEBP_DLL
  printf("  -noasm ................. disable all assembly optimizations\n");
#endif
  printf("  -v ..................... verbose, e.g. print encoding/decoding "
         "times\n");
  printf("  -progress .............. report encoding progress\n");
  printf("\n");
  printf("Experimental Options:\n");
  printf("  -jpeg_like ............. roughly match expected JPEG size\n");
  printf("  -af .................... auto-adjust filter strength\n");
  printf("  -pre <int> ............. pre-processing filter\n");
  printf("\n");
  printf("Supported input formats:\n  %s\n", WebPGetEnabledInputFileFormats());
}

//------------------------------------------------------------------------------
// Error messages

static const char* const kErrorMessages[VP8_ENC_ERROR_LAST] = {
  "OK",
  "OUT_OF_MEMORY: Out of memory allocating objects",
  "BITSTREAM_OUT_OF_MEMORY: Out of memory re-allocating byte buffer",
  "NULL_PARAMETER: NULL parameter passed to function",
  "INVALID_CONFIGURATION: configuration is invalid",
  "BAD_DIMENSION: Bad picture dimension. Maximum width and height "
  "allowed is 16383 pixels.",
  "PARTITION0_OVERFLOW: Partition #0 is too big to fit 512k.\n"
  "To reduce the size of this partition, try using less segments "
  "with the -segments option, and eventually reduce the number of "
  "header bits using -partition_limit. More details are available "
  "in the manual (`man cwebp`)",
  "PARTITION_OVERFLOW: Partition is too big to fit 16M",
  "BAD_WRITE: Picture writer returned an I/O error",
  "FILE_TOO_BIG: File would be too big to fit in 4G",
  "USER_ABORT: encoding abort requested by user"
};

//------------------------------------------------------------------------------

// Returns EXIT_SUCCESS on success, EXIT_FAILURE on failure.
int main(int argc, const char* argv[]) {
  int return_value = EXIT_FAILURE;
  const char* in_file = NULL, *out_file = NULL, *dump_file = NULL;
  FILE* out = NULL;
  int c;
  int short_output = 0;
  int quiet = 0;
  int keep_alpha = 1;
  int blend_alpha = 0;
  uint32_t background_color = 0xffffffu;
  int crop = 0, crop_x = 0, crop_y = 0, crop_w = 0, crop_h = 0;
  int resize_w = 0, resize_h = 0;
  int lossless_preset = 6;
  int use_lossless_preset = -1;  // -1=unset, 0=don't use, 1=use it
  int show_progress = 0;
  int keep_metadata = 0;
  int metadata_written = 0;
  WebPPicture picture;
  int print_distortion = -1;        // -1=off, 0=PSNR, 1=SSIM, 2=LSIM
  WebPPicture original_picture;    // when PSNR or SSIM is requested
  WebPConfig config;
  WebPAuxStats stats;
  WebPMemoryWriter memory_writer;
  int use_memory_writer;
  Metadata metadata;
  Stopwatch stop_watch;

  INIT_WARGV(argc, argv);

  MetadataInit(&metadata);
  WebPMemoryWriterInit(&memory_writer);
  if (!WebPPictureInit(&picture) ||
      !WebPPictureInit(&original_picture) ||
      !WebPConfigInit(&config)) {
    fprintf(stderr, "Error! Version mismatch!\n");
    FREE_WARGV_AND_RETURN(EXIT_FAILURE);
  }

  if (argc == 1) {
    HelpShort();
    FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
  }

  for (c = 1; c < argc; ++c) {
    int parse_error = 0;
    if (!strcmp(argv[c], "-h") || !strcmp(argv[c], "-help")) {
      HelpShort();
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else if (!strcmp(argv[c], "-H") || !strcmp(argv[c], "-longhelp")) {
      HelpLong();
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else if (!strcmp(argv[c], "-o") && c + 1 < argc) {
      out_file = (const char*)GET_WARGV(argv, ++c);
    } else if (!strcmp(argv[c], "-d") && c + 1 < argc) {
      dump_file = (const char*)GET_WARGV(argv, ++c);
      config.show_compressed = 1;
    } else if (!strcmp(argv[c], "-print_psnr")) {
      config.show_compressed = 1;
      print_distortion = 0;
    } else if (!strcmp(argv[c], "-print_ssim")) {
      config.show_compressed = 1;
      print_distortion = 1;
    } else if (!strcmp(argv[c], "-print_lsim")) {
      config.show_compressed = 1;
      print_distortion = 2;
    } else if (!strcmp(argv[c], "-short")) {
      ++short_output;
    } else if (!strcmp(argv[c], "-s") && c + 2 < argc) {
      picture.width = ExUtilGetInt(argv[++c], 0, &parse_error);
      picture.height = ExUtilGetInt(argv[++c], 0, &parse_error);
      if (picture.width > WEBP_MAX_DIMENSION || picture.width < 0 ||
          picture.height > WEBP_MAX_DIMENSION ||  picture.height < 0) {
        fprintf(stderr,
                "Specified dimension (%d x %d) is out of range.\n",
                picture.width, picture.height);
        goto Error;
      }
    } else if (!strcmp(argv[c], "-m") && c + 1 < argc) {
      config.method = ExUtilGetInt(argv[++c], 0, &parse_error);
      use_lossless_preset = 0;   // disable -z option
    } else if (!strcmp(argv[c], "-q") && c + 1 < argc) {
      config.quality = ExUtilGetFloat(argv[++c], &parse_error);
      use_lossless_preset = 0;   // disable -z option
    } else if (!strcmp(argv[c], "-z") && c + 1 < argc) {
      lossless_preset = ExUtilGetInt(argv[++c], 0, &parse_error);
      if (use_lossless_preset != 0) use_lossless_preset = 1;
    } else if (!strcmp(argv[c], "-alpha_q") && c + 1 < argc) {
      config.alpha_quality = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-alpha_method") && c + 1 < argc) {
      config.alpha_compression = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-alpha_cleanup")) {
      // This flag is obsolete, does opposite of -exact.
      config.exact = 0;
    } else if (!strcmp(argv[c], "-exact")) {
      config.exact = 1;
    } else if (!strcmp(argv[c], "-blend_alpha") && c + 1 < argc) {
      blend_alpha = 1;
      // background color is given in hex with an optional '0x' prefix
      background_color = ExUtilGetInt(argv[++c], 16, &parse_error);
      background_color = background_color & 0x00ffffffu;
    } else if (!strcmp(argv[c], "-alpha_filter") && c + 1 < argc) {
      ++c;
      if (!strcmp(argv[c], "none")) {
        config.alpha_filtering = 0;
      } else if (!strcmp(argv[c], "fast")) {
        config.alpha_filtering = 1;
      } else if (!strcmp(argv[c], "best")) {
        config.alpha_filtering = 2;
      } else {
        fprintf(stderr, "Error! Unrecognized alpha filter: %s\n", argv[c]);
        goto Error;
      }
    } else if (!strcmp(argv[c], "-noalpha")) {
      keep_alpha = 0;
    } else if (!strcmp(argv[c], "-lossless")) {
      config.lossless = 1;
    } else if (!strcmp(argv[c], "-near_lossless") && c + 1 < argc) {
      config.near_lossless = ExUtilGetInt(argv[++c], 0, &parse_error);
      config.lossless = 1;  // use near-lossless only with lossless
    } else if (!strcmp(argv[c], "-hint") && c + 1 < argc) {
      ++c;
      if (!strcmp(argv[c], "photo")) {
        config.image_hint = WEBP_HINT_PHOTO;
      } else if (!strcmp(argv[c], "picture")) {
        config.image_hint = WEBP_HINT_PICTURE;
      } else if (!strcmp(argv[c], "graph")) {
        config.image_hint = WEBP_HINT_GRAPH;
      } else {
        fprintf(stderr, "Error! Unrecognized image hint: %s\n", argv[c]);
        goto Error;
      }
    } else if (!strcmp(argv[c], "-size") && c + 1 < argc) {
      config.target_size = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-psnr") && c + 1 < argc) {
      config.target_PSNR = ExUtilGetFloat(argv[++c], &parse_error);
    } else if (!strcmp(argv[c], "-sns") && c + 1 < argc) {
      config.sns_strength = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-f") && c + 1 < argc) {
      config.filter_strength = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-af")) {
      config.autofilter = 1;
    } else if (!strcmp(argv[c], "-jpeg_like")) {
      config.emulate_jpeg_size = 1;
    } else if (!strcmp(argv[c], "-mt")) {
      ++config.thread_level;  // increase thread level
    } else if (!strcmp(argv[c], "-low_memory")) {
      config.low_memory = 1;
    } else if (!strcmp(argv[c], "-strong")) {
      config.filter_type = 1;
    } else if (!strcmp(argv[c], "-nostrong")) {
      config.filter_type = 0;
    } else if (!strcmp(argv[c], "-sharpness") && c + 1 < argc) {
      config.filter_sharpness = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-sharp_yuv")) {
      config.use_sharp_yuv = 1;
    } else if (!strcmp(argv[c], "-pass") && c + 1 < argc) {
      config.pass = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-qrange") && c + 2 < argc) {
      config.qmin = ExUtilGetInt(argv[++c], 0, &parse_error);
      config.qmax = ExUtilGetInt(argv[++c], 0, &parse_error);
      if (config.qmin < 0) config.qmin = 0;
      if (config.qmax > 100) config.qmax = 100;
    } else if (!strcmp(argv[c], "-pre") && c + 1 < argc) {
      config.preprocessing = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-segments") && c + 1 < argc) {
      config.segments = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-partition_limit") && c + 1 < argc) {
      config.partition_limit = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-map") && c + 1 < argc) {
      picture.extra_info_type = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-crop") && c + 4 < argc) {
      crop = 1;
      crop_x = ExUtilGetInt(argv[++c], 0, &parse_error);
      crop_y = ExUtilGetInt(argv[++c], 0, &parse_error);
      crop_w = ExUtilGetInt(argv[++c], 0, &parse_error);
      crop_h = ExUtilGetInt(argv[++c], 0, &parse_error);
    } else if (!strcmp(argv[c], "-resize") && c + 2 < argc) {
      resize_w = ExUtilGetInt(argv[++c], 0, &parse_error);
      resize_h = ExUtilGetInt(argv[++c], 0, &parse_error);
#ifndef WEBP_DLL
    } else if (!strcmp(argv[c], "-noasm")) {
      VP8GetCPUInfo = NULL;
#endif
    } else if (!strcmp(argv[c], "-version")) {
      const int version = WebPGetEncoderVersion();
      const int sharpyuv_version = SharpYuvGetVersion();
      printf("%d.%d.%d\n",
             (version >> 16) & 0xff, (version >> 8) & 0xff, version & 0xff);
      printf("libsharpyuv: %d.%d.%d\n",
             (sharpyuv_version >> 24) & 0xff, (sharpyuv_version >> 16) & 0xffff,
             sharpyuv_version & 0xff);
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else if (!strcmp(argv[c], "-progress")) {
      show_progress = 1;
    } else if (!strcmp(argv[c], "-quiet")) {
      quiet = 1;
    } else if (!strcmp(argv[c], "-preset") && c + 1 < argc) {
      WebPPreset preset;
      ++c;
      if (!strcmp(argv[c], "default")) {
        preset = WEBP_PRESET_DEFAULT;
      } else if (!strcmp(argv[c], "photo")) {
        preset = WEBP_PRESET_PHOTO;
      } else if (!strcmp(argv[c], "picture")) {
        preset = WEBP_PRESET_PICTURE;
      } else if (!strcmp(argv[c], "drawing")) {
        preset = WEBP_PRESET_DRAWING;
      } else if (!strcmp(argv[c], "icon")) {
        preset = WEBP_PRESET_ICON;
      } else if (!strcmp(argv[c], "text")) {
        preset = WEBP_PRESET_TEXT;
      } else {
        fprintf(stderr, "Error! Unrecognized preset: %s\n", argv[c]);
        goto Error;
      }
      if (!WebPConfigPreset(&config, preset, config.quality)) {
        fprintf(stderr, "Error! Could initialize configuration with preset.\n");
        goto Error;
      }
    } else if (!strcmp(argv[c], "-metadata") && c + 1 < argc) {
      static const struct {
        const char* option;
        int flag;
      } kTokens[] = {
        { "all",  METADATA_ALL },
        { "none", 0 },
        { "exif", METADATA_EXIF },
        { "icc",  METADATA_ICC },
        { "xmp",  METADATA_XMP },
      };
      const size_t kNumTokens = sizeof(kTokens) / sizeof(kTokens[0]);
      const char* start = argv[++c];
      const char* const end = start + strlen(start);

      while (start < end) {
        size_t i;
        const char* token = strchr(start, ',');
        if (token == NULL) token = end;

        for (i = 0; i < kNumTokens; ++i) {
          if ((size_t)(token - start) == strlen(kTokens[i].option) &&
              !strncmp(start, kTokens[i].option, strlen(kTokens[i].option))) {
            if (kTokens[i].flag != 0) {
              keep_metadata |= kTokens[i].flag;
            } else {
              keep_metadata = 0;
            }
            break;
          }
        }
        if (i == kNumTokens) {
          fprintf(stderr, "Error! Unknown metadata type '%.*s'\n",
                  (int)(token - start), start);
          FREE_WARGV_AND_RETURN(EXIT_FAILURE);
        }
        start = token + 1;
      }
#ifdef HAVE_WINCODEC_H
      if (keep_metadata != 0 && keep_metadata != METADATA_ICC) {
        // TODO(jzern): remove when -metadata is supported on all platforms.
        fprintf(stderr, "Warning: only ICC profile extraction is currently"
                        " supported on this platform!\n");
      }
#endif
    } else if (!strcmp(argv[c], "-v")) {
      verbose = 1;
    } else if (!strcmp(argv[c], "--")) {
      if (c + 1 < argc) in_file = (const char*)GET_WARGV(argv, ++c);
      break;
    } else if (argv[c][0] == '-') {
      fprintf(stderr, "Error! Unknown option '%s'\n", argv[c]);
      HelpLong();
      FREE_WARGV_AND_RETURN(EXIT_FAILURE);
    } else {
      in_file = (const char*)GET_WARGV(argv, c);
    }

    if (parse_error) {
      HelpLong();
      FREE_WARGV_AND_RETURN(EXIT_FAILURE);
    }
  }
  if (in_file == NULL) {
    fprintf(stderr, "No input file specified!\n");
    HelpShort();
    goto Error;
  }

  if (use_lossless_preset == 1) {
    if (!WebPConfigLosslessPreset(&config, lossless_preset)) {
      fprintf(stderr, "Invalid lossless preset (-z %d)\n", lossless_preset);
      goto Error;
    }
  }

  // Check for unsupported command line options for lossless mode and log
  // warning for such options.
  if (!quiet && config.lossless == 1) {
    if (config.target_size > 0 || config.target_PSNR > 0) {
      fprintf(stderr, "Encoding for specified size or PSNR is not supported"
                      " for lossless encoding. Ignoring such option(s)!\n");
    }
    if (config.partition_limit > 0) {
      fprintf(stderr, "Partition limit option is not required for lossless"
                      " encoding. Ignoring this option!\n");
    }
  }
  // If a target size or PSNR was given, but somehow the -pass option was
  // omitted, force a reasonable value.
  if (config.target_size > 0 || config.target_PSNR > 0) {
    if (config.pass == 1) config.pass = 6;
  }

  if (!WebPValidateConfig(&config)) {
    fprintf(stderr, "Error! Invalid configuration.\n");
    goto Error;
  }

  // Read the input. We need to decide if we prefer ARGB or YUVA
  // samples, depending on the expected compression mode (this saves
  // some conversion steps).
  picture.use_argb = (config.lossless || config.use_sharp_yuv ||
                      config.preprocessing > 0 ||
                      crop || (resize_w | resize_h) > 0);
  if (verbose) {
    StopwatchReset(&stop_watch);
  }
  if (!ReadPicture(in_file, &picture, keep_alpha,
                   (keep_metadata == 0) ? NULL : &metadata)) {
    WFPRINTF(stderr, "Error! Cannot read input picture file '%s'\n",
             (const W_CHAR*)in_file);
    goto Error;
  }
  picture.progress_hook = (show_progress && !quiet) ? ProgressReport : NULL;

  if (blend_alpha) {
    WebPBlendAlpha(&picture, background_color);
  }

  if (verbose) {
    const double read_time = StopwatchReadAndReset(&stop_watch);
    fprintf(stderr, "Time to read input: %.3fs\n", read_time);
  }
  // The bitstream should be kept in memory when metadata must be appended
  // before writing it to a file/stream, and/or when the near-losslessly encoded
  // bitstream must be decoded for distortion computation (lossy will modify the
  // 'picture' but not the lossless pipeline).
  // Otherwise directly write the bitstream to a file.
  use_memory_writer = (out_file != NULL && keep_metadata) ||
                      (!quiet && print_distortion >= 0 && config.lossless &&
                       config.near_lossless < 100);

  // Open the output
  if (out_file != NULL) {
    const int use_stdout = !WSTRCMP(out_file, "-");
    out = use_stdout ? ImgIoUtilSetBinaryMode(stdout) : WFOPEN(out_file, "wb");
    if (out == NULL) {
      WFPRINTF(stderr, "Error! Cannot open output file '%s'\n",
               (const W_CHAR*)out_file);
      goto Error;
    } else {
      if (!short_output && !quiet) {
        WFPRINTF(stderr, "Saving file '%s'\n", (const W_CHAR*)out_file);
      }
    }
    if (use_memory_writer) {
      picture.writer = WebPMemoryWrite;
      picture.custom_ptr = (void*)&memory_writer;
    } else {
      picture.writer = MyWriter;
      picture.custom_ptr = (void*)out;
    }
  } else {
    out = NULL;
    if (use_memory_writer) {
      picture.writer = WebPMemoryWrite;
      picture.custom_ptr = (void*)&memory_writer;
    }
    if (!quiet && !short_output) {
      fprintf(stderr, "No output file specified (no -o flag). Encoding will\n");
      fprintf(stderr, "be performed, but its results discarded.\n\n");
    }
  }
  if (!quiet) {
    picture.stats = &stats;
    picture.user_data = (void*)in_file;
  }

  // Crop & resize.
  if (verbose) {
    StopwatchReset(&stop_watch);
  }
  if (crop != 0) {
    // We use self-cropping using a view.
    if (!WebPPictureView(&picture, crop_x, crop_y, crop_w, crop_h, &picture)) {
      fprintf(stderr, "Error! Cannot crop picture\n");
      goto Error;
    }
  }
  if ((resize_w | resize_h) > 0) {
    WebPPicture picture_no_alpha;
    if (config.exact) {
      // If -exact, we can't premultiply RGB by A otherwise RGB is lost if A=0.
      // We rescale an opaque copy and assemble scaled A and non-premultiplied
      // RGB channels. This is slower but it's a very uncommon use case. Color
      // leak at sharp alpha edges is possible.
      if (!WebPPictureCopy(&picture, &picture_no_alpha)) {
        fprintf(stderr, "Error! Cannot copy temporary picture\n");
        goto Error;
      }

      // We enforced picture.use_argb = 1 above. Now, remove the alpha values.
      {
        int x, y;
        uint32_t* argb_no_alpha = picture_no_alpha.argb;
        for (y = 0; y < picture_no_alpha.height; ++y) {
          for (x = 0; x < picture_no_alpha.width; ++x) {
            argb_no_alpha[x] |= 0xff000000;  // Opaque copy.
          }
          argb_no_alpha += picture_no_alpha.argb_stride;
        }
      }

      if (!WebPPictureRescale(&picture_no_alpha, resize_w, resize_h)) {
        fprintf(stderr, "Error! Cannot resize temporary picture\n");
        goto Error;
      }
    }

    if (!WebPPictureRescale(&picture, resize_w, resize_h)) {
      fprintf(stderr, "Error! Cannot resize picture\n");
      goto Error;
    }

    if (config.exact) {  // Put back the alpha information.
      int x, y;
      uint32_t* argb_no_alpha = picture_no_alpha.argb;
      uint32_t* argb = picture.argb;
      for (y = 0; y < picture_no_alpha.height; ++y) {
        for (x = 0; x < picture_no_alpha.width; ++x) {
          argb[x] = (argb[x] & 0xff000000) | (argb_no_alpha[x] & 0x00ffffff);
        }
        argb_no_alpha += picture_no_alpha.argb_stride;
        argb += picture.argb_stride;
      }
      WebPPictureFree(&picture_no_alpha);
    }
  }
  if (verbose && (crop != 0 || (resize_w | resize_h) > 0)) {
    const double preproc_time = StopwatchReadAndReset(&stop_watch);
    fprintf(stderr, "Time to crop/resize picture: %.3fs\n", preproc_time);
  }

  if (picture.extra_info_type > 0) {
    AllocExtraInfo(&picture);
  }
  // Save original picture for later comparison. Only for lossy as lossless does
  // not modify 'picture' (even near-lossless).
  if (print_distortion >= 0 && !config.lossless &&
      !WebPPictureCopy(&picture, &original_picture)) {
    fprintf(stderr, "Error! Cannot copy temporary picture\n");
    goto Error;
  }

  // Compress.
  if (verbose) {
    StopwatchReset(&stop_watch);
  }
  if (!WebPEncode(&config, &picture)) {
    fprintf(stderr, "Error! Cannot encode picture as WebP\n");
    fprintf(stderr, "Error code: %d (%s)\n",
            picture.error_code, kErrorMessages[picture.error_code]);
    goto Error;
  }
  if (verbose) {
    const double encode_time = StopwatchReadAndReset(&stop_watch);
    fprintf(stderr, "Time to encode picture: %.3fs\n", encode_time);
  }

  // Get the decompressed image for the lossless pipeline.
  if (!quiet && print_distortion >= 0 && config.lossless) {
    if (config.near_lossless == 100) {
      // Pure lossless: image was not modified, make 'original_picture' a view
      // of 'picture' by copying all members except the freeable pointers.
      original_picture = picture;
      original_picture.memory_ = original_picture.memory_argb_ = NULL;
    } else {
      // Decode the bitstream stored in 'memory_writer' to get the altered image
      // to 'picture'; save the 'original_picture' beforehand.
      assert(use_memory_writer);
      original_picture = picture;
      if (!WebPPictureInit(&picture)) {  // Do not free 'picture'.
        fprintf(stderr, "Error! Version mismatch!\n");
        goto Error;
      }

      picture.use_argb = 1;
      if (!ReadWebP(
              memory_writer.mem, memory_writer.size, &picture,
              /*keep_alpha=*/WebPPictureHasTransparency(&original_picture),
              /*metadata=*/NULL)) {
        fprintf(stderr, "Error! Cannot decode encoded WebP bitstream\n");
        fprintf(stderr, "Error code: %d (%s)\n", picture.error_code,
                kErrorMessages[picture.error_code]);
        goto Error;
      }
      picture.stats = original_picture.stats;
    }
    original_picture.stats = NULL;
  }

  // Write the YUV planes to a PGM file. Only available for lossy.
  if (dump_file) {
    if (picture.use_argb) {
      fprintf(stderr, "Warning: can't dump file (-d option) "
                      "in lossless mode.\n");
    } else if (!DumpPicture(&picture, dump_file)) {
      WFPRINTF(stderr, "Warning, couldn't dump picture %s\n",
               (const W_CHAR*)dump_file);
    }
  }

  if (use_memory_writer && out != NULL &&
      !WriteWebPWithMetadata(out, &picture, &memory_writer, &metadata,
                             keep_metadata, &metadata_written)) {
    fprintf(stderr, "Error writing WebP file!\n");
    goto Error;
  }

  if (out == NULL && keep_metadata) {
    // output is disabled, just display the metadata stats.
    const struct {
      const MetadataPayload* const payload;
      int flag;
    } *iter, info[] = {{&metadata.exif, METADATA_EXIF},
                       {&metadata.iccp, METADATA_ICC},
                       {&metadata.xmp, METADATA_XMP},
                       {NULL, 0}};
    uint32_t unused1 = 0;
    uint64_t unused2 = 0;

    for (iter = info; iter->payload != NULL; ++iter) {
      if (UpdateFlagsAndSize(iter->payload, !!(keep_metadata & iter->flag),
                             /*flag=*/0, &unused1, &unused2)) {
        metadata_written |= iter->flag;
      }
    }
  }

  if (!quiet) {
    if (!short_output || print_distortion < 0) {
      if (config.lossless) {
        PrintExtraInfoLossless(&picture, short_output, in_file);
      } else {
        PrintExtraInfoLossy(&picture, short_output, config.low_memory, in_file);
      }
    }
    if (!short_output && picture.extra_info_type > 0) {
      PrintMapInfo(&picture);
    }
    if (print_distortion >= 0) {    // print distortion
      static const char* distortion_names[] = { "PSNR", "SSIM", "LSIM" };
      float values[5];
      if (!WebPPictureDistortion(&picture, &original_picture,
                                 print_distortion, values)) {
        fprintf(stderr, "Error while computing the distortion.\n");
        goto Error;
      }
      if (!short_output) {
        fprintf(stderr, "%s: ", distortion_names[print_distortion]);
        fprintf(stderr, "B:%.2f G:%.2f R:%.2f A:%.2f  Total:%.2f\n",
                values[0], values[1], values[2], values[3], values[4]);
      } else {
        fprintf(stderr, "%7d %.4f\n", picture.stats->coded_size, values[4]);
      }
    }
    if (!short_output) {
      PrintMetadataInfo(&metadata, metadata_written);
    }
  }
  return_value = EXIT_SUCCESS;

 Error:
  WebPMemoryWriterClear(&memory_writer);
  WebPFree(picture.extra_info);
  MetadataFree(&metadata);
  WebPPictureFree(&picture);
  WebPPictureFree(&original_picture);
  if (out != NULL && out != stdout) {
    fclose(out);
  }

  FREE_WARGV_AND_RETURN(return_value);
}

//------------------------------------------------------------------------------

// Copyright 2013 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
// Windows Imaging Component (WIC) decode.

#include "./wicdec.h"

#ifdef HAVE_CONFIG_H
#include "webp/config.h"
#endif

#include <assert.h>
#include <stdio.h>
#include <string.h>

#ifdef HAVE_WINCODEC_H
#ifdef __MINGW32__
#define INITGUID  // Without this GUIDs are declared extern and fail to link
#endif
#define CINTERFACE
#define COBJMACROS
#define _WIN32_IE 0x500  // Workaround bug in shlwapi.h when compiling C++
                         // code with COBJMACROS.
#include <ole2.h>  // CreateStreamOnHGlobal()
#include <shlwapi.h>
#include <tchar.h>
#include <windows.h>
#include <wincodec.h>

#include "../examples/unicode.h"
#include "./imageio_util.h"
#include "./metadata.h"
#include "webp/encode.h"

#define IFS(fn)                                                     \
  do {                                                              \
    if (SUCCEEDED(hr)) {                                            \
      hr = (fn);                                                    \
      if (FAILED(hr)) fprintf(stderr, #fn " failed %08lx\n", hr);   \
    }                                                               \
  } while (0)

// modified version of DEFINE_GUID from guiddef.h.
#define WEBP_DEFINE_GUID(name, l, w1, w2, b1, b2, b3, b4, b5, b6, b7, b8) \
  static const GUID name = \
      { l, w1, w2, { b1, b2,  b3,  b4,  b5,  b6,  b7,  b8 } }

#ifdef __cplusplus
#define MAKE_REFGUID(x) (x)
#else
#define MAKE_REFGUID(x) &(x)
#endif

typedef struct WICFormatImporter {
  const GUID* pixel_format;
  int bytes_per_pixel;
  int (*import)(WebPPicture* const, const uint8_t* const, int);
} WICFormatImporter;

// From Microsoft SDK 7.0a -- wincodec.h
// Create local copies for compatibility when building against earlier
// versions of the SDK.
WEBP_DEFINE_GUID(GUID_WICPixelFormat24bppBGR_,
                 0x6fddc324, 0x4e03, 0x4bfe,
                 0xb1, 0x85, 0x3d, 0x77, 0x76, 0x8d, 0xc9, 0x0c);
WEBP_DEFINE_GUID(GUID_WICPixelFormat24bppRGB_,
                 0x6fddc324, 0x4e03, 0x4bfe,
                 0xb1, 0x85, 0x3d, 0x77, 0x76, 0x8d, 0xc9, 0x0d);
WEBP_DEFINE_GUID(GUID_WICPixelFormat32bppBGRA_,
                 0x6fddc324, 0x4e03, 0x4bfe,
                 0xb1, 0x85, 0x3d, 0x77, 0x76, 0x8d, 0xc9, 0x0f);
WEBP_DEFINE_GUID(GUID_WICPixelFormat32bppRGBA_,
                 0xf5c7ad2d, 0x6a8d, 0x43dd,
                 0xa7, 0xa8, 0xa2, 0x99, 0x35, 0x26, 0x1a, 0xe9);
WEBP_DEFINE_GUID(GUID_WICPixelFormat64bppBGRA_,
                 0x1562ff7c, 0xd352, 0x46f9,
                 0x97, 0x9e, 0x42, 0x97, 0x6b, 0x79, 0x22, 0x46);
WEBP_DEFINE_GUID(GUID_WICPixelFormat64bppRGBA_,
                 0x6fddc324, 0x4e03, 0x4bfe,
                 0xb1, 0x85, 0x3d, 0x77, 0x76, 0x8d, 0xc9, 0x16);

static HRESULT OpenInputStream(const char* filename, IStream** stream) {
  HRESULT hr = S_OK;
  if (!WSTRCMP(filename, "-")) {
    const uint8_t* data = NULL;
    size_t data_size = 0;
    const int ok = ImgIoUtilReadFile(filename, &data, &data_size);
    if (ok) {
      HGLOBAL image = GlobalAlloc(GMEM_MOVEABLE, data_size);
      if (image != NULL) {
        void* const image_mem = GlobalLock(image);
        if (image_mem != NULL) {
          memcpy(image_mem, data, data_size);
          GlobalUnlock(image);
          IFS(CreateStreamOnHGlobal(image, TRUE, stream));
        } else {
          hr = E_FAIL;
        }
      } else {
        hr = E_OUTOFMEMORY;
      }
      free((void*)data);
    } else {
      hr = E_FAIL;
    }
  } else {
    IFS(SHCreateStreamOnFile((const LPTSTR)filename, STGM_READ, stream));
  }

  if (FAILED(hr)) {
    _ftprintf(stderr, _T("Error opening input file %s (%08lx)\n"),
              (const LPTSTR)filename, hr);
  }
  return hr;
}

// -----------------------------------------------------------------------------
// Metadata processing

// Stores the first non-zero sized color profile from 'frame' to 'iccp'.
// Returns an HRESULT to indicate success or failure. The caller is responsible
// for freeing 'iccp->bytes' in either case.
static HRESULT ExtractICCP(IWICImagingFactory* const factory,
                           IWICBitmapFrameDecode* const frame,
                           MetadataPayload* const iccp) {
  HRESULT hr = S_OK;
  UINT i, count;
  IWICColorContext** color_contexts;

  IFS(IWICBitmapFrameDecode_GetColorContexts(frame, 0, NULL, &count));
  if (FAILED(hr) || count == 0) {
    // Treat unsupported operation as a non-fatal error. See crbug.com/webp/506.
    return (hr == WINCODEC_ERR_UNSUPPORTEDOPERATION) ? S_OK : hr;
  }

  color_contexts = (IWICColorContext**)calloc(count, sizeof(*color_contexts));
  if (color_contexts == NULL) return E_OUTOFMEMORY;
  for (i = 0; SUCCEEDED(hr) && i < count; ++i) {
    IFS(IWICImagingFactory_CreateColorContext(factory, &color_contexts[i]));
  }

  if (SUCCEEDED(hr)) {
    UINT num_color_contexts;
    IFS(IWICBitmapFrameDecode_GetColorContexts(frame,
                                               count, color_contexts,
                                               &num_color_contexts));
    assert(FAILED(hr) || num_color_contexts <= count);
    for (i = 0; SUCCEEDED(hr) && i < num_color_contexts; ++i) {
      WICColorContextType type;
      IFS(IWICColorContext_GetType(color_contexts[i], &type));
      if (SUCCEEDED(hr) && type == WICColorContextProfile) {
        UINT size;
        IFS(IWICColorContext_GetProfileBytes(color_contexts[i],
                                             0, NULL, &size));
        if (SUCCEEDED(hr) && size > 0) {
          iccp->bytes = (uint8_t*)malloc(size);
          if (iccp->bytes == NULL) {
            hr = E_OUTOFMEMORY;
            break;
          }
          iccp->size = size;
          IFS(IWICColorContext_GetProfileBytes(color_contexts[i],
                                               (UINT)iccp->size, iccp->bytes,
                                               &size));
          if (SUCCEEDED(hr) && size != iccp->size) {
            fprintf(stderr, "Warning! ICC profile size (%u) != expected (%u)\n",
                    size, (uint32_t)iccp->size);
            iccp->size = size;
          }
          break;
        }
      }
    }
  }
  for (i = 0; i < count; ++i) {
    if (color_contexts[i] != NULL) IUnknown_Release(color_contexts[i]);
  }
  free(color_contexts);
  return hr;
}

static HRESULT ExtractMetadata(IWICImagingFactory* const factory,
                               IWICBitmapFrameDecode* const frame,
                               Metadata* const metadata) {
  // TODO(jzern): add XMP/EXIF extraction.
  const HRESULT hr = ExtractICCP(factory, frame, &metadata->iccp);
  if (FAILED(hr)) MetadataFree(metadata);
  return hr;
}

// -----------------------------------------------------------------------------

static int HasPalette(GUID pixel_format) {
  return (IsEqualGUID(MAKE_REFGUID(pixel_format),
                      MAKE_REFGUID(GUID_WICPixelFormat1bppIndexed)) ||
          IsEqualGUID(MAKE_REFGUID(pixel_format),
                      MAKE_REFGUID(GUID_WICPixelFormat2bppIndexed)) ||
          IsEqualGUID(MAKE_REFGUID(pixel_format),
                      MAKE_REFGUID(GUID_WICPixelFormat4bppIndexed)) ||
          IsEqualGUID(MAKE_REFGUID(pixel_format),
                      MAKE_REFGUID(GUID_WICPixelFormat8bppIndexed)));
}

static int HasAlpha(IWICImagingFactory* const factory,
                    IWICBitmapDecoder* const decoder,
                    IWICBitmapFrameDecode* const frame,
                    GUID pixel_format) {
  int has_alpha;
  if (HasPalette(pixel_format)) {
    IWICPalette* frame_palette = NULL;
    IWICPalette* global_palette = NULL;
    BOOL frame_palette_has_alpha = FALSE;
    BOOL global_palette_has_alpha = FALSE;

    // A palette may exist at the frame or container level,
    // check IWICPalette::HasAlpha() for both if present.
    if (SUCCEEDED(IWICImagingFactory_CreatePalette(factory, &frame_palette)) &&
        SUCCEEDED(IWICBitmapFrameDecode_CopyPalette(frame, frame_palette))) {
      IWICPalette_HasAlpha(frame_palette, &frame_palette_has_alpha);
    }
    if (SUCCEEDED(IWICImagingFactory_CreatePalette(factory, &global_palette)) &&
        SUCCEEDED(IWICBitmapDecoder_CopyPalette(decoder, global_palette))) {
      IWICPalette_HasAlpha(global_palette, &global_palette_has_alpha);
    }
    has_alpha = frame_palette_has_alpha || global_palette_has_alpha;

    if (frame_palette != NULL) IUnknown_Release(frame_palette);
    if (global_palette != NULL) IUnknown_Release(global_palette);
  } else {
    has_alpha = IsEqualGUID(MAKE_REFGUID(pixel_format),
                            MAKE_REFGUID(GUID_WICPixelFormat32bppRGBA_)) ||
                IsEqualGUID(MAKE_REFGUID(pixel_format),
                            MAKE_REFGUID(GUID_WICPixelFormat32bppBGRA_)) ||
                IsEqualGUID(MAKE_REFGUID(pixel_format),
                            MAKE_REFGUID(GUID_WICPixelFormat64bppRGBA_)) ||
                IsEqualGUID(MAKE_REFGUID(pixel_format),
                            MAKE_REFGUID(GUID_WICPixelFormat64bppBGRA_));
  }
  return has_alpha;
}

int ReadPictureWithWIC(const char* const filename,
                       WebPPicture* const pic, int keep_alpha,
                       Metadata* const metadata) {
  // From Microsoft SDK 6.0a -- ks.h
  // Define a local copy to avoid link errors under mingw.
  WEBP_DEFINE_GUID(GUID_NULL_, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  static const WICFormatImporter kAlphaFormatImporters[] = {
    { &GUID_WICPixelFormat32bppBGRA_, 4, WebPPictureImportBGRA },
    { &GUID_WICPixelFormat32bppRGBA_, 4, WebPPictureImportRGBA },
    { NULL, 0, NULL },
  };
  static const WICFormatImporter kNonAlphaFormatImporters[] = {
    { &GUID_WICPixelFormat24bppBGR_, 3, WebPPictureImportBGR },
    { &GUID_WICPixelFormat24bppRGB_, 3, WebPPictureImportRGB },
    { NULL, 0, NULL },
  };
  HRESULT hr = S_OK;
  IWICBitmapFrameDecode* frame = NULL;
  IWICFormatConverter* converter = NULL;
  IWICImagingFactory* factory = NULL;
  IWICBitmapDecoder* decoder = NULL;
  IStream* stream = NULL;
  UINT frame_count = 0;
  UINT width = 0, height = 0;
  BYTE* rgb = NULL;
  WICPixelFormatGUID src_pixel_format = GUID_WICPixelFormatUndefined;
  const WICFormatImporter* importer = NULL;
  GUID src_container_format = GUID_NULL_;
  // From Windows Kits\10\Include\10.0.19041.0\um\wincodec.h
  WEBP_DEFINE_GUID(GUID_ContainerFormatWebp_,
                   0xe094b0e2, 0x67f2, 0x45b3,
                   0xb0, 0xea, 0x11, 0x53, 0x37, 0xca, 0x7c, 0xf3);
  static const GUID* kAlphaContainers[] = {
    &GUID_ContainerFormatBmp,
    &GUID_ContainerFormatPng,
    &GUID_ContainerFormatTiff,
    &GUID_ContainerFormatWebp_,
    NULL
  };
  int has_alpha = 0;
  int64_t stride;

  if (filename == NULL || pic == NULL) return 0;

  IFS(CoInitialize(NULL));
  IFS(CoCreateInstance(MAKE_REFGUID(CLSID_WICImagingFactory), NULL,
                       CLSCTX_INPROC_SERVER,
                       MAKE_REFGUID(IID_IWICImagingFactory),
                       (LPVOID*)&factory));
  if (hr == REGDB_E_CLASSNOTREG) {
    fprintf(stderr,
            "Couldn't access Windows Imaging Component (are you running "
            "Windows XP SP3 or newer?). Most formats not available. "
            "Use -s for the available YUV input.\n");
  }
  // Prepare for image decoding.
  IFS(OpenInputStream(filename, &stream));
  IFS(IWICImagingFactory_CreateDecoderFromStream(
          factory, stream, NULL,
          WICDecodeMetadataCacheOnDemand, &decoder));
  IFS(IWICBitmapDecoder_GetFrameCount(decoder, &frame_count));
  if (SUCCEEDED(hr)) {
    if (frame_count == 0) {
      fprintf(stderr, "No frame found in input file.\n");
      hr = E_FAIL;
    } else if (frame_count > 1) {
      // WIC will be tried before native WebP decoding so avoid duplicating the
      // error message.
      hr = E_FAIL;
    }
  }
  IFS(IWICBitmapDecoder_GetFrame(decoder, 0, &frame));
  IFS(IWICBitmapFrameDecode_GetPixelFormat(frame, &src_pixel_format));
  IFS(IWICBitmapDecoder_GetContainerFormat(decoder, &src_container_format));

  if (SUCCEEDED(hr) && keep_alpha) {
    const GUID** guid;
    for (guid = kAlphaContainers; *guid != NULL; ++guid) {
      if (IsEqualGUID(MAKE_REFGUID(src_container_format),
                      MAKE_REFGUID(**guid))) {
        has_alpha = HasAlpha(factory, decoder, frame, src_pixel_format);
        break;
      }
    }
  }

  // Prepare for pixel format conversion (if necessary).
  IFS(IWICImagingFactory_CreateFormatConverter(factory, &converter));

  for (importer = has_alpha ? kAlphaFormatImporters : kNonAlphaFormatImporters;
       hr == S_OK && importer->import != NULL; ++importer) {
    BOOL can_convert;
    const HRESULT cchr = IWICFormatConverter_CanConvert(
        converter,
        MAKE_REFGUID(src_pixel_format),
        MAKE_REFGUID(*importer->pixel_format),
        &can_convert);
    if (SUCCEEDED(cchr) && can_convert) break;
  }
  if (importer->import == NULL) hr = E_FAIL;

  IFS(IWICFormatConverter_Initialize(converter, (IWICBitmapSource*)frame,
                                     importer->pixel_format,
                                     WICBitmapDitherTypeNone,
                                     NULL, 0.0, WICBitmapPaletteTypeCustom));

  // Decode.
  IFS(IWICFormatConverter_GetSize(converter, &width, &height));
  stride = (int64_t)importer->bytes_per_pixel * width * sizeof(*rgb);
  if (stride != (int)stride ||
      !ImgIoUtilCheckSizeArgumentsOverflow(stride, height)) {
    hr = E_FAIL;
  }

  if (SUCCEEDED(hr)) {
    rgb = (BYTE*)malloc((size_t)stride * height);
    if (rgb == NULL)
      hr = E_OUTOFMEMORY;
  }
  IFS(IWICFormatConverter_CopyPixels(converter, NULL,
                                     (UINT)stride, (UINT)stride * height, rgb));

  // WebP conversion.
  if (SUCCEEDED(hr)) {
    int ok;
    pic->width = width;
    pic->height = height;
    pic->use_argb = 1;    // For WIC, we always force to argb
    ok = importer->import(pic, rgb, (int)stride);
    if (!ok) hr = E_FAIL;
  }
  if (SUCCEEDED(hr)) {
    if (metadata != NULL) {
      hr = ExtractMetadata(factory, frame, metadata);
      if (FAILED(hr)) {
        fprintf(stderr, "Error extracting image metadata using WIC!\n");
      }
    }
  }

  // Cleanup.
  if (converter != NULL) IUnknown_Release(converter);
  if (frame != NULL) IUnknown_Release(frame);
  if (decoder != NULL) IUnknown_Release(decoder);
  if (factory != NULL) IUnknown_Release(factory);
  if (stream != NULL) IUnknown_Release(stream);
  free(rgb);
  return SUCCEEDED(hr);
}
#else  // !HAVE_WINCODEC_H
int ReadPictureWithWIC(const char* const filename,
                       struct WebPPicture* const pic, int keep_alpha,
                       struct Metadata* const metadata) {
  (void)filename;
  (void)pic;
  (void)keep_alpha;
  (void)metadata;
  fprintf(stderr, "Windows Imaging Component (WIC) support not compiled. "
                  "Visual Studio and mingw-w64 builds support WIC. Make sure "
                  "wincodec.h detection is working correctly if using autoconf "
                  "and HAVE_WINCODEC_H is defined before building.\n");
  return 0;
}
#endif  // HAVE_WINCODEC_H

// -----------------------------------------------------------------------------

// Copyright 2012 Google Inc. All Rights Reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the COPYING file in the root of the source
// tree. An additional intellectual property rights grant can be found
// in the file PATENTS. All contributing project authors may
// be found in the AUTHORS file in the root of the source tree.
// -----------------------------------------------------------------------------
//
//  Metadata types and functions.
//

#ifndef WEBP_IMAGEIO_METADATA_H_
#define WEBP_IMAGEIO_METADATA_H_

#include "../src/webp/types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MetadataPayload {
  uint8_t* bytes;
  size_t size;
} MetadataPayload;

typedef struct Metadata {
  MetadataPayload exif;
  MetadataPayload iccp;
  MetadataPayload xmp;
} Metadata;

#define METADATA_OFFSET(x) offsetof(Metadata, x)

void MetadataInit(Metadata* const metadata);
void MetadataPayloadDelete(MetadataPayload* const payload);
void MetadataFree(Metadata* const metadata);

// Stores 'metadata' to 'payload->bytes', returns false on allocation error.
int MetadataCopy(const char* metadata, size_t metadata_len,
                 MetadataPayload* const payload);

#ifdef __cplusplus
}    // extern "C"
#endif

#endif  // WEBP_IMAGEIO_METADATA_H_

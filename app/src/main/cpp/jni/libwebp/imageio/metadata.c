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

#include "./metadata.h"

#include <stdlib.h>
#include <string.h>

#include "../src/webp/types.h"

void MetadataInit(Metadata* const metadata) {
  if (metadata == NULL) return;
  memset(metadata, 0, sizeof(*metadata));
}

void MetadataPayloadDelete(MetadataPayload* const payload) {
  if (payload == NULL) return;
  free(payload->bytes);
  payload->bytes = NULL;
  payload->size = 0;
}

void MetadataFree(Metadata* const metadata) {
  if (metadata == NULL) return;
  MetadataPayloadDelete(&metadata->exif);
  MetadataPayloadDelete(&metadata->iccp);
  MetadataPayloadDelete(&metadata->xmp);
}

int MetadataCopy(const char* metadata, size_t metadata_len,
                 MetadataPayload* const payload) {
  if (metadata == NULL || metadata_len == 0 || payload == NULL) return 0;
  payload->bytes = (uint8_t*)malloc(metadata_len);
  if (payload->bytes == NULL) return 0;
  payload->size = metadata_len;
  memcpy(payload->bytes, metadata, metadata_len);
  return 1;
}

// -----------------------------------------------------------------------------

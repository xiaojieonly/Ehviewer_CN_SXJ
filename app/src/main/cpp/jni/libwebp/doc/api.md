# WebP APIs

## Encoding API

The main encoding functions are available in the header src/webp/encode.h

The ready-to-use ones are:

```c
size_t WebPEncodeRGB(const uint8_t* rgb, int width, int height, int stride,
                     float quality_factor, uint8_t** output);
size_t WebPEncodeBGR(const uint8_t* bgr, int width, int height, int stride,
                     float quality_factor, uint8_t** output);
size_t WebPEncodeRGBA(const uint8_t* rgba, int width, int height, int stride,
                      float quality_factor, uint8_t** output);
size_t WebPEncodeBGRA(const uint8_t* bgra, int width, int height, int stride,
                      float quality_factor, uint8_t** output);
```

They will convert raw RGB samples to a WebP data. The only control supplied is
the quality factor.

There are some variants for using the lossless format:

```c
size_t WebPEncodeLosslessRGB(const uint8_t* rgb, int width, int height,
                             int stride, uint8_t** output);
size_t WebPEncodeLosslessBGR(const uint8_t* bgr, int width, int height,
                             int stride, uint8_t** output);
size_t WebPEncodeLosslessRGBA(const uint8_t* rgba, int width, int height,
                              int stride, uint8_t** output);
size_t WebPEncodeLosslessBGRA(const uint8_t* bgra, int width, int height,
                              int stride, uint8_t** output);
```

Of course in this case, no quality factor is needed since the compression occurs
without loss of the input values, at the expense of larger output sizes.

### Advanced encoding API

A more advanced API is based on the WebPConfig and WebPPicture structures.

WebPConfig contains the encoding settings and is not tied to a particular
picture. WebPPicture contains input data, on which some WebPConfig will be used
for compression. The encoding flow looks like:

```c
#include <webp/encode.h>

// Setup a config, starting form a preset and tuning some additional
// parameters
WebPConfig config;
if (!WebPConfigPreset(&config, WEBP_PRESET_PHOTO, quality_factor)) {
  return 0;   // version error
}
// ... additional tuning
config.sns_strength = 90;
config.filter_sharpness = 6;
config_error = WebPValidateConfig(&config);  // not mandatory, but useful

// Setup the input data
WebPPicture pic;
if (!WebPPictureInit(&pic)) {
  return 0;  // version error
}
pic.width = width;
pic.height = height;
// allocated picture of dimension width x height
if (!WebPPictureAlloc(&pic)) {
  return 0;   // memory error
}
// at this point, 'pic' has been initialized as a container,
// and can receive the Y/U/V samples.
// Alternatively, one could use ready-made import functions like
// WebPPictureImportRGB(), which will take care of memory allocation.
// In any case, past this point, one will have to call
// WebPPictureFree(&pic) to reclaim memory.

// Set up a byte-output write method. WebPMemoryWriter, for instance.
WebPMemoryWriter wrt;
WebPMemoryWriterInit(&wrt);     // initialize 'wrt'

pic.writer = MyFileWriter;
pic.custom_ptr = my_opaque_structure_to_make_MyFileWriter_work;

// Compress!
int ok = WebPEncode(&config, &pic);   // ok = 0 => error occurred!
WebPPictureFree(&pic);  // must be called independently of the 'ok' result.

// output data should have been handled by the writer at that point.
// -> compressed data is the memory buffer described by wrt.mem / wrt.size

// deallocate the memory used by compressed data
WebPMemoryWriterClear(&wrt);
```

## Decoding API

This is mainly just one function to call:

```c
#include "webp/decode.h"
uint8_t* WebPDecodeRGB(const uint8_t* data, size_t data_size,
                       int* width, int* height);
```

Please have a look at the file src/webp/decode.h for the details. There are
variants for decoding in BGR/RGBA/ARGB/BGRA order, along with decoding to raw
Y'CbCr samples. One can also decode the image directly into a pre-allocated
buffer.

To detect a WebP file and gather the picture's dimensions, the function:

```c
int WebPGetInfo(const uint8_t* data, size_t data_size,
                int* width, int* height);
```

is supplied. No decoding is involved when using it.

### Incremental decoding API

In the case when data is being progressively transmitted, pictures can still be
incrementally decoded using a slightly more complicated API. Decoder state is
stored into an instance of the WebPIDecoder object. This object can be created
with the purpose of decoding either RGB or Y'CbCr samples. For instance:

```c
WebPDecBuffer buffer;
WebPInitDecBuffer(&buffer);
buffer.colorspace = MODE_BGR;
...
WebPIDecoder* idec = WebPINewDecoder(&buffer);
```

As data is made progressively available, this incremental-decoder object can be
used to decode the picture further. There are two (mutually exclusive) ways to
pass freshly arrived data:

either by appending the fresh bytes:

```c
WebPIAppend(idec, fresh_data, size_of_fresh_data);
```

or by just mentioning the new size of the transmitted data:

```c
WebPIUpdate(idec, buffer, size_of_transmitted_buffer);
```

Note that 'buffer' can be modified between each call to WebPIUpdate, in
particular when the buffer is resized to accommodate larger data.

These functions will return the decoding status: either VP8_STATUS_SUSPENDED if
decoding is not finished yet or VP8_STATUS_OK when decoding is done. Any other
status is an error condition.

The 'idec' object must always be released (even upon an error condition) by
calling: WebPIDelete(idec).

To retrieve partially decoded picture samples, one must use the corresponding
method: WebPIDecGetRGB or WebPIDecGetYUVA. It will return the last displayable
pixel row.

Lastly, note that decoding can also be performed into a pre-allocated pixel
buffer. This buffer must be passed when creating a WebPIDecoder, calling
WebPINewRGB() or WebPINewYUVA().

Please have a look at the src/webp/decode.h header for further details.

### Advanced Decoding API

WebP decoding supports an advanced API which provides on-the-fly cropping and
rescaling, something of great usefulness on memory-constrained environments like
mobile phones. Basically, the memory usage will scale with the output's size,
not the input's, when one only needs a quick preview or a zoomed in portion of
an otherwise too-large picture. Some CPU can be saved too, incidentally.

```c
// A) Init a configuration object
WebPDecoderConfig config;
CHECK(WebPInitDecoderConfig(&config));

// B) optional: retrieve the bitstream's features.
CHECK(WebPGetFeatures(data, data_size, &config.input) == VP8_STATUS_OK);

// C) Adjust 'config' options, if needed
config.options.no_fancy_upsampling = 1;
config.options.use_scaling = 1;
config.options.scaled_width = scaledWidth();
config.options.scaled_height = scaledHeight();
// etc.

// D) Specify 'config' output options for specifying output colorspace.
// Optionally the external image decode buffer can also be specified.
config.output.colorspace = MODE_BGRA;
// Optionally, the config.output can be pointed to an external buffer as
// well for decoding the image. This externally supplied memory buffer
// should be big enough to store the decoded picture.
config.output.u.RGBA.rgba = (uint8_t*) memory_buffer;
config.output.u.RGBA.stride = scanline_stride;
config.output.u.RGBA.size = total_size_of_the_memory_buffer;
config.output.is_external_memory = 1;

// E) Decode the WebP image. There are two variants w.r.t decoding image.
// The first one (E.1) decodes the full image and the second one (E.2) is
// used to incrementally decode the image using small input buffers.
// Any one of these steps can be used to decode the WebP image.

// E.1) Decode full image.
CHECK(WebPDecode(data, data_size, &config) == VP8_STATUS_OK);

// E.2) Decode image incrementally.
WebPIDecoder* const idec = WebPIDecode(NULL, NULL, &config);
CHECK(idec != NULL);
while (bytes_remaining > 0) {
  VP8StatusCode status = WebPIAppend(idec, input, bytes_read);
  if (status == VP8_STATUS_OK || status == VP8_STATUS_SUSPENDED) {
    bytes_remaining -= bytes_read;
  } else {
    break;
  }
}
WebPIDelete(idec);

// F) Decoded image is now in config.output (and config.output.u.RGBA).
// It can be saved, displayed or otherwise processed.

// G) Reclaim memory allocated in config's object. It's safe to call
// this function even if the memory is external and wasn't allocated
// by WebPDecode().
WebPFreeDecBuffer(&config.output);
```

## WebP Mux

WebPMux is a set of two libraries 'Mux' and 'Demux' for creation, extraction and
manipulation of an extended format WebP file, which can have features like color
profile, metadata and animation. Reference command-line tools `webpmux` and
`vwebp` as well as the WebP container specification
'doc/webp-container-spec.txt' are also provided in this package, see the
[tools documentation](tools.md).

### Mux API

The Mux API contains methods for adding data to and reading data from WebP
files. This API currently supports XMP/EXIF metadata, ICC profile and animation.
Other features may be added in subsequent releases.

Example#1 (pseudo code): Creating a WebPMux object with image data, color
profile and XMP metadata.

```c
int copy_data = 0;
WebPMux* mux = WebPMuxNew();
// ... (Prepare image data).
WebPMuxSetImage(mux, &image, copy_data);
// ... (Prepare ICC profile data).
WebPMuxSetChunk(mux, "ICCP", &icc_profile, copy_data);
// ... (Prepare XMP metadata).
WebPMuxSetChunk(mux, "XMP ", &xmp, copy_data);
// Get data from mux in WebP RIFF format.
WebPMuxAssemble(mux, &output_data);
WebPMuxDelete(mux);
// ... (Consume output_data; e.g. write output_data.bytes to file).
WebPDataClear(&output_data);
```

Example#2 (pseudo code): Get image and color profile data from a WebP file.

```c
int copy_data = 0;
// ... (Read data from file).
WebPMux* mux = WebPMuxCreate(&data, copy_data);
WebPMuxGetFrame(mux, 1, &image);
// ... (Consume image; e.g. call WebPDecode() to decode the data).
WebPMuxGetChunk(mux, "ICCP", &icc_profile);
// ... (Consume icc_profile).
WebPMuxDelete(mux);
free(data);
```

For a detailed Mux API reference, please refer to the header file
(src/webp/mux.h).

### Demux API

The Demux API enables extraction of images and extended format data from WebP
files. This API currently supports reading of XMP/EXIF metadata, ICC profile and
animated images. Other features may be added in subsequent releases.

Code example: Demuxing WebP data to extract all the frames, ICC profile and
EXIF/XMP metadata.

```c
WebPDemuxer* demux = WebPDemux(&webp_data);
uint32_t width = WebPDemuxGetI(demux, WEBP_FF_CANVAS_WIDTH);
uint32_t height = WebPDemuxGetI(demux, WEBP_FF_CANVAS_HEIGHT);
// ... (Get information about the features present in the WebP file).
uint32_t flags = WebPDemuxGetI(demux, WEBP_FF_FORMAT_FLAGS);

// ... (Iterate over all frames).
WebPIterator iter;
if (WebPDemuxGetFrame(demux, 1, &iter)) {
  do {
    // ... (Consume 'iter'; e.g. Decode 'iter.fragment' with WebPDecode(),
    // ... and get other frame properties like width, height, offsets etc.
    // ... see 'struct WebPIterator' below for more info).
  } while (WebPDemuxNextFrame(&iter));
  WebPDemuxReleaseIterator(&iter);
}

// ... (Extract metadata).
WebPChunkIterator chunk_iter;
if (flags & ICCP_FLAG) WebPDemuxGetChunk(demux, "ICCP", 1, &chunk_iter);
// ... (Consume the ICC profile in 'chunk_iter.chunk').
WebPDemuxReleaseChunkIterator(&chunk_iter);
if (flags & EXIF_FLAG) WebPDemuxGetChunk(demux, "EXIF", 1, &chunk_iter);
// ... (Consume the EXIF metadata in 'chunk_iter.chunk').
WebPDemuxReleaseChunkIterator(&chunk_iter);
if (flags & XMP_FLAG) WebPDemuxGetChunk(demux, "XMP ", 1, &chunk_iter);
// ... (Consume the XMP metadata in 'chunk_iter.chunk').
WebPDemuxReleaseChunkIterator(&chunk_iter);
WebPDemuxDelete(demux);
```

For a detailed Demux API reference, please refer to the header file
(src/webp/demux.h).

## AnimEncoder API

The AnimEncoder API can be used to create animated WebP images.

Code example:

```c
WebPAnimEncoderOptions enc_options;
WebPAnimEncoderOptionsInit(&enc_options);
// ... (Tune 'enc_options' as needed).
WebPAnimEncoder* enc = WebPAnimEncoderNew(width, height, &enc_options);
while(<there are more frames>) {
  WebPConfig config;
  WebPConfigInit(&config);
  // ... (Tune 'config' as needed).
  WebPAnimEncoderAdd(enc, frame, duration, &config);
}
WebPAnimEncoderAssemble(enc, webp_data);
WebPAnimEncoderDelete(enc);
// ... (Write the 'webp_data' to a file, or re-mux it further).
```

For a detailed AnimEncoder API reference, please refer to the header file
(src/webp/mux.h).

## AnimDecoder API

This AnimDecoder API allows decoding (possibly) animated WebP images.

Code Example:

```c
WebPAnimDecoderOptions dec_options;
WebPAnimDecoderOptionsInit(&dec_options);
// Tune 'dec_options' as needed.
WebPAnimDecoder* dec = WebPAnimDecoderNew(webp_data, &dec_options);
WebPAnimInfo anim_info;
WebPAnimDecoderGetInfo(dec, &anim_info);
for (uint32_t i = 0; i < anim_info.loop_count; ++i) {
  while (WebPAnimDecoderHasMoreFrames(dec)) {
    uint8_t* buf;
    int timestamp;
    WebPAnimDecoderGetNext(dec, &buf, &timestamp);
    // ... (Render 'buf' based on 'timestamp').
    // ... (Do NOT free 'buf', as it is owned by 'dec').
  }
  WebPAnimDecoderReset(dec);
}
const WebPDemuxer* demuxer = WebPAnimDecoderGetDemuxer(dec);
// ... (Do something using 'demuxer'; e.g. get EXIF/XMP/ICC data).
WebPAnimDecoderDelete(dec);
```

For a detailed AnimDecoder API reference, please refer to the header file
(src/webp/demux.h).

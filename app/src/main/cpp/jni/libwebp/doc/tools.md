# WebP tools

## Encoding tool

The examples/ directory contains tools for encoding (cwebp) and decoding (dwebp)
images.

The easiest use should look like:

```shell
cwebp input.png -q 80 -o output.webp
```

which will convert the input file to a WebP file using a quality factor of 80 on
a 0->100 scale (0 being the lowest quality, 100 being the best. Default value is
75).

You might want to try the `-lossless` flag too, which will compress the source
(in RGBA format) without any loss. The `-q` quality parameter will in this case
control the amount of processing time spent trying to make the output file as
small as possible.

A longer list of options is available using the `-longhelp` command line flag:

```shell
> cwebp -longhelp
Usage:
 cwebp [-preset <...>] [options] in_file [-o out_file]
```

If input size (-s) for an image is not specified, it is assumed to be a PNG,
JPEG, TIFF or WebP file. Note: Animated PNG and WebP files are not supported.

Options:

```
-h / -help ............. short help
-H / -longhelp ......... long help
-q <float> ............. quality factor (0:small..100:big), default=75
-alpha_q <int> ......... transparency-compression quality (0..100),
                         default=100
-preset <string> ....... preset setting, one of:
                          default, photo, picture,
                          drawing, icon, text
   -preset must come first, as it overwrites other parameters
-z <int> ............... activates lossless preset with given
                         level in [0:fast, ..., 9:slowest]

-m <int> ............... compression method (0=fast, 6=slowest), default=4
-segments <int> ........ number of segments to use (1..4), default=4
-size <int> ............ target size (in bytes)
-psnr <float> .......... target PSNR (in dB. typically: 42)

-s <int> <int> ......... input size (width x height) for YUV
-sns <int> ............. spatial noise shaping (0:off, 100:max), default=50
-f <int> ............... filter strength (0=off..100), default=60
-sharpness <int> ....... filter sharpness (0:most .. 7:least sharp), default=0
-strong ................ use strong filter instead of simple (default)
-nostrong .............. use simple filter instead of strong
-sharp_yuv ............. use sharper (and slower) RGB->YUV conversion
-partition_limit <int> . limit quality to fit the 512k limit on
                         the first partition (0=no degradation ... 100=full)
-pass <int> ............ analysis pass number (1..10)
-qrange <min> <max> .... specifies the permissible quality range
                         (default: 0 100)
-crop <x> <y> <w> <h> .. crop picture with the given rectangle
-resize <w> <h> ........ resize picture (*after* any cropping)
-mt .................... use multi-threading if available
-low_memory ............ reduce memory usage (slower encoding)
-map <int> ............. print map of extra info
-print_psnr ............ prints averaged PSNR distortion
-print_ssim ............ prints averaged SSIM distortion
-print_lsim ............ prints local-similarity distortion
-d <file.pgm> .......... dump the compressed output (PGM file)
-alpha_method <int> .... transparency-compression method (0..1), default=1
-alpha_filter <string> . predictive filtering for alpha plane,
                         one of: none, fast (default) or best
-exact ................. preserve RGB values in transparent area, default=off
-blend_alpha <hex> ..... blend colors against background color
                         expressed as RGB values written in
                         hexadecimal, e.g. 0xc0e0d0 for red=0xc0
                         green=0xe0 and blue=0xd0
-noalpha ............... discard any transparency information
-lossless .............. encode image losslessly, default=off
-near_lossless <int> ... use near-lossless image preprocessing
                         (0..100=off), default=100
-hint <string> ......... specify image characteristics hint,
                         one of: photo, picture or graph

-metadata <string> ..... comma separated list of metadata to
                         copy from the input to the output if present.
                         Valid values: all, none (default), exif, icc, xmp

-short ................. condense printed message
-quiet ................. don't print anything
-version ............... print version number and exit
-noasm ................. disable all assembly optimizations
-v ..................... verbose, e.g. print encoding/decoding times
-progress .............. report encoding progress
```

Experimental Options:

```
-jpeg_like ............. roughly match expected JPEG size
-af .................... auto-adjust filter strength
-pre <int> ............. pre-processing filter
```

The main options you might want to try in order to further tune the visual
quality are:

-preset -sns -f -m

Namely:

*   `preset` will set up a default encoding configuration targeting a particular
    type of input. It should appear first in the list of options, so that
    subsequent options can take effect on top of this preset. Default value is
    'default'.
*   `sns` will progressively turn on (when going from 0 to 100) some additional
    visual optimizations (like: segmentation map re-enforcement). This option
    will balance the bit allocation differently. It tries to take bits from the
    "easy" parts of the picture and use them in the "difficult" ones instead.
    Usually, raising the sns value (at fixed -q value) leads to larger files,
    but with better quality. Typical value is around '75'.
*   `f` option directly links to the filtering strength used by the codec's
    in-loop processing. The higher the value, the smoother the highly-compressed
    area will look. This is particularly useful when aiming at very small files.
    Typical values are around 20-30. Note that using the option
    -strong/-nostrong will change the type of filtering. Use "-f 0" to turn
    filtering off.
*   `m` controls the trade-off between encoding speed and quality. Default is 4.
    You can try -m 5 or -m 6 to explore more (time-consuming) encoding
    possibilities. A lower value will result in faster encoding at the expense
    of quality.

## Decoding tool

There is a decoding sample in examples/dwebp.c which will take a .webp file and
decode it to a PNG image file (amongst other formats). This is simply to
demonstrate the use of the API. You can verify the file test.webp decodes to
exactly the same as test_ref.ppm by using:

```shell
cd examples
./dwebp test.webp -ppm -o test.ppm
diff test.ppm test_ref.ppm
```

The full list of options is available using -h:

```shell
> dwebp -h
Usage: dwebp in_file [options] [-o out_file]
```

Decodes the WebP image file to PNG format [Default]. Note: Animated WebP files
are not supported.

Use following options to convert into alternate image formats:

```
-pam ......... save the raw RGBA samples as a color PAM
-ppm ......... save the raw RGB samples as a color PPM
-bmp ......... save as uncompressed BMP format
-tiff ........ save as uncompressed TIFF format
-pgm ......... save the raw YUV samples as a grayscale PGM
               file with IMC4 layout
-yuv ......... save the raw YUV samples in flat layout
```

Other options are:

```
-version ..... print version number and exit
-nofancy ..... don't use the fancy YUV420 upscaler
-nofilter .... disable in-loop filtering
-nodither .... disable dithering
-dither <d> .. dithering strength (in 0..100)
-alpha_dither  use alpha-plane dithering if needed
-mt .......... use multi-threading
-crop <x> <y> <w> <h> ... crop output with the given rectangle
-resize <w> <h> ......... resize output (*after* any cropping)
-flip ........ flip the output vertically
-alpha ....... only save the alpha plane
-incremental . use incremental decoding (useful for tests)
-h ........... this help message
-v ........... verbose (e.g. print encoding/decoding times)
-quiet ....... quiet mode, don't print anything
-noasm ....... disable all assembly optimizations
```

## WebP file analysis tool

`webpinfo` can be used to print out the chunk level structure and bitstream
header information of WebP files. It can also check if the files are of valid
WebP format.

Usage:

```shell
webpinfo [options] in_files
```

Note: there could be multiple input files; options must come before input files.

Options:

```
-version ........... Print version number and exit.
-quiet ............. Do not show chunk parsing information.
-diag .............. Show parsing error diagnosis.
-summary ........... Show chunk stats summary.
-bitstream_info .... Parse bitstream header.
```

## Visualization tool

There's a little self-serve visualization tool called 'vwebp' under the
examples/ directory. It uses OpenGL to open a simple drawing window and show a
decoded WebP file. It's not yet integrated in the automake build system, but you
can try to manually compile it using the recommendations below.

Usage:

```shell
vwebp in_file [options]
```

Decodes the WebP image file and visualize it using OpenGL

Options are:

```
-version ..... print version number and exit
-noicc ....... don't use the icc profile if present
-nofancy ..... don't use the fancy YUV420 upscaler
-nofilter .... disable in-loop filtering
-dither <int>  dithering strength (0..100), default=50
-noalphadither disable alpha plane dithering
-usebgcolor .. display background color
-mt .......... use multi-threading
-info ........ print info
-h ........... this help message
```

Keyboard shortcuts:

```
'c' ................ toggle use of color profile
'b' ................ toggle background color display
'i' ................ overlay file information
'd' ................ disable blending & disposal (debug)
'q' / 'Q' / ESC .... quit
```

### Building

Prerequisites:

1.  OpenGL & OpenGL Utility Toolkit (GLUT)

    Linux: `sudo apt-get install freeglut3-dev mesa-common-dev`

    Mac + Xcode: These libraries should be available in the OpenGL / GLUT
    frameworks.

    Windows: http://freeglut.sourceforge.net/index.php#download

2.  (Optional) qcms (Quick Color Management System)

    1.  Download qcms from Mozilla / Chromium:
        https://hg.mozilla.org/mozilla-central/file/0e7639e3bdfb/gfx/qcms
        https://source.chromium.org/chromium/chromium/src/+/main:third_party/qcms/;drc=d4a2f8e1ed461d8fc05ed88d1ae2dc94c9773825
    2.  Build and archive the source files as libqcms.a / qcms.lib
    3.  Update makefile.unix / Makefile.vc
        1.  Define WEBP_HAVE_QCMS
        2.  Update include / library paths to reference the qcms directory.

Build using makefile.unix / Makefile.vc:

```shell
$ make -f makefile.unix examples/vwebp
> nmake /f Makefile.vc CFG=release-static \
    ../obj/x64/release-static/bin/vwebp.exe
```

## Animation creation tool

The utility `img2webp` can turn a sequence of input images (PNG, JPEG, ...) into
an animated WebP file. It offers fine control over duration, encoding modes,
etc.

Usage:

```shell
img2webp [file_options] [[frame_options] frame_file]... [-o webp_file]
```

File-level options (only used at the start of compression):

```
-min_size ............ minimize size
-kmax <int> .......... maximum number of frame between key-frames
                        (0=only keyframes)
-kmin <int> .......... minimum number of frame between key-frames
                        (0=disable key-frames altogether)
-mixed ............... use mixed lossy/lossless automatic mode
-near_lossless <int> . use near-lossless image preprocessing
                       (0..100=off), default=100
-sharp_yuv ........... use sharper (and slower) RGB->YUV conversion
                       (lossy only)
-loop <int> .......... loop count (default: 0, = infinite loop)
-v ................... verbose mode
-h ................... this help
-version ............. print version number and exit
```

Per-frame options (only used for subsequent images input):

```
-d <int> ............. frame duration in ms (default: 100)
-lossless ............ use lossless mode (default)
-lossy ............... use lossy mode
-q <float> ........... quality
-m <int> ............. method to use
-exact, -noexact ..... preserve or alter RGB values in transparent area
                       (default: -noexact, may cause artifacts
                                 with lossy animations)
```

example: `img2webp -loop 2 in0.png -lossy in1.jpg -d 80 in2.tiff -o out.webp`

Note: if a single file name is passed as the argument, the arguments will be
tokenized from this file. The file name must not start with the character '-'.

## Animated GIF conversion

Animated GIF files can be converted to WebP files with animation using the
gif2webp utility available under examples/. The files can then be viewed using
vwebp.

Usage:

```shell
gif2webp [options] gif_file -o webp_file
```

Options:

```
-h / -help ............. this help
-lossy ................. encode image using lossy compression
-mixed ................. for each frame in the image, pick lossy
                         or lossless compression heuristically
-q <float> ............. quality factor (0:small..100:big)
-m <int> ............... compression method (0=fast, 6=slowest)
-min_size .............. minimize output size (default:off)
                         lossless compression by default; can be
                         combined with -q, -m, -lossy or -mixed
                         options
-kmin <int> ............ min distance between key frames
-kmax <int> ............ max distance between key frames
-f <int> ............... filter strength (0=off..100)
-metadata <string> ..... comma separated list of metadata to
                         copy from the input to the output if present
                         Valid values: all, none, icc, xmp (default)
-loop_compatibility .... use compatibility mode for Chrome
                         version prior to M62 (inclusive)
-mt .................... use multi-threading if available

-version ............... print version number and exit
-v ..................... verbose
-quiet ................. don't print anything
```

### Building

With the libgif development files installed, gif2webp can be built using
makefile.unix:

```shell
$ make -f makefile.unix examples/gif2webp
```

or using autoconf:

```shell
$ ./configure --enable-everything
$ make
```

## Comparison of animated images

Test utility anim_diff under examples/ can be used to compare two animated
images (each can be GIF or WebP).

Usage:

```shell
anim_diff <image1> <image2> [options]
```

Options:

```
-dump_frames <folder> dump decoded frames in PAM format
-min_psnr <float> ... minimum per-frame PSNR
-raw_comparison ..... if this flag is not used, RGB is
                      premultiplied before comparison
-max_diff <int> ..... maximum allowed difference per channel
                      between corresponding pixels in subsequent
                      frames
-h .................. this help
-version ............ print version number and exit
```

### Building

With the libgif development files installed, anim_diff can be built using
makefile.unix:

```shell
$ make -f makefile.unix examples/anim_diff
```

or using autoconf:

```shell
$ ./configure --enable-everything
$ make
```

## WebP Mux tool

The examples/ directory contains a tool (webpmux) for manipulating WebP files.
The webpmux tool can be used to create an extended format WebP file and also to
extract or strip relevant data from such a file.

A list of options is available using the -help command line flag:

```shell
> webpmux -help
Usage: webpmux -get GET_OPTIONS INPUT -o OUTPUT
       webpmux -set SET_OPTIONS INPUT -o OUTPUT
       webpmux -duration DURATION_OPTIONS [-duration ...]
               INPUT -o OUTPUT
       webpmux -strip STRIP_OPTIONS INPUT -o OUTPUT
       webpmux -frame FRAME_OPTIONS [-frame...] [-loop LOOP_COUNT]
               [-bgcolor BACKGROUND_COLOR] -o OUTPUT
       webpmux -info INPUT
       webpmux [-h|-help]
       webpmux -version
       webpmux argument_file_name

GET_OPTIONS:
 Extract relevant data:
   icc       get ICC profile
   exif      get EXIF metadata
   xmp       get XMP metadata
   frame n   get nth frame

SET_OPTIONS:
 Set color profile/metadata/parameters:
   loop LOOP_COUNT            set the loop count
   bgcolor BACKGROUND_COLOR   set the animation background color
   icc  file.icc              set ICC profile
   exif file.exif             set EXIF metadata
   xmp  file.xmp              set XMP metadata
   where:    'file.icc' contains the ICC profile to be set,
             'file.exif' contains the EXIF metadata to be set
             'file.xmp' contains the XMP metadata to be set

DURATION_OPTIONS:
 Set duration of selected frames:
   duration            set duration for all frames
   duration,frame      set duration of a particular frame
   duration,start,end  set duration of frames in the
                        interval [start,end])
   where: 'duration' is the duration in milliseconds
          'start' is the start frame index
          'end' is the inclusive end frame index
           The special 'end' value '0' means: last frame.

STRIP_OPTIONS:
 Strip color profile/metadata:
   icc       strip ICC profile
   exif      strip EXIF metadata
   xmp       strip XMP metadata

FRAME_OPTIONS(i):
 Create animation:
   file_i +di[+xi+yi[+mi[bi]]]
   where:    'file_i' is the i'th animation frame (WebP format),
             'di' is the pause duration before next frame,
             'xi','yi' specify the image offset for this frame,
             'mi' is the dispose method for this frame (0 or 1),
             'bi' is the blending method for this frame (+b or -b)

LOOP_COUNT:
 Number of times to repeat the animation.
 Valid range is 0 to 65535 [Default: 0 (infinite)].

BACKGROUND_COLOR:
 Background color of the canvas.
  A,R,G,B
  where:    'A', 'R', 'G' and 'B' are integers in the range 0 to 255 specifying
            the Alpha, Red, Green and Blue component values respectively
            [Default: 255,255,255,255]

INPUT & OUTPUT are in WebP format.

Note: The nature of EXIF, XMP and ICC data is not checked and is assumed to be
valid.

Note: if a single file name is passed as the argument, the arguments will be
tokenized from this file. The file name must not start with the character '-'.
```

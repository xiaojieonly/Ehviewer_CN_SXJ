2.0.0
=====

### Significant changes relative to 2.0 beta1:

1. The TurboJPEG API can now decompress CMYK JPEG images that have subsampled M
and Y components (not to be confused with YCCK JPEG images, in which the C/M/Y
components have been transformed into luma and chroma.)   Previously, an error
was generated ("Could not determine subsampling type for JPEG image") when such
an image was passed to `tjDecompressHeader3()`, `tjTransform()`,
`tjDecompressToYUVPlanes()`, `tjDecompressToYUV2()`, or the equivalent Java
methods.

2. Fixed an issue (CVE-2018-11813) whereby a specially-crafted malformed input
file (specifically, a file with a valid Targa header but incomplete pixel data)
would cause cjpeg to generate a JPEG file that was potentially thousands of
times larger than the input file.  The Targa reader in cjpeg was not properly
detecting that the end of the input file had been reached prematurely, so after
all valid pixels had been read from the input, the reader injected dummy pixels
with values of 255 into the JPEG compressor until the number of pixels
specified in the Targa header had been compressed.  The Targa reader in cjpeg
now behaves like the PPM reader and aborts compression if the end of the input
file is reached prematurely.  Because this issue only affected cjpeg and not
the underlying library, and because it did not involve any out-of-bounds reads
or other exploitable behaviors, it was not believed to represent a security
threat.

3. Fixed an issue whereby the `tjLoadImage()` and `tjSaveImage()` functions
would produce a "Bogus message code" error message if the underlying bitmap and
PPM readers/writers threw an error that was specific to the readers/writers
(as opposed to a general libjpeg API error.)

4. Fixed an issue whereby a specially-crafted malformed BMP file, one in which
the header specified an image width of 1073741824 pixels, would trigger a
floating point exception (division by zero) in the `tjLoadImage()` function
when attempting to load the BMP file into a 4-component image buffer.

5. Fixed an issue whereby certain combinations of calls to
`jpeg_skip_scanlines()` and `jpeg_read_scanlines()` could trigger an infinite
loop when decompressing progressive JPEG images that use vertical chroma
subsampling (for instance, 4:2:0 or 4:4:0.)

6. Fixed a segfault in `jpeg_skip_scanlines()` that occurred when decompressing
a 4:2:2 or 4:2:0 JPEG image using the merged (non-fancy) upsampling algorithms
(that is, when setting `cinfo.do_fancy_upsampling` to `FALSE`.)

7. The new CMake-based build system will now disable the MIPS DSPr2 SIMD
extensions if it detects that the compiler does not support DSPr2 instructions.

8. Fixed out-of-bounds read in cjpeg that occurred when attempting to compress
a specially-crafted malformed color-index (8-bit-per-sample) BMP file in which
some of the samples (color indices) exceeded the bounds of the BMP file's color
table.

9. Fixed a signed integer overflow in the progressive Huffman decoder, detected
by the Clang and GCC undefined behavior sanitizers, that could be triggered by
attempting to decompress a specially-crafted malformed JPEG image.  This issue
did not pose a security threat, but removing the warning made it easier to
detect actual security issues, should they arise in the future.


1.5.90 (2.0 beta1)
==================

### Significant changes relative to 1.5.3:

1. Added AVX2 SIMD implementations of the colorspace conversion, chroma
downsampling and upsampling, integer quantization and sample conversion, and
slow integer DCT/IDCT algorithms.  When using the slow integer DCT/IDCT
algorithms on AVX2-equipped CPUs, the compression of RGB images is
approximately 13-36% (avg. 22%) faster (relative to libjpeg-turbo 1.5.x) with
64-bit code and 11-21% (avg. 17%) faster with 32-bit code, and the
decompression of RGB images is approximately 9-35% (avg. 17%) faster with
64-bit code and 7-17% (avg. 12%) faster with 32-bit code.  (As tested on a
3 GHz Intel Core i7.  Actual mileage may vary.)

2. Overhauled the build system to use CMake on all platforms, and removed the
autotools-based build system.  This decision resulted from extensive
discussions within the libjpeg-turbo community.  libjpeg-turbo traditionally
used CMake only for Windows builds, but there was an increasing amount of
demand to extend CMake support to other platforms.  However, because of the
unique nature of our code base (the need to support different assemblers on
each platform, the need for Java support, etc.), providing dual build systems
as other OSS imaging libraries do (including libpng and libtiff) would have
created a maintenance burden.  The use of CMake greatly simplifies some aspects
of our build system, owing to CMake's built-in support for various assemblers,
Java, and unit testing, as well as generally fewer quirks that have to be
worked around in order to implement our packaging system.  Eliminating
autotools puts our project slightly at odds with the traditional practices of
the OSS community, since most "system libraries" tend to be built with
autotools, but it is believed that the benefits of this move outweigh the
risks.  In addition to providing a unified build environment, switching to
CMake allows for the use of various build tools and IDEs that aren't supported
under autotools, including XCode, Ninja, and Eclipse.  It also eliminates the
need to install autotools via MacPorts/Homebrew on OS X and allows
libjpeg-turbo to be configured without the use of a terminal/command prompt.
Extensive testing was conducted to ensure that all features provided by the
autotools-based build system are provided by the new build system.

3. The libjpeg API in this version of libjpeg-turbo now includes two additional
functions, `jpeg_read_icc_profile()` and `jpeg_write_icc_profile()`, that can
be used to extract ICC profile data from a JPEG file while decompressing or to
embed ICC profile data in a JPEG file while compressing or transforming.  This
eliminates the need for downstream projects, such as color management libraries
and browsers, to include their own glueware for accomplishing this.

4. Improved error handling in the TurboJPEG API library:

     - Introduced a new function (`tjGetErrorStr2()`) in the TurboJPEG C API
that allows compression/decompression/transform error messages to be retrieved
in a thread-safe manner.  Retrieving error messages from global functions, such
as `tjInitCompress()` or `tjBufSize()`, is still thread-unsafe, but since those
functions will only throw errors if passed an invalid argument or if a memory
allocation failure occurs, thread safety is not as much of a concern.
     - Introduced a new function (`tjGetErrorCode()`) in the TurboJPEG C API
and a new method (`TJException.getErrorCode()`) in the TurboJPEG Java API that
can be used to determine the severity of the last
compression/decompression/transform error.  This allows applications to
choose whether to ignore warnings (non-fatal errors) from the underlying
libjpeg API or to treat them as fatal.
     - Introduced a new flag (`TJFLAG_STOPONWARNING` in the TurboJPEG C API and
`TJ.FLAG_STOPONWARNING` in the TurboJPEG Java API) that causes the library to
immediately halt a compression/decompression/transform operation if it
encounters a warning from the underlying libjpeg API (the default behavior is
to allow the operation to complete unless a fatal error is encountered.)

5. Introduced a new flag in the TurboJPEG C and Java APIs (`TJFLAG_PROGRESSIVE`
and `TJ.FLAG_PROGRESSIVE`, respectively) that causes the library to use
progressive entropy coding in JPEG images generated by compression and
transform operations.  Additionally, a new transform option
(`TJXOPT_PROGRESSIVE` in the C API and `TJTransform.OPT_PROGRESSIVE` in the
Java API) has been introduced, allowing progressive entropy coding to be
enabled for selected transforms in a multi-transform operation.

6. Introduced a new transform option in the TurboJPEG API (`TJXOPT_COPYNONE` in
the C API and `TJTransform.OPT_COPYNONE` in the Java API) that allows the
copying of markers (including EXIF and ICC profile data) to be disabled for a
particular transform.

7. Added two functions to the TurboJPEG C API (`tjLoadImage()` and
`tjSaveImage()`) that can be used to load/save a BMP or PPM/PGM image to/from a
memory buffer with a specified pixel format and layout.  These functions
replace the project-private (and slow) bmp API, which was previously used by
TJBench, and they also provide a convenient way for first-time users of
libjpeg-turbo to quickly develop a complete JPEG compression/decompression
program.

8. The TurboJPEG C API now includes a new convenience array (`tjAlphaOffset[]`)
that contains the alpha component index for each pixel format (or -1 if the
pixel format lacks an alpha component.)  The TurboJPEG Java API now includes a
new method (`TJ.getAlphaOffset()`) that returns the same value.  In addition,
the `tjRedOffset[]`, `tjGreenOffset[]`, and `tjBlueOffset[]` arrays-- and the
corresponding `TJ.getRedOffset()`, `TJ.getGreenOffset()`, and
`TJ.getBlueOffset()` methods-- now return -1 for `TJPF_GRAY`/`TJ.PF_GRAY`
rather than 0.  This allows programs to easily determine whether a pixel format
has red, green, blue, and alpha components.

9. Added a new example (tjexample.c) that demonstrates the basic usage of the
TurboJPEG C API.  This example mirrors the functionality of TJExample.java.
Both files are now included in the libjpeg-turbo documentation.

10. Fixed two signed integer overflows in the arithmetic decoder, detected by
the Clang undefined behavior sanitizer, that could be triggered by attempting
to decompress a specially-crafted malformed JPEG image.  These issues did not
pose a security threat, but removing the warnings makes it easier to detect
actual security issues, should they arise in the future.

11. Fixed a bug in the merged 4:2:0 upsampling/dithered RGB565 color conversion
algorithm that caused incorrect dithering in the output image.  This algorithm
now produces bitwise-identical results to the unmerged algorithms.

12. The SIMD function symbols for x86[-64]/ELF, MIPS/ELF, macOS/x86[-64] (if
libjpeg-turbo is built with YASM), and iOS/ARM[64] builds are now private.
This prevents those symbols from being exposed in applications or shared
libraries that link statically with libjpeg-turbo.

13. Added Loongson MMI SIMD implementations of the RGB-to-YCbCr and
YCbCr-to-RGB colorspace conversion, 4:2:0 chroma downsampling, 4:2:0 fancy
chroma upsampling, integer quantization, and slow integer DCT/IDCT algorithms.
When using the slow integer DCT/IDCT, this speeds up the compression of RGB
images by approximately 70-100% and the decompression of RGB images by
approximately 2-3.5x.

14. Fixed a build error when building with older MinGW releases (regression
caused by 1.5.1[7].)

15. Added SIMD acceleration for progressive Huffman encoding on SSE2-capable
x86 and x86-64 platforms.  This speeds up the compression of full-color
progressive JPEGs by about 85-90% on average (relative to libjpeg-turbo 1.5.x)
when using modern Intel and AMD CPUs.


1.5.3
=====

### Significant changes relative to 1.5.2:

1. Fixed a NullPointerException in the TurboJPEG Java wrapper that occurred
when using the YUVImage constructor that creates an instance backed by separate
image planes and allocates memory for the image planes.

2. Fixed an issue whereby the Java version of TJUnitTest would fail when
testing BufferedImage encoding/decoding on big endian systems.

3. Fixed a segfault in djpeg that would occur if an output format other than
PPM/PGM was selected along with the `-crop` option.  The `-crop` option now
works with the GIF and Targa formats as well (unfortunately, it cannot be made
to work with the BMP and RLE formats due to the fact that those output engines
write scanlines in bottom-up order.)  djpeg will now exit gracefully if an
output format other than PPM/PGM, GIF, or Targa is selected along with the
`-crop` option.

4. Fixed an issue whereby `jpeg_skip_scanlines()` would segfault if color
quantization was enabled.

5. TJBench (both C and Java versions) will now display usage information if any
command-line argument is unrecognized.  This prevents the program from silently
ignoring typos.

6. Fixed an access violation in tjbench.exe (Windows) that occurred when the
program was used to decompress an existing JPEG image.

7. Fixed an ArrayIndexOutOfBoundsException in the TJExample Java program that
occurred when attempting to decompress a JPEG image that had been compressed
with 4:1:1 chrominance subsampling.

8. Fixed an issue whereby, when using `jpeg_skip_scanlines()` to skip to the
end of a single-scan (non-progressive) image, subsequent calls to
`jpeg_consume_input()` would return `JPEG_SUSPENDED` rather than
`JPEG_REACHED_EOI`.

9. `jpeg_crop_scanlines()` now works correctly when decompressing grayscale
JPEG images that were compressed with a sampling factor other than 1 (for
instance, with `cjpeg -grayscale -sample 2x2`).


1.5.2
=====

### Significant changes relative to 1.5.1:

1. Fixed a regression introduced by 1.5.1[7] that prevented libjpeg-turbo from
building with Android NDK platforms prior to android-21 (5.0).

2. Fixed a regression introduced by 1.5.1[1] that prevented the MIPS DSPR2 SIMD
code in libjpeg-turbo from building.

3. Fixed a regression introduced by 1.5 beta1[11] that prevented the Java
version of TJBench from outputting any reference images (the `-nowrite` switch
was accidentally enabled by default.)

4. libjpeg-turbo should now build and run with full AltiVec SIMD acceleration
on PowerPC-based AmigaOS 4 and OpenBSD systems.

5. Fixed build and runtime errors on Windows that occurred when building
libjpeg-turbo with libjpeg v7 API/ABI emulation and the in-memory
source/destination managers.  Due to an oversight, the `jpeg_skip_scanlines()`
and `jpeg_crop_scanlines()` functions were not being included in jpeg7.dll when
libjpeg-turbo was built with `-DWITH_JPEG7=1` and `-DWITH_MEMSRCDST=1`.

6. Fixed "Bogus virtual array access" error that occurred when using the
lossless crop feature in jpegtran or the TurboJPEG API, if libjpeg-turbo was
built with libjpeg v7 API/ABI emulation.  This was apparently a long-standing
bug that has existed since the introduction of libjpeg v7/v8 API/ABI emulation
in libjpeg-turbo v1.1.

7. The lossless transform features in jpegtran and the TurboJPEG API will now
always attempt to adjust the EXIF image width and height tags if the image size
changed as a result of the transform.  This behavior has always existed when
using libjpeg v8 API/ABI emulation.  It was supposed to be available with
libjpeg v7 API/ABI emulation as well but did not work properly due to a bug.
Furthermore, there was never any good reason not to enable it with libjpeg v6b
API/ABI emulation, since the behavior is entirely internal.  Note that
`-copy all` must be passed to jpegtran in order to transfer the EXIF tags from
the source image to the destination image.

8. Fixed several memory leaks in the TurboJPEG API library that could occur
if the library was built with certain compilers and optimization levels
(known to occur with GCC 4.x and clang with `-O1` and higher but not with
GCC 5.x or 6.x) and one of the underlying libjpeg API functions threw an error
after a TurboJPEG API function allocated a local buffer.

9. The libjpeg-turbo memory manager will now honor the `max_memory_to_use`
structure member in jpeg\_memory\_mgr, which can be set to the maximum amount
of memory (in bytes) that libjpeg-turbo should use during decompression or
multi-pass (including progressive) compression.  This limit can also be set
using the `JPEGMEM` environment variable or using the `-maxmemory` switch in
cjpeg/djpeg/jpegtran (refer to the respective man pages for more details.)
This has been a documented feature of libjpeg since v5, but the
`malloc()`/`free()` implementation of the memory manager (jmemnobs.c) never
implemented the feature.  Restricting libjpeg-turbo's memory usage is useful
for two reasons:  it allows testers to more easily work around the 2 GB limit
in libFuzzer, and it allows developers of security-sensitive applications to
more easily defend against one of the progressive JPEG exploits (LJT-01-004)
identified in
[this report](http://www.libjpeg-turbo.org/pmwiki/uploads/About/TwoIssueswiththeJPEGStandard.pdf).

10. TJBench will now run each benchmark for 1 second prior to starting the
timer, in order to improve the consistency of the results.  Furthermore, the
`-warmup` option is now used to specify the amount of warmup time rather than
the number of warmup iterations.

11. Fixed an error (`short jump is out of range`) that occurred when assembling
the 32-bit x86 SIMD extensions with NASM versions prior to 2.04.  This was a
regression introduced by 1.5 beta1[12].


1.5.1
=====

### Significant changes relative to 1.5.0:

1. Previously, the undocumented `JSIMD_FORCE*` environment variables could be
used to force-enable a particular SIMD instruction set if multiple instruction
sets were available on a particular platform.  On x86 platforms, where CPU
feature detection is bulletproof and multiple SIMD instruction sets are
available, it makes sense for those environment variables to allow forcing the
use of an instruction set only if that instruction set is available.  However,
since the ARM implementations of libjpeg-turbo can only use one SIMD
instruction set, and since their feature detection code is less bulletproof
(parsing /proc/cpuinfo), it makes sense for the `JSIMD_FORCENEON` environment
variable to bypass the feature detection code and really force the use of NEON
instructions.  A new environment variable (`JSIMD_FORCEDSPR2`) was introduced
in the MIPS implementation for the same reasons, and the existing
`JSIMD_FORCENONE` environment variable was extended to that implementation.
These environment variables provide a workaround for those attempting to test
ARM and MIPS builds of libjpeg-turbo in QEMU, which passes through
/proc/cpuinfo from the host system.

2. libjpeg-turbo previously assumed that AltiVec instructions were always
available on PowerPC platforms, which led to "illegal instruction" errors when
running on PowerPC chips that lack AltiVec support (such as the older 7xx/G3
and newer e5500 series.)  libjpeg-turbo now examines /proc/cpuinfo on
Linux/Android systems and enables AltiVec instructions only if the CPU supports
them.  It also now provides two environment variables, `JSIMD_FORCEALTIVEC` and
`JSIMD_FORCENONE`, to force-enable and force-disable AltiVec instructions in
environments where /proc/cpuinfo is an unreliable means of CPU feature
detection (such as when running in QEMU.)  On OS X, libjpeg-turbo continues to
assume that AltiVec support is always available, which means that libjpeg-turbo
cannot be used with G3 Macs unless you set the environment variable
`JSIMD_FORCENONE` to `1`.

3. Fixed an issue whereby 64-bit ARM (AArch64) builds of libjpeg-turbo would
crash when built with recent releases of the Clang/LLVM compiler.  This was
caused by an ABI conformance issue in some of libjpeg-turbo's 64-bit NEON SIMD
routines.  Those routines were incorrectly using 64-bit instructions to
transfer a 32-bit JDIMENSION argument, whereas the ABI allows the upper
(unused) 32 bits of a 32-bit argument's register to be undefined.  The new
Clang/LLVM optimizer uses load combining to transfer multiple adjacent 32-bit
structure members into a single 64-bit register, and this exposed the ABI
conformance issue.

4. Fancy upsampling is now supported when decompressing JPEG images that use
4:4:0 (h1v2) chroma subsampling.  These images are generated when losslessly
rotating or transposing JPEG images that use 4:2:2 (h2v1) chroma subsampling.
The h1v2 fancy upsampling algorithm is not currently SIMD-accelerated.

5. If merged upsampling isn't SIMD-accelerated but YCbCr-to-RGB conversion is,
then libjpeg-turbo will now disable merged upsampling when decompressing YCbCr
JPEG images into RGB or extended RGB output images.  This significantly speeds
up the decompression of 4:2:0 and 4:2:2 JPEGs on ARM platforms if fancy
upsampling is not used (for example, if the `-nosmooth` option to djpeg is
specified.)

6. The TurboJPEG API will now decompress 4:2:2 and 4:4:0 JPEG images with
2x2 luminance sampling factors and 2x1 or 1x2 chrominance sampling factors.
This is a non-standard way of specifying 2x subsampling (normally 4:2:2 JPEGs
have 2x1 luminance and 1x1 chrominance sampling factors, and 4:4:0 JPEGs have
1x2 luminance and 1x1 chrominance sampling factors), but the JPEG format and
the libjpeg API both allow it.

7. Fixed an unsigned integer overflow in the libjpeg memory manager, detected
by the Clang undefined behavior sanitizer, that could be triggered by
attempting to decompress a specially-crafted malformed JPEG image.  This issue
affected only 32-bit code and did not pose a security threat, but removing the
warning makes it easier to detect actual security issues, should they arise in
the future.

8. Fixed additional negative left shifts and other issues reported by the GCC
and Clang undefined behavior sanitizers when attempting to decompress
specially-crafted malformed JPEG images.  None of these issues posed a security
threat, but removing the warnings makes it easier to detect actual security
issues, should they arise in the future.

9. Fixed an out-of-bounds array reference, introduced by 1.4.90[2] (partial
image decompression) and detected by the Clang undefined behavior sanitizer,
that could be triggered by a specially-crafted malformed JPEG image with more
than four components.  Because the out-of-bounds reference was still within the
same structure, it was not known to pose a security threat, but removing the
warning makes it easier to detect actual security issues, should they arise in
the future.

10. Fixed another ABI conformance issue in the 64-bit ARM (AArch64) NEON SIMD
code.  Some of the routines were incorrectly reading and storing data below the
stack pointer, which caused segfaults in certain applications under specific
circumstances.


1.5.0
=====

### Significant changes relative to 1.5 beta1:

1. Fixed an issue whereby a malformed motion-JPEG frame could cause the "fast
path" of libjpeg-turbo's Huffman decoder to read from uninitialized memory.

2. Added libjpeg-turbo version and build information to the global string table
of the libjpeg and TurboJPEG API libraries.  This is a common practice in other
infrastructure libraries, such as OpenSSL and libpng, because it makes it easy
to examine an application binary and determine which version of the library the
application was linked against.

3. Fixed a couple of issues in the PPM reader that would cause buffer overruns
in cjpeg if one of the values in a binary PPM/PGM input file exceeded the
maximum value defined in the file's header.  libjpeg-turbo 1.4.2 already
included a similar fix for ASCII PPM/PGM files.  Note that these issues were
not security bugs, since they were confined to the cjpeg program and did not
affect any of the libjpeg-turbo libraries.

4. Fixed an issue whereby attempting to decompress a JPEG file with a corrupt
header using the `tjDecompressToYUV2()` function would cause the function to
abort without returning an error and, under certain circumstances, corrupt the
stack.  This only occurred if `tjDecompressToYUV2()` was called prior to
calling `tjDecompressHeader3()`, or if the return value from
`tjDecompressHeader3()` was ignored (both cases represent incorrect usage of
the TurboJPEG API.)

5. Fixed an issue in the ARM 32-bit SIMD-accelerated Huffman encoder that
prevented the code from assembling properly with clang.

6. The `jpeg_stdio_src()`, `jpeg_mem_src()`, `jpeg_stdio_dest()`, and
`jpeg_mem_dest()` functions in the libjpeg API will now throw an error if a
source/destination manager has already been assigned to the compress or
decompress object by a different function or by the calling program.  This
prevents these functions from attempting to reuse a source/destination manager
structure that was allocated elsewhere, because there is no way to ensure that
it would be big enough to accommodate the new source/destination manager.


1.4.90 (1.5 beta1)
==================

### Significant changes relative to 1.4.2:

1. Added full SIMD acceleration for PowerPC platforms using AltiVec VMX
(128-bit SIMD) instructions.  Although the performance of libjpeg-turbo on
PowerPC was already good, due to the increased number of registers available
to the compiler vs. x86, it was still possible to speed up compression by about
3-4x and decompression by about 2-2.5x (relative to libjpeg v6b) through the
use of AltiVec instructions.

2. Added two new libjpeg API functions (`jpeg_skip_scanlines()` and
`jpeg_crop_scanline()`) that can be used to partially decode a JPEG image.  See
[libjpeg.txt](libjpeg.txt) for more details.

3. The TJCompressor and TJDecompressor classes in the TurboJPEG Java API now
implement the Closeable interface, so those classes can be used with a
try-with-resources statement.

4. The TurboJPEG Java classes now throw unchecked idiomatic exceptions
(IllegalArgumentException, IllegalStateException) for unrecoverable errors
caused by incorrect API usage, and those classes throw a new checked exception
type (TJException) for errors that are passed through from the C library.

5. Source buffers for the TurboJPEG C API functions, as well as the
`jpeg_mem_src()` function in the libjpeg API, are now declared as const
pointers.  This facilitates passing read-only buffers to those functions and
ensures the caller that the source buffer will not be modified.  This should
not create any backward API or ABI incompatibilities with prior libjpeg-turbo
releases.

6. The MIPS DSPr2 SIMD code can now be compiled to support either FR=0 or FR=1
FPUs.

7. Fixed additional negative left shifts and other issues reported by the GCC
and Clang undefined behavior sanitizers.  Most of these issues affected only
32-bit code, and none of them was known to pose a security threat, but removing
the warnings makes it easier to detect actual security issues, should they
arise in the future.

8. Removed the unnecessary `.arch` directive from the ARM64 NEON SIMD code.
This directive was preventing the code from assembling using the clang
integrated assembler.

9. Fixed a regression caused by 1.4.1[6] that prevented 32-bit and 64-bit
libjpeg-turbo RPMs from being installed simultaneously on recent Red Hat/Fedora
distributions.  This was due to the addition of a macro in jconfig.h that
allows the Huffman codec to determine the word size at compile time.  Since
that macro differs between 32-bit and 64-bit builds, this caused a conflict
between the i386 and x86_64 RPMs (any differing files, other than executables,
are not allowed when 32-bit and 64-bit RPMs are installed simultaneously.)
Since the macro is used only internally, it has been moved into jconfigint.h.

10. The x86-64 SIMD code can now be disabled at run time by setting the
`JSIMD_FORCENONE` environment variable to `1` (the other SIMD implementations
already had this capability.)

11. Added a new command-line argument to TJBench (`-nowrite`) that prevents the
benchmark from outputting any images.  This removes any potential operating
system overhead that might be caused by lazy writes to disk and thus improves
the consistency of the performance measurements.

12. Added SIMD acceleration for Huffman encoding on SSE2-capable x86 and x86-64
platforms.  This speeds up the compression of full-color JPEGs by about 10-15%
on average (relative to libjpeg-turbo 1.4.x) when using modern Intel and AMD
CPUs.  Additionally, this works around an issue in the clang optimizer that
prevents it (as of this writing) from achieving the same performance as GCC
when compiling the C version of the Huffman encoder
(<https://llvm.org/bugs/show_bug.cgi?id=16035>).  For the purposes of
benchmarking or regression testing, SIMD-accelerated Huffman encoding can be
disabled by setting the `JSIMD_NOHUFFENC` environment variable to `1`.

13. Added ARM 64-bit (ARMv8) NEON SIMD implementations of the commonly-used
compression algorithms (including the slow integer forward DCT and h2v2 & h2v1
downsampling algorithms, which are not accelerated in the 32-bit NEON
implementation.)  This speeds up the compression of full-color JPEGs by about
75% on average on a Cavium ThunderX processor and by about 2-2.5x on average on
Cortex-A53 and Cortex-A57 cores.

14. Added SIMD acceleration for Huffman encoding on NEON-capable ARM 32-bit
and 64-bit platforms.

    For 32-bit code, this speeds up the compression of full-color JPEGs by
about 30% on average on a typical iOS device (iPhone 4S, Cortex-A9) and by
about 6-7% on average on a typical Android device (Nexus 5X, Cortex-A53 and
Cortex-A57), relative to libjpeg-turbo 1.4.x.  Note that the larger speedup
under iOS is due to the fact that iOS builds use LLVM, which does not optimize
the C Huffman encoder as well as GCC does.

    For 64-bit code, NEON-accelerated Huffman encoding speeds up the
compression of full-color JPEGs by about 40% on average on a typical iOS device
(iPhone 5S, Apple A7) and by about 7-8% on average on a typical Android device
(Nexus 5X, Cortex-A53 and Cortex-A57), in addition to the speedup described in
[13] above.

    For the purposes of benchmarking or regression testing, SIMD-accelerated
Huffman encoding can be disabled by setting the `JSIMD_NOHUFFENC` environment
variable to `1`.

15. pkg-config (.pc) scripts are now included for both the libjpeg and
TurboJPEG API libraries on Un*x systems.  Note that if a project's build system
relies on these scripts, then it will not be possible to build that project
with libjpeg or with a prior version of libjpeg-turbo.

16. Optimized the ARM 64-bit (ARMv8) NEON SIMD decompression routines to
improve performance on CPUs with in-order pipelines.  This speeds up the
decompression of full-color JPEGs by nearly 2x on average on a Cavium ThunderX
processor and by about 15% on average on a Cortex-A53 core.

17. Fixed an issue in the accelerated Huffman decoder that could have caused
the decoder to read past the end of the input buffer when a malformed,
specially-crafted JPEG image was being decompressed.  In prior versions of
libjpeg-turbo, the accelerated Huffman decoder was invoked (in most cases) only
if there were > 128 bytes of data in the input buffer.  However, it is possible
to construct a JPEG image in which a single Huffman block is over 430 bytes
long, so this version of libjpeg-turbo activates the accelerated Huffman
decoder only if there are > 512 bytes of data in the input buffer.

18. Fixed a memory leak in tjunittest encountered when running the program
with the `-yuv` option.


1.4.2
=====

### Significant changes relative to 1.4.1:

1. Fixed an issue whereby cjpeg would segfault if a Windows bitmap with a
negative width or height was used as an input image (Windows bitmaps can have
a negative height if they are stored in top-down order, but such files are
rare and not supported by libjpeg-turbo.)

2. Fixed an issue whereby, under certain circumstances, libjpeg-turbo would
incorrectly encode certain JPEG images when quality=100 and the fast integer
forward DCT were used.  This was known to cause `make test` to fail when the
library was built with `-march=haswell` on x86 systems.

3. Fixed an issue whereby libjpeg-turbo would crash when built with the latest
& greatest development version of the Clang/LLVM compiler.  This was caused by
an x86-64 ABI conformance issue in some of libjpeg-turbo's 64-bit SSE2 SIMD
routines.  Those routines were incorrectly using a 64-bit `mov` instruction to
transfer a 32-bit JDIMENSION argument, whereas the x86-64 ABI allows the upper
(unused) 32 bits of a 32-bit argument's register to be undefined.  The new
Clang/LLVM optimizer uses load combining to transfer multiple adjacent 32-bit
structure members into a single 64-bit register, and this exposed the ABI
conformance issue.

4. Fixed a bug in the MIPS DSPr2 4:2:0 "plain" (non-fancy and non-merged)
upsampling routine that caused a buffer overflow (and subsequent segfault) when
decompressing a 4:2:0 JPEG image whose scaled output width was less than 16
pixels.  The "plain" upsampling routines are normally only used when
decompressing a non-YCbCr JPEG image, but they are also used when decompressing
a JPEG image whose scaled output height is 1.

5. Fixed various negative left shifts and other issues reported by the GCC and
Clang undefined behavior sanitizers.  None of these was known to pose a
security threat, but removing the warnings makes it easier to detect actual
security issues, should they arise in the future.


1.4.1
=====

### Significant changes relative to 1.4.0:

1. tjbench now properly handles CMYK/YCCK JPEG files.  Passing an argument of
`-cmyk` (instead of, for instance, `-rgb`) will cause tjbench to internally
convert the source bitmap to CMYK prior to compression, to generate YCCK JPEG
files, and to internally convert the decompressed CMYK pixels back to RGB after
decompression (the latter is done automatically if a CMYK or YCCK JPEG is
passed to tjbench as a source image.)  The CMYK<->RGB conversion operation is
not benchmarked.  NOTE: The quick & dirty CMYK<->RGB conversions that tjbench
uses are suitable for testing only.  Proper conversion between CMYK and RGB
requires a color management system.

2. `make test` now performs additional bitwise regression tests using tjbench,
mainly for the purpose of testing compression from/decompression to a subregion
of a larger image buffer.

3. `make test` no longer tests the regression of the floating point DCT/IDCT
by default, since the results of those tests can vary if the algorithms in
question are not implemented using SIMD instructions on a particular platform.
See the comments in [Makefile.am](Makefile.am) for information on how to
re-enable the tests and to specify an expected result for them based on the
particulars of your platform.

4. The NULL color conversion routines have been significantly optimized,
which speeds up the compression of RGB and CMYK JPEGs by 5-20% when using
64-bit code and 0-3% when using 32-bit code, and the decompression of those
images by 10-30% when using 64-bit code and 3-12% when using 32-bit code.

5. Fixed an "illegal instruction" error that occurred when djpeg from a
SIMD-enabled libjpeg-turbo MIPS build was executed with the `-nosmooth` option
on a MIPS machine that lacked DSPr2 support.  The MIPS SIMD routines for h2v1
and h2v2 merged upsampling were not properly checking for the existence of
DSPr2.

6. Performance has been improved significantly on 64-bit non-Linux and
non-Windows platforms (generally 10-20% faster compression and 5-10% faster
decompression.)  Due to an oversight, the 64-bit version of the accelerated
Huffman codec was not being compiled in when libjpeg-turbo was built on
platforms other than Windows or Linux.  Oops.

7. Fixed an extremely rare bug in the Huffman encoder that caused 64-bit
builds of libjpeg-turbo to incorrectly encode a few specific test images when
quality=98, an optimized Huffman table, and the slow integer forward DCT were
used.

8. The Windows (CMake) build system now supports building only static or only
shared libraries.  This is accomplished by adding either `-DENABLE_STATIC=0` or
`-DENABLE_SHARED=0` to the CMake command line.

9. TurboJPEG API functions will now return an error code if a warning is
triggered in the underlying libjpeg API.  For instance, if a JPEG file is
corrupt, the TurboJPEG decompression functions will attempt to decompress
as much of the image as possible, but those functions will now return -1 to
indicate that the decompression was not entirely successful.

10. Fixed a bug in the MIPS DSPr2 4:2:2 fancy upsampling routine that caused a
buffer overflow (and subsequent segfault) when decompressing a 4:2:2 JPEG image
in which the right-most MCU was 5 or 6 pixels wide.


1.4.0
=====

### Significant changes relative to 1.4 beta1:

1. Fixed a build issue on OS X PowerPC platforms (md5cmp failed to build
because OS X does not provide the `le32toh()` and `htole32()` functions.)

2. The non-SIMD RGB565 color conversion code did not work correctly on big
endian machines.  This has been fixed.

3. Fixed an issue in `tjPlaneSizeYUV()` whereby it would erroneously return 1
instead of -1 if `componentID` was > 0 and `subsamp` was `TJSAMP_GRAY`.

3. Fixed an issue in `tjBufSizeYUV2()` whereby it would erroneously return 0
instead of -1 if `width` was < 1.

5. The Huffman encoder now uses `clz` and `bsr` instructions for bit counting
on ARM64 platforms (see 1.4 beta1[5].)

6. The `close()` method in the TJCompressor and TJDecompressor Java classes is
now idempotent.  Previously, that method would call the native `tjDestroy()`
function even if the TurboJPEG instance had already been destroyed.  This
caused an exception to be thrown during finalization, if the `close()` method
had already been called.  The exception was caught, but it was still an
expensive operation.

7. The TurboJPEG API previously generated an error (`Could not determine
subsampling type for JPEG image`) when attempting to decompress grayscale JPEG
images that were compressed with a sampling factor other than 1 (for instance,
with `cjpeg -grayscale -sample 2x2`).  Subsampling technically has no meaning
with grayscale JPEGs, and thus the horizontal and vertical sampling factors
for such images are ignored by the decompressor.  However, the TurboJPEG API
was being too rigid and was expecting the sampling factors to be equal to 1
before it treated the image as a grayscale JPEG.

8. cjpeg, djpeg, and jpegtran now accept an argument of `-version`, which will
print the library version and exit.

9. Referring to 1.4 beta1[15], another extremely rare circumstance was
discovered under which the Huffman encoder's local buffer can be overrun
when a buffered destination manager is being used and an
extremely-high-frequency block (basically junk image data) is being encoded.
Even though the Huffman local buffer was increased from 128 bytes to 136 bytes
to address the previous issue, the new issue caused even the larger buffer to
be overrun.  Further analysis reveals that, in the absolute worst case (such as
setting alternating AC coefficients to 32767 and -32768 in the JPEG scanning
order), the Huffman encoder can produce encoded blocks that approach double the
size of the unencoded blocks.  Thus, the Huffman local buffer was increased to
256 bytes, which should prevent any such issue from re-occurring in the future.

10. The new `tjPlaneSizeYUV()`, `tjPlaneWidth()`, and `tjPlaneHeight()`
functions were not actually usable on any platform except OS X and Windows,
because those functions were not included in the libturbojpeg mapfile.  This
has been fixed.

11. Restored the `JPP()`, `JMETHOD()`, and `FAR` macros in the libjpeg-turbo
header files.  The `JPP()` and `JMETHOD()` macros were originally implemented
in libjpeg as a way of supporting non-ANSI compilers that lacked support for
prototype parameters.  libjpeg-turbo has never supported such compilers, but
some software packages still use the macros to define their own prototypes.
Similarly, libjpeg-turbo has never supported MS-DOS and other platforms that
have far symbols, but some software packages still use the `FAR` macro.  A
pretty good argument can be made that this is a bad practice on the part of the
software in question, but since this affects more than one package, it's just
easier to fix it here.

12. Fixed issues that were preventing the ARM 64-bit SIMD code from compiling
for iOS, and included an ARMv8 architecture in all of the binaries installed by
the "official" libjpeg-turbo SDK for OS X.


1.3.90 (1.4 beta1)
==================

### Significant changes relative to 1.3.1:

1. New features in the TurboJPEG API:

     - YUV planar images can now be generated with an arbitrary line padding
(previously only 4-byte padding, which was compatible with X Video, was
supported.)
     - The decompress-to-YUV function has been extended to support image
scaling.
     - JPEG images can now be compressed from YUV planar source images.
     - YUV planar images can now be decoded into RGB or grayscale images.
     - 4:1:1 subsampling is now supported.  This is mainly included for
compatibility, since 4:1:1 is not fully accelerated in libjpeg-turbo and has no
significant advantages relative to 4:2:0.
     - CMYK images are now supported.  This feature allows CMYK source images
to be compressed to YCCK JPEGs and YCCK or CMYK JPEGs to be decompressed to
CMYK destination images.  Conversion between CMYK/YCCK and RGB or YUV images is
not supported.  Such conversion requires a color management system and is thus
out of scope for a codec library.
     - The handling of YUV images in the Java API has been significantly
refactored and should now be much more intuitive.
     - The Java API now supports encoding a YUV image from an arbitrary
position in a large image buffer.
     - All of the YUV functions now have a corresponding function that operates
on separate image planes instead of a unified image buffer.  This allows for
compressing/decoding from or decompressing/encoding to a subregion of a larger
YUV image.  It also allows for handling YUV formats that swap the order of the
U and V planes.

2. Added SIMD acceleration for DSPr2-capable MIPS platforms.  This speeds up
the compression of full-color JPEGs by 70-80% on such platforms and
decompression by 25-35%.

3. If an application attempts to decompress a Huffman-coded JPEG image whose
header does not contain Huffman tables, libjpeg-turbo will now insert the
default Huffman tables.  In order to save space, many motion JPEG video frames
are encoded without the default Huffman tables, so these frames can now be
successfully decompressed by libjpeg-turbo without additional work on the part
of the application.  An application can still override the Huffman tables, for
instance to re-use tables from a previous frame of the same video.

4. The Mac packaging system now uses pkgbuild and productbuild rather than
PackageMaker (which is obsolete and no longer supported.)  This means that
OS X 10.6 "Snow Leopard" or later must be used when packaging libjpeg-turbo,
although the packages produced can be installed on OS X 10.5 "Leopard" or
later.  OS X 10.4 "Tiger" is no longer supported.

5. The Huffman encoder now uses `clz` and `bsr` instructions for bit counting
on ARM platforms rather than a lookup table.  This reduces the memory footprint
by 64k, which may be important for some mobile applications.  Out of four
Android devices that were tested, two demonstrated a small overall performance
loss (~3-4% on average) with ARMv6 code and a small gain (also ~3-4%) with
ARMv7 code when enabling this new feature, but the other two devices
demonstrated a significant overall performance gain with both ARMv6 and ARMv7
code (~10-20%) when enabling the feature.  Actual mileage may vary.

6. Worked around an issue with Visual C++ 2010 and later that caused incorrect
pixels to be generated when decompressing a JPEG image to a 256-color bitmap,
if compiler optimization was enabled when libjpeg-turbo was built.  This caused
the regression tests to fail when doing a release build under Visual C++ 2010
and later.

7. Improved the accuracy and performance of the non-SIMD implementation of the
floating point inverse DCT (using code borrowed from libjpeg v8a and later.)
The accuracy of this implementation now matches the accuracy of the SSE/SSE2
implementation.  Note, however, that the floating point DCT/IDCT algorithms are
mainly a legacy feature.  They generally do not produce significantly better
accuracy than the slow integer DCT/IDCT algorithms, and they are quite a bit
slower.

8. Added a new output colorspace (`JCS_RGB565`) to the libjpeg API that allows
for decompressing JPEG images into RGB565 (16-bit) pixels.  If dithering is not
used, then this code path is SIMD-accelerated on ARM platforms.

9. Numerous obsolete features, such as support for non-ANSI compilers and
support for the MS-DOS memory model, were removed from the libjpeg code,
greatly improving its readability and making it easier to maintain and extend.

10. Fixed a segfault that occurred when calling `output_message()` with
`msg_code` set to `JMSG_COPYRIGHT`.

11. Fixed an issue whereby wrjpgcom was allowing comments longer than 65k
characters to be passed on the command line, which was causing it to generate
incorrect JPEG files.

12. Fixed a bug in the build system that was causing the Windows version of
wrjpgcom to be built using the rdjpgcom source code.

13. Restored 12-bit-per-component JPEG support.  A 12-bit version of
libjpeg-turbo can now be built by passing an argument of `--with-12bit` to
configure (Unix) or `-DWITH_12BIT=1` to cmake (Windows.)  12-bit JPEG support
is included only for convenience.  Enabling this feature disables all of the
performance features in libjpeg-turbo, as well as arithmetic coding and the
TurboJPEG API.  The resulting library still contains the other libjpeg-turbo
features (such as the colorspace extensions), but in general, it performs no
faster than libjpeg v6b.

14. Added ARM 64-bit SIMD acceleration for the YCC-to-RGB color conversion
and IDCT algorithms (both are used during JPEG decompression.)  For unknown
reasons (probably related to clang), this code cannot currently be compiled for
iOS.

15. Fixed an extremely rare bug that could cause the Huffman encoder's local
buffer to overrun when a very high-frequency MCU is compressed using quality
100 and no subsampling, and when the JPEG output buffer is being dynamically
resized by the destination manager.  This issue was so rare that, even with a
test program specifically designed to make the bug occur (by injecting random
high-frequency YUV data into the compressor), it was reproducible only once in
about every 25 million iterations.

16. Fixed an oversight in the TurboJPEG C wrapper:  if any of the JPEG
compression functions was called repeatedly with the same
automatically-allocated destination buffer, then TurboJPEG would erroneously
assume that the `jpegSize` parameter was equal to the size of the buffer, when
in fact that parameter was probably equal to the size of the most recently
compressed JPEG image.  If the size of the previous JPEG image was not as large
as the current JPEG image, then TurboJPEG would unnecessarily reallocate the
destination buffer.


1.3.1
=====

### Significant changes relative to 1.3.0:

1. On Un*x systems, `make install` now installs the libjpeg-turbo libraries
into /opt/libjpeg-turbo/lib32 by default on any 32-bit system, not just x86,
and into /opt/libjpeg-turbo/lib64 by default on any 64-bit system, not just
x86-64.  You can override this by overriding either the `prefix` or `libdir`
configure variables.

2. The Windows installer now places a copy of the TurboJPEG DLLs in the same
directory as the rest of the libjpeg-turbo binaries.  This was mainly done
to support TurboVNC 1.3, which bundles the DLLs in its Windows installation.
When using a 32-bit version of CMake on 64-bit Windows, it is impossible to
access the c:\WINDOWS\system32 directory, which made it impossible for the
TurboVNC build scripts to bundle the 64-bit TurboJPEG DLL.

3. Fixed a bug whereby attempting to encode a progressive JPEG with arithmetic
entropy coding (by passing arguments of `-progressive -arithmetic` to cjpeg or
jpegtran, for instance) would result in an error, `Requested feature was
omitted at compile time`.

4. Fixed a couple of issues whereby malformed JPEG images would cause
libjpeg-turbo to use uninitialized memory during decompression.

5. Fixed an error (`Buffer passed to JPEG library is too small`) that occurred
when calling the TurboJPEG YUV encoding function with a very small (< 5x5)
source image, and added a unit test to check for this error.

6. The Java classes should now build properly under Visual Studio 2010 and
later.

7. Fixed an issue that prevented SRPMs generated using the in-tree packaging
tools from being rebuilt on certain newer Linux distributions.

8. Numerous minor fixes to eliminate compilation and build/packaging system
warnings, fix cosmetic issues, improve documentation clarity, and other general
source cleanup.


1.3.0
=====

### Significant changes relative to 1.3 beta1:

1. `make test` now works properly on FreeBSD, and it no longer requires the
md5sum executable to be present on other Un*x platforms.

2. Overhauled the packaging system:

     - To avoid conflict with vendor-supplied libjpeg-turbo packages, the
official RPMs and DEBs for libjpeg-turbo have been renamed to
"libjpeg-turbo-official".
     - The TurboJPEG libraries are now located under /opt/libjpeg-turbo in the
official Linux and Mac packages, to avoid conflict with vendor-supplied
packages and also to streamline the packaging system.
     - Release packages are now created with the directory structure defined
by the configure variables `prefix`, `bindir`, `libdir`, etc. (Un\*x) or by the
`CMAKE_INSTALL_PREFIX` variable (Windows.)  The exception is that the docs are
always located under the system default documentation directory on Un\*x and
Mac systems, and on Windows, the TurboJPEG DLL is always located in the Windows
system directory.
     - To avoid confusion, official libjpeg-turbo packages on Linux/Unix
platforms (except for Mac) will always install the 32-bit libraries in
/opt/libjpeg-turbo/lib32 and the 64-bit libraries in /opt/libjpeg-turbo/lib64.
     - Fixed an issue whereby, in some cases, the libjpeg-turbo executables on
Un*x systems were not properly linking with the shared libraries installed by
the same package.
     - Fixed an issue whereby building the "installer" target on Windows when
`WITH_JAVA=1` would fail if the TurboJPEG JAR had not been previously built.
     - Building the "install" target on Windows now installs files into the
same places that the installer does.

3. Fixed a Huffman encoder bug that prevented I/O suspension from working
properly.


1.2.90 (1.3 beta1)
==================

### Significant changes relative to 1.2.1:

1. Added support for additional scaling factors (3/8, 5/8, 3/4, 7/8, 9/8, 5/4,
11/8, 3/2, 13/8, 7/4, 15/8, and 2) when decompressing.  Note that the IDCT will
not be SIMD-accelerated when using any of these new scaling factors.

2. The TurboJPEG dynamic library is now versioned.  It was not strictly
necessary to do so, because TurboJPEG uses versioned symbols, and if a function
changes in an ABI-incompatible way, that function is renamed and a legacy
function is provided to maintain backward compatibility.  However, certain
Linux distro maintainers have a policy against accepting any library that isn't
versioned.

3. Extended the TurboJPEG Java API so that it can be used to compress a JPEG
image from and decompress a JPEG image to an arbitrary position in a large
image buffer.

4. The `tjDecompressToYUV()` function now supports the `TJFLAG_FASTDCT` flag.

5. The 32-bit supplementary package for amd64 Debian systems now provides
symlinks in /usr/lib/i386-linux-gnu for the TurboJPEG libraries in /usr/lib32.
This allows those libraries to be used on MultiArch-compatible systems (such as
Ubuntu 11 and later) without setting the linker path.

6. The TurboJPEG Java wrapper should now find the JNI library on Mac systems
without having to pass `-Djava.library.path=/usr/lib` to java.

7. TJBench has been ported to Java to provide a convenient way of validating
the performance of the TurboJPEG Java API.  It can be run with
`java -cp turbojpeg.jar TJBench`.

8. cjpeg can now be used to generate JPEG files with the RGB colorspace
(feature ported from jpeg-8d.)

9. The width and height in the `-crop` argument passed to jpegtran can now be
suffixed with `f` to indicate that, when the upper left corner of the cropping
region is automatically moved to the nearest iMCU boundary, the bottom right
corner should be moved by the same amount.  In other words, this feature causes
jpegtran to strictly honor the specified width/height rather than the specified
bottom right corner (feature ported from jpeg-8d.)

10. JPEG files using the RGB colorspace can now be decompressed into grayscale
images (feature ported from jpeg-8d.)

11. Fixed a regression caused by 1.2.1[7] whereby the build would fail with
multiple "Mismatch in operand sizes" errors when attempting to build the x86
SIMD code with NASM 0.98.

12. The in-memory source/destination managers (`jpeg_mem_src()` and
`jpeg_mem_dest()`) are now included by default when building libjpeg-turbo with
libjpeg v6b or v7 emulation, so that programs can take advantage of these
functions without requiring the use of the backward-incompatible libjpeg v8
ABI.  The "age number" of the libjpeg-turbo library on Un*x systems has been
incremented by 1 to reflect this.  You can disable this feature with a
configure/CMake switch in order to retain strict API/ABI compatibility with the
libjpeg v6b or v7 API/ABI (or with previous versions of libjpeg-turbo.)  See
[README.md](README.md) for more details.

13. Added ARMv7s architecture to libjpeg.a and libturbojpeg.a in the official
libjpeg-turbo binary package for OS X, so that those libraries can be used to
build applications that leverage the faster CPUs in the iPhone 5 and iPad 4.


1.2.1
=====

### Significant changes relative to 1.2.0:

1. Creating or decoding a JPEG file that uses the RGB colorspace should now
properly work when the input or output colorspace is one of the libjpeg-turbo
colorspace extensions.

2. When libjpeg-turbo was built without SIMD support and merged (non-fancy)
upsampling was used along with an alpha-enabled colorspace during
decompression, the unused byte of the decompressed pixels was not being set to
0xFF.  This has been fixed.  TJUnitTest has also been extended to test for the
correct behavior of the colorspace extensions when merged upsampling is used.

3. Fixed a bug whereby the libjpeg-turbo SSE2 SIMD code would not preserve the
upper 64 bits of xmm6 and xmm7 on Win64 platforms, which violated the Win64
calling conventions.

4. Fixed a regression caused by 1.2.0[6] whereby decompressing corrupt JPEG
images (specifically, images in which the component count was erroneously set
to a large value) would cause libjpeg-turbo to segfault.

5. Worked around a severe performance issue with "Bobcat" (AMD Embedded APU)
processors.  The `MASKMOVDQU` instruction, which was used by the libjpeg-turbo
SSE2 SIMD code, is apparently implemented in microcode on AMD processors, and
it is painfully slow on Bobcat processors in particular.  Eliminating the use
of this instruction improved performance by an order of magnitude on Bobcat
processors and by a small amount (typically 5%) on AMD desktop processors.

6. Added SIMD acceleration for performing 4:2:2 upsampling on NEON-capable ARM
platforms.  This speeds up the decompression of 4:2:2 JPEGs by 20-25% on such
platforms.

7. Fixed a regression caused by 1.2.0[2] whereby, on Linux/x86 platforms
running the 32-bit SSE2 SIMD code in libjpeg-turbo, decompressing a 4:2:0 or
4:2:2 JPEG image into a 32-bit (RGBX, BGRX, etc.) buffer without using fancy
upsampling would produce several incorrect columns of pixels at the right-hand
side of the output image if each row in the output image was not evenly
divisible by 16 bytes.

8. Fixed an issue whereby attempting to build the SIMD extensions with Xcode
4.3 on OS X platforms would cause NASM to return numerous errors of the form
"'%define' expects a macro identifier".

9. Added flags to the TurboJPEG API that allow the caller to force the use of
either the fast or the accurate DCT/IDCT algorithms in the underlying codec.


1.2.0
=====

### Significant changes relative to 1.2 beta1:

1. Fixed build issue with YASM on Unix systems (the libjpeg-turbo build system
was not adding the current directory to the assembler include path, so YASM
was not able to find jsimdcfg.inc.)

2. Fixed out-of-bounds read in SSE2 SIMD code that occurred when decompressing
a JPEG image to a bitmap buffer whose size was not a multiple of 16 bytes.
This was more of an annoyance than an actual bug, since it did not cause any
actual run-time problems, but the issue showed up when running libjpeg-turbo in
valgrind.  See <http://crbug.com/72399> for more information.

3. Added a compile-time macro (`LIBJPEG_TURBO_VERSION`) that can be used to
check the version of libjpeg-turbo against which an application was compiled.

4. Added new RGBA/BGRA/ABGR/ARGB colorspace extension constants (libjpeg API)
and pixel formats (TurboJPEG API), which allow applications to specify that,
when decompressing to a 4-component RGB buffer, the unused byte should be set
to 0xFF so that it can be interpreted as an opaque alpha channel.

5. Fixed regression issue whereby DevIL failed to build against libjpeg-turbo
because libjpeg-turbo's distributed version of jconfig.h contained an `INLINE`
macro, which conflicted with a similar macro in DevIL.  This macro is used only
internally when building libjpeg-turbo, so it was moved into config.h.

6. libjpeg-turbo will now correctly decompress erroneous CMYK/YCCK JPEGs whose
K component is assigned a component ID of 1 instead of 4.  Although these files
are in violation of the spec, other JPEG implementations handle them
correctly.

7. Added ARMv6 and ARMv7 architectures to libjpeg.a and libturbojpeg.a in
the official libjpeg-turbo binary package for OS X, so that those libraries can
be used to build both OS X and iOS applications.


1.1.90 (1.2 beta1)
==================

### Significant changes relative to 1.1.1:

1. Added a Java wrapper for the TurboJPEG API.  See [java/README](java/README)
for more details.

2. The TurboJPEG API can now be used to scale down images during
decompression.

3. Added SIMD routines for RGB-to-grayscale color conversion, which
significantly improves the performance of grayscale JPEG compression from an
RGB source image.

4. Improved the performance of the C color conversion routines, which are used
on platforms for which SIMD acceleration is not available.

5. Added a function to the TurboJPEG API that performs lossless transforms.
This function is implemented using the same back end as jpegtran, but it
performs transcoding entirely in memory and allows multiple transforms and/or
crop operations to be batched together, so the source coefficients only need to
be read once.  This is useful when generating image tiles from a single source
JPEG.

6. Added tests for the new TurboJPEG scaled decompression and lossless
transform features to tjbench (the TurboJPEG benchmark, formerly called
"jpgtest".)

7. Added support for 4:4:0 (transposed 4:2:2) subsampling in TurboJPEG, which
was necessary in order for it to read 4:2:2 JPEG files that had been losslessly
transposed or rotated 90 degrees.

8. All legacy VirtualGL code has been re-factored, and this has allowed
libjpeg-turbo, in its entirety, to be re-licensed under a BSD-style license.

9. libjpeg-turbo can now be built with YASM.

10. Added SIMD acceleration for ARM Linux and iOS platforms that support
NEON instructions.

11. Refactored the TurboJPEG C API and documented it using Doxygen.  The
TurboJPEG 1.2 API uses pixel formats to define the size and component order of
the uncompressed source/destination images, and it includes a more efficient
version of `TJBUFSIZE()` that computes a worst-case JPEG size based on the
level of chrominance subsampling.  The refactored implementation of the
TurboJPEG API now uses the libjpeg memory source and destination managers,
which allows the TurboJPEG compressor to grow the JPEG buffer as necessary.

12. Eliminated errors in the output of jpegtran on Windows that occurred when
the application was invoked using I/O redirection
(`jpegtran <input.jpg >output.jpg`.)

13. The inclusion of libjpeg v7 and v8 emulation as well as arithmetic coding
support in libjpeg-turbo v1.1.0 introduced several new error constants in
jerror.h, and these were mistakenly enabled for all emulation modes, causing
the error enum in libjpeg-turbo to sometimes have different values than the
same enum in libjpeg.  This represents an ABI incompatibility, and it caused
problems with rare applications that took specific action based on a particular
error value.  The fix was to include the new error constants conditionally
based on whether libjpeg v7 or v8 emulation was enabled.

14. Fixed an issue whereby Windows applications that used libjpeg-turbo would
fail to compile if the Windows system headers were included before jpeglib.h.
This issue was caused by a conflict in the definition of the INT32 type.

15. Fixed 32-bit supplementary package for amd64 Debian systems, which was
broken by enhancements to the packaging system in 1.1.

16. When decompressing a JPEG image using an output colorspace of
`JCS_EXT_RGBX`, `JCS_EXT_BGRX`, `JCS_EXT_XBGR`, or `JCS_EXT_XRGB`,
libjpeg-turbo will now set the unused byte to 0xFF, which allows applications
to interpret that byte as an alpha channel (0xFF = opaque).


1.1.1
=====

### Significant changes relative to 1.1.0:

1. Fixed a 1-pixel error in row 0, column 21 of the luminance plane generated
by `tjEncodeYUV()`.

2. libjpeg-turbo's accelerated Huffman decoder previously ignored unexpected
markers found in the middle of the JPEG data stream during decompression.  It
will now hand off decoding of a particular block to the unaccelerated Huffman
decoder if an unexpected marker is found, so that the unaccelerated Huffman
decoder can generate an appropriate warning.

3. Older versions of MinGW64 prefixed symbol names with underscores by
default, which differed from the behavior of 64-bit Visual C++.  MinGW64 1.0
has adopted the behavior of 64-bit Visual C++ as the default, so to accommodate
this, the libjpeg-turbo SIMD function names are no longer prefixed with an
underscore when building with MinGW64.  This means that, when building
libjpeg-turbo with older versions of MinGW64, you will now have to add
`-fno-leading-underscore` to the `CFLAGS`.

4. Fixed a regression bug in the NSIS script that caused the Windows installer
build to fail when using the Visual Studio IDE.

5. Fixed a bug in `jpeg_read_coefficients()` whereby it would not initialize
`cinfo->image_width` and `cinfo->image_height` if libjpeg v7 or v8 emulation
was enabled.  This specifically caused the jpegoptim program to fail if it was
linked against a version of libjpeg-turbo that was built with libjpeg v7 or v8
emulation.

6. Eliminated excessive I/O overhead that occurred when reading BMP files in
cjpeg.

7. Eliminated errors in the output of cjpeg on Windows that occurred when the
application was invoked using I/O redirection (`cjpeg <inputfile >output.jpg`.)


1.1.0
=====

### Significant changes relative to 1.1 beta1:

1. The algorithm used by the SIMD quantization function cannot produce correct
results when the JPEG quality is >= 98 and the fast integer forward DCT is
used.  Thus, the non-SIMD quantization function is now used for those cases,
and libjpeg-turbo should now produce identical output to libjpeg v6b in all
cases.

2. Despite the above, the fast integer forward DCT still degrades somewhat for
JPEG qualities greater than 95, so the TurboJPEG wrapper will now automatically
use the slow integer forward DCT when generating JPEG images of quality 96 or
greater.  This reduces compression performance by as much as 15% for these
high-quality images but is necessary to ensure that the images are perceptually
lossless.  It also ensures that the library can avoid the performance pitfall
created by [1].

3. Ported jpgtest.cxx to pure C to avoid the need for a C++ compiler.

4. Fixed visual artifacts in grayscale JPEG compression caused by a typo in
the RGB-to-luminance lookup tables.

5. The Windows distribution packages now include the libjpeg run-time programs
(cjpeg, etc.)

6. All packages now include jpgtest.

7. The TurboJPEG dynamic library now uses versioned symbols.

8. Added two new TurboJPEG API functions, `tjEncodeYUV()` and
`tjDecompressToYUV()`, to replace the somewhat hackish `TJ_YUV` flag.


1.0.90 (1.1 beta1)
==================

### Significant changes relative to 1.0.1:

1. Added emulation of the libjpeg v7 and v8 APIs and ABIs.  See
[README.md](README.md) for more details.  This feature was sponsored by
CamTrace SAS.

2. Created a new CMake-based build system for the Visual C++ and MinGW builds.

3. Grayscale bitmaps can now be compressed from/decompressed to using the
TurboJPEG API.

4. jpgtest can now be used to test decompression performance with existing
JPEG images.

5. If the default install prefix (/opt/libjpeg-turbo) is used, then
`make install` now creates /opt/libjpeg-turbo/lib32 and
/opt/libjpeg-turbo/lib64 sym links to duplicate the behavior of the binary
packages.

6. All symbols in the libjpeg-turbo dynamic library are now versioned, even
when the library is built with libjpeg v6b emulation.

7. Added arithmetic encoding and decoding support (can be disabled with
configure or CMake options)

8. Added a `TJ_YUV` flag to the TurboJPEG API, which causes both the compressor
and decompressor to output planar YUV images.

9. Added an extended version of `tjDecompressHeader()` to the TurboJPEG API,
which allows the caller to determine the type of subsampling used in a JPEG
image.

10. Added further protections against invalid Huffman codes.


1.0.1
=====

### Significant changes relative to 1.0.0:

1. The Huffman decoder will now handle erroneous Huffman codes (for instance,
from a corrupt JPEG image.)  Previously, these would cause libjpeg-turbo to
crash under certain circumstances.

2. Fixed typo in SIMD dispatch routines that was causing 4:2:2 upsampling to
be used instead of 4:2:0 when decompressing JPEG images using SSE2 code.

3. The configure script will now automatically determine whether the
`INCOMPLETE_TYPES_BROKEN` macro should be defined.


1.0.0
=====

### Significant changes relative to 0.0.93:

1. 2983700: Further FreeBSD build tweaks (no longer necessary to specify
`--host` when configuring on a 64-bit system)

2. Created symlinks in the Unix/Linux packages so that the TurboJPEG
include file can always be found in /opt/libjpeg-turbo/include, the 32-bit
static libraries can always be found in /opt/libjpeg-turbo/lib32, and the
64-bit static libraries can always be found in /opt/libjpeg-turbo/lib64.

3. The Unix/Linux distribution packages now include the libjpeg run-time
programs (cjpeg, etc.) and man pages.

4. Created a 32-bit supplementary package for amd64 Debian systems, which
contains just the 32-bit libjpeg-turbo libraries.

5. Moved the libraries from */lib32 to */lib in the i386 Debian package.

6. Include distribution package for Cygwin

7. No longer necessary to specify `--without-simd` on non-x86 architectures,
and unit tests now work on those architectures.


0.0.93
======

### Significant changes since 0.0.91:

1. 2982659: Fixed x86-64 build on FreeBSD systems

2. 2988188: Added support for Windows 64-bit systems


0.0.91
======

### Significant changes relative to 0.0.90:

1. Added documentation to .deb packages

2. 2968313: Fixed data corruption issues when decompressing large JPEG images
and/or using buffered I/O with the libjpeg-turbo decompressor


0.0.90
======

Initial release

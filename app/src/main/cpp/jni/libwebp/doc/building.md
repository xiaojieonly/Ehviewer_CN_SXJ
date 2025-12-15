# Building

## Windows build

By running:

```batch
nmake /f Makefile.vc CFG=release-static RTLIBCFG=static OBJDIR=output
```

the directory `output\release-static\(x64|x86)\bin` will contain the tools
cwebp.exe and dwebp.exe. The directory `output\release-static\(x64|x86)\lib`
will contain the libwebp static library. The target architecture (x86/x64) is
detected by Makefile.vc from the Visual Studio compiler (cl.exe) available in
the system path.

## Unix build using makefile.unix

On platforms with GNU tools installed (gcc and make), running

```shell
make -f makefile.unix
```

will build the binaries examples/cwebp and examples/dwebp, along with the static
library src/libwebp.a. No system-wide installation is supplied, as this is a
simple alternative to the full installation system based on the autoconf tools
(see below). Please refer to makefile.unix for additional details and
customizations.

## Using autoconf tools

Prerequisites: a compiler (e.g., gcc), make, autoconf, automake, libtool.

On a Debian-like system the following should install everything you need for a
minimal build:

```shell
$ sudo apt-get install gcc make autoconf automake libtool
```

When building from git sources, you will need to run autogen.sh to generate the
configure script.

```shell
./configure
make
make install
```

should be all you need to have the following files

```
/usr/local/include/webp/decode.h
/usr/local/include/webp/encode.h
/usr/local/include/webp/types.h
/usr/local/lib/libwebp.*
/usr/local/bin/cwebp
/usr/local/bin/dwebp
```

installed.

Note: A decode-only library, libwebpdecoder, is available using the
`--enable-libwebpdecoder` flag. The encode library is built separately and can
be installed independently using a minor modification in the corresponding
Makefile.am configure files (see comments there). See `./configure --help` for
more options.

## Building for MIPS Linux

MIPS Linux toolchain stable available releases can be found at:
https://community.imgtec.com/developers/mips/tools/codescape-mips-sdk/available-releases/

```shell
# Add toolchain to PATH
export PATH=$PATH:/path/to/toolchain/bin

# 32-bit build for mips32r5 (p5600)
HOST=mips-mti-linux-gnu
MIPS_CFLAGS="-O3 -mips32r5 -mabi=32 -mtune=p5600 -mmsa -mfp64 \
  -msched-weight -mload-store-pairs -fPIE"
MIPS_LDFLAGS="-mips32r5 -mabi=32 -mmsa -mfp64 -pie"

# 64-bit build for mips64r6 (i6400)
HOST=mips-img-linux-gnu
MIPS_CFLAGS="-O3 -mips64r6 -mabi=64 -mtune=i6400 -mmsa -mfp64 \
  -msched-weight -mload-store-pairs -fPIE"
MIPS_LDFLAGS="-mips64r6 -mabi=64 -mmsa -mfp64 -pie"

./configure --host=${HOST} --build=`config.guess` \
  CC="${HOST}-gcc -EL" \
  CFLAGS="$MIPS_CFLAGS" \
  LDFLAGS="$MIPS_LDFLAGS"
make
make install
```

## Building libwebp - Using vcpkg

You can download and install libwebp using the
[vcpkg](https://github.com/Microsoft/vcpkg) dependency manager:

```shell
git clone https://github.com/Microsoft/vcpkg.git
cd vcpkg
./bootstrap-vcpkg.sh
./vcpkg integrate install
./vcpkg install libwebp
```

The libwebp port in vcpkg is kept up to date by Microsoft team members and
community contributors. If the version is out of date, please
[create an issue or pull request](https://github.com/Microsoft/vcpkg) on the
vcpkg repository.

## CMake

With CMake, you can compile libwebp, cwebp, dwebp, gif2webp, img2webp, webpinfo
and the JS bindings.

Prerequisites: a compiler (e.g., gcc with autotools) and CMake.

On a Debian-like system the following should install everything you need for a
minimal build:

```shell
$ sudo apt-get install build-essential cmake
```

When building from git sources, you will need to run cmake to generate the
makefiles.

```shell
mkdir build && cd build && cmake ../
make
make install
```

If you also want any of the executables, you will need to enable them through
CMake, e.g.:

```shell
cmake -DWEBP_BUILD_CWEBP=ON -DWEBP_BUILD_DWEBP=ON ../
```

or through your favorite interface (like ccmake or cmake-qt-gui).

Use option `-DWEBP_UNICODE=ON` for Unicode support on Windows (with chcp 65001).

Finally, once installed, you can also use WebP in your CMake project by doing:

```cmake
find_package(WebP)
```

which will define the CMake variables WebP_INCLUDE_DIRS and WebP_LIBRARIES.

## Gradle

The support for Gradle is minimal: it only helps you compile libwebp, cwebp and
dwebp and webpmux_example.

Prerequisites: a compiler (e.g., gcc with autotools) and gradle.

On a Debian-like system the following should install everything you need for a
minimal build:

```shell
$ sudo apt-get install build-essential gradle
```

When building from git sources, you will need to run the Gradle wrapper with the
appropriate target, e.g. :

```shell
./gradlew buildAllExecutables
```

## SWIG bindings

To generate language bindings from swig/libwebp.swig at least swig-1.3
(http://www.swig.org) is required.

Currently the following functions are mapped:

Decode:

```
WebPGetDecoderVersion
WebPGetInfo
WebPDecodeRGBA
WebPDecodeARGB
WebPDecodeBGRA
WebPDecodeBGR
WebPDecodeRGB
```

Encode:

```
WebPGetEncoderVersion
WebPEncodeRGBA
WebPEncodeBGRA
WebPEncodeRGB
WebPEncodeBGR
WebPEncodeLosslessRGBA
WebPEncodeLosslessBGRA
WebPEncodeLosslessRGB
WebPEncodeLosslessBGR
```

See also the [swig documentation](../swig/README.md) for more detailed build
instructions and usage examples.

### Java bindings

To build the swig-generated JNI wrapper code at least JDK-1.5 (or equivalent) is
necessary for enum support. The output is intended to be a shared object / DLL
that can be loaded via `System.loadLibrary("webp_jni")`.

### Python bindings

To build the swig-generated Python extension code at least Python 2.6 is
required. Python < 2.6 may build with some minor changes to libwebp.swig or the
generated code, but is untested.

## Javascript decoder

Libwebp can be compiled into a JavaScript decoder using Emscripten and CMake.
See the [corresponding documentation](../webp_js/README.md)

#!/bin/bash
#
# This script generates 'WebP.xcframework', 'WebPDecoder.xcframework',
# 'WebPDemux.xcframework' and 'WebPMux.xcframework'.
# An iOS, Mac or Mac Catalyst app can decode WebP images by including
# 'WebPDecoder.xcframework' and both encode and decode WebP images by including
# 'WebP.xcframework'.
#
# Run ./xcframeworkbuild.sh to generate the frameworks under the current
# directory (the previous build will be erased if it exists).
#

set -e

# Set these variables based on the desired minimum deployment target.
readonly IOS_MIN_VERSION=6.0
readonly MACOSX_MIN_VERSION=10.15
readonly MACOSX_CATALYST_MIN_VERSION=14.0

# Extract Xcode version.
readonly XCODE=$(xcodebuild -version | grep Xcode | cut -d " " -f2)
if [[ -z "${XCODE}" ]] || [[ "${XCODE%%.*}" -lt 11 ]]; then
  echo "Xcode 11.0 or higher is required!"
  exit 1
fi

# Extract the latest SDK version from the final field of the form: iphoneosX.Y
# / macosxX.Y
readonly SDK=($(
  xcodebuild -showsdks \
    | grep iphoneos | sort | tail -n 1 | awk '{print substr($NF, 9)}'
  xcodebuild -showsdks \
    | grep macosx | sort | tail -n 1 | awk '{print substr($NF, 7)}'
))
readonly IOS=0
readonly MACOS=1
readonly IOS_SIMULATOR=2
readonly MACOS_CATALYST=3
readonly NUM_PLATFORMS=4

readonly OLDPATH=${PATH}

# Names should be of the form '<platform>-[<variant>-]<architecture>'.
PLATFORMS[$IOS]="iPhoneOS-armv7 iPhoneOS-armv7s iPhoneOS-arm64"
PLATFORMS[$IOS_SIMULATOR]="iPhoneSimulator-i386 iPhoneSimulator-x86_64"
PLATFORMS[$MACOS]="MacOSX-x86_64"
PLATFORMS[$MACOS_CATALYST]="MacOSX-Catalyst-x86_64"
if [[ "${XCODE%%.*}" -ge 12 ]]; then
  PLATFORMS[$MACOS]+=" MacOSX-arm64"
  PLATFORMS[$MACOS_CATALYST]+=" MacOSX-Catalyst-arm64"
  PLATFORMS[$IOS_SIMULATOR]+=" iPhoneSimulator-arm64"
elif [[ "${XCODE%%.*}" -eq 11 ]]; then
  cat << EOF
WARNING: Xcode 12.0 or higher is required to build targets for
WARNING: Apple Silicon (arm64). The XCFrameworks generated with Xcode 11 will
WARNING: contain libraries for MacOS & Catalyst supporting x86_64 only.
WARNING: The build will continue in 5 seconds...
EOF
  sleep 5
else
  echo "Xcode 11.0 or higher is required!"
  exit 1
fi
readonly PLATFORMS
readonly SRCDIR=$(dirname $0)
readonly TOPDIR=$(pwd)
readonly BUILDDIR="${TOPDIR}/xcframeworkbuild"
readonly TARGETDIR="${TOPDIR}/WebP.xcframework"
readonly DECTARGETDIR="${TOPDIR}/WebPDecoder.xcframework"
readonly MUXTARGETDIR="${TOPDIR}/WebPMux.xcframework"
readonly DEMUXTARGETDIR="${TOPDIR}/WebPDemux.xcframework"
readonly SHARPYUVTARGETDIR="${TOPDIR}/SharpYuv.xcframework"
readonly DEVELOPER=$(xcode-select --print-path)
readonly DEVROOT="${DEVELOPER}/Toolchains/XcodeDefault.xctoolchain"
readonly PLATFORMSROOT="${DEVELOPER}/Platforms"
readonly LIPO=$(xcrun -sdk iphoneos${SDK[$IOS]} -find lipo)

if [[ -z "${SDK[$IOS]}" ]] || [[ ${SDK[$IOS]%%.*} -lt 8 ]]; then
  echo "iOS SDK version 8.0 or higher is required!"
  exit 1
fi

#######################################
# Moves Headers/*.h to Headers/<framework>/
#
# Places framework headers in a subdirectory to avoid Xcode errors when using
# multiple frameworks:
#   error: Multiple commands produce
#     '.../Build/Products/Debug-iphoneos/include/types.h'
# Arguments:
#   $1 - path to framework
#######################################
update_headers_path() {
  local framework_name="$(basename ${1%.xcframework})"
  local subdir
  for d in $(find "$1" -path "*/Headers"); do
    subdir="$d/$framework_name"
    if [[ -d "$subdir" ]]; then
      # SharpYuv will have a sharpyuv subdirectory. macOS is case insensitive,
      # but for consistency with the other frameworks, rename the directory to
      # match the case of the framework name.
      mv "$(echo ${subdir} | tr 'A-Z' 'a-z')" "$subdir"
    else
      mkdir "$subdir"
      mv "$d/"*.h "$subdir"
    fi
  done
}

echo "Xcode Version: ${XCODE}"
echo "iOS SDK Version: ${SDK[$IOS]}"
echo "MacOS SDK Version: ${SDK[$MACOS]}"

if [[ -e "${BUILDDIR}" || -e "${TARGETDIR}" || -e "${DECTARGETDIR}" \
      || -e "${MUXTARGETDIR}" || -e "${DEMUXTARGETDIR}" \
      || -e "${SHARPYUVTARGETDIR}" ]]; then
  cat << EOF
WARNING: The following directories will be deleted:
WARNING:   ${BUILDDIR}
WARNING:   ${TARGETDIR}
WARNING:   ${DECTARGETDIR}
WARNING:   ${MUXTARGETDIR}
WARNING:   ${DEMUXTARGETDIR}
WARNING:   ${SHARPYUVTARGETDIR}
WARNING: The build will continue in 5 seconds...
EOF
  sleep 5
fi
rm -rf ${BUILDDIR} ${TARGETDIR} ${DECTARGETDIR} \
  ${MUXTARGETDIR} ${DEMUXTARGETDIR} ${SHARPYUVTARGETDIR}

if [[ ! -e ${SRCDIR}/configure ]]; then
  if ! (cd ${SRCDIR} && sh autogen.sh); then
    cat << EOF
Error creating configure script!
This script requires the autoconf/automake and libtool to build. MacPorts or
Homebrew can be used to obtain these:
https://www.macports.org/install.php
https://brew.sh/
EOF
    exit 1
  fi
fi

for (( i = 0; i < $NUM_PLATFORMS; ++i )); do
  LIBLIST=()
  DECLIBLIST=()
  MUXLIBLIST=()
  DEMUXLIBLIST=()
  SHARPYUVLIBLIST=()

  for PLATFORM in ${PLATFORMS[$i]}; do
    ROOTDIR="${BUILDDIR}/${PLATFORM}"
    mkdir -p "${ROOTDIR}"

    ARCH="${PLATFORM##*-}"
    case "${PLATFORM}" in
      iPhone*)
        sdk="${SDK[$IOS]}"
        ;;
      MacOS*)
        sdk="${SDK[$MACOS]}"
        ;;
      *)
        echo "Unrecognized platform: ${PLATFORM}!"
        exit 1
        ;;
    esac

    SDKROOT="${PLATFORMSROOT}/${PLATFORM%%-*}.platform/"
    SDKROOT+="Developer/SDKs/${PLATFORM%%-*}${sdk}.sdk/"
    CFLAGS="-pipe -isysroot ${SDKROOT} -O3 -DNDEBUG"
    case "${PLATFORM}" in
      iPhone*)
        CFLAGS+=" -fembed-bitcode"
        CFLAGS+=" -target ${ARCH}-apple-ios${IOS_MIN_VERSION}"
        [[ "${PLATFORM}" == *Simulator* ]] && CFLAGS+="-simulator"
        ;;
      MacOSX-Catalyst*)
        CFLAGS+=" -target"
        CFLAGS+=" ${ARCH}-apple-ios${MACOSX_CATALYST_MIN_VERSION}-macabi"
        ;;
      MacOSX*)
        CFLAGS+=" -mmacosx-version-min=${MACOSX_MIN_VERSION}"
        ;;
    esac

    set -x
    export PATH="${DEVROOT}/usr/bin:${OLDPATH}"
    ${SRCDIR}/configure --host=${ARCH/arm64/aarch64}-apple-darwin \
      --build=$(${SRCDIR}/config.guess) \
      --prefix=${ROOTDIR} \
      --disable-shared --enable-static \
      --enable-libwebpdecoder --enable-swap-16bit-csp \
      --enable-libwebpmux \
      CC="clang -arch ${ARCH}" \
      CFLAGS="${CFLAGS}"
    set +x

    # Build only the libraries, skip the examples.
    make V=0 -C sharpyuv install
    make V=0 -C src install

    LIBLIST+=("${ROOTDIR}/lib/libwebp.a")
    DECLIBLIST+=("${ROOTDIR}/lib/libwebpdecoder.a")
    MUXLIBLIST+=("${ROOTDIR}/lib/libwebpmux.a")
    DEMUXLIBLIST+=("${ROOTDIR}/lib/libwebpdemux.a")
    SHARPYUVLIBLIST+=("${ROOTDIR}/lib/libsharpyuv.a")
    # xcodebuild requires a directory for the -headers option, these will match
    # for all builds.
    make -C src install-data DESTDIR="${ROOTDIR}/lib-headers"
    make -C src install-commonHEADERS DESTDIR="${ROOTDIR}/dec-headers"
    make -C src/demux install-data DESTDIR="${ROOTDIR}/demux-headers"
    make -C src/mux install-data DESTDIR="${ROOTDIR}/mux-headers"
    make -C sharpyuv install-data DESTDIR="${ROOTDIR}/sharpyuv-headers"
    LIB_HEADERS="${ROOTDIR}/lib-headers/${ROOTDIR}/include/webp"
    DEC_HEADERS="${ROOTDIR}/dec-headers/${ROOTDIR}/include/webp"
    DEMUX_HEADERS="${ROOTDIR}/demux-headers/${ROOTDIR}/include/webp"
    MUX_HEADERS="${ROOTDIR}/mux-headers/${ROOTDIR}/include/webp"
    SHARPYUV_HEADERS="${ROOTDIR}/sharpyuv-headers/${ROOTDIR}/include/webp"

    make distclean

    export PATH=${OLDPATH}
  done

  [[ -z "${LIBLIST[@]}" ]] && continue

  # Create a temporary target directory for each <platform>[-<variant>].
  target_dir="${BUILDDIR}/${PLATFORMS[$i]}"
  target_dir="${target_dir%% *}"
  target_dir="${target_dir%-*}"
  target_lib="${target_dir}/$(basename ${LIBLIST[0]})"
  target_declib="${target_dir}/$(basename ${DECLIBLIST[0]})"
  target_demuxlib="${target_dir}/$(basename ${DEMUXLIBLIST[0]})"
  target_muxlib="${target_dir}/$(basename ${MUXLIBLIST[0]})"
  target_sharpyuvlib="${target_dir}/$(basename ${SHARPYUVLIBLIST[0]})"

  mkdir -p "${target_dir}"
  ${LIPO} -create ${LIBLIST[@]} -output "${target_lib}"
  ${LIPO} -create ${DECLIBLIST[@]} -output "${target_declib}"
  ${LIPO} -create ${DEMUXLIBLIST[@]} -output "${target_demuxlib}"
  ${LIPO} -create ${MUXLIBLIST[@]} -output "${target_muxlib}"
  ${LIPO} -create ${SHARPYUVLIBLIST[@]} -output "${target_sharpyuvlib}"
  FAT_LIBLIST+=(-library "${target_lib}" -headers "${LIB_HEADERS}")
  FAT_DECLIBLIST+=(-library "${target_declib}" -headers "${DEC_HEADERS}")
  FAT_DEMUXLIBLIST+=(-library "${target_demuxlib}" -headers "${DEMUX_HEADERS}")
  FAT_MUXLIBLIST+=(-library "${target_muxlib}" -headers "${MUX_HEADERS}")
  FAT_SHARPYUVLIBLIST+=(-library "${target_sharpyuvlib}")
  FAT_SHARPYUVLIBLIST+=(-headers "${SHARPYUV_HEADERS}")
done

# lipo will not put archives with the same architecture (e.g., x86_64
# iPhoneSimulator & MacOS) in the same fat output file. xcodebuild
# -create-xcframework requires universal archives to avoid e.g.:
#   Both ios-x86_64-maccatalyst and ios-arm64-maccatalyst represent two
#   equivalent library definitions
set -x
xcodebuild -create-xcframework "${FAT_LIBLIST[@]}" \
  -output ${TARGETDIR}
xcodebuild -create-xcframework "${FAT_DECLIBLIST[@]}" \
  -output ${DECTARGETDIR}
xcodebuild -create-xcframework "${FAT_DEMUXLIBLIST[@]}" \
  -output ${DEMUXTARGETDIR}
xcodebuild -create-xcframework "${FAT_MUXLIBLIST[@]}" \
  -output ${MUXTARGETDIR}
xcodebuild -create-xcframework "${FAT_SHARPYUVLIBLIST[@]}" \
  -output ${SHARPYUVTARGETDIR}
update_headers_path "${TARGETDIR}"
update_headers_path "${DECTARGETDIR}"
update_headers_path "${DEMUXTARGETDIR}"
update_headers_path "${MUXTARGETDIR}"
update_headers_path "${SHARPYUVTARGETDIR}"
set +x

echo  "SUCCESS"

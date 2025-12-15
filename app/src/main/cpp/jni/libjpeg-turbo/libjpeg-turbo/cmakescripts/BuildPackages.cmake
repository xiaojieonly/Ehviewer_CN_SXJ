# This file is included from the top-level CMakeLists.txt.  We just store it
# here to avoid cluttering up that file.

set(PKGNAME ${CMAKE_PROJECT_NAME} CACHE STRING
  "Distribution package name (default: ${CMAKE_PROJECT_NAME})")
set(PKGVENDOR "The ${CMAKE_PROJECT_NAME} Project" CACHE STRING
  "Vendor name to be included in distribution package descriptions (default: The ${CMAKE_PROJECT_NAME} Project)")
set(PKGURL "http://www.${CMAKE_PROJECT_NAME}.org" CACHE STRING
  "URL of project web site to be included in distribution package descriptions (default: http://www.${CMAKE_PROJECT_NAME}.org)")
set(PKGEMAIL "information@${CMAKE_PROJECT_NAME}.org" CACHE STRING
  "E-mail of project maintainer to be included in distribution package descriptions (default: information@${CMAKE_PROJECT_NAME}.org")
set(PKGID "com.${CMAKE_PROJECT_NAME}.${PKGNAME}" CACHE STRING
  "Globally unique package identifier (reverse DNS notation) (default: com.${CMAKE_PROJECT_NAME}.${PKGNAME})")


###############################################################################
# Linux RPM and DEB
###############################################################################

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")

set(RPMARCH ${CMAKE_SYSTEM_PROCESSOR})
if(CPU_TYPE STREQUAL "x86_64")
  set(DEBARCH amd64)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "armv7*")
  set(DEBARCH armhf)
elseif(CPU_TYPE STREQUAL "arm64")
  set(DEBARCH ${CPU_TYPE})
elseif(CPU_TYPE STREQUAL "arm")
  set(DEBARCH armel)
elseif(CMAKE_SYSTEM_PROCESSOR_LC STREQUAL "ppc64le")
  set(DEBARCH ppc64el)
elseif(CPU_TYPE STREQUAL "powerpc" AND BITS EQUAL 32)
  set(RPMARCH ppc)
  set(DEBARCH ppc)
else()
  set(DEBARCH ${CMAKE_SYSTEM_PROCESSOR})
endif()
message(STATUS "RPM architecture = ${RPMARCH}, DEB architecture = ${DEBARCH}")

# Re-set CMAKE_POSITION_INDEPENDENT_CODE so that the RPM spec file works
# properly
boolean_number(CMAKE_POSITION_INDEPENDENT_CODE)

configure_file(release/makerpm.in pkgscripts/makerpm)
configure_file(release/rpm.spec.in pkgscripts/rpm.spec @ONLY)

add_custom_target(rpm sh pkgscripts/makerpm
  SOURCES pkgscripts/makerpm)

configure_file(release/makesrpm.in pkgscripts/makesrpm)

add_custom_target(srpm sh pkgscripts/makesrpm
  SOURCES pkgscripts/makesrpm
  DEPENDS dist)

configure_file(release/makedpkg.in pkgscripts/makedpkg)
configure_file(release/deb-control.in pkgscripts/deb-control)

add_custom_target(deb sh pkgscripts/makedpkg
  SOURCES pkgscripts/makedpkg)

endif() # Linux


###############################################################################
# Windows installer (NullSoft Installer)
###############################################################################

if(WIN32)

if(MSVC)
  set(INST_PLATFORM "Visual C++")
  set(INST_NAME ${CMAKE_PROJECT_NAME}-${VERSION}-vc)
  set(INST_REG_NAME ${CMAKE_PROJECT_NAME})
elseif(MINGW)
  set(INST_PLATFORM GCC)
  set(INST_NAME ${CMAKE_PROJECT_NAME}-${VERSION}-gcc)
  set(INST_REG_NAME ${CMAKE_PROJECT_NAME}-gcc)
  set(INST_DEFS -DGCC)
endif()

if(BITS EQUAL 64)
  set(INST_PLATFORM "${INST_PLATFORM} 64-bit")
  set(INST_NAME ${INST_NAME}64)
  set(INST_REG_NAME ${INST_DIR}64)
  set(INST_DEFS ${INST_DEFS} -DWIN64)
endif()

if(WITH_JAVA)
  set(INST_DEFS ${INST_DEFS} -DJAVA)
endif()

if(MSVC_IDE)
  set(INST_DEFS ${INST_DEFS} "-DBUILDDIR=${CMAKE_CFG_INTDIR}\\")
else()
  set(INST_DEFS ${INST_DEFS} "-DBUILDDIR=")
endif()

string(REGEX REPLACE "/" "\\\\" INST_DIR ${CMAKE_INSTALL_PREFIX})

configure_file(release/installer.nsi.in installer.nsi @ONLY)

if(WITH_JAVA)
  set(JAVA_DEPEND turbojpeg-java)
endif()
add_custom_target(installer
  makensis -nocd ${INST_DEFS} installer.nsi
  DEPENDS jpeg jpeg-static turbojpeg turbojpeg-static rdjpgcom wrjpgcom
    cjpeg djpeg jpegtran tjbench ${JAVA_DEPEND}
  SOURCES installer.nsi)

endif() # WIN32


###############################################################################
# Cygwin Package
###############################################################################

if(CYGWIN)

configure_file(release/makecygwinpkg.in pkgscripts/makecygwinpkg)

add_custom_target(cygwinpkg sh pkgscripts/makecygwinpkg)

endif() # CYGWIN


###############################################################################
# Mac DMG
###############################################################################

if(APPLE)

set(DEFAULT_OSX_32BIT_BUILD ${CMAKE_SOURCE_DIR}/osxx86)
set(OSX_32BIT_BUILD ${DEFAULT_OSX_32BIT_BUILD} CACHE PATH
  "Directory containing 32-bit (i386) Mac build to include in universal binaries (default: ${DEFAULT_OSX_32BIT_BUILD})")
set(DEFAULT_IOS_ARMV7_BUILD ${CMAKE_SOURCE_DIR}/iosarmv7)
set(IOS_ARMV7_BUILD ${DEFAULT_IOS_ARMV7_BUILD} CACHE PATH
  "Directory containing ARMv7 iOS build to include in universal binaries (default: ${DEFAULT_IOS_ARMV7_BUILD})")
set(DEFAULT_IOS_ARMV7S_BUILD ${CMAKE_SOURCE_DIR}/iosarmv7s)
set(IOS_ARMV7S_BUILD ${DEFAULT_IOS_ARMV7S_BUILD} CACHE PATH
  "Directory containing ARMv7s iOS build to include in universal binaries (default: ${DEFAULT_IOS_ARMV7S_BUILD})")
set(DEFAULT_IOS_ARMV8_BUILD ${CMAKE_SOURCE_DIR}/iosarmv8)
set(IOS_ARMV8_BUILD ${DEFAULT_IOS_ARMV8_BUILD} CACHE PATH
  "Directory containing ARMv8 iOS build to include in universal binaries (default: ${DEFAULT_IOS_ARMV8_BUILD})")

configure_file(release/makemacpkg.in pkgscripts/makemacpkg)
configure_file(release/Distribution.xml.in pkgscripts/Distribution.xml)
configure_file(release/uninstall.in pkgscripts/uninstall)

add_custom_target(dmg sh pkgscripts/makemacpkg
  SOURCES pkgscripts/makemacpkg)

add_custom_target(udmg sh pkgscripts/makemacpkg universal
  SOURCES pkgscripts/makemacpkg)

endif() # APPLE


###############################################################################
# Generic
###############################################################################

add_custom_target(dist
  COMMAND git archive --prefix=${CMAKE_PROJECT_NAME}-${VERSION}/ HEAD |
    gzip > ${CMAKE_CURRENT_BINARY_DIR}/${CMAKE_PROJECT_NAME}-${VERSION}.tar.gz
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})

configure_file(release/maketarball.in pkgscripts/maketarball)

add_custom_target(tarball sh pkgscripts/maketarball
  SOURCES pkgscripts/maketarball)

configure_file(release/libjpeg.pc.in pkgscripts/libjpeg.pc @ONLY)

configure_file(release/libturbojpeg.pc.in pkgscripts/libturbojpeg.pc @ONLY)

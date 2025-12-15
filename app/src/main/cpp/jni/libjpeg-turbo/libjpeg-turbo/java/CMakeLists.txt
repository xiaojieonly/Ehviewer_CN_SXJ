find_package(Java REQUIRED)
find_package(JNI REQUIRED)

# Allow the Java compiler flags to be set using an environment variable
if(NOT DEFINED CMAKE_JAVA_COMPILE_FLAGS AND DEFINED ENV{JAVAFLAGS})
  set(CMAKE_JAVA_COMPILE_FLAGS $ENV{JAVAFLAGS})
endif()

include(UseJava)

set(CMAKE_JAVA_COMPILE_FLAGS "${CMAKE_JAVA_COMPILE_FLAGS} -J-Dfile.encoding=UTF8")
message(STATUS "CMAKE_JAVA_COMPILE_FLAGS = ${CMAKE_JAVA_COMPILE_FLAGS}")
string(REGEX REPLACE " " ";" CMAKE_JAVA_COMPILE_FLAGS "${CMAKE_JAVA_COMPILE_FLAGS}")

set(JAVAARGS "" CACHE STRING "Additional arguments to pass to java when running unit tests (example: -d32)")
message(STATUS "JAVAARGS = ${JAVAARGS}")

set(JAVA_SOURCES org/libjpegturbo/turbojpeg/TJ.java
  org/libjpegturbo/turbojpeg/TJCompressor.java
  org/libjpegturbo/turbojpeg/TJCustomFilter.java
  org/libjpegturbo/turbojpeg/TJDecompressor.java
  org/libjpegturbo/turbojpeg/TJException.java
  org/libjpegturbo/turbojpeg/TJScalingFactor.java
  org/libjpegturbo/turbojpeg/TJTransform.java
  org/libjpegturbo/turbojpeg/TJTransformer.java
  org/libjpegturbo/turbojpeg/YUVImage.java
  TJUnitTest.java
  TJExample.java
  TJBench.java)

set(TURBOJPEG_DLL_NAME "turbojpeg")
if(MINGW)
  set(TURBOJPEG_DLL_NAME "libturbojpeg")
endif()
if(WIN32)
  configure_file(org/libjpegturbo/turbojpeg/TJLoader-win.java.in
    ${CMAKE_CURRENT_BINARY_DIR}/org/libjpegturbo/turbojpeg/TJLoader.java)
else()
  configure_file(org/libjpegturbo/turbojpeg/TJLoader-unix.java.in
    ${CMAKE_CURRENT_BINARY_DIR}/org/libjpegturbo/turbojpeg/TJLoader.java)
endif()
set(JAVA_SOURCES ${JAVA_SOURCES}
  ${CMAKE_CURRENT_BINARY_DIR}/org/libjpegturbo/turbojpeg/TJLoader.java)

if(MSYS)
  # UGLY HACK ALERT: If we don't do this, then UseJava.cmake will separate
  # class path members with a semicolon, which is interpreted as a command
  # separator by the MSYS shell.
  set(CMAKE_HOST_SYSTEM_NAME_BAK ${CMAKE_HOST_SYSTEM_NAME})
  set(CMAKE_HOST_SYSTEM_NAME "MSYS")
endif()
add_jar(turbojpeg-java ${JAVA_SOURCES} OUTPUT_NAME turbojpeg
  ENTRY_POINT TJExample)
if(MSYS)
  set(CMAKE_HOST_SYSTEM_NAME ${CMAKE_HOST_SYSTEM_NAME_BAK})
endif()

add_custom_target(javadoc COMMAND
  javadoc -notimestamp -d ${CMAKE_CURRENT_SOURCE_DIR}/doc -sourcepath ${CMAKE_CURRENT_SOURCE_DIR} org.libjpegturbo.turbojpeg)
set(JAVACLASSPATH ${CMAKE_CURRENT_BINARY_DIR}/${CMAKE_FILES_DIRECTORY}/turbojpeg-java.dir)
add_custom_target(javah
  COMMAND javah -d ${CMAKE_CURRENT_SOURCE_DIR} -classpath ${JAVACLASSPATH} org.libjpegturbo.turbojpeg.TJ
  COMMAND javah -d ${CMAKE_CURRENT_SOURCE_DIR} -classpath ${JAVACLASSPATH} org.libjpegturbo.turbojpeg.TJCompressor
  COMMAND javah -d ${CMAKE_CURRENT_SOURCE_DIR} -classpath ${JAVACLASSPATH} org.libjpegturbo.turbojpeg.TJDecompressor
  COMMAND javah -d ${CMAKE_CURRENT_SOURCE_DIR} -classpath ${JAVACLASSPATH} org.libjpegturbo.turbojpeg.TJTransformer)

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_JAVADIR)
  set(CMAKE_INSTALL_DEFAULT_JAVADIR "<CMAKE_INSTALL_DATAROOTDIR>/java")
endif()
GNUInstallDirs_set_install_dir(JAVADIR
  "The directory into which Java classes should be installed")
GNUInstallDirs_get_absolute_install_dir(CMAKE_INSTALL_FULL_JAVADIR
  CMAKE_INSTALL_JAVADIR)
set(CMAKE_INSTALL_JAVADIR ${CMAKE_INSTALL_JAVADIR} PARENT_SCOPE)
set(CMAKE_INSTALL_FULL_JAVADIR ${CMAKE_INSTALL_FULL_JAVADIR} PARENT_SCOPE)
report_directory(JAVADIR)
install_jar(turbojpeg-java ${CMAKE_INSTALL_JAVADIR})
mark_as_advanced(CLEAR CMAKE_INSTALL_JAVADIR)

file(GLOB FILES
  testout*
  *_GRAY_*.bmp
  *_GRAY_*.png
  *_GRAY_*.ppm
  *_GRAY_*.jpg
  *_GRAY.yuv
  *_420_*.bmp
  *_420_*.png
  *_420_*.ppm
  *_420_*.jpg
  *_420.yuv
  *_422_*.bmp
  *_422_*.png
  *_422_*.ppm
  *_422_*.jpg
  *_422.yuv
  *_444_*.bmp
  *_444_*.png
  *_444_*.ppm
  *_444_*.jpg
  *_444.yuv
  *_440_*.bmp
  *_440_*.png
  *_440_*.ppm
  *_440_*.jpg
  *_440.yuv
  *_411_*.bmp
  *_411_*.png
  *_411_*.ppm
  *_411_*.jpg
  *_411.yuv
  tjbenchtest*.log
  tjexampletest*.log)

if(NOT FILES STREQUAL "")
  message(STATUS "Removing test files")
  file(REMOVE ${FILES})
else()
  message(STATUS "No files to remove")
endif()

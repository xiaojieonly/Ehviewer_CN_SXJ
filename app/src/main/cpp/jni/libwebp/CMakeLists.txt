cmake_minimum_required(VERSION 3.18.1)
project(libwebp C)

file(GLOB LIBWEBP_DEC_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/dec/*.c")
file(GLOB LIBWEBP_DSP_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/dsp/*.c")
file(GLOB LIBWEBP_ENC_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/enc/*.c")
file(GLOB LIBWEBP_MUX_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/mux/*.c")
file(GLOB LIBWEBP_DEMUX_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/demux/*.c")
file(GLOB LIBWEBP_UTILS_SRC "${CMAKE_CURRENT_SOURCE_DIR}/src/utils/*.c")
file(GLOB LIBWEBP_SHARPYUV_SRC "${CMAKE_CURRENT_SOURCE_DIR}/sharpyuv/*.c")

set(LIBWEBP_SOURCES
    ${LIBWEBP_DEC_SRC}
    ${LIBWEBP_DSP_SRC}
    ${LIBWEBP_ENC_SRC}
    ${LIBWEBP_MUX_SRC}
    ${LIBWEBP_DEMUX_SRC}
    ${LIBWEBP_UTILS_SRC}
    ${LIBWEBP_SHARPYUV_SRC}
)

add_library(libwebp STATIC ${LIBWEBP_SOURCES})
target_include_directories(libwebp PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_compile_definitions(libwebp PRIVATE WEBP_USE_THREAD HAVE_PTHREAD HAVE_MALLOC_H)
target_link_libraries(libwebp PUBLIC m)
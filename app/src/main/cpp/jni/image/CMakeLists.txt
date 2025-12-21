# Copyright 2018 Hippo Seven
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 3.14)
project(image C)

set(IMAGE_SOURCES
        image.c
        image_utils.c
        image_plain.c
        image_jpeg.c
        image_png.c
        image_gif.c
        image_webp.c
        filter/clahe.c
        filter/gray.c
        java_wrapper.c
)
#set(IMAGE_INCLUDES
#    ${CMAKE_CURRENT_SOURCE_DIR}
#    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../javah
#)
set(IMAGE_LIBRARIES
        log
        jnigraphics
        GLESv2
        stream
        libjpeg-turbo
        libpng
        giflib
        libwebp
)

add_library(image SHARED ${IMAGE_SOURCES})
target_include_directories(image PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../libwebp/src
)

# 确保生成的 libimage.so 在 16KB 页大小设备上 LOAD 段按 16KB 对齐
target_link_options(image PRIVATE
        -Wl,-z,max-page-size=16384
)
target_link_libraries(image PRIVATE ${IMAGE_LIBRARIES})

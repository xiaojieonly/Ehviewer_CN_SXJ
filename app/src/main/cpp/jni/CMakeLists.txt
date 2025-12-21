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

cmake_minimum_required(VERSION 3.18.1)
project(root)

add_subdirectory(cpufeatures)
add_subdirectory(stream)
add_subdirectory(libjpeg-turbo)
add_subdirectory(libpng)
add_subdirectory(giflib)
add_subdirectory(libwebp)
add_subdirectory(image)

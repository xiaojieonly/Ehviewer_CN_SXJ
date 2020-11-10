/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

public class EhCacheKeyFactory {

    public static String getThumbKey(long gid) {
        return "preview:large:" + gid + ":" + 0; // "thumb:" + gid;
    }

    public static String getNormalPreviewKey(long gid, int index) {
        return "preview:normal:" + gid + ":" + index;
    }

    public static String getLargePreviewKey(long gid, int index) {
        return "preview:large:" + gid + ":" + index;
    }

    public static String getLargePreviewSetKey(long gid, int index) {
        return "large_preview_set:" + gid + ":" + index;
    }

    public static String getImageKey(long gid, int index) {
        return "image:" + gid + ":" + index;
    }
}

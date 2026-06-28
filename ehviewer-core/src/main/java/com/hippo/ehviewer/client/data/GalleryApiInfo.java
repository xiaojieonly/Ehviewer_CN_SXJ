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

package com.hippo.ehviewer.client.data;

public class GalleryApiInfo {

    public long gid;
    public String token;
    public String archiverKey;
    public String title;
    public String titleJpn;
    public int category;
    public String thumb;
    public String uploader;
    public long posted;
    public int filecount;
    public long filesize;
    public boolean expunged;
    public float rating;
    public int torrentcount;
    @Nullable
    public String[] tags;

    public GalleryApiInfo() {
    }
}

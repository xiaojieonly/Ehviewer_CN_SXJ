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

import com.hippo.util.DataUtils;

import java.util.Arrays;

/**
 * 画廊参数存储对象
 */
public class GalleryDetail extends GalleryInfo {

    public long apiUid = -1L;
    public String apiKey;
    public int torrentCount;
    public String torrentUrl;
    public String archiveUrl;
    public String parent;
    public String visible;
    public String language;
    public String size;
//    public String updateUrl;
//    public int pages;
    public int SpiderInfoPages;

    public int favoriteCount;
    public boolean isFavorited;
    public int ratingCount;
    public GalleryTagGroup[] tags;
    public GalleryCommentList comments;
    public int previewPages;
    public int SpiderInfoPreviewPages;
    public PreviewSet previewSet;
    public PreviewSet SpiderInfoPreviewSet;

//    public String body;
//    //    public GalleryDetail oldDetail;

    public NewVersion[] newVersions;

    public GalleryDetail() {
    }

    public GalleryDetail getNewGalleryDetail(int index) {
       try{
           GalleryDetail n = DataUtils.copy(this);
           if (newVersions==null){
               return n;
           }
           String updateUrl = newVersions[index].versionUrl;
           String[] params = updateUrl.split("/");
           int length = params.length;
           n.token = params[length-1];
           n.gid = Long.parseLong(params[length-2]);
           n.newVersions = null;
           return n;
       }catch (Throwable e){
           return this;
       }
    }

    public String[] getUpdateVersionName(){
        String[] result = new String[newVersions.length];
        for (int i = 0; i < newVersions.length; i++) {
            result[i] = newVersions[i].versionName;
        }
        return result;
    }
}

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

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser;
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.StringUtils;

import android.text.TextUtils;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalGalleryInfo implements Parcelable {
    
    public static final int TYPE_LOCAL = 0;
    public static final int TYPE_RECYCLE_BIN = 1;
    
    private static final Pattern GID_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("([a-z0-9]{10})");
    
    public long id;
    public String title;
    public String titleJpn;
    public String category;
    public String thumb;
    public String path;
    public int type;
    public long timestamp;
    public int pageCount;
    public long size;
    public String gid;
    public String token;
    public Drawable thumbnail;
    
    public LocalGalleryInfo() {
        id = System.currentTimeMillis();
        type = TYPE_LOCAL;
        timestamp = System.currentTimeMillis();
    }
    
    public LocalGalleryInfo(String path) {
        this();
        this.path = path;
        decodeFromPath(path);
    }
    
    private void decodeFromPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        
        File file = new File(path);
        String folderName = file.getName();
        
        // 尝试从文件夹名称解析画廊信息
        // 格式通常是: [GID]TOKEN - Title
        try {
            // 提取GID
            Matcher gidMatcher = GID_PATTERN.matcher(folderName);
            if (gidMatcher.find()) {
                gid = gidMatcher.group(1);
            }
            
            // 提取Token
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(folderName);
            if (tokenMatcher.find()) {
                token = tokenMatcher.group(1);
            }
            
            // 提取标题
            int titleStart = folderName.indexOf(" - ");
            if (titleStart != -1) {
                title = folderName.substring(titleStart + 3).trim();
            } else {
                title = folderName;
            }
            
            // 计算页面数量和大小
            calculateStats();
            
        } catch (Exception e) {
            // 如果解析失败，使用文件夹名作为标题
            title = folderName;
            calculateStats();
        }
    }
    
    private void calculateStats() {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        pageCount = 0;
        size = 0;
        
        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                // 只计算图片文件
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                    name.endsWith(".png") || name.endsWith(".gif") || 
                    name.endsWith(".webp")) {
                    pageCount++;
                    size += file.length();
                    
                    // 设置第一张图片作为缩略图
                    if (TextUtils.isEmpty(thumb)) {
                        thumb = file.getAbsolutePath();
                    }
                }
            }
        }
    }
    
    public boolean isValid() {
        return !TextUtils.isEmpty(path) && new File(path).exists() && pageCount > 0;
    }
    
    public String getDisplayTitle() {
        return !TextUtils.isEmpty(titleJpn) ? titleJpn : title;
    }
    
    public String getFormattedSize() {
        return android.text.format.Formatter.formatFileSize(null, size);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(titleJpn);
        dest.writeString(category);
        dest.writeString(thumb);
        dest.writeString(path);
        dest.writeInt(type);
        dest.writeLong(timestamp);
        dest.writeInt(pageCount);
        dest.writeLong(size);
        dest.writeString(gid);
        dest.writeString(token);
    }
    
    public static final Creator<LocalGalleryInfo> CREATOR = new Creator<LocalGalleryInfo>() {
        @Override
        public LocalGalleryInfo createFromParcel(Parcel in) {
            LocalGalleryInfo info = new LocalGalleryInfo();
            info.id = in.readLong();
            info.title = in.readString();
            info.titleJpn = in.readString();
            info.category = in.readString();
            info.thumb = in.readString();
            info.path = in.readString();
            info.type = in.readInt();
            info.timestamp = in.readLong();
            info.pageCount = in.readInt();
            info.size = in.readLong();
            info.gid = in.readString();
            info.token = in.readString();
            return info;
        }
        
        @Override
        public LocalGalleryInfo[] newArray(int size) {
            return new LocalGalleryInfo[size];
        }
    };
}
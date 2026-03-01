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

package com.hippo.ehviewer.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.unifile.UniFile;
import com.hippo.beerbelly.BeerBelly;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 画廊缓存管理器
 * 负责管理画廊详细信息的缓存，包括标签、缩略图等
 * 缓存保存在下载目录的.ehviewer.extra.json文件中
 */
public class GalleryCacheManager {
    
    private static final String TAG = "GalleryCacheManager";
    public static final String GALLERY_CACHE_FILENAME = ".ehviewer.extra.json";
    
    private static final String KEY_GID = "gid";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TITLE_JPN = "titleJpn";
    private static final String KEY_THUMB = "thumb";
    private static final String KEY_THUMB_BASE64 = "thumbBase64";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_POSTED = "posted";
    private static final String KEY_UPLOADER = "uploader";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RATING_COUNT = "ratingCount";
    private static final String KEY_SIMPLE_LANGUAGE = "simpleLanguage";
    private static final String KEY_PAGES = "pages";
    private static final String KEY_SIZE = "size";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FAVORITE_COUNT = "favoriteCount";
    private static final String KEY_IS_FAVORITED = "isFavorited";
    private static final String KEY_TORRENT_COUNT = "torrentCount";
    private static final String KEY_TORRENT_URL = "torrentUrl";
    private static final String KEY_ARCHIVE_URL = "archiveUrl";
    private static final String KEY_PARENT = "parent";
    private static final String KEY_VISIBLE = "visible";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_IS_DELETED = "isDeleted";
    private static final String KEY_CACHE_TIME = "cacheTime";
    
    private static GalleryCacheManager sInstance;
    private final Context mContext;
    
    private GalleryCacheManager(Context context) {
        mContext = context.getApplicationContext();
    }
    
    public static GalleryCacheManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new GalleryCacheManager(context);
        }
        return sInstance;
    }
    
    /**
     * 从下载目录读取画廊缓存
     * @param gid 画廊ID
     * @return 缓存的GalleryDetail，如果不存在则返回null
     */
    @Nullable
    public GalleryDetail readGalleryCache(long gid) {
        UniFile downloadDir = SpiderDen.getGalleryDownloadDir(new GalleryInfo() {{
            this.gid = gid;
        }});
        
        if (downloadDir == null || !downloadDir.isDirectory()) {
            Log.d(TAG, "下载目录不存在: GID " + gid);
            return null;
        }
        
        UniFile cacheFile = downloadDir.findFile(GALLERY_CACHE_FILENAME);
        if (cacheFile == null) {
            Log.d(TAG, "缓存文件不存在: GID " + gid);
            return null;
        }
        
        try {
            String jsonContent = readTextFile(cacheFile);
            if (jsonContent == null || jsonContent.isEmpty()) {
                Log.w(TAG, "缓存文件内容为空: GID " + gid);
                return null;
            }
            
            JSONObject jsonObject = JSON.parseObject(jsonContent);
            if (jsonObject == null || jsonObject.getLongValue(KEY_GID) != gid) {
                Log.w(TAG, "缓存文件内容无效或GID不匹配: GID " + gid);
                return null;
            }
            
            return parseJsonToGalleryDetail(jsonObject);
        } catch (Exception e) {
            Log.e(TAG, "读取画廊缓存失败: GID " + gid, e);
            return null;
        }
    }
    
    /**
     * 将画廊详细信息保存到缓存
     * @param galleryDetail 画廊详细信息
     * @return 是否保存成功
     */
    public boolean saveGalleryCache(@NonNull GalleryDetail galleryDetail) {
        UniFile downloadDir = SpiderDen.getGalleryDownloadDir(galleryDetail);
        if (downloadDir == null) {
            Log.e(TAG, "无法获取下载目录: " + galleryDetail.title);
            return false;
        }
        
        if (!downloadDir.ensureDir()) {
            Log.e(TAG, "无法创建下载目录: " + galleryDetail.title);
            return false;
        }
        
        try {
            JSONObject jsonObject = convertGalleryDetailToJson(galleryDetail);
            if (jsonObject == null) {
                Log.e(TAG, "转换GalleryDetail到JSON失败: " + galleryDetail.title);
                return false;
            }
            
            // 添加缓存时间戳
            jsonObject.put(KEY_CACHE_TIME, System.currentTimeMillis());
            
            UniFile cacheFile = downloadDir.createFile(GALLERY_CACHE_FILENAME);
            if (cacheFile == null) {
                Log.e(TAG, "无法创建缓存文件: " + galleryDetail.title);
                return false;
            }
            
            String jsonContent = JSON.toJSONString(jsonObject, true);
            return writeTextFile(cacheFile, jsonContent);
        } catch (Exception e) {
            Log.e(TAG, "保存画廊缓存失败: " + galleryDetail.title, e);
            return false;
        }
    }
    
    /**
     * 标记画廊为已删除
     * @param gid 画廊ID
     * @return 是否标记成功
     */
    public boolean markGalleryAsDeleted(long gid) {
        GalleryDetail cache = readGalleryCache(gid);
        if (cache == null) {
            Log.w(TAG, "尝试标记不存在的缓存为已删除: GID " + gid);
            return false;
        }
        
        // 设置删除标记
        cache.visible = "deleted";
        
        return saveGalleryCache(cache);
    }
    
    /**
     * 检查画廊是否已被标记为删除
     * @param gid 画廊ID
     * @return 是否已被删除
     */
    public boolean isGalleryMarkedAsDeleted(long gid) {
        GalleryDetail cache = readGalleryCache(gid);
        return cache != null && "deleted".equals(cache.visible);
    }
    
    /**
     * 将缩略图转换为Base64编码的PNG格式
     * @param gid 画廊ID
     * @return Base64编码的缩略图，转换失败则返回null
     */
    @Nullable
    public String convertThumbToBase64(long gid) {
        try {
            // 尝试从缓存获取缩略图
            BeerBelly beerBelly = EhApplication.getConaco(mContext).getBeerBelly();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            if (beerBelly.pullFromDiskCache(EhCacheKeyFactory.getThumbKey(gid), outputStream)) {
                byte[] thumbData = outputStream.toByteArray();
                return Base64.encodeToString(thumbData, Base64.NO_WRAP);
            }
            
            Log.d(TAG, "无法从缓存获取缩略图: GID " + gid);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "转换缩略图为Base64失败: GID " + gid, e);
            return null;
        }
    }
    
    /**
     * 将GalleryDetail对象转换为JSON
     */
    @Nullable
    private JSONObject convertGalleryDetailToJson(@NonNull GalleryDetail galleryDetail) {
        try {
            JSONObject jsonObject = new JSONObject();
            
            // 基本信息
            jsonObject.put(KEY_GID, galleryDetail.gid);
            jsonObject.put(KEY_TOKEN, galleryDetail.token);
            jsonObject.put(KEY_TITLE, galleryDetail.title);
            jsonObject.put(KEY_TITLE_JPN, galleryDetail.titleJpn);
            jsonObject.put(KEY_THUMB, galleryDetail.thumb);
            jsonObject.put(KEY_CATEGORY, galleryDetail.category);
            jsonObject.put(KEY_POSTED, galleryDetail.posted);
            jsonObject.put(KEY_UPLOADER, galleryDetail.uploader);
            jsonObject.put(KEY_RATING, galleryDetail.rating);
            jsonObject.put(KEY_RATING_COUNT, galleryDetail.ratingCount);
            jsonObject.put(KEY_SIMPLE_LANGUAGE, galleryDetail.simpleLanguage);
            jsonObject.put(KEY_PAGES, galleryDetail.pages);
            jsonObject.put(KEY_SIZE, galleryDetail.size);
            jsonObject.put(KEY_LANGUAGE, galleryDetail.language);
            jsonObject.put(KEY_FAVORITE_COUNT, galleryDetail.favoriteCount);
            jsonObject.put(KEY_IS_FAVORITED, galleryDetail.isFavorited);
            jsonObject.put(KEY_TORRENT_COUNT, galleryDetail.torrentCount);
            jsonObject.put(KEY_TORRENT_URL, galleryDetail.torrentUrl);
            jsonObject.put(KEY_ARCHIVE_URL, galleryDetail.archiveUrl);
            jsonObject.put(KEY_PARENT, galleryDetail.parent);
            jsonObject.put(KEY_VISIBLE, galleryDetail.visible);
            
            // 转换缩略图为Base64
            String thumbBase64 = convertThumbToBase64(galleryDetail.gid);
            if (thumbBase64 != null) {
                jsonObject.put(KEY_THUMB_BASE64, thumbBase64);
            }
            
            // 转换标签
            if (galleryDetail.tags != null && galleryDetail.tags.length > 0) {
                jsonObject.put(KEY_TAGS, convertTagsToJson(galleryDetail.tags));
            }
            
            // 检查是否已被删除
            if (isGalleryMarkedAsDeleted(galleryDetail.gid)) {
                jsonObject.put(KEY_IS_DELETED, true);
            }
            
            return jsonObject;
        } catch (Exception e) {
            Log.e(TAG, "转换GalleryDetail到JSON失败", e);
            return null;
        }
    }
    
    /**
     * 将JSON转换为GalleryDetail对象
     */
    @Nullable
    private GalleryDetail parseJsonToGalleryDetail(@NonNull JSONObject jsonObject) {
        try {
            GalleryDetail galleryDetail = new GalleryDetail();
            
            // 基本信息
            galleryDetail.gid = jsonObject.getLongValue(KEY_GID);
            galleryDetail.token = jsonObject.getString(KEY_TOKEN);
            galleryDetail.title = jsonObject.getString(KEY_TITLE);
            galleryDetail.titleJpn = jsonObject.getString(KEY_TITLE_JPN);
            galleryDetail.thumb = jsonObject.getString(KEY_THUMB);
            galleryDetail.category = jsonObject.getIntValue(KEY_CATEGORY);
            galleryDetail.posted = jsonObject.getString(KEY_POSTED);
            galleryDetail.uploader = jsonObject.getString(KEY_UPLOADER);
            galleryDetail.rating = jsonObject.getFloatValue(KEY_RATING);
            galleryDetail.ratingCount = jsonObject.getIntValue(KEY_RATING_COUNT);
            galleryDetail.simpleLanguage = jsonObject.getString(KEY_SIMPLE_LANGUAGE);
            galleryDetail.pages = jsonObject.getIntValue(KEY_PAGES);
            galleryDetail.size = jsonObject.getString(KEY_SIZE);
            galleryDetail.language = jsonObject.getString(KEY_LANGUAGE);
            galleryDetail.favoriteCount = jsonObject.getIntValue(KEY_FAVORITE_COUNT);
            galleryDetail.isFavorited = jsonObject.getBooleanValue(KEY_IS_FAVORITED);
            galleryDetail.torrentCount = jsonObject.getIntValue(KEY_TORRENT_COUNT);
            galleryDetail.torrentUrl = jsonObject.getString(KEY_TORRENT_URL);
            galleryDetail.archiveUrl = jsonObject.getString(KEY_ARCHIVE_URL);
            galleryDetail.parent = jsonObject.getString(KEY_PARENT);
            galleryDetail.visible = jsonObject.getString(KEY_VISIBLE);
            
            // 优先使用Base64缩略图
            if (jsonObject.containsKey(KEY_THUMB_BASE64)) {
                galleryDetail.thumb = jsonObject.getString(KEY_THUMB_BASE64);
            }
            
            // 解析标签
            if (jsonObject.containsKey(KEY_TAGS)) {
                galleryDetail.tags = parseJsonToTags(jsonObject.getJSONArray(KEY_TAGS));
            }
            
            return galleryDetail;
        } catch (Exception e) {
            Log.e(TAG, "解析JSON到GalleryDetail失败", e);
            return null;
        }
    }
    
    /**
     * 将标签数组转换为JSON数组
     */
    @Nullable
    private Object convertTagsToJson(@NonNull GalleryTagGroup[] tagGroups) {
        try {
            if (tagGroups.length == 0) {
                return null;
            }
            
            Map<String, Object> tagsMap = new HashMap<>();
            for (GalleryTagGroup tagGroup : tagGroups) {
                if (tagGroup.groupName != null && tagGroup.size() > 0) {
                    String[] tags = new String[tagGroup.size()];
                    for (int i = 0; i < tagGroup.size(); i++) {
                        tags[i] = tagGroup.getTagAt(i);
                    }
                    tagsMap.put(tagGroup.groupName, tags);
                }
            }
            
            return tagsMap;
        } catch (Exception e) {
            Log.e(TAG, "转换标签到JSON失败", e);
            return null;
        }
    }
    
    /**
     * 将JSON解析为标签数组
     */
    @Nullable
    private GalleryTagGroup[] parseJsonToTags(@Nullable Object tagsObject) {
        try {
            if (tagsObject == null) {
                return null;
            }
            
            if (!(tagsObject instanceof Map)) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tagsMap = (Map<String, Object>) tagsObject;
            if (tagsMap.isEmpty()) {
                return null;
            }
            
            GalleryTagGroup[] tagGroups = new GalleryTagGroup[tagsMap.size()];
            int index = 0;
            
            for (Map.Entry<String, Object> entry : tagsMap.entrySet()) {
                String groupName = entry.getKey();
                Object tagsValue = entry.getValue();
                
                if (tagsValue instanceof Object[]) {
                    Object[] tagsArray = (Object[]) tagsValue;
                    String[] tags = new String[tagsArray.length];
                    for (int i = 0; i < tagsArray.length; i++) {
                        tags[i] = tagsArray[i].toString();
                    }
                    
                    GalleryTagGroup tagGroup = new GalleryTagGroup();
                    tagGroup.groupName = groupName;
                    for (String tag : tags) {
                        tagGroup.addTag(tag);
                    }
                    
                    tagGroups[index++] = tagGroup;
                }
            }
            
            return tagGroups;
        } catch (Exception e) {
            Log.e(TAG, "解析JSON到标签失败", e);
            return null;
        }
    }
    
    /**
     * 读取文本文件内容
     */
    @Nullable
    private String readTextFile(@NonNull UniFile file) {
        InputStream inputStream = null;
        try {
            inputStream = file.openInputStream();
            return IOUtils.readString(inputStream, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "读取文本文件失败: " + file.getName(), e);
            return null;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
    
    /**
     * 写入文本文件内容
     */
    private boolean writeTextFile(@NonNull UniFile file, @NonNull String content) {
        OutputStream outputStream = null;
        try {
            outputStream = file.openOutputStream();
            outputStream.write(content.getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "写入文本文件失败: " + file.getName(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(outputStream);
        }
    }
}
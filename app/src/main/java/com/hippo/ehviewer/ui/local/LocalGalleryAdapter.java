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

package com.hippo.ehviewer.ui.local;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.Log;

import com.hippo.lib.glgallery.GalleryProvider;
import com.hippo.lib.glgallery.SimpleAdapter;
import com.hippo.lib.glview.view.GLRootView;

import java.io.File;
import java.util.List;

public class LocalGalleryAdapter extends SimpleAdapter {
    
    private static final String TAG = "LocalGalleryAdapter";
    private final Context mContext;
    private final List<String> mImagePaths;
    private final GLRootView mGLRootView;
    
    public LocalGalleryAdapter(GLRootView glRootView, List<String> imagePaths) {
        super(glRootView, new LocalGalleryProvider(imagePaths, glRootView.getContext()));
        Log.d(TAG, "LocalGalleryAdapter: 构造函数，图片数量 - " + (imagePaths != null ? imagePaths.size() : "null"));
        Log.d(TAG, "LocalGalleryAdapter: Android版本: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "LocalGalleryAdapter: WebP支持: " + (Build.VERSION.SDK_INT >= 14 ? "是" : "否 (需要API 14+)"));
        Log.d(TAG, "LocalGalleryAdapter: WebP动画支持: " + (Build.VERSION.SDK_INT >= 18 ? "是" : "否 (需要API 18+)"));
        
        mContext = glRootView.getContext();
        mImagePaths = imagePaths;
        mGLRootView = glRootView;
        
        if (imagePaths != null && !imagePaths.isEmpty()) {
            for (int i = 0; i < Math.min(imagePaths.size(), 3); i++) {
                Log.d(TAG, "LocalGalleryAdapter: 图片路径[" + i + "] - " + imagePaths.get(i));
            }
        }
    }
    
    @Override
    public void onDataChanged() {
        // 这个方法会在渲染线程中被调用，不要在这里做耗时操作
        super.onDataChanged();
    }
    
    public static class LocalGalleryProvider extends GalleryProvider {
        
        private static final String TAG = "LocalGalleryProvider";
        private final List<String> mImagePaths;
        private Context mContext;
        
        public LocalGalleryProvider(List<String> imagePaths, Context context) {
            Log.d(TAG, "LocalGalleryProvider: 构造函数，图片数量 - " + (imagePaths != null ? imagePaths.size() : "null"));
            mImagePaths = imagePaths;
            mContext = context;
        }
        
        public int size() {
            int size = mImagePaths != null ? mImagePaths.size() : 0;
            Log.d(TAG, "LocalGalleryProvider: size() - " + size);
            return size;
        }
        
        protected void onRequest(int index) {
            Log.d(TAG, "LocalGalleryProvider: onRequest(" + index + ")");
            // 本地文件无需请求
        }
        
        protected void onForceRequest(int index) {
            Log.d(TAG, "LocalGalleryProvider: onForceRequest(" + index + ")");
            // 本地文件无需强制请求
        }
        
        protected void onCancelRequest(int index) {
            Log.d(TAG, "LocalGalleryProvider: onCancelRequest(" + index + ")");
            // 本地文件无需取消请求
        }
        
        public String getError() {
            return null; // 本地文件无错误
        }
        
        public String getPageFilename(int index) {
            if (mImagePaths == null || index < 0 || index >= mImagePaths.size()) {
                return null;
            }
            return mImagePaths.get(index);
        }
        
        public Object getGLData(int index) {
            Log.d(TAG, "LocalGalleryProvider: getGLData(" + index + ") - 开始加载图片");
            long startTime = System.currentTimeMillis();
            
            if (mImagePaths == null || index < 0 || index >= mImagePaths.size()) {
                Log.e(TAG, "LocalGalleryProvider: getGLData - 索引超出范围或数据为null");
                return null;
            }
            
            String path = mImagePaths.get(index);
            Log.d(TAG, "LocalGalleryProvider: getGLData - 加载图片: " + path);
            
            // 检查文件格式
            String fileName = new File(path).getName().toLowerCase();
            boolean isWebP = fileName.endsWith(".webp");
            boolean isJpg = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
            boolean isPng = fileName.endsWith(".png");
            boolean isGif = fileName.endsWith(".gif");
            
            Log.d(TAG, "LocalGalleryProvider: getGLData - 图片格式: " + 
                  (isWebP ? "WebP" : isJpg ? "JPG" : isPng ? "PNG" : isGif ? "GIF" : "未知"));
            
            try {
                File file = new File(path);
                if (!file.exists()) {
                    Log.e(TAG, "LocalGalleryProvider: getGLData - 文件不存在: " + path);
                    return null;
                }
                
                long fileSize = file.length();
                Log.d(TAG, "LocalGalleryProvider: getGLData - 文件大小: " + fileSize + " bytes");
                
                Log.d(TAG, "LocalGalleryProvider: getGLData - 文件存在，开始解码");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                
                // 先检查图片尺寸而不加载完整图片
                options.inJustDecodeBounds = true;
                Bitmap boundsBitmap = BitmapFactory.decodeFile(path, options);
                boolean boundsDecodeSuccess = (boundsBitmap != null);
                Log.d(TAG, "LocalGalleryProvider: getGLData - 边界解码结果: " + (boundsDecodeSuccess ? "成功" : "失败"));
                Log.d(TAG, "LocalGalleryProvider: getGLData - 图片尺寸: " + options.outWidth + "x" + options.outHeight);
                Log.d(TAG, "LocalGalleryProvider: getGLData - 图片格式(MIME): " + options.outMimeType);
                
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    Log.e(TAG, "LocalGalleryProvider: getGLData - 图片尺寸无效，可能是格式不支持");
                    return null;
                }
                
                // 计算合适的采样率
                options.inJustDecodeBounds = false;
                options.inSampleSize = calculateInSampleSize(options, 1080, 1920);
                Log.d(TAG, "LocalGalleryProvider: getGLData - 使用采样率: " + options.inSampleSize);
                
                // 对于WebP格式，尝试不同的解码选项
                if (isWebP) {
                    Log.d(TAG, "LocalGalleryProvider: getGLData - 检测到WebP格式，使用特殊处理");
                    // 尝试使用ARGB_8888格式，WebP支持更好
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                }
                
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap != null) {
                    Log.d(TAG, "LocalGalleryProvider: getGLData - 解码成功，实际尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", 内存占用: " + bitmap.getByteCount() + " bytes");
                    Log.d(TAG, "LocalGalleryProvider: getGLData - 位图配置: " + bitmap.getConfig());
                    if (mContext != null) {
                        BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);
                        long endTime = System.currentTimeMillis();
                        Log.d(TAG, "LocalGalleryProvider: getGLData - 创建BitmapDrawable成功，耗时: " + (endTime - startTime) + "ms");
                        return drawable;
                    }
                } else {
                    Log.e(TAG, "LocalGalleryProvider: getGLData - 解码失败，bitmap为null");
                    // 对于WebP，尝试备用方案
                    if (isWebP) {
                        Log.w(TAG, "LocalGalleryProvider: getGLData - WebP解码失败，尝试使用默认配置");
                        options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        options.inSampleSize = 2; // 降低采样率
                        bitmap = BitmapFactory.decodeFile(path, options);
                        if (bitmap != null) {
                            Log.d(TAG, "LocalGalleryProvider: getGLData - WebP备用解码成功，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                            if (mContext != null) {
                                BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);
                                long endTime = System.currentTimeMillis();
                                Log.d(TAG, "LocalGalleryProvider: getGLData - WebP备用方案成功，耗时: " + (endTime - startTime) + "ms");
                                return drawable;
                            }
                        }
                    }
                }
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "LocalGalleryProvider: getGLData - 内存不足: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "LocalGalleryProvider: getGLData - 加载图片异常: " + e.getMessage(), e);
            }
            
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "LocalGalleryProvider: getGLData - 加载失败，总耗时: " + (endTime - startTime) + "ms");
            return null;
        }
        
        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;
            
            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            
            return inSampleSize;
        }
        
        public void recycleGLData(int index, Object data) {
            if (data instanceof BitmapDrawable) {
                BitmapDrawable drawable = (BitmapDrawable) data;
                Bitmap bitmap = drawable.getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }
}
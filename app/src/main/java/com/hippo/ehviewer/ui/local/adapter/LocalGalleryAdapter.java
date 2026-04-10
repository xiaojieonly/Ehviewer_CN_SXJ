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

package com.hippo.ehviewer.ui.local.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.ViewUtils;

import android.graphics.drawable.ColorDrawable;
import java.util.ArrayList;
import java.util.List;

public class LocalGalleryAdapter extends RecyclerView.Adapter<LocalGalleryAdapter.ViewHolder> {
    
    private final List<LocalGalleryInfo> mGalleries;
    private final boolean mIsRecycleBin;
    private final OnLocalGalleryClickListener mClickListener;
    private final LayoutInflater mInflater;
    private final Context mContext;
    
    public interface OnLocalGalleryClickListener {
        void onLocalGalleryClick(LocalGalleryInfo gallery);
        void onLocalGalleryLongClick(LocalGalleryInfo gallery);
    }
    
    public LocalGalleryAdapter(Context context, List<LocalGalleryInfo> galleries, boolean isRecycleBin, OnLocalGalleryClickListener clickListener) {
        mGalleries = galleries; // 直接引用，不创建副本
        mIsRecycleBin = isRecycleBin;
        mClickListener = clickListener;
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_local_gallery, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalGalleryInfo gallery = mGalleries.get(position);
        holder.bind(gallery);
    }
    
    @Override
    public int getItemCount() {
        return mGalleries.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mThumb;
        private final TextView mTitle;
        private final TextView mUploader;
        private final TextView mCategory;
        private final SimpleRatingView mRating;
        private final TextView mPages;
        private final TextView mSize;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            
            mThumb = itemView.findViewById(R.id.thumb);
            mTitle = itemView.findViewById(R.id.title);
            mUploader = itemView.findViewById(R.id.uploader);
            mCategory = itemView.findViewById(R.id.category);
            mRating = itemView.findViewById(R.id.rating);
            mPages = itemView.findViewById(R.id.pages);
            mSize = itemView.findViewById(R.id.size);
        }
        
        public void bind(LocalGalleryInfo gallery) {
            // 设置标题
            mTitle.setText(gallery.getDisplayTitle());
            
            // 设置上传者（显示路径信息）
            String uploader = gallery.path;
            if (uploader != null && uploader.length() > 30) {
                uploader = "..." + uploader.substring(uploader.length() - 30);
            }
            mUploader.setText(uploader);
            
            // 设置分类
            mCategory.setText(gallery.category != null ? gallery.category : "LOCAL");
            
            // 设置评分（本地画廊没有评分，显示为空）
            mRating.setRating(0.0f);
            
            // 设置页数
            mPages.setText(String.valueOf(gallery.pageCount));
            
            // 设置大小
            mSize.setText(gallery.getFormattedSize());
            
            // 设置缩略图
            loadThumbnail(gallery);
            
            // 设置点击事件
            itemView.setOnClickListener(v -> {
                if (mClickListener != null) {
                    mClickListener.onLocalGalleryClick(gallery);
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                if (mClickListener != null) {
                    mClickListener.onLocalGalleryLongClick(gallery);
                }
                return true;
            });
            
            // 如果是回收站项目，设置红色背景
            if (mIsRecycleBin) {
                itemView.setBackgroundColor(0x33FF0000); // 半透明红色
            } else {
                itemView.setBackgroundColor(0x00000000); // 透明
            }
        }
        
        private void loadThumbnail(LocalGalleryInfo gallery) {
            if (gallery.thumbnail != null) {
                mThumb.setImageDrawable(gallery.thumbnail);
            } else {
                // 加载默认缩略图
                Drawable placeholder = new ColorDrawable(0xFFCCCCCC);
                mThumb.setImageDrawable(placeholder);
                
                // 异步加载真实缩略图
                loadRealThumbnail(gallery);
            }
        }
        
        private void loadRealThumbnail(LocalGalleryInfo gallery) {
            if (gallery.thumb == null || gallery.thumb.isEmpty()) {
                return;
            }
            
            // 异步加载第一张图片作为缩略图
            new Thread(() -> {
                try {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(gallery.thumb);
                    if (bitmap != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> mThumb.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    // 加载失败，保持占位符
                }
            }).start();
        }
    }
}
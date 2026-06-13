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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.ui.local.LocalGalleryDetailActivity;
import com.hippo.ehviewer.ui.local.LocalGalleryViewerActivity;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.ui.scene.gallery.list.EnterGalleryDetailTransaction;
import android.widget.ImageView;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.scene.Announcer;
import com.hippo.yorozuya.ViewUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地画廊适配器 - 使用与下载页面相同的卡片样式
 */
public class LocalGalleryCardAdapter extends RecyclerView.Adapter<LocalGalleryCardAdapter.LocalGalleryHolder> {

    private final List<LocalGalleryInfo> mGalleries;
    private final boolean mIsRecycleBin;
    private final Context mContext;
    private final LayoutInflater mInflater;
    private final OnLocalGalleryClickListener mClickListener;

    public interface OnLocalGalleryClickListener {
        void onLocalGalleryClick(LocalGalleryInfo gallery);
        void onLocalGalleryLongClick(LocalGalleryInfo gallery);
        void onThumbClick(LocalGalleryInfo gallery);
    }

    public LocalGalleryCardAdapter(Context context, List<LocalGalleryInfo> galleries, boolean isRecycleBin, OnLocalGalleryClickListener clickListener) {
        mGalleries = galleries;
        mIsRecycleBin = isRecycleBin;
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mClickListener = clickListener;
    }

    @NonNull
    @Override
    public LocalGalleryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_download, parent, false);
        return new LocalGalleryHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocalGalleryHolder holder, int position) {
        LocalGalleryInfo gallery = mGalleries.get(position);
        holder.bind(gallery);
    }

    @Override
    public int getItemCount() {
        return mGalleries.size();
    }

    public class LocalGalleryHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public final ImageView thumb;
        public final TextView title;
        public final TextView uploader;
        public final SimpleRatingView rating;
        public final TextView category;
        public final TextView readProgress;
        public final View cardView;

        private LocalGalleryInfo mCurrentGallery;

        public LocalGalleryHolder(View itemView) {
            super(itemView);

            thumb = itemView.findViewById(R.id.thumb);
            title = itemView.findViewById(R.id.title);
            uploader = itemView.findViewById(R.id.uploader);
            rating = itemView.findViewById(R.id.rating);
            category = itemView.findViewById(R.id.category);
            readProgress = itemView.findViewById(R.id.read_progress);
            cardView = itemView.findViewById(R.id.card_view);

            // 设置点击监听器
            thumb.setOnClickListener(this);
            cardView.setOnClickListener(this);
            cardView.setOnLongClickListener(v -> {
                if (mClickListener != null && mCurrentGallery != null) {
                    mClickListener.onLocalGalleryLongClick(mCurrentGallery);
                }
                return true;
            });
        }

        public void bind(LocalGalleryInfo gallery) {
            mCurrentGallery = gallery;

            // 设置标题
            title.setText(gallery.getDisplayTitle());

            // 设置上传者（显示路径）
            uploader.setText(gallery.path);

            // 设置评分（本地画廊默认为0）
            rating.setRating(0.0f);

            // 设置分类
            category.setText(mIsRecycleBin ? "RECYCLE" : "LOCAL");
            category.setBackgroundColor(mContext.getResources().getColor(
                mIsRecycleBin ? R.color.red_500 : R.color.colorPrimaryDark));

            // 设置阅读进度
            if (gallery.pageCount > 0) {
                readProgress.setText("已读 " + 0 + "/" + gallery.pageCount);
                readProgress.setVisibility(View.VISIBLE);
            } else {
                readProgress.setVisibility(View.GONE);
            }

            // 加载缩略图
            loadThumbnail(gallery);
        }

        private void loadThumbnail(LocalGalleryInfo gallery) {
            if (gallery.thumb != null && !gallery.thumb.isEmpty()) {
                File thumbFile = new File(gallery.thumb);
                if (thumbFile.exists()) {
                    thumb.setImageBitmap(BitmapFactory.decodeFile(thumbFile.getAbsolutePath()));
                } else {
                    // 如果缩略图不存在，尝试加载第一张图片
                    loadFirstImageAsThumbnail(gallery);
                }
            } else {
                // 尝试加载第一张图片作为缩略图
                loadFirstImageAsThumbnail(gallery);
            }
        }

        private void loadFirstImageAsThumbnail(LocalGalleryInfo gallery) {
            if (gallery.path == null) return;
            
            File galleryDir = new File(gallery.path);
            if (!galleryDir.exists() || !galleryDir.isDirectory()) return;

            File[] files = galleryDir.listFiles();
            if (files == null) return;

            // 查找第一张图片
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                        name.endsWith(".png") || name.endsWith(".gif") || 
                        name.endsWith(".webp")) {
                        // 异步加载图片
                        new Thread(() -> {
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            if (bitmap != null) {
                                // 在主线程设置图片
                                thumb.post(() -> thumb.setImageBitmap(bitmap));
                            }
                        }).start();
                        break;
                    }
                }
            }
        }

        @Override
        public void onClick(View v) {
            if (mCurrentGallery == null) return;

            if (v == thumb) {
                // 点击缩略图 - 进入详情页
                if (mClickListener != null) {
                    mClickListener.onThumbClick(mCurrentGallery);
                }
            } else if (v == cardView) {
                // 点击卡片本体 - 进入浏览界面
                if (mClickListener != null) {
                    mClickListener.onLocalGalleryClick(mCurrentGallery);
                }
            }
        }
    }
}
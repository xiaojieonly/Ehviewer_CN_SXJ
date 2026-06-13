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

package com.hippo.ehviewer.ui.local.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;

/**
 * 本地画廊评论Fragment - 简化版本，仅显示基本信息
 */
public class LocalGalleryCommentsFragment extends Fragment {
    
    private static final String KEY_GALLERY_INFO = "gallery_info";
    
    private LocalGalleryInfo mGalleryInfo;
    
    public static LocalGalleryCommentsFragment newInstance(LocalGalleryInfo galleryInfo) {
        LocalGalleryCommentsFragment fragment = new LocalGalleryCommentsFragment();
        Bundle args = new Bundle();
        args.putParcelable(KEY_GALLERY_INFO, galleryInfo);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bundle args = getArguments();
        if (args != null) {
            mGalleryInfo = args.getParcelable(KEY_GALLERY_INFO);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_gallery_comments, container, false);
        
        if (mGalleryInfo != null) {
            setupViews(view);
        }
        
        return view;
    }
    
    private void setupViews(View view) {
        TextView messageView = view.findViewById(R.id.message);
        
        StringBuilder message = new StringBuilder();
        message.append("这是本地画廊，没有在线评论功能。\n\n");
        message.append("画廊信息：\n");
        message.append("标题：").append(mGalleryInfo.getDisplayTitle()).append("\n");
        message.append("路径：").append(mGalleryInfo.path).append("\n");
        message.append("页数：").append(mGalleryInfo.pageCount).append("\n");
        
        if (mGalleryInfo.pageCount > 0) {
            message.append("阅读进度：").append(0).append("/").append(mGalleryInfo.pageCount).append("\n");
        }
        
        messageView.setText(message.toString());
    }
}
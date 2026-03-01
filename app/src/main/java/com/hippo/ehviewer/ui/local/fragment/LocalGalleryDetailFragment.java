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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地画廊详情Fragment
 */
public class LocalGalleryDetailFragment extends Fragment {
    
    private static final String KEY_GALLERY_INFO = "gallery_info";
    
    private LocalGalleryInfo mGalleryInfo;
    
    public static LocalGalleryDetailFragment newInstance(LocalGalleryInfo galleryInfo) {
        LocalGalleryDetailFragment fragment = new LocalGalleryDetailFragment();
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
        View view = inflater.inflate(R.layout.fragment_local_gallery_detail, container, false);
        
        if (mGalleryInfo != null) {
            setupViews(view);
        }
        
        return view;
    }
    
    private void setupViews(View view) {
        TextView titleView = view.findViewById(R.id.title);
        TextView pathView = view.findViewById(R.id.path);
        TextView sizeView = view.findViewById(R.id.size);
        TextView pagesView = view.findViewById(R.id.pages);
        TextView categoryView = view.findViewById(R.id.category);
        TextView modifiedView = view.findViewById(R.id.modified);
        TextView progressView = view.findViewById(R.id.progress);
        
        titleView.setText(mGalleryInfo.getDisplayTitle());
        pathView.setText(mGalleryInfo.path);
        sizeView.setText(formatFileSize(mGalleryInfo.size));
        pagesView.setText(String.valueOf(mGalleryInfo.pageCount));
        
        if (mGalleryInfo.category != null) {
            categoryView.setText(mGalleryInfo.category);
            categoryView.setVisibility(View.VISIBLE);
        } else {
            categoryView.setVisibility(View.GONE);
        }
        
        if (mGalleryInfo.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            modifiedView.setText(sdf.format(new Date(mGalleryInfo.timestamp)));
            modifiedView.setVisibility(View.VISIBLE);
        } else {
            modifiedView.setVisibility(View.GONE);
        }
        
        if (mGalleryInfo.pageCount > 0) {
            progressView.setText("已读 " + 0 + "/" + mGalleryInfo.pageCount);
            progressView.setVisibility(View.VISIBLE);
        } else {
            progressView.setVisibility(View.GONE);
        }
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
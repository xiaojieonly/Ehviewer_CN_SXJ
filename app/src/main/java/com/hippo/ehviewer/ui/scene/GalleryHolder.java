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

package com.hippo.ehviewer.ui.scene;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.widget.LoadImageView;

class GalleryHolder extends RecyclerView.ViewHolder {

    public final LoadImageView thumb;
    public final TextView title;
    public final TextView uploader;
    public final SimpleRatingView rating;
    public final TextView category;
    public final TextView posted;
    public final TextView pages;
    public final TextView simpleLanguage;
    public final ImageView favourited;
    public final ImageView downloaded;

    public GalleryHolder(View itemView) {
        super(itemView);

        thumb = itemView.findViewById(R.id.thumb);
        title = itemView.findViewById(R.id.title);
        uploader = itemView.findViewById(R.id.uploader);
        rating = itemView.findViewById(R.id.rating);
        category = itemView.findViewById(R.id.category);
        posted = itemView.findViewById(R.id.posted);
        pages = itemView.findViewById(R.id.pages);
        simpleLanguage = itemView.findViewById(R.id.simple_language);
        favourited = itemView.findViewById(R.id.favourited);
        downloaded = itemView.findViewById(R.id.downloaded);
    }
}

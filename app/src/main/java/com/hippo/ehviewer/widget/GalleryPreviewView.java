/*
 * Copyright 2026 antigone
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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.hippo.ehviewer.R;

/** One focus target per preview, retaining the image's open and retry actions. */
public class GalleryPreviewView extends LinearLayout {
    public GalleryPreviewView(Context context) {
        super(context);
    }

    public GalleryPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GalleryPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View image = findViewById(R.id.image);
        setOnClickListener(v -> image.performClick());
        setOnLongClickListener(v -> image.performLongClick());
    }
}

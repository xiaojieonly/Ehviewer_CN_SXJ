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

package com.hippo.ehviewer.ui.scene;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.widget.ContentLayout;
import com.hippo.widget.LoadImageView;
import com.hippo.widget.SimpleGridAutoSpanLayout;
import com.hippo.widget.recyclerview.AutoGridLayoutManager;

/** Offline fixture sharing the detail and full-preview layouts and activation view. */
public class GalleryPreviewNavigationTestActivity extends Activity {
    public Button toolbar;
    public ScrollView scrollView;
    public SimpleGridAutoSpanLayout grid;
    public EasyRecyclerView recyclerView;
    public TextView more;
    public int clicks;
    public int longClicks;
    public int moreClicks;
    public int lastOpenedPage = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new Button(this);
        toolbar.setText("Preview navigation test");
        root.addView(toolbar);
        int columnWidth = getResources().getDimensionPixelOffset(Settings.getThumbSizeResId());

        if (getIntent().getBooleanExtra("full_previews", false)) {
            ContentLayout content = new ContentLayout(this);
            content.hideFastScroll();
            content.findViewById(R.id.progress).setVisibility(View.GONE);
            content.findViewById(R.id.tip).setVisibility(View.GONE);
            recyclerView = content.getRecyclerView();
            AutoGridLayoutManager manager = new AutoGridLayoutManager(this, columnWidth);
            manager.setStrategy(AutoGridLayoutManager.STRATEGY_SUITABLE_SIZE);
            recyclerView.setLayoutManager(manager);
            recyclerView.setAdapter(new RecyclerView.Adapter<PreviewHolder>() {
                @Override
                public PreviewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    return new PreviewHolder(getLayoutInflater().inflate(
                            R.layout.item_gallery_preview, parent, false));
                }

                @Override
                public void onBindViewHolder(PreviewHolder holder, int position) {
                    bindPreview(holder.itemView, position);
                }

                @Override
                public int getItemCount() {
                    return 120;
                }
            });
            recyclerView.setOnItemClickListener((parent, view, position, id) -> {
                openPage(position);
                return true;
            });
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            scrollView = new ScrollView(this);
            scrollView.setVerticalScrollBarEnabled(false);
            View previews = getLayoutInflater().inflate(
                    R.layout.gallery_detail_previews, scrollView, false);
            grid = previews.findViewById(R.id.grid_layout);
            grid.setColumnSize(columnWidth);
            grid.setStrategy(SimpleGridAutoSpanLayout.STRATEGY_SUITABLE_SIZE);
            for (int i = 0; i < 120; i++) {
                View preview = getLayoutInflater().inflate(R.layout.item_gallery_preview, grid, false);
                bindPreview(preview, i);
                grid.addView(preview);
            }
            more = previews.findViewById(R.id.preview_text);
            more.setText(R.string.more_previews);
            more.setOnClickListener(v -> moreClicks++);
            scrollView.addView(previews);
            root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        setContentView(root);
    }

    private void bindPreview(View view, int page) {
        view.setTag(R.id.index, page);
        LoadImageView image = view.findViewById(R.id.image);
        image.load(new ColorDrawable(Color.rgb(40 + page % 6 * 25, 90, 150)));
        image.setOnClickListener(v -> openPage(page));
        image.setOnLongClickListener(v -> {
            longClicks++;
            return true;
        });
        ((TextView) view.findViewById(R.id.text)).setText(Integer.toString(page + 1));
    }

    private void openPage(int page) {
        clicks++;
        lastOpenedPage = page;
    }

    public View previewAt(int page) {
        return recyclerView != null ? recyclerView.findViewHolderForAdapterPosition(page).itemView
                : grid.getChildAt(page);
    }

    private static class PreviewHolder extends RecyclerView.ViewHolder {
        PreviewHolder(View itemView) {
            super(itemView);
        }
    }
}

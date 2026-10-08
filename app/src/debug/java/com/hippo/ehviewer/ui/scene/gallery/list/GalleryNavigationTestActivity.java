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

package com.hippo.ehviewer.ui.scene.gallery.list;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.hippo.drawable.DrawerArrowDrawable;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.widget.ControllerRecyclerView;
import com.hippo.ehviewer.widget.EhDrawerLayout;
import com.hippo.ehviewer.widget.EhStageLayout;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.ehviewer.widget.TileThumbNew;
import com.hippo.widget.ContentLayout;
import com.hippo.widget.SearchBarMover;
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline fixture using the homepage's real card layouts and layout manager. */
public class GalleryNavigationTestActivity extends Activity {
    public EasyRecyclerView recyclerView;
    public GalleryAdapterNew adapter;
    public View toolbar;
    public SearchBar searchBar;
    public EhDrawerLayout drawer;
    public NavigationView navigation;
    public int menuClicks;
    public int lastMenuId;
    public ContentLayout content;
    public PagingHelper helper;
    public int clicks;
    public int longClicks;
    public int thumbnailClicks;
    public long lastClickedId;
    private final ArrayList<GalleryInfo> galleries = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("menu", false)) {
            setContentView(R.layout.activity_main);
            drawer = findViewById(R.id.draw_view);
            navigation = findViewById(R.id.nav_view);
            navigation.setCheckedItem(R.id.nav_homepage);
            navigation.setNavigationItemSelectedListener(item -> {
                menuClicks++;
                lastMenuId = item.getItemId();
                drawer.closeDrawers();
                return true;
            });
            EhStageLayout stage = findViewById(R.id.fragment_container);
            View main = getLayoutInflater().inflate(R.layout.scene_gallery_list, stage, false);
            stage.addView(main);
            main.findViewById(R.id.search_layout).setVisibility(View.GONE);
            main.findViewById(R.id.fab_layout).setVisibility(View.GONE);
            content = main.findViewById(R.id.content_layout);
            searchBar = main.findViewById(R.id.search_bar);
            toolbar = searchBar.findViewById(R.id.search_menu);
            searchBar.setLeftDrawable(new DrawerArrowDrawable(this, Color.DKGRAY));
            searchBar.setHelper(new SearchBar.Helper() {
                @Override
                public void onClickLeftIcon() {
                    drawer.openDrawer(Gravity.LEFT);
                }
                @Override
                public void onClickTitle() {
                }
                @Override
                public void onClickRightIcon() {
                }
                @Override
                public void onSearchEditTextClick() {
                }
                @Override
                public void onApplySearch(String query) {
                }
                @Override
                public void onSearchEditTextBackPressed() {
                }
            });
            recyclerView = content.getRecyclerView();
            ((ControllerRecyclerView) recyclerView).setLeftFocusView(toolbar);
            new SearchBarMover(new SearchBarMover.Helper() {
                @Override
                public boolean isValidView(RecyclerView view) {
                    return view == recyclerView;
                }
                @Override
                public RecyclerView getValidRecyclerView() {
                    return recyclerView;
                }
                @Override
                public boolean forceShowSearchBar() {
                    return searchBar.hasFocus();
                }
            }, searchBar, recyclerView);
        } else {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            Button button = new Button(this);
            button.setText("Controller navigation test");
            toolbar = button;
            root.addView(toolbar);
            content = new ContentLayout(this);
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
            setContentView(root);
        }
        content.hideFastScroll();
        content.findViewById(R.id.progress).setVisibility(View.GONE);
        content.findViewById(R.id.tip).setVisibility(View.GONE);
        recyclerView = content.getRecyclerView();

        for (int i = 0; i < 120; i++) {
            GalleryInfo gallery = new GalleryInfo();
            gallery.gid = i + 1;
            gallery.title = "Gallery " + (i + 1);
            galleries.add(gallery);
        }
        adapter = new GalleryAdapterNew(getLayoutInflater(), getResources(), recyclerView,
                getIntent().getIntExtra("layout", GalleryAdapterNew.TYPE_LIST), false, executor, false) {
            @Override
            public int getItemCount() {
                return helper != null ? helper.size() : galleries.size();
            }

            @Override
            public GalleryInfo getDataAt(int position) {
                return helper != null ? helper.getDataAtEx(position) : galleries.get(position);
            }

            @Override
            public void onBindViewHolder(GalleryHolder holder, int position) {
                holder.thumb.setImageDrawable(new ColorDrawable(Color.rgb(
                        40 + position % 6 * 25, 90, 150)));
                if (holder.title != null) {
                    holder.title.setText(getDataAt(position).title);
                    holder.uploader.setText("Offline test card");
                } else {
                    ((TileThumbNew) holder.thumb).setThumbSize(120, 180 + position % 3 * 20);
                }
            }

            @Override
            protected boolean onItemClick(View view, GalleryInfo gallery) {
                clicks++;
                lastClickedId = gallery.gid;
                return true;
            }

            @Override
            protected boolean onItemLongClick(View view, GalleryInfo gallery) {
                longClicks++;
                lastClickedId = gallery.gid;
                return true;
            }
        };
        if (adapter.getType() == GalleryAdapterNew.TYPE_LIST) {
            // Exercise horizontal navigation without changing the app's preferences.
            ((AutoStaggeredGridLayoutManager) recyclerView.getLayoutManager()).setColumnSize(
                    getResources().getDimensionPixelOffset(R.dimen.gallery_list_column_width_short));
        }
        adapter.setThumbItemClickListener((position, view, gallery) -> thumbnailClicks++);
        recyclerView.setOnItemClickListener((parent, view, position, id) -> {
            clicks++;
            lastClickedId = adapter.getDataAt(position).gid;
            return true;
        });
        recyclerView.setOnItemLongClickListener((parent, view, position, id) -> {
            longClicks++;
            return true;
        });
    }

    public void enablePaging(int pages) {
        helper = new PagingHelper();
        content.setHelper(helper);
        helper.firstRefresh();
        helper.complete(pages, galleries);
    }

    public void completeCurrentPage(int pages) {
        helper.complete(pages, galleries);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && drawer != null && drawer.isDrawersVisible()) {
            drawer.closeDrawers();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Uses the production paging state machine with manually completed offline requests. */
    public class PagingHelper extends ContentLayout.ContentHelper<GalleryInfo> {
        public int requests;
        public int taskId;
        public int requestType;
        public int requestPage;

        @Override
        protected void getPageData(int taskId, int type, int page) {
            requests++;
            this.taskId = taskId;
            requestType = type;
            requestPage = page;
        }

        @Override
        protected void getPageData(int taskId, int type, int page, String append) {
            getPageData(taskId, type, page);
        }

        @Override
        protected void getExPageData(int pageAction, int taskId, int page) {
            getPageData(taskId, pageAction, page);
        }

        public void complete(int pages, List<GalleryInfo> data) {
            onGetPageData(taskId, pages, 0, new ArrayList<>(data));
        }

        @Override
        protected Context getContext() {
            return GalleryNavigationTestActivity.this;
        }

        @Override
        protected void notifyDataSetChanged() {
            adapter.notifyDataSetChanged();
        }

        @Override
        protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
            adapter.notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        protected void notifyItemRangeInserted(int positionStart, int itemCount) {
            adapter.notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        protected boolean isDuplicate(GalleryInfo d1, GalleryInfo d2) {
            return d1.gid == d2.gid;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

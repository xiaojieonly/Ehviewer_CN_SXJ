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

package com.hippo.ehviewer.ui.scene.download.part;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.callBack.DownloadSearchCallback;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.sync.DownloadListInfosExecutor;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.util.DrawableManager;
import com.hippo.widget.ProgressView;
import com.hippo.widget.SearchBarMover;

import java.util.List;

/**
 * 下载页搜索与筛选。
 */
public class DownloadSearchController implements SearchBar.Helper, SearchBarMover.Helper,
        SearchBar.OnStateChangeListener {

    public interface Host {
        @Nullable
        Context getEHContext();

        String getSearchKey();

        void setSearchKey(String searchKey);

        void setSearching(boolean searching);

        @Nullable
        ProgressView getProgressView();

        @Nullable
        MyEasyRecyclerView getRecyclerView();

        @Nullable
        List<DownloadInfo> getList();

        @Nullable
        List<DownloadInfo> getBackList();

        @Nullable
        DownloadManager getDownloadManager();

        void updateForLabel();

        DownloadSearchCallback getDownloadSearchCallback();
    }

    @NonNull
    private final Host mHost;

    private AlertDialog mSearchDialog;
    private SearchBar mSearchBar;
    @Nullable
    private SearchBarMover mSearchBarMover;
    private boolean mSearchMode = false;

    public DownloadSearchController(@NonNull Host host) {
        mHost = host;
    }

    public void gotoSearch(Context context, SearchBar.Helper helper, SearchBarMover.Helper moverHelper) {
        if (mSearchDialog != null) {
            mSearchDialog.show();
            return;
        }
        LayoutInflater layoutInflater = LayoutInflater.from(context);

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_download);

        LinearLayout linearLayout = (LinearLayout) layoutInflater.inflate(R.layout.download_search_dialog, null);
        mSearchBar = linearLayout.findViewById(R.id.download_search_bar);
        mSearchBar.setHelper(helper);
        mSearchBar.setIsComeFromDownload(true);
        mSearchBar.setEditTextHint(R.string.download_search_hint);
        mSearchBar.setLeftDrawable(drawable);
        String searchKey = mHost.getSearchKey();
        mSearchBar.setText(searchKey);
        if (searchKey != null && !searchKey.isEmpty()) {
            mSearchBar.setTitle(searchKey);
            mSearchBar.cursorToEnd();
        } else {
            mSearchBar.setTitle(R.string.download_search_hint);
        }

        mSearchBar.setRightDrawable(DrawableManager.getVectorDrawable(context, R.drawable.v_magnify_x24));
        mSearchBarMover = new SearchBarMover(moverHelper, mSearchBar);
        mSearchDialog = new AlertDialog.Builder(context)
                .setMessage(R.string.download_search_gallery)
                .setView(linearLayout)
                .setCancelable(true)
                .setOnDismissListener(this::onSearchDialogDismiss)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    mHost.setSearchKey(null);
                    mSearchBar.setText(null);
                    mSearchBar.setTitle(null);
                    mSearchBar.applySearch(true);
                    dialog.dismiss();
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mSearchBar.applySearch(true);
                    dialog.dismiss();
                }).show();
    }

    private void onSearchDialogDismiss(DialogInterface dialog) {
        mSearchMode = false;
    }

    private void enterSearchMode(boolean animation) {
        if (mSearchMode || mSearchBar == null || mSearchBarMover == null) {
            return;
        }
        mSearchMode = true;
        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);

        mSearchBarMover.returnSearchBarPosition(animation);

    }

    public void startSearching() {
        mHost.getProgressView().setVisibility(View.VISIBLE);
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }

        if (mSearchMode) {
            mSearchMode = false;
            mSearchBar.setTitle(mHost.getSearchKey());
            mSearchBar.setState(SearchBar.STATE_NORMAL);
        }

        mSearchDialog.dismiss();

        mHost.updateForLabel();

        DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mHost.getList(), mHost.getSearchKey());

        executor.setDownloadSearchingListener(mHost.getDownloadSearchCallback());

        executor.executeSearching();
    }

    public void gotoFilterAndSort(int id) {
        mHost.getProgressView().setVisibility(View.VISIBLE);
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }

        DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mHost.getBackList(), mHost.getDownloadManager());

        executor.setDownloadSearchingListener(mHost.getDownloadSearchCallback());

        executor.executeFilterAndSort(id);
    }

    @Override
    public void onClickTitle() {
        if (!mSearchMode) {
            enterSearchMode(true);
        }
    }

    @Override
    public void onClickLeftIcon() {

    }

    @Override
    public void onClickRightIcon() {
        mSearchBar.applySearch(true);
    }

    @Override
    public void onSearchEditTextClick() {

    }

    @Override
    public void onApplySearch(String query) {
        mHost.setSearchKey(query);
        mSearchBar.hideKeyBoard();
        mHost.setSearching(true);
        startSearching();
    }

    @Override
    public void onSearchEditTextBackPressed() {
        if (mSearchMode) {
            mSearchMode = false;
        }
        mSearchBar.setState(SearchBar.STATE_NORMAL, true);
    }

    @Override
    public void onStateChange(SearchBar searchBar, int newState, int oldState, boolean animation) {

    }

    @Override
    public boolean isValidView(RecyclerView recyclerView) {
        return false;
    }

    @Nullable
    @Override
    public RecyclerView getValidRecyclerView() {
        return mHost.getRecyclerView();
    }

    @Override
    public boolean forceShowSearchBar() {
        return false;
    }
}

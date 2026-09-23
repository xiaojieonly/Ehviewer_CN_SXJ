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

import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import com.hippo.widget.FabLayout;

/**
 * 下载列表多选模式。
 */
public class DownloadChoiceListener implements MyEasyRecyclerView.CustomChoiceListener {

    public interface Host {
        @Nullable
        MyEasyRecyclerView getRecyclerView();

        @Nullable
        FabLayout getFabLayout();

        void setDrawerLockMode(int lockMode, int edgeGravity);

        MyEasyRecyclerView.OnItemLongClickListener getItemLongClickListener();
    }

    @NonNull
    private final Host mHost;

    public DownloadChoiceListener(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public void onIntoCustomChoice(EasyRecyclerView view) {
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (recyclerView != null) {
            recyclerView.setOnItemLongClickListener(null);
            recyclerView.setLongClickable(false);
        }
        FabLayout fabLayout = mHost.getFabLayout();
        if (fabLayout != null) {
            fabLayout.setExpanded(true);
        }
        // Lock drawer
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);

//            // 进入选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(true);
    }

    @Override
    public void onOutOfCustomChoice(EasyRecyclerView view) {
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (recyclerView != null) {
            recyclerView.setOnItemLongClickListener(mHost.getItemLongClickListener());
        }
        FabLayout fabLayout = mHost.getFabLayout();
        if (fabLayout != null) {
            fabLayout.setExpanded(false);
        }
        // Unlock drawer
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);

//            // 退出选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(false);
    }

    @Override
    public void onItemCheckedStateChanged(EasyRecyclerView view, int position, long id, boolean checked) {
        if (view.getCheckedItemCount() == 0) {
            view.outOfCustomChoiceMode();
        }
    }
}

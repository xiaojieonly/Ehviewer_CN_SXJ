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

import android.graphics.Point;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import com.hippo.lib.yorozuya.ViewUtils;
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager;

/**
 * 下载页新手引导。
 */
public class DownloadGuideHelper {

    public interface Host {
        @Nullable
        MainActivity getActivity2();

        @Nullable
        MyEasyRecyclerView getRecyclerView();

        @Nullable
        AutoStaggeredGridLayoutManager getLayoutManager();

        void openDrawer(int gravity);
    }

    @NonNull
    private final Host mHost;

    private ShowcaseView mShowcaseView;

    public DownloadGuideHelper(@NonNull Host host) {
        mHost = host;
    }

    public boolean isShowing() {
        return mShowcaseView != null;
    }

    public void destroy() {
        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView);
            mShowcaseView = null;
        }
    }

    public void guide() {
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (Settings.getGuideDownloadThumb() && null != recyclerView) {
            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Settings.getGuideDownloadThumb()) {
                        guideDownloadThumb();
                    }
                    MyEasyRecyclerView current = mHost.getRecyclerView();
                    if (null != current) {
                        ViewUtils.removeOnGlobalLayoutListener(current.getViewTreeObserver(), this);
                    }
                }
            });
        } else {
            guideDownloadLabels();
        }
    }

    private void guideDownloadThumb() {
        MainActivity activity = mHost.getActivity2();
        AutoStaggeredGridLayoutManager layoutManager = mHost.getLayoutManager();
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        if (null == activity || !Settings.getGuideDownloadThumb() || null == layoutManager || null == recyclerView) {
            guideDownloadLabels();
            return;
        }
        int position = layoutManager.findFirstCompletelyVisibleItemPositions(null)[0];
        if (position < 0) {
            guideDownloadLabels();
            return;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (null == holder) {
            guideDownloadLabels();
            return;
        }

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new ViewTarget(((DownloadAdapter.DownloadHolder) holder).thumb))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_thumb_title)
                .setContentText(R.string.guide_download_thumb_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.putGuideDownloadThumb(false);
                        guideDownloadLabels();
                    }
                }).build();
    }

    private void guideDownloadLabels() {
        MainActivity activity = mHost.getActivity2();
        if (null == activity || !Settings.getGuideDownloadLabels()) {
            return;
        }

        Display display = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new PointTarget(point.x, point.y / 3))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_labels_title)
                .setContentText(R.string.guide_download_labels_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.puttGuideDownloadLabels(false);
                        mHost.openDrawer(Gravity.RIGHT);
                    }
                }).build();
    }
}

/*
 * Copyright 2026 Pegion Fish
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

package com.hippo.anotherviewer.ui.pane;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.ui.MainActivity;
import com.hippo.anotherviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.scene.Announcer;

/**
 * 双屏双栏控制器：MainActivity 的薄钩子，只负责生命周期转发与
 * "列表点击送详情"一个入口。其余逻辑都在本类内完成：
 *
 * <ul>
 *     <li>持有 {@link FoldSplitDetector}，监听竖向分隔铰链；</li>
 *     <li>出现铰链时把左栏场景容器（{@code R.id.fragment_container}，
 *     原有 SceneFragment 体系原样不动）压窄到左屏宽度，点亮右栏
 *     {@code R.id.detail_pane}；</li>
 *     <li>右栏承载真正的 {@link GalleryDetailScene}：借
 *     {@link Announcer#setContainerId(int)} 让 StageActivity 把场景加进右栏
 *     容器，原版详情逻辑（下载、收藏、标签、评论……）完整可用；</li>
 *     <li>铰链消失/收起时结束右栏全部场景、恢复容器宽度，左栏场景栈
 *     全程未被改动。</li>
 * </ul>
 *
 * <p>右栏场景的生命周期归框架所有（StageActivity/SceneFragment 负责
 * add/detach/pop 与返回键语义），本类只在"右栏该显示什么"发生变化时
 * 启动/结束右栏场景，从不手动 detach/attach 场景视图，也不直接操作
 * 场景 Fragment。
 *
 * <p>单屏（无分隔铰链）时 {@link #showGalleryDetail(GalleryInfo)}
 * 直接返回 false，调用方走原有单栏流程，默认路径零改动。
 */
public final class TwoPaneController implements FoldSplitDetector.Listener {

    private static final String TAG = "TwoPaneController";
    private static final String KEY_SAVED_TWO_PANE = "two_pane_active";

    private final MainActivity mActivity;
    private final FoldSplitDetector mDetector;

    /** 当前生效的分割；null 表示单栏。 */
    @Nullable
    private FoldSplitDetector.Split mSplit;
    /** 双栏下最近一次要求展示的画廊；铰链重新出现时据此恢复右栏。 */
    @Nullable
    private GalleryInfo mPendingDetail;

    public TwoPaneController(@NonNull MainActivity activity) {
        mActivity = activity;
        mDetector = new FoldSplitDetector(activity, this);
    }

    /*---------------
     Life cycle（由 MainActivity 转发）
     ---------------*/

    public void start() {
        mDetector.start();
    }

    public void stop() {
        mDetector.stop();
    }

    public void destroy() {
        mDetector.stop();
        mSplit = null;
        mPendingDetail = null;
    }

    /** 当前是否处于双栏模式。 */
    public boolean isTwoPane() {
        return mSplit != null;
    }

    /**
     * 双栏模式下在右栏展示画廊详情（真实的 {@link GalleryDetailScene}）。
     *
     * @return true 表示已交给右栏（调用方不要再走单栏 startScene 流程）；
     * false 表示当前不是双栏，调用方按原流程处理。
     */
    public boolean showGalleryDetail(@Nullable GalleryInfo galleryInfo) {
        Log.i(TAG, "showGalleryDetail split=" + (mSplit != null) + " gi=" + (galleryInfo != null ? galleryInfo.gid : -1));
        if (mSplit == null) {
            // 单屏：清理可能残留的右栏场景。Duo 外壳在移屏/取消跨屏时会重建
            // Activity，FragmentManager 会把右栏场景恢复——单屏下它既不可见也
            // 不该存活，否则下次跨屏会重新露出旧详情。
            cleanupStalePaneScenes();
            return false;
        }
        if (galleryInfo == null) {
            return false;
        }
        mPendingDetail = galleryInfo;
        reconcilePaneFragment();
        return true;
    }

    /*---------------
     FoldSplitDetector.Listener
     ---------------*/

    @Override
    public void onSplitChanged(@Nullable FoldSplitDetector.Split split) {
        if (split == null) {
            collapse();
        } else {
            expand(split);
        }
    }

    /*---------------
     Two pane layout
     ---------------*/

    private void expand(@NonNull FoldSplitDetector.Split split) {
        Log.i(TAG, "expand() leftPane=" + split.leftPaneWidth + " win=" + split.windowWidth);
        mSplit = split;

        View container = mActivity.findViewById(R.id.fragment_container);
        View pane = mActivity.findViewById(R.id.detail_pane);
        if (container == null || pane == null) {
            return;
        }
        // 左栏：DrawerLayout 的内容子视图宽度被强制为"父宽 - 左右边距"，
        // 压窄左栏必须用右外边距（右边距 = 窗口宽 - 铰链左侧宽度），
        // 左栏内容止步于铰链左缘，物理铰链遮挡区与右栏都不被其占用。
        ViewGroup.LayoutParams containerLp = container.getLayoutParams();
        if (containerLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) containerLp;
            lp.leftMargin = 0;
            lp.rightMargin = Math.max(0, split.windowWidth - split.leftPaneWidth);
            container.setLayoutParams(lp);
        } else {
            // 防御：非 Margin 布局参数时退化为直接设宽
            containerLp.width = split.leftPaneWidth;
            container.setLayoutParams(containerLp);
        }

        // 右栏：普通 CoordinatorLayout 子视图，直接设宽度并靠右
        ViewGroup.LayoutParams paneLp = pane.getLayoutParams();
        paneLp.width = split.rightPaneWidth;
        pane.setLayoutParams(paneLp);
        pane.setVisibility(View.VISIBLE);

        reconcilePaneFragment();
    }

    /** 单屏交互时清理残留的右栏场景（重建恢复的旧详情等）。 */
    private void cleanupStalePaneScenes() {
        if (mActivity.hasScenesIn(R.id.detail_pane)) {
            Log.i(TAG, "cleanupStalePaneScenes: finishing stale pane scenes");
            mActivity.finishScenesIn(R.id.detail_pane);
            updateHint();
        }
    }

    /** 跨重建持久化：保存当前是否处于双栏，重建后据此判断右栏场景是否合法。 */
    public void saveState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_SAVED_TWO_PANE, mSplit != null);
    }

    /**
     * 重建恢复：保存时为单屏却被恢复的右栏场景必然是残留（Duo 外壳重建），
     * 立即清理，避免再次跨屏时露出旧详情；保存时为双栏则保留恢复的详情。
     */
    public void restoreState(@Nullable Bundle savedState) {
        if (savedState == null) {
            return;
        }
        if (!savedState.getBoolean(KEY_SAVED_TWO_PANE, false)) {
            cleanupStalePaneScenes();
        }
    }

    private void collapse() {
        Log.i(TAG, "collapse() enter");
        mSplit = null;
        mPendingDetail = null;

        // 结束右栏全部场景：走框架标准 finish 流程，右栏场景的生命周期
        // 回调与结果回传照常触发，左栏场景栈不动。
        mActivity.finishScenesIn(R.id.detail_pane);
        updateHint();

        View container = mActivity.findViewById(R.id.fragment_container);
        View pane = mActivity.findViewById(R.id.detail_pane);
        if (container != null) {
            ViewGroup.LayoutParams lp = container.getLayoutParams();
            // 恢复 activity_main.xml 里的占满布局
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.leftMargin = 0;
                mlp.rightMargin = 0;
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            container.setLayoutParams(lp);
        }
        if (pane != null) {
            pane.setVisibility(View.GONE);
        }
    }

    /**
     * 保证右栏场景与待展示画廊一致：右栏已在展示同一画廊的详情场景时
     * 什么都不做（铰链事件会反复触发本方法，重启场景会丢场景内状态）；
     * 否则先结束右栏现有场景，再以真实 {@link GalleryDetailScene} 打开。
     * 场景生命周期归框架所有，本类只负责启动与结束。
     */
    private void reconcilePaneFragment() {
        if (mPendingDetail == null) {
            // 无待展示画廊（例如刚旋转恢复）：右栏场景若还在就保持原状，
            // 只同步提示文案。
            updateHint();
            return;
        }
        if (isShowingGallery(mPendingDetail)) {
            updateHint();
            return;
        }
        mActivity.finishScenesIn(R.id.detail_pane);
        startPaneDetailScene(mPendingDetail);
        updateHint();
    }

    /** 右栏当前是否已经在展示该画廊的详情场景。 */
    private boolean isShowingGallery(@NonNull GalleryInfo galleryInfo) {
        Fragment pane = mActivity.getSupportFragmentManager().findFragmentById(R.id.detail_pane);
        if (!(pane instanceof GalleryDetailScene)) {
            return false;
        }
        Bundle args = pane.getArguments();
        if (args == null) {
            return false;
        }
        GalleryInfo showing = args.getParcelable(GalleryDetailScene.KEY_GALLERY_INFO);
        return showing != null && showing.gid == galleryInfo.gid
                && TextUtils.equals(showing.token, galleryInfo.token);
    }

    /**
     * 让 StageActivity 把真实详情场景加进右栏容器。列表场景点击时
     * 拿不到可用的缩略图，因此不设置转场（{@code setTranHelper}）。
     */
    private void startPaneDetailScene(@NonNull GalleryInfo galleryInfo) {
        Bundle args = new Bundle();
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, galleryInfo);
        Announcer announcer = new Announcer(GalleryDetailScene.class).setArgs(args);
        announcer.setContainerId(R.id.detail_pane);
        mActivity.startScene(announcer);
    }

    /** 右栏空置提示：仅当右栏没有场景时可见。 */
    private void updateHint() {
        View pane = mActivity.findViewById(R.id.detail_pane);
        if (pane == null) {
            return;
        }
        View hint = pane.findViewById(R.id.detail_pane_hint);
        if (hint != null) {
            hint.setVisibility(mActivity.hasScenesIn(R.id.detail_pane) ? View.GONE : View.VISIBLE);
        }
    }
}

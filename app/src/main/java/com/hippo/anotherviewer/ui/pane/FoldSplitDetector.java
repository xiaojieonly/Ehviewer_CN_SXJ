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

import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

import com.hippo.anotherviewer.widget.FoldPolicy;

/**
 * 双屏铰链检测器：监听 androidx.window 的窗口布局信息，判定是否存在
 * "竖向分隔铰链"（铰链线竖直，左右两屏且互相独立，如 Surface Duo 展开），
 * 并给出左右两栏的布局宽度。判定逻辑复用纯策略类 {@link FoldPolicy}：
 * 仅当"竖向折叠 && 分隔"（物理铰链遮挡或两屏成夹角）时才输出分割结果，
 * FLAT + 无遮挡的连续屏玻璃折痕（如 Pixel Fold 内屏平放）不构成两栏。
 *
 * <p>本类持有并封装 WindowInfoTracker 的监听生命周期，回调保证在主线程；
 * 纯几何决策收在静态方法 {@link #computeSplit(int, int, int, int, int, int, int, int, int)}，
 * 输入全部是原始类型，可直接单元测试。
 */
public final class FoldSplitDetector {

    /**
     * 双栏最小栏宽（dp）：任一侧小于该值视为"没有分栏意义"，
     * 保守回退单栏，避免铰链贴边或小屏时挤出不可用的窄栏。
     */
    public static final int MIN_PANE_WIDTH_DP = 280;

    /**
     * 一次有效的竖向分隔铰链分割结果。铰链自身的显示区域
     * （{@code [hingeLeft, hingeRight)}）不参与两栏内容布局，
     * 避免内容绘制进物理铰链遮挡区。
     */
    public static final class Split {
        /** 左栏宽度（窗口坐标系），即铰链 bounds 左边界。 */
        public final int leftPaneWidth;
        /** 右栏宽度（窗口坐标系），即窗口宽减去铰链 bounds 右边界。 */
        public final int rightPaneWidth;
        /** 计算时的窗口内容宽度。 */
        public final int windowWidth;

        Split(int leftPaneWidth, int rightPaneWidth, int windowWidth) {
            this.leftPaneWidth = leftPaneWidth;
            this.rightPaneWidth = rightPaneWidth;
            this.windowWidth = windowWidth;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Split)) {
                return false;
            }
            Split split = (Split) o;
            return leftPaneWidth == split.leftPaneWidth
                    && rightPaneWidth == split.rightPaneWidth
                    && windowWidth == split.windowWidth;
        }

        @Override
        public int hashCode() {
            int result = leftPaneWidth;
            result = 31 * result + rightPaneWidth;
            result = 31 * result + windowWidth;
            return result;
        }
    }

    /** 分割状态回调，保证主线程；{@code null} 表示应回退单栏。 */
    public interface Listener {
        void onSplitChanged(@Nullable Split split);
    }

    private final Activity mActivity;
    private final Listener mListener;
    @Nullable
    private WindowInfoTrackerCallbackAdapter mAdapter;
    @Nullable
    private Split mLastSplit;
    /** 窗口尺寸变化的监听器：Duo 上取消跨屏不会重推 WindowLayoutInfo，需重注册强制刷新。 */
    @Nullable
    private View mReRegisterView;
    @Nullable
    private android.view.View.OnLayoutChangeListener mLayoutListener;
    private int mLastWidth = -1;
    private int mLastHeight = -1;

    private final Consumer<WindowLayoutInfo> mConsumer = this::onLayoutInfoChanged;

    public FoldSplitDetector(@NonNull Activity activity, @NonNull Listener listener) {
        mActivity = activity;
        mListener = listener;
    }

    /** 开始监听（Activity onStart 时机）；监听结果由 {@link Listener} 异步回报。 */
    public void start() {
        if (mAdapter != null) {
            return;
        }
        WindowInfoTrackerCallbackAdapter adapter = new WindowInfoTrackerCallbackAdapter(
                WindowInfoTracker.getOrCreate(mActivity));
        mAdapter = adapter;
        // 主线程 Executor：保证回调线程一致，无需再加锁
        adapter.addWindowLayoutInfoListener(mActivity,
                androidx.core.content.ContextCompat.getMainExecutor(mActivity), mConsumer);
        // Duo 上 androidx.window 只在铰链姿态变化时推送，窗口尺寸变化（拖拽跨屏/
        // 折回单屏）不重推：尺寸变化时重注册监听器强制刷新一次分割状态，
        // 保证取消跨屏能及时触发 collapse()。
        View content = mActivity.findViewById(android.R.id.content);
        if (content != null) {
            mReRegisterView = content;
            mLayoutListener = (v, l, t, r, b, ol, ot, or2, ob) -> {
                int width = r - l;
                int height = b - t;
                if (mLastWidth != -1 && (width != mLastWidth || height != mLastHeight)) {
                    reregister();
                }
                mLastWidth = width;
                mLastHeight = height;
            };
            content.addOnLayoutChangeListener(mLayoutListener);
        }
    }

    /** 重注册窗口布局监听：强制系统重推一次当前折叠几何（含"无分隔"状态）。 */
    private void reregister() {
        WindowInfoTrackerCallbackAdapter adapter = mAdapter;
        if (adapter == null) {
            return;
        }
        try {
            adapter.removeWindowLayoutInfoListener(mConsumer);
        } catch (RuntimeException ignored) {
        }
        adapter.addWindowLayoutInfoListener(mActivity,
                androidx.core.content.ContextCompat.getMainExecutor(mActivity), mConsumer);
    }

    /** 停止监听（Activity onStop 时机）。 */
    public void stop() {
        WindowInfoTrackerCallbackAdapter adapter = mAdapter;
        if (adapter == null) {
            return;
        }
        mAdapter = null;
        try {
            adapter.removeWindowLayoutInfoListener(mConsumer);
        } catch (RuntimeException ignored) {
        }
        if (mReRegisterView != null && mLayoutListener != null) {
            mReRegisterView.removeOnLayoutChangeListener(mLayoutListener);
        }
        mReRegisterView = null;
        mLayoutListener = null;
        mLastWidth = -1;
        mLastHeight = -1;
    }

    /** 仅供同包单元测试直调；处理一条窗口布局信息并去重回报。 */
    void onLayoutInfoChanged(@NonNull WindowLayoutInfo info) {
        Split split = null;
        for (DisplayFeature feature : info.getDisplayFeatures()) {
            if (feature instanceof FoldingFeature) {
                FoldingFeature folding = (FoldingFeature) feature;
                Rect bounds = folding.getBounds();
                Split candidate = computeSplit(bounds.left, bounds.top, bounds.right, bounds.bottom,
                        mapOrientation(folding.getOrientation()),
                        mapState(folding.getState()),
                        mapOcclusion(folding.getOcclusionType()),
                        getWindowWidth(), minPaneWidthPx());
                // 取第一条可用的竖向分隔特征；多个特征时后续的忽略
                if (candidate != null) {
                    split = candidate;
                    break;
                }
            }
        }
        // 去重：窗口布局信息流会重复推送相同状态
        Split last = mLastSplit;
        if (split == null && last == null) {
            return;
        }
        if (split != null && split.equals(last)) {
            return;
        }
        mLastSplit = split;
        Log.i("FoldSplitDetector", "split changed -> " + (split != null ? "two-pane" : "single"));
        // 全局双栏状态：跨屏时列表布局管理器据此钳制单列（见 TwoPaneState）
        TwoPaneState.setActive(split != null);
        mListener.onSplitChanged(split);
    }

    /**
     * 窗口内容宽度：取 content view 宽度，与 FoldingFeature bounds
     * 同处窗口坐标系，且在布局完成后即为窗口宽。未布局完成时回退
     * displayMetrics 宽度（首帧前监听器一般拿不到有效布局）。
     */
    private int getWindowWidth() {
        View content = mActivity.findViewById(android.R.id.content);
        if (content != null && content.getWidth() > 0) {
            return content.getWidth();
        }
        return mActivity.getResources().getDisplayMetrics().widthPixels;
    }

    private int minPaneWidthPx() {
        return Math.round(MIN_PANE_WIDTH_DP
                * mActivity.getResources().getDisplayMetrics().density);
    }

    /** androidx.window 方向映射到 {@link FoldPolicy.Orientation}。 */
    public static int mapOrientation(@NonNull FoldingFeature.Orientation orientation) {
        return orientation == FoldingFeature.Orientation.HORIZONTAL
                ? FoldPolicy.ORIENTATION_HORIZONTAL : FoldPolicy.ORIENTATION_VERTICAL;
    }

    /** androidx.window 状态映射到 {@link FoldPolicy.State}；未知状态保守按 FLAT（连续屏）处理。 */
    public static int mapState(@NonNull FoldingFeature.State state) {
        return state == FoldingFeature.State.HALF_OPENED
                ? FoldPolicy.STATE_HALF_OPENED : FoldPolicy.STATE_FLAT;
    }

    /** androidx.window 遮挡类型映射到 {@link FoldPolicy.OcclusionType}。 */
    public static int mapOcclusion(@NonNull FoldingFeature.OcclusionType occlusionType) {
        return occlusionType == FoldingFeature.OcclusionType.FULL
                ? FoldPolicy.OCCLUSION_FULL : FoldPolicy.OCCLUSION_NONE;
    }

    /**
     * 双栏分割决策（纯逻辑，全部为原始类型输入）：
     * 仅当"竖向折叠 && 分隔"（复用 {@link FoldPolicy#isSeparating(int, int)}）
     * 且左右两栏都不小于 {@code minPaneWidth} 时，返回以铰链边界划定的
     * 两栏分割；否则返回 {@code null} 表示维持单栏。
     *
     * <p>与阅读页 {@code FoldPolicy.readerSplitX} 取缝中心不同，双栏布局
     * 用铰链 bounds 的左右边界切分，左右内容都不落入铰链遮挡区。
     *
     * @param left          折叠特征 bounds 左边界（窗口坐标系）
     * @param top           折叠特征 bounds 上边界
     * @param right         折叠特征 bounds 右边界
     * @param bottom        折叠特征 bounds 下边界
     * @param orientation   折痕方向（{@link FoldPolicy.Orientation}）
     * @param state         折叠状态（{@link FoldPolicy.State}）
     * @param occlusionType 遮挡类型（{@link FoldPolicy.OcclusionType}）
     * @param windowWidth   窗口内容宽度
     * @param minPaneWidth  双栏最小栏宽（像素）
     */
    @Nullable
    public static Split computeSplit(int left, int top, int right, int bottom,
            @FoldPolicy.Orientation int orientation, @FoldPolicy.State int state,
            @FoldPolicy.OcclusionType int occlusionType,
            int windowWidth, int minPaneWidth) {
        // 横向折痕（上下两屏）不构成左右双栏
        if (orientation != FoldPolicy.ORIENTATION_VERTICAL) {
            return null;
        }
        // FLAT + 无遮挡是连续屏玻璃折痕，不是独立两屏
        if (!FoldPolicy.isSeparating(occlusionType, state)) {
            return null;
        }
        // bounds 无效或窗口宽度非法：无从切分
        if (right <= left || bottom <= top || windowWidth <= 0) {
            return null;
        }
        // 最小宽度约束：任一侧过窄则保守维持单栏
        if (minPaneWidth <= 0) {
            minPaneWidth = 1;
        }
        int leftPaneWidth = left;
        int rightPaneWidth = windowWidth - right;
        if (leftPaneWidth < minPaneWidth || rightPaneWidth < minPaneWidth) {
            return null;
        }
        return new Split(leftPaneWidth, rightPaneWidth, windowWidth);
    }
}

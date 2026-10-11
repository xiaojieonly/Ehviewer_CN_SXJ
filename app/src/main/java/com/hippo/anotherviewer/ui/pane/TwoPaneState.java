package com.hippo.anotherviewer.ui.pane;

/**
 * 全局双栏（跨屏）激活状态。跨屏时左栏列表必须恒定单列：{@link FoldSplitDetector}
 * 在分割变化时更新本状态，列表的 AutoStaggeredGridLayoutManager 在测量时读取并
 * 钳制列数——单屏/未跨屏时行为与改造前完全一致（宽屏平板仍按宽度自动分列）。
 */
public final class TwoPaneState {

    private static volatile boolean sActive;

    private TwoPaneState() {
    }

    public static boolean isActive() {
        return sActive;
    }

    public static void setActive(boolean active) {
        sActive = active;
    }
}

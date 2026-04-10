package com.hippo.ehviewer.ui.inset;

import androidx.annotation.NonNull;

/**
 * Calculates baseline-aware bottom spacing for overlay-heavy scenes hosted inside a toolbar scene.
 *
 * <p>Callers capture the legacy paddings once, then resolve the latest values from the current
 * bottom system-bar inset. Because every result is derived from the baseline contract, repeated
 * inset delivery, pagination visibility changes, and FAB state changes never stack offsets.</p>
 */
public final class BottomOverlayInsetHelper {

    private BottomOverlayInsetHelper() {
    }

    @NonNull
    public static Baseline capture(int recyclerBottomPadding,
                                   int recyclerBottomOverlayClearance,
                                   int fastScrollerBottomPadding,
                                   int paginationBottomPadding,
                                   int fabBottomPadding) {
        return new Baseline(
                clampToZero(recyclerBottomPadding),
                clampToZero(recyclerBottomOverlayClearance),
                clampToZero(fastScrollerBottomPadding),
                clampToZero(paginationBottomPadding),
                clampToZero(fabBottomPadding)
        );
    }

    @NonNull
    public static Resolved resolve(@NonNull Baseline baseline,
                                   int bottomInset,
                                   boolean paginationVisible,
                                   boolean fabExpanded) {
        final int safeBottomInset = clampToZero(bottomInset);
        return new Resolved(
                baseline.recyclerBottomPadding + baseline.recyclerBottomOverlayClearance + safeBottomInset,
                baseline.fastScrollerBottomPadding + safeBottomInset,
                baseline.paginationBottomPadding + (paginationVisible ? safeBottomInset : 0),
                baseline.fabBottomPadding + safeBottomInset,
                safeBottomInset,
                paginationVisible,
                fabExpanded
        );
    }

    private static int clampToZero(int value) {
        return Math.max(0, value);
    }

    public static final class Baseline {
        public final int recyclerBottomPadding;
        public final int recyclerBottomOverlayClearance;
        public final int fastScrollerBottomPadding;
        public final int paginationBottomPadding;
        public final int fabBottomPadding;

        private Baseline(int recyclerBottomPadding,
                         int recyclerBottomOverlayClearance,
                         int fastScrollerBottomPadding,
                         int paginationBottomPadding,
                         int fabBottomPadding) {
            this.recyclerBottomPadding = recyclerBottomPadding;
            this.recyclerBottomOverlayClearance = recyclerBottomOverlayClearance;
            this.fastScrollerBottomPadding = fastScrollerBottomPadding;
            this.paginationBottomPadding = paginationBottomPadding;
            this.fabBottomPadding = fabBottomPadding;
        }
    }

    public static final class Resolved {
        public final int recyclerBottomPadding;
        public final int fastScrollerBottomPadding;
        public final int paginationBottomPadding;
        public final int fabBottomPadding;
        public final int appliedBottomInset;
        public final boolean paginationVisible;
        public final boolean fabExpanded;

        private Resolved(int recyclerBottomPadding,
                         int fastScrollerBottomPadding,
                         int paginationBottomPadding,
                         int fabBottomPadding,
                         int appliedBottomInset,
                         boolean paginationVisible,
                         boolean fabExpanded) {
            this.recyclerBottomPadding = recyclerBottomPadding;
            this.fastScrollerBottomPadding = fastScrollerBottomPadding;
            this.paginationBottomPadding = paginationBottomPadding;
            this.fabBottomPadding = fabBottomPadding;
            this.appliedBottomInset = appliedBottomInset;
            this.paginationVisible = paginationVisible;
            this.fabExpanded = fabExpanded;
        }
    }
}

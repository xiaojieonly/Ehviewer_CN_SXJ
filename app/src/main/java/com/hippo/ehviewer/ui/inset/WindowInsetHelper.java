package com.hippo.ehviewer.ui.inset;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Shared helper for idempotent window inset application.
 *
 * <p>The helper snapshots the view's baseline padding and margins once, then reapplies system bar
 * and cutout insets against that baseline on every dispatch so repeated inset delivery never stacks
 * offsets.</p>
 */
public final class WindowInsetHelper {

    private WindowInsetHelper() {
    }

    @NonNull
    public static InitialBounds capture(@NonNull View view) {
        return new InitialBounds(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom(),
                readMargins(view)
        );
    }

    public static void applySystemBarsToPadding(@NonNull View view) {
        applyPadding(view, WindowInsetsCompat.Type.systemBars());
    }

    public static void applyTopSystemBarToPadding(@NonNull View view) {
        applyPadding(view, WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout(), Edge.TOP);
    }

    public static void applyBottomSystemBarToPadding(@NonNull View view) {
        applyPadding(view, WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout(), Edge.BOTTOM);
    }

    /**
     * Applies both status bar (top) and navigation bar (bottom) insets in a single listener.
     * Use this instead of calling {@link #applyTopSystemBarToPadding} and
     * {@link #applyBottomSystemBarToPadding} separately on the same view, which would
     * cause the second listener to overwrite the first.
     */
    public static void applyVerticalSystemBarsToPadding(@NonNull View view) {
        applyPadding(view, WindowInsetsCompat.Type.systemBars(), Edge.VERTICAL);
    }

    public static void applySystemBarsToMargins(@NonNull View view) {
        applyMargins(view, WindowInsetsCompat.Type.systemBars());
    }

    public static void applyBottomSystemBarToMargin(@NonNull View view) {
        applyMargins(view, WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout(), Edge.BOTTOM);
    }

    public static void applyTopSystemBarToMargin(@NonNull View view) {
        applyMargins(view, WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout(), Edge.TOP);
    }

    public static void dispatch(@NonNull View view) {
        ViewCompat.requestApplyInsets(view);
    }

    static void applyPadding(@NonNull View view, int insetTypes) {
        applyPadding(view, insetTypes, Edge.ALL);
    }

    static void applyPadding(@NonNull View view, int insetTypes, @NonNull Edge edge) {
        final InitialBounds initialBounds = capture(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            final Insets resolvedInsets = resolveInsets(insets, insetTypes);
            target.setPadding(
                    initialBounds.left + (edge.includeLeft ? resolvedInsets.left : 0),
                    initialBounds.top + (edge.includeTop ? resolvedInsets.top : 0),
                    initialBounds.right + (edge.includeRight ? resolvedInsets.right : 0),
                    initialBounds.bottom + (edge.includeBottom ? resolvedInsets.bottom : 0)
            );
            return insets;
        });
        dispatch(view);
    }

    static void applyMargins(@NonNull View view, int insetTypes) {
        applyMargins(view, insetTypes, Edge.ALL);
    }

    static void applyMargins(@NonNull View view, int insetTypes, @NonNull Edge edge) {
        final InitialBounds initialBounds = capture(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            final ViewGroup.LayoutParams params = target.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                return insets;
            }
            final Insets resolvedInsets = resolveInsets(insets, insetTypes);
            final ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
            marginLayoutParams.leftMargin = initialBounds.margins.left + (edge.includeLeft ? resolvedInsets.left : 0);
            marginLayoutParams.topMargin = initialBounds.margins.top + (edge.includeTop ? resolvedInsets.top : 0);
            marginLayoutParams.rightMargin = initialBounds.margins.right + (edge.includeRight ? resolvedInsets.right : 0);
            marginLayoutParams.bottomMargin = initialBounds.margins.bottom + (edge.includeBottom ? resolvedInsets.bottom : 0);
            target.setLayoutParams(marginLayoutParams);
            return insets;
        });
        dispatch(view);
    }

    @NonNull
    public static Insets resolveInsets(@NonNull WindowInsetsCompat insets, int insetTypes) {
        final Insets resolvedInsets = insets.getInsets(insetTypes);
        final DisplayCutoutCompat displayCutout = insets.getDisplayCutout();
        if (displayCutout == null) {
            return resolvedInsets;
        }
        return maxInsets(
                resolvedInsets,
                Insets.of(
                        displayCutout.getSafeInsetLeft(),
                        displayCutout.getSafeInsetTop(),
                        displayCutout.getSafeInsetRight(),
                        displayCutout.getSafeInsetBottom()
                )
        );
    }

    @NonNull
    static Insets maxInsets(@NonNull Insets primary, Insets secondary) {
        if (secondary == null) {
            return primary;
        }
        return Insets.of(
                Math.max(primary.left, secondary.left),
                Math.max(primary.top, secondary.top),
                Math.max(primary.right, secondary.right),
                Math.max(primary.bottom, secondary.bottom)
        );
    }

    @NonNull
    private static Rect readMargins(@NonNull View view) {
        final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            return new Rect(
                    marginLayoutParams.leftMargin,
                    marginLayoutParams.topMargin,
                    marginLayoutParams.rightMargin,
                    marginLayoutParams.bottomMargin
            );
        }
        return new Rect();
    }

    static final class InitialBounds {
        final int left;
        final int top;
        final int right;
        final int bottom;
        @NonNull
        final Rect margins;

        InitialBounds(int left, int top, int right, int bottom, @NonNull Rect margins) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.margins = new Rect(margins);
        }
    }

    static final class Edge {
        static final Edge ALL = new Edge(true, true, true, true);
        static final Edge TOP = new Edge(false, true, false, false);
        static final Edge BOTTOM = new Edge(false, false, false, true);
        static final Edge VERTICAL = new Edge(false, true, false, true);

        final boolean includeLeft;
        final boolean includeTop;
        final boolean includeRight;
        final boolean includeBottom;

        Edge(boolean includeLeft, boolean includeTop, boolean includeRight, boolean includeBottom) {
            this.includeLeft = includeLeft;
            this.includeTop = includeTop;
            this.includeRight = includeRight;
            this.includeBottom = includeBottom;
        }
    }
}

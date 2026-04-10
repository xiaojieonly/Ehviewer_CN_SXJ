package com.hippo.ehviewer.ui.adaptive;

import android.app.Activity;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowLayoutInfo;

import java.util.List;

public final class AdaptiveWindowState {

    public static final int WIDTH_COMPACT = 0;
    public static final int WIDTH_MEDIUM = 1;
    public static final int WIDTH_EXPANDED = 2;

    @NonNull
    public static final AdaptiveWindowState DEFAULT = new AdaptiveWindowState(
            WIDTH_COMPACT,
            0,
            0,
            0,
            false,
            false,
            false,
            new Rect()
    );

    private final int widthSizeClass;
    private final int windowWidthPx;
    private final int windowHeightPx;
    private final int windowWidthDp;
    private final boolean hasFoldingFeature;
    private final boolean separating;
    private final boolean verticalSplit;
    @NonNull
    private final Rect hingeBounds;

    private AdaptiveWindowState(
            int widthSizeClass,
            int windowWidthPx,
            int windowHeightPx,
            int windowWidthDp,
            boolean hasFoldingFeature,
            boolean separating,
            boolean verticalSplit,
            @NonNull Rect hingeBounds
    ) {
        this.widthSizeClass = widthSizeClass;
        this.windowWidthPx = windowWidthPx;
        this.windowHeightPx = windowHeightPx;
        this.windowWidthDp = windowWidthDp;
        this.hasFoldingFeature = hasFoldingFeature;
        this.separating = separating;
        this.verticalSplit = verticalSplit;
        this.hingeBounds = new Rect(hingeBounds);
    }

    @NonNull
    public static AdaptiveWindowState from(
            @NonNull Activity activity,
            int windowWidthPx,
            int windowHeightPx,
            @Nullable WindowLayoutInfo windowLayoutInfo
    ) {
        final float density = activity.getResources().getDisplayMetrics().density;
        final int widthDp = density == 0f ? 0 : Math.round(windowWidthPx / density);
        final int widthSizeClass;
        if (widthDp >= 840) {
            widthSizeClass = WIDTH_EXPANDED;
        } else if (widthDp >= 600) {
            widthSizeClass = WIDTH_MEDIUM;
        } else {
            widthSizeClass = WIDTH_COMPACT;
        }

        FoldingFeature foldingFeature = null;
        if (windowLayoutInfo != null) {
            final List<?> displayFeatures = windowLayoutInfo.getDisplayFeatures();
            for (Object feature : displayFeatures) {
                if (feature instanceof FoldingFeature) {
                    foldingFeature = (FoldingFeature) feature;
                    break;
                }
            }
        }

        if (foldingFeature == null) {
            return new AdaptiveWindowState(
                    widthSizeClass,
                    windowWidthPx,
                    windowHeightPx,
                    widthDp,
                    false,
                    false,
                    false,
                    new Rect()
            );
        }

        return new AdaptiveWindowState(
                widthSizeClass,
                windowWidthPx,
                windowHeightPx,
                widthDp,
                true,
                foldingFeature.isSeparating(),
                foldingFeature.getOrientation() == FoldingFeature.Orientation.VERTICAL,
                new Rect(foldingFeature.getBounds())
        );
    }

    public int getWidthSizeClass() {
        return widthSizeClass;
    }

    public int getWindowWidthPx() {
        return windowWidthPx;
    }

    public int getWindowHeightPx() {
        return windowHeightPx;
    }

    public int getWindowWidthDp() {
        return windowWidthDp;
    }

    public boolean hasFoldingFeature() {
        return hasFoldingFeature;
    }

    public boolean isSeparating() {
        return separating;
    }

    public boolean isVerticalSplit() {
        return verticalSplit;
    }

    public boolean supportsDualPane() {
        return widthSizeClass == WIDTH_EXPANDED;
    }

    public boolean useHingeDivider() {
        return supportsDualPane() && separating && verticalSplit && !hingeBounds.isEmpty();
    }

    public int getDividerWidthPx() {
        return useHingeDivider() ? hingeBounds.width() : 0;
    }

    @NonNull
    public Rect getHingeBounds() {
        return new Rect(hingeBounds);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AdaptiveWindowState other)) {
            return false;
        }
        return widthSizeClass == other.widthSizeClass
                && windowWidthPx == other.windowWidthPx
                && windowHeightPx == other.windowHeightPx
                && windowWidthDp == other.windowWidthDp
                && hasFoldingFeature == other.hasFoldingFeature
                && separating == other.separating
                && verticalSplit == other.verticalSplit
                && hingeBounds.equals(other.hingeBounds);
    }

    @Override
    public int hashCode() {
        int result = widthSizeClass;
        result = 31 * result + windowWidthPx;
        result = 31 * result + windowHeightPx;
        result = 31 * result + windowWidthDp;
        result = 31 * result + (hasFoldingFeature ? 1 : 0);
        result = 31 * result + (separating ? 1 : 0);
        result = 31 * result + (verticalSplit ? 1 : 0);
        result = 31 * result + hingeBounds.hashCode();
        return result;
    }
}

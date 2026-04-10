package com.hippo.ehviewer.ui.adaptive;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.concurrent.Executor;

public final class AdaptiveWindowStateController {

    public interface Listener {
        void onAdaptiveWindowStateChanged(@NonNull AdaptiveWindowState state);
    }

    @NonNull
    private final Activity activity;
    @NonNull
    private final Listener listener;
    @NonNull
    private final Executor executor;
    @NonNull
    private final WindowInfoTrackerCallbackAdapter callbackAdapter;

    @NonNull
    private final View.OnLayoutChangeListener onLayoutChangeListener = (
            view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom
    ) -> dispatch();

    @NonNull
    private final androidx.core.util.Consumer<WindowLayoutInfo> layoutInfoConsumer = info -> {
        lastWindowLayoutInfo = info;
        dispatch();
    };

    @NonNull
    private AdaptiveWindowState lastState = AdaptiveWindowState.DEFAULT;

    private boolean started;
    @NonNull
    private WindowLayoutInfo lastWindowLayoutInfo = new WindowLayoutInfo(java.util.Collections.emptyList());

    public AdaptiveWindowStateController(@NonNull Activity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.executor = ContextCompat.getMainExecutor(activity);
        this.callbackAdapter = new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity));
    }

    public void start() {
        if (started) {
            dispatch();
            return;
        }
        started = true;
        callbackAdapter.addWindowLayoutInfoListener(activity, executor, layoutInfoConsumer);
        activity.getWindow().getDecorView().addOnLayoutChangeListener(onLayoutChangeListener);
        dispatch();
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        callbackAdapter.removeWindowLayoutInfoListener(layoutInfoConsumer);
        activity.getWindow().getDecorView().removeOnLayoutChangeListener(onLayoutChangeListener);
    }

    public void refresh() {
        dispatch();
    }

    /**
     * Synchronously computes the current window state and stores it as lastState.
     * Call this immediately after construction so that {@code getLastState()} returns
     * the real value before {@link #start()} is invoked (which only happens in onStart).
     * This prevents a recreate() loop on tablets/foldables where the DEFAULT compact
     * state would differ from the actual expanded state.
     */
    @NonNull
    public AdaptiveWindowState computeInitialState() {
        final androidx.window.layout.WindowMetrics metrics =
                WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
        lastState = AdaptiveWindowState.from(
                activity,
                metrics.getBounds().width(),
                metrics.getBounds().height(),
                lastWindowLayoutInfo
        );
        return lastState;
    }

    @NonNull
    public AdaptiveWindowState getLastState() {
        return lastState;
    }

    private void dispatch() {
        final androidx.window.layout.WindowMetrics metrics =
                WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
        final AdaptiveWindowState newState = AdaptiveWindowState.from(
                activity,
                metrics.getBounds().width(),
                metrics.getBounds().height(),
                lastWindowLayoutInfo
        );
        if (!newState.equals(lastState)) {
            lastState = newState;
            listener.onAdaptiveWindowStateChanged(newState);
        }
    }
}

package com.hippo.ehviewer.util;

import android.os.Build;
import android.view.Display;

import androidx.annotation.Nullable;

public final class ReadingRefreshRate {

    public static final int SYSTEM_DEFAULT = 0;
    public static final int DISPLAY_MAX = -1;

    private ReadingRefreshRate() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static int sanitize(int value) {
        if (value == DISPLAY_MAX || value == SYSTEM_DEFAULT
                || value == 60 || value == 90 || value == 120) {
            return value;
        }
        return SYSTEM_DEFAULT;
    }

    @Nullable
    public static Display.Mode findBestMode(@Nullable Display display, int settingHz) {
        if (display == null || settingHz == SYSTEM_DEFAULT) {
            return null;
        }
        Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0) {
            return null;
        }
        Display.Mode current = display.getMode();
        int width = current.getPhysicalWidth();
        int height = current.getPhysicalHeight();

        float maxHz = maxRefreshRate(modes, width, height, true);
        if (maxHz <= 0f) {
            maxHz = maxRefreshRate(modes, width, height, false);
        }
        if (maxHz <= 0f) {
            return current;
        }

        float targetHz = settingHz == DISPLAY_MAX ? maxHz : Math.min(settingHz, maxHz);
        Display.Mode best = null;
        float bestHz = -1f;
        boolean foundSameRes = false;
        for (Display.Mode mode : modes) {
            boolean sameRes = mode.getPhysicalWidth() == width && mode.getPhysicalHeight() == height;
            float hz = mode.getRefreshRate();
            if (hz > targetHz + 0.1f) {
                continue;
            }
            if (sameRes) {
                if (!foundSameRes || hz > bestHz) {
                    foundSameRes = true;
                    bestHz = hz;
                    best = mode;
                }
            } else if (!foundSameRes && hz > bestHz) {
                bestHz = hz;
                best = mode;
            }
        }
        return best != null ? best : current;
    }

    public static float resolveHz(@Nullable Display display, int settingHz) {
        if (settingHz == SYSTEM_DEFAULT) {
            return 0f;
        }
        Display.Mode mode = findBestMode(display, settingHz);
        return mode != null ? mode.getRefreshRate() : 0f;
    }

    private static float maxRefreshRate(Display.Mode[] modes, int width, int height, boolean matchRes) {
        float maxHz = 0f;
        for (Display.Mode mode : modes) {
            if (matchRes && (mode.getPhysicalWidth() != width || mode.getPhysicalHeight() != height)) {
                continue;
            }
            maxHz = Math.max(maxHz, mode.getRefreshRate());
        }
        return maxHz;
    }
}

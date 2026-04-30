package com.hippo.ehviewer.util;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * 设备类型检测工具类
 * 用于判断当前设备是手机还是平板，以及屏幕方向
 */
public class DeviceUtils {
    
    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        /** 手机竖屏 */
        PHONE_PORTRAIT,
        /** 手机横屏 */
        PHONE_LANDSCAPE,
        /** 小平板 (sw600dp) */
        TABLET_SMALL,
        /** 大平板 (sw720dp+) */
        TABLET_LARGE
    }
    
    // 最小屏幕宽度阈值（dp）
    private static final int SW600DP = 600;
    private static final int SW720DP = 720;
    
    /**
     * 判断是否为平板设备
     * 
     * @param context 上下文
     * @return true表示平板，false表示手机
     */
    public static boolean isTablet(@NonNull Context context) {
        return getSmallestScreenWidthDp(context) >= SW600DP;
    }
    
    /**
     * 获取当前设备类型
     * 
     * @param context 上下文
     * @return 设备类型枚举
     */
    @NonNull
    public static DeviceType getDeviceType(@NonNull Context context) {
        int smallestWidth = getSmallestScreenWidthDp(context);
        int orientation = context.getResources().getConfiguration().orientation;
        
        if (smallestWidth >= SW720DP) {
            return DeviceType.TABLET_LARGE;
        } else if (smallestWidth >= SW600DP) {
            return DeviceType.TABLET_SMALL;
        } else {
            // 手机设备
            return orientation == Configuration.ORIENTATION_LANDSCAPE 
                    ? DeviceType.PHONE_LANDSCAPE 
                    : DeviceType.PHONE_PORTRAIT;
        }
    }
    
    /**
     * 获取最小屏幕宽度（dp）
     * 使用 Configuration.smallestScreenWidthDp
     * 
     * @param context 上下文
     * @return 最小屏幕宽度dp值
     */
    public static int getSmallestScreenWidthDp(@NonNull Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp;
    }
    
    /**
     * 判断是否应该使用双栏布局
     * 通常在大平板上启用
     * 
     * @param context 上下文
     * @return true表示应使用双栏布局
     */
    public static boolean shouldUseDualPane(@NonNull Context context) {
        return getSmallestScreenWidthDp(context) >= SW720DP;
    }
    
    /**
     * 根据设备类型推荐span count
     * 
     * @param context 上下文
     * @param baseSpanCount 基础span count（通常为手机竖屏的值）
     * @return 推荐的span count
     */
    public static int getSuggestedSpanCount(@NonNull Context context, int baseSpanCount) {
        DeviceType deviceType = getDeviceType(context);
        
        switch (deviceType) {
            case TABLET_LARGE:
                // 大平板：至少2倍
                return Math.max(baseSpanCount * 2, 4);
            case TABLET_SMALL:
                // 小平板：1.5倍
                return Math.max((int)(baseSpanCount * 1.5), 3);
            case PHONE_LANDSCAPE:
                // 手机横屏：1.5倍
                return Math.max((int)(baseSpanCount * 1.5), 2);
            case PHONE_PORTRAIT:
            default:
                // 手机竖屏：保持原值
                return baseSpanCount;
        }
    }
    
    /**
     * 获取屏幕宽度（像素）
     * 
     * @param context 上下文
     * @return 屏幕宽度像素值
     */
    public static int getScreenWidthPixels(@NonNull Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.widthPixels;
    }
    
    /**
     * 获取屏幕高度（像素）
     * 
     * @param context 上下文
     * @return 屏幕高度像素值
     */
    public static int getScreenHeightPixels(@NonNull Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.heightPixels;
    }
    
    /**
     * dp转px
     * 
     * @param context 上下文
     * @param dp dp值
     * @return px值
     */
    public static int dpToPx(@NonNull Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
    
    /**
     * px转dp
     * 
     * @param context 上下文
     * @param px px值
     * @return dp值
     */
    public static int pxToDp(@NonNull Context context, float px) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (px / density + 0.5f);
    }
}

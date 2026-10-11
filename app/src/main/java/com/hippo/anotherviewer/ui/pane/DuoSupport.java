package com.hippo.anotherviewer.ui.pane;

import android.os.Build;

/**
 * Surface Duo 真机识别。Duo 阅读适配在真机上完全由真实折叠几何驱动：
 * 阅读模拟只服务于没有 Duo 硬件的评估场景——真机上隐藏模拟入口与双页
 * 偏好，跨屏即双页、单屏/折叠即单页，不给用户选择（设置与阅读菜单同步隐藏）。
 */
public final class DuoSupport {

    private static final String DUO_MODEL_PREFIX = "Surface Duo";

    private DuoSupport() {
    }

    /** 命中 Surface Duo / Surface Duo 2 等真机型号。 */
    public static boolean isDuoHardware() {
        return Build.MODEL != null && Build.MODEL.startsWith(DUO_MODEL_PREFIX);
    }
}

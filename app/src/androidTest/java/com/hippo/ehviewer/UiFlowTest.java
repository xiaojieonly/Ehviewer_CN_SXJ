package com.hippo.ehviewer;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.ui.MainActivity;

import java.io.File;
import java.io.FileOutputStream;

/**
 * iOS27 底部导航 UI 自动化测试(框架插桩,无需第三方依赖):
 * <ol>
 * <li>登录场景:底部导航栏隐藏、状态栏/系统导航栏透明、对比度蒙层关闭、图标明暗随主题</li>
 * <li>游客模式进入主页:底部栏可见、5 个 tab 图标与文字居中对齐、分段控件可见、
 * 场景底部让位、搜索栏/分段控件/底栏等表面不透明</li>
 * <li>遍历 5 个 tab:场景正常切换、底部栏保持可见;
 * 下载/历史页验证悬浮 app bar 与列表留位,滚动后截图审查收起与状态栏联动</li>
 * <li>个人页:资料卡(头像/账户名)、主题、设置入口可见</li>
 * </ol>
 * 每步截图写入应用内部存储 files/uitest/,由 scripts/run_ui_test.sh 拉取。
 *
 * 运行前提:设备已开启 USB 调试;MIUI 需要「USB调试(安全设置)」才能注入触摸事件。
 * 运行前请 force-stop 应用(脚本已处理),测试通过 Settings.putNeedSignIn(true)
 * 强制从登录流程开始,与设备当前的登录状态无关。
 */
public class UiFlowTest extends InstrumentationTestCase {

    private static final String PKG = "com.xjs.ehviewer.debug";
    private static final long SCENE_TIMEOUT = 15000;

    private Instrumentation mInstr;
    private Context mApp;
    private volatile Activity mResumed;
    private Application.ActivityLifecycleCallbacks mTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstr = getInstrumentation();
        mApp = mInstr.getTargetContext();
        mTracker = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                mResumed = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) { }

            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) { }

            @Override
            public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityStopped(Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

            @Override
            public void onActivityDestroyed(Activity activity) { }
        };
        ((Application) mApp.getApplicationContext()).registerActivityLifecycleCallbacks(mTracker);
    }

    @Override
    protected void tearDown() throws Exception {
        ((Application) mApp.getApplicationContext()).unregisterActivityLifecycleCallbacks(mTracker);
        super.tearDown();
    }

    public void testBottomNavUiFlow() throws Exception {
        // 游客模式会持久化 needSignIn=false,每次运行强制从登录流程开始,保证可重复。
        // 直接写默认 SharedPreferences(与 Settings.getNeedSignIn 读同一份),
        // 不依赖 Settings 静态初始化时机
        PreferenceManager.getDefaultSharedPreferences(mApp).edit()
                .putBoolean("need_sign_in", true)
                // 关闭各场景的一次性 ShowcaseView 引导层,避免遮挡截图与断言
                .putBoolean("guide_quick_search", false)
                .putBoolean("guide_collections", false)
                .putBoolean("guide_download_thumb", false)
                .putBoolean("guide_download_labels", false)
                .putBoolean("guide_gallery", false)
                .apply();

        launchApp();
        MainActivity main = (MainActivity) waitForActivity(MainActivity.class, 20000);
        SystemClock.sleep(1500);

        // ---- 阶段1:登录场景,底部栏隐藏,系统栏沉浸式 ----
        waitForScene(main, "SignInScene", SCENE_TIMEOUT);
        View nav = main.findViewById(R.id.bottom_nav);
        assertNotNull("找不到 bottom_nav", nav);
        assertEquals("登录场景应隐藏底部导航栏", View.GONE, nav.getVisibility());
        assertImmersiveBars(main);
        int loginBottomOccupied = main.getBottomOccupiedHeight();
        screenshot("01_login");

        // ---- 阶段2:游客模式进入主页,底部栏可见且对齐 ----
        View guest = waitForView(main, R.id.guest_mode, 5000);
        clickOn(guest);
        waitForScene(main, "GalleryListScene", SCENE_TIMEOUT);
        SystemClock.sleep(1500);

        assertEquals("主页应显示底部导航栏", View.VISIBLE, nav.getVisibility());
        assertImmersiveBars(main);
        assertOpaqueSurfaces(main);
        assertNavAlignment(nav);
        int homeBottomOccupied = main.getBottomOccupiedHeight();
        assertTrue("显示底部栏后场景底部让位应增大(" + loginBottomOccupied
                + " -> " + homeBottomOccupied + ")", homeBottomOccupied > loginBottomOccupied);
        View seg = waitForView(main, R.id.mode_segmented, 5000);
        assertTrue("首页分段控件应可见", seg.isShown());
        screenshot("02_home");

        // ---- 阶段3:遍历底部 5 个 tab ----
        int[] tabIds = {
                R.id.nav_favourite, R.id.nav_downloads, R.id.nav_history,
                R.id.nav_more, R.id.nav_homepage
        };
        String[] sceneNames = {
                "FavoritesScene", "DownloadsScene", "HistoryScene", "MoreScene", "GalleryListScene"
        };
        String[] shots = { "03_favourite", "04_downloads", "05_history", "06_more", "07_home" };
        for (int i = 0; i < tabIds.length; i++) {
            View tab = nav.findViewById(tabIds[i]);
            assertNotNull("找不到 tab: " + sceneNames[i], tab);
            clickOn(tab);
            waitForScene(main, sceneNames[i], SCENE_TIMEOUT);
            SystemClock.sleep(1200);
            assertEquals(sceneNames[i] + " 应保持底部导航栏可见", View.VISIBLE, nav.getVisibility());
            screenshot(shots[i]);
            // 下载/历史页:悬浮 app bar + 列表留位断言,滚动后追加截图供人工审查收起效果
            if ("DownloadsScene".equals(sceneNames[i]) || "HistoryScene".equals(sceneNames[i])) {
                assertOverlayAppBar(main, sceneNames[i]);
                View recycler = main.findViewById(R.id.recycler_view);
                scrollRecycler(recycler, 1200);
                SystemClock.sleep(600);
                screenshot(shots[i].replace("_", "_scrolled_"));
                scrollRecycler(recycler, -1200);
                SystemClock.sleep(600);
            }
        }

        // ---- 阶段4:个人页资料区 ----
        View moreTab = nav.findViewById(R.id.nav_more);
        assertNotNull(moreTab);
        clickOn(moreTab);
        waitForScene(main, "MoreScene", SCENE_TIMEOUT);
        SystemClock.sleep(1200);
        assertViewVisible(main, R.id.avatar, "个人页头像");
        assertViewVisible(main, R.id.display_name, "个人页账户名");
        assertViewVisible(main, R.id.theme_row, "个人页主题入口");
        assertViewVisible(main, R.id.entry_settings, "个人页设置入口");
        screenshot("08_more_profile");
    }

    /** 下载/历史页应为悬浮 app bar 结构,且列表 paddingTop 已为 app bar 留位 */
    private void assertOverlayAppBar(MainActivity activity, String sceneName) {
        View appBar = waitForView(activity, R.id.appbar_container, 5000);
        assertTrue(sceneName + " app bar 应已布局", appBar.getHeight() > 0);
        View recycler = activity.findViewById(R.id.recycler_view);
        assertNotNull(sceneName + " 找不到列表", recycler);
        assertTrue(sceneName + " 列表 paddingTop(" + recycler.getPaddingTop()
                        + ")应为 app bar(" + appBar.getHeight() + ")留位",
                recycler.getPaddingTop() >= appBar.getHeight());
    }

    private void scrollRecycler(final View recycler, final int dy) {
        if (recycler == null) {
            return;
        }
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (recycler instanceof RecyclerView) {
                    ((RecyclerView) recycler).scrollBy(0, dy);
                }
            }
        });
    }

    /*---------------
     断言辅助
     ---------------*/

    private void assertImmersiveBars(Activity activity) {
        Window w = activity.getWindow();
        assertEquals("状态栏应为透明", Color.TRANSPARENT, w.getStatusBarColor());
        assertEquals("系统导航栏应为透明", Color.TRANSPARENT, w.getNavigationBarColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse("状态栏对比度蒙层应关闭", w.isStatusBarContrastEnforced());
            assertFalse("系统导航栏对比度蒙层应关闭", w.isNavigationBarContrastEnforced());
        }
        int ui = w.getDecorView().getSystemUiVisibility();
        boolean lightStatus = (ui & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0;
        boolean lightNav = (ui & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0;
        boolean lightTheme = Settings.getTheme() == Settings.THEME_LIGHT;
        assertEquals("浅色主题应使用深色状态栏图标", lightTheme, lightStatus);
        assertEquals("浅色主题应使用深色导航栏图标", lightTheme, lightNav);
    }

    /** 应用内玻璃表面必须不透明,避免下层内容透出 */
    private void assertOpaqueSurfaces(Activity activity) {
        String[] names = { "glassFillColor" };
        int[] attrs = { R.attr.glassFillColor };
        for (int i = 0; i < attrs.length; i++) {
            int color = AttrResources.getAttrColor(activity, attrs[i]);
            assertEquals(names[i] + " 应为不透明(alpha=255)", 0xFF, Color.alpha(color));
        }
    }

    /** M3 标准底栏:壳 → BottomNavigationView → 菜单容器 → 5 个 tab,逐项校验图标/文字居中 */
    private void assertNavAlignment(View nav) {
        ViewGroup shell = (ViewGroup) nav;
        assertTrue("底部导航壳应包含 NavigationBarView", shell.getChildCount() == 1);
        ViewGroup navView = (ViewGroup) shell.getChildAt(0);
        assertTrue("NavigationBarView 应包含菜单容器", navView.getChildCount() >= 1);
        ViewGroup menuView = (ViewGroup) navView.getChildAt(0);
        assertEquals("tab 数量应为 5", 5, menuView.getChildCount());
        for (int i = 0; i < menuView.getChildCount(); i++) {
            View item = menuView.getChildAt(i);
            View icon = findFirst(item, ImageView.class);
            View label = findFirst(item, TextView.class);
            assertNotNull("tab" + i + " 缺少图标", icon);
            assertNotNull("tab" + i + " 缺少文字", label);
            int itemCenter = centerX(item);
            int iconCenter = centerX(icon);
            int labelCenter = centerX(label);
            assertTrue("tab" + i + " 图标未居中对齐: iconCenter=" + iconCenter
                    + " itemCenter=" + itemCenter, Math.abs(iconCenter - itemCenter) <= 1);
            assertTrue("tab" + i + " 文字未居中对齐: labelCenter=" + labelCenter
                    + " itemCenter=" + itemCenter, Math.abs(labelCenter - itemCenter) <= 1);
        }
    }

    private View findFirst(View view, Class<?> cls) {
        if (cls.isInstance(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirst(group.getChildAt(i), cls);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void assertViewVisible(Activity activity, int id, String desc) {
        View v = waitForView(activity, id, 5000);
        assertTrue(desc + " 应可见", v.isShown());
    }

    /*---------------
     等待辅助
     ---------------*/

    private Activity waitForActivity(Class<?> cls, long ms) {
        long end = SystemClock.elapsedRealtime() + ms;
        while (SystemClock.elapsedRealtime() < end) {
            Activity a = mResumed;
            if (a != null && cls.isInstance(a)) {
                return a;
            }
            SystemClock.sleep(100);
        }
        fail("等待 Activity 超时: " + cls.getSimpleName());
        return null;
    }

    private void waitForScene(MainActivity activity, String simpleName, long ms) {
        long end = SystemClock.elapsedRealtime() + ms;
        String last = null;
        while (SystemClock.elapsedRealtime() < end) {
            Class<?> top = activity.getTopSceneClass();
            last = top == null ? null : top.getSimpleName();
            if (simpleName.equals(last)) {
                return;
            }
            SystemClock.sleep(100);
        }
        fail("等待场景 " + simpleName + " 超时,当前场景=" + last);
    }

    private View waitForView(Activity activity, int id, long ms) {
        long end = SystemClock.elapsedRealtime() + ms;
        while (SystemClock.elapsedRealtime() < end) {
            View v = activity.findViewById(id);
            if (v != null) {
                return v;
            }
            SystemClock.sleep(100);
        }
        fail("找不到 view: id=0x" + Integer.toHexString(id));
        return null;
    }

    /*---------------
     点击(MIUI 未开「USB调试(安全设置)」时连 instrumentation 的事件注入
     也会被 INJECT_EVENTS 拦截,因此直接在主线程触发 performClick,
     走同一套 OnClickListener,绕过输入系统)
     ---------------*/

    private void clickOn(final View v) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                v.performClick();
            }
        });
    }

    /*---------------
     其他
     ---------------*/

    private void launchApp() {
        Intent intent = mApp.getPackageManager().getLaunchIntentForPackage(PKG);
        assertNotNull("找不到启动 Intent", intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mApp.startActivity(intent);
    }

    private int centerX(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return loc[0] + v.getWidth() / 2;
    }

    private void screenshot(String name) {
        Bitmap bmp = mInstr.getUiAutomation().takeScreenshot();
        if (bmp == null) {
            return;
        }
        try {
            File dir = new File(mApp.getFilesDir(), "uitest");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"));
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception ignore) {
            // 截图失败不阻塞测试
        } finally {
            bmp.recycle();
        }
    }
}

package com.hippo.ehviewer;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.SettingsActivity;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 主界面现代化收尾的 UI 自动化验证(框架插桩,无需第三方依赖):
 * <ol>
 * <li>tab 切换:点击后连续截取转场中间帧,供人工审查 fade through 动画</li>
 * <li>下载页:状态栏区域颜色应随悬浮 toolbar 主题色着色(像素采样断言)</li>
 * <li>底部导航栏滚动显隐:经 MainActivity 联动 API 验证跟手位移与 IDLE 吸附
 * (设备无网络列表无内容,无法真实滚动,故直接驱动联动入口),
 * 显隐期间场景底部让位高度不得变化</li>
 * <li>设置页:MaterialToolbar 顶栏可见、系统栏沉浸式,进入 EH 子页截图审查
 * Material 3 开关样式</li>
 * </ol>
 * 截图写入 files/uitest/,由 scripts/run_ui_test.sh 拉取。
 */
public class ModernUiTest extends InstrumentationTestCase {

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

    public void testModernUiFlow() throws Exception {
        // 游客态直接进入主页;关闭一次性引导层
        PreferenceManager.getDefaultSharedPreferences(mApp).edit()
                .putBoolean("need_sign_in", false)
                .putBoolean("guide_quick_search", false)
                .putBoolean("guide_collections", false)
                .putBoolean("guide_download_thumb", false)
                .putBoolean("guide_download_labels", false)
                .putBoolean("guide_gallery", false)
                .apply();

        launchApp();
        MainActivity main = (MainActivity) waitForActivity(MainActivity.class, 20000);
        waitForScene(main, "GalleryListScene", SCENE_TIMEOUT);
        SystemClock.sleep(1500);
        View nav = main.findViewById(R.id.bottom_nav);
        assertNotNull("找不到 bottom_nav", nav);
        assertEquals("主页应显示底部导航栏", View.VISIBLE, nav.getVisibility());

        // ---- 阶段1:tab 切换转场中间帧(人工审查 fade through) ----
        View favTab = nav.findViewById(R.id.nav_favourite);
        assertNotNull(favTab);
        clickOn(favTab);
        SystemClock.sleep(130);
        screenshot("20_transition_frame_a");
        SystemClock.sleep(140);
        screenshot("20_transition_frame_b");
        waitForScene(main, "FavoritesScene", SCENE_TIMEOUT);
        SystemClock.sleep(1000);

        // ---- 阶段2:下载页状态栏颜色随顶栏着色(像素采样) ----
        View dlTab = nav.findViewById(R.id.nav_downloads);
        assertNotNull(dlTab);
        clickOn(dlTab);
        waitForScene(main, "DownloadsScene", SCENE_TIMEOUT);
        SystemClock.sleep(1200);
        assertStatusBarScrim(main, "下载页");
        // 悬浮 app bar 应延伸进状态栏区域(容器顶部 padding = 状态栏高度)
        View appBarContainer = main.findViewById(R.id.appbar_container);
        assertNotNull("下载页找不到悬浮 app bar", appBarContainer);
        assertTrue("悬浮 app bar 应延伸进状态栏区域(paddingTop>0)",
                appBarContainer.getPaddingTop() > 0);
        screenshot("21_downloads_statusbar");

        // ---- 阶段3:底部导航栏滚动显隐(直接驱动联动 API) ----
        View homeTab = nav.findViewById(R.id.nav_homepage);
        assertNotNull(homeTab);
        clickOn(homeTab);
        waitForScene(main, "GalleryListScene", SCENE_TIMEOUT);
        SystemClock.sleep(1200);
        assertEquals("回到主页底部导航栏应恢复显示", 0f, nav.getTranslationY(), 0.5f);

        final int occupiedBefore = main.getBottomOccupiedHeight();
        scrollBottomNav(main, 2000);
        SystemClock.sleep(400);
        float maxOffset = nav.getTranslationY();
        assertTrue("下滚后底栏应滑出(translationY>0),实际=" + maxOffset, maxOffset > 0);
        assertEquals("滚动显隐期间底栏应保持 VISIBLE(纯位移)", View.VISIBLE, nav.getVisibility());
        assertEquals("滚动显隐不得改变场景底部让位", occupiedBefore, main.getBottomOccupiedHeight());
        screenshot("22_home_nav_hidden");

        // IDLE 吸附:已过半应吸附到全隐
        settleBottomNav(main);
        SystemClock.sleep(500);
        assertEquals("吸附后底栏应全隐(translationY=让位高度)",
                (float) occupiedBefore, nav.getTranslationY(), 1.5f);

        scrollBottomNav(main, -2000);
        SystemClock.sleep(400);
        assertEquals("上滑后底栏应回到全显(translationY=0)", 0f, nav.getTranslationY(), 0.5f);
        assertEquals("让位高度仍不得变化", occupiedBefore, main.getBottomOccupiedHeight());
        screenshot("23_home_nav_shown");

        // ---- 阶段3.6:真实滑动手势驱动底栏显隐(需列表有内容) ----
        View gestureRv = main.findViewById(R.id.recycler_view);
        if (gestureRv instanceof ViewGroup && ((ViewGroup) gestureRv).getChildCount() > 0) {
            float h = gestureRv.getHeight();
            // 手指上甩(内容下滚,dy>0):底栏应跟手隐藏
            dragOn(gestureRv, h * 0.7f, h * 0.15f);
            SystemClock.sleep(900);
            assertTrue("上甩后底栏应隐藏(translationY>0),实际=" + nav.getTranslationY(),
                    nav.getTranslationY() > 0);
            screenshot("28_home_drag_hidden");
            // 手指下甩(内容回滚,dy<0):底栏应恢复显示;期间分页加载派发的
            // onScrolled(0,0) 不得打断回位动画
            dragOn(gestureRv, h * 0.15f, h * 0.7f);
            SystemClock.sleep(900);
            assertEquals("下甩后底栏应恢复显示(translationY=0)",
                    0f, nav.getTranslationY(), 0.5f);
            screenshot("29_home_drag_shown");
        } else {
            android.util.Log.w("ModernUiTest", "主页列表无内容,跳过真实手势底栏验证");
        }

        // ---- 阶段3.5:画廊详情页状态栏随 header 着色(依赖网络内容,无内容则跳过) ----
        View listRv = main.findViewById(R.id.recycler_view);
        if (listRv instanceof ViewGroup && ((ViewGroup) listRv).getChildCount() > 0) {
            tapOn(listRv, ((ViewGroup) listRv).getChildAt(0));
            waitForScene(main, "GalleryDetailScene", SCENE_TIMEOUT);
            SystemClock.sleep(2000);
            assertEquals("详情页应隐藏底部导航栏", View.GONE, nav.getVisibility());
            assertStatusBarScrim(main, "画廊详情页", R.attr.galleryDetailHeaderBackgroundColor);
            screenshot("27_gallery_detail_statusbar");
            mInstr.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mResumed.onBackPressed();
                }
            });
            waitForScene(main, "GalleryListScene", SCENE_TIMEOUT);
            SystemClock.sleep(1000);
        } else {
            android.util.Log.w("ModernUiTest", "主页列表无内容,跳过详情页状态栏验证");
        }

        // ---- 阶段3.7:右侧抽屉沉浸式(全高延伸进系统栏区域,内容内部避让) ----
        final View drawerLayout = main.findViewById(R.id.draw_view);
        final View drawer = main.findViewById(R.id.right_drawer);
        assertNotNull("找不到右侧抽屉", drawer);
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ((com.hippo.ehviewer.widget.EhDrawerLayout) drawerLayout)
                        .openDrawer(android.view.Gravity.RIGHT);
            }
        });
        SystemClock.sleep(900);
        assertEquals("抽屉应顶到屏幕顶端(全高沉浸式)", 0, drawer.getTop());
        assertTrue("抽屉内容应避让状态栏(paddingTop>0)", drawer.getPaddingTop() > 0);
        screenshot("30_drawer_immersive");
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ((com.hippo.ehviewer.widget.EhDrawerLayout) drawerLayout).closeDrawers();
            }
        });
        SystemClock.sleep(600);

        // ---- 阶段4:设置页 M3 顶栏与沉浸式 ----
        View moreTab = nav.findViewById(R.id.nav_more);
        assertNotNull(moreTab);
        clickOn(moreTab);
        waitForScene(main, "MoreScene", SCENE_TIMEOUT);
        SystemClock.sleep(1200);
        View entry = waitForView(main, R.id.entry_settings, 5000);
        clickOn(entry);

        SettingsActivity settings = (SettingsActivity) waitForActivity(SettingsActivity.class, 8000);
        SystemClock.sleep(1200);
        View toolbar = waitForView(settings, R.id.toolbar, 5000);
        assertTrue("设置页 M3 顶栏应可见", toolbar.isShown());
        assertImmersiveBars(settings);
        screenshot("24_settings_m3");

        // 进入首个设置子页(EH),截图审查 Material 3 开关
        View recycler = findFirst(settings.getWindow().getDecorView().getRootView(), RecyclerView.class);
        assertNotNull("设置页找不到偏好列表", recycler);
        ViewGroup list = (ViewGroup) recycler;
        assertTrue("设置首页应有入口项", list.getChildCount() > 0);
        clickOn(list.getChildAt(0));
        SystemClock.sleep(1500);
        screenshot("25_settings_eh_switch");

        // 滚动子页直到 M3 开关进入视野,验证 MaterialSwitch 已生效并截图
        View switchView = scrollUntilVisible(
                settings.getWindow().getDecorView().getRootView(),
                com.google.android.material.materialswitch.MaterialSwitch.class, 12);
        assertNotNull("设置子页应能找到 MaterialSwitch 开关", switchView);
        SystemClock.sleep(400);
        screenshot("26_settings_switch_m3");
    }

    /** 在根视图内找到首个 RecyclerView 并逐屏滚动,直到目标类型 view 可见 */
    @Nullable
    private View scrollUntilVisible(View root, Class<?> cls, int maxScrolls) {
        View recycler = findFirst(root, RecyclerView.class);
        for (int i = 0; i < maxScrolls; i++) {
            View found = findFirst(root, cls);
            if (found != null && found.isShown()) {
                return found;
            }
            if (recycler == null) {
                return null;
            }
            final RecyclerView rv = (RecyclerView) recycler;
            mInstr.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    rv.scrollBy(0, rv.getHeight() * 3 / 4);
                }
            });
            SystemClock.sleep(350);
        }
        return findFirst(root, cls);
    }

    /*---------------
     断言与驱动辅助
     ---------------*/

    /** 状态栏区域颜色应随指定主题属性着色;采样点避开左侧时钟与右侧系统图标 */
    private void assertStatusBarScrim(Activity activity, String desc, int attrRes) {
        int expected = AttrResources.getAttrColor(activity, attrRes);
        Bitmap bmp = mInstr.getUiAutomation().takeScreenshot();
        assertNotNull("截图失败", bmp);
        int actual = bmp.getPixel((int) (bmp.getWidth() * 0.35f), 6);
        bmp.recycle();
        assertTrue(desc + " 状态栏区域颜色应随顶栏(期望≈" + Integer.toHexString(expected)
                + ",实际=" + Integer.toHexString(actual) + ")", colorClose(expected, actual));
    }

    /** 状态栏区域颜色应随顶栏主题色着色 */
    private void assertStatusBarScrim(Activity activity, String desc) {
        assertStatusBarScrim(activity, desc, R.attr.toolbarColor);
    }

    private boolean colorClose(int expected, int actual) {
        return Math.abs(Color.red(expected) - Color.red(actual)) <= 24
                && Math.abs(Color.green(expected) - Color.green(actual)) <= 24
                && Math.abs(Color.blue(expected) - Color.blue(actual)) <= 24;
    }

    private void assertImmersiveBars(Activity activity) {
        Window w = activity.getWindow();
        assertEquals("状态栏应为透明", Color.TRANSPARENT, w.getStatusBarColor());
        assertEquals("系统导航栏应为透明", Color.TRANSPARENT, w.getNavigationBarColor());
        int ui = w.getDecorView().getSystemUiVisibility();
        boolean lightStatus = (ui & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0;
        boolean lightTheme = Settings.getTheme() == Settings.THEME_LIGHT;
        assertEquals("浅色主题应使用深色状态栏图标", lightTheme, lightStatus);
    }

    private void scrollBottomNav(final MainActivity activity, final int dy) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.onContentListScrolled(dy);
            }
        });
    }

    private void settleBottomNav(final MainActivity activity) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.settleBottomNav();
            }
        });
    }

    private void clickOn(final View v) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                v.performClick();
            }
        });
    }

    /**
     * 在 target 中心合成 DOWN/UP 触摸序列并直接分发给 root
     * (主线程内分发,不经过输入系统,绕过 MIUI 的 INJECT_EVENTS 限制;
     * 适用于 EasyRecyclerView 这类经 onTouchEvent 手势识别派发点击的控件)
     */
    private void tapOn(final View root, final View target) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                int[] rl = new int[2];
                int[] tl = new int[2];
                root.getLocationOnScreen(rl);
                target.getLocationOnScreen(tl);
                float x = tl[0] - rl[0] + target.getWidth() / 2f;
                float y = tl[1] - rl[1] + target.getHeight() / 2f;
                long t = SystemClock.uptimeMillis();
                android.view.MotionEvent down = android.view.MotionEvent.obtain(
                        t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
                root.dispatchTouchEvent(down);
                down.recycle();
                android.view.MotionEvent up = android.view.MotionEvent.obtain(
                        t, t + 80, android.view.MotionEvent.ACTION_UP, x, y, 0);
                root.dispatchTouchEvent(up);
                up.recycle();
            }
        });
    }

    /** 在 view 水平中线合成垂直拖拽手势(DOWN + 多段 MOVE + UP),驱动列表真实滚动 */
    private void dragOn(final View v, final float fromY, final float toY) {
        mInstr.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                float x = v.getWidth() / 2f;
                long t = SystemClock.uptimeMillis();
                int steps = 12;
                android.view.MotionEvent down = android.view.MotionEvent.obtain(
                        t, t, android.view.MotionEvent.ACTION_DOWN, x, fromY, 0);
                v.dispatchTouchEvent(down);
                down.recycle();
                for (int i = 1; i <= steps; i++) {
                    float y = fromY + (toY - fromY) * i / steps;
                    android.view.MotionEvent move = android.view.MotionEvent.obtain(
                            t, t + i * 16, android.view.MotionEvent.ACTION_MOVE, x, y, 0);
                    v.dispatchTouchEvent(move);
                    move.recycle();
                }
                android.view.MotionEvent up = android.view.MotionEvent.obtain(
                        t, t + steps * 16 + 30, android.view.MotionEvent.ACTION_UP, x, toY, 0);
                v.dispatchTouchEvent(up);
                up.recycle();
            }
        });
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
     其他
     ---------------*/

    private void launchApp() {
        Intent intent = mApp.getPackageManager().getLaunchIntentForPackage(PKG);
        assertNotNull("找不到启动 Intent", intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mApp.startActivity(intent);
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

    /**
     * 状态栏完全透明 + 首页分段控件 + 缩略图模式沉浸式验证:
     * <ol>
     * <li>分段控件:轨道应为半透明毛玻璃填充;选中块应被布局(尺寸>0)且
     * 层级低于文字行(文字行是最上层子 View),切换分段后选中块跟随</li>
     * <li>状态栏完全透明:无遮盖条,系统栏透明;舞台顶到屏幕顶端,
     * 搜索栏容器完全位于状态栏下方</li>
     * <li>缩略图模式:滚动列表后列表内容应进入状态栏区域
     * (彩色缩略图直接透到状态栏下方),证明内容沉浸式穿入状态栏区域</li>
     * <li>下载页:状态栏随顶栏着色不受影响</li>
     * </ol>
     */
    public void testStatusBarImmersiveFlow() throws Exception {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);
        String oldListMode = prefs.getString("list_mode", "0");
        prefs.edit()
                .putBoolean("need_sign_in", false)
                .putBoolean("guide_quick_search", false)
                .putBoolean("guide_collections", false)
                .putBoolean("guide_download_thumb", false)
                .putBoolean("guide_download_labels", false)
                .putBoolean("guide_gallery", false)
                // 缩略图模式:彩色图片最能暴露状态栏未沉浸式的问题
                .putString("list_mode", "1")
                .apply();
        try {
            launchApp();
            MainActivity main = (MainActivity) waitForActivity(MainActivity.class, 20000);
            waitForScene(main, "GalleryListScene", SCENE_TIMEOUT);
            SystemClock.sleep(1500);

            // ---- 分段控件:毛玻璃轨道 + 选中块层级与尺寸 ----
            View seg = main.findViewById(R.id.mode_segmented);
            assertNotNull("找不到分段控件", seg);
            assertEquals("主页普通态应显示分段控件", View.VISIBLE, seg.getVisibility());
            assertTrue("分段控件应为 ViewGroup", seg instanceof ViewGroup);
            ViewGroup segGroup = (ViewGroup) seg;
            assertEquals("分段控件应有选中块与文字行两层", 2, segGroup.getChildCount());
            View indicator = segGroup.getChildAt(0);
            View track = segGroup.getChildAt(1);
            assertTrue("文字行应位于选中块之上(最上层)", track instanceof android.widget.LinearLayout);
            assertTrue("选中块应被布局(宽>0),实际=" + indicator.getWidth(), indicator.getWidth() > 0);
            assertTrue("选中块应被布局(高>0),实际=" + indicator.getHeight(), indicator.getHeight() > 0);
            assertTrue("轨道背景应为 GradientDrawable", seg.getBackground() instanceof android.graphics.drawable.GradientDrawable);
            android.graphics.drawable.GradientDrawable trackBg =
                    (android.graphics.drawable.GradientDrawable) seg.getBackground();
            assertNotNull("轨道应有填充色(半透明毛玻璃)", trackBg.getColor());
            int trackFill = trackBg.getColor().getDefaultColor();
            assertTrue("轨道填充应半透明(alpha<255),实际=" + Color.alpha(trackFill),
                    Color.alpha(trackFill) < 255);
            screenshot("31_segmented_frosted");

            // ---- 状态栏完全透明:无遮盖条、系统栏透明、场景顶到屏幕顶端 ----
            assertImmersiveBars(main);
            int statusBarInset = main.getWindowInsetTop();
            assertTrue("状态栏 inset 应>0", statusBarInset > 0);
            View stage = main.findViewById(R.id.fragment_container);
            assertEquals("首页场景应顶到屏幕顶端(舞台无顶部避让)", 0, stage.getTop());
            View searchBarContainer = main.findViewById(R.id.search_bar_container);
            assertNotNull(searchBarContainer);
            assertTrue("搜索栏容器应完全位于状态栏下方(top>=inset),实际 top="
                    + searchBarContainer.getTop() + ", inset=" + statusBarInset,
                    searchBarContainer.getTop() >= statusBarInset);

            // ---- 缩略图模式沉浸式:滚动后列表内容应进入状态栏区域(直接透出,无遮盖) ----
            // 须在切换分段之前验证:订阅列表可能为空,且滚动后搜索栏会收起。
            // 用 item 位置断言而非像素对比(内容直接透出时暗色主题像素对比不可靠)
            View rv = main.findViewById(R.id.recycler_view);
            // 慢网等待列表加载,最多 15s
            for (int i = 0; i < 30 && !(rv instanceof ViewGroup && ((ViewGroup) rv).getChildCount() > 0); i++) {
                SystemClock.sleep(500);
            }
            if (rv instanceof ViewGroup && ((ViewGroup) rv).getChildCount() > 0) {
                ViewGroup rvg = (ViewGroup) rv;
                dragOn(rv, rv.getHeight() * 0.7f, rv.getHeight() * 0.15f);
                SystemClock.sleep(900);
                boolean entered = false;
                int[] loc = new int[2];
                for (int i = 0; i < rvg.getChildCount(); i++) {
                    View child = rvg.getChildAt(i);
                    child.getLocationOnScreen(loc);
                    if (loc[1] < statusBarInset && loc[1] + child.getHeight() > 0) {
                        entered = true;
                        break;
                    }
                }
                assertTrue("缩略图模式滚动后列表内容应进入状态栏区域(完全透明,直接透出)", entered);
                screenshot("32_thumb_scrolled_glass");
            } else {
                android.util.Log.w("ModernUiTest", "主页列表无内容,跳过缩略图沉浸式滚动验证");
            }

            // 切换到「订阅」:选中块跟随移动,选中项文字颜色为主题色(在选中块上可读)
            assertTrue("文字行应有3个分段", ((ViewGroup) track).getChildCount() == 3);
            clickOn(((ViewGroup) track).getChildAt(1));
            SystemClock.sleep(600);
            assertEquals("点击后应选中第2段", 1,
                    ((com.hippo.ehviewer.widget.SegmentedControl) seg).getSelectedIndex());
            assertTrue("切换后选中块应移出首段(translationX>0)", indicator.getTranslationX() > 0);
            int selectedColor = AttrResources.getAttrColor(main, androidx.appcompat.R.attr.colorPrimary);
            android.widget.TextView selectedLabel =
                    (android.widget.TextView) ((ViewGroup) track).getChildAt(1);
            assertEquals("选中项文字应为主题色", selectedColor, selectedLabel.getCurrentTextColor());
            screenshot("33_segmented_selected");

            // ---- 下载页:状态栏随顶栏着色不受影响 ----
            View nav = main.findViewById(R.id.bottom_nav);
            View dlTab = nav.findViewById(R.id.nav_downloads);
            clickOn(dlTab);
            waitForScene(main, "DownloadsScene", SCENE_TIMEOUT);
            SystemClock.sleep(1200);
            assertStatusBarScrim(main, "下载页");
        } finally {
            PreferenceManager.getDefaultSharedPreferences(mApp).edit()
                    .putString("list_mode", oldListMode)
                    .apply();
        }
    }
}

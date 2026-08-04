/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

import static com.hippo.ehviewer.util.ClipboardUtil.createAnnouncerFromClipboardUrl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
//补一下，不然编译不通过
import android.os.Build;
import android.os.Environment;
//
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;
import com.hippo.android.resource.AttrResources;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUrlOpener;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.shortcuts.ShortcutsActivity;
import com.hippo.ehviewer.ui.main.UserImageChange;
import com.hippo.ehviewer.ui.scene.AnalyticsScene;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.scene.ToolbarScene;
import com.hippo.ehviewer.ui.scene.more.MoreScene;
import com.hippo.ehviewer.ui.scene.sign.CookieSignInScene;
import com.hippo.ehviewer.ui.scene.download.DownloadLabelsScene;
import com.hippo.ehviewer.ui.scene.download.DownloadsScene;
import com.hippo.ehviewer.ui.scene.gallery.list.FavoritesScene;
import com.hippo.ehviewer.ui.scene.GalleryCommentsScene;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.ui.scene.GalleryInfoScene;
import com.hippo.ehviewer.ui.scene.gallery.list.GalleryListScene;
import com.hippo.ehviewer.ui.scene.GalleryPreviewsScene;
import com.hippo.ehviewer.ui.scene.gallery.list.SubscriptionsScene;
import com.hippo.ehviewer.ui.scene.sign.GetProfileScene;
import com.hippo.ehviewer.ui.scene.topList.EhTopListScene;
import com.hippo.ehviewer.ui.scene.history.HistoryScene;
import com.hippo.ehviewer.ui.scene.ProgressScene;
import com.hippo.ehviewer.ui.scene.gallery.list.QuickSearchScene;
import com.hippo.ehviewer.ui.scene.SecurityScene;
import com.hippo.ehviewer.ui.scene.SelectSiteScene;
import com.hippo.ehviewer.ui.scene.sign.SignInScene;
import com.hippo.ehviewer.ui.scene.SolidScene;
import com.hippo.ehviewer.ui.scene.WarningScene;
import com.hippo.ehviewer.ui.scene.sign.WebViewSignInScene;
import com.hippo.ehviewer.ui.splash.SplashActivity;
import com.hippo.ehviewer.updater.AppUpdater;
import com.hippo.ehviewer.widget.BottomNavBar;
import com.hippo.ehviewer.widget.EhDrawerLayout;
import com.hippo.ehviewer.widget.MainContentLayout;
import com.hippo.io.UniFileInputStreamPipe;
import com.hippo.network.Network;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.scene.StageActivity;
import com.hippo.unifile.UniFile;
import com.hippo.util.BitmapUtils;
import com.hippo.util.PermissionRequester;
import com.hippo.widget.AvatarImageView;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.SimpleHandler;
import com.hippo.lib.yorozuya.ViewUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public final class MainActivity extends StageActivity {

    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0;

    public static final int REQUEST_CODE_SETTINGS = 0;

    private static final String KEY_NAV_CHECKED_ITEM = "nav_checked_item";
//    private static final String KEY_CLIP_TEXT_HASH_CODE = "clip_text_hash_code";

    // 桌面长按图标快捷方式 intent action(显式 intent 直达本 Activity,action 只需进程内唯一)
    private static final String ACTION_SHORTCUT_WHATS_HOT = "ehviewer.action.SHORTCUT_WHATS_HOT";
    private static final String ACTION_SHORTCUT_FAVORITES = "ehviewer.action.SHORTCUT_FAVORITES";
    private static final String ACTION_SHORTCUT_DOWNLOADS = "ehviewer.action.SHORTCUT_DOWNLOADS";

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private EhDrawerLayout mDrawerLayout;
    @Nullable
    private BottomNavBar mBottomNav;
    @Nullable
    private MainContentLayout mMainContentLayout;
    @Nullable
    private FrameLayout mRightDrawer;

    private int mNavCheckedItem = 0;

    /** 右侧抽屉是否打开;打开时状态栏样式随主题底色(抽屉背景覆盖状态栏区域) */
    private boolean mRightDrawerOpen = false;

    /** 工具栏主题色,工具栏场景置顶时状态栏区域随之着色 */
    private int mToolbarColor = Color.TRANSPARENT;

    /** 个人页头像/背景更换流程持有者,结果在 onActivityResult 中路由回去 */
    @Nullable
    private UserImageChange mUserImageChange;
    @Nullable
    private AvatarImageView mUserImageAvatar;

    static {
        registerLaunchMode(SecurityScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(WarningScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(AnalyticsScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(SignInScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(WebViewSignInScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(CookieSignInScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(GetProfileScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(SelectSiteScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(GalleryListScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TOP);
        registerLaunchMode(EhTopListScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TOP);
        registerLaunchMode(QuickSearchScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(SubscriptionsScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(GalleryDetailScene.class, SceneFragment.LAUNCH_MODE_STANDARD);
        registerLaunchMode(GalleryInfoScene.class, SceneFragment.LAUNCH_MODE_STANDARD);
        registerLaunchMode(GalleryCommentsScene.class, SceneFragment.LAUNCH_MODE_STANDARD);
        registerLaunchMode(GalleryPreviewsScene.class, SceneFragment.LAUNCH_MODE_STANDARD);
        registerLaunchMode(DownloadsScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(DownloadLabelsScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(FavoritesScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
        registerLaunchMode(HistoryScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TOP);
        registerLaunchMode(ProgressScene.class, SceneFragment.LAUNCH_MODE_STANDARD);
        registerLaunchMode(MoreScene.class, SceneFragment.LAUNCH_MODE_SINGLE_TASK);
    }

    @Override
    protected int getThemeResId(int theme) {
        switch (theme) {
            case Settings.THEME_LIGHT:
            default:
                return R.style.AppTheme_Main;
            case Settings.THEME_DARK:
                return R.style.AppTheme_Main_Dark;
            case Settings.THEME_BLACK:
                return R.style.AppTheme_Main_Black;
        }
    }

    @Override
    public int getContainerViewId() {
        return R.id.fragment_container;
    }

    @NonNull
    @Override
    protected Announcer getLaunchAnnouncer() {
        if (!TextUtils.isEmpty(Settings.getSecurity())) {
            return new Announcer(SecurityScene.class);
        } else if (Settings.getShowWarning()) {
            return new Announcer(WarningScene.class);
        } else if (Settings.getAskAnalytics()) {
            return new Announcer(AnalyticsScene.class);
        } else if (EhUtils.needSignedIn(this)) {
            return new Announcer(SignInScene.class);
        } else if (Settings.getSelectSite()) {
            return new Announcer(SelectSiteScene.class);
        } else {
            Bundle args = new Bundle();
            args.putString(GalleryListScene.KEY_ACTION, Settings.getLaunchPageGalleryListSceneAction());
            return new Announcer(GalleryListScene.class).setArgs(args);
        }
    }

    // Sometimes scene can't show directly
    private Announcer processAnnouncer(Announcer announcer) {
        if (0 == getSceneCount()) {
            if (!TextUtils.isEmpty(Settings.getSecurity())) {
                Bundle newArgs = new Bundle();
                newArgs.putString(SecurityScene.KEY_TARGET_SCENE, announcer.getClazz().getName());
                newArgs.putBundle(SecurityScene.KEY_TARGET_ARGS, announcer.getArgs());
                return new Announcer(SecurityScene.class).setArgs(newArgs);
            } else if (Settings.getShowWarning()) {
                Bundle newArgs = new Bundle();
                newArgs.putString(WarningScene.KEY_TARGET_SCENE, announcer.getClazz().getName());
                newArgs.putBundle(WarningScene.KEY_TARGET_ARGS, announcer.getArgs());
                return new Announcer(WarningScene.class).setArgs(newArgs);
            } else if (Settings.getAskAnalytics()) {
                Bundle newArgs = new Bundle();
                newArgs.putString(AnalyticsScene.KEY_TARGET_SCENE, announcer.getClazz().getName());
                newArgs.putBundle(AnalyticsScene.KEY_TARGET_ARGS, announcer.getArgs());
                return new Announcer(AnalyticsScene.class).setArgs(newArgs);
            } else if (EhUtils.needSignedIn(this)) {
                Bundle newArgs = new Bundle();
                newArgs.putString(SignInScene.KEY_TARGET_SCENE, announcer.getClazz().getName());
                newArgs.putBundle(SignInScene.KEY_TARGET_ARGS, announcer.getArgs());
                return new Announcer(SignInScene.class).setArgs(newArgs);
            } else if (Settings.getSelectSite()) {
                Bundle newArgs = new Bundle();
                newArgs.putString(SelectSiteScene.KEY_TARGET_SCENE, announcer.getClazz().getName());
                newArgs.putBundle(SelectSiteScene.KEY_TARGET_ARGS, announcer.getArgs());
                return new Announcer(SelectSiteScene.class).setArgs(newArgs);
            }
        }
        return announcer;
    }

    private File saveImageToTempFile(UniFile file) {
        if (null == file) {
            return null;
        }

        Bitmap bitmap = null;
        try {
            bitmap = BitmapUtils.decodeStream(new UniFileInputStreamPipe(file),
                    -1, -1, 500 * 500, false, false, null);
        } catch (OutOfMemoryError e) {
            // Ignore
        }
        if (null == bitmap) {
            return null;
        }

        File temp = AppConfig.createTempFile();
        if (null == temp) {
            return null;
        }

        OutputStream os = null;
        try {
            os = new FileOutputStream(temp);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
            return temp;
        } catch (IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) {
                return false;
            }
            Announcer announcer = EhUrlOpener.parseUrl(uri.toString());
            if (announcer != null) {
                startScene(processAnnouncer(announcer));
                return true;
            }
        } else if (ACTION_SHORTCUT_WHATS_HOT.equals(action)) {
            // 桌面快捷方式:热门
            Bundle args = new Bundle();
            args.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_WHATS_HOT);
            startScene(processAnnouncer(new Announcer(GalleryListScene.class).setArgs(args)));
            return true;
        } else if (ACTION_SHORTCUT_FAVORITES.equals(action)) {
            // 桌面快捷方式:收藏
            startScene(processAnnouncer(new Announcer(FavoritesScene.class)));
            return true;
        } else if (ACTION_SHORTCUT_DOWNLOADS.equals(action)) {
            // 桌面快捷方式:下载
            startScene(processAnnouncer(new Announcer(DownloadsScene.class)));
            return true;
        } else if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();
            if ("text/plain".equals(type)) {
                ListUrlBuilder builder = new ListUrlBuilder();
                builder.setKeyword(intent.getStringExtra(Intent.EXTRA_TEXT));
                startScene(processAnnouncer(GalleryListScene.getStartAnnouncer(builder)));
                return true;
            } else {
                assert type != null;
                if (type.startsWith("image/")) {
                    Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (null != uri) {
                        UniFile file = UniFile.fromUri(this, uri);
                        File temp = saveImageToTempFile(file);
                        if (null != temp) {
                            ListUrlBuilder builder = new ListUrlBuilder();
                            builder.setMode(ListUrlBuilder.MODE_IMAGE_SEARCH);
                            builder.setImagePath(temp.getPath());
                            builder.setUseSimilarityScan(true);
                            builder.setShowExpunged(true);
                            startScene(processAnnouncer(GalleryListScene.getStartAnnouncer(builder)));
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    protected void onUnrecognizedIntent(@Nullable Intent intent) {
        Class<?> clazz = getTopSceneClass();
        if (clazz != null && SolidScene.class.isAssignableFrom(clazz)) {
            // TODO the intent lost
            return;
        }

        if (!handleIntent(intent)) {
            boolean handleUrl = false;
            if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                handleUrl = true;
                Toast.makeText(this, R.string.error_cannot_parse_the_url, Toast.LENGTH_SHORT).show();
            }

            if (0 == getSceneCount()) {
                if (handleUrl) {
                    finish();
                } else {
                    Bundle args = new Bundle();
                    args.putString(GalleryListScene.KEY_ACTION, Settings.getLaunchPageGalleryListSceneAction());
                    startScene(processAnnouncer(new Announcer(GalleryListScene.class).setArgs(args)));
                }
            }
        }
    }

    @Nullable
    @Override
    protected Announcer onStartSceneFromIntent(@NonNull Class<?> clazz, @Nullable Bundle args) {
        return processAnnouncer(new Announcer(clazz).setArgs(args));
    }

    @Override
    protected void onCreate2(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (intent != null) {
            boolean res = intent.getBooleanExtra(SplashActivity.KEY_RESTART,false);
            if (res){
                savedInstanceState = null;
            }
        }
        setContentView(R.layout.activity_main);

        mDrawerLayout = (EhDrawerLayout) ViewUtils.$$(this, R.id.draw_view);
        mBottomNav = (BottomNavBar) ViewUtils.$$(this, R.id.bottom_nav);
        mMainContentLayout = (MainContentLayout) ViewUtils.$$(this, R.id.main_content);
        mRightDrawer = (FrameLayout) ViewUtils.$$(this, R.id.right_drawer);

        // 左侧导航抽屉已移除,锁定左边缘手势;右侧筛选抽屉保留
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);

        // 右侧抽屉打开时其背景覆盖状态栏区域,状态栏样式随主题底色(见 updateStatusBarStyle)
        mDrawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float percent) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                mRightDrawerOpen = true;
                updateStatusBarStyle();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                mRightDrawerOpen = false;
                updateStatusBarStyle();
            }

            @Override
            public void onDrawerStateChanged(View drawerView, int newState) {
            }
        });

        mBottomNav.setOnTabSelectedListener(this::onBottomNavTabSelected);

        // 沉浸式 window 设置已由 EhActivity.applyEdgeToEdge() 统一完成;
        // 状态栏图标明暗与区域着色由 updateStatusBarStyle() 按栈顶场景运行时控制
        // drawerlayout 库默认在系统导航栏区域画黑色矩形,必须显式设透明,否则导航条区域发黑
        mDrawerLayout.setNavigationBarColor(Color.TRANSPARENT);
        mToolbarColor = AttrResources.getAttrColor(this, R.attr.toolbarColor);
        updateStatusBarStyle();
        updateAppShortcuts();

        if (savedInstanceState == null) {
            onInit();
            checkDownloadLocation();
            if (Settings.getCellularNetworkWarning()) {
                checkCellularNetwork();
            }
        } else {
            onRestore(savedInstanceState);
        }
        EhTagDatabase.update(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!Settings.getCloseAutoUpdate()){
            AppUpdater.update(this,false);
        }
    }

    private void checkDownloadLocation() {
        UniFile uniFile = Settings.getDownloadLocation();
        // null == uniFile for first start
        if (null == uniFile || uniFile.ensureDir()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.waring)
                .setMessage(R.string.invalid_download_location)
                .setPositiveButton(R.string.get_it, null)
                .show();
    }

    private void checkCellularNetwork() {
        if (Network.getActiveNetworkType(this) == ConnectivityManager.TYPE_MOBILE) {
            showTip(R.string.cellular_network_warning, BaseScene.LENGTH_SHORT);
        }
    }

    private void onInit() {
        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesAccessPermissionSafely();
            }
        } else {
            PermissionRequester.request(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    getString(R.string.write_rationale), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        EhCookieStore store = EhApplication.getEhCookieStore(getApplicationContext());
        List<Cookie> eCookies = store.getCookies(HttpUrl.get(EhUrl.HOST_E));
        List<Cookie> exCookies = store.getCookies(HttpUrl.get(EhUrl.HOST_EX));
        List<Cookie> cookies = new LinkedList<>(eCookies);
        cookies.addAll(exCookies);

        String ipbMemberId = null;
        String ipbPassHash = null;
        String igneous = null;

        for (int i = 0, n = cookies.size(); i < n; i++) {
            Cookie cookie = cookies.get(i);
            switch (cookie.name()) {
                case EhCookieStore.KEY_IPD_MEMBER_ID:
                    ipbMemberId = cookie.value();
                    break;
                case EhCookieStore.KEY_IPD_PASS_HASH:
                    ipbPassHash = cookie.value();
                    break;
                case EhCookieStore.KEY_IGNEOUS:
                    igneous = cookie.value();
                    break;
            }
        }
//        if (ipbMemberId != null || ipbPassHash != null || igneous != null) {
//            Settings.setLoginState(true);
//        } else {
//            Settings.setLoginState(false);
//        }
        Settings.setLoginState(ipbMemberId != null || ipbPassHash != null || igneous != null);
    }

    /**
     * Some ROMs reject the app-specific all-files-access page with SecurityException.
     * Try app-specific page first, then fallback to global management page.
     */
    private void requestAllFilesAccessPermissionSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            Intent appSpecificIntent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            appSpecificIntent.setData(Uri.parse("package:" + getPackageName()));
            if (startActivityQuietly(appSpecificIntent)) {
                return;
            }

            Intent globalIntent = new Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityQuietly(globalIntent);
        }
    }

    private boolean startActivityQuietly(@NonNull Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void onRestore(Bundle savedInstanceState) {
        mNavCheckedItem = savedInstanceState.getInt(KEY_NAV_CHECKED_ITEM);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, @NonNull PersistableBundle outPersistentState) {
//        super.onSaveInstanceState(outState, outPersistentState);
        outState.putInt(KEY_NAV_CHECKED_ITEM, mNavCheckedItem);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDrawerLayout = null;
        mBottomNav = null;
        mMainContentLayout = null;
        mRightDrawer = null;
        mUserImageChange = null;
        mUserImageAvatar = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        setNavCheckedItem(mNavCheckedItem);

        checkClipboardUrl();
    }

    @Override
    protected void onTransactScene() {
        super.onTransactScene();

        updateStatusBarStyle();
        checkClipboardUrl();
    }

    /**
     * 按栈顶场景适配状态栏区域:工具栏场景(下载/历史等)在 app bar 未完全收起时
     * 随 toolbar 主题色着色;顶部有固定彩色区域的场景(如画廊详情页)按其
     * getStatusBarScrimColor 着色;着色时状态栏图标用白色。其余场景状态栏
     * 完全透明(内容可直接滚动到状态栏下方透出),图标明暗随主题。
     * app bar 收起/展开时由场景经 SearchBarMover 回调主动触发刷新;
     * 右侧抽屉打开时抽屉背景覆盖状态栏区域,状态栏强制按主题底色处理
     */
    public void updateStatusBarStyle() {
        SceneFragment topScene = getTopScene();
        Integer scrimColor = null;
        if (topScene instanceof ToolbarScene
                && !((ToolbarScene) topScene).isAppBarFullyHidden()) {
            scrimColor = mToolbarColor;
        } else if (topScene instanceof BaseScene) {
            scrimColor = ((BaseScene) topScene).getStatusBarScrimColor();
        }
        if (mRightDrawerOpen) {
            scrimColor = null;
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.setStatusBarColor(scrimColor != null ? scrimColor : Color.TRANSPARENT);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(
                scrimColor == null && Settings.getTheme() == Settings.THEME_LIGHT);
    }

    /**
     * 桌面长按图标快捷方式:热门/收藏/下载。
     * 动态注册并随每次启动刷新——不受静态 shortcuts meta-data 挂载位置
     * (LAUNCHER 在 SplashActivity)与 applicationId 变体(release/debug 不同)影响;
     * 点击后经 handleIntent 路由到目标场景
     */
    private void updateAppShortcuts() {
        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        shortcuts.add(new ShortcutInfoCompat.Builder(this, "whats_hot")
                .setShortLabel(getString(R.string.whats_hot))
                .setLongLabel(getString(R.string.whats_hot))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_whats_hot))
                .setIntent(new Intent(this, MainActivity.class).setAction(ACTION_SHORTCUT_WHATS_HOT))
                .build());
        shortcuts.add(new ShortcutInfoCompat.Builder(this, "favorites")
                .setShortLabel(getString(R.string.favourite))
                .setLongLabel(getString(R.string.favourite))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_favorites))
                .setIntent(new Intent(this, MainActivity.class).setAction(ACTION_SHORTCUT_FAVORITES))
                .build());
        shortcuts.add(new ShortcutInfoCompat.Builder(this, "downloads")
                .setShortLabel(getString(R.string.downloads))
                .setLongLabel(getString(R.string.downloads))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_downloads))
                .setIntent(new Intent(this, MainActivity.class).setAction(ACTION_SHORTCUT_DOWNLOADS))
                .build());
        // 下载快捷操作:全部开始/全部停止(迁移自旧静态 shortcuts,action 契约见 ShortcutsActivity)
        shortcuts.add(new ShortcutInfoCompat.Builder(this, "start_all")
                .setShortLabel(getString(R.string.download_start_all))
                .setLongLabel(getString(R.string.download_start_all))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_start))
                .setIntent(new Intent(this, ShortcutsActivity.class)
                        .setAction(DownloadService.ACTION_START_ALL))
                .build());
        shortcuts.add(new ShortcutInfoCompat.Builder(this, "stop_all")
                .setShortLabel(getString(R.string.download_stop_all))
                .setLongLabel(getString(R.string.download_stop_all))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_stop))
                .setIntent(new Intent(this, ShortcutsActivity.class)
                        .setAction(DownloadService.ACTION_STOP_ALL))
                .build());
        ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts);
    }

    private void checkClipboardUrl() {
        SimpleHandler.getInstance().postDelayed(() -> {
            if (!isSolid()) {
                checkClipboardUrlInternal();
            }
        }, 300);
    }

    private boolean isSolid() {
        Class<?> topClass = getTopSceneClass();
        return topClass == null || SolidScene.class.isAssignableFrom(topClass);
    }

    private String getTextFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            if (clipboard != null) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                    return clip.getItemAt(0).getText().toString();
                }
            }
        } catch (RuntimeException ignore) {
        }
        return null;
    }



    private void checkClipboardUrlInternal() {
        String text = getTextFromClipboard();
        int hashCode = text != null ? text.hashCode() : 0;

        if (text != null && hashCode != 0 && Settings.getClipboardTextHashCode() != hashCode) {
            Announcer announcer = createAnnouncerFromClipboardUrl(text);
            if (announcer != null && mDrawerLayout != null) {
                Snackbar snackbar = Snackbar.make(mDrawerLayout, R.string.clipboard_gallery_url_snack_message, Snackbar.LENGTH_INDEFINITE);
                if (mBottomNav != null && mBottomNav.getVisibility() == View.VISIBLE) {
                    snackbar.setAnchorView(mBottomNav);
                }
                snackbar.setAction(R.string.clipboard_gallery_url_snack_action, v -> startScene(announcer));
                snackbar.show();
            }
        }

        Settings.putClipboardTextHashCode(hashCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length == 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.you_rejected_me, Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onSceneViewCreated(SceneFragment scene, Bundle savedInstanceState) {
        super.onSceneViewCreated(scene, savedInstanceState);

        if (scene instanceof BaseScene && mRightDrawer != null && mDrawerLayout != null) {
            BaseScene baseScene = (BaseScene) scene;
            mRightDrawer.removeAllViews();
            View drawerView = baseScene.createDrawerView(
                    baseScene.getLayoutInflater2(), mRightDrawer, savedInstanceState);
            if (drawerView != null) {
                mRightDrawer.addView(drawerView);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
            } else {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
            }
        }
    }

    @Override
    public void onSceneViewDestroyed(SceneFragment scene) {
        super.onSceneViewDestroyed(scene);

        if (scene instanceof BaseScene) {
            BaseScene baseScene = (BaseScene) scene;
            baseScene.destroyDrawerView();
        }
    }

    public void addAboveSnackView(View view) {
        if (mDrawerLayout != null) {
            mDrawerLayout.addAboveSnackView(view);
        }
    }

    public void removeAboveSnackView(View view) {
        if (mDrawerLayout != null) {
            mDrawerLayout.removeAboveSnackView(view);
        }
    }

    public void setDrawerLockMode(int lockMode, int edgeGravity) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(lockMode, edgeGravity);
        }
    }

    public void openDrawer(int drawerGravity) {
        if (mDrawerLayout != null) {
            mDrawerLayout.openDrawer(drawerGravity);
        }
    }

    public void closeDrawer(int drawerGravity) {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(drawerGravity);
        }
    }

    public void setDrawerGestureBlocker(DrawerLayout.GestureBlocker gestureBlocker) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setGestureBlocker(gestureBlocker);
        }
    }

    public boolean isDrawersVisible() {
        if (mDrawerLayout != null) {
            return mDrawerLayout.isDrawersVisible();
        } else {
            return false;
        }
    }

    public void setNavCheckedItem(@IdRes int resId) {
        mNavCheckedItem = resId;
        if (mBottomNav != null) {
            mBottomNav.setSelectedId(mapToBottomTab(resId));
        }
    }

    /**
     * 按当前场景显隐底部导航栏(登录引导等流程场景隐藏)
     */
    public void setBottomNavVisible(boolean visible) {
        if (mMainContentLayout != null) {
            mMainContentLayout.setBottomNavVisible(visible);
        }
    }

    /**
     * 是否由舞台统一避让状态栏;悬浮 app bar 场景(下载/历史)传 false,
     * 场景顶到屏幕顶端,app bar 自行延伸进状态栏区域
     */
    public void setStageFitsStatusBar(boolean fits) {
        if (mMainContentLayout != null) {
            mMainContentLayout.setStageFitsStatusBar(fits);
        }
    }

    /**
     * 内容列表滚动联动:跟手位移底部导航栏(下滚隐藏/上滚显示)
     */
    public void onContentListScrolled(int dy) {
        if (mMainContentLayout != null) {
            mMainContentLayout.offsetBottomNav(dy);
        }
    }

    /**
     * 列表滚动停止时,底部导航栏吸附到全显或全隐
     */
    public void settleBottomNav() {
        if (mMainContentLayout != null) {
            mMainContentLayout.settleBottomNav();
        }
    }

    /**
     * 状态栏高度(px),沉浸式下场景自行避让顶部
     */
    public int getWindowInsetTop() {
        return mMainContentLayout != null ? mMainContentLayout.getWindowInsetTop() : 0;
    }

    /**
     * 系统导航栏 inset(px)
     */
    public int getWindowInsetBottom() {
        return mMainContentLayout != null ? mMainContentLayout.getWindowInsetBottom() : 0;
    }

    /**
     * 场景内容底部需要避让的高度:系统导航栏 inset + 底部导航栏(可见时)占位
     */
    public int getBottomOccupiedHeight() {
        return mMainContentLayout != null ? mMainContentLayout.getBottomOccupiedHeight() : 0;
    }

    public void addOnInsetsChangedListener(MainContentLayout.OnInsetsChangedListener listener) {
        if (mMainContentLayout != null) {
            mMainContentLayout.addOnInsetsChangedListener(listener);
        }
    }

    public void removeOnInsetsChangedListener(MainContentLayout.OnInsetsChangedListener listener) {
        if (mMainContentLayout != null) {
            mMainContentLayout.removeOnInsetsChangedListener(listener);
        }
    }

    /**
     * 旧抽屉菜单 id 映射到底部 tab;订阅/热门/榜单归并到主页 tab,0 或其他 id 不映射
     */
    @IdRes
    private int mapToBottomTab(@IdRes int resId) {
        if (resId == R.id.nav_homepage || resId == R.id.nav_subscription
                || resId == R.id.nav_whats_hot || resId == R.id.nav_top_lists) {
            return R.id.nav_homepage;
        }
        if (resId == R.id.nav_favourite || resId == R.id.nav_downloads
                || resId == R.id.nav_history || resId == R.id.nav_more) {
            return resId;
        }
        return 0;
    }

    public void showTip(@StringRes int id, int length) {
        showTip(getString(id), length);
    }

    /**
     * If activity is running, show snack bar, otherwise show toast
     */
    public void showTip(CharSequence message, int length) {
        if (null != mDrawerLayout) {
            Snackbar snackbar = Snackbar.make(mDrawerLayout, message,
                    length == BaseScene.LENGTH_LONG ? 5000 : 3000);
            if (null != mBottomNav && mBottomNav.getVisibility() == View.VISIBLE) {
                snackbar.setAnchorView(mBottomNav);
            }
            snackbar.show();
        } else {
            Toast.makeText(this, message,
                    length == BaseScene.LENGTH_LONG ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    @SuppressLint("NonConstantResourceId")
    private void onBottomNavTabSelected(@IdRes int tabId) {
        // 不重复切换当前 tab
        if (tabId == mNavCheckedItem) {
            return;
        }

        switch (tabId) {
            case R.id.nav_homepage: {
                Bundle args = new Bundle();
                args.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_HOMEPAGE);
                startSceneFirstly(new Announcer(GalleryListScene.class).setArgs(args));
                break;
            }
            case R.id.nav_favourite:
                startSceneFirstly(new Announcer(FavoritesScene.class));
                break;
            case R.id.nav_history:
                startSceneFirstly(new Announcer(HistoryScene.class));
                break;
            case R.id.nav_downloads:
                startSceneFirstly(new Announcer(DownloadsScene.class));
                break;
            case R.id.nav_more:
                startSceneFirstly(new Announcer(MoreScene.class));
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            if (RESULT_OK == resultCode) {
                refreshTopScene();
            }
            return;
        }
        // 个人页头像/背景更换:拍照或相册选择结果路由回 UserImageChange
        if (resultCode == RESULT_OK
                && (requestCode == UserImageChange.TAKE_CAMERA || requestCode == UserImageChange.PICK_PHOTO)
                && mUserImageChange != null) {
            mUserImageChange.saveImageForResult(requestCode, resultCode, data, mUserImageAvatar);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 个人页注册头像/背景更换流程;avatarView 用于相册/拍照结果直接刷新头像
     */
    public void registerUserImageChange(@NonNull UserImageChange userImageChange,
                                        @Nullable AvatarImageView avatarView) {
        mUserImageChange = userImageChange;
        mUserImageAvatar = avatarView;
    }

    public void unregisterUserImageChange(@NonNull UserImageChange userImageChange) {
        if (mUserImageChange == userImageChange) {
            mUserImageChange = null;
            mUserImageAvatar = null;
        }
    }
}

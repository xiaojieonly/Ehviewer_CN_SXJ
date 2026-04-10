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
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.callBack.ImageChangeCallBack;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUrlOpener;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.ui.adaptive.AdaptiveWindowState;
import com.hippo.ehviewer.ui.inset.WindowInsetHelper;
import com.hippo.ehviewer.ui.main.UserImageChange;
import com.hippo.ehviewer.ui.scene.AnalyticsScene;
import com.hippo.ehviewer.ui.scene.BaseScene;
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
import com.hippo.ehviewer.widget.EhDrawerLayout;
import com.hippo.ehviewer.widget.EhDrawerView;
import com.hippo.ehviewer.widget.EhNavigationView;
import com.hippo.ehviewer.widget.EhStageLayout;
import com.hippo.ehviewer.widget.LimitsCountView;
import com.hippo.io.UniFileInputStreamPipe;
import com.hippo.network.Network;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.scene.StageActivity;
import com.hippo.scene.TransitionHelper;
import com.hippo.unifile.UniFile;
import com.hippo.util.BitmapUtils;
import com.hippo.util.GifHandler;
import com.hippo.util.PermissionRequester;
import com.hippo.widget.AvatarImageView;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.ResourcesUtils;
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

public final class MainActivity extends StageActivity
        implements NavigationView.OnNavigationItemSelectedListener, ImageChangeCallBack, DrawerLayout.DrawerListener {

    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0;

    private static final int REQUEST_CODE_SETTINGS = 0;

    private static final String KEY_NAV_CHECKED_ITEM = "nav_checked_item";
    private static final String KEY_SECONDARY_SCENE_TAG_LIST = "secondary_scene_tag_list";
//    private static final String KEY_CLIP_TEXT_HASH_CODE = "clip_text_hash_code";

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private LinearLayout mAdaptiveContentHost;
    @Nullable
    private View mFoldDivider;
    @Nullable
    private FrameLayout mSecondaryContainer;
    @Nullable
    private EhDrawerLayout mDrawerLayout;
    @Nullable
    private NavigationView mNavView;
    @Nullable
    private FrameLayout mRightDrawer;
    @Nullable
    private AvatarImageView mAvatar;
    @Nullable
    private ImageView mHeaderBackground;
    @Nullable
    private TextView mDisplayName;
    @Nullable
    private LimitsCountView limitsCountView;
    @Nullable
    UserImageChange userImageChange;

    private int mNavCheckedItem = 0;
    @NonNull
    private final ArrayList<String> mSecondarySceneTagList = new ArrayList<>();

    GifHandler gifHandler;

    Bitmap backgroundBit;

    Handler handlerB = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            int mNextFrame = gifHandler.updateFrame(backgroundBit);
            handlerB.sendEmptyMessageDelayed(1, mNextFrame);
            mHeaderBackground.setImageBitmap(backgroundBit);
        }
    };

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

        mAdaptiveContentHost = (LinearLayout) ViewUtils.$$(this, R.id.adaptive_content_host);
        mFoldDivider = ViewUtils.$$(this, R.id.fold_divider);
        mSecondaryContainer = (FrameLayout) ViewUtils.$$(this, R.id.secondary_fragment_container);
        mDrawerLayout = (EhDrawerLayout) ViewUtils.$$(this, R.id.draw_view);
        mDrawerLayout.setDrawerListener(this);
        mNavView = (NavigationView) ViewUtils.$$(this, R.id.nav_view);
        mRightDrawer = (FrameLayout) ViewUtils.$$(this, R.id.right_drawer);
        applyHostWindowInsets();
        View headerLayout = mNavView.getHeaderView(0);
        mAvatar = (AvatarImageView) ViewUtils.$$(headerLayout, R.id.avatar);
        mAvatar.setOnClickListener(l -> onAvatarChange());
        mHeaderBackground = (ImageView) ViewUtils.$$(headerLayout, R.id.header_background);
        mHeaderBackground.setOnClickListener(l -> onBackgroundChange());
        initUserImage();
        updateProfile();
        mDisplayName = (TextView) ViewUtils.$$(headerLayout, R.id.display_name);
        TextView mChangeTheme = (TextView) ViewUtils.$$(this, R.id.change_theme);

        limitsCountView = (LimitsCountView) ViewUtils.$$(this, R.id.limits_count_view);

        mDrawerLayout.setStatusBarColor(0);

        if (mNavView != null) {
//            if (Settings.isLogin()){
//                MenuItem newsItem = mNavView.getMenu().findItem(R.id.nav_eh_news);
//                newsItem.setVisible(true);
//            }
            mNavView.setNavigationItemSelectedListener(this);
        }
        // Keep the drawer footer on theme attrs so dark/black presentation tracks the
        // current drawer/window surface instead of relying on nav-bar recolor branches.
        mChangeTheme.setText(getThemeText());
        mChangeTheme.setOnClickListener(v -> {
            Settings.putTheme(getNextTheme());
            ((EhApplication) getApplication()).recreate();
        });

        if (savedInstanceState == null) {
            onInit();
            checkDownloadLocation();
            if (Settings.getCellularNetworkWarning()) {
                checkCellularNetwork();
            }
        } else {
            onRestore(savedInstanceState);
        }
        syncSecondaryPaneVisibility();
        EhTagDatabase.update(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!Settings.getCloseAutoUpdate()){
            AppUpdater.update(this,false);
        }
    }

    private void initUserImage() {
        File headerBackgroundFile = Settings.getUserImageFile(Settings.USER_BACKGROUND_IMAGE);
        initBackgroundImageData(headerBackgroundFile);
    }

    private void applyHostWindowInsets() {
        if (mDrawerLayout == null) {
            return;
        }
        final EhStageLayout stageLayout = findViewById(R.id.fragment_container);
        final EhNavigationView navigationView = findViewById(R.id.navigation_host);
        final EhDrawerView rightDrawerView = findViewById(R.id.right_drawer);
        final View secondaryContainer = findViewById(R.id.secondary_fragment_container);
        // Neither the primary stage nor the secondary container apply system bar padding —
        // each scene handles its own insets (ToolbarScene, GalleryDetailScene, etc.).
        // Applying insets on the container AND the scene would double the offset.
        // Navigation drawer no longer applies top padding — the nav header image
        // extends behind the transparent status bar instead of showing a white gap.
        // Right drawer padding is handled by applyDrawerWindowPadding() in the
        // DrawerLayout's OnApplyWindowInsetsListener below.  Do NOT also call
        // applyDrawerInsets() here — it sets a competing listener that overwrites
        // the padding on every subsequent inset dispatch.
        // Apply bottom nav bar inset to drawer inner content
        final View innerNavView = findViewById(R.id.nav_view);
        if (innerNavView != null) {
            WindowInsetHelper.applyBottomSystemBarToPadding(innerNavView);
        }
        final View changeThemeView = findViewById(R.id.change_theme);
        if (changeThemeView != null) {
            WindowInsetHelper.applyBottomSystemBarToPadding(changeThemeView);
        }
        ViewCompat.setOnApplyWindowInsetsListener(mDrawerLayout, (view, insets) -> {
            if (stageLayout != null) {
                stageLayout.applyDrawerWindowPadding(insets);
            }
            if (navigationView != null) {
                navigationView.applyDrawerWindowPadding(insets);
            }
            if (rightDrawerView != null) {
                rightDrawerView.applyDrawerWindowPadding(insets);
            }
            return insets;
        });
        WindowInsetHelper.dispatch(mDrawerLayout);
    }

    private void initBackgroundImageData(File file) {
        if (file != null) {
            String name = file.getName();
            String[] ns = name.split("\\.");
            if (ns[1].equals("gif") || ns[1].equals("GIF")) {
                gifHandler = new GifHandler(file.getAbsolutePath());
                int width = gifHandler.getWidth();
                int height = gifHandler.getHeight();
                backgroundBit = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                int nextFrame = gifHandler.updateFrame(backgroundBit);
                handlerB.sendEmptyMessageDelayed(1, nextFrame);
            } else {
                backgroundBit = BitmapFactory.decodeFile(file.getPath());
                assert mHeaderBackground != null;
                mHeaderBackground.setImageBitmap(backgroundBit);
            }
        }
    }

    @Override
    public void backgroundSourceChange(File file) {
        initBackgroundImageData(file);
    }

    private String getThemeText() {
        int resId;
        switch (Settings.getTheme()) {
            default:
            case Settings.THEME_LIGHT:
                resId = R.string.theme_light;
                break;
            case Settings.THEME_DARK:
                resId = R.string.theme_dark;
                break;
            case Settings.THEME_BLACK:
                resId = R.string.theme_black;
                break;
        }
        return getString(resId);
    }

    private int getNextTheme() {
        switch (Settings.getTheme()) {
            default:
            case Settings.THEME_LIGHT:
                return Settings.THEME_DARK;
            case Settings.THEME_DARK:
                return Settings.THEME_BLACK;
            case Settings.THEME_BLACK:
                return Settings.THEME_LIGHT;
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
        ArrayList<String> secondarySceneTags = savedInstanceState.getStringArrayList(KEY_SECONDARY_SCENE_TAG_LIST);
        if (secondarySceneTags != null) {
            mSecondarySceneTagList.clear();
            mSecondarySceneTagList.addAll(secondarySceneTags);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_NAV_CHECKED_ITEM, mNavCheckedItem);
        outState.putStringArrayList(KEY_SECONDARY_SCENE_TAG_LIST, new ArrayList<>(mSecondarySceneTagList));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mAdaptiveContentHost = null;
        mFoldDivider = null;
        mSecondaryContainer = null;
        mDrawerLayout = null;
        mNavView = null;
        mRightDrawer = null;
        mAvatar = null;
        mDisplayName = null;
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

        checkClipboardUrl();
    }

    @Override
    protected void onAdaptiveWindowStateChanged(@NonNull AdaptiveWindowState state) {
        syncAdaptiveLayout(state);
    }

    private void syncAdaptiveLayout(@NonNull AdaptiveWindowState state) {
        if (mAdaptiveContentHost == null || mSecondaryContainer == null || mFoldDivider == null) {
            return;
        }
        if (!state.supportsDualPane() && !mSecondarySceneTagList.isEmpty()) {
            collapseSecondaryPaneToPrimary();
        }
        syncSecondaryPaneVisibility(state);
    }

    private void syncSecondaryPaneVisibility() {
        syncSecondaryPaneVisibility(getAdaptiveWindowState());
    }

    private void syncSecondaryPaneVisibility(@NonNull AdaptiveWindowState state) {
        final EhStageLayout primaryContainer = findViewById(R.id.fragment_container);
        if (primaryContainer == null || mSecondaryContainer == null || mFoldDivider == null) {
            return;
        }

        final boolean showSecondary = state.supportsDualPane() && !mSecondarySceneTagList.isEmpty();
        final LinearLayout.LayoutParams primaryParams =
                (LinearLayout.LayoutParams) primaryContainer.getLayoutParams();
        final LinearLayout.LayoutParams secondaryParams =
                (LinearLayout.LayoutParams) mSecondaryContainer.getLayoutParams();
        final LinearLayout.LayoutParams dividerParams =
                (LinearLayout.LayoutParams) mFoldDivider.getLayoutParams();

        if (showSecondary) {
            primaryParams.width = 0;
            primaryParams.weight = 1f;
            secondaryParams.width = 0;
            secondaryParams.weight = 1f;
            dividerParams.width = state.useHingeDivider()
                    ? Math.max(state.getDividerWidthPx(), 1)
                    : getResources().getDimensionPixelOffset(R.dimen.foldable_dual_pane_spacing);
            dividerParams.weight = 0f;
            mSecondaryContainer.setVisibility(View.VISIBLE);
            mFoldDivider.setVisibility(View.VISIBLE);
        } else {
            primaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            primaryParams.weight = 0f;
            secondaryParams.width = 0;
            secondaryParams.weight = 1f;
            dividerParams.width = 0;
            dividerParams.weight = 0f;
            mSecondaryContainer.setVisibility(View.GONE);
            mFoldDivider.setVisibility(View.GONE);
        }

        primaryContainer.setLayoutParams(primaryParams);
        mSecondaryContainer.setLayoutParams(secondaryParams);
        mFoldDivider.setLayoutParams(dividerParams);
    }

    private boolean isSecondaryCapableScene(@NonNull Class<?> clazz) {
        return GalleryDetailScene.class.isAssignableFrom(clazz)
                || GalleryInfoScene.class.isAssignableFrom(clazz)
                || GalleryCommentsScene.class.isAssignableFrom(clazz)
                || GalleryPreviewsScene.class.isAssignableFrom(clazz);
    }

    private boolean isSecondaryScene(@Nullable SceneFragment scene) {
        return scene != null && isSecondarySceneTag(scene.getTag());
    }

    private boolean isSecondarySceneTag(@Nullable String tag) {
        return tag != null && mSecondarySceneTagList.contains(tag);
    }

    private boolean shouldOpenInSecondary(@NonNull SceneFragment requestFrom, @NonNull Announcer announcer) {
        if (!getAdaptiveWindowState().supportsDualPane()) {
            return false;
        }
        if (!isSecondaryCapableScene(announcer.getClazz())) {
            return false;
        }
        if (isSecondaryScene(requestFrom)) {
            return true;
        }
        return requestFrom instanceof GalleryListScene
                || requestFrom instanceof FavoritesScene
                || requestFrom instanceof DownloadsScene
                || requestFrom instanceof HistoryScene
                || requestFrom instanceof QuickSearchScene
                || requestFrom instanceof SubscriptionsScene
                || requestFrom instanceof EhTopListScene
                || requestFrom instanceof ProgressScene;
    }

    @Override
    public void startScene(@NonNull SceneFragment requestFrom, @NonNull Announcer announcer) {
        if (shouldOpenInSecondary(requestFrom, announcer)) {
            startSecondaryScene(announcer);
            return;
        }
        if (isSecondaryScene(requestFrom) && !isSecondaryCapableScene(announcer.getClazz())) {
            clearSecondaryScenes();
        }
        super.startScene(requestFrom, announcer);
    }

    private void startSecondaryScene(@NonNull Announcer announcer) {
        if (mSecondaryContainer == null) {
            super.startScene(announcer);
            return;
        }

        final Class<?> clazz = announcer.getClazz();
        final Bundle args = announcer.getArgs();
        final TransitionHelper tranHelper = announcer.getTranHelper();
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final SceneFragment currentScene = getTopSecondaryScene();
        final int launchMode = getSceneLaunchMode(clazz);

        if (currentScene != null && clazz.isInstance(currentScene)
                && launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TOP) {
            if (args != null) {
                currentScene.onNewArguments(args);
            }
            return;
        }

        final SceneFragment newScene = newSecondarySceneInstance(clazz);
        newScene.setArguments(args);
        final String newTag = "secondary_" + System.currentTimeMillis() + "_" + mSecondarySceneTagList.size();
        mSecondarySceneTagList.add(newTag);

        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (currentScene != null) {
            if (tranHelper == null || !tranHelper.onTransition(this, transaction, currentScene, newScene)) {
                clearTransitions(currentScene);
                clearTransitions(newScene);
                transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit);
            }
            if (!currentScene.isDetached()) {
                transaction.detach(currentScene);
            }
        }
        transaction.add(R.id.secondary_fragment_container, newScene, newTag);
        transaction.commitAllowingStateLoss();

        if (announcer.getRequestFrom() != null) {
            newScene.registerRequestFrom(announcer.getRequestFrom(), announcer.getRequestCode());
        }
        syncSecondaryPaneVisibility();
    }

    private void finishSecondaryScene(@NonNull SceneFragment scene, @Nullable TransitionHelper transitionHelper) {
        final String tag = scene.getTag();
        if (tag == null) {
            return;
        }
        final int index = mSecondarySceneTagList.indexOf(tag);
        if (index < 0) {
            return;
        }

        final FragmentManager fragmentManager = getSupportFragmentManager();
        final Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment == null) {
            mSecondarySceneTagList.remove(index);
            syncSecondaryPaneVisibility();
            return;
        }

        Fragment next = null;
        if (index == mSecondarySceneTagList.size() - 1 && index > 0) {
            next = fragmentManager.findFragmentByTag(mSecondarySceneTagList.get(index - 1));
        }

        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (next != null) {
            if (transitionHelper == null || !transitionHelper.onTransition(this, transaction, fragment, next)) {
                clearTransitions(fragment);
                clearTransitions(next);
                transaction.setCustomAnimations(R.anim.scene_close_enter, R.anim.scene_close_exit);
            }
            transaction.attach(next);
        }
        transaction.remove(fragment);
        transaction.commitAllowingStateLoss();

        mSecondarySceneTagList.remove(index);
        scene.dispatchResultToRequesters(this);
        syncSecondaryPaneVisibility();
    }

    private void clearSecondaryScenes() {
        if (mSecondarySceneTagList.isEmpty()) {
            syncSecondaryPaneVisibility();
            return;
        }
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        for (String secondaryTag : mSecondarySceneTagList) {
            final Fragment fragment = fragmentManager.findFragmentByTag(secondaryTag);
            if (fragment != null) {
                transaction.remove(fragment);
            }
        }
        transaction.commitAllowingStateLoss();
        mSecondarySceneTagList.clear();
        syncSecondaryPaneVisibility();
    }

    private void collapseSecondaryPaneToPrimary() {
        final SceneFragment topSecondaryScene = getTopSecondaryScene();
        final Announcer announcer;
        if (topSecondaryScene != null) {
            final Bundle currentArgs = topSecondaryScene.getArguments();
            announcer = new Announcer(topSecondaryScene.getClass())
                    .setArgs(currentArgs != null ? new Bundle(currentArgs) : null);
        } else {
            announcer = null;
        }
        clearSecondaryScenes();
        if (announcer != null) {
            super.startScene(announcer);
        }
    }

    @Nullable
    private SceneFragment getTopSecondaryScene() {
        if (mSecondarySceneTagList.isEmpty()) {
            return null;
        }
        return findSecondarySceneByTag(mSecondarySceneTagList.get(mSecondarySceneTagList.size() - 1));
    }

    @Nullable
    private SceneFragment findSecondarySceneByTag(@NonNull String tag) {
        final Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof SceneFragment) {
            return (SceneFragment) fragment;
        }
        return null;
    }

    @NonNull
    private SceneFragment newSecondarySceneInstance(@NonNull Class<?> clazz) {
        try {
            return (SceneFragment) clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Can't instance " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("The constructor of " + clazz.getName() + " is not visible", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException(clazz.getName() + " can not cast to scene", e);
        }
    }

    private void clearTransitions(@NonNull Fragment fragment) {
        fragment.setSharedElementEnterTransition(null);
        fragment.setSharedElementReturnTransition(null);
        fragment.setEnterTransition(null);
        fragment.setExitTransition(null);
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

        if (isSecondaryScene(scene)) {
            return;
        }

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

        if (isSecondaryScene(scene)) {
            return;
        }

        if (scene instanceof BaseScene) {
            BaseScene baseScene = (BaseScene) scene;
            baseScene.destroyDrawerView();
        }
    }

    @Override
    public void finishScene(SceneFragment scene, TransitionHelper transitionHelper) {
        if (isSecondaryScene(scene)) {
            finishSecondaryScene(scene, transitionHelper);
            return;
        }
        super.finishScene(scene, transitionHelper);
    }

    @Override
    public int getSceneStackIndex(@NonNull SceneFragment scene) {
        if (isSecondaryScene(scene)) {
            return mSecondarySceneTagList.indexOf(scene.getTag());
        }
        return super.getSceneStackIndex(scene);
    }

    @Override
    public SceneFragment findSceneByTag(String tag) {
        SceneFragment primaryScene = super.findSceneByTag(tag);
        if (primaryScene != null) {
            return primaryScene;
        }
        return tag != null ? findSecondarySceneByTag(tag) : null;
    }

    public void updateProfile() {
        if (null != mAvatar) {
            String avatarUrl = Settings.getAvatar();
            if (TextUtils.isEmpty(avatarUrl)) {
                File userAvatarFile = Settings.getUserImageFile(Settings.USER_AVATAR_IMAGE);
                if (userAvatarFile != null) {
                    Bitmap bitmap = BitmapFactory.decodeFile(userAvatarFile.getPath());
                    Drawable drawable = new BitmapDrawable(mAvatar.getResources(), bitmap);
                    mAvatar.load(drawable);
                } else {
                    mAvatar.load(R.drawable.default_avatar);
                }
            } else {
                mAvatar.load(avatarUrl, avatarUrl);
            }
        }

        if (null != mDisplayName) {
            String displayName = Settings.getDisplayName();
            if (TextUtils.isEmpty(displayName)) {
                displayName = getString(R.string.default_display_name);
            }
            Toast.makeText(this, displayName, Toast.LENGTH_LONG).show();
            mDisplayName.setText(displayName);
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

    /**
     * 更换壁纸
     */
    public void onBackgroundChange() {
        if (userImageChange != null) {
            userImageChange = null;
        }
        userImageChange = new UserImageChange(MainActivity.this,
                UserImageChange.CHANGE_BACKGROUND,
                getLayoutInflater(),
                LayoutInflater.from(MainActivity.this),
                this
        );
        userImageChange.showImageChangeDialog();
    }

    /**
     * 更换头像
     */
    public void onAvatarChange() {
        if (userImageChange != null) {
            userImageChange = null;
        }
        userImageChange = new UserImageChange(MainActivity.this,
                UserImageChange.CHANGE_AVATAR,
                getLayoutInflater(),
                LayoutInflater.from(MainActivity.this),
                this
        );

        userImageChange.showImageChangeDialog();
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

    public void toggleDrawer(int drawerGravity) {
        if (mDrawerLayout != null) {
            if (mDrawerLayout.isDrawerOpen(drawerGravity)) {
                mDrawerLayout.closeDrawer(drawerGravity);
            } else {
                mDrawerLayout.openDrawer(drawerGravity);
            }
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
        if (mNavView != null) {
            if (resId == 0) {
                mNavView.setCheckedItem(R.id.nav_stub);
            } else {
                mNavView.setCheckedItem(resId);
            }
        }
    }

    public void showTip(@StringRes int id, int length) {
        showTip(getString(id), length);
    }

    /**
     * If activity is running, show snack bar, otherwise show toast
     */
    public void showTip(CharSequence message, int length) {
        if (null != mDrawerLayout) {
            Snackbar.make(mDrawerLayout, message,
                    length == BaseScene.LENGTH_LONG ? 5000 : 3000).show();
        } else {
            Toast.makeText(this, message,
                    length == BaseScene.LENGTH_LONG ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && (mDrawerLayout.isDrawerOpen(Gravity.LEFT) ||
                mDrawerLayout.isDrawerOpen(Gravity.RIGHT))) {
            mDrawerLayout.closeDrawers();
        } else if (!mSecondarySceneTagList.isEmpty()) {
            SceneFragment topSecondaryScene = getTopSecondaryScene();
            if (topSecondaryScene != null) {
                topSecondaryScene.onBackPressed();
            } else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @SuppressLint({"NonConstantResourceId", "RtlHardcoded"})
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Don't select twice
        if (item.isChecked()) {
            return false;
        }

        int id = item.getItemId();

        switch (item.getItemId()) {
            case R.id.nav_homepage:
                Bundle nav_homepage = new Bundle();
                nav_homepage.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_HOMEPAGE);
                startSceneFirstly(new Announcer(GalleryListScene.class)
                        .setArgs(nav_homepage));
                break;
            case R.id.nav_subscription:
                Bundle nav_subscription = new Bundle();
                nav_subscription.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_SUBSCRIPTION);
                startSceneFirstly(new Announcer(GalleryListScene.class)
                        .setArgs(nav_subscription));
                break;
            case R.id.nav_whats_hot:
                Bundle nav_whats_hot = new Bundle();
                nav_whats_hot.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_WHATS_HOT);
                startSceneFirstly(new Announcer(GalleryListScene.class)
                        .setArgs(nav_whats_hot));
                break;
            case R.id.nav_top_lists:
                Bundle nav_top_lists = new Bundle();
                nav_top_lists.putString(EhTopListScene.KEY_ACTION, EhTopListScene.ACTION_TOP_LIST);
                startSceneFirstly(new Announcer(EhTopListScene.class)
                        .setArgs(nav_top_lists));
                break;
            case R.id.nav_favourite:
                startScene(new Announcer(FavoritesScene.class));
                break;
            case R.id.nav_history:
                startScene(new Announcer(HistoryScene.class));
                break;
            case R.id.nav_downloads:
                startScene(new Announcer(DownloadsScene.class));
                break;
            case R.id.nav_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SETTINGS);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + item.getItemId());
        }

        if (id != R.id.nav_settings) {
            clearSecondaryScenes();
        }

        if (id != R.id.nav_stub && mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }

        if (limitsCountView != null) {
            limitsCountView.hide();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            if (RESULT_OK == resultCode) {
                refreshTopScene();
            }
            return;
        }
        if (resultCode == RESULT_OK)
            if ((requestCode == UserImageChange.TAKE_CAMERA || requestCode == UserImageChange.PICK_PHOTO) && userImageChange != null) {
                userImageChange.saveImageForResult(requestCode, resultCode, data, mAvatar);
                return;
            }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDrawerSlide(View drawerView, float percent) {

    }

    @Override
    public void onDrawerOpened(View drawerView) {
        if (limitsCountView != null) {
            limitsCountView.onLoadData(drawerView, true);
        }
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        if (limitsCountView != null) {
            limitsCountView.hide();
        }
    }

    @Override
    public void onDrawerStateChanged(View drawerView, int newState) {

    }
}

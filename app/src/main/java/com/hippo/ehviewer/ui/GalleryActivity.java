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

import static com.hippo.ehviewer.ui.scene.download.DownloadsScene.LOCAL_GALLERY_INFO_CHANGE;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.event.GalleryActivityEvent;
import com.hippo.ehviewer.gallery.ArchiveGalleryProvider;
import com.hippo.ehviewer.gallery.DirGalleryProvider;
import com.hippo.ehviewer.gallery.EhGalleryProvider;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.widget.GalleryGuideView;
import com.hippo.ehviewer.widget.GalleryHeader;
import com.hippo.ehviewer.widget.BottomIndicatorView;
import com.hippo.ehviewer.widget.ReversibleSeekBar;
import com.hippo.lib.glgallery.GalleryProvider;
import com.hippo.lib.glgallery.GalleryView;
import com.hippo.lib.glgallery.SimpleAdapter;
import com.hippo.lib.glview.view.GLRootView;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.SystemUiHelper;
import com.hippo.widget.ColorView;
import com.hippo.lib.yorozuya.AnimationUtils;
import com.hippo.lib.yorozuya.ConcurrentPool;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.ResourcesUtils;
import com.hippo.lib.yorozuya.SimpleAnimatorListener;
import com.hippo.lib.yorozuya.SimpleHandler;
import com.hippo.lib.yorozuya.ViewUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class GalleryActivity extends EhActivity implements SeekBar.OnSeekBarChangeListener, GalleryView.Listener {

    private static final String TAG = "GalleryActivity";

    public static final String ACTION_DIR = "dir";
    public static final String ACTION_EH = "eh";

    public static final String KEY_ACTION = "action";
    public static final String KEY_FILENAME = "filename";
    public static final String KEY_URI = "uri";
    public static final String KEY_GALLERY_INFO = "gallery_info";
    public static final String DATA_IN_EVENT = "data_in_event";
    public static final String KEY_PAGE = "page";
    public static final String KEY_CURRENT_INDEX = "current_index";

    private static final long SLIDER_ANIMATION_DURING = 150;
    private static final long HIDE_SLIDER_DELAY = 3000;

    private static final int WRITE_REQUEST_CODE = 43;

    private String mAction;
    private String mFilename;
    private Uri mUri;
    private GalleryInfo mGalleryInfo;
    private int mPage;
    private String mCacheFileName;

    @Nullable
    private GLRootView mGLRootView;
    @Nullable
    private GalleryView mGalleryView;
    @Nullable
    private GalleryProvider2 mGalleryProvider;
    @Nullable
    private GalleryAdapter mGalleryAdapter;

    @Nullable
    private SystemUiHelper mSystemUiHelper;
    private boolean mShowSystemUi;

    @Nullable
    private ColorView mMaskView;
    @Nullable
    private View mClock;
    @Nullable
    private TextView mProgress;
    @Nullable
    private View mBattery;
    @Nullable
    private TextView mGalleryTitle;
    @Nullable
    private TextView mFileTypeBadge;
    @Nullable
    private TextView mGifBadge;
    @Nullable
    private View mBadgeContainer;
    @Nullable
    private View mTitleBar;
    @Nullable
    private ImageButton mBtnBack;
    @Nullable
    private ImageButton mBtnMenu;
    @Nullable
    private BottomIndicatorView mBottomIndicator;
    @Nullable
    private View mSeekBarPanel;
    @Nullable
    private ImageView mAutoTransferPanel;
    @Nullable
    private TextView mLeftText;
    @Nullable
    private TextView mRightText;
    @Nullable
    private ReversibleSeekBar mSeekBar;
    @Nullable
    private TextView mTransferCountdown;

    private ObjectAnimator mSeekBarPanelAnimator;
    private ObjectAnimator mAutoTransferAnimator;
    private ObjectAnimator mTitleBarAnimator;

    private int mLayoutMode;
    private int mSize;
    private int mCurrentIndex;

    private boolean canFinish = false;
    private boolean autoTransferring = false;

    // Countdown timer for auto-play
    private long mAutoPageStartTime = 0;
    private long mAutoPageDuration = 0;
    private final Handler mCountdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoTransferring || mTransferCountdown == null) {
                if (mTransferCountdown != null) {
                    mTransferCountdown.setVisibility(View.GONE);
                }
                return;
            }
            long elapsed = System.currentTimeMillis() - mAutoPageStartTime;
            long remaining = mAutoPageDuration - elapsed;
            if (remaining > 0) {
                // Show countdown starting at 1/3 of the total time
                long showThreshold = mAutoPageDuration / 3;
                if (elapsed >= showThreshold) {
                    long seconds = (long) Math.ceil(remaining / 1000.0);
                    mTransferCountdown.setText(seconds + "s");
                    mTransferCountdown.setVisibility(View.VISIBLE);
                }
                mCountdownHandler.postDelayed(this, 200);
            } else {
                mTransferCountdown.setVisibility(View.GONE);
            }
        }
    };

    // Animation waiting for auto page flip
    private boolean mWaitingForAnimation = false;
    
    // Track which pages are currently loading (index -> true = loading)
    private final java.util.Set<Integer> mLoadingPages = java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());
    
    // Track whether the bottom slider/auto-transfer panel is currently visible
    private boolean mSliderVisible = false;
    
    // Track last page flip time to prevent rapid consecutive flips
    private long mLastPageFlipTime = 0;
    private static final long MIN_PAGE_FLIP_INTERVAL = 500; // Minimum 500ms between flips
    
    final Runnable mAnimationWaitRunnable = new Runnable() {
        @Override
        public void run() {
            if (mWaitingForAnimation && mGalleryView != null && mGalleryProvider != null) {
                int currentIndex = mGalleryView.getCurrentIndex();
                
                // Check if current page is still loading
                if (mLoadingPages.contains(currentIndex)) {
                    android.util.Log.d(TAG, "[AutoFlip] Page " + currentIndex + " still loading, waiting...");
                    mCountdownHandler.postDelayed(this, 200);
                    return;
                }
                
                if (isCurrentPageAnimating()) {
                    // Still animating, check again shortly
                    android.util.Log.d(TAG, "[AutoFlip] Animation still running, waiting...");
                    mCountdownHandler.postDelayed(this, 100);
                } else {
                    // Animation done, proceed with page flip
                    android.util.Log.d(TAG, "[AutoFlip] Page " + currentIndex + " ready, proceeding with flip");
                    mWaitingForAnimation = false;
                    doAutoPageFlip();
                }
            }
        }
    };

    private final ConcurrentPool<NotifyTask> mNotifyTaskPool = new ConcurrentPool<>(3);

    private ScheduledExecutorService transferService = Executors.newSingleThreadScheduledExecutor();
    private final Handler transHandle = new Handler(Looper.getMainLooper());

    private final ValueAnimator.AnimatorUpdateListener mUpdateSliderListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (null != mSeekBarPanel) {
                mSeekBarPanel.requestLayout();
            }
            if (null != mAutoTransferPanel) {
                mAutoTransferPanel.requestLayout();
            }
        }
    };

    private final SimpleAnimatorListener mShowSliderListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSeekBarPanelAnimator = null;
            mAutoTransferAnimator = null;
        }
    };

    private final SimpleAnimatorListener mHideSliderListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSeekBarPanelAnimator = null;
            if (mSeekBarPanel != null) {
                mSeekBarPanel.setVisibility(View.INVISIBLE);
            }
            mAutoTransferAnimator = null;
            if (mAutoTransferPanel != null) {
                mAutoTransferPanel.setVisibility(View.INVISIBLE);
            }
            // Show bottom indicator when slider is hidden
            if (mBottomIndicator != null) {
                mBottomIndicator.setVisibility(View.VISIBLE);
            }
            // Mark slider as hidden
            mSliderVisible = false;
        }
    };

    private final SimpleAnimatorListener mHideTitleBarListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mTitleBarAnimator = null;
            if (mTitleBar != null) {
                mTitleBar.setVisibility(View.INVISIBLE);
            }
        }
    };

    private final Runnable mHideSliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSeekBarPanel != null) {
                hideSlider(mSeekBarPanel, mSeekBarPanelAnimator);
                hideSlider(mAutoTransferPanel, mAutoTransferAnimator);
                hideTitleBar(mTitleBarAnimator);
            }
        }
    };

    @Override
    protected int getThemeResId(int theme) {
        // 使用父类的默认实现，支持自适应主题切换
        return super.getThemeResId(theme);
    }

    private void buildProvider() {
        if (mGalleryProvider != null) {
            return;
        }

        if (ACTION_DIR.equals(mAction)) {
            if (mFilename != null) {
                mGalleryProvider = new DirGalleryProvider(UniFile.fromFile(new File(mFilename)));
            }
        } else if (ACTION_EH.equals(mAction)) {
            if (mGalleryInfo != null) {
                mGalleryProvider = new EhGalleryProvider(this, mGalleryInfo);
            }
        } else if (Intent.ACTION_VIEW.equals(mAction)) {
            if (mUri != null) {
                // Only support zip now
                mGalleryProvider = new ArchiveGalleryProvider(this, mUri);
            }
        }
    }

    /**
     * eventbus 通知，用于修复跳转奔溃的问题
     *
     * @param event 通知数据对象
     */
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onGalleryActivityEvent(GalleryActivityEvent event) {
        if (mGalleryProvider != null) {
            return;
        }
        mGalleryInfo = event.galleryInfo;
        mPage = event.pagePosition;
        buildProvider();
        onCreateView(null);
    }

    private void onInit() {
        Intent intent = getIntent();
        if (intent == null) {
            canFinish = true;
            return;
        }

        mAction = intent.getAction();
        mFilename = intent.getStringExtra(KEY_FILENAME);
        mUri = intent.getData();
        mGalleryInfo = intent.getParcelableExtra(KEY_GALLERY_INFO);
        boolean onEvent = intent.getBooleanExtra(DATA_IN_EVENT, false);
        if (!onEvent) {
            canFinish = true;
        }
        mPage = intent.getIntExtra(KEY_PAGE, -1);
        buildProvider();
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mAction = savedInstanceState.getString(KEY_ACTION);
        mFilename = savedInstanceState.getString(KEY_FILENAME);
        mUri = savedInstanceState.getParcelable(KEY_URI);
        mGalleryInfo = savedInstanceState.getParcelable(KEY_GALLERY_INFO);
        mPage = savedInstanceState.getInt(KEY_PAGE, -1);
        mCurrentIndex = savedInstanceState.getInt(KEY_CURRENT_INDEX);
        buildProvider();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACTION, mAction);
        outState.putString(KEY_FILENAME, mFilename);
        outState.putParcelable(KEY_URI, mUri);
        if (mGalleryInfo != null) {
            outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo);
        }
        outState.putInt(KEY_PAGE, mPage);
        outState.putInt(KEY_CURRENT_INDEX, mCurrentIndex);
    }

    @Override
    @SuppressWarnings({"WrongConstant"})
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Settings.getReadingFullscreen()) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        super.onCreate(savedInstanceState);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
        onCreateView(savedInstanceState);
        //注册事件
        EventBus.getDefault().register(this);
    }

    private void onCreateView(@Nullable Bundle savedInstanceState) {
        if (mGalleryProvider == null) {
            if (!canFinish) {
                return;
            }
            finish();
            return;
        }
        mGalleryProvider.start();

        // Get start page
        int startPage;
        if (savedInstanceState == null) {
            startPage = mPage >= 0 ? mPage : mGalleryProvider.getStartPage();
        } else {
            startPage = mCurrentIndex;
        }

        if (!isEglAvailable()) {
            mGalleryProvider.stop();
            showGlFallbackView();
            return;
        }

        setContentView(R.layout.activity_gallery);
        mGLRootView = (GLRootView) ViewUtils.$$(this, R.id.gl_root_view);
        mGalleryAdapter = new GalleryAdapter(mGLRootView, mGalleryProvider);
        if (Settings.getShowGalleryLoadingSpeed()) {
            mGalleryAdapter.setDetailedProgressProvider(new SimpleAdapter.DetailedProgressProvider() {
                @Override
                public String[] getDetailedProgress(int index, float percent) {
                    return buildDetailedProgressText(index, percent);
                }
            });
        } else {
            mGalleryAdapter.setDetailedProgressProvider(null);
        }
        Resources resources = getResources();
        mGalleryView = new GalleryView.Builder(this, mGalleryAdapter).setListener(this).setLayoutMode(Settings.getReadingDirection()).setScaleMode(Settings.getPageScaling()).setStartPosition(Settings.getStartPosition()).setStartPage(startPage).setBackgroundColor(AttrResources.getAttrColor(this, android.R.attr.colorBackground)).setEdgeColor(AttrResources.getAttrColor(this, R.attr.colorEdgeEffect) & 0xffffff | 0x33000000).setPagerInterval(Settings.getShowPageInterval() ? resources.getDimensionPixelOffset(R.dimen.gallery_pager_interval) : 0).setScrollInterval(Settings.getShowPageInterval() ? resources.getDimensionPixelOffset(R.dimen.gallery_scroll_interval) : 0).setPageMinHeight(resources.getDimensionPixelOffset(R.dimen.gallery_page_min_height)).setPageInfoInterval(resources.getDimensionPixelOffset(R.dimen.gallery_page_info_interval)).setProgressColor(ResourcesUtils.getAttrColor(this, androidx.appcompat.R.attr.colorPrimary)).setProgressSize(resources.getDimensionPixelOffset(R.dimen.gallery_progress_size)).setPageTextColor(AttrResources.getAttrColor(this, android.R.attr.textColorSecondary)).setPageTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_page_text_size)).setPageTextTypeface(Typeface.DEFAULT).setErrorTextColor(resources.getColor(R.color.red_500, null)).setErrorTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_error_text_size)).setDefaultErrorString(resources.getString(R.string.error_unknown)).setEmptyString(resources.getString(R.string.error_empty)).build();
        mGLRootView.setContentPane(mGalleryView);
        mGLRootView.setOnGenericMotionListener(this::onGenericMotion);
        mGalleryProvider.setListener(mGalleryAdapter);
        mGalleryProvider.setGLRoot(mGLRootView);

        // System UI helper
        if (Settings.getReadingFullscreen()) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            mSystemUiHelper = new SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES | SystemUiHelper.FLAG_IMMERSIVE_STICKY);
            mSystemUiHelper.hide();
            mShowSystemUi = false;
        }

        mMaskView = (ColorView) ViewUtils.$$(this, R.id.mask);
        mClock = ViewUtils.$$(this, R.id.clock);
        mProgress = (TextView) ViewUtils.$$(this, R.id.progress);
        mBattery = ViewUtils.$$(this, R.id.battery);
        mClock.setVisibility(Settings.getShowClock() ? View.VISIBLE : View.GONE);
        mProgress.setVisibility(Settings.getShowProgress() ? View.VISIBLE : View.GONE);
        mBattery.setVisibility(Settings.getShowBattery() ? View.VISIBLE : View.GONE);
        
        // Initialize restructured title bar views (direct children of FrameLayout)
        mTitleBar = ViewUtils.$$(this, R.id.title_bar);
        mBtnBack = (ImageButton) ViewUtils.$$(this, R.id.btn_back);
        mBtnMenu = (ImageButton) ViewUtils.$$(this, R.id.btn_menu);
        mGalleryTitle = (TextView) ViewUtils.$$(this, R.id.gallery_title);
        mFileTypeBadge = (TextView) ViewUtils.$$(this, R.id.file_type_badge);
        mGifBadge = (TextView) ViewUtils.$$(this, R.id.gif_badge);
        mBadgeContainer = ViewUtils.$$(this, R.id.badge_container);
        
        // Always show title bar elements regardless of fullscreen mode
        setupTitleBar();
        updateGalleryTitle();
        
        // Initialize bottom indicator
        mBottomIndicator = (BottomIndicatorView) ViewUtils.$$(this, R.id.bottom_indicator);
        if (mBottomIndicator != null) {
            mBottomIndicator.setOnClickListener(v -> {
                showSlider(mSeekBarPanel, mSeekBarPanelAnimator);
                showSlider(mAutoTransferPanel, mAutoTransferAnimator);
                showTitleBar();
            });
        }

        mSeekBarPanel = ViewUtils.$$(this, R.id.seek_bar_panel);
        mAutoTransferPanel = (ImageView) ViewUtils.$$(this, R.id.auto_transfer);
        // Auto-transfer panel visibility is now controlled dynamically based on loading state
        if (mAutoTransferPanel != null) {
            mAutoTransferPanel.setVisibility(View.INVISIBLE);
        }
        mLeftText = (TextView) ViewUtils.$$(mSeekBarPanel, R.id.left);
        mRightText = (TextView) ViewUtils.$$(mSeekBarPanel, R.id.right);
        mSeekBar = (ReversibleSeekBar) ViewUtils.$$(mSeekBarPanel, R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mAutoTransferPanel.setOnClickListener(this::autoRead);

        // Transfer countdown
        mTransferCountdown = (TextView) ViewUtils.$$(this, R.id.transfer_countdown);

        mSize = mGalleryProvider.size();
        mCurrentIndex = startPage;
        if (mGalleryView != null) {
            mLayoutMode = mGalleryView.getLayoutMode();
        }
        updateSlider();

        // Update keep screen on
        if (Settings.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Orientation
        int orientation;
        switch (Settings.getScreenRotation()) {
            default:
            case 0:
                orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                break;
            case 1:
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
                break;
            case 2:
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                break;
            case 3:
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
                break;
        }
        setRequestedOrientation(orientation);

        // Screen lightness
        setScreenLightness(Settings.getCustomScreenLightness(), Settings.getScreenLightness());

        // Cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

            GalleryHeader galleryHeader = findViewById(R.id.gallery_header);
            galleryHeader.setOnApplyWindowInsetsListener((v, insets) -> {
                galleryHeader.setDisplayCutout(insets.getDisplayCutout());
                return insets;
            });
        }

        if (Settings.getGuideGallery()) {
            FrameLayout mainLayout = (FrameLayout) ViewUtils.$$(this, R.id.main);
            mainLayout.addView(new GalleryGuideView(this));
        }
    }

    private boolean isEglAvailable() {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (display == null || display == EGL10.EGL_NO_DISPLAY) {
            return false;
        }

        int[] version = new int[2];
        if (!egl.eglInitialize(display, version)) {
            return false;
        }

        try {
            int[] numConfig = new int[1];
            return egl.eglChooseConfig(display, new int[]{EGL10.EGL_NONE}, null, 0, numConfig)
                    && numConfig[0] > 0;
        } catch (Throwable e) {
            return false;
        } finally {
            egl.eglTerminate(display);
        }
    }

    private void showGlFallbackView() {
        setContentView(R.layout.activity_gallery_fallback);
        View close = ViewUtils.$$(this, R.id.gl_fallback_close);
        close.setOnClickListener(v -> finish());
        Log.w("GalleryActivity", "EGL init failed, switch to non-GL fallback page");
        Toast.makeText(this, R.string.gallery_gl_fallback_toast, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (!transferService.isShutdown()) {
            transferService.shutdown();
            transferService = null;
        }
        mGLRootView = null;
        mGalleryView = null;
        if (mGalleryAdapter != null) {
            mGalleryAdapter.clearUploader();
            mGalleryAdapter = null;
        }
        if (mGalleryProvider != null) {
            mGalleryProvider.setListener(null);
            mGalleryProvider.stop();
            mGalleryProvider = null;
        }

        mMaskView = null;
        mClock = null;
        mProgress = null;
        mBattery = null;
        mSeekBarPanel = null;
        mAutoTransferPanel = null;
        mLeftText = null;
        mRightText = null;
        mSeekBar = null;
        mTitleBar = null;
        mBtnBack = null;
        mBtnMenu = null;
        mGifBadge = null;
        mBadgeContainer = null;
        mTransferCountdown = null;

        // Clean up countdown, animation waiting, and loading states
        mCountdownHandler.removeCallbacks(mCountdownRunnable);
        mCountdownHandler.removeCallbacks(mAnimationWaitRunnable);
        mLoadingPages.clear();

        if (transferService != null && !transferService.isShutdown()) {
            transferService.shutdown();
            transferService = null;
        }

        super.onDestroy();
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable);
        //销毁事件
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra("info", mGalleryInfo);
        setResult(LOCAL_GALLERY_INFO_CHANGE, intent);
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mGLRootView != null) {
            mGLRootView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mGLRootView != null) {
            mGLRootView.onResume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        SimpleHandler.getInstance().postDelayed(() -> {
            if (hasFocus && mSystemUiHelper != null) {
                if (mShowSystemUi) {
                    mSystemUiHelper.show();
                } else {
                    mSystemUiHelper.hide();
                }
            }
        }, 300);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mGalleryView == null) {
            return super.onKeyDown(keyCode, event);
        }
        boolean unReverse = !Settings.getReverseVolumePage();
        // Check volume
        if (Settings.getVolumePage()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT && unReverse) {
                    mGalleryView.pageRight();
                } else {
                    mGalleryView.pageLeft();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT && unReverse) {
                    mGalleryView.pageLeft();
                } else {
                    mGalleryView.pageRight();
                }
                return true;
            }
        }

        // Check keyboard and Dpad
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView.pageRight();
                } else {
                    mGalleryView.pageLeft();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mGalleryView.pageLeft();
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    mGalleryView.pageLeft();
                } else {
                    mGalleryView.pageRight();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mGalleryView.pageRight();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MENU:
                onTapMenuArea();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Check volume
        if (Settings.getVolumePage()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
        }

        // Check keyboard and Dpad
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

//    private GalleryPageView findPageByIndex(int index) {
//        if (mGalleryView != null) {
//            return mGalleryView.findPageByIndex(index);
//        } else {
//            return null;
//        }
//    }

    private void autoRead(View view) {
        autoTransferring = !autoTransferring;
        android.util.Log.d(TAG, "[AutoFlip] Auto-read toggled: " + (autoTransferring ? "ON" : "OFF"));
        if (mAutoTransferPanel == null) {
            return;
        }

        if (!autoTransferring) {
            mAutoTransferPanel.setImageResource(R.drawable.ic_start_play_24);
            transferService.shutdown();
            // Cancel countdown, animation waiting, and loading checks
            if (mTransferCountdown != null) {
                mTransferCountdown.setVisibility(View.GONE);
            }
            mCountdownHandler.removeCallbacks(mCountdownRunnable);
            mCountdownHandler.removeCallbacks(mAnimationWaitRunnable);
            mWaitingForAnimation = false;
            android.util.Log.d(TAG, "[AutoFlip] Auto-read stopped, all timers cleared");
        } else {
            mAutoTransferPanel.setImageResource(R.drawable.ic_pause_circle);
            if (transferService == null || transferService.isShutdown()) {
                transferService = Executors.newSingleThreadScheduledExecutor();
            }
            long initialDelay = Settings.getStartTransferTime();
            long waitTime = initialDelay * 2L;
            mAutoPageDuration = waitTime; // Total time between page flips
            android.util.Log.d(TAG, "[AutoFlip] Auto-read started - initialDelay=" + initialDelay + "s, waitTime=" + waitTime + "s");
            
            // Use one-shot schedule with manual rescheduling to support animation wait
            scheduleNextAutoFlip(initialDelay);
        }
    }

    /**
     * Schedule the next auto page flip after the given delay
     */
    private void scheduleNextAutoFlip(long delaySeconds) {
        if (transferService == null || transferService.isShutdown()) {
            android.util.Log.w(TAG, "[AutoFlip] transferService is unavailable, cannot schedule flip");
            return;
        }
        android.util.Log.d(TAG, "[AutoFlip] Scheduling next flip in " + delaySeconds + "s");
        try {
            transferService.schedule(() -> transHandle.post(() -> {
                if (mGalleryView == null || !autoTransferring) {
                    android.util.Log.d(TAG, "[AutoFlip] Skipping flip - galleryView=" + (mGalleryView != null) + ", autoTransferring=" + autoTransferring);
                    return;
                }
                
                // Check if we should wait for animation before flipping
                if (Settings.getWaitForAnimation() && isCurrentPageAnimating()) {
                    // Wait for animation to finish before flipping
                    android.util.Log.d(TAG, "[AutoFlip] Animation in progress, waiting before flip");
                    mWaitingForAnimation = true;
                    mCountdownHandler.removeCallbacks(mAnimationWaitRunnable);
                    mCountdownHandler.postDelayed(mAnimationWaitRunnable, 200);
                    return;
                }
                
                doAutoPageFlip();
                // Next flip is scheduled inside doAutoPageFlip()
            }), delaySeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            android.util.Log.w(TAG, "[AutoFlip] transferService rejected execution, recreating service");
            transferService = Executors.newSingleThreadScheduledExecutor();
            scheduleNextAutoFlip(delaySeconds);
        } catch (IllegalArgumentException ignore) {
            android.util.Log.w(TAG, "[AutoFlip] Failed to schedule next flip", ignore);
        }
    }

    /**
     * Perform the actual auto page flip and start countdown
     */
    private void doAutoPageFlip() {
        if (mGalleryView == null) return;
        
        // Prevent rapid consecutive flips
        long timeSinceLastFlip = System.currentTimeMillis() - mLastPageFlipTime;
        if (timeSinceLastFlip < MIN_PAGE_FLIP_INTERVAL) {
            android.util.Log.d(TAG, "[AutoFlip] Too soon since last flip (" + timeSinceLastFlip + "ms), deferring");
            mCountdownHandler.postDelayed(mAnimationWaitRunnable, MIN_PAGE_FLIP_INTERVAL - timeSinceLastFlip);
            mWaitingForAnimation = true;
            return;
        }
        
        int currentIndex = mGalleryView.getCurrentIndex();
        
        // Check if current page is still loading
        if (mLoadingPages.contains(currentIndex)) {
            android.util.Log.d(TAG, "[AutoFlip] Page " + currentIndex + " still loading, deferring flip");
            mCountdownHandler.postDelayed(mAnimationWaitRunnable, 200);
            mWaitingForAnimation = true;
            return;
        }
        
        android.util.Log.d(TAG, "[AutoFlip] Executing page flip at index " + currentIndex);
        
        // Record flip time
        mLastPageFlipTime = System.currentTimeMillis();
        
        // Reset countdown
        mAutoPageStartTime = System.currentTimeMillis();
        if (mTransferCountdown != null) {
            mTransferCountdown.setVisibility(View.GONE);
        }
        mCountdownHandler.removeCallbacks(mCountdownRunnable);
        mCountdownHandler.postDelayed(mCountdownRunnable, 200);
        
        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            mGalleryView.pageLeft();
        } else {
            mGalleryView.pageRight();
        }
        
        // Schedule next flip after this one completes
        // mAutoPageDuration is already in SECONDS, no conversion needed
        long waitTime = mAutoPageDuration > 0 ? mAutoPageDuration : 4L;
        android.util.Log.d(TAG, "[AutoFlip] Scheduling next flip in " + waitTime + " seconds");
        scheduleNextAutoFlip(waitTime);
    }

    /**
     * Check if the current page image is still animating (GIF/WebP playing)
     */
    private boolean isCurrentPageAnimating() {
        if (mGalleryView == null || mGalleryProvider == null) {
            return false;
        }
        if (!mGalleryProvider.isAnimated(mCurrentIndex)) {
            return false;
        }
        // Try to get the current page view and check animation state
        return mGalleryView.isCurrentPageAnimating();
    }

    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
        if (mGalleryView == null) {
            return false;
        }

        if (motionEvent.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            if (motionEvent.getAction() == MotionEvent.ACTION_SCROLL) {
                float scrollY = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY == 0) return false;  // wrong input
                if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                    if (scrollY > 0) {
                        mGalleryView.pageLeft();
                    } else {
                        mGalleryView.pageRight();
                    }
                } else {
                    if (scrollY < 0) {
                        mGalleryView.pageLeft();
                    } else {
                        mGalleryView.pageRight();
                    }
                }
                return true;
            }
        }
        return false;
    }

    @SuppressLint("SetTextI18n")
    private void updateProgress() {
        if (mProgress == null) {
            return;
        }
        if (mSize <= 0 || mCurrentIndex < 0) {
            mProgress.setText(null);
        } else {
            mProgress.setText((mCurrentIndex + 1) + "/" + mSize);
        }
    }

    private String buildLoadingProgressText(int index, float percent) {
        int percentValue = Math.min(100, Math.max(0, Math.round(percent * 100f)));
        String speedText = null;
        if (mGalleryProvider instanceof EhGalleryProvider) {
            long speed = ((EhGalleryProvider) mGalleryProvider).getPageSpeedBytesPerSecond(index);
            if (speed > 0L) {
                speedText = formatSpeed(speed);
            }
        }
        if (speedText == null) {
            return percentValue + "%";
        }
        return percentValue + "% " + speedText;
    }

    /**
     * Build detailed progress text array for loading display
     * @return String array: [0]=page text, [1]=progress text, [2]=speed text
     */
    private String[] buildDetailedProgressText(int index, float percent) {
        int percentValue = Math.min(100, Math.max(0, Math.round(percent * 100f)));
        String progressText = percentValue + "%";
        
        String speedText = "";
        if (mGalleryProvider instanceof EhGalleryProvider) {
            long speed = ((EhGalleryProvider) mGalleryProvider).getPageSpeedBytesPerSecond(index);
            if (speed > 0L) {
                speedText = formatSpeed(speed);
            }
        }
        
        return new String[] { "第" + (index + 1) + "页", progressText, speedText };
    }

    private String formatSpeed(long bytesPerSecond) {
        final long kb = 1024L;
        final long mb = kb * 1024L;
        final long gb = mb * 1024L;
        if (bytesPerSecond >= gb) {
            return String.format(Locale.US, "%.1fGB/s", bytesPerSecond / (float) gb);
        }
        if (bytesPerSecond >= mb) {
            return String.format(Locale.US, "%.1fMB/s", bytesPerSecond / (float) mb);
        }
        if (bytesPerSecond >= kb) {
            return String.format(Locale.US, "%.1fKB/s", bytesPerSecond / (float) kb);
        }
        return bytesPerSecond + "B/s";
    }

    @SuppressLint("SetTextI18n")
    private void updateSlider() {
        if (mSeekBar == null || mRightText == null || mLeftText == null || mSize <= 0 || mCurrentIndex < 0) {
            return;
        }

        TextView start;
        TextView end;
        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            start = mRightText;
            end = mLeftText;
            mSeekBar.setReverse(true);
        } else {
            start = mLeftText;
            end = mRightText;
            mSeekBar.setReverse(false);
        }
        start.setText(Integer.toString(mCurrentIndex + 1));
        end.setText(Integer.toString(mSize));
        mSeekBar.setMax(mSize - 1);
        mSeekBar.setProgress(mCurrentIndex);
        
        // Update title and badge when page changes
        updateGalleryTitle();
    }

    /**
     * Update gallery title and file type badge
     */
    private void updateGalleryTitle() {
        if (mGalleryTitle == null) {
            return;
        }
        
        // Set title from gallery info or filename
        String title = null;
        if (mGalleryInfo != null) {
            title = mGalleryInfo.title;
        } else if (mFilename != null) {
            title = new java.io.File(mFilename).getName();
        }
        
        if (!TextUtils.isEmpty(title)) {
            mGalleryTitle.setText(title);
            mGalleryTitle.setVisibility(View.VISIBLE);
        } else {
            mGalleryTitle.setVisibility(View.GONE);
        }
        
        // Update file type badge based on current page
        updateFileTypeBadge();
    }

    /**
     * Update file type badge based on current page image type
     */
    private void updateFileTypeBadge() {
        if (mFileTypeBadge == null || mGalleryProvider == null) {
            return;
        }
        
        // Get file extension from provider
        String extension = mGalleryProvider.getImageExtension(mCurrentIndex);
        if (!TextUtils.isEmpty(extension)) {
            // Remove the dot and uppercase
            String badgeText = extension.substring(1).toUpperCase();
            mFileTypeBadge.setText(badgeText);
            mFileTypeBadge.setVisibility(View.VISIBLE);
            
            // Add animated indicator for GIF/WebP animations
            if (mGalleryProvider.isAnimated(mCurrentIndex)) {
                mFileTypeBadge.setText(badgeText + " " + getString(R.string.settings_read_wait_for_animation));
            }
        } else {
            mFileTypeBadge.setVisibility(View.GONE);
        }
        
        // Update GIF badge separately
        updateGifBadge();
    }

    /**
     * Update GIF badge when current page is an animated image
     */
    private void updateGifBadge() {
        if (mGifBadge == null || mGalleryProvider == null) {
            return;
        }
        if (mGalleryProvider.isAnimated(mCurrentIndex)) {
            mGifBadge.setText("GIF");
            mGifBadge.setVisibility(View.VISIBLE);
        } else {
            mGifBadge.setVisibility(View.GONE);
        }
    }

    /**
     * Setup title bar with back button, scrolling title, and menu button
     * Note: Title bar visibility is controlled by showSlider/hideSlider
     */
    private void setupTitleBar() {
        if (mTitleBar == null) {
            return;
        }
        
        // Setup click listeners only (visibility handled by showSlider/hideSlider)
        if (mBtnBack != null) {
            mBtnBack.setOnClickListener(v -> onBackPressed());
        }
        if (mBtnMenu != null) {
            mBtnMenu.setOnClickListener(v -> onTapMenuArea());
        }
        
        // Enable marquee scrolling for long titles
        if (mGalleryTitle != null) {
            mGalleryTitle.setSelected(true);
            mGalleryTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            mGalleryTitle.setMarqueeRepeatLimit(-1);
        }
    }

    /**
     * Show title bar with slide-down animation
     */
    private void showTitleBar() {
        if (mTitleBar == null) return;
        
        if (null != mTitleBarAnimator) {
            mTitleBarAnimator.cancel();
        }
        
        mTitleBar.setTranslationY(-mTitleBar.getHeight());
        mTitleBar.setVisibility(View.VISIBLE);
        
        ObjectAnimator animator = ObjectAnimator.ofFloat(mTitleBar, "translationY", 0.0f);
        animator.setDuration(SLIDER_ANIMATION_DURING);
        animator.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        animator.addUpdateListener(mUpdateSliderListener);
        animator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTitleBarAnimator = null;
            }
        });
        animator.start();
        mTitleBarAnimator = animator;
    }

    /**
     * Hide title bar with slide-up animation
     */
    private void hideTitleBar(ObjectAnimator animator) {
        if (mTitleBar == null) return;
        
        if (null != animator) {
            animator.cancel();
        }
        
        animator = ObjectAnimator.ofFloat(mTitleBar, "translationY", -mTitleBar.getHeight());
        animator.setDuration(SLIDER_ANIMATION_DURING);
        animator.setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR);
        animator.addUpdateListener(mUpdateSliderListener);
        animator.addListener(mHideTitleBarListener);
        animator.start();
        mTitleBarAnimator = animator;
    }

    /**
     * Start title marquee scrolling
     */
    private void startTitleMarquee() {
        if (mGalleryTitle != null) {
            mGalleryTitle.setSelected(true);
        }
    }

    /**
     * Stop title marquee scrolling
     */
    private void stopTitleMarquee() {
        if (mGalleryTitle != null) {
            mGalleryTitle.setSelected(false);
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        TextView start;
        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            start = mRightText;
        } else {
            start = mLeftText;
        }
        if (fromUser && null != start) {
            start.setText(Integer.toString(progress + 1));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY);
        int progress = seekBar.getProgress();
        if (progress != mCurrentIndex && null != mGalleryView) {
            mGalleryView.setCurrentPage(progress);
        }
    }

    @Override
    public void onUpdateCurrentIndex(int index) {
        if (null != mGalleryProvider) {
            mGalleryProvider.putStartPage(index);
        }

        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setData(NotifyTask.KEY_CURRENT_INDEX, index);
        SimpleHandler.getInstance().post(task);
        
        // Update play button visibility when page changes
        updateAutoTransferVisibility();
    }
    
    /**
     * Update auto-transfer (play button) visibility based on loading state and slider visibility.
     * Hide during loading, show only when loading is complete AND slider is visible.
     */
    private void updateAutoTransferVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::updateAutoTransferVisibility);
            return;
        }

        if (mAutoTransferPanel == null || mGalleryView == null) {
            return;
        }
        
        int currentIndex = mGalleryView.getCurrentIndex();
        boolean isLoading = mLoadingPages.contains(currentIndex);
        
        if (isLoading || !mSliderVisible) {
            // Hide play button during loading or when slider is hidden
            mAutoTransferPanel.setVisibility(View.INVISIBLE);
        } else {
            // Show play button when loading is complete and slider is visible
            mAutoTransferPanel.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onTapSliderArea() {
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setData(NotifyTask.KEY_TAP_SLIDER_AREA, 0);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onTapMenuArea() {
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setData(NotifyTask.KEY_TAP_MENU_AREA, 0);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onTapErrorText(int index) {
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setData(NotifyTask.KEY_TAP_ERROR_TEXT, index);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onLongPressPage(int index) {
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setData(NotifyTask.KEY_LONG_PRESS_PAGE, index);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onAutoTransferDone() {
        if (autoTransferring) {
            autoRead(mAutoTransferPanel);
        }
    }

//    @Override
//    public boolean onGenericMotionEvent(MotionEvent event) {
//        //The input source is a pointing device associated with a display.
//        //输入源为可显示的指针设备，如：mouse pointing device(鼠标指针),stylus pointing device(尖笔设备)
//        if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)) {
//            switch (event.getAction()) {
//                // process the scroll wheel movement...处理滚轮事件
//                case MotionEvent.ACTION_SCROLL:
//                    //获得垂直坐标上的滚动方向,也就是滚轮向下滚
//                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
//                        Log.i("fortest::onGenericMotionEvent", "down");
//                    }
//                    //获得垂直坐标上的滚动方向,也就是滚轮向上滚
//                    else {
//                        Log.i("fortest::onGenericMotionEvent", "up");
//                    }
//                    return true;
//            }
//        }
//        return super.onGenericMotionEvent(event);
//    }


    private void showSlider(View sliderPanel, ObjectAnimator animator) {
        if (null != mSeekBarPanelAnimator) {
            animator.cancel();
        }
        if (sliderPanel == mAutoTransferPanel) {
            sliderPanel.setTranslationX(sliderPanel.getWidth());
            animator = ObjectAnimator.ofFloat(sliderPanel, "translationX", 0.0f);
        } else {
            sliderPanel.setTranslationY(sliderPanel.getHeight());
            animator = ObjectAnimator.ofFloat(sliderPanel, "translationY", 0.0f);
        }

        sliderPanel.setVisibility(View.VISIBLE);
        
        // Hide bottom indicator when slider is shown
        if (mBottomIndicator != null) {
            mBottomIndicator.setVisibility(View.GONE);
        }
        
        // Mark slider as visible
        if (sliderPanel == mSeekBarPanel) {
            mSliderVisible = true;
        }

        animator.setDuration(SLIDER_ANIMATION_DURING);
        animator.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        animator.addUpdateListener(mUpdateSliderListener);
        animator.addListener(mShowSliderListener);
        animator.start();

        if (null != mSystemUiHelper) {
            mSystemUiHelper.show();
            mShowSystemUi = true;
        }
        
        // Update play button visibility when slider is shown
        updateAutoTransferVisibility();
    }


    private void hideSlider(View sliderPanel, ObjectAnimator animator) {
        if (null != animator) {
            animator.cancel();
        }
        if (sliderPanel == mAutoTransferPanel) {
            animator = ObjectAnimator.ofFloat(sliderPanel, "translationX", sliderPanel.getWidth());
        } else {
            animator = ObjectAnimator.ofFloat(sliderPanel, "translationY", sliderPanel.getHeight());
        }

        animator.setDuration(SLIDER_ANIMATION_DURING);
        animator.setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR);
        animator.addUpdateListener(mUpdateSliderListener);
        animator.addListener(mHideSliderListener);
        animator.start();

        // Also hide title bar
        hideTitleBar(mTitleBarAnimator);

        if (null != mSystemUiHelper) {
            mSystemUiHelper.hide();
            mShowSystemUi = false;
        }
    }

    /**
     * @param lightness 0 - 200
     */
    private void setScreenLightness(boolean enable, int lightness) {
        if (null == mMaskView) {
            return;
        }

        Window w = getWindow();
        WindowManager.LayoutParams lp = w.getAttributes();
        if (enable) {
            lightness = MathUtils.clamp(lightness, 0, 200);
            if (lightness > 100) {
                mMaskView.setColor(0);
                // Avoid BRIGHTNESS_OVERRIDE_OFF,
                // screen may be off when lp.screenBrightness is 0.0f
                lp.screenBrightness = Math.max((lightness - 100) / 100.0f, 0.01f);
            } else {
                mMaskView.setColor(MathUtils.lerp(0xde, 0x00, lightness / 100.0f) << 24);
                lp.screenBrightness = 0.01f;
            }
        } else {
            mMaskView.setColor(0);
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
        w.setAttributes(lp);
    }

    private void shareImage(int page) {
        if (null == mGalleryProvider) {
            return;
        }

        File dir = AppConfig.getExternalTempDir();
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_create_temp_file, Toast.LENGTH_SHORT).show();
            return;
        }
        UniFile file;
        if (null == (file = mGalleryProvider.save(page, UniFile.fromFile(dir), mGalleryProvider.getImageFilename(page)))) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }
        String filename = file.getName();
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }


        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(filename));
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/jpeg";
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, file.getUri());
        intent.setType(mimeType);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_image)));
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(this, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage(int page) {
        if (null == mGalleryProvider) {
            return;
        }

        File dir = AppConfig.getExternalImageDir();
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }
        UniFile file;
        if (null == (file = mGalleryProvider.save(page, UniFile.fromFile(dir), mGalleryProvider.getImageFilename(page)))) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, getString(R.string.image_saved, file.getUri()), Toast.LENGTH_SHORT).show();

        // Sync media store
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, file.getUri()));
    }

    private void saveImageTo(int page) {
        if (null == mGalleryProvider) {
            return;
        }
        File dir = getCacheDir();
        UniFile file;
        if (null == (file = mGalleryProvider.save(page, UniFile.fromFile(dir), mGalleryProvider.getImageFilename(page)))) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }
        String filename = file.getName();
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show();
            return;
        }
        mCacheFileName = filename;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(intent, WRITE_REQUEST_CODE);
//            registerForActivityResult(intent, WRITE_REQUEST_CODE);
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::saveImageDats)
//                    .launch(intent);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(this, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                String filepath = getCacheDir() + "/" + mCacheFileName;
                File cacheFile = new File(filepath);

                InputStream is = null;
                OutputStream os = null;
                ContentResolver resolver = getContentResolver();

                try {
                    is = new FileInputStream(cacheFile);
                    os = resolver.openOutputStream(uri);
                    IOUtils.copy(is, os);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.closeQuietly(is);
                    IOUtils.closeQuietly(os);
                }

                cacheFile.delete();

                Toast.makeText(this, getString(R.string.image_saved, uri.getPath()), Toast.LENGTH_SHORT).show();
                // Sync media store
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
            }
        }
    }

    private void saveImageDats(ActivityResult result) {
        if (result == null) {
            return;
        }
        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }
        Intent resultData = result.getData();
        if (resultData != null) {
            Uri uri = resultData.getData();
            String filepath = getCacheDir() + "/" + mCacheFileName;
            File cacheFile = new File(filepath);

            InputStream is = null;
            OutputStream os = null;
            ContentResolver resolver = getContentResolver();

            try {
                is = new FileInputStream(cacheFile);
                os = resolver.openOutputStream(uri);
                IOUtils.copy(is, os);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(os);
            }

            boolean deleted = cacheFile.delete();
            if (!deleted) {
                cacheFile.deleteOnExit();
            }

            Toast.makeText(this, getString(R.string.image_saved, uri.getPath()), Toast.LENGTH_SHORT).show();
            // Sync media store
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
        }
    }


    private void showPageDialog(final int page) {
        Resources resources = GalleryActivity.this.getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
        builder.setTitle(resources.getString(R.string.page_menu_title, page + 1));

        final CharSequence[] items;
        items = new CharSequence[]{getString(R.string.page_menu_refresh), getString(R.string.page_menu_share), getString(R.string.page_menu_save), getString(R.string.page_menu_save_to)};
        pageDialogListener(builder, items, page);
        builder.show();
    }

    private void pageDialogListener(AlertDialog.Builder builder, CharSequence[] items, int page) {
        builder.setItems(items, (dialog, which) -> {
            if (mGalleryProvider == null) {
                return;
            }

            switch (which) {
                case 0: // Refresh
                    mGalleryProvider.removeCache(page);
                    mGalleryProvider.forceRequest(page);
                    break;
                case 1: // Share
                    shareImage(page);
                    break;
                case 2: // Save
                    saveImage(page);
                    break;
                case 3: // Save to
                    saveImageTo(page);
                    break;
            }
        });
    }

    private class GalleryMenuHelper implements DialogInterface.OnClickListener {

        private final View mView;
        private final Spinner mScreenRotation;
        private final Spinner mReadingDirection;
        private final Spinner mScaleMode;
        private final Spinner mStartPosition;
        private final TextView mStartTransferTimeDisplay;
        private final ImageButton mBtnDecreaseTime;
        private final ImageButton mBtnIncreaseTime;
        private final EditText mStaticTransferTime;
        private final EditText mAnimatedTransferTime;
        private final SwitchCompat mWaitForAnimation;
        private final SwitchCompat mShowTransferCountdown;
        private final SwitchCompat mKeepScreenOn;
        private final SwitchCompat mShowClock;
        private final SwitchCompat mShowProgress;
        private final SwitchCompat mShowBattery;
        private final SwitchCompat mShowPageInterval;
        private final SwitchCompat mVolumePage;
        private final SwitchCompat mReverseVolumePage;
        private final SwitchCompat mReadingFullscreen;
        private final SwitchCompat mCustomScreenLightness;
        private final SeekBar mScreenLightness;

        @SuppressLint("InflateParams")
        public GalleryMenuHelper(Context context) {
            mView = LayoutInflater.from(context).inflate(R.layout.dialog_gallery_menu, null);
            mScreenRotation = mView.findViewById(R.id.screen_rotation);
            mReadingDirection = mView.findViewById(R.id.reading_direction);
            mScaleMode = mView.findViewById(R.id.page_scaling);
            mStartPosition = mView.findViewById(R.id.start_position);
            mStartTransferTimeDisplay = mView.findViewById(R.id.start_transfer_time_display);
            mBtnDecreaseTime = mView.findViewById(R.id.btn_decrease_time);
            mBtnIncreaseTime = mView.findViewById(R.id.btn_increase_time);
            mStaticTransferTime = mView.findViewById(R.id.static_transfer_time);
            mAnimatedTransferTime = mView.findViewById(R.id.animated_transfer_time);
            mWaitForAnimation = mView.findViewById(R.id.wait_for_animation);
            mShowTransferCountdown = mView.findViewById(R.id.show_transfer_countdown);
            mKeepScreenOn = mView.findViewById(R.id.keep_screen_on);
            mShowClock = mView.findViewById(R.id.show_clock);
            mShowProgress = mView.findViewById(R.id.show_progress);
            mShowBattery = mView.findViewById(R.id.show_battery);
            mShowPageInterval = mView.findViewById(R.id.show_page_interval);
            mVolumePage = mView.findViewById(R.id.volume_page);
            mReverseVolumePage = mView.findViewById(R.id.reverse_volume_page);
            mReadingFullscreen = mView.findViewById(R.id.reading_fullscreen);
            mCustomScreenLightness = mView.findViewById(R.id.custom_screen_lightness);
            mScreenLightness = mView.findViewById(R.id.screen_lightness);

            mScreenRotation.setSelection(Settings.getScreenRotation());
            mReadingDirection.setSelection(Settings.getReadingDirection());
            mScaleMode.setSelection(Settings.getPageScaling());
            mStartPosition.setSelection(Settings.getStartPosition());
            mStartTransferTimeDisplay.setText(String.valueOf(Settings.getStartTransferTime()));
            
            // Stepper button listeners
            mBtnDecreaseTime.setOnClickListener(v -> {
                int val = Integer.parseInt(mStartTransferTimeDisplay.getText().toString());
                if (val > 1) {
                    mStartTransferTimeDisplay.setText(String.valueOf(val - 1));
                }
            });
            mBtnIncreaseTime.setOnClickListener(v -> {
                int val = Integer.parseInt(mStartTransferTimeDisplay.getText().toString());
                if (val < 15) {
                    mStartTransferTimeDisplay.setText(String.valueOf(val + 1));
                }
            });
            
            // Initialize new UI elements
            mStaticTransferTime.setText(String.valueOf(Settings.getStaticTransferTime()));
            mAnimatedTransferTime.setText(String.valueOf(Settings.getAnimatedTransferTime()));
            mWaitForAnimation.setChecked(Settings.getWaitForAnimation());
            mShowTransferCountdown.setChecked(Settings.getShowTransferCountdown());
            
            mKeepScreenOn.setChecked(Settings.getKeepScreenOn());
            mShowClock.setChecked(Settings.getShowClock());
            mShowProgress.setChecked(Settings.getShowProgress());
            mShowBattery.setChecked(Settings.getShowBattery());
            mShowPageInterval.setChecked(Settings.getShowPageInterval());
            mVolumePage.setChecked(Settings.getVolumePage());
            mReverseVolumePage.setChecked(Settings.getReverseVolumePage());
            mReadingFullscreen.setChecked(Settings.getReadingFullscreen());
            mCustomScreenLightness.setChecked(Settings.getCustomScreenLightness());
            mScreenLightness.setProgress(Settings.getScreenLightness());
            mScreenLightness.setEnabled(Settings.getCustomScreenLightness());

            mVolumePage.setOnCheckedChangeListener(this::onVolumePageChange);

            if (Settings.getVolumePage()) {
                mReverseVolumePage.setVisibility(View.VISIBLE);

            } else {
                mReverseVolumePage.setVisibility(View.GONE);
            }

            mCustomScreenLightness.setOnCheckedChangeListener((buttonView, isChecked) -> mScreenLightness.setEnabled(isChecked));
        }

        private void onVolumePageChange(CompoundButton compoundButton, boolean b) {
            if (compoundButton.isChecked()) {
                mReverseVolumePage.setVisibility(View.VISIBLE);
            } else {
                mReverseVolumePage.setVisibility(View.GONE);
            }
        }

        public View getView() {
            return mView;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mGalleryView == null) {
                return;
            }

            int screenRotation = mScreenRotation.getSelectedItemPosition();
            int layoutMode = GalleryView.sanitizeLayoutMode(mReadingDirection.getSelectedItemPosition());
            int scaleMode = GalleryView.sanitizeScaleMode(mScaleMode.getSelectedItemPosition());
            int startPosition = GalleryView.sanitizeStartPosition(mStartPosition.getSelectedItemPosition());
            boolean keepScreenOn = mKeepScreenOn.isChecked();
            boolean showClock = mShowClock.isChecked();
            boolean showProgress = mShowProgress.isChecked();
            boolean showBattery = mShowBattery.isChecked();
            boolean showPageInterval = mShowPageInterval.isChecked();
            boolean volumePage = mVolumePage.isChecked();
            boolean reverseVolumePage = mReverseVolumePage.isChecked();
            boolean readingFullscreen = mReadingFullscreen.isChecked();
            boolean customScreenLightness = mCustomScreenLightness.isChecked();

            int screenLightness = mScreenLightness.getProgress();
            int transferTime;
            try {
                transferTime = Integer.parseInt(mStartTransferTimeDisplay.getText().toString());
            } catch (NumberFormatException e) {
                transferTime = Settings.getStartTransferTime();
            }
            
            // Get new settings values
            float staticTransferTime = 4.0f;
            float animatedTransferTime = 8.0f;
            try {
                staticTransferTime = Float.parseFloat(mStaticTransferTime.getText().toString());
                animatedTransferTime = Float.parseFloat(mAnimatedTransferTime.getText().toString());
            } catch (NumberFormatException e) {
                // Use defaults if parsing fails
            }
            boolean waitForAnimation = mWaitForAnimation.isChecked();
            boolean showTransferCountdown = mShowTransferCountdown.isChecked();

            boolean oldReadingFullscreen = Settings.getReadingFullscreen();

            Settings.putScreenRotation(screenRotation);
            Settings.putReadingDirection(layoutMode);
            Settings.putPageScaling(scaleMode);
            Settings.putStartPosition(startPosition);
            Settings.putStartTransferTime(transferTime);
            Settings.putStaticTransferTime(staticTransferTime);
            Settings.putAnimatedTransferTime(animatedTransferTime);
            Settings.putWaitForAnimation(waitForAnimation);
            Settings.putShowTransferCountdown(showTransferCountdown);
            Settings.putKeepScreenOn(keepScreenOn);
            Settings.putShowClock(showClock);
            Settings.putShowProgress(showProgress);
            Settings.putShowBattery(showBattery);
            Settings.putShowPageInterval(showPageInterval);
            Settings.putVolumePage(volumePage);
            Settings.putReadingFullscreen(readingFullscreen);
            Settings.putCustomScreenLightness(customScreenLightness);
            Settings.putScreenLightness(screenLightness);
            Settings.putReverseVolumePage(reverseVolumePage);
            if (!volumePage) {
                mReverseVolumePage.setVisibility(View.GONE);
            } else {
                mReverseVolumePage.setVisibility(View.VISIBLE);
            }

            int orientation;
            switch (screenRotation) {
                default:
                case 0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                    break;
                case 1:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
                    break;
                case 2:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                    break;
                case 3:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
                    break;
            }
            setRequestedOrientation(orientation);
            mGalleryView.setLayoutMode(layoutMode);
            mGalleryView.setScaleMode(scaleMode);
            mGalleryView.setStartPosition(startPosition);
            if (keepScreenOn) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            if (mClock != null) {
                mClock.setVisibility(showClock ? View.VISIBLE : View.GONE);
            }
            if (mProgress != null) {
                mProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            }
            if (mBattery != null) {
                mBattery.setVisibility(showBattery ? View.VISIBLE : View.GONE);
            }
            mGalleryView.setPagerInterval(showPageInterval ? getResources().getDimensionPixelOffset(R.dimen.gallery_pager_interval) : 0);
            mGalleryView.setScrollInterval(showPageInterval ? getResources().getDimensionPixelOffset(R.dimen.gallery_scroll_interval) : 0);
            setScreenLightness(customScreenLightness, screenLightness);

            // Update slider
            mLayoutMode = layoutMode;
            updateSlider();

            if (oldReadingFullscreen != readingFullscreen) {
                recreate();
            }
        }
    }

    private class NotifyTask implements Runnable {

        public static final int KEY_LAYOUT_MODE = 0;
        public static final int KEY_SIZE = 1;
        public static final int KEY_CURRENT_INDEX = 2;
        public static final int KEY_TAP_SLIDER_AREA = 3;
        public static final int KEY_TAP_MENU_AREA = 4;
        public static final int KEY_TAP_ERROR_TEXT = 5;
        public static final int KEY_LONG_PRESS_PAGE = 6;

        private int mKey;
        private int mValue;

        public void setData(int key, int value) {
            mKey = key;
            mValue = value;
        }

        private void onTapMenuArea() {
            AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
            GalleryMenuHelper helper = new GalleryMenuHelper(builder.getContext());
            builder.setTitle(R.string.gallery_menu_title).setView(helper.getView()).setPositiveButton(android.R.string.ok, helper).show();
        }

        private void onTapSliderArea() {
            if (mSeekBarPanel == null || mSize <= 0 || mCurrentIndex < 0 || mAutoTransferPanel == null) {
                return;
            }

            SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable);

            if (mSeekBarPanel.getVisibility() == View.VISIBLE) {
                hideSlider(mSeekBarPanel, mSeekBarPanelAnimator);
                hideSlider(mAutoTransferPanel, mAutoTransferAnimator);
                hideTitleBar(mTitleBarAnimator);
            } else {
                showSlider(mSeekBarPanel, mSeekBarPanelAnimator);
                showSlider(mAutoTransferPanel, mAutoTransferAnimator);
                showTitleBar();
                SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY);
            }
        }

        private void onTapErrorText(int index) {
            if (mGalleryProvider != null) {
                mGalleryProvider.forceRequest(index);
            }
        }

        private void onLongPressPage(final int index) {
            showPageDialog(index);
        }

        @Override
        public void run() {
            switch (mKey) {
                case KEY_LAYOUT_MODE:
                    GalleryActivity.this.mLayoutMode = mValue;
                    updateSlider();
                    break;
                case KEY_SIZE:
                    GalleryActivity.this.mSize = mValue;
                    updateSlider();
                    updateProgress();
                    break;
                case KEY_CURRENT_INDEX:
                    GalleryActivity.this.mCurrentIndex = mValue;
                    updateSlider();
                    updateProgress();
                    android.util.Log.d(TAG, "[AutoFlip] Page changed to " + mValue);
                    // Cancel animation wait when page changes (manual navigation)
                    if (mWaitingForAnimation) {
                        mWaitingForAnimation = false;
                        mCountdownHandler.removeCallbacks(mAnimationWaitRunnable);
                    }
                    break;
                case KEY_TAP_MENU_AREA:
                    onTapMenuArea();
                    break;
                case KEY_TAP_SLIDER_AREA:
                    onTapSliderArea();
                    break;
                case KEY_TAP_ERROR_TEXT:
                    onTapErrorText(mValue);
                    break;
                case KEY_LONG_PRESS_PAGE:
                    onLongPressPage(mValue);
                    break;
            }
            mNotifyTaskPool.push(this);
        }
    }

    private class GalleryAdapter extends SimpleAdapter {

        public GalleryAdapter(@NonNull GLRootView glRootView, @NonNull GalleryProvider provider) {
            super(glRootView, provider);
        }

        @Override
        public void onBind(com.hippo.lib.glgallery.GalleryPageView view, int index) {
            // CRITICAL: Mark page as loading BEFORE calling super.onBind()
            // because super.onBind() calls mProvider.request() which may
            // synchronously fire notifyPageSucceed() for cached images.
            // If we add after super.onBind(), the page gets stuck in mLoadingPages forever.
            mLoadingPages.add(index);
            android.util.Log.d(TAG, "[AutoFlip] Page " + index + " marked as loading (pre-bind)");
            
            super.onBind(view, index);
        }

        @Override
        public void onUnbind(com.hippo.lib.glgallery.GalleryPageView view, int index) {
            super.onUnbind(view, index);
            mLoadingPages.remove(index);
        }

        @Override
        public void onDataChanged() {
            super.onDataChanged();

            if (mGalleryProvider != null) {
                int size = mGalleryProvider.size();
                NotifyTask task = mNotifyTaskPool.pop();
                if (task == null) {
                    task = new NotifyTask();
                }
                task.setData(NotifyTask.KEY_SIZE, size);
                SimpleHandler.getInstance().post(task);
            }
        }

        @Override
        public void onPageWait(int index) {
            super.onPageWait(index);
            mLoadingPages.add(index);
            // Hide play button when page starts loading
            updateAutoTransferVisibility();
        }

        @Override
        public void onPagePercent(int index, float percent) {
            super.onPagePercent(index, percent);
            // Page is still loading, keep in loading set
            if (percent < 1.0f) {
                mLoadingPages.add(index);
            }
        }

        @Override
        public void onPageSucceed(int index, com.hippo.lib.glview.image.ImageWrapper image) {
            super.onPageSucceed(index, image);
            mLoadingPages.remove(index);
            android.util.Log.d(TAG, "[AutoFlip] Page " + index + " loaded successfully");
            // Show play button if loading is complete and slider is visible
            updateAutoTransferVisibility();
        }

        @Override
        public void onPageFailed(int index, String error) {
            super.onPageFailed(index, error);
            mLoadingPages.remove(index);
            android.util.Log.d(TAG, "[AutoFlip] Page " + index + " loading failed: " + error);
            // Show play button if loading is complete and slider is visible
            updateAutoTransferVisibility();
        }
    }

}

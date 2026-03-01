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

package com.hippo.ehviewer.ui.local;

import android.animation.ObjectAnimator;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.annotation.SuppressLint;

import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.gallery.ArchiveGalleryProvider;
import com.hippo.ehviewer.gallery.DirGalleryProvider;
import com.hippo.ehviewer.gallery.EhGalleryProvider;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.widget.GalleryGuideView;
import com.hippo.ehviewer.widget.GalleryHeader;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;

import android.view.MenuItem;

public class LocalGalleryViewerActivity extends EhActivity implements SeekBar.OnSeekBarChangeListener {
    
    private static final String KEY_GALLERY_INFO = "gallery_info";
    
    private LocalGalleryInfo mGalleryInfo;
    private List<String> mImagePaths;
    
    // UI组件 - 与GalleryActivity保持一致
    private GLRootView mGLRootView;
    private GalleryView mGalleryView;
    private LocalGalleryAdapter mGalleryAdapter;
    private GalleryProvider2 mGalleryProvider;
    
    private ColorView mMaskView;
    private TextView mClock;
    private TextView mProgress;
    private TextView mBattery;
    private View mSeekBarPanel;
    private ImageView mAutoTransferPanel;
    private TextView mLeftText;
    private TextView mRightText;
    private ReversibleSeekBar mSeekBar;
    
    private SystemUiHelper mSystemUiHelper;
    private boolean mShowSystemUi = true;
    private int mSize;
    private int mCurrentIndex = 0;
    private int mLayoutMode;
    private int mPage = -1;
    
    // 自动播放相关
    private boolean autoTransferring = false;
    private ObjectAnimator mAutoTransferAnimator;
    private ScheduledExecutorService transferService = Executors.newSingleThreadScheduledExecutor();
    private final Handler transHandle = new Handler(Looper.getMainLooper());
    
    private boolean canFinish = true;
    
    public static void start(Context context, LocalGalleryInfo galleryInfo) {
        Intent intent = new Intent(context, GalleryActivity.class);
        intent.setAction(GalleryActivity.ACTION_DIR);
        intent.putExtra(GalleryActivity.KEY_FILENAME, galleryInfo.path);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("LocalGalleryViewer", "onCreate: 开始创建本地画廊查看器");
        
        if (Settings.getReadingFullscreen()) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        super.onCreate(savedInstanceState);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        // 获取画廊信息
        Intent intent = getIntent();
        if (intent != null) {
            mGalleryInfo = intent.getParcelableExtra(KEY_GALLERY_INFO);
        }
        
        Log.d("LocalGalleryViewer", "onCreate: 获取到的画廊信息 - " + (mGalleryInfo != null ? mGalleryInfo.getDisplayTitle() : "null"));
        if (mGalleryInfo != null) {
            Log.d("LocalGalleryViewer", "onCreate: 画廊路径 - " + mGalleryInfo.path);
            Log.d("LocalGalleryViewer", "onCreate: 画廊页数 - " + mGalleryInfo.pageCount);
            Log.d("LocalGalleryViewer", "onCreate: 画廊大小 - " + mGalleryInfo.size);
        }
        
        if (mGalleryInfo == null) {
            Log.e("LocalGalleryViewer", "onCreate: 画廊信息为null，结束活动");
            finish();
            return;
        }
        
        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
        onCreateView(savedInstanceState);
    }
    
    private void onInit() {
        // 初始化页面
        mPage = -1;
    }
    
    private void onRestore(Bundle savedInstanceState) {
        // 恢复状态
        mCurrentIndex = savedInstanceState.getInt("current_index", 0);
    }
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_index", mCurrentIndex);
    }
    
    private void onCreateView(@Nullable Bundle savedInstanceState) {
        Log.d("LocalGalleryViewer", "onCreateView: 开始创建视图");
        // 加载图片路径
        loadImagePaths();
        
        Log.d("LocalGalleryViewer", "onCreateView: 图片路径加载完成，数量 - " + (mImagePaths != null ? mImagePaths.size() : "null"));
        if (mImagePaths != null && !mImagePaths.isEmpty()) {
            for (int i = 0; i < Math.min(mImagePaths.size(), 5); i++) {
                Log.d("LocalGalleryViewer", "onCreateView: 图片路径[" + i + "] - " + mImagePaths.get(i));
            }
        }
        
        if (mImagePaths == null || mImagePaths.isEmpty()) {
            Log.e("LocalGalleryViewer", "onCreateView: 没有找到图片文件，显示错误信息");
            Toast.makeText(this, R.string.local_gallery_no_images, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 创建画廊提供者
        // mGalleryProvider = new LocalGalleryAdapter.LocalGalleryProvider(mImagePaths);
        // mGalleryProvider.start();

        // Get start page
        int startPage;
        if (savedInstanceState == null) {
            startPage = mPage >= 0 ? mPage : 0; // 本地画廊从第0页开始
        } else {
            startPage = mCurrentIndex;
        }
        
        Log.d("LocalGalleryViewer", "onCreateView: 开始页 - " + startPage);

        Log.d("LocalGalleryViewer", "onCreateView: 设置布局文件");
        setContentView(R.layout.activity_local_gallery_viewer);
        mGLRootView = (GLRootView) ViewUtils.$$(this, R.id.gl_root_view);
        Log.d("LocalGalleryViewer", "onCreateView: GLRootView - " + (mGLRootView != null ? "已创建" : "null"));
        
        Log.d("LocalGalleryViewer", "onCreateView: 创建LocalGalleryAdapter");
        mGalleryAdapter = new LocalGalleryAdapter(mGLRootView, mImagePaths);
        Log.d("LocalGalleryViewer", "onCreateView: LocalGalleryAdapter - " + (mGalleryAdapter != null ? "已创建" : "null"));
        
        Resources resources = getResources();
        Log.d("LocalGalleryViewer", "onCreateView: 开始创建GalleryView");
        mGalleryView = new GalleryView.Builder(this, mGalleryAdapter)
                .setLayoutMode(Settings.getReadingDirection())
                .setScaleMode(Settings.getPageScaling())
                .setStartPosition(Settings.getStartPosition())
                .setStartPage(startPage)
                .setBackgroundColor(AttrResources.getAttrColor(this, android.R.attr.colorBackground))
                .setEdgeColor(AttrResources.getAttrColor(this, R.attr.colorEdgeEffect) & 0xffffff | 0x33000000)
                .setPagerInterval(Settings.getShowPageInterval() ? resources.getDimensionPixelOffset(R.dimen.gallery_pager_interval) : 0)
                .setScrollInterval(Settings.getShowPageInterval() ? resources.getDimensionPixelOffset(R.dimen.gallery_scroll_interval) : 0)
                .setPageMinHeight(resources.getDimensionPixelOffset(R.dimen.gallery_page_min_height))
                .setPageInfoInterval(resources.getDimensionPixelOffset(R.dimen.gallery_page_info_interval))
                // 本地图片优化：最小化进度显示，避免不必要的加载动画
                .setProgressColor(AttrResources.getAttrColor(this, android.R.attr.colorBackground))
                .setProgressSize(1) // 最小进度条大小
                .setPageTextColor(AttrResources.getAttrColor(this, android.R.attr.textColorSecondary))
                .setPageTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_page_text_size))
                .setPageTextTypeface(Typeface.DEFAULT)
                .setErrorTextColor(resources.getColor(R.color.red_500, null))
                .setErrorTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_error_text_size))
                .setDefaultErrorString(resources.getString(R.string.error_unknown))
                .setEmptyString(resources.getString(R.string.error_empty))
                .build();
        Log.d("LocalGalleryViewer", "onCreateView: GalleryView - " + (mGalleryView != null ? "已创建" : "null"));
        
        Log.d("LocalGalleryViewer", "onCreateView: 设置GLRootView内容");
        mGLRootView.setContentPane(mGalleryView);
        mGLRootView.setOnGenericMotionListener(this::onGenericMotion);
        Log.d("LocalGalleryViewer", "onCreateView: GLRootView设置完成");
        
        // GLRootView会自动触发数据加载，不需要手动干预
        Log.d("LocalGalleryViewer", "onCreateView: 等待GLRootView自动初始化完成");
        // mGalleryProvider.setListener(mGalleryAdapter);
        // mGalleryProvider.setGLRoot(mGLRootView);

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
        mClock = (TextView) ViewUtils.$$(this, R.id.clock);
        mProgress = (TextView) ViewUtils.$$(this, R.id.progress);
        mBattery = (TextView) ViewUtils.$$(this, R.id.battery);
        mClock.setVisibility(Settings.getShowClock() ? View.VISIBLE : View.GONE);
        mProgress.setVisibility(Settings.getShowProgress() ? View.VISIBLE : View.GONE);
        mBattery.setVisibility(Settings.getShowBattery() ? View.VISIBLE : View.GONE);

        mSeekBarPanel = ViewUtils.$$(this, R.id.seek_bar_panel);
        mAutoTransferPanel = (ImageView) ViewUtils.$$(this, R.id.auto_transfer);
        mLeftText = (TextView) ViewUtils.$$(mSeekBarPanel, R.id.left);
        mRightText = (TextView) ViewUtils.$$(mSeekBarPanel, R.id.right);
        mSeekBar = (ReversibleSeekBar) ViewUtils.$$(mSeekBarPanel, R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mAutoTransferPanel.setOnClickListener(this::autoRead);

        mSize = mImagePaths != null ? mImagePaths.size() : 0;
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
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(mGalleryInfo.getDisplayTitle());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        Log.d("LocalGalleryViewer", "onCreateView: 视图创建完成");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d("LocalGalleryViewer", "onResume: 活动恢复");
        
        // 移除重新设置GLRootView内容 - 这会导致渲染线程错误
        // GLRootView的内容应该在初始化时设置一次
        Log.d("LocalGalleryViewer", "onResume: GLRootView已正确初始化");
        
        if (mGLRootView != null) {
            Log.d("LocalGalleryViewer", "onResume: GLRootView状态检查 - 已创建");
        }
    }
    
    private void loadImagePaths() {
        Log.d("LocalGalleryViewer", "loadImagePaths: 开始加载图片路径");
        mImagePaths = new ArrayList<>();
        
        if (mGalleryInfo.path == null) {
            Log.e("LocalGalleryViewer", "loadImagePaths: 画廊路径为null");
            return;
        }
        
        Log.d("LocalGalleryViewer", "loadImagePaths: 检查目录 - " + mGalleryInfo.path);
        File galleryDir = new File(mGalleryInfo.path);
        if (!galleryDir.exists()) {
            Log.e("LocalGalleryViewer", "loadImagePaths: 目录不存在 - " + mGalleryInfo.path);
            return;
        }
        
        if (!galleryDir.isDirectory()) {
            Log.e("LocalGalleryViewer", "loadImagePaths: 路径不是目录 - " + mGalleryInfo.path);
            return;
        }
        
        Log.d("LocalGalleryViewer", "loadImagePaths: 目录存在，开始列出文件");
        File[] files = galleryDir.listFiles();
        if (files == null) {
            Log.e("LocalGalleryViewer", "loadImagePaths: 无法列出文件，files为null");
            return;
        }
        
        Log.d("LocalGalleryViewer", "loadImagePaths: 找到 " + files.length + " 个文件");
        
        // 排序并过滤图片文件
        Arrays.sort(files, (f1, f2) -> {
            String n1 = f1.getName().toLowerCase();
            String n2 = f2.getName().toLowerCase();
            return n1.compareTo(n2);
        });
        
        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                Log.d("LocalGalleryViewer", "loadImagePaths: 检查文件 - " + name);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                    name.endsWith(".png") || name.endsWith(".gif") || 
                    name.endsWith(".webp")) {
                    mImagePaths.add(file.getAbsolutePath());
                    Log.d("LocalGalleryViewer", "loadImagePaths: 添加图片文件 - " + file.getAbsolutePath());
                } else {
                    Log.d("LocalGalleryViewer", "loadImagePaths: 跳过非图片文件 - " + name);
                }
            } else {
                Log.d("LocalGalleryViewer", "loadImagePaths: 跳过目录 - " + file.getName());
            }
        }
        
        Log.d("LocalGalleryViewer", "loadImagePaths: 最终找到 " + mImagePaths.size() + " 个图片文件");
    }
    
    private void updateSlider() {
        if (mSeekBarPanel == null || mSize <= 0) {
            return;
        }
        
        mLeftText.setText(String.valueOf(mCurrentIndex + 1));
        mRightText.setText(String.valueOf(mSize));
        mSeekBar.setMax(mSize - 1);
        mSeekBar.setProgress(mCurrentIndex);
        
        if (mProgress != null) {
            mProgress.setText((mCurrentIndex + 1) + " / " + mSize);
        }
    }
    
    private void autoRead(View view) {
        autoTransferring = !autoTransferring;
        if (mAutoTransferPanel == null) {
            return;
        }

        if (!autoTransferring) {
            mAutoTransferPanel.setImageResource(R.drawable.ic_start_play_24);
            transferService.shutdown();
        } else {
            mAutoTransferPanel.setImageResource(R.drawable.ic_pause_circle);
            if (transferService.isShutdown()) {
                transferService = Executors.newSingleThreadScheduledExecutor();
            }
            long initialDelay = Settings.getStartTransferTime();
            long waitTime = initialDelay * 2L;
            try {
                ((ScheduledExecutorService) transferService).scheduleWithFixedDelay(() -> transHandle.post(() -> {
                    if (mGalleryView == null) {
                        return;
                    }
                    if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
                        mGalleryView.pageLeft();
                    } else {
                        mGalleryView.pageRight();
                    }
                }), initialDelay, waitTime, TimeUnit.SECONDS);
            } catch (IllegalArgumentException ignore) {

            }
        }
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
                    if (scrollY > 0) {
                        mGalleryView.pageRight();
                    } else {
                        mGalleryView.pageLeft();
                    }
                }
                return true;
            }
        }
        return false;
    }
    
    // GalleryView.Listener implementation
    public void onCenterClick(int index) {
        // Toggle system UI
        if (mSystemUiHelper != null) {
            if (mShowSystemUi) {
                mSystemUiHelper.hide();
                mShowSystemUi = false;
            } else {
                mSystemUiHelper.show();
                mShowSystemUi = true;
            }
        }
    }
    
    public void onLeftClick(int index) {
        if (mGalleryView != null) {
            mGalleryView.pageLeft();
        }
    }
    
    public void onRightClick(int index) {
        if (mGalleryView != null) {
            mGalleryView.pageRight();
        }
    }
    
    public void onPageChanged(int index) {
        mCurrentIndex = index;
        updateSlider();
    }
    
    public void onLongPress(int index) {
        // Show menu
        showPageMenu(index);
    }
    
    private void showPageMenu(int index) {
        // 简单的提示，暂不实现复杂菜单
        Toast.makeText(this, "菜单功能待实现", Toast.LENGTH_SHORT).show();
    }
    
    // SeekBar.OnSeekBarChangeListener implementation
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && mGalleryView != null) {
            mGalleryView.setCurrentPage(progress);
        }
    }
    
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing
    }
    
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Do nothing
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止自动播放
        if (transferService != null && !transferService.isShutdown()) {
            transferService.shutdown();
        }
        
        // 清理画廊提供者
        // if (mGalleryProvider != null) {
        //     mGalleryProvider.stop();
        // }
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
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

package com.hippo.ehviewer.ui.scene.download;

import static com.hippo.ehviewer.spider.SpiderDen.getGalleryDownloadDir;
import static com.hippo.ehviewer.spider.SpiderInfo.getSpiderInfo;
import static com.hippo.ehviewer.ui.scene.download.part.DownloadAdapter.DRAG_ENABLE;
import static com.hippo.util.FileUtils.getFileName;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import java.util.List;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.text.Editable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.android.resource.AttrResources;
import com.hippo.app.CheckBoxDialogBuilder;
import com.hippo.drawable.AddDeleteDrawable;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.FastScroller;
import com.hippo.easyrecyclerview.HandlerDrawable;
import com.hippo.easyrecyclerview.MarginItemDecoration;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.callBack.DownloadSearchCallback;
import com.hippo.ehviewer.client.EhConfig;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.event.SomethingNeedRefresh;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.sync.DownloadListInfosExecutor;
import com.hippo.ehviewer.sync.DownloadSpiderInfoExecutor;
// removed unused background task imports
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.annotation.ViewLifeCircle;
import com.hippo.ehviewer.ui.scene.ToolbarScene;
import com.hippo.ehviewer.ui.scene.download.part.DownloadAdapter;
import com.hippo.ehviewer.ui.scene.download.part.MyPageChangeListener;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.ehviewer.ui.scene.download.part.DownloadCategoryTable;
import com.hippo.ehviewer.widget.AdvanceSearchTable;
import com.hippo.lib.yorozuya.AssertUtils;
import com.hippo.lib.yorozuya.ObjectUtils;
import com.hippo.lib.yorozuya.ViewUtils;
import com.hippo.lib.yorozuya.collect.LongList;
import com.hippo.ripple.Ripple;
import com.hippo.unifile.UniFile;
import com.hippo.util.DrawableManager;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.view.ViewTransition;
import com.hippo.widget.FabLayout;
import com.hippo.widget.ProgressView;
import com.hippo.widget.SearchBarMover;
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager;
import com.sxj.paginationlib.PaginationIndicator;
import com.hippo.ehviewer.ui.scene.download.part.MyPageChangeListener;
import com.hippo.ehviewer.ui.scene.download.part.DownloadAdapter;
import com.hippo.ehviewer.ui.scene.download.part.CheckboxAdapter;
import com.hippo.util.ExecutorManager;
import com.hippo.lib.yorozuya.SimpleHandler;
// ProgressDialogManager removed - using Toast notifications instead
import com.hippo.ehviewer.util.UiThreadHelper;
import com.hippo.ehviewer.download.DownloadInfoListener;
import com.hippo.ehviewer.task.BackgroundTask;
import com.hippo.ehviewer.task.TaskExecutor;
import com.hippo.ehviewer.task.impl.CompressSelectedGalleriesTask;
import com.hippo.ehviewer.task.impl.DeleteFilesTask;
import com.hippo.ehviewer.task.impl.DeleteRangeDownloadTask;
import com.hippo.ehviewer.task.impl.StartAllDownloadTask;
import com.hippo.ehviewer.task.impl.StartRangeDownloadTask;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 拖拽排序相关导入
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import android.graphics.drawable.NinePatchDrawable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DownloadsScene extends ToolbarScene
        implements DownloadInfoListener, DownloadSearchCallback,
        EasyRecyclerView.OnItemClickListener,
        EasyRecyclerView.OnItemLongClickListener,
        FabLayout.OnClickFabListener, FabLayout.OnExpandListener, FastScroller.OnDragHandlerListener, SearchBar.Helper, SearchBarMover.Helper, SearchBar.OnStateChangeListener, DownloadAdapter.DownloadAdapterCallback {

    private static final String TAG = DownloadsScene.class.getSimpleName();

    public static final String KEY_GID = "gid";

    public static final String KEY_ACTION = "action";
    private static final String KEY_LABEL = "label";

    public static final String ACTION_CLEAR_DOWNLOAD_SERVICE = "clear_download_service";

    public static final int LOCAL_GALLERY_INFO_CHANGE = 909;

    private static final long ANIMATE_TIME = 300L;

    @Nullable
    private AddDeleteDrawable mActionFabDrawable;


    /*---------------
         Whole life cycle
         ---------------*/
    @Nullable
    private DownloadManager mDownloadManager;
    @Nullable
    public String mLabel;
    @Nullable
    private String mCurrentLabel;
    @Nullable
    private List<DownloadInfo> mList;
    @Nullable
    private List<DownloadInfo> mBackList;
    @Nullable
    private ArrayAdapter<DownloadLabel> mLabelAdapter;

    /*---------------
     List pagination
     ---------------*/
    private int indexPage = 1;
    private int pageSize = 1;
    private boolean canPagination = true;
    private final int paginationSize = 500;

    // 排序和过滤相关变量
    private AlertDialog mSortFilterDialog;
    private CheckboxAdapter mCategoryAdapter;
    private CheckboxAdapter mStatusAdapter;
    private Spinner mCategorySpinner;
    private CheckboxAdapter mSortAdapter;
    private Set<Integer> mSelectedCategories = new HashSet<>();
    private Set<Integer> mSelectedStatuses = new HashSet<>();
    private Set<Integer> mSelectedSorts = new HashSet<>();
    @Nullable
    private Long mFilterTimeFrom;
    @Nullable
    private Long mFilterTimeTo;
    @Nullable
    private Long mFilterSizeFrom;
    @Nullable
    private Long mFilterSizeTo;
    private boolean mFilterDuplicateOnly = false;
    private String mFilterTimeFromInput = "";
    private String mFilterTimeToInput = "";
    private String mFilterSizeFromInput = "";
    private String mFilterSizeToInput = "";
    //    private final int paginationSize = 5;
    private final int[] perPageCountChoices = {50, 100, 200, 300, 500};
//    private final int[] perPageCountChoices = {1, 2, 3, 4, 5};

    private MyPageChangeListener myPageChangeListener;

    private final Map<Long, SpiderInfo> mSpiderInfoMap = new HashMap<>(64);
    
    // 缓存的 MainLooper Handler，避免重复创建
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    // 添加页码切换标志位，避免与进度更新冲突
    private volatile boolean isPageChanging = false;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private MyEasyRecyclerView mRecyclerView;
    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private FabLayout mFabLayout;
    @Nullable
    private RecyclerView.Adapter mAdapter;
    @Nullable
    private DownloadAdapter mOriginalAdapter;
    @Nullable
    private AutoStaggeredGridLayoutManager mLayoutManager;

    // 拖拽管理器
    @Nullable
    private RecyclerViewDragDropManager mDragDropManager;

    private ShowcaseView mShowcaseView;

    private ProgressView mProgressView;

    private AlertDialog mSearchDialog;
    private SearchBar mSearchBar;
    @Nullable
    private PaginationIndicator mPaginationIndicator;

    private DownloadLabelDraw downloadLabelDraw;
    @Nullable
    @ViewLifeCircle
    private SearchBarMover mSearchBarMover;
    private boolean mSearchMode = false;
    private boolean isFilteringOrSearching = false;  // 标记是否处于筛选或搜索状态
    public String searchKey = null;

    private int mInitPosition = -1;

    public boolean searching = false;
    private boolean doNotScroll = false;

    private boolean needInitPage = false;
    private boolean needInitPageSize = false;

    private int mSelectedCategory = EhUtils.ALL_CATEGORY;

    @NonNull
    private final ActivityResultLauncher<Intent> galleryActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::updateReadProcess
    );

    @NonNull
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::handleSelectedFile
    );

    @Override
    public int getNavCheckedItem() {
        return R.id.nav_downloads;
    }

    private boolean handleArguments(Bundle args) {
        if (null == args) {
            return false;
        }

        if (ACTION_CLEAR_DOWNLOAD_SERVICE.equals(args.getString(KEY_ACTION))) {
            DownloadService.Companion.clear();
        }

        long gid;
        if (null != mDownloadManager && -1L != (gid = args.getLong(KEY_GID, -1L))) {
            DownloadInfo info = mDownloadManager.getDownloadInfo(gid);
            if (null != info) {
                mLabel = info.getLabel();
                updateForLabel();
                updateView();

                // Get position
                if (null != mList) {
                    int position = mList.indexOf(info);
                    if (position >= 0 && null != mRecyclerView) {
                        initPage(position);
                    } else {
                        mInitPosition = position;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onNewArguments(@NonNull Bundle args) {
        handleArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getEHContext();
        AssertUtils.assertNotNull(context);
        mDownloadManager = EhApplication.getDownloadManager(context);
        mDownloadManager.addDownloadInfoListener(this);
        canPagination = Settings.getDownloadPagination();
        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mList = null;

        DownloadManager manager = mDownloadManager;
        if (null == manager) {
            Context context = getEHContext();
            if (null != context) {
                manager = EhApplication.getDownloadManager(context);
            }
        } else {
            mDownloadManager = null;
        }

        if (null != manager) {
            manager.removeDownloadInfoListener(this);
        } else {
            Log.e(TAG, "Can't removeDownloadInfoListener");
        }
        mActionFabDrawable = null;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateForLabel() {
        if (null == mDownloadManager) {
            return;
        }

        if (mLabel == null) {
            mList = mDownloadManager.getDefaultDownloadInfoList();
        } else {
            mList = mDownloadManager.getLabelDownloadInfoList(mLabel);
            if (mList == null) {
                mLabel = null;
                mList = mDownloadManager.getDefaultDownloadInfoList();
            }
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mBackList = mList;
        isFilteringOrSearching = false;  // 切换标签时退出筛选状态
//        filterByCategory();
        updateTitle();
        updatePaginationIndicator(true); // 标签变化时强制重新初始化
        Settings.putRecentDownloadLabel(mLabel);
        queryUnreadSpiderInfo();
    }

    private void updatePaginationIndicator() {
        updatePaginationIndicator(false);
    }
    
    private void updatePaginationIndicator(boolean forceReinit) {
        if (mPaginationIndicator == null || mList == null) {
            return;
        }
        
        // 如果处于筛选或搜索模式，使用当前结果列表大小；否则检查是否需要分页
        int currentListSize = mList.size();
        
        // 判断是否需要显示分页：列表大小要超过阈值且支持分页
        if (currentListSize < paginationSize || !canPagination) {
            mPaginationIndicator.setVisibility(View.GONE);
            return;
        }
        
        // 只在必要时才重新初始化
        if (forceReinit || needInitPageSize) {
            mPaginationIndicator.setVisibility(View.VISIBLE);
            needInitPageSize = false;
            // 使用当前结果列表的大小初始化分页，而非原始列表
            mPaginationIndicator.initPaginationIndicator(pageSize, perPageCountChoices, currentListSize, indexPage);
            
            // 只在第一次或强制重新初始化时设置监听器
            if (myPageChangeListener != null) {
                mPaginationIndicator.setListener(myPageChangeListener);
            }
        }
        
        // 同步分页监听器的状态
        if (myPageChangeListener != null) {
            myPageChangeListener.setIndexPage(indexPage);
            myPageChangeListener.setPageSize(pageSize);
            myPageChangeListener.setNeedInitPage(needInitPage);
            myPageChangeListener.setDoNotScroll(doNotScroll);
        }
    }

    @SuppressLint("StringFormatMatches")
    private void updateTitle() {
        try {
            setTitle(getString(R.string.scene_download_title_new,
                    mLabel != null ? mLabel : getString(R.string.default_download_label_name),
                    Integer.toString(mList == null ? 0 : mList.size())));
        } catch (Exception e) {
            Analytics.recordException(e);
            setTitle(getString(R.string.scene_download_title_new,
                    mLabel != null ? mLabel : getString(R.string.default_download_label_name)));
        }
    }

    private void onInit() {
        if (!handleArguments(getArguments())) {
            mLabel = Settings.getRecentDownloadLabel();
            updateForLabel();
        }
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mLabel = savedInstanceState.getString(KEY_LABEL);
        updateForLabel();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_LABEL, mLabel);
    }

    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
                              @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_download, container, false);

        Context context = getEHContext();
        assert context != null;

        mCategorySpinner = (Spinner) ViewUtils.$$(view, R.id.category_spinner);
        // Initialize category spinner
        List<String> categoryList = new ArrayList<>();
        categoryList.add(getString(R.string.category_all)); // Add "All" option
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.DOUJINSHI)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.MANGA)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.ARTIST_CG)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.GAME_CG)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.WESTERN)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.NON_H)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.IMAGE_SET)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.COSPLAY)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.ASIAN_PORN)).toUpperCase(Locale.ROOT));
        categoryList.add(Objects.requireNonNull(EhUtils.getCategory(EhConfig.MISC)).toUpperCase(Locale.ROOT));
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(categoryAdapter);
        mCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedCategory;
                switch (position) {
                    case 0:
                        selectedCategory = EhUtils.ALL_CATEGORY;
                        break;
                    case 1:
                        selectedCategory = EhConfig.DOUJINSHI;
                        break;
                    case 2:
                        selectedCategory = EhConfig.MANGA;
                        break;
                    case 3:
                        selectedCategory = EhConfig.ARTIST_CG;
                        break;
                    case 4:
                        selectedCategory = EhConfig.GAME_CG;
                        break;
                    case 5:
                        selectedCategory = EhConfig.WESTERN;
                        break;
                    case 6:
                        selectedCategory = EhConfig.NON_H;
                        break;
                    case 7:
                        selectedCategory = EhConfig.IMAGE_SET;
                        break;
                    case 8:
                        selectedCategory = EhConfig.COSPLAY;
                        break;
                    case 9:
                        selectedCategory = EhConfig.ASIAN_PORN;
                        break;
                    case 10:
                        selectedCategory = EhConfig.MISC;
                        break;
                    default:
                        selectedCategory = EhUtils.ALL_CATEGORY;
                        break;
                }
                if (selectedCategory != mSelectedCategory) {
                    mSelectedCategory = selectedCategory;
                    filterByCategory();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        // Set default selection
        mCategorySpinner.setSelection(0);

        mProgressView = (ProgressView) ViewUtils.$$(view, R.id.download_progress_view);
        View content = ViewUtils.$$(view, R.id.content);
        mRecyclerView = (MyEasyRecyclerView) ViewUtils.$$(content, R.id.recycler_view);
        FastScroller fastScroller = (FastScroller) ViewUtils.$$(content, R.id.fast_scroller);
        mFabLayout = (FabLayout) ViewUtils.$$(view, R.id.fab_layout);
        TextView tip = (TextView) ViewUtils.$$(view, R.id.tip);
        if (mPaginationIndicator != null) {
            needInitPage = true;
        }
        mPaginationIndicator = (PaginationIndicator) ViewUtils.$$(view, R.id.indicator);

        mPaginationIndicator.setPerPageCountChoices(perPageCountChoices, getPageSizePos(pageSize));

        mViewTransition = new ViewTransition(content, tip);

        Resources resources = context.getResources();

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_download);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);
        // 初始化拖拽管理器
        mDragDropManager = new RecyclerViewDragDropManager();
        try {
            mDragDropManager.setDraggingItemShadowDrawable(
                    (NinePatchDrawable) context.getResources().getDrawable(R.drawable.shadow_8dp));
        } catch (Exception e) {
            // 忽略硬件位图相关错误
            android.util.Log.w("DownloadsScene", "Error setting drag shadow: " + e.getMessage());
        }


        mOriginalAdapter = new DownloadAdapter(this, this);
        mOriginalAdapter.setHasStableIds(true);
        mAdapter = mDragDropManager.createWrappedAdapter(mOriginalAdapter); // 包装适配器以支持拖拽
        mDragDropManager.setCheckCanDropEnabled(false);
        mRecyclerView.setAdapter(mAdapter);

        // 初始化分页监听器
        myPageChangeListener = new MyPageChangeListener(indexPage, pageSize, needInitPage, doNotScroll, mOriginalAdapter, mRecyclerView);

        // 设置分页监听器的回调
        myPageChangeListener.setPageChangeCallback(new MyPageChangeListener.PageChangeCallback() {
            @Override
            public void onPageChanged(int newIndexPage) {
                indexPage = newIndexPage;
                // 设置页码切换标志
                isPageChanging = true;
                
                // 页码切换时，延迟更新列表以避免与进度更新冲突
                if (mAdapter != null) {
                    mMainHandler.postDelayed(() -> {
                        try {
                            mAdapter.notifyDataSetChanged();
                            // 重置页码切换标志
                            isPageChanging = false;
                        } catch (Exception e) {
                            android.util.Log.e("DownloadsScene", "Error updating adapter after page change: " + e.getMessage());
                            isPageChanging = false;
                        }
                    }, 150); // 延迟150ms执行，确保页码切换完成
                }
            }

            @Override
            public void onPageSizeChanged(int newPageSize) {
                pageSize = newPageSize;
            }
        });
        mLayoutManager = new AutoStaggeredGridLayoutManager(0, StaggeredGridLayoutManager.VERTICAL);
        mLayoutManager.setColumnSize(resources.getDimensionPixelOffset(Settings.getDetailSizeResId()));
        mLayoutManager.setStrategy(AutoStaggeredGridLayoutManager.STRATEGY_MIN_SIZE);

        // 设置拖拽动画
        final GeneralItemAnimator animator = new DraggableItemAnimator();
        mRecyclerView.setItemAnimator(animator);

        mRecyclerView.setItemViewCacheSize(100);
        try {
            mRecyclerView.setDrawingCacheEnabled(true);
            mRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        } catch (Exception e) {
            // 忽略硬件位图相关错误
            android.util.Log.w("DownloadsScene", "Error setting drawing cache: " + e.getMessage());
        }
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, androidx.appcompat.R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        mRecyclerView.setDrawSelectorOnTop(true);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setOnItemClickListener(this);
        mRecyclerView.setOnItemLongClickListener(this);
        mRecyclerView.setChoiceMode(MyEasyRecyclerView.CHOICE_MODE_MULTIPLE_CUSTOM);
        mRecyclerView.setCustomCheckedListener(new DownloadChoiceListener());
//        mRecyclerView.setOnGenericMotionListener(this::onGenericMotion);
        // Cancel change animation
        RecyclerView.ItemAnimator itemAnimator = mRecyclerView.getItemAnimator();
        if (itemAnimator instanceof GeneralItemAnimator) {
            ((GeneralItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
        int interval = resources.getDimensionPixelOffset(R.dimen.gallery_list_interval);
        int paddingH = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_h);
        int paddingV = resources.getDimensionPixelOffset(R.dimen.gallery_list_margin_v);
        MarginItemDecoration decoration = new MarginItemDecoration(interval, paddingH, paddingV, paddingH, paddingV);
        mRecyclerView.addItemDecoration(decoration);
        decoration.applyPaddings(mRecyclerView);

        // 将拖拽管理器附加到RecyclerView
        if (mDragDropManager != null) {
            try {
                mDragDropManager.attachRecyclerView(mRecyclerView);
            } catch (Exception e) {
                // 忽略硬件位图相关错误
                android.util.Log.w("DownloadsScene", "Error attaching drag manager: " + e.getMessage());
            }
        }

        if (mInitPosition >= 0 && indexPage != 1) {
            initPage(mInitPosition);
            mRecyclerView.scrollToPosition(listIndexInPage(mInitPosition));
            mInitPosition = -1;
        }

        fastScroller.attachToRecyclerView(mRecyclerView);
        HandlerDrawable handlerDrawable = new HandlerDrawable();
        handlerDrawable.setColor(AttrResources.getAttrColor(context, R.attr.widgetColorThemeAccent));
        fastScroller.setHandlerDrawable(handlerDrawable);
        fastScroller.setOnDragHandlerListener(this);

        mFabLayout.setExpanded(false, true);
        mFabLayout.setHidePrimaryFab(false);
        mFabLayout.setAutoCancel(false);
        mFabLayout.setOnClickFabListener(this);
        mFabLayout.setOnExpandListener(this);
        mFabLayout.setShowFabFunctionName(Settings.getShowFabFunctionName());
        mActionFabDrawable = new AddDeleteDrawable(context, resources.getColor(R.color.primary_drawable_dark, null));
        mFabLayout.getPrimaryFab().setImageDrawable(mActionFabDrawable);
        mFabLayout.getPrimaryFab().setContentDescription(getString(R.string.fab_action_menu));
        
        // 为FloatingActionButton添加标签
        setupFabLabels();
        
        FloatingActionButton fab = mFabLayout.getSecondaryFabAt(6);
        if (DRAG_ENABLE) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.v_mobile_hand_left_x24, context.getTheme()));
        } else {
            fab.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.v_mobile_hand_left_off_x24, context.getTheme()));
        }
        addAboveSnackView(mFabLayout);

        updateView();

        guide();
        updatePaginationIndicator(true); // 首次初始化时强制重新初始化
        return view;
    }

    private void guide() {
        if (Settings.getGuideDownloadThumb() && null != mRecyclerView) {
            mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Settings.getGuideDownloadThumb()) {
                        guideDownloadThumb();
                    }
                    if (null != mRecyclerView) {
                        ViewUtils.removeOnGlobalLayoutListener(mRecyclerView.getViewTreeObserver(), this);
                    }
                }
            });
        } else {
            guideDownloadLabels();
        }
    }

    private void guideDownloadThumb() {
        MainActivity activity = getActivity2();
        if (null == activity || !Settings.getGuideDownloadThumb() || null == mLayoutManager || null == mRecyclerView) {
            guideDownloadLabels();
            return;
        }
        int position = mLayoutManager.findFirstCompletelyVisibleItemPositions(null)[0];
        if (position < 0) {
            guideDownloadLabels();
            return;
        }
        RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (null == holder) {
            guideDownloadLabels();
            return;
        }

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new ViewTarget(((DownloadAdapter.DownloadHolder) holder).thumb))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_thumb_title)
                .setContentText(R.string.guide_download_thumb_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.putGuideDownloadThumb(false);
                        guideDownloadLabels();
                    }
                }).build();
    }

    private void guideDownloadLabels() {
        MainActivity activity = getActivity2();
        if (null == activity || !Settings.getGuideDownloadLabels()) {
            return;
        }

        Display display = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);

        mShowcaseView = new ShowcaseView.Builder(activity)
                .withMaterialShowcase()
                .setStyle(R.style.Guide)
                .setTarget(new PointTarget(point.x, point.y / 3))
                .blockAllTouches()
                .setContentTitle(R.string.guide_download_labels_title)
                .setContentText(R.string.guide_download_labels_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.puttGuideDownloadLabels(false);
                        openDrawer(Gravity.RIGHT);
                    }
                }).build();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFabLayout != null) {
            mFabLayout.setShowFabFunctionName(Settings.getShowFabFunctionName());
        }
    }

    private void startAllDownloads() {
        Activity activity = getActivity2();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        
        StartAllDownloadTask task = new StartAllDownloadTask(activity);
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        Toast.makeText(activity, R.string.start_all_download, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateTitle();
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView);
            mShowcaseView = null;
        }
        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout);
            mFabLayout = null;
        }

        mRecyclerView = null;
        mViewTransition = null;
        mAdapter = null;
        mOriginalAdapter = null;
        mLayoutManager = null;
        mDragDropManager = null;
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onNavigationClick(View view) {
        onBackPressed();
    }

    @Override
    public int getMenuResId() {
        return R.menu.scene_download;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Skip when in choice mode
        Activity activity = getActivity2();
        if (null == activity || null == mRecyclerView || mRecyclerView.isInCustomChoice()) {
            return false;
        }

        int id = item.getItemId();
        switch (id) {
            case R.id.action_start_all: {
                if (mDownloadManager == null) {
                    return false;
                }
                
                DownloadService.startAllDownloads(activity);
                return true;
            }
            case R.id.action_stop_all: {
                if (activity != null) {
                    DownloadService.stopAllDownloads(activity);
                }
                return true;
            }
            case R.id.action_reset_reading_progress: {
                Context context = getEHContext();
                if (context == null) {
                    return false;
                }
                if (searching) {
                    UiThreadHelper.showToastSafely(context, R.string.download_searching, Toast.LENGTH_LONG);
                    return true;
                }
                new AlertDialog.Builder(context)
                        .setMessage(R.string.reset_reading_progress_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (mDownloadManager != null) {
                                mDownloadManager.resetAllReadingProgress();
                            }
                        }).show();
                return true;
            }
            case R.id.search_download_gallery: {
                Context context = getEHContext();
                if (context == null) {
                    return false;
                }
                gotoSearch(context);
                return true;
            }
            case R.id.all:
            case R.id.sort_by_default:
            case R.id.download_done:
            case R.id.not_started:
            case R.id.waiting:
            case R.id.downloading:
            case R.id.failed:
            case R.id.sort_by_gallery_id_asc:
            case R.id.sort_by_gallery_id_desc:
            case R.id.sort_by_create_time_asc:
            case R.id.sort_by_create_time_desc:
            case R.id.sort_by_rating_asc:
            case R.id.sort_by_rating_desc:
            case R.id.sort_by_name_asc:
            case R.id.sort_by_name_desc:
            case R.id.sort_by_file_size_asc:
            case R.id.sort_by_file_size_desc:
            case R.id.all_kind:
            case R.id.misc:
            case R.id.doujinshi:
            case R.id.manga:
            case R.id.artist_cg:
            case R.id.game_cg:
            case R.id.image_set:
            case R.id.cosplay:
            case R.id.asian_porn:
            case R.id.non_h:
            case R.id.western:
            case R.id.unknown:
                gotoFilterAndSort(id);
            case R.id.sort_download_list: {
                // 不再显示排序窗口，直接使用菜单中的排序选项
                return true;
            }
            case R.id.advanced_filter: {
                Log.d("DownloadsScene", "onOptionsItemSelected: 高级过滤按钮被点");
                showSortFilterDialog();
                return true;
            }
            case R.id.import_local_archive:
                importLocalArchive();
                return true;
//            case R.id.misc:
//            case R.id.doujinshi:
//            case R.id.manga:
//            case R.id.artist_cg:
//            case R.id.game_cg:
//            case R.id.image_set:
//            case R.id.cosplay:
//            case R.id.asian_porn:
//            case R.id.non_h:
//            case R.id.western:
//            case R.id.unknown:
//
//                return true;
        }
        return false;
    }

    private void gotoSearch(Context context) {
        if (mSearchDialog != null && mSearchDialog.isShowing()) {
            mSearchDialog.show();
            return;
        }
        if (mSearchDialog != null) {
            mSearchDialog.dismiss();
            mSearchDialog = null;
        }
        
        // 创建增强的搜索对话框
        View dialogView = LayoutInflater.from(context).inflate(R.layout.download_search_dialog_v2, null);
        
        // 获取各个组件
        SearchBar searchBar = dialogView.findViewById(R.id.download_search_bar);
        mSearchBar = searchBar; // 供回调使用，避免空指针
        AdvanceSearchTable advanceSearchTable = dialogView.findViewById(R.id.advance_search_table);
        DownloadCategoryTable categoryTable = dialogView.findViewById(R.id.category_table);
        RecyclerView searchSortRecyclerView = dialogView.findViewById(R.id.search_sort_recycler_view);
        View advanceSearchToggleRow = dialogView.findViewById(R.id.advance_search_toggle_row);
        TextView advanceSearchToggleIcon = dialogView.findViewById(R.id.advance_search_toggle_icon);
        View advanceSearchContentContainer = dialogView.findViewById(R.id.advance_search_content_container);
        View categoryFilterToggleRow = dialogView.findViewById(R.id.category_filter_toggle_row);
        TextView categoryFilterToggleIcon = dialogView.findViewById(R.id.category_filter_toggle_icon);
        View categoryFilterContentContainer = dialogView.findViewById(R.id.category_filter_content_container);
        Button resetButton = dialogView.findViewById(R.id.reset_button);
        Button searchButton = dialogView.findViewById(R.id.search_button);

        // 高级搜索选项和分类过滤默认折叠
        setSectionExpanded(advanceSearchContentContainer, advanceSearchToggleIcon, false);
        setSectionExpanded(categoryFilterContentContainer, categoryFilterToggleIcon, false);
        advanceSearchToggleRow.setOnClickListener(v -> toggleSection(advanceSearchContentContainer, advanceSearchToggleIcon));
        categoryFilterToggleRow.setOnClickListener(v -> toggleSection(categoryFilterContentContainer, categoryFilterToggleIcon));

        int spanCount = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
        searchSortRecyclerView.setLayoutManager(new GridLayoutManager(context, spanCount));
        searchSortRecyclerView.setNestedScrollingEnabled(false);

        List<String> sortItems = new ArrayList<>();
        List<Integer> sortIds = new ArrayList<>();
        sortItems.add(getString(R.string.default_sort));
        sortIds.add(R.id.sort_by_default);
        sortItems.add(getString(R.string.sort_by_gallery_id_asc));
        sortIds.add(R.id.sort_by_gallery_id_asc);
        sortItems.add(getString(R.string.sort_by_gallery_id_desc));
        sortIds.add(R.id.sort_by_gallery_id_desc);
        sortItems.add(getString(R.string.sort_by_create_time_asc));
        sortIds.add(R.id.sort_by_create_time_asc);
        sortItems.add(getString(R.string.sort_by_create_time_desc));
        sortIds.add(R.id.sort_by_create_time_desc);
        sortItems.add(getString(R.string.sort_by_rating_asc));
        sortIds.add(R.id.sort_by_rating_asc);
        sortItems.add(getString(R.string.sort_by_rating_desc));
        sortIds.add(R.id.sort_by_rating_desc);
        sortItems.add(getString(R.string.sort_by_name_asc));
        sortIds.add(R.id.sort_by_name_asc);
        sortItems.add(getString(R.string.sort_by_name_desc));
        sortIds.add(R.id.sort_by_name_desc);
        sortItems.add(getString(R.string.sort_by_file_size_asc));
        sortIds.add(R.id.sort_by_file_size_asc);
        sortItems.add(getString(R.string.sort_by_file_size_desc));
        sortIds.add(R.id.sort_by_file_size_desc);

        CheckboxAdapter searchSortAdapter = new CheckboxAdapter(sortItems, sortIds);
        searchSortAdapter.setMutuallyExclusive(true);
        if (mSelectedSorts.isEmpty()) {
            mSelectedSorts.add(R.id.sort_by_default);
        }
        searchSortRecyclerView.setAdapter(searchSortAdapter);
        dialogView.post(() -> searchSortAdapter.setSelectedItems(mSelectedSorts));
        searchSortAdapter.setOnSelectionChangedListener(selectedItems -> {
            mSelectedSorts.clear();
            mSelectedSorts.addAll(selectedItems);
            if (mSelectedSorts.isEmpty()) {
                mSelectedSorts.add(R.id.sort_by_default);
                dialogView.post(() -> searchSortAdapter.setSelectedItems(mSelectedSorts));
            }
        });
        
        // 设置SearchBar
        searchBar.setHelper(this);
        searchBar.setIsComeFromDownload(true);
        searchBar.setEditTextHint(R.string.download_search_hint);
        searchBar.setText(searchKey);
        if (searchKey != null && !searchKey.isEmpty()) {
            searchBar.setTitle(searchKey);
            searchBar.cursorToEnd();
        } else {
            searchBar.setTitle(R.string.download_search_hint);
        }
        searchBar.setRightDrawable(DrawableManager.getVectorDrawable(context, R.drawable.v_magnify_x24));
        
        // 设置SearchBar为搜索状态，但不立即显示建议列表
        searchBar.setState(SearchBar.STATE_SEARCH, false);
        
        // 确保EditText可以获取焦点和点击
        searchBar.mEditText.setFocusable(true);
        searchBar.mEditText.setFocusableInTouchMode(true);
        searchBar.mEditText.setClickable(true);
        
        // 设置默认搜索选项
        advanceSearchTable.setAdvanceSearch(AdvanceSearchTable.SNAME | AdvanceSearchTable.STAGS);
        
        // 设置默认分类为全部- 确保所有按钮都是亮起的
        Set<Integer> defaultCategories = new HashSet<>();
        defaultCategories.add(EhUtils.ALL_CATEGORY);
        categoryTable.setSelectedCategories(defaultCategories);
        
        resetButton.setOnClickListener(v -> {
            searchBar.setText("");
            searchBar.setTitle(R.string.download_search_hint);
            advanceSearchTable.setAdvanceSearch(AdvanceSearchTable.SNAME | AdvanceSearchTable.STAGS);

            Set<Integer> resetCategories = new HashSet<>();
            resetCategories.add(EhUtils.ALL_CATEGORY);
            categoryTable.setSelectedCategories(resetCategories);

            mSelectedSorts.clear();
            mSelectedSorts.add(R.id.sort_by_default);
            dialogView.post(() -> searchSortAdapter.setSelectedItems(mSelectedSorts));
            searchBar.hideSuggestionsList();
        });

        searchButton.setOnClickListener(v -> {
            Editable editable = searchBar.mEditText.getText();
            searchKey = editable != null ? editable.toString() : null;

            int searchOption = advanceSearchTable.getAdvanceSearch();
            Set<Integer> selectedCategories = categoryTable.getSelectedCategories();
            int selectedSort = mSelectedSorts.isEmpty() ? R.id.sort_by_default : mSelectedSorts.iterator().next();

            searchBar.hideSuggestionsList();
            performAdvancedSearch(searchKey, searchOption, selectedCategories, selectedSort);
            mSearchDialog.dismiss();
        });

        mSearchDialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        mSearchDialog.setOnDismissListener(dialog -> {
            if (searchBar != null) {
                searchBar.hideSuggestionsList();
            }
            onSearchDialogDismiss(dialog);
        });
        mSearchDialog.show();
    }
    
    // 新增方法：执行高级搜索
    private void performAdvancedSearch(String keyword, int searchOption, Set<Integer> categories, int sortId) {
        Log.d("DownloadsScene", "performAdvancedSearch: keyword=" + keyword + ", searchOption=" + searchOption + ", categories=" + categories + ", sortId=" + sortId);
        
        isFilteringOrSearching = true;  // 标记进入搜索状态
        mProgressView.setVisibility(View.VISIBLE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.GONE);
        }
        
        // 创建执行器并执行搜索
        DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mBackList, mDownloadManager);
        executor.setDownloadSearchingListener(this);
        executor.executeAdvancedSearch(keyword, searchOption, categories, sortId);
    }

    private void onSearchDialogDismiss(DialogInterface dialog) {
        mSearchMode = false;
        mSearchDialog = null;
        mSearchBar = null; // 释放引用，避免后续回调访问空对象
    }

    private void setSectionExpanded(@NonNull View sectionContent, @NonNull TextView indicatorView, boolean expanded) {
        sectionContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        indicatorView.setText(expanded ? "-" : "+");
    }

    private void toggleSection(@NonNull View sectionContent, @NonNull TextView indicatorView) {
        boolean shouldExpand = sectionContent.getVisibility() != View.VISIBLE;
        setSectionExpanded(sectionContent, indicatorView, shouldExpand);
    }

    private void enterSearchMode(boolean animation) {
        if (mSearchMode || mSearchBar == null || mSearchBarMover == null) {
            return;
        }
        mSearchMode = true;
        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);

        mSearchBarMover.returnSearchBarPosition(animation);

    }

    public void updateView() {
        if (mViewTransition != null) {
            if (mList == null || mList.size() == 0) {
                mViewTransition.showView(1);
            } else {
                mViewTransition.showView(0);
            }
        }
    }

    @Override
    public View onCreateDrawerView(LayoutInflater inflater,
                                   @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (downloadLabelDraw == null) {
            downloadLabelDraw = new DownloadLabelDraw(inflater, container, this);
        }

        return downloadLabelDraw.createView();
    }

    @Override
    public void onBackPressed() {
        if (null != mShowcaseView) {
            return;
        }

        if (mRecyclerView != null && mRecyclerView.isInCustomChoice()) {
            mRecyclerView.outOfCustomChoiceMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
    }

    @Override
    public void onEndDragHandler() {
        // Restore right drawer
        if (null != mRecyclerView && !mRecyclerView.isInCustomChoice()) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        Activity activity = getActivity2();
        MyEasyRecyclerView recyclerView = mRecyclerView;
        if (null == activity || null == recyclerView) {
            return false;
        }

        if (recyclerView.isInCustomChoice()) {
            recyclerView.toggleItemChecked(position);
            return true;
        } else {
            List<DownloadInfo> list = mList;
            if (list == null) {
                return false;
            }
            if (position < 0 || position >= list.size()) {
                return false;
            }

            DownloadInfo downloadInfo = list.get(positionInList(position));
            Intent intent = new Intent(activity, GalleryActivity.class);
            // Check if this is an imported archive
            if (downloadInfo.archiveUri != null && downloadInfo.archiveUri.startsWith("content://")) {
                // This is an imported archive, ensure URI permission is available
                Uri archiveUri = Uri.parse(downloadInfo.archiveUri);
                try {
                    // Test if we can access the URI
                    try (InputStream testStream = getEHContext().getContentResolver().openInputStream(archiveUri)) {
                        if (testStream == null) {
                            UiThreadHelper.showToastSafely(getEHContext(), R.string.archive_not_accessible, Toast.LENGTH_SHORT);
                            return true;
                        }
                    }
                } catch (SecurityException e) {
                    // Try to restore permission
                    try {
                        getEHContext().getContentResolver().takePersistableUriPermission(archiveUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ex) {
                        UiThreadHelper.showToastSafely(getEHContext(), R.string.archive_permission_lost, Toast.LENGTH_LONG);
                        Analytics.recordException(ex);
                        return true;
                    }
                } catch (Exception e) {
                    UiThreadHelper.showToastSafely(getEHContext(), R.string.archive_not_accessible, Toast.LENGTH_SHORT);
                    return true;
                }
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(archiveUri);
            } else {
                // This is a normal download, use ACTION_EH
                intent.setAction(GalleryActivity.ACTION_EH);
                intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, downloadInfo);
            }
//            startActivity(intent);
            galleryActivityLauncher.launch(intent);
            return true;
        }
    }

    @Override
    public boolean onItemLongClick(EasyRecyclerView parent, View view, int position, long id) {
        MyEasyRecyclerView recyclerView = mRecyclerView;
        if (recyclerView == null) {
            return false;
        }

        if (!recyclerView.isInCustomChoice()) {
            recyclerView.intoCustomChoiceMode();
        }
        recyclerView.toggleItemChecked(position);

        return true;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onExpand(boolean expanded) {
        if (null == mActionFabDrawable) {
            return;
        }

        if (expanded) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
            mActionFabDrawable.setDelete(ANIMATE_TIME);
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
            mActionFabDrawable.setAdd(ANIMATE_TIME);
        }
    }

    @Override
    public void onClickPrimaryFab(FabLayout view, FloatingActionButton fab) {
        if (mRecyclerView != null && mRecyclerView.isInCustomChoice()) {
            mRecyclerView.outOfCustomChoiceMode();
            return;
        }
        if (mRecyclerView != null && !mRecyclerView.isInCustomChoice()) {
            mRecyclerView.intoCustomChoiceMode();
            return;
        }
        view.toggle();
    }

    @Override
    public void onClickSecondaryFab(FabLayout view, FloatingActionButton fab, int position) {
        Context context = getEHContext();
        Activity activity = getActivity2();
        MyEasyRecyclerView recyclerView = mRecyclerView;
        if (null == context || null == activity || null == recyclerView) {
            return;
        }

        if (0 == position) {
            recyclerView.checkAll();
        } else {
            List<DownloadInfo> list = mList;
            if (list == null) {
                return;
            }

            LongList gidList = null;
            List<DownloadInfo> downloadInfoList = null;
            boolean collectGid = position == 1 || position == 2 || position == 3; // Start, Stop, Delete
            boolean collectDownloadInfo = position == 3 || position == 4 || position == 7; // Delete or Move or Zip
            if (collectGid) {
                gidList = new LongList();
            }
            if (collectDownloadInfo) {
                downloadInfoList = new LinkedList<>();
            }

            SparseBooleanArray stateArray = recyclerView.getCheckedItemPositions();
            for (int i = 0, n = stateArray.size(); i < n; i++) {
                if (stateArray.valueAt(i)) {
                    DownloadInfo info = list.get(positionInList(stateArray.keyAt(i)));
                    if (collectDownloadInfo) {
                        downloadInfoList.add(info);
                    }
                    if (collectGid) {
                        gidList.add(info.gid);
                    }
                }
            }

            switch (position) {
                case 1: { // Start
                    if (gidList.isEmpty()) {
                        break;
                    }
                    // 使用后台任务处理多选下载，避免界面卡顿
                    StartRangeDownloadTask task = new StartRangeDownloadTask(activity, gidList);
                    TaskExecutor.getInstance().execute(task);
                    Toast.makeText(context, R.string.background_task_submitted, Toast.LENGTH_SHORT).show();
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode();
                    break;
                }
                case 2: { // Stop
                    if (gidList.isEmpty()) {
                        break;
                    }
                    if (null != mDownloadManager) {
                        mDownloadManager.stopRangeDownload(gidList);
                    }
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode();
                    break;
                }
                case 3: { // Delete
                    if (downloadInfoList.isEmpty()) {
                        break;
                    }
                    CheckBoxDialogBuilder builder = new CheckBoxDialogBuilder(context,
                            getString(R.string.download_remove_dialog_message_2, gidList.size()),
                            getString(R.string.download_remove_dialog_check_text),
                            Settings.getRemoveImageFiles());
                    DeleteRangeDialogHelper helper = new DeleteRangeDialogHelper(
                            downloadInfoList, gidList, builder);
                    builder.setTitle(R.string.download_remove_dialog_title)
                            .setPositiveButton(android.R.string.ok, helper)
                            .show();
                    break;
                }
                case 4: {// Move
                    if (downloadInfoList.isEmpty()) {
                        break;
                    }
                    List<DownloadLabel> labelRawList = EhApplication.getDownloadManager(context).getLabelList();
                    List<String> labelList = new ArrayList<>(labelRawList.size() + 1);
                    labelList.add(getString(R.string.default_download_label_name));
                    for (int i = 0, n = labelRawList.size(); i < n; i++) {
                        labelList.add(labelRawList.get(i).getLabel());
                    }
                    String[] labels = labelList.toArray(new String[labelList.size()]);

                    MoveDialogHelper helper = new MoveDialogHelper(labels, downloadInfoList);

                    new AlertDialog.Builder(context)
                            .setTitle(R.string.download_move_dialog_title)
                            .setItems(labels, helper)
                            .show();
                    break;
                }
                case 5: // Random Play
                    if (mList == null || mList.isEmpty()) {
                        return;
                    }
                    onClickPrimaryFab(mFabLayout, null);
                    viewRandom();
                    break;
                case 6: // Drap
                    setDragEnable(fab);
                    break;
                case 7: // Zip
                    compressSelectedGalleries(downloadInfoList);
                    break;
                case 8: //Refresh
                    refreshCurrentPage();
                    break;
            }
        }
    }

    private void setDragEnable(FloatingActionButton fab) {
        DRAG_ENABLE = !DRAG_ENABLE;
        Settings.setDragDownloadGallery(DRAG_ENABLE);
        Context context = getEHContext();
        if (null == context) return;
        if (DRAG_ENABLE) {
            fab.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.v_mobile_hand_left_x24, context.getTheme()));
        } else {
            fab.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.v_mobile_hand_left_off_x24, context.getTheme()));
        }
//        mDragDropManager.cancelDrag(dragEnable);
    }

    private void setupFabLabels() {
        Context context = getEHContext();
        if (null == context || null == mFabLayout) {
            return;
        }
        
        // 为每个SecondaryFab添加标签
        String[] labels = {
            getString(R.string.select_all),
            getString(R.string.start_all_download),
            getString(R.string.pause_all_download),
            getString(R.string.delete_download),
            getString(R.string.move_download),
            getString(R.string.random_download),
            getString(R.string.drag_mode),
            getString(R.string.compress_selected_galleries),
            getString(R.string.refresh_current_page)
        };
        
        for (int i = 0; i < mFabLayout.getSecondaryFabCount() && i < labels.length; i++) {
            FloatingActionButton fab = mFabLayout.getSecondaryFabAt(i);
            if (fab != null) {
                // 设置内容描述作为标签
                fab.setContentDescription(labels[i]);
            }
        }
    }

    private void refreshCurrentPage() {
        if (mDownloadManager != null) {
            // 重新加载当前页面的数据
            updateView();
            showTip(R.string.refreshed, LENGTH_SHORT);
        }
    }

    private void refreshDownloadListAfterDelete() {
        // 依据当前筛搜索状态刷新列表
        if (searchKey != null && !searchKey.isEmpty()) {
            startSearching();
        } else if (isFilteringOrSearching) {
            applySortAndFilter();
        } else {
            updateForLabel();
        }

        updateView();
    }

    private void viewRandom() {
        List<DownloadInfo> list = mList;
        if (list == null) {
            return;
        }
        int position = (int) (Math.random() * list.size());
        if (position < 0 || position >= list.size()) {
            return;
        }
        Activity activity = getActivity2();
        if (null == activity || null == mRecyclerView) {
            return;
        }

        Intent intent = new Intent(activity, GalleryActivity.class);
        intent.setAction(GalleryActivity.ACTION_EH);
        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, list.get(position));
        galleryActivityLauncher.launch(intent);
    }

    private void compressSelectedGalleries(List<DownloadInfo> selectedList) {
        if (selectedList == null || selectedList.isEmpty()) {
            showTip(R.string.empty_select_download_info, LENGTH_SHORT);
            return;
        }

        Context context = getEHContext();
        if (context == null) {
            return;
        }

        Toast.makeText(context, R.string.compress_selected_galleries, Toast.LENGTH_SHORT).show();

        CompressSelectedGalleriesTask task = new CompressSelectedGalleriesTask(context, selectedList);
        task.setProgressListener(new BackgroundTask.ProgressListener() {
            @Override
            public void onProgressChanged(int progress, String detail) {
                // Progress updates handled via notification, no UI update needed here
            }

            @Override
            public void onProgressChanged(int current, int total, String detail) {
                // Progress updates handled via notification, no UI update needed here
            }

            @Override
            public void onCompleted() {
                List<String> outFiles = task.getOutputFileNames();
                String result = outFiles.isEmpty() ? "" : TextUtils.join(", ", outFiles);
                String message = result.isEmpty() ? getString(R.string.compress_success_no_output) : getString(R.string.compress_success_multi, result);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(context, getString(R.string.compress_failed) + ": " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
    }

    private void addUniFileToZip(UniFile uniFile, String basePath, ZipOutputStream zos, Set<String> addedEntries) throws IOException {
        if (uniFile.isDirectory()) {
            if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            if (addedEntries.contains(basePath)) {
                Log.i(TAG, "Skip duplicate directory entry: " + basePath);
            } else {
                zos.putNextEntry(new ZipEntry(basePath));
                zos.closeEntry();
                addedEntries.add(basePath);
            }

            UniFile[] children = uniFile.listFiles();
            if (children != null) {
                for (UniFile child : children) {
                    String childPath = basePath + sanitizeFileName(child.getName());
                    try {
                        addUniFileToZip(child, childPath, zos, addedEntries);
                    } catch (IOException e) {
                        // Log and continue with remaining files
                        Log.e(TAG, "Failed to add child " + childPath + " to zip", e);
                    }
                }
            }
        } else if (uniFile.isFile()) {
            String entryName = basePath;
            if (entryName == null || entryName.isEmpty()) {
                entryName = sanitizeFileName(uniFile.getName());
            }
            if (addedEntries.contains(entryName)) {
                Log.i(TAG, "Skip duplicate file entry: " + entryName);
                return;
            }

            zos.putNextEntry(new ZipEntry(entryName));
            InputStream is = uniFile.openInputStream();
            if (is != null) {
                try (BufferedInputStream bis = new BufferedInputStream(is)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, count);
                    }
                } catch (IOException e) {
                    String fileName = uniFile.getName() != null ? uniFile.getName() : "unknown";
                    Log.e(TAG, "Error reading file " + fileName, e);
                    zos.closeEntry();
                    throw e;
                }
            }
            zos.closeEntry();
            addedEntries.add(entryName);
        }
    }

    private String sanitizeFileName(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public void onAdd(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        if (mList != list) {
            // 如果列表不匹配，尝试在当前列表中查找是否已存在该项目
            boolean found = false;
            for (int i = 0; i < mList.size(); i++) {
                DownloadInfo item = mList.get(i);
                if (item.gid == info.gid) {
                    // 项目已存在，更新信息
                    item.title = info.title;
                    item.finished = info.finished;
                    item.downloaded = info.downloaded;
                    item.total = info.total;
                    item.legacy = info.legacy;
                    item.state = info.state;
                    item.speed = info.speed;
                    item.remaining = info.remaining;
                    
                    // 通知更新
                    if (mAdapter != null) {
                        mAdapter.notifyItemChanged(listIndexInPage(i));
                    }
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                // 项目不存在，添加到当前列表
                Log.d(TAG, "onAdd: 项目不存在，添加到当前列表，GID: " + info.gid);
                // 这里需要谨慎处理，因为直接添加可能会破坏列表结
                // 最好的方式是触发列表刷
                updateForLabel();
                updateView();
            }
            return;
        }
        
        // 原有逻辑
        if (mAdapter != null) {
            mAdapter.notifyItemInserted(position);
        }
        if (downloadLabelDraw != null) {
            downloadLabelDraw.updateDownloadLabels();
        }
        updateView();
    }

    @Override
    public void onReplace(@NonNull DownloadInfo newInfo, @NonNull DownloadInfo oldInfo) {
        if (mList == null) {
            return;
        }
        
        // 尝试在当前列表中找到对应的项目
        boolean found = false;
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo item = mList.get(i);
            if (item.gid == oldInfo.gid) {
                // 更新项目信息
                item.title = newInfo.title;
                item.finished = newInfo.finished;
                item.downloaded = newInfo.downloaded;
                item.total = newInfo.total;
                item.legacy = newInfo.legacy;
                item.state = newInfo.state;
                item.speed = newInfo.speed;
                item.remaining = newInfo.remaining;
                
                // 通知更新
                if (mAdapter != null) {
                    mAdapter.notifyItemChanged(listIndexInPage(i));
                }
                found = true;
                break;
            }
        }
        
        if (!found) {
            Log.d(TAG, "onReplace: 在当前列表中未找到对应的项目，GID: " + oldInfo.gid);
            // 如果找不到，回退到原来的逻辑
            updateForLabel();
            updateView();
        } else {
            // 如果找到了，更新视图
            updateView();
        }
    }

    @Override
    public void onUpdate(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, LinkedList<DownloadInfo> mWaitList) {
        // 如果正在页码切换，延迟处理进度更新
        if (isPageChanging) {
            mMainHandler.postDelayed(() -> onUpdate(info, list, mWaitList), 200);
            return;
        }
        
        if (mList != list) {
            // 如果列表不匹配，尝试在当前列表中找到对应的项目
            boolean found = false;
            for (int i = 0; i < mList.size(); i++) {
                DownloadInfo item = mList.get(i);
                if (item.gid == info.gid) {
                    // 更新项目信息
                    item.title = info.title;
                    item.finished = info.finished;
                    item.downloaded = info.downloaded;
                    item.total = info.total;
                    item.legacy = info.legacy;
                    item.state = info.state;
                    item.speed = info.speed;
                    item.remaining = info.remaining;
                    
                    // 使用payload通知更新，避免整个item重绘，提高性能
                    if (mAdapter != null) {
                        try {
                            mAdapter.notifyItemChanged(listIndexInPage(i), "progress");
                        } catch (Exception e) {
                            android.util.Log.e("DownloadsScene", "Error notifying item change: " + e.getMessage());
                        }
                    }
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                Log.d(TAG, "onUpdate: 在当前列表中未找到对应的项目，GID: " + info.gid);
            }
            return;
        }
        
        // 原有逻辑
        if (!mList.contains(info)) {
            return;
        }
        int index = mList.indexOf(info);
        if (index >= 0 && mAdapter != null) {
            // 使用payload通知更新，避免整个item重绘，提高性能
            try {
                mAdapter.notifyItemChanged(listIndexInPage(index), "progress");
            } catch (Exception e) {
                android.util.Log.e("DownloadsScene", "Error notifying item change: " + e.getMessage());
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onUpdateAll() {
        if (mAdapter != null) {
            // 确保在主线程中更新UI
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                mAdapter.notifyDataSetChanged();
            } else {
                // 在后台线程中，使用Handler切换到主线程
                mMainHandler.post(() -> {
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onReload() {
        if (mAdapter != null) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                refreshDownloadListAfterDelete();
                mOriginalAdapter.preloadFolderMetaAsync();
            } else {
                mMainHandler.post(() -> {
                    refreshDownloadListAfterDelete();
                    mOriginalAdapter.preloadFolderMetaAsync();
                });
            }
        }
    }

    @Override
    public void onChange() {
        mLabel = null;
        // 确保在主线程中更新UI
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            updateForLabel();
            updateView();
        } else {
            // 在后台线程中，使用Handler切换到主线程
            mMainHandler.post(() -> {
                updateForLabel();
                updateView();
            });
        }
    }

    @Override
    public void onRenameLabel(String from, String to) {
        if (!ObjectUtils.equal(mLabel, from)) {
            return;
        }

        mLabel = to;
        // 确保在主线程中更新UI
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            updateForLabel();
            updateView();
        } else {
            // 在后台线程中，使用Handler切换到主线程
            mMainHandler.post(() -> {
                updateForLabel();
                updateView();
            });
        }
    }

    @Override
    public void onRemove(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        if (mList != list) {
            return;
        }
        if (mAdapter != null) {
            // 确保在主线程中更新UI
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                mAdapter.notifyItemRemoved(listIndexInPage(position));
                updateView();
            } else {
                // 在后台线程中，使用Handler切换到主线程
                mMainHandler.post(() -> {
                    if (mAdapter != null) {
                        mAdapter.notifyItemRemoved(listIndexInPage(position));
                    }
                    updateView();
                });
            }
        }
    }

    @Override
    public void onUpdateLabels() {
        // 确保在主线程执行UI更新
        if (Looper.myLooper() != Looper.getMainLooper()) {
            SimpleHandler.getInstance().post(this::onUpdateLabels);
            return;
        }
        
        // 更新标签相关的UI
        updateLabelTabs();
        
        // 刷新当前显示的列表
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        
        // 更新视图状态
        updateView();
    }
    
    /**
     * 更新标签页显示
     */
    private void updateLabelTabs() {
        if (mDownloadManager == null) {
            return;
        }
        
        // 获取所有标
        List<DownloadLabel> labels = mDownloadManager.getLabelList();
        
        // 更新标签适配器
        if (mLabelAdapter != null) {
            mLabelAdapter.notifyDataSetChanged();
        }
        
        // 如果当前选中的标签被删除了，切换到默认标
        if (mCurrentLabel != null && !mDownloadManager.containLabel(mCurrentLabel)) {
            mCurrentLabel = null;
            mLabel = null;
            onInit();
        }
    }

    @Nullable
    public DownloadManager getMDownloadManager() {
        return mDownloadManager;
    }

    // DownloadAdapterCallback 接口实现
    @Override
    public int getIndexPage() {
        return indexPage;
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public int getPaginationSize() {
        return paginationSize;
    }

    @Override
    public boolean isCanPagination() {
        return canPagination;
    }

    @Override
    public int positionInList(int position) {
        if (mList != null && mList.size() > paginationSize && canPagination) {
            return position + pageSize * (indexPage - 1);
        }
        return position;
    }

    @Override
    public int listIndexInPage(int position) {
        if (mList != null && mList.size() > paginationSize && canPagination) {
            return position % pageSize;
        }
        return position;
    }

    @Override
    public List<DownloadInfo> getList() {
        return mList;
    }

    @Override
    public Map<Long, SpiderInfo> getSpiderInfoMap() {
        return mSpiderInfoMap;
    }

    @Override
    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    @Override
    public MyEasyRecyclerView getRecyclerView() {
        return mRecyclerView;
    }


    private static void deleteFileAsync(UniFile... files) {
        Context context = EhApplication.getInstance();
        DeleteFilesTask task = new DeleteFilesTask(context, files);
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
    }

    @Override
    public void onClickTitle() {
        if (!mSearchMode) {
            enterSearchMode(true);
            return;
        }

        if (mSearchBar != null) {
            int state = mSearchBar.getState();
            if (state != SearchBar.STATE_SEARCH_LIST) {
                mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, true);
            } else {
                // 如果已经是搜索列表状态，则重复点击隐显示
                mSearchBar.toggleSuggestionsList();
            }
        }
    }

    @Override
    public void onClickLeftIcon() {

    }

    @Override
    public void onClickRightIcon() {
        if (mSearchBar != null) {
            mSearchBar.applySearch(true);
        }
    }

    @Override
    public void onSearchEditTextClick() {

    }


    @Override
    public void onApplySearch(String query) {
        // 检查mSearchBar是否为空，避免空指针异常
        if (mSearchBar == null) {
            Log.d("DownloadsScene", "onApplySearch: mSearchBar为null，可能对话框已关闭");
            return;
        }
        
        searchKey = query;
        mSearchBar.hideKeyBoard();
        searching = true;
        isFilteringOrSearching = true;  // 标记进入搜索状态
        startSearching();
    }

    protected void startSearching() {
        mProgressView.setVisibility(View.VISIBLE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.GONE);
        }

        if (mSearchMode && mSearchBar != null) {
            mSearchMode = false;
            isFilteringOrSearching = false;  // 退出搜索模式时清除标记
            mSearchBar.setTitle(searchKey);
            mSearchBar.setState(SearchBar.STATE_NORMAL);
        }

        if (mSearchDialog != null) {
            mSearchDialog.dismiss();
        }

        updateForLabel();

        DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mList, searchKey);

        executor.setDownloadSearchingListener(this);

        executor.executeSearching();
    }

    private void gotoFilterAndSort(int id) {
        mProgressView.setVisibility(View.VISIBLE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.GONE);
        }

        // 使用当前列表而不是原始列表进行过滤和排序
        DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mList, mDownloadManager);

        executor.setDownloadSearchingListener(this);

        executor.executeFilterAndSort(id);
    }

    private void updateAdapter() {
        // 检查Fragment 是否已附加，如果未附加则延迟创建适配器
        if (!isAdded()) {
            return;
        }
        mOriginalAdapter = new DownloadAdapter(this, this);
        mOriginalAdapter.setHasStableIds(true);
        // 避免重复创建包装适配器，直接使用原始适配器
        mAdapter = mOriginalAdapter;
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(mAdapter);
        }
        // 同步分页监听器持有的适配器与 RecyclerView，避免换适配器后页码点击无效
        if (myPageChangeListener != null) {
            myPageChangeListener.setAdapter(mOriginalAdapter);
            myPageChangeListener.setRecyclerView(mRecyclerView);
        }
    }

    @Override
    public void onSearchEditTextBackPressed() {
        if (mSearchMode) {
            mSearchMode = false;
        }
        // 防止空指针异
        if (mSearchBar != null) {
            mSearchBar.setState(SearchBar.STATE_NORMAL, true);
        }
    }

    @Override
    public void onStateChange(SearchBar searchBar, int newState, int oldState, boolean animation) {

    }

    @Override
    public boolean isValidView(RecyclerView recyclerView) {
        return false;
    }

    @Nullable
    @Override
    public RecyclerView getValidRecyclerView() {
        return mRecyclerView;
    }

    @Override
    public boolean forceShowSearchBar() {
        return false;
    }

    @Override
    public void onDownloadSearchSuccess(List<DownloadInfo> list) {
        // 检查Fragment 是否已附加，如果未附加则忽略回调
        if (!isAdded()) {
            Log.w("DownloadsScene", "onDownloadSearchSuccess: Fragment未附加，忽略回调");
            return;
        }
        
        Log.d("DownloadsScene", "onDownloadSearchSuccess: 收到回调，列表大" + list.size());
        mList = list;
        // 过滤或排序后重置分页到第一页，重新初始化分页指示器
        indexPage = 1;
        needInitPage = true;
        doNotScroll = false;
        updateAdapter();
        updatePaginationIndicator(true);
        updateTitle();
        mProgressView.setVisibility(View.GONE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.VISIBLE);
        }
        searching = false;
        queryUnreadSpiderInfo();
        
        // 打印前几个项目的排序信息用于调试
        if (list != null && list.size() > 0) {
            Log.d("DownloadsScene", "onDownloadSearchSuccess: 排序后的个项目");
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                DownloadInfo info = list.get(i);
                Log.d("DownloadsScene", "  [" + i + "] ID=" + info.gid + ", 标题=" + info.title + ", 时间=" + info.time);
            }
        }
    }

    @Override
    public void onDownloadListHandleSuccess(List<DownloadInfo> list) {
        // 检查Fragment 是否已附加，如果未附加则忽略回调
        if (!isAdded()) {
            return;
        }
        mList = list;
        // 处理列表成功后同样重置分页并刷新指示器
        indexPage = 1;
        needInitPage = true;
        doNotScroll = false;
        updateAdapter();
        updatePaginationIndicator(true);
        updateTitle();
        mProgressView.setVisibility(View.GONE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.VISIBLE);
        }
        queryUnreadSpiderInfo();
    }

    @Override
    public void onDownloadSearchFailed(List<DownloadInfo> list) {
        UiThreadHelper.showToastSafely(getEHContext(), R.string.download_searching_failed, Toast.LENGTH_LONG);
        mList = list;
        // 异常时也重置分页，避免页码停留在无效
        indexPage = 1;
        needInitPage = true;
        doNotScroll = false;
        updateAdapter();
        updatePaginationIndicator(true);
        updateTitle();
        mProgressView.setVisibility(View.GONE);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(View.VISIBLE);
        }
        searching = false;
        queryUnreadSpiderInfo();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateReadProcess(ActivityResult result) {
        if (result.getResultCode() == LOCAL_GALLERY_INFO_CHANGE) {
            Intent data = result.getData();
            if (data != null) {
                GalleryInfo info = data.getParcelableExtra("info");

                // Check if this is an imported archive - skip SpiderInfo processing
                boolean isImportedArchive = false;
                if (info instanceof DownloadInfo downloadInfo) {
                    isImportedArchive = downloadInfo.archiveUri != null &&
                            downloadInfo.archiveUri.startsWith("content://");
                }

                if (!isImportedArchive && info != null) {
                    // Only process SpiderInfo for regular downloads, not imported archives
                    mSpiderInfoMap.remove(info.gid);
                    SpiderInfo spiderInfo = getSpiderInfo(info);
                    if (spiderInfo != null) {
                        mSpiderInfoMap.put(info.gid, spiderInfo);
                    }
                }

//                mSpiderInfoMap.remove(info.gid);
//                SpiderInfo spiderInfo = getSpiderInfo(info);
                int position = -1;
                if (mList == null || mAdapter == null || info == null) {
                    return;
                }
                for (int i = 0; i < mList.size(); i++) {
                    if (mList.get(i).gid == info.gid) {
                        position = listIndexInPage(i);
                        break;
                    }
                }
                if (position != -1) {
                    mAdapter.notifyItemChanged(position);
                } else {
                    mAdapter.notifyDataSetChanged();
                }

            }
        }
    }

    private void queryUnreadSpiderInfo() {
        if (mList == null) {
            return;
        }
        List<DownloadInfo> requestList = new ArrayList<>();
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            if (!mSpiderInfoMap.containsKey(info.gid) || mSpiderInfoMap.get(info.gid) == null) {
                requestList.add(info);
            }
        }
        DownloadSpiderInfoExecutor executor = new DownloadSpiderInfoExecutor(requestList, this::spiderInfoResultCallBack);
        executor.execute();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void spiderInfoResultCallBack(Map<Long, SpiderInfo> resultMap) {
        mSpiderInfoMap.putAll(resultMap);
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void updateDownloadLabels(SomethingNeedRefresh somethingNeedRefresh) {
        if (somethingNeedRefresh.isDownloadLabelDrawNeed()) {
            downloadLabelDraw.updateDownloadLabels();
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private void initPage(int position) {
        if (mList != null && mList.size() > paginationSize && canPagination) {
            indexPage = position / pageSize + 1;
        }
        doNotScroll = true;
        if (mPaginationIndicator != null) {
            mPaginationIndicator.skip2Pos(indexPage);
        }
        mRecyclerView.scrollToPosition(listIndexInPage(position));
    }


    private int getPageSizePos(int pageSize) {
        int index = 0;
        for (int i = 0; i < perPageCountChoices.length; i++) {
            if (pageSize == perPageCountChoices[i]) {
                index = i;
                break;
            }
        }
        return index;
    }

    private void importLocalArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/x-rar",
                "application/rar",
                "application/x-cbz",
                "application/x-cbr"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // CRITICAL: Add flags to enable persistent URI permissions
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.import_archive_title)));
        } catch (Exception e) {
            Context context = getEHContext();
            if (context != null) {
                UiThreadHelper.showToastSafely(context, R.string.import_archive_failed, Toast.LENGTH_SHORT);
            }
        }
    }

    private void handleSelectedFile(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }

        Context context = getEHContext();
        if (context == null) {
            return;
        }

        // CRITICAL: Request persistent URI permission IMMEDIATELY when file is selected
        // This is the key to solving the permission loss issue after app restart
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(TAG, "Successfully obtained persistent URI permission for: " + uri);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to obtain persistent URI permission for: " + uri, e);
            UiThreadHelper.showToastSafely(context, R.string.archive_permission_lost, Toast.LENGTH_LONG);
            return;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error when obtaining URI permission for: " + uri, e);
            UiThreadHelper.showToastSafely(context, R.string.import_archive_failed, Toast.LENGTH_SHORT);
            return;
        }

        // Show processing dialog
        UiThreadHelper.showToastSafely(context, R.string.import_archive_processing, Toast.LENGTH_LONG);

        // Process the archive file in background
        ExecutorManager.getBackgroundExecutor().execute(() -> processArchiveFile(uri));
    }

    private void processArchiveFile(Uri uri) {
        Context context = getEHContext();
        if (context == null) {
            return;
        }

        try {
            // Verify URI accessibility (permission should already be granted)
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    runOnUiThread(() ->
                            UiThreadHelper.showToastSafely(context, R.string.import_archive_failed, Toast.LENGTH_SHORT)
                    );
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot access file even with persistent permission", e);
                runOnUiThread(() ->
                        UiThreadHelper.showToastSafely(context, R.string.import_archive_failed, Toast.LENGTH_SHORT)
                );
                return;
            }

            // Get file name
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                fileName = "imported_archive_" + System.currentTimeMillis();
            }

            // Validate file format
            if (!isValidArchiveFormat(fileName)) {
                runOnUiThread(() ->
                        Toast.makeText(context, R.string.import_archive_invalid_format, Toast.LENGTH_SHORT).show()
                );
                return;
            }

            // Create DownloadInfo for the archive
            DownloadInfo downloadInfo = createArchiveDownloadInfo(context, uri, fileName);
            if (downloadInfo == null) {
                runOnUiThread(() ->
                        Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show()
                );
                return;
            }

            // Check if already imported
            if (mDownloadManager != null && mDownloadManager.containDownloadInfo(downloadInfo.gid)) {
                runOnUiThread(() ->
                        Toast.makeText(context, R.string.import_archive_already_imported, Toast.LENGTH_SHORT).show()
                );
                return;
            }

            // Add to download manager
            if (mDownloadManager != null) {
                List<DownloadInfo> downloadList = new ArrayList<>();
                downloadList.add(downloadInfo);
                mDownloadManager.addDownload(downloadList);
                runOnUiThread(() -> {
                    Toast.makeText(context, R.string.import_archive_success, Toast.LENGTH_SHORT).show();
                    updateForLabel();
                    updateView();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to process archive file", e);
            runOnUiThread(() ->
                    Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private boolean isValidArchiveFormat(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                lowerName.endsWith(".cbz") || lowerName.endsWith(".cbr");
    }


    public void runOnUiThread(Runnable runnable) {
        Activity activity = getActivity2();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }

    private DownloadInfo createArchiveDownloadInfo(Context context, Uri uri, String fileName) {
        try {
            DownloadInfo downloadInfo = new DownloadInfo();
            downloadInfo.gid = System.currentTimeMillis(); // Use timestamp as unique ID
            downloadInfo.token = "";
            downloadInfo.title = fileName.replaceAll("\\.[^.]*$", ""); // Remove extension
            downloadInfo.titleJpn = null;
            downloadInfo.thumb = null; // No thumbnail for imported archives
            downloadInfo.category = EhUtils.UNKNOWN; // Keep as UNKNOWN, will be handled in display logic
            downloadInfo.posted = null;
            downloadInfo.uploader = "Local Archive";
            downloadInfo.rating = -1.0f; // Keep default rating to not affect other downloads
            downloadInfo.state = DownloadInfo.STATE_FINISH;
            downloadInfo.legacy = 0;
            downloadInfo.time = System.currentTimeMillis();
            downloadInfo.label = null;
            downloadInfo.total = 0; // Will be set by archive provider
            downloadInfo.finished = 0;

            // Store the URI in the archiveUri field - this is the key identifier
            downloadInfo.archiveUri = uri.toString();

            return downloadInfo;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create DownloadInfo", e);
            return null;
        }
    }

    private class DeleteDialogHelper implements DialogInterface.OnClickListener {

        private final GalleryInfo mGalleryInfo;
        private final CheckBoxDialogBuilder mBuilder;

        public DeleteDialogHelper(GalleryInfo galleryInfo, CheckBoxDialogBuilder builder) {
            mGalleryInfo = galleryInfo;
            mBuilder = builder;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return;
            }

            boolean checked = mBuilder.isChecked();
            Settings.putRemoveImageFiles(checked);

            // Remove from download list and move to recycle bin
            if (mDownloadManager != null) {
                mDownloadManager.deleteDownload(mGalleryInfo.gid);
            }

            // When user selects delete, downloads are already moved to recycle bin by DownloadManager
            // 'remove image files' setting means the user wants to quickly inline clear later.
        }
    }

    private void permanentlyDeleteGallery(LocalGalleryInfo galleryInfo, String displayTitle) {
        Context context = getEHContext();
        if (context == null || galleryInfo == null) {
            return;
        }

        Toast.makeText(context, getString(R.string.recycle_bin_delete_permanent_confirm, displayTitle), Toast.LENGTH_SHORT).show();

        LocalGalleryManager.getInstance(context).permanentlyDeleteGallery(galleryInfo, (current, total, detail) -> {
            if (current >= total) {
                Toast.makeText(context, R.string.recycle_bin_delete_permanent_success, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class DeleteRangeDialogHelper implements DialogInterface.OnClickListener {

        private final List<DownloadInfo> mDownloadInfoList;
        private final LongList mGidList;
        private final CheckBoxDialogBuilder mBuilder;

        public DeleteRangeDialogHelper(List<DownloadInfo> downloadInfoList,
                                       LongList gidList, CheckBoxDialogBuilder builder) {
            mDownloadInfoList = downloadInfoList;
            mGidList = gidList;
            mBuilder = builder;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return;
            }

            // Cancel check mode
            if (mRecyclerView != null) {
                mRecyclerView.outOfCustomChoiceMode();
            }

            // 图片文件删除选项
            boolean checked = mBuilder.isChecked();
            Settings.putRemoveImageFiles(checked);

            // 少量删除直接原逻辑（避免开新线程开销
            if (mDownloadInfoList.size() <= 5) {
                if (mDownloadManager != null) {
                    mDownloadManager.deleteRangeDownload(mGidList);
                }
                if (checked) {
                    // item moved to recycle bin already; we keep permanent delete to user action in recycle bin UI
                }
                return;
            }

            // 多个项目删档用后台任务
            Context context = getActivity2();
            if (context == null) {
                return;
            }

            Toast.makeText(context, R.string.download_remove_dialog_title, Toast.LENGTH_SHORT).show();

            DeleteRangeDownloadTask task = new DeleteRangeDownloadTask(context, mDownloadManager, mGidList, new Runnable() {
                @Override
                public void run() {
                    refreshDownloadListAfterDelete();
                }
            });

            task.setProgressListener(new BackgroundTask.ProgressListener() {
                @Override
                public void onProgressChanged(int progress, String detail) {
                    // Progress updates handled via notification
                }

                @Override
                public void onProgressChanged(int current, int total, String detail) {
                    // Progress updates handled via notification
                }

                @Override
                public void onCompleted() {
                    // Handled by onCompletedCallback
                }

                @Override
                public void onError(Throwable error) {
                    refreshDownloadListAfterDelete();
                }
            });

            com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
            Toast.makeText(context, R.string.background_task_submitted, Toast.LENGTH_SHORT).show();
        }
    }

    private class MoveDialogHelper implements DialogInterface.OnClickListener {

        private final String[] mLabels;
        private final List<DownloadInfo> mDownloadInfoList;

        public MoveDialogHelper(String[] labels, List<DownloadInfo> downloadInfoList) {
            mLabels = labels;
            mDownloadInfoList = downloadInfoList;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Cancel check mode
            Context context = getEHContext();
            if (null == context) {
                return;
            }
            if (null != mRecyclerView) {
                mRecyclerView.outOfCustomChoiceMode();
            }

            String label;
            if (which == 0) {
                label = null;
            } else {
                label = mLabels[which];
            }
            EhApplication.getDownloadManager(context).changeLabel(mDownloadInfoList, label);
        }
    }

//    /**
//     * 更新thumb的可见性（拖拽功能已直接附加到thumb上）
//     * @param isSelectionMode 是否处于选择模式
//     */
//    private void updateThumbVisibility(boolean isSelectionMode) {
//        if (mRecyclerView == null) {
//            return;
//        }
//
//        for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
//            RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(mRecyclerView.getChildAt(i));
//            if (holder instanceof DownloadAdapter.DownloadHolder) {
//                DownloadAdapter.DownloadHolder downloadHolder = (DownloadAdapter.DownloadHolder) holder;
//                // thumb 始终可见，拖拽功能已直接附加到thumb
//                downloadHolder.thumb.setVisibility(View.VISIBLE);
//            }
//        }
//    }

    private class DownloadChoiceListener implements EasyRecyclerView.CustomChoiceListener {

        @Override
        public void onIntoCustomChoice(EasyRecyclerView view) {
            if (mRecyclerView != null) {
                mRecyclerView.setOnItemLongClickListener(null);
                mRecyclerView.setLongClickable(false);
            }
            if (mFabLayout != null) {
                mFabLayout.setExpanded(true);
            }
            // Lock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);

//            // 进入选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(true);
        }

        @Override
        public void onOutOfCustomChoice(EasyRecyclerView view) {
            if (mRecyclerView != null) {
                mRecyclerView.setOnItemLongClickListener(DownloadsScene.this);
            }
            if (mFabLayout != null) {
                mFabLayout.setExpanded(false);
            }
            // Unlock drawer
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);

//            // 退出选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(false);
        }

        @Override
        public void onItemCheckedStateChanged(EasyRecyclerView view, int position, long id, boolean checked) {
            if (view.getCheckedItemCount() == 0) {
                view.outOfCustomChoiceMode();
            }
        }
    }

    private void filterByCategory() {
        if (mBackList == null) {
            return;
        }
        if (mSelectedCategory == EhUtils.ALL_CATEGORY) {
            mList = new ArrayList<>(mBackList);
        } else {
            mList = new ArrayList<>();
            for (DownloadInfo info : mBackList) {
                if (info.category == mSelectedCategory) {
                    mList.add(info);
                }
            }
        }
        // 如果没有应用状态过滤或排序，直接更新界面
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        updateTitle();
        updatePaginationIndicator(true); // 筛选时强制重新初始化
        updateView();
        queryUnreadSpiderInfo();
    }

    private void showSortFilterDialog() {
        Context context = getEHContext();
        if (context == null) {
            return;
        }

        // 如果弹窗已经存在，直接显示
        if (mSortFilterDialog != null && mSortFilterDialog.isShowing()) {
            return;
        }

        // 创建弹窗视图 - 使用新的布局
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_sort_filter_v2, null);
        View advancedFilterToggleRow = dialogView.findViewById(R.id.advanced_filter_toggle_row);
        TextView advancedFilterToggleIcon = dialogView.findViewById(R.id.advanced_filter_toggle_icon);
        View advancedFilterContentContainer = dialogView.findViewById(R.id.advanced_filter_content_container);

        // 高级筛选面板默认折叠
        setSectionExpanded(advancedFilterContentContainer, advancedFilterToggleIcon, false);
        advancedFilterToggleRow.setOnClickListener(v -> toggleSection(advancedFilterContentContainer, advancedFilterToggleIcon));
        
        // 获取CategoryTable
        DownloadCategoryTable categoryTable = dialogView.findViewById(R.id.category_table);
        
        // 初始化RecyclerView和范围输入
        RecyclerView statusRecyclerView = dialogView.findViewById(R.id.status_recycler_view);
        RecyclerView sortRecyclerView = dialogView.findViewById(R.id.sort_recycler_view);
        EditText timeFromInput = dialogView.findViewById(R.id.filter_time_from_input);
        EditText timeToInput = dialogView.findViewById(R.id.filter_time_to_input);
        EditText sizeFromInput = dialogView.findViewById(R.id.filter_size_from_input);
        EditText sizeToInput = dialogView.findViewById(R.id.filter_size_to_input);
        Button pickTimeFromButton = dialogView.findViewById(R.id.pick_time_from_button);
        Button pickTimeToButton = dialogView.findViewById(R.id.pick_time_to_button);
        CheckBox duplicateOnlyCheckbox = dialogView.findViewById(R.id.filter_duplicate_only_checkbox);
        
        // 竖屏单列，横屏双列平铺
        int spanCount = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
        statusRecyclerView.setLayoutManager(new GridLayoutManager(context, spanCount));
        sortRecyclerView.setLayoutManager(new GridLayoutManager(context, spanCount));
        statusRecyclerView.setNestedScrollingEnabled(false);
        sortRecyclerView.setNestedScrollingEnabled(false);

        // 准备状态数据（去除全部选项目
        List<String> statusItems = new ArrayList<>();
        List<Integer> statusIds = new ArrayList<>();
        statusItems.add(getString(R.string.download_state_downloaded));
        statusIds.add(R.id.download_done);
        statusItems.add(getString(R.string.download_state_none));
        statusIds.add(R.id.not_started);
        statusItems.add(getString(R.string.download_state_wait));
        statusIds.add(R.id.waiting);
        statusItems.add(getString(R.string.download_state_downloading));
        statusIds.add(R.id.downloading);
        statusItems.add(getString(R.string.download_state_failed));
        statusIds.add(R.id.failed);

        // 准备排序数据
        List<String> sortItems = new ArrayList<>();
        List<Integer> sortIds = new ArrayList<>();
        sortItems.add(getString(R.string.default_sort));
        sortIds.add(R.id.sort_by_default);
        sortItems.add(getString(R.string.sort_by_gallery_id_asc));
        sortIds.add(R.id.sort_by_gallery_id_asc);
        sortItems.add(getString(R.string.sort_by_gallery_id_desc));
        sortIds.add(R.id.sort_by_gallery_id_desc);
        sortItems.add(getString(R.string.sort_by_create_time_asc));
        sortIds.add(R.id.sort_by_create_time_asc);
        sortItems.add(getString(R.string.sort_by_create_time_desc));
        sortIds.add(R.id.sort_by_create_time_desc);
        sortItems.add(getString(R.string.sort_by_rating_asc));
        sortIds.add(R.id.sort_by_rating_asc);
        sortItems.add(getString(R.string.sort_by_rating_desc));
        sortIds.add(R.id.sort_by_rating_desc);
        sortItems.add(getString(R.string.sort_by_name_asc));
        sortIds.add(R.id.sort_by_name_asc);
        sortItems.add(getString(R.string.sort_by_name_desc));
        sortIds.add(R.id.sort_by_name_desc);
        sortItems.add(getString(R.string.sort_by_file_size_asc));
        sortIds.add(R.id.sort_by_file_size_asc);
        sortItems.add(getString(R.string.sort_by_file_size_desc));
        sortIds.add(R.id.sort_by_file_size_desc);

        // 初始化适配器
        mStatusAdapter = new CheckboxAdapter(statusItems, statusIds);
        mSortAdapter = new CheckboxAdapter(sortItems, sortIds);
        
        // 设置互斥选择 - 状态改为多选，排序保持互斥
        mStatusAdapter.setMutuallyExclusive(false);
        mSortAdapter.setMutuallyExclusive(true);

        // 设置选择变更监听器
        mStatusAdapter.setOnSelectionChangedListener(selectedItems -> {
            Log.d("DownloadsScene", "状态选择已变量 " + selectedItems);
            mSelectedStatuses.clear();
            mSelectedStatuses.addAll(selectedItems);
        });
        
        mSortAdapter.setOnSelectionChangedListener(selectedItems -> {
            Log.d("DownloadsScene", "排序选择已变量 " + selectedItems);
            mSelectedSorts.clear();
            mSelectedSorts.addAll(selectedItems);
        });

        // 设置适配器
        statusRecyclerView.setAdapter(mStatusAdapter);
        sortRecyclerView.setAdapter(mSortAdapter);

        // 设置CategoryTable的选中状态
        if (mSelectedCategories != null && !mSelectedCategories.isEmpty()) {
            // 使用新的DownloadCategoryTable，直接设置选中的分页
            categoryTable.setSelectedCategories(mSelectedCategories);
        } else {
            // 默认全部- 确保所有按钮都是亮起的
            mSelectedCategories = new HashSet<>();
            mSelectedCategories.add(EhUtils.ALL_CATEGORY);
            categoryTable.setSelectedCategories(mSelectedCategories);
        }

        // 设置默认选中
        if (mSelectedStatuses.isEmpty()) {
            // 默认全选所有状态
            mSelectedStatuses.add(R.id.download_done);
            mSelectedStatuses.add(R.id.not_started);
            mSelectedStatuses.add(R.id.waiting);
            mSelectedStatuses.add(R.id.downloading);
            mSelectedStatuses.add(R.id.failed);
        }
        if (mSelectedSorts.isEmpty()) {
            mSelectedSorts.add(R.id.sort_by_default);
        }

        timeFromInput.setText(mFilterTimeFromInput);
        timeToInput.setText(mFilterTimeToInput);
        sizeFromInput.setText(mFilterSizeFromInput);
        sizeToInput.setText(mFilterSizeToInput);
        duplicateOnlyCheckbox.setChecked(mFilterDuplicateOnly);

        pickTimeFromButton.setOnClickListener(v -> showDateTimePicker(timeFromInput));
        pickTimeToButton.setOnClickListener(v -> showDateTimePicker(timeToInput));

        // 使用post方法延迟设置选中项，避免在RecyclerView计算布局时调用
        dialogView.post(() -> {
            mStatusAdapter.setSelectedItems(mSelectedStatuses);
            mSortAdapter.setSelectedItems(mSelectedSorts);
        });

        // 创建弹窗
        mSortFilterDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.advanced_filter)
                .setView(dialogView)
                .create();

        // 设置按钮点击事件
        Button resetButton = dialogView.findViewById(R.id.reset_button);
        Button applyButton = dialogView.findViewById(R.id.apply_button);
        Button selectAllStatusButton = dialogView.findViewById(R.id.select_all_status_button);
        Button selectNoneStatusButton = dialogView.findViewById(R.id.select_none_status_button);
        
        // 全选按钮点击事件
        selectAllStatusButton.setOnClickListener(v -> {
            mSelectedStatuses.clear();
            mSelectedStatuses.add(R.id.download_done);
            mSelectedStatuses.add(R.id.not_started);
            mSelectedStatuses.add(R.id.waiting);
            mSelectedStatuses.add(R.id.downloading);
            mSelectedStatuses.add(R.id.failed);
            mStatusAdapter.setSelectedItems(mSelectedStatuses);
        });
        
        // 全不选按钮点击事件
        selectNoneStatusButton.setOnClickListener(v -> {
            mSelectedStatuses.clear();
            mStatusAdapter.setSelectedItems(mSelectedStatuses);
        });

        resetButton.setOnClickListener(v -> {
            // 重置所有选择
            mSelectedCategories.clear();
            mSelectedStatuses.clear();
            mSelectedSorts.clear();

            mSelectedCategories.add(EhUtils.ALL_CATEGORY);
            // 重置为全选所有状态
            mSelectedStatuses.add(R.id.download_done);
            mSelectedStatuses.add(R.id.not_started);
            mSelectedStatuses.add(R.id.waiting);
            mSelectedStatuses.add(R.id.downloading);
            mSelectedStatuses.add(R.id.failed);
            mSelectedSorts.add(R.id.sort_by_default);

            mFilterTimeFrom = null;
            mFilterTimeTo = null;
            mFilterSizeFrom = null;
            mFilterSizeTo = null;
            mFilterDuplicateOnly = false;
            mFilterTimeFromInput = "";
            mFilterTimeToInput = "";
            mFilterSizeFromInput = "";
            mFilterSizeToInput = "";
            timeFromInput.setText("");
            timeToInput.setText("");
            sizeFromInput.setText("");
            sizeToInput.setText("");
            duplicateOnlyCheckbox.setChecked(false);

            // 重置CategoryTable - 确保所有按钮都是亮起的
            categoryTable.setSelectedCategories(mSelectedCategories);

            // 使用post方法延迟设置选中项，避免在RecyclerView计算布局时调用
            dialogView.post(() -> {
                mStatusAdapter.setSelectedItems(mSelectedStatuses);
                mSortAdapter.setSelectedItems(mSelectedSorts);
            });
        });

        applyButton.setOnClickListener(v -> {
            mSelectedCategories = categoryTable.getSelectedCategories();
            Log.d("DownloadsScene", "applySortAndFilter: 获取到的分类=" + mSelectedCategories);

            String timeFromRaw = timeFromInput.getText() != null ? timeFromInput.getText().toString().trim() : "";
            String timeToRaw = timeToInput.getText() != null ? timeToInput.getText().toString().trim() : "";
            String sizeFromRaw = sizeFromInput.getText() != null ? sizeFromInput.getText().toString().trim() : "";
            String sizeToRaw = sizeToInput.getText() != null ? sizeToInput.getText().toString().trim() : "";

            Long parsedTimeFrom = parseTimeInput(timeFromRaw, false);
            if (timeFromRaw.length() > 0 && parsedTimeFrom == null) {
                Toast.makeText(context, R.string.download_filter_invalid_time, Toast.LENGTH_SHORT).show();
                return;
            }
            Long parsedTimeTo = parseTimeInput(timeToRaw, true);
            if (timeToRaw.length() > 0 && parsedTimeTo == null) {
                Toast.makeText(context, R.string.download_filter_invalid_time, Toast.LENGTH_SHORT).show();
                return;
            }
            if (parsedTimeFrom != null && parsedTimeTo != null && parsedTimeFrom > parsedTimeTo) {
                Toast.makeText(context, R.string.download_filter_range_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            Long parsedSizeFrom = parseSizeInput(sizeFromRaw);
            if (sizeFromRaw.length() > 0 && parsedSizeFrom == null) {
                Toast.makeText(context, R.string.download_filter_invalid_size, Toast.LENGTH_SHORT).show();
                return;
            }
            Long parsedSizeTo = parseSizeInput(sizeToRaw);
            if (sizeToRaw.length() > 0 && parsedSizeTo == null) {
                Toast.makeText(context, R.string.download_filter_invalid_size, Toast.LENGTH_SHORT).show();
                return;
            }
            if (parsedSizeFrom != null && parsedSizeTo != null && parsedSizeFrom > parsedSizeTo) {
                Toast.makeText(context, R.string.download_filter_range_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            mFilterTimeFrom = parsedTimeFrom;
            mFilterTimeTo = parsedTimeTo;
            mFilterSizeFrom = parsedSizeFrom;
            mFilterSizeTo = parsedSizeTo;
            mFilterDuplicateOnly = duplicateOnlyCheckbox.isChecked();
            mFilterTimeFromInput = timeFromRaw;
            mFilterTimeToInput = timeToRaw;
            mFilterSizeFromInput = sizeFromRaw;
            mFilterSizeToInput = sizeToRaw;

            Log.d("DownloadsScene", "applyButton clicked: 应用过滤和排序");
            applySortAndFilter();
            mSortFilterDialog.dismiss();
        });

        mSortFilterDialog.show();
    }

    private void applySortAndFilter() {
        // 添加日志
        Log.d("DownloadsScene", "applySortAndFilter: 开始应用过滤和排序");
        
        // 应用分类过滤
        Set<Integer> selectedCategories = new HashSet<>();
        Integer selectedStatus = R.id.all;
        Integer selectedSort = R.id.sort_by_default;
        
        if (mSelectedCategories != null && !mSelectedCategories.isEmpty()) {
            selectedCategories.addAll(mSelectedCategories);
            Log.d("DownloadsScene", "applySortAndFilter: 选中的分类ID = " + selectedCategories);
        } else {
            // 如果没有选择分类，默认为全部
            selectedCategories.add(EhUtils.ALL_CATEGORY);
        }
        
        // 状态现在是多选的，不需要取第一个，直接传递集合
        Log.d("DownloadsScene", "applySortAndFilter: 选中的状态ID集合 = " + mSelectedStatuses);
        
        if (!mSelectedSorts.isEmpty()) {
            // 取第一个选中的排序（现在是互斥的，只会有一个）
            selectedSort = mSelectedSorts.iterator().next();
            Log.d("DownloadsScene", "applySortAndFilter: 选中的排序ID = " + selectedSort);
        }
        
        // 检查是否只选择了ALL_CATEGORY（即全选状态）
        boolean isAllCategories = selectedCategories.size() == 1 && selectedCategories.contains(EhUtils.ALL_CATEGORY);
        
        // 如果需要应用任何过滤或排序
        boolean hasStatusFilter = !mSelectedStatuses.isEmpty() && mSelectedStatuses.size() < 5; // 5是所有状态的数量
        boolean hasRangeFilter = mFilterTimeFrom != null || mFilterTimeTo != null
                || mFilterSizeFrom != null || mFilterSizeTo != null;
        boolean hasDuplicateFilter = mFilterDuplicateOnly;
        if (!isAllCategories || hasStatusFilter || selectedSort != R.id.sort_by_default || hasRangeFilter || hasDuplicateFilter) {
            Log.d("DownloadsScene", "applySortAndFilter: 需要应用过滤或排序");
            isFilteringOrSearching = true;  // 标记进入筛选状态
            mProgressView.setVisibility(View.VISIBLE);
            if (mRecyclerView != null) {
                mRecyclerView.setVisibility(View.GONE);
            }
            
            // 创建执行器并应用过滤和排序
            DownloadListInfosExecutor executor = new DownloadListInfosExecutor(mBackList, mDownloadManager);
            executor.setDownloadSearchingListener(this);
            
            // 同时应用分类过滤、状态过滤和排序（含时间/大小范围）
            Log.d("DownloadsScene", "applySortAndFilter: 调用executeFilterAndSort, categories=" + selectedCategories + ", statuses=" + mSelectedStatuses + ", sort=" + selectedSort);
            executor.executeFilterAndSort(selectedCategories, mSelectedStatuses, selectedSort,
                    mFilterTimeFrom, mFilterTimeTo, mFilterSizeFrom, mFilterSizeTo, mFilterDuplicateOnly);
        } else {
            Log.d("DownloadsScene", "applySortAndFilter: 不需要任何过滤或排序，使用原始列表");
            isFilteringOrSearching = false;  // 标记退出筛选状态
            // 不需要任何过滤或排序，直接使用原始列表
            mList = new ArrayList<>(mBackList);
            updateAdapter();
            updateTitle();
            updatePaginationIndicator(true);
            updateView();
            queryUnreadSpiderInfo();
        }
    }

    /**
     * 解析时间输入字符串为毫秒时间戳。
     * 支持格式: "yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm"
     * @param input 用户输入
     * @param isEnd 若为 true 且仅输入日期，则自动补全为当天结束时间 23:59:59.999
     */
    @Nullable
    private Long parseTimeInput(String input, boolean isEnd) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String s = input.trim();
        String[] formats = {"yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                sdf.setLenient(false);
                Date d = sdf.parse(s);
                if (d != null) {
                    long ts = d.getTime();
                    if (isEnd && fmt.equals("yyyy-MM-dd")) {
                        ts += 24L * 60 * 60 * 1000 - 1; // 补全到当天末尾
                    }
                    return ts;
                }
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private void showDateTimePicker(@NonNull EditText targetInput) {
        Context context = getEHContext();
        if (context == null) {
            return;
        }

        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                            context,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);

                                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                                targetInput.setText(outputFormat.format(calendar.getTime()));
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * 解析大小输入字符串为字节数。
     * 支持: 纯数字(视为 MB)，或数字+单位(B/KB/MB/GB)。
     */
    @Nullable
    private Long parseSizeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String s = input.trim().toUpperCase(Locale.ROOT);
        long multiplier = 1024L * 1024L; // 默认 MB
        String numPart = s;
        if (s.endsWith("GB")) {
            multiplier = 1024L * 1024L * 1024L;
            numPart = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("MB")) {
            multiplier = 1024L * 1024L;
            numPart = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("KB")) {
            multiplier = 1024L;
            numPart = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("B")) {
            multiplier = 1L;
            numPart = s.substring(0, s.length() - 1).trim();
        }
        try {
            double num = Double.parseDouble(numPart);
            if (num < 0) return null;
            return (long) (num * multiplier);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


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

package com.hippo.ehviewer.ui.scene.gallery.list;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.android.resource.AttrResources;
import com.hippo.app.CheckBoxDialogBuilder;
import com.hippo.app.EditTextDialogBuilder;
import com.hippo.drawable.AddDeleteDrawable;
import com.hippo.drawable.DrawerArrowDrawable;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.FastScroller;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.FavouriteStatusRouter;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.callBack.SubscriptionCallback;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser;
import com.hippo.ehviewer.client.parser.GalleryListParser;
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser;

import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.event.SomethingNeedRefresh;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.dialog.SelectItemWithIconAdapter;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.ui.scene.ProgressScene;
import com.hippo.ehviewer.util.TagTranslationUtil;
import com.hippo.ehviewer.widget.GalleryInfoContentHelper;
import com.hippo.ehviewer.widget.JumpDateSelector;
import com.hippo.ehviewer.widget.SearchBar;
import com.hippo.ehviewer.widget.SearchLayout;
import com.hippo.refreshlayout.RefreshLayout;
import com.hippo.ripple.Ripple;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.util.AppHelper;
import com.hippo.util.DrawableManager;
import com.hippo.view.ViewTransition;
import com.hippo.widget.ContentLayout;
import com.hippo.widget.FabLayout;
import com.hippo.widget.LoadImageViewNew;
import com.hippo.widget.SearchBarMover;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.SimpleAnimatorListener;
import com.hippo.yorozuya.StringUtils;
import com.hippo.yorozuya.ViewUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class GalleryListScene extends BaseScene
        implements EasyRecyclerView.OnItemClickListener, EasyRecyclerView.OnItemLongClickListener,
        SearchBar.Helper, SearchBar.OnStateChangeListener, FastScroller.OnDragHandlerListener,
        SearchLayout.Helper, SearchBarMover.Helper, View.OnClickListener, FabLayout.OnClickFabListener,
        FabLayout.OnExpandListener, SubscriptionCallback {

    private static final String TAG = "GalleryListScene";

    @IntDef({STATE_NORMAL, STATE_SIMPLE_SEARCH, STATE_SEARCH, STATE_SEARCH_SHOW_LIST})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {
    }

    private static final int BACK_PRESSED_INTERVAL = 2000;

    public final static int REQUEST_CODE_SELECT_IMAGE = 0;

    public final static String KEY_ACTION = "action";
    public final static String ACTION_HOMEPAGE = "action_homepage";
    public final static String ACTION_SUBSCRIPTION = "action_subscription";
    public final static String ACTION_WHATS_HOT = "action_whats_hot";
    public final static String ACTION_TOP_LIST = "action_top_list";
    public final static String ACTION_LIST_URL_BUILDER = "action_list_url_builder";

    public final static String KEY_LIST_URL_BUILDER = "list_url_builder";
    public final static String KEY_HAS_FIRST_REFRESH = "has_first_refresh";
    public final static String KEY_STATE = "state";

    final static int STATE_NORMAL = 0;
    final static int STATE_SIMPLE_SEARCH = 1;
    final static int STATE_SEARCH = 2;
    final static int STATE_SEARCH_SHOW_LIST = 3;

    private static final long ANIMATE_TIME = 300L;

    private boolean showReadProgress = false;
    private boolean filterOpen = false;
    private final List<String> filterTagList = new ArrayList<>();

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private EhClient mClient;
    @Nullable
    ListUrlBuilder mUrlBuilder;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private SearchLayout mSearchLayout;
    @Nullable
    private SearchBar mSearchBar;
    @Nullable
    private View mSearchFab;
    @Nullable
    private FabLayout mFabLayout;
    @Nullable
    private FloatingActionButton mFloatingActionButton;
    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private GalleryListAdapter mAdapter;
    @Nullable
    public GalleryListHelper mHelper;
    @Nullable
    private DrawerArrowDrawable mLeftDrawable;
    @Nullable
    private AddDeleteDrawable mRightDrawable;
    @Nullable
    private SearchBarMover mSearchBarMover;
    @Nullable
    private AddDeleteDrawable mActionFabDrawable;
    @Nullable
    private PopupWindow popupWindow;
    @Nullable
    private AlertDialog alertDialog;
    @Nullable
    private AlertDialog jumpSelectorDialog;
    @NonNull
    ViewPager drawPager;
    @NonNull
    private View bookmarksView;
    @Nullable
    private View subscriptionView;

    private JumpDateSelector mJumpDateSelector;

    private EhTagDatabase ehTags;

    private ExecutorService executorService;

    private BookmarksDraw mBookmarksDraw;
    private SubscriptionDraw mSubscriptionDraw;

    @Nullable
    private final Animator.AnimatorListener mActionFabAnimatorListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (null != mFabLayout) {
                ((View) mFabLayout.getPrimaryFab()).setVisibility(View.INVISIBLE);
            }
        }
    };

    @Nullable
    private final Animator.AnimatorListener mSearchFabAnimatorListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (null != mSearchFab) {
                mSearchFab.setVisibility(View.INVISIBLE);
            }
        }
    };

    private int mHideActionFabSlop;
    private boolean mShowActionFab = true;

    @Nullable
    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy >= mHideActionFabSlop) {
                hideActionFab();
            } else if (dy <= -mHideActionFabSlop / 2) {
                showActionFab();
            }
        }
    };

    @State
    private int mState = STATE_NORMAL;

    // Double click back exit
    private long mPressBackTime = 0;

    private boolean mHasFirstRefresh = false;

    private int mNavCheckedId = 0;

    private int popupWindowPosition = -1;

    private ShowcaseView mShowcaseView;

    private DownloadManager mDownloadManager;
    private DownloadManager.DownloadInfoListener mDownloadInfoListener;
    private FavouriteStatusRouter mFavouriteStatusRouter;
    private FavouriteStatusRouter.Listener mFavouriteStatusRouterListener;

    @Override
    public int getNavCheckedItem() {
        return mNavCheckedId;
    }

    private void handleArgs(Bundle args) {
        if (null == args || null == mUrlBuilder) {
            return;
        }

        String action = args.getString(KEY_ACTION);
        if (ACTION_HOMEPAGE.equals(action)) {
            mUrlBuilder.reset();
        } else if (ACTION_SUBSCRIPTION.equals(action)) {
            mUrlBuilder.reset();
            mUrlBuilder.setMode(ListUrlBuilder.MODE_SUBSCRIPTION);
        } else if (ACTION_WHATS_HOT.equals(action)) {
            mUrlBuilder.reset();
            mUrlBuilder.setMode(ListUrlBuilder.MODE_WHATS_HOT);
        } else if (ACTION_LIST_URL_BUILDER.equals(action)) {
            ListUrlBuilder builder = args.getParcelable(KEY_LIST_URL_BUILDER);
            if (builder != null) {
                mUrlBuilder.set(builder);
            }
        } else if (ACTION_TOP_LIST.equals(action)) {
            mUrlBuilder.reset();
            mUrlBuilder.setMode(ListUrlBuilder.MODE_NORMAL);
        }
    }

    @Override
    public void onNewArguments(@NonNull Bundle args) {
        handleArgs(args);
        onUpdateUrlBuilder();
        if (null != mHelper) {
            mHelper.refresh();
        }
        setState(STATE_NORMAL);
        if (null != mSearchBarMover) {
            mSearchBarMover.showSearchBar();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getEHContext();
        assert context != null;
        AssertUtils.assertNotNull(context);
        executorService = EhApplication.getExecutorService(context);
        mClient = EhApplication.getEhClient(context);
        mDownloadManager = EhApplication.getDownloadManager(context);
        mFavouriteStatusRouter = EhApplication.getFavouriteStatusRouter(context);

        mDownloadInfoListener = new DownloadManager.DownloadInfoListener() {

            @Override
            public void onAdd(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onReplace(@NonNull DownloadInfo newInfo, @NonNull DownloadInfo oldInfo) {

            }

            @Override
            public void onUpdate(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, LinkedList<DownloadInfo> mWaitList) {
            }

            @Override
            public void onUpdateAll() {
            }

            @Override
            public void onReload() {
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onChange() {
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onRenameLabel(String from, String to) {
            }

            @Override
            public void onRemove(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onUpdateLabels() {
            }
        };
        mDownloadManager.addDownloadInfoListener(mDownloadInfoListener);

        mFavouriteStatusRouterListener = (gid, slot) -> {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        };
        mFavouriteStatusRouter.addListener(mFavouriteStatusRouterListener);

        ehTags = EhTagDatabase.getInstance(context);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
        showReadProgress = Settings.getShowReadProgress();
    }

    public void onInit() {
        mUrlBuilder = new ListUrlBuilder();
        handleArgs(getArguments());
    }

    @SuppressWarnings("WrongConstant")
    private void onRestore(Bundle savedInstanceState) {
        mHasFirstRefresh = savedInstanceState.getBoolean(KEY_HAS_FIRST_REFRESH);
        mUrlBuilder = savedInstanceState.getParcelable(KEY_LIST_URL_BUILDER);
        mState = savedInstanceState.getInt(KEY_STATE);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        boolean hasFirstRefresh;
        if (mHelper != null && 1 == mHelper.getShownViewIndex()) {
            hasFirstRefresh = false;
        } else {
            hasFirstRefresh = mHasFirstRefresh;
        }
        outState.putBoolean(KEY_HAS_FIRST_REFRESH, hasFirstRefresh);
        outState.putParcelable(KEY_LIST_URL_BUILDER, mUrlBuilder);
        outState.putInt(KEY_STATE, mState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mClient = null;
        mUrlBuilder = null;
        mDownloadManager.removeDownloadInfoListener(mDownloadInfoListener);
        mFavouriteStatusRouter.removeListener(mFavouriteStatusRouterListener);
        EventBus.getDefault().unregister(this);
    }

    private void setSearchBarHint(Context context, SearchBar searchBar) {
        Resources resources = context.getResources();
        Drawable searchImage = DrawableManager.getVectorDrawable(context, R.drawable.v_magnify_x24);
        SpannableStringBuilder ssb = new SpannableStringBuilder("   ");
        ssb.append(resources.getString(EhUrl.SITE_EX == Settings.getGallerySite() ?
                R.string.gallery_list_search_bar_hint_exhentai :
                R.string.gallery_list_search_bar_hint_e_hentai));
        int textSize = (int) (searchBar.getEditTextTextSize() * 1.25);
        if (searchImage != null) {
            searchImage.setBounds(0, 0, textSize, textSize);
            ssb.setSpan(new ImageSpan(searchImage), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        searchBar.setEditTextHint(ssb);
    }

    private void setSearchBarSuggestionProvider(SearchBar searchBar) {
        searchBar.setSuggestionProvider(text -> {
            GalleryDetailUrlParser.Result result1 = GalleryDetailUrlParser.parse(text, false);
            if (result1 != null) {
                return Collections.singletonList(new GalleryDetailUrlSuggestion(result1.gid, result1.token));
            }
            GalleryPageUrlParser.Result result2 = GalleryPageUrlParser.parse(text, false);
            if (result2 != null) {
                return Collections.singletonList(new GalleryPageUrlSuggestion(result2.gid, result2.pToken, result2.page));
            }
            return null;
        });
    }

    @Nullable
    private static String getSuitableTitleForUrlBuilder(
            Resources resources, ListUrlBuilder urlBuilder, boolean appName) {
        String keyword = urlBuilder.getKeyword();
        int category = urlBuilder.getCategory();

        if (ListUrlBuilder.MODE_NORMAL == urlBuilder.getMode() &&
                EhUtils.NONE == category &&
                TextUtils.isEmpty(keyword) &&
                urlBuilder.getAdvanceSearch() == -1 &&
                urlBuilder.getMinRating() == -1 &&
                urlBuilder.getPageFrom() == -1 &&
                urlBuilder.getPageTo() == -1) {
            return resources.getString(appName ? R.string.app_name : R.string.homepage);
        } else if (ListUrlBuilder.MODE_SUBSCRIPTION == urlBuilder.getMode() &&
                EhUtils.NONE == category &&
                TextUtils.isEmpty(keyword) &&
                urlBuilder.getAdvanceSearch() == -1 &&
                urlBuilder.getMinRating() == -1 &&
                urlBuilder.getPageFrom() == -1 &&
                urlBuilder.getPageTo() == -1) {
            return resources.getString(R.string.subscription);
        } else if (ListUrlBuilder.MODE_WHATS_HOT == urlBuilder.getMode()) {
            return resources.getString(R.string.whats_hot);
        } else if (!TextUtils.isEmpty(keyword)) {
            return keyword;
        } else if (MathUtils.hammingWeight(category) == 1) {
            return EhUtils.getCategory(category);
        } else {
            return null;
        }
    }

    private String wrapTagKeyword(String keyword) {
        keyword = keyword.trim();

        int index1 = keyword.indexOf(':');
        if (index1 == -1 || index1 >= keyword.length() - 1) {
            // Can't find :, or : is the last char
            return keyword;
        }
        if (keyword.charAt(index1 + 1) == '"') {
            // The char after : is ", the word must be quoted
            return keyword;
        }
        int index2 = keyword.indexOf(' ');
        if (index2 <= index1) {
            // Can't find space, or space is before :
            return keyword;
        }

        return keyword.substring(0, index1 + 1) + "\"" + keyword.substring(index1 + 1) + "$\"";
    }

    // Update search bar title, drawer checked item
    void onUpdateUrlBuilder() {
        ListUrlBuilder builder = mUrlBuilder;
        Resources resources = getResources2();
        if (resources == null || builder == null || mSearchLayout == null) {
            return;
        }

        String keyword = builder.getKeyword();
        int category = builder.getCategory();

        // Update normal search mode
        mSearchLayout.setNormalSearchMode(builder.getMode() == ListUrlBuilder.MODE_SUBSCRIPTION
                ? R.id.search_subscription_search
                : R.id.search_normal_search);

        // Update search edit text
        if (!TextUtils.isEmpty(keyword) && null != mSearchBar) {
            if (builder.getMode() == ListUrlBuilder.MODE_TAG) {
                keyword = wrapTagKeyword(keyword);
            }
            mSearchBar.setText(keyword);
            mSearchBar.cursorToEnd();
        }

        // Update title
        String title = getSuitableTitleForUrlBuilder(resources, builder, true);
        if (null == title) {
            title = resources.getString(R.string.search);
        }
        if (null != mSearchBar) {
            mSearchBar.setTitle(title);
        }

        // Update nav checked item
        int checkedItemId;
        if (ListUrlBuilder.MODE_NORMAL == builder.getMode() &&
                EhUtils.NONE == category &&
                TextUtils.isEmpty(keyword)) {
            checkedItemId = R.id.nav_homepage;
        } else if (ListUrlBuilder.MODE_SUBSCRIPTION == builder.getMode()) {
            checkedItemId = R.id.nav_subscription;
        } else if (ListUrlBuilder.MODE_WHATS_HOT == builder.getMode()) {
            checkedItemId = R.id.nav_whats_hot;
        } else {
            checkedItemId = 0;
        }
        setNavCheckedItem(checkedItemId);
        mNavCheckedId = checkedItemId;
    }

    @NonNull
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_gallery_list, container, false);

        Context context = getEHContext();
        assert context != null;
        AssertUtils.assertNotNull(context);
        Resources resources = context.getResources();

        mHideActionFabSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mShowActionFab = true;

        View mainLayout = ViewUtils.$$(view, R.id.main_layout);
        ContentLayout contentLayout = (ContentLayout) ViewUtils.$$(mainLayout, R.id.content_layout);
        mRecyclerView = contentLayout.getRecyclerView();
        FastScroller fastScroller = contentLayout.getFastScroller();
        RefreshLayout refreshLayout = contentLayout.getRefreshLayout();
        mSearchLayout = (SearchLayout) ViewUtils.$$(mainLayout, R.id.search_layout);
        mSearchBar = (SearchBar) ViewUtils.$$(mainLayout, R.id.search_bar);
        mFabLayout = (FabLayout) ViewUtils.$$(mainLayout, R.id.fab_layout);
        mFloatingActionButton = (FloatingActionButton) ViewUtils.$$(mFabLayout, R.id.tag_filter);

        onFilter(filterOpen, filterTagList.size());

        mSearchFab = ViewUtils.$$(mainLayout, R.id.search_fab);

        int paddingTopSB = resources.getDimensionPixelOffset(R.dimen.gallery_padding_top_search_bar);
        int paddingBottomFab = resources.getDimensionPixelOffset(R.dimen.gallery_padding_bottom_fab);


        mViewTransition = new ViewTransition(contentLayout, mSearchLayout);

        mHelper = new GalleryListHelper();
        contentLayout.setHelper(mHelper);
        contentLayout.getFastScroller().setOnDragHandlerListener(this);

        mAdapter = new GalleryListAdapter(inflater, resources,
                mRecyclerView, Settings.getListMode());

        mAdapter.setThumbItemClickListener(this::onThumbItemClick);
        mRecyclerView.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, androidx.appcompat.R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        mRecyclerView.setDrawSelectorOnTop(true);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setOnItemClickListener(this);
        mRecyclerView.setOnItemLongClickListener(this);
        assert mOnScrollListener != null;
        mRecyclerView.addOnScrollListener(mOnScrollListener);
//        mRecyclerView.setOnGenericMotionListener(this::onGenericMotion);
        fastScroller.setPadding(fastScroller.getPaddingLeft(), fastScroller.getPaddingTop() + paddingTopSB,
                fastScroller.getPaddingRight(), fastScroller.getPaddingBottom());

        refreshLayout.setHeaderTranslationY(paddingTopSB);

        mLeftDrawable = new DrawerArrowDrawable(context, AttrResources.getAttrColor(context, R.attr.drawableColorPrimary));
        mRightDrawable = new AddDeleteDrawable(context, AttrResources.getAttrColor(context, R.attr.drawableColorPrimary));
        mSearchBar.setLeftDrawable(mLeftDrawable);
        mSearchBar.setRightDrawable(mRightDrawable);
        mSearchBar.setHelper(this);
        mSearchBar.setOnStateChangeListener(this);
        setSearchBarHint(context, mSearchBar);
        setSearchBarSuggestionProvider(mSearchBar);

        mSearchLayout.setHelper(this);
        mSearchLayout.setPadding(mSearchLayout.getPaddingLeft(), mSearchLayout.getPaddingTop() + paddingTopSB,
                mSearchLayout.getPaddingRight(), mSearchLayout.getPaddingBottom() + paddingBottomFab);

        mFabLayout.setAutoCancel(true);
        mFabLayout.setExpanded(false);
        mFabLayout.setHidePrimaryFab(false);
        mFabLayout.setOnClickFabListener(this);
        mFabLayout.setOnExpandListener(this);
        addAboveSnackView(mFabLayout);

        mActionFabDrawable = new AddDeleteDrawable(context, resources.getColor(R.color.primary_drawable_dark, null));
        mFabLayout.getPrimaryFab().setImageDrawable(mActionFabDrawable);

        mSearchFab.setOnClickListener(this);

        mSearchBarMover = new SearchBarMover(this, mSearchBar, mRecyclerView, mSearchLayout);

        // Update list url builder
        onUpdateUrlBuilder();

        // Restore state
        int newState = mState;
        mState = STATE_NORMAL;
        setState(newState, false);

        // Only refresh for the first time
        if (!mHasFirstRefresh) {
            mHasFirstRefresh = true;
            mHelper.firstRefresh();
        }

        guideQuickSearch();

        return view;
    }


    private void onThumbItemClick(int position, View view, GalleryInfo gi) {
        LoadImageViewNew thumb = view.findViewById(R.id.thumb_new);
        if (thumb.mFailed) {
            thumb.load();
            return;
        }

        if (popupWindow != null) {
            if (popupWindowPosition == position) {
                popupWindowPosition = -1;
                popupWindow.dismiss();
                return;
            }
            popupWindowPosition = -1;
            popupWindow.dismiss();
        }

        if (gi != null && (gi.tgList == null || gi.tgList.isEmpty())) {
            onItemClick(view, gi);
            return;
        }

        if (position != popupWindowPosition) {

            @SuppressLint("InflateParams") LinearLayout popView = (LinearLayout) getLayoutInflater().inflate(R.layout.list_thumb_popupwindow, null);
            ChipGroup tagFlowLayout = buildChipGroup(gi, popView.findViewById(R.id.tab_tag_flow));

            popupWindow = new PopupWindow(popView, view.getWidth() - thumb.getWidth(), thumb.getHeight());
            popupWindow.setOutsideTouchable(true);
            popupWindow.setAnimationStyle(R.style.PopupWindow);

            tagFlowLayout.setOnClickListener(l -> {
                popupWindowPosition = -1;
                popupWindow.dismiss();
                onItemClick(view, gi);
            });
            tagFlowLayout.setOnLongClickListener(l -> onItemLongClick(gi, view));
            int[] location = new int[2];
            thumb.getLocationOnScreen(location);
            popupWindow.showAtLocation(thumb, Gravity.NO_GRAVITY, location[0] + thumb.getWidth(), location[1]);
            popupWindowPosition = position;
        }
    }

    private ChipGroup buildChipGroup(GalleryInfo gi, ChipGroup tagFlowLayout) {
        int colorTag = AttrResources.getAttrColor(getContext(), R.attr.tagBackgroundColor);
        if (null == gi.tgList) {
            String tagName = "暂无预览标签";
            @SuppressLint("InflateParams") Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_tag, null);
            chip.setChipBackgroundColor(ColorStateList.valueOf(colorTag));
            chip.setTextColor(Color.WHITE);
            if (Settings.getShowTagTranslations()) {
                if (ehTags == null) {
                    ehTags = EhTagDatabase.getInstance(getContext());
                }
                chip.setText(TagTranslationUtil.getTagCNBody(tagName.split(":"), ehTags));
            } else {
                String[] tagSplit = tagName.split(":");
                chip.setText(tagSplit.length > 1 ? tagSplit[1] : tagSplit[0]);
            }
            tagFlowLayout.addView(chip, 0);
            return tagFlowLayout;
        }
        for (int i = 0; i < gi.tgList.size(); i++) {
            String tagName = gi.tgList.get(i);
            @SuppressLint("InflateParams") Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_tag, null);
            chip.setChipBackgroundColor(ColorStateList.valueOf(colorTag));
            chip.setTextColor(Color.WHITE);
            if (Settings.getShowTagTranslations()) {
                if (ehTags == null) {
                    ehTags = EhTagDatabase.getInstance(getContext());
                }
                chip.setText(TagTranslationUtil.getTagCNBody(tagName.split(":"), ehTags));
            } else {
                chip.setText(tagName.split(":")[1]);
            }
            chip.setOnClickListener(l -> onTagClick(tagName));
            chip.setOnLongClickListener(l -> onTagLongClick(tagName));
            tagFlowLayout.addView(chip, i);
        }

        return tagFlowLayout;
    }

    private boolean onTagLongClick(String tagName) {
        GalleryListSecenDialog dialog = new GalleryListSecenDialog(this);
        dialog.setTagName(tagName);
        dialog.showTagLongPressDialog();
        return true;
    }


    private void onTagClick(String tagName) {
        if (isDrawersVisible()) {
            closeDrawer(Gravity.RIGHT);
        }
        if (null == mHelper || null == mUrlBuilder) {
            return;
        }
        popupWindowPosition = -1;
        if (null != popupWindow) {
            popupWindow.dismiss();
        }

        if (null != alertDialog) {
            alertDialog.dismiss();
        }

        if (filterOpen) {
            mUrlBuilder.set(searchTagBuild(tagName), ListUrlBuilder.MODE_FILTER);
            onFilter(filterOpen, filterTagList.size());
        } else {
            mUrlBuilder.set(tagName);
        }

        mUrlBuilder.setPageIndex(0);
        onUpdateUrlBuilder();
        mHelper.refresh();
        setState(STATE_NORMAL);
    }

    private String searchTagBuild(String tagName) {

        String[] list = tagName.split(":");

        String key;
        if (list.length == 2) {
            key = list[1];
        } else {
            key = list[0];
        }

        if (!filterTagList.contains(key)) {
            filterTagList.add(key);
        }
        return listToString(filterTagList);
    }

    private String listToString(List<String> list) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i == 0) {
                result.append(list.get(i));
            } else {
                result.append("  ").append(list.get(i));
            }
        }
        return result.toString();
    }

    private void guideQuickSearch() {
        Activity activity = getActivity2();
        if (null == activity || !Settings.getGuideQuickSearch()) {
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
                .setContentTitle(R.string.guide_quick_search_title)
                .setContentText(R.string.guide_quick_search_text)
                .replaceEndButton(R.layout.button_guide)
                .setShowcaseEventListener(new SimpleShowcaseEventListener() {
                    @SuppressLint("RtlHardcoded")
                    @Override
                    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
                        mShowcaseView = null;
                        ViewUtils.removeFromParent(showcaseView);
                        Settings.putGuideQuickSearch(false);
                        openDrawer(Gravity.RIGHT);
                    }
                }).build();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView);
            mShowcaseView = null;
        }
        if (null != mSearchBarMover) {
            mSearchBarMover.cancelAnimation();
            mSearchBarMover = null;
        }
        if (null != mHelper) {
            mHelper.destroy();
            if (1 == mHelper.getShownViewIndex()) {
                mHasFirstRefresh = false;
            }
        }
        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout);
            mFabLayout = null;
        }

        mAdapter = null;
        mSearchLayout = null;
        mSearchBar = null;
        mSearchFab = null;
        mViewTransition = null;
        mLeftDrawable = null;
        mRightDrawable = null;
        mActionFabDrawable = null;
    }

    void showQuickSearchTipDialog(final List<QuickSearch> list,
                                  final ArrayAdapter<QuickSearch> adapter, final ListView listView, final TextView tip) {
        Context context = getEHContext();
        if (null == context) {
            return;
        }
        final CheckBoxDialogBuilder builder = new CheckBoxDialogBuilder(
                context, getString(R.string.add_quick_search_tip), getString(R.string.get_it), false);
        builder.setTitle(R.string.readme);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            if (builder.isChecked()) {
                Settings.putQuickSearchTip(false);
            }
            showAddQuickSearchDialog(list, adapter, listView, tip);
        }).show();
    }

    void showAddQuickSearchDialog(final List<QuickSearch> list,
                                  final ArrayAdapter<QuickSearch> adapter, final ListView listView, final TextView tip) {
        Context context = getEHContext();
        final ListUrlBuilder urlBuilder = mUrlBuilder;
        if (null == context || null == urlBuilder) {
            return;
        }

        // Can't add image search as quick search
        if (ListUrlBuilder.MODE_IMAGE_SEARCH == urlBuilder.getMode()) {
            showTip(R.string.image_search_not_quick_search, LENGTH_LONG);
            return;
        }

        // Check duplicate
        for (QuickSearch q : list) {
            if (urlBuilder.equalsQuickSearch(q)) {
                showTip(getString(R.string.duplicate_quick_search, q.name), LENGTH_LONG);
                return;
            }
        }

        final EditTextDialogBuilder builder = new EditTextDialogBuilder(context,
                getSuitableTitleForUrlBuilder(context.getResources(), urlBuilder, false), getString(R.string.quick_search));
        builder.setTitle(R.string.add_quick_search_dialog_title);
        builder.setPositiveButton(android.R.string.ok, null);
        final AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = builder.getText().trim();

            // Check name empty
            if (TextUtils.isEmpty(text)) {
                builder.setError(getString(R.string.name_is_empty));
                return;
            }

            // Check name duplicate
            for (QuickSearch q : list) {
                if (text.equals(q.name)) {
                    builder.setError(getString(R.string.duplicate_name));
                    return;
                }
            }

            builder.setError(null);
            dialog.dismiss();
            QuickSearch quickSearch = urlBuilder.toQuickSearch();

            //汉化or不汉化
            if (Settings.getShowTagTranslations()) {
                if (ehTags == null) {
                    ehTags = EhTagDatabase.getInstance(context);
                }
                //根据‘：’分割字符串为组名和标签名
                //String[] tags = text.split(":");
                //quickSearch.name = TagTranslationUtil.getTagCN(tags, ehTags);
                String[] parts = text.split("(?=(?:(?:[^\"]*\"){2})*[^\"]*$)\\s+");
                String newText = "";
                for (int i = 0; i < parts.length; i++) {
                    String[] tags = parts[i].split(":");
                    for (int j = 0; j < tags.length; j++) {
                        tags[j] = tags[j].replace("\"", "").replace("$", "");
                    }
                    quickSearch.name = TagTranslationUtil.getTagCN(tags, ehTags);
                    if (newText.isEmpty()) {
                        newText = TagTranslationUtil.getTagCN(tags, ehTags);
                    } else {
                        newText += " " + TagTranslationUtil.getTagCN(tags, ehTags);
                    }
                }
                quickSearch.name = newText;
            } else {
                quickSearch.name = text;
            }
            EhDB.insertQuickSearch(quickSearch);
            list.add(quickSearch);
            adapter.notifyDataSetChanged();

            if (0 == list.size()) {
                tip.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
            } else {
                tip.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
            }
        });
    }

    @SuppressLint({"RtlHardcoded", "NonConstantResourceId"})
    @Override
    public View onCreateDrawerView(LayoutInflater inflater, @Nullable ViewGroup container,
                                   @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.drawer_list, container, false);

        drawPager = view.findViewById(R.id.drawer_list_pager);

        bookmarksView = bookmarksViewBuild(inflater);
        subscriptionView = subscriptionViewBuild(inflater);

        List<View> views = new ArrayList<>();

        views.add(bookmarksView);
        views.add(subscriptionView);

        DrawViewPagerAdapter pagerAdapter = new DrawViewPagerAdapter(views);

        drawPager.setAdapter(pagerAdapter);

        return view;
    }

    @SuppressLint({"RtlHardcoded", "NonConstantResourceId"})
    private View bookmarksViewBuild(LayoutInflater inflater) {
        Context context = getEHContext();
        if (context == null) {
            return new View(null);
        }
        mBookmarksDraw = new BookmarksDraw(getEHContext(), inflater, ehTags);
        return mBookmarksDraw.onCreate(this);
    }

    private View subscriptionViewBuild(LayoutInflater inflater) {
        mSubscriptionDraw = new SubscriptionDraw(getEHContext(), inflater, mClient, getTag(), ehTags);
        return mSubscriptionDraw.onCreate(drawPager, getActivity2(), this);
    }

    @Override
    public void onSubscriptionItemClick(String name) {
        onTagClick(name);
    }

    @Override
    public String getAddTagName(UserTagList userTagList) {
        Context context = getEHContext();
        final ListUrlBuilder urlBuilder = mUrlBuilder;
        if (null == context || null == urlBuilder) {
            return null;
        }

        // Can't add image search as quick search
        if (ListUrlBuilder.MODE_IMAGE_SEARCH == urlBuilder.getMode()) {
            showTip(R.string.image_search_not_quick_search, LENGTH_LONG);
            return null;
        }

        if (urlBuilder.getKeyword() == null) {
            return null;
        }

        if (userTagList == null || userTagList.userTags == null) {
            return getSuitableTitleForUrlBuilder(getEHContext().getResources(), urlBuilder, false);
        }
        // Check duplicate
        for (UserTag q : userTagList.userTags) {
            if (urlBuilder.equalKeyWord(q.tagName)) {
                showTip(getString(R.string.duplicate_quick_search, q.tagName), LENGTH_LONG);
                return null;
            }
        }
        return getSuitableTitleForUrlBuilder(getEHContext().getResources(), urlBuilder, false);
    }

    /**
     * eventBus 通知书签数据修改
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQuickSearchDataChanged(SomethingNeedRefresh refresh) {
        if (!refresh.isBookmarkDrawNeed()) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        bookmarksView = bookmarksViewBuild(inflater);
        if (subscriptionView == null) {
            subscriptionView = subscriptionViewBuild(inflater);
        }
        List<View> views = new ArrayList<>();

        views.add(bookmarksView);
        views.add(subscriptionView);

        DrawViewPagerAdapter pagerAdapter = new DrawViewPagerAdapter(views);

        drawPager.setAdapter(pagerAdapter);
    }

    private boolean checkDoubleClickExit() {
        if (getStackIndex() != 0) {
            return false;
        }

        long time = System.currentTimeMillis();
        if (time - mPressBackTime > BACK_PRESSED_INTERVAL) {
            // It is the last scene
            mPressBackTime = time;
            showTip(R.string.press_twice_exit, LENGTH_SHORT);
            return true;
        } else {
            return false;
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mBookmarksDraw == null) {
            return;
        }
        mBookmarksDraw.resume();
        if (mSubscriptionDraw == null) {
            return;
        }
        mSubscriptionDraw.resume();
    }

    @Override
    public void onBackPressed() {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
        if (null != mShowcaseView) {
            return;
        }

        if (null != mFabLayout && mFabLayout.isExpanded()) {
            mFabLayout.setExpanded(false);
            return;
        }

        if (filterOpen && filterTagList.size() > 1) {
            filterTagList.remove(filterTagList.size() - 1);
            mUrlBuilder.set(listToString(filterTagList), ListUrlBuilder.MODE_FILTER);
            onFilter(filterOpen, filterTagList.size());

            mUrlBuilder.setPageIndex(0);
            onUpdateUrlBuilder();
            mHelper.refresh();
            setState(STATE_NORMAL);
            return;
        }

        boolean handle;
        switch (mState) {
            default:
            case STATE_NORMAL:
                handle = checkDoubleClickExit();
                break;
            case STATE_SIMPLE_SEARCH:
            case STATE_SEARCH:
                setState(STATE_NORMAL);
                handle = true;
                break;
            case STATE_SEARCH_SHOW_LIST:
                setState(STATE_SEARCH);
                handle = true;
                break;
        }

        if (!handle) {
            finish();
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        return onItemClick(view, mHelper.getDataAtEx(position));
    }

    public boolean onItemClick(View view, GalleryInfo gi) {
        if (null == mHelper || null == mRecyclerView) {
            return false;
        }
        if (gi == null) {
            return true;
        }
        if (null != alertDialog) {
            alertDialog.dismiss();
        }

        Bundle args = new Bundle();
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, gi);
        Announcer announcer = new Announcer(GalleryDetailScene.class).setArgs(args);
        View thumb;
        if (null != view && null != (thumb = view.findViewById(R.id.thumb))) {
            announcer.setTranHelper(new EnterGalleryDetailTransaction(thumb));
        }
        startScene(announcer);
        return true;
    }

    @Override
    public boolean onItemLongClick(EasyRecyclerView parent, View view, int position, long id) {
        assert mHelper != null;
        return onItemLongClick(mHelper.getDataAtEx(position), view);
    }

    public boolean onItemLongClick(GalleryInfo gi, View view) {
        final Context context = getEHContext();
        final MainActivity activity = getActivity2();
        if (null == context || null == activity || null == mHelper) {
            return false;
        }

        if (gi == null) {
            return true;
        }

        boolean downloaded = mDownloadManager.getDownloadState(gi.gid) != DownloadInfo.STATE_INVALID;
        boolean favourited = gi.favoriteSlot != -2;

        CharSequence[] items = new CharSequence[]{
                context.getString(R.string.read),
                context.getString(downloaded ? R.string.delete_downloads : R.string.download),
                context.getString(favourited ? R.string.remove_from_favourites : R.string.add_to_favourites),
        };

        int[] icons = new int[]{
                R.drawable.v_book_open_x24,
                downloaded ? R.drawable.v_delete_x24 : R.drawable.v_download_x24,
                favourited ? R.drawable.v_heart_broken_x24 : R.drawable.v_heart_x24,
        };

        @SuppressLint("InflateParams") LinearLayout linearLayout = (LinearLayout) getLayoutInflater2().inflate(R.layout.gallery_item_dialog_coustom_title, null);

        linearLayout.setOnClickListener(l -> onItemClick(view, gi));

        LoadImageViewNew imageViewNew = linearLayout.findViewById(R.id.dialog_thumb);

        imageViewNew.load(EhCacheKeyFactory.getThumbKey(gi.gid), gi.thumb);

        imageViewNew.setOnClickListener(l -> onItemClick(view, gi));

        buildChipGroup(gi, linearLayout.findViewById(R.id.tab_tag_flow));

        TextView textView = linearLayout.findViewById(R.id.title_text);
        textView.setText(EhUtils.getSuitableTitle(gi));
        textView.setOnClickListener(l -> {
            AppHelper.copyPlainText(EhUtils.getSuitableTitle(gi), getEHContext());
            Toast toast = Toast.makeText(getEHContext(), "标题文本已复制", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        });


        alertDialog = new AlertDialog.Builder(context)
//                .setTitle(EhUtils.getSuitableTitle(gi))
//                .setView(imageViewNew)
                .setCustomTitle(linearLayout)
                .setAdapter(new SelectItemWithIconAdapter(context, items, icons), (dialog, which) -> {
                    switch (which) {
                        case 0: // Read
                            Intent intent = new Intent(activity, GalleryActivity.class);
                            intent.setAction(GalleryActivity.ACTION_EH);
                            intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, gi);
                            startActivity(intent);
                            break;
                        case 1: // Download
                            if (downloaded) {
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.download_remove_dialog_title)
                                        .setMessage(getString(R.string.download_remove_dialog_message, gi.title))
                                        .setPositiveButton(android.R.string.ok, (dialog1, which1) -> mDownloadManager.deleteDownload(gi.gid))
                                        .show();
                            } else {
                                CommonOperations.startDownload(activity, gi, false);
                            }
                            break;
                        case 2: // Favorites
                            if (favourited) {
                                CommonOperations.removeFromFavorites(activity, gi, new RemoveFromFavoriteListener(context, activity.getStageId(), getTag()));
                            } else {
                                CommonOperations.addToFavorites(activity, gi, new AddToFavoriteListener(context, activity.getStageId(), getTag()),false);
                            }
                            break;
                    }
                }).show();
        return true;
    }


    @Override
    public void onClick(View v) {
        if (STATE_NORMAL != mState && null != mSearchBar) {
            mSearchBar.applySearch(false);
            hideSoftInput();
        }
    }

    @Override
    public void onClickPrimaryFab(FabLayout view, FloatingActionButton fab) {
        if (STATE_NORMAL == mState) {
            view.toggle();
        }
    }

    private void showGoToDialog() {
        Context context = getEHContext();

        if (null == context || null == mHelper) {
            return;
        }

        if (mHelper.mPages < 0) {
            showDateJumpDialog(context);
        } else {
            showPageJumpDialog(context);
        }
    }

    private void showDateJumpDialog(Context context) {
        if (mHelper == null) {
            return;
        }
        if (mHelper.nextHref == null || mHelper.nextHref.isEmpty()) {
//            if ((mHelper.nextHref == null || mHelper.nextHref.isEmpty())&&(mHelper.prevHref == null || mHelper.prevHref.isEmpty())) {
            Toast.makeText(getEHContext(), R.string.gallery_list_no_more_data, Toast.LENGTH_LONG).show();
            return;
        }
        if (jumpSelectorDialog == null) {
            LinearLayout linearLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.gallery_list_date_jump_dialog, null);
            mJumpDateSelector = linearLayout.findViewById(R.id.gallery_list_jump_date);
            mJumpDateSelector.setOnTimeSelectedListener(this::onTimeSelected);
            jumpSelectorDialog = new AlertDialog.Builder(context).setView(linearLayout).create();
        }
        mJumpDateSelector.setFoundMessage(mHelper.resultCount);
        jumpSelectorDialog.show();
    }

    private void showPageJumpDialog(Context context) {
        final int page = mHelper.getPageForTop();
        final int pages = mHelper.getPages();
        String hint = getString(R.string.go_to_hint, page + 1, pages);
        final EditTextDialogBuilder builder = new EditTextDialogBuilder(context, null, hint);
        builder.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final AlertDialog dialog = builder.setTitle(R.string.go_to)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (null == mHelper) {
                dialog.dismiss();
                return;
            }

            String text = builder.getText().trim();
            int goTo;
            try {
                goTo = Integer.parseInt(text) - 1;
            } catch (NumberFormatException e) {
                builder.setError(getString(R.string.error_invalid_number));
                return;
            }
            if (goTo < 0 || goTo >= pages) {
                builder.setError(getString(R.string.error_out_of_range));
                return;
            }
            builder.setError(null);
            mHelper.goTo(goTo);
            AppHelper.hideSoftInput(dialog);
            dialog.dismiss();
        });
    }

    private void onTimeSelected(String urlAppend) {
        Log.d(TAG, urlAppend);
        if (urlAppend.isEmpty() || mHelper == null || jumpSelectorDialog == null || mUrlBuilder == null) {
            return;
        }
        jumpSelectorDialog.dismiss();
        mHelper.nextHref = mUrlBuilder.jumpHrefBuild(mHelper.nextHref, urlAppend);
        mHelper.goTo(-996);
    }

    @Override
    public void onClickSecondaryFab(FabLayout view, FloatingActionButton fab, int position) {
        if (null == mHelper) {
            return;
        }

        switch (position) {
            case 0: // 开启\关闭多标签搜索
                filterOpen = !filterOpen;
                onFilter(filterOpen, filterTagList.size());
                break;
            case 1: // Go to
                if (mHelper.canGoTo()) {
                    if (mUrlBuilder != null && mUrlBuilder.getMode() == ListUrlBuilder.MODE_TOP_LIST)
                        break;
                    showGoToDialog();
                }
                break;
            case 2: // Refresh
                mHelper.refresh();
                break;
            case 3:
                List<GalleryInfo> gInfoL = mHelper.getData();
                if (gInfoL == null || gInfoL.isEmpty()) {
                    return;
                }
                onItemClick(null, gInfoL.get((int) (Math.random() * gInfoL.size())));
                break;
        }

        view.setExpanded(false);
    }

    public void onFilter(boolean open, int num) {
        if (null == mFloatingActionButton) {
            return;
        }
        if (!open) {
            mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_none_24);
            filterTagList.clear();
            return;
        }

        switch (num) {
            case 0:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_24);
                break;
            case 1:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_1_24);
                break;
            case 2:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_2_24);
                break;
            case 3:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_3_24);
                break;
            case 4:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_4_24);
                break;
            case 5:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_5_24);
                break;
            case 6:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_6_24);
                break;
            case 7:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_7_24);
                break;
            case 8:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_8_24);
                break;
            case 9:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_9_24);
                break;
            default:
                mFloatingActionButton.setImageResource(R.drawable.ic_baseline_filter_9_plus_24);
                break;
        }

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

    private void showActionFab() {
        if (null != mFabLayout && STATE_NORMAL == mState && !mShowActionFab) {
            mShowActionFab = true;
            View fab = mFabLayout.getPrimaryFab();
            fab.setVisibility(View.VISIBLE);
            fab.setRotation(-45.0f);
            fab.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                    .setDuration(ANIMATE_TIME).setStartDelay(0L)
                    .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR).start();
        }
    }

    private void hideActionFab() {
        if (null != mFabLayout && STATE_NORMAL == mState && mShowActionFab) {
            mShowActionFab = false;
            View fab = mFabLayout.getPrimaryFab();
            fab.animate().scaleX(0.0f).scaleY(0.0f).setListener(mActionFabAnimatorListener)
                    .setDuration(ANIMATE_TIME).setStartDelay(0L)
                    .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR).start();
        }
    }

    private void selectSearchFab(boolean animation) {
        if (null == mFabLayout || null == mSearchFab) {
            return;
        }

        mShowActionFab = false;

        if (animation) {
            View fab = mFabLayout.getPrimaryFab();
            long delay;
            if (View.INVISIBLE == fab.getVisibility()) {
                delay = 0L;
            } else {
                delay = ANIMATE_TIME;
                fab.animate().scaleX(0.0f).scaleY(0.0f).setListener(mActionFabAnimatorListener)
                        .setDuration(ANIMATE_TIME).setStartDelay(0L)
                        .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR).start();
            }
            mSearchFab.setVisibility(View.VISIBLE);
            mSearchFab.setRotation(-45.0f);
            mSearchFab.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                    .setDuration(ANIMATE_TIME).setStartDelay(delay)
                    .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR).start();
        } else {
            mFabLayout.setExpanded(false, false);
            View fab = mFabLayout.getPrimaryFab();
            fab.setVisibility(View.INVISIBLE);
            fab.setScaleX(0.0f);
            fab.setScaleY(0.0f);
            mSearchFab.setVisibility(View.VISIBLE);
            mSearchFab.setScaleX(1.0f);
            mSearchFab.setScaleY(1.0f);
        }
    }

    private void selectActionFab(boolean animation) {
        if (null == mFabLayout || null == mSearchFab) {
            return;
        }

        mShowActionFab = true;

        if (animation) {
            long delay;
            if (View.INVISIBLE == mSearchFab.getVisibility()) {
                delay = 0L;
            } else {
                delay = ANIMATE_TIME;
                mSearchFab.animate().scaleX(0.0f).scaleY(0.0f).setListener(mSearchFabAnimatorListener)
                        .setDuration(ANIMATE_TIME).setStartDelay(0L)
                        .setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR).start();
            }
            View fab = mFabLayout.getPrimaryFab();
            fab.setVisibility(View.VISIBLE);
            fab.setRotation(-45.0f);
            fab.animate().scaleX(1.0f).scaleY(1.0f).rotation(0.0f).setListener(null)
                    .setDuration(ANIMATE_TIME).setStartDelay(delay)
                    .setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR).start();

        } else {
            mFabLayout.setExpanded(false, false);
            View fab = mFabLayout.getPrimaryFab();
            fab.setVisibility(View.VISIBLE);
            fab.setScaleX(1.0f);
            fab.setScaleY(1.0f);
            mSearchFab.setVisibility(View.INVISIBLE);
            mSearchFab.setScaleX(0.0f);
            mSearchFab.setScaleY(0.0f);
        }
    }

    void setState(@State int state) {
        setState(state, true);
    }

    private void setState(@State int state, boolean animation) {
        if (null == mSearchBar || null == mSearchBarMover ||
                null == mViewTransition || null == mSearchLayout) {
            return;
        }

        if (mState != state) {
            int oldState = mState;
            mState = state;

            switch (oldState) {
                case STATE_NORMAL:
                    if (state == STATE_SIMPLE_SEARCH) {
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectSearchFab(animation);
                    } else if (state == STATE_SEARCH) {
                        mViewTransition.showView(1, animation);
                        mSearchLayout.scrollSearchContainerToTop();
                        mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectSearchFab(animation);
                    } else if (state == STATE_SEARCH_SHOW_LIST) {
                        mViewTransition.showView(1, animation);
                        mSearchLayout.scrollSearchContainerToTop();
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectSearchFab(animation);
                    }
                    break;
                case STATE_SIMPLE_SEARCH:
                    if (state == STATE_NORMAL) {
                        mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectActionFab(animation);
                    } else if (state == STATE_SEARCH) {
                        mViewTransition.showView(1, animation);
                        mSearchLayout.scrollSearchContainerToTop();
                        mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    } else if (state == STATE_SEARCH_SHOW_LIST) {
                        mViewTransition.showView(1, animation);
                        mSearchLayout.scrollSearchContainerToTop();
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    }
                    break;
                case STATE_SEARCH:
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0, animation);
                        mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectActionFab(animation);
                    } else if (state == STATE_SIMPLE_SEARCH) {
                        mViewTransition.showView(0, animation);
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    } else if (state == STATE_SEARCH_SHOW_LIST) {
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    }
                    break;
                case STATE_SEARCH_SHOW_LIST:
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0, animation);
                        mSearchBar.setState(SearchBar.STATE_NORMAL, animation);
                        mSearchBarMover.returnSearchBarPosition();
                        selectActionFab(animation);
                    } else if (state == STATE_SIMPLE_SEARCH) {
                        mViewTransition.showView(0, animation);
                        mSearchBar.setState(SearchBar.STATE_SEARCH_LIST, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    } else if (state == STATE_SEARCH) {
                        mSearchBar.setState(SearchBar.STATE_SEARCH, animation);
                        mSearchBarMover.returnSearchBarPosition();
                    }
                    break;
            }
        }
    }

    @Override
    public void onClickTitle() {
        if (mState == STATE_NORMAL) {
            setState(STATE_SIMPLE_SEARCH);
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onClickLeftIcon() {
        if (null == mSearchBar) {
            return;
        }

        if (mSearchBar.getState() == SearchBar.STATE_NORMAL) {
            toggleDrawer(Gravity.LEFT);
        } else {
            setState(STATE_NORMAL);
        }
    }

    @Override
    public void onClickRightIcon() {
        if (null == mSearchBar) {
            return;
        }

        if (mSearchBar.getState() == SearchBar.STATE_NORMAL) {
            setState(STATE_SEARCH);
        } else {
            // Clear
            mSearchBar.setText("");
        }
    }

    @Override
    public void onSearchEditTextClick() {
        if (mState == STATE_SEARCH) {
            setState(STATE_SEARCH_SHOW_LIST);
        }
    }

    @Override
    public void onApplySearch(String query) {
        if (null == mUrlBuilder || null == mHelper || null == mSearchLayout) {
            return;
        }

        if (mState == STATE_SEARCH || mState == STATE_SEARCH_SHOW_LIST) {
            try {
                mSearchLayout.formatListUrlBuilder(mUrlBuilder, query);
            } catch (EhException e) {
                showTip(e.getMessage(), LENGTH_LONG);
                return;
            }
        } else {
            int oldMode = mUrlBuilder.getMode();
            // If it's MODE_SUBSCRIPTION, keep it
            int newMode = oldMode == ListUrlBuilder.MODE_SUBSCRIPTION
                    ? ListUrlBuilder.MODE_SUBSCRIPTION
                    : ListUrlBuilder.MODE_NORMAL;
            mUrlBuilder.reset();
            mUrlBuilder.setMode(newMode);
            mUrlBuilder.setKeyword(query);
        }
        onUpdateUrlBuilder();
        mHelper.refresh();
        setState(STATE_NORMAL);
    }

    @Override
    public void onSearchEditTextBackPressed() {
        onBackPressed();
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onEndDragHandler() {
        // Restore right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);

        if (null != mSearchBarMover) {
            mSearchBarMover.returnSearchBarPosition();
        }
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onStateChange(SearchBar searchBar, int newState, int oldState, boolean animation) {
        if (null == mLeftDrawable || null == mRightDrawable) {
            return;
        }

        switch (oldState) {
            default:
            case SearchBar.STATE_NORMAL:
                mLeftDrawable.setArrow(animation ? ANIMATE_TIME : 0);
                mRightDrawable.setDelete(animation ? ANIMATE_TIME : 0);
                break;
            case SearchBar.STATE_SEARCH:
                if (newState == SearchBar.STATE_NORMAL) {
                    mLeftDrawable.setMenu(animation ? ANIMATE_TIME : 0);
                    mRightDrawable.setAdd(animation ? ANIMATE_TIME : 0);
                }
                break;
            case SearchBar.STATE_SEARCH_LIST:
                if (newState == STATE_NORMAL) {
                    mLeftDrawable.setMenu(animation ? ANIMATE_TIME : 0);
                    mRightDrawable.setAdd(animation ? ANIMATE_TIME : 0);
                }
                break;
        }

        if (newState == STATE_NORMAL || newState == STATE_SIMPLE_SEARCH) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
        }
    }

    @Override
    public void onChangeSearchMode() {
        if (null != mSearchBarMover) {
            mSearchBarMover.showSearchBar();
        }
    }

    @Override
    public void onSelectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
//        ActivityResultContracts.StartActivityForResult(Intent.createChooser(intent,
//                getString(R.string.select_image)), REQUEST_CODE_SELECT_IMAGE);
        startActivityForResult(Intent.createChooser(intent,
                getString(R.string.select_image)), REQUEST_CODE_SELECT_IMAGE);
    }

    // SearchBarMover.Helper
    @Override
    public boolean isValidView(RecyclerView recyclerView) {
        return (mState == STATE_NORMAL && recyclerView == mRecyclerView) ||
                (mState == STATE_SEARCH && recyclerView == mSearchLayout);
    }

    // SearchBarMover.Helper
    @Override
    public RecyclerView getValidRecyclerView() {
        if (mState == STATE_NORMAL || mState == STATE_SIMPLE_SEARCH) {
            return mRecyclerView;
        } else {
            return mSearchLayout;
        }
    }

    // SearchBarMover.Helper
    @Override
    public boolean forceShowSearchBar() {
        return mState == STATE_SIMPLE_SEARCH || mState == STATE_SEARCH_SHOW_LIST;
    }

    public static void startScene(SceneFragment scene, ListUrlBuilder lub) {
        scene.startScene(getStartAnnouncer(lub));
    }

    public static Announcer getStartAnnouncer(ListUrlBuilder lub) {
        Bundle args = new Bundle();
        args.putString(KEY_ACTION, ACTION_LIST_URL_BUILDER);
        args.putParcelable(KEY_LIST_URL_BUILDER, lub);
        return new Announcer(GalleryListScene.class).setArgs(args);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_CODE_SELECT_IMAGE == requestCode) {
            if (Activity.RESULT_OK == resultCode && null != mSearchLayout && null != data) {
                mSearchLayout.setImageUri(data.getData());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void onGetGalleryListSuccess(GalleryListParser.Result result, int taskId) {
        if (mHelper != null && mSearchBarMover != null &&
                mHelper.isCurrentTask(taskId)) {
            String emptyString = getResources2().getString(mUrlBuilder.getMode() == ListUrlBuilder.MODE_SUBSCRIPTION && result.noWatchedTags
                    ? R.string.gallery_list_empty_hit_subscription
                    : R.string.gallery_list_empty_hit);
            mHelper.setEmptyString(emptyString);
//            mHelper.onGetPageData(taskId, result.pages, result.nextPage, result.galleryInfoList);
            mHelper.onGetPageData(taskId, result, result.galleryInfoList);
        }
    }

    private void onGetGalleryListFailure(Exception e, int taskId) {
        if (mHelper != null && mSearchBarMover != null &&
                mHelper.isCurrentTask(taskId)) {
            mHelper.onGetException(taskId, e);
        }
    }

    private abstract class UrlSuggestion extends SearchBar.Suggestion {
        @Override
        public CharSequence getText(float textSize) {
            Drawable bookImage = DrawableManager.getVectorDrawable(getEHContext(), R.drawable.v_book_open_x24);
            SpannableStringBuilder ssb = new SpannableStringBuilder("    ");
            ssb.append(getResources2().getString(R.string.gallery_list_search_bar_open_gallery));
            int imageSize = (int) (textSize * 1.25);
            if (bookImage != null) {
                bookImage.setBounds(0, 0, imageSize, imageSize);
                ssb.setSpan(new ImageSpan(bookImage), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return ssb;
        }

        @Override
        public void onClick() {
            startScene(createAnnouncer());

            if (mState == STATE_SIMPLE_SEARCH) {
                setState(STATE_NORMAL);
            } else if (mState == STATE_SEARCH_SHOW_LIST) {
                setState(STATE_SEARCH);
            }
        }

        public abstract Announcer createAnnouncer();

        @Override
        public void onLongClick() {
        }
    }

    private class GalleryDetailUrlSuggestion extends UrlSuggestion {
        private final long mGid;
        private final String mToken;

        private GalleryDetailUrlSuggestion(long gid, String token) {
            mGid = gid;
            mToken = token;
        }

        @Override
        public Announcer createAnnouncer() {
            Bundle args = new Bundle();
            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN);
            args.putLong(GalleryDetailScene.KEY_GID, mGid);
            args.putString(GalleryDetailScene.KEY_TOKEN, mToken);
            return new Announcer(GalleryDetailScene.class).setArgs(args);
        }

        @Override
        public CharSequence getText(TextView textView) {
            return null;
        }
    }

    private class GalleryPageUrlSuggestion extends UrlSuggestion {
        private final long mGid;
        private final String mPToken;
        private final int mPage;

        private GalleryPageUrlSuggestion(long gid, String pToken, int page) {
            mGid = gid;
            mPToken = pToken;
            mPage = page;
        }

        @Override
        public Announcer createAnnouncer() {
            Bundle args = new Bundle();
            args.putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN);
            args.putLong(ProgressScene.KEY_GID, mGid);
            args.putString(ProgressScene.KEY_PTOKEN, mPToken);
            args.putInt(ProgressScene.KEY_PAGE, mPage);
            return new Announcer(ProgressScene.class).setArgs(args);
        }

        @Override
        public CharSequence getText(TextView textView) {
            return null;
        }
    }

    private class GalleryListAdapter extends GalleryAdapterNew {

        public GalleryListAdapter(@NonNull LayoutInflater inflater,
                                  @NonNull Resources resources, @NonNull RecyclerView recyclerView, int type) {
            super(inflater, resources, recyclerView, type, true, executorService, showReadProgress);
        }

        @Override
        public int getItemCount() {
            return null != mHelper ? mHelper.size() : 0;
        }

        @Nullable
        @Override
        public GalleryInfo getDataAt(int position) {
            return null != mHelper ? mHelper.getDataAtEx(position) : null;
        }

    }

    class GalleryListHelper extends GalleryInfoContentHelper {

        @Override
        protected void getPageData(int taskId, int type, int page) {
            MainActivity activity = getActivity2();
            if (null == activity || null == mClient || null == mUrlBuilder) {
                return;
            }

            mUrlBuilder.setPageIndex(page);
            if (ListUrlBuilder.MODE_IMAGE_SEARCH == mUrlBuilder.getMode()) {
                EhRequest request = new EhRequest();
                request.setMethod(EhClient.METHOD_IMAGE_SEARCH);
                request.setCallback(new GetGalleryListListener(getContext(),
                        activity.getStageId(), getTag(), taskId));
                request.setArgs(new File(StringUtils.avoidNull(mUrlBuilder.getImagePath())),
                        mUrlBuilder.isUseSimilarityScan(),
                        mUrlBuilder.isOnlySearchCovers(), mUrlBuilder.isShowExpunged());
                mClient.execute(request);
            } else {
                String url = mUrlBuilder.build();
                EhRequest request = new EhRequest();
                request.setMethod(EhClient.METHOD_GET_GALLERY_LIST);
                request.setCallback(new GetGalleryListListener(getContext(),
                        activity.getStageId(), getTag(), taskId));
                request.setArgs(url, mUrlBuilder.getMode());
                mClient.execute(request);
            }
        }

        @Override
        protected void getExPageData(int pageAction, int taskId, int page) {
            MainActivity activity = getActivity2();
            if (null == activity || null == mClient || null == mUrlBuilder) {
                return;
            }
            mUrlBuilder.setPageIndex(page);
            String url = mUrlBuilder.build(pageAction, mHelper);
            if (url == null || url.isEmpty()) {
                Toast.makeText(getContext(), R.string.gallery_list_action_url_missed, Toast.LENGTH_LONG).show();
                if (mHelper != null) {
                    mHelper.cancelCurrentTask();
                }
                return;
            }
            EhRequest request = new EhRequest();
            request.setMethod(EhClient.METHOD_GET_GALLERY_LIST);
            request.setCallback(new GetGalleryListListener(getContext(),
                    activity.getStageId(), getTag(), taskId));
            request.setArgs(url, mUrlBuilder.getMode());
            mClient.execute(request);
        }


        @Override
        protected Context getContext() {
            return GalleryListScene.this.getEHContext();
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void notifyDataSetChanged() {
            if (null != mAdapter) {
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
            if (null != mAdapter) {
                mAdapter.notifyItemRangeRemoved(positionStart, itemCount);
            }
        }

        @Override
        protected void notifyItemRangeInserted(int positionStart, int itemCount) {
            if (null != mAdapter) {
                mAdapter.notifyItemRangeInserted(positionStart, itemCount);
            }
        }

        @Override
        public void onShowView(View hiddenView, View shownView) {
            if (null != mSearchBarMover) {
                mSearchBarMover.showSearchBar();
            }
            showActionFab();
        }

        @Override
        protected boolean isDuplicate(GalleryInfo d1, GalleryInfo d2) {
            return d1.gid == d2.gid;
        }

        @Override
        protected void onScrollToPosition(int postion) {
            if (0 == postion) {
                if (null != mSearchBarMover) {
                    mSearchBarMover.showSearchBar();
                }
                showActionFab();
            }
        }
    }

    private static class GetGalleryListListener extends EhCallback<GalleryListScene, GalleryListParser.Result> {

        private final int mTaskId;

        public GetGalleryListListener(Context context, int stageId, String sceneTag, int taskId) {
            super(context, stageId, sceneTag);
            mTaskId = taskId;
        }

        @Override
        public void onSuccess(GalleryListParser.Result result) {
            GalleryListScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryListSuccess(result, mTaskId);
            }
        }

        @Override
        public void onFailure(Exception e) {
            GalleryListScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryListFailure(e, mTaskId);
            }
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryListScene;
        }
    }

    private static class AddToFavoriteListener extends EhCallback<GalleryListScene, Void> {

        public AddToFavoriteListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(Void result) {
            showTip(R.string.add_to_favorite_success, LENGTH_SHORT);
        }

        @Override
        public void onFailure(Exception e) {
            showTip(R.string.add_to_favorite_failure, LENGTH_LONG);
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryListScene;
        }
    }

    private static class RemoveFromFavoriteListener extends EhCallback<GalleryListScene, Void> {

        public RemoveFromFavoriteListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(Void result) {
            showTip(R.string.remove_from_favorite_success, LENGTH_SHORT);
        }

        @Override
        public void onFailure(Exception e) {
            showTip(R.string.remove_from_favorite_failure, LENGTH_LONG);
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryListScene;
        }
    }
}

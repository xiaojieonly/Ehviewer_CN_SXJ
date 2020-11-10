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

package com.hippo.ehviewer.ui.scene;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.transition.TransitionInflater;
import com.hippo.android.resource.AttrResources;
import com.hippo.beerbelly.BeerBelly;
import com.hippo.drawable.RoundSideRectDrawable;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhFilter;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryComment;
import com.hippo.ehviewer.client.data.GalleryCommentList;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.data.PreviewSet;
import com.hippo.ehviewer.client.exception.NoHAtHClientException;
import com.hippo.ehviewer.client.parser.RateGalleryParser;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.Filter;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.annotation.WholeLifeCircle;
import com.hippo.ehviewer.widget.GalleryRatingBar;
import com.hippo.reveal.ViewAnimationUtils;
import com.hippo.ripple.Ripple;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.scene.TransitionHelper;
import com.hippo.text.Html;
import com.hippo.text.URLImageGetter;
import com.hippo.util.AppHelper;
import com.hippo.util.DrawableManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.ReadableTime;
import com.hippo.view.ViewTransition;
import com.hippo.widget.AutoWrapLayout;
import com.hippo.widget.LoadImageView;
import com.hippo.widget.ObservedTextView;
import com.hippo.widget.ProgressView;
import com.hippo.widget.SimpleGridAutoSpanLayout;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.IntIdGenerator;
import com.hippo.yorozuya.SimpleHandler;
import com.hippo.yorozuya.ViewUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import okhttp3.HttpUrl;

public class GalleryDetailScene extends BaseScene implements View.OnClickListener,
        com.hippo.ehviewer.download.DownloadManager.DownloadInfoListener,
        View.OnLongClickListener{

    @IntDef({STATE_INIT, STATE_NORMAL, STATE_REFRESH, STATE_REFRESH_HEADER, STATE_FAILED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {}

    private static final int REQUEST_CODE_COMMENT_GALLERY = 0;

    private static final int STATE_INIT = -1;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_REFRESH = 1;
    private static final int STATE_REFRESH_HEADER = 2;
    private static final int STATE_FAILED = 3;

    public final static String KEY_ACTION = "action";
    public static final String ACTION_GALLERY_INFO = "action_gallery_info";
    public static final String ACTION_GID_TOKEN = "action_gid_token";

    public static final String KEY_GALLERY_INFO = "gallery_info";
    public static final String KEY_GID = "gid";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_PAGE = "page";

    private static final String KEY_GALLERY_DETAIL = "gallery_detail";
    private static final String KEY_REQUEST_ID = "request_id";

    private static final boolean TRANSITION_ANIMATION_DISABLED = true;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private TextView mTip;
    @Nullable
    private ViewTransition mViewTransition;
    // Header
    @Nullable
    private View mHeader;
    @Nullable
    private View mColorBg;
    @Nullable
    private LoadImageView mThumb;
    @Nullable
    private TextView mTitle;
    @Nullable
    private TextView mUploader;
    @Nullable
    private TextView mCategory;
    @Nullable
    private ImageView mOtherActions;
    @Nullable
    private ViewGroup mActionGroup;
    @Nullable
    private TextView mDownload;
    @Nullable
    private View mRead;
    // Below header
    @Nullable
    private View mBelowHeader;
    // Info
    @Nullable
    private View mInfo;
    @Nullable
    private TextView mLanguage;
    @Nullable
    private TextView mPages;
    @Nullable
    private TextView mSize;
    @Nullable
    private TextView mPosted;
    @Nullable
    private TextView mFavoredTimes;
    // Actions
    @Nullable
    private View mActions;
    @Nullable
    private TextView mRatingText;
    @Nullable
    private RatingBar mRating;
    @Nullable
    private View mHeartGroup;
    @Nullable
    private TextView mHeart;
    @Nullable
    private TextView mHeartOutline;
    @Nullable
    private TextView mTorrent;
    @Nullable
    private TextView mArchive;
    @Nullable
    private TextView mShare;
    @Nullable
    private TextView mRate;
    @Nullable
    private TextView mSimilar;
    @Nullable
    private TextView mSearchCover;
    // Tags
    @Nullable
    private LinearLayout mTags;
    @Nullable
    private TextView mNoTags;
    // Comments
    @Nullable
    private LinearLayout mComments;
    @Nullable
    private TextView mCommentsText;
    // Previews
    @Nullable
    private View mPreviews;
    @Nullable
    private SimpleGridAutoSpanLayout mGridLayout;
    @Nullable
    private TextView mPreviewText;
    // Progress
    @Nullable
    private View mProgress;
    @Nullable
    private ViewTransition mViewTransition2;
    @Nullable
    private PopupMenu mPopupMenu;

    @WholeLifeCircle
    private int mDownloadState;

    @Nullable
    private String mAction;
    @Nullable
    private GalleryInfo mGalleryInfo;
    private long mGid;
    private String mToken;

    @Nullable
    private GalleryDetail mGalleryDetail;
    private int mRequestId = IntIdGenerator.INVALID_ID;

    private Pair<String, String>[] mTorrentList;

    private String mArchiveFormParamOr;
    private Pair<String, String>[] mArchiveList;

    @State
    private int mState = STATE_INIT;

    private boolean mModifingFavorites;

    private void handleArgs(Bundle args) {
        if (args == null) {
            return;
        }

        String action = args.getString(KEY_ACTION);
        mAction = action;
        if (ACTION_GALLERY_INFO.equals(action)) {
            mGalleryInfo = args.getParcelable(KEY_GALLERY_INFO);
            // Add history
            if (null != mGalleryInfo) {
                EhDB.putHistoryInfo(mGalleryInfo);
            }
        } else if (ACTION_GID_TOKEN.equals(action)) {
            mGid = args.getLong(KEY_GID);
            mToken = args.getString(KEY_TOKEN);
        }
    }

    @Nullable
    private String getGalleryDetailUrl(boolean allComment) {
        long gid;
        String token;
        if (mGalleryDetail != null) {
            gid = mGalleryDetail.gid;
            token = mGalleryDetail.token;
        } else if (mGalleryInfo != null) {
            gid = mGalleryInfo.gid;
            token = mGalleryInfo.token;
        } else if (ACTION_GID_TOKEN.equals(mAction)) {
            gid = mGid;
            token = mToken;
        } else {
            return null;
        }
        return EhUrl.getGalleryDetailUrl(gid, token, 0, allComment);
    }

    // -1 for error
    private long getGid() {
        if (mGalleryDetail != null) {
            return mGalleryDetail.gid;
        } else if (mGalleryInfo != null) {
            return mGalleryInfo.gid;
        } else if (ACTION_GID_TOKEN.equals(mAction)) {
            return mGid;
        } else {
            return -1;
        }
    }

    private String getToken() {
        if (mGalleryDetail != null) {
            return mGalleryDetail.token;
        } else if (mGalleryInfo != null) {
            return mGalleryInfo.token;
        } else if (ACTION_GID_TOKEN.equals(mAction)) {
            return mToken;
        } else {
            return null;
        }
    }

    private String getUploader() {
        if (mGalleryDetail != null) {
            return mGalleryDetail.uploader;
        } else if (mGalleryInfo != null) {
            return mGalleryInfo.uploader;
        } else {
            return null;
        }
    }

    // -1 for error
    private int getCategory() {
        if (mGalleryDetail != null) {
            return mGalleryDetail.category;
        } else if (mGalleryInfo != null) {
            return mGalleryInfo.category;
        } else {
            return -1;
        }
    }

    private GalleryInfo getGalleryInfo() {
        if (null != mGalleryDetail) {
            return mGalleryDetail;
        } else if (null != mGalleryInfo) {
            return mGalleryInfo;
        } else {
            return null;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private void onInit() {
        handleArgs(getArguments());
    }

    private void onRestore(Bundle savedInstanceState) {
        mAction = savedInstanceState.getString(KEY_ACTION);
        mGalleryInfo = savedInstanceState.getParcelable(KEY_GALLERY_INFO);
        mGid = savedInstanceState.getLong(KEY_GID);
        mToken = savedInstanceState.getString(KEY_TOKEN);
        mGalleryDetail = savedInstanceState.getParcelable(KEY_GALLERY_DETAIL);
        mRequestId = savedInstanceState.getInt(KEY_REQUEST_ID);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mAction != null) {
            outState.putString(KEY_ACTION, mAction);
        }
        if (mGalleryInfo != null) {
            outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo);
        }
        outState.putLong(KEY_GID, mGid);
        if (mToken != null) {
            outState.putString(KEY_TOKEN, mAction);
        }
        if (mGalleryDetail != null) {
            outState.putParcelable(KEY_GALLERY_DETAIL, mGalleryDetail);
        }
        outState.putInt(KEY_REQUEST_ID, mRequestId);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Get download state
        long gid = getGid();
        if (gid != -1) {
            Context context = getContext2();
            AssertUtils.assertNotNull(context);
            mDownloadState = EhApplication.getDownloadManager(context).getDownloadState(gid);
        } else {
            mDownloadState = DownloadInfo.STATE_INVALID;
        }

        View view = inflater.inflate(R.layout.scene_gallery_detail, container, false);

        ViewGroup main = (ViewGroup) ViewUtils.$$(view, R.id.main);
        View mainView = ViewUtils.$$(main, R.id.scroll_view);
        View progressView = ViewUtils.$$(main, R.id.progress_view);
        mTip = (TextView) ViewUtils.$$(main, R.id.tip);
        mViewTransition = new ViewTransition(mainView, progressView, mTip);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);

        View actionsScrollView = ViewUtils.$$(view, R.id.actions_scroll_view);
        setDrawerGestureBlocker(new DrawerLayout.GestureBlocker() {
            private void transformPointToViewLocal(int[] point, View child) {
                ViewParent viewParent = child.getParent();

                while (viewParent instanceof View) {
                    View view = (View) viewParent;
                    point[0] += view.getScrollX() - child.getLeft();
                    point[1] += view.getScrollY() - child.getTop();

                    if (view instanceof DrawerLayout) {
                        break;
                    }

                    child = view;
                    viewParent = child.getParent();
                }
            }

            @Override
            public boolean shouldBlockGesture(MotionEvent ev) {
                int[] point = new int[] {(int) ev.getX(), (int) ev.getY()};
                transformPointToViewLocal(point, actionsScrollView);
                return !isDrawersVisible()
                    && point[0] > 0 && point[0] < actionsScrollView.getWidth()
                    && point[1] > 0 && point[1] < actionsScrollView.getHeight();
            }
        });

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_sad_pandroid);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        mTip.setCompoundDrawables(null, drawable, null, null);
        mTip.setOnClickListener(this);

        boolean isDarkTheme = !AttrResources.getAttrBoolean(context, R.attr.isLightTheme);
        mHeader = ViewUtils.$$(mainView, R.id.header);
        mColorBg = ViewUtils.$$(mHeader, R.id.color_bg);
        mThumb = (LoadImageView) ViewUtils.$$(mHeader, R.id.thumb);
        mTitle = (TextView) ViewUtils.$$(mHeader, R.id.title);
        mUploader = (TextView) ViewUtils.$$(mHeader, R.id.uploader);
        mCategory = (TextView) ViewUtils.$$(mHeader, R.id.category);
        mOtherActions = (ImageView) ViewUtils.$$(mHeader, R.id.other_actions);
        mActionGroup = (ViewGroup) ViewUtils.$$(mHeader, R.id.action_card);
        mDownload = (TextView) ViewUtils.$$(mActionGroup, R.id.download);
        mRead = ViewUtils.$$(mActionGroup, R.id.read);
        Ripple.addRipple(mOtherActions, isDarkTheme);
        Ripple.addRipple(mDownload, isDarkTheme);
        Ripple.addRipple(mRead, isDarkTheme);
        mUploader.setOnClickListener(this);
        mCategory.setOnClickListener(this);
        mOtherActions.setOnClickListener(this);
        mDownload.setOnClickListener(this);
        mDownload.setOnLongClickListener(this);
        mRead.setOnClickListener(this);

        mUploader.setOnLongClickListener(this);

        mBelowHeader = mainView.findViewById(R.id.below_header);
        View belowHeader = mBelowHeader;

        mInfo = ViewUtils.$$(belowHeader, R.id.info);
        mLanguage = (TextView) ViewUtils.$$(mInfo, R.id.language);
        mPages = (TextView) ViewUtils.$$(mInfo, R.id.pages);
        mSize = (TextView) ViewUtils.$$(mInfo, R.id.size);
        mPosted = (TextView) ViewUtils.$$(mInfo, R.id.posted);
        mFavoredTimes = (TextView) ViewUtils.$$(mInfo, R.id.favoredTimes);
        Ripple.addRipple(mInfo, isDarkTheme);
        mInfo.setOnClickListener(this);

        mActions = ViewUtils.$$(belowHeader, R.id.actions);
        mRatingText = (TextView) ViewUtils.$$(mActions, R.id.rating_text);
        mRating = (RatingBar) ViewUtils.$$(mActions, R.id.rating);
        mHeartGroup = ViewUtils.$$(mActions, R.id.heart_group);
        mHeart = (TextView) ViewUtils.$$(mHeartGroup, R.id.heart);
        mHeartOutline = (TextView) ViewUtils.$$(mHeartGroup, R.id.heart_outline);
        mTorrent = (TextView) ViewUtils.$$(mActions, R.id.torrent);
        mArchive = (TextView) ViewUtils.$$(mActions, R.id.archive);
        mShare = (TextView) ViewUtils.$$(mActions, R.id.share);
        mRate = (TextView) ViewUtils.$$(mActions, R.id.rate);
        mSimilar = (TextView) ViewUtils.$$(mActions, R.id.similar);
        mSearchCover = (TextView) ViewUtils.$$(mActions, R.id.search_cover);
        Ripple.addRipple(mHeartGroup, isDarkTheme);
        Ripple.addRipple(mTorrent, isDarkTheme);
        Ripple.addRipple(mArchive, isDarkTheme);
        Ripple.addRipple(mShare, isDarkTheme);
        Ripple.addRipple(mRate, isDarkTheme);
        Ripple.addRipple(mSimilar, isDarkTheme);
        Ripple.addRipple(mSearchCover, isDarkTheme);
        mHeartGroup.setOnClickListener(this);
        mTorrent.setOnClickListener(this);
        mArchive.setOnClickListener(this);
        mShare.setOnClickListener(this);
        mRate.setOnClickListener(this);
        mSimilar.setOnClickListener(this);
        mSearchCover.setOnClickListener(this);
        ensureActionDrawable(context);

        mTags = (LinearLayout) ViewUtils.$$(belowHeader, R.id.tags);
        mNoTags = (TextView) ViewUtils.$$(mTags, R.id.no_tags);

        mComments = (LinearLayout) ViewUtils.$$(belowHeader, R.id.comments);
        mCommentsText = (TextView) ViewUtils.$$(mComments, R.id.comments_text);
        Ripple.addRipple(mComments, isDarkTheme);
        mComments.setOnClickListener(this);

        mPreviews = ViewUtils.$$(belowHeader, R.id.previews);
        mGridLayout = (SimpleGridAutoSpanLayout) ViewUtils.$$(mPreviews, R.id.grid_layout);
        mPreviewText = (TextView) ViewUtils.$$(mPreviews, R.id.preview_text);
        Ripple.addRipple(mPreviews, isDarkTheme);
        mPreviews.setOnClickListener(this);

        mProgress = ViewUtils.$$(mainView, R.id.progress);

        mViewTransition2 = new ViewTransition(mBelowHeader, mProgress);

        if (prepareData()) {
            if (mGalleryDetail != null) {
                bindViewSecond();
                setTransitionName();
                adjustViewVisibility(STATE_NORMAL, false);
            } else if (mGalleryInfo != null) {
                bindViewFirst();
                setTransitionName();
                adjustViewVisibility(STATE_REFRESH_HEADER, false);
            } else {
                adjustViewVisibility(STATE_REFRESH, false);
            }
        } else {
            mTip.setText(R.string.error_cannot_find_gallery);
            adjustViewVisibility(STATE_FAILED, false);
        }

        EhApplication.getDownloadManager(context).addDownloadInfoListener(this);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        EhApplication.getDownloadManager(context).removeDownloadInfoListener(this);

        setDrawerGestureBlocker(null);

        mTip = null;
        mViewTransition = null;

        mHeader = null;
        mColorBg = null;
        mThumb = null;
        mTitle = null;
        mUploader = null;
        mCategory = null;
        mOtherActions = null;
        mActionGroup = null;
        mDownload = null;
        mRead = null;
        mBelowHeader = null;

        mInfo = null;
        mLanguage = null;
        mPages = null;
        mSize = null;
        mPosted = null;
        mFavoredTimes = null;

        mActions = null;
        mRatingText = null;
        mRating = null;
        mHeartGroup = null;
        mHeart = null;
        mHeartOutline = null;
        mTorrent = null;
        mArchive = null;
        mShare = null;
        mRate = null;
        mSimilar = null;
        mSearchCover = null;

        mTags = null;
        mNoTags = null;

        mComments = null;
        mCommentsText = null;

        mPreviews = null;
        mGridLayout = null;
        mPreviewText = null;

        mProgress = null;

        mViewTransition2 = null;

        mPopupMenu = null;
    }

    private boolean prepareData() {
        Context context = getContext2();
        AssertUtils.assertNotNull(context);

        if (mGalleryDetail != null) {
            return true;
        }

        long gid = getGid();
        if (gid == -1) {
            return false;
        }

        // Get from cache
        mGalleryDetail = EhApplication.getGalleryDetailCache(context).get(gid);
        if (mGalleryDetail != null) {
            return true;
        }

        EhApplication application = (EhApplication) context.getApplicationContext();
        if (application.containGlobalStuff(mRequestId)) {
            // request exist
            return true;
        }

        // Do request
        return request();
    }

    private boolean request() {
        Context context = getContext2();
        MainActivity activity = getActivity2();
        String url = getGalleryDetailUrl(false);
        if (null == context || null == activity || null == url) {
            return false;
        }

        EhClient.Callback callback = new GetGalleryDetailListener(context,
                activity.getStageId(), getTag());
        mRequestId = ((EhApplication) context.getApplicationContext()).putGlobalStuff(callback);
        EhRequest request = new EhRequest()
                .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
                .setArgs(url)
                .setCallback(callback);
        EhApplication.getEhClient(context).execute(request);

        return true;
    }

    private void setActionDrawable(TextView text, Drawable drawable) {
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        text.setCompoundDrawables(null, drawable, null, null);
    }

    private void ensureActionDrawable(Context context) {
        Drawable heart = DrawableManager.getVectorDrawable(context, R.drawable.v_heart_primary_x48);
        setActionDrawable(mHeart, heart);
        Drawable heartOutline = DrawableManager.getVectorDrawable(context, R.drawable.v_heart_outline_primary_x48);
        setActionDrawable(mHeartOutline, heartOutline);
        Drawable torrent = DrawableManager.getVectorDrawable(context, R.drawable.v_utorrent_primary_x48);
        setActionDrawable(mTorrent, torrent);
        Drawable archive = DrawableManager.getVectorDrawable(context, R.drawable.v_archive_primary_x48);
        setActionDrawable(mArchive, archive);
        Drawable share = DrawableManager.getVectorDrawable(context, R.drawable.v_share_primary_x48);
        setActionDrawable(mShare, share);
        Drawable rate = DrawableManager.getVectorDrawable(context, R.drawable.v_thumb_up_primary_x48);
        setActionDrawable(mRate, rate);
        Drawable similar = DrawableManager.getVectorDrawable(context, R.drawable.v_similar_primary_x48);
        setActionDrawable(mSimilar, similar);
        Drawable searchCover = DrawableManager.getVectorDrawable(context, R.drawable.v_file_find_primary_x48);
        setActionDrawable(mSearchCover, searchCover);
    }

    private boolean createCircularReveal() {
        if (mColorBg == null) {
            return false;
        }

        int w = mColorBg.getWidth();
        int h = mColorBg.getHeight();
        if (ViewCompat.isAttachedToWindow(mColorBg) && w != 0 && h != 0) {
            Resources resources = getContext2().getResources();
            int keylineMargin = resources.getDimensionPixelSize(R.dimen.keyline_margin);
            int thumbWidth = resources.getDimensionPixelSize(R.dimen.gallery_detail_thumb_width);
            int thumbHeight = resources.getDimensionPixelSize(R.dimen.gallery_detail_thumb_height);

            int x = thumbWidth / 2 + keylineMargin;
            int y = thumbHeight / 2 + keylineMargin;

            int radiusX = Math.max(Math.abs(x), Math.abs(w - x));
            int radiusY = Math.max(Math.abs(y), Math.abs(h - y));
            float radius = (float) Math.hypot(radiusX, radiusY);

            ViewAnimationUtils.createCircularReveal(mColorBg, x, y, 0, radius).setDuration(300).start();
            return true;
        } else {
            return false;
        }
    }

    private void adjustViewVisibility(int state, boolean animation) {
        if (state == mState) {
            return;
        }
        if (mViewTransition == null || mViewTransition2 == null) {
            return;
        }

        int oldState = mState;
        mState = state;

        animation = !TRANSITION_ANIMATION_DISABLED && animation;

        switch (state) {
            case STATE_NORMAL:
                // Show mMainView
                mViewTransition.showView(0, animation);
                // Show mBelowHeader
                mViewTransition2.showView(0, animation);
                break;
            case STATE_REFRESH:
                // Show mProgressView
                mViewTransition.showView(1, animation);
                break;
            case STATE_REFRESH_HEADER:
                // Show mMainView
                mViewTransition.showView(0, animation);
                // Show mProgress
                mViewTransition2.showView(1, animation);
                break;
            default:
            case STATE_INIT:
            case STATE_FAILED:
                // Show mFailedView
                mViewTransition.showView(2, animation);
                break;
        }

        if ((oldState == STATE_INIT || oldState == STATE_FAILED || oldState == STATE_REFRESH) &&
                (state == STATE_NORMAL || state == STATE_REFRESH_HEADER) && AttrResources.getAttrBoolean(getContext2(), R.attr.isLightTheme)) {
            if (!createCircularReveal()) {
                SimpleHandler.getInstance().post(this::createCircularReveal);
            }
        }
    }

    private void bindViewFirst() {
        if (mGalleryDetail != null) {
            return;
        }
        if (mThumb == null || mTitle == null || mUploader == null || mCategory == null) {
            return;
        }

        if (ACTION_GALLERY_INFO.equals(mAction) && mGalleryInfo != null) {
            GalleryInfo gi = mGalleryInfo;
            mThumb.load(EhCacheKeyFactory.getThumbKey(gi.gid), gi.thumb);
            mTitle.setText(EhUtils.getSuitableTitle(gi));
            mUploader.setText(gi.uploader);
            mCategory.setText(EhUtils.getCategory(gi.category));
            mCategory.setTextColor(EhUtils.getCategoryColor(gi.category));
            updateDownloadText();
        }
    }

    private void updateFavoriteDrawable() {
        GalleryDetail gd = mGalleryDetail;
        if (gd == null) {
            return;
        }
        if (mHeart == null || mHeartOutline == null ) {
            return;
        }

        if (gd.isFavorited || EhDB.containLocalFavorites(gd.gid)) {
            mHeart.setVisibility(View.VISIBLE);
            if (gd.favoriteName == null) {
                mHeart.setText(R.string.local_favorites);
            } else {
                mHeart.setText(gd.favoriteName);
            }
            mHeartOutline.setVisibility(View.GONE);
        } else {
            mHeart.setVisibility(View.GONE);
            mHeartOutline.setVisibility(View.VISIBLE);
        }
    }

    private void bindViewSecond() {
        GalleryDetail gd = mGalleryDetail;
        if (gd == null) {
            return;
        }
        if (mThumb == null || mTitle == null || mUploader == null || mCategory == null ||
                mLanguage == null || mPages == null || mSize == null || mPosted == null ||
                mFavoredTimes == null || mRatingText == null || mRating == null || mTorrent == null) {
            return;
        }

        Resources resources = getResources2();
        AssertUtils.assertNotNull(resources);

        mThumb.load(EhCacheKeyFactory.getThumbKey(gd.gid), gd.thumb);
        mTitle.setText(EhUtils.getSuitableTitle(gd));
        mUploader.setText(gd.uploader);
        mCategory.setText(EhUtils.getCategory(gd.category));
        mCategory.setTextColor(EhUtils.getCategoryColor(gd.category));
        updateDownloadText();

        mLanguage.setText(gd.language);
        mPages.setText(resources.getQuantityString(
                R.plurals.page_count, gd.pages, gd.pages));
        mSize.setText(gd.size);
        mPosted.setText(gd.posted);
        mFavoredTimes.setText(resources.getString(R.string.favored_times, gd.favoriteCount));

        mRatingText.setText(getAllRatingText(gd.rating, gd.ratingCount));
        mRating.setRating(gd.rating);

        updateFavoriteDrawable();

        mTorrent.setText(resources.getString(R.string.torrent_count, gd.torrentCount));

        bindTags(gd.tags);
        bindComments(gd.comments.comments);
        bindPreviews(gd);
    }

    @SuppressWarnings("deprecation")
    private void bindTags(GalleryTagGroup[] tagGroups) {
        Context context = getContext2();
        LayoutInflater inflater = getLayoutInflater2();
        Resources resources = getResources2();
        if (null == context || null == inflater || null == resources || null == mTags || null == mNoTags) {
            return;
        }

        mTags.removeViews(1, mTags.getChildCount() - 1);
        if (tagGroups == null || tagGroups.length == 0) {
            mNoTags.setVisibility(View.VISIBLE);
            return;
        } else {
            mNoTags.setVisibility(View.GONE);
        }

        EhTagDatabase ehTags = Settings.getShowTagTranslations() ? EhTagDatabase.getInstance(context) : null;
        int colorTag = AttrResources.getAttrColor(context, R.attr.tagBackgroundColor);
        int colorName = AttrResources.getAttrColor(context, R.attr.tagGroupBackgroundColor);
        for (GalleryTagGroup tg : tagGroups) {
            LinearLayout ll = (LinearLayout) inflater.inflate(R.layout.gallery_tag_group, mTags, false);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            mTags.addView(ll);

            String readableTagName = null;
            if (ehTags != null) {
                readableTagName = ehTags.getTranslation("n:" + tg.groupName);
            }

            TextView tgName = (TextView) inflater.inflate(R.layout.item_gallery_tag, ll, false);
            ll.addView(tgName);
            tgName.setText(readableTagName != null ? readableTagName : tg.groupName);
            tgName.setBackgroundDrawable(new RoundSideRectDrawable(colorName));

            String prefix = EhTagDatabase.namespaceToPrefix(tg.groupName);
            if (prefix == null) {
                prefix = "";
            }

            AutoWrapLayout awl = new AutoWrapLayout(context);
            ll.addView(awl, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            for (int j = 0, z = tg.size(); j < z; j++) {
                TextView tag = (TextView) inflater.inflate(R.layout.item_gallery_tag, awl, false);
                awl.addView(tag);
                String tagStr = tg.getTagAt(j);

                String readableTag = null;
                if (ehTags != null) {
                    readableTag = ehTags.getTranslation(prefix + tagStr);
                }

                tag.setText(readableTag != null ? readableTag : tagStr);
                tag.setBackgroundDrawable(new RoundSideRectDrawable(colorTag));
                tag.setTag(R.id.tag, tg.groupName + ":" + tagStr);
                tag.setOnClickListener(this);
                tag.setOnLongClickListener(this);
            }
        }
    }

    private void bindComments(GalleryComment[] comments) {
        Context context = getContext2();
        LayoutInflater inflater = getLayoutInflater2();
        if (null == context || null == inflater || null == mComments || null == mCommentsText) {
            return;
        }

        mComments.removeViews(0, mComments.getChildCount() - 1);

        final int maxShowCount = 2;
        if (comments == null || comments.length == 0) {
            mCommentsText.setText(R.string.no_comments);
            return;
        } else if (comments.length <= maxShowCount) {
            mCommentsText.setText(R.string.no_more_comments);
        } else {
            mCommentsText.setText(R.string.more_comment);
        }

        int length = Math.min(maxShowCount, comments.length);
        for (int i = 0; i < length; i++) {
            GalleryComment comment = comments[i];
            View v = inflater.inflate(R.layout.item_gallery_comment, mComments, false);
            mComments.addView(v, i);
            TextView user = (TextView) v.findViewById(R.id.user);
            user.setText(comment.user);
            TextView time = (TextView) v.findViewById(R.id.time);
            time.setText(ReadableTime.getTimeAgo(comment.time));
            ObservedTextView c = (ObservedTextView) v.findViewById(R.id.comment);
            c.setMaxLines(5);
            c.setText(Html.fromHtml(comment.comment,
                    new URLImageGetter(c, EhApplication.getConaco(context)), null));
        }
    }

    @SuppressLint("SetTextI18n")
    private void bindPreviews(GalleryDetail gd) {
        LayoutInflater inflater = getLayoutInflater2();
        Resources resources = getResources2();
        if (null == inflater || null == resources || null == mGridLayout || null == mPreviewText) {
            return;
        }

        mGridLayout.removeAllViews();
        PreviewSet previewSet = gd.previewSet;
        if (gd.previewPages <= 0 || previewSet == null || previewSet.size() == 0) {
            mPreviewText.setText(R.string.no_previews);
            return;
        } else if (gd.previewPages == 1) {
            mPreviewText.setText(R.string.no_more_previews);
        } else {
            mPreviewText.setText(R.string.more_previews);
        }

        int columnWidth = resources.getDimensionPixelOffset(Settings.getThumbSizeResId());
        mGridLayout.setColumnSize(columnWidth);
        mGridLayout.setStrategy(SimpleGridAutoSpanLayout.STRATEGY_SUITABLE_SIZE);
        for (int i = 0, size = previewSet.size(); i < size; i++) {
            View view = inflater.inflate(R.layout.item_gallery_preview, mGridLayout, false);
            mGridLayout.addView(view);

            LoadImageView image = (LoadImageView) view.findViewById(R.id.image);
            previewSet.load(image, gd.gid, i);
            image.setTag(R.id.index, i);
            image.setOnClickListener(this);
            TextView text = (TextView) view.findViewById(R.id.text);
            text.setText(Integer.toString(previewSet.getPosition(i) + 1));
        }
    }

    private static String getRatingText(float rating, Resources resources) {
        int resId;
        switch (Math.round(rating * 2)) {
            case 0:
                resId = R.string.rating0; break;
            case 1:
                resId = R.string.rating1; break;
            case 2:
                resId = R.string.rating2; break;
            case 3:
                resId = R.string.rating3; break;
            case 4:
                resId = R.string.rating4; break;
            case 5:
                resId = R.string.rating5; break;
            case 6:
                resId = R.string.rating6; break;
            case 7:
                resId = R.string.rating7; break;
            case 8:
                resId = R.string.rating8; break;
            case 9:
                resId = R.string.rating9; break;
            case 10:
                resId = R.string.rating10; break;
            default:
                resId = R.string.rating_none; break;
        }

        return resources.getString(resId);
    }

    private String getAllRatingText(float rating, int ratingCount) {
        Resources resources = getResources2();
        AssertUtils.assertNotNull(resources);
        return resources.getString(R.string.rating_text, getRatingText(rating, resources), rating, ratingCount);
    }

    private void setTransitionName() {
        long gid = getGid();

        if (gid != -1 && mThumb != null &&
                mTitle != null && mUploader != null && mCategory != null) {
            ViewCompat.setTransitionName(mThumb, TransitionNameFactory.getThumbTransitionName(gid));
            ViewCompat.setTransitionName(mTitle, TransitionNameFactory.getTitleTransitionName(gid));
            ViewCompat.setTransitionName(mUploader, TransitionNameFactory.getUploaderTransitionName(gid));
            ViewCompat.setTransitionName(mCategory, TransitionNameFactory.getCategoryTransitionName(gid));
        }
    }

    private void ensurePopMenu() {
        if (mPopupMenu != null) {
            return;
        }

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        PopupMenu popup = new PopupMenu(context, mOtherActions, Gravity.TOP);
        mPopupMenu = popup;
        popup.getMenuInflater().inflate(R.menu.scene_gallery_detail, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_open_in_other_app:
                        String url = getGalleryDetailUrl(false);
                        Activity activity = getActivity2();
                        if (null != url && null != activity) {
                            UrlOpener.openUrl(activity, url, false);
                        }
                        break;
                    case R.id.action_refresh:
                        if (mState != STATE_REFRESH && mState != STATE_REFRESH_HEADER) {
                            adjustViewVisibility(STATE_REFRESH, true);
                            request();
                        }
                        break;
                }
                return true;
            }
        });
    }

    @Nullable
    private static String getArtist(GalleryTagGroup[] tagGroups) {
        if (null == tagGroups) {
            return null;
        }
        for (GalleryTagGroup tagGroup: tagGroups) {
            if ("artist".equals(tagGroup.groupName) && tagGroup.size() > 0) {
                return tagGroup.getTagAt(0);
            }
        }
        return null;
    }

    private void showSimilarGalleryList() {
        GalleryDetail gd = mGalleryDetail;
        if (null == gd) {
            return;
        }
        String keyword = EhUtils.extractTitle(gd.title);
        if (null != keyword) {
            ListUrlBuilder lub = new ListUrlBuilder();
            lub.setMode(ListUrlBuilder.MODE_NORMAL);
            lub.setKeyword("\"" + keyword + "\"");
            GalleryListScene.startScene(this, lub);
            return;
        }
        String artist = getArtist(gd.tags);
        if (null != artist) {
            ListUrlBuilder lub = new ListUrlBuilder();
            lub.setMode(ListUrlBuilder.MODE_TAG);
            lub.setKeyword("artist:" + artist);
            GalleryListScene.startScene(this, lub);
            return;
        }
        if (null != gd.uploader) {
            ListUrlBuilder lub = new ListUrlBuilder();
            lub.setMode(ListUrlBuilder.MODE_UPLOADER);
            lub.setKeyword(gd.uploader);
            GalleryListScene.startScene(this, lub);
        }
    }

    private void showCoverGalleryList() {
        Context context = getContext2();
        if (null == context) {
            return;
        }
        long gid = getGid();
        if (-1L == gid) {
            return;
        }
        File temp = AppConfig.createTempFile();
        if (null == temp) {
            return;
        }
        BeerBelly beerBelly = EhApplication.getConaco(context).getBeerBelly();

        OutputStream os = null;
        try {
            os = new FileOutputStream(temp);
            if (beerBelly.pullFromDiskCache(EhCacheKeyFactory.getThumbKey(gid), os)) {
                ListUrlBuilder lub = new ListUrlBuilder();
                lub.setMode(ListUrlBuilder.MODE_IMAGE_SEARCH);
                lub.setImagePath(temp.getPath());
                lub.setUseSimilarityScan(true);
                lub.setShowExpunged(true);
                GalleryListScene.startScene(this, lub);
            }
        } catch (FileNotFoundException e) {
            // Ignore
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    @Override
    public void onClick(View v) {
        Context context = getContext2();
        MainActivity activity = getActivity2();
        if (null == context || null == activity) {
            return;
        }

        if (mTip == v) {
            if (request()) {
                adjustViewVisibility(STATE_REFRESH, true);
            }
        } else if (mOtherActions == v) {
            ensurePopMenu();
            if (mPopupMenu != null) {
                mPopupMenu.show();
            }
        } else if (mUploader == v) {
            String uploader = getUploader();
            if (TextUtils.isEmpty(uploader)) {
                return;
            }
            ListUrlBuilder lub = new ListUrlBuilder();
            lub.setMode(ListUrlBuilder.MODE_UPLOADER);
            lub.setKeyword(uploader);
            GalleryListScene.startScene(this, lub);
        } else if (mCategory == v) {
            int category = getCategory();
            if (category == -1) {
                return;
            }
            ListUrlBuilder lub = new ListUrlBuilder();
            lub.setCategory(category);
            GalleryListScene.startScene(this, lub);
        } else if (mDownload == v) {
            GalleryInfo galleryInfo = getGalleryInfo();
            if (galleryInfo != null) {
                if (EhApplication.getDownloadManager(context).getDownloadState(galleryInfo.gid) == DownloadInfo.STATE_INVALID) {
                    CommonOperations.startDownload(activity, galleryInfo, false);
                } else {
                    new AlertDialog.Builder(context)
                        .setTitle(R.string.download_remove_dialog_title)
                        .setMessage(getString(R.string.download_remove_dialog_message, galleryInfo.title))
                        .setPositiveButton(android.R.string.ok, (dialog1, which1) -> EhApplication.getDownloadManager(context).deleteDownload(galleryInfo.gid))
                        .show();
                }
            }
        } else if (mRead == v) {
            GalleryInfo galleryInfo = null;
            if (mGalleryInfo != null) {
                galleryInfo = mGalleryInfo;
            } else if (mGalleryDetail != null) {
                galleryInfo = mGalleryDetail;
            }
            if (galleryInfo != null) {
                Intent intent = new Intent(activity, GalleryActivity.class);
                intent.setAction(GalleryActivity.ACTION_EH);
                intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, galleryInfo);
                startActivity(intent);
            }
        } else if (mInfo == v) {
            Bundle args = new Bundle();
            args.putParcelable(GalleryInfoScene.KEY_GALLERY_DETAIL, mGalleryDetail);
            startScene(new Announcer(GalleryInfoScene.class).setArgs(args));
        } else if (mHeartGroup == v) {
            if (mGalleryDetail != null && !mModifingFavorites) {
                boolean remove = false;
                if (EhDB.containLocalFavorites(mGalleryDetail.gid) || mGalleryDetail.isFavorited) {
                    mModifingFavorites = true;
                    CommonOperations.removeFromFavorites(activity, mGalleryDetail,
                            new ModifyFavoritesListener(context,
                                    activity.getStageId(), getTag(), true));
                    remove = true;
                }
                if (!remove) {
                    mModifingFavorites = true;
                    CommonOperations.addToFavorites(activity, mGalleryDetail,
                            new ModifyFavoritesListener(context,
                                    activity.getStageId(), getTag(), false));
                }
                // Update UI
                updateFavoriteDrawable();
            }
        } else if (mShare == v) {
            String url = getGalleryDetailUrl(false);
            if (url != null) {
                AppHelper.share(activity, url);
            }
        } else if (mTorrent == v) {
            if (mGalleryDetail != null) {
                TorrentListDialogHelper helper = new TorrentListDialogHelper();
                Dialog dialog = new AlertDialog.Builder(context)
                        .setTitle(R.string.torrents)
                        .setView(R.layout.dialog_torrent_list)
                        .setOnDismissListener(helper)
                        .show();
                helper.setDialog(dialog, mGalleryDetail.torrentUrl);
            }
        } else if (mArchive == v) {
            if (mGalleryDetail == null) {
                return;
            }
            if (mGalleryDetail.apiUid < 0) {
                showTip(R.string.sign_in_first, LENGTH_LONG);
                return;
            }
            ArchiveListDialogHelper helper = new ArchiveListDialogHelper();
            Dialog dialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_archive_title)
                    .setView(R.layout.dialog_archive_list)
                    .setOnDismissListener(helper)
                    .show();
            helper.setDialog(dialog, mGalleryDetail.archiveUrl);
        } else if (mRate == v) {
            if (mGalleryDetail == null) {
                return;
            }
            if (mGalleryDetail.apiUid < 0) {
                showTip(R.string.sign_in_first, LENGTH_LONG);
                return;
            }
            RateDialogHelper helper = new RateDialogHelper();
            Dialog dialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.rate)
                    .setView(R.layout.dialog_rate)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, helper)
                    .show();
            helper.setDialog(dialog, mGalleryDetail.rating);
        } else if (mSimilar == v) {
            showSimilarGalleryList();
        } else if (mSearchCover == v) {
            showCoverGalleryList();
        } else if (mComments == v) {
            if (mGalleryDetail == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong(GalleryCommentsScene.KEY_API_UID, mGalleryDetail.apiUid);
            args.putString(GalleryCommentsScene.KEY_API_KEY, mGalleryDetail.apiKey);
            args.putLong(GalleryCommentsScene.KEY_GID, mGalleryDetail.gid);
            args.putString(GalleryCommentsScene.KEY_TOKEN, mGalleryDetail.token);
            args.putParcelable(GalleryCommentsScene.KEY_COMMENT_LIST, mGalleryDetail.comments);
            startScene(new Announcer(GalleryCommentsScene.class)
                    .setArgs(args)
                    .setRequestCode(this, REQUEST_CODE_COMMENT_GALLERY));
        } else if (mPreviews == v) {
            if (null != mGalleryDetail) {
                Bundle args = new Bundle();
                args.putParcelable(GalleryPreviewsScene.KEY_GALLERY_INFO, mGalleryDetail);
                startScene(new Announcer(GalleryPreviewsScene.class).setArgs(args));
            }
        } else {
            Object o = v.getTag(R.id.tag);
            if (o instanceof String) {
                String tag = (String) o;
                ListUrlBuilder lub = new ListUrlBuilder();
                lub.setMode(ListUrlBuilder.MODE_TAG);
                lub.setKeyword(tag);
                GalleryListScene.startScene(this, lub);
                return;
            }

            GalleryInfo galleryInfo = getGalleryInfo();
            o = v.getTag(R.id.index);
            if (null != galleryInfo && o instanceof Integer) {
                int index = (Integer) o;
                Intent intent = new Intent(context, GalleryActivity.class);
                intent.setAction(GalleryActivity.ACTION_EH);
                intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, galleryInfo);
                intent.putExtra(GalleryActivity.KEY_PAGE, index);
                startActivity(intent);
                return;
            }
        }
    }

    private void showFilterUploaderDialog() {
        Context context = getContext2();
        String uploader = getUploader();
        if (context == null || uploader == null) {
            return;
        }

        new AlertDialog.Builder(context)
                .setMessage(getString(R.string.filter_the_uploader, uploader))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (which != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }

                    Filter filter = new Filter();
                    filter.mode = EhFilter.MODE_UPLOADER;
                    filter.text = uploader;
                    EhFilter.getInstance().addFilter(filter);

                    showTip(R.string.filter_added, LENGTH_SHORT);
                }).show();
    }

    private void showFilterTagDialog(String tag) {
        Context context = getContext2();
        if (context == null) {
            return;
        }

        new AlertDialog.Builder(context)
                .setMessage(getString(R.string.filter_the_tag, tag))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (which != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }

                    Filter filter = new Filter();
                    filter.mode = EhFilter.MODE_TAG;
                    filter.text = tag;
                    EhFilter.getInstance().addFilter(filter);

                    showTip(R.string.filter_added, LENGTH_SHORT);
                }).show();
    }

    private void showTagDialog(final String tag) {
        final Context context = getContext2();
        if (null == context) {
            return;
        }
        String temp;
        int index = tag.indexOf(':');
        if (index >= 0) {
            temp = tag.substring(index + 1);
        } else {
            temp = tag;
        }
        final String tag2 = temp;

        new AlertDialog.Builder(context)
                .setTitle(tag)
                .setItems(R.array.tag_menu_entries, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            UrlOpener.openUrl(context, EhUrl.getTagDefinitionUrl(tag2), false);
                            break;
                        case 1:
                            showFilterTagDialog(tag);
                            break;
                    }
                }).show();
    }

    @Override
    public boolean onLongClick(View v) {
        MainActivity activity = getActivity2();
        if (null == activity) {
            return false;
        }

        if (mUploader == v) {
            showFilterUploaderDialog();
        } else if (mDownload == v) {
            GalleryInfo galleryInfo = getGalleryInfo();
            if (galleryInfo != null) {
                CommonOperations.startDownload(activity, galleryInfo, true);
            }
            return true;
        } else {
            String tag = (String) v.getTag(R.id.tag);
            if (null != tag) {
                showTagDialog(tag);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (mViewTransition != null && mThumb != null &&
                mViewTransition.getShownViewIndex() == 0 && mThumb.isShown()) {
            int[] location = new int[2];
            mThumb.getLocationInWindow(location);
            // Only show transaction when thumb can be seen
            if (location[1] + mThumb.getHeight() > 0) {
                setTransitionName();
                finish(new ExitTransaction(mThumb));
                return;
            }
        }
        finish();
    }

    @Override
    protected void onSceneResult(int requestCode, int resultCode, Bundle data) {
        switch (requestCode) {
            case REQUEST_CODE_COMMENT_GALLERY:
                if (resultCode != RESULT_OK || data == null){
                    break;
                }
                GalleryCommentList comments = data.getParcelable(GalleryCommentsScene.KEY_COMMENT_LIST);
                if (mGalleryDetail == null && comments == null) {
                    break;
                }
                mGalleryDetail.comments = comments;
                bindComments(comments.comments);
                break;
            default:
                super.onSceneResult(requestCode, resultCode, data);
        }
    }

    private void updateDownloadText() {
        if (null == mDownload) {
            return;
        }
        switch (mDownloadState) {
            default:
            case DownloadInfo.STATE_INVALID:
                mDownload.setText(R.string.download);
                break;
            case DownloadInfo.STATE_NONE:
                mDownload.setText(R.string.download_state_none);
                break;
            case DownloadInfo.STATE_WAIT:
                mDownload.setText(R.string.download_state_wait);
                break;
            case DownloadInfo.STATE_DOWNLOAD:
                mDownload.setText(R.string.download_state_downloading);
                break;
            case DownloadInfo.STATE_FINISH:
                mDownload.setText(R.string.download_state_downloaded);
                break;
            case DownloadInfo.STATE_FAILED:
                mDownload.setText(R.string.download_state_failed);
                break;
        }
    }

    private void updateDownloadState() {
        Context context = getContext2();
        long gid = getGid();
        if (null == context || -1L == gid) {
            return;
        }

        int downloadState = EhApplication.getDownloadManager(context).getDownloadState(gid);
        if (downloadState == mDownloadState) {
            return;
        }
        mDownloadState = downloadState;
        updateDownloadText();
    }

    @Override
    public void onAdd(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        updateDownloadState();
    }

    @Override
    public void onUpdate(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list) {
        updateDownloadState();
    }

    @Override
    public void onUpdateAll() {
        updateDownloadState();
    }

    @Override
    public void onReload() {
        updateDownloadState();
    }

    @Override
    public void onChange() {
        updateDownloadState();
    }

    @Override
    public void onRemove(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position) {
        updateDownloadState();
    }

    @Override
    public void onRenameLabel(String from, String to) {}

    @Override
    public void onUpdateLabels() {}

    private static class ExitTransaction implements TransitionHelper {

        private final View mThumb;

        public ExitTransaction(View thumb) {
            mThumb = thumb;
        }

        @Override
        public boolean onTransition(Context context,
                FragmentTransaction transaction, Fragment exit, Fragment enter) {
            if (!(enter instanceof GalleryListScene) && !(enter instanceof DownloadsScene) &&
                    !(enter instanceof FavoritesScene) && !(enter instanceof HistoryScene)) {
                return false;
            }

            String transitionName = ViewCompat.getTransitionName(mThumb);
            if (transitionName != null) {
                exit.setSharedElementReturnTransition(
                        TransitionInflater.from(context).inflateTransition(R.transition.trans_move));
                exit.setExitTransition(
                        TransitionInflater.from(context).inflateTransition(R.transition.trans_fade));
                enter.setSharedElementEnterTransition(
                        TransitionInflater.from(context).inflateTransition(R.transition.trans_move));
                enter.setEnterTransition(
                        TransitionInflater.from(context).inflateTransition(R.transition.trans_fade));
                transaction.addSharedElement(mThumb, transitionName);
            }
            return true;
        }
    }

    private void onGetGalleryDetailSuccess(GalleryDetail result) {
        mGalleryDetail = result;
        updateDownloadState();
        adjustViewVisibility(STATE_NORMAL, true);
        bindViewSecond();
    }

    private void onGetGalleryDetailFailure(Exception e) {
        e.printStackTrace();
        Context context = getContext2();
        if (null != context && null != mTip) {
            String error = ExceptionUtils.getReadableString(e);
            mTip.setText(error);
            adjustViewVisibility(STATE_FAILED, true);
        }
    }

    private void onRateGallerySuccess(RateGalleryParser.Result result) {
        if (mGalleryDetail != null) {
            mGalleryDetail.rating = result.rating;
            mGalleryDetail.ratingCount = result.ratingCount;
        }

        // Update UI
        if (mRatingText != null && mRating != null) {
            mRatingText.setText(getAllRatingText(result.rating, result.ratingCount));
            mRating.setRating(result.rating);
        }
    }

    private void onModifyFavoritesSuccess(boolean addOrRemove) {
        mModifingFavorites = false;
        if (mGalleryDetail != null) {
            mGalleryDetail.isFavorited = !addOrRemove && mGalleryDetail.favoriteName != null;
            updateFavoriteDrawable();
        }
    }

    private void onModifyFavoritesFailure(boolean addOrRemove) {
        mModifingFavorites = false;
    }

    private void onModifyFavoritesCancel(boolean addOrRemove) {
        mModifingFavorites = false;
    }

    private class ModifyFavoritesListener extends EhCallback<GalleryDetailScene, Void> {

        private final boolean mAddOrRemove;

        /**
         * @param addOrRemove false for add, true for remove
         */
        public ModifyFavoritesListener(Context context, int stageId, String sceneTag, boolean addOrRemove) {
            super(context, stageId, sceneTag);
            mAddOrRemove = addOrRemove;
        }

        @Override
        public void onSuccess(Void result) {
            showTip(mAddOrRemove ? R.string.remove_from_favorite_success :
                    R.string.add_to_favorite_success, LENGTH_SHORT);
            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onModifyFavoritesSuccess(mAddOrRemove);
            }
        }

        @Override
        public void onFailure(Exception e) {
            showTip(mAddOrRemove ? R.string.remove_from_favorite_failure :
                    R.string.add_to_favorite_failure, LENGTH_LONG);
            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onModifyFavoritesFailure(mAddOrRemove);
            }
        }

        @Override
        public void onCancel() {
            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onModifyFavoritesCancel(mAddOrRemove);
            }
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }

    private class DownloadArchiveListener extends EhCallback<GalleryDetailScene, Void> {

        public DownloadArchiveListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(Void result) {
            showTip(R.string.download_archive_started, LENGTH_SHORT);
        }

        @Override
        public void onFailure(Exception e) {
            if (e instanceof NoHAtHClientException) {
                showTip(R.string.download_archive_failure_no_hath, LENGTH_LONG);
            } else {
                showTip(R.string.download_archive_failure, LENGTH_LONG);
            }
        }

        @Override
        public void onCancel() {}

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }

    private class ArchiveListDialogHelper implements AdapterView.OnItemClickListener,
            DialogInterface.OnDismissListener, EhClient.Callback<Pair<String, Pair<String, String>[]>> {

        @Nullable
        private ProgressView mProgressView;
        @Nullable
        private TextView mErrorText;
        @Nullable
        private ListView mListView;
        @Nullable
        private EhRequest mRequest;
        @Nullable
        private Dialog mDialog;

        public void setDialog(@Nullable Dialog dialog, String url) {
            mDialog = dialog;
            mProgressView = (ProgressView) ViewUtils.$$(dialog, R.id.progress);
            mErrorText = (TextView) ViewUtils.$$(dialog, R.id.text);
            mListView = (ListView) ViewUtils.$$(dialog, R.id.list_view);
            mListView.setOnItemClickListener(this);

            Context context = getContext2();
            if (context != null) {
                if (mArchiveList == null) {
                    mErrorText.setVisibility(View.GONE);
                    mListView.setVisibility(View.GONE);
                    mRequest = new EhRequest().setMethod(EhClient.METHOD_ARCHIVE_LIST)
                            .setArgs(url, mGid, mToken)
                            .setCallback(this);
                    EhApplication.getEhClient(context).execute(mRequest);
                } else {
                    bind(mArchiveList);
                }
            }
        }

        private void bind(Pair<String, String>[] data) {
            if (null == mDialog || null == mProgressView || null == mErrorText || null == mListView) {
                return;
            }

            if (0 == data.length) {
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.GONE);
                mErrorText.setText(R.string.no_archives);
            } else {
                String[] nameArray = new String[data.length];
                for (int i = 0, n = data.length; i < n; i++) {
                    nameArray[i] = data[i].second;
                }
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.GONE);
                mListView.setVisibility(View.VISIBLE);
                mListView.setAdapter(new ArrayAdapter<>(mDialog.getContext(), R.layout.item_select_dialog, nameArray));
            }
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Context context = getContext2();
            MainActivity activity = getActivity2();
            if (null != context && null != activity && null != mArchiveList && position < mArchiveList.length) {
                String res = mArchiveList[position].first;
                EhRequest request = new EhRequest();
                request.setMethod(EhClient.METHOD_DOWNLOAD_ARCHIVE);
                request.setArgs(mGalleryDetail.gid, mGalleryDetail.token, mArchiveFormParamOr, res);
                request.setCallback(new DownloadArchiveListener(context, activity.getStageId(), getTag()));
                EhApplication.getEhClient(context).execute(request);
            }

            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (mRequest != null) {
                mRequest.cancel();
                mRequest = null;
            }
            mDialog = null;
            mProgressView = null;
            mErrorText = null;
            mListView = null;
        }

        @Override
        public void onSuccess(Pair<String, Pair<String, String>[]> result) {
            if (mRequest != null) {
                mRequest = null;
                mArchiveFormParamOr = result.first;
                mArchiveList = result.second;
                bind(result.second);
            }
        }

        @Override
        public void onFailure(Exception e) {
            mRequest = null;
            Context context = getContext2();
            if (null != context && null != mProgressView && null != mErrorText && null != mListView) {
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.GONE);
                mErrorText.setText(ExceptionUtils.getReadableString(e));
            }
        }

        @Override
        public void onCancel() { mRequest = null; }
    }

    private class TorrentListDialogHelper implements AdapterView.OnItemClickListener,
            DialogInterface.OnDismissListener, EhClient.Callback<Pair<String, String>[]> {

        @Nullable
        private ProgressView mProgressView;
        @Nullable
        private TextView mErrorText;
        @Nullable
        private ListView mListView;
        @Nullable
        private EhRequest mRequest;
        @Nullable
        private Dialog mDialog;

        public void setDialog(@Nullable Dialog dialog, String url) {
            mDialog = dialog;
            mProgressView = (ProgressView) ViewUtils.$$(dialog, R.id.progress);
            mErrorText = (TextView) ViewUtils.$$(dialog, R.id.text);
            mListView = (ListView) ViewUtils.$$(dialog, R.id.list_view);
            mListView.setOnItemClickListener(this);

            Context context = getContext2();
            if (context != null) {
                if (mTorrentList == null) {
                    mErrorText.setVisibility(View.GONE);
                    mListView.setVisibility(View.GONE);
                    mRequest = new EhRequest().setMethod(EhClient.METHOD_GET_TORRENT_LIST)
                            .setArgs(url, mGid, mToken)
                            .setCallback(this);
                    EhApplication.getEhClient(context).execute(mRequest);
                } else {
                    bind(mTorrentList);
                }
            }
        }

        private void bind(Pair<String, String>[] data) {
            if (null == mDialog || null == mProgressView || null == mErrorText || null == mListView) {
                return;
            }

            if (0 == data.length) {
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.GONE);
                mErrorText.setText(R.string.no_torrents);
            } else {
                String[] nameArray = new String[data.length];
                for (int i = 0, n = data.length; i < n; i++) {
                    nameArray[i] = data[i].second;
                }
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.GONE);
                mListView.setVisibility(View.VISIBLE);
                mListView.setAdapter(new ArrayAdapter<>(mDialog.getContext(), R.layout.item_select_dialog, nameArray));
            }
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Context context = getContext2();
            if (null != context && null != mTorrentList && position < mTorrentList.length) {
                String url = mTorrentList[position].first;
                String name = mTorrentList[position].second;
                // Use system download service
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        FileUtils.sanitizeFilename(name + ".torrent"));
                r.allowScanningByMediaScanner();
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.addRequestHeader("Cookie", EhApplication.getEhCookieStore(context).getCookieHeader(HttpUrl.get(url)));
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    try {
                        dm.enqueue(r);
                    } catch (Throwable e) {
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
            }

            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (mRequest != null) {
                mRequest.cancel();
                mRequest = null;
            }
            mDialog = null;
            mProgressView = null;
            mErrorText = null;
            mListView = null;
        }

        @Override
        public void onSuccess(Pair<String, String>[] result) {
            if (mRequest != null) {
                mRequest = null;
                mTorrentList = result;
                bind(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            mRequest = null;
            Context context = getContext2();
            if (null != context && null != mProgressView && null != mErrorText && null != mListView) {
                mProgressView.setVisibility(View.GONE);
                mErrorText.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.GONE);
                mErrorText.setText(ExceptionUtils.getReadableString(e));
            }
        }

        @Override
        public void onCancel() {
            mRequest = null;
        }
    }

    private static class GetGalleryDetailListener extends EhCallback<GalleryDetailScene, GalleryDetail> {

        public GetGalleryDetailListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(GalleryDetail result) {
            getApplication().removeGlobalStuff(this);

            // Put gallery detail to cache
            EhApplication.getGalleryDetailCache(getApplication()).put(result.gid, result);

            // Add history
            EhDB.putHistoryInfo(result);

            // Notify success
            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryDetailSuccess(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            getApplication().removeGlobalStuff(this);
            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryDetailFailure(e);
            }
        }

        @Override
        public void onCancel() {
            getApplication().removeGlobalStuff(this);
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }

    private class RateDialogHelper implements GalleryRatingBar.OnUserRateListener,
            DialogInterface.OnClickListener {

        @Nullable
        private GalleryRatingBar mRatingBar;
        @Nullable
        private TextView mRatingText;

        public void setDialog(Dialog dialog, float rating) {
            mRatingText = (TextView) ViewUtils.$$(dialog, R.id.rating_text);
            mRatingBar = (GalleryRatingBar) ViewUtils.$$(dialog, R.id.rating_view);
            mRatingText.setText(getRatingText(rating, getResources2()));
            mRatingBar.setRating(rating);
            mRatingBar.setOnUserRateListener(this);
        }

        @Override
        public void onUserRate(float rating) {
            if (null != mRatingText) {
                mRatingText.setText(getRatingText(rating, getResources2()));
            }
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Context context = getContext2();
            MainActivity activity = getActivity2();
            if (null == context || null == activity || which != DialogInterface.BUTTON_POSITIVE ||
                    null == mGalleryDetail || null == mRatingBar) {
                return;
            }

            EhRequest request = new EhRequest()
                    .setMethod(EhClient.METHOD_GET_RATE_GALLERY)
                    .setArgs(mGalleryDetail.apiUid, mGalleryDetail.apiKey,
                            mGalleryDetail.gid, mGalleryDetail.token, mRatingBar.getRating())
                    .setCallback(new RateGalleryListener(context,
                            activity.getStageId(), getTag(), mGalleryDetail.gid));
            EhApplication.getEhClient(context).execute(request);
        }
    }

    private static class RateGalleryListener extends EhCallback<GalleryDetailScene, RateGalleryParser.Result> {

        private final long mGid;

        public RateGalleryListener(Context context, int stageId, String sceneTag, long gid) {
            super(context, stageId, sceneTag);
            mGid = gid;
        }

        @Override
        public void onSuccess(RateGalleryParser.Result result) {
            showTip(R.string.rate_successfully, LENGTH_SHORT);

            GalleryDetailScene scene = getScene();
            if (scene != null) {
                scene.onRateGallerySuccess(result);
            } else {
                // Update rating in cache
                GalleryDetail gd = EhApplication.getGalleryDetailCache(getApplication()).get(mGid);
                if (gd != null) {
                    gd.rating = result.rating;
                    gd.ratingCount = result.ratingCount;
                }
            }
        }

        @Override
        public void onFailure(Exception e) {
            e.printStackTrace();
            showTip(R.string.rate_failed, LENGTH_LONG);
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }
}

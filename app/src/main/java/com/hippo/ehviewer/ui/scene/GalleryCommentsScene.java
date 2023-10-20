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

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.LinearDividerItemDecoration;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryComment;
import com.hippo.ehviewer.client.data.GalleryCommentList;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.parser.VoteCommentParser;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.reveal.ViewAnimationUtils;
import com.hippo.ripple.Ripple;
import com.hippo.scene.SceneFragment;
import com.hippo.text.Html;
import com.hippo.text.URLImageGetter;
import com.hippo.util.BlackListUtils;
import com.hippo.util.DrawableManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.ReadableTime;
import com.hippo.util.TextUrl;
import com.hippo.view.ViewTransition;
import com.hippo.widget.FabLayout;
import com.hippo.widget.LinkifyTextView;
import com.hippo.widget.ObservedTextView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.ResourcesUtils;
import com.hippo.yorozuya.SimpleAnimatorListener;
import com.hippo.yorozuya.StringUtils;
import com.hippo.yorozuya.ViewUtils;
import com.hippo.yorozuya.collect.IntList;
import java.util.ArrayList;
import java.util.List;

/**
 * 画廊评论对象
 */
public final class GalleryCommentsScene extends ToolbarScene
        implements EasyRecyclerView.OnItemClickListener,
        View.OnClickListener {

    public static final String TAG = GalleryCommentsScene.class.getSimpleName();

    public static final String KEY_API_UID = "api_uid";
    public static final String KEY_API_KEY = "api_key";
    public static final String KEY_GID = "gid";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_COMMENT_LIST = "comment_list";

    private long mApiUid;
    private String mApiKey;
    private long mGid;
    private String mToken;
    @Nullable
    private GalleryCommentList mCommentList;

    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private FabLayout mFabLayout;
    @Nullable
    private FloatingActionButton mFab;
    @Nullable
    private View mEditPanel;
    @Nullable
    private ImageView mSendImage;
    @Nullable
    private EditText mEditText;
    @Nullable
    private CommentAdapter mAdapter;
    @Nullable
    private ViewTransition mViewTransition;

    private Drawable mSendDrawable;
    private Drawable mPencilDrawable;
    private long mCommentId;

    private boolean mInAnimation = false;

    private boolean mShowAllComments = false;
    private boolean mRefreshingComments = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private void handleArgs(Bundle args) {
        if (args == null) {
            return;
        }

        mApiUid = args.getLong(KEY_API_UID, -1L);
        mApiKey = args.getString(KEY_API_KEY);
        mGid = args.getLong(KEY_GID, -1L);
        mToken = args.getString(KEY_TOKEN, null);
        mCommentList = args.getParcelable(KEY_COMMENT_LIST);
        mShowAllComments = mCommentList != null && !mCommentList.hasMore;
    }

    private void onInit() {
        handleArgs(getArguments());
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mApiUid = savedInstanceState.getLong(KEY_API_UID, -1L);
        mApiKey = savedInstanceState.getString(KEY_API_KEY);
        mGid = savedInstanceState.getLong(KEY_GID, -1L);
        mToken = savedInstanceState.getString(KEY_TOKEN, null);
        mCommentList = savedInstanceState.getParcelable(KEY_COMMENT_LIST);
        mShowAllComments = mCommentList != null && !mCommentList.hasMore;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_API_UID, mApiUid);
        outState.putString(KEY_API_KEY, mApiKey);
        outState.putLong(KEY_GID, mGid);
        outState.putString(KEY_TOKEN, mToken);
        outState.putParcelable(KEY_COMMENT_LIST, mCommentList);
    }


    /**
     * 评论详情页创建
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_gallery_comments, container, false);
        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(view, R.id.recycler_view);
        TextView tip = (TextView) ViewUtils.$$(view, R.id.tip);
        mEditPanel = ViewUtils.$$(view, R.id.edit_panel);
        mSendImage = (ImageView) ViewUtils.$$(mEditPanel, R.id.send);
        mEditText = (EditText) ViewUtils.$$(mEditPanel, R.id.edit_text);
        mFabLayout = (FabLayout) ViewUtils.$$(view, R.id.fab_layout);
        mFab = (FloatingActionButton) ViewUtils.$$(view, R.id.fab);

        Context context = getEHContext();
        AssertUtils.assertNotNull(context);
        Resources resources = context.getResources();
        int paddingBottomFab = resources.getDimensionPixelOffset(R.dimen.gallery_padding_bottom_fab);

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_sad_pandroid);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);

        mSendDrawable = DrawableManager.getVectorDrawable(context, R.drawable.v_send_dark_x24);
        mPencilDrawable = DrawableManager.getVectorDrawable(context, R.drawable.v_pencil_dark_x24);

        mAdapter = new CommentAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context,
                RecyclerView.VERTICAL, false));
        LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
                LinearDividerItemDecoration.VERTICAL, AttrResources.getAttrColor(context, R.attr.dividerColor),
                LayoutUtils.dp2pix(context, 1));
        decoration.setShowLastDivider(true);
        mRecyclerView.addItemDecoration(decoration);
        mRecyclerView.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, androidx.appcompat.R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setOnItemClickListener(this);
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), mRecyclerView.getPaddingTop(),
                mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom() + paddingBottomFab);
        // Cancel change animator
        RecyclerView.ItemAnimator itemAnimator = mRecyclerView.getItemAnimator();
        if (itemAnimator instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }

        mSendImage.setOnClickListener(this);
        mFab.setOnClickListener(this);

        addAboveSnackView(mEditPanel);
        addAboveSnackView(mFabLayout);

        mViewTransition = new ViewTransition(mRecyclerView, tip);

        updateView(false);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }
        if (null != mEditPanel) {
            removeAboveSnackView(mEditPanel);
            mEditPanel = null;
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout);
            mFabLayout = null;
        }

        mFab = null;
        mSendImage = null;
        mEditText = null;
        mAdapter = null;
        mViewTransition = null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.gallery_comments);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public void onNavigationClick(View view) {
        onBackPressed();
    }

    private void voteComment(long id, int vote) {
        Context context = getEHContext();
        MainActivity activity = getActivity2();
        if (null == context || null == activity) {
            return;
        }

        EhRequest request = new EhRequest()
                .setMethod(EhClient.METHOD_VOTE_COMMENT)
                .setArgs(mApiUid, mApiKey, mGid, mToken, id, vote)
                .setCallback(new VoteCommentListener(context,
                        activity.getStageId(), getTag()));
        EhApplication.getEhClient(context).execute(request);
    }




    private class InfoHolder extends RecyclerView.ViewHolder {

        private final TextView key;
        private final TextView value;

        public InfoHolder(View itemView) {
            super(itemView);
            key = (TextView) ViewUtils.$$(itemView, R.id.key);
            value = (TextView) ViewUtils.$$(itemView, R.id.value);
        }
    }

    @SuppressLint("InflateParams")
    public void showVoteStatusDialog(Context context, String voteStatus) {
        String[] temp = StringUtils.split(voteStatus, ',');
        final int length = temp.length;
        final String[] userArray = new String[length];
        final String[] voteArray = new String[length];
        for (int i = 0; i < length; i++) {
            String str = StringUtils.trim(temp[i]);
            int index = str.lastIndexOf(' ');
            if (index < 0) {
                Log.d(TAG, "Something wrong happened about vote state");
                userArray[i] = str;
                voteArray[i] = "";
            } else {
                userArray[i] = StringUtils.trim(str.substring(0, index));
                voteArray[i] = StringUtils.trim(str.substring(index + 1));
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        context = builder.getContext();
        final LayoutInflater inflater = LayoutInflater.from(context);
        EasyRecyclerView rv = (EasyRecyclerView) inflater.inflate(R.layout.dialog_recycler_view, null);
        rv.setAdapter(new RecyclerView.Adapter<InfoHolder>() {
            @Override
            public InfoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new InfoHolder(inflater.inflate(R.layout.item_drawer_favorites, parent, false));
            }

            @Override
            public void onBindViewHolder(InfoHolder holder, int position) {
                holder.key.setText(userArray[position]);
                holder.value.setText(voteArray[position]);
            }

            @Override
            public int getItemCount() {
                return length;
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(context));
        LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
                LinearDividerItemDecoration.VERTICAL, AttrResources.getAttrColor(context, R.attr.dividerColor),
                LayoutUtils.dp2pix(context, 1));
        decoration.setPadding(ResourcesUtils.getAttrDimensionPixelOffset(context, androidx.appcompat.R.attr.dialogPreferredPadding));
        rv.addItemDecoration(decoration);
        rv.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, androidx.appcompat.R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        rv.setClipToPadding(false);
        builder.setView(rv).show();
    }

    private void showCommentDialog(int position) {
        final Context context = getEHContext();
        if (context == null || mCommentList == null || mCommentList.comments == null || position >= mCommentList.comments.length || position < 0) {
            return;
        }

        final GalleryComment comment = mCommentList.comments[position];
        List<String> menu = new ArrayList<>();
        final IntList menuId = new IntList();
        Resources resources = context.getResources();

        menu.add(resources.getString(R.string.copy_comment_text));
        menuId.add(R.id.copy);
        menu.add(resources.getString(R.string.join_in_blacklist));
        menuId.add(R.id.join_blacklist);
        if (comment.editable) {
            menu.add(resources.getString(R.string.edit_comment));
            menuId.add(R.id.edit_comment);
        }
        if (comment.voteUpAble) {
            menu.add(resources.getString(comment.voteUpEd ? R.string.cancel_vote_up : R.string.vote_up));
            menuId.add(R.id.vote_up);
        }
        if (comment.voteDownAble) {
            menu.add(resources.getString(comment.voteDownEd ? R.string.cancel_vote_down : R.string.vote_down));
            menuId.add(R.id.vote_down);
        }
        if (!TextUtils.isEmpty(comment.voteState)) {
            menu.add(resources.getString(R.string.check_vote_status));
            menuId.add(R.id.check_vote_status);
        }

        new AlertDialog.Builder(context)
                .setItems(menu.toArray(new String[menu.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0 || which >= menuId.size()) {
                           return;
                        }
                        int id = menuId.get(which);
                        switch (id) {
                            case R.id.copy:
                                ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                cmb.setPrimaryClip(ClipData.newPlainText(null, comment.comment));
                                showTip(R.string.copied_to_clipboard, LENGTH_SHORT);
                                break;
                            case R.id.vote_up:
                                voteComment(comment.id, 1);
                                break;
                            case R.id.vote_down:
                                voteComment(comment.id, -1);
                                break;
                            case R.id.check_vote_status:
                                showVoteStatusDialog(context, comment.voteState);
                                break;
                            case R.id.edit_comment:
                                prepareEditComment(comment.id);
                                if (!mInAnimation && mEditPanel != null && mEditPanel.getVisibility() != View.VISIBLE) {
                                    showEditPanel(true);
                                }
                                break;
                            case R.id.join_blacklist:
                                EhDB.insertBlackList(BlackListUtils.parseBlacklist(comment));
                                mCommentList.DeleteComment(position);
                                mAdapter.notifyDataSetChanged();
                                updateView(true);
                                break;
                        }
                    }
                }).show();
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        MainActivity activity = getActivity2();
        if (null == activity) {
            return false;
        }

        RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
        if (holder instanceof ActualCommentHolder) {
            ActualCommentHolder commentHolder = (ActualCommentHolder) holder;
            ClickableSpan span = commentHolder.comment.getCurrentSpan();
            commentHolder.comment.clearCurrentSpan();

            if (span instanceof URLSpan) {
                UrlOpener.openUrl(activity, ((URLSpan) span).getURL(), true);
            } else {
                showCommentDialog(position);
            }
        } else if (holder instanceof MoreCommentHolder && !mRefreshingComments && mAdapter != null) {
            mRefreshingComments = true;
            mShowAllComments = true;
            mAdapter.notifyItemChanged(position);

            String url = getGalleryDetailUrl();
            if (url != null) {
                // Request
                EhRequest request = new EhRequest()
                    .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
                    .setArgs(url)
                    .setCallback(new RefreshCommentListener(activity, activity.getStageId(), getTag()));
                EhApplication.getEhClient(activity).execute(request);
            }
        }

        return true;
    }

    private void updateView(boolean animation) {
        if (null == mViewTransition) {
            return;
        }

        if (mCommentList == null || mCommentList.comments == null || mCommentList.comments.length <= 0) {
            mViewTransition.showView(1, animation);
        } else {
            mViewTransition.showView(0, animation);
        }
    }

    private void prepareNewComment() {
        mCommentId = 0;
        if (mSendImage != null) {
            mSendImage.setImageDrawable(mSendDrawable);
        }
    }

    private void prepareEditComment(long commentId) {
        mCommentId = commentId;
        if (mSendImage != null) {
            mSendImage.setImageDrawable(mPencilDrawable);
        }
    }

    private void showEditPanelWithAnimation() {
        if (null == mFab || null == mEditPanel) {
            return;
        }

        mInAnimation = true;
        mFab.setTranslationX(0.0f);
        mFab.setTranslationY(0.0f);
        mFab.setScaleX(1.0f);
        mFab.setScaleY(1.0f);
        int fabEndX = mEditPanel.getLeft() + (mEditPanel.getWidth() / 2) - (mFab.getWidth() / 2);
        int fabEndY = mEditPanel.getTop() + (mEditPanel.getHeight() / 2) - (mFab.getHeight() / 2);
        mFab.animate().x(fabEndX).y(fabEndY).scaleX(0.0f).scaleY(0.0f)
                .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                .setDuration(300L).setListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (null == mFab || null == mEditPanel) {
                    return;
                }

                ((View) mFab).setVisibility(View.INVISIBLE);
                mEditPanel.setVisibility(View.VISIBLE);
                int halfW = mEditPanel.getWidth() / 2;
                int halfH = mEditPanel.getHeight() / 2;
                Animator animator = ViewAnimationUtils.createCircularReveal(mEditPanel, halfW, halfH, 0,
                        (float) Math.hypot(halfW, halfH)).setDuration(300L);
                animator.addListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        mInAnimation = false;
                    }
                });
                animator.start();
            }
        }).start();
    }

    private void showEditPanel(boolean animation) {
        if (animation) {
            showEditPanelWithAnimation();
        } else {
            if (null == mFab || null == mEditPanel) {
                return;
            }

            ((View) mFab).setVisibility(View.INVISIBLE);
            mEditPanel.setVisibility(View.VISIBLE);
        }
    }

    private void hideEditPanelWithAnimation() {
        if (null == mFab || null == mEditPanel) {
            return;
        }

        mInAnimation = true;
        int halfW = mEditPanel.getWidth() / 2;
        int halfH = mEditPanel.getHeight() / 2;
        Animator animator = ViewAnimationUtils.createCircularReveal(mEditPanel, halfW, halfH,
                (float) Math.hypot(halfW, halfH), 0.0f).setDuration(300L);
        animator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (null == mFab || null == mEditPanel) {
                    return;
                }

                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Some devices may run this block in non-UI thread.
                    // It might be a bug of Android OS.
                    // Check it here to avoid crash.
                    return;
                }

                mEditPanel.setVisibility(View.GONE);
                ((View) mFab).setVisibility(View.VISIBLE);
                int fabStartX = mEditPanel.getLeft() + (mEditPanel.getWidth() / 2) - (mFab.getWidth() / 2);
                int fabStartY = mEditPanel.getTop() + (mEditPanel.getHeight() / 2) - (mFab.getHeight() / 2);
                mFab.setX(fabStartX);
                mFab.setY(fabStartY);
                mFab.setScaleX(0.0f);
                mFab.setScaleY(0.0f);
                mFab.setRotation(-45.0f);
                mFab.animate().translationX(0.0f).translationY(0.0f).scaleX(1.0f).scaleY(1.0f).rotation(0.0f)
                        .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                        .setDuration(300L).setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mInAnimation = false;
                    }
                }).start();
            }
        });
        animator.start();
    }

    private void hideEditPanel(boolean animation) {
        if (animation) {
            hideEditPanelWithAnimation();
        } else {
            if (null == mFab || null == mEditPanel) {
                return;
            }

            ((View) mFab).setVisibility(View.VISIBLE);
            mEditPanel.setVisibility(View.INVISIBLE);
        }
    }

    @Nullable
    private String getGalleryDetailUrl() {
        if (mGid != -1 && mToken != null) {
            return EhUrl.getGalleryDetailUrl(mGid, mToken, 0, mShowAllComments);
        } else {
            return null;
        }
    }

    /**
     * 点击发送评论按钮时触发
     * @param v
     */
    @Override
    public void onClick(View v) {
        Context context = getEHContext();
        MainActivity activity = getActivity2();
        if (null == context || null == activity || null == mEditText) {
            return;
        }

        if (mFab == v) {
            if (!mInAnimation) {
                prepareNewComment();
                showEditPanel(true);
            }
        } else if (mSendImage == v) {
            if (!mInAnimation) {
                String comment = mEditText.getText().toString();
                if (TextUtils.isEmpty(comment)) {
                    // Comment is empty
                    return;
                }
                String url = getGalleryDetailUrl();
                if (url == null) {
                    return;
                }
                // Request
                EhRequest request = new EhRequest()
                        .setMethod(EhClient.METHOD_GET_COMMENT_GALLERY)
                        .setArgs(url, comment, mCommentId != 0 ? Long.toString(mCommentId) : null)
                        .setCallback(new CommentGalleryListener(context,
                                activity.getStageId(), getTag(), mCommentId));
                EhApplication.getEhClient(context).execute(request);
                hideSoftInput();
                hideEditPanel(true);
            }
        }
    }

    /**
     * 点击评论详情页右上角返回图标时触发
     */
    @Override
    public void onBackPressed() {
        if (mInAnimation) {
            return;
        }
        if (null != mEditPanel && mEditPanel.getVisibility() == View.VISIBLE) {
            hideEditPanel(true);
        } else {
            finish();
        }
    }

    private static final int TYPE_COMMENT = 0;
    private static final int TYPE_MORE = 1;
    private static final int TYPE_PROGRESS = 2;

    private abstract class CommentHolder extends RecyclerView.ViewHolder {
        public CommentHolder(LayoutInflater inflater, int resId, ViewGroup parent) {
            super(inflater.inflate(resId, parent, false));
        }
    }

    private class ActualCommentHolder extends CommentHolder {

        private final TextView user;
        private final TextView time;
        private final LinkifyTextView comment;

        public ActualCommentHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater, R.layout.item_gallery_comment, parent);
            user = itemView.findViewById(R.id.user);
            time = itemView.findViewById(R.id.time);
            comment = itemView.findViewById(R.id.comment);
        }

        private CharSequence generateComment(Context context, ObservedTextView textView, GalleryComment comment) {
            SpannableStringBuilder ssb = Html.fromHtml(comment.comment, new URLImageGetter(textView,
                EhApplication.getConaco(context)), null);

            if (0 != comment.id && 0 != comment.score) {
                int score = comment.score;
                String scoreString = score > 0 ? "+" + score : Integer.toString(score);
                SpannableString ss = new SpannableString(scoreString);
                ss.setSpan(new RelativeSizeSpan(0.8f), 0, scoreString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(Typeface.BOLD), 0, scoreString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(AttrResources.getAttrColor(context, android.R.attr.textColorSecondary))
                    , 0, scoreString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("  ").append(ss);
            }

            if (comment.lastEdited != 0) {
                String str = context.getString(R.string.last_edited, ReadableTime.getTimeAgo(comment.lastEdited));
                SpannableString ss = new SpannableString(str);
                ss.setSpan(new RelativeSizeSpan(0.8f), 0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(Typeface.BOLD), 0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(AttrResources.getAttrColor(context, android.R.attr.textColorSecondary)),
                    0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("\n\n").append(ss);
            }

            return TextUrl.handleTextUrl(ssb);
        }

        public void bind(GalleryComment value) {
            user.setText(value.user);
            time.setText(ReadableTime.getTimeAgo(value.time));
            comment.setText(generateComment(comment.getContext(), comment, value));
        }
    }

    private class MoreCommentHolder extends CommentHolder {
        public MoreCommentHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater, R.layout.item_gallery_comment_more, parent);
        }
    }

    private class ProgressCommentHolder extends CommentHolder {
        public ProgressCommentHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater, R.layout.item_gallery_comment_progress, parent);
        }
    }

    private class CommentAdapter extends RecyclerView.Adapter<CommentHolder> {

        private final LayoutInflater mInflater;

        public CommentAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);
        }

        @Override
        public CommentHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case TYPE_COMMENT:
                    return new ActualCommentHolder(mInflater, parent);
                case TYPE_MORE:
                    return new MoreCommentHolder(mInflater, parent);
                case TYPE_PROGRESS:
                    return new ProgressCommentHolder(mInflater, parent);
                default:
                    throw new IllegalStateException("Invalid view type: " + viewType);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull CommentHolder holder, int position) {
            Context context = getEHContext();
            if (context == null || mCommentList == null) {
                return;
            }

            if (holder instanceof ActualCommentHolder) {
                ((ActualCommentHolder) holder).bind(mCommentList.comments[position]);
            }
        }

        @Override
        public int getItemCount() {
            if (mCommentList == null || mCommentList.comments == null) {
                return 0;
            } else if (mCommentList.hasMore) {
                return mCommentList.comments.length + 1;
            } else {
                return mCommentList.comments.length;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= mCommentList.comments.length) {
                return mRefreshingComments ? TYPE_PROGRESS : TYPE_MORE;
            } else {
                return TYPE_COMMENT;
            }
        }
    }

    private void onRefreshGallerySuccess(GalleryCommentList result) {
        if (mAdapter == null) {
            return;
        }

        mRefreshingComments = false;
        mCommentList = result;
        mAdapter.notifyDataSetChanged();

        updateView(true);

        Bundle re = new Bundle();
        re.putParcelable(KEY_COMMENT_LIST, result);
        setResult(SceneFragment.RESULT_OK, re);
    }

    private void onRefreshGalleryFailure() {
        if (mAdapter == null) {
            return;
        }

        mRefreshingComments = false;
        int position = mAdapter.getItemCount() - 1;
        if (position >= 0) {
            mAdapter.notifyItemChanged(position);
        }
    }

    private void onCommentGallerySuccess(GalleryCommentList result) {
        if (mAdapter == null) {
            return;
        }

        mCommentList = result;
        mAdapter.notifyDataSetChanged();
        Bundle re = new Bundle();
        re.putParcelable(KEY_COMMENT_LIST, result);
        setResult(SceneFragment.RESULT_OK, re);

        // Remove text
        if (mEditText != null) {
            mEditText.setText("");
        }

        updateView(true);
    }

    private void onVoteCommentSuccess(VoteCommentParser.Result result) {
        if (mAdapter == null || mCommentList == null || mCommentList.comments == null ) {
            return;
        }

        int position = -1;
        for (int i = 0, n = mCommentList.comments.length; i < n; i++) {
            GalleryComment comment = mCommentList.comments[i];
            if (comment.id == result.id) {
                position = i;
                break;
            }
        }

        if (-1 == position) {
            Log.d(TAG, "Can't find comment with id " + result.id);
            return;
        }

        // Update comment
        GalleryComment comment = mCommentList.comments[position];
        comment.score = result.score;
        if (result.expectVote > 0) {
            comment.voteUpEd = 0 != result.vote;
            comment.voteDownEd = false;
        } else {
            comment.voteDownEd = 0 != result.vote;
            comment.voteUpEd = false;
        }

        mAdapter.notifyItemChanged(position);

        Bundle re = new Bundle();
        re.putParcelable(KEY_COMMENT_LIST, mCommentList);
        setResult(SceneFragment.RESULT_OK, re);
    }

    private static class RefreshCommentListener extends EhCallback<GalleryCommentsScene, GalleryDetail> {

        public RefreshCommentListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(GalleryDetail result) {
            GalleryCommentsScene scene = getScene();
            if (scene != null) {
                scene.onRefreshGallerySuccess(result.comments);
            }
        }

        @Override
        public void onFailure(Exception e) {
            GalleryCommentsScene scene = getScene();
            if (scene != null) {
                scene.onRefreshGalleryFailure();
            }
        }

        @Override
        public void onCancel() { }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryCommentsScene;
        }
    }

    private static class CommentGalleryListener extends EhCallback<GalleryCommentsScene, GalleryCommentList> {

        private long mCommentId;

        public CommentGalleryListener(Context context, int stageId, String sceneTag, long commentId) {
            super(context, stageId, sceneTag);
            mCommentId = commentId;
        }

        @Override
        public void onSuccess(GalleryCommentList result) {
            showTip(mCommentId != 0 ? R.string.edit_comment_successfully : R.string.comment_successfully, LENGTH_SHORT);

            GalleryCommentsScene scene = getScene();
            if (scene != null) {
                scene.onCommentGallerySuccess(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            showTip(getContent().getString(mCommentId != 0 ? R.string.edit_comment_failed : R.string.comment_failed) + "\n" + ExceptionUtils.getReadableString(e), LENGTH_LONG);
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryCommentsScene;
        }
    }

    private static class VoteCommentListener extends EhCallback<GalleryCommentsScene, VoteCommentParser.Result> {

        public VoteCommentListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(VoteCommentParser.Result result) {
            showTip(result.expectVote > 0 ?
                    (0 != result.vote ? R.string.vote_up_successfully : R.string.cancel_vote_up_successfully) :
                    (0 != result.vote ? R.string.vote_down_successfully : R.string.cancel_vote_down_successfully),
                    LENGTH_SHORT);

            GalleryCommentsScene scene = getScene();
            if (scene != null) {
                scene.onVoteCommentSuccess(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            showTip(R.string.vote_failed, LENGTH_LONG);
        }

        @Override
        public void onCancel() {}

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryCommentsScene;
        }
    }
}

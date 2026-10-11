/*
 * Copyright 2026 Pegion Fish
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

package com.hippo.anotherviewer.ui.pane;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.SiteDB;
import com.hippo.anotherviewer.client.PrivacyMask;
import com.hippo.anotherviewer.client.SiteCacheKeyFactory;
import com.hippo.anotherviewer.client.SiteClient;
import com.hippo.anotherviewer.client.SiteRequest;
import com.hippo.anotherviewer.client.SiteUrl;
import com.hippo.anotherviewer.client.SiteUtils;
import com.hippo.anotherviewer.client.data.GalleryDetail;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.client.data.GalleryTagGroup;
import com.hippo.anotherviewer.event.PrivacyMaskChanged;
import com.hippo.anotherviewer.sync.GalleryDetailTagsSyncTask;
import com.hippo.anotherviewer.ui.GalleryActivity;
import com.hippo.anotherviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.scene.Announcer;
import com.hippo.scene.StageActivity;
import com.hippo.util.ExceptionUtils;
import com.hippo.lib.yorozuya.IntIdGenerator;
import com.hippo.widget.LoadImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;

/**
 * 双屏双栏模式下右栏的轻量画廊详情面板。与完整详情场景
 * {@link GalleryDetailScene}（SceneFragment 体系，强依赖 StageActivity
 * 的场景栈与抽屉回调）不同，本类是普通 androidx Fragment：
 * <ul>
 *     <li>不进入 StageActivity 的场景栈，不触发抽屉/导航栏回调，
 *     左栏原有场景体系零感知；</li>
 *     <li>复用详情数据链路：GalleryDetail 缓存（LruCache）、SiteClient 的
 *     METHOD_GET_GALLERY_DETAIL 请求、GalleryDetailTagsSyncTask
 *     标签同步与历史记录写入，与完整详情场景共享同一份缓存；</li>
 *     <li>只展示封面、标题、分类、评分、页数、日期、标签等只读信息与
 *     "阅读"（启动 GalleryActivity）、"完整详情"（进完整详情场景）两个入口，
 *     不重复实现下载、评论、收藏等重逻辑。</li>
 * </ul>
 */
public class GalleryDetailPaneFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_GALLERY_DETAIL = "pane_gallery_detail";
    private static final String KEY_REQUEST_ID = "pane_request_id";

    @Nullable
    private GalleryInfo mGalleryInfo;
    @Nullable
    private GalleryDetail mGalleryDetail;
    private int mRequestId = IntIdGenerator.INVALID_ID;

    @Nullable
    private LoadImageView mThumb;
    @Nullable
    private TextView mTitle;
    @Nullable
    private TextView mUploader;
    @Nullable
    private TextView mCategory;
    @Nullable
    private TextView mRatingText;
    @Nullable
    private TextView mLanguage;
    @Nullable
    private TextView mPages;
    @Nullable
    private TextView mPosted;
    @Nullable
    private TextView mFavoredTimes;
    @Nullable
    private TextView mRead;
    @Nullable
    private TextView mFullDetail;
    @Nullable
    private TextView mTip;
    @Nullable
    private View mProgress;
    @Nullable
    private LinearLayout mTags;

    /** 通过列表点击创建面板，参数键复用 {@link GalleryDetailScene} 的语义。 */
    @NonNull
    public static GalleryDetailPaneFragment newInstance(@NonNull GalleryInfo galleryInfo) {
        GalleryDetailPaneFragment fragment = new GalleryDetailPaneFragment();
        Bundle args = new Bundle();
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, galleryInfo);
        fragment.setArguments(args);
        return fragment;
    }

    /** 当前展示的画廊信息；双栏恢复时供控制器回读。 */
    @Nullable
    public GalleryInfo getCurrentGallery() {
        if (mGalleryDetail != null) {
            return mGalleryDetail;
        }
        return mGalleryInfo;
    }

    /**
     * 切换到另一个画廊。相同画廊（gid+token 一致）时为幂等 no-op；
     * 不同画廊时清空详情并重新走缓存/网络链路。
     */
    public void updateGallery(@NonNull GalleryInfo galleryInfo) {
        if (isSameGallery(galleryInfo, mGalleryInfo)) {
            return;
        }
        mGalleryDetail = null;
        mRequestId = IntIdGenerator.INVALID_ID;
        mGalleryInfo = galleryInfo;
        SiteDB.putHistoryInfo(galleryInfo);
        if (mTitle == null) {
            // 尚未创建视图：onCreateView 会按新画廊初始化
            return;
        }
        bindFirst();
        // 空状态恢复：面板已有画廊时重新显示"阅读/完整详情"按钮
        setActionsVisible(true);
        if (!prepareData()) {
            showTip(getString(R.string.error_cannot_find_gallery));
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mGalleryInfo = args.getParcelable(GalleryDetailScene.KEY_GALLERY_INFO);
            // 与完整详情场景一致：进入详情即记入历史
            if (mGalleryInfo != null) {
                SiteDB.putHistoryInfo(mGalleryInfo);
            }
        }
        if (savedInstanceState != null) {
            mGalleryDetail = savedInstanceState.getParcelable(KEY_GALLERY_DETAIL);
            mRequestId = savedInstanceState.getInt(KEY_REQUEST_ID, IntIdGenerator.INVALID_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery_detail_pane, container, false);
        mThumb = (LoadImageView) view.findViewById(R.id.pane_thumb);
        mTitle = (TextView) view.findViewById(R.id.pane_title);
        mUploader = (TextView) view.findViewById(R.id.pane_uploader);
        mCategory = (TextView) view.findViewById(R.id.pane_category);
        mRatingText = (TextView) view.findViewById(R.id.pane_rating_text);
        mLanguage = (TextView) view.findViewById(R.id.pane_language);
        mPages = (TextView) view.findViewById(R.id.pane_pages);
        mPosted = (TextView) view.findViewById(R.id.pane_posted);
        mFavoredTimes = (TextView) view.findViewById(R.id.pane_favored_times);
        mRead = (TextView) view.findViewById(R.id.pane_read);
        mFullDetail = (TextView) view.findViewById(R.id.pane_full_detail);
        mTip = (TextView) view.findViewById(R.id.pane_tip);
        mProgress = view.findViewById(R.id.pane_progress);
        mTags = (LinearLayout) view.findViewById(R.id.pane_tags);

        mRead.setOnClickListener(this);
        mFullDetail.setOnClickListener(this);
        mTip.setOnClickListener(this);

        if (mGalleryInfo != null) {
            bindFirst();
            if (!prepareData()) {
                // 请求无法发起（参数缺失/网络层不可用）：给出失败提示，点击可重试
                showTip(getString(R.string.error_cannot_find_gallery));
            }
        } else {
            // 无画廊空状态：隐藏"阅读/完整详情"按钮，避免悬空误导
            setActionsVisible(false);
            showTip(getString(R.string.detail_pane_hint));
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mGalleryDetail != null) {
            outState.putParcelable(KEY_GALLERY_DETAIL, mGalleryDetail);
        }
        if (mRequestId != IntIdGenerator.INVALID_ID) {
            outState.putInt(KEY_REQUEST_ID, mRequestId);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumb = null;
        mTitle = null;
        mUploader = null;
        mCategory = null;
        mRatingText = null;
        mLanguage = null;
        mPages = null;
        mPosted = null;
        mFavoredTimes = null;
        mRead = null;
        mFullDetail = null;
        mTip = null;
        mProgress = null;
        mTags = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        //注册事件
        EventBus.getDefault().register(this);
        // 恢复时按当前数据重新绑定，补上暂停期间错过的打码开关变化
        if (mGalleryDetail != null) {
            bindSecond();
        } else if (mGalleryInfo != null) {
            bindFirst();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //销毁事件
        EventBus.getDefault().unregister(this);
    }

    /**
     * eventBus 通知隐私打码开关变化，按当前已有数据重新绑定上传者与标签
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPrivacyMaskChanged(PrivacyMaskChanged e) {
        if (mGalleryDetail != null) {
            bindSecond();
        } else if (mGalleryInfo != null) {
            bindFirst();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mRead) {
            onReadClick();
        } else if (v == mFullDetail) {
            onFullDetailClick();
        } else if (v == mTip) {
            // 失败提示点击重试
            prepareData();
        }
    }

    private void onReadClick() {
        GalleryInfo info = getCurrentGallery();
        if (info == null || getActivity() == null) {
            return;
        }
        // 与完整详情场景的"阅读"按钮一致：GalleryActivity 承载阅读
        Intent intent = new Intent(getActivity(), GalleryActivity.class);
        intent.setAction(GalleryActivity.ACTION_EH);
        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, info);
        startActivity(intent);
    }

    private void onFullDetailClick() {
        GalleryInfo info = getCurrentGallery();
        // getActivity() 为 null（面板已脱离）或宿主不是场景宿主时不处理
        Activity activity = getActivity();
        if (info == null || !(activity instanceof StageActivity)) {
            return;
        }
        // 与列表场景单屏回退一致：进完整详情场景（无缩略图转场）
        Bundle args = new Bundle();
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, info);
        ((StageActivity) activity).startScene(new Announcer(GalleryDetailScene.class).setArgs(args));
    }

    private static boolean isSameGallery(@Nullable GalleryInfo a, @Nullable GalleryInfo b) {
        if (a == null || b == null) {
            return false;
        }
        return a.gid == b.gid && TextUtils.equals(a.token, b.token);
    }

    /** "阅读/完整详情"按钮整体显隐：无画廊空状态下隐藏，选中画廊后恢复。 */
    private void setActionsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (mRead != null) {
            mRead.setVisibility(visibility);
        }
        if (mFullDetail != null) {
            mFullDetail.setVisibility(visibility);
        }
    }

    private boolean prepareData() {
        Context context = getContext();
        if (context == null || mGalleryInfo == null) {
            return false;
        }

        if (mGalleryDetail != null) {
            bindSecond();
            return true;
        }

        // 命中 GalleryDetail 缓存则直接展示（与完整详情场景共享同一缓存）
        GalleryDetail cached = SiteApplication.getGalleryDetailCache(context).get(mGalleryInfo.gid);
        if (cached != null) {
            mGalleryDetail = cached;
            bindSecond();
            return true;
        }

        // 已有在途请求（旋转恢复）：重新挂接回调即可
        SiteApplication application = (SiteApplication) context.getApplicationContext();
        if (mRequestId != IntIdGenerator.INVALID_ID
                && application.containGlobalStuff(mRequestId)) {
            Object stuff = application.getGlobalStuff(mRequestId);
            if (stuff instanceof PaneDetailCallback) {
                ((PaneDetailCallback) stuff).attach(this);
                showProgress();
                return true;
            }
            mRequestId = IntIdGenerator.INVALID_ID;
        }

        return request();
    }

    private boolean request() {
        Context context = getContext();
        if (context == null || mGalleryInfo == null) {
            return false;
        }
        String url = SiteUrl.getGalleryDetailUrl(mGalleryInfo.gid, mGalleryInfo.token, 0, false);
        if (TextUtils.isEmpty(url)) {
            return false;
        }

        showProgress();
        SiteApplication application = (SiteApplication) context.getApplicationContext();
        PaneDetailCallback callback = new PaneDetailCallback(application, this);
        mRequestId = application.putGlobalStuff(callback);
        SiteRequest request = new SiteRequest()
                .setMethod(SiteClient.METHOD_GET_GALLERY_DETAIL)
                .setArgs(url)
                .setCallback(callback);
        SiteApplication.getSiteClient(context).execute(request);
        return true;
    }

    private void onDetailLoaded(@NonNull GalleryDetail detail) {
        // 拦截过期的迟到回调（切换画廊后旧请求才返回）
        if (mGalleryInfo == null || detail.gid != mGalleryInfo.gid) {
            return;
        }
        mGalleryDetail = detail;
        showContent();
        bindSecond();
    }

    private void onDetailFailed(@NonNull Exception e) {
        mRequestId = IntIdGenerator.INVALID_ID;
        showTip(ExceptionUtils.getReadableString(e));
    }

    /*---------------
     View binding
     ---------------*/

    /** 用 GalleryInfo 即可展示的部分，点击后立即可见。 */
    private void bindFirst() {
        GalleryInfo gi = mGalleryInfo;
        if (gi == null || mThumb == null || mTitle == null || mUploader == null
                || mCategory == null || mPages == null || mPosted == null) {
            return;
        }
        mThumb.load(SiteCacheKeyFactory.getThumbKey(gi.gid), gi.thumb);
        mTitle.setText(SiteUtils.getSuitableTitle(gi));
        bindUploaderText(gi);
        mCategory.setText(SiteUtils.getCategory(gi.category));
        mCategory.setTextColor(SiteUtils.getCategoryColor(gi.category));
        bindPages(gi.pages);
        mPosted.setText(gi.posted);
    }

    /** 需要 GalleryDetail 的部分：语言、收藏数、评分、标签。 */
    private void bindSecond() {
        GalleryDetail gd = mGalleryDetail;
        if (gd == null || mThumb == null || mTitle == null || mUploader == null
                || mCategory == null || mPages == null || mPosted == null) {
            return;
        }
        mThumb.load(SiteCacheKeyFactory.getThumbKey(gd.gid), gd.thumb);
        mTitle.setText(SiteUtils.getSuitableTitle(gd));
        bindUploaderText(gd);
        mCategory.setText(SiteUtils.getCategory(gd.category));
        mCategory.setTextColor(SiteUtils.getCategoryColor(gd.category));
        bindPages(gd.pages);
        mPosted.setText(gd.posted);

        if (mLanguage != null) {
            mLanguage.setText(gd.language);
        }
        if (mFavoredTimes != null) {
            mFavoredTimes.setText(getString(R.string.favored_times, gd.favoriteCount));
        }
        if (mRatingText != null) {
            mRatingText.setText(getString(R.string.rating_text,
                    String.valueOf(gd.rating), gd.rating, gd.ratingCount));
        }
        bindTags(gd.tags);
    }

    private void bindPages(int pages) {
        if (mPages == null || pages <= 0) {
            return;
        }
        mPages.setText(getResources().getQuantityString(R.plurals.page_count, pages, pages));
    }

    /** 绑定上传者：隐私打码开启时隐藏上传者，关闭时恢复。 */
    private void bindUploaderText(@Nullable GalleryInfo info) {
        if (mUploader == null) {
            return;
        }
        if (PrivacyMask.isEnabled()) {
            mUploader.setText(null);
            mUploader.setVisibility(View.GONE);
        } else {
            mUploader.setText(info.uploader);
            mUploader.setVisibility(View.VISIBLE);
        }
    }

    /** 简单标签渲染：每个标签组一行"组名: tag1, tag2, ..."。 */
    private void bindTags(@Nullable GalleryTagGroup[] tagGroups) {
        if (PrivacyMask.isEnabled()) {
            // 隐私打码开启时不显示标签，显示"无标签"空状态
            tagGroups = new GalleryTagGroup[0];
        }
        LinearLayout tags = mTags;
        Context context = getContext();
        if (tags == null || context == null) {
            return;
        }
        tags.removeAllViews();
        if (tagGroups == null || tagGroups.length == 0) {
            tags.addView(newTagRow(context, R.string.no_tags, 0));
            return;
        }
        for (GalleryTagGroup group : tagGroups) {
            int padding = Math.round(getResources().getDisplayMetrics().density * 4);
            TextView row = newTagRow(context, 0, padding);
            row.setText(groupText(group));
            tags.addView(row);
        }
    }

    @NonNull
    private TextView newTagRow(@NonNull Context context, int textRes, int topPadding) {
        TextView row = new TextView(context);
        row.setTextAppearance(context,
                androidx.appcompat.R.style.TextAppearance_AppCompat_Small);
        if (textRes != 0) {
            row.setText(textRes);
        }
        row.setPadding(0, topPadding, 0, 0);
        return row;
    }

    @NonNull
    private static String groupText(@NonNull GalleryTagGroup group) {
        StringBuilder builder = new StringBuilder(group.groupName);
        builder.append(": ");
        for (int i = 0, n = group.size(); i < n; i++) {
            if (i != 0) {
                builder.append(", ");
            }
            builder.append(group.getTagAt(i));
        }
        return builder.toString();
    }

    /*---------------
     Visibility
     ---------------*/

    private void showProgress() {
        if (mProgress != null) {
            mProgress.setVisibility(View.VISIBLE);
        }
        if (mTip != null) {
            mTip.setVisibility(View.GONE);
        }
    }

    private void showContent() {
        if (mProgress != null) {
            mProgress.setVisibility(View.GONE);
        }
        if (mTip != null) {
            mTip.setVisibility(View.GONE);
        }
    }

    private void showTip(@Nullable String text) {
        if (mProgress != null) {
            mProgress.setVisibility(View.GONE);
        }
        if (mTip != null) {
            mTip.setVisibility(View.VISIBLE);
            mTip.setText(text);
        }
    }

    /**
     * 详情请求回调：副作用与 {@code GetGalleryDetailListener} 保持一致
     * （写缓存、记历史、同步标签），但通过弱引用投递给面板，
     * 请求注册在 SiteApplication 的全局请求表里以跨越视图重建。
     */
    private static class PaneDetailCallback implements SiteClient.Callback<GalleryDetail> {

        @NonNull
        private final SiteApplication mApplication;
        @NonNull
        private WeakReference<GalleryDetailPaneFragment> mFragmentRef;

        PaneDetailCallback(@NonNull SiteApplication application,
                           @NonNull GalleryDetailPaneFragment fragment) {
            mApplication = application;
            mFragmentRef = new WeakReference<>(fragment);
        }

        /** 视图重建后重新挂接面板（原引用已随旧面板失效）。 */
        void attach(@NonNull GalleryDetailPaneFragment fragment) {
            mFragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void onSuccess(@Nullable GalleryDetail result) {
            mApplication.removeGlobalStuff(this);
            if (result == null) {
                return;
            }
            // 与 GetGalleryDetailListener 相同的副作用
            SiteApplication.getGalleryDetailCache(mApplication).put(result.gid, result);
            SiteDB.putHistoryInfo(result);
            new GalleryDetailTagsSyncTask(result).start();

            GalleryDetailPaneFragment fragment = mFragmentRef.get();
            if (fragment != null) {
                fragment.onDetailLoaded(result);
            }
        }

        @Override
        public void onFailure(@NonNull Exception e) {
            mApplication.removeGlobalStuff(this);
            GalleryDetailPaneFragment fragment = mFragmentRef.get();
            if (fragment != null) {
                fragment.onDetailFailed(e);
            }
        }

        @Override
        public void onCancel() {
            mApplication.removeGlobalStuff(this);
        }
    }
}

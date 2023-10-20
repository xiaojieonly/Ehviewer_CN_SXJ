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

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.ehviewer.ui.scene.ToolbarScene;
import com.hippo.scene.SceneFragment;
import com.hippo.util.DrawableManager;
import com.hippo.view.ViewTransition;
import com.hippo.widget.ProgressView;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ViewUtils;

import java.util.ArrayList;

public final class SubscriptionsScene extends ToolbarScene {

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private UserTagList userTagList;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private EasyRecyclerView mRecyclerView;
    private ProgressView progressView;
    @Nullable
    private ViewTransition mViewTransition;

    private EhTagDatabase ehTags;

    private Context context;

    private EhClient ehClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = getEHContext();
        if (ehClient == null){
            this.ehClient = EhApplication.getEhClient(context);
        }
        userTagList = EhApplication.getUserTagList(context);
        ehTags = EhTagDatabase.getInstance(context);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        userTagList = null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
                              @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.scene_label_list, container, false);
        progressView = view.findViewById(R.id.scene_label_progress);
        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(view, R.id.recycler_view);
        TextView tip = (TextView) ViewUtils.$$(view, R.id.tip);
        mViewTransition = new ViewTransition(mRecyclerView, tip);

        Context context = getEHContext();
        AssertUtils.assertNotNull(context);

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_search);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);
        tip.setText(R.string.no_quick_search);

        // drag & drop manager
        RecyclerViewDragDropManager dragDropManager = new RecyclerViewDragDropManager();
        dragDropManager.setDraggingItemShadowDrawable(
                (NinePatchDrawable) context.getResources().getDrawable(R.drawable.shadow_8dp));

        RecyclerView.Adapter adapter = new QuickSearchAdapter();
        adapter.setHasStableIds(true);
        adapter = dragDropManager.createWrappedAdapter(adapter); // wrap for dragging

        final GeneralItemAnimator animator = new DraggableItemAnimator();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setItemAnimator(animator);

        dragDropManager.attachRecyclerView(mRecyclerView);

        updateView();

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.tag_title);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }

        mViewTransition = null;
    }

    @Override
    public void onNavigationClick(View view) {
        onBackPressed();
    }

    private void bindSecond() {
        progressView.setVisibility(View.GONE);
        if (mRecyclerView == null){
            Toast.makeText(context,"描述文件未找到？？？重启试试~",Toast.LENGTH_LONG).show();
            return;
        }
        mRecyclerView.setVisibility(View.VISIBLE);
        if (userTagList == null){
            userTagList = new UserTagList();
            userTagList.userTags = new ArrayList<>();
        }
        // drag & drop manager
        RecyclerViewDragDropManager dragDropManager = new RecyclerViewDragDropManager();
        dragDropManager.setDraggingItemShadowDrawable(
                (NinePatchDrawable) context.getResources().getDrawable(R.drawable.shadow_8dp));

        RecyclerView.Adapter adapter = new QuickSearchAdapter();
        adapter.setHasStableIds(true);
        adapter = dragDropManager.createWrappedAdapter(adapter); // wrap for dragging

        final GeneralItemAnimator animator = new DraggableItemAnimator();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setItemAnimator(animator);

        dragDropManager.attachRecyclerView(mRecyclerView);
    }

    private void updateView() {
        if (mViewTransition != null) {
            if (userTagList != null && userTagList.userTags.size() > 0) {
                mViewTransition.showView(0);
            } else {
                mViewTransition.showView(1);
            }
        }
    }

    private class SubscriptionHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener {

        public final TextView label;
        public final View dragHandler;
        public final View delete;
        public ImageView imageView;

        public SubscriptionHolder(View itemView) {
            super(itemView);

            label = (TextView) ViewUtils.$$(itemView, R.id.label);
            dragHandler = ViewUtils.$$(itemView, R.id.drag_handler);
            delete = ViewUtils.$$(itemView, R.id.delete);
            imageView = itemView.findViewById(R.id.drag_handler);

            delete.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            Context context = getEHContext();
            if (position == RecyclerView.NO_POSITION || userTagList == null) {
                return;
            }

            final UserTag userTag = userTagList.userTags.get(position);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.delete_subscription_title)
                    .setMessage(getString(R.string.delete_quick_search_message, userTag.tagName))
                    .setPositiveButton(android.R.string.ok, (dialog,i)->deleteTag(userTag)).show();
        }

        private void deleteTag(UserTag userTag) {
            progressView.setVisibility(View.VISIBLE);
            assert mRecyclerView != null;
            mRecyclerView.setVisibility(View.INVISIBLE);
            deleteRequest(userTag);

        }

        private void deleteRequest(UserTag userTag){
            String url = EhUrl.getMyTag();

            if (null == context) {
                return;
            }
            assert userTagList != null;
            EhClient.Callback callback = new SubscriptionsScene.SubscriptionDetailListener(context, userTagList.stageId, getTag());

            EhRequest mRequest = new EhRequest()
                    .setMethod(EhClient.METHOD_DELETE_WATCHED)
                    .setArgs(url,userTag).setCallback(callback);

            ehClient.execute(mRequest);

        }

    }

    private class QuickSearchAdapter extends RecyclerView.Adapter<SubscriptionHolder> {

        private final LayoutInflater mInflater;

        public QuickSearchAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);
        }

        @Override
        public SubscriptionHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SubscriptionHolder(mInflater.inflate(R.layout.item_subscription, parent, false));
        }

        @Override
        public void onBindViewHolder(SubscriptionHolder holder, int position) {
            if (userTagList != null) {
                if (Settings.getShowTagTranslations()){
                    holder.label.setText(userTagList.userTags.get(position).getName(ehTags));
                }else {
                    holder.label.setText(userTagList.userTags.get(position).tagName);
                }
            }
            if (userTagList.get(position).hidden){
                holder.imageView.setImageResource(R.drawable.ic_baseline_visibility_off_24);
            }
            if (userTagList.get(position).watched){
                holder.imageView.setImageResource(R.drawable.ic_baseline_visibility_24);
            }
        }

        @Override
        public long getItemId(int position) {
            return userTagList != null ? userTagList.userTags.get(position).getId() : 0;
        }

        @Override
        public int getItemCount() {
            return userTagList != null ? userTagList.userTags.size() : 0;
        }

    }

    private class SubscriptionDetailListener extends EhCallback<GalleryListScene, UserTagList> {

        public SubscriptionDetailListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return false;
        }

        @Override
        public void onSuccess(UserTagList result) {
            if (userTagList==null){
                userTagList =  new UserTagList();
            }
            if (result == null || result.userTags == null){
                userTagList.userTags = new ArrayList<>();
            }else {
                userTagList.userTags = result.userTags;
            }
            EhApplication.saveUserTagList(context,result);
            bindSecond();
        }

        @Override
        public void onFailure(Exception e) {

        }

        @Override
        public void onCancel() {

        }
    }
}

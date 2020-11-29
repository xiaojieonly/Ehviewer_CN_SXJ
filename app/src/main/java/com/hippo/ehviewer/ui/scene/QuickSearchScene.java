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

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.util.DrawableManager;
import com.hippo.view.ViewTransition;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ViewUtils;
import java.util.List;

public final class QuickSearchScene extends ToolbarScene {

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private List<QuickSearch> mQuickSearchList;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private RecyclerView.Adapter mAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mQuickSearchList = EhDB.getAllQuickSearch();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mQuickSearchList = null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_label_list, container, false);

        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(view, R.id.recycler_view);
        TextView tip = (TextView) ViewUtils.$$(view, R.id.tip);
        mViewTransition = new ViewTransition(mRecyclerView, tip);

        Context context = getContext2();
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
        mAdapter = adapter;

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
        setTitle(R.string.quick_search);
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
    public void onNavigationClick() {
        onBackPressed();
    }

    private void updateView() {
        if (mViewTransition != null) {
            if (mQuickSearchList != null && mQuickSearchList.size() > 0) {
                mViewTransition.showView(0);
            } else {
                mViewTransition.showView(1);
            }
        }
    }

    private class QuickSearchHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener {

        public final TextView label;
        public final View dragHandler;
        public final View delete;

        public QuickSearchHolder(View itemView) {
            super(itemView);

            label = (TextView) ViewUtils.$$(itemView, R.id.label);
            dragHandler = ViewUtils.$$(itemView, R.id.drag_handler);
            delete = ViewUtils.$$(itemView, R.id.delete);

            delete.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            Context context = getContext2();
            if (position == RecyclerView.NO_POSITION || mQuickSearchList == null) {
                return;
            }

            final QuickSearch quickSearch = mQuickSearchList.get(position);
            new AlertDialog.Builder(context)
                .setTitle(R.string.delete_quick_search_title)
                .setMessage(getString(R.string.delete_quick_search_message, quickSearch.name))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EhDB.deleteQuickSearch(quickSearch);
                        mQuickSearchList.remove(position);
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (null != mAdapter) {
                            mAdapter.notifyDataSetChanged();
                        }
                        updateView();
                    }
                }).show();
        }
    }

    private class QuickSearchAdapter extends RecyclerView.Adapter<QuickSearchHolder>
            implements DraggableItemAdapter<QuickSearchHolder> {

        private final LayoutInflater mInflater;

        public QuickSearchAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);
        }

        @Override
        public QuickSearchHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new QuickSearchHolder(mInflater.inflate(R.layout.item_quick_search, parent, false));
        }

        @Override
        public void onBindViewHolder(QuickSearchHolder holder, int position) {
            if (mQuickSearchList != null) {
                holder.label.setText(mQuickSearchList.get(position).name);
            }
        }

        @Override
        public long getItemId(int position) {
            return mQuickSearchList != null ? mQuickSearchList.get(position).getId() : 0;
        }

        @Override
        public int getItemCount() {
            return mQuickSearchList != null ? mQuickSearchList.size() : 0;
        }

        @Override
        public boolean onCheckCanStartDrag(QuickSearchHolder holder, int position, int x, int y) {
            return ViewUtils.isViewUnder(holder.dragHandler, x, y, 0);
        }

        @Override
        public ItemDraggableRange onGetItemDraggableRange(QuickSearchHolder holder, int position) {
            return null;
        }

        @Override
        public void onMoveItem(int fromPosition, int toPosition) {
            if (fromPosition == toPosition) {
                return;
            }
            if (null == mQuickSearchList) {
                return;
            }

            EhDB.moveQuickSearch(fromPosition, toPosition);
            final QuickSearch item = mQuickSearchList.remove(fromPosition);
            mQuickSearchList.add(toPosition, item);
        }

        @Override
        public boolean onCheckCanDrop(int draggingPosition, int dropPosition) {
            return true;
        }

        @Override
        public void onItemDragStarted(int position) { }

        @Override
        public void onItemDragFinished(int fromPosition, int toPosition, boolean result) { }
    }
}

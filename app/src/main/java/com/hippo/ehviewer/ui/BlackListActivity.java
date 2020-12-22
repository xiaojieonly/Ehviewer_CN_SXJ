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

import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhFilter;
import com.hippo.ehviewer.dao.BlackList;
import com.hippo.ehviewer.dao.Filter;
import com.hippo.util.DrawableManager;
import com.hippo.util.TimeUtils;
import com.hippo.view.ViewTransition;
import com.hippo.yorozuya.ViewUtils;

import java.util.List;

public class BlackListActivity extends ToolbarActivity {

    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private FilterAdapter mAdapter;
    @Nullable
    private FilterList mFilterList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        mFilterList = new FilterList();

        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(this, R.id.recycler_view);
        TextView tip = (TextView) ViewUtils.$$(this, R.id.tip);
        mViewTransition = new ViewTransition(mRecyclerView, tip);

        Drawable drawable = DrawableManager.getVectorDrawable(this, R.drawable.big_filter);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);

        mAdapter = new FilterAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setClipChildren(false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.hasFixedSize();
        mRecyclerView.setItemAnimator(null);

        updateView(false);
    }

    private void updateView(boolean animation) {
        if (null == mViewTransition) {
            return;
        }

        if (null == mFilterList || 0 == mFilterList.size()) {
            mViewTransition.showView(1, animation);
        } else {
            mViewTransition.showView(0, animation);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mRecyclerView = null;
        mViewTransition = null;
        mAdapter = null;
        mFilterList = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_blick_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_add:
                showAddFilterDialog();
                return true;
            case R.id.action_tip:
                showTipDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showTipDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blacklist)
                .setMessage(R.string.blacklist_tip)
                .show();
    }

    private void showAddFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.add_filter)
                .setView(R.layout.dialog_add_filter)
                .setPositiveButton(R.string.add, null)
                .show();
        AddFilterDialogHelper helper = new AddFilterDialogHelper();
        helper.setDialog(dialog);
    }

    private void showDeleteFilterDialog(final BlackList blackList) {
        String message = getString(R.string.delete_blacklist, blackList.badgayname);
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (DialogInterface.BUTTON_POSITIVE != which || null == mFilterList) {
                        return;
                    }
                    mFilterList.delete(blackList);
                    if (null != mAdapter) {
                        mAdapter.notifyDataSetChanged();
                    }
                    updateView(true);
                }).show();
    }

    private class AddFilterDialogHelper implements View.OnClickListener {

        @Nullable
        private AlertDialog mDialog;
        @Nullable
        private Spinner mSpinner;
        @Nullable
        private TextInputLayout mInputLayout;
        @Nullable
        private EditText mEditText;

        public void setDialog(AlertDialog dialog) {
            mDialog = dialog;
            mSpinner = (Spinner) ViewUtils.$$(dialog, R.id.spinner);
            mInputLayout = (TextInputLayout) ViewUtils.$$(dialog, R.id.text_input_layout);
            mEditText = mInputLayout.getEditText();
            View button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (null != button) {
                button.setOnClickListener(this);
            }
        }

        @Override
        public void onClick(View v) {
            if (null == mFilterList || null == mDialog || null == mSpinner ||
                    null == mInputLayout || null == mEditText) {
                return;
            }

            String text = mEditText.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                mInputLayout.setError(getString(R.string.text_is_empty));
                return;
            } else {
                mInputLayout.setError(null);
            }
            int mode = mSpinner.getSelectedItemPosition();

            BlackList blackList = new BlackList();
            blackList.badgayname = text;
            blackList.add_time = TimeUtils.getTimeNow();
            blackList.angrywith = "/手动添加/";

            mFilterList.add(blackList);

            if (null != mAdapter) {
                mAdapter.notifyDataSetChanged();
            }
            updateView(true);

            mDialog.dismiss();
            mDialog = null;
            mSpinner = null;
            mInputLayout = null;
            mEditText = null;
        }
    }

    private class FilterHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView text;
        private final ImageView icon;

        public FilterHolder(View itemView) {
            super(itemView);
            text = (TextView) ViewUtils.$$(itemView, R.id.text);
            icon = itemView.findViewById(R.id.icon);

            if (null != icon) {
                icon.setOnClickListener(this);
            }
            // click on the filter text to enable/disable it
            text.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position < 0 || null == mFilterList) {
                return;
            }

            BlackList blackList = mFilterList.get(position);
            if (v instanceof ImageView) {
                showDeleteFilterDialog(blackList);
            } else if (v instanceof TextView) {
//                    mFilterList.trigger(filter);

                //for updating delete line on filter text
                mAdapter.notifyItemChanged(getAdapterPosition());
            }

//            Filter filter = mFilterList.get(position);
//            if (FilterList.MODE_HEADER != filter.mode) {
//                if (v instanceof ImageView) {
//                    showDeleteFilterDialog(filter);
//                } else if (v instanceof TextView) {
////                    mFilterList.trigger(filter);
//
//                    //for updating delete line on filter text
//                    mAdapter.notifyItemChanged(getAdapterPosition());
//                }
//
//            }
        }
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterHolder> {

        private static final int TYPE_ITEM = 0;
        private static final int TYPE_HEADER = 1;

        @Override
        public int getItemViewType(int position) {
            if (null == mFilterList) {
                return TYPE_ITEM;
            }else {
                return TYPE_HEADER;
            }

//            if (mFilterList.get(position).mode == FilterList.MODE_HEADER) {
//                return TYPE_HEADER;
//            } else {
//                return TYPE_ITEM;
//            }
        }

        @Override
        public FilterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layoutId;
            switch (viewType) {
                default:
                case TYPE_ITEM:
                    layoutId = R.layout.item_blacklist;
                    break;
                case TYPE_HEADER:
                    layoutId = R.layout.item_blacklist_header;
                    break;
            }

            FilterHolder holder = new FilterHolder(getLayoutInflater().inflate(layoutId, parent, false));

            if (R.layout.item_blacklist == layoutId) {
                holder.icon.setImageDrawable(
                        DrawableManager.getVectorDrawable(BlackListActivity.this, R.drawable.v_delete_x24));
            }

            return holder;
        }

        @Override
        public void onBindViewHolder(FilterHolder holder, int position) {
            if (null == mFilterList) {
                return;
            }
            BlackList blackList = mFilterList.get(position);
            holder.text.setText(blackList.badgayname);
            holder.text.setPaintFlags(holder.text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

//            Filter filter = mFilterList.get(position);
//            if (FilterList.MODE_HEADER == filter.mode) {
//                holder.text.setText(filter.text);
//            } else {
//                holder.text.setText(filter.text);
//                // add a delete line if the filter is disabled
//                if (!filter.enable) {
//                    holder.text.setPaintFlags(holder.text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
//                } else {
//                    holder.text.setPaintFlags(holder.text.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
//                }
//            }
        }

        @Override
        public int getItemCount() {
            return null != mFilterList ? mFilterList.size() : 0;
        }
    }

    private class FilterList {

//        public static final int MODE_HEADER = -1;


        private final List<BlackList> mTitleBlackList;

        private BlackList mTitleHeader;

        public FilterList() {
            mTitleBlackList = EhDB.getAllBlackList();
        }

        public int size() {
            int count = 0;
            int size = mTitleBlackList.size();
            count += 0 == size ? 0 : size + 1;

            return count;
        }

        private BlackList getTitleHeader() {
            if (null == mTitleHeader) {
                mTitleHeader = new BlackList();
//                mTitleHeader.mode = MODE_HEADER;
                mTitleHeader.badgayname = getString(R.string.blacklist_id);
            }
            return mTitleHeader;
        }



        public BlackList get(int index) {
            int size = mTitleBlackList.size();
            if (0 != size) {
                if (index == 0) {
                    return getTitleHeader();
                } else if (index <= size) {
                    return mTitleBlackList.get(index - 1);
                } else {
                    index -= size + 1;
                }
            }


            throw new IndexOutOfBoundsException();
        }

        public void add(BlackList blackList) {
            EhDB.insertBlackList(blackList);
        }

        public void delete(BlackList blackList) {
            EhDB.deleteBlackList(blackList);
        }

//        public void trigger(Filter filter) {
//            mEhFilter.triggerFilter(filter);
//        }
    }
}

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
import com.hippo.ehviewer.dao.BlackList;
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
    private BlackListAdapter mAdapter;
    @Nullable
    private BlackListList mblackListList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        mblackListList = new BlackListList();

        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(this, R.id.recycler_view1);
        TextView tip = (TextView) ViewUtils.$$(this, R.id.tip);
        mViewTransition = new ViewTransition(mRecyclerView, tip);

        Drawable drawable = DrawableManager.getVectorDrawable(this, R.drawable.big_filter);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        tip.setCompoundDrawables(null, drawable, null, null);

        mAdapter = new BlackListAdapter();
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

        if (null == mblackListList || 0 == mblackListList.size()) {
            mViewTransition.showView(1, animation);
        }else {
            mViewTransition.showView(0, animation);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mRecyclerView = null;
        mViewTransition = null;
        mAdapter = null;
        mblackListList = null;
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
                showAddBlackListDialog();
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

    private void showAddBlackListDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.add_blacklist)
                .setView(R.layout.dialog_add_blacklist)
                .setPositiveButton(R.string.add, null)
                .show();
        AddBlackListDialogHelper helper = new AddBlackListDialogHelper();
        helper.setDialog(dialog);
    }

    private void showDeleteBlackListDialog(final BlackList blackList) {
        String message = getString(R.string.delete_blacklist, blackList.badgayname);
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (DialogInterface.BUTTON_POSITIVE != which || null == mblackListList) {
                        return;
                    }
                    mblackListList.delete(blackList);
                    if (null != mAdapter) {
                        mAdapter.notifyDataSetChanged();
                    }
                    updateView(true);
                }).show();
    }

    private class AddBlackListDialogHelper implements View.OnClickListener {

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
            mInputLayout = (TextInputLayout) ViewUtils.$$(dialog, R.id.text_inputreason_layout);
            mEditText = mInputLayout.getEditText();
            View button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (null != button) {
                button.setOnClickListener(this);
            }
        }

        @Override
        public void onClick(View v) {
            if (null == mblackListList || null == mDialog || null == mSpinner ||
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
            blackList.mode=1;

            mblackListList.add(blackList);

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

    private class BlackListHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView text;
        private final ImageView icon;

        public BlackListHolder(View itemView) {
            super(itemView);
            text = (TextView) ViewUtils.$$(itemView, R.id.text);
            icon = itemView.findViewById(R.id.icon);

            if (null != icon) {
                icon.setOnClickListener(this);
            }
            // click on the blacklist text to enable/disable it
            text.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position < 0 || null == mblackListList) {
                return;
            }

            BlackList blackList = mblackListList.get(position);

            if (v instanceof ImageView) {
                showDeleteBlackListDialog(blackList);
            } else if (v instanceof TextView) {
                mAdapter.notifyItemChanged(getAdapterPosition());
            }

        }
    }

    private class BlackListAdapter extends RecyclerView.Adapter<BlackListHolder> {

        private static final int TYPE_ITEM = 0;
        private static final int TYPE_HEADER = 1;

        @Override
        public int getItemViewType(int position) {
            if (null == mblackListList) {
                return TYPE_ITEM;
            }

            if (mblackListList.get(position).mode == BlackListList.MODE_HEADER) {
                return TYPE_HEADER;
            } else {
                return TYPE_ITEM;
            }
        }

        @Override
        public BlackListHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layoutId ;
            switch (viewType) {
                default:
                case TYPE_ITEM:
                    layoutId = R.layout.item_blacklist;
                    break;
                case TYPE_HEADER:
                    layoutId = R.layout.item_blacklist_header;
                    break;
            }

            BlackListHolder holder = new BlackListHolder(getLayoutInflater().inflate(layoutId, parent, false));

            if (R.layout.item_blacklist == layoutId) {
                holder.icon.setImageDrawable(
                        DrawableManager.getVectorDrawable(BlackListActivity.this, R.drawable.v_delete_x24));
            }

            return holder;
        }

        @Override
        public void onBindViewHolder(BlackListHolder holder, int position) {
            if (null == mblackListList) {
                return;
            }
            BlackList blackList = mblackListList.get(position);

            if (BlackListList.MODE_HEADER == blackList.mode) {
                holder.text.setText(blackList.badgayname);
            } else {
                holder.text.setText(blackList.badgayname);

                holder.text.setPaintFlags(holder.text.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

            }

        }

        @Override
        public int getItemCount() {
            return null != mblackListList ? mblackListList.size() : 0;
        }
    }

    private class BlackListList {

        public static final int MODE_HEADER = -1;


        private List<BlackList> mTitleBlackList;

        private BlackList mTitleHeader;

        public BlackListList() {
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
                mTitleHeader.mode = MODE_HEADER;
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
            mTitleBlackList.add(blackList);
        }

        public void delete(BlackList blackList) {
            EhDB.deleteBlackList(blackList);
            mTitleBlackList.remove(blackList);
        }

    }
}

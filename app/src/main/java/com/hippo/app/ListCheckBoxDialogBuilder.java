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

package com.hippo.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.ViewUtils;

public class ListCheckBoxDialogBuilder extends AlertDialog.Builder {

    private final CheckBox mCheckBox;

    private AlertDialog mDialog;

    @SuppressLint("InflateParams")
    public ListCheckBoxDialogBuilder(Context context, CharSequence[] items,
            final OnItemClickListener listener, String checkText, boolean checked) {
        super(context);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_list_checkbox_builder, null);
        setView(view);
        ListView listView = (ListView) ViewUtils.$$(view, R.id.list_view);
        mCheckBox = (CheckBox) ViewUtils.$$(view, R.id.checkbox);
        listView.setAdapter(new ArrayAdapter<>(getContext(), R.layout.item_select_dialog, items));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (listener != null) {
                    listener.onItemClick(ListCheckBoxDialogBuilder.this, mDialog, position);
                }
                mDialog.dismiss();
            }
        });
        mCheckBox.setText(checkText);
        mCheckBox.setChecked(checked);
    }

    public boolean isChecked() {
        return mCheckBox.isChecked();
    }

    @Override
    public AlertDialog create() {
        mDialog = super.create();
        return mDialog;
    }

    public interface OnItemClickListener {
        void onItemClick(ListCheckBoxDialogBuilder builder, AlertDialog dialog, int position);
    }
}

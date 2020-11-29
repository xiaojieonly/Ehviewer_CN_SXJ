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
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.hippo.ehviewer.R;

public class EditTextDialogBuilder extends AlertDialog.Builder implements EditText.OnEditorActionListener {

    private final TextInputLayout mTextInputLayout;
    private final EditText mEditText;
    private AlertDialog mDialog;

    @SuppressLint("InflateParams")
    public EditTextDialogBuilder(Context context, String text, String hint) {
        super(context);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edittext_builder, null);
        setView(view);
        mTextInputLayout = (TextInputLayout) view;
        mEditText = (EditText) view.findViewById(R.id.edit_text);
        mEditText.setText(text);
        mEditText.setSelection(mEditText.getText().length());
        mEditText.setOnEditorActionListener(this);
        mTextInputLayout.setHint(hint);
    }

    public EditText getEditText() {
        return mEditText;
    }

    public String getText() {
        return mEditText.getText().toString();
    }

    public void setError(CharSequence error) {
        mTextInputLayout.setError(error);
    }

    @Override
    public AlertDialog create() {
        mDialog = super.create();
        return mDialog;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (mDialog != null) {
            Button button = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.performClick();
            }
            return true;
        } else {
            return false;
        }
    }
}

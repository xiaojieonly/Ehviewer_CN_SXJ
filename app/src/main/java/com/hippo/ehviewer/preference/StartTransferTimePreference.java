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

package com.hippo.ehviewer.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputLayout;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.lib.yorozuya.ViewUtils;
import com.hippo.preference.DialogPreference;

public class StartTransferTimePreference extends DialogPreference implements View.OnClickListener {

    private TextInputLayout mInputLayout;
    private EditText mInput;

    public StartTransferTimePreference(Context context) {
        super(context);
        init();
    }

    public StartTransferTimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StartTransferTimePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setPersistent(false);
        setDialogLayoutResource(R.layout.dialog_edittext_builder);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        updateSummary();
    }

    private void updateSummary() {
        setSummary(getContext().getString(
                R.string.settings_read_auto_transfer_time_value,
                Settings.getStartTransferTime()));
    }

    @Override
    protected boolean needInputMethod() {
        return true;
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        mInputLayout = (TextInputLayout) view;
        mInput = (EditText) ViewUtils.$$(view, R.id.edit_text);
        mInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        mInput.setGravity(Gravity.CENTER);
        mInput.setText(Integer.toString(Settings.getStartTransferTime()));
        mInput.selectAll();
        mInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onClick(textView);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDialogCreated(AlertDialog dialog) {
        super.onDialogCreated(dialog);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        Dialog dialog = getDialog();
        if (dialog == null || mInputLayout == null || mInput == null) {
            return;
        }

        int value;
        try {
            value = Integer.parseInt(mInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            value = -1;
        }

        if (value < Settings.MIN_START_TRANSFER_TIME_MS ||
                value > Settings.MAX_START_TRANSFER_TIME_MS) {
            mInputLayout.setError(getContext().getString(
                    R.string.settings_read_auto_transfer_time_range,
                    Settings.MIN_START_TRANSFER_TIME_MS,
                    Settings.MAX_START_TRANSFER_TIME_MS));
            return;
        }

        mInputLayout.setError(null);
        if (!callChangeListener(value)) {
            return;
        }

        Settings.putStartTransferTime(value);
        updateSummary();
        dialog.dismiss();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        mInputLayout = null;
        mInput = null;
    }
}

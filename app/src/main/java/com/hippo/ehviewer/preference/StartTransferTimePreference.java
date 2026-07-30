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

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.lib.yorozuya.MathUtils;

public class StartTransferTimePreference extends Preference {

    @Nullable
    private EditText mInput;
    @Nullable
    private SeekBar mSeekBar;

    private boolean mBinding;

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable text) {
            if (mBinding) {
                return;
            }

            Integer transferTime = parseTransferTime(text.toString());
            if (transferTime == null ||
                    transferTime < Settings.MIN_START_TRANSFER_TIME_MS ||
                    transferTime > Settings.MAX_START_TRANSFER_TIME_MS) {
                return;
            }

            Settings.putStartTransferTime(transferTime);
            if (mSeekBar != null) {
                mSeekBar.setProgress(Settings.startTransferTimeToProgress(transferTime));
            }
        }
    };

    private final SeekBar.OnSeekBarChangeListener mSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        setTransferTime(Settings.startTransferProgressToTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            };

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
        setSelectable(false);
        setLayoutResource(R.layout.preference_start_transfer_time);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mInput != null) {
            mInput.removeTextChangedListener(mTextWatcher);
        }
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(null);
        }

        mInput = (EditText) holder.findViewById(R.id.start_transfer_time_input);
        mSeekBar = (SeekBar) holder.findViewById(R.id.start_transfer_time);
        if (mInput == null || mSeekBar == null) {
            return;
        }

        int transferTime = Settings.getStartTransferTime();
        mSeekBar.setMax(Settings.startTransferTimeToProgress(
                Settings.MAX_START_TRANSFER_SLIDER_TIME_MS));

        mBinding = true;
        mInput.setText(Integer.toString(transferTime));
        mInput.setSelection(mInput.length());
        mSeekBar.setProgress(Settings.startTransferTimeToProgress(transferTime));
        mBinding = false;

        mInput.addTextChangedListener(mTextWatcher);
        mInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                commitInput();
            }
        });
        mInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitInput();
                return true;
            }
            return false;
        });
        mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
    }

    @Nullable
    private Integer parseTransferTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void commitInput() {
        if (mInput == null) {
            return;
        }

        Integer transferTime = parseTransferTime(mInput.getText().toString());
        if (transferTime == null) {
            transferTime = Settings.getStartTransferTime();
        } else {
            transferTime = MathUtils.clamp(transferTime,
                    Settings.MIN_START_TRANSFER_TIME_MS,
                    Settings.MAX_START_TRANSFER_TIME_MS);
        }
        setTransferTime(transferTime);
    }

    private void setTransferTime(int transferTime) {
        Settings.putStartTransferTime(transferTime);

        mBinding = true;
        if (mInput != null) {
            String value = Integer.toString(transferTime);
            if (!value.contentEquals(mInput.getText())) {
                mInput.setText(value);
                mInput.setSelection(value.length());
            }
        }
        if (mSeekBar != null) {
            mSeekBar.setProgress(Settings.startTransferTimeToProgress(transferTime));
        }
        mBinding = false;
    }
}

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
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.hippo.ehviewer.R;

public class SliderPreference extends androidx.preference.Preference {

    private int mMinValue = 0;
    private int mMaxValue = 100;
    private int mStepSize = 1;
    private int mCurrentValue = 0;
    private String mUnit = "";
    private boolean mShowValue = true;
    private float mScale = 1.0f;
    private int mPrecision = 0;
    private CharSequence[] mEntries;
    private int[] mEntryValues;

    public SliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SliderPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_slider);
        
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference);
        mMinValue = a.getInt(R.styleable.SliderPreference_minValue, 0);
        mMaxValue = a.getInt(R.styleable.SliderPreference_maxValue, 100);
        mStepSize = a.getInt(R.styleable.SliderPreference_stepSize, 1);
        mUnit = a.getString(R.styleable.SliderPreference_unit);
        mShowValue = a.getBoolean(R.styleable.SliderPreference_showValue, true);
        mScale = a.getFloat(R.styleable.SliderPreference_scale, 1.0f);
        mPrecision = a.getInt(R.styleable.SliderPreference_precision, 0);
        mEntries = a.getTextArray(R.styleable.SliderPreference_sliderEntries);
        int entryValuesResId = a.getResourceId(R.styleable.SliderPreference_sliderEntryValues, 0);
        if (entryValuesResId != 0) {
            TypedArray entryValueArray = context.getResources().obtainTypedArray(entryValuesResId);
            mEntryValues = new int[entryValueArray.length()];
            for (int i = 0; i < entryValueArray.length(); i++) {
                try {
                    mEntryValues[i] = Integer.parseInt(entryValueArray.getString(i));
                } catch (NumberFormatException e) {
                    mEntryValues[i] = mMinValue;
                }
            }
            entryValueArray.recycle();
        }
        a.recycle();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        TextView titleView = (TextView) holder.findViewById(R.id.title);
        TextView summaryView = (TextView) holder.findViewById(R.id.summary);
        TextView valueView = (TextView) holder.findViewById(R.id.value);
        SeekBar seekBar = (SeekBar) holder.findViewById(R.id.seek_bar);
        
        titleView.setText(getTitle());
        
        if (getSummary() != null) {
            summaryView.setText(getSummary());
            summaryView.setVisibility(View.VISIBLE);
        } else {
            summaryView.setVisibility(View.GONE);
        }
        
        if (mShowValue) {
            valueView.setVisibility(View.VISIBLE);
            updateValueText(valueView);
        } else {
            valueView.setVisibility(View.GONE);
        }
        
        if (mEntryValues != null && mEntryValues.length > 0) {
            int index = findIndexOfValue(mCurrentValue);
            if (index < 0) {
                index = 0;
                mCurrentValue = mEntryValues[0];
                persistInt(mCurrentValue);
            }
            seekBar.setMax(mEntryValues.length - 1);
            seekBar.setProgress(index);
        } else {
            seekBar.setMax((mMaxValue - mMinValue) / mStepSize);
            seekBar.setProgress((mCurrentValue - mMinValue) / mStepSize);
        }
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int newValue;
                    if (mEntryValues != null && mEntryValues.length > 0) {
                        newValue = mEntryValues[progress];
                    } else {
                        newValue = mMinValue + progress * mStepSize;
                    }
                    if (newValue != mCurrentValue) {
                        mCurrentValue = newValue;
                        if (mShowValue) {
                            updateValueText(valueView);
                        }
                        persistInt(mCurrentValue);
                        if (getOnPreferenceChangeListener() != null) {
                            getOnPreferenceChangeListener().onPreferenceChange(SliderPreference.this, mCurrentValue);
                        }
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateValueText(TextView valueView) {
        String valueText;
        if (mEntryValues != null && mEntries != null) {
            int index = findIndexOfValue(mCurrentValue);
            if (index >= 0 && index < mEntries.length) {
                valueText = mEntries[index].toString();
            } else {
                valueText = String.valueOf(mCurrentValue);
            }
        } else if (mScale == 1.0f) {
            valueText = String.valueOf(mCurrentValue);
        } else {
            String format = "%." + mPrecision + "f";
            valueText = String.format(format, mCurrentValue * mScale);
        }
        if (mUnit != null && !mUnit.isEmpty() && (mEntryValues == null || mEntries == null)) {
            valueText += " " + mUnit;
        }
        valueView.setText(valueText);
    }

    private int findIndexOfValue(int value) {
        if (mEntryValues == null) {
            return -1;
        }
        for (int i = 0; i < mEntryValues.length; i++) {
            if (mEntryValues[i] == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean persistInt(int value) {
        if (!shouldPersist()) {
            return false;
        }

        try {
            if (value == getPersistedInt(mMinValue)) {
                return true;
            }
        } catch (ClassCastException e) {
            SharedPreferences prefs = getSharedPreferences();
            if (prefs != null) {
                prefs.edit().remove(getKey()).apply();
            }
        }

        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) {
            return false;
        }
        prefs.edit().putInt(getKey(), value).apply();
        return true;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, mMinValue);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // 尝试获取 int 值，如果失败则强制清空存储值并重新保存
            try {
                mCurrentValue = getPersistedInt(mMinValue);
            } catch (ClassCastException e) {
                // 强制清空存储值
                try {
                    // 清除存储的值
                    getSharedPreferences().edit().remove(getKey()).apply();
                } catch (Exception ex) {
                    // 忽略清除失败
                }
                
                // 尝试从 String 转换
                try {
                    String stringValue = getPersistedString(String.valueOf(mMinValue));
                    mCurrentValue = Integer.parseInt(stringValue);
                } catch (NumberFormatException | ClassCastException nfe) {
                    // 如果还是失败，再次清空存储值并使用默认值
                    try {
                        getSharedPreferences().edit().remove(getKey()).apply();
                    } catch (Exception ex) {
                        // 忽略清除失败
                    }
                    mCurrentValue = mMinValue;
                }
                // 将值以 int 格式重新存储
                persistInt(mCurrentValue);
            }
        } else {
            if (defaultValue != null) {
                if (defaultValue instanceof Integer) {
                    mCurrentValue = (Integer) defaultValue;
                } else if (defaultValue instanceof String) {
                    try {
                        mCurrentValue = Integer.parseInt((String) defaultValue);
                    } catch (NumberFormatException e) {
                        mCurrentValue = mMinValue;
                    }
                } else {
                    mCurrentValue = mMinValue;
                }
            } else {
                mCurrentValue = mMinValue;
            }
            persistInt(mCurrentValue);
        }
    }

    public void setValue(int value) {
        if (value < mMinValue) {
            value = mMinValue;
        } else if (value > mMaxValue) {
            value = mMaxValue;
        }
        
        // 调整到最近的步长值
        int remainder = (value - mMinValue) % mStepSize;
        if (remainder != 0) {
            if (remainder >= mStepSize / 2) {
                value += mStepSize - remainder;
            } else {
                value -= remainder;
            }
        }
        
        if (value != mCurrentValue) {
            mCurrentValue = value;
            persistInt(mCurrentValue);
            notifyChanged();
        }
    }

    public int getValue() {
        return mCurrentValue;
    }

    public void setMinValue(int minValue) {
        mMinValue = minValue;
        notifyChanged();
    }

    public void setMaxValue(int maxValue) {
        mMaxValue = maxValue;
        notifyChanged();
    }

    public void setStepSize(int stepSize) {
        mStepSize = stepSize;
        notifyChanged();
    }

    public void setUnit(String unit) {
        mUnit = unit;
        notifyChanged();
    }

    public void setShowValue(boolean showValue) {
        mShowValue = showValue;
        notifyChanged();
    }
}
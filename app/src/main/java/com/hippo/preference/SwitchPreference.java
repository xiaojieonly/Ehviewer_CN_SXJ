/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.TwoStatePreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import com.hippo.ehviewer.R;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SwitchPreference extends TwoStatePreference {

    private static Method sSyncSummaryViewMethod;

    static {
        try {
            sSyncSummaryViewMethod = TwoStatePreference.class.getDeclaredMethod("syncSummaryView", View.class);
            sSyncSummaryViewMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            sSyncSummaryViewMethod = null;
        }
    }

    // Switch text for on and off states
    private CharSequence mSwitchOn;
    private CharSequence mSwitchOff;

    private final Listener mListener = new Listener();

    private class Listener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!callChangeListener(isChecked)) {
                // Listener didn't like it, change it back.
                // CompoundButton will make sure we don't recurse.
                buttonView.setChecked(!isChecked);
                return;
            }

            SwitchPreference.this.setChecked(isChecked);
        }
    }

    public SwitchPreference(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.preferenceStyle);
        init(context, attrs, 0, 0);
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    public void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setWidgetLayoutResource(R.layout.preference_widget_fixed_switch);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwitchPreference, defStyleAttr, defStyleRes);
        setSummaryOn(a.getString(R.styleable.SwitchPreference_summaryOn));
        setSummaryOff(a.getString(R.styleable.SwitchPreference_summaryOff));
        setSwitchTextOn(a.getString(R.styleable.SwitchPreference_switchTextOn));
        setSwitchTextOff(a.getString(R.styleable.SwitchPreference_switchTextOff));
        setDisableDependentsState(a.getBoolean(R.styleable.SwitchPreference_disableDependentsState, false));
        a.recycle();
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        View checkableView = view.findViewById(R.id.switchWidget);
        if (checkableView != null && checkableView instanceof Checkable) {
            if (checkableView instanceof SwitchCompat) {
                final SwitchCompat switchView = (SwitchCompat) checkableView;
                switchView.setOnCheckedChangeListener(null);
            }

            ((Checkable) checkableView).setChecked(isChecked());

            if (checkableView instanceof SwitchCompat) {
                final SwitchCompat switchView = (SwitchCompat) checkableView;
                switchView.setTextOn(mSwitchOn);
                switchView.setTextOff(mSwitchOff);
                switchView.setOnCheckedChangeListener(mListener);
            }
        }

        if (sSyncSummaryViewMethod != null) {
            try {
                sSyncSummaryViewMethod.invoke(this, view);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string; one word if possible.
     *
     * @param onText Text to display in the on state
     */
    public void setSwitchTextOn(CharSequence onText) {
        mSwitchOn = onText;
        notifyChanged();
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string; one word if possible.
     *
     * @param offText Text to display in the off state
     */
    public void setSwitchTextOff(CharSequence offText) {
        mSwitchOff = offText;
        notifyChanged();
    }

    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string; one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOn(@StringRes int resId) {
        setSwitchTextOn(getContext().getString(resId));
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string; one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOff(@StringRes int resId) {
        setSwitchTextOff(getContext().getString(resId));
    }

    /**
     * @return The text that will be displayed on the switch widget in the on state
     */
    public CharSequence getSwitchTextOn() {
        return mSwitchOn;
    }

    /**
     * @return The text that will be displayed on the switch widget in the off state
     */
    public CharSequence getSwitchTextOff() {
        return mSwitchOff;
    }
}

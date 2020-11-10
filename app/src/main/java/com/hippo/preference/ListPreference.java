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

package com.hippo.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.annotation.ArrayRes;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;

public class ListPreference extends DialogPreference {

    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private String mValue;
    private String mSummary;
    private int mClickedDialogEntryIndex;
    private boolean mValueSet;

    public ListPreference(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public ListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setNegativeButtonText(android.R.string.cancel);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ListPreference, defStyleAttr, defStyleRes);
        mEntries = a.getTextArray(R.styleable.ListPreference_entries);
        mEntryValues = a.getTextArray(R.styleable.ListPreference_entryValues);
        a.recycle();

        /* Retrieve the Preference summary attribute since it's private
         * in the Preference class.
         */
        a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.summary}, defStyleAttr, defStyleRes);
        mSummary = a.getString(0);
        a.recycle();
    }

    /**
     * Sets the human-readable entries to be shown in the list. This will be
     * shown in subsequent dialogs.
     * <p>
     * Each entry must have a corresponding index in
     * {@link #setEntryValues(CharSequence[])}.
     *
     * @param entries The entries.
     * @see #setEntryValues(CharSequence[])
     */
    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }

    /**
     * @see #setEntries(CharSequence[])
     * @param entriesResId The entries array as a resource.
     */
    public void setEntries(@ArrayRes int entriesResId) {
        setEntries(getContext().getResources().getTextArray(entriesResId));
    }

    /**
     * The list of entries to be shown in the list in subsequent dialogs.
     *
     * @return The list as an array.
     */
    public CharSequence[] getEntries() {
        return mEntries;
    }

    /**
     * The array to find the value to save for a preference when an entry from
     * entries is selected. If a user clicks on the second item in entries, the
     * second item in this array will be saved to the preference.
     *
     * @param entryValues The array to be used as values to save for the preference.
     */
    public void setEntryValues(CharSequence[] entryValues) {
        mEntryValues = entryValues;
    }

    /**
     * @see #setEntryValues(CharSequence[])
     * @param entryValuesResId The entry values array as a resource.
     */
    public void setEntryValues(@ArrayRes int entryValuesResId) {
        setEntryValues(getContext().getResources().getTextArray(entryValuesResId));
    }

    /**
     * Returns the array of values to be saved for the preference.
     *
     * @return The array of values.
     */
    public CharSequence[] getEntryValues() {
        return mEntryValues;
    }

    /**
     * Sets the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        // Always persist/notify the first time.
        final boolean changed = !TextUtils.equals(mValue, value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistString(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (mSummary == null) {
            return super.getSummary();
        } else {
            return String.format(mSummary, entry == null ? "" : entry);
        }
    }

    /**
     * Sets the summary for this Preference with a CharSequence.
     * If the summary has a
     * {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place when it's retrieved.
     *
     * @param summary The summary for the preference.
     */
    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        if (summary == null && mSummary != null) {
            mSummary = null;
        } else if (summary != null && !summary.equals(mSummary)) {
            mSummary = summary.toString();
        }
    }

    /**
     * Sets the value to the given index from the entry values.
     *
     * @param index The index of the value to set.
     */
    public void setValueIndex(int index) {
        if (mEntryValues != null) {
            setValue(mEntryValues[index].toString());
        }
    }

    /**
     * Returns the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return mValue;
    }

    /**
     * Returns the entry corresponding to the current value.
     *
     * @return The entry corresponding to the current value, or null.
     */
    public CharSequence getEntry() {
        int index = getValueIndex();
        return index >= 0 && mEntries != null ? mEntries[index] : null;
    }

    /**
     * Returns the index of the given value (in the entry values array).
     *
     * @param value The value whose index should be returned.
     * @return The index of the value, or -1 if not found.
     */
    public int findIndexOfValue(String value) {
        if (value != null && mEntryValues != null) {
            for (int i = mEntryValues.length - 1; i >= 0; i--) {
                if (mEntryValues[i].equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int getValueIndex() {
        return findIndexOfValue(mValue);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

        mClickedDialogEntryIndex = getValueIndex();
        builder.setSingleChoiceItems(mEntries, mClickedDialogEntryIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mClickedDialogEntryIndex = which;

                        /*
                         * Clicking on an item simulates the positive button
                         * click, and dismisses the dialog.
                         */
                        ListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && mClickedDialogEntryIndex >= 0 && mEntryValues != null) {
            String value = mEntryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(mValue) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        String value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

}

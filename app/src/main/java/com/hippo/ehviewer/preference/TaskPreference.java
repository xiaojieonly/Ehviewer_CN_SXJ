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
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.customview.view.AbsSavedState;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.preference.DialogPreference;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.yorozuya.IntIdGenerator;

public abstract class TaskPreference extends DialogPreference {

    private Task mTask;
    private int mTaskId = IntIdGenerator.INVALID_ID;

    public TaskPreference(Context context) {
        super(context);
    }

    public TaskPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TaskPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setTitle(null);
        builder.setView(R.layout.preference_dialog_task);
        builder.setCancelable(false);
    }

    @Override
    protected void onDialogCreated(AlertDialog dialog) {
        if (null == mTask) {
            mTask = onCreateTask();
            mTask.setPreference(this);
            mTaskId = ((EhApplication) getContext().getApplicationContext()).putGlobalStuff(mTask);
            mTask.executeOnExecutor(IoThreadPoolExecutor.getInstance());
        }
    }

    protected void onTaskEnd() {
        // Dismiss dialog
        Dialog dialog = getDialog();
        if (null != dialog) {
            dialog.dismiss();
        }
        // Clear async
        mTask = null;
        mTaskId = IntIdGenerator.INVALID_ID;
    }

    @NonNull
    protected abstract Task onCreateTask();

    @Override
    public void onActivityDestroy() {
        super.onActivityDestroy();

        if (null != mTask) {
            mTask.setPreference(null);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState myState = new SavedState(superState);
        myState.asyncTaskId = mTaskId;
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
        mTaskId = myState.asyncTaskId;
        if (IntIdGenerator.INVALID_ID != mTaskId) {
            Object o = ((EhApplication) getContext().getApplicationContext()).getGlobalStuff(mTaskId);
            if (o instanceof Task) {
                mTask = (Task) o;
                mTask.setPreference(this);
            }
        }
        if (null == mTask) {
            mTaskId = IntIdGenerator.INVALID_ID;
        }

        // TODO if not task, show not reopen dialog

        super.onRestoreInstanceState(myState.getSuperState());
    }

    private static class SavedState extends AbsSavedState {
        int asyncTaskId;

        public SavedState(Parcel source) {
            super(source, SavedState.class.getClassLoader());
            asyncTaskId = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(asyncTaskId);
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

    public abstract static class Task extends AsyncTask<Void, Void, Object> {

        private final EhApplication mApplication;
        @Nullable
        private TaskPreference mPreference;

        public Task(@NonNull Context context) {
            mApplication = (EhApplication) context.getApplicationContext();
        }

        public EhApplication getApplication() {
            return mApplication;
        }

        @Nullable
        public TaskPreference getPreference() {
            return mPreference;
        }

        public void setPreference(@Nullable TaskPreference preference) {
            mPreference = preference;
        }

        @CallSuper
        @Override
        protected void onPostExecute(Object o) {
            mApplication.removeGlobalStuff(this);
            if (null != mPreference) {
                mPreference.onTaskEnd();
            }
        }
    }
}

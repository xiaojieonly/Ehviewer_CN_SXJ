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
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.widget.CategoryTable;
import com.hippo.preference.DialogPreference;
import com.hippo.yorozuya.ViewUtils;

public class DefaultCategoryPreference extends DialogPreference {

    @Nullable
    private CategoryTable mCategoryTable;

    public DefaultCategoryPreference(Context context) {
        super(context);
        init();
    }

    public DefaultCategoryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DefaultCategoryPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.preference_dialog_default_categories);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setPositiveButton(android.R.string.ok, this);
    }

    @Override
    protected void onDialogCreated(AlertDialog dialog) {
        super.onDialogCreated(dialog);
        mCategoryTable = (CategoryTable) ViewUtils.$$(dialog, R.id.category_table);
        mCategoryTable.setCategory(Settings.getDefaultCategories());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (null != mCategoryTable && positiveResult) {
            Settings.putDefaultCategories(mCategoryTable.getCategory());
        }
        mCategoryTable = null;
    }
}

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
import android.view.View;
import android.widget.CheckBox;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.preference.DialogPreference;
import com.hippo.yorozuya.NumberUtils;
import com.hippo.yorozuya.ViewUtils;

public class ExcludedTagNamespacesPreference extends DialogPreference {

    private View mTableLayout;

    private static final int[] EXCLUDED_TAG_GROUP_RES_ID = {
            R.id.tag_group_reclass,
            R.id.tag_group_language,
            R.id.tag_group_parody,
            R.id.tag_group_character,
            R.id.tag_group_group,
            R.id.tag_group_artist,
            R.id.tag_group_male,
            R.id.tag_group_female,
    };

    private static final int[] EXCLUDED_TAG_GROUP_ID = {
            0x1,
            0x2,
            0x4,
            0x8,
            0x10,
            0x20,
            0x40,
            0x80
    };

    public ExcludedTagNamespacesPreference(Context context) {
        super(context);
        init();
    }

    public ExcludedTagNamespacesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExcludedTagNamespacesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.preference_dialog_excluded_tag_namespaces);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setPositiveButton(android.R.string.ok, this);
    }

    @Override
    protected void onDialogCreated(AlertDialog dialog) {
        super.onDialogCreated(dialog);
        mTableLayout = ViewUtils.$$(dialog, R.id.table_layout);
        setExcludedTagNamespaces(mTableLayout, Settings.getExcludedTagNamespaces());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (null != mTableLayout && positiveResult) {
            Settings.putExcludedTagNamespaces(getExcludedTagNamespaces(mTableLayout));
        }
        mTableLayout = null;
    }

    private void setExcludedTagNamespaces(View view, int value) {
        for (int i = 0; i < EXCLUDED_TAG_GROUP_RES_ID.length; i++) {
            CheckBox cb = (CheckBox) view.findViewById(EXCLUDED_TAG_GROUP_RES_ID[i]);
            cb.setChecked(NumberUtils.int2boolean(value & EXCLUDED_TAG_GROUP_ID[i]));
        }
    }

    private int getExcludedTagNamespaces(View view) {
        int newValue = 0;
        for (int i = 0; i < EXCLUDED_TAG_GROUP_RES_ID.length; i++) {
            CheckBox cb = (CheckBox) view.findViewById(EXCLUDED_TAG_GROUP_RES_ID[i]);
            if (cb.isChecked()) {
                newValue |= EXCLUDED_TAG_GROUP_ID[i];
            }
        }
        return newValue;
    }
}

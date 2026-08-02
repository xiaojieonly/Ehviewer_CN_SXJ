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

package com.hippo.anotherviewer.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.client.SiteConfig;
import com.hippo.ripple.Ripple;
import com.hippo.widget.SensitiveCheckBox;
import com.hippo.lib.yorozuya.ViewUtils;

public class ExcludedLanguagesActivity extends ToolbarActivity
        implements View.OnClickListener {

    private static final String KEY_SELECTIONS = "selections";

    private static final int ROW_COUNT = 17;
    private static final int[] LANGUAGE_STR_IDS = {
            R.string.language_japanese,
            R.string.language_english,
            R.string.language_chinese,
            R.string.language_dutch,
            R.string.language_french,
            R.string.language_german,
            R.string.language_hungarian,
            R.string.language_italian,
            R.string.language_korean,
            R.string.language_polish,
            R.string.language_portuguese,
            R.string.language_russian,
            R.string.language_spanish,
            R.string.language_thai,
            R.string.language_vietnamese,
            R.string.language_na,
            R.string.language_other
    };

    private static final String[] LANGUAGES = {
            SiteConfig.JAPANESE_ORIGINAL,
            SiteConfig.JAPANESE_TRANSLATED,
            SiteConfig.JAPANESE_REWRITE,
            SiteConfig.ENGLISH_ORIGINAL,
            SiteConfig.ENGLISH_TRANSLATED,
            SiteConfig.ENGLISH_REWRITE,
            SiteConfig.CHINESE_ORIGINAL,
            SiteConfig.CHINESE_TRANSLATED,
            SiteConfig.CHINESE_REWRITE,
            SiteConfig.DUTCH_ORIGINAL,
            SiteConfig.DUTCH_TRANSLATED,
            SiteConfig.DUTCH_REWRITE,
            SiteConfig.FRENCH_ORIGINAL,
            SiteConfig.FRENCH_TRANSLATED,
            SiteConfig.FRENCH_REWRITE,
            SiteConfig.GERMAN_ORIGINAL,
            SiteConfig.GERMAN_TRANSLATED,
            SiteConfig.GERMAN_REWRITE,
            SiteConfig.HUNGARIAN_ORIGINAL,
            SiteConfig.HUNGARIAN_TRANSLATED,
            SiteConfig.HUNGARIAN_REWRITE,
            SiteConfig.ITALIAN_ORIGINAL,
            SiteConfig.ITALIAN_TRANSLATED,
            SiteConfig.ITALIAN_REWRITE,
            SiteConfig.KOREAN_ORIGINAL,
            SiteConfig.KOREAN_TRANSLATED,
            SiteConfig.KOREAN_REWRITE,
            SiteConfig.POLISH_ORIGINAL,
            SiteConfig.POLISH_TRANSLATED,
            SiteConfig.POLISH_REWRITE,
            SiteConfig.PORTUGUESE_ORIGINAL,
            SiteConfig.PORTUGUESE_TRANSLATED,
            SiteConfig.PORTUGUESE_REWRITE,
            SiteConfig.RUSSIAN_ORIGINAL,
            SiteConfig.RUSSIAN_TRANSLATED,
            SiteConfig.RUSSIAN_REWRITE,
            SiteConfig.SPANISH_ORIGINAL,
            SiteConfig.SPANISH_TRANSLATED,
            SiteConfig.SPANISH_REWRITE,
            SiteConfig.THAI_ORIGINAL,
            SiteConfig.THAI_TRANSLATED,
            SiteConfig.THAI_REWRITE,
            SiteConfig.VIETNAMESE_ORIGINAL,
            SiteConfig.VIETNAMESE_TRANSLATED,
            SiteConfig.VIETNAMESE_REWRITE,
            SiteConfig.NA_ORIGINAL,
            SiteConfig.NA_TRANSLATED,
            SiteConfig.NA_REWRITE,
            SiteConfig.OTHER_ORIGINAL,
            SiteConfig.OTHER_TRANSLATED,
            SiteConfig.OTHER_REWRITE};

    private final boolean[][] mSelections = new boolean[ROW_COUNT][3];

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private View mCancel;
    @Nullable
    private View mOk;
    @Nullable
    private View mSelectAll;
    @Nullable
    private View mDeselectAll;
    @Nullable
    private View mInvertSelection;
    @Nullable
    private EasyRecyclerView mRecyclerView;
    @Nullable
    private LanguageAdapter mAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scene_excluded_languages);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        if (null == savedInstanceState) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }

        mCancel = ViewUtils.$$(this, R.id.cancel);
        mOk = ViewUtils.$$(this, R.id.ok);
        mSelectAll = ViewUtils.$$(this, R.id.select_all);
        mDeselectAll = ViewUtils.$$(this, R.id.deselect_all);
        mInvertSelection = ViewUtils.$$(this, R.id.invert_selection);
        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(this, R.id.recycler_view);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setClipToPadding(false);
        mAdapter = new LanguageAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mCancel.setOnClickListener(this);
        mOk.setOnClickListener(this);
        mSelectAll.setOnClickListener(this);
        mDeselectAll.setOnClickListener(this);
        mInvertSelection.setOnClickListener(this);

        boolean isDarkTheme = !AttrResources.getAttrBoolean(this, androidx.appcompat.R.attr.isLightTheme);
        Ripple.addRipple(mCancel, isDarkTheme);
        Ripple.addRipple(mOk, isDarkTheme);
        Ripple.addRipple(mSelectAll, isDarkTheme);
        Ripple.addRipple(mDeselectAll, isDarkTheme);
        Ripple.addRipple(mInvertSelection, isDarkTheme);
    }

    private boolean isDecimal(String str) {
        int length = str.length();

        // "" is not decimal
        if (length <= 0) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            char ch = str.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private void onInit() {
        String excludedLanguages = Settings.getExcludedLanguages();
        if (null == excludedLanguages) {
            return;
        }

        String[] languages = excludedLanguages.split("x");

        int iLength = languages.length;
        int jLength = LANGUAGES.length;
        for (int i = 0, j = 0; i < iLength; i++) {
            String language = languages[i];
            if (!isDecimal(language)) {
                continue;
            }

            for (; j < jLength; j++) {
                String pattern = LANGUAGES[j];
                if (pattern.equals(language)) {
                    // Get it
                    int row = j / 3;
                    int column = j % 3;
                    mSelections[row][column] = true;
                    break;
                }
            }
        }
    }

    private long saveSelectionsToLong() {
        boolean[][] selections = mSelections;
        long value = 0;
        for (int i = 0; i < ROW_COUNT; i++) {
            for (int j = 0; j < 3; j++) {
                if (selections[i][j]) {
                    value |= 1 << (i * 3 + j);
                }
            }
        }
        return value;
    }

    private void restoreSelectionsFromLong(long value) {
        boolean[][] selections = mSelections;
        for (int i = 0; i < ROW_COUNT; i++) {
            for (int j = 0; j < 3; j++) {
                selections[i][j] = 0 != ((value >>> (i * 3 + j)) & 1);
            }
        }
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        restoreSelectionsFromLong(savedInstanceState.getLong(KEY_SELECTIONS));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_SELECTIONS, saveSelectionsToLong());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mCancel = null;
        mOk = null;
        mSelectAll = null;
        mDeselectAll = null;
        mInvertSelection = null;
        mRecyclerView = null;
        mAdapter = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        if (null == mAdapter) {
            return;
        }

        if (v == mCancel) {
            finish();
        } else if (v == mOk) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            boolean first = true;
            for (boolean[] selections : mSelections) {
                for (boolean b : selections) {
                    if (b) {
                        if (!first) {
                            sb.append("x");
                        } else {
                            first = false;
                        }
                        sb.append(LANGUAGES[i]);
                    }
                    i++;
                }
            }

            String excludedLanguages = sb.toString();
            Settings.putExcludedLanguages(excludedLanguages);
            finish();
        } else if (v == mSelectAll) {
            for (boolean[] selections : mSelections) {
                int length = selections.length;
                for (int i = 0; i < length; i++) {
                    selections[i] = true;
                }
            }
            mAdapter.notifyDataSetChanged();
        } else if (v == mDeselectAll) {
            for (boolean[] selections : mSelections) {
                int length = selections.length;
                for (int i = 0; i < length; i++) {
                    selections[i] = false;
                }
            }
            mAdapter.notifyDataSetChanged();
        } else if (v == mInvertSelection) {
            for (boolean[] selections : mSelections) {
                int length = selections.length;
                for (int i = 0; i < length; i++) {
                    selections[i] = !selections[i];
                }
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    private class LanguageHolder extends RecyclerView.ViewHolder
            implements SensitiveCheckBox.OnCheckedChangeListener {

        public TextView language;
        public SensitiveCheckBox original;
        public SensitiveCheckBox translated;
        public SensitiveCheckBox rewrite;

        public LanguageHolder(View itemView) {
            super(itemView);

            ViewGroup viewGroup = (ViewGroup) itemView;
            language = (TextView) viewGroup.getChildAt(0);
            original = (SensitiveCheckBox) viewGroup.getChildAt(1);
            translated = (SensitiveCheckBox) viewGroup.getChildAt(2);
            rewrite = (SensitiveCheckBox) viewGroup.getChildAt(3);

            original.setOnCheckedChangeListener(this);
            translated.setOnCheckedChangeListener(this);
            rewrite.setOnCheckedChangeListener(this);
        }

        @Override
        public void onCheckedChanged(SensitiveCheckBox view, boolean isChecked, boolean fromUser) {
            if (fromUser) {
                int row = getAdapterPosition();
                if (row < 0) {
                    return;
                }
                int column;
                if (view == original) {
                    column = 0;
                } else if (view == translated) {
                    column = 1;
                } else {
                    column = 2;
                }
                mSelections[row][column] = !mSelections[row][column];
            }
        }
    }

    private class LanguageAdapter extends RecyclerView.Adapter<LanguageHolder> {

        @Override
        public LanguageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new LanguageHolder(getLayoutInflater().inflate(R.layout.item_excluded_languages, parent, false));
        }

        @Override
        public void onBindViewHolder(LanguageHolder holder, int position) {
            holder.language.setText(LANGUAGE_STR_IDS[position]);
            boolean[] selections = mSelections[position];
            holder.original.setChecked(selections[0]);
            holder.translated.setChecked(selections[1]);
            holder.rewrite.setChecked(selections[2]);
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT;
        }
    }
}

/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.anotherviewer.widget;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.client.SiteConfig;
import com.hippo.widget.CheckTextView;
import com.hippo.lib.yorozuya.NumberUtils;

public class CategoryTable extends TableLayout implements View.OnLongClickListener {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_CATEGORY = "category";

    private CheckTextView mDoujinshi;
    private CheckTextView mManga;
    private CheckTextView mArtistCG;
    private CheckTextView mGameCG;
    private CheckTextView mWestern;
    private CheckTextView mNonH;
    private CheckTextView mImageSets;
    private CheckTextView mCosplay;
    private CheckTextView mAsianPorn;
    private CheckTextView mMisc;

    private CheckTextView[] mOptions;

    public CategoryTable(Context context) {
        super(context);
        init();
    }

    public CategoryTable(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_category_table, this);

        ViewGroup row0 = (ViewGroup) getChildAt(0);
        mDoujinshi = (CheckTextView) row0.getChildAt(0);
        mManga = (CheckTextView) row0.getChildAt(1);

        ViewGroup row1 = (ViewGroup) getChildAt(1);
        mArtistCG = (CheckTextView) row1.getChildAt(0);
        mGameCG = (CheckTextView) row1.getChildAt(1);

        ViewGroup row2 = (ViewGroup) getChildAt(2);
        mWestern = (CheckTextView) row2.getChildAt(0);
        mNonH = (CheckTextView) row2.getChildAt(1);

        ViewGroup row3 = (ViewGroup) getChildAt(3);
        mImageSets = (CheckTextView) row3.getChildAt(0);
        mCosplay = (CheckTextView) row3.getChildAt(1);

        ViewGroup row4 = (ViewGroup) getChildAt(4);
        mAsianPorn = (CheckTextView) row4.getChildAt(0);
        mMisc = (CheckTextView) row4.getChildAt(1);

        mOptions = new CheckTextView[] {
            mDoujinshi, mManga, mArtistCG, mGameCG, mWestern,
            mNonH, mImageSets, mCosplay, mAsianPorn, mMisc
        };

        for (CheckTextView option : mOptions) {
            option.setOnLongClickListener(this);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v instanceof CheckTextView) {
            boolean checked = ((CheckTextView) v).isChecked();
            for (CheckTextView option : mOptions) {
                if (option != v) {
                    option.setChecked(!checked, false);
                }
            }
        }

        return true;
    }

    /**
     * Set each button checked or not according to category.
     *
     * @param category target category
     */
    public void setCategory(int category) {
        mDoujinshi.setChecked(!NumberUtils.int2boolean(category & SiteConfig.DOUJINSHI), false);
        mManga.setChecked(!NumberUtils.int2boolean(category & SiteConfig.MANGA), false);
        mArtistCG.setChecked(!NumberUtils.int2boolean(category & SiteConfig.ARTIST_CG), false);
        mGameCG.setChecked(!NumberUtils.int2boolean(category & SiteConfig.GAME_CG), false);
        mWestern.setChecked(!NumberUtils.int2boolean(category & SiteConfig.WESTERN), false);
        mNonH.setChecked(!NumberUtils.int2boolean(category & SiteConfig.NON_H), false);
        mImageSets.setChecked(!NumberUtils.int2boolean(category & SiteConfig.IMAGE_SET), false);
        mCosplay.setChecked(!NumberUtils.int2boolean(category & SiteConfig.COSPLAY), false);
        mAsianPorn.setChecked(!NumberUtils.int2boolean(category & SiteConfig.ASIAN_PORN), false);
        mMisc.setChecked(!NumberUtils.int2boolean(category & SiteConfig.MISC), false);
    }

    /**
     * Get category according to button.
     * @return the category of this view
     */
    public int getCategory() {
        int category = 0;
        if (!mDoujinshi.isChecked()) category |= SiteConfig.DOUJINSHI;
        if (!mManga.isChecked()) category |= SiteConfig.MANGA;
        if (!mArtistCG.isChecked()) category |= SiteConfig.ARTIST_CG;
        if (!mGameCG.isChecked()) category |= SiteConfig.GAME_CG;
        if (!mWestern.isChecked()) category |= SiteConfig.WESTERN;
        if (!mNonH.isChecked()) category |= SiteConfig.NON_H;
        if (!mImageSets.isChecked()) category |= SiteConfig.IMAGE_SET;
        if (!mCosplay.isChecked()) category |= SiteConfig.COSPLAY;
        if (!mAsianPorn.isChecked()) category |= SiteConfig.ASIAN_PORN;
        if (!mMisc.isChecked()) category |= SiteConfig.MISC;
        return category;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_KEY_CATEGORY, getCategory());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setCategory(savedState.getInt(STATE_KEY_CATEGORY));
        }
    }
}

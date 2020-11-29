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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.NumberUtils;

public class AdvanceSearchTable extends LinearLayout {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_ADVANCE_SEARCH = "advance_search";
    private static final String STATE_KEY_MIN_RATING = "min_rating";
    private static final String STATE_KEY_PAGE_FROM = "page_from";
    private static final String STATE_KEY_PAGE_TO = "page_to";

    public static final int SNAME = 0x1;
    public static final int STAGS = 0x2;
    public static final int SDESC = 0x4;
    public static final int STORR = 0x8;
    public static final int STO = 0x10;
    public static final int SDT1 = 0x20;
    public static final int SDT2 = 0x40;
    public static final int SH = 0x80;
    public static final int SFL = 0x100;
    public static final int SFU = 0x200;
    public static final int SFT = 0x400;

    private CheckBox mSname;
    private CheckBox mStags;
    private CheckBox mSdesc;
    private CheckBox mStorr;
    private CheckBox mSto;
    private CheckBox mSdt1;
    private CheckBox mSdt2;
    private CheckBox mSh;
    private CheckBox mSr;
    private Spinner mMinRating;
    private CheckBox mSp;
    private EditText mSpf;
    private EditText mSpt;
    private CheckBox mSfl;
    private CheckBox mSfu;
    private CheckBox mSft;

    public AdvanceSearchTable(Context context) {
        super(context);
        init(context);
    }

    public AdvanceSearchTable(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_advance_search_table, this);

        ViewGroup row0 = (ViewGroup) getChildAt(0);
        mSname = (CheckBox) row0.getChildAt(0);
        mStags = (CheckBox) row0.getChildAt(1);

        ViewGroup row1 = (ViewGroup) getChildAt(1);
        mSdesc = (CheckBox) row1.getChildAt(0);
        mStorr = (CheckBox) row1.getChildAt(1);

        ViewGroup row2 = (ViewGroup) getChildAt(2);
        mSto = (CheckBox) row2.getChildAt(0);
        mSdt1 = (CheckBox) row2.getChildAt(1);

        ViewGroup row3 = (ViewGroup) getChildAt(3);
        mSdt2 = (CheckBox) row3.getChildAt(0);
        mSh = (CheckBox) row3.getChildAt(1);

        ViewGroup row4 = (ViewGroup) getChildAt(4);
        mSr = (CheckBox) row4.getChildAt(0);
        mMinRating = (Spinner) row4.getChildAt(1);

        ViewGroup row5 = (ViewGroup) getChildAt(5);
        mSp = (CheckBox) row5.getChildAt(0);
        mSpf = (EditText) row5.getChildAt(1);
        mSpt = (EditText) row5.getChildAt(3);

        ViewGroup row7 = (ViewGroup) getChildAt(7);
        mSfl = (CheckBox) row7.getChildAt(0);
        mSfu = (CheckBox) row7.getChildAt(1);
        mSft = (CheckBox) row7.getChildAt(2);

        // Avoid java.lang.IllegalStateException: focus search returned a view that wasn't able to take focus!
        mSpt.setOnEditorActionListener((v, actionId, event) -> {
            View nextView = v.focusSearch(View.FOCUS_DOWN);
            if (nextView != null) {
                nextView.requestFocus(View.FOCUS_DOWN);
            }
            return true;
        });
    }

    public int getAdvanceSearch() {
        int advanceSearch = 0;
        if (mSname.isChecked()) advanceSearch |= SNAME;
        if (mStags.isChecked()) advanceSearch |= STAGS;
        if (mSdesc.isChecked()) advanceSearch |= SDESC;
        if (mStorr.isChecked()) advanceSearch |= STORR;
        if (mSto.isChecked()) advanceSearch |= STO;
        if (mSdt1.isChecked()) advanceSearch |= SDT1;
        if (mSdt2.isChecked()) advanceSearch |= SDT2;
        if (mSh.isChecked()) advanceSearch |= SH;
        if (mSfl.isChecked()) advanceSearch |= SFL;
        if (mSfu.isChecked()) advanceSearch |= SFU;
        if (mSft.isChecked()) advanceSearch |= SFT;
        return advanceSearch;
    }

    public int getMinRating() {
        int position = mMinRating.getSelectedItemPosition();
        if (mSr.isChecked() && position >= 0) {
            return position + 2;
        } else {
            return -1;
        }
    }

    public int getPageFrom() {
        if (mSp.isChecked()) {
            return NumberUtils.parseIntSafely(mSpf.getText().toString(), -1);
        }
        return -1;
    }

    public int getPageTo() {
        if (mSp.isChecked()) {
            return NumberUtils.parseIntSafely(mSpt.getText().toString(), -1);
        }
        return -1;
    }

    public void setAdvanceSearch(int advanceSearch) {
        mSname.setChecked(NumberUtils.int2boolean(advanceSearch & SNAME));
        mStags.setChecked(NumberUtils.int2boolean(advanceSearch & STAGS));
        mSdesc.setChecked(NumberUtils.int2boolean(advanceSearch & SDESC));
        mStorr.setChecked(NumberUtils.int2boolean(advanceSearch & STORR));
        mSto.setChecked(NumberUtils.int2boolean(advanceSearch & STO));
        mSdt1.setChecked(NumberUtils.int2boolean(advanceSearch & SDT1));
        mSdt2.setChecked(NumberUtils.int2boolean(advanceSearch & SDT2));
        mSh.setChecked(NumberUtils.int2boolean(advanceSearch & SH));
        mSfl.setChecked(NumberUtils.int2boolean(advanceSearch & SFL));
        mSfu.setChecked(NumberUtils.int2boolean(advanceSearch & SFU));
        mSft.setChecked(NumberUtils.int2boolean(advanceSearch & SFT));
    }

    public void setMinRating(int minRating) {
        if (minRating >= 2 && minRating <= 5) {
            mSr.setChecked(true);
            mMinRating.setSelection(minRating - 2);
        } else {
            mSr.setChecked(false);
        }
    }

    public void setPageFrom(int pageFrom) {
        if (pageFrom > 0) {
            mSpf.setText(Integer.toString(pageFrom));
            mSp.setChecked(true);
        } else {
            mSp.setChecked(false);
            mSpf.setText(null);
        }
    }

    public void setPageTo(int pageTo) {
        if (pageTo > 0) {
            mSpt.setText(Integer.toString(pageTo));
            mSp.setChecked(true);
        } else {
            mSp.setChecked(false);
            mSpt.setText(null);
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_KEY_ADVANCE_SEARCH, getAdvanceSearch());
        state.putInt(STATE_KEY_MIN_RATING, getMinRating());
        state.putInt(STATE_KEY_PAGE_FROM, getPageFrom());
        state.putInt(STATE_KEY_PAGE_TO, getPageTo());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setAdvanceSearch(savedState.getInt(STATE_KEY_ADVANCE_SEARCH));
            setMinRating(savedState.getInt(STATE_KEY_MIN_RATING));
            setPageFrom(savedState.getInt(STATE_KEY_PAGE_FROM));
            setPageTo(savedState.getInt(STATE_KEY_PAGE_TO));
        }
    }
}

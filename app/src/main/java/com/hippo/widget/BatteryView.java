/*
 * Copyright (C) 2014 Hippo Seven
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

package com.hippo.widget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.BatteryManager;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import com.hippo.drawable.BatteryDrawable;
import com.hippo.ehviewer.R;

public class BatteryView extends AppCompatTextView {

    private int mColor;
    private int mWarningColor;
    private int mCurrentColor;

    private int mLevel = 0;
    private boolean mCharging = false;

    private BatteryDrawable mDrawable;

    private boolean mAttached = false;
    private boolean mIsChargerWorking = false;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

        @Override
        @SuppressLint("SetTextI18n")
        public void onReceive(Context context, Intent intent) {

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            boolean charging = (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    == BatteryManager.BATTERY_STATUS_CHARGING);

            if (mLevel != level || mCharging != charging) {
                mLevel = level;
                mCharging = charging;
                if (mCharging && mLevel != 100) {
                    startCharger();
                } else {
                    stopCharger();
                    mDrawable.setElect(mLevel);
                }

                if (level <= BatteryDrawable.WARN_LIMIT && !charging) {
                    setTextColor(mWarningColor);
                } else {
                    setTextColor(mColor);
                }
                setText(mLevel + "%");
            }
        }
    };

    private final Runnable mCharger = new Runnable() {

        private int level = 0;

        @Override
        public void run() {
            level += 2;
            if (level > 100) {
                level = 0;
            }
            mDrawable.setElect(level, false);
            getHandler().postDelayed(mCharger, 200);
        }
    };

    public BatteryView(Context context) {
        super(context);
        init();
    }

    public BatteryView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();

        TypedArray typedArray = context.obtainStyledAttributes(
                attrs, R.styleable.BatteryView, defStyleAttr, 0);
        mColor = typedArray.getColor(R.styleable.BatteryView_color, Color.WHITE);
        mWarningColor = typedArray.getColor(R.styleable.BatteryView_warningColor, Color.RED);
        typedArray.recycle();

        mDrawable.setColor(mColor);
        mDrawable.setWarningColor(mWarningColor);
    }

    private void init() {
        mDrawable = new BatteryDrawable();
        int height = (int) getTextSize();
        mDrawable.setBounds(0, 0, (int) (height / 0.618f), height);
        setCompoundDrawables(mDrawable, null, null, null);
    }

    @Override
    public void setTextColor(int color) {
        if (mCurrentColor == color) {
            return;
        }
        mCurrentColor = color;
        super.setTextColor(color);
    }

    private void startCharger() {
        if (!mIsChargerWorking) {
            getHandler().post(mCharger);
            mIsChargerWorking = true;
        }
    }

    private void stopCharger() {
        if (mIsChargerWorking) {
            getHandler().removeCallbacks(mCharger);
            mIsChargerWorking = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;

            registerReceiver();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            unregisterReceiver();
            stopCharger();
            mAttached = false;
        }
    }

    private void registerReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
    }

    private void unregisterReceiver() {
        getContext().unregisterReceiver(mIntentReceiver);
    }
}

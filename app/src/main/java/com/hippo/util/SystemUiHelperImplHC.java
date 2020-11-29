/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.hippo.util;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class SystemUiHelperImplHC extends SystemUiHelper.SystemUiHelperImpl
        implements View.OnSystemUiVisibilityChangeListener {

    final View mDecorView;

    SystemUiHelperImplHC(Activity activity, int level, int flags,
            SystemUiHelper.OnVisibilityChangeListener onVisibilityChangeListener) {
        super(activity, level, flags, onVisibilityChangeListener);

        mDecorView = activity.getWindow().getDecorView();
        mDecorView.setOnSystemUiVisibilityChangeListener(this);
    }


    @Override
    void show() {
        mDecorView.setSystemUiVisibility(createShowFlags());
    }

    @Override
    void hide() {
        mDecorView.setSystemUiVisibility(createHideFlags());
    }

    @Override
    public final void onSystemUiVisibilityChange(int visibility) {
        if ((visibility & createTestFlags()) != 0) {
            onSystemUiHidden();
        } else {
            onSystemUiShown();
        }
    }

    protected void onSystemUiShown() {
        ActionBar ab = mActivity.getActionBar();
        if (ab != null) {
            ab.show();
        }

        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setIsShowing(true);
    }

    protected void onSystemUiHidden() {
        ActionBar ab = mActivity.getActionBar();
        if (ab != null) {
            ab.hide();
        }

        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setIsShowing(false);
    }

    protected int createShowFlags() {
       return View.STATUS_BAR_VISIBLE;
    }

    protected int createHideFlags() {
        return View.STATUS_BAR_HIDDEN;
    }

    protected int createTestFlags() {
        return View.STATUS_BAR_HIDDEN;
    }
}

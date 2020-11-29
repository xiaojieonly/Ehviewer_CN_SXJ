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

package com.hippo.ehviewer.ui.scene;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.text.Html;
import com.hippo.text.LinkMovementMethod2;
import com.hippo.yorozuya.ViewUtils;

public class AnalyticsScene extends SolidScene implements View.OnClickListener {

    @Nullable
    private View mReject;
    @Nullable
    private View mAccept;

    @Override
    public boolean needShowLeftDrawer() {
        return false;
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_analytics, container, false);

        mReject = ViewUtils.$$(view, R.id.reject);
        mAccept = ViewUtils.$$(view, R.id.accept);
        TextView text = (TextView) ViewUtils.$$(view, R.id.text);

        text.setText(Html.fromHtml(getString(R.string.analytics_explain)));
        text.setMovementMethod(LinkMovementMethod2.getInstance());

        mReject.setOnClickListener(this);
        mAccept.setOnClickListener(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mReject = null;
        mAccept = null;
    }

    @Override
    public void onClick(View v) {
        Context context = getContext2();
        if (null == context) {
            return;
        }

        if (mReject == v) {
            Settings.putEnableAnalytics(false);
        } else if (mAccept == v) {
            Settings.putEnableAnalytics(true);
            // Start Analytics
            Analytics.start(context);
        }
        Settings.putAskAnalytics(false);

        // Start new scene and finish it self
        MainActivity activity = getActivity2();
        if (null != activity) {
            startSceneForCheckStep(CHECK_STEP_ANALYTICS, getArguments());
        }
        finish();
    }
}

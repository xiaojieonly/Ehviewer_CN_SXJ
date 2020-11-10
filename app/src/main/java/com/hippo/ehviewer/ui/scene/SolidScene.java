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

import android.os.Bundle;
import android.util.Log;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.scene.Announcer;

/**
 * Scene for safety, can't be covered
 */
public class SolidScene extends BaseScene {

    private static final String TAG = SolidScene.class.getSimpleName();

    public static final int CHECK_STEP_SECURITY = 0;
    public static final int CHECK_STEP_WARNING = 1;
    public static final int CHECK_STEP_ANALYTICS = 2;
    public static final int CHECK_STEP_SIGN_IN = 3;
    public static final int CHECK_STEP_SELECT_SITE = 4;

    public static final String KEY_TARGET_SCENE = "target_scene";
    public static final String KEY_TARGET_ARGS = "target_args";

    public void startSceneForCheckStep(int checkStep, Bundle args) {
        switch (checkStep) {
            case CHECK_STEP_SECURITY:
                if (Settings.getShowWarning()) {
                    startScene(new Announcer(WarningScene.class).setArgs(args));
                    break;
                }
            case CHECK_STEP_WARNING:
                if (Settings.getAskAnalytics()) {
                    startScene(new Announcer(AnalyticsScene.class).setArgs(args));
                    break;
                }
            case CHECK_STEP_ANALYTICS:
                if (EhUtils.needSignedIn(getContext2())) {
                    startScene(new Announcer(SignInScene.class).setArgs(args));
                    break;
                }
            case CHECK_STEP_SIGN_IN:
                if (Settings.getSelectSite()) {
                    startScene(new Announcer(SelectSiteScene.class).setArgs(args));
                    break;
                }
            case CHECK_STEP_SELECT_SITE:
                String targetScene = null;
                Bundle targetArgs = null;
                if (null != args) {
                    targetScene = args.getString(KEY_TARGET_SCENE);
                    targetArgs = args.getBundle(KEY_TARGET_ARGS);
                }

                Class<?> clazz = null;
                if (targetScene != null) {
                    try {
                        clazz = Class.forName(targetScene);
                    } catch (ClassNotFoundException e) {
                        Log.e(TAG, "Can't find class with name: " + targetScene);
                    }
                }

                if (clazz != null) {
                    startScene(new Announcer(clazz).setArgs(targetArgs));
                } else {
                    Bundle newArgs = new Bundle();
                    newArgs.putString(GalleryListScene.KEY_ACTION, Settings.getLaunchPageGalleryListSceneAction());
                    startScene(new Announcer(GalleryListScene.class).setArgs(newArgs));
                }
                break;
        }
    }
}

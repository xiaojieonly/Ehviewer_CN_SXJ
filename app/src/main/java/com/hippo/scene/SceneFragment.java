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

package com.hippo.scene;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.collect.IntList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class SceneFragment extends Fragment {

    @IntDef({LAUNCH_MODE_STANDARD, LAUNCH_MODE_SINGLE_TOP, LAUNCH_MODE_SINGLE_TASK})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaunchMode {}

    public static final int LAUNCH_MODE_STANDARD = 0;
    public static final int LAUNCH_MODE_SINGLE_TOP = 1;
    public static final int LAUNCH_MODE_SINGLE_TASK = 2;

    /** Standard scene result: operation canceled. */
    public static final int RESULT_CANCELED  = 0;
    /** Standard scene result: operation succeeded. */
    public static final int RESULT_OK = -1;

    int resultCode = RESULT_CANCELED;
    Bundle result = null;

    List<String> mRequestSceneTagList = new ArrayList<>(0);
    IntList mRequestCodeList = new IntList(0);

    public void onNewArguments(@NonNull Bundle args) {}

    public void startScene(Announcer announcer) {
        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            ((StageActivity) activity).startScene(announcer);
        }
    }

    public void finish() {
        finish(null);
    }

    public void finish(TransitionHelper transitionHelper) {
        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            ((StageActivity) activity).finishScene(this, transitionHelper);
        }
    }

    public void finishStage() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    /**
     * @return negative for error
     */
    public int getStackIndex() {
        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            return ((StageActivity) activity).getSceneIndex(this);
        } else {
            return -1;
        }
    }

    public void onBackPressed() {
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setTag(R.id.fragment_tag, getTag());
        view.setBackgroundDrawable(AttrResources.getAttrDrawable(getContext(), android.R.attr.windowBackground));

        // Notify
        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            ((StageActivity) activity).onSceneViewCreated(this, savedInstanceState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Notify
        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            ((StageActivity) activity).onSceneViewDestroyed(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FragmentActivity activity = getActivity();
        if (activity instanceof StageActivity) {
            ((StageActivity) activity).onSceneDestroyed(this);
        }
    }

    void addRequest(String requestSceneTag, int requestCode) {
        mRequestSceneTagList.add(requestSceneTag);
        mRequestCodeList.add(requestCode);
    }

    void returnResult(StageActivity stage) {
        for (int i = 0, size = Math.min(mRequestSceneTagList.size(), mRequestCodeList.size()); i < size; i++) {
            String tag = mRequestSceneTagList.get(i);
            int code = mRequestCodeList.get(i);
            SceneFragment scene = stage.findSceneByTag(tag);
            if (scene != null) {
                scene.onSceneResult(code, resultCode, result);
            }
        }
        mRequestSceneTagList.clear();
        mRequestCodeList.clear();
    }

    protected void onSceneResult(int requestCode, int resultCode, Bundle data) {
    }

    public void setResult(int resultCode, Bundle result) {
        this.resultCode = resultCode;
        this.result = result;
    }
}

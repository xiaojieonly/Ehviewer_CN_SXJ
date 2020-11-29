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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.util.DrawableManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.view.ViewTransition;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ViewUtils;

/**
 * Only show a progress with jobs in background
 */
public final class ProgressScene extends BaseScene implements View.OnClickListener {

    public static final String KEY_ACTION = "action";
    public static final String ACTION_GALLERY_TOKEN = "gallery_token";

    private static final String KEY_VALID = "valid";
    private static final String KEY_ERROR = "error";

    public static final String KEY_GID = "gid";
    public static final String KEY_PTOKEN = "ptoken";
    public static final String KEY_PAGE = "page";

    private boolean mValid;
    private String mError;

    private String mAction;

    private long mGid;
    private String mPToken;
    private int mPage;

    @Nullable
    private TextView mTip;
    @Nullable
    private ViewTransition mViewTransition;

    @Override
    public boolean needShowLeftDrawer() {
        return false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private boolean doJobs() {
        Context context = getContext2();
        MainActivity activity = getActivity2();
        if (null == context || null == activity) {
            return false;
        }

        if (ACTION_GALLERY_TOKEN.equals(mAction)) {
            if (mGid == -1 || mPToken == null || mPage == -1) {
                return false;
            }

            EhRequest request = new EhRequest()
                    .setMethod(EhClient.METHOD_GET_GALLERY_TOKEN)
                    .setArgs(mGid, mPToken, mPage)
                    .setCallback(new GetGalleryTokenListener(context,
                            activity.getStageId(), getTag()));
            EhApplication.getEhClient(context).execute(request);
            return true;
        }
        return false;
    }

    private boolean handleArgs(Bundle args) {
        if (args == null) {
            return false;
        }

        mAction = args.getString(KEY_ACTION);
        if (ACTION_GALLERY_TOKEN.equals(mAction)) {
            mGid = args.getLong(KEY_GID, -1);
            mPToken = args.getString(KEY_PTOKEN, null);
            mPage = args.getInt(KEY_PAGE, -1);
            if (mGid == -1 || mPToken == null || mPage == -1) {
                return false;
            }
            return true;
        }

        return false;
    }

    private void onInit() {
        mValid = handleArgs(getArguments());
        if (mValid) {
            mValid = doJobs();
        }
        if (!mValid) {
            mError = getString(R.string.error_something_wrong_happened);
        }
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mValid = savedInstanceState.getBoolean(KEY_VALID);
        mError = savedInstanceState.getString(KEY_ERROR);

        mAction = savedInstanceState.getString(KEY_ACTION);

        mGid = savedInstanceState.getLong(KEY_GID, -1);
        mPToken = savedInstanceState.getString(KEY_PTOKEN, null);
        mPage = savedInstanceState.getInt(KEY_PAGE, -1);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_VALID, mValid);
        outState.putString(KEY_ERROR, mError);

        outState.putString(KEY_ACTION, mAction);

        outState.putLong(KEY_GID, mGid);
        outState.putString(KEY_PTOKEN, mPToken);
        outState.putInt(KEY_PAGE, mPage);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_progress, container, false);
        View progress = ViewUtils.$$(view, R.id.progress);
        mTip = (TextView) ViewUtils.$$(view, R.id.tip);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);

        Drawable drawable = DrawableManager.getVectorDrawable(context, R.drawable.big_sad_pandroid);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        mTip.setCompoundDrawables(null, drawable, null, null);
        mTip.setOnClickListener(this);
        mTip.setText(mError);

        mViewTransition = new ViewTransition(progress, mTip);

        if (mValid) {
            mViewTransition.showView(0, false);
        } else {
            mViewTransition.showView(1, false);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mTip = null;
        mViewTransition = null;
    }

    @Override
    public void onClick(View v) {
        if (mTip == v) {
            if (doJobs()) {
                mValid = true;
                // Show progress
                if (null != mViewTransition) {
                    mViewTransition.showView(0, true);
                }
            }
        }
    }

    private void onGetGalleryTokenSuccess(String result) {
        Bundle arg = new Bundle();
        arg.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN);
        arg.putLong(GalleryDetailScene.KEY_GID, mGid);
        arg.putString(GalleryDetailScene.KEY_TOKEN, result);
        arg.putInt(GalleryDetailScene.KEY_PAGE, mPage);
        startScene(new Announcer(GalleryDetailScene.class).setArgs(arg));
        finish();
    }

    private void onGetGalleryTokenFailure(Exception e) {
        mValid = false;

        Context context = getContext2();

        if (null != context && null != mViewTransition && null != mTip) {
            // Show tip
            mError = ExceptionUtils.getReadableString(e);
            mViewTransition.showView(1);
            mTip.setText(mError);
        }
    }

    private static class GetGalleryTokenListener extends EhCallback<ProgressScene, String> {

        public GetGalleryTokenListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(String result) {
            ProgressScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryTokenSuccess(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            ProgressScene scene = getScene();
            if (scene != null) {
                scene.onGetGalleryTokenFailure(e);
            }
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof ProgressScene;
        }
    }
}

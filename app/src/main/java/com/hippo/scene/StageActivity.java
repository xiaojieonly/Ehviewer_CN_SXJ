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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.IntIdGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class StageActivity extends EhActivity {

    private static final String TAG = StageActivity.class.getSimpleName();

    public static final String ACTION_START_SCENE = "start_scene";

    public static final String KEY_SCENE_NAME = "stage_activity_scene_name";

    public static final String KEY_SCENE_ARGS = "stage_activity_scene_args";

    private static final String KEY_STAGE_ID = "stage_activity_stage_id";

    private static final String KEY_SCENE_TAG_LIST = "stage_activity_scene_tag_list";

    private static final String KEY_NEXT_ID = "stage_activity_next_id";

    private final ArrayList<String> mSceneTagList = new ArrayList<>();

    private final ArrayList<String> mDelaySceneTagList = new ArrayList<>();

    private final AtomicInteger mIdGenerator = new AtomicInteger();

    private int mStageId = IntIdGenerator.INVALID_ID;

    private final SceneViewComparator mSceneViewComparator = new SceneViewComparator();

    private final class SceneViewComparator implements Comparator<View> {

        private int getIndex(View view) {
            Object o = view.getTag(R.id.fragment_tag);
            if (o instanceof String) {
                return mDelaySceneTagList.indexOf(o);
            } else {
                return -1;
            }
        }

        @Override
        public int compare(View lhs, View rhs) {
            return getIndex(lhs) - getIndex(rhs);
        }
    }

    private static final Map<Class<?>, Integer> sLaunchModeMap = new HashMap<>();

    public static void registerLaunchMode(Class<?> clazz, @SceneFragment.LaunchMode int launchMode) {
        if (launchMode != SceneFragment.LAUNCH_MODE_STANDARD && launchMode != SceneFragment.LAUNCH_MODE_SINGLE_TOP && launchMode != SceneFragment.LAUNCH_MODE_SINGLE_TASK) {
            throw new IllegalStateException("Invalid launch mode: " + launchMode);
        }
        sLaunchModeMap.put(clazz, launchMode);
    }

    public abstract int getContainerViewId();

    /**
     * @return {@code true} for start scene
     */
    private boolean startSceneFromIntent(Intent intent) {
        String clazzStr = intent.getStringExtra(KEY_SCENE_NAME);
        if (null == clazzStr) {
            return false;
        }
        Class clazz;
        try {
            clazz = Class.forName(clazzStr);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Can't find class " + clazzStr, e);
            return false;
        }
        Bundle args = intent.getBundleExtra(KEY_SCENE_ARGS);
        Announcer announcer = onStartSceneFromIntent(clazz, args);
        if (announcer == null) {
            return false;
        }
        startScene(announcer);
        return true;
    }

    /**
     * Start scene from {@code Intent}, it might be not safe,
     * Correct it here.
     *
     * @return {@code null} for do not start scene
     */
    @Nullable
    protected Announcer onStartSceneFromIntent(@NonNull Class<?> clazz, @Nullable Bundle args) {
        return new Announcer(clazz).setArgs(args);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || !ACTION_START_SCENE.equals(intent.getAction()) || !startSceneFromIntent(intent)) {
            onUnrecognizedIntent(intent);
        }
    }

    /**
     * Called when launch with action {@code android.intent.action.MAIN}
     */
    @Nullable
    protected abstract Announcer getLaunchAnnouncer();

    /**
     * Can't recognize intent in first time {@code onCreate} and {@code onNewIntent},
     * null included.
     */
    protected void onUnrecognizedIntent(@Nullable Intent intent) {
    }

    /**
     * Call {@code setContentView} here. Do <b>NOT</b> call {@code startScene} here
     */
    protected abstract void onCreate2(@Nullable Bundle savedInstanceState);

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mStageId = savedInstanceState.getInt(KEY_STAGE_ID, IntIdGenerator.INVALID_ID);
            ArrayList<String> list = savedInstanceState.getStringArrayList(KEY_SCENE_TAG_LIST);
            if (list != null) {
                mSceneTagList.addAll(list);
                mDelaySceneTagList.addAll(list);
            }
            mIdGenerator.lazySet(savedInstanceState.getInt(KEY_NEXT_ID));
        }
        if (mStageId == IntIdGenerator.INVALID_ID) {
            ((SceneApplication) getApplicationContext()).registerStageActivity(this);
        } else {
            ((SceneApplication) getApplicationContext()).registerStageActivity(this, mStageId);
        }
        // Create layout
        onCreate2(savedInstanceState);
        Intent intent = getIntent();
        if (savedInstanceState == null) {
            if (intent != null) {
                String action = intent.getAction();
                if (Intent.ACTION_MAIN.equals(action)) {
                    Announcer announcer = getLaunchAnnouncer();
                    if (announcer != null) {
                        startScene(announcer);
                        return;
                    }
                } else if (ACTION_START_SCENE.equals(action)) {
                    if (startSceneFromIntent(intent)) {
                        return;
                    }
                }
            }
            // Can't recognize intent
            onUnrecognizedIntent(intent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STAGE_ID, mStageId);
        outState.putStringArrayList(KEY_SCENE_TAG_LIST, mSceneTagList);
        outState.putInt(KEY_NEXT_ID, mIdGenerator.getAndIncrement());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((SceneApplication) getApplicationContext()).unregisterStageActivity(mStageId);
    }

    public void onSceneViewCreated(SceneFragment scene, Bundle savedInstanceState) {
    }

    public void onSceneViewDestroyed(SceneFragment scene) {
    }

    void onSceneDestroyed(SceneFragment scene) {
        mDelaySceneTagList.remove(scene.getTag());
    }

    protected void onRegister(int id) {
        mStageId = id;
    }

    protected void onUnregister() {
    }

    protected void onTransactScene() {
    }

    public int getStageId() {
        return mStageId;
    }

    public int getSceneCount() {
        return mSceneTagList.size();
    }

    public int getSceneLaunchMode(Class<?> clazz) {
        Integer integer = sLaunchModeMap.get(clazz);
        if (integer == null) {
            throw new RuntimeException("Not register " + clazz.getName());
        } else {
            return integer;
        }
    }

    private SceneFragment newSceneInstance(Class<?> clazz) {
        try {
            return (SceneFragment) clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Can't instance " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("The constructor of " + clazz.getName() + " is not visible", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException(clazz.getName() + " can not cast to scene", e);
        }
    }

    public void startScene(Announcer announcer) {
        Class<?> clazz = announcer.clazz;
        Bundle args = announcer.args;
        TransitionHelper tranHelper = announcer.tranHelper;
        FragmentManager fragmentManager = getSupportFragmentManager();
        int launchMode = getSceneLaunchMode(clazz);
        // Check LAUNCH_MODE_SINGLE_TASK
        if (launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TASK) {
            for (int i = 0, n = mSceneTagList.size(); i < n; i++) {
                String tag = mSceneTagList.get(i);
                Fragment fragment = fragmentManager.findFragmentByTag(tag);
                if (fragment == null) {
                    Log.e(TAG, "Can't find fragment with tag: " + tag);
                    continue;
                }
                if (clazz.isInstance(fragment)) {
                    // Get it
                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    // Use default animation
                    transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit);
                    // Remove top fragments
                    for (int j = i + 1; j < n; j++) {
                        String topTag = mSceneTagList.get(j);
                        Fragment topFragment = fragmentManager.findFragmentByTag(topTag);
                        if (null == topFragment) {
                            Log.e(TAG, "Can't find fragment with tag: " + topTag);
                            continue;
                        }
                        // Clear shared element
                        topFragment.setSharedElementEnterTransition(null);
                        topFragment.setSharedElementReturnTransition(null);
                        topFragment.setEnterTransition(null);
                        topFragment.setExitTransition(null);
                        // Remove it
                        transaction.remove(topFragment);
                    }
                    // Remove tag from index i+1
                    mSceneTagList.subList(i + 1, mSceneTagList.size()).clear();
                    // Attach fragment
                    if (fragment.isDetached()) {
                        transaction.attach(fragment);
                    }
                    // Commit
                    transaction.commitAllowingStateLoss();
                    onTransactScene();
                    // New arguments
                    if (args != null && fragment instanceof SceneFragment) {
                        ((SceneFragment) fragment).onNewArguments(args);
                    }
                    return;
                }
            }
        }
        // Get current fragment
        SceneFragment currentScene = null;
        if (mSceneTagList.size() > 0) {
            // Get last tag
            String tag = mSceneTagList.get(mSceneTagList.size() - 1);
            Fragment fragment = fragmentManager.findFragmentByTag(tag);
            if (fragment != null) {
                AssertUtils.assertTrue(SceneFragment.class.isInstance(fragment));
                currentScene = (SceneFragment) fragment;
            }
        }
        // Check LAUNCH_MODE_SINGLE_TASK
        if (clazz.isInstance(currentScene) && launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TOP) {
            if (args != null) {
                currentScene.onNewArguments(args);
            }
            return;
        }
        // Create new scene
        SceneFragment newScene = newSceneInstance(clazz);
        newScene.setArguments(args);
        // Create new scene tag
        String newTag = Integer.toString(mIdGenerator.getAndIncrement());
        // Add new tag to list
        mSceneTagList.add(newTag);
        mDelaySceneTagList.add(newTag);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        // Animation
        if (currentScene != null) {
            if (tranHelper == null || !tranHelper.onTransition(this, transaction, currentScene, newScene)) {
                // Clear shared item
                currentScene.setSharedElementEnterTransition(null);
                currentScene.setSharedElementReturnTransition(null);
                currentScene.setEnterTransition(null);
                currentScene.setExitTransition(null);
                newScene.setSharedElementEnterTransition(null);
                newScene.setSharedElementReturnTransition(null);
                newScene.setEnterTransition(null);
                newScene.setExitTransition(null);
                // Set default animation
                transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit);
            }
            // Detach current scene
            if (!currentScene.isDetached()) {
                transaction.detach(currentScene);
            } else {
                Log.e(TAG, "Current scene is detached");
            }
        }
        // Add new scene
        transaction.add(getContainerViewId(), newScene, newTag);
        // Commit
        transaction.commitAllowingStateLoss();
        onTransactScene();
        // Check request
        if (announcer.requestFrom != null) {
            newScene.addRequest(announcer.requestFrom.getTag(), announcer.requestCode);
        }
    }

    public void startSceneFirstly(Announcer announcer) {
        Class<?> clazz = announcer.clazz;
        Bundle args = announcer.args;
        FragmentManager fragmentManager = getSupportFragmentManager();
        int launchMode = getSceneLaunchMode(clazz);
        boolean forceNewScene = launchMode == SceneFragment.LAUNCH_MODE_STANDARD;
        boolean createNewScene = true;
        boolean findScene = false;
        SceneFragment scene = null;
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        // Set default animation
        transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit);
        String findSceneTag = null;
        for (int i = 0, n = mSceneTagList.size(); i < n; i++) {
            String tag = mSceneTagList.get(i);
            Fragment fragment = fragmentManager.findFragmentByTag(tag);
            if (fragment == null) {
                Log.e(TAG, "Can't find fragment with tag: " + tag);
                continue;
            }
            // Clear shared element
            fragment.setSharedElementEnterTransition(null);
            fragment.setSharedElementReturnTransition(null);
            fragment.setEnterTransition(null);
            fragment.setExitTransition(null);
            // Check is target scene
            if (!forceNewScene && !findScene && clazz.isInstance(fragment) && (launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TASK || !fragment.isDetached())) {
                scene = (SceneFragment) fragment;
                findScene = true;
                createNewScene = false;
                findSceneTag = tag;
                if (fragment.isDetached()) {
                    transaction.attach(fragment);
                }
            } else {
                // Remove it
                transaction.remove(fragment);
            }
        }
        // Handle tag list
        mSceneTagList.clear();
        if (null != findSceneTag) {
            mSceneTagList.add(findSceneTag);
        }
        if (createNewScene) {
            scene = newSceneInstance(clazz);
            scene.setArguments(args);
            // Create scene tag
            String tag = Integer.toString(mIdGenerator.getAndIncrement());
            // Add tag to list
            mSceneTagList.add(tag);
            mDelaySceneTagList.add(tag);
            // Add scene
            transaction.add(getContainerViewId(), scene, tag);
        }
        // Commit
        transaction.commitAllowingStateLoss();
        onTransactScene();
        if (!createNewScene && args != null) {
            scene.onNewArguments(args);
        }
    }

    int getSceneIndex(SceneFragment scene) {
        return getTagIndex(scene.getTag());
    }

    int getTagIndex(String tag) {
        return mSceneTagList.indexOf(tag);
    }

    void sortSceneViews(List<View> views) {
        Collections.sort(views, mSceneViewComparator);
    }

    public void finishScene(SceneFragment scene) {
        finishScene(scene, null);
    }

    public void finishScene(SceneFragment scene, TransitionHelper transitionHelper) {
        finishScene(scene.getTag(), transitionHelper);
    }

    private void finishScene(String tag, TransitionHelper transitionHelper) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        // Get scene
        Fragment scene = fragmentManager.findFragmentByTag(tag);
        if (scene == null) {
            Log.e(TAG, "finishScene: Can't find scene by tag: " + tag);
            return;
        }
        // Get scene index
        int index = mSceneTagList.indexOf(tag);
        if (index < 0) {
            Log.e(TAG, "finishScene: Can't find the tag in tag list: " + tag);
            return;
        }
        if (mSceneTagList.size() == 1) {
            // It is the last fragment, finish Activity now
            Log.i(TAG, "finishScene: It is the last scene, finish activity now");
            finish();
            return;
        }
        Fragment next = null;
        if (index == mSceneTagList.size() - 1) {
            // It is first fragment, show the next one
            next = fragmentManager.findFragmentByTag(mSceneTagList.get(index - 1));
        }
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (next != null) {
            if (transitionHelper == null || !transitionHelper.onTransition(this, transaction, scene, next)) {
                // Clear shared item
                scene.setSharedElementEnterTransition(null);
                scene.setSharedElementReturnTransition(null);
                scene.setEnterTransition(null);
                scene.setExitTransition(null);
                next.setSharedElementEnterTransition(null);
                next.setSharedElementReturnTransition(null);
                next.setEnterTransition(null);
                next.setExitTransition(null);
                // Do not show animate if it is not the first fragment
                transaction.setCustomAnimations(R.anim.scene_close_enter, R.anim.scene_close_exit);
            }
            // Attach fragment
            transaction.attach(next);
        }
        transaction.remove(scene);
        transaction.commitAllowingStateLoss();
        onTransactScene();
        // Remove tag
        mSceneTagList.remove(index);
        // Return result
        if (scene instanceof SceneFragment) {
            ((SceneFragment) scene).returnResult(this);
        }
    }

    public void refreshTopScene() {
        int index = mSceneTagList.size() - 1;
        if (index < 0) {
            return;
        }
        String tag = mSceneTagList.get(index);
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.detach(fragment);
        transaction.attach(fragment);
        transaction.commitAllowingStateLoss();
        onTransactScene();
    }

    @Override
    public void onBackPressed() {
        int size = mSceneTagList.size();
        String tag = mSceneTagList.get(size - 1);
        SceneFragment scene;
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            Log.e(TAG, "onBackPressed: Can't find scene by tag: " + tag);
            return;
        }
        if (!(fragment instanceof SceneFragment)) {
            Log.e(TAG, "onBackPressed: The fragment is not SceneFragment");
            return;
        }
        scene = (SceneFragment) fragment;
        scene.onBackPressed();
    }

    public SceneFragment findSceneByTag(String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment != null) {
            return (SceneFragment) fragment;
        } else {
            return null;
        }
    }

    @Nullable
    public Class<?> getTopSceneClass() {
        int index = mSceneTagList.size() - 1;
        if (index < 0) {
            return null;
        }
        String tag = mSceneTagList.get(index);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (null == fragment) {
            return null;
        }
        return fragment.getClass();
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            int uiMode = overrideConfiguration.uiMode;
            overrideConfiguration.setTo(getApplicationContext().getResources().getConfiguration());
            overrideConfiguration.uiMode = uiMode;
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }
}

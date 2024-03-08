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
package com.hippo.scene

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.hippo.ehviewer.R
import com.hippo.ehviewer.ui.EhActivity
import com.hippo.scene.SceneFragment
import com.hippo.scene.SceneFragment.LaunchMode
import com.hippo.yorozuya.AssertUtils
import com.hippo.yorozuya.IntIdGenerator
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class StageActivity : EhActivity() {
    private val mSceneTagList = ArrayList<String?>()
    private val mDelaySceneTagList = ArrayList<String?>()
    private val mIdGenerator = AtomicInteger()
    var stageId = IntIdGenerator.INVALID_ID
        private set
    private val mSceneViewComparator = SceneViewComparator()

    private inner class SceneViewComparator : Comparator<View> {
        private fun getIndex(view: View): Int {
            val o = view.getTag(R.id.fragment_tag)
            return if (o is String) {
                mDelaySceneTagList.indexOf(o)
            } else {
                -1
            }
        }

        override fun compare(lhs: View, rhs: View): Int {
            return getIndex(lhs) - getIndex(rhs)
        }
    }

    abstract val containerViewId: Int

    /**
     * @return `true` for start scene
     */
    private fun startSceneFromIntent(intent: Intent): Boolean {
        val clazzStr = intent.getStringExtra(KEY_SCENE_NAME) ?: return false
        val clazz: Class<*>
        clazz = try {
            Class.forName(clazzStr)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Can't find class $clazzStr", e)
            return false
        }
        val args = intent.getBundleExtra(KEY_SCENE_ARGS)
        val announcer = onStartSceneFromIntent(clazz, args) ?: return false
        startScene(announcer)
        return true
    }

    /**
     * Start scene from `Intent`, it might be not safe,
     * Correct it here.
     *
     * @return `null` for do not start scene
     */
    protected open fun onStartSceneFromIntent(clazz: Class<*>, args: Bundle?): Announcer? {
        return Announcer(clazz).setArgs(args)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent == null || ACTION_START_SCENE != intent.action ||
            !startSceneFromIntent(intent)
        ) {
            onUnrecognizedIntent(intent)
        }
    }

    /**
     * Called when launch with action `android.intent.action.MAIN`
     */
    protected abstract val launchAnnouncer: Announcer?

    /**
     * Can't recognize intent in first time `onCreate` and `onNewIntent`,
     * null included.
     */
    protected open fun onUnrecognizedIntent(intent: Intent?) {}

    /**
     * Call `setContentView` here. Do **NOT** call `startScene` here
     */
    protected abstract fun onCreate2(savedInstanceState: Bundle?)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            stageId = savedInstanceState.getInt(KEY_STAGE_ID, IntIdGenerator.INVALID_ID)
            val list = savedInstanceState.getStringArrayList(KEY_SCENE_TAG_LIST)
            if (list != null) {
                mSceneTagList.addAll(list)
                mDelaySceneTagList.addAll(list)
            }
            mIdGenerator.lazySet(savedInstanceState.getInt(KEY_NEXT_ID))
        }
        if (stageId == IntIdGenerator.INVALID_ID) {
            (applicationContext as SceneApplication).registerStageActivity(this)
        } else {
            (applicationContext as SceneApplication).registerStageActivity(this, stageId)
        }

        // Create layout
        onCreate2(savedInstanceState)
        val intent = intent
        if (savedInstanceState == null) {
            if (intent != null) {
                val action = intent.action
                if (Intent.ACTION_MAIN == action) {
                    val announcer = launchAnnouncer
                    if (announcer != null) {
                        startScene(announcer)
                        return
                    }
                } else if (ACTION_START_SCENE == action) {
                    if (startSceneFromIntent(intent)) {
                        return
                    }
                }
            }

            // Can't recognize intent
            onUnrecognizedIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STAGE_ID, stageId)
        outState.putStringArrayList(KEY_SCENE_TAG_LIST, mSceneTagList)
        outState.putInt(KEY_NEXT_ID, mIdGenerator.getAndIncrement())
    }

    override fun onDestroy() {
        super.onDestroy()
        (applicationContext as SceneApplication).unregisterStageActivity(stageId)
    }

    open fun onSceneViewCreated(scene: SceneFragment?, savedInstanceState: Bundle?) {}
    open fun onSceneViewDestroyed(scene: SceneFragment?) {}
    fun onSceneDestroyed(scene: SceneFragment) {
        mDelaySceneTagList.remove(scene.tag)
    }

    fun onRegister(id: Int) {
        stageId = id
    }

    fun onUnregister() {}
    protected open fun onTransactScene() {}
    val sceneCount: Int
        get() = mSceneTagList.size

    fun getSceneLaunchMode(clazz: Class<*>): Int {
        val integer = sLaunchModeMap[clazz]
        return integer ?: throw RuntimeException("Not register " + clazz.name)
    }

    private fun newSceneInstance(clazz: Class<*>): SceneFragment {
        return try {
            clazz.newInstance() as SceneFragment
        } catch (e: InstantiationException) {
            throw IllegalStateException("Can't instance " + clazz.name, e)
        } catch (e: IllegalAccessException) {
            throw IllegalStateException(
                "The constructor of " +
                        clazz.name + " is not visible", e
            )
        } catch (e: ClassCastException) {
            throw IllegalStateException(clazz.name + " can not cast to scene", e)
        }
    }

    fun startScene(announcer: Announcer) {
        val clazz = announcer.clazz
        val args = announcer.args
        val tranHelper = announcer.tranHelper
        val fragmentManager = supportFragmentManager
        val launchMode = getSceneLaunchMode(clazz)

        // Check LAUNCH_MODE_SINGLE_TASK
        if (launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TASK) {
            var i = 0
            val n = mSceneTagList.size
            while (i < n) {
                val tag = mSceneTagList[i]
                val fragment = fragmentManager.findFragmentByTag(tag)
                if (fragment == null) {
                    Log.e(TAG, "Can't find fragment with tag: $tag")
                    i++
                    continue
                }
                if (clazz.isInstance(fragment)) { // Get it
                    val transaction = fragmentManager.beginTransaction()

                    // Use default animation
                    transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit)

                    // Remove top fragments
                    for (j in i + 1 until n) {
                        val topTag = mSceneTagList[j]
                        val topFragment = fragmentManager.findFragmentByTag(topTag)
                        if (null == topFragment) {
                            Log.e(TAG, "Can't find fragment with tag: $topTag")
                            continue
                        }
                        // Clear shared element
                        topFragment.sharedElementEnterTransition = null
                        topFragment.sharedElementReturnTransition = null
                        topFragment.enterTransition = null
                        topFragment.exitTransition = null
                        // Remove it
                        transaction.remove(topFragment)
                    }

                    // Remove tag from index i+1
                    mSceneTagList.subList(i + 1, mSceneTagList.size).clear()

                    // Attach fragment
                    if (fragment.isDetached) {
                        transaction.attach(fragment)
                    }

                    // Commit
                    transaction.commitAllowingStateLoss()
                    onTransactScene()

                    // New arguments
                    if (args != null && fragment is SceneFragment) {
                        fragment.onNewArguments(args)
                    }
                    return
                }
                i++
            }
        }

        // Get current fragment
        var currentScene: SceneFragment? = null
        if (mSceneTagList.size > 0) {
            // Get last tag
            val tag = mSceneTagList[mSceneTagList.size - 1]
            val fragment = fragmentManager.findFragmentByTag(tag)
            if (fragment != null) {
                AssertUtils.assertTrue(SceneFragment::class.java.isInstance(fragment))
                currentScene = fragment as SceneFragment?
            }
        }

        // Check LAUNCH_MODE_SINGLE_TASK
        if (clazz.isInstance(currentScene) && launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TOP) {
            if (args != null) {
                currentScene!!.onNewArguments(args)
            }
            return
        }

        // Create new scene
        val newScene = newSceneInstance(clazz)
        newScene.arguments = args

        // Create new scene tag
        val newTag = Integer.toString(mIdGenerator.getAndIncrement())

        // Add new tag to list
        mSceneTagList.add(newTag)
        mDelaySceneTagList.add(newTag)
        val transaction = fragmentManager.beginTransaction()
        // Animation
        if (currentScene != null) {
            if (tranHelper == null || !tranHelper.onTransition(
                    this, transaction, currentScene, newScene
                )
            ) {
                // Clear shared item
                currentScene.sharedElementEnterTransition = null
                currentScene.sharedElementReturnTransition = null
                currentScene.enterTransition = null
                currentScene.exitTransition = null
                newScene.sharedElementEnterTransition = null
                newScene.sharedElementReturnTransition = null
                newScene.enterTransition = null
                newScene.exitTransition = null
                // Set default animation
                transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit)
            }
            // Detach current scene
            if (!currentScene.isDetached) {
                transaction.detach(currentScene)
            } else {
                Log.e(TAG, "Current scene is detached")
            }
        }

        // Add new scene
        transaction.add(containerViewId, newScene, newTag)

        // Commit
        transaction.commitAllowingStateLoss()
        onTransactScene()

        // Check request
        if (announcer.requestFrom != null) {
            newScene.addRequest(announcer.requestFrom!!.tag!!, announcer.requestCode)
        }
    }

    fun startSceneFirstly(announcer: Announcer) {
        val clazz = announcer.clazz
        val args = announcer.args
        val fragmentManager = supportFragmentManager
        val launchMode = getSceneLaunchMode(clazz)
        val forceNewScene = launchMode == SceneFragment.LAUNCH_MODE_STANDARD
        var createNewScene = true
        var findScene = false
        var scene: SceneFragment? = null
        val transaction = fragmentManager.beginTransaction()

        // Set default animation
        transaction.setCustomAnimations(R.anim.scene_open_enter, R.anim.scene_open_exit)
        var findSceneTag: String? = null
        var i = 0
        val n = mSceneTagList.size
        while (i < n) {
            val tag = mSceneTagList[i]
            val fragment = fragmentManager.findFragmentByTag(tag)
            if (fragment == null) {
                Log.e(TAG, "Can't find fragment with tag: $tag")
                i++
                continue
            }

            // Clear shared element
            fragment.sharedElementEnterTransition = null
            fragment.sharedElementReturnTransition = null
            fragment.enterTransition = null
            fragment.exitTransition = null

            // Check is target scene
            if (!forceNewScene && !findScene && clazz.isInstance(fragment) &&
                (launchMode == SceneFragment.LAUNCH_MODE_SINGLE_TASK || !fragment.isDetached)
            ) {
                scene = fragment as SceneFragment?
                findScene = true
                createNewScene = false
                findSceneTag = tag
                if (fragment.isDetached()) {
                    transaction.attach(fragment)
                }
            } else {
                // Remove it
                transaction.remove(fragment)
            }
            i++
        }

        // Handle tag list
        mSceneTagList.clear()
        if (null != findSceneTag) {
            mSceneTagList.add(findSceneTag)
        }
        if (createNewScene) {
            scene = newSceneInstance(clazz)
            scene.arguments = args

            // Create scene tag
            val tag = Integer.toString(mIdGenerator.getAndIncrement())

            // Add tag to list
            mSceneTagList.add(tag)
            mDelaySceneTagList.add(tag)

            // Add scene
            transaction.add(containerViewId, scene, tag)
        }

        // Commit
        transaction.commitAllowingStateLoss()
        onTransactScene()
        if (!createNewScene && args != null) {
            scene!!.onNewArguments(args)
        }
    }

    fun getSceneIndex(scene: SceneFragment): Int {
        return getTagIndex(scene.tag)
    }

    fun getTagIndex(tag: String?): Int {
        return mSceneTagList.indexOf(tag)
    }

    fun sortSceneViews(views: List<View>?) {
        Collections.sort(views, mSceneViewComparator)
    }

    @JvmOverloads
    fun finishScene(scene: SceneFragment, transitionHelper: TransitionHelper? = null) {
        finishScene(scene.tag, transitionHelper)
    }

    private fun finishScene(tag: String?, transitionHelper: TransitionHelper?) {
        val fragmentManager = supportFragmentManager

        // Get scene
        val scene = fragmentManager.findFragmentByTag(tag)
        if (scene == null) {
            Log.e(TAG, "finishScene: Can't find scene by tag: $tag")
            return
        }

        // Get scene index
        val index = mSceneTagList.indexOf(tag)
        if (index < 0) {
            Log.e(TAG, "finishScene: Can't find the tag in tag list: $tag")
            return
        }
        if (mSceneTagList.size == 1) {
            // It is the last fragment, finish Activity now
            Log.i(TAG, "finishScene: It is the last scene, finish activity now")
            finish()
            return
        }
        var next: Fragment? = null
        if (index == mSceneTagList.size - 1) {
            // It is first fragment, show the next one
            next = fragmentManager.findFragmentByTag(mSceneTagList[index - 1])
        }
        val transaction = fragmentManager.beginTransaction()
        if (next != null) {
            if (transitionHelper == null || !transitionHelper.onTransition(
                    this, transaction, scene, next
                )
            ) {
                // Clear shared item
                scene.sharedElementEnterTransition = null
                scene.sharedElementReturnTransition = null
                scene.enterTransition = null
                scene.exitTransition = null
                next.sharedElementEnterTransition = null
                next.sharedElementReturnTransition = null
                next.enterTransition = null
                next.exitTransition = null
                // Do not show animate if it is not the first fragment
                transaction.setCustomAnimations(R.anim.scene_close_enter, R.anim.scene_close_exit)
            }
            // Attach fragment
            transaction.attach(next)
        }
        transaction.remove(scene)
        transaction.commitAllowingStateLoss()
        onTransactScene()

        // Remove tag
        mSceneTagList.removeAt(index)

        // Return result
        if (scene is SceneFragment) {
            scene.returnResult(this)
        }
    }

    fun refreshTopScene() {
        val index = mSceneTagList.size - 1
        if (index < 0) {
            return
        }
        val tag = mSceneTagList[index]
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(tag)
        val transaction = fragmentManager.beginTransaction()
        transaction.detach(fragment!!)
        transaction.attach(fragment)
        transaction.commitAllowingStateLoss()
        onTransactScene()
    }

    override fun onBackPressed() {
        val size = mSceneTagList.size
        val tag = mSceneTagList[size - 1]
        val scene: SceneFragment
        val fragment = supportFragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            Log.e(TAG, "onBackPressed: Can't find scene by tag: $tag")
            return
        }
        if (fragment !is SceneFragment) {
            Log.e(TAG, "onBackPressed: The fragment is not SceneFragment")
            return
        }
        scene = fragment
        scene.onBackPressed()
    }

    fun findSceneByTag(tag: String?): SceneFragment? {
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(tag)
        return if (fragment != null) {
            fragment as SceneFragment?
        } else {
            null
        }
    }

    val topSceneClass: Class<*>?
        get() {
            val index = mSceneTagList.size - 1
            if (index < 0) {
                return null
            }
            val tag = mSceneTagList[index]
            val fragment = supportFragmentManager.findFragmentByTag(tag) ?: return null
            return fragment.javaClass
        }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(applicationContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    companion object {
        private val TAG = StageActivity::class.java.simpleName
        const val ACTION_START_SCENE = "start_scene"
        const val KEY_SCENE_NAME = "stage_activity_scene_name"
        const val KEY_SCENE_ARGS = "stage_activity_scene_args"
        private const val KEY_STAGE_ID = "stage_activity_stage_id"
        private const val KEY_SCENE_TAG_LIST = "stage_activity_scene_tag_list"
        private const val KEY_NEXT_ID = "stage_activity_next_id"
        private val sLaunchModeMap: MutableMap<Class<*>, Int> = HashMap()

        @JvmStatic
        fun registerLaunchMode(clazz: Class<*>, @LaunchMode launchMode: Int) {
            if (launchMode != SceneFragment.LAUNCH_MODE_STANDARD
                && launchMode != SceneFragment.LAUNCH_MODE_SINGLE_TOP
                && launchMode != SceneFragment.LAUNCH_MODE_SINGLE_TASK) {
                throw java.lang.IllegalStateException("Invalid launch mode: $launchMode")
            }
            sLaunchModeMap[clazz] = launchMode
        }
    }
}
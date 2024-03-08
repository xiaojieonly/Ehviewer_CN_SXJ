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
package com.hippo.app

import android.R
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.*
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.VectorEnabledTintResources
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.app.TaskStackBuilder.SupportParentable

abstract class AppCompatPreferenceActivity : PreferenceActivity(), AppCompatCallback,
    SupportParentable, ActionBarDrawerToggle.DelegateProvider {
    private var mDelegate: AppCompatDelegate? = null
    private var mThemeId = 0
    private var mResources: Resources? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        val delegate = delegate
        delegate.installViewFactory()
        delegate.onCreate(savedInstanceState)
        if (delegate.applyDayNight() && mThemeId != 0) {
            // If DayNight has been applied, we need to re-apply the theme for
            // the changes to take effect. On API 23+, we should bypass
            // setTheme(), which will no-op if the theme ID is identical to the
            // current theme ID.
            onApplyThemeResource(theme, mThemeId, false)
        }
        super.onCreate(savedInstanceState)
    }

    override fun setTheme(@StyleRes resid: Int) {
        super.setTheme(resid)
        // Keep hold of the theme id so that we can re-set it later if needed
        mThemeId = resid
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        delegate.onPostCreate(savedInstanceState)
    }

    /**
     * Support library version of [android.app.Activity.getActionBar].
     *
     *
     * Retrieve a reference to this activity's ActionBar.
     *
     * @return The Activity's ActionBar, or null if it does not have one.
     */
    val supportActionBar: ActionBar?
        get() = delegate.supportActionBar

    /**
     * Set a [Toolbar][android.widget.Toolbar] to act as the
     * [ActionBar] for this Activity window.
     *
     *
     * When set to a non-null value the [.getActionBar] method will return
     * an [ActionBar] object that can be used to control the given
     * toolbar as if it were a traditional window decor action bar. The toolbar's menu will be
     * populated with the Activity's options menu and the navigation button will be wired through
     * the standard [home][android.R.id.home] menu select action.
     *
     *
     * In order to use a Toolbar within the Activity's window content the application
     * must not request the window feature
     * [FEATURE_SUPPORT_ACTION_BAR][Window.FEATURE_ACTION_BAR].
     *
     * @param toolbar Toolbar to set as the Activity's action bar, or `null` to clear it
     */
    fun setSupportActionBar(toolbar: Toolbar?) {
        delegate.setSupportActionBar(toolbar)
    }

    override fun getMenuInflater(): MenuInflater {
        return delegate.menuInflater
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        delegate.setContentView(layoutResID)
    }

    override fun setContentView(view: View) {
        delegate.setContentView(view)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        delegate.setContentView(view, params)
    }

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        delegate.addContentView(view, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.onConfigurationChanged(newConfig)
        if (mResources != null) {
            // The real (and thus managed) resources object was already updated
            // by ResourcesManager, so pull the current metrics from there.
            val newMetrics = super.getResources().displayMetrics
            mResources!!.updateConfiguration(newConfig, newMetrics)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        delegate.onPostResume()
    }

    override fun onStart() {
        super.onStart()
        delegate.onStart()
    }

    override fun onStop() {
        super.onStop()
        delegate.onStop()
    }

    override fun <T : View?> findViewById(@IdRes id: Int): T? {
        return delegate.findViewById(id)
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        if (super.onMenuItemSelected(featureId, item)) {
            return true
        }
        val ab = supportActionBar
        return if (item.itemId == R.id.home && ab != null && ab.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0) {
            onSupportNavigateUp()
        } else false
    }

    override fun onDestroy() {
        super.onDestroy()
        delegate.onDestroy()
    }

    override fun onTitleChanged(title: CharSequence, color: Int) {
        super.onTitleChanged(title, color)
        delegate.setTitle(title)
    }

    /**
     * Enable extended support library window features.
     *
     *
     * This is a convenience for calling
     * [getWindow().requestFeature()][Window.requestFeature].
     *
     *
     * @param featureId The desired feature as defined in
     * [Window] or [androidx.core.view.WindowCompat].
     * @return Returns true if the requested feature is supported and now enabled.
     *
     * @see android.app.Activity.requestWindowFeature
     *
     * @see Window.requestFeature
     */
    fun supportRequestWindowFeature(featureId: Int): Boolean {
        return delegate.requestWindowFeature(featureId)
    }

    fun supportInvalidateOptionsMenu() {
        delegate.invalidateOptionsMenu()
    }

    override fun invalidateOptionsMenu() {
        delegate.invalidateOptionsMenu()
    }

    /**
     * Notifies the Activity that a support action mode has been started.
     * Activity subclasses overriding this method should call the superclass implementation.
     *
     * @param mode The new action mode.
     */
    @CallSuper
    override fun onSupportActionModeStarted(mode: ActionMode) {
    }

    /**
     * Notifies the activity that a support action mode has finished.
     * Activity subclasses overriding this method should call the superclass implementation.
     *
     * @param mode The action mode that just finished.
     */
    @CallSuper
    override fun onSupportActionModeFinished(mode: ActionMode) {
    }

    /**
     * Called when a support action mode is being started for this window. Gives the
     * callback an opportunity to handle the action mode in its own unique and
     * beautiful way. If this method returns null the system can choose a way
     * to present the mode or choose not to start the mode at all.
     *
     * @param callback Callback to control the lifecycle of this action mode
     * @return The ActionMode that was started, or null if the system should present it
     */
    override fun onWindowStartingSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        return null
    }

    /**
     * Start an action mode.
     *
     * @param callback Callback that will manage lifecycle events for this context mode
     * @return The ContextMode that was started, or null if it was canceled
     */
    fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        return delegate.startSupportActionMode(callback)
    }

    @Deprecated("Progress bars are no longer provided in AppCompat.")
    fun setSupportProgressBarVisibility(visible: Boolean) {
    }

    @Deprecated("Progress bars are no longer provided in AppCompat.")
    fun setSupportProgressBarIndeterminateVisibility(visible: Boolean) {
    }

    @Deprecated("Progress bars are no longer provided in AppCompat.")
    fun setSupportProgressBarIndeterminate(indeterminate: Boolean) {
    }

    @Deprecated("Progress bars are no longer provided in AppCompat.")
    fun setSupportProgress(progress: Int) {
    }

    /**
     * Support version of [.onCreateNavigateUpTaskStack].
     * This method will be called on all platform versions.
     *
     * Define the synthetic task stack that will be generated during Up navigation from
     * a different task.
     *
     *
     * The default implementation of this method adds the parent chain of this activity
     * as specified in the manifest to the supplied [TaskStackBuilder]. Applications
     * may choose to override this method to construct the desired task stack in a different
     * way.
     *
     *
     * This method will be invoked by the default implementation of [.onNavigateUp]
     * if [.shouldUpRecreateTask] returns true when supplied with the intent
     * returned by [.getParentActivityIntent].
     *
     *
     * Applications that wish to supply extra Intent parameters to the parent stack defined
     * by the manifest should override
     * [.onPrepareSupportNavigateUpTaskStack].
     *
     * @param builder An empty TaskStackBuilder - the application should add intents representing
     * the desired task stack
     */
    fun onCreateSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.addParentStack(this)
    }

    /**
     * Support version of [.onPrepareNavigateUpTaskStack].
     * This method will be called on all platform versions.
     *
     * Prepare the synthetic task stack that will be generated during Up navigation
     * from a different task.
     *
     *
     * This method receives the [TaskStackBuilder] with the constructed series of
     * Intents as generated by [.onCreateSupportNavigateUpTaskStack].
     * If any extra data should be added to these intents before launching the new task,
     * the application should override this method and add that data here.
     *
     * @param builder A TaskStackBuilder that has been populated with Intents by
     * onCreateNavigateUpTaskStack.
     */
    fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {}

    /**
     * This method is called whenever the user chooses to navigate Up within your application's
     * activity hierarchy from the action bar.
     *
     *
     * If a parent was specified in the manifest for this activity or an activity-alias to it,
     * default Up navigation will be handled automatically. See
     * [.getSupportParentActivityIntent] for how to specify the parent. If any activity
     * along the parent chain requires extra Intent arguments, the Activity subclass
     * should override the method [.onPrepareSupportNavigateUpTaskStack]
     * to supply those arguments.
     *
     *
     * See [Tasks and
 * Back Stack]({@docRoot}guide/topics/fundamentals/tasks-and-back-stack.html) from the developer guide and
     * [Navigation]({@docRoot}design/patterns/navigation.html) from the design guide
     * for more information about navigating within your app.
     *
     *
     * See the [TaskStackBuilder] class and the Activity methods
     * [.getSupportParentActivityIntent], [.supportShouldUpRecreateTask], and
     * [.supportNavigateUpTo] for help implementing custom Up navigation.
     *
     * @return true if Up navigation completed successfully and this Activity was finished,
     * false otherwise.
     */
    fun onSupportNavigateUp(): Boolean {
        val upIntent = supportParentActivityIntent
        if (upIntent != null) {
            if (supportShouldUpRecreateTask(upIntent)) {
                val b = TaskStackBuilder.create(this)
                onCreateSupportNavigateUpTaskStack(b)
                onPrepareSupportNavigateUpTaskStack(b)
                b.startActivities()
                try {
                    ActivityCompat.finishAffinity(this)
                } catch (e: IllegalStateException) {
                    // This can only happen on 4.1+, when we don't have a parent or a result set.
                    // In that case we should just finish().
                    finish()
                }
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                supportNavigateUpTo(upIntent)
            }
            return true
        }
        return false
    }

    /**
     * Obtain an [Intent] that will launch an explicit target activity
     * specified by sourceActivity's [NavUtils.PARENT_ACTIVITY] &lt;meta-data&gt;
     * element in the application's manifest. If the device is running
     * Jellybean or newer, the android:parentActivityName attribute will be preferred
     * if it is present.
     *
     * @return a new Intent targeting the defined parent activity of sourceActivity
     */
    override fun getSupportParentActivityIntent(): Intent? {
        return NavUtils.getParentActivityIntent(this)
    }

    /**
     * Returns true if sourceActivity should recreate the task when navigating 'up'
     * by using targetIntent.
     *
     *
     * If this method returns false the app can trivially call
     * [.supportNavigateUpTo] using the same parameters to correctly perform
     * up navigation. If this method returns false, the app should synthesize a new task stack
     * by using [TaskStackBuilder] or another similar mechanism to perform up navigation.
     *
     * @param targetIntent An intent representing the target destination for up navigation
     * @return true if navigating up should recreate a new task stack, false if the same task
     * should be used for the destination
     */
    fun supportShouldUpRecreateTask(targetIntent: Intent): Boolean {
        return NavUtils.shouldUpRecreateTask(this, targetIntent)
    }

    /**
     * Navigate from sourceActivity to the activity specified by upIntent, finishing sourceActivity
     * in the process. upIntent will have the flag [Intent.FLAG_ACTIVITY_CLEAR_TOP] set
     * by this method, along with any others required for proper up navigation as outlined
     * in the Android Design Guide.
     *
     *
     * This method should be used when performing up navigation from within the same task
     * as the destination. If up navigation should cross tasks in some cases, see
     * [.supportShouldUpRecreateTask].
     *
     * @param upIntent An intent representing the target destination for up navigation
     */
    fun supportNavigateUpTo(upIntent: Intent) {
        NavUtils.navigateUpTo(this, upIntent)
    }

    override fun getDrawerToggleDelegate(): ActionBarDrawerToggle.Delegate? {
        return delegate.drawerToggleDelegate
    }

    /**
     * {@inheritDoc}
     *
     *
     * Please note: AppCompat uses its own feature id for the action bar:
     * [FEATURE_SUPPORT_ACTION_BAR][AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR].
     */
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Please note: AppCompat uses its own feature id for the action bar:
     * [FEATURE_SUPPORT_ACTION_BAR][AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR].
     */
    override fun onPanelClosed(featureId: Int, menu: Menu) {
        super.onPanelClosed(featureId, menu)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        delegate.onSaveInstanceState(outState)
    }

    /**
     * @return The [AppCompatDelegate] being used by this Activity.
     */
    val delegate: AppCompatDelegate
        get() {
            if (mDelegate == null) {
                mDelegate = AppCompatDelegate.create(this, this)
            }
            return mDelegate as AppCompatDelegate
        }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let support action bars open menus in response to the menu key prioritized over
        // the window handling it
        val keyCode = event.keyCode
        val actionBar = supportActionBar
        return if (keyCode == KeyEvent.KEYCODE_MENU && actionBar != null && actionBar.onMenuKeyEvent(
                event
            )
        ) {
            true
        } else super.dispatchKeyEvent(event)
    }

    @SuppressLint("RestrictedApi")
    override fun getResources(): Resources {
        if (mResources == null && VectorEnabledTintResources.shouldBeUsed()) {
            mResources = VectorEnabledTintResources(this, super.getResources())
        }
        return if (mResources == null) super.getResources() else mResources!!
    }

    /**
     * KeyEvents with non-default modifiers are not dispatched to menu's performShortcut in API 25
     * or lower. Here, we check if the keypress corresponds to a menuitem's shortcut combination
     * and perform the corresponding action.
     */
    private fun performMenuItemShortcut(keycode: Int, event: KeyEvent): Boolean {
        if ((Build.VERSION.SDK_INT < 26 && !event.isCtrlPressed
                    && !KeyEvent.metaStateHasNoModifiers(event.metaState)) && event.repeatCount == 0 && !KeyEvent.isModifierKey(
                event.keyCode
            )
        ) {
            val currentWindow = window
            if (currentWindow != null && currentWindow.decorView != null) {
                val decorView = currentWindow.decorView
                if (decorView.dispatchKeyShortcutEvent(event)) {
                    return true
                }
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (performMenuItemShortcut(keyCode, event)) {
            true
        } else super.onKeyDown(keyCode, event)
    }

    @SuppressLint("RestrictedApi")
    override fun openOptionsMenu() {
        val actionBar = supportActionBar
        if (window.hasFeature(Window.FEATURE_OPTIONS_PANEL)
            && (actionBar == null || !actionBar.openOptionsMenu())
        ) {
            super.openOptionsMenu()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun closeOptionsMenu() {
        val actionBar = supportActionBar
        if (window.hasFeature(Window.FEATURE_OPTIONS_PANEL)
            && (actionBar == null || !actionBar.closeOptionsMenu())
        ) {
            super.closeOptionsMenu()
        }
    }
}
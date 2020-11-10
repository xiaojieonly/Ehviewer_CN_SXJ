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

package com.hippo.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatCallback;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.VectorEnabledTintResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;

public abstract class AppCompatPreferenceActivity extends PreferenceActivity implements AppCompatCallback,
    TaskStackBuilder.SupportParentable, ActionBarDrawerToggle.DelegateProvider {

    private AppCompatDelegate mDelegate;
    private int mThemeId = 0;
    private Resources mResources;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        final AppCompatDelegate delegate = getDelegate();
        delegate.installViewFactory();
        delegate.onCreate(savedInstanceState);
        if (delegate.applyDayNight() && mThemeId != 0) {
            // If DayNight has been applied, we need to re-apply the theme for
            // the changes to take effect. On API 23+, we should bypass
            // setTheme(), which will no-op if the theme ID is identical to the
            // current theme ID.
            if (Build.VERSION.SDK_INT >= 23) {
                onApplyThemeResource(getTheme(), mThemeId, false);
            } else {
                setTheme(mThemeId);
            }
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setTheme(@StyleRes final int resid) {
        super.setTheme(resid);
        // Keep hold of the theme id so that we can re-set it later if needed
        mThemeId = resid;
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    /**
     * Support library version of {@link android.app.Activity#getActionBar}.
     *
     * <p>Retrieve a reference to this activity's ActionBar.
     *
     * @return The Activity's ActionBar, or null if it does not have one.
     */
    @Nullable
    public ActionBar getSupportActionBar() {
        return getDelegate().getSupportActionBar();
    }

    /**
     * Set a {@link android.widget.Toolbar Toolbar} to act as the
     * {@link androidx.appcompat.app.ActionBar} for this Activity window.
     *
     * <p>When set to a non-null value the {@link #getActionBar()} method will return
     * an {@link androidx.appcompat.app.ActionBar} object that can be used to control the given
     * toolbar as if it were a traditional window decor action bar. The toolbar's menu will be
     * populated with the Activity's options menu and the navigation button will be wired through
     * the standard {@link android.R.id#home home} menu select action.</p>
     *
     * <p>In order to use a Toolbar within the Activity's window content the application
     * must not request the window feature
     * {@link android.view.Window#FEATURE_ACTION_BAR FEATURE_SUPPORT_ACTION_BAR}.</p>
     *
     * @param toolbar Toolbar to set as the Activity's action bar, or {@code null} to clear it
     */
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        getDelegate().setSupportActionBar(toolbar);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
        if (mResources != null) {
            // The real (and thus managed) resources object was already updated
            // by ResourcesManager, so pull the current metrics from there.
            final DisplayMetrics newMetrics = super.getResources().getDisplayMetrics();
            mResources.updateConfiguration(newConfig, newMetrics);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getDelegate().onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    @Override
    public <T extends View> T findViewById(@IdRes int id) {
        return getDelegate().findViewById(id);
    }

    @Override
    public final boolean onMenuItemSelected(int featureId, android.view.MenuItem item) {
        if (super.onMenuItemSelected(featureId, item)) {
            return true;
        }

        final ActionBar ab = getSupportActionBar();
        if (item.getItemId() == android.R.id.home && ab != null &&
            (ab.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0) {
            return onSupportNavigateUp();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    /**
     * Enable extended support library window features.
     * <p>
     * This is a convenience for calling
     * {@link android.view.Window#requestFeature getWindow().requestFeature()}.
     * </p>
     *
     * @param featureId The desired feature as defined in
     * {@link android.view.Window} or {@link androidx.core.view.WindowCompat}.
     * @return Returns true if the requested feature is supported and now enabled.
     *
     * @see android.app.Activity#requestWindowFeature
     * @see android.view.Window#requestFeature
     */
    public boolean supportRequestWindowFeature(int featureId) {
        return getDelegate().requestWindowFeature(featureId);
    }

    public void supportInvalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }

    @Override
    public void invalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }

    /**
     * Notifies the Activity that a support action mode has been started.
     * Activity subclasses overriding this method should call the superclass implementation.
     *
     * @param mode The new action mode.
     */
    @Override
    @CallSuper
    public void onSupportActionModeStarted(@NonNull ActionMode mode) {
    }

    /**
     * Notifies the activity that a support action mode has finished.
     * Activity subclasses overriding this method should call the superclass implementation.
     *
     * @param mode The action mode that just finished.
     */
    @Override
    @CallSuper
    public void onSupportActionModeFinished(@NonNull ActionMode mode) {
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
    @Nullable
    @Override
    public ActionMode onWindowStartingSupportActionMode(@NonNull ActionMode.Callback callback) {
        return null;
    }

    /**
     * Start an action mode.
     *
     * @param callback Callback that will manage lifecycle events for this context mode
     * @return The ContextMode that was started, or null if it was canceled
     */
    @Nullable
    public ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback) {
        return getDelegate().startSupportActionMode(callback);
    }

    /**
     * @deprecated Progress bars are no longer provided in AppCompat.
     */
    @Deprecated
    public void setSupportProgressBarVisibility(boolean visible) {
    }

    /**
     * @deprecated Progress bars are no longer provided in AppCompat.
     */
    @Deprecated
    public void setSupportProgressBarIndeterminateVisibility(boolean visible) {
    }

    /**
     * @deprecated Progress bars are no longer provided in AppCompat.
     */
    @Deprecated
    public void setSupportProgressBarIndeterminate(boolean indeterminate) {
    }

    /**
     * @deprecated Progress bars are no longer provided in AppCompat.
     */
    @Deprecated
    public void setSupportProgress(int progress) {
    }

    /**
     * Support version of {@link #onCreateNavigateUpTaskStack(android.app.TaskStackBuilder)}.
     * This method will be called on all platform versions.
     *
     * Define the synthetic task stack that will be generated during Up navigation from
     * a different task.
     *
     * <p>The default implementation of this method adds the parent chain of this activity
     * as specified in the manifest to the supplied {@link androidx.core.app.TaskStackBuilder}. Applications
     * may choose to override this method to construct the desired task stack in a different
     * way.</p>
     *
     * <p>This method will be invoked by the default implementation of {@link #onNavigateUp()}
     * if {@link #shouldUpRecreateTask(android.content.Intent)} returns true when supplied with the intent
     * returned by {@link #getParentActivityIntent()}.</p>
     *
     * <p>Applications that wish to supply extra Intent parameters to the parent stack defined
     * by the manifest should override
     * {@link #onPrepareSupportNavigateUpTaskStack(androidx.core.app.TaskStackBuilder)}.</p>
     *
     * @param builder An empty TaskStackBuilder - the application should add intents representing
     *                the desired task stack
     */
    public void onCreateSupportNavigateUpTaskStack(@NonNull TaskStackBuilder builder) {
        builder.addParentStack(this);
    }

    /**
     * Support version of {@link #onPrepareNavigateUpTaskStack(android.app.TaskStackBuilder)}.
     * This method will be called on all platform versions.
     *
     * Prepare the synthetic task stack that will be generated during Up navigation
     * from a different task.
     *
     * <p>This method receives the {@link androidx.core.app.TaskStackBuilder} with the constructed series of
     * Intents as generated by {@link #onCreateSupportNavigateUpTaskStack(androidx.core.app.TaskStackBuilder)}.
     * If any extra data should be added to these intents before launching the new task,
     * the application should override this method and add that data here.</p>
     *
     * @param builder A TaskStackBuilder that has been populated with Intents by
     *                onCreateNavigateUpTaskStack.
     */
    public void onPrepareSupportNavigateUpTaskStack(@NonNull TaskStackBuilder builder) {
    }

    /**
     * This method is called whenever the user chooses to navigate Up within your application's
     * activity hierarchy from the action bar.
     *
     * <p>If a parent was specified in the manifest for this activity or an activity-alias to it,
     * default Up navigation will be handled automatically. See
     * {@link #getSupportParentActivityIntent()} for how to specify the parent. If any activity
     * along the parent chain requires extra Intent arguments, the Activity subclass
     * should override the method {@link #onPrepareSupportNavigateUpTaskStack(androidx.core.app.TaskStackBuilder)}
     * to supply those arguments.</p>
     *
     * <p>See <a href="{@docRoot}guide/topics/fundamentals/tasks-and-back-stack.html">Tasks and
     * Back Stack</a> from the developer guide and
     * <a href="{@docRoot}design/patterns/navigation.html">Navigation</a> from the design guide
     * for more information about navigating within your app.</p>
     *
     * <p>See the {@link androidx.core.app.TaskStackBuilder} class and the Activity methods
     * {@link #getSupportParentActivityIntent()}, {@link #supportShouldUpRecreateTask(android.content.Intent)}, and
     * {@link #supportNavigateUpTo(android.content.Intent)} for help implementing custom Up navigation.</p>
     *
     * @return true if Up navigation completed successfully and this Activity was finished,
     *         false otherwise.
     */
    public boolean onSupportNavigateUp() {
        Intent upIntent = getSupportParentActivityIntent();

        if (upIntent != null) {
            if (supportShouldUpRecreateTask(upIntent)) {
                TaskStackBuilder b = TaskStackBuilder.create(this);
                onCreateSupportNavigateUpTaskStack(b);
                onPrepareSupportNavigateUpTaskStack(b);
                b.startActivities();

                try {
                    ActivityCompat.finishAffinity(this);
                } catch (IllegalStateException e) {
                    // This can only happen on 4.1+, when we don't have a parent or a result set.
                    // In that case we should just finish().
                    finish();
                }
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                supportNavigateUpTo(upIntent);
            }
            return true;
        }
        return false;
    }

    /**
     * Obtain an {@link android.content.Intent} that will launch an explicit target activity
     * specified by sourceActivity's {@link androidx.core.app.NavUtils#PARENT_ACTIVITY} &lt;meta-data&gt;
     * element in the application's manifest. If the device is running
     * Jellybean or newer, the android:parentActivityName attribute will be preferred
     * if it is present.
     *
     * @return a new Intent targeting the defined parent activity of sourceActivity
     */
    @Nullable
    @Override
    public Intent getSupportParentActivityIntent() {
        return NavUtils.getParentActivityIntent(this);
    }

    /**
     * Returns true if sourceActivity should recreate the task when navigating 'up'
     * by using targetIntent.
     *
     * <p>If this method returns false the app can trivially call
     * {@link #supportNavigateUpTo(android.content.Intent)} using the same parameters to correctly perform
     * up navigation. If this method returns false, the app should synthesize a new task stack
     * by using {@link androidx.core.app.TaskStackBuilder} or another similar mechanism to perform up navigation.</p>
     *
     * @param targetIntent An intent representing the target destination for up navigation
     * @return true if navigating up should recreate a new task stack, false if the same task
     *         should be used for the destination
     */
    public boolean supportShouldUpRecreateTask(@NonNull Intent targetIntent) {
        return NavUtils.shouldUpRecreateTask(this, targetIntent);
    }

    /**
     * Navigate from sourceActivity to the activity specified by upIntent, finishing sourceActivity
     * in the process. upIntent will have the flag {@link android.content.Intent#FLAG_ACTIVITY_CLEAR_TOP} set
     * by this method, along with any others required for proper up navigation as outlined
     * in the Android Design Guide.
     *
     * <p>This method should be used when performing up navigation from within the same task
     * as the destination. If up navigation should cross tasks in some cases, see
     * {@link #supportShouldUpRecreateTask(android.content.Intent)}.</p>
     *
     * @param upIntent An intent representing the target destination for up navigation
     */
    public void supportNavigateUpTo(@NonNull Intent upIntent) {
        NavUtils.navigateUpTo(this, upIntent);
    }

    @Nullable
    @Override
    public ActionBarDrawerToggle.Delegate getDrawerToggleDelegate() {
        return getDelegate().getDrawerToggleDelegate();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Please note: AppCompat uses its own feature id for the action bar:
     * {@link AppCompatDelegate#FEATURE_SUPPORT_ACTION_BAR FEATURE_SUPPORT_ACTION_BAR}.</p>
     */
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        return super.onMenuOpened(featureId, menu);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Please note: AppCompat uses its own feature id for the action bar:
     * {@link AppCompatDelegate#FEATURE_SUPPORT_ACTION_BAR FEATURE_SUPPORT_ACTION_BAR}.</p>
     */
    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        super.onPanelClosed(featureId, menu);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getDelegate().onSaveInstanceState(outState);
    }

    /**
     * @return The {@link AppCompatDelegate} being used by this Activity.
     */
    @NonNull
    public AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, this);
        }
        return mDelegate;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let support action bars open menus in response to the menu key prioritized over
        // the window handling it
        final int keyCode = event.getKeyCode();
        final ActionBar actionBar = getSupportActionBar();
        if (keyCode == KeyEvent.KEYCODE_MENU
            && actionBar != null && actionBar.onMenuKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public Resources getResources() {
        if (mResources == null && VectorEnabledTintResources.shouldBeUsed()) {
            mResources = new VectorEnabledTintResources(this, super.getResources());
        }
        return mResources == null ? super.getResources() : mResources;
    }

    /**
     * KeyEvents with non-default modifiers are not dispatched to menu's performShortcut in API 25
     * or lower. Here, we check if the keypress corresponds to a menuitem's shortcut combination
     * and perform the corresponding action.
     */
    private boolean performMenuItemShortcut(int keycode, KeyEvent event) {
        if (!(Build.VERSION.SDK_INT >= 26) && !event.isCtrlPressed()
            && !KeyEvent.metaStateHasNoModifiers(event.getMetaState())
            && event.getRepeatCount() == 0
            && !KeyEvent.isModifierKey(event.getKeyCode())) {
            final Window currentWindow = getWindow();
            if (currentWindow != null && currentWindow.getDecorView() != null) {
                final View decorView = currentWindow.getDecorView();
                if (decorView.dispatchKeyShortcutEvent(event)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (performMenuItemShortcut(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void openOptionsMenu() {
        ActionBar actionBar = getSupportActionBar();
        if (getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL)
            && (actionBar == null || !actionBar.openOptionsMenu())) {
            super.openOptionsMenu();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void closeOptionsMenu() {
        ActionBar actionBar = getSupportActionBar();
        if (getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL)
            && (actionBar == null || !actionBar.closeOptionsMenu())) {
            super.closeOptionsMenu();
        }
    }
}

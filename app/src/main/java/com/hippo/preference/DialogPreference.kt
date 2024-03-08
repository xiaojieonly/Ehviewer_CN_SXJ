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
package com.hippo.preference

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.preference.Preference
import android.preference.PreferenceManager.OnActivityDestroyListener
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.hippo.ehviewer.R

/**
 * A base class for [Preference] objects that are
 * dialog-based. These preferences will, when clicked, open a dialog showing the
 * actual preference controls.
 */
abstract class DialogPreference : Preference, DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener, OnActivityDestroyListener {
    private var mBuilder: AlertDialog.Builder? = null
    /**
     * Returns the title to be shown on subsequent dialogs.
     * @return The title.
     */
    /**
     * Sets the title of the dialog. This will be shown on subsequent dialogs.
     *
     * @param dialogTitle The title.
     */
    var dialogTitle: CharSequence? = null
    /**
     * Returns the icon to be shown on subsequent dialogs.
     * @return The icon, as a [Drawable].
     */
    /**
     * Sets the icon of the dialog. This will be shown on subsequent dialogs.
     *
     * @param dialogIcon The icon, as a [Drawable].
     */
    var dialogIcon: Drawable? = null
    /**
     * Returns the text of the positive button to be shown on subsequent
     * dialogs.
     *
     * @return The text of the positive button.
     */
    /**
     * Sets the text of the positive button of the dialog. This will be shown on
     * subsequent dialogs.
     *
     * @param positiveButtonText The text of the positive button.
     */
    var positiveButtonText: CharSequence? = null
    /**
     * Returns the text of the negative button to be shown on subsequent
     * dialogs.
     *
     * @return The text of the negative button.
     */
    /**
     * Sets the text of the negative button of the dialog. This will be shown on
     * subsequent dialogs.
     *
     * @param negativeButtonText The text of the negative button.
     */
    var negativeButtonText: CharSequence? = null
    /**
     * Returns the layout resource that is used as the content View for
     * subsequent dialogs.
     *
     * @return The layout resource.
     */
    /**
     * Sets the layout resource that is inflated as the [View] to be shown
     * as the content View of subsequent dialogs.
     *
     * @param dialogLayoutResId The layout resource ID to be inflated.
     */
    var dialogLayoutResource = 0

    /** The dialog, if it is showing.  */
    private var mDialog: AlertDialog? = null

    /** Which button was clicked.  */
    private var mWhichButtonClicked = 0

    constructor(context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr, 0)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.DialogPreference,
            defStyleAttr,
            defStyleRes
        )
        dialogTitle = a.getString(R.styleable.DialogPreference_dialogTitle)
        if (dialogTitle == null) {
            // Fallback on the regular title of the preference
            // (the one that is seen in the list)
            dialogTitle = title
        }
        dialogIcon = a.getDrawable(R.styleable.DialogPreference_dialogIcon)
        positiveButtonText = a.getString(R.styleable.DialogPreference_positiveButtonText)
        negativeButtonText = a.getString(R.styleable.DialogPreference_negativeButtonText)
        dialogLayoutResource =
            a.getResourceId(R.styleable.DialogPreference_dialogLayout, dialogLayoutResource)
        a.recycle()
    }

    /**
     * @see .setDialogTitle
     * @param dialogTitleResId The dialog title as a resource.
     */
    fun setDialogTitle(dialogTitleResId: Int) {
        dialogTitle = context.getString(dialogTitleResId)
    }

    /**
     * Sets the icon (resource ID) of the dialog. This will be shown on
     * subsequent dialogs.
     *
     * @param dialogIconRes The icon, as a resource ID.
     */
    fun setDialogIcon(@DrawableRes dialogIconRes: Int) {
        dialogIcon = ContextCompat.getDrawable(context, dialogIconRes)
    }

    /**
     * @see .setPositiveButtonText
     * @param positiveButtonTextResId The positive button text as a resource.
     */
    fun setPositiveButtonText(@StringRes positiveButtonTextResId: Int) {
        positiveButtonText = context.getString(positiveButtonTextResId)
    }

    /**
     * @see .setNegativeButtonText
     * @param negativeButtonTextResId The negative button text as a resource.
     */
    fun setNegativeButtonText(@StringRes negativeButtonTextResId: Int) {
        negativeButtonText = context.getString(negativeButtonTextResId)
    }

    /**
     * Prepares the dialog builder to be shown when the preference is clicked.
     * Use this to set custom properties on the dialog.
     *
     *
     * Do not [AlertDialog.Builder.create] or
     * [AlertDialog.Builder.show].
     */
    protected open fun onPrepareDialogBuilder(builder: AlertDialog.Builder?) {}
    override fun onClick() {
        if (mDialog != null && mDialog!!.isShowing) return
        showDialog(null)
    }

    /**
     * Shows the dialog associated with this Preference. This is normally initiated
     * automatically on clicking on the preference. Call this method if you need to
     * show the dialog on some other event.
     *
     * @param state Optional instance state to restore on the dialog
     */
    protected fun showDialog(state: Bundle?) {
        val context = getContext()
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        mBuilder = AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setIcon(dialogIcon)
            .setPositiveButton(positiveButtonText, this)
            .setNegativeButton(negativeButtonText, this)
        val contentView = onCreateDialogView()
        if (contentView != null) {
            onBindDialogView(contentView)
            mBuilder!!.setView(contentView)
        }
        onPrepareDialogBuilder(mBuilder)
        PreferenceUtils.registerOnActivityDestroyListener(this, this)

        // Create the dialog
        mDialog = mBuilder!!.create()
        val dialog = mDialog!!
        if (state != null) {
            dialog.onRestoreInstanceState(state)
        }
        if (needInputMethod()) {
            requestInputMethod(dialog)
        }
        dialog.setOnDismissListener(this)
        dialog.show()
        onDialogCreated(dialog)
    }

    /**
     * Returns whether the preference needs to display a soft input method when the dialog
     * is displayed. Default is false. Subclasses should override this method if they need
     * the soft input method brought up automatically.
     */
    protected fun needInputMethod(): Boolean {
        return false
    }

    /**
     * Sets the required flags on the dialog window to enable input method window to show up.
     */
    private fun requestInputMethod(dialog: Dialog) {
        val window = dialog.window
        window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    /**
     * Creates the content view for the dialog (if a custom content view is
     * required). By default, it inflates the dialog layout resource if it is
     * set.
     *
     * @return The content View for the dialog.
     * @see .setLayoutResource
     */
    protected fun onCreateDialogView(): View? {
        if (dialogLayoutResource == 0) {
            return null
        }
        val inflater = LayoutInflater.from(mBuilder!!.context)
        return inflater.inflate(dialogLayoutResource, null)
    }

    /**
     * Binds views in the content View of the dialog to data.
     *
     * @param view The content View of the dialog, if it is custom.
     */
    protected fun onBindDialogView(view: View?) {}
    protected open fun onDialogCreated(dialog: AlertDialog?) {}
    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        PreferenceUtils.unregisterOnActivityDestroyListener(this, this)
        mDialog = null
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
    }

    /**
     * Called when the dialog is dismissed and should be used to save data to
     * the [SharedPreferences].
     *
     * @param positiveResult Whether the positive button was clicked (true), or
     * the negative button was clicked or the dialog was canceled (false).
     */
    protected open fun onDialogClosed(positiveResult: Boolean) {}

    /**
     * Gets the dialog that is shown by this preference.
     *
     * @return The dialog, or null if a dialog is not being shown.
     */
    val dialog: Dialog?
        get() = mDialog

    override fun onActivityDestroy() {
        if (mDialog == null || !mDialog!!.isShowing) {
            return
        }
        mDialog!!.dismiss()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (mDialog == null || !mDialog!!.isShowing) {
            return superState
        }
        val myState = SavedState(superState)
        myState.isDialogShowing = true
        myState.dialogBundle = mDialog!!.onSaveInstanceState()
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state == null || state.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }
        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        if (myState.isDialogShowing) {
            showDialog(myState.dialogBundle)
        }
    }

    private class SavedState : BaseSavedState {
        var isDialogShowing = false
        var dialogBundle: Bundle? = null

        constructor(source: Parcel) : super(source) {
            isDialogShowing = source.readInt() == 1
            dialogBundle = source.readBundle(DialogPreference::class.java.classLoader)
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(if (isDialogShowing) 1 else 0)
            dest.writeBundle(dialogBundle)
        }

        constructor(superState: Parcelable?) : super(superState) {}

        @JvmField
        val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
            override fun createFromParcel(`in`: Parcel): SavedState {
                return SavedState(`in`)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}
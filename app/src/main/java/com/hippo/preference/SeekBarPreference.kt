/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.hippo.preference

import android.content.Context
import android.content.res.TypedArray
import android.os.Parcel
import android.os.Parcelable
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.hippo.ehviewer.R

class SeekBarPreference : Preference, OnSeekBarChangeListener {
    private var mProgress = 0
    private var mMax = 0
    private var mTrackingTouch = false

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
            R.styleable.SeekBarPreference,
            defStyleAttr,
            defStyleRes
        )
        setMax(a.getInt(R.styleable.SeekBarPreference_max, mMax))
        a.recycle()
        layoutResource = R.layout.preference_widget_seekbar
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        val seekBar = view.findViewById<View>(R.id.seekbar) as SeekBar
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.max = mMax
        seekBar.progress = mProgress
        seekBar.isEnabled = isEnabled
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any) {
        progress = if (restoreValue) getPersistedInt(mProgress) else (defaultValue as Int)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInt(index, 0)
    }

    fun setMax(max: Int) {
        if (max != mMax) {
            mMax = max
            notifyChanged()
        }
    }

    private fun setProgress(progress: Int, notifyChanged: Boolean) {
        var progress = progress
        if (progress > mMax) {
            progress = mMax
        }
        if (progress < 0) {
            progress = 0
        }
        if (progress != mProgress) {
            mProgress = progress
            persistInt(progress)
            if (notifyChanged) {
                notifyChanged()
            }
        }
    }

    var progress: Int
        get() = mProgress
        set(progress) {
            setProgress(progress, true)
        }

    /**
     * Persist the seekBar's progress value if callChangeListener
     * returns true, otherwise set the seekBar's progress to the stored value
     */
    fun syncProgress(seekBar: SeekBar) {
        val progress = seekBar.progress
        if (progress != mProgress) {
            if (callChangeListener(progress)) {
                setProgress(progress, false)
            } else {
                seekBar.progress = mProgress
            }
        }
    }

    override fun onProgressChanged(
        seekBar: SeekBar, progress: Int, fromUser: Boolean
    ) {
        if (fromUser && !mTrackingTouch) {
            syncProgress(seekBar)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        mTrackingTouch = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        mTrackingTouch = false
        if (seekBar.progress != mProgress) {
            syncProgress(seekBar)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState
        }

        // Save the instance state
        val myState = SavedState(superState)
        myState.progress = mProgress
        myState.max = mMax
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }

        // Restore the instance state
        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        mProgress = myState.progress
        mMax = myState.max
        notifyChanged()
    }

    /**
     * SavedState, a subclass of [BaseSavedState], will store the state
     * of MyPreference, a subclass of Preference.
     *
     *
     * It is important to always call through to super methods.
     */
    private class SavedState : BaseSavedState {
        var progress = 0
        var max = 0

        constructor(source: Parcel) : super(source) {

            // Restore the click counter
            progress = source.readInt()
            max = source.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)

            // Save the click counter
            dest.writeInt(progress)
            dest.writeInt(max)
        }

        constructor(superState: Parcelable?) : super(superState) {}

        companion object {
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
}
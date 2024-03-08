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

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R
import java.text.NumberFormat

/**
 *
 * A dialog showing a progress indicator and an optional text message or view.
 * Only a text message or a view can be used at the same time.
 *
 * The dialog can be made cancelable on back key press.
 *
 * The progress range is 0..10000.
 */
class ProgressDialog : AlertDialog {
    private var mProgress: ProgressBar? = null
    private var mMessageView: TextView? = null
    private var mProgressStyle = STYLE_SPINNER
    private var mProgressNumber: TextView? = null
    private var mProgressNumberFormat: String? = null
    private var mProgressPercent: TextView? = null
    private var mProgressPercentFormat: NumberFormat? = null
    private var mMax = 0
    private var mProgressVal = 0
    private var mSecondaryProgressVal = 0
    private var mIncrementBy = 0
    private var mIncrementSecondaryBy = 0
    private var mProgressDrawable: Drawable? = null
    private var mIndeterminateDrawable: Drawable? = null
    private var mMessage: CharSequence? = null
    private var mIndeterminate = false
    private var mHasStarted = false
    private var mViewUpdateHandler: Handler? = null

    constructor(context: Context?) : super(context!!) {
        initFormats()
    }

    constructor(context: Context?, theme: Int) : super(context!!, theme) {
        initFormats()
    }

    private fun initFormats() {
        mProgressNumberFormat = "%1d/%2d"
        mProgressPercentFormat = NumberFormat.getPercentInstance()
        mProgressPercentFormat!!.maximumFractionDigits = 0
    }

    override fun onCreate(savedInstanceState: Bundle) {
        val inflater = LayoutInflater.from(context)
        if (mProgressStyle == STYLE_HORIZONTAL) {

            /* Use a separate handler to update the text views as they
             * must be updated on the same thread that created them.
             */
            mViewUpdateHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    super.handleMessage(msg)

                    /* Update the number and percent */
                    val progress = mProgress!!.progress
                    val max = mProgress!!.max
                    if (mProgressNumberFormat != null) {
                        val format: String = mProgressNumberFormat.toString()
                        mProgressNumber!!.text = String.format(format, progress, max)
                    } else {
                        mProgressNumber!!.text = ""
                    }
                    if (mProgressPercentFormat != null) {
                        val percent = progress.toDouble() / max.toDouble()
                        val tmp = SpannableString(mProgressPercentFormat!!.format(percent))
                        tmp.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0, tmp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        mProgressPercent!!.text = tmp
                    } else {
                        mProgressPercent!!.text = ""
                    }
                }
            }
            val view = inflater.inflate(R.layout.alert_dialog_progress_material, null)
            mProgress = view.findViewById<View>(R.id.progress) as ProgressBar
            mProgressNumber = view.findViewById<View>(R.id.progress_number) as TextView
            mProgressPercent = view.findViewById<View>(R.id.progress_percent) as TextView
            setView(view)
        } else {
            val view = inflater.inflate(R.layout.progress_dialog_material, null)
            mProgress = view.findViewById<View>(R.id.progress) as ProgressBar
            mMessageView = view.findViewById<View>(R.id.message) as TextView
            setView(view)
        }
        if (mMax > 0) {
            max = mMax
        }
        if (mProgressVal > 0) {
            progress = mProgressVal
        }
        if (mSecondaryProgressVal > 0) {
            secondaryProgress = mSecondaryProgressVal
        }
        if (mIncrementBy > 0) {
            incrementProgressBy(mIncrementBy)
        }
        if (mIncrementSecondaryBy > 0) {
            incrementSecondaryProgressBy(mIncrementSecondaryBy)
        }
        if (mProgressDrawable != null) {
            setProgressDrawable(mProgressDrawable)
        }
        if (mIndeterminateDrawable != null) {
            setIndeterminateDrawable(mIndeterminateDrawable)
        }
        if (mMessage != null) {
            setMessage(mMessage!!)
        }
        isIndeterminate = mIndeterminate
        onProgressChanged()
        super.onCreate(savedInstanceState)
    }

    public override fun onStart() {
        super.onStart()
        mHasStarted = true
    }

    override fun onStop() {
        super.onStop()
        mHasStarted = false
    }

    var progress: Int
        get() = if (mProgress != null) {
            mProgress!!.progress
        } else mProgressVal
        set(value) {
            if (mHasStarted) {
                mProgress!!.progress = value
                onProgressChanged()
            } else {
                mProgressVal = value
            }
        }
    var secondaryProgress: Int
        get() = if (mProgress != null) {
            mProgress!!.secondaryProgress
        } else mSecondaryProgressVal
        set(secondaryProgress) {
            if (mProgress != null) {
                mProgress!!.secondaryProgress = secondaryProgress
                onProgressChanged()
            } else {
                mSecondaryProgressVal = secondaryProgress
            }
        }
    var max: Int
        get() = if (mProgress != null) {
            mProgress!!.max
        } else mMax
        set(max) {
            if (mProgress != null) {
                mProgress!!.max = max
                onProgressChanged()
            } else {
                mMax = max
            }
        }

    fun incrementProgressBy(diff: Int) {
        if (mProgress != null) {
            mProgress!!.incrementProgressBy(diff)
            onProgressChanged()
        } else {
            mIncrementBy += diff
        }
    }

    fun incrementSecondaryProgressBy(diff: Int) {
        if (mProgress != null) {
            mProgress!!.incrementSecondaryProgressBy(diff)
            onProgressChanged()
        } else {
            mIncrementSecondaryBy += diff
        }
    }

    fun setProgressDrawable(d: Drawable?) {
        if (mProgress != null) {
            mProgress!!.progressDrawable = d
        } else {
            mProgressDrawable = d
        }
    }

    fun setIndeterminateDrawable(d: Drawable?) {
        if (mProgress != null) {
            mProgress!!.indeterminateDrawable = d
        } else {
            mIndeterminateDrawable = d
        }
    }

    var isIndeterminate: Boolean
        get() = if (mProgress != null) {
            mProgress!!.isIndeterminate
        } else mIndeterminate
        set(indeterminate) {
            if (mProgress != null) {
                mProgress!!.isIndeterminate = indeterminate
            } else {
                mIndeterminate = indeterminate
            }
        }

    override fun setMessage(message: CharSequence) {
        if (mProgress != null) {
            if (mProgressStyle == STYLE_HORIZONTAL) {
                super.setMessage(message)
            } else {
                mMessageView!!.text = message
            }
        } else {
            mMessage = message
        }
    }

    fun setProgressStyle(style: Int) {
        mProgressStyle = style
    }

    /**
     * Change the format of the small text showing current and maximum units
     * of progress.  The default is "%1d/%2d".
     * Should not be called during the number is progressing.
     * @param format A string passed to [String.format()][String.format];
     * use "%1d" for the current number and "%2d" for the maximum.  If null,
     * nothing will be shown.
     */
    fun setProgressNumberFormat(format: String?) {
        mProgressNumberFormat = format
        onProgressChanged()
    }

    /**
     * Change the format of the small text showing the percentage of progress.
     * The default is
     * [NumberFormat.getPercentageInstnace().][NumberFormat.getPercentInstance]
     * Should not be called during the number is progressing.
     * @param format An instance of a [NumberFormat] to generate the
     * percentage text.  If null, nothing will be shown.
     */
    fun setProgressPercentFormat(format: NumberFormat?) {
        mProgressPercentFormat = format
        onProgressChanged()
    }

    private fun onProgressChanged() {
        if (mProgressStyle == STYLE_HORIZONTAL) {
            if (mViewUpdateHandler != null && !mViewUpdateHandler!!.hasMessages(0)) {
                mViewUpdateHandler!!.sendEmptyMessage(0)
            }
        }
    }

    companion object {
        /** Creates a ProgressDialog with a circular, spinning progress
         * bar. This is the default.
         */
        const val STYLE_SPINNER = 0

        /** Creates a ProgressDialog with a horizontal progress bar.
         */
        const val STYLE_HORIZONTAL = 1
        @JvmOverloads
        fun show(
            context: Context?, title: CharSequence?,
            message: CharSequence, indeterminate: Boolean = false,
            cancelable: Boolean = false, cancelListener: DialogInterface.OnCancelListener? = null
        ): ProgressDialog {
            val dialog = ProgressDialog(context)
            dialog.setTitle(title)
            dialog.setMessage(message)
            dialog.isIndeterminate = indeterminate
            dialog.setCancelable(cancelable)
            dialog.setOnCancelListener(cancelListener)
            dialog.show()
            return dialog
        }
    }
}
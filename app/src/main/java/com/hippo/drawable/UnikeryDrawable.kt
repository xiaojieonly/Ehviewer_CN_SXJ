/*
 * Copyright 2015 Hippo Seven
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
package com.hippo.drawable

import android.graphics.drawable.Drawable
import android.util.Log
import com.hippo.conaco.Conaco
import com.hippo.conaco.ConacoTask
import com.hippo.conaco.Unikery
import com.hippo.image.ImageBitmap
import com.hippo.image.ImageDrawable
import com.hippo.image.RecycledException
import com.hippo.widget.ObservedTextView

class UnikeryDrawable(private val mTextView: ObservedTextView, conaco: Conaco<ImageBitmap>) :
    WrapDrawable(), Unikery<ImageBitmap>, ObservedTextView.OnWindowAttachListener {
    private var mTaskId = Unikery.INVALID_ID
    private val mConaco: Conaco<ImageBitmap>
    private var mUrl: String? = null

    init {
        mTextView.setOnWindowAttachListener(this)
        mConaco = conaco
    }

    override fun onAttachedToWindow() {
        load(mUrl)
    }

    override fun onDetachedFromWindow() {
        mConaco.cancel(this)
        clearDrawable()
    }

    fun load(url: String?) {
        if (url != null) {
            mUrl = url
            mConaco.load(ConacoTask.Builder<ImageBitmap>().setUnikery(this).setUrl(url).setKey(url))
            //            ConacoTask.Builder<ImageBitmap> builder =new ConacoTask.Builder<>();
//            builder.url = url;
//            builder.unikery = this;
//            builder.key = url;
//            mConaco.load(builder);
        }
    }

    private fun clearDrawable() {
        val drawable = getDrawable()
        if (drawable is ImageDrawable) {
            drawable.recycle()
        }
        setDrawable(null)
    }

    override fun setDrawable(drawable: Drawable?) {
        // Remove old callback
        val oldDrawable = getDrawable()
        if (oldDrawable != null) {
            oldDrawable.callback = null
        }
        super.setDrawable(drawable)
        if (drawable != null) {
            drawable.callback = mTextView
        }
        updateBounds()
        if (drawable != null) {
            invalidateSelf()
        }
    }

    override fun setTaskId(id: Int) {
        mTaskId = id
    }

    override fun getTaskId(): Int {
        return mTaskId
    }

    override fun invalidateSelf() {
        val cs = mTextView.text
        mTextView.text = cs
    }

    override fun onMiss(source: Int) {}
    override fun onRequest() {}

    //    @Override
    //    public void onRequest() {}
    override fun onProgress(singleReceivedSize: Long, receivedSize: Long, totalSize: Long) {}
    override fun onWait() {}
    override fun onGetValue(value: ImageBitmap, source: Int): Boolean {
        val drawable: ImageDrawable = try {
            ImageDrawable(value)
        } catch (e: RecycledException) {
            Log.d(TAG, "The ImageBitmap is recycled", e)
            return false
        }
        clearDrawable()
        setDrawable(drawable)
        drawable.start()
        return true
    }

    override fun onFailure() {
        // Empty
    }

    override fun onCancel() {
        // Empty
    }

    companion object {
        private val TAG = UnikeryDrawable::class.java.simpleName
    }
}
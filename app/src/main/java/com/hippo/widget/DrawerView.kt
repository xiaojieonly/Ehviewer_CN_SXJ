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
package com.hippo.widget

import android.R
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.hippo.lib.yorozuya.LayoutUtils
import kotlin.math.min

open class DrawerView : FrameLayout {
    private var mMaxWidth = 0

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, SIZE_ATTRS)
        mMaxWidth =
            a.getDimensionPixelOffset(0, LayoutUtils.dp2pix(context, DEFAULT_MAX_WIDTH.toFloat()))
        a.recycle()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var widthSpec = widthSpec
        when (MeasureSpec.getMode(widthSpec)) {
            MeasureSpec.EXACTLY -> {}
            MeasureSpec.AT_MOST -> widthSpec = MeasureSpec.makeMeasureSpec(
                min(MeasureSpec.getSize(widthSpec), mMaxWidth), MeasureSpec.EXACTLY
            )

            MeasureSpec.UNSPECIFIED -> widthSpec =
                MeasureSpec.makeMeasureSpec(mMaxWidth, MeasureSpec.EXACTLY)
        }
        // Let super sort out the height
        super.onMeasure(widthSpec, heightSpec)
    }

    companion object {
        private const val DEFAULT_MAX_WIDTH = 280

        private val SIZE_ATTRS = intArrayOf(
            R.attr.maxWidth
        )
    }
}

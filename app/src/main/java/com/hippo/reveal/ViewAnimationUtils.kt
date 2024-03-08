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
package com.hippo.reveal

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import com.hippo.yorozuya.SimpleAnimatorListener

object ViewAnimationUtils {
    const val API_SUPPORT_CIRCULAR_REVEAL = true

    // http://developer.android.com/guide/topics/graphics/hardware-accel.html#unsupported
    const val API_SUPPORT_CANVAS_CLIP_PATH = true
    @JvmStatic
    fun createCircularReveal(
        view: View?,
        centerX: Int, centerY: Int, startRadius: Float, endRadius: Float
    ): Animator {
        return if (API_SUPPORT_CIRCULAR_REVEAL) {
            ViewAnimationUtils.createCircularReveal(
                view, centerX, centerY, startRadius, endRadius
            )
        } else if (view is Reveal) {
            createRevealAnimator(
                view as Reveal, centerX, centerY,
                startRadius, endRadius
            )
        } else {
            throw IllegalStateException(
                "Only View implements CircularReveal or" +
                        " api >= 21 can create circular reveal"
            )
        }
    }

    private fun createRevealAnimator(
        reveal: Reveal, centerX: Int, centerY: Int,
        startRadius: Float, endRadius: Float
    ): Animator {
        val animator = ValueAnimator.ofFloat(startRadius, endRadius)
        animator.addUpdateListener(RevealAnimatorUpdateListener(reveal, centerX, centerY))
        animator.addListener(RevealAnimatorListener(reveal))
        return animator
    }

    private class RevealAnimatorUpdateListener(
        private val mReveal: Reveal,
        private val mCenterX: Int,
        private val mCenterY: Int
    ) : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val value = animation.animatedValue
            if (value is Float) {
                mReveal.setReveal(mCenterX, mCenterY, value)
            }
        }
    }

    private class RevealAnimatorListener(private val mReveal: Reveal) : SimpleAnimatorListener() {
        private val mView: View = mReveal as View

        override fun onAnimationStart(animation: Animator) {
            if (!API_SUPPORT_CANVAS_CLIP_PATH) {
                mView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            mReveal.setRevealEnable(true)
        }

        override fun onAnimationEnd(animation: Animator) {
            mReveal.setRevealEnable(false)
            if (!API_SUPPORT_CANVAS_CLIP_PATH) {
                mView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }
}
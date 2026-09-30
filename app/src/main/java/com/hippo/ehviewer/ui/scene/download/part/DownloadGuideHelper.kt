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
package com.hippo.ehviewer.ui.scene.download.part

import android.graphics.Point
import android.view.Gravity
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import com.github.amlcurran.showcaseview.ShowcaseView
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener
import com.github.amlcurran.showcaseview.targets.PointTarget
import com.github.amlcurran.showcaseview.targets.ViewTarget
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.scene.download.part.DownloadAdapter.DownloadHolder
import com.hippo.ehviewer.widget.MyEasyRecyclerView
import com.hippo.lib.yorozuya.ViewUtils
import com.hippo.widget.recyclerview.AutoStaggeredGridLayoutManager

/**
 * 下载页新手引导。
 */
class DownloadGuideHelper(private val mHost: Host) {
    interface Host {
        val activity2: MainActivity?

        val recyclerView: MyEasyRecyclerView?

        val layoutManager: AutoStaggeredGridLayoutManager?

        fun openDrawer(gravity: Int)
    }

    private var mShowcaseView: ShowcaseView? = null

    val isShowing: Boolean
        get() = mShowcaseView != null

    fun destroy() {
        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView)
            mShowcaseView = null
        }
    }

    fun guide() {
        val recyclerView = mHost.recyclerView
        if (Settings.getGuideDownloadThumb() && null != recyclerView) {
            recyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (Settings.getGuideDownloadThumb()) {
                            guideDownloadThumb()
                        }
                        val current = mHost.recyclerView
                        if (null != current) {
                            ViewUtils.removeOnGlobalLayoutListener(
                                current.getViewTreeObserver(),
                                this
                            )
                        }
                    }
                })
        } else {
            guideDownloadLabels()
        }
    }

    private fun guideDownloadThumb() {
        val activity = mHost.activity2
        val layoutManager = mHost.layoutManager
        val recyclerView = mHost.recyclerView
        if (null == activity || !Settings.getGuideDownloadThumb() || null == layoutManager || null == recyclerView) {
            guideDownloadLabels()
            return
        }
        val position = layoutManager.findFirstCompletelyVisibleItemPositions(null)[0]
        if (position < 0) {
            guideDownloadLabels()
            return
        }
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        if (null == holder) {
            guideDownloadLabels()
            return
        }

        mShowcaseView = ShowcaseView.Builder(activity)
            .withMaterialShowcase()
            .setStyle(R.style.Guide)
            .setTarget(ViewTarget((holder as DownloadHolder).thumb))
            .blockAllTouches()
            .setContentTitle(R.string.guide_download_thumb_title)
            .setContentText(R.string.guide_download_thumb_text)
            .replaceEndButton(R.layout.button_guide)
            .setShowcaseEventListener(object : SimpleShowcaseEventListener() {
                override fun onShowcaseViewDidHide(showcaseView: ShowcaseView) {
                    mShowcaseView = null
                    ViewUtils.removeFromParent(showcaseView)
                    Settings.putGuideDownloadThumb(false)
                    guideDownloadLabels()
                }
            }).build()
    }

    private fun guideDownloadLabels() {
        val activity = mHost.activity2
        if (null == activity || !Settings.getGuideDownloadLabels()) {
            return
        }

        val display = activity.windowManager.getDefaultDisplay()
        val point = Point()
        display.getSize(point)

        mShowcaseView = ShowcaseView.Builder(activity)
            .withMaterialShowcase()
            .setStyle(R.style.Guide)
            .setTarget(PointTarget(point.x, point.y / 3))
            .blockAllTouches()
            .setContentTitle(R.string.guide_download_labels_title)
            .setContentText(R.string.guide_download_labels_text)
            .replaceEndButton(R.layout.button_guide)
            .setShowcaseEventListener(object : SimpleShowcaseEventListener() {
                override fun onShowcaseViewDidHide(showcaseView: ShowcaseView) {
                    mShowcaseView = null
                    ViewUtils.removeFromParent(showcaseView)
                    Settings.puttGuideDownloadLabels(false)
                    mHost.openDrawer(Gravity.RIGHT)
                }
            }).build()
    }
}

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

import android.view.Gravity
import com.hippo.drawerlayout.DrawerLayout
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.EasyRecyclerView.CustomChoiceListener
import com.hippo.ehviewer.widget.MyEasyRecyclerView
import com.hippo.widget.FabLayout

/**
 * 下载列表多选模式。
 */
class DownloadChoiceListener(private val mHost: Host) : CustomChoiceListener {
    interface Host {
        val recyclerView: MyEasyRecyclerView?

        val fabLayout: FabLayout?

        fun setDrawerLockMode(lockMode: Int, edgeGravity: Int)

        val itemLongClickListener: EasyRecyclerView.OnItemLongClickListener?
    }

    override fun onIntoCustomChoice(view: EasyRecyclerView?) {
        val recyclerView = mHost.recyclerView
        if (recyclerView != null) {
            recyclerView.setOnItemLongClickListener(null)
            recyclerView.isLongClickable = false
        }
        val fabLayout = mHost.fabLayout
        fabLayout?.isExpanded = true
        // Lock drawer
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT)
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT)

        //            // 进入选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(true);
    }

    override fun onOutOfCustomChoice(view: EasyRecyclerView?) {
        val recyclerView = mHost.recyclerView
        recyclerView?.setOnItemLongClickListener(mHost.itemLongClickListener)
        val fabLayout = mHost.fabLayout
        fabLayout?.isExpanded = false
        // Unlock drawer
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT)
        mHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT)

        //            // 退出选择模式时，thumb保持可见（拖拽功能已直接附加到thumb上）
//            updateThumbVisibility(false);
    }

    override fun onItemCheckedStateChanged(
        view: EasyRecyclerView,
        position: Int,
        id: Long,
        checked: Boolean
    ) {
        if (view.checkedItemCount == 0) {
            view.outOfCustomChoiceMode()
        }
    }
}

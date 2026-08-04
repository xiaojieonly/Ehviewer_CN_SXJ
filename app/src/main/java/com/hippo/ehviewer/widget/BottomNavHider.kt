package com.hippo.ehviewer.widget

import androidx.recyclerview.widget.RecyclerView
import com.hippo.ehviewer.ui.MainActivity

/**
 * 列表滚动联动底部导航栏:拖拽跟手位移(下滚隐藏/上滚显示),
 * 滚动停止(IDLE)时吸附到全显或全隐。实际位移逻辑在 MainContentLayout,
 * 与 SearchBarMover 互不干扰(一个管底栏,一个管搜索栏/app bar)
 */
class BottomNavHider(
    private val activity: MainActivity?,
    recyclerView: RecyclerView
) : RecyclerView.OnScrollListener() {

    init {
        recyclerView.addOnScrollListener(this)
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        activity?.onContentListScrolled(dy)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            activity?.settleBottomNav()
        }
    }
}

package com.hippo.ehviewer.widget

import android.content.Context
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.hippo.drawerlayout.DrawerLayoutChild

/**
 * MainActivity 内容区包装:EhDrawerLayout 只允许一个无 gravity 的内容子 View,
 * 因此场景容器与底部导航栏统一放进本布局。
 * 系统窗口 insets 通过 DrawerLayoutChild 回调到这里:
 * - 场景容器顶部留出状态栏占位(内容从默认显示区域开始),
 *   底部全幅绘制(内容可滚动穿透到底部导航栏/系统导航条下方),
 *   inset 值通过 OnInsetsChangedListener 分发给各场景自行处理底部避让
 * - 底部导航栏底部留出系统导航栏占位,显隐带下滑/上滑淡入动画
 * - 状态栏完全透明:顶到屏幕顶端的场景(首页列表等)内容滚动时直接透到状态栏下方
 */
class MainContentLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), DrawerLayoutChild {

    fun interface OnInsetsChangedListener {
        /** top = 状态栏高度;bottomOccupied = 系统导航栏 inset + 底部导航栏占位(可见时) */
        fun onInsetsChanged(top: Int, bottomOccupied: Int)
    }

    /** 状态栏高度 */
    var windowInsetTop = 0
        private set

    /** 系统导航栏 inset */
    var windowInsetBottom = 0
        private set

    /** 场景内容底部需要避让的高度:系统导航栏 inset + 底部导航栏(可见时)占位 */
    var bottomOccupiedHeight = 0
        private set

    private var stageLayout: EhStageLayout? = null
    private var bottomNavBar: BottomNavBar? = null
    private var bottomNavVisible = false
    /** 是否由舞台统一避让状态栏;悬浮 app bar 场景(下载/历史)为 false,场景顶到屏幕顶端 */
    private var stageFitsStatusBar = true
    private val listeners = ArrayList<OnInsetsChangedListener>()
    private var lastNotifiedTop = -1
    private var lastNotifiedBottom = -1

    override fun onFinishInflate() {
        super.onFinishInflate()
        stageLayout = findViewById(com.hippo.ehviewer.R.id.fragment_container)
        bottomNavBar = findViewById(com.hippo.ehviewer.R.id.bottom_nav)
        bottomNavVisible = bottomNavBar?.visibility == VISIBLE
        applyWindowPadding()
    }

    override fun onGetWindowPadding(top: Int, bottom: Int) {
        if (windowInsetTop != top || windowInsetBottom != bottom) {
            windowInsetTop = top
            windowInsetBottom = bottom
            applyWindowPadding()
        }
    }

    override fun getAdditionalTopMargin(): Int = 0

    override fun getAdditionalBottomMargin(): Int = 0

    fun addOnInsetsChangedListener(l: OnInsetsChangedListener) {
        if (!listeners.contains(l)) {
            listeners.add(l)
        }
    }

    fun removeOnInsetsChangedListener(l: OnInsetsChangedListener) {
        listeners.remove(l)
    }

    /** 按场景显隐底部导航栏,隐藏时场景内容不再为其让位;带滑动淡入动画 */
    fun setBottomNavVisible(visible: Boolean) {
        val bar = bottomNavBar ?: return
        if (visible == bottomNavVisible) {
            // 场景级显隐状态未变,但栏体可能被列表滚动隐藏:切场景时恢复显示
            if (visible && bar.visibility == VISIBLE && bar.translationY != 0f) {
                bar.animate().cancel()
                bar.animate()
                    .translationY(0f)
                    .setDuration(ANIM_DURATION)
                    .setInterpolator(INTERPOLATOR_ENTER)
                    .withEndAction(null)
                    .start()
            }
            return
        }
        bottomNavVisible = visible
        // 先按目标态更新让位高度,再播动画
        applyWindowPadding()
        animateBottomNav(bar, visible)
    }

    /** 滚动显隐的位移上限:栏体内容高 + 系统导航栏 inset(栏体完全滑出屏幕) */
    private val bottomNavMaxOffset: Float
        get() = (bottomNavBar?.barBlockHeight ?: 0).toFloat() + windowInsetBottom

    /**
     * 列表滚动跟手位移底部导航栏(下滚滑出/上滚滑回)。
     * 纯 translationY 位移,不改变场景底部让位高度——场景内容本就以
     * clipToPadding=false 绘制在栏体下方,栏体滑走后内容自然露出
     */
    fun offsetBottomNav(dy: Int) {
        // 空闲时分页加载/布局更新也会派发 onScrolled(0,0),若因此 cancel
        // 进行中的吸附/恢复动画,栏体会停在半隐状态无法回位——dy=0 直接忽略
        if (dy == 0) return
        val bar = bottomNavBar ?: return
        if (!bottomNavVisible || bar.visibility != VISIBLE) return
        val max = bottomNavMaxOffset
        if (max <= 0f) return
        // 打断吸附/恢复动画,直接跟手
        bar.animate().cancel()
        bar.translationY = (bar.translationY + dy).coerceIn(0f, max)
    }

    /** 列表滚动停止(IDLE)时,栏体吸附到全显或全隐(复用显隐动画的时长与曲线) */
    fun settleBottomNav() {
        val bar = bottomNavBar ?: return
        if (!bottomNavVisible || bar.visibility != VISIBLE) return
        val max = bottomNavMaxOffset
        if (max <= 0f) return
        val current = bar.translationY
        val target = if (current > max / 2f) max else 0f
        if (current == target) return
        bar.animate().cancel()
        bar.animate()
            .translationY(target)
            .setDuration(ANIM_DURATION)
            .setInterpolator(if (target == 0f) INTERPOLATOR_ENTER else INTERPOLATOR_EXIT)
            .withEndAction(null)
            .start()
    }

    private fun animateBottomNav(bar: BottomNavBar, show: Boolean) {
        val offset = (bar.barBlockHeight + windowInsetBottom).toFloat()
        bar.animate().cancel()
        if (offset <= 0f) {
            // 尚未完成首次布局,无法计算滑出距离,直接显隐
            bar.translationY = 0f
            bar.alpha = 1f
            bar.visibility = if (show) VISIBLE else GONE
            return
        }
        if (show) {
            bar.visibility = VISIBLE
            bar.translationY = offset
            bar.alpha = 0f
            bar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(INTERPOLATOR_ENTER)
                .withEndAction(null)
                .start()
        } else {
            bar.animate()
                .translationY(offset)
                .alpha(0f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(INTERPOLATOR_EXIT)
                .withEndAction {
                    // 动画期间可能又切回显示,只在目标态仍为隐藏时置 GONE
                    if (!bottomNavVisible) {
                        bar.visibility = GONE
                    }
                }
                .start()
        }
    }

    /** 按场景切换舞台顶部避让:true=避开状态栏(默认);false=场景顶到屏幕顶端 */
    fun setStageFitsStatusBar(fits: Boolean) {
        if (stageFitsStatusBar != fits) {
            stageFitsStatusBar = fits
            applyWindowPadding()
        }
    }

    private fun applyWindowPadding() {
        // 顶部:舞台整体下移避开状态栏(内容从默认显示区域开始),
        // 悬浮 app bar 场景(stageFitsStatusBar=false)顶到屏幕顶端自行避让;
        // 底部:舞台全幅,场景内容可穿透到胶囊/系统导航条下方,让位值分发给场景
        stageLayout?.let { stage ->
            val lp = stage.layoutParams as? MarginLayoutParams
            val topMargin = if (stageFitsStatusBar) windowInsetTop else 0
            if (lp != null && lp.topMargin != topMargin) {
                lp.topMargin = topMargin
                stage.layoutParams = lp
            }
        }
        val barHeight = if (bottomNavVisible) {
            bottomNavBar?.barBlockHeight ?: 0
        } else {
            0
        }
        bottomOccupiedHeight = windowInsetBottom + barHeight
        bottomNavBar?.setWindowInsetBottom(windowInsetBottom)
        notifyInsetsChangedIfNeeded()
    }

    private fun notifyInsetsChangedIfNeeded() {
        if (lastNotifiedTop == windowInsetTop && lastNotifiedBottom == bottomOccupiedHeight) {
            return
        }
        lastNotifiedTop = windowInsetTop
        lastNotifiedBottom = bottomOccupiedHeight
        listeners.forEach { it.onInsetsChanged(windowInsetTop, bottomOccupiedHeight) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 底部栏高度在布局后才确定,刷新场景底部占位并通知
        applyWindowPadding()
    }

    private companion object {
        const val ANIM_DURATION = 200L
        // M3 emphasized:进入减速、退出加速
        val INTERPOLATOR_ENTER = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val INTERPOLATOR_EXIT = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}

package com.hippo.ehviewer.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.IdRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.ShapeAppearanceModel
import com.hippo.android.resource.AttrResources
import com.hippo.ehviewer.R

/**
 * M3 标准底部导航栏:贴底长条,无阴影;选中项为图标后的单一药丸指示器(64x32dp)。
 * 薄壳封装 NavigationBarView,对外保留原有 API;
 * 系统导航栏 inset 由 MainContentLayout 通过 setWindowInsetBottom 注入,
 * padding 加在栏体上,栏体背景延伸到手势条区域(edge-to-edge)。
 */
class BottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    fun interface OnTabSelectedListener {
        fun onTabSelected(@IdRes tabId: Int)
    }

    private val navView: NavigationBarView
    private var listener: OnTabSelectedListener? = null

    @IdRes
    private var selectedId: Int = 0

    /** 程序化选中时不回抛 tab 切换事件(避免与 MainActivity 互相递归) */
    private var suppressNotify = false

    private var windowInsetBottom = 0

    /** 栏体内容高度(不含系统导航栏 inset),用于场景内容底部让位 */
    var barBlockHeight: Int = 0
        private set

    init {
        val density = resources.displayMetrics.density
        navView = BottomNavigationView(context).apply {
            inflateMenu(R.menu.bottom_nav_main)
            // M3:扁平无阴影,容器色不透明
            elevation = 0f
            setBackgroundColor(AttrResources.getAttrColor(context, R.attr.navBarContainerColor))
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconSize = (24 * density).toInt()
            // 选中态图标/文字取主题色,未选取次级文字色
            val colors = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    AttrResources.getAttrColor(context, androidx.appcompat.R.attr.colorPrimary),
                    AttrResources.getAttrColor(context, android.R.attr.textColorSecondary)
                )
            )
            itemIconTintList = colors
            itemTextColor = colors
            // 单一层药丸指示器:M3 规范 64x32dp 全圆角,仅衬于图标之后
            isItemActiveIndicatorEnabled = true
            setItemActiveIndicatorColor(
                ColorStateList.valueOf(AttrResources.getAttrColor(context, R.attr.navBarIndicatorColor))
            )
            setItemActiveIndicatorWidth((64 * density).toInt())
            setItemActiveIndicatorHeight((32 * density).toInt())
            setItemActiveIndicatorMarginHorizontal((4 * density).toInt())
            setItemActiveIndicatorShapeAppearance(
                ShapeAppearanceModel.builder().setAllCornerSizes(16 * density).build()
            )
            setOnItemSelectedListener { item ->
                if (item.itemId != selectedId) {
                    selectedId = item.itemId
                    if (!suppressNotify) {
                        listener?.onTabSelected(item.itemId)
                    }
                }
                true
            }
        }
        addView(navView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setOnTabSelectedListener(l: OnTabSelectedListener?) {
        listener = l
    }

    /** 更新选中态,未知 id 或 0 时保持现状 */
    fun setSelectedId(@IdRes id: Int) {
        if (navView.menu.findItem(id) == null) {
            return
        }
        selectedId = id
        suppressNotify = true
        navView.selectedItemId = id
        suppressNotify = false
    }

    /** 由 MainContentLayout 注入系统导航栏 inset;栏体背景延伸到手势条区域 */
    fun setWindowInsetBottom(bottom: Int) {
        if (windowInsetBottom != bottom) {
            windowInsetBottom = bottom
            navView.setPadding(0, 0, 0, bottom)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 内容区高度 = 栏体总高 - inset 占位,场景让位公式沿用(inset + 栏高)
        barBlockHeight = navView.height - windowInsetBottom
    }
}

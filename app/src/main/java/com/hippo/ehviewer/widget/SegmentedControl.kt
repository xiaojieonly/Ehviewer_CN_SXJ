package com.hippo.ehviewer.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.hippo.android.resource.AttrResources
import com.hippo.ehviewer.R

/**
 * M3 风格分段控件:半透明毛玻璃轨道(全圆角) + 主题淡色选中块(平移动画)。
 * 层级自底向上:轨道背景(控件自身背景) < 选中块 < 文字。
 */
class SegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    fun interface OnSegmentSelectedListener {
        fun onSegmentSelected(index: Int)
    }

    private val indicator: View
    private val track: LinearLayout
    private val labels = ArrayList<TextView>()
    private var listener: OnSegmentSelectedListener? = null

    var selectedIndex = 0
        private set

    private val trackInset: Int
    private var pendingIndicatorUpdate = true

    init {
        val density = resources.displayMetrics.density
        trackInset = (2 * density).toInt()
        // 布局固定 32dp 高,全圆角半径取一半
        val cornerRadius = 16 * density

        // 轨道:半透明毛玻璃填充 + 1dp 描边,作为控件自身背景(最底层),
        // 列表内容滚动到控件下方时透出磨砂质感
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(AttrResources.getAttrColor(context, R.attr.navBarContainerColor))
            setStroke(
                (1 * density).toInt().coerceAtLeast(1),
                AttrResources.getAttrColor(context, R.attr.glassStrokeColor)
            )
        }

        // M3:选中块为主题淡色(secondary container 风格),扁平无阴影。
        // 先添加使其层级低于文字行:位于轨道背景之上、文字之下,
        // 否则选中块会盖住选中项文字
        indicator = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius - trackInset
                setColor(AttrResources.getAttrColor(context, R.attr.navBarIndicatorColor))
            }
        }
        addView(indicator, LayoutParams(0, 0))

        // 文字行:背景透明,位于选中块之上
        track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setSegments(@StringRes vararg textResIds: Int) {
        track.removeAllViews()
        labels.clear()
        textResIds.forEachIndexed { index, resId ->
            val label = TextView(context).apply {
                setText(resId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                includeFontPadding = false
                maxLines = 1
                gravity = Gravity.CENTER
                setOnClickListener { onSegmentClicked(index) }
            }
            labels.add(label)
            track.addView(
                label,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        pendingIndicatorUpdate = true
        requestLayout()
        updateLabelColors()
    }

    fun setOnSegmentSelectedListener(l: OnSegmentSelectedListener?) {
        listener = l
    }

    private fun onSegmentClicked(index: Int) {
        if (index == selectedIndex) {
            return
        }
        setSelectedIndex(index, true)
        listener?.onSegmentSelected(index)
    }

    fun setSelectedIndex(index: Int, animate: Boolean) {
        if (labels.isEmpty() || index < 0 || index >= labels.size) {
            return
        }
        selectedIndex = index
        updateLabelColors()
        moveIndicator(animate)
    }

    private fun updateLabelColors() {
        // M3:选中项主题色压淡色块,未选中项次要色
        val selectedColor = AttrResources.getAttrColor(context, androidx.appcompat.R.attr.colorPrimary)
        val normalColor = AttrResources.getAttrColor(context, android.R.attr.textColorSecondary)
        labels.forEachIndexed { i, label ->
            label.setTextColor(if (i == selectedIndex) selectedColor else normalColor)
        }
    }

    private fun moveIndicator(animate: Boolean) {
        val count = labels.size
        if (count == 0) {
            return
        }
        val segmentWidth = (width - trackInset * 2) / count
        if (segmentWidth <= 0) {
            pendingIndicatorUpdate = true
            return
        }
        // 选中块的尺寸与纵向位置由 onLayout 保证,这里只处理选中段的横向平移
        val targetX = (trackInset + segmentWidth * selectedIndex).toFloat()
        if (animate) {
            // M3 emphasized standard 曲线
            indicator.animate().translationX(targetX).setDuration(200)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)).start()
        } else {
            indicator.animate().cancel()
            indicator.translationX = targetX
        }
        pendingIndicatorUpdate = false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 显式布置选中块:在 onSizeChanged 里改 LayoutParams 再 requestLayout 不可靠
        // (布局过程中的 requestLayout 可能被框架丢弃,选中块将一直保持 0×0 不渲染)
        val count = labels.size
        if (count == 0) {
            return
        }
        val segmentWidth = (width - trackInset * 2) / count
        if (segmentWidth <= 0) {
            return
        }
        val top = trackInset
        val bottom = height - trackInset
        if (indicator.width != segmentWidth || indicator.height != bottom - top) {
            indicator.layout(0, top, segmentWidth, bottom)
        }
        if (pendingIndicatorUpdate) {
            moveIndicator(false)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pendingIndicatorUpdate || w != oldw) {
            moveIndicator(false)
        }
    }
}

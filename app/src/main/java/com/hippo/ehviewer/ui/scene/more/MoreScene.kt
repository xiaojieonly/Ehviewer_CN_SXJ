package com.hippo.ehviewer.ui.scene.more

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.callBack.ImageChangeCallBack
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.SettingsActivity
import com.hippo.ehviewer.ui.main.UserImageChange
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.ehviewer.widget.LimitsCountView
import com.hippo.widget.AvatarImageView
import java.io.File

/**
 * 「个人」场景:顶部资料卡(背景/头像/账户名)与账户额度,下方为设置菜单(主题、应用设置入口)。
 * 头像/背景更换走 UserImageChange,拍照/相册结果由 MainActivity.onActivityResult 路由回来。
 */
class MoreScene : BaseScene(), ImageChangeCallBack {

    private var themeValue: TextView? = null
    private var avatarView: AvatarImageView? = null
    private var headerBackground: ImageView? = null
    private var displayNameView: TextView? = null
    private var limitsCountView: LimitsCountView? = null
    private var userImageChange: UserImageChange? = null

    // 最外层 tab 场景,显示底部导航栏
    override fun needShowBottomNav(): Boolean = true

    override fun getNavCheckedItem(): Int = R.id.nav_more

    override fun onCreateView2(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.scene_more, container, false)
    }

    override fun needFitNavigationBar(): Boolean {
        return false
    }

    /**
     * 沉浸式避让:根 ScrollView 底部让出底部导航占位,顶部由舞台容器统一避让;
     * 内容可滚动到占位下方(clipToPadding=false)。幂等
     */
    override fun onApplyWindowInsets(statusBarInset: Int, bottomOccupied: Int) {
        val scrollView = view as? ScrollView ?: return
        scrollView.clipToPadding = false
        scrollView.setPadding(scrollView.paddingLeft, 0,
            scrollView.paddingRight, bottomOccupied)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        themeValue = view.findViewById(R.id.theme_value)
        themeValue?.text = getThemeText()
        view.findViewById<View>(R.id.theme_row).setOnClickListener {
            Settings.putTheme(getNextTheme())
            (requireActivity().application as EhApplication).recreate()
        }
        view.findViewById<View>(R.id.entry_settings).setOnClickListener {
            val intent = Intent(requireActivity(), SettingsActivity::class.java)
            requireActivity().startActivityForResult(intent, MainActivity.REQUEST_CODE_SETTINGS)
        }

        avatarView = view.findViewById(R.id.avatar)
        headerBackground = view.findViewById(R.id.header_background)
        displayNameView = view.findViewById(R.id.display_name)
        limitsCountView = view.findViewById(R.id.limits_count_view)
        avatarView?.setOnClickListener { startImageChange(UserImageChange.CHANGE_AVATAR) }
        headerBackground?.setOnClickListener { startImageChange(UserImageChange.CHANGE_BACKGROUND) }

        updateProfile()
        limitsCountView?.onLoadData(view, true)
    }

    override fun onResume() {
        super.onResume()
        // 登录态变化、更换头像/背景返回后刷新资料展示
        updateProfile()
    }

    override fun onDestroyView() {
        userImageChange?.let { uic -> getActivity2()?.unregisterUserImageChange(uic) }
        userImageChange = null
        themeValue = null
        avatarView = null
        headerBackground = null
        displayNameView = null
        limitsCountView = null
        super.onDestroyView()
    }

    private fun startImageChange(dialogType: Int) {
        val activity = getActivity2() ?: return
        val uic = UserImageChange(
            activity, dialogType,
            LayoutInflater.from(activity), LayoutInflater.from(activity), this
        )
        userImageChange = uic
        activity.registerUserImageChange(uic, avatarView)
        uic.showImageChangeDialog()
    }

    /** 背景图更换成功回调(仅静态图,GIF 背景不支持) */
    override fun backgroundSourceChange(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return
        headerBackground?.setImageBitmap(bitmap)
    }

    private fun updateProfile() {
        avatarView?.let { avatar ->
            val avatarUrl = Settings.getAvatar()
            if (TextUtils.isEmpty(avatarUrl)) {
                val userAvatarFile = Settings.getUserImageFile(Settings.USER_AVATAR_IMAGE)
                if (userAvatarFile != null) {
                    val bitmap = BitmapFactory.decodeFile(userAvatarFile.path)
                    avatar.load(BitmapDrawable(avatar.resources, bitmap))
                } else {
                    avatar.load(R.drawable.default_avatar)
                }
            } else {
                avatar.load(avatarUrl, avatarUrl)
            }
        }

        displayNameView?.let { nameView ->
            var displayName = Settings.getDisplayName()
            if (TextUtils.isEmpty(displayName)) {
                displayName = getString(R.string.default_display_name)
            }
            nameView.text = displayName
        }

        if (headerBackground != null) {
            val backgroundFile = Settings.getUserImageFile(Settings.USER_BACKGROUND_IMAGE)
            if (backgroundFile != null) {
                val bitmap = BitmapFactory.decodeFile(backgroundFile.path)
                if (bitmap != null) {
                    headerBackground?.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun getThemeText(): String {
        val resId = when (Settings.getTheme()) {
            Settings.THEME_DARK -> R.string.theme_dark
            Settings.THEME_BLACK -> R.string.theme_black
            else -> R.string.theme_light
        }
        return getString(resId)
    }

    private fun getNextTheme(): Int {
        return when (Settings.getTheme()) {
            Settings.THEME_LIGHT -> Settings.THEME_DARK
            Settings.THEME_DARK -> Settings.THEME_BLACK
            else -> Settings.THEME_LIGHT
        }
    }
}

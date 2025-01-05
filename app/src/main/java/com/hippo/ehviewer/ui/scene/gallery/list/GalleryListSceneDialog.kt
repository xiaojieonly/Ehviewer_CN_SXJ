package com.hippo.ehviewer.ui.scene.gallery.list

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhFilter
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.userTag.TagPushParam
import com.hippo.ehviewer.client.data.userTag.UserTagList
import com.hippo.ehviewer.dao.Filter
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.ehviewer.ui.scene.EhCallback
import com.hippo.ehviewer.util.TagTranslationUtil
import com.hippo.scene.SceneFragment

class GalleryListSceneDialog(val baseScene: BaseScene) {
    val context: Context? = baseScene.context
    private var tagName: String? = null

    fun setTagName(tagName: String?) {
        this.tagName = tagName
    }

    fun showTagLongPressDialog(ehTags: EhTagDatabase?) {
        val temp: String?
        val index = tagName!!.indexOf(':')
        temp = if (index >= 0) {
            tagName!!.substring(index + 1)
        } else {
            tagName
        }
        val title = if (Settings.getShowTagTranslations()) {
            TagTranslationUtil.getTagCN(tagName, ehTags) + "(" + tagName + ")"
        } else {
            tagName
        }
        val builder = AlertDialog.Builder(
            context!!
        )
            .setTitle(title)
            .setItems(
                R.array.tag_menu_entries
            ) { _: DialogInterface?, which: Int ->
                when (which) {
                    0 -> UrlOpener.openUrl(
                        context, EhUrl.getTagDefinitionUrl(temp), false
                    )

                    1 -> showFilterTagDialog()
                }
            }
        if (!Settings.isLogin()) {
            builder.setNegativeButton(
                R.string.copy_tag
            ) { _: DialogInterface?, _: Int -> copyTag(tagName) }
                .show()
        } else {
            builder.setNeutralButton(
                R.string.copy_tag
            ) { _: DialogInterface?, _: Int -> copyTag(tagName) }
                .setNegativeButton(
                    R.string.subscription_watched
                ) { _: DialogInterface?, _: Int -> requestTag(tagName, true) }
                .setPositiveButton(
                    R.string.subscription_hidden
                ) { _: DialogInterface?, _: Int -> requestTag(tagName, false) }
                .show()
        }
    }

    private fun showFilterTagDialog() {
        if (context == null) {
            return
        }

        AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.filter_the_tag, tagName))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, which: Int ->
                if (which != DialogInterface.BUTTON_POSITIVE) {
                    return@setPositiveButton
                }
                val filter = Filter()
                filter.mode = EhFilter.MODE_TAG
                filter.text = tagName
                EhFilter.getInstance().addFilter(filter)
                showTip(R.string.filter_added, BaseScene.LENGTH_SHORT)
            }.show()
    }

    private fun showTip(@StringRes id: Int, length: Int) {
        val activity = baseScene.activity
        if (activity is MainActivity) {
            activity.showTip(id, length)
        }
    }

    private fun copyTag(tag: String?) {
        val manager = context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(null, tag))
        Toast.makeText(context, R.string.gallery_tag_copy, Toast.LENGTH_LONG).show()
    }

    private fun requestTag(tagName: String?, tagState: Boolean) {
        val url = EhUrl.getMyTag()

        if (null == context) {
            return
        }
        val activity = baseScene.activity2 ?: return

        val callback =
            SubscriptionDetailListener(context, activity.stageId, baseScene.tag, tagState)

        val param = TagPushParam()

        param.tagNameNew = tagName
        if (tagState) {
            param.tagWatchNew = "on"
        } else {
            param.tagHiddenNew = "on"
        }


        val mRequest = EhRequest()
            .setMethod(EhClient.METHOD_ADD_TAG)
            .setArgs(url, param).setCallback(callback)

        EhApplication.getEhClient(context).execute(mRequest)
    }

    private inner class SubscriptionDetailListener(
        context: Context,
        stageId: Int,
        sceneTag: String?,
        private val tagState: Boolean
    ) :
        EhCallback<GalleryListScene?, UserTagList?>(context, stageId, sceneTag) {
        override fun isInstance(scene: SceneFragment): Boolean {
            return false
        }

        override fun onSuccess(result: UserTagList?) {
            baseScene.setTagList(result)
            val state =
                if (tagState) context!!.getString(R.string.subscription_watched) else context!!.getString(
                    R.string.subscription_hidden
                )
            val msg = context.getString(R.string.subscription_success, state)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        override fun onFailure(e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }

        override fun onCancel() {
        }
    }
}

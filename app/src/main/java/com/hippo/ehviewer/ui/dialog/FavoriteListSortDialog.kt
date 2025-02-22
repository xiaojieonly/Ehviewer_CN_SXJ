package com.hippo.ehviewer.ui.dialog

import android.app.AlertDialog
import android.content.DialogInterface
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhConfig
import com.hippo.ehviewer.client.parser.FavoritesParser
import com.hippo.ehviewer.ui.scene.gallery.list.FavoritesScene

class FavoriteListSortDialog(private val scene: FavoritesScene) {
    fun showCloudSort(mResult: FavoritesParser.Result?) {
        val checked: Int
        if (null == mResult || null == mResult.favOrder) {
            return
        }
        if (mResult.favOrder == EhConfig.ORDER_BY_FAV_TIME) {
            checked = 0
        } else if (mResult.favOrder == EhConfig.ORDER_BY_PUB_TIME) {
            checked = 1
        } else {
            return
        }
        val dialog = AlertDialog.Builder(scene.context)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(R.string.order)
            .setSingleChoiceItems(
                R.array.fav_sort,
                checked,
                DialogInterface.OnClickListener { dialogInterface: DialogInterface?, i: Int ->
                    if (i == 0) {
                        scene.updateSort("inline_set=fs_f")
                    } else {
                        scene.updateSort("inline_set=fs_p")
                    }
                    dialogInterface!!.dismiss()
                })
            .create()
        dialog.show()
    }

    fun showLocalSort(mResult: FavoritesParser.Result?) {
    }
}

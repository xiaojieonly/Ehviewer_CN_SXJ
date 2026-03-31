package com.hippo.util

import com.hippo.ehviewer.client.data.GalleryComment
import com.hippo.ehviewer.dao.BlackList
//只是稍微修一下，不然会运行报错
object BlackListUtils {
    @JvmStatic
    fun parseBlacklist(comment: GalleryComment): BlackList {
        val blackList = BlackList()

        blackList.badgayname = comment.user
        blackList.angrywith = comment.comment
        blackList.mode = 1
        blackList.add_time = getTimeNow()

        return blackList
    }
}
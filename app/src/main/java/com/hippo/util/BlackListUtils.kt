package com.hippo.util

import com.hippo.ehviewer.client.data.GalleryComment
import com.hippo.ehviewer.dao.BlackList
import com.hippo.util.TimeUtils.timeNow

object BlackListUtils {
    @JvmStatic
    fun parseBlacklist(comment: GalleryComment): BlackList {
        val blackList = BlackList()

        blackList.badgayname = comment.user
        blackList.angrywith = comment.comment
        blackList.mode = 1
        blackList.add_time = timeNow

        return blackList
    }
}

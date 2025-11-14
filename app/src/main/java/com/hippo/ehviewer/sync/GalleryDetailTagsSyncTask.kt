package com.hippo.ehviewer.sync

import android.util.Log
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.data.GalleryTagGroup
import com.hippo.ehviewer.dao.GalleryTags

class GalleryDetailTagsSyncTask(val detail: GalleryDetail) : Thread() {
    private val TAG = "TagsSyncTask"

    @Synchronized
    override fun start() {
        super.start()
    }

    override fun run() {
        super.run()
        val galleryTags = this.tags
        try {
            if (EhDB.inGalleryTags(detail.gid)) {
                EhDB.updateGalleryTags(galleryTags)
            } else {
                EhDB.insertGalleryTags(galleryTags)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private val tags: GalleryTags
        get() {
            val tags = GalleryTags(detail.gid)

            val groups = detail.tags
            for (group in groups) {
                parserData(tags, group)
            }

            return tags
        }

    private fun parserData(tags: GalleryTags, group: GalleryTagGroup) {
        when (group.groupName) {
            "rows" -> tags.rows = getTagString(group.size(), group)
            "artist" -> tags.artist = getTagString(group.size(), group)
            "cosplayer" -> tags.cosplayer = getTagString(group.size(), group)
            "character" -> tags.character = getTagString(group.size(), group)
            "female" -> tags.female = getTagString(group.size(), group)
            "group" -> tags.group = getTagString(group.size(), group)
            "language" -> tags.language = getTagString(group.size(), group)
            "male" -> tags.male = getTagString(group.size(), group)
            "misc" -> tags.misc = getTagString(group.size(), group)
            "mixed" -> tags.mixed = getTagString(group.size(), group)
            "other" -> tags.other = getTagString(group.size(), group)
            "parody" -> tags.parody = getTagString(group.size(), group)
            "reclass" -> tags.reclass = getTagString(group.size(), group)
            else -> {}
        }
    }

    private fun getTagString(size: Int, group: GalleryTagGroup): String {
        val builder = StringBuilder()

        for (i in 0..<size) {
            if (i == size - 1) {
                builder.append(group.getTagAt(i))
            } else {
                builder.append(group.getTagAt(i)).append(",")
            }
        }
        return builder.toString()
    }
}

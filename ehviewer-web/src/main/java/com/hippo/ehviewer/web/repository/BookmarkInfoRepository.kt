package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.BookmarkInfoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BookmarkInfoRepository : JpaRepository<BookmarkInfoEntity, Long> {
    fun findByGid(gid: Long): BookmarkInfoEntity?
    fun findByCategory(category: Int): List<BookmarkInfoEntity>
    fun deleteByGid(gid: Long)
}

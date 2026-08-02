package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface BookmarkInfoRepository : JpaRepository<BookmarkInfoEntity, Long> {
    fun findByGid(gid: Long): BookmarkInfoEntity?
    fun findByCategory(category: Int): List<BookmarkInfoEntity>
    fun findAllByUsernameIsNull(): List<BookmarkInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
}

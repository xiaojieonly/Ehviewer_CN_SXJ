package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.GalleryTagsEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface GalleryTagsRepository : JpaRepository<GalleryTagsEntity, Long> {
    fun findByGid(gid: Long): List<GalleryTagsEntity>
    fun findByGidAndTag(gid: Long, tag: String): GalleryTagsEntity?
    @Transactional
    fun deleteByGid(gid: Long)
}

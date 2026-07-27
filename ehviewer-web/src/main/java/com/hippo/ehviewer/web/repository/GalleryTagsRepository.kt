package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.GalleryTagsEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GalleryTagsRepository : JpaRepository<GalleryTagsEntity, Long> {
    fun findByGid(gid: Long): List<GalleryTagsEntity>
    fun findByGidAndTag(gid: Long, tag: String): GalleryTagsEntity?
    fun deleteByGid(gid: Long)
}

package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.LocalFavoriteInfoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LocalFavoriteInfoRepository : JpaRepository<LocalFavoriteInfoEntity, Long> {
    fun findByGid(gid: Long): LocalFavoriteInfoEntity?
    fun findAllByOrderByTimeDesc(): List<LocalFavoriteInfoEntity>
    fun deleteByGid(gid: Long)
}

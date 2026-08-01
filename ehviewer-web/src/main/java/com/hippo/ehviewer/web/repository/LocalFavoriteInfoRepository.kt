package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.LocalFavoriteInfoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface LocalFavoriteInfoRepository : JpaRepository<LocalFavoriteInfoEntity, Long> {
    fun findByGid(gid: Long): LocalFavoriteInfoEntity?
    fun findAllByOrderByTimeDesc(): List<LocalFavoriteInfoEntity>
    fun findAllByUsernameIsNull(): List<LocalFavoriteInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
}

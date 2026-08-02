package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByGid(gid: Long): DownloadInfoEntity?
    fun findByLabel(label: Int): List<DownloadInfoEntity>
    fun findByState(state: Int): List<DownloadInfoEntity>
    fun findAllByUsernameIsNull(): List<DownloadInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
}

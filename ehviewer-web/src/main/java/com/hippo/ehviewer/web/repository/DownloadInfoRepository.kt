package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByGid(gid: Long): DownloadInfoEntity?
    fun findByLabel(label: Int): List<DownloadInfoEntity>
    fun findByState(state: Int): List<DownloadInfoEntity>
    fun deleteByGid(gid: Long)
}

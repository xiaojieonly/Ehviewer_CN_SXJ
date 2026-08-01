package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.DownloadDirnameEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface DownloadDirnameRepository : JpaRepository<DownloadDirnameEntity, Long> {
    fun findByGid(gid: Long): DownloadDirnameEntity?
    @Transactional
    fun deleteByGid(gid: Long)
}

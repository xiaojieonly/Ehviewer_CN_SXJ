package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.DownloadDirnameEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DownloadDirnameRepository : JpaRepository<DownloadDirnameEntity, Long> {
    fun findByGid(gid: Long): DownloadDirnameEntity?
    fun deleteByGid(gid: Long)
}

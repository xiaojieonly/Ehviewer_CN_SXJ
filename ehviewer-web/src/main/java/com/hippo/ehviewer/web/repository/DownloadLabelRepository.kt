package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.DownloadLabelEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DownloadLabelRepository : JpaRepository<DownloadLabelEntity, Long> {
    fun findByLabel(label: String): DownloadLabelEntity?
    fun findAllByUsernameIsNull(): List<DownloadLabelEntity>
    fun countByUsername(username: String): Long
}

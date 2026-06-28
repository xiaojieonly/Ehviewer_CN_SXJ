package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.SmbConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SmbConfigRepository : JpaRepository<SmbConfigEntity, Long> {
    fun findByEnabled(enabled: Boolean): SmbConfigEntity?
}

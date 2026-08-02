package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.SmbConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SmbConfigRepository : JpaRepository<SmbConfigEntity, Long> {
    fun findByEnabled(enabled: Boolean): SmbConfigEntity?
}

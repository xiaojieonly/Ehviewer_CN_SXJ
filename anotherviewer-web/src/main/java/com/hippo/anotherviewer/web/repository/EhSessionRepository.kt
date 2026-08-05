package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.EhSessionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EhSessionRepository : JpaRepository<EhSessionEntity, Long> {
    fun findByUsername(username: String): EhSessionEntity?
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<EhSessionEntity>
    fun findAllByUsernameIsNull(): List<EhSessionEntity>
}

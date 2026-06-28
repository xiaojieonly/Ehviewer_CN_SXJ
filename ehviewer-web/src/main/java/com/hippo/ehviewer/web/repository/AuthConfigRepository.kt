package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.AuthConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AuthConfigRepository : JpaRepository<AuthConfigEntity, Long> {
    fun findByUsername(username: String): AuthConfigEntity?
    fun existsByUsername(username: String): Boolean
}

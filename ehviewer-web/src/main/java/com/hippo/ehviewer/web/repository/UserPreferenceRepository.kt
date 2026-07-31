package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.UserPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreferenceEntity, Long> {
    fun findByUsername(username: String): UserPreferenceEntity?
}

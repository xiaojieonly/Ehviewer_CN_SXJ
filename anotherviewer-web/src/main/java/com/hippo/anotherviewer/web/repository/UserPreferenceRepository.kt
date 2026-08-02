package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreferenceEntity, Long> {
    fun findByUsername(username: String): UserPreferenceEntity?
}

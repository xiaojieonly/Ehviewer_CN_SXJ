package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.FilterEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FilterRepository : JpaRepository<FilterEntity, Long> {
    fun findByText(text: String): FilterEntity?
    fun findByType(type: Int): List<FilterEntity>
    fun findByTypeAndEnabled(type: Int, enabled: Boolean): List<FilterEntity>
    fun findAllByUsernameIsNull(): List<FilterEntity>
    fun countByUsername(username: String): Long
}

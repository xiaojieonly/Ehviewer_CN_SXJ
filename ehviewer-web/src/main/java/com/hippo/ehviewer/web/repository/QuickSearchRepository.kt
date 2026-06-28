package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.QuickSearchEntity
import org.springframework.data.jpa.repository.JpaRepository

interface QuickSearchRepository : JpaRepository<QuickSearchEntity, Long> {
    fun findByName(name: String): QuickSearchEntity?
    fun findAllByOrderById(): List<QuickSearchEntity>
}

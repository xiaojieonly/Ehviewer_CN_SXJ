package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.BlackListEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BlackListRepository : JpaRepository<BlackListEntity, Long> {
    fun findByUser(user: String): BlackListEntity?
    fun existsByUser(user: String): Boolean
}

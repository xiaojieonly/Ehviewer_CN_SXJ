package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HistoryInfoRepository : JpaRepository<HistoryInfoEntity, Long> {
    fun findByGid(gid: Long): HistoryInfoEntity?
    fun findAllByOrderByTimeDesc(): List<HistoryInfoEntity>
    fun deleteByGid(gid: Long)
}

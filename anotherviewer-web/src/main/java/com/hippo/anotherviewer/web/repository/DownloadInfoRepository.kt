package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByGid(gid: Long): DownloadInfoEntity?
    fun findByLabel(label: Int): List<DownloadInfoEntity>
    /** 按 label 分页（W6 下载列表分页）。 */
    fun findByLabel(label: Int, pageable: Pageable): Page<DownloadInfoEntity>
    fun countByLabel(label: Int): Long
    fun findByState(state: Int): List<DownloadInfoEntity>
    fun findAllByUsernameIsNull(): List<DownloadInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<DownloadInfoEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<DownloadInfoEntity>
}

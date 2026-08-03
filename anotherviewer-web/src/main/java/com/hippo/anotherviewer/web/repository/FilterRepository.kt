package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.FilterEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FilterRepository : JpaRepository<FilterEntity, Long> {
    fun findByText(text: String): FilterEntity?
    fun findByType(type: Int): List<FilterEntity>
    fun findByTypeAndEnabled(type: Int, enabled: Boolean): List<FilterEntity>
    fun findAllByUsernameIsNull(): List<FilterEntity>
    fun countByUsername(username: String): Long
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<FilterEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<FilterEntity>
}

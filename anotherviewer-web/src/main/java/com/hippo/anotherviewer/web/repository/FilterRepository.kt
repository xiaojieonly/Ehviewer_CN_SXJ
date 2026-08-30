package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.FilterEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FilterRepository : JpaRepository<FilterEntity, Long> {
    fun findByText(text: String): FilterEntity?
    fun findByType(type: Int): List<FilterEntity>
    /** MASTER-2026-08-22 P8：mergeFilter 按 (type,text) 精确查找，替代全表扫描。 */
    fun findByTypeAndText(type: Int, text: String): FilterEntity?
    /**
     * 容错派生查询：返回全部 (type,text) 匹配行（历史数据可能含重复，见 2026-08-30
     * 联调事故 NonUniqueResultException），调用方按 lastModified 去重取最新；
     * 配合唯一约束避免重复插入。
     */
    fun findByTypeAndTextIgnoreCaseOrderByLastModifiedDesc(type: Int, text: String): List<FilterEntity>
    fun findByTypeAndEnabled(type: Int, enabled: Boolean): List<FilterEntity>
    fun findAllByUsernameIsNull(): List<FilterEntity>
    fun countByUsername(username: String): Long
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<FilterEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<FilterEntity>
}

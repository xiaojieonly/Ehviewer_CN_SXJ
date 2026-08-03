package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface HistoryInfoRepository : JpaRepository<HistoryInfoEntity, Long> {
    fun findByGid(gid: Long): HistoryInfoEntity?
    fun findAllByOrderByTimeDesc(): List<HistoryInfoEntity>
    fun findAllByUsernameIsNull(): List<HistoryInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<HistoryInfoEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<HistoryInfoEntity>

    /** DB-paginated local history, newest first (empty-keyword fallback). 墓碑行不列进 REST 列表。 */
    @Query("select h from HistoryInfoEntity h where h.deleted = false order by h.time desc")
    fun findHistoryPaged(pageable: Pageable): Page<HistoryInfoEntity>

    /** DB-paginated local history filtered by category, newest first. */
    fun findByCategoryOrderByTimeDesc(category: Int, pageable: Pageable): Page<HistoryInfoEntity>

    /** DB-paginated local history matching keyword in title/titleJpn (LIKE %kw%, case-insensitive), newest first. */
    @Query("""
        select h from HistoryInfoEntity h
        where lower(h.title) like lower(concat('%', :keyword, '%'))
           or lower(h.titleJpn) like lower(concat('%', :keyword, '%'))
        order by h.time desc
    """)
    fun findByTitleContainingIgnoreCaseOrTitleJpnContainingIgnoreCaseOrderByTimeDesc(
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<HistoryInfoEntity>
}

package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.HistoryInfoEntity
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

    /** DB-paginated local history, newest first (empty-keyword fallback). */
    @Query("select h from HistoryInfoEntity h order by h.time desc")
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

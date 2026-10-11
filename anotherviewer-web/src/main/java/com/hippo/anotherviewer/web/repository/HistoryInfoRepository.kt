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
    /**
     * A7-3（P1-1）：同 gid 多行（历史脏数据/属主并存）时单实体 [findByGid] 派生查询
     * 会抛 IncorrectResultSizeDataAccessException，毒化整条同步通道。同步仲裁与
     * 写前查找一律走本 List 版本 + 属主/存活 firstOrNull。
     */
    fun findAllByGid(gid: Long): List<HistoryInfoEntity>
    /** S7: 列表端点（history/favorites/downloads）批量取阅读进度，避免逐行 findByGid 的 N+1。 */
    fun findByGidIn(gids: Collection<Long>): List<HistoryInfoEntity>
    fun findAllByOrderByTimeDesc(): List<HistoryInfoEntity>
    fun findAllByUsernameIsNull(): List<HistoryInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<HistoryInfoEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<HistoryInfoEntity>

    // ── v4 回收站 / status（WP3）──
    // 注意：以下带 deleted 条件的查询**仅供回收站（list/restore/purge）与 status
    // 计数使用**；merge/pull 查询（findByGid*/findByUsername*）仍不得加 deleted
    // 条件（呼应 DownloadInfoRepository.kt:11-16 既有约定——墓碑行是同步删除的
    // 唯一传播载体，过滤掉即等于删除永不传播）。

    /** 回收站列表（本用户墓碑行，id 序；页面按 lastModified 倒序分页由服务层完成）。 */
    fun findByUsernameAndDeletedTrueOrderById(username: String): List<HistoryInfoEntity>
    /** 到期物理清除输入（deleted=true 且 lastModified < cutoff）。 */
    fun findByDeletedTrueAndLastModifiedBefore(lastModified: Long): List<HistoryInfoEntity>
    /** status 计数：只数存活行（墓碑不计入）。 */
    fun countByUsernameAndDeletedFalse(username: String): Long
    /** v4 服务器打戳：SyncClock 启动水位初始化。 */
    @Query("SELECT COALESCE(MAX(e.lastModified), 0) FROM HistoryInfoEntity e")
    fun maxLastModified(): Long

    /** DB-paginated local history, newest first (empty-keyword fallback). 墓碑行不列进 REST 列表。 */
    @Query("select h from HistoryInfoEntity h where h.deleted = false order by h.time desc")
    fun findHistoryPaged(pageable: Pageable): Page<HistoryInfoEntity>

    /** A7-2（H2）: DB-paginated local history filtered by category, newest first, live rows only. */
    fun findByCategoryAndDeletedFalseOrderByTimeDesc(category: Int, pageable: Pageable): Page<HistoryInfoEntity>

    /**
     * P2: 子串 q 过滤下沉 DB（title/titleJpn LIKE %kw%，大小写不敏感，仅存活行），
     * time 倒序 + DB 分页——替代旧的不分页全表载入 + 内存过滤路径。
     * 已知取舍：q 中的 LIKE 通配符（%/_）不转义，只可能放大匹配面、不会漏配，
     * 与下方既有 keyword 先例查询保持同一行为。
     */
    @Query("""
        select h from HistoryInfoEntity h
        where h.deleted = false
          and ( lower(h.title) like lower(concat('%', :keyword, '%'))
             or lower(h.titleJpn) like lower(concat('%', :keyword, '%')) )
        order by h.time desc
    """)
    fun findLiveByTitleOrTitleJpnContainingPaged(
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<HistoryInfoEntity>

    /** P2: 同上但不分页——仅作 regex 路径的 DB 预过滤窗口（内存 regex 只跑在该集合上）。 */
    @Query("""
        select h from HistoryInfoEntity h
        where h.deleted = false
          and ( lower(h.title) like lower(concat('%', :keyword, '%'))
             or lower(h.titleJpn) like lower(concat('%', :keyword, '%')) )
        order by h.time desc
    """)
    fun findLiveByTitleOrTitleJpnContaining(@Param("keyword") keyword: String): List<HistoryInfoEntity>
}

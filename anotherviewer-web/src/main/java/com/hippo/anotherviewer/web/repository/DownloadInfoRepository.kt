package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * A7-2 软删后的查询面约定：REST 列表/筛选/统计/生命周期遍历只看存活行
 * （deleted=false），墓碑行（deleted=true）是同步删除的唯一传播载体——
 * `findByGid`/`findAllByGid`（merge/复活仲裁）与 `findByUsername*`（pull 载体）必须保持
 * 不过滤，不得给它们加 deleted 条件。
 */
interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByGid(gid: Long): DownloadInfoEntity?
    /**
     * A7-3（P1-1）：同 gid 多行（历史脏数据/属主并存）时单实体 [findByGid] 派生查询
     * 会抛 IncorrectResultSizeDataAccessException，毒化整条同步通道。同步仲裁与
     * 写前查找一律走本 List 版本 + 属主/存活 firstOrNull。
     */
    fun findAllByGid(gid: Long): List<DownloadInfoEntity>
    fun findByLabel(label: Int): List<DownloadInfoEntity>
    /** 按 label 分页（W6 下载列表分页）。A7-2（D1）: 仅存活行。 */
    fun findByLabelAndDeletedFalse(label: Int, pageable: Pageable): Page<DownloadInfoEntity>
    fun countByLabelAndDeletedFalse(label: Int): Long
    /** A7-2（D5/D7）: start-all/pause-all 遍历不触墓碑。 */
    fun findByStateAndDeletedFalse(state: Int): List<DownloadInfoEntity>
    /** COUNT(state) 派生查询：stats 计数不加载实体（Metrics 150-200ms → ms 级）。A7-2（D8）: 不计墓碑。 */
    fun countByStateAndDeletedFalse(state: Int): Long
    /** A7-2（D1）: 分页全量列表（默认路径，替代 findAll+count）。 */
    fun findAllByDeletedFalse(pageable: Pageable): Page<DownloadInfoEntity>
    fun countByDeletedFalse(): Long
    /** A7-2（D6）: restart-all / 维护扫描遍历不触墓碑（id 序，遍历确定性）。 */
    fun findAllByDeletedFalseOrderById(): List<DownloadInfoEntity>
    fun findAllByUsernameIsNull(): List<DownloadInfoEntity>
    fun countByUsername(username: String): Long
    @Transactional
    fun deleteByGid(gid: Long)
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<DownloadInfoEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<DownloadInfoEntity>

    // ── v4 回收站 / status（WP3）──
    // 本文件头部的 A7-2 约定继续成立：以下带 deleted 条件的查询**仅供回收站
    // （list/restore/purge）与 status 计数使用**；merge/pull 查询
    // （findByGid*/findAllByGid/findByUsername*）仍不得加 deleted 条件。

    /** 回收站列表（本用户墓碑行，id 序；页面按 lastModified 倒序分页由服务层完成）。 */
    fun findByUsernameAndDeletedTrueOrderById(username: String): List<DownloadInfoEntity>
    /** 到期物理清除输入（deleted=true 且 lastModified < cutoff）。 */
    fun findByDeletedTrueAndLastModifiedBefore(lastModified: Long): List<DownloadInfoEntity>
    /** status 计数：只数存活行（墓碑不计入）。 */
    fun countByUsernameAndDeletedFalse(username: String): Long
    /** v4 服务器打戳：SyncClock 启动水位初始化。 */
    @Query("SELECT COALESCE(MAX(e.lastModified), 0) FROM DownloadInfoEntity e")
    fun maxLastModified(): Long

    // ── 服务端搜索 + 过滤（label 空/0 → 全部；q 空 → 不过滤）──────────

    private companion object {
        // 标题/标题日文大小写不敏感模糊匹配；label 可空过滤。q 由调用方预转义
        // （%/_/\\ → 带 ESCAPE），避免通配符注入。
        // A7-2（D1/D3）: 搜索/计数/全集 id 投影一律仅存活行。
        const val SEARCH_WHERE = """
            (:label IS NULL OR d.label = :label)
            AND (:q IS NULL OR :q = ''
                 OR LOWER(COALESCE(d.title, '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\'
                 OR LOWER(COALESCE(d.titleJpn, '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\')
            AND d.deleted = false
        """
    }

    /** 分页搜索（q 为空时退化为 label 过滤）。 */
    @Query("SELECT d FROM DownloadInfoEntity d WHERE ${SEARCH_WHERE}")
    fun searchDownloads(
        @Param("label") label: Int?,
        @Param("q") q: String?,
        pageable: Pageable
    ): Page<DownloadInfoEntity>

    @Query("SELECT COUNT(d) FROM DownloadInfoEntity d WHERE ${SEARCH_WHERE}")
    fun countSearchDownloads(@Param("label") label: Int?, @Param("q") q: String?): Long

    /** 全量 id 投影（跨页全选/批量：按当前过滤条件解析全集，不加载实体）。 */
    @Query("SELECT d.id FROM DownloadInfoEntity d WHERE ${SEARCH_WHERE}")
    fun findAllIdsBy(
        @Param("label") label: Int?,
        @Param("q") q: String?,
        pageable: Pageable
    ): Page<Long>

    /** 正则筛选用的轻量投影（id/title/titleJpn/time，SQL 层仅按 label 过滤，
        正则匹配与排序在服务端内存完成——SQLite 无 REGEXP）。A7-2（D2）: 仅存活行。 */
    interface TitleProjection {
        val id: Long
        val title: String?
        val titleJpn: String?
        val time: Long
    }

    @Query("SELECT d.id AS id, d.title AS title, d.titleJpn AS titleJpn, d.time AS time FROM DownloadInfoEntity d WHERE (:label IS NULL OR d.label = :label) AND d.deleted = false")
    fun findTitlesByLabel(@Param("label") label: Int?): List<TitleProjection>
}

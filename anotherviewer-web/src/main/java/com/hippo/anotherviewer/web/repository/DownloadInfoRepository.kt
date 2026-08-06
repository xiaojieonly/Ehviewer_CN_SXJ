package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    // ── 服务端搜索 + 过滤（label 空/0 → 全部；q 空 → 不过滤）──────────

    private companion object {
        // 标题/标题日文大小写不敏感模糊匹配；label 可空过滤。q 由调用方预转义
        // （%/_/\\ → 带 ESCAPE），避免通配符注入。
        const val SEARCH_WHERE = """
            (:label IS NULL OR d.label = :label)
            AND (:q IS NULL OR :q = ''
                 OR LOWER(COALESCE(d.title, '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\'
                 OR LOWER(COALESCE(d.titleJpn, '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\')
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
        正则匹配与排序在服务端内存完成——SQLite 无 REGEXP）。 */
    interface TitleProjection {
        val id: Long
        val title: String?
        val titleJpn: String?
        val time: Long
    }

    @Query("SELECT d.id AS id, d.title AS title, d.titleJpn AS titleJpn, d.time AS time FROM DownloadInfoEntity d WHERE (:label IS NULL OR d.label = :label)")
    fun findTitlesByLabel(@Param("label") label: Int?): List<TitleProjection>
}

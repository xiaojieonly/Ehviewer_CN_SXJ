package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface QuickSearchRepository : JpaRepository<QuickSearchEntity, Long> {
    fun findByName(name: String): QuickSearchEntity?
    fun findAllByOrderById(): List<QuickSearchEntity>
    /** A7-2（Q1）: REST 列表只出存活快搜（墓碑行是同步删除的传播载体，不下发列表）。 */
    fun findAllByDeletedFalseOrderById(): List<QuickSearchEntity>
    fun findAllByUsernameIsNull(): List<QuickSearchEntity>
    fun countByUsername(username: String): Long
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<QuickSearchEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<QuickSearchEntity>

    // ── v4 回收站 / status（WP3）──
    // 注意：以下带 deleted 条件的查询**仅供回收站（list/restore/purge）与 status
    // 计数使用**；merge/pull 查询（findByName*/findByUsername*）仍不得加 deleted
    // 条件（呼应 DownloadInfoRepository.kt:11-16 既有约定——墓碑行是同步删除的
    // 唯一传播载体，过滤掉即等于删除永不传播）。

    /** 回收站列表（本用户墓碑行，id 序；页面按 lastModified 倒序分页由服务层完成）。 */
    fun findByUsernameAndDeletedTrueOrderById(username: String): List<QuickSearchEntity>
    /** 到期物理清除输入（deleted=true 且 lastModified < cutoff）。 */
    fun findByDeletedTrueAndLastModifiedBefore(lastModified: Long): List<QuickSearchEntity>
    /** status 计数：只数存活行（墓碑不计入）。 */
    fun countByUsernameAndDeletedFalse(username: String): Long
    /** v4 服务器打戳：SyncClock 启动水位初始化。 */
    @Query("SELECT COALESCE(MAX(e.lastModified), 0) FROM QuickSearchEntity e")
    fun maxLastModified(): Long
}

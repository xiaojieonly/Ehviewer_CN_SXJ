package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface LocalFavoriteInfoRepository : JpaRepository<LocalFavoriteInfoEntity, Long> {
    fun findByGid(gid: Long): LocalFavoriteInfoEntity?
    /**
     * A7-3（P1-1）：同 gid 多行（历史脏数据/属主并存）时单实体 [findByGid] 派生查询
     * 会抛 IncorrectResultSizeDataAccessException，毒化整条同步通道。同步仲裁与
     * 写前查找一律走本 List 版本 + 属主/存活 firstOrNull。
     */
    fun findAllByGid(gid: Long): List<LocalFavoriteInfoEntity>
    fun findAllByOrderByTimeDesc(): List<LocalFavoriteInfoEntity>
    fun findAllByUsernameIsNull(): List<LocalFavoriteInfoEntity>
    fun countByUsername(username: String): Long
    /** H-3: 同步增量拉取按 (username, lastModified) 走索引查询，避免全表扫描后内存过滤。 */
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<LocalFavoriteInfoEntity>
    /** H-3: 全量拉取 (since=0) —— 必须返回 lastModified=0 的合法记录，故不过滤 lastModified。 */
    fun findByUsername(username: String): List<LocalFavoriteInfoEntity>
    @Transactional
    fun deleteByGid(gid: Long)

    // ── v4 回收站 / status（WP3）──
    // 注意：以下带 deleted 条件的查询**仅供回收站（list/restore/purge）与 status
    // 计数使用**；merge/pull 查询（findByGid*/findByUsername*）仍不得加 deleted
    // 条件（呼应 DownloadInfoRepository.kt:11-16 既有约定——墓碑行是同步删除的
    // 唯一传播载体，过滤掉即等于删除永不传播）。

    /** 回收站列表（本用户墓碑行，id 序；页面按 lastModified 倒序分页由服务层完成）。 */
    fun findByUsernameAndDeletedTrueOrderById(username: String): List<LocalFavoriteInfoEntity>
    /** 到期物理清除输入（deleted=true 且 lastModified < cutoff）。 */
    fun findByDeletedTrueAndLastModifiedBefore(lastModified: Long): List<LocalFavoriteInfoEntity>
    /** status 计数：只数存活行（墓碑不计入）。 */
    fun countByUsernameAndDeletedFalse(username: String): Long
    /** v4 服务器打戳：SyncClock 启动水位初始化。 */
    @Query("SELECT COALESCE(MAX(e.lastModified), 0) FROM LocalFavoriteInfoEntity e")
    fun maxLastModified(): Long

    /**
     * P2: 收藏列表过滤/分页下沉 DB——slot 语义见 FavoriteService.listFavorites
     * （<0 全部；0 = 默认夹 favoriteSlot in (-1, 0)；>0 = 恰好该夹），
     * 墓碑行（deleted = true）由 JPQL 排除，time 倒序 + DB 分页，
     * 替代旧的不分页全表载入 + 内存过滤路径。
     */
    @Query("""
        select f from LocalFavoriteInfoEntity f
        where f.deleted = false
          and ( :slot < 0
             or ( :slot = 0 and f.favoriteSlot in (-1, 0) )
             or ( :slot > 0 and f.favoriteSlot = :slot ) )
        order by f.time desc
    """)
    fun findLiveBySlotPaged(@Param("slot") slot: Int, pageable: Pageable): Page<LocalFavoriteInfoEntity>

    /** P2: 同 findLiveBySlotPaged + 子串 q 下沉（title/titleJpn LIKE %kw%，大小写不敏感）。 */
    @Query("""
        select f from LocalFavoriteInfoEntity f
        where f.deleted = false
          and ( :slot < 0
             or ( :slot = 0 and f.favoriteSlot in (-1, 0) )
             or ( :slot > 0 and f.favoriteSlot = :slot ) )
          and ( lower(f.title) like lower(concat('%', :keyword, '%'))
             or lower(f.titleJpn) like lower(concat('%', :keyword, '%')) )
        order by f.time desc
    """)
    fun findLiveBySlotAndTitlePaged(
        @Param("slot") slot: Int,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<LocalFavoriteInfoEntity>

    /** P2: 同 findLiveBySlotAndTitlePaged 但不分页——仅作 regex 路径的 DB 预过滤窗口。 */
    @Query("""
        select f from LocalFavoriteInfoEntity f
        where f.deleted = false
          and ( :slot < 0
             or ( :slot = 0 and f.favoriteSlot in (-1, 0) )
             or ( :slot > 0 and f.favoriteSlot = :slot ) )
          and ( lower(f.title) like lower(concat('%', :keyword, '%'))
             or lower(f.titleJpn) like lower(concat('%', :keyword, '%')) )
        order by f.time desc
    """)
    fun findLiveBySlotAndTitle(@Param("slot") slot: Int, @Param("keyword") keyword: String): List<LocalFavoriteInfoEntity>

    /** P2: 仅 slot 下沉、不分页——regex 路径在推不出字面种子时的预过滤窗口（语义优先回退）。 */
    @Query("""
        select f from LocalFavoriteInfoEntity f
        where f.deleted = false
          and ( :slot < 0
             or ( :slot = 0 and f.favoriteSlot in (-1, 0) )
             or ( :slot > 0 and f.favoriteSlot = :slot ) )
        order by f.time desc
    """)
    fun findLiveBySlot(@Param("slot") slot: Int): List<LocalFavoriteInfoEntity>
}

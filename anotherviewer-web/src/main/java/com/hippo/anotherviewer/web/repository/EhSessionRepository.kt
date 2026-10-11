package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.EhSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface EhSessionRepository : JpaRepository<EhSessionEntity, Long> {
    fun findByUsername(username: String): EhSessionEntity?
    fun findByUsernameAndLastModifiedGreaterThan(username: String, lastModified: Long): List<EhSessionEntity>
    fun findAllByUsernameIsNull(): List<EhSessionEntity>

    // ── v4 回收站（WP3）──
    // ehSession 是单例「服务器级」会话行：墓碑**不进回收站**（回收站只列 7 类用户实体），
    // 到期墓碑由 RecycleService 的定时清除静默物理删（findByDeletedTrueAndLastModifiedBefore）。

    /** 到期物理清除输入（deleted=true 且 lastModified < cutoff）：ehSession 墓碑静默清除。 */
    fun findByDeletedTrueAndLastModifiedBefore(lastModified: Long): List<EhSessionEntity>
    /** v4 服务器打戳：SyncClock 启动水位初始化。 */
    @Query("SELECT COALESCE(MAX(e.lastModified), 0) FROM EhSessionEntity e")
    fun maxLastModified(): Long
}

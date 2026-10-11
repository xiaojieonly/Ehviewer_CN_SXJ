package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserPreferenceRepository : JpaRepository<UserPreferenceEntity, Long> {
    fun findByUsername(username: String): UserPreferenceEntity?
    /** v4 服务器打戳：SyncClock 启动水位初始化（本表的时间列名为 updatedAt）。 */
    @Query("SELECT COALESCE(MAX(e.updatedAt), 0) FROM UserPreferenceEntity e")
    fun maxLastModified(): Long
}

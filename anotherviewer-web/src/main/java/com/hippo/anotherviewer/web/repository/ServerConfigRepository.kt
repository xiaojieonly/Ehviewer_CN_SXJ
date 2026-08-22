package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ServerConfigRepository : JpaRepository<ServerConfigEntity, String> {
    /** MASTER-2026-08-22 P2：按前缀枚举（sync.prov.* 孤儿键清理用）。 */
    fun findByKeyStartingWith(prefix: String): List<ServerConfigEntity>
}

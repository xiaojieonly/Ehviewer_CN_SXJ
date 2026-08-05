package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

/**
 * ADR-0004: 单条 E-Hentai 登录会话 + 用户设置同步记录（ehSession 单例实体）。
 *
 * 服务器级共享、与 WebUI 用户无关：固定存于 `username = "server"`（SiteSessionManager.EH_SESSION_OWNER），
 * 与行级来源缺省 deviceId 一致，且永不落入 adoptNullOwnership 的认领范围。删除走 tombstone（deleted=true 保留行）。
 *
 * `cookies` 列存 `enc:v1:` 前缀 + security.key 派生密钥（AES-GCM）加密后的 JSON 密文，明文不落盘。
 */
@Entity
@Table(
    name = "eh_session",
    indexes = [
        // 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_eh_session_username_lm", columnList = "username, last_modified"),
    ],
)
class EhSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    /** 单例属主，固定 "server"；unique 约束保证至多一行。 */
    @Column(nullable = false, unique = true, length = 64)
    var username: String = ""

    /** enc:v1: 密文 JSON（List<CookieRecord> 序列化），明文不落盘。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    var cookies: String = ""

    @Column(length = 256)
    var displayName: String? = null

    @Column(length = 2048)
    var avatar: String? = null

    /** 0 = e-hentai.org, 1 = exhentai.org；null = 保持接收端当前选择。 */
    @Column
    var gallerySite: Int? = null

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    /** 最后写入方 deviceId（行级来源，pull 回显）。 */
    @Column(length = 128)
    var updatedBy: String? = null
}

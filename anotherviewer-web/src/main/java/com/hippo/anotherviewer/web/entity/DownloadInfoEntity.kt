package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "download_info",
    indexes = [
        // H-3/W4: 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_download_username_lm", columnList = "username, last_modified"),
        // findByGid / deleteByGid
        Index(name = "idx_download_gid", columnList = "gid"),
        // findByLabel（按标签过滤下载列表）
        Index(name = "idx_download_label", columnList = "label"),
        // findByState（下载状态机轮询热路径）
        Index(name = "idx_download_state", columnList = "state"),
    ],
)
class DownloadInfoEntity : GalleryInfoBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var state: Int = 0

    /** Human-readable failure/cancel reason; null while the task is healthy. */
    @Column(length = 1024)
    var error: String? = null

    @Column(nullable = false)
    var legacy: Int = 0

    @Column(nullable = false)
    var total: Int = 0

    @Column(nullable = false)
    var done: Int = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null

    @Column(nullable = false)
    var label: Int = 0

    @Column(length = 1024)
    var downloadDir: String? = null
}

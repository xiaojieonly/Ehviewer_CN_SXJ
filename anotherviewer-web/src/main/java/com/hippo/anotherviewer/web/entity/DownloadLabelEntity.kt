package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "download_label",
    indexes = [
        // H-3/W4: 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_download_label_username_lm", columnList = "username, last_modified"),
        // findByLabel（标签业务键查重）
        Index(name = "idx_download_label_label", columnList = "label"),
    ],
)
class DownloadLabelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 256)
    var label: String = ""

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

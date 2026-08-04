package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "filter",
    indexes = [
        // H-3/W4: 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_filter_username_lm", columnList = "username, last_modified"),
        // findByText（过滤器业务键查重）
        Index(name = "idx_filter_text", columnList = "text"),
        // findByType / findByTypeAndEnabled（前缀复用）
        Index(name = "idx_filter_type_enabled", columnList = "type, enabled"),
    ],
)
class FilterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 512)
    var text: String = ""

    @Column(nullable = false)
    var type: Int = 0

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

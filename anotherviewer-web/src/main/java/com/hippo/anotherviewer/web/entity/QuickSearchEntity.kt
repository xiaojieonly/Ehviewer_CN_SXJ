package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "quick_search",
    indexes = [
        // H-3/W4: 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_quicksearch_username_lm", columnList = "username, last_modified"),
        // findByName（快搜预设业务键查重）
        Index(name = "idx_quicksearch_name", columnList = "name"),
    ],
)
class QuickSearchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 256)
    var name: String = ""

    @Column(nullable = false)
    var mode: Int = 0

    @Column(nullable = false)
    var category: Int = 0

    @Column(length = 512)
    var keyword: String? = null

    @Column(nullable = false)
    var advanceSearch: Int = 0

    @Column(nullable = false)
    var minRating: Int = 0

    @Column(nullable = false)
    var pageFrom: Int = 0

    @Column(nullable = false)
    var pageTo: Int = 0

    /** Sort order persisted with the preset (W3 R4-11); ddl-auto: update 自动补列, 旧行默认 0 */
    @Column(nullable = false)
    var sort: Int = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

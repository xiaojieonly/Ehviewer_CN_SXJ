package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "bookmark_info",
    indexes = [
        // H-3/W4: 同步 pull 派生查询的 (username, last_modified) 复合索引；ddl-auto: update 自动建索引
        Index(name = "idx_bookmark_username_lm", columnList = "username, last_modified"),
        // findByGid / deleteByGid
        Index(name = "idx_bookmark_gid", columnList = "gid"),
        // findByCategory（书签分类查询）
        Index(name = "idx_bookmark_category", columnList = "category"),
    ],
)
class BookmarkInfoEntity : GalleryInfoBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    /** 软删墓碑: 硬删改软删后行保留, 供增量 pull 传播删除 */
    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null

    @Column(length = 256)
    var note: String? = null
}

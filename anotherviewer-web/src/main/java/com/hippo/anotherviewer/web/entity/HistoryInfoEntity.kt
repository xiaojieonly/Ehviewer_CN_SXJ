package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "history_info",
    indexes = [
        // H-3/W4: 同步 pull 走 findByUsername / findByUsernameAndLastModifiedGreaterThan，
        // (username, last_modified) 复合索引避免全表扫描；ddl-auto: update 自动建索引
        Index(name = "idx_history_username_lm", columnList = "username, last_modified"),
        // findByGid / deleteByGid
        Index(name = "idx_history_gid", columnList = "gid"),
        // findByCategoryOrderByTimeDesc（WebUI 本地历史分类分页）
        Index(name = "idx_history_category_time", columnList = "category, time"),
    ],
)
class HistoryInfoEntity : GalleryInfoBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    /** 阅读模式 (app 端 HistoryInfo.mode), 经 sync 双向同步; ddl-auto: update 自动补列, 无需迁移脚本 */
    @Column(nullable = false)
    var mode: Int = 0

    /** 阅读进度（0 起的页索引，对齐 app 端 HistoryInfo.page / SpiderInfo.startPage）；
     * sync 合并取 max 防旧设备回退。columnDefinition 必须带 default 0：
     * SQLite 拒绝对存量表 ADD COLUMN NOT NULL 无默认值（ddl-auto 补列实测
     * 2026-09-03 生产库被拒），带 default 后存量库/新库两条路径都成立。 */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    var page: Int = 0

    /** 软删墓碑: 硬删改软删后行保留, 供增量 pull 传播删除 */
    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

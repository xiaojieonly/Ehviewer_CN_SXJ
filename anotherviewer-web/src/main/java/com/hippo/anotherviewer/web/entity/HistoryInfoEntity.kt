package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "history_info")
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

    /** 软删墓碑: 硬删改软删后行保留, 供增量 pull 传播删除 */
    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

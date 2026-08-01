package com.hippo.ehviewer.web.entity

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

    @Column(length = 256)
    var username: String? = null
}

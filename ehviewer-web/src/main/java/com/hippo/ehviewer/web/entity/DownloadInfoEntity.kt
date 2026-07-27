package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "download_info")
class DownloadInfoEntity : GalleryInfoBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var state: Int = 0

    @Column(nullable = false)
    var legacy: Int = 0

    @Column(nullable = false)
    var total: Int = 0

    @Column(nullable = false)
    var done: Int = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var label: Int = 0

    @Column(length = 1024)
    var downloadDir: String? = null
}

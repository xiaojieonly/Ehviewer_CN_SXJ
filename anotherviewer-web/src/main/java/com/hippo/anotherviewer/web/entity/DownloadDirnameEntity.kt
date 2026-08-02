package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "download_dirname")
class DownloadDirnameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var gid: Long = 0

    @Column(nullable = false, length = 1024)
    var dirname: String = ""
}

package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "gallery_tags")
class GalleryTagsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var gid: Long = 0

    @Column(nullable = false, length = 128)
    var tag: String = ""

    @Column(length = 256)
    var tagNamespace: String? = null
}

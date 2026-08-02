package com.hippo.anotherviewer.web.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class GalleryInfoBase {
    @Column(nullable = false)
    var gid: Long = 0

    @Column(nullable = false, length = 128)
    var token: String = ""

    @Column(length = 512)
    var title: String? = null

    @Column(length = 512)
    var titleJpn: String? = null

    @Column(length = 2048)
    var thumb: String? = null

    @Column(nullable = false)
    var category: Int = 0

    @Column(length = 64)
    var posted: String? = null

    @Column(length = 256)
    var uploader: String? = null

    @Column(nullable = false)
    var rating: Float = 0f

    @Column(nullable = false)
    var rated: Boolean = false

    @Column(length = 512)
    var simpleLanguage: String? = null

    @Column(columnDefinition = "TEXT")
    var simpleTags: String? = null

    @Column(nullable = false)
    var thumbWidth: Int = 0

    @Column(nullable = false)
    var thumbHeight: Int = 0

    @Column(nullable = false)
    var spanSize: Int = 0

    @Column(nullable = false)
    var spanIndex: Int = 0

    @Column(nullable = false)
    var spanGroupIndex: Int = 0

    @Column(nullable = false)
    var favoriteSlot: Int = -2

    @Column(length = 256)
    var favoriteName: String? = null

    @Column(nullable = false)
    var pages: Int = 0
}

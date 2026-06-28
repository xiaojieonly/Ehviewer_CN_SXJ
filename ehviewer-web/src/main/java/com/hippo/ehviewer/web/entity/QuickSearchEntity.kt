package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "quick_search")
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
}

package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "filter")
class FilterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 512)
    var text: String = ""

    @Column(nullable = false)
    var type: Int = 0

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}

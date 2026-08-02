package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_preference")
class UserPreferenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, unique = true, length = 256)
    var username: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var preferences: String = "{}"

    @Column(nullable = false)
    var updatedAt: Long = System.currentTimeMillis()

    @Column(length = 64)
    var updatedBy: String = ""
}

package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "auth_config")
class AuthConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 256)
    var username: String = ""

    @Column(nullable = false, length = 512)
    var passwordHash: String = ""

    @Column(nullable = false)
    var enabled: Boolean = true

    @Column(nullable = false)
    var createdAt: Long = System.currentTimeMillis()

    @Column(nullable = false)
    var lastLoginAt: Long = 0
}

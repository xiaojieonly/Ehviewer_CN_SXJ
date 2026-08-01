package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "eh_token")
class TokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, unique = true, length = 64)
    var tokenHash: String = ""

    @Column(nullable = false, length = 256)
    var username: String = ""

    @Column(nullable = false)
    var createdAt: Long = 0

    @Column(nullable = false)
    var expiresAt: Long = 0
}

package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "smb_config")
class SmbConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 256)
    var host: String = ""

    @Column(nullable = false)
    var port: Int = 445

    @Column(nullable = false, length = 256)
    var share: String = ""

    @Column(length = 512)
    var path: String? = null

    @Column(nullable = false, length = 64)
    var loginMode: String = "GUEST"

    @Column(length = 256)
    var username: String? = null

    @Column(length = 512)
    var password: String? = null

    @Column(nullable = false)
    var enabled: Boolean = false
}

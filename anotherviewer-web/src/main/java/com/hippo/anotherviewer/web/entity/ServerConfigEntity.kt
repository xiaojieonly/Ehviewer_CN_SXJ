package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "server_config")
class ServerConfigEntity {
    @Id
    @Column(length = 128)
    var key: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var value: String = ""
}

package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "black_list")
class BlackListEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, length = 256)
    var user: String = ""
}

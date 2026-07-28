package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "sync_device")
class SyncDeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, unique = true, length = 128)
    var deviceId: String = ""

    @Column(length = 256)
    var deviceName: String? = null

    @Column(nullable = false, length = 32)
    var platform: String = "other"

    @Column(nullable = false)
    var lastSeen: Long = 0

    @Column(nullable = false)
    var lastSyncTimestamp: Long = 0
}

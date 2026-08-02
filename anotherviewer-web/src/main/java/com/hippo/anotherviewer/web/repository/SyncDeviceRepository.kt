package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SyncDeviceRepository : JpaRepository<SyncDeviceEntity, Long> {
    fun findByDeviceId(deviceId: String): SyncDeviceEntity?
    fun findByToken(token: String): SyncDeviceEntity?
}

package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.SyncDeviceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SyncDeviceRepository : JpaRepository<SyncDeviceEntity, Long> {
    fun findByDeviceId(deviceId: String): SyncDeviceEntity?
}

package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.dto.DeviceInfoDto
import com.hippo.anotherviewer.web.entity.SyncDeviceEntity
import com.hippo.anotherviewer.web.repository.SyncDeviceRepository
import org.springframework.stereotype.Service

@Service
class DeviceService(private val repo: SyncDeviceRepository) {

    fun register(deviceId: String, deviceName: String, platform: String, token: String, username: String) {
        val entity = repo.findByDeviceId(deviceId) ?: SyncDeviceEntity().apply { this.deviceId = deviceId }
        entity.deviceName = deviceName
        entity.platform = platform
        entity.pairedAt = System.currentTimeMillis()
        entity.lastSeen = entity.pairedAt
        entity.token = token
        entity.username = username
        repo.save(entity)
    }

    fun list(): List<DeviceInfoDto> =
        repo.findAll().map {
            DeviceInfoDto(
                deviceId = it.deviceId,
                deviceName = it.deviceName ?: it.deviceId,
                platform = it.platform,
                pairedAt = it.pairedAt,
                lastSeen = it.lastSeen,
            )
        }.sortedByDescending { it.pairedAt }

    fun findToken(deviceId: String): String? = repo.findByDeviceId(deviceId)?.token

    fun delete(deviceId: String) {
        repo.findByDeviceId(deviceId)?.let { repo.delete(it) }
    }
}

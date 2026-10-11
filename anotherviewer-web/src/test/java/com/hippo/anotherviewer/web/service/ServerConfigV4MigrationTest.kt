package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * v4（WP2）策略默认与一次性迁移：
 *  - 出厂默认 = lww；
 *  - 存量库的 `sync.conflict_strategy = device_priority`（旧出厂默认而非用户选择）
 *    首次升级改写成 lww + 打 marker；
 *  - marker 存在即幂等跳过——用户迁移后显式改回 device_priority 不再被改写；
 *  - 已是 lww 的库不动。
 */
class ServerConfigV4MigrationTest {

    /** Real [ServerConfigService] over an in-memory repository fake（同 SyncServiceTest 模式）。 */
    private fun newService(): Pair<ServerConfigService, ConcurrentHashMap<String, ServerConfigEntity>> {
        val repo = mock(ServerConfigRepository::class.java)
        val store = ConcurrentHashMap<String, ServerConfigEntity>()
        `when`(repo.findById(anyString())).thenAnswer { inv ->
            Optional.ofNullable(store[inv.getArgument<String>(0)])
        }
        `when`(repo.save(any(ServerConfigEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<ServerConfigEntity>(0)
            store[e.key] = e
            e
        }
        `when`(repo.existsById(anyString())).thenAnswer { inv -> store.containsKey(inv.getArgument<String>(0)) }
        return ServerConfigService(repo, EncryptionService(), SiteCoreConfigProperties()) to store
    }

    @Test
    fun `fresh install defaults to lww`() {
        val (service, store) = newService()

        service.initDefaults()

        assertEquals("lww", store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY]!!.value)
        assertEquals("true", store[ServerConfigService.KEY_SYNC_V4_STRATEGY_MIGRATED]!!.value, "迁移标记必须落库")
        assertEquals("lww", service.get(ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY))
    }

    @Test
    fun `legacy device_priority is migrated to lww exactly once`() {
        val (service, store) = newService()
        // 存量库：旧出厂默认 device_priority，无 marker。
        store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY] = ServerConfigEntity().apply {
            key = ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY
            value = "device_priority"
        }

        service.initDefaults()

        assertEquals("lww", store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY]!!.value, "存量 device_priority 一次性迁移为 lww")
        assertEquals("true", store[ServerConfigService.KEY_SYNC_V4_STRATEGY_MIGRATED]!!.value)
    }

    @Test
    fun `migration is idempotent and never reverts a user choice back to lww`() {
        val (service, store) = newService()
        store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY] = ServerConfigEntity().apply {
            key = ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY
            value = "device_priority"
        }

        service.initDefaults()
        assertEquals("lww", store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY]!!.value)

        // 用户（或 android push 的权威覆盖）显式改回 device_priority：marker 已存在 →
        // 再次 initDefaults（重启）不得改写。
        service.set(ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY, "device_priority")
        service.initDefaults()

        assertEquals("device_priority", store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY]!!.value, "迁移只做一次，用户选择不被覆盖")
    }

    @Test
    fun `an existing lww value is left untouched`() {
        val (service, store) = newService()
        store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY] = ServerConfigEntity().apply {
            key = ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY
            value = "lww"
        }

        service.initDefaults()

        assertEquals("lww", store[ServerConfigService.KEY_SYNC_CONFLICT_STRATEGY]!!.value)
        assertTrue(store.containsKey(ServerConfigService.KEY_SYNC_V4_STRATEGY_MIGRATED))
    }
}

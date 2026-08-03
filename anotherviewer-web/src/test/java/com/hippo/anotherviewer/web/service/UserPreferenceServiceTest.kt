package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.dto.GeneralPreferences
import com.hippo.anotherviewer.web.dto.PreferenceResponse
import com.hippo.anotherviewer.web.dto.PreferenceUpdateRequest
import com.hippo.anotherviewer.web.dto.ReaderPreferences
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.clearInvocations
import java.util.concurrent.ConcurrentHashMap

/**
 * 契约测试（E2E-1 / N-3）:
 *
 *  - 读取侧容错: 异构偏好串（{"theme":"dark"}、未知嵌套键）经 replace 后
 *    get/update 不抛异常、缺省填充。
 *  - replace 校验/归一: 合法 JSON 对象原样保留（含未知字段），非法内容回退 "{}"。
 *  - LWW: 仅当 incoming.lastModified 明显新于存量 updatedAt（±5s 容忍）或无
 *    存量时覆盖，旧 push 不覆盖新值。
 */
class UserPreferenceServiceTest {

    private lateinit var repo: UserPreferenceRepository
    private lateinit var service: UserPreferenceService

    @BeforeEach
    fun setUp() {
        repo = mock(UserPreferenceRepository::class.java)
        val store = ConcurrentHashMap<String, UserPreferenceEntity>()
        `when`(repo.save(any(UserPreferenceEntity::class.java))).thenAnswer { inv ->
            val e = inv.getArgument<UserPreferenceEntity>(0)
            store[e.username] = e
            e
        }
        `when`(repo.findByUsername(anyString())).thenAnswer { inv -> store[inv.getArgument<String>(0)] }
        service = UserPreferenceService(repo)
    }

    // ---- E2E-1: 读取侧容错 ----

    @Test
    fun `heterogeneous top-level keys survive a round trip without 500`() {
        service.replace("alice", """{"theme":"dark"}""", "android-1", System.currentTimeMillis() + 60_000)

        val resp = service.get("alice")
        // 未知字段被忽略，缺省填充
        assertEquals("light", resp.general.theme)
        assertEquals(2, resp.reader.autoPlayIntervalSec)
    }

    @Test
    fun `unknown nested keys do not break reads`() {
        service.replace(
            "alice",
            """{"general":{"foreignKey":true,"theme":"dark"},"reader":{"unknownField":9},"mystery":42}""",
            "android-1",
            System.currentTimeMillis() + 60_000,
        )

        val resp = service.get("alice")
        // 已知字段仍生效，未知字段被忽略
        assertEquals("dark", resp.general.theme)
        assertEquals(2, resp.reader.autoPlayIntervalSec)
    }

    @Test
    fun `update after a heterogeneous push deep-merges without errors`() {
        service.replace("alice", """{"theme":"dark"}""", "android-1", System.currentTimeMillis() + 60_000)

        val merged = service.update("alice", PreferenceUpdateRequest(general = GeneralPreferences(theme = "dark")), "webui")
        assertEquals("dark", merged.general.theme)

        // 写入的内容仍然可读
        assertEquals("dark", service.get("alice").general.theme)
    }

    // ---- Wave-1 新键（1b/1c）: 缺省填充 + 分节深合并不变 ----

    @Test
    fun `legacy json without wave-1 keys reads back with the new defaults`() {
        service.replace(
            "alice",
            """{"general":{"theme":"dark"},"reader":{"brightness":30}}""",
            "android-1",
            System.currentTimeMillis() + 60_000,
        )

        val resp = service.get("alice")
        assertEquals("grid", resp.general.listMode)
        assertEquals(0, resp.general.defaultFavoriteSlot)
        assertEquals(10, resp.general.recentSearchMax)
        assertEquals("black", resp.reader.backgroundColor)
        assertEquals(1.5, resp.reader.zoomStep)
        assertEquals("slide", resp.reader.pageTransition)
        assertEquals(30, resp.reader.brightness)
    }

    @Test
    fun `wave-1 keys survive a round trip through update`() {
        val merged = service.update(
            "alice",
            PreferenceUpdateRequest(
                general = GeneralPreferences(defaultFavoriteSlot = -2, favoriteSlotNames = "a|b"),
                reader = ReaderPreferences(zoomStep = 2.5, splitWidePages = true, pageTransition = "fade"),
            ),
            "webui",
        )
        assertEquals(-2, merged.general.defaultFavoriteSlot)
        assertEquals("a|b", merged.general.favoriteSlotNames)
        assertEquals(2.5, merged.reader.zoomStep)
        assertEquals(true, merged.reader.splitWidePages)
        assertEquals("fade", merged.reader.pageTransition)

        val reloaded = service.get("alice")
        assertEquals(-2, reloaded.general.defaultFavoriteSlot)
        assertEquals(2.5, reloaded.reader.zoomStep)
        assertEquals("fade", reloaded.reader.pageTransition)
    }

    @Test
    fun `section-level merge keeps untouched reader section with wave-1 keys`() {
        service.update(
            "alice",
            PreferenceUpdateRequest(reader = ReaderPreferences(preloadCount = 6, maxZoom = 9.0)),
            "webui",
        )

        // 只更新 general 节，reader 节（含新键）原样保留
        val merged = service.update(
            "alice",
            PreferenceUpdateRequest(general = GeneralPreferences(showUploader = true)),
            "webui",
        )
        assertEquals(true, merged.general.showUploader)
        assertEquals(6, merged.reader.preloadCount)
        assertEquals(9.0, merged.reader.maxZoom)
    }

    @Test
    fun `unknown keys next to wave-1 keys do not break reads`() {
        service.replace(
            "alice",
            """{"general":{"listMode":"list","futureKey":1},"reader":{"pageTransition":"fade","unknown":2}}""",
            "android-1",
            System.currentTimeMillis() + 60_000,
        )

        val resp = service.get("alice")
        assertEquals("list", resp.general.listMode)
        assertEquals("fade", resp.reader.pageTransition)
        // 其余新键缺省填充
        assertEquals(10, resp.general.recentSearchMax)
        assertEquals(8, resp.reader.dualPageGap)
    }

    @Test
    fun `get falls back to defaults on corrupt stored data`() {
        repo.save(UserPreferenceEntity().apply {
            username = "alice"
            preferences = "not-json{"
            updatedAt = System.currentTimeMillis()
        })

        assertEquals(PreferenceResponse(), service.get("alice"))
    }

    // ---- replace 校验/归一 ----

    @Test
    fun `replace normalizes invalid json to an empty object`() {
        service.replace("alice", "not json", "android-1", System.currentTimeMillis() + 60_000)

        assertEquals("{}", service.getRaw("alice"))
        assertEquals(PreferenceResponse(), service.get("alice"))
    }

    @Test
    fun `replace stores a valid object verbatim including unknown fields`() {
        service.replace("alice", """{"general":{"theme":"dark"},"custom":1}""", "android-1", System.currentTimeMillis() + 60_000)

        assertTrue(service.getRaw("alice").contains("\"custom\":1"))
    }

    // ---- N-3: last-write-wins ----

    @Test
    fun `replace stores even with a tiny lastModified when no row exists`() {
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", 0)

        assertEquals("dark", service.get("alice").general.theme)
    }

    @Test
    fun `replace with a clearly newer lastModified overwrites`() {
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", System.currentTimeMillis())
        clearInvocations(repo)
        service.replace("alice", """{"general":{"theme":"blue"}}""", "android-2", System.currentTimeMillis() + 60_000)

        assertEquals("blue", service.get("alice").general.theme)
    }

    @Test
    fun `replace with an older lastModified does not overwrite`() {
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", System.currentTimeMillis() + 60_000)
        clearInvocations(repo)
        service.replace("alice", """{"general":{"theme":"blue"}}""", "android-2", System.currentTimeMillis() - 60_000)

        // 旧设备后推不覆盖新值
        assertEquals("dark", service.get("alice").general.theme)
        verify(repo, org.mockito.Mockito.never()).save(any(UserPreferenceEntity::class.java))
    }

    // ---- E2E-8: 保留客户端 lastModified，服务器不再重打戳 ----

    @Test
    fun `replace stamps updatedAt with the incoming lastModified (E2E-8)`() {
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", 12_345L)

        assertEquals(12_345L, repo.findByUsername("alice")!!.updatedAt)
    }

    @Test
    fun `replace keeps the LWW gate when stamping the incoming lastModified (E2E-8)`() {
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", 12_345L)
        clearInvocations(repo)
        service.replace("alice", """{"general":{"theme":"blue"}}""", "android-2", 12_000L)

        // LWW 判定不变: 明显旧于存量 updatedAt 的 push 仍被拒
        assertEquals("dark", service.get("alice").general.theme)
        verify(repo, org.mockito.Mockito.never()).save(any(UserPreferenceEntity::class.java))
        assertEquals(12_345L, repo.findByUsername("alice")!!.updatedAt)
    }

    @Test
    fun `replace round-trips the lastModified through getRaw callers (E2E-8)`() {
        val lastModified = 99_000L
        service.replace("alice", """{"general":{"theme":"dark"}}""", "android-1", lastModified)

        val entity = repo.findByUsername("alice")!!
        // SyncService.pull 直接读 updatedAt 作为 preferences.lastModified
        assertEquals(lastModified, entity.updatedAt)
    }
}

package com.hippo.anotherviewer.web.dto

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wave-1 B 组（1b）+ A 组（1c）新增偏好键的 DTO 契约测试:
 *
 *  - 默认值: 6 个 general 键 + 9 个 reader 键逐项核对
 *  - 校验: @field: 注解边界（zoomStep>1、maxZoom≥1、非负约束、
 *    defaultFavoriteSlot -2..9、favoriteSlotNames 长度）
 *  - 容错: 未知键忽略、缺省填充（与 UserPreferenceService 的 mapper
 *    配置一致 —— FAIL_ON_UNKNOWN_PROPERTIES=false，不 brick）
 */
class PreferenceDtoTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    // 与 UserPreferenceService.mapper 相同的容错配置
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // ---- 默认值 ----

    @Test
    fun `general preferences carry the wave-1 B defaults`() {
        val general = GeneralPreferences()
        assertEquals("grid", general.listMode)
        assertEquals(false, general.showUploader)
        assertEquals(false, general.showPostedTime)
        assertEquals(0, general.defaultFavoriteSlot)
        assertEquals("", general.favoriteSlotNames)
        assertEquals(10, general.recentSearchMax)
    }

    @Test
    fun `reader preferences carry the wave-1 A defaults`() {
        val reader = ReaderPreferences()
        assertEquals("black", reader.backgroundColor)
        assertEquals("threeZone", reader.tapZoneScheme)
        assertEquals(true, reader.keyboardPaging)
        assertEquals(1.5, reader.zoomStep)
        assertEquals(5.0, reader.maxZoom)
        assertEquals(8, reader.dualPageGap)
        assertEquals(false, reader.splitWidePages)
        assertEquals(2, reader.preloadCount)
        assertEquals("slide", reader.pageTransition)
    }

    @Test
    fun `full response defaults expose the new keys`() {
        val resp = PreferenceResponse()
        assertEquals("grid", resp.general.listMode)
        assertEquals(10, resp.general.recentSearchMax)
        assertEquals("slide", resp.reader.pageTransition)
    }

    // ---- 校验: 合法边界 ----

    private fun violations(general: GeneralPreferences? = null, reader: ReaderPreferences? = null) =
        validator.validate(PreferenceUpdateRequest(general = general, reader = reader))
            .map { "${it.propertyPath}: ${it.message}" }

    @Test
    fun `new keys accept their boundary values`() {
        assertTrue(
            violations(
                general = GeneralPreferences(
                    defaultFavoriteSlot = -2,
                    favoriteSlotNames = "a|b|c|d|e|f|g|h|i|j",
                    recentSearchMax = 0,
                ),
                reader = ReaderPreferences(
                    zoomStep = 1.01,
                    maxZoom = 1.0,
                    dualPageGap = 0,
                    preloadCount = 0,
                ),
            ).isEmpty(),
        )

        assertTrue(
            violations(
                general = GeneralPreferences(defaultFavoriteSlot = 9),
                reader = ReaderPreferences(maxZoom = 100.0, dualPageGap = 64, preloadCount = 20),
            ).isEmpty(),
        )
    }

    // ---- 校验: 非法值拒绝 ----

    @Test
    fun `defaultFavoriteSlot outside -2 to 9 is rejected`() {
        val tooLow = violations(general = GeneralPreferences(defaultFavoriteSlot = -3))
        val tooHigh = violations(general = GeneralPreferences(defaultFavoriteSlot = 10))
        assertEquals(1, tooLow.size)
        assertEquals(1, tooHigh.size)
        assertTrue(tooLow[0].contains("defaultFavoriteSlot"))
        assertTrue(tooHigh[0].contains("defaultFavoriteSlot"))
    }

    @Test
    fun `negative counters are rejected`() {
        val v = violations(
            general = GeneralPreferences(recentSearchMax = -1),
            reader = ReaderPreferences(dualPageGap = -1, preloadCount = -3),
        )
        assertEquals(3, v.size)
        assertTrue(v.any { it.startsWith("general.recentSearchMax") })
        assertTrue(v.any { it.startsWith("reader.dualPageGap") })
        assertTrue(v.any { it.startsWith("reader.preloadCount") })
    }

    @Test
    fun `zoomStep must be strictly greater than 1`() {
        assertEquals(1, violations(reader = ReaderPreferences(zoomStep = 1.0)).size)
        assertEquals(1, violations(reader = ReaderPreferences(zoomStep = 0.5)).size)
    }

    @Test
    fun `maxZoom must be at least 1`() {
        val v = violations(reader = ReaderPreferences(maxZoom = 0.9))
        assertEquals(1, v.size)
        assertTrue(v[0].contains("maxZoom"))
    }

    @Test
    fun `favoriteSlotNames over 255 chars is rejected`() {
        val v = violations(general = GeneralPreferences(favoriteSlotNames = "x".repeat(256)))
        assertEquals(1, v.size)
        assertTrue(v[0].contains("favoriteSlotNames"))
    }

    // ---- 容错: 未知键与缺省填充 ----

    @Test
    fun `unknown keys in sections are ignored without bricking`() {
        val json = """
            {
              "general": {"listMode": "list", "futureGeneralKey": "x"},
              "reader": {"pageTransition": "fade", "futureReaderKey": 9},
              "futureSection": {}
            }
        """.trimIndent()

        val resp = mapper.readValue(json, PreferenceResponse::class.java)
        assertEquals("list", resp.general.listMode)
        assertEquals("fade", resp.reader.pageTransition)
        // 未知键不影响其余缺省填充
        assertEquals(10, resp.general.recentSearchMax)
        assertEquals(1.5, resp.reader.zoomStep)
    }

    @Test
    fun `legacy json without the new keys fills them with defaults`() {
        // 旧版本写入的偏好串（新键全部缺失）读出后按缺省填充
        val json = """{"general":{"theme":"dark"},"reader":{"brightness":30}}"""

        val resp = mapper.readValue(json, PreferenceResponse::class.java)
        assertEquals("dark", resp.general.theme)
        assertEquals("grid", resp.general.listMode)
        assertEquals(false, resp.general.showUploader)
        assertEquals(0, resp.general.defaultFavoriteSlot)
        assertEquals(10, resp.general.recentSearchMax)
        assertEquals(30, resp.reader.brightness)
        assertEquals("black", resp.reader.backgroundColor)
        assertEquals(true, resp.reader.keyboardPaging)
        assertEquals(2, resp.reader.preloadCount)
    }

    @Test
    fun `new keys round trip through serialization`() {
        val original = PreferenceResponse(
            general = GeneralPreferences(
                listMode = "list",
                showUploader = true,
                showPostedTime = true,
                defaultFavoriteSlot = 3,
                favoriteSlotNames = "主用|备用|||||||",
                recentSearchMax = 0,
            ),
            reader = ReaderPreferences(
                backgroundColor = "white",
                tapZoneScheme = "edgeOnly",
                keyboardPaging = false,
                zoomStep = 2.0,
                maxZoom = 8.0,
                dualPageGap = 0,
                splitWidePages = true,
                preloadCount = 5,
                pageTransition = "none",
            ),
        )

        val roundTripped = mapper.readValue(mapper.writeValueAsString(original), PreferenceResponse::class.java)
        assertEquals(original, roundTripped)
    }
}

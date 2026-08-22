/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.anotherviewer.webui

import android.content.Context
import android.content.SharedPreferences

import com.hippo.anotherviewer.Settings

import org.json.JSONArray
import org.json.JSONObject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PreferenceSyncHelperTest {

    private enum class Store { STR, INT, BOOL }

    private fun JSONObject.stringKeys(): Set<String> {
        val result = HashSet<String>()
        val iterator = keys()
        while (iterator.hasNext()) result.add(iterator.next())
        return result
    }

    private class Spec(
        val jsonKey: String,
        val section: String,
        val prefsKey: String,
        val store: Store,
        val wire: Any,
        val applied: Any,
        val sentinel: Any
    )

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.application
        prefs = context.getSharedPreferences("preference_sync_helper_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val field = Settings::class.java.getDeclaredField("sSettingsPre")
        field.isAccessible = true
        field.set(null, prefs)
    }

    private fun export(): JSONObject =
        JSONObject(PreferenceSyncHelper.exportPreferences(context))

    private fun importJson(json: String) {
        PreferenceSyncHelper.importPreferences(context, json)
    }

    private fun seedStrRaw(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    private fun seedBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
    }

    private fun seedInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
    }

    private fun seed(spec: Spec, value: Any) {
        when (spec.store) {
            Store.STR -> prefs.edit().putString(spec.prefsKey, value.toString()).commit()
            Store.INT -> prefs.edit().putInt(spec.prefsKey, value as Int).commit()
            Store.BOOL -> prefs.edit().putBoolean(spec.prefsKey, value as Boolean).commit()
        }
    }

    private fun storedIs(spec: Spec, expected: Any): Boolean = when (spec.store) {
        Store.STR -> prefs.getString(spec.prefsKey, null) == expected.toString()
        Store.INT -> prefs.contains(spec.prefsKey) && prefs.getInt(spec.prefsKey, 0) == expected
        Store.BOOL -> prefs.contains(spec.prefsKey) && prefs.getBoolean(spec.prefsKey, false) == expected
    }

    private fun sectionDoc(section: String, vararg fields: Pair<String, Any>): String =
        JSONObject().put(section, JSONObject().apply {
            for ((k, v) in fields) put(k, v)
        }).toString()

    private fun buildDoc(specs: List<Spec>, omitIndex: Int): String {
        val root = JSONObject()
        for ((index, s) in specs.withIndex()) {
            if (index == omitIndex) continue
            val sec = root.optJSONObject(s.section) ?: JSONObject().also { root.put(s.section, it) }
            sec.put(s.jsonKey, s.wire)
        }
        return root.toString()
    }

    private val enumSpecs = listOf(
        Spec("theme", "general", "theme", Store.STR, "dark", Settings.THEME_DARK, -99),
        Spec("launchPage", "general", "launch_page", Store.STR, "whats_hot", 2, -99),
        Spec("listMode", "general", "list_mode", Store.STR, "thumb", 1, -99),
        Spec("detailSize", "general", "detail_size", Store.STR, "short", 1, -99),
        Spec("thumbSize", "general", "thumb_size", Store.STR, "small", 2, -99),
        Spec("readingDirection", "reader", "reading_direction", Store.STR, "ltr", 0, -99),
        Spec("pageScaling", "reader", "page_scaling", Store.STR, "fixed", 4, -99),
        Spec("startPosition", "reader", "start_position", Store.STR, "center", 4, -99)
    )

    private val boolSpecs = listOf(
        Spec("themeAutoSwitch", "general", "theme_auto_switch", Store.BOOL, true, true, false),
        Spec("showReadProgress", "general", "show_read_progress", Store.BOOL, false, false, true),
        Spec("showJpnTitle", "general", "show_jpn_title", Store.BOOL, true, true, false),
        Spec("showGalleryPages", "general", "show_gallery_pages", Store.BOOL, true, true, false),
        Spec("showTagTranslations", "general", "show_tag_translations", Store.BOOL, false, false, true),
        Spec("showGalleryComment", "general", "show_gallery_comment", Store.BOOL, false, false, true),
        Spec("showGalleryRating", "general", "show_gallery_rating", Store.BOOL, false, false, true),
        Spec("showEhEvents", "general", "show_eh_events", Store.BOOL, false, false, true),
        Spec("showEhLimits", "general", "show_eh_limits", Store.BOOL, false, false, true),
        Spec("firstPageCover", "reader", "reading_first_page_cover", Store.BOOL, false, false, true),
        Spec("showProgress", "reader", "gallery_show_progress", Store.BOOL, false, false, true),
        Spec("showPageInterval", "reader", "gallery_show_page_interval", Store.BOOL, false, false, true),
        Spec("fullscreen", "reader", "reading_fullscreen", Store.BOOL, false, false, true),
        Spec("enableAnalytics", "privacy", "enable_analytics", Store.BOOL, true, true, false)
    )

    private val numericSpecs = listOf(
        Spec("historyInfoSize", "general", "history_info_size", Store.STR, 55, 55, -99),
        Spec("autoPlayIntervalSec", "reader", "start_transfer_time", Store.INT, 33, 33, -999)
    )

    private val allSpecs: List<Spec> = enumSpecs + boolSpecs + numericSpecs + listOf(
        Spec("pageMode", "reader", "reading_dual_page", Store.BOOL, "dual", true, false)
    )

    @Test
    fun exportDocumentHasExactlyTheWhitelistedSectionsAndKeys() {
        val root = export()

        assertEquals(setOf("general", "reader", "privacy"), root.stringKeys())

        assertEquals(
            setOf(
                "theme", "themeAutoSwitch", "launchPage", "listMode", "showReadProgress",
                "detailSize", "thumbSize", "historyInfoSize", "showJpnTitle", "showGalleryPages",
                "showTagTranslations", "showGalleryComment", "showGalleryRating", "showEhEvents",
                "showEhLimits"
            ),
            root.getJSONObject("general").stringKeys()
        )
        assertEquals(
            setOf(
                "readingDirection", "pageMode", "firstPageCover", "pageScaling", "startPosition",
                "autoPlayIntervalSec", "showProgress", "showPageInterval", "fullscreen", "brightness"
            ),
            root.getJSONObject("reader").stringKeys()
        )
        assertEquals(setOf("enableAnalytics"), root.getJSONObject("privacy").stringKeys())
    }

    @Test
    fun exportDefaultsTable() {
        val expectedGeneral = mapOf<String, Any>(
            "theme" to "light",
            "themeAutoSwitch" to false,
            "launchPage" to "homepage",
            "listMode" to "detail",
            "showReadProgress" to true,
            "detailSize" to "long",
            "thumbSize" to "middle",
            "historyInfoSize" to 100,
            "showJpnTitle" to false,
            "showGalleryPages" to false,
            "showTagTranslations" to true,
            "showGalleryComment" to true,
            "showGalleryRating" to true,
            "showEhEvents" to true,
            "showEhLimits" to true
        )
        val expectedReader = mapOf<String, Any>(
            "readingDirection" to "rtl",
            "pageMode" to "dual",
            "firstPageCover" to true,
            "pageScaling" to "fit",
            "startPosition" to "top_right",
            "autoPlayIntervalSec" to 2,
            "showProgress" to true,
            "showPageInterval" to true,
            "fullscreen" to true,
            "brightness" to 0
        )
        val expectedPrivacy = mapOf<String, Any>("enableAnalytics" to false)

        val root = export()

        for ((key, expected) in expectedGeneral) {
            assertEquals("general.$key", expected, root.getJSONObject("general").opt(key))
        }
        for ((key, expected) in expectedReader) {
            assertEquals("reader.$key", expected, root.getJSONObject("reader").opt(key))
        }
        for ((key, expected) in expectedPrivacy) {
            assertEquals("privacy.$key", expected, root.getJSONObject("privacy").opt(key))
        }
    }

    @Test
    fun exportSeededValuesRoundTripThroughWireRepresentation() {
        for (spec in allSpecs) seed(spec, spec.applied)

        val root = export()

        for (spec in allSpecs) {
            assertEquals(
                "${spec.section}.${spec.jsonKey}",
                spec.wire,
                root.getJSONObject(spec.section).opt(spec.jsonKey)
            )
        }
    }

    @Test
    fun exportBrightnessFollowsCustomLightnessFlag() {
        data class Row(val name: String, val custom: Boolean?, val lightness: Int?, val expected: Int)

        val rows = listOf(
            Row("custom on exports stored value", true, 80, 80),
            Row("custom off exports zero", false, 80, 0),
            Row("unset custom exports zero", null, null, 0),
            Row("custom on without value falls back to default fifty", true, null, 50),
            Row("stored value above clamp is exported raw", true, 500, 500)
        )

        for (row in rows) {
            prefs.edit().clear().commit()
            if (row.custom != null) seedBool("custom_screen_lightness", row.custom)
            if (row.lightness != null) seedInt("screen_lightness", row.lightness)

            val actual = export().getJSONObject("reader").getInt("brightness")

            assertEquals(row.name, row.expected, actual)
        }
    }

    @Test
    fun pullEnumFieldsMapEveryValidTokenAndSkipInvalidOnes() {
        data class Case(val wire: Any, val expectedStored: Int?)
        data class Row(val jsonKey: String, val section: String, val prefsKey: String, val cases: List<Case>)

        val rows = listOf(
            Row("theme", "general", "theme", listOf(
                Case("light", Settings.THEME_LIGHT),
                Case("dark", Settings.THEME_DARK),
                Case("black", Settings.THEME_BLACK),
                Case("sepia", null),
                Case(5, null),
                Case(true, null))),
            Row("launchPage", "general", "launch_page", listOf(
                Case("homepage", 0),
                Case("subscription", 1),
                Case("whats_hot", 2),
                Case("gallery", null))),
            Row("listMode", "general", "list_mode", listOf(
                Case("detail", 0), Case("thumb", 1), Case("grid", null))),
            Row("detailSize", "general", "detail_size", listOf(
                Case("long", 0), Case("short", 1), Case("medium", null))),
            Row("thumbSize", "general", "thumb_size", listOf(
                Case("large", 0), Case("middle", 1), Case("small", 2), Case("huge", null))),
            Row("readingDirection", "reader", "reading_direction", listOf(
                Case("ltr", 0), Case("rtl", 1), Case("vertical", 2), Case("diagonal", null))),
            Row("pageScaling", "reader", "page_scaling", listOf(
                Case("actual", 0), Case("width", 1), Case("height", 2),
                Case("fit", 3), Case("fixed", 4), Case("stretch", null))),
            Row("startPosition", "reader", "start_position", listOf(
                Case("top_left", 0), Case("top_right", 1), Case("bottom_left", 2),
                Case("bottom_right", 3), Case("center", 4), Case("middle", null)))
        )

        for (row in rows) {
            for (case in row.cases) {
                prefs.edit().clear().commit()
                seedStrRaw(row.prefsKey, "-99")

                importJson(sectionDoc(row.section, row.jsonKey to case.wire))

                val expectedStored = case.expectedStored?.toString() ?: "-99"
                assertEquals(
                    "${row.jsonKey} <- ${case.wire}",
                    expectedStored,
                    prefs.getString(row.prefsKey, null)
                )
            }
        }
    }

    @Test
    fun pullPageModeMapsDualSingleAndSkipsUnknownTokens() {
        data class Case(val wire: Any, val expected: Boolean?)

        for (case in listOf(
            Case("dual", true),
            Case("single", false),
            Case("both", null),
            Case("", null)
        )) {
            prefs.edit().clear().commit()
            seedBool("reading_dual_page", false)

            importJson(sectionDoc("reader", "pageMode" to case.wire))

            assertEquals(
                "pageMode <- ${case.wire}",
                case.expected ?: false,
                prefs.getBoolean("reading_dual_page", false)
            )
        }
    }

    @Test
    fun pullBooleanFieldsApplyTrueAndFalse() {
        for (spec in boolSpecs) {
            for (wire in listOf(true, false)) {
                prefs.edit().clear().commit()
                seed(spec, spec.sentinel)

                importJson(sectionDoc(spec.section, spec.jsonKey to wire))

                assertTrue("${spec.jsonKey} <- $wire", storedIs(spec, wire))
            }
        }
    }

    @Test
    fun pullBooleanFieldsAcceptStrictJsonBooleansOnly() {
        val rows = listOf(boolSpecs[0], boolSpecs[10], boolSpecs[13])
        val wrongTyped = listOf<Any>("true", 1, JSONObject.NULL, "false")

        for (spec in rows) {
            for (wire in wrongTyped) {
                prefs.edit().clear().commit()
                seed(spec, spec.sentinel)

                importJson(sectionDoc(spec.section, spec.jsonKey to wire))

                assertTrue(
                    "${spec.jsonKey} must ignore non-boolean $wire",
                    storedIs(spec, spec.sentinel)
                )
            }
        }
    }

    @Test
    fun pullHistoryInfoSizeAcceptsNonNegativeNumbersOnly() {
        data class Case(val wire: Any, val expectedStored: String)

        for (case in listOf(
            Case(250, "250"),
            Case(0, "0"),
            Case(-5, "-99"),
            Case(3.9, "3"),
            Case("250", "-99")
        )) {
            prefs.edit().clear().commit()
            seedStrRaw("history_info_size", "-99")

            importJson(sectionDoc("general", "historyInfoSize" to case.wire))

            assertEquals(
                "historyInfoSize <- ${case.wire}",
                case.expectedStored,
                prefs.getString("history_info_size", null)
            )
        }
    }

    @Test
    fun pullAutoPlayIntervalAcceptsNonNegativeNumbersOnly() {
        data class Case(val wire: Any, val expected: Int)

        for (case in listOf(
            Case(30, 30),
            Case(0, 0),
            Case(-1, -999),
            Case("30", -999),
            Case(2.5, 2)
        )) {
            prefs.edit().clear().commit()
            seedInt("start_transfer_time", -999)

            importJson(sectionDoc("reader", "autoPlayIntervalSec" to case.wire))

            assertEquals(
                "autoPlayIntervalSec <- ${case.wire}",
                case.expected,
                prefs.getInt("start_transfer_time", -1)
            )
        }
    }

    @Test
    fun pullBrightnessSemantics() {
        data class Case(
            val name: String,
            val wire: Any?,
            val seedCustom: Boolean?,
            val seedLightness: Int?,
            val expectedCustom: Boolean,
            val expectedLightness: Int
        )

        val rows = listOf(
            Case("positive enables custom and stores value", 120, null, null, true, 120),
            Case("values above two hundred are clamped", 300, false, 90, true, 200),
            Case("smallest positive is accepted", 1, false, null, true, 1),
            Case("zero disables custom and keeps stored value", 0, true, 90, false, 90),
            Case("negative is ignored entirely", -3, true, 90, true, 90),
            Case("absent is ignored entirely", null, true, 90, true, 90)
        )

        for (case in rows) {
            prefs.edit().clear().commit()
            if (case.seedCustom != null) seedBool("custom_screen_lightness", case.seedCustom)
            if (case.seedLightness != null) seedInt("screen_lightness", case.seedLightness)

            if (case.wire != null) {
                importJson(sectionDoc("reader", "brightness" to case.wire))
            } else {
                importJson(sectionDoc("reader"))
            }

            assertEquals(case.name, case.expectedCustom, prefs.getBoolean("custom_screen_lightness", !case.expectedCustom))
            assertEquals(case.name, case.expectedLightness, prefs.getInt("screen_lightness", -1))
        }
    }

    @Test
    fun pullMissingFieldLeavesStoredValueIntactForEveryKey() {
        for (omit in allSpecs.indices) {
            prefs.edit().clear().commit()
            for (spec in allSpecs) seed(spec, spec.sentinel)

            importJson(buildDoc(allSpecs, omit))

            val omitted = allSpecs[omit]
            assertTrue(
                "omitted ${omitted.jsonKey} must keep sentinel",
                storedIs(omitted, omitted.sentinel)
            )
            val canary = allSpecs[(omit + 1) % allSpecs.size]
            assertTrue(
                "canary ${canary.jsonKey} must be applied by doc of omit=${omitted.jsonKey}",
                storedIs(canary, canary.applied)
            )
        }
    }

    @Test
    fun pullMalformedDocumentsAreNoOps() {
        val malformed = listOf("", "not json{", "\"just a string\"", "[1,2,3]", "null")

        for (json in malformed) {
            prefs.edit().clear().commit()
            seedStrRaw("theme", "-99")
            seedBool("enable_analytics", true)

            importJson(json)

            assertEquals("doc '$json' must not touch theme", "-99", prefs.getString("theme", null))
            assertTrue("doc '$json' must not touch enable_analytics", prefs.getBoolean("enable_analytics", false))
        }
    }

    @Test
    fun pullWrongSectionTypeSkipsSectionButAppliesOthers() {
        prefs.edit().clear().commit()
        for (spec in allSpecs) seed(spec, spec.sentinel)

        val doc = JSONObject()
            .put("general", JSONArray())
            .put("reader", JSONObject().put("pageMode", "single"))
            .put("privacy", JSONObject().put("enableAnalytics", true))
            .toString()

        importJson(doc)

        assertTrue(storedIs(allSpecs.first { it.jsonKey == "pageMode" }, false))
        assertTrue(storedIs(allSpecs.first { it.jsonKey == "enableAnalytics" }, true))
        assertTrue(
            "wrongly typed general section must stay untouched",
            storedIs(enumSpecs[0], -99)
        )
    }

    @Test
    fun pullUnknownSectionsAndKeysAreIgnoredEntirely() {
        prefs.edit().clear().commit()

        val doc = JSONObject()
            .put("general", JSONObject().put("evilKey", "x").put("theme", "dark"))
            .put("admin", true)
            .put("favorites", JSONObject().put("1", "g"))
            .toString()

        importJson(doc)

        assertEquals("only whitelisted keys may land in preferences", setOf("theme"), prefs.all.keys)
    }

    @Test
    fun fullRoundTripRestoresEverySeededValue() {
        for (spec in allSpecs) seed(spec, spec.applied)
        seedBool("custom_screen_lightness", true)
        seedInt("screen_lightness", 77)

        val document = PreferenceSyncHelper.exportPreferences(context)

        prefs.edit().clear().commit()
        importJson(document)

        for (spec in allSpecs) {
            assertTrue("round trip lost ${spec.jsonKey}", storedIs(spec, spec.applied))
        }
        assertEquals(true, prefs.getBoolean("custom_screen_lightness", false))
        assertEquals(77, prefs.getInt("screen_lightness", -1))
    }
}

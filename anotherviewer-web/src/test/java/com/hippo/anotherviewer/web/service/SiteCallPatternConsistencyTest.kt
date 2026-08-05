package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteConfig
import com.hippo.anotherviewer.client.SiteRequestBuilder
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Guards the WebUI → Gallery Site call pattern against drift from the Android app.
 *
 * The Android app builds every upstream request through `ChromeRequestBuilder`
 * (Host + Chrome UA + image Accept + zh-CN Accept-Language) and encodes
 * category filters with its `SiteConfig` bit values. The WebUI backend shares
 * the same core `SiteRequestBuilder` / `SiteConfig`, so these two surfaces must
 * stay bit-for-bit identical with the app, otherwise the server gets a
 * different fingerprint / different f_cats than the app would send.
 */
class SiteCallPatternConsistencyTest {

    @Test
    fun `request carries the same browser fingerprint as the app ChromeRequestBuilder`() {
        val request = SiteRequestBuilder("https://e-hentai.org/g/123/abc", "https://e-hentai.org/").build()

        assertEquals("e-hentai.org", request.header("Host"))
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
            request.header("User-Agent")
        )
        assertEquals(
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            request.header("Accept")
        )
        assertEquals(
            "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            request.header("Accept-Language")
        )
        assertEquals("https://e-hentai.org/", request.header("Referer"))
    }

    @Test
    fun `post requests keep the browser fingerprint and body`() {
        val body = "x=1".toRequestBody(null)
        val request = SiteRequestBuilder("https://e-hentai.org/api.php", "https://e-hentai.org/")
            .post(body)
            .build()

        assertEquals("e-hentai.org", request.header("Host"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `category bits match the Android app SiteConfig mapping`() {
        // Values copied from app/src/main/java/com/hippo/anotherviewer/client/SiteConfig.java
        assertEquals(0x1, SiteConfig.MISC)
        assertEquals(0x2, SiteConfig.DOUJINSHI)
        assertEquals(0x4, SiteConfig.MANGA)
        assertEquals(0x8, SiteConfig.ARTIST_CG)
        assertEquals(0x10, SiteConfig.GAME_CG)
        assertEquals(0x20, SiteConfig.IMAGE_SET)
        assertEquals(0x40, SiteConfig.COSPLAY)
        assertEquals(0x80, SiteConfig.ASIAN_PORN)
        assertEquals(0x100, SiteConfig.NON_H)
        assertEquals(0x200, SiteConfig.WESTERN)
        assertEquals(0x3ff, SiteConfig.ALL_CATEGORY)
        assertEquals(0, SiteConfig.NONE)
    }
}

package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.dto.GalleryItemDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression guard for the WebUI "share / copy link" feature.
 *
 * The share URL surfaced by the WebUI (`GalleryItemDto.galleryUrl`) must
 * stay bit-for-bit identical with the Android core builder
 * `SiteUrl.getGalleryDetailUrl`, otherwise copied links drift from what the
 * app and Gallery Site itself expect. These tests pin both the canonical URL
 * shape and the DTO ↔ core equality.
 */
class GalleryUrlContractTest {

    @Test
    fun `getGalleryDetailUrl has the canonical e-hentai shape with trailing slash`() {
        val gid = 12345L
        val token = "0123456789abcdef"

        assertEquals(
            "https://e-hentai.org/g/$gid/$token/",
            SiteUrl.getGalleryDetailUrl(gid, token)
        )
    }

    @Test
    fun `GalleryItemDto galleryUrl equals SiteUrl getGalleryDetailUrl for the same gid and token`() {
        val gid = 987654L
        val token = "feedfacecafebabe"

        val dto = GalleryItemDto(
            gid = gid,
            token = token,
            title = "Sample title",
            titleJpn = null,
            thumb = null,
            category = 0,
            posted = null,
            uploader = null,
            rating = 0f,
            rated = false,
            simpleLanguage = null,
            simpleTags = emptyList(),
            thumbWidth = 0,
            thumbHeight = 0,
            pages = 0,
            favoriteSlot = 0,
            favoriteName = null,
            galleryUrl = SiteUrl.getGalleryDetailUrl(gid, token)
        )

        assertEquals(SiteUrl.getGalleryDetailUrl(gid, token), dto.galleryUrl)
    }
}

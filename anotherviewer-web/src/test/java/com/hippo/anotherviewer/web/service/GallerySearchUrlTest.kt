package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.ListUrlBuilder
import com.hippo.anotherviewer.web.any
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl

/**
 * Pins the WebUI search → Gallery Site URL mapping for the extended search
 * params (contracts/openapi.yaml GET /api/v1/gallery/search v1.1).
 *
 * The URL must be assembled by the shared Android core [ListUrlBuilder] so
 * the WebUI sends exactly the parameter shape the app produces. These tests
 * cover:
 *  - each extended param -> site param mapping (sort/f_order, pageMin/pageMax
 *    -> f_sp*, minRating -> f_sr*, searchName/Tags/Desc/Torrents -> advsearch
 *    scope flags),
 *  - default behavior staying bit-for-bit unchanged when no extended param
 *    is given,
 *  - E2E-6 failure semantics (unreachable site -> success=false, empty data;
 *    never fabricated fallback results).
 */
class GallerySearchUrlTest {

    private val host = SiteUrl.getHost()

    private fun service(): GalleryService {
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        return GalleryService(
            mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.QuickSearchRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.GalleryTagsRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository::class.java),
            sessionManager,
            mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
            com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
            mock(GalleryLookupService::class.java),
            EhAvailabilityService("https://e-hentai.org", 5000),
            mock(DownloadDirIndex::class.java)
        )
    }

    /** All-default shortcut so each case only spells the param under test. */
    private fun url(
        keyword: String = "alpha",
        category: Int? = null,
        page: Int = 0,
        sort: Int = 0,
        pageMin: Int? = null,
        pageMax: Int? = null,
        minRating: Int = 0,
        searchName: Boolean = false,
        searchTags: Boolean = false,
        searchDesc: Boolean = false,
        searchTorrents: Boolean = false,
        searchTorrentsOnly: Boolean = false,
        searchLowPowerTags: Boolean = false,
        searchDownvotedTags: Boolean = false,
        searchExpunged: Boolean = false,
        disableLanguageFilter: Boolean = false,
        disableUploaderFilter: Boolean = false,
        disableTagFilter: Boolean = false
    ): String = service().buildSearchUrl(
        keyword, category, page, sort, pageMin, pageMax, minRating,
        searchName, searchTags, searchDesc, searchTorrents,
        searchTorrentsOnly, searchLowPowerTags, searchDownvotedTags,
        searchExpunged, disableLanguageFilter, disableUploaderFilter,
        disableTagFilter
    )

    // ------------------------------------------------------------------
    // Default behavior unchanged (no extended param)
    // ------------------------------------------------------------------

    @Test
    fun `defaults build the exact pre-extension URL shape`() {
        assertEquals("${host}?f_search=alpha", url())
    }

    @Test
    fun `defaults with category and page keep the legacy param order`() {
        assertEquals("${host}?f_cats=2&f_search=alpha&page=3", url(category = 2, page = 3))
    }

    @Test
    fun `category zero omits f_cats like before`() {
        assertFalse(url(category = 0).contains("f_cats"))
    }

    @Test
    fun `keyword is URL-encoded`() {
        assertEquals("${host}?f_search=a+b%2Bc", url(keyword = "a b+c"))
    }

    // ------------------------------------------------------------------
    // sort -> f_order (contract: 0/default, 1 posted desc, 2 rating desc, 3 title asc)
    // ------------------------------------------------------------------

    @Test
    fun `sort 1 2 3 map to f_order 1 2 3`() {
        for (sort in 1..3) {
            assertTrue(url(sort = sort).contains("&f_order=$sort"), "sort=$sort -> f_order=$sort")
        }
    }

    @Test
    fun `sort 0 omits f_order`() {
        assertFalse(url(sort = 0).contains("f_order"))
    }

    // ------------------------------------------------------------------
    // category pass-through (exclusion bitmask reaches the URL untouched)
    // ------------------------------------------------------------------

    @Test
    fun `frontend exclusion bitmask reaches f_cats untouched`() {
        for (exclusion in listOf(1, 2, 0x3fd, 0x3fe)) {
            assertTrue(
                url(category = exclusion).contains("f_cats=$exclusion&"),
                "category=$exclusion must reach f_cats unchanged"
            )
        }
    }

    @Test
    fun `excluding every category collapses to the core NONE sentinel`() {
        // 0x3ff ("exclude all") inverts to selected = 0 == SiteUtils.NONE,
        // which ListUrlBuilder renders as "no category filter" — the same
        // limitation the Android app has (all-deselected emits no f_cats).
        // Pin the behavior so a future sentinel change is noticed.
        assertFalse(url(category = 0x3ff).contains("f_cats"))
    }

    // ------------------------------------------------------------------
    // pageMin/pageMax -> f_sp=on + f_spf/f_spt (absent bound not emitted)
    // ------------------------------------------------------------------

    @Test
    fun `pageMin alone emits f_sp on and f_spf only`() {
        val u = url(pageMin = 5)
        assertTrue(u.contains("advsearch=1"), "f_sp needs the advsearch carrier")
        assertTrue(u.contains("f_sp=on"))
        assertTrue(u.contains("f_spf=5"))
        assertFalse(u.contains("f_spt"), "absent upper bound must not be emitted")
    }

    @Test
    fun `pageMax alone emits f_sp on and f_spt only`() {
        val u = url(pageMax = 9)
        assertTrue(u.contains("f_sp=on"))
        assertTrue(u.contains("f_spt=9"))
        assertFalse(u.contains("f_spf"), "absent lower bound must not be emitted")
    }

    @Test
    fun `pageMin zero is a real bound, not absence`() {
        assertTrue(url(pageMin = 0).contains("f_spf=0"))
    }

    // ------------------------------------------------------------------
    // minRating -> f_sr=on & f_srdd
    // ------------------------------------------------------------------

    @Test
    fun `positive minRating emits f_sr on and f_srdd`() {
        val u = url(minRating = 3)
        assertTrue(u.contains("advsearch=1"), "f_sr needs the advsearch carrier")
        assertTrue(u.contains("f_sr=on"))
        assertTrue(u.contains("f_srdd=3"))
    }

    @Test
    fun `minRating zero disables the rating filter`() {
        val u = url(minRating = 0)
        assertFalse(u.contains("f_sr"))
        assertFalse(u.contains("advsearch"))
    }

    // ------------------------------------------------------------------
    // searchName/searchTags/searchDesc/searchTorrents -> advsearch scope flags
    // ------------------------------------------------------------------

    @Test
    fun `each scope flag maps to its own f_s param under advsearch`() {
        val cases = listOf(
            url(searchName = true) to "f_sname",
            url(searchTags = true) to "f_stags",
            url(searchDesc = true) to "f_sdesc",
            url(searchTorrents = true) to "f_storr"
        )
        for ((u, flag) in cases) {
            assertTrue(u.contains("advsearch=1"), "$flag needs advsearch=1")
            assertTrue(u.contains("$flag=on"), "expected $flag=on in $u")
        }
    }

    @Test
    fun `single scope does not leak the other scope flags`() {
        val u = url(searchDesc = true)
        assertFalse(u.contains("f_sname"))
        assertFalse(u.contains("f_stags"))
        assertFalse(u.contains("f_storr"))
    }

    @Test
    fun `no scope flags and no advanced fields means no advsearch at all`() {
        assertFalse(url().contains("advsearch"))
    }

    // ------------------------------------------------------------------
    // combined: full URL shape with every param set
    // ------------------------------------------------------------------

    @Test
    fun `all params together produce the full site URL`() {
        assertEquals(
            "${host}?f_cats=5&f_search=alpha&page=2&f_order=1&advsearch=1" +
                "&f_sname=on&f_stags=on&f_sr=on&f_srdd=4&f_sp=on&f_spf=5&f_spt=9",
            url(
                category = 5, page = 2, sort = 1, pageMin = 5, pageMax = 9,
                minRating = 4, searchName = true, searchTags = true
            )
        )
    }

    // ------------------------------------------------------------------
    // ListUrlBuilder.setQuery parses f_order back (round trip)
    // ------------------------------------------------------------------

    @Test
    fun `setQuery parses f_order and clamps out-of-range values to default`() {
        val lub = ListUrlBuilder()
        lub.setQuery("f_search=alpha&f_order=2")
        assertEquals(2, lub.order)

        lub.setQuery("f_search=alpha&f_order=9")
        assertEquals(0, lub.order)

        lub.setQuery("f_search=alpha")
        assertEquals(0, lub.order)
    }

    @Test
    fun `setQuery one-sided page bound round-trips without emitting the missing side`() {
        val lub = ListUrlBuilder()
        lub.setQuery("f_search=alpha&advsearch=1&f_sp=on&f_spf=5")
        assertEquals(5, lub.pageFrom)
        assertEquals(-1, lub.pageTo)

        val rebuilt = lub.build()
        assertTrue(rebuilt.contains("f_sp=on"))
        assertTrue(rebuilt.contains("f_spf=5"))
        assertFalse(rebuilt.contains("f_spt"), "missing upper bound must not reappear")
    }

    @Test
    fun `setQuery order survives into build`() {
        val lub = ListUrlBuilder()
        lub.setQuery("f_search=alpha&f_order=3")
        assertTrue(lub.build().contains("f_order=3"))
    }

    // ------------------------------------------------------------------
    // E2E-6 failure semantics: unreachable site -> error passed through,
    // no fabricated fallback data
    // ------------------------------------------------------------------

    @Test
    fun `unreachable gallery site yields success=false and empty data`() {
        // The search URL targets the real Gallery Site host (SiteUrl.getHost()
        // -> https://e-hentai.org/); in a sandboxed/offline environment the
        // unreachable path is exercised end-to-end through SiteEngine.
        val response = service().searchGallery("anything", null, 0, 20)

        assertFalse(response.success)
        assertTrue(response.data.isEmpty())
        assertEquals(0, response.total)
    }

    @Test
    fun `unreachable site with extended params still fails instead of falling back`() {
        val response = service().searchGallery(
            "anything", 2, 0, 20,
            sort = 2, pageMin = 1, pageMax = 10, minRating = 3,
            searchName = true, searchTags = true
        )

        assertFalse(response.success)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun `blank keyword with local history answers from the DB without a site round-trip`() {
        val historyRepository = mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java)
        // Non-empty history: the home page serves browsing history locally.
        `when`(historyRepository.findHistoryPaged(any())).thenReturn(
            PageImpl(
                listOf(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply { gid = 1L }),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1,
            )
        )
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        val service = GalleryService(
            historyRepository,
            mock(com.hippo.anotherviewer.web.repository.QuickSearchRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.GalleryTagsRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository::class.java),
            sessionManager,
            mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
            com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
            mock(GalleryLookupService::class.java),
            EhAvailabilityService("https://e-hentai.org", 5000),
            mock(DownloadDirIndex::class.java)
        )

        val response = service.searchGallery(null, null, 0, 20)

        assertTrue(response.success)
        org.mockito.Mockito.verify(historyRepository).findHistoryPaged(any())
    }

    @Test
    fun `blank keyword with empty history falls back to the site latest list`() {
        val historyRepository = mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java)
        `when`(historyRepository.findHistoryPaged(any())).thenReturn(Page.empty())
        val sessionManager = mock(SiteSessionManager::class.java)
        `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
        val service = GalleryService(
            historyRepository,
            mock(com.hippo.anotherviewer.web.repository.QuickSearchRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.GalleryTagsRepository::class.java),
            mock(com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository::class.java),
            sessionManager,
            mock(com.hippo.anotherviewer.web.repository.DownloadInfoRepository::class.java),
            com.hippo.anotherviewer.web.config.SiteCoreConfigProperties(),
            mock(GalleryLookupService::class.java),
            EhAvailabilityService("https://e-hentai.org", 5000),
            mock(DownloadDirIndex::class.java)
        )

        // 历史为空 → 回退站点最新列表；沙箱内站点不可达 → E2E-6 语义 success=false（证明确实触网回退）。
        val response = service.searchGallery(null, null, 0, 20)

        assertFalse(response.success, "empty history must trigger the site fallback, which fails offline")
        assertTrue(response.data.isEmpty())
    }

    // ------------------------------------------------------------------
    // W3 R4-10: higher AdvanceSearchTable bits (backendized)
    // ------------------------------------------------------------------

    @Test
    fun `each higher advance bit maps to its site flag under advsearch`() {
        val cases = listOf(
            url(searchTorrentsOnly = true) to "f_sto=on",
            url(searchLowPowerTags = true) to "f_sdt1=on",
            url(searchDownvotedTags = true) to "f_sdt2=on",
            url(searchExpunged = true) to "f_sh=on",
            url(disableLanguageFilter = true) to "f_sfl=on",
            url(disableUploaderFilter = true) to "f_sfu=on",
            url(disableTagFilter = true) to "f_sft=on"
        )
        for ((built, flag) in cases) {
            assertTrue(built.contains("advsearch=1"), "$flag needs the advsearch carrier: $built")
            assertTrue(built.contains(flag), "missing $flag in $built")
        }
    }

    @Test
    fun `higher bits stay absent when not requested`() {
        val built = url(searchName = true)
        for (flag in listOf("f_sto", "f_sdt1", "f_sdt2", "f_sh", "f_sfl", "f_sfu", "f_sft")) {
            assertFalse(built.contains(flag), "unexpected $flag in $built")
        }
    }

    @Test
    fun `scope and higher bits combine into one advsearch block`() {
        val built = url(
            searchName = true, searchTags = true,
            searchTorrentsOnly = true, searchExpunged = true,
            disableTagFilter = true
        )
        assertTrue(built.contains("f_sname=on"))
        assertTrue(built.contains("f_stags=on"))
        assertTrue(built.contains("f_sto=on"))
        assertTrue(built.contains("f_sh=on"))
        assertTrue(built.contains("f_sft=on"))
        assertEquals(1, Regex("advsearch=1").findAll(built).count(), "single advsearch carrier")
    }
}

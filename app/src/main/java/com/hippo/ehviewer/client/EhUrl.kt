/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.ehviewer.client

import com.hippo.ehviewer.Settings
import com.hippo.network.UrlBuilder
import okhttp3.HttpUrl

/**
 * appurl请求设置
 */
object EhUrl {
    const val SITE_E: Int = 0
    const val SITE_EX: Int = 1

    const val DOMAIN_EX: String = "exhentai.org"
    const val DOMAIN_E: String = "e-hentai.org"
    const val DOMAIN_LOFI: String = "lofi.e-hentai.org"

    val REFERER_EX: String = "https://" + DOMAIN_EX
    val REFERER_E: String = "https://" + DOMAIN_E

    @JvmField
    val HOST_EX: String = REFERER_EX + "/"
    @JvmField
    val HOST_E: String = REFERER_E + "/"

    const val API_SIGN_IN: String = "https://forums.e-hentai.org/index.php?act=Login&CODE=01"

    /**
     * 获取排行榜‘top list’连接
     * @return
     */
    val ehNewsUrl: String = HOST_E + "news.php"

    val API_E: String = HOST_E + "api.php"
    val API_EX: String = HOST_EX + "api.php"

    val homeUrl: String = HOST_E + "home.php"
    val HOME_EX: String = HOST_EX + "home.php"

    const val URL_POPULAR_E: String = "https://e-hentai.org/popular"
    const val URL_POPULAR_EX: String = "https://exhentai.org/popular"
    /**
     * 里站没排行榜入口？？？
     * 妈的绝了
     */
//        switch (Settings.getGallerySite()) {
//            default:
//            case SITE_E:
//                return URL_TOP_LIST_E;
//            case SITE_EX:
//                return URL_TOP_LIST_EX;
//        }

    /**
     * 获取排行榜‘top list’连接
     * @return
     */
    val topListUrl: String = HOST_E + "toplist.php"
    val URL_TOP_LIST_EX: String = HOST_EX + "toplist.php"

    const val URL_IMAGE_SEARCH_E: String = "https://upld.e-hentai.org/image_lookup.php"
    const val URL_IMAGE_SEARCH_EX: String = "https://upld.exhentai.org/upld/image_lookup.php"

    const val URL_SIGN_IN: String = "https://forums.e-hentai.org/index.php?act=Login"
    const val URL_REGISTER: String = "https://forums.e-hentai.org/index.php?act=Reg&CODE=00"
    val URL_FAVORITES_E: String = HOST_E + "favorites.php"
    val URL_FAVORITES_EX: String = HOST_EX + "favorites.php"
    const val URL_FORUMS: String = "https://forums.e-hentai.org/"

    val ORIGIN_EX: String = REFERER_EX
    val ORIGIN_E: String = REFERER_E

    val URL_UCONFIG_E: String = HOST_E + "uconfig.php"
    val URL_UCONFIG_EX: String = HOST_EX + "uconfig.php"

    val URL_MY_TAGS_E: String = HOST_E + "mytags"
    val URL_MY_TAGS_EX: String = HOST_EX + "mytags"

    val URL_WATCHED_E: String = HOST_E + "watched"
    val URL_WATCHED_EX: String = HOST_EX + "watched"

    private const val URL_PREFIX_THUMB_E = "https://ehgt.org/"
    private const val URL_PREFIX_THUMB_EX = "https://exhentai.org/t/"

    @JvmStatic
    fun getGalleryDetailUrl(gid: Long, token: String?): String? {
        return getGalleryDetailUrl(gid, token, 0, false)
    }

    @JvmStatic
    val host: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return HOST_E
                SITE_EX -> return HOST_EX
                else -> return HOST_E
            }
        }

    @JvmStatic
    val myTag: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_MY_TAGS_E
                SITE_EX -> return URL_MY_TAGS_EX
                else -> return URL_MY_TAGS_E
            }
        }

    @JvmStatic
    val favoritesUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_FAVORITES_E
                SITE_EX -> return URL_FAVORITES_EX
                else -> return URL_FAVORITES_E
            }
        }

    @JvmStatic
    val apiUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return API_E
                SITE_EX -> return API_EX
                else -> return API_E
            }
        }

    @JvmStatic
    val referer: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return REFERER_E
                SITE_EX -> return REFERER_EX
                else -> return REFERER_E
            }
        }

    @JvmStatic
    val origin: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return ORIGIN_E
                SITE_EX -> return ORIGIN_EX
                else -> return ORIGIN_E
            }
        }

    @JvmStatic
    val uConfigUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_UCONFIG_E
                SITE_EX -> return URL_UCONFIG_EX
                else -> return URL_UCONFIG_E
            }
        }

    @JvmStatic
    val myTagsUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_MY_TAGS_E
                SITE_EX -> return URL_MY_TAGS_EX
                else -> return URL_MY_TAGS_E
            }
        }

    /**
     * 获取画廊详情地址
     * @param gid
     * @param token
     * @param index
     * @param allComment
     * @return
     */
    @JvmStatic
    fun getGalleryDetailUrl(gid: Long, token: String?, index: Int, allComment: Boolean): String? {
        val builder = UrlBuilder(host + "g/" + gid + '/' + token + '/')
        if (index != 0) {
            builder.addQuery("p", index)
        }
        if (allComment) {
            builder.addQuery("hc", 1)
        }
        return builder.build()
    }

    @JvmStatic
    fun getPageUrl(gid: Long, index: Int, pToken: String?): String {
        return host + "s/" + pToken + '/' + gid + '-' + (index + 1)
    }

    @JvmStatic
    fun getAddFavorites(gid: Long, token: String?): String {
        return host + "gallerypopups.php?gid=" + gid + "&t=" + token + "&act=addfav"
    }

    @JvmStatic
    fun getDownloadArchive(gid: Long, token: String?, or: String): String {
        if (or.isEmpty()) {
            return host + "archiver.php?gid=" + gid + "&token=" + token
        }
        return host + "archiver.php?gid=" + gid + "&token=" + token + "&or=" + or
    }

    fun getTagDefinitionUrl(tag: String): String {
        return "https://ehwiki.org/wiki/" + tag.replace(' ', '_')
    }

    @JvmStatic
    val popularUrl: String
        /**
         * 获取‘favorites’连接
         * @return
         */
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_POPULAR_E
                SITE_EX -> return URL_POPULAR_EX
                else -> return URL_POPULAR_E
            }
        }

    @JvmStatic
    val imageSearchUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_IMAGE_SEARCH_E
                SITE_EX -> return URL_IMAGE_SEARCH_EX
                else -> return URL_IMAGE_SEARCH_E
            }
        }

    @JvmStatic
    val watchedUrl: String
        get() {
            when (Settings.getGallerySite()) {
                SITE_E -> return URL_WATCHED_E
                SITE_EX -> return URL_WATCHED_EX
                else -> return URL_WATCHED_E
            }
        }

    val thumbUrlPrefix: String
        get() {
            when (Settings.getGallerySite()) {
                else ->             //case SITE_E:
                    return URL_PREFIX_THUMB_E
            }
        }

    @JvmStatic
    fun getFixedPreviewThumbUrl(originUrl: String): String {
        val url = HttpUrl.parse(originUrl)
        if (url == null) return originUrl
        val pathSegments = url.pathSegments()
        if (pathSegments.size < 3) return originUrl

        val iterator = pathSegments.listIterator(pathSegments.size)
        // The last segments, like
        // 317a1a254cd9c3269e71b2aa2671fe8d28c91097-260198-640-480-png_250.jpg
        if (!iterator.hasPrevious()) return originUrl
        val lastSegment = iterator.previous()
        // The second last segments, like
        // 7a
        if (!iterator.hasPrevious()) return originUrl
        val secondLastSegment = iterator.previous()
        // The third last segments, like
        // 31
        if (!iterator.hasPrevious()) return originUrl
        val thirdLastSegment = iterator.previous()
        // Check path segments
        if (lastSegment != null && secondLastSegment != null && thirdLastSegment != null && lastSegment.startsWith(
                thirdLastSegment
            )
            && lastSegment.startsWith(secondLastSegment, thirdLastSegment.length)
        ) {
            return thumbUrlPrefix + thirdLastSegment + "/" + secondLastSegment + "/" + lastSegment
        } else {
            return originUrl
        }
    }
}

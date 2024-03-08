/*
 * Copyright 2017 Hippo Seven
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
package com.hippo.network

import android.content.Context
import com.hippo.yorozuya.ObjectUtils
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.*
import java.util.regex.Pattern

/*
 * Created by Hippo on 2017/9/4.
 */ open class CookieRepository(context: Context?, name: String?) : CookieJar {
    private val db: CookieDatabase
    private val map: MutableMap<String, CookieSet>
    @Synchronized
    fun addCookie(cookie: Cookie) {
        // For cookie database
        var toAdd: Cookie? = null
        var toUpdate: Cookie? = null
        var toRemove: Cookie? = null
        var set = map[cookie.domain()]
        if (set == null) {
            set = CookieSet()
            map[cookie.domain()] = set
        }
        if (cookie.expiresAt() <= System.currentTimeMillis()) {
            toRemove = set.remove(cookie)
            // If the cookie is not persistent, it's not in database
            if (toRemove != null && !toRemove.persistent()) {
                toRemove = null
            }
        } else {
            toAdd = cookie
            toUpdate = set.add(cookie)
            // If the cookie is not persistent, it's not in database
            if (!toAdd.persistent()) toAdd = null
            if (toUpdate != null && !toUpdate.persistent()) toUpdate = null
            // Remove the cookie if it updates to null
            if (toAdd == null && toUpdate != null) {
                toRemove = toUpdate
                toUpdate = null
            }
        }
        if (toRemove != null) {
            db.remove(toRemove)
        }
        if (toAdd != null) {
            if (toUpdate != null) {
                db.update(toUpdate, toAdd)
            } else {
                db.add(toAdd)
            }
        }
    }

    fun getCookieHeader(url: HttpUrl): String {
        val cookies = getCookies(url)
        val cookieHeader = StringBuilder()
        var i = 0
        val size = cookies.size
        while (i < size) {
            if (i > 0) {
                cookieHeader.append("; ")
            }
            val cookie = cookies[i]
            cookieHeader.append(cookie?.name()).append('=').append(cookie?.value())
            i++
        }
        return cookieHeader.toString()
    }

    @Synchronized
    fun getCookies(url: HttpUrl): MutableList<Cookie?> {
        val accepted: MutableList<Cookie?> = ArrayList()
        val expired: MutableList<Cookie?> = ArrayList()
        for ((domain, cookieSet) in map) {
            if (domainMatch(url, domain)) {
                cookieSet[url, accepted, expired]
            }
        }
        for (cookie in expired) {
            if (cookie != null) {
                if (cookie.persistent()) {
                    db.remove(cookie)
                }
            }
        }

        // RFC 6265 Section-5.4 step 2, sort the cookie-list
        // Cookies with longer paths are listed before cookies with shorter paths.
        // Ignore creation-time, we don't store them.
        Collections.sort(accepted) { o1, o2 -> o2!!.path().length - o1!!.path().length }
        return accepted
    }

    //mystery
    fun contains(url: HttpUrl, name: String?): Boolean {
        for (cookie in getCookies(url)) {
            if (ObjectUtils.equal(cookie?.name(), name)) {
                return true
            }
        }
        return false
    }

    /**
     * Remove all cookies in this `CookieRepository`.
     */
    @Synchronized
    fun clear() {
        map.clear()
        db.clear()
    }

    @Synchronized
    fun close() {
        db.close()
    }

    override fun saveFromResponse(httpUrl: HttpUrl, list: List<Cookie>) {
        for (cookie in list) {
            addCookie(cookie)
        }
    }

    override fun loadForRequest(httpUrl: HttpUrl): List<Cookie?> {
        return getCookies(httpUrl)
    }

    init {
        db = CookieDatabase(context, name)
        map = db.allCookies as MutableMap<String, CookieSet>
    }

    companion object {
        /**
         * Quick and dirty pattern to differentiate IP addresses from hostnames. This is an approximation
         * of Android's private InetAddress#isNumeric API.
         *
         * This matches IPv6 addresses as a hex string containing at least one colon, and possibly
         * including dots after the first colon. It matches IPv4 addresses as strings containing only
         * decimal digits and dots. This pattern matches strings like "a:.23" and "54" that are neither IP
         * addresses nor hostnames; they will be verified as IP addresses (which is a more strict
         * verification).
         */
        private val VERIFY_AS_IP_ADDRESS =
            Pattern.compile("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)")

        /**
         * Returns true if `host` is not a host name and might be an IP address.
         */
        private fun verifyAsIpAddress(host: String): Boolean {
            return VERIFY_AS_IP_ADDRESS.matcher(host).matches()
        }

        // okhttp3.Cookie.domainMatch(HttpUrl, String)
        @JvmStatic
        protected fun domainMatch(url: HttpUrl, domain: String): Boolean {
            val urlHost = url.host()
            if (urlHost == domain) {
                return true // As in 'example.com' matching 'example.com'.
            }
            return if (urlHost.endsWith(domain) && urlHost[urlHost.length - domain.length - 1] == '.' && !verifyAsIpAddress(
                    urlHost
                )
            ) {
                true // As in 'example.com' matching 'www.example.com'.
            } else false
        }
    }
}
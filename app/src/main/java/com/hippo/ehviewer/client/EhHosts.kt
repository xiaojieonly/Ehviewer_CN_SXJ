/*
 * Copyright 2018 Hippo Seven
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

import android.content.Context
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Hosts
import com.hippo.ehviewer.Settings
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Arrays
import java.util.Collections
import java.util.Random

/*
 * Created by Hippo on 2018/3/23.
 */

class EhHosts(context: Context) : Dns {
    private val hosts: Hosts = EhApplication.getHosts(context)
    private val dnsOverHttps: DnsOverHttps
    init {
        val builder = DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().cache(EhApplication.getOkHttpCache(context)).build())
            .url(HttpUrl.get("https://77.88.8.1/dns-query"))
        dnsOverHttps = builder.post(true).build()
    }

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): MutableList<InetAddress?> {
        var inetAddresses = hosts.getList(hostname)
        if (inetAddresses != null && !inetAddresses.isEmpty()) {
            Collections.shuffle(inetAddresses, Random(System.currentTimeMillis()))
            return inetAddresses
        }
        if (Settings.getBuiltInHosts() || Settings.getBuiltEXHosts()) {
            inetAddresses = builtInHosts[hostname]
            if (inetAddresses != null) {
                Collections.shuffle(inetAddresses, Random(System.currentTimeMillis()))
                return inetAddresses
            }
        }
        if (Settings.getDoH()) {
            inetAddresses = dnsOverHttps.lookup(hostname)
            if (!inetAddresses.isEmpty()) {
                Collections.shuffle(inetAddresses, Random(System.currentTimeMillis()))
                return inetAddresses
            }
        }
        try {
            inetAddresses = listOf(*InetAddress.getAllByName(hostname))
            Collections.shuffle(inetAddresses, Random(System.currentTimeMillis()))
            return inetAddresses
        } catch (e: NullPointerException) {
            val unknownHostException =
                UnknownHostException("Broken system behaviour for dns lookup of $hostname")
            unknownHostException.initCause(e)
            throw unknownHostException
        }
    }

    companion object {
        private val builtInHosts: MutableMap<String?, MutableList<InetAddress?>?>

        init {
            val map: MutableMap<String?, MutableList<InetAddress?>?> =
                HashMap()
            if (Settings.getBuiltInHosts()) {
                put(
                    map, "e-hentai.org",
                    "104.20.18.168",
                    "104.20.19.168",
                    "172.66.132.196",
                    "172.66.140.62",
                    "104.20.18.168",
                    "172.67.2.238" //                    "194.126.173.115"
                )
                put(
                    map, "repo.e-hentai.org", "104.20.18.168",
                    "104.20.19.168",
                    "172.67.2.238"
                )
                put(
                    map, "forums.e-hentai.org", "172.66.132.196",
                    "172.66.140.62"
                )
                put(map, "upld.e-hentai.org", "89.149.221.236", "95.211.208.236")
                put(
                    map, "ehgt.org",
                    "109.236.85.28",
                    "62.112.8.21",
                    "89.39.106.43",
                    "2a00:7c80:0:123::3a85",
                    "2a00:7c80:0:12d::38a1",
                    "2a00:7c80:0:13b::37a4"
                )
                //            put(map, "ehgt.org",
//                    "109.236.85.28",
//                    "62.112.8.21",
//                    "89.39.106.43");
                put(
                    map,
                    "raw.githubusercontent.com",
                    "151.101.0.133",
                    "151.101.64.133",
                    "151.101.128.133",
                    "151.101.192.133"
                )
            }

            if (Settings.getBuiltEXHosts()) {
                put(
                    map, "exhentai.org",
                    "178.175.128.251",
                    "178.175.128.252",
                    "178.175.128.253",
                    "178.175.128.254",
                    "178.175.129.251",
                    "178.175.129.252",
                    "178.175.129.253",
                    "178.175.129.254",
                    "178.175.132.19",
                    "178.175.132.20",
                    "178.175.132.21",
                    "178.175.132.22"
                )
                put(
                    map,
                    "upld.exhentai.org",
                    "178.175.132.22",
                    "178.175.129.254",
                    "178.175.128.254"
                )
                put(
                    map, "s.exhentai.org",  //                    "178.175.129.251",
                    //                    "178.175.129.252",
                    "178.175.129.253",
                    "178.175.129.254",  //                    "178.175.128.251",
                    //                    "178.175.128.252",
                    "178.175.128.253",
                    "178.175.128.254",  //                    "178.175.132.19",
                    //                    "178.175.132.20",
                    "178.175.132.21",
                    "178.175.132.22"
                )
            }

            builtInHosts = map
        }



        private fun put(
            map: MutableMap<String?, MutableList<InetAddress?>?>,
            host: String?,
            vararg ips: String?
        ) {
            val addresses: MutableList<InetAddress?> = ArrayList<InetAddress?>()
            for (ip in ips) {
                addresses.add(Hosts.toInetAddress(host, ip))
            }
            map[host] = addresses
        }
    }
}

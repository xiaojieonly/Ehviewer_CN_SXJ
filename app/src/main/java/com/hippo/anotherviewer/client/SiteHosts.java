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

package com.hippo.anotherviewer.client;

/*
 * Created by Hippo on 2018/3/23.
 */

import android.content.Context;

import androidx.annotation.NonNull;

import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.Hosts;
import com.hippo.anotherviewer.Settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;


public class SiteHosts implements Dns {

    private static final Map<String, List<InetAddress>> builtInHosts;

    static {
        Map<String, List<InetAddress>> map = new HashMap<>();
        if (Settings.getBuiltInHosts()) {
            put(map, "gallery.test",
                    "104.20.18.168",
                    "104.20.19.168",
                    "172.66.132.196",
                    "172.66.140.62",
                    "104.20.18.168",
                    "172.67.2.238"
//                    "194.126.173.115"
            );
            put(map, "repo.gallery.test",   "104.20.18.168",
                    "104.20.19.168",
                    "172.67.2.238");
            put(map, "forums.gallery.test",   "172.66.132.196",
                    "172.66.140.62");
            put(map, "upld.gallery.test", "89.149.221.236", "95.211.208.236");
            put(map, "gallery.test",
                    "109.236.85.28",
                    "62.112.8.21",
                    "89.39.106.43",
                    "2a00:7c80:0:123::3a85",
                    "2a00:7c80:0:12d::38a1",
                    "2a00:7c80:0:13b::37a4");
//            put(map, "gallery.test",
//                    "109.236.85.28",
//                    "62.112.8.21",
//                    "89.39.106.43");

        }

        if (Settings.getBuiltEXHosts()) {
            put(map, "gallery.test",
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
            );
            put(map, "upld.gallery.test", "178.175.132.22", "178.175.129.254", "178.175.128.254");
            put(map, "s.gallery.test",
//                    "178.175.129.251",
//                    "178.175.129.252",
                    "178.175.129.253",
                    "178.175.129.254",
//                    "178.175.128.251",
//                    "178.175.128.252",
                    "178.175.128.253",
                    "178.175.128.254",
//                    "178.175.132.19",
//                    "178.175.132.20",
                    "178.175.132.21",
                    "178.175.132.22"
            );
        }

        builtInHosts = map;
    }

    private final Hosts hosts;
    private static DnsOverHttps dnsOverHttps;

    public SiteHosts(Context context) {
        hosts = SiteApplication.getHosts(context);
        DnsOverHttps.Builder builder = new DnsOverHttps.Builder()
                .client(new OkHttpClient.Builder().cache(SiteApplication.getOkHttpCache(context)).build())
                .url(HttpUrl.get("https://77.88.8.1/dns-query"));
        dnsOverHttps = builder.post(true).build();
    }

    private static void put(Map<String, List<InetAddress>> map, String host, String... ips) {
        List<InetAddress> addresses = new ArrayList<>();
        for (String ip : ips) {
            addresses.add(Hosts.toInetAddress(host, ip));
        }
        map.put(host, addresses);
    }


    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        List<InetAddress> inetAddresses = hosts.getList(hostname);
        if (inetAddresses != null && !inetAddresses.isEmpty()) {
            Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
            return inetAddresses;
        }
        if (Settings.getBuiltInHosts() || Settings.getBuiltEXHosts()) {
            inetAddresses = builtInHosts.get(hostname);
            if (inetAddresses != null) {
                Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
                return inetAddresses;
            }
        }
        if (Settings.getDoH()) {
            inetAddresses = dnsOverHttps.lookup(hostname);
            if (!inetAddresses.isEmpty()) {
                Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
                return inetAddresses;
            }
        }
        try {
            inetAddresses = Arrays.asList(InetAddress.getAllByName(hostname));
            Collections.shuffle(inetAddresses, new Random(System.currentTimeMillis()));
            return inetAddresses;
        } catch (NullPointerException e) {
            UnknownHostException unknownHostException =
                    new UnknownHostException("Broken system behaviour for dns lookup of " + hostname);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}

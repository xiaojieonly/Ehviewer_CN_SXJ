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

package com.hippo.ehviewer.client;

/*
 * Created by Hippo on 2018/3/23.
 */

import android.content.Context;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.Hosts;
import com.hippo.ehviewer.Settings;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class EhDns implements Dns {

  private static final Map<String, InetAddress> builtInHosts;

  static {
    Map<String, InetAddress> map = new HashMap<>();
    put(map, "e-hentai.org", "104.20.26.25");
    put(map, "exhentai.org", "178.175.132.20");
    put(map, "repo.e-hentai.org", "94.100.29.73");
    put(map, "forums.e-hentai.org", "94.100.18.243");
    put(map, "ehgt.org", "178.162.139.24");
    put(map, "ul.ehgt.org", "94.100.24.82");
    put(map, "github.com", "192.30.255.112");
    put(map, "raw.githubusercontent.com", "151.101.0.133");
    builtInHosts = map;
  }

  private static void put(Map<String, InetAddress> map, String host, String ip) {
    InetAddress address = Hosts.toInetAddress(host, ip);
    if (address != null) {
      map.put(host, address);
    }
  }

  private final Hosts hosts;
  private static DnsOverHttps dnsOverHttps;

//  public EhDns(Context context) {
//    hosts = EhApplication.getHosts(context);
//  }
  public EhDns(Context context) {
    hosts = EhApplication.getHosts(context);
    DnsOverHttps.Builder builder = new DnsOverHttps.Builder()
            .client(new OkHttpClient.Builder().cache(EhApplication.getOkHttpCache(context)).build())
            .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"));
    try {
      builder.bootstrapDnsHosts(InetAddress.getByName("162.159.36.1"),
              InetAddress.getByName("162.159.46.1"),
              InetAddress.getByName("1.1.1.1"),
              InetAddress.getByName("1.0.0.1"),
              InetAddress.getByName("162.159.132.53"),
              InetAddress.getByName("2606:4700:4700::1111"),
              InetAddress.getByName("2606:4700:4700::1001"),
              InetAddress.getByName("2606:4700:4700::0064"),
              InetAddress.getByName("2606:4700:4700::6400"));
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    dnsOverHttps = builder.post(true).build();
  }

  @Override
  public List<InetAddress> lookup(String hostname) throws UnknownHostException {
    if (hostname == null) throw new UnknownHostException("hostname == null");

    InetAddress inetAddress = hosts.get(hostname);
    if (inetAddress != null) {
      return Collections.singletonList(inetAddress);
    }

    if (Settings.getBuiltInHosts()) {
      inetAddress = builtInHosts.get(hostname);
      if (inetAddress != null) {
        return Collections.singletonList(inetAddress);
      }
    }

    if (Settings.getDoH()) {
      List<InetAddress> inetAddresses = dnsOverHttps.lookup(hostname);
      if (inetAddresses != null && inetAddresses.size() > 0) {
        return inetAddresses;
      }
    }
    try {
      return Arrays.asList(InetAddress.getAllByName(hostname));
    } catch (NullPointerException e) {
      UnknownHostException unknownHostException =
          new UnknownHostException("Broken system behaviour for dns lookup of " + hostname);
      unknownHostException.initCause(e);
      throw unknownHostException;
    }
  }
}

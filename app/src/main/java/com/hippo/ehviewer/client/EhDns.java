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

public class EhDns implements Dns {

  private static final Map<String, InetAddress> builtInHosts;

  static {
    Map<String, InetAddress> map = new HashMap<>();
    put(map, "e-hentai.org", "104.20.26.25");
    put(map, "repo.e-hentai.org", "94.100.29.73");
    put(map, "forums.e-hentai.org", "94.100.18.243");
    put(map, "ehgt.org", "81.171.14.118");
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

  public EhDns(Context context) {
    hosts = EhApplication.getHosts(context);
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

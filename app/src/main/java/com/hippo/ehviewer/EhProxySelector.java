/*
 * Copyright 2019 Hippo Seven
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

package com.hippo.ehviewer;

import android.text.TextUtils;
import com.hippo.network.InetValidator;
import com.hippo.util.ExceptionUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class EhProxySelector extends ProxySelector {

  public static final int TYPE_DIRECT = 0;
  public static final int TYPE_SYSTEM = 1;
  public static final int TYPE_HTTP = 2;
  public static final int TYPE_SOCKS = 3;

  private ProxySelector delegation;
  private ProxySelector alternative;

  EhProxySelector() {
    alternative = ProxySelector.getDefault();
    if (alternative == null) {
      alternative = new NullProxySelector();
    }

    updateProxy();
  }

  public void updateProxy() {
    switch (Settings.getProxyType()) {
      case TYPE_DIRECT:
        delegation = new NullProxySelector();
        break;
      default:
      case TYPE_SYSTEM:
        delegation = alternative;
        break;
      case TYPE_HTTP:
      case TYPE_SOCKS:
        delegation = null;
        break;
    }
  }

  @Override
  public List<Proxy> select(URI uri) {
    int type = Settings.getProxyType();
    if (type == TYPE_HTTP || type == TYPE_SOCKS) {
      try {
        String ip = Settings.getProxyIp();
        int port = Settings.getProxyPort();
        if (!TextUtils.isEmpty(ip) && InetValidator.isValidInetPort(port)) {
          InetAddress inetAddress = InetAddress.getByName(ip);
          SocketAddress socketAddress = new InetSocketAddress(inetAddress, port);
          return Collections.singletonList(new Proxy(type == TYPE_HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS, socketAddress));
        }
      } catch (Throwable t) {
        ExceptionUtils.throwIfFatal(t);
      }
    }

    if (delegation != null) {
      return delegation.select(uri);
    }

    return alternative.select(uri);
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    if (delegation != null) {
      delegation.select(uri);
    }
  }

  private static class NullProxySelector extends ProxySelector {
    @Override
    public List<Proxy> select(URI uri) {
      return Collections.singletonList(Proxy.NO_PROXY);
    }
    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) { }
  }
}

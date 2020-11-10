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

package com.hippo.network;

/*
 * Created by Hippo on 2017/9/4.
 */

import android.content.Context;
import com.hippo.yorozuya.ObjectUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class CookieRepository implements CookieJar {

  private CookieDatabase db;
  private Map<String, CookieSet> map;

  public CookieRepository(Context context, String name) {
    db = new CookieDatabase(context, name);
    map = db.getAllCookies();
  }

  public synchronized void addCookie(Cookie cookie) {
    // For cookie database
    Cookie toAdd = null;
    Cookie toUpdate = null;
    Cookie toRemove = null;

    CookieSet set = map.get(cookie.domain());
    if (set == null) {
      set = new CookieSet();
      map.put(cookie.domain(), set);
    }

    if (cookie.expiresAt() <= System.currentTimeMillis()) {
      toRemove = set.remove(cookie);
      // If the cookie is not persistent, it's not in database
      if (toRemove != null && !toRemove.persistent()) {
        toRemove = null;
      }
    } else {
      toAdd = cookie;
      toUpdate = set.add(cookie);
      // If the cookie is not persistent, it's not in database
      if (!toAdd.persistent()) toAdd = null;
      if (toUpdate != null && !toUpdate.persistent()) toUpdate = null;
      // Remove the cookie if it updates to null
      if (toAdd == null && toUpdate != null) {
        toRemove = toUpdate;
        toUpdate = null;
      }
    }

    if (toRemove != null) {
      db.remove(toRemove);
    }
    if (toAdd != null) {
      if (toUpdate != null) {
        db.update(toUpdate, toAdd);
      } else {
        db.add(toAdd);
      }
    }
  }

  public String getCookieHeader(HttpUrl url) {
    List<Cookie> cookies = getCookies(url);
    StringBuilder cookieHeader = new StringBuilder();
    for (int i = 0, size = cookies.size(); i < size; i++) {
      if (i > 0) {
        cookieHeader.append("; ");
      }
      Cookie cookie = cookies.get(i);
      cookieHeader.append(cookie.name()).append('=').append(cookie.value());
    }
    return cookieHeader.toString();
  }

  public synchronized List<Cookie> getCookies(HttpUrl url) {
    List<Cookie> accepted = new ArrayList<>();
    List<Cookie> expired = new ArrayList<>();

    for (Map.Entry<String, CookieSet> entry : map.entrySet()) {
      String domain = entry.getKey();
      CookieSet cookieSet = entry.getValue();
      if (domainMatch(url, domain)) {
        cookieSet.get(url, accepted, expired);
      }
    }

    for (Cookie cookie : expired) {
      if (cookie.persistent()) {
        db.remove(cookie);
      }
    }

    // RFC 6265 Section-5.4 step 2, sort the cookie-list
    // Cookies with longer paths are listed before cookies with shorter paths.
    // Ignore creation-time, we don't store them.
    Collections.sort(accepted, new Comparator<Cookie>() {
      @Override
      public int compare(Cookie o1, Cookie o2) {
        return o2.path().length() - o1.path().length();
      }
    });

    return accepted;
  }

  public boolean contains(HttpUrl url, String name) {
    for (Cookie cookie : getCookies(url)) {
      if (ObjectUtils.equal(cookie.name(), name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Remove all cookies in this {@code CookieRepository}.
   */
  public synchronized void clear() {
    map.clear();
    db.clear();
  }

  public synchronized void close() {
    db.close();
  }

  @Override
  public void saveFromResponse(HttpUrl httpUrl, List<Cookie> list) {
    for (Cookie cookie : list) {
      addCookie(cookie);
    }
  }

  @Override
  public List<Cookie> loadForRequest(HttpUrl httpUrl) {
    return getCookies(httpUrl);
  }

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
  private static final Pattern VERIFY_AS_IP_ADDRESS = Pattern.compile("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)");

  /**
   * Returns true if `host` is not a host name and might be an IP address.
   */
  private static boolean verifyAsIpAddress(String host) {
    return VERIFY_AS_IP_ADDRESS.matcher(host).matches();
  }

  // okhttp3.Cookie.domainMatch(HttpUrl, String)
  protected static boolean domainMatch(HttpUrl url, String domain) {
    String urlHost = url.host();

    if (urlHost.equals(domain)) {
      return true; // As in 'example.com' matching 'example.com'.
    }

    if (urlHost.endsWith(domain)
        && urlHost.charAt(urlHost.length() - domain.length() - 1) == '.'
        && !verifyAsIpAddress(urlHost)) {
      return true; // As in 'example.com' matching 'www.example.com'.
    }

    return false;
  }
}

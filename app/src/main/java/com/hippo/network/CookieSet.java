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

import com.hippo.util.HashCodeUtils;
import com.hippo.yorozuya.ObjectUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

class CookieSet {

  private Map<Key, Cookie> map = new HashMap<>();

  /**
   * Adds a cookie to this {@code CookieSet}.
   * Returns a previous cookie with
   * the same name, domain and path or {@code null}.
   */
  public Cookie add(Cookie cookie) {
    return map.put(new Key(cookie), cookie);
  }

  /**
   * Removes a cookie with the same name,
   * domain and path as the cookie.
   * Returns the removed cookie or {@code null}.
   */
  public Cookie remove(Cookie cookie) {
    return map.remove(new Key(cookie));
  }

  /**
   * Get cookies for the url. Fill {@code accepted} and {@code expired}.
   */
  public void get(HttpUrl url, List<Cookie> accepted, List<Cookie> expired) {
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<Key, Cookie>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Cookie cookie = iterator.next().getValue();
      if (cookie.expiresAt() <= now) {
        iterator.remove();
        expired.add(cookie);
      } else if (cookie.matches(url)) {
        accepted.add(cookie);
      }
    }
  }

  static class Key {

    private String name;
    private String domain;
    private String path;

    public Key(Cookie cookie) {
      this.name = cookie.name();
      this.domain = cookie.domain();
      this.path = cookie.path();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }

      if (obj instanceof Key) {
        Key key = (Key) obj;
        return ObjectUtils.equal(key.name, this.name) &&
            ObjectUtils.equal(key.domain, this.domain) &&
            ObjectUtils.equal(key.path, this.path);
      }

      return false;
    }

    @Override
    public int hashCode() {
      return HashCodeUtils.hashCode(name, domain, path);
    }
  }
}

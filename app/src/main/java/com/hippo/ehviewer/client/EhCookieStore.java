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

package com.hippo.ehviewer.client;

import android.content.Context;

import com.hippo.network.CookieRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class EhCookieStore extends CookieRepository {

    public static final String KEY_IPD_MEMBER_ID = "ipb_member_id";
    public static final String KEY_IPD_PASS_HASH = "ipb_pass_hash";
    public static final String KEY_IGNEOUS = "igneous";

    public static final Cookie sTipsCookie =
            new Cookie.Builder()
                    .name(EhConfig.KEY_CONTENT_WARNING)
                    .value(EhConfig.CONTENT_WARNING_NOT_SHOW)
                    .domain(EhUrl.DOMAIN_E)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .build();

    public EhCookieStore(Context context) {
        super(context, "okhttp3-cookie.db");
    }

    public void signOut() {
        clear();
    }

    public boolean hasSignedIn() {
        HttpUrl url = HttpUrl.parse(EhUrl.HOST_E);
        return contains(url, KEY_IPD_MEMBER_ID) &&
                contains(url, KEY_IPD_PASS_HASH);
    }

    public static Cookie newCookie(Cookie cookie, String newDomain, boolean forcePersistent,
            boolean forceLongLive, boolean forceNotHostOnly) {
        Cookie.Builder builder = new Cookie.Builder();
        builder.name(cookie.name());
        builder.value(cookie.value());

        if (forceLongLive) {
            builder.expiresAt(Long.MAX_VALUE);
        } else if (cookie.persistent()) {
            builder.expiresAt(cookie.expiresAt());
        } else if (forcePersistent) {
            builder.expiresAt(Long.MAX_VALUE);
        }
        if (cookie.hostOnly() && !forceNotHostOnly) {
            builder.hostOnlyDomain(newDomain);
        } else {
            builder.domain(newDomain);
        }
        builder.path(cookie.path());
        if (cookie.secure()) {
            builder.secure();
        }
        if (cookie.httpOnly()) {
            builder.httpOnly();
        }
        return builder.build();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = super.loadForRequest(url);

        boolean checkTips = domainMatch(url, EhUrl.DOMAIN_E);

        if (checkTips) {
            List<Cookie> result = new ArrayList<>(cookies.size() + 1);
            // Add all but skip some
            for (Cookie cookie: cookies) {
                String name = cookie.name();
                if (EhConfig.KEY_CONTENT_WARNING.equals(name)) {
                    continue;
                }
                if (EhConfig.KEY_UCONFIG.equals(name)) {
                    continue;
                }
                result.add(cookie);
            }
            // Add some
            result.add(sTipsCookie);
            return Collections.unmodifiableList(result);
        } else {
            return cookies;
        }
    }
}

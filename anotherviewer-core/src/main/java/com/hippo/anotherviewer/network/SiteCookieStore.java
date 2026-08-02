package com.hippo.anotherviewer.network;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.*;

public class SiteCookieStore implements CookieJar {
    private final Map<String, List<Cookie>> cookieStore = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        cookieStore.put(url.host(), cookies);
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = cookieStore.get(url.host());
        return cookies != null ? new ArrayList<>(cookies) : Collections.emptyList();
    }

    public void addCookie(Cookie cookie) {
        String host = cookie.domain().startsWith(".")
            ? cookie.domain().substring(1) : cookie.domain();
        List<Cookie> cookies = cookieStore.computeIfAbsent(host, k -> new ArrayList<>());
        cookies.removeIf(c -> c.name().equals(cookie.name()));
        cookies.add(cookie);
    }

    public void clear() {
        cookieStore.clear();
    }

    public Map<String, List<Cookie>> getAll() {
        return new HashMap<>(cookieStore);
    }
}

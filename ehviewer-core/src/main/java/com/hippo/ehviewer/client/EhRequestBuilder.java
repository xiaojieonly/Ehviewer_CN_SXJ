package com.hippo.ehviewer.client;

import okhttp3.*;

public class EhRequestBuilder {
    private final Request.Builder builder;

    public EhRequestBuilder(String url, String referer) { this(url, referer, null); }
    public EhRequestBuilder(String url, String referer, String origin) {
        builder = new Request.Builder().url(url);
        if (referer != null) builder.addHeader("Referer", referer);
        if (origin != null) builder.addHeader("Origin", origin);
    }
    public EhRequestBuilder(String url) { builder = new Request.Builder().url(url); }
    public EhRequestBuilder post(RequestBody body) { builder.post(body); return this; }
    public Request build() { return builder.build(); }
}

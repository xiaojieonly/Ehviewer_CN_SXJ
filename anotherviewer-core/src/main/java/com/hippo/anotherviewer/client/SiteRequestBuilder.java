package com.hippo.anotherviewer.client;

import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Request builder mirroring the Android app's {@code ChromeRequestBuilder}
 * (see app/src/main/java/com/hippo/okhttp/ChromeRequestBuilder.java).
 *
 * The WebUI backend and the Android app must present the same browser-like
 * fingerprint to Gallery Site: explicit Host, Chrome user agent, image-oriented
 * Accept and zh-CN Accept-Language. Keeping this in the shared core module
 * guarantees every upstream request built by {@link SiteEngine} matches the
 * app's call pattern, whatever OkHttp client is used.
 */
public class SiteRequestBuilder extends Request.Builder {

    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36";

    private static final String CHROME_ACCEPT =
            "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8";

    private static final String CHROME_ACCEPT_LANGUAGE =
            "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";

    public SiteRequestBuilder(String url, String referer) { this(url, referer, null); }
    public SiteRequestBuilder(String url, String referer, String origin) {
        url(url);
        // Host header, explicit like the app's ChromeRequestBuilder.
        String[] urlParts = url.split("/");
        if (urlParts.length > 2) {
            addHeader("Host", urlParts[2]);
        }
        addHeader("User-Agent", CHROME_USER_AGENT);
        addHeader("Accept", CHROME_ACCEPT);
        addHeader("Accept-Language", CHROME_ACCEPT_LANGUAGE);
        if (referer != null) addHeader("Referer", referer);
        if (origin != null) addHeader("Origin", origin);
    }
    public SiteRequestBuilder(String url) { this(url, null, null); }
    public SiteRequestBuilder post(RequestBody body) { super.post(body); return this; }
}

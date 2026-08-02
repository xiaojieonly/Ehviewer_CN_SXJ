package com.hippo.network;

public class UrlBuilder {
    private final StringBuilder sb;

    public UrlBuilder(String rootUrl) {
        sb = new StringBuilder(rootUrl);
    }

    public UrlBuilder appendQueryParameter(String key, String value) {
        if (sb.indexOf("?") == -1) {
            sb.append("?");
        } else {
            sb.append("&");
        }
        sb.append(key).append("=").append(value);
        return this;
    }

    public UrlBuilder addQuery(String key, String value) {
        return appendQueryParameter(key, value);
    }

    public UrlBuilder addQuery(String key, int value) {
        return appendQueryParameter(key, String.valueOf(value));
    }

    public String build() {
        return sb.toString();
    }

    @Override
    public String toString() {
        return build();
    }
}

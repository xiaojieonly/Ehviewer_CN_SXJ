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

package com.hippo.ehviewer.ui.scene;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.acsbendi.requestinspectorwebview.RequestInspectorOptions;
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient;
import com.acsbendi.requestinspectorwebview.WebViewRequest;
import com.acsbendi.requestinspectorwebview.WebViewRequestType;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhRequestBuilder;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.ui.UConfigActivity;
import com.hippo.ehviewer.widget.DialogWebChromeClient;
import com.hippo.lib.yorozuya.AssertUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class WebViewSignInScene extends SolidScene {

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private WebView mWebView;
    private OkHttpClient okHttpClient;
    @Override
    public boolean needShowLeftDrawer() {
        return false;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    @SuppressWarnings("deprecation")

    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = getEHContext();
        AssertUtils.assertNotNull(context);
        if (okHttpClient == null) {
            okHttpClient = EhApplication.getOkHttpClient(context.getApplicationContext());
        }
        EhUtils.signOut(context);

        // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.flush();
        cookieManager.removeAllCookies(null);
        cookieManager.removeSessionCookies(null);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.setAcceptFileSchemeCookies(true);

        mWebView = new WebView(context);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new LoginWebViewClient(mWebView));
//        mWebView.setWebViewClient(new UConfigActivity.UConfigWebViewClient(webView));
//        mWebView.setWebChromeClient(new DialogWebChromeClient(this));
        mWebView.loadUrl(EhUrl.URL_SIGN_IN);
        return mWebView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mWebView) {
            mWebView.destroy();
            mWebView = null;
        }
    }

    private class LoginWebViewClient extends RequestInspectorWebViewClient {

        public LoginWebViewClient(@NonNull WebView webView, @NonNull RequestInspectorOptions options) {
            super(webView, options);
        }

        public LoginWebViewClient(@NonNull WebView webView) {
            super(webView);
        }

        public List<Cookie> parseCookies(HttpUrl url, String cookieStrings) {
            if (cookieStrings == null) {
                return Collections.emptyList();
            }

            List<Cookie> cookies = null;
            String[] pieces = cookieStrings.split(";");
            for (String piece: pieces) {
                Cookie cookie = Cookie.parse(url, piece);
                if (cookie == null) {
                    continue;
                }
                if (cookies == null) {
                    cookies = new ArrayList<>();
                }
                cookies.add(cookie);
            }

            return cookies != null ? cookies : Collections.<Cookie>emptyList();
        }

        private void addCookie(Context context, String domain, Cookie cookie) {
            EhApplication.getEhCookieStore(context).addCookie(EhCookieStore.newCookie(cookie, domain, true, true, true));
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebViewRequest request) {
            Request okRequest;
            EhRequestBuilder builder = new EhRequestBuilder(request.getHeaders(),
                    request.getUrl());
            WebViewRequestType type = request.getType();
            switch (type) {
                case FETCH:
                case HTML:
                case XML_HTTP:
                    break;
                case FORM:
                    FormBody formBody = buildForm(request);
                    builder.post(formBody);
                    break;
            }
            okRequest = builder.build();
            try {
                Response response = okHttpClient.newCall(okRequest).execute();
                if (response.body() == null) {
                    throw new IOException("请求结果为空");
                }
                return convertOkHttpResponse(response);
            } catch (IOException e) {
                e.printStackTrace();
                FirebaseCrashlytics.getInstance().recordException(e);
            }
            return null;
        }

        public FormBody buildForm(WebViewRequest request) {
            Map<String, String> formMap = request.getFormParameters();
            FormBody.Builder builder = new FormBody.Builder();

            for (Map.Entry<String, String> entry : formMap.entrySet()) {
                builder.add(entry.getKey(), entry.getValue());
            }

            return builder.build();
        }
        @Override
        public void onPageFinished(WebView view, String url) {
            Context context = getEHContext();
            if (context == null) {
                return;
            }
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl == null) {
                return;
            }
            CookieManager manager = CookieManager.getInstance();
            String cookieString = manager.getCookie(EhUrl.HOST_E);
            List<Cookie> cookies = parseCookies(httpUrl, cookieString);
            boolean getId = false;
            boolean getHash = false;
            for (Cookie cookie: cookies) {
                if (EhCookieStore.KEY_IPD_MEMBER_ID.equals(cookie.name())) {
                    getId = true;
                } else if (EhCookieStore.KEY_IPD_PASS_HASH.equals(cookie.name())) {
                    getHash = true;
                }
                addCookie(context, EhUrl.DOMAIN_EX, cookie);
                addCookie(context, EhUrl.DOMAIN_E, cookie);
            }

            if (getId && getHash) {
                setResult(RESULT_OK, null);
                finish();
            }

        }

        public WebResourceResponse convertOkHttpResponse(Response okHttpResponse) {
            // Get the content type
            String contentType = "text/html"; // default
            if (okHttpResponse.header("Content-Type") != null) {
                contentType = okHttpResponse.header("Content-Type");
            }

            // Get the encoding (charset)
            String encoding = "UTF-8"; // default
            assert contentType != null;
            if (contentType.contains("charset=")) {
                encoding = contentType.split("charset=")[1];
            }

            // Get the MIME type
            String mimeType = contentType.split(";")[0];

            // Get the response code and message
            int statusCode = okHttpResponse.code();
            String reasonPhrase = okHttpResponse.message();

            // Get headers as a Map
            Map<String, String> responseHeaders = new HashMap<>();
            for (String headerName : okHttpResponse.headers().names()) {
                responseHeaders.put(headerName, okHttpResponse.header(headerName));
            }

            // Create the WebResourceResponse

            return new WebResourceResponse(
                    mimeType,
                    encoding,
                    statusCode,
                    reasonPhrase,
                    responseHeaders,
                    okHttpResponse.body().byteStream()
            );
        }
    }

}

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

package com.hippo.ehviewer.ui;

/*
 * Created by Hippo on 2018/2/9.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.acsbendi.requestinspectorwebview.RequestInspectorOptions;
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient;
import com.acsbendi.requestinspectorwebview.WebViewRequest;
import com.acsbendi.requestinspectorwebview.WebViewRequestType;
import com.google.android.material.snackbar.Snackbar;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhRequestBuilder;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.widget.DialogWebChromeClient;
import com.hippo.widget.ProgressView;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.IOException;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UConfigActivity extends ToolbarActivity {

    private WebView webView;
    private ProgressView progress;
    private String url;
    private boolean loaded;

    private OkHttpClient okHttpClient;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (okHttpClient == null) {
            okHttpClient = EhApplication.getOkHttpClient(getApplicationContext());
        }


        // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.flush();
        cookieManager.removeAllCookies(null);
        cookieManager.removeSessionCookies(null);

        // Copy cookies from okhttp cookie store to CookieManager
        url = EhUrl.getUConfigUrl();
        EhCookieStore store = EhApplication.getEhCookieStore(this);
        for (Cookie cookie : store.getCookies(HttpUrl.parse(url))) {
            cookieManager.setCookie(url, cookie.toString());
        }

        setContentView(R.layout.activity_u_config);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        webView = (WebView) findViewById(R.id.webview);

        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true); //将图片调整到适合webview的大小
        settings.setLoadWithOverviewMode(true); // 缩放至屏幕的大小
        settings.setSupportZoom(true); //支持缩放，默认为true。是下面那个的前提。
        settings.setBuiltInZoomControls(true); //设置内置的缩放控件。若为false，则该WebView不可缩放
        settings.setDisplayZoomControls(false); //隐藏原生的缩放控件

        webView.setWebViewClient(new UConfigWebViewClient(webView));
        webView.setWebChromeClient(new DialogWebChromeClient(this));
//        webView.addJavascriptInterface(payloadRecorder, "recorder");
        webView.loadUrl(url);
        progress = (ProgressView) findViewById(R.id.progress);

        Snackbar.make(webView, R.string.apply_tip, Snackbar.LENGTH_LONG).show();
    }

    private void apply() {
        webView.loadUrl("javascript:"
                + "(function() {\n"
                + "    var apply = document.getElementById(\"apply\").children[0];\n"
                + "    apply.click();\n"
                + "})();");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_u_config, menu);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_apply:
                if (loaded) {
                    apply();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private Cookie longLive(Cookie cookie) {
        return new Cookie.Builder()
                .name(cookie.name())
                .value(cookie.value())
                .domain(cookie.domain())
                .path(cookie.path())
                .expiresAt(Long.MAX_VALUE)
                .build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
        webView = null;

        // Put cookies back to okhttp cookie store
        CookieManager cookieManager = CookieManager.getInstance();
        String cookiesString = cookieManager.getCookie(url);

        if (cookiesString != null && !cookiesString.isEmpty()) {
            EhCookieStore store = EhApplication.getEhCookieStore(this);
            HttpUrl eUrl = HttpUrl.parse(EhUrl.HOST_E);
            HttpUrl exUrl = HttpUrl.parse(EhUrl.HOST_EX);

            // The cookies saved in the uconfig page should be shared between e and ex
            for (String header : cookiesString.split(";")) {
                Cookie eCookie = Cookie.parse(eUrl, header);
                if (eCookie != null) {
                    store.addCookie(longLive(eCookie));
                }

                Cookie exCookie = Cookie.parse(exUrl, header);
                if (exCookie != null) {
                    store.addCookie(longLive(exCookie));
                }
            }
        }
    }

    private class UConfigWebViewClient extends RequestInspectorWebViewClient {

        final OkHttpClient webOkHttpClient = okHttpClient;

        public UConfigWebViewClient(@NonNull WebView webView) {
            super(webView);
        }

        public UConfigWebViewClient(@NonNull WebView webView, @NonNull RequestInspectorOptions options) {
            super(webView, options);
        }

        @Override
        public void onFormResubmission(WebView view, Message dontResend, Message resend) {
            super.onFormResubmission(view, dontResend, resend);
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
                Response response = webOkHttpClient.newCall(okRequest).execute();
                if (response.body() == null) {
                    throw new IOException("请求结果为空");
                }
                String contentType, mimeType, encoding;
                contentType = response.header("content-type", "text/html; charset=UTF-8");
                if (contentType == null) {
                    contentType = "text/html; charset=UTF-8";
                }
                String[] contentA = contentType.split(";");
                mimeType = contentA[0];
                if (contentA.length > 1) {
                    String[] charsetA = contentA[1].split("=");
                    if (charsetA.length > 1) {
                        encoding = charsetA[1];
                    } else {
                        encoding = "";
                    }
                } else {
                    encoding = "";
                }
                ResponseBody body = response.body();
                if (mimeType.equals("text/html")) {
                    return new WebResourceResponse(mimeType, encoding, body.byteStream());
                }
                return new WebResourceResponse(mimeType, encoding, body.byteStream());
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
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Never load other urls
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            loaded = true;
        }
    }
}

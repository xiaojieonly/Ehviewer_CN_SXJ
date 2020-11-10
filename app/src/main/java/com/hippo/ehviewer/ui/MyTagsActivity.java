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

package com.hippo.ehviewer.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.widget.DialogWebChromeClient;
import com.hippo.widget.ProgressView;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class MyTagsActivity extends ToolbarActivity {

  private WebView webView;
  private ProgressView progress;
  private String url;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
    CookieManager cookieManager = CookieManager.getInstance();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      cookieManager.flush();
      cookieManager.removeAllCookies(null);
      cookieManager.removeSessionCookies(null);
    } else {
      CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(this);
      cookieSyncManager.startSync();
      cookieManager.removeAllCookie();
      cookieManager.removeSessionCookie();
      cookieSyncManager.stopSync();
    }

    // Copy cookies from okhttp cookie store to CookieManager
    url = EhUrl.getMyTagsUrl();
    EhCookieStore store = EhApplication.getEhCookieStore(this);
    for (Cookie cookie : store.getCookies(HttpUrl.parse(url))) {
      cookieManager.setCookie(url, cookie.toString());
    }

    setContentView(R.layout.activity_my_tags);
    setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    webView = findViewById(R.id.webview);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.setWebViewClient(new MyTagsWebViewClient());
    webView.setWebChromeClient(new DialogWebChromeClient(this));
    webView.loadUrl(url);
    progress = findViewById(R.id.progress);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private class MyTagsWebViewClient extends WebViewClient {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      // Never load other urls
      return !url.equals(MyTagsActivity.this.url);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      progress.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      progress.setVisibility(View.GONE);
    }
  }
}

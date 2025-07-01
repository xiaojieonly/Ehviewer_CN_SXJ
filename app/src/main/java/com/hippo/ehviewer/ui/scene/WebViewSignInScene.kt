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
package com.hippo.ehviewer.ui.scene

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.acsbendi.requestinspectorwebview.RequestInspectorOptions
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.acsbendi.requestinspectorwebview.WebViewRequestType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.lib.yorozuya.AssertUtils
import com.hippo.util.AppHelper
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class WebViewSignInScene : SolidScene() {
    /*---------------
         View life cycle
         ---------------*/
    private var mWebView: WebView? = null
    private var okHttpClient: OkHttpClient? = null

    override fun needShowLeftDrawer(): Boolean {
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("deprecation")
    override fun onCreateView2(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = ehContext
        AssertUtils.assertNotNull(context)
        if (okHttpClient == null) {
            okHttpClient = EhApplication.getOkHttpClient(context!!.applicationContext)
        }
        EhUtils.signOut(context)

        // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        cookieManager.removeAllCookies(null)
        cookieManager.removeSessionCookies(null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.setAcceptFileSchemeCookies(true)

        mWebView = WebView(context!!)
        val webSettings = mWebView!!.settings
        webSettings.javaScriptEnabled = true
        if (Settings.getDF()&&AppHelper.checkVPN(context)){
            mWebView!!.webViewClient = LoginWebViewClientSNI(mWebView!!)
        }else{
            mWebView!!.webViewClient = LoginWebViewClient()
        }

        //        mWebView.setWebViewClient(new UConfigActivity.UConfigWebViewClient(webView));
//        mWebView.setWebChromeClient(new DialogWebChromeClient(this));
        mWebView!!.loadUrl(EhUrl.URL_SIGN_IN)
        return mWebView
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (null != mWebView) {
            mWebView!!.destroy()
            mWebView = null
        }
    }

    private inner class LoginWebViewClientSNI : RequestInspectorWebViewClient {
        constructor(webView: WebView, options: RequestInspectorOptions) : super(webView, options)

        constructor(webView: WebView) : super(webView)

        fun parseCookies(url: HttpUrl, cookieStrings: String?): List<Cookie> {
            if (cookieStrings == null) {
                return emptyList()
            }

            var cookies: MutableList<Cookie>? = null
            val pieces =
                cookieStrings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (piece in pieces) {
                val cookie = Cookie.parse(url, piece) ?: continue
                if (cookies == null) {
                    cookies = ArrayList()
                }
                cookies.add(cookie)
            }

            return cookies ?: emptyList()
        }

        fun addCookie(context: Context, domain: String?, cookie: Cookie) {
            EhApplication.getEhCookieStore(context)
                .addCookie(EhCookieStore.newCookie(cookie, domain, true, true, true))
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebViewRequest
        ): WebResourceResponse? {
            val okRequest: Request
            val builder = EhRequestBuilder(
                request.headers,
                request.url
            )
            val type = request.type
            when (type) {
                WebViewRequestType.FETCH, WebViewRequestType.HTML, WebViewRequestType.XML_HTTP -> {}
                WebViewRequestType.FORM -> {
                    val formBody = buildForm(request)
                    builder.post(formBody)
                }
            }
            okRequest = builder.build()
            try {
                val response = okHttpClient!!.newCall(okRequest).execute()
                if (response.body() == null) {
                    throw IOException("请求结果为空")
                }
                return convertOkHttpResponse(response)
            } catch (e: IOException) {
                e.printStackTrace()
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            return null
        }

        fun buildForm(request: WebViewRequest): FormBody {
            val formMap: Map<String, String> = request.formParameters
            val builder = FormBody.Builder()

            for ((key, value) in formMap) {
                builder.add(key, value)
            }

            return builder.build()
        }

        override fun onPageFinished(view: WebView, url: String) {
            val context = ehContext ?: return
            val httpUrl = HttpUrl.parse(url) ?: return
            val manager = CookieManager.getInstance()
            val cookieString = manager.getCookie(EhUrl.HOST_E)
            val cookies = parseCookies(httpUrl, cookieString)
            var getId = false
            var getHash = false
            for (cookie in cookies) {
                if (EhCookieStore.KEY_IPD_MEMBER_ID == cookie.name()) {
                    getId = true
                } else if (EhCookieStore.KEY_IPD_PASS_HASH == cookie.name()) {
                    getHash = true
                }
                addCookie(context, EhUrl.DOMAIN_EX, cookie)
                addCookie(context, EhUrl.DOMAIN_E, cookie)
            }

            if (getId && getHash) {
                setResult(RESULT_OK, null)
                finish()
            }
        }

        fun convertOkHttpResponse(okHttpResponse: Response): WebResourceResponse {
            // Get the content type
            var contentType: String? = "text/html" // default
            if (okHttpResponse.header("Content-Type") != null) {
                contentType = okHttpResponse.header("Content-Type")
            }

            // Get the encoding (charset)
            var encoding = "UTF-8" // default
            checkNotNull(contentType)
            if (contentType.contains("charset=")) {
                encoding = contentType.split("charset=".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1]
            }

            // Get the MIME type
            val mimeType = contentType.split(";".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]

            // Get the response code and message
            val statusCode = okHttpResponse.code()
            val reasonPhrase = okHttpResponse.message()

            // Get headers as a Map
            val responseHeaders: MutableMap<String, String?> = HashMap()
            for (headerName in okHttpResponse.headers().names()) {
                responseHeaders[headerName] = okHttpResponse.header(headerName)
            }

            // Create the WebResourceResponse
            if (okHttpResponse.body() == null) {
                return WebResourceResponse(
                    mimeType,
                    encoding,
                    statusCode,
                    reasonPhrase,
                    responseHeaders,
                    null
                )
            }
            return WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                reasonPhrase,
                responseHeaders,
                okHttpResponse.body()!!.byteStream()
            )
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {
        fun parseCookies(url: HttpUrl, cookieStrings: String?): List<Cookie> {
            if (cookieStrings == null) {
                return emptyList()
            }

            var cookies: MutableList<Cookie>? = null
            val pieces =
                cookieStrings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (piece in pieces) {
                val cookie = Cookie.parse(url, piece) ?: continue
                if (cookies == null) {
                    cookies = java.util.ArrayList()
                }
                cookies.add(cookie)
            }

            return cookies ?: emptyList()
        }

        fun addCookie(context: Context, domain: String?, cookie: Cookie) {
            EhApplication.getEhCookieStore(context)
                .addCookie(EhCookieStore.newCookie(cookie, domain, true, true, true))
        }

        override fun onPageFinished(view: WebView, url: String) {
            val context: Context =  ehContext ?: return
            val httpUrl = HttpUrl.parse(url) ?: return

            val cookieString = CookieManager.getInstance().getCookie(EhUrl.HOST_E)
            val cookies = parseCookies(httpUrl, cookieString)
            var getId = false
            var getHash = false
            for (cookie in cookies) {
                if (EhCookieStore.KEY_IPD_MEMBER_ID == cookie.name()) {
                    getId = true
                } else if (EhCookieStore.KEY_IPD_PASS_HASH == cookie.name()) {
                    getHash = true
                }
                addCookie(context, EhUrl.DOMAIN_EX, cookie)
                addCookie(context, EhUrl.DOMAIN_E, cookie)
            }

            if (getId && getHash) {
                setResult(RESULT_OK, null)
                finish()
            }
        }
    }
}

package com.hippo.ehviewer.ui.scene.sign

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.acsbendi.requestinspectorwebview.RequestInspectorOptions
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.acsbendi.requestinspectorwebview.WebViewRequestType
import com.hippo.ehviewer.Analytics
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.ui.scene.SolidScene
import com.hippo.lib.yorozuya.AssertUtils
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GetProfileScene  : SolidScene(){

    private var mWebView: WebView? = null
    private var okHttpClient: OkHttpClient? = null

    override fun needShowLeftDrawer(): Boolean {
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView2(
        inflater: LayoutInflater?,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val context = ehContext
        AssertUtils.assertNotNull(context)
        if (okHttpClient == null) {
            okHttpClient = EhApplication.getOkHttpClient(context!!.applicationContext)
        }

        mWebView = WebView(context!!)
        val webSettings = mWebView!!.settings
        webSettings.javaScriptEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)

//        if (Settings.getDF()&& AppHelper.checkVPN(context)){
//            mWebView!!.webViewClient = ProfileWebViewClientSNI(mWebView!!)
//        }else{
//
//        }
        mWebView!!.webViewClient = ProfileWebViewClient()
//        mWebView!!.evaluateJavascript(
//            "(function(){ return document.documentElement.outerHTML; })();"
//        ) { html ->
//            println(html)
//        }
        //        mWebView.setWebViewClient(new UConfigActivity.UConfigWebViewClient(webView));
//        mWebView.setWebChromeClient(new DialogWebChromeClient(this));
        mWebView!!.loadUrl(EhUrl.URL_FORUMS)

        return mWebView
    }

    private inner class ProfileWebViewClientSNI: RequestInspectorWebViewClient {
        constructor(webView: WebView, options: RequestInspectorOptions) : super(webView, options)

        constructor(webView: WebView) : super(webView)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebViewRequest,
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
                Analytics.recordException(e)
            }
            return null
        }

        override fun onPageFinished(view: WebView, url: String) {
            val context = ehContext ?: return
            val httpUrl = HttpUrl.parse(url) ?: return
            val manager = CookieManager.getInstance()
            val cookieString = manager.getCookie(EhUrl.HOST_E)

            var getId = false
            var getHash = false

            if (getId && getHash) {
                setResult(RESULT_OK, null)
                finish()
            }
        }

        fun buildForm(request: WebViewRequest): FormBody {
            val formMap: Map<String, String> = request.formParameters
            val builder = FormBody.Builder()

            for ((key, value) in formMap) {
                builder.add(key, value)
            }

            return builder.build()
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
            val reasonPhraseRaw = okHttpResponse.message()
            val reasonPhrase = if (reasonPhraseRaw.isNullOrEmpty()) defaultReasonPhrase(statusCode) else reasonPhraseRaw

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

        private fun defaultReasonPhrase(statusCode: Int): String {
            return when (statusCode) {
                100 -> "Continue"
                101 -> "Switching Protocols"
                102 -> "Processing"
                200 -> "OK"
                201 -> "Created"
                202 -> "Accepted"
                203 -> "Non-Authoritative Information"
                204 -> "No Content"
                205 -> "Reset Content"
                206 -> "Partial Content"
                300 -> "Multiple Choices"
                301 -> "Moved Permanently"
                302 -> "Found"
                303 -> "See Other"
                304 -> "Not Modified"
                305 -> "Use Proxy"
                307 -> "Temporary Redirect"
                308 -> "Permanent Redirect"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                402 -> "Payment Required"
                403 -> "Forbidden"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                406 -> "Not Acceptable"
                407 -> "Proxy Authentication Required"
                408 -> "Request Timeout"
                409 -> "Conflict"
                410 -> "Gone"
                411 -> "Length Required"
                412 -> "Precondition Failed"
                413 -> "Payload Too Large"
                414 -> "URI Too Long"
                415 -> "Unsupported Media Type"
                416 -> "Range Not Satisfiable"
                417 -> "Expectation Failed"
                418 -> "I'm a teapot"
                421 -> "Misdirected Request"
                422 -> "Unprocessable Entity"
                423 -> "Locked"
                424 -> "Failed Dependency"
                426 -> "Upgrade Required"
                428 -> "Precondition Required"
                429 -> "Too Many Requests"
                431 -> "Request Header Fields Too Large"
                451 -> "Unavailable For Legal Reasons"
                500 -> "Internal Server Error"
                501 -> "Not Implemented"
                502 -> "Bad Gateway"
                503 -> "Service Unavailable"
                504 -> "Gateway Timeout"
                505 -> "HTTP Version Not Supported"
                507 -> "Insufficient Storage"
                508 -> "Loop Detected"
                510 -> "Not Extended"
                511 -> "Network Authentication Required"
                else -> if (statusCode in 200..299) "OK" else "Error"
            }
        }

    }

    private inner class ProfileWebViewClient: WebViewClient(){

        override fun onPageFinished(view: WebView, url: String) {
//            val context: Context =  ehContext ?: return
//            val httpUrl = HttpUrl.parse(url) ?: return

//            val cookieString = CookieManager.getInstance().getCookie(EhUrl.HOST_E)
//            val cookies = parseCookies(httpUrl, cookieString)
            var getId = false
            var getHash = false
//            for (cookie in cookies) {
//                if (EhCookieStore.KEY_IPD_MEMBER_ID == cookie.name()) {
//                    getId = true
//                } else if (EhCookieStore.KEY_IPD_PASS_HASH == cookie.name()) {
//                    getHash = true
//                }
//                addCookie(context, EhUrl.DOMAIN_EX, cookie)
//                addCookie(context, EhUrl.DOMAIN_E, cookie)
//            }

            if (getId && getHash) {
                setResult(RESULT_OK, null)
                finish()
            }
            readPageContent();
        }
        private fun readPageContent() {
            mWebView?.evaluateJavascript(
                "(function() {" +
                        "var content = {" +
                        "  title: document.title," +
                        "  url: window.location.href," +
                        "  html: document.documentElement.outerHTML," +
                        "  text: document.body.innerText," +
                        "  metaDescription: document.querySelector('meta[name=\"description\"]')?.content || ''," +
                        "  links: Array.from(document.getElementsByTagName('a')).map(a => ({href: a.href, text: a.textContent}))" +
                        "};" +
                        "return JSON.stringify(content);" +
                        "})();"
            ) { json ->
                try {
                    val content: JSONObject = JSONObject(json)
                    println(json)
                    val title: String? = content.getString("title")
                    val pageText: String? = content.getString("text")
                    // 处理内容...
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

    }
}
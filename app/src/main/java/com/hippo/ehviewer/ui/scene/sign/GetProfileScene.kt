package com.hippo.ehviewer.ui.scene.sign

import android.annotation.SuppressLint
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
import com.hippo.ehviewer.Analytics
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.exception.ParseException
import com.hippo.ehviewer.client.parser.ProfileParser
import com.hippo.ehviewer.ui.scene.SolidScene
import com.hippo.ehviewer.ui.scene.sign.SignInScene.AVATAR
import com.hippo.ehviewer.ui.scene.sign.SignInScene.DISPLAY_NAME
import com.hippo.ehviewer.ui.scene.sign.SignInScene.REQUEST_CODE_PROFILE
import com.hippo.lib.yorozuya.AssertUtils
import com.hippo.util.AppHelper
import android.util.Log
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import java.io.IOException

class GetProfileScene : SolidScene() {

    private var mWebView: WebView? = null
    private var okHttpClient: OkHttpClient? = null
    
    companion object {
        private const val TAG = "GetProfileScene"
    }

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
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
//        val store = EhApplication.getEhCookieStore(context)
//        val cookie = store.getCookieHeader(HttpUrl(EhUrl.URL_FORUMS))
//        manager.setCookie(EhUrl.URL_FORUMS)

        if (Settings.getDF()&& AppHelper.checkVPN(context)){
            mWebView!!.webViewClient = ProfileWebViewClientSNI(mWebView!!)
        }else{
            mWebView!!.webViewClient = ProfileWebViewClient()
        }

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

    private fun readPageContent() {
        mWebView?.evaluateJavascript(
            "(function() {" +
//                        "var content = {" +
//                        "  title: document.title," +
//                        "  url: window.location.href," +
//                        "  html: document.documentElement.outerHTML," +
//                        "  text: document.body.innerText," +
//                        "  metaDescription: document.querySelector('meta[name=\"description\"]')?.content || ''," +
//                        "  links: Array.from(document.getElementsByTagName('a')).map(a => ({href: a.href, text: a.textContent}))" +
//                        "};" +
//                        "return JSON.stringify(content);" +
                    "return document.documentElement.outerHTML;" +
                    "})();"
        ) { json ->
            try {
                val result = ProfileParser.parseNew(json)
                val bundle = Bundle()
                bundle.putString(DISPLAY_NAME, result.displayName)
                bundle.putString(AVATAR, result.avatar)
                setResult(REQUEST_CODE_PROFILE,bundle)
                finish()
                print(result)
                println(json)
                // 处理内容...
            } catch (e: JSONException) {
                e.printStackTrace()
            }catch (_: ParseException){}
        }
    }

    private inner class ProfileWebViewClientSNI : RequestInspectorWebViewClient {
        constructor(webView: WebView, options: RequestInspectorOptions) : super(webView, options)

        constructor(webView: WebView) : super(webView)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebViewRequest,
        ): WebResourceResponse? {
            // 方案五：URL Scheme 过滤 - 只处理 HTTP/HTTPS 请求
            val url = request.url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return null // 让 WebView 处理 file://, data:// 等
            }

            val okRequest: Request
            val builder = EhRequestBuilder(
                request.headers,
                request.url
            )

            // 方案一和方案二：获取请求方法并处理所有请求类型
            val method = getRequestMethod(request)
            val type = request.type

            // 方案四：处理 Range 请求（断点续传）
            val rangeHeader = request.headers["Range"]
            if (rangeHeader != null) {
                builder.addHeader("Range", rangeHeader)
            }

            // 处理不同类型的请求
            when (type) {
                WebViewRequestType.FETCH, WebViewRequestType.HTML, 
                WebViewRequestType.XML_HTTP -> {
                    // GET 请求或带请求体的其他方法
                    handleRequestWithBody(builder, request, method)
                }
                WebViewRequestType.FORM -> {
                    val formBody = buildForm(request)
                    builder.post(formBody)
                }
                // 方案三：扩展请求类型处理
//                WebViewRequestType.RESOURCE,
//                WebViewRequestType.IMAGE,
//                WebViewRequestType.STYLESHEET,
//                WebViewRequestType.SCRIPT,
//                WebViewRequestType.FONT -> {
//                    // 资源请求通常是 GET
//                    handleRequestWithBody(builder, request, method)
//                }
                else -> {
                    // 未知类型，使用默认处理
                    handleRequestWithBody(builder, request, method)
                }
            }
            
            okRequest = builder.build()
            
            // 方案三和方案七：改进异常处理和重定向处理
            try {
                val response = okHttpClient!!.newCall(okRequest).execute()
                
                // 处理重定向响应（3xx状态码）
                val statusCode = response.code()
                if (statusCode in 300..399) {
                    val redirectUrl = response.header("Location")
                    if (redirectUrl != null) {
                        // 让 WebView 处理重定向
                        response.close()
                        return null
                    }
                }
                
                if (response.body() == null) {
                    response.close()
                    throw IOException("请求结果为空")
                }
                
                // 方案六：同步 Cookie
                syncCookiesFromResponse(response, url)
                
                return convertOkHttpResponse(response)
            } catch (e: IOException) {
                Analytics.recordException(e)
                // 记录更详细的错误信息
                Log.e(TAG, "OkHttp request failed for $url", e)
                // 根据错误类型决定是否回退
                return null // 让 WebView 使用默认网络栈
            } catch (e: Exception) {
                Analytics.recordException(e)
                Log.e(TAG, "Unexpected error for $url", e)
                return null
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            ehContext ?: return
            HttpUrl.parse(url) ?: return
            val manager = CookieManager.getInstance()
            manager.getCookie(EhUrl.HOST_E)
            readPageContent()
//            var getId = false
//            var getHash = false
//
//            if (getId && getHash) {
//                setResult(RESULT_OK, null)
//                finish()
//            }
        }

        /**
         * 获取请求方法
         * 从 headers 中查找，或根据类型推断
         */
        private fun getRequestMethod(request: WebViewRequest): String {
            // 尝试从 headers 中获取方法（某些库可能会添加）
            val methodHeader = request.headers["X-HTTP-Method-Override"] 
                ?: request.headers["X-Method-Override"]
            
            if (methodHeader != null) {
                return methodHeader.uppercase()
            }
            
            // 根据类型推断
            return when (request.type) {
                WebViewRequestType.FORM -> "POST"
                else -> {
                    // 检查是否有请求体来判断是否为 POST
                    if (request.formParameters.isNotEmpty()) {
                        "POST"
                    } else {
                        "GET"
                    }
                }
            }
        }

        /**
         * 方案二：支持多种请求体类型
         * 
         * 注意：由于 Android-Request-Inspector-WebView 库的限制，
         * WebViewRequest 可能不直接提供请求体内容（特别是对于 FETCH/XML_HTTP 类型的请求）。
         * 如果库提供了 getBody() 或类似方法，应该优先使用。
         * 
         * 当前实现：
         * - 表单数据：从 formParameters 构建
         * - 其他类型：根据 Content-Type 创建空的 RequestBody（实际内容需要库支持）
         */
        private fun buildRequestBody(request: WebViewRequest, method: String): RequestBody? {
            return when {
                // 表单数据
                request.formParameters.isNotEmpty() -> {
                    buildForm(request)
                }
                // 尝试从 headers 获取 Content-Type 来判断请求体类型
                else -> {
                    val contentType = request.headers["Content-Type"] ?: ""
                    
                    // 尝试通过反射获取请求体（如果库支持）
                    val requestBody = tryGetRequestBodyViaReflection(request)
                    if (requestBody != null) {
                        return requestBody
                    }
                    
                    // 如果没有获取到请求体，根据 Content-Type 创建占位符
                    when {
                        contentType.contains("application/json") -> {
                            // JSON 数据
                            // 注意：实际请求体内容需要通过库的 API 获取
                            RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"),
                                "{}"
                            )
                        }
                        contentType.contains("application/xml") || contentType.contains("text/xml") -> {
                            // XML 数据
                            RequestBody.create(
                                MediaType.parse("application/xml; charset=utf-8"),
                                ""
                            )
                        }
                        contentType.contains("text/plain") -> {
                            // 纯文本
                            RequestBody.create(
                                MediaType.parse("text/plain; charset=utf-8"),
                                ""
                            )
                        }
                        contentType.isNotEmpty() && method != "GET" -> {
                            // 其他类型的请求体
                            RequestBody.create(
                                MediaType.parse(contentType),
                                ByteArray(0)
                            )
                        }
                        else -> null
                    }
                }
            }
        }

        /**
         * 尝试通过反射获取请求体
         * Android-Request-Inspector-WebView 库可能提供 getBody() 或类似方法
         */
        private fun tryGetRequestBodyViaReflection(request: WebViewRequest): RequestBody? {
            return try {
                // 尝试调用 getBody() 方法
                val getBodyMethod = request.javaClass.getMethod("getBody")
                val bodyString = getBodyMethod.invoke(request) as? String
                if (!bodyString.isNullOrEmpty()) {
                    val contentType = request.headers["Content-Type"] ?: "application/octet-stream"
                    RequestBody.create(MediaType.parse(contentType), bodyString)
                } else {
                    null
                }
            } catch (_: Exception) {
                // 方法不存在或其他错误，返回 null
                null
            }
        }

        /**
         * 处理带请求体的请求
         */
        private fun handleRequestWithBody(
            builder: EhRequestBuilder,
            request: WebViewRequest,
            method: String
        ) {
            val requestBody = buildRequestBody(request, method)
            when (method.uppercase()) {
                "POST" -> {
                    builder.post(requestBody ?: FormBody.Builder().build())
                }
                "PUT" -> {
                    builder.put(requestBody ?: FormBody.Builder().build())
                }
                "PATCH" -> {
                    builder.patch(requestBody ?: FormBody.Builder().build())
                }
                "DELETE" -> {
                    builder.delete(requestBody)
                }
                "HEAD" -> {
                    builder.head()
                }
                "OPTIONS" -> {
                    // OPTIONS 通常不需要 body
                }
                else -> {
                    // GET 等，不需要设置 body
                }
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

        /**
         * 方案六：Cookie 同步增强
         */
        private fun syncCookiesFromResponse(response: Response, url: String) {
            val cookies = response.headers("Set-Cookie")
            if (cookies.isNotEmpty()) {
                val cookieManager = CookieManager.getInstance()
                for (cookie in cookies) {
                    cookieManager.setCookie(url, cookie)
                }
                cookieManager.flush()
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
            val reasonPhraseRaw = okHttpResponse.message()
            val reasonPhrase =
                if (reasonPhraseRaw.isNullOrEmpty()) defaultReasonPhrase(statusCode) else reasonPhraseRaw

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

    private inner class ProfileWebViewClient : WebViewClient() {

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
            readPageContent()
        }

    }
}
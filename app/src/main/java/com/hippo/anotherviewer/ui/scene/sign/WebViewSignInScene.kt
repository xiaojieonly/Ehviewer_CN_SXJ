package com.hippo.anotherviewer.ui.scene.sign

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.client.SiteCookieStore
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.SiteUtils
import com.hippo.anotherviewer.R
import com.hippo.anotherviewer.ui.scene.SolidScene
import androidx.appcompat.app.AlertDialog
import com.hippo.lib.yorozuya.AssertUtils
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

class WebViewSignInScene : SolidScene() {
    /*---------------
         View life cycle
         ---------------*/
    companion object {
        private const val TAG = "WebViewSignInScene"
    }

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
            okHttpClient = SiteApplication.getOkHttpClient(context!!.applicationContext)
        }
        SiteUtils.signOut(context!!)

        return try {
            // http://stackoverflow.com/questions/32284642/how-to-handle-an-uncatched-exception
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            cookieManager.removeAllCookies(null)
            cookieManager.removeSessionCookies(null)
            CookieManager.getInstance().setAcceptCookie(true)

            mWebView = WebView(context)
            val webSettings = mWebView!!.settings
            webSettings.javaScriptEnabled = true
            mWebView!!.webViewClient = LoginWebViewClient()
            mWebView!!.loadUrl(SiteUrl.URL_SIGN_IN)
            mWebView
        } catch (t: Throwable) {
            Log.e(TAG, "WebView/CookieManager init failed", t)
            val root = FrameLayout(context)
            root.post {
                AlertDialog.Builder(context)
                    .setTitle(R.string.webview_unavailable_title)
                    .setMessage(R.string.webview_unavailable_message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
            root
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (null != mWebView) {
            mWebView!!.destroy()
            mWebView = null
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
            SiteApplication.getSiteCookieStore(context)
                .addCookie(SiteCookieStore.newCookie(cookie, domain, true, true, true))
        }

        @Suppress("deprecation")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val httpUrl = HttpUrl.parse(url) ?: return true
            val host = httpUrl.host()
            return !(host == SiteUrl.DOMAIN_E ||
                host == SiteUrl.DOMAIN_FORUMS ||
                host.endsWith("." + SiteUrl.DOMAIN_E))
        }

        override fun onPageFinished(view: WebView, url: String) {
            val context: Context =  ehContext ?: return
            val httpUrl = HttpUrl.parse(url) ?: return

            val cookieString = CookieManager.getInstance().getCookie(SiteUrl.HOST_E)
            val cookies = parseCookies(httpUrl, cookieString)
            var getId = false
            var getHash = false
            for (cookie in cookies) {
                if (SiteCookieStore.KEY_IPD_MEMBER_ID == cookie.name()) {
                    getId = true
                } else if (SiteCookieStore.KEY_IPD_PASS_HASH == cookie.name()) {
                    getHash = true
                }
                addCookie(context, SiteUrl.DOMAIN_EX, cookie)
                addCookie(context, SiteUrl.DOMAIN_E, cookie)
            }

            if (getId && getHash) {
                setResult(RESULT_OK, null)
                finish()
            }
        }
    }
}
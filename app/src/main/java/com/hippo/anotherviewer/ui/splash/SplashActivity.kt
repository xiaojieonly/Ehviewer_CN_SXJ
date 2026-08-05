package com.hippo.anotherviewer.ui.splash

import android.content.Intent
import android.os.Bundle
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.R
import com.hippo.anotherviewer.Settings
import com.hippo.anotherviewer.client.SiteClient
import com.hippo.anotherviewer.client.SiteRequest
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.SiteNewsDetail
import com.hippo.anotherviewer.ui.SiteActivity
import com.hippo.anotherviewer.ui.MainActivity
//import com.hippo.anotherviewer.ui.dialog.SiteDistributeListener
import java.lang.Exception

class SplashActivity : SiteActivity() {
    private val signNewsListener = SignNewsListener()

    private var checkUpdate = false
    private var openNews = false

    override fun getThemeResId(theme: Int): Int {
        return R.style.SplashTheme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        if (!checkUpdate) {
//            Distribute.setListener(SiteDistributeListener())
//            checkUpdate = true
//        }
//        AppCenter.start(
//            application, "a47010fb-702a-415a-ad93-ab5c674093ca",
//        )
//        Distribute.setEnabled(!Settings.getCloseAutoUpdate())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_layout)
        Thread(Runnable {
            //耗时任务，比如加载网络数据
            runOnUiThread(Runnable {
                val intentIn = intent
                val restart = intentIn.getBooleanExtra(KEY_RESTART, false)
                //跳转至 MainActivity
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                if (restart) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.putExtra(KEY_RESTART, true)
                }
                startActivity(intent)
                //结束当前的 Activity
                this@SplashActivity.finish()
            })
        }).start()
        if (!openNews && Settings.getShowEhEvents()) {
            signInNews()
        }
    }

    private fun signInNews() {
        val request = SiteRequest()
            .setMethod(SiteClient.METHOD_GET_NEWS)
            .setArgs(SiteUrl.getSiteNewsUrl())
            .setCallback(signNewsListener)
        SiteApplication.getSiteClient(applicationContext).execute(request)
    }

    private inner class SignNewsListener : SiteClient.Callback<SiteNewsDetail?> {

        override fun onSuccess(result: SiteNewsDetail?) {
            openNews = true
            if (result==null) return
            SiteApplication.getInstance().showEventPane(result)
        }

        override fun onFailure(e: Exception?) {
            openNews = true
        }

        override fun onCancel() {
            openNews = true
        }
    }

    companion object {
        const val KEY_RESTART: String = "restart"
    }
}

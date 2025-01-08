package com.hippo.ehviewer.ui.splash

import android.content.Intent
import android.os.Bundle
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.EhNewsDetail
import com.hippo.ehviewer.ui.EhActivity
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.dialog.EhDistributeListener
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.distribute.Distribute
import java.lang.Exception

class SplashActivity : EhActivity() {
    private val signNewsListener = SignNewsListener()

    private var checkUpdate = false
    private var openNews = false

    override fun getThemeResId(theme: Int): Int {
        return R.style.SplashTheme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!checkUpdate) {
            Distribute.setListener(EhDistributeListener())
            checkUpdate = true
        }
        AppCenter.start(
            application, "a47010fb-702a-415a-ad93-ab5c674093ca",
        )
        //        AppCenter.start(getApplication(), "feb52710-e245-4820-aebb-a57e00ed806d",
//                Analytics.class, Crashes.class, Distribute.class);
        Distribute.setEnabled(!Settings.getCloseAutoUpdate())
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
        val request = EhRequest()
            .setMethod(EhClient.METHOD_GET_NEWS)
            .setArgs(EhUrl.getEhNewsUrl())
            .setCallback(signNewsListener)
        EhApplication.getEhClient(applicationContext).execute(request)
    }

    private inner class SignNewsListener : EhClient.Callback<EhNewsDetail?> {

        override fun onSuccess(result: EhNewsDetail?) {
            openNews = true
            if (result==null) return
            EhApplication.getInstance().showEventPane(result)
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

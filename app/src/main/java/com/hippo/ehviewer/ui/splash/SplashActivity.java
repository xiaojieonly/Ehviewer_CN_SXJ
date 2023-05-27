package com.hippo.ehviewer.ui.splash;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.EhNewsDetail;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.dialog.EhDistributeListener;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.distribute.Distribute;

public class SplashActivity extends EhActivity {

    private final SignNewsListener signNewsListener = new SignNewsListener();

    private boolean CheckUpdate = false;

    private boolean openNews = false;

    private Context mContext;

    @Override
    protected int getThemeResId(int theme) {
        return R.style.SplashTheme;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mContext = getApplicationContext();
        if (!CheckUpdate) {
            Distribute.setListener(new EhDistributeListener());
            CheckUpdate = true;
        }
        AppCenter.start(getApplication(), "a47010fb-702a-415a-ad93-ab5c674093ca", Analytics.class, Crashes.class, Distribute.class);
        //        AppCenter.start(getApplication(), "feb52710-e245-4820-aebb-a57e00ed806d",
        //                Analytics.class, Crashes.class, Distribute.class);
        Distribute.setEnabled(!Settings.getCloseAutoUpdate());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_layout);
        new Thread(() -> {
            //耗时任务，比如加载网络数据
            runOnUiThread(() -> {
                //跳转至 MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                //结束当前的 Activity
                SplashActivity.this.finish();
            });
        }).start();
        if (!openNews && Settings.getShowEhEvents()) {
            signInNews();
        }
    }

    private void signInNews() {
        EhRequest request = new EhRequest().setMethod(EhClient.METHOD_GET_NEWS).setArgs(EhUrl.getEhNewsUrl()).setCallback(signNewsListener);
        EhApplication.getEhClient(getApplicationContext()).execute(request);
    }

    private class SignNewsListener implements EhClient.Callback<EhNewsDetail> {

        @Override
        public void onSuccess(EhNewsDetail result) {
            openNews = true;
            EhApplication.getInstance().showEventPane(result);
        }

        @Override
        public void onFailure(Exception e) {
            openNews = true;
        }

        @Override
        public void onCancel() {
            openNews = true;
        }
    }
}

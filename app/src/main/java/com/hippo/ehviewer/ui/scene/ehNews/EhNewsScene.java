package com.hippo.ehviewer.ui.scene.ehNews;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;


import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.EhNewsDetail;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.scene.SceneFragment;

import java.lang.reflect.Method;

public class EhNewsScene extends BaseScene {
    private static final String TAG = "EhNewsScene";
    private Context mContext;
    private EhNewsDetail detail;
    private View transitionView;
    private WebView webView;
    private View secene;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getEHContext();
        if (mContext!=null){
            EhApplication application = (EhApplication) mContext.getApplicationContext();
            detail = application.getEhNewsDetail();
        }
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        secene = inflater.inflate(R.layout.scene_eh_news, container, false);
        transitionView = secene.findViewById(R.id.news_loading_view);
        webView = secene.findViewById(R.id.eh_news_web_id);
        initWebView();
        showLoading();
        if (detail!=null){
            useDate();
//            webView.loadUrl("https://www.sogou.com");
        }else{
            loadData();
        }

        return secene;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }

    private void initWebView(){
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.clearCache(true);
        webView.setWebViewClient(new EhNewsWebViewClient());
        try {
            Method method = Class.forName("android.webkit.WebView")
                    .getMethod("setWebContentsDebuggingEnabled", Boolean.TYPE);
            if (method!=null){
                method.setAccessible(true);
                method.invoke(null,true);
            }
        }catch (Exception e){
            e.printStackTrace();
            Log.e(TAG,e.getMessage());
        }
    }

    private void useDate(){
        String encodeHtml = Base64.encodeToString(detail.getHtmlData().getBytes(),Base64.NO_PADDING);
        webView.loadDataWithBaseURL(null,detail.getHtmlData(),"text/html","UTF-8",null);
//        webView.loadData(detail.getHtmlData(),"text/html","UTF-8");
//        webView.loadDataWithBaseURL(null,detail.webData,"text/html","base64",null);
//        webView.loadData(encodeHtml,"text/html","base64");
        showNews();
    }

    private void loadData() {
        MainActivity activity = getActivity2();
        String url = EhUrl.getEhNewsUrl();
        EhRequest request = new EhRequest()
                .setMethod(EhClient.METHOD_GET_NEWS)
                .setArgs(url)
                .setCallback(new EhNewsDetailListener(mContext,activity.getStageId(),getTag()));
        EhApplication.getEhClient(mContext).execute(request);
    }




    private void showNews(){
        transitionView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showLoading(){
        transitionView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
    }

    private class EhNewsDetailListener extends EhCallback<EhNewsScene,EhNewsDetail>{

        public EhNewsDetailListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public void onSuccess(EhNewsDetail result) {
            detail = result;
            useDate();
            showNews();
        }

        @Override
        public void onFailure(Exception e) {

        }

        @Override
        public void onCancel() {

        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return false;
        }
    }

    private class EhNewsWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.e(TAG,url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            secene.setBackgroundColor(0);
            showNews();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            request.getUrl();
            return super.shouldOverrideUrlLoading(view, request);
        }
    }

}

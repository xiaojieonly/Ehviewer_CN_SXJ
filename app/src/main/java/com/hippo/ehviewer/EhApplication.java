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

package com.hippo.ehviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Debug;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.LruCache;

import com.hippo.a7zip.A7Zip;
import com.hippo.beerbelly.SimpleDiskCache;
import com.hippo.conaco.Conaco;
import com.hippo.content.RecordingApplication;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhHosts;
import com.hippo.ehviewer.client.EhEngine;
import com.hippo.ehviewer.client.data.EhNewsDetail;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.image.Image;
import com.hippo.image.ImageBitmap;
import com.hippo.network.EhSSLSocketFactory;
import com.hippo.network.EhSSLSocketFactoryLowSDK;
import com.hippo.network.EhX509TrustManager;
import com.hippo.network.StatusCodeException;
import com.hippo.text.Html;
import com.hippo.unifile.UniFile;
import com.hippo.util.AppHelper;
import com.hippo.util.BitmapUtils;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.util.ReadableTime;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IntIdGenerator;
import com.hippo.yorozuya.OSUtils;
import com.hippo.yorozuya.SimpleHandler;

import org.conscrypt.Conscrypt;

import java.io.File;
import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public class EhApplication extends RecordingApplication {

    private static final String TAG = EhApplication.class.getSimpleName();
    private static final String KEY_GLOBAL_STUFF_NEXT_ID = "global_stuff_next_id";

    public static final boolean BETA = false;

    private static final boolean DEBUG_CONACO = false;
    private static final boolean DEBUG_PRINT_NATIVE_MEMORY = false;
    private static final boolean DEBUG_PRINT_IMAGE_COUNT = false;
    private static final long DEBUG_PRINT_INTERVAL = 3000L;

    private static EhApplication instance;

    private final IntIdGenerator mIdGenerator = new IntIdGenerator();
    private final HashMap<Integer, Object> mGlobalStuffMap = new HashMap<>();

    private final HashMap<String, Object> mTempCacheMap = new HashMap<>();

    private EhCookieStore mEhCookieStore;
    private EhClient mEhClient;
    private EhProxySelector mEhProxySelector;
    private OkHttpClient mOkHttpClient;
    private OkHttpClient mImageOkHttpClient;
    private Cache mOkHttpCache;
    private ImageBitmapHelper mImageBitmapHelper;
    private Conaco<ImageBitmap> mConaco;
    private LruCache<Long, GalleryDetail> mGalleryDetailCache;
    private SimpleDiskCache mSpiderInfoCache;
    private DownloadManager mDownloadManager;
    private Hosts mHosts;
    private FavouriteStatusRouter mFavouriteStatusRouter;
    @Nullable
    private UserTagList userTagList;
    @Nullable
    private EhNewsDetail ehNewsDetail;

    private final List<Activity> mActivityList = new ArrayList<>();

    private final List<String> torrentList = new ArrayList<>();

    private boolean initialized = false;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public static EhApplication getInstance() {
        return instance;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onCreate() {
        instance = this;

        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                // Always save crash file if onCreate() is not done
                if (!initialized || Settings.getSaveCrashLog()) {
                    Crash.saveCrashLog(instance, e);
                }
            } catch (Throwable ignored) {
            }

            if (handler != null) {
                handler.uncaughtException(t, e);
            }
        });

        super.onCreate();

        GetText.initialize(this);
        StatusCodeException.initialize(this);
        Settings.initialize(this);
        ReadableTime.initialize(this);
        Html.initialize(this);
        AppConfig.initialize(this);
        SpiderDen.initialize(this);
        EhDB.initialize(this);
        EhEngine.initialize();
        BitmapUtils.initialize(this);
        Image.initialize(this);
        // 实际作用不确定，但是与64位应用有冲突
//        A7Zip.loadLibrary(A7ZipExtractLite.LIBRARY, libname -> ReLinker.loadLibrary(EhApplication.this, libname));
        // 64位适配
        A7Zip.initialize(this);
        if (EhDB.needMerge()) {
            EhDB.mergeOldDB(this);
        }

        if (Settings.getEnableAnalytics()) {
            Analytics.start(this);
        }

        // Do io tasks in new thread
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // Check no media file
                try {
                    UniFile downloadLocation = Settings.getDownloadLocation();
                    if (Settings.getMediaScan()) {
                        CommonOperations.removeNoMediaFile(downloadLocation);
                    } else {
                        CommonOperations.ensureNoMediaFile(downloadLocation);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.throwIfFatal(t);
                }

                // Clear temp files
                try {
                    clearTempDir();
                } catch (Throwable t) {
                    ExceptionUtils.throwIfFatal(t);
                }

                return null;
            }
        }.executeOnExecutor(IoThreadPoolExecutor.getInstance());

        // Check app update
        update();

        // Update version code
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            Settings.putVersionCode(pi.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore
        }

        mIdGenerator.setNextId(Settings.getInt(KEY_GLOBAL_STUFF_NEXT_ID, 0));

        if (DEBUG_PRINT_NATIVE_MEMORY || DEBUG_PRINT_IMAGE_COUNT) {
            debugPrint();
        }

        initialized = true;
    }

    private void clearTempDir() {
        File dir = AppConfig.getTempDir();
        if (null != dir) {
            FileUtils.deleteContent(dir);
        }
        dir = AppConfig.getExternalTempDir();
        if (null != dir) {
            FileUtils.deleteContent(dir);
        }

        // Add .nomedia to external temp dir
        CommonOperations.ensureNoMediaFile(UniFile.fromFile(AppConfig.getExternalTempDir()));
    }

    public EhCookieStore getmEhCookieStore() {
        return mEhCookieStore;
    }

    private void update() {
        int version = Settings.getVersionCode();
        if (version < 52) {
            Settings.putGuideGallery(true);
        }
    }

    public void clearMemoryCache() {
        if (null != mConaco) {
            mConaco.getBeerBelly().clearMemory();
        }
        if (null != mGalleryDetailCache) {
            mGalleryDetailCache.evictAll();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            clearMemoryCache();
        }
    }

    private void debugPrint() {
        new Runnable() {
            @Override
            public void run() {
                if (DEBUG_PRINT_NATIVE_MEMORY) {
                    Log.i(TAG, "Native memory: " + FileUtils.humanReadableByteCount(
                            Debug.getNativeHeapAllocatedSize(), false));
                }
                if (DEBUG_PRINT_IMAGE_COUNT) {
                    Log.i(TAG, "Image count: " + Image.getImageCount());
                }
                SimpleHandler.getInstance().postDelayed(this, DEBUG_PRINT_INTERVAL);
            }
        }.run();
    }

    public int putGlobalStuff(@NonNull Object o) {
        int id = mIdGenerator.nextId();
        mGlobalStuffMap.put(id, o);
        Settings.putInt(KEY_GLOBAL_STUFF_NEXT_ID, mIdGenerator.nextId());
        return id;
    }

    public boolean containGlobalStuff(int id) {
        return mGlobalStuffMap.containsKey(id);
    }

    public Object getGlobalStuff(int id) {
        return mGlobalStuffMap.get(id);
    }

    public Object removeGlobalStuff(int id) {
        return mGlobalStuffMap.remove(id);
    }

    public String putTempCache(@NonNull String key,@NonNull Object o) {
        mTempCacheMap.put(key, o);
        return key;
    }

    public boolean containTempCache(@NonNull String key) {
        return mTempCacheMap.containsKey(key);
    }

    public Object getTempCache(@NonNull String key) {
        return mTempCacheMap.get(key);
    }

    public Object removeTempCache(@NonNull String key) {
        return mTempCacheMap.remove(key);
    }

    public void removeGlobalStuff(Object o) {
        mGlobalStuffMap.values().removeAll(Collections.singleton(o));
    }

    public static EhCookieStore getEhCookieStore(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mEhCookieStore == null) {
            application.mEhCookieStore = new EhCookieStore(context);
        }
        return application.mEhCookieStore;
    }

    @NonNull
    public static EhClient getEhClient(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mEhClient == null) {
            application.mEhClient = new EhClient(application);
        }
        return application.mEhClient;
    }

    @NonNull
    public static EhProxySelector getEhProxySelector(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mEhProxySelector == null) {
            application.mEhProxySelector = new EhProxySelector();
        }
        return application.mEhProxySelector;
    }

    @NonNull
    public static OkHttpClient getOkHttpClient(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mOkHttpClient == null) {
//            Dispatcher dispatcher = new Dispatcher();
//            dispatcher.setMaxRequestsPerHost(4);
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
//                    .callTimeout(10, TimeUnit.SECONDS)
                    .cookieJar(getEhCookieStore(application))
                    .cache(getOkHttpCache(application))
//                    .hostnameVerifier((hostname, session) -> true)
//                    .dispatcher(dispatcher)
                    .dns(new EhHosts(application))
                    .addNetworkInterceptor(sprocket -> {
                        try {
                            return sprocket.proceed(sprocket.request());
                        } catch (NullPointerException e) {
                            throw new NullPointerException(e.getMessage());
                        }
                    })
                    .proxySelector(getEhProxySelector(application));
            if (Settings.getDF() && AppHelper.checkVPN(context)) {
                if (Build.VERSION.SDK_INT < 29) {
                    Security.insertProviderAt(Conscrypt.newProvider(), 1);
                    builder.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
                    try {
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init((KeyStore) null);
                        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
                        }
                        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
//                        X509TrustManager tm = Conscrypt.getDefaultX509TrustManager();
                        SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
                        sslContext.init(null, trustManagers, null);
                        builder.sslSocketFactory(new EhSSLSocketFactoryLowSDK(sslContext.getSocketFactory()), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new EhSSLSocketFactoryLowSDK(new EhSSLSocketFactory()), new EhX509TrustManager());
                    }
                } else {
                    try {
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init((KeyStore) null);
                        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
                        }
                        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                        builder.sslSocketFactory(new EhSSLSocketFactory(), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new EhSSLSocketFactory(), new EhX509TrustManager());
                    }
                }
            }
            application.mOkHttpClient = builder.build();
        }

        return application.mOkHttpClient;
    }

    @NonNull
    public static OkHttpClient getImageOkHttpClient(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mImageOkHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .cookieJar(getEhCookieStore(application))
                    .cache(getOkHttpCache(application))
//                    .hostnameVerifier((hostname, session) -> true)
                    .dns(new EhHosts(application))
                    .addNetworkInterceptor(sprocket -> {
                        try {
                            return sprocket.proceed(sprocket.request());
                        } catch (NullPointerException e) {
                            throw new NullPointerException(e.getMessage());
                        }
                    })
                    .proxySelector(getEhProxySelector(application));
            if (Settings.getDF() && AppHelper.checkVPN(context)) {
                if (Build.VERSION.SDK_INT < 29) {
                    Security.insertProviderAt(Conscrypt.newProvider(), 1);
                    builder.connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
                    try {
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init((KeyStore) null);
                        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
                        }
                        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                        SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
                        sslContext.init(null, trustManagers, null);
                        builder.sslSocketFactory(new EhSSLSocketFactoryLowSDK(sslContext.getSocketFactory()), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new EhSSLSocketFactoryLowSDK(new EhSSLSocketFactory()), new EhX509TrustManager());
                    }
                } else {
                    try {
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init((KeyStore) null);
                        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
                        }
                        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                        builder.sslSocketFactory(new EhSSLSocketFactory(), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new EhSSLSocketFactory(), new EhX509TrustManager());
                    }
                }
            }
            application.mImageOkHttpClient = builder.build();
        }

        return application.mImageOkHttpClient;
    }

    @NonNull
    public static ImageBitmapHelper getImageBitmapHelper(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mImageBitmapHelper == null) {
            application.mImageBitmapHelper = new ImageBitmapHelper();
        }
        return application.mImageBitmapHelper;
    }

    private static int getMemoryCacheMaxSize() {
        return Math.min(20 * 1024 * 1024, (int) OSUtils.getAppMaxMemory());
    }

    @NonNull
    public static Conaco<ImageBitmap> getConaco(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mConaco == null) {
            Conaco.Builder<ImageBitmap> builder = new Conaco.Builder<>();
            builder.hasMemoryCache = true;
            builder.memoryCacheMaxSize = getMemoryCacheMaxSize();
            builder.hasDiskCache = true;
            builder.diskCacheDir = new File(context.getCacheDir(), "thumb");
            builder.diskCacheMaxSize = 320 * 1024 * 1024; // 320MB
            builder.okHttpClient = getOkHttpClient(context);
//            builder.okHttpClient = getImageOkHttpClient(context);
            builder.objectHelper = getImageBitmapHelper(context);
            builder.debug = DEBUG_CONACO;
            application.mConaco = builder.build();
        }
        return application.mConaco;
    }


    @NonNull
    public static LruCache<Long, GalleryDetail> getGalleryDetailCache(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mGalleryDetailCache == null) {
            // Max size 25, 3 min timeout
            application.mGalleryDetailCache = new LruCache<>(25);
            getFavouriteStatusRouter().addListener((gid, slot) -> {
                GalleryDetail gd = application.mGalleryDetailCache.get(gid);
                if (gd != null) {
                    gd.favoriteSlot = slot;
                }
            });
        }
        return application.mGalleryDetailCache;
    }

    @NonNull
    public static SimpleDiskCache getSpiderInfoCache(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (null == application.mSpiderInfoCache) {
            application.mSpiderInfoCache = new SimpleDiskCache(
                    new File(context.getCacheDir(), "spider_info"), 5 * 1024 * 1024); // 5M
        }
        return application.mSpiderInfoCache;
    }

    @NonNull
    public static DownloadManager getDownloadManager() {
        return getDownloadManager(instance);
    }

    @NonNull
    public static DownloadManager getDownloadManager(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mDownloadManager == null) {
            application.mDownloadManager = new DownloadManager(application);
        }
        return application.mDownloadManager;
    }

    @NonNull
    public static Hosts getHosts(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mHosts == null) {
            application.mHosts = new Hosts(application, "hosts.db");
        }
        return application.mHosts;
    }

    @NonNull
    public static FavouriteStatusRouter getFavouriteStatusRouter() {
        return getFavouriteStatusRouter(getInstance());
    }

    @NonNull
    public static FavouriteStatusRouter getFavouriteStatusRouter(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mFavouriteStatusRouter == null) {
            application.mFavouriteStatusRouter = new FavouriteStatusRouter();
        }
        return application.mFavouriteStatusRouter;
    }

    @NonNull
    public static String getDeveloperEmail() {
        return "xiaojieonly$foxmail.com".replace('$', '@');
    }

    public void registerActivity(Activity activity) {
        mActivityList.add(activity);
    }

    public void unregisterActivity(Activity activity) {
        mActivityList.remove(activity);
    }

    @Nullable
    public Activity getTopActivity() {
        if (!mActivityList.isEmpty()) {
            return mActivityList.get(mActivityList.size() - 1);
        } else {
            return null;
        }
    }

    @NonNull
    public static Cache getOkHttpCache(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        if (application.mOkHttpCache == null) {
            application.mOkHttpCache = new Cache(new File(application.getCacheDir(), "http_cache"), 50L * 1024L * 1024L);
        }
        return application.mOkHttpCache;
    }

    // Avoid crash on some "energy saving" devices
    @Override
    public ComponentName startService(Intent service) {
        try {
            return super.startService(service);
        } catch (Throwable t) {
            ExceptionUtils.throwIfFatal(t);
            return null;
        }
    }

    // Avoid crash on some "energy saving" devices
    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        try {
            return super.bindService(service, conn, flags);
        } catch (Throwable t) {
            ExceptionUtils.throwIfFatal(t);
            return false;
        }
    }

    // Avoid crash on some "energy saving" devices
    @Override
    public void unbindService(ServiceConnection conn) {
        try {
            super.unbindService(conn);
        } catch (Throwable t) {
            ExceptionUtils.throwIfFatal(t);
        }
    }

    public static boolean addDownloadTorrent(@NonNull Context context, String url) {
        EhApplication application = ((EhApplication) context.getApplicationContext());

        if (application.torrentList.contains(url)) {
            return false;
        }

        application.torrentList.add(url);
        return true;
    }

    public static void removeDownloadTorrent(@NonNull Context context, String url) {
        EhApplication application = ((EhApplication) context.getApplicationContext());

        application.torrentList.remove(url);
    }

    /**
     * 将用户订阅标签列表存入内存缓存
     *
     */
    public static void saveUserTagList(@NonNull Context context, UserTagList userTagList) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        application.userTagList = userTagList;
    }

    /**
     * 从内存缓存中获取用户订阅标签列表
     *
     */
    public static UserTagList getUserTagList(@NonNull Context context) {
        EhApplication application = ((EhApplication) context.getApplicationContext());
        return application.userTagList;
    }

    public void showEventPane(String html){
        if (!Settings.getShowEhEvents()){
            return;
        }
        if (html==null){
            return;
        }
        Activity activity = getTopActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setMessage(Html.fromHtml(html))
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                dialog.setOnShowListener(d -> {
                    final View messageView = dialog.findViewById(android.R.id.message);
                    if (messageView instanceof TextView) {
                        ((TextView) messageView).setMovementMethod(LinkMovementMethod.getInstance());
                    }
                });
                try {
                    dialog.show();
                } catch (Throwable t) {
                    // ignore
                }
            });
        }
    }

    /**
     * 显示eh事件
     *
     */
    public void showEventPane(EhNewsDetail result) {
        ehNewsDetail = result;
        String html = result.getEventPane();
        showEventPane(html);
    }

    @Nullable
    public EhNewsDetail getEhNewsDetail(){
        return ehNewsDetail;
    }

    public static ExecutorService getExecutorService(@NonNull Context context){
        EhApplication application = ((EhApplication) context.getApplicationContext());
        return  application.executorService;
    }

}


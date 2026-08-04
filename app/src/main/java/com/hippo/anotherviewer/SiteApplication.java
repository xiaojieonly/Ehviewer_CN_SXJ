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

package com.hippo.anotherviewer;

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
import android.webkit.CookieManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.LruCache;

import com.hippo.Native;
//import com.gu.toolargetool.TooLargeTool;
import com.hippo.a7zip.A7Zip;
import com.hippo.beerbelly.SimpleDiskCache;
import com.hippo.conaco.Conaco;
import com.hippo.content.RecordingApplication;
import com.hippo.anotherviewer.client.SiteClient;
import com.hippo.anotherviewer.client.SiteCookieStore;
import com.hippo.anotherviewer.client.SiteHosts;
import com.hippo.anotherviewer.client.SiteEngine;
import com.hippo.anotherviewer.client.data.SiteNewsDetail;
import com.hippo.anotherviewer.client.data.GalleryDetail;
import com.hippo.anotherviewer.client.data.userTag.UserTagList;
import com.hippo.anotherviewer.download.ArchiverDownloadCompleter;
import com.hippo.anotherviewer.download.DownloadManager;
import com.hippo.anotherviewer.spider.SpiderDen;
import com.hippo.anotherviewer.ui.CommonOperations;
import com.hippo.lib.image.Image;
//import com.hippo.lib.image.Image1;
//import com.hippo.lib.image.ImageBitmap;
import com.hippo.network.SiteSSLSocketFactory;
import com.hippo.network.SiteSSLSocketFactoryLowSDK;
import com.hippo.network.SiteX509TrustManager;
import com.hippo.network.StatusCodeException;
import com.hippo.text.Html;
import com.hippo.unifile.UniFile;
import com.hippo.util.AppHelper;
import com.hippo.util.BitmapUtils;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.util.ReadableTime;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IntIdGenerator;
import com.hippo.lib.yorozuya.OSUtils;
import com.hippo.lib.yorozuya.SimpleHandler;

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
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SiteApplication extends RecordingApplication {

    private static final String TAG = SiteApplication.class.getSimpleName();
    private static final String KEY_GLOBAL_STUFF_NEXT_ID = "global_stuff_next_id";

    public static final boolean BETA = false;

    private static final boolean DEBUG_CONACO = false;
    private static final boolean DEBUG_PRINT_NATIVE_MEMORY = false;
    private static final boolean DEBUG_PRINT_IMAGE_COUNT = false;
    private static final long DEBUG_PRINT_INTERVAL = 3000L;

    private static SiteApplication instance;

    private final IntIdGenerator mIdGenerator = new IntIdGenerator();
    private final HashMap<Integer, Object> mGlobalStuffMap = new HashMap<>();

    private final HashMap<String, Object> mTempCacheMap = new HashMap<>();

    private SiteCookieStore mSiteCookieStore;
    private SiteClient mSiteClient;
    private SiteProxySelector mSiteProxySelector;
    private OkHttpClient mOkHttpClient;
    private OkHttpClient mImageOkHttpClient;
    private Cache mOkHttpCache;
    private ImageBitmapHelper mImageBitmapHelper;
    private Conaco<Image> mConaco;
    private LruCache<Long, GalleryDetail> mGalleryDetailCache;
    private SimpleDiskCache mSpiderInfoCache;
    private DownloadManager mDownloadManager;
    private Hosts mHosts;
    private FavouriteStatusRouter mFavouriteStatusRouter;
    @Nullable
    private UserTagList userTagList;
    @Nullable
    private SiteNewsDetail ehNewsDetail;

    private final List<Activity> mActivityList = new ArrayList<>();

    private final List<String> torrentList = new ArrayList<>();

    private boolean initialized = false;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public static SiteApplication getInstance() {
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
//        if(BuildConfig.DEBUG){
//            TooLargeTool.startLogging(this);
//        }

        GetText.initialize(this);
        StatusCodeException.initialize(this);
        Settings.initialize(this);
        ArchiverDownloadCompleter.resumePendingDownloads(this);
        ReadableTime.initialize(this);
        Html.initialize(this);
        AppConfig.initialize(this);
        SpiderDen.initialize(this);
        SiteDB.initialize(this);
        SiteEngine.initialize();
        BitmapUtils.initialize(this);

        // Wave-2 (ADR-0003): D2 policy source + D4 network-aware auto-sync.
        final com.hippo.anotherviewer.webui.WebUiSettings webUiSettings =
                new com.hippo.anotherviewer.webui.WebUiSettings(this);
        com.hippo.anotherviewer.webui.WebUiSyncEngine.setPolicySource(() -> {
            com.hippo.anotherviewer.webui.WebUiSyncModels.SyncPolicy policy =
                    new com.hippo.anotherviewer.webui.WebUiSyncModels.SyncPolicy();
            policy.conflictStrategy = webUiSettings.conflictStrategy();
            policy.clientTier = webUiSettings.clientTier();
            policy.autoSyncIntervalSec = webUiSettings.autoSyncIntervalSec();
            return policy;
        });
        new com.hippo.anotherviewer.webui.WebUiAutoSyncScheduler(this).start();
//        Image1.initialize(this);
        Image.initialize(this);
        if (!"robolectric".equals(Build.FINGERPRINT)) {
            Native.initialize();
        }
        // 实际作用不确定，但是与64位应用有冲突
//        A7Zip.loadLibrary(A7ZipExtractLite.LIBRARY, libname -> ReLinker.loadLibrary(SiteApplication.this, libname));
        // 64位适配
        if (!"robolectric".equals(Build.FINGERPRINT)) {
            A7Zip.initialize(this);
        }
        if (SiteDB.needMerge()) {
            SiteDB.mergeOldDB(this);
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

                try{
                    AppConfig.deleteOldParseErrorFiles();
                } catch (Throwable ignored) {
                }

                return null;
            }
        }.executeOnExecutor(IoThreadPoolExecutor.Companion.getInstance());

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

    public SiteCookieStore getSiteCookieStore() {
        return mSiteCookieStore;
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

    public static SiteCookieStore getSiteCookieStore(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mSiteCookieStore == null) {
            application.mSiteCookieStore = new SiteCookieStore(context);
        }
        return application.mSiteCookieStore;
    }

    @NonNull
    public static SiteClient getSiteClient(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mSiteClient == null) {
            application.mSiteClient = new SiteClient(application);
        }
        return application.mSiteClient;
    }

    @NonNull
    public static SiteProxySelector getSiteProxySelector(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mSiteProxySelector == null) {
            application.mSiteProxySelector = new SiteProxySelector();
        }
        return application.mSiteProxySelector;
    }

    /**
     * Debug-only: rewrites requests for the real Gallery Site hosts to the dummy
     * server (mock-server/gallery.mjs) when BuildConfig.MOCK_EH_BASE_URL is set.
     * Hosts rewritten: gallery.test, gallery.test, lofi.gallery.test, gallery.test.
     * Referer/Origin headers are rewritten the same way.
     */
    private static Interceptor createMockSiteInterceptor() {
        final String mockBase = BuildConfig.MOCK_EH_BASE_URL;
        if (mockBase.isEmpty()) {
            return chain -> chain.proceed(chain.request());
        }
        // OkHttp 4.x: parse() stays (deprecated) as the only null-safe Java factory —
        // HttpUrl.get would throw on malformed input where this code degrades to null.
        final HttpUrl base = HttpUrl.parse(mockBase);
        if (base == null) {
            return chain -> chain.proceed(chain.request());
        }
        return chain -> {
            Request request = chain.request();
            HttpUrl url = request.url();
            if (!isMockSiteHost(url.host())) {
                return chain.proceed(request);
            }
            HttpUrl newUrl = url.newBuilder()
                    .scheme(base.scheme())
                    .host(base.host())
                    .port(base.port())
                    .build();
            Request.Builder builder = request.newBuilder().url(newUrl);
            String referer = request.header("Referer");
            if (referer != null && isMockSiteHost(HttpUrl.parse(referer) != null ? HttpUrl.parse(referer).host() : "")) {
                builder.header("Referer", rewriteMockSiteUrl(referer, base));
            }
            String origin = request.header("Origin");
            if (origin != null && isMockSiteHost(HttpUrl.parse(origin) != null ? HttpUrl.parse(origin).host() : "")) {
                builder.header("Origin", rewriteMockSiteUrl(origin, base));
            }
            return chain.proceed(builder.build());
        };
    }

    private static boolean isMockSiteHost(@NonNull String host) {
        return host.equals("gallery.test") || host.endsWith(".gallery.test");
    }

    private static String rewriteMockSiteUrl(@NonNull String url, @NonNull HttpUrl base) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || !isMockSiteHost(parsed.host())) {
            return url;
        }
        return parsed.newBuilder().scheme(base.scheme()).host(base.host()).port(base.port()).build().toString();
    }

    @NonNull
    public static OkHttpClient getOkHttpClient(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mOkHttpClient == null) {
//            Dispatcher dispatcher = new Dispatcher();
//            dispatcher.setMaxRequestsPerHost(4);
            
            // 创建优化的连接池 - 针对后台下载优化
            // 最多保持 10 个连接，每个连接保持 5 分钟，适合后台长时间下载
            ConnectionPool connectionPool = new ConnectionPool(
                    10,  // 最大空闲连接数
                    5,   // 连接保活时间（分钟）
                    TimeUnit.MINUTES
            );
            
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
//                    .callTimeout(10, TimeUnit.SECONDS)
                    .connectionPool(connectionPool)  // 添加优化的连接池
                    .retryOnConnectionFailure(true)  // 连接失败时重试
                    .cookieJar(getSiteCookieStore(application))
                    .cache(getOkHttpCache(application))
//                    .hostnameVerifier((hostname, session) -> true)
//                    .dispatcher(dispatcher)
                    .addInterceptor(createMockSiteInterceptor())
                    // Wave-2 (ADR-0003 D3): Tier-2 routes Gallery Site browsing
                    // through the paired WebUI server. Registered after the mock
                    // interceptor so debug-mode rewrites win; in production the
                    // mock is a pass-through and this applies per request.
                    .addInterceptor(new com.hippo.anotherviewer.webui.WebUiTier2ProxyInterceptor(
                            new com.hippo.anotherviewer.webui.WebUiSettings(application)))
                    .dns(new SiteHosts(application))
                    .addNetworkInterceptor(sprocket -> {
                        try {
                            return sprocket.proceed(sprocket.request());
                        } catch (NullPointerException e) {
                            throw new NullPointerException(e.getMessage());
                        }
                    })
                    .addNetworkInterceptor(chain -> {
                        Response response = chain.proceed(chain.request());
                        // 同步Cookie到WebView
                        if (response.headers("Set-Cookie") != null) {
                            try {
                                CookieManager cookieManager = CookieManager.getInstance();
                                String url = chain.request().url().toString();
                                for (String header : response.headers("Set-Cookie")) {
                                    cookieManager.setCookie(url, header);
                                }
                                cookieManager.flush();
                            } catch (Throwable t) {
                                Log.e(TAG, "CookieManager/WebView sync skipped", t);
                            }
                        }
                        return response;
                    })
                    .proxySelector(getSiteProxySelector(application));
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
                        builder.sslSocketFactory(new SiteSSLSocketFactoryLowSDK(sslContext.getSocketFactory()), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new SiteSSLSocketFactoryLowSDK(new SiteSSLSocketFactory()), new SiteX509TrustManager());
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
                        builder.sslSocketFactory(new SiteSSLSocketFactory(), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new SiteSSLSocketFactory(), new SiteX509TrustManager());
                    }
                }
            }
            application.mOkHttpClient = builder.build();
        }

        return application.mOkHttpClient;
    }

    @NonNull
    public static OkHttpClient getImageOkHttpClient(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mImageOkHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .cookieJar(getSiteCookieStore(application))
                    .cache(getOkHttpCache(application))
//                    .hostnameVerifier((hostname, session) -> true)
                    .addInterceptor(createMockSiteInterceptor())
                    // Wave-2 (ADR-0003 D3): Tier-2 routes Gallery Site browsing
                    // through the paired WebUI server. Registered after the mock
                    // interceptor so debug-mode rewrites win; in production the
                    // mock is a pass-through and this applies per request.
                    .addInterceptor(new com.hippo.anotherviewer.webui.WebUiTier2ProxyInterceptor(
                            new com.hippo.anotherviewer.webui.WebUiSettings(application)))
                    .dns(new SiteHosts(application))
                    .addNetworkInterceptor(sprocket -> {
                        try {
                            return sprocket.proceed(sprocket.request());
                        } catch (NullPointerException e) {
                            throw new NullPointerException(e.getMessage());
                        }
                    })
                    .proxySelector(getSiteProxySelector(application));
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
                        builder.sslSocketFactory(new SiteSSLSocketFactoryLowSDK(sslContext.getSocketFactory()), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new SiteSSLSocketFactoryLowSDK(new SiteSSLSocketFactory()), new SiteX509TrustManager());
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
                        builder.sslSocketFactory(new SiteSSLSocketFactory(), trustManager);
                    } catch (Exception e) {
                        e.printStackTrace();
                        builder.sslSocketFactory(new SiteSSLSocketFactory(), new SiteX509TrustManager());
                    }
                }
            }
            application.mImageOkHttpClient = builder.build();
        }

        return application.mImageOkHttpClient;
    }

    @NonNull
    public static ImageBitmapHelper getImageBitmapHelper(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mImageBitmapHelper == null) {
            application.mImageBitmapHelper = new ImageBitmapHelper();
        }
        return application.mImageBitmapHelper;
    }

    private static int getMemoryCacheMaxSize() {
        return Math.min(20 * 1024 * 1024, (int) OSUtils.getAppMaxMemory());
    }

    @NonNull
    public static Conaco<Image> getConaco(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mConaco == null) {
            Conaco.Builder<Image> builder = new Conaco.Builder<>();
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        if (application.mDownloadManager == null) {
            application.mDownloadManager = new DownloadManager(application);
        }
        return application.mDownloadManager;
    }

    @NonNull
    public static Hosts getHosts(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
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
        SiteApplication application = ((SiteApplication) context.getApplicationContext());

        if (application.torrentList.contains(url)) {
            return false;
        }

        application.torrentList.add(url);
        return true;
    }

    public static void removeDownloadTorrent(@NonNull Context context, String url) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());

        application.torrentList.remove(url);
    }

    /**
     * 将用户订阅标签列表存入内存缓存
     *
     */
    public static void saveUserTagList(@NonNull Context context, UserTagList userTagList) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        application.userTagList = userTagList;
    }

    /**
     * 从内存缓存中获取用户订阅标签列表
     *
     */
    public static UserTagList getUserTagList(@NonNull Context context) {
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        return application.userTagList;
    }

    public void showEventPane(String html){
        if (!Settings.getShowSiteEvents()){
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
    public void showEventPane(SiteNewsDetail result) {
        ehNewsDetail = result;
        String html = result.getEventPane();
        showEventPane(html);
    }

    @Nullable
    public SiteNewsDetail getSiteNewsDetail(){
        return ehNewsDetail;
    }

    public static ExecutorService getExecutorService(@NonNull Context context){
        SiteApplication application = ((SiteApplication) context.getApplicationContext());
        return  application.executorService;
    }

}


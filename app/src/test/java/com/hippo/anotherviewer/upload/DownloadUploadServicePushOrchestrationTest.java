/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.SparseArray;

import com.hippo.anotherviewer.AppConfig;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.SiteDB;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.spider.SpiderInfo;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.anotherviewer.webui.WebUiUploadClient;
import com.hippo.unifile.UniFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * TH9：DownloadUploadService 的 init/storePage/complete 编排层测试。
 *
 * <p>网络 client 用 fake：反射替换 {@link WebUiUploadClient} 的私有静态
 * OkHttpClient 为「终端拦截器」型假客户端（同 WebUiTier2ProxyInterceptorTest
 * 手法）——真实 OkHttp 管线全跑，但不出 socket，响应由用例排队注入，请求被
 * 逐条录制（方法/路径/Bearer/body）。文件系统侧用 Robolectric + 真实临时目录
 * 搭出标准下载布局（{@code <download>/<gid>-title/.anotherviewer + %08d.jpg}），
 * 经 Settings/SiteDB 公共入口接线，SpiderInfo/SpiderDen 走真实现；pushOne 经
 * 反射在后台线程调用（贴近生产 executor 语义，也避开目录发现的主线程守卫）。
 *
 * <p>重点钉死 A2 泳道的 pushed_gids TTL 交互：init 冲突时只有「本设备推送过且
 * 未过 TTL」的条目才授权 force 续传补页；过期、旧版裸 gid、损坏时间戳、其他
 * gid 一律降级为跳过，绝不 force 覆盖服务器行。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DownloadUploadServicePushOrchestrationTest {

    private static final String TOKEN = "svc-tk";

    private File tempRoot;
    private File downloadDir;
    private DownloadUploadService service;
    private WebUiConfig config;
    private Method pushOneMethod;
    private String prefsName;
    private String prefsKey;

    private final Deque<FakeResponse> queued = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        tempRoot = new File(System.getProperty("java.io.tmpdir"),
                "push-svc-test-" + System.nanoTime());
        assertTrue(tempRoot.mkdirs());
        downloadDir = new File(tempRoot, "dl");
        assertTrue(downloadDir.mkdirs());

        Context context = RuntimeEnvironment.application;
        AppConfig.initialize(context);
        Settings.initialize(context);
        SiteDB.initialize(context);
        // 公共入口把下载位置指到临时目录（file:// URI，绕开 SAF）。
        Settings.putDownloadLocation(UniFile.fromFile(downloadDir));

        service = Robolectric.setupService(DownloadUploadService.class);
        config = new WebUiConfig("http", "127.0.0.1", 1, "", TOKEN);

        pushOneMethod = DownloadUploadService.class.getDeclaredMethod(
                "pushOne", WebUiConfig.class, GalleryInfo.class);
        pushOneMethod.setAccessible(true);

        Field nameField = DownloadUploadService.class.getDeclaredField("PREFS_NAME");
        nameField.setAccessible(true);
        prefsName = (String) nameField.get(null);
        Field keyField = DownloadUploadService.class.getDeclaredField("KEY_PUSHED_GIDS");
        keyField.setAccessible(true);
        prefsKey = (String) keyField.get(null);
        clearPushedPrefs();

        injectFakeClient();
    }

    @After
    public void tearDown() throws Exception {
        clientField().set(null, null);
        deleteRecursive(tempRoot);
    }

    // ------------------------------------------------------------------
    // 假网络 client（终端拦截器，不出 socket）
    // ------------------------------------------------------------------

    private static final class FakeResponse {
        final int code;
        final String body;

        FakeResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static final class RecordedRequest {
        final String method;
        final String path;
        final String authorization;
        final byte[] body;

        RecordedRequest(okhttp3.Request request) throws IOException {
            this.method = request.method();
            this.path = request.url().encodedPath();
            this.authorization = request.header("Authorization");
            byte[] bytes = new byte[0];
            if (request.body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                bytes = buffer.readByteArray();
            }
            this.body = bytes;
        }

        boolean isPutInit(long gid) {
            return "PUT".equals(method) && path.endsWith("/download/upload/" + gid);
        }

        boolean isPage(int page) {
            return "POST".equals(method) && path.matches(".*/page/" + page + "$");
        }

        boolean isComplete() {
            return "POST".equals(method) && path.endsWith("/complete");
        }
    }

    private void injectFakeClient() throws Exception {
        OkHttpClient fake = new OkHttpClient.Builder()
                .addInterceptor((Interceptor.Chain chain) -> {
                    RecordedRequest recorded = new RecordedRequest(chain.request());
                    requests.add(recorded);
                    FakeResponse next = queued.poll();
                    if (next == null) {
                        throw new IOException("no queued response for " + recorded.path);
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(next.code)
                            .message(next.code >= 200 && next.code < 300 ? "OK" : "Err")
                            .body(ResponseBody.create(
                                    next.body.getBytes(StandardCharsets.UTF_8),
                                    MediaType.get("application/json")))
                            .build();
                })
                .build();
        clientField().set(null, fake);
    }

    private static Field clientField() throws Exception {
        Field field = WebUiUploadClient.class.getDeclaredField("sClient");
        field.setAccessible(true);
        return field;
    }

    private void enqueue(int code, String body) {
        queued.add(new FakeResponse(code, body));
    }

    // ------------------------------------------------------------------
    // 文件系统布局 + 实体构造
    // ------------------------------------------------------------------

    /** 标准下载布局：spider_info 文件 + 全部页图 + SiteDB 目录名登记。 */
    private GalleryInfo makeGallery(long gid, String spiderToken, int pagesInFile,
            boolean withSpiderFile) throws Exception {
        File galleryDir = new File(downloadDir, gid + "-title");
        assertTrue(galleryDir.isDirectory() || galleryDir.mkdirs());

        if (withSpiderFile) {
            SpiderInfo spiderInfo = new SpiderInfo();
            spiderInfo.gid = gid;
            spiderInfo.token = spiderToken;
            spiderInfo.pages = pagesInFile;
            spiderInfo.previewPages = 1;
            spiderInfo.previewPerPage = 10;
            spiderInfo.pTokenMap = new SparseArray<>();
            try (OutputStream os = new FileOutputStream(new File(galleryDir, ".anotherviewer"))) {
                spiderInfo.write(os);
            }
        }
        int images = Math.max(pagesInFile, 1);
        for (int i = 1; i <= images; i++) {
            try (FileOutputStream os = new FileOutputStream(imageFile(galleryDir, i))) {
                os.write(("page-" + i).getBytes(StandardCharsets.UTF_8));
            }
        }
        SiteDB.putDownloadDirname(gid, galleryDir.getName());

        GalleryInfo info = new GalleryInfo();
        info.gid = gid;
        info.token = spiderToken;
        info.title = "Title " + gid;
        info.thumb = "";
        info.uploader = "uploader";
        info.simpleTags = new String[]{"artist:alpha"};
        return info;
    }

    private static File imageFile(File galleryDir, int page) {
        return new File(galleryDir, String.format(Locale.US, "%08d.jpg", page));
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ------------------------------------------------------------------
    // pushed_gids 偏好辅助
    // ------------------------------------------------------------------

    private Set<String> pushedPrefs() {
        Set<String> value = service.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getStringSet(prefsKey, null);
        return value == null ? new HashSet<>() : value;
    }

    private void seedPushedRaw(String... entries) {
        service.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().putStringSet(prefsKey, new HashSet<>(Arrays.asList(entries))).commit();
    }

    private void clearPushedPrefs() {
        service.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().remove(prefsKey).commit();
    }

    private boolean prefsContainFresh(long gid) {
        return DownloadUploadService.isPushedEntryFresh(pushedPrefs(), gid,
                System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // pushOne 反射调用（后台线程）
    // ------------------------------------------------------------------

    private int pushOne(GalleryInfo info) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        int[] result = {Integer.MIN_VALUE};
        Thread worker = new Thread(() -> {
            try {
                result[0] = (Integer) pushOneMethod.invoke(service, config, info);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse("pushOne must terminate", worker.isAlive());
        if (error.get() != null) {
            throw new AssertionError("pushOne crashed", error.get());
        }
        assertTrue("所有排队响应都应被消费", queued.isEmpty());
        return result[0];
    }

    private static String bodyOf(RecordedRequest request) {
        return new String(request.body, StandardCharsets.UTF_8);
    }

    private boolean hasPageRequest(int page) {
        for (RecordedRequest request : requests) {
            if (request.isPage(page)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 编排主流程：init → storePage → complete
    // ------------------------------------------------------------------

    @Test
    public void initSuccessUploadsAllPagesThenCompletesAndMarksPushed() throws Exception {
        GalleryInfo info = makeGallery(42L, "tok42", 2, true);
        enqueue(200, "{\"success\":true}");
        enqueue(200, "{}");   // page 1
        enqueue(200, "{}");   // page 2
        enqueue(200, "true"); // complete

        assertEquals(0, pushOne(info));

        assertEquals(4, requests.size());
        RecordedRequest init = requests.get(0);
        assertTrue(init.isPutInit(42L));
        assertEquals("Bearer " + TOKEN, init.authorization);
        assertFalse("首次推送不得带 force", bodyOf(init).contains("\"force\":true"));
        assertTrue("init body 携带画册元数据", bodyOf(init).contains("\"token\":\"tok42\"")
                && bodyOf(init).contains("\"pages\":2"));
        assertTrue(requests.get(1).isPage(1));
        assertTrue(requests.get(2).isPage(2));
        assertTrue(requests.get(3).isComplete());
        assertEquals("\"total\"/\"done\" 均为总页数",
                true, bodyOf(requests.get(3)).contains("\"total\":2")
                        && bodyOf(requests.get(3)).contains("\"done\":2"));
        assertTrue("init 成功即标记已推送（此后中断可续传）", prefsContainFresh(42L));
    }

    @Test
    public void conflictWithFreshEntryResumesForceAndOnlyMissingPages() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        seedPushedRaw(DownloadUploadService.encodePushedEntry(42L,
                System.currentTimeMillis() - 1000L));
        enqueue(400, "{\"success\":false,\"message\":\"gid exists\"}");
        enqueue(200, "{\"success\":true,\"existingPages\":[1]}");
        enqueue(200, "{}");   // 仅缺的 page 2
        enqueue(200, "true");

        assertEquals(0, pushOne(newGalleryInfo(42L, "tok42")));

        assertEquals("init 探测 + force 重试 + 缺失页 + complete", 4, requests.size());
        assertFalse("首个 PUT 是非 force 探测",
                bodyOf(requests.get(0)).contains("\"force\":true"));
        assertTrue("fresh 条目授权的第二个 PUT 必须 force=true",
                bodyOf(requests.get(1)).contains("\"force\":true"));
        assertFalse("服务器已有的页 1 不得重传", hasPageRequest(1));
        assertTrue("缺失页 2 必须补传", requests.get(2).isPage(2));
        assertTrue(requests.get(3).isComplete());
    }

    @Test
    public void conflictWithoutLocalEntrySkipsServerOwnedRow() throws Exception {
        makeGallery(7L, "tok7", 3, true);
        enqueue(400, "{\"success\":false,\"message\":\"gid exists\"}");

        assertEquals("非本设备推送过的 gid 一律跳过", 1, pushOne(newGalleryInfo(7L, "tok7")));

        assertEquals("只发一次 init 探测", 1, requests.size());
        assertFalse(hasPageRequest(1));
    }

    @Test
    public void non2xxJsonBodyOnInitBehavesLikeConflictAndSkips() throws Exception {
        // uploadInit 对任何非 2xx 都解析 body；success=false 即走冲突分支，
        // 本设备未推送过 → 保守跳过（与 400 冲突同语义，不区分 4xx/5xx）。
        makeGallery(9L, "tok9", 1, true);
        enqueue(500, "{\"success\":false,\"message\":\"internal\"}");

        assertEquals(1, pushOne(newGalleryInfo(9L, "tok9")));
        assertEquals(1, requests.size());
    }

    @Test
    public void ioFailureOnInitReportsFailure() throws Exception {
        // 不排队任何响应：假 client 直接抛 IOException（模拟网络不可达），
        // pushOne 捕获后必须按失败处理。
        makeGallery(10L, "tok10", 1, true);

        assertEquals(2, pushOne(newGalleryInfo(10L, "tok10")));
        assertEquals(1, requests.size());
    }

    // ------------------------------------------------------------------
    // A2：pushed_gids TTL 过期交互（本泳道核心）
    // ------------------------------------------------------------------

    @Test
    public void expiredTtlEntryDegradesForceResumeToSkip() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        long ttl = DownloadUploadService.PUSHED_GID_TTL_MS;
        seedPushedRaw(DownloadUploadService.encodePushedEntry(42L,
                System.currentTimeMillis() - ttl - 1L));
        enqueue(400, "{\"success\":false,\"message\":\"gid exists\"}");

        assertEquals("过期条目不得授权 force 续传——保守跳过",
                1, pushOne(newGalleryInfo(42L, "tok42")));
        assertEquals(1, requests.size());
        assertFalse(hasPageRequest(1));
        assertFalse(hasPageRequest(2));
    }

    @Test
    public void freshTtlBoundaryEntryStillAuthorizesForceResume() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        long ttl = DownloadUploadService.PUSHED_GID_TTL_MS;
        seedPushedRaw(DownloadUploadService.encodePushedEntry(42L,
                System.currentTimeMillis() - ttl + 60_000L));
        enqueue(400, "{\"success\":false,\"message\":\"gid exists\"}");
        enqueue(200, "{\"success\":true,\"existingPages\":[1,2]}");
        enqueue(200, "true");

        assertEquals("TTL 窗口内仍可断点续传（此处无缺页，直接 complete）",
                0, pushOne(newGalleryInfo(42L, "tok42")));
        assertTrue(bodyOf(requests.get(1)).contains("\"force\":true"));
        assertFalse(hasPageRequest(1));
        assertFalse(hasPageRequest(2));
        assertTrue(requests.get(2).isComplete());
    }

    @Test
    public void legacyBareGidEntryAlsoDegradesToSkip() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        seedPushedRaw("42");
        enqueue(400, "{\"success\":false}");

        assertEquals("旧版裸 gid 条目无法证明新鲜度 → 跳过",
                1, pushOne(newGalleryInfo(42L, "tok42")));
        assertEquals(1, requests.size());
    }

    @Test
    public void corruptTimestampEntryAlsoDegradesToSkip() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        seedPushedRaw("42:not-a-number");
        enqueue(400, "{\"success\":false}");

        assertEquals(1, pushOne(newGalleryInfo(42L, "tok42")));
        assertEquals(1, requests.size());
    }

    @Test
    public void entryForDifferentGidDoesNotAuthorizeThisRow() throws Exception {
        makeGallery(43L, "tok43", 2, true);
        seedPushedRaw(DownloadUploadService.encodePushedEntry(42L,
                System.currentTimeMillis()));
        enqueue(400, "{\"success\":false}");

        assertEquals("条目按 gid 区分，别行的鲜活条目不授权本行",
                1, pushOne(newGalleryInfo(43L, "tok43")));
        assertEquals(1, requests.size());
    }

    // ------------------------------------------------------------------
    // 失败路径与防御
    // ------------------------------------------------------------------

    @Test
    public void failedPageUploadAbortsAsFailureButKeepsPushedMark() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        enqueue(200, "{\"success\":true}");
        enqueue(500, "err"); // page 1 fails

        assertEquals(2, pushOne(newGalleryInfo(42L, "tok42")));

        assertEquals(2, requests.size());
        assertFalse("complete 不得在页失败后调用", requests.get(1).isComplete());
        assertTrue("中断前已 markPushed，下次可 force 续传", prefsContainFresh(42L));
    }

    @Test
    public void failedCompleteReportsFailure() throws Exception {
        makeGallery(42L, "tok42", 2, true);
        enqueue(200, "{\"success\":true}");
        enqueue(200, "{}");
        enqueue(200, "{}");
        enqueue(500, "err");

        assertEquals(2, pushOne(newGalleryInfo(42L, "tok42")));
        assertEquals(4, requests.size());
        assertTrue(requests.get(3).isComplete());
    }

    @Test
    public void missingOrMismatchedSpiderInfoFailsWithoutNetwork() throws Exception {
        // 无 .anotherviewer 文件。
        makeGallery(50L, "tok50", 2, false);
        assertEquals(2, pushOne(newGalleryInfo(50L, "tok50")));
        assertEquals(0, requests.size());

        // spider_info 的 token 与 GalleryInfo 不一致 → 视为无效。
        makeGallery(51L, "info-token", 2, true);
        assertEquals(2, pushOne(newGalleryInfo(51L, "other-token")));
        assertEquals(0, requests.size());
    }

    @Test
    public void nonPositivePagesInSpiderInfoFailsWithoutNetwork() throws Exception {
        makeGallery(52L, "tok52", /* pagesInFile */ 0, true);
        assertEquals(2, pushOne(newGalleryInfo(52L, "tok52")));
        assertEquals(0, requests.size());
    }

    // ------------------------------------------------------------------
    // markPushed 写入剪枝（经服务实例的真实偏好持久化）
    // ------------------------------------------------------------------

    @Test
    public void markPushedPersistsEntryAndPrunesStaleCorruptLegacyOnes() throws Throwable {
        makeGallery(200L, "tok200", 1, true);
        Method mark = DownloadUploadService.class.getDeclaredMethod("markPushed", long.class);
        mark.setAccessible(true);

        long freshAt = System.currentTimeMillis() - 1000L;
        String stale = DownloadUploadService.encodePushedEntry(101L,
                System.currentTimeMillis() - DownloadUploadService.PUSHED_GID_TTL_MS - 5L);
        seedPushedRaw(
                DownloadUploadService.encodePushedEntry(100L, freshAt), // 鲜活：保留
                stale,                                                  // 过期：剪除
                "102:corrupt",                                          // 损坏：剪除
                "103");                                                 // 旧版裸 gid：剪除

        mark.invoke(service, 200L);

        Set<String> stored = pushedPrefs();
        assertTrue(stored.contains(DownloadUploadService.encodePushedEntry(100L, freshAt)));
        boolean hasNewEntry = false;
        for (String entry : stored) {
            if (entry.startsWith("200:") && entry.substring(4).matches("\\d+")) {
                hasNewEntry = true;
            }
        }
        assertTrue("新 gid 必须写入", hasNewEntry);
        assertFalse(stored.contains(stale));
        assertFalse(stored.contains("102:corrupt"));
        assertFalse(stored.contains("103"));
        assertEquals("集合只含存活条目", 2, stored.size());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static GalleryInfo newGalleryInfo(long gid, String token) {
        GalleryInfo info = new GalleryInfo();
        info.gid = gid;
        info.token = token;
        info.title = "Title " + gid;
        info.thumb = "";
        info.uploader = "uploader";
        info.simpleTags = new String[]{"artist:alpha"};
        return info;
    }
}

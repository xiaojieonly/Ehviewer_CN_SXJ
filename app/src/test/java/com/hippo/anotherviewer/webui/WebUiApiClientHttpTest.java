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

package com.hippo.anotherviewer.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.ParserConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * TH7：WebUiApiClient 的 HTTP 层行为，经 {@link LocalHttpServer}（JDK 真实
 * socket + 真实 OkHttp 栈）钉死——mockwebserver 非 test 依赖，按泳道约束不自行
 * 添加。覆盖：非 2xx 抛错语义、空 body 处理、fastjson safeMode 行为、Bearer
 * 附载头，以及各端点的方法/路径/body 形状。
 */
public class WebUiApiClientHttpTest {

    private LocalHttpServer server;
    private volatile int responseCode = 200;
    private volatile String responseBody = "{}";
    private volatile boolean sendNoBody;
    private volatile boolean rawMode;
    private WebUiConfig config;

    @BeforeClass
    public static void forceClassInit() throws ClassNotFoundException {
        // 静态块把全局 safeMode 硬关 autotype；类字面量只加载不初始化，
        // 必须 Class.forName 触发 <clinit> 再断言。
        Class.forName("com.hippo.anotherviewer.webui.WebUiApiClient");
        assertTrue("static init 必须开启 fastjson safeMode",
                ParserConfig.getGlobalInstance().isSafeMode());
    }

    @Before
    public void setUp() throws IOException {
        sendNoBody = false;
        rawMode = false;
        server = LocalHttpServer.start(request -> {
            if (rawMode) {
                return LocalHttpServer.Response.raw(responseCode,
                        responseCode >= 400 ? "Err" : "OK",
                        responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (sendNoBody) {
                return LocalHttpServer.Response.noBody(responseCode);
            }
            return LocalHttpServer.Response.json(responseCode, responseBody);
        });
        config = new WebUiConfig("http", server.host(), server.port(), "", "");
    }

    @After
    public void tearDown() {
        server.stop();
    }

    /** 等待请求完整到达并捕获之（随后重置闩锁以迎接下一个请求）。 */
    private LocalHttpServer.Request capture() throws InterruptedException {
        server.awaitHandled();
        LocalHttpServer.Request request = server.lastRequest();
        server.nextRequest();
        return request;
    }

    private void respond(int code, String body) {
        this.responseCode = code;
        this.responseBody = body;
    }

    // ------------------------------------------------------------------
    // 非 2xx 抛错语义
    // ------------------------------------------------------------------

    @Test
    public void non2xxStatusThrowsIOExceptionWithCode() throws Exception {
        respond(401, "{\"error\":\"bad token\"}");
        try {
            WebUiApiClient.status(config);
            fail("401 must surface as IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 401"));
        }
        assertEquals("/api/v1/sync/status", capture().path);
    }

    @Test
    public void non2xxPullAndPushThrowIOException() throws Exception {
        respond(500, "boom");
        try {
            WebUiApiClient.pull(config, 0);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
        assertEquals("/api/v1/sync/pull", capture().path);

        try {
            WebUiApiClient.push(config, new WebUiSyncModels.PushRequest());
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
        assertEquals("/api/v1/sync/push", capture().path);
    }

    @Test
    public void registerDevice404SurfacesAsIOException() throws Exception {
        respond(404, "not found");
        try {
            WebUiApiClient.registerDevice(config, "dev", "name", "android", null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("HTTP 404"));
        }
        assertEquals("/api/v1/auth/register-device", capture().path);
    }

    // ------------------------------------------------------------------
    // 空 body 处理
    // ------------------------------------------------------------------

    @Test
    public void zeroLengthBodyLoginThrowsEmptyResponseError() throws Exception {
        respond(200, "");
        try {
            WebUiApiClient.login(config, "u", "p");
            fail("空 body 解析为 null 必须抛 IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("login"));
        }
        capture();
    }

    @Test
    public void absentBodyStatusThrowsEmptyResponseError() throws Exception {
        sendNoBody = true; // 无 Content-Length、无 Transfer-Encoding 的响应
        try {
            WebUiApiClient.status(config);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("status"));
        }
        capture();
    }

    @Test
    public void emptyBodyListDownloadsYieldsEmptyListNotError() throws Exception {
        sendNoBody = true;
        List<WebUiDownloadModels.DownloadListItem> items = WebUiApiClient.listDownloads(config);
        assertTrue(items.isEmpty());
        assertEquals("/api/v1/download/list", capture().path);
    }

    // ------------------------------------------------------------------
    // fastjson safeMode 行为
    // ------------------------------------------------------------------

    @Test
    public void safeModeRejectsAutoTypeGadgetInResponseBody() throws Exception {
        respond(200, "{\"@type\":\"java.net.InetAddress\",\"val\":\"localhost\",\"token\":\"t\"}");
        try {
            WebUiApiClient.status(config);
            fail("safe mode 必须拒绝 @type 载荷而不是实例化");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("safeMode"));
        }
        assertEquals("/api/v1/sync/status", capture().path);
    }

    @Test
    public void benignDtoParsingStillWorksUnderSafeMode() throws Exception {
        respond(200, "{\"success\":true,\"token\":\"tok-1\",\"username\":\"alice\"}");
        WebUiSyncModels.AuthResponse response = WebUiApiClient.login(config, "u", "p");
        assertTrue(response.success);
        assertEquals("tok-1", response.token);
        assertEquals("alice", response.username);
        capture();
    }

    // ------------------------------------------------------------------
    // Bearer 附载头 + 端点形状
    // ------------------------------------------------------------------

    @Test
    public void bearerHeaderAttachedWhenTokenConfigured() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "sekret-token");
        respond(200, "{\"lastSyncTimestamp\":42}");
        WebUiSyncModels.StatusResponse status = WebUiApiClient.status(config);
        assertEquals(42, status.lastSyncTimestamp);
        LocalHttpServer.Request request = capture();
        assertEquals("Bearer sekret-token", request.header("authorization"));
        assertEquals("GET", request.method);
        assertEquals("/api/v1/sync/status", request.path);
    }

    @Test
    public void emptyTokenOmitsAuthorizationHeader() throws Exception {
        respond(200, "{\"lastSyncTimestamp\":0}");
        WebUiApiClient.status(config);
        assertNull(capture().header("authorization"));
    }

    @Test
    public void permitAllEndpointsNeverSendAuthorization() throws Exception {
        respond(200, "{\"success\":true,\"token\":\"t\"}");
        WebUiApiClient.pairComplete(config, "123456", "device", "Pixel", "android");
        LocalHttpServer.Request pair = capture();
        assertNull(pair.header("authorization"));
        assertEquals("/api/v1/auth/pair/complete", pair.path);
        WebUiSyncModels.PairCompleteRequest req =
                JSON.parseObject(pair.bodyText(), WebUiSyncModels.PairCompleteRequest.class);
        assertEquals("123456", req.code);
        assertEquals("device", req.deviceId);
        assertEquals("android", req.platform);

        respond(200, "{\"success\":true,\"token\":\"t\"}");
        WebUiApiClient.registerDevice(config, "device2", "Nexus", "android", "setup-key");
        LocalHttpServer.Request reg = capture();
        assertNull(reg.header("authorization"));
        assertEquals("/api/v1/auth/register-device", reg.path);
        WebUiSyncModels.RegisterDeviceRequest regReq =
                JSON.parseObject(reg.bodyText(), WebUiSyncModels.RegisterDeviceRequest.class);
        assertEquals("setup-key", regReq.setupKey);

        respond(200, "{\"success\":true,\"token\":\"tok\"}");
        WebUiSyncModels.AuthResponse login = WebUiApiClient.login(config, "bob", "pw");
        assertEquals("tok", login.token);
        LocalHttpServer.Request loginCap = capture();
        assertNull(loginCap.header("authorization"));
        assertNotNull(JSON.parseObject(loginCap.bodyText(),
                WebUiSyncModels.LoginRequest.class).password);
    }

    @Test
    public void pullSendsSinceAsQueryParameter() throws Exception {
        respond(200, "{\"serverTimestamp\":99,\"entities\":{\"favorites\":[]}}");
        WebUiSyncModels.PullResponse pull = WebUiApiClient.pull(config, 123456789L);
        assertEquals(99, pull.serverTimestamp);
        LocalHttpServer.Request request = capture();
        assertEquals("/api/v1/sync/pull", request.path);
        assertEquals("since=123456789", request.query);
        assertNull("未配置 token 时不得带 Authorization", request.header("authorization"));
    }

    @Test
    public void pushPostsJsonAndParsesConflicts() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "tk");
        respond(200, "{\"success\":true,\"serverTimestamp\":777,\"conflicts\":3}");
        WebUiSyncModels.PushResponse response =
                WebUiApiClient.push(config, new WebUiSyncModels.PushRequest());
        assertTrue(response.success);
        assertEquals(777, response.serverTimestamp);
        assertEquals(3, response.conflicts);
        LocalHttpServer.Request request = capture();
        assertEquals("POST", request.method);
        assertEquals("/api/v1/sync/push", request.path);
        assertEquals("Bearer tk", request.header("authorization"));
        assertTrue(request.header("content-type").startsWith("application/json"));
    }

    @Test
    public void getGalleryPagesReturnsPositiveCountOrSentinel() throws Exception {
        respond(200, "{\"pages\":20}");
        assertEquals(20, WebUiApiClient.getGalleryPages(config, 1001L));
        assertEquals("/api/v1/gallery/1001", capture().path);

        respond(200, "{\"pages\":0}");
        assertEquals(-1, WebUiApiClient.getGalleryPages(config, 1001L));
        capture();
    }

    @Test
    public void fetchImageStreamsRawBytesAndKeepsNon2xxForCaller() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "img-tk");

        byte[] payload = "BINARY-LIKE-PAYLOAD".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        rawMode = true;
        respond(200, new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        okhttp3.Response ok = WebUiApiClient.fetchImage(config, 55L, 3);
        try {
            assertEquals(200, ok.code());
            assertEquals("原始字节流必须原样交回调用方",
                    payload.length, ok.body().contentLength());
        } finally {
            ok.close();
        }
        LocalHttpServer.Request imageReq = capture();
        assertEquals("/api/v1/image/55/3", imageReq.path);
        assertEquals("Bearer img-tk", imageReq.header("authorization"));

        respond(502, "upstream down");
        okhttp3.Response bad = WebUiApiClient.fetchImage(config, 55L, 3);
        try {
            assertEquals("fetchImage 不替调用方判错：非 2xx 原样交回", 502, bad.code());
        } finally {
            bad.close();
        }
        capture();
    }

    @Test
    public void downloadEndpointsParseBooleansAndLists() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "dtk");

        respond(200, "true");
        assertTrue(WebUiApiClient.addDownload(config,
                new WebUiDownloadModels.DownloadAddRequest()));
        LocalHttpServer.Request addCap = capture();
        assertEquals("/api/v1/download/add", addCap.path);
        assertEquals("Bearer dtk", addCap.header("authorization"));

        respond(200, "false");
        assertFalse(WebUiApiClient.addDownload(config,
                new WebUiDownloadModels.DownloadAddRequest()));
        capture();

        respond(200, "{\"downloads\":[{\"id\":5,\"gid\":9001,\"state\":2,\"title\":\"T\"}]}");
        List<WebUiDownloadModels.DownloadListItem> list = WebUiApiClient.listDownloads(config);
        assertEquals(1, list.size());
        assertEquals(5, list.get(0).id);
        assertEquals(9001, list.get(0).gid);
        assertEquals("/api/v1/download/list", capture().path);

        respond(200, "true");
        assertTrue(WebUiApiClient.startDownload(config, 5L));
        assertEquals("/api/v1/download/start/5", capture().path);

        respond(500, "err");
        try {
            WebUiApiClient.startDownload(config, 5L);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
        capture();
    }

    // ------------------------------------------------------------------
    // 空 body 处理（补充）：listDownloads 的 no-body 分支已覆盖；
    // status/push 的空 body 报错文案区分端点。
    // ------------------------------------------------------------------

    @Test
    public void emptyBodyPullThrowsEmptyResponseError() throws Exception {
        respond(200, "");
        try {
            WebUiApiClient.pull(config, 5L);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("pull"));
        }
        capture();
    }

    @Test
    public void nonBooleanBodyOnAddDownloadParsesAsFalse() throws Exception {
        respond(200, "{\"unexpected\":\"shape\"}");
        assertFalse("非布尔 body 按 Boolean.parseBoolean 兜底为 false",
                WebUiApiClient.addDownload(config, new WebUiDownloadModels.DownloadAddRequest()));
        capture();
    }
}

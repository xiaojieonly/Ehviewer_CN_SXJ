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
import com.hippo.unifile.UniFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TH7：WebUiUploadClient（推送上传 REST 客户端）的 HTTP 层行为，经
 * {@link LocalHttpServer}（JDK 真实 socket + 真实 OkHttp 栈）钉死。
 * 关键语义差异：uploadInit 对非 2xx 仍解析 body（400 + success=false 是「gid
 * 冲突」的协议内表达，由调用方决定跳过），空 body 解析为 null 时才抛
 * IOException；uploadPage/uploadComplete 只回传 isSuccessful 布尔。multipart
 * part 的文件名与扩展名→content-type 映射一并覆盖。UniFile 走真实临时文件
 * （Robolectric 提供运行环境）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class WebUiUploadClientHttpTest {

    private LocalHttpServer server;
    private volatile int responseCode = 200;
    private volatile String responseBody = "{}";
    private volatile boolean sendNoBody;
    private WebUiConfig config;
    private File tempDir;

    @Before
    public void setUp() throws IOException {
        sendNoBody = false;
        server = LocalHttpServer.start(request -> {
            if (sendNoBody) {
                return LocalHttpServer.Response.noBody(responseCode);
            }
            return LocalHttpServer.Response.json(responseCode, responseBody);
        });
        config = new WebUiConfig("http", server.host(), server.port(), "", "");

        tempDir = new File(System.getProperty("java.io.tmpdir"),
                "wui-upload-test-" + System.nanoTime());
        assertTrue(tempDir.mkdirs());
    }

    @After
    public void tearDown() {
        server.stop();
        deleteRecursive(tempDir);
    }

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

    private UniFile newPageFile(String name, byte[] content) throws IOException {
        File file = new File(tempDir, name);
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(content);
        }
        UniFile uniFile = UniFile.fromFile(file);
        assertNotNull(uniFile);
        return uniFile;
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
    // uploadInit：非 2xx 不抛错而是解析 body；空 body 才抛错
    // ------------------------------------------------------------------

    @Test
    public void initParsesSuccessResponseAndPutsBearer() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "push-tk");
        respond(200, "{\"success\":true,\"existingPages\":null}");
        WebUiUploadModels.UploadInitRequest request = new WebUiUploadModels.UploadInitRequest();
        request.token = "gallery-token";
        request.pages = 5;

        WebUiUploadModels.InitResponse response =
                WebUiUploadClient.uploadInit(config, 42L, request);

        assertTrue(response.success);
        assertNull(response.existingPages);
        LocalHttpServer.Request captured = capture();
        assertEquals("PUT", captured.method);
        assertEquals("/api/v1/download/upload/42", captured.path);
        assertEquals("Bearer push-tk", captured.header("authorization"));
        WebUiUploadModels.UploadInitRequest sent = JSON.parseObject(
                captured.bodyText(), WebUiUploadModels.UploadInitRequest.class);
        assertEquals("gallery-token", sent.token);
        assertEquals(5, sent.pages);
        assertFalse(sent.force);
    }

    @Test
    public void initConflictAnswersParsedSuccessFalseInsteadOfThrowing() throws Exception {
        respond(400, "{\"success\":false,\"message\":\"gid exists\"}");
        WebUiUploadModels.InitResponse response = WebUiUploadClient.uploadInit(
                config, 42L, new WebUiUploadModels.UploadInitRequest());

        assertFalse("非 force 冲突以 400 + success=false 表达，由调用方决定跳过",
                response.success);
        assertEquals("gid exists", response.message);
        assertEquals("/api/v1/download/upload/42", capture().path);
    }

    @Test
    public void initNonJsonErrorPageThrowsIOExceptionNotRuntime() throws Exception {
        // 反代/错误页返回 HTML（如 502 页）：必须包装为 IOException（pushOne 只捕
        // IOException），RuntimeException 逃逸会击穿 executor 线程令前台服务崩溃。
        respond(502, "<html><body>502 Bad Gateway</body></html>");
        try {
            WebUiUploadClient.uploadInit(config, 43L, new WebUiUploadModels.UploadInitRequest());
            fail("非 JSON body 必须包装为 IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Malformed init response"));
            assertTrue(e.getMessage(), e.getMessage().contains("502"));
        }
        capture();
    }

    @Test
    public void initEmptyBodyThrowsIOExceptionWithStatus() throws Exception {
        respond(200, "");
        try {
            WebUiUploadClient.uploadInit(config, 1L, new WebUiUploadModels.UploadInitRequest());
            fail("body 解析为 null 必须抛 IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 200"));
        }
        capture();

        sendNoBody = true; // 无 body 响应同样按空串处理
        try {
            WebUiUploadClient.uploadInit(config, 1L, new WebUiUploadModels.UploadInitRequest());
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 200"));
        }
        capture();
    }

    // ------------------------------------------------------------------
    // uploadPage：multipart、扩展名→content-type、布尔语义
    // ------------------------------------------------------------------

    @Test
    public void pageUploadReturnsTrueOn2xxAndSendsMultipart() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "pg-tk");
        byte[] payload = "fake-jpeg-bytes-0x00FF".getBytes(StandardCharsets.UTF_8);
        respond(200, "{}");

        assertTrue(WebUiUploadClient.uploadPage(config, 42L, 1,
                newPageFile("00000001.jpg", payload)));

        LocalHttpServer.Request captured = capture();
        assertEquals("POST", captured.method);
        assertEquals("/api/v1/download/upload/42/page/1", captured.path);
        assertEquals("Bearer pg-tk", captured.header("authorization"));
        assertNotNull(captured.header("content-type"));
        assertTrue(captured.header("content-type").startsWith("multipart/form-data"));
        String bodyText = captured.bodyText();
        assertTrue("文件名必须随 part 上送", bodyText.contains("filename=\"00000001.jpg\""));
        assertTrue(".jpg part 应为 image/jpeg", bodyText.contains("Content-Type: image/jpeg"));
        assertTrue("页内容必须原样上送",
                indexOf(captured.body, payload) >= 0);
    }

    @Test
    public void pageUploadNon2xxIsFalseNotThrow() throws Exception {
        respond(409, "{\"error\":\"page exists\"}");
        assertFalse(WebUiUploadClient.uploadPage(config, 42L, 2,
                newPageFile("00000002.png", new byte[]{1, 2, 3})));
        LocalHttpServer.Request captured = capture();
        assertTrue(new String(captured.body, StandardCharsets.UTF_8)
                .contains("Content-Type: image/png"));
        assertNull("未配置 token 时不带 Authorization", captured.header("authorization"));
    }

    @Test
    public void unknownExtensionFallsBackToOctetStream() throws Exception {
        respond(200, "{}");
        assertTrue(WebUiUploadClient.uploadPage(config, 42L, 3,
                newPageFile("00000003.txt", new byte[]{9})));
        assertTrue(capture().bodyText()
                .contains("Content-Type: application/octet-stream"));
    }

    @Test
    public void jpegUpperCaseExtensionMapsToJpeg() throws Exception {
        respond(200, "{}");
        assertTrue(WebUiUploadClient.uploadPage(config, 42L, 4,
                newPageFile("00000004.JPEG", new byte[]{9})));
        assertTrue(capture().bodyText().contains("Content-Type: image/jpeg"));
    }

    // ------------------------------------------------------------------
    // uploadComplete：布尔语义 + CompleteRequest 形状
    // ------------------------------------------------------------------

    @Test
    public void completeSendsTotalDoneAndParsesBoolean() throws Exception {
        config = new WebUiConfig("http", config.getHost(), config.getPort(), "", "done-tk");
        respond(200, "true");
        assertTrue(WebUiUploadClient.uploadComplete(config, 42L, 20));

        LocalHttpServer.Request captured = capture();
        assertEquals("POST", captured.method);
        assertEquals("/api/v1/download/upload/42/complete", captured.path);
        assertEquals("Bearer done-tk", captured.header("authorization"));
        WebUiUploadModels.CompleteRequest sent = JSON.parseObject(
                captured.bodyText(), WebUiUploadModels.CompleteRequest.class);
        assertEquals(20, sent.total);
        assertEquals(20, sent.done);

        respond(500, "err");
        assertFalse(WebUiUploadClient.uploadComplete(config, 42L, 20));
        capture();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}

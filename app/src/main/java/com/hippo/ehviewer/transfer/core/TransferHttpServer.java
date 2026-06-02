/*
 * Copyright 2025 EhViewer Contributors
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

package com.hippo.ehviewer.transfer.core;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP传输服务器
 * 提供REST API接口用于文件传输和任务管理
 *
 * 端点:
 * - GET  /api/v1/device/info             获取设备信息
 * - POST /api/v1/transfer/tasks          创建传输任务（type: backup|restore|sync|bookmarks|downloads|favorites|export）
 * - GET  /api/v1/transfer/tasks/{id}     查询传输状态
 * - PATCH /api/v1/transfer/tasks/{id}    暂停/恢复传输
 * - PUT  /api/v1/transfer/files/{fileId} 分块上传文件（Content-Range 可选）
 */
public class TransferHttpServer {

    private static final String TAG = "TransferHttpServer";
    private final int port;
    private final TransferServerManager serverManager;
    private boolean isRunning = false;

    private HttpServerImpl httpServer;

    // 内存中的任务与文件状态（示例实现）
    private final Map<String, Map<String, Object>> tasks = new HashMap<>();
    private final Map<String, Long> fileTransferred = new HashMap<>();

    public TransferHttpServer(int port, TransferServerManager serverManager) {
        this.port = port;
        this.serverManager = serverManager;
    }

    /** 启动HTTP服务器 */
    public void start() throws Exception {
        Log.d(TAG, "Starting HTTP server on port " + port);
        httpServer = new HttpServerImpl(port);
        httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        isRunning = true;
        Log.d(TAG, "HTTP server started");
    }

    /** 停止HTTP服务器 */
    public void stop() throws Exception {
        Log.d(TAG, "Stopping HTTP server");
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        isRunning = false;
        Log.d(TAG, "HTTP server stopped");
    }

    /** 检查服务器是否运行 */
    public boolean isRunning() { return isRunning; }

    /** 获取服务器端口 */
    public int getPort() { return port; }

    private class HttpServerImpl extends NanoHTTPD {
        HttpServerImpl(int port) { super(port); }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Method method = session.getMethod();
            Log.d(TAG, method + " " + uri);

            try {
                if (Method.GET.equals(method) && "/api/v1/device/info".equals(uri)) {
                    String deviceName = android.os.Build.MODEL;
                    String json = "{" +
                            "\"version\":\"1.0\"," +
                            "\"device_name\":\"" + deviceName + "\"," +
                            "\"device_type\":\"android\"," +
                            "\"capabilities\":\"file_transfer,backup,restore\"" +
                            "}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                if (Method.POST.equals(method) && "/api/v1/transfer/tasks".equals(uri)) {
                    String body = readBody(session);
                    String taskId = UUID.randomUUID().toString();
                    Map<String, Object> status = new HashMap<>();
                    status.put("task_id", taskId);
                    status.put("status", "pending");
                    status.put("type", extractJsonField(body, "type", "backup"));
                    status.put("transferred_files", 0);
                    status.put("total_files", 0);
                    tasks.put(taskId, status);
                    String json = "{\"task_id\":\"" + taskId + "\",\"status\":\"pending\"}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                if (Method.GET.equals(method) && uri.startsWith("/api/v1/transfer/tasks/")) {
                    String taskId = uri.substring("/api/v1/transfer/tasks/".length());
                    Map<String, Object> status = tasks.get(taskId);
                    if (status == null) {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{}");
                    }
                    String json = toJson(status);
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                if (Method.PATCH.equals(method) && uri.startsWith("/api/v1/transfer/tasks/")) {
                    String taskId = uri.substring("/api/v1/transfer/tasks/".length());
                    Map<String, Object> status = tasks.get(taskId);
                    String body = readBody(session);
                    String newStatus = extractJsonField(body, "status", null);
                    if (status != null && newStatus != null) {
                        status.put("status", newStatus);
                        return newFixedLengthResponse(Response.Status.OK, "application/json", toJson(status));
                    }
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{}");
                }

                if (Method.PUT.equals(method) && uri.startsWith("/api/v1/transfer/files/")) {
                    String fileId = uri.substring("/api/v1/transfer/files/".length());
                    long contentLength = getContentLength(session);
                    Long progressed = fileTransferred.getOrDefault(fileId, 0L);
                    progressed += contentLength;
                    fileTransferred.put(fileId, progressed);
                    String json = "{\"file_id\":\"" + fileId + "\",\"received\":" + progressed + "}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                // 简易导入/导出占位端点（客户端四项）
                if (Method.POST.equals(method) && uri.startsWith("/api/v1/import/")) {
                    String type = uri.substring("/api/v1/import/".length());
                    // 这里只做占位，实际导入留待后续实现
                    readBody(session);
                    String json = "{\"result\":\"accepted\",\"type\":\"" + type + "\"}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                if (Method.GET.equals(method) && uri.startsWith("/api/v1/export/")) {
                    String type = uri.substring("/api/v1/export/".length());
                    // 返回占位数据
                    String json = "{\"type\":\"" + type + "\",\"data\":[]}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{}");
            } catch (Exception e) {
                Log.e(TAG, "HTTP handle error", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{}");
            }
        }

        private String readBody(IHTTPSession session) throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream is = session.getInputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) {
                bos.write(buf, 0, r);
                // NanoHTTPD input stream provides only request body; break if available length consumed
                if (bos.size() >= getContentLength(session)) break;
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        }

        private long getContentLength(IHTTPSession session) {
            String len = session.getHeaders().get("content-length");
            try { return len != null ? Long.parseLong(len) : 0L; } catch (Exception ignored) { return 0L; }
        }

        private String extractJsonField(String json, String field, String def) {
            if (json == null) return def;
            String key = "\"" + field + "\":";
            int idx = json.indexOf(key);
            if (idx < 0) return def;
            int start = json.indexOf('"', idx + key.length());
            int end = json.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return json.substring(start + 1, end);
            }
            return def;
        }

        private String toJson(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':');
                Object v = e.getValue();
                if (v instanceof Number) {
                    sb.append(v.toString());
                } else {
                    sb.append('"').append(String.valueOf(v)).append('"');
                }
            }
            sb.append('}');
            return sb.toString();
        }
    }
}

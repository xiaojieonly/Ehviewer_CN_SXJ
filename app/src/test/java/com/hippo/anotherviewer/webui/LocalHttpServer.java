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

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TH7 专用迷你 HTTP/1.1 测试服务器：仅用 {@link ServerSocket}（android.jar
 * 编译可见、单测运行时为真实 JDK 实现），不引入任何新依赖（mockwebserver 非
 * test 依赖，按泳道约束不自行添加）。逐请求一连接，支持 Content-Length 与
 * chunked 请求体（OkHttp 对未知长度的 multipart 走 chunked）。响应固定
 * {@code Connection: close}，客户端每请求重连，语义最简、无状态复用歧义。
 */
public final class LocalHttpServer {

    /** 已完整接收的一个请求。 */
    public static final class Request {
        public final String method;
        public final String path;
        public final String query;
        public final byte[] body;
        private final Map<String, String> headers;

        Request(String method, String path, String query,
                Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        public String header(String lowerCaseName) {
            return headers.get(lowerCaseName);
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** 服务端回写的一个响应。 */
    public static final class Response {
        final int code;
        final String reason;
        final byte[] body;

        private Response(int code, String reason, byte[] body) {
            this.code = code;
            this.reason = reason;
            this.body = body;
        }

        public static Response json(int code, String body) {
            return new Response(code, code >= 400 ? "Err" : "OK",
                    body.getBytes(StandardCharsets.UTF_8));
        }

        public static Response raw(int code, String reason, byte[] body) {
            return new Response(code, reason, body);
        }

        /** 不发 body 也不发 Content-Length（模拟空响应）。 */
        public static Response noBody(int code) {
            return new Response(code, code >= 400 ? "Err" : "OK", null);
        }
    }

    public interface Handler {
        Response handle(Request request) throws IOException;
    }

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Request> lastRequest = new AtomicReference<>();
    private volatile CountDownLatch handled = new CountDownLatch(1);

    public static LocalHttpServer start(Handler handler) throws IOException {
        return new LocalHttpServer(handler);
    }

    private LocalHttpServer(Handler handler) throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor.execute(() -> serveLoop(handler));
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public String host() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    /** 等待最近一次请求被完整接收并已回写响应。 */
    public void awaitHandled() throws InterruptedException {
        Assert.assertTrue("request not handled in time",
                handled.await(10, TimeUnit.SECONDS));
    }

    /** 重置闩锁以便断言下一次请求（多请求用例在每次发起前调用）。 */
    public void nextRequest() {
        handled = new CountDownLatch(1);
    }

    public Request lastRequest() {
        return lastRequest.get();
    }

    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        executor.shutdownNow();
    }

    private void serveLoop(Handler handler) {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                socket.setTcpNoDelay(true);
                serveOne(socket, handler);
            } catch (IOException serveEnded) {
                return; // stop() 关闭 ServerSocket 的正常退出路径
            }
        }
    }

    private void serveOne(Socket socket, Handler handler) {
        try {
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
            OutputStream out = socket.getOutputStream();

            Request request = readRequest(in);
            lastRequest.set(request);
            Response response = handler.handle(request);
            writeResponse(out, response);
            handled.countDown();
            socket.close(); // Connection: close
        } catch (IOException e) {
            handled.countDown();
        }
    }

    private static Request readRequest(BufferedInputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("empty request line");
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("bad request line: " + requestLine);
        }
        String target = parts[1];
        String path = target;
        String query = null;
        int qm = target.indexOf('?');
        if (qm >= 0) {
            path = target.substring(0, qm);
            query = target.substring(qm + 1);
        }

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }

        byte[] body = readBody(in, headers);
        return new Request(parts[0], path, query, headers, body);
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers)
            throws IOException {
        String te = headers.get("transfer-encoding");
        if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
            ByteArrayOutputStream chunks = new ByteArrayOutputStream();
            while (true) {
                int size = Integer.parseInt(readLine(in).split(";")[0].trim(), 16);
                if (size == 0) {
                    break; // 拖尾头与终结空行对断言无关；连接随即关闭
                }
                byte[] chunk = new byte[size];
                readFully(in, chunk);
                chunks.write(chunk, 0, size);
                readLine(in); // 每块后的 CRLF
            }
            return chunks.toByteArray();
        }
        String cl = headers.get("content-length");
        if (cl == null || "0".equals(cl.trim())) {
            return new byte[0];
        }
        byte[] body = new byte[Integer.parseInt(cl.trim())];
        readFully(in, body);
        return body;
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int off = 0;
        while (off < buffer.length) {
            int n = in.read(buffer, off, buffer.length - off);
            if (n < 0) throw new IOException("EOF mid-body");
            off += n;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        int c = -1;
        boolean any = false;
        while ((c = in.read()) != -1) {
            any = true;
            if (c == '\n') break;
            sb.append((char) c);
        }
        if (!any) return null;
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\r') sb.setLength(len - 1);
        return sb.toString();
    }

    private static void writeResponse(OutputStream out, Response response) throws IOException {
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(response.code).append(' ')
                .append(response.reason).append("\r\n")
                .append("Connection: close\r\n");
        if (response.body == null) {
            head.append("\r\n");
        } else {
            head.append("Content-Type: application/json\r\n")
                    .append("Content-Length: ").append(response.body.length).append("\r\n")
                    .append("\r\n");
        }
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        if (response.body != null && response.body.length > 0) {
            out.write(response.body);
        }
        out.flush();
    }
}

package com.hippo.ehviewer.server.api;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.server.util.ContentTypeUtils;
import com.hippo.ehviewer.server.util.PathValidator;
import com.hippo.ehviewer.server.util.ServerLog;
import com.hippo.unifile.UniFile;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimpleHttpServer {

    private static final int API_VERSION_V2 = 2;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LIMIT = 60;
    private static final int MAX_LIMIT = 300;
    private static final long MAX_THUMBNAIL_BYTES = 512 * 1024;
    private final Context context;
    private final UniFile root;
    private final int port;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread serverThread;

    public SimpleHttpServer(@NonNull Context context, @NonNull UniFile root, int port) {
        this.context = context.getApplicationContext();
        this.root = root;
        this.port = port;
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        running = true;
        serverSocket = new ServerSocket(port);
        serverThread = new Thread(this::acceptLoop, "LanServer-Accept");
        serverThread.start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        clientPool.shutdownNow();
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                clientPool.execute(() -> handleClient(socket));
            } catch (IOException e) {
                if (running) {
                    ServerLog.e("Accept failed: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void handleClient(@NonNull Socket socket) {
        try (Socket ignored = socket;
             BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
             OutputStream os = socket.getOutputStream()) {
            try {
                HttpRequest request = parseRequest(bis);
                if (request == null) {
                    return;
                }
                if (!"GET".equalsIgnoreCase(request.method)) {
                    writeText(os, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                    return;
                }

                if ("/".equals(request.path) || "/index.html".equals(request.path)) {
                    serveIndex(os);
                    return;
                }

                if ("/api/browse".equals(request.path)) {
                    serveBrowse(os, request.query);
                    return;
                }

                if ("/api/reader".equals(request.path)) {
                    serveReader(os, request.query);
                    return;
                }

                if ("/api/thumb".equals(request.path)) {
                    serveThumbnail(os, request.query.get("gid"));
                    return;
                }

                if ("/api/labels".equals(request.path)) {
                    serveLabels(os);
                    return;
                }

                if (request.path.startsWith("/files/")) {
                    String relative = request.path.substring("/files/".length());
                    serveFile(os, relative);
                    return;
                }

                writeJson(os, 404, jsonError("not_found", "Path not found"));
            } catch (Throwable e) {
                // Return a generic 500 to the client; log details server-side
                try {
                    writeJson(os, 500, jsonError("internal_error", "Server error"));
                } catch (IOException ioe) {
                    // Ignore write failures after error
                }
                ServerLog.e("Request handling error: " + e.getMessage());
            }
        } catch (Throwable e) {
            ServerLog.e("I/O error handling client: " + e.getMessage());
        }
    }

    private HttpRequest parseRequest(@NonNull InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
        String line = reader.readLine();
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return null;
        }

        String method = parts[0].trim();
        String target = parts[1].trim();

        while (true) {
            String header = reader.readLine();
            if (header == null || header.isEmpty()) {
                break;
            }
        }

        String path = target;
        String queryText = "";
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            queryText = target.substring(q + 1);
        }

        HttpRequest request = new HttpRequest();
        request.method = method;
        request.path = path;
        request.query = parseQuery(queryText);
        return request;
    }

    @NonNull
    private Map<String, String> parseQuery(@NonNull String query) {
        Map<String, String> map = new HashMap<>();
        if (query.isEmpty()) {
            return map;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                map.put(decodeComponent(pair), "");
            } else {
                map.put(decodeComponent(pair.substring(0, idx)), decodeComponent(pair.substring(idx + 1)));
            }
        }
        return map;
    }

    @NonNull
    private String decodeComponent(@NonNull String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
        } catch (Throwable ignored) {
            return raw;
        }
    }

    private void serveIndex(@NonNull OutputStream os) throws IOException {
        byte[] html = readAsset("server/index.html");
        writeBytes(os, 200, "text/html; charset=utf-8", html);
    }

    private void serveBrowse(@NonNull OutputStream os, @NonNull Map<String, String> query) throws IOException {
        String path = query.get("path");
        int version = parseVersion(query.get("version"));
        boolean includeThumbnails = parseBoolean(query.get("include_thumbnails"));
        String labelFilter = BrowseFilterUtils.normalizeFilterValue(query.get("label"));
        List<String> tagFilters = BrowseFilterUtils.parseTagFilters(query.get("tags"));
        String searchFilter = BrowseFilterUtils.normalizeFilterValue(query.get("search"));
        String sortField = BrowseFilterUtils.normalizeFilterValue(query.get("sort"));
        String sortOrder = BrowseFilterUtils.normalizeFilterValue(query.get("order"));
        Pagination pagination;
        try {
            pagination = parsePagination(query.get("page"), query.get("limit"));
        } catch (IllegalArgumentException e) {
            writeJson(os, 400, jsonError("invalid_request", e.getMessage()));
            return;
        }

        if (version >= API_VERSION_V2) {
            serveBrowseV2Indexed(os, path, includeThumbnails, labelFilter, tagFilters, searchFilter, sortField, sortOrder, pagination);
            return;
        }

        UniFile target = PathValidator.resolveRelativePath(root, path);
        if (target == null || !target.exists() || !target.isDirectory()) {
            ServerLog.e("Rejected browse path: " + path);
            writeJson(os, 403, jsonError("forbidden", "Invalid browse path"));
            return;
        }

        String currentPath = PathValidator.normalizePath(path);
        JSONObject result = new JSONObject();
        result.put("currentPath", currentPath);
        result.put("parentPath", parentPath(currentPath));

        UniFile[] children = target.listFiles();
        ArrayList<UniFile> rows = new ArrayList<>();
        if (children != null) {
            Collections.addAll(rows, children);
        }
        rows.sort(Comparator
                .comparing((UniFile f) -> !f.isDirectory())
                .thenComparing(f -> {
                    String name = f.getName();
                    return name == null ? "" : name.toLowerCase();
                }));

        MetadataContext metadataContext = version >= API_VERSION_V2
                ? buildMetadataContext(rows)
                : MetadataContext.empty();

        ArrayList<BrowseItem> browseItems = new ArrayList<>(rows.size());
        for (UniFile child : rows) {
            String name = child.getName();
            if (name == null) {
                continue;
            }
            String itemPath = currentPath.isEmpty() ? name : currentPath + "/" + name;
            String mimeType = child.isDirectory() ? "inode/directory" : ContentTypeUtils.fromName(name);

            BrowseMetadata metadata = null;
            if (version >= API_VERSION_V2) {
                metadata = resolveMetadata(child, name, metadataContext, includeThumbnails);
                if (!matchesFilters(metadata, labelFilter, tagFilters, searchFilter)) {
                    continue;
                }
            }

            BrowseItem item = new BrowseItem();
            item.name = name;
            item.path = itemPath;
            item.directory = child.isDirectory();
            item.size = child.isDirectory() ? 0 : Math.max(0, child.length());
            item.modified = child.lastModified();
            item.mimeType = mimeType;
            item.image = !child.isDirectory() && ContentTypeUtils.isImage(mimeType);
            item.url = child.isDirectory() ? null : "/files/" + PathValidator.encodePath(itemPath);
            item.metadata = metadata;
            browseItems.add(item);
        }

        List<BrowseItem> pageItems = browseItems;
        if (pagination.enabled) {
            int fromIndex = Math.min((pagination.page - 1) * pagination.limit, browseItems.size());
            int toIndex = Math.min(fromIndex + pagination.limit, browseItems.size());
            pageItems = browseItems.subList(fromIndex, toIndex);
            JSONObject pageObject = new JSONObject();
            pageObject.put("page", pagination.page);
            pageObject.put("limit", pagination.limit);
            pageObject.put("total", browseItems.size());
            pageObject.put("pageCount", browseItems.isEmpty() ? 0 : (int) Math.ceil(browseItems.size() / (double) pagination.limit));
            result.put("pagination", pageObject);
        }

        JSONArray items = new JSONArray();
        for (BrowseItem item : pageItems) {
            items.add(toJsonItem(item, version));
        }
        result.put("items", items);

        writeJson(os, 200, result);
    }

    private void serveBrowseV2Indexed(@NonNull OutputStream os,
                                      @Nullable String path,
                                      boolean includeThumbnails,
                                      @Nullable String labelFilter,
                                      @NonNull List<String> tagFilters,
                                      @Nullable String searchFilter,
                                      @Nullable String sortField,
                                      @Nullable String sortOrder,
                                      @NonNull Pagination pagination) throws IOException {
        String normalizedPath = PathValidator.normalizePath(path);
        if (!normalizedPath.isEmpty()) {
            writeJson(os, 403, jsonError("forbidden", "Indexed browse only supports root path"));
            return;
        }

        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("currentPath", "");
        result.put("parentPath", "");

        DownloadManager manager = EhApplication.getDownloadManager(context);
        List<DownloadInfo> downloads = manager.getAllDownloadInfoList();
        ArrayList<BrowseItem> browseItems = new ArrayList<>(downloads.size());

        for (DownloadInfo info : downloads) {
            BrowseItem item = buildIndexedItem(info, includeThumbnails);
            if (!matchesFilters(item.metadata, labelFilter, tagFilters, searchFilter)) {
                continue;
            }
            browseItems.add(item);
        }

        // Sort only when an explicit sort field is requested.
        // When no sort is specified, preserve the in-app download order from
        // getAllDownloadInfoList() (which reflects drag-reorder adjustments).
        if (sortField != null) {
            boolean ascending = "asc".equalsIgnoreCase(sortOrder != null ? sortOrder : "");
            Comparator<BrowseItem> itemComparator;
            if ("title".equalsIgnoreCase(sortField)) {
                Comparator<BrowseItem> c = Comparator.comparing(
                        i -> i.metadata != null && i.metadata.title != null ? i.metadata.title.toLowerCase() : "");
                itemComparator = ascending ? c : c.reversed();
            } else if ("rating".equalsIgnoreCase(sortField)) {
                Comparator<BrowseItem> c = Comparator.comparing(
                        i -> i.metadata != null && i.metadata.rating != null ? i.metadata.rating : 0f);
                itemComparator = ascending ? c : c.reversed();
            } else if ("pageCount".equalsIgnoreCase(sortField)) {
                Comparator<BrowseItem> c = Comparator.comparing(
                        i -> i.metadata != null && i.metadata.pageCount != null ? i.metadata.pageCount : 0);
                itemComparator = ascending ? c : c.reversed();
            } else if ("size".equalsIgnoreCase(sortField)) {
                Comparator<BrowseItem> c = Comparator.comparing(i -> i.size);
                itemComparator = ascending ? c : c.reversed();
            } else {
                // Explicit "modified" sort requested
                itemComparator = (a, b) -> ascending
                        ? Long.compare(a.modified, b.modified)
                        : Long.compare(b.modified, a.modified);
            }
            browseItems.sort(itemComparator);
        }

        List<BrowseItem> pageItems = browseItems;
        if (pagination.enabled) {
            int fromIndex = Math.min((pagination.page - 1) * pagination.limit, browseItems.size());
            int toIndex = Math.min(fromIndex + pagination.limit, browseItems.size());
            pageItems = browseItems.subList(fromIndex, toIndex);

            JSONObject pageObject = new JSONObject();
            pageObject.put("page", pagination.page);
            pageObject.put("limit", pagination.limit);
            pageObject.put("total", browseItems.size());
            pageObject.put("pageCount", browseItems.isEmpty() ? 0 : (int) Math.ceil(browseItems.size() / (double) pagination.limit));
            result.put("pagination", pageObject);
        }

        JSONArray items = new JSONArray();
        for (BrowseItem item : pageItems) {
            enrichIndexedReadProgress(item);
            items.add(toJsonItem(item, API_VERSION_V2));
        }
        result.put("items", items);

        JSONObject filters = new JSONObject(new LinkedHashMap<>());
        if (labelFilter != null) {
            filters.put("label", labelFilter);
        }
        if (!tagFilters.isEmpty()) {
            filters.put("tags", toJsonArray(tagFilters));
        }
        if (searchFilter != null) {
            filters.put("search", searchFilter);
        }
        result.put("filters", filters);

        // Include all available labels (including "Default") so the client
        // can populate the label dropdown independently of the current filter.
        JSONArray availableLabels = new JSONArray();
        availableLabels.add(BrowseFilterUtils.DEFAULT_LABEL_NAME);
        List<DownloadLabel> labelList = manager.getLabelList();
        if (labelList != null) {
            for (DownloadLabel dl : labelList) {
                if (dl == null || dl.getLabel() == null) continue;
                String name = dl.getLabel().trim();
                if (!name.isEmpty() && !BrowseFilterUtils.DEFAULT_LABEL_NAME.equalsIgnoreCase(name)) {
                    availableLabels.add(name);
                }
            }
        }
        result.put("availableLabels", availableLabels);

        writeJson(os, 200, result);
    }

    @NonNull
    private BrowseItem buildIndexedItem(@NonNull DownloadInfo info, boolean includeThumbnails) {
        BrowseMetadata metadata = new BrowseMetadata();
        metadata.title = TextUtils.isEmpty(info.title) ? Long.toString(info.gid) : info.title;
        metadata.rating = info.rating < 0 ? null : info.rating;

        int inferredPages = info.pages > 0 ? info.pages : info.total;
        metadata.pageCount = inferredPages > 0 ? inferredPages : null;
        metadata.labels = TextUtils.isEmpty(info.label)
                ? Collections.emptyList()
                : Collections.singletonList(info.label);
        metadata.tags = extractIndexedTags(info);
        // Include category in tags so tag toggles can filter by category
        String categoryText = EhUtils.getCategory(info.category);
        metadata.category = TextUtils.isEmpty(categoryText) ? "-" : categoryText;
        if (!TextUtils.isEmpty(categoryText) && !metadata.tags.contains(categoryText)) {
            metadata.tags = new ArrayList<>(metadata.tags);
            metadata.tags.add(0, categoryText);
        }
        metadata.uploader = TextUtils.isEmpty(info.uploader) ? "-" : info.uploader;

        metadata.thumbnailUrl = includeThumbnails ? "/api/thumb?gid=" + info.gid : null;

        BrowseItem item = new BrowseItem();
        item.gid = info.gid;
        item.name = metadata.title;
        item.path = Long.toString(info.gid);
        item.directory = false;
        item.size = Math.max(0, info.fileSize);
        item.modified = info.time;
        item.mimeType = "application/octet-stream";
        item.image = false;
        item.url = "/api/reader?gid=" + info.gid;
        item.metadata = metadata;
        return item;
    }

    private void enrichIndexedReadProgress(@NonNull BrowseItem item) {
        if (item.gid <= 0 || item.metadata == null) {
            return;
        }

        DownloadManager manager = EhApplication.getDownloadManager(context);
        DownloadInfo info = manager.getDownloadInfo(item.gid);
        if (info == null) {
            return;
        }

        UniFile dir = SpiderDen.getExistingGalleryDownloadDir(info);
        if (dir == null || !dir.isDirectory()) {
            return;
        }

        UniFile spiderFile = dir.findFile(".ehviewer");
        if (spiderFile == null || !spiderFile.isFile()) {
            return;
        }

        SpiderInfo spiderInfo = SpiderInfo.read(spiderFile);
        if (spiderInfo == null || spiderInfo.pages <= 0) {
            return;
        }

        int total = spiderInfo.pages;
        int current = Math.max(0, Math.min(total, spiderInfo.startPage + 1));
        JSONObject progress = new JSONObject();
        progress.put("current", current);
        progress.put("total", total);
        item.metadata.readProgress = progress;
        // Only override pageCount from spiderInfo if the DB-reported pageCount is null/unknown
        if (item.metadata.pageCount == null || item.metadata.pageCount <= 0) {
            item.metadata.pageCount = total;
        }
    }

    private void serveThumbnail(@NonNull OutputStream os, @Nullable String gidText) throws IOException {
        long gid = parseGid(gidText);
        if (gid <= 0) {
            writeJson(os, 400, jsonError("invalid_request", "gid is required"));
            return;
        }

        DownloadManager manager = EhApplication.getDownloadManager(context);
        DownloadInfo info = manager.getDownloadInfo(gid);
        if (info == null) {
            writeJson(os, 404, jsonError("not_found", "item not found"));
            return;
        }

        UniFile dir = SpiderDen.getExistingGalleryDownloadDir(info);
        if (dir == null || !dir.isDirectory()) {
            writeJson(os, 404, jsonError("not_found", "download dir not found"));
            return;
        }

        UniFile thumb = dir.findFile(".thumb");
        if (thumb == null || !thumb.isFile()) {
            writeJson(os, 404, jsonError("not_found", "thumbnail not found"));
            return;
        }

        long length = Math.max(0, thumb.length());
        if (length <= 0 || length > MAX_THUMBNAIL_BYTES) {
            writeJson(os, 404, jsonError("not_found", "thumbnail unavailable"));
            return;
        }

        try (InputStream in = thumb.openInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream((int) length)) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                bos.write(buffer, 0, read);
            }
            byte[] bytes = bos.toByteArray();
            if (bytes.length == 0) {
                writeJson(os, 404, jsonError("not_found", "thumbnail empty"));
                return;
            }
            writeBytes(os, 200, detectImageMimeType(bytes), bytes);
        }
    }

    private void serveLabels(@NonNull OutputStream os) throws IOException {
        DownloadManager manager = EhApplication.getDownloadManager(context);
        List<DownloadLabel> labelList = manager.getLabelList();
        JSONArray labels = new JSONArray();
        // Always include "Default" as the first option for unlabeled items,
        // matching the in-app download page behavior.
        labels.add(BrowseFilterUtils.DEFAULT_LABEL_NAME);
        if (labelList != null) {
            for (DownloadLabel dl : labelList) {
                if (dl == null || dl.getLabel() == null) continue;
                String name = dl.getLabel().trim();
                if (!name.isEmpty() && !BrowseFilterUtils.DEFAULT_LABEL_NAME.equalsIgnoreCase(name)) {
                    labels.add(name);
                }
            }
        }
        JSONObject result = new JSONObject();
        result.put("labels", labels);
        writeJson(os, 200, result);
    }

    private void serveReader(@NonNull OutputStream os, @NonNull Map<String, String> query) throws IOException {
        long gid = parseGid(query.get("gid"));
        if (gid <= 0) {
            writeJson(os, 400, jsonError("invalid_request", "gid is required"));
            return;
        }

        DownloadManager manager = EhApplication.getDownloadManager(context);
        DownloadInfo info = manager.getDownloadInfo(gid);
        if (info == null) {
            writeJson(os, 404, jsonError("not_found", "item not found"));
            return;
        }

        UniFile dir = SpiderDen.getExistingGalleryDownloadDir(info);
        if (dir == null || !dir.isDirectory()) {
            writeJson(os, 404, jsonError("not_found", "download dir not found"));
            return;
        }

        UniFile[] files = dir.listFiles();
        if (files == null) {
            files = new UniFile[0];
        }

        ArrayList<UniFile> imageFiles = new ArrayList<>();
        for (UniFile file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name == null || name.startsWith(".")) {
                continue;
            }
            String mimeType = ContentTypeUtils.fromName(name);
            if (ContentTypeUtils.isImage(mimeType)) {
                imageFiles.add(file);
            }
        }

        imageFiles.sort((a, b) -> compareNaturalName(a.getName(), b.getName()));

        String dirName = dir.getName();
        if (TextUtils.isEmpty(dirName)) {
            writeJson(os, 404, jsonError("not_found", "download dir name missing"));
            return;
        }

        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("gid", gid);
        result.put("title", TextUtils.isEmpty(info.title) ? Long.toString(gid) : info.title);

        int startIndex = 0;
        UniFile spider = dir.findFile(".ehviewer");
        if (spider != null && spider.isFile()) {
            SpiderInfo spiderInfo = SpiderInfo.read(spider);
            if (spiderInfo != null) {
                startIndex = Math.max(0, spiderInfo.startPage);
            }
        }

        JSONArray pages = new JSONArray();
        for (int i = 0; i < imageFiles.size(); i++) {
            UniFile image = imageFiles.get(i);
            String name = image.getName();
            if (name == null) {
                continue;
            }
            String relativePath = dirName + "/" + name;
            JSONObject page = new JSONObject();
            page.put("index", i);
            page.put("name", name);
            page.put("url", "/files/" + PathValidator.encodePath(relativePath));
            pages.add(page);
        }
        result.put("pages", pages);
        result.put("startIndex", Math.min(startIndex, Math.max(0, pages.size() - 1)));
        writeJson(os, 200, result);
    }

    private long parseGid(@Nullable String gidText) {
        if (gidText == null || gidText.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(gidText.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int compareNaturalName(@Nullable String a, @Nullable String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;
        return left.compareTo(right);
    }

    @NonNull
    private List<String> extractIndexedTags(@NonNull DownloadInfo info) {
        if (info.simpleTags == null) return Collections.emptyList();
        ArrayList<String> tags = new ArrayList<>(info.simpleTags.length);
        for (String tag : info.simpleTags) {
            if (tag == null) continue;
            String t = tag.trim();
            if (!t.isEmpty()) tags.add(t);
        }
        return tags;
    }

    @NonNull
    private JSONObject toJsonItem(@NonNull BrowseItem item, int version) {
        JSONObject object = new JSONObject(new LinkedHashMap<>());
        object.put("name", item.name);
        object.put("path", item.path);
        object.put("directory", item.directory);
        object.put("size", item.size);
        object.put("modified", item.modified);
        object.put("mimeType", item.mimeType);
        object.put("image", item.image);
        object.put("url", item.url);
        if (version >= API_VERSION_V2) {
            BrowseMetadata metadata = item.metadata;
            object.put("title", metadata == null ? item.name : metadata.title);
            object.put("rating", metadata == null ? null : metadata.rating);
            object.put("pageCount", metadata == null ? null : metadata.pageCount);
            object.put("readProgress", metadata == null ? null : metadata.readProgress);
            object.put("labels", metadata == null ? new JSONArray() : toJsonArray(metadata.labels));
            object.put("tags", metadata == null ? new JSONArray() : toJsonArray(metadata.tags));
            object.put("uploader", metadata == null ? "-" : metadata.uploader);
            object.put("category", metadata == null ? "-" : metadata.category);
            object.put("thumbnailUrl", metadata == null ? null : metadata.thumbnailUrl);
        }
        return object;
    }

    @NonNull
    private JSONArray toJsonArray(@NonNull List<String> source) {
        JSONArray array = new JSONArray(source.size());
        array.addAll(source);
        return array;
    }

    private int parseVersion(@Nullable String rawVersion) {
        if (rawVersion == null || rawVersion.isEmpty()) {
            return 1;
        }
        try {
            int parsed = Integer.parseInt(rawVersion);
            return parsed == API_VERSION_V2 ? API_VERSION_V2 : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean parseBoolean(@Nullable String value) {
        if (value == null) {
            return false;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    @NonNull
    private Pagination parsePagination(@Nullable String rawPage, @Nullable String rawLimit) {
        if ((rawPage == null || rawPage.isEmpty()) && (rawLimit == null || rawLimit.isEmpty())) {
            return Pagination.disabled();
        }

        int page = DEFAULT_PAGE;
        int limit = DEFAULT_LIMIT;
        try {
            if (rawPage != null && !rawPage.isEmpty()) {
                page = Integer.parseInt(rawPage.trim());
            }
            if (rawLimit != null && !rawLimit.isEmpty()) {
                limit = Integer.parseInt(rawLimit.trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("page and limit must be integers");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        return Pagination.enabled(page, limit);
    }

    @NonNull
    private MetadataContext buildMetadataContext(@NonNull List<UniFile> rows) {
        DownloadManager manager = EhApplication.getDownloadManager(context);
        List<DownloadInfo> downloads = manager.getAllDownloadInfoList();
        Map<Long, DownloadInfo> byGid = new HashMap<>(downloads.size());
        Map<String, DownloadInfo> byDirname = new HashMap<>(downloads.size());
        Set<Long> candidateGids = new LinkedHashSet<>();
        for (DownloadInfo info : downloads) {
            byGid.put(info.gid, info);
            String dirname = EhDB.getDownloadDirname(info.gid);
            if (!TextUtils.isEmpty(dirname)) {
                byDirname.put(dirname, info);
            }
        }

        for (UniFile row : rows) {
            if (!row.isDirectory()) {
                continue;
            }
            String name = row.getName();
            if (name == null) {
                continue;
            }
            DownloadInfo info = byDirname.get(name);
            if (info == null) {
                info = byGid.get(parseLeadingGid(name));
            }
            if (info != null) {
                candidateGids.add(info.gid);
            }
        }

        Map<Long, List<String>> tagsByGid = new HashMap<>();
        for (Long gid : candidateGids) {
            tagsByGid.put(gid, BrowseFilterUtils.extractTags(EhDB.queryGalleryTags(gid)));
        }

        return new MetadataContext(byGid, byDirname, tagsByGid);
    }

    @Nullable
    private BrowseMetadata resolveMetadata(@NonNull UniFile child,
                                           @NonNull String name,
                                           @NonNull MetadataContext context,
                                           boolean includeThumbnails) {
        if (!child.isDirectory()) {
            return null;
        }

        DownloadInfo info = context.byDirname.get(name);
        if (info == null) {
            info = context.byGid.get(parseLeadingGid(name));
        }
        if (info == null) {
            return null;
        }

        BrowseMetadata metadata = new BrowseMetadata();
        metadata.title = TextUtils.isEmpty(info.title) ? name : info.title;
        metadata.rating = info.rating < 0 ? null : info.rating;
        metadata.pageCount = info.pages > 0 ? info.pages : null;
        metadata.labels = TextUtils.isEmpty(info.label)
                ? Collections.emptyList()
                : Collections.singletonList(info.label);
        metadata.tags = context.tagsByGid.getOrDefault(info.gid, Collections.emptyList());
        metadata.uploader = TextUtils.isEmpty(info.uploader) ? "-" : info.uploader;
        String categoryText = EhUtils.getCategory(info.category);
        metadata.category = TextUtils.isEmpty(categoryText) ? "-" : categoryText;
        metadata.readProgress = buildReadProgress(child, info.pages);
        if (metadata.pageCount == null && metadata.readProgress != null) {
            metadata.pageCount = metadata.readProgress.getInteger("total");
        }
        metadata.thumbnailUrl = includeThumbnails ? loadThumbnailAsDataUri(child) : null;
        return metadata;
    }

    @Nullable
    private JSONObject buildReadProgress(@NonNull UniFile directory, int fallbackPageCount) {
        UniFile spiderFile = directory.findFile(".ehviewer");
        if (spiderFile == null || !spiderFile.isFile()) {
            return null;
        }
        SpiderInfo spiderInfo = SpiderInfo.read(spiderFile);
        if (spiderInfo == null) {
            return null;
        }

        int total = spiderInfo.pages > 0 ? spiderInfo.pages : fallbackPageCount;
        if (total <= 0) {
            return null;
        }
        // SpiderInfo.startPage is zero-based; expose 1-based current page when available
        int current = Math.max(0, Math.min(total, spiderInfo.startPage + 1));
        JSONObject progress = new JSONObject();
        progress.put("current", current);
        progress.put("total", total);
        return progress;
    }

    @Nullable
    private String loadThumbnailAsDataUri(@NonNull UniFile directory) {
        UniFile thumb = directory.findFile(".thumb");
        if (thumb == null || !thumb.isFile()) {
            return null;
        }

        long length = Math.max(0, thumb.length());
        if (length == 0 || length > MAX_THUMBNAIL_BYTES) {
            return null;
        }

        try (InputStream is = thumb.openInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream((int) length)) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = is.read(buffer)) >= 0) {
                bos.write(buffer, 0, read);
            }
            byte[] bytes = bos.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            String mimeType = detectImageMimeType(bytes);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    @NonNull
    private String detectImageMimeType(@NonNull byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private long parseLeadingGid(@NonNull String dirname) {
        int dash = dirname.indexOf('-');
        String prefix = dash > 0 ? dirname.substring(0, dash) : dirname;
        if (prefix.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(prefix);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean matchesFilters(@Nullable BrowseMetadata metadata,
                                   @Nullable String labelFilter,
                                   @NonNull List<String> tagFilters,
                                   @Nullable String searchFilter) {
        boolean hasFilter = labelFilter != null || !tagFilters.isEmpty() || searchFilter != null;
        if (!hasFilter) {
            return true;
        }
        if (metadata == null) {
            return false;
        }

        if (labelFilter != null && !BrowseFilterUtils.matchesLabel(metadata.labels, labelFilter)) {
            return false;
        }
        if (!tagFilters.isEmpty() && !BrowseFilterUtils.matchesTags(metadata.tags, tagFilters)) {
            return false;
        }
        return searchFilter == null || matchesSearch(metadata, searchFilter);
    }

    private boolean matchesSearch(@NonNull BrowseMetadata metadata, @NonNull String searchFilter) {
        return BrowseFilterUtils.matchesSearch(metadata.title, metadata.labels, metadata.tags, searchFilter);
    }

    private void serveFile(@NonNull OutputStream os, @NonNull String encodedPath) throws IOException {
        UniFile target = PathValidator.resolveRelativePath(root, encodedPath);
        if (target == null || !target.exists() || !target.isFile()) {
            ServerLog.e("Rejected file path: " + encodedPath);
            writeJson(os, 403, jsonError("forbidden", "Invalid file path"));
            return;
        }

        String name = target.getName() == null ? "file" : target.getName();
        String type = ContentTypeUtils.fromName(name);
        long length = Math.max(0, target.length());

        writeStatusAndHeaders(os, 200, type, length);
        try (InputStream in = target.openInputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                os.write(buffer, 0, read);
            }
        }
    }

    private void writeJson(@NonNull OutputStream os, int code, @NonNull JSONObject object) throws IOException {
        writeBytes(os, code, "application/json; charset=utf-8",
                object.toJSONString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeText(@NonNull OutputStream os, int code, @NonNull String contentType,
            @NonNull String body) throws IOException {
        writeBytes(os, code, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(@NonNull OutputStream os, int code, @NonNull String contentType,
            @NonNull byte[] body) throws IOException {
        writeStatusAndHeaders(os, code, contentType, body.length);
        os.write(body);
    }

    private void writeStatusAndHeaders(@NonNull OutputStream os, int code,
            @NonNull String contentType, long contentLength) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
                .append("Content-Type: ").append(contentType).append("\r\n")
                .append("Content-Length: ").append(contentLength).append("\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");
        os.write(headers.toString().getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    private byte[] readAsset(@NonNull String path) throws IOException {
        try (InputStream is = context.getAssets().open(path);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = is.read(buffer)) >= 0) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }

    @NonNull
    private JSONObject jsonError(@NonNull String code, @NonNull String message) {
        JSONObject object = new JSONObject();
        object.put("error", code);
        object.put("message", message);
        return object;
    }

    @NonNull
    private String parentPath(@NonNull String path) {
        if (path.isEmpty()) {
            return "";
        }
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return "";
        }
        return path.substring(0, index);
    }

    @NonNull
    private String reason(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        };
    }

    private static final class HttpRequest {
        String method;
        String path;
        Map<String, String> query;
    }

    private static final class Pagination {
        final boolean enabled;
        final int page;
        final int limit;

        private Pagination(boolean enabled, int page, int limit) {
            this.enabled = enabled;
            this.page = page;
            this.limit = limit;
        }

        static Pagination disabled() {
            return new Pagination(false, DEFAULT_PAGE, DEFAULT_LIMIT);
        }

        static Pagination enabled(int page, int limit) {
            return new Pagination(true, page, limit);
        }
    }

    private static final class BrowseItem {
        long gid;
        String name;
        String path;
        boolean directory;
        long size;
        long modified;
        String mimeType;
        boolean image;
        String url;
        BrowseMetadata metadata;
    }

    private static final class BrowseMetadata {
        String title;
        Float rating;
        Integer pageCount;
        JSONObject readProgress;
        List<String> labels = Collections.emptyList();
        List<String> tags = Collections.emptyList();
        String uploader = "-";
        String category = "-";
        String thumbnailUrl;
    }

    private static final class MetadataContext {
        final Map<Long, DownloadInfo> byGid;
        final Map<String, DownloadInfo> byDirname;
        final Map<Long, List<String>> tagsByGid;

        MetadataContext(@NonNull Map<Long, DownloadInfo> byGid,
                        @NonNull Map<String, DownloadInfo> byDirname,
                        @NonNull Map<Long, List<String>> tagsByGid) {
            this.byGid = byGid;
            this.byDirname = byDirname;
            this.tagsByGid = tagsByGid;
        }

        static MetadataContext empty() {
            return new MetadataContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }
}

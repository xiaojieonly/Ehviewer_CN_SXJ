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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.dao.DaoSession;
import com.hippo.ehviewer.dao.DownloadedFile;
import com.hippo.ehviewer.dao.DownloadedFilesDao;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.MathUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadedFileManager {

    private static final String TAG = DownloadedFileManager.class.getSimpleName();
    private static DownloadedFileManager sInstance;

    private final Context mContext;
    private final DownloadedFilesDao mDownloadedFilesDao;

    public static final int SCAN_STATUS_IDLE = 0;
    public static final int SCAN_STATUS_SCANNING = 1;
    public static final int SCAN_STATUS_COMPLETED = 2;
    public static final int SCAN_STATUS_ERROR = 3;

    private volatile int mScanStatus = SCAN_STATUS_IDLE;
    private final AtomicInteger mScanProgress = new AtomicInteger(0);
    private final AtomicInteger mScanTotal = new AtomicInteger(0);
    private String mScanError;

    public static void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new DownloadedFileManager(context.getApplicationContext());
        }
    }

    public static DownloadedFileManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("DownloadedFileManager not initialized");
        }
        return sInstance;
    }

    private DownloadedFileManager(Context context) {
        mContext = context;
        DaoSession daoSession = EhDB.getDaoSession();
        mDownloadedFilesDao = daoSession.getDownloadedFilesDao();
        ensureFileTokenColumn();
    }

    private void ensureFileTokenColumn() {
        try {
            boolean hasFileToken = false;
            Cursor cursor = mDownloadedFilesDao.getDatabase().rawQuery("PRAGMA table_info('DOWNLOADED_FILES')", null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int columnIndex = cursor.getColumnIndex("name");
                    if (columnIndex == -1) {
                        continue;
                    }
                    String columnName = cursor.getString(columnIndex);
                    if ("FILE_TOKEN".equalsIgnoreCase(columnName)) {
                        hasFileToken = true;
                        break;
                    }
                }
                cursor.close();
            }
            if (!hasFileToken) {
                mDownloadedFilesDao.getDatabase().execSQL("ALTER TABLE DOWNLOADED_FILES ADD COLUMN FILE_TOKEN TEXT;");
                Log.i(TAG, "Added missing FILE_TOKEN column to DOWNLOADED_FILES");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to ensure FILE_TOKEN column", e);
        }
    }

    @Nullable
    private File toFile(@Nullable UniFile uniFile) {
        if (uniFile == null || uniFile.getUri() == null) {
            return null;
        }
        String path = uniFile.getUri().getPath();
        return path != null ? new File(path) : null;
    }

    @Nullable
    private List<File> listGalleryDirectoriesShell(@NonNull File downloadDir) {
        String command = "find " + escapeShellArg(downloadDir.getAbsolutePath()) + " -maxdepth 1 -mindepth 1 -type d 2>/dev/null";
        String output = executeShellCommand("/system/bin/sh", "-c", command);
        if (output == null) {
            return null;
        }
        List<File> dirs = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            dirs.add(new File(line));
        }
        return dirs;
    }

    @Nullable
    private List<String> listFilesShell(@NonNull File directory) {
        String command = "find " + escapeShellArg(directory.getAbsolutePath()) + " -maxdepth 1 -mindepth 1 -type f 2>/dev/null";
        String output = executeShellCommand("/system/bin/sh", "-c", command);
        if (output == null) {
            return null;
        }
        List<String> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (!line.isEmpty()) {
                files.add(line);
            }
        }
        return files;
    }

    @Nullable
    private String readFileShell(@NonNull File file) {
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        String command = "cat " + escapeShellArg(file.getAbsolutePath()) + " 2>/dev/null";
        return executeShellCommand("/system/bin/sh", "-c", command);
    }

    private String escapeShellArg(@NonNull String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    @Nullable
    private String executeShellCommand(@NonNull String... command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            String output = IOUtils.readString(process.getInputStream(), StandardCharsets.UTF_8.name());
            String error = IOUtils.readString(process.getErrorStream(), StandardCharsets.UTF_8.name());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.w(TAG, "Shell command failed: " + Arrays.toString(command) + " exit=" + exitCode + " err=" + error);
                return null;
            }
            return output;
        } catch (Exception e) {
            Log.w(TAG, "Failed to execute shell command: " + Arrays.toString(command), e);
            return null;
        }
    }

    /**
     * 检查文件是否已存在
     *
     * @param token 文件token（短码）
     * @return 文件信息，如果不存在则返回null
     */
    @Nullable
    public DownloadedFile getFileByToken(String token) {
        if (TextUtils.isEmpty(token)) {
            Log.d(TAG, "getFileByToken called with empty token");
            return null;
        }

        Log.d(TAG, "getFileByToken called for token: " + token);

        try {
            // 优先查找正常状态记录
            DownloadedFile file = mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.Token.eq(token))
                    .where(DownloadedFilesDao.Properties.Status.eq(DownloadedFile.STATUS_NORMAL))
                    .unique();

            if (file != null) {
                Log.d(TAG, "Found normal status file for token: " + token + ", filename: " + file.getFilename());
                return file;
            }

            // 未找到，尝试使用 fileToken 进行查找
            file = getFileByFileToken(token);
            if (file != null) {
                Log.d(TAG, "Found normal status file by fileToken: " + token + ", filename: " + file.getFilename());
                return file;
            }

            // SQL 追踪打印，便于排查
            try {
                Cursor cursor = mDownloadedFilesDao.getDatabase().rawQuery(
                        "SELECT TOKEN, FILE_TOKEN, GID, FILENAME, PATH, STATUS FROM DOWNLOADED_FILES WHERE TOKEN = ?",
                        new String[]{token});
                if (cursor != null) {
                    Log.d(TAG, "Database query for token " + token + " returned " + cursor.getCount() + " rows");
                    while (cursor.moveToNext()) {
                        Log.d(TAG, String.format("ROW: token=%s, fileToken=%s, gid=%d, filename=%s, path=%s, status=%d",
                                cursor.getString(0), cursor.isNull(1) ? "null" : cursor.getString(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5)));
                    }
                    cursor.close();
                }
            } catch (Exception sqlExp) {
                Log.e(TAG, "Failed to execute debug SQL for token: " + token, sqlExp);
            }

            Log.d(TAG, "No file found for token: " + token);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error querying file by token: " + token, e);
            return null;
        }
    }

    @Nullable
    public DownloadedFile getFileByFileToken(String fileToken) {
        if (TextUtils.isEmpty(fileToken)) {
            return null;
        }

        try {
            DownloadedFile file = mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.FileToken.eq(fileToken))
                    .where(DownloadedFilesDao.Properties.Status.eq(DownloadedFile.STATUS_NORMAL))
                    .unique();
            if (file != null) {
                return file;
            }

            List<DownloadedFile> candidates = mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.FileToken.eq(fileToken))
                    .list();
            if (candidates != null && !candidates.isEmpty()) {
                for (DownloadedFile candidate : candidates) {
                    File candidateFile = new File(candidate.getPath());
                    if (candidateFile.exists()) {
                        if (candidate.getStatus() != DownloadedFile.STATUS_NORMAL) {
                            candidate.setStatus(DownloadedFile.STATUS_NORMAL);
                            candidate.setLast_accessed(System.currentTimeMillis());
                            mDownloadedFilesDao.update(candidate);
                        }
                        return candidate;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error querying file by fileToken: " + fileToken, e);
            return null;
        }
    }

    /**
     * 添加或更新文件信息
     *
     * @param token    文件token
     * @param gid      画廊GID
     * @param filename 文件名
     * @param path     文件路径
     * @param size     文件大小
     * @return 是否成功
     */
    public boolean addOrUpdateFile(String token, long gid, String filename, String path, long size) {
        return addOrUpdateFile(token, token, gid, filename, path, size);
    }

    public boolean addOrUpdateFile(String token, String fileToken, long gid, String filename, String path, long size) {
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(filename) || TextUtils.isEmpty(path)) {
            Log.w(TAG, "Invalid parameters for addOrUpdateFile - token: " + token + ", filename: " + filename + ", path: " + path);
            return false;
        }

        Log.d(TAG, "addOrUpdateFile called - token: " + token + ", fileToken: " + fileToken + ", gid: " + gid + ", filename: " + filename + ", size: " + size);

        try {
            DownloadedFile file = getFileByToken(token);
            if (file == null && !TextUtils.isEmpty(fileToken) && !TextUtils.equals(token, fileToken)) {
                file = getFileByFileToken(fileToken);
            }
            if (file == null) {
                Log.d(TAG, "Creating new file record for token: " + token + ", fileToken: " + fileToken);
                // 新建文件记录
                file = new DownloadedFile();
                file.setToken(token);
                file.setFileToken(fileToken);
                file.setGid(gid);
                file.setFilename(filename);
                file.setPath(path);
                file.setSize(size);
                file.setDownload_time(System.currentTimeMillis());
                file.setLast_accessed(System.currentTimeMillis());
                file.setStatus(DownloadedFile.STATUS_NORMAL);

                // 计算MD5
                Log.d(TAG, "Calculating MD5 for file: " + path);
                String md5 = calculateMD5(path);
                if (md5 != null) {
                    file.setMd5(md5);
                    Log.d(TAG, "MD5 calculated: " + md5);
                } else {
                    Log.w(TAG, "Failed to calculate MD5 for file: " + path);
                }

                Log.d(TAG, "Inserting new file record into database");
                mDownloadedFilesDao.insert(file);
                Log.d(TAG, "Successfully inserted file record for token: " + token + ", fileToken: " + fileToken);
            } else {
                Log.d(TAG, "Updating existing file record for token: " + token + ", fileToken: " + fileToken);
                // 更新现有记录
                if (!TextUtils.isEmpty(fileToken) && !TextUtils.equals(fileToken, file.getFileToken())) {
                    file.setFileToken(fileToken);
                }
                file.setLast_accessed(System.currentTimeMillis());
                if (size > 0) {
                    file.setSize(size);
                }
                mDownloadedFilesDao.update(file);
                Log.d(TAG, "Successfully updated file record for token: " + token + ", fileToken: " + fileToken);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error adding/updating file: " + filename, e);
            // 记录数据库相关信息
            try {
                Log.e(TAG, "Database error details", e);
            } catch (Exception dbEx) {
                Log.e(TAG, "Failed to get database info", dbEx);
            }
            return false;
        }
    }

    /**
     * 标记文件为已删除
     *
     * @param token 文件token
     */
    public void markFileDeleted(String token) {
        if (TextUtils.isEmpty(token)) {
            return;
        }

        try {
            DownloadedFile file = getFileByToken(token);
            if (file != null) {
                file.setStatus(DownloadedFile.STATUS_DELETED);
                mDownloadedFilesDao.update(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking file as deleted: " + token, e);
        }
    }

    /**
     * 标记画廊的所有文件为已移除
     *
     * @param gid 画廊GID
     */
    public void markGalleryRemoved(long gid) {
        try {
            List<DownloadedFile> files = mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.Gid.eq(gid))
                    .where(DownloadedFilesDao.Properties.Status.eq(DownloadedFile.STATUS_NORMAL))
                    .list();

            for (DownloadedFile file : files) {
                file.setStatus(DownloadedFile.STATUS_GALLERY_REMOVED);
                mDownloadedFilesDao.update(file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking gallery as removed: " + gid, e);
        }
    }

    /**
     * 获取画廊的所有文件
     *
     * @param gid 画廊GID
     * @return 文件列表
     */
    public List<DownloadedFile> getGalleryFiles(long gid) {
        try {
            return mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.Gid.eq(gid))
                    .orderAsc(DownloadedFilesDao.Properties.Filename)
                    .list();
        } catch (Exception e) {
            Log.e(TAG, "Error getting gallery files: " + gid, e);
            return new ArrayList<>();
        }
    }

    /**
     * 批量检查画廊文件是否存在
     *
     * @param gid 画廊GID
     * @return 文件存在性检查结果
     */
    public GalleryFileCheckResult checkGalleryFilesExist(long gid) {
        Log.d(TAG, "Checking gallery files existence for GID: " + gid);

        GalleryFileCheckResult result = new GalleryFileCheckResult(gid);
        List<DownloadedFile> files = getGalleryFiles(gid);

        result.totalFiles = files.size();

        for (DownloadedFile file : files) {
            String filePath = file.getPath();
            java.io.File physicalFile = new java.io.File(filePath);

            if (physicalFile.exists() && physicalFile.canRead()) {
                result.existingFiles++;
                result.existingFileList.add(file);

                // 检查文件大小
                long expectedSize = file.getSize() != null ? file.getSize() : 0;
                long actualSize = physicalFile.length();

                if (expectedSize > 0 && actualSize == expectedSize) {
                    result.validFiles++;
                } else {
                    result.invalidFiles++;
                    result.invalidFileList.add(file);
                    Log.w(TAG, "File size mismatch: " + file.getFilename() +
                            " - expected: " + expectedSize + ", actual: " + actualSize);
                }
            } else {
                result.missingFiles++;
                result.missingFileList.add(file);
                Log.w(TAG, "Missing file: " + file.getFilename() + " at " + filePath);
            }
        }

        Log.d(TAG, "Gallery file check completed for GID " + gid +
                " - Total: " + result.totalFiles +
                ", Existing: " + result.existingFiles +
                ", Valid: " + result.validFiles +
                ", Missing: " + result.missingFiles +
                ", Invalid: " + result.invalidFiles);

        return result;
    }

    /**
     * 画廊文件检查结果
     */
    public static class GalleryFileCheckResult {
        public final long gid;
        public int totalFiles = 0;
        public int existingFiles = 0;
        public int validFiles = 0;
        public int missingFiles = 0;
        public int invalidFiles = 0;

        public final List<DownloadedFile> existingFileList = new ArrayList<>();
        public final List<DownloadedFile> missingFileList = new ArrayList<>();
        public final List<DownloadedFile> invalidFileList = new ArrayList<>();

        public GalleryFileCheckResult(long gid) {
            this.gid = gid;
        }

        public boolean isComplete() {
            return totalFiles > 0 && validFiles == totalFiles;
        }

        public boolean hasMissingFiles() {
            return missingFiles > 0;
        }

        public boolean hasInvalidFiles() {
            return invalidFiles > 0;
        }

        public double getCompletionRate() {
            return totalFiles > 0 ? (double) validFiles / totalFiles : 0.0;
        }
    }

    /**
     * 获取总文件数量
     */
    public int getTotalFilesCount() {
        try {
            return (int) mDownloadedFilesDao.queryBuilder()
                    .where(DownloadedFilesDao.Properties.Status.eq(DownloadedFile.STATUS_NORMAL))
                    .count();
        } catch (Exception e) {
            Log.e(TAG, "Error getting total files count", e);
            return 0;
        }
    }

    /**
     * 清理无效文件记录
     *
     * @return 清理的文件数量
     */
    public int cleanupInvalidFiles() {
        Log.d(TAG, "开始清理无效文件记录");

        List<DownloadedFile> allFiles = mDownloadedFilesDao.queryBuilder()
                .where(DownloadedFilesDao.Properties.Status.eq(DownloadedFile.STATUS_NORMAL))
                .list();

        int cleanedCount = 0;

        for (DownloadedFile file : allFiles) {
            java.io.File physicalFile = new java.io.File(file.getPath());

            if (!physicalFile.exists() || !physicalFile.canRead()) {
                Log.w(TAG, "发现无效文件记录: " + file.getFilename() + " (路径: " + file.getPath() + ")");

                // 标记为已删除而不是直接删除，保留历史记录
                file.setStatus(DownloadedFile.STATUS_DELETED);
                mDownloadedFilesDao.update(file);
                cleanedCount++;
            }
        }

        Log.i(TAG, "清理完成，删除了 " + cleanedCount + " 个无效文件记录");
        return cleanedCount;
    }

    /**
     * 计算文件MD5
     *
     * @param filePath 文件路径
     * @return MD5字符串，失败返回null
     */
    @Nullable
    private String calculateMD5(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filePath);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }

                byte[] md5Bytes = digest.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : md5Bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } finally {
                IOUtils.closeQuietly(fis);
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Error calculating MD5 for: " + filePath, e);
            return null;
        }
    }

    /**
     * 扫描下载目录重建文件信息表
     *
     * @param progressListener 进度监听器
     */
    public void scanDownloadDirectories(@Nullable DownloadedFileManagerScanListener progressListener) {
        if (mScanStatus != SCAN_STATUS_IDLE) {
            Log.w(TAG, "Scan already in progress");
            return;
        }

        new Thread(() -> {
            mScanStatus = SCAN_STATUS_SCANNING;
            mScanProgress.set(0);
            mScanTotal.set(0);
            mScanError = null;

            try {
                // 检查数据库表是否存在
                Log.i(TAG, "Starting scan, checking database connection");
                try {
                    // 尝试查询表是否存在
                    long count = mDownloadedFilesDao.count();
                    Log.i(TAG, "DownloadedFiles table exists, current record count: " + count);
                } catch (Exception e) {
                    Log.e(TAG, "Error checking DownloadedFiles table, table may not exist: " + e.getMessage(), e);
                    // 尝试重新创建表
                    try {
                        Log.i(TAG, "Attempting to create DOWNLOADED_FILES table");
                        mDownloadedFilesDao.getDatabase().execSQL("CREATE TABLE IF NOT EXISTS \"DOWNLOADED_FILES\" (" +
                                "\"TOKEN\" TEXT PRIMARY KEY NOT NULL ," +
                                "\"FILE_TOKEN\" TEXT," +
                                "\"GID\" INTEGER NOT NULL ," +
                                "\"FILENAME\" TEXT NOT NULL ," +
                                "\"MD5\" TEXT," +
                                "\"PATH\" TEXT NOT NULL ," +
                                "\"SIZE\" INTEGER," +
                                "\"DOWNLOAD_TIME\" INTEGER NOT NULL ," +
                                "\"LAST_ACCESSED\" INTEGER," +
                                "\"STATUS\" INTEGER NOT NULL );");
                        Log.i(TAG, "DOWNLOADED_FILES table created or already exists");
                    } catch (Exception e2) {
                        Log.e(TAG, "Failed to create DOWNLOADED_FILES table: " + e2.getMessage(), e2);
                        throw new RuntimeException("Failed to create DOWNLOADED_FILES table", e2);
                    }
                }

                // 清空现有表
                Log.i(TAG, "Clearing existing records from DOWNLOADED_FILES table");
                try {
                    mDownloadedFilesDao.deleteAll();
                    Log.i(TAG, "Successfully cleared DOWNLOADED_FILES table");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear DOWNLOADED_FILES table: " + e.getMessage(), e);
                    // 如果是表不存在错误，尝试创建表
                    if (e.getMessage() != null && e.getMessage().contains("no such table")) {
                        Log.i(TAG, "Table does not exist, attempting to create it");
                        mDownloadedFilesDao.getDatabase().execSQL("CREATE TABLE IF NOT EXISTS \"DOWNLOADED_FILES\" (" +
                                "\"TOKEN\" TEXT PRIMARY KEY NOT NULL ," +
                                "\"FILE_TOKEN\" TEXT," +
                                "\"GID\" INTEGER NOT NULL ," +
                                "\"FILENAME\" TEXT NOT NULL ," +
                                "\"MD5\" TEXT," +
                                "\"PATH\" TEXT NOT NULL ," +
                                "\"SIZE\" INTEGER," +
                                "\"DOWNLOAD_TIME\" INTEGER NOT NULL ," +
                                "\"LAST_ACCESSED\" INTEGER," +
                                "\"STATUS\" INTEGER NOT NULL );");
                        Log.i(TAG, "DOWNLOADED_FILES table created");
                    } else {
                        throw e;
                    }
                }

                // 获取下载目录
                Log.i(TAG, "Getting download location from settings");
                UniFile downloadDir = Settings.getDownloadLocation();
                if (downloadDir == null) {
                    Log.e(TAG, "Download location is not set in settings");
                    throw new RuntimeException("Download location not set");
                }
                Log.i(TAG, "Download location: " + downloadDir.getUri());

                // 扫描所有画廊目录
                List<UniFile> galleryDirs = new ArrayList<>();
                File downloadDirFile = toFile(downloadDir);
                boolean shellUsed = false;
                if (downloadDirFile != null) {
                    List<File> shellDirs = listGalleryDirectoriesShell(downloadDirFile);
                    if (shellDirs != null) {
                        shellUsed = true;
                        for (File shellDir : shellDirs) {
                            if (shellDir != null) {
                                UniFile uniDir = UniFile.fromFile(shellDir);
                                if (uniDir != null && uniDir.isDirectory()) {
                                    galleryDirs.add(uniDir);
                                }
                            }
                        }
                    }
                }
                if (!shellUsed) {
                    UniFile[] allEntries = downloadDir.listFiles();
                    if (allEntries != null) {
                        for (UniFile entry : allEntries) {
                            if (entry != null && entry.isDirectory()) {
                                galleryDirs.add(entry);
                            }
                        }
                    }
                }

                int totalDirs = galleryDirs.size();
                mScanTotal.set(totalDirs);
                Log.i(TAG, "Found " + totalDirs + " gallery directories to scan (shellUsed=" + shellUsed + ")");

                if (totalDirs == 0) {
                    Log.w(TAG, "No gallery directories found, nothing to scan");
                }

                final int batchSize = 50;
                int processed = 0;
                int batchCount = (totalDirs + batchSize - 1) / batchSize;
                for (int batch = 0; batch < batchCount; batch++) {
                    int startIndex = batch * batchSize;
                    int endIndex = Math.min(totalDirs, startIndex + batchSize);
                    Log.i(TAG, "Scanning batch " + (batch + 1) + " / " + batchCount + " (directories " + (startIndex + 1) + " to " + endIndex + ")");

                    for (int i = startIndex; i < endIndex; i++) {
                        UniFile galleryDir = galleryDirs.get(i);
                        String dirName = galleryDir.getName();
                        Log.d(TAG, "Scanning gallery directory [" + (i + 1) + "/" + totalDirs + "]: " + dirName);

                        try {
                            scanGalleryDirectory(galleryDir);
                            Log.d(TAG, "Successfully scanned gallery directory: " + dirName);
                        } catch (Exception e) {
                            Log.e(TAG, "Error scanning gallery directory: " + dirName, e);
                        }

                        processed++;
                        mScanProgress.set(processed);
                        if (progressListener != null) {
                            progressListener.onProgress(processed, totalDirs);
                        }
                    }
                }

                mScanStatus = SCAN_STATUS_COMPLETED;

                if (progressListener != null) {
                    progressListener.onCompleted();
                }
            } catch (Exception e) {
                mScanStatus = SCAN_STATUS_ERROR;
                mScanError = e.getMessage();
                Log.e(TAG, "Error during scan", e);

                if (progressListener != null) {
                    progressListener.onError(e);
                }
            }
        }).start();
    }

    /**
     * 扫描单个画廊目录
     */
    private void scanGalleryDirectory(UniFile galleryDir) throws Exception {
        Log.d(TAG, "Scanning gallery directory: " + galleryDir.getName());

        // 检查.ehviewer文件
        UniFile ehviewerFile = galleryDir.findFile(".ehviewer");
        if (ehviewerFile == null) {
            Log.d(TAG, "No .ehviewer file found in directory: " + galleryDir.getName());
            return;
        }
        Log.d(TAG, "Found .ehviewer file: " + ehviewerFile.getUri());

        // 读取SpiderInfo
        SpiderInfo spiderInfo;
        File galleryDirFile = toFile(galleryDir);
        if (galleryDirFile != null) {
            String ehviewerContent = readFileShell(new File(galleryDirFile, ".ehviewer"));
            if (ehviewerContent != null) {
                Log.d(TAG, "Read .ehviewer file from shell for directory: " + galleryDir.getName());
                spiderInfo = SpiderInfo.read(new ByteArrayInputStream(ehviewerContent.getBytes(StandardCharsets.UTF_8)));
            } else {
                spiderInfo = SpiderInfo.read(ehviewerFile);
            }
        } else {
            spiderInfo = SpiderInfo.read(ehviewerFile);
        }
        if (spiderInfo == null) {
            Log.w(TAG, "Failed to read SpiderInfo from .ehviewer file: " + ehviewerFile.getUri());
            return;
        }
        Log.d(TAG, "Read SpiderInfo for GID: " + spiderInfo.gid + ", token: " + spiderInfo.token);

        // 扫描图片文件
        List<String> shellFiles = null;
        if (galleryDirFile != null) {
            shellFiles = listFilesShell(galleryDirFile);
        }

        int imageCount = 0;
        if (shellFiles != null) {
            Log.d(TAG, "Found " + shellFiles.size() + " files in directory via shell");
            for (String filePath : shellFiles) {
                if (TextUtils.isEmpty(filePath)) {
                    continue;
                }
                File fileObj = new File(filePath);
                if (!fileObj.exists() || fileObj.isDirectory()) {
                    Log.d(TAG, "Skipping non-file from shell listing: " + filePath);
                    continue;
                }
                String filename = fileObj.getName();
                if (filename == null) {
                    Log.d(TAG, "Skipping file with null name from shell listing");
                    continue;
                }

                if (filename.startsWith(".")) {
                    Log.d(TAG, "Skipping hidden file: " + filename);
                    continue;
                }

                Log.d(TAG, "Processing file: " + filename);

                // 检查是否是图片文件
                String extension = "";
                int dotIndex = filename.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = filename.substring(dotIndex + 1);
                }

                if (TextUtils.isEmpty(extension)) {
                    Log.d(TAG, "Skipping file without extension: " + filename);
                    continue;
                }

                if (!isImageExtension(extension)) {
                    Log.d(TAG, "Skipping non-image file (" + extension + "): " + filename);
                    continue;
                }

                Log.d(TAG, "File is image with extension: " + extension);

                // 调试：打印文件名字符
                Log.d(TAG, "Filename characters: " + Arrays.toString(filename.toCharArray()));

                // 从文件名提取token（假设格式为 index-token.ext 或 index.ext）
                String rawToken = extractTokenFromFilename(filename);
                if (rawToken == null) {
                    Log.w(TAG, "Could not extract token from filename: " + filename);
                    continue;
                }

                String fileToken = null;
                String dbToken;
                if (rawToken.matches("\\d+")) {
                    // 纯数字文件名，尝试从 .ehviewer 的 pTokenMap 恢复真实 fileToken
                    int index = Integer.parseInt(rawToken);
                    if (spiderInfo.pTokenMap != null) {
                        String resolvedToken = spiderInfo.pTokenMap.get(index);
                        if (resolvedToken != null && !SpiderInfo.TOKEN_FAILED.equals(resolvedToken)) {
                            fileToken = resolvedToken;
                            dbToken = resolvedToken;
                            Log.d(TAG, "Resolved fileToken from .ehviewer for index " + index + ": " + resolvedToken);
                        } else {
                            dbToken = spiderInfo.gid + "_" + rawToken;
                            Log.d(TAG, "No fileToken in .ehviewer for index " + index + ", using fallback composite token: " + dbToken);
                        }
                    } else {
                        dbToken = spiderInfo.gid + "_" + rawToken;
                        Log.d(TAG, "pTokenMap missing, using fallback composite token: " + dbToken);
                    }
                } else {
                    dbToken = rawToken;
                    fileToken = rawToken;
                }

                Log.d(TAG, "Final dbToken: " + dbToken + ", fileToken: " + fileToken + " from filename: " + filename + ", raw token: " + rawToken);

                // 添加文件信息
                String path = fileObj.getAbsolutePath();
                long size = fileObj.length();

                try {
                    addOrUpdateFile(dbToken, fileToken, spiderInfo.gid, filename, path, size);
                    Log.d(TAG, "Successfully added file to database: " + filename);
                    imageCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to add file to database: " + filename, e);
                }
            }
        } else {
            UniFile[] imageFiles = galleryDir.listFiles();
            if (imageFiles == null) {
                Log.d(TAG, "No files found in directory: " + galleryDir.getName());
                return;
            }
            Log.d(TAG, "Found " + imageFiles.length + " files in directory via UniFile");
            for (UniFile file : imageFiles) {
                if (file.isDirectory()) {
                    Log.d(TAG, "Skipping directory: " + file.getName());
                    continue;
                }

                String filename = file.getName();
                if (filename == null) {
                    Log.d(TAG, "Skipping file with null name");
                    continue;
                }

                if (filename.startsWith(".")) {
                    Log.d(TAG, "Skipping hidden file: " + filename);
                    continue;
                }

                Log.d(TAG, "Processing file: " + filename);

                // 检查是否是图片文件
                String extension = "";
                int dotIndex = filename.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = filename.substring(dotIndex + 1);
                }

                if (TextUtils.isEmpty(extension)) {
                    Log.d(TAG, "Skipping file without extension: " + filename);
                    continue;
                }

                if (!isImageExtension(extension)) {
                    Log.d(TAG, "Skipping non-image file (" + extension + "): " + filename);
                    continue;
                }

                Log.d(TAG, "File is image with extension: " + extension);

                // 调试：打印文件名字符
                Log.d(TAG, "Filename characters: " + Arrays.toString(filename.toCharArray()));

                // 从文件名提取token（假设格式为 index-token.ext 或 index.ext）
                String rawToken = extractTokenFromFilename(filename);
                if (rawToken == null) {
                    Log.w(TAG, "Could not extract token from filename: " + filename);
                    continue;
                }

                String fileToken = null;
                String dbToken;
                if (rawToken.matches("\\d+")) {
                    // 纯数字文件名，尝试从 .ehviewer 的 pTokenMap 恢复真实 fileToken
                    int index = Integer.parseInt(rawToken);
                    if (spiderInfo.pTokenMap != null) {
                        String resolvedToken = spiderInfo.pTokenMap.get(index);
                        if (resolvedToken != null && !SpiderInfo.TOKEN_FAILED.equals(resolvedToken)) {
                            fileToken = resolvedToken;
                            dbToken = resolvedToken;
                            Log.d(TAG, "Resolved fileToken from .ehviewer for index " + index + ": " + resolvedToken);
                        } else {
                            dbToken = spiderInfo.gid + "_" + rawToken;
                            Log.d(TAG, "No fileToken in .ehviewer for index " + index + ", using fallback composite token: " + dbToken);
                        }
                    } else {
                        dbToken = spiderInfo.gid + "_" + rawToken;
                        Log.d(TAG, "pTokenMap missing, using fallback composite token: " + dbToken);
                    }
                } else {
                    dbToken = rawToken;
                    fileToken = rawToken;
                }

                Log.d(TAG, "Final dbToken: " + dbToken + ", fileToken: " + fileToken + " from filename: " + filename + ", raw token: " + rawToken);

                // 添加文件信息
                String path = file.getUri().getPath();
                long size = file.length();

                try {
                    addOrUpdateFile(dbToken, fileToken, spiderInfo.gid, filename, path, size);
                    Log.d(TAG, "Successfully added file to database: " + filename);
                    imageCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to add file to database: " + filename, e);
                }
            }
        }
        Log.i(TAG, "Scanned " + imageCount + " image files in gallery directory: " + galleryDir.getName());
    }

        /**
         * 检查是否是图片扩展名
         */
        private boolean isImageExtension (String extension){
            return "jpg".equalsIgnoreCase(extension) ||
                    "jpeg".equalsIgnoreCase(extension) ||
                    "png".equalsIgnoreCase(extension) ||
                    "gif".equalsIgnoreCase(extension) ||
                    "webp".equalsIgnoreCase(extension) ||
                    "bmp".equalsIgnoreCase(extension);
        }

        /**
         * 从文件名提取token
         * 支持两种文件名格式:
         * 1. index-token.ext (例如: 0001-abc123.jpg)
         * 2. index.ext (例如: 00000008.jpg)
         */
        @Nullable
        private String extractTokenFromFilename (String filename){
            if (TextUtils.isEmpty(filename)) {
                Log.d(TAG, "extractTokenFromFilename: filename is null or empty");
                return null;
            }

            String trimmedFilename = filename.trim();
            Log.d(TAG, "extractTokenFromFilename called with: '" + filename + "' (trimmed: '" + trimmedFilename + "'), length: " + filename.length() + ", trimmed length: " + trimmedFilename.length());

            // 使用修剪后的文件名
            // 查找最后一个点号（支持半角点号 '.' 和全角点号 '．'）
            int lastDot = trimmedFilename.lastIndexOf('.');
            if (lastDot < 0) {
                // 尝试全角点号
                lastDot = trimmedFilename.lastIndexOf('．');
            }
            Log.d(TAG, "lastDot position: " + lastDot);

            if (lastDot <= 0) {
                Log.d(TAG, "No valid dot found in filename, returning null");
                return null;
            }

            int lastDash = trimmedFilename.lastIndexOf('-', lastDot);
            Log.d(TAG, "lastDash position: " + lastDash);

            if (lastDash > 0) {
                // 格式1: index-token.ext
                String token = trimmedFilename.substring(lastDash + 1, lastDot);
                Log.d(TAG, "Format 1 (index-token.ext) extracted token: " + token);
                return token;
            } else {
                // 格式2: index.ext - 使用不带扩展名的文件名作为token
                String token = trimmedFilename.substring(0, lastDot);
                Log.d(TAG, "Format 2 (index.ext) extracted token: " + token);
                return token;
            }
        }

        /**
         * 获取扫描状态
         */
        public int getScanStatus () {
            return mScanStatus;
        }

        /**
         * 获取扫描进度
         */
        public int getScanProgress () {
            return mScanProgress.get();
        }

        /**
         * 获取扫描总数
         */
        public int getScanTotal () {
            return mScanTotal.get();
        }

        /**
         * 获取扫描错误信息
         */
        public String getScanError() {
            return mScanError;
        }
    }
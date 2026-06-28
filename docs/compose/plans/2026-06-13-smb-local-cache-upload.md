# SMB Local Cache Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When download path is SMB, download to local temp first, then batch upload to SMB with DRAM aggregation, reducing SMB overhead and improving reliability.

**Architecture:** Local temp (1GB) → DRAM aggregation (100MB) → SMB upload. On gallery read, stream from local temp or SMB to memory.

**Tech Stack:** Java, Android ForegroundService, smbj library, SharedPreferences

---

## File Structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/hippo/ehviewer/smb/SmbCacheSettings.java` | Cache configuration (threshold, temp dir) |
| `app/src/main/java/com/hippo/ehviewer/smb/SmbUploadService.java` | ForegroundService for batch SMB upload |
| `app/src/main/java/com/hippo/ehviewer/spider/SpiderDen.java` | Redirect download to local temp when SMB |
| `app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java` | Trigger upload when cache threshold reached |
| `app/src/main/java/com/hippo/ehviewer/ui/fragment/DownloadFragment.java` | Config UI + reminder |
| `app/src/main/AndroidManifest.xml` | Register SmbUploadService |

---

### Task 1: Create SmbCacheSettings

**Covers:** Cache configuration

**Files:**
- Create: `app/src/main/java/com/hippo/ehviewer/smb/SmbCacheSettings.java`

- [ ] **Step 1: Create SmbCacheSettings class**

```java
package com.hippo.ehviewer.smb;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public final class SmbCacheSettings {

    private static final String PREFS = "smb_cache_settings";
    private static final String KEY_CACHE_DIR = "cache_dir";
    private static final String KEY_THRESHOLD_PERCENT = "threshold_percent";
    private static final String KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb";

    private static final int DEFAULT_THRESHOLD_PERCENT = 60;
    private static final int DEFAULT_MAX_CACHE_SIZE_MB = 1024; // 1GB

    private final SharedPreferences preferences;

    public SmbCacheSettings(Context context) {
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getCacheDir() {
        return preferences.getString(KEY_CACHE_DIR, null);
    }

    public void setCacheDir(String path) {
        preferences.edit().putString(KEY_CACHE_DIR, path).apply();
    }

    public int getThresholdPercent() {
        return preferences.getInt(KEY_THRESHOLD_PERCENT, DEFAULT_THRESHOLD_PERCENT);
    }

    public void setThresholdPercent(int percent) {
        preferences.edit().putInt(KEY_THRESHOLD_PERCENT, Math.max(10, Math.min(90, percent))).apply();
    }

    public int getMaxCacheSizeMB() {
        return preferences.getInt(KEY_MAX_CACHE_SIZE_MB, DEFAULT_MAX_CACHE_SIZE_MB);
    }

    public void setMaxCacheSizeMB(int mb) {
        preferences.edit().putInt(KEY_MAX_CACHE_SIZE_MB, Math.max(100, Math.min(4096, mb))).apply();
    }

    public long getMaxCacheSizeBytes() {
        return (long) getMaxCacheSizeMB() * 1024 * 1024;
    }

    public long getThresholdBytes() {
        return getMaxCacheSizeBytes() * getThresholdPercent() / 100;
    }

    public static File getSmbCacheDir(Context context) {
        File cacheDir = context.getCacheDir();
        File smbCache = new File(cacheDir, "smb_cache");
        if (!smbCache.exists()) {
            smbCache.mkdirs();
        }
        return smbCache;
    }

    public static long getDirSize(File dir) {
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += getDirSize(file);
                    }
                }
            }
        }
        return size;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 2: Create SmbUploadService

**Covers:** SMB upload with DRAM aggregation

**Files:**
- Create: `app/src/main/java/com/hippo/ehviewer/smb/SmbUploadService.java`
- Modify: `app/src/main/AndroidManifest.xml` (register service)

- [ ] **Step 1: Create SmbUploadService class**

```java
package com.hippo.ehviewer.smb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.unifile.UniFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmbUploadService extends Service {

    private static final String TAG = "SmbUploadService";
    private static final String CHANNEL_ID = "smb_upload";
    private static final int NOTIFICATION_ID = 0x734D55; // "SmbU"
    private static final String ACTION_START = "com.hippo.ehviewer.smb.UPLOAD_START";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.smb.UPLOAD_CANCEL";
    private static final int DRAM_BUFFER_SIZE = 100 * 1024 * 1024; // 100MB

    private static final AtomicBoolean sRunning = new AtomicBoolean(false);

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private ExecutorService mExecutor;
    private PowerManager.WakeLock mWakeLock;
    private volatile boolean mCancelled;

    public static boolean isRunning() {
        return sRunning.get();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, SmbUploadService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, SmbUploadService.class);
        intent.setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotifyManager = getSystemService(NotificationManager.class);
        mExecutor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_CANCEL:
                mCancelled = true;
                stopSelf();
                return START_NOT_STICKY;
            case ACTION_START:
                if (sRunning.compareAndSet(false, true)) {
                    mCancelled = false;
                    acquireWakeLock();
                    startForeground(NOTIFICATION_ID, buildNotification("准备上传...", 0));
                    mExecutor.execute(this::doUpload);
                }
                return START_STICKY;
            default:
                return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        sRunning.set(false);
        releaseWakeLock();
        super.onDestroy();
    }

    private void doUpload() {
        try {
            SmbSettings smbSettings = new SmbSettings(this);
            SmbConfig config = smbSettings.loadConfig();
            if (config == null) {
                Log.e(TAG, "SMB config not found");
                stopSelf();
                return;
            }

            File smbCacheDir = SmbCacheSettings.getSmbCacheDir(this);
            File[] galleryDirs = smbCacheDir.listFiles(File::isDirectory);
            if (galleryDirs == null || galleryDirs.length == 0) {
                Log.d(TAG, "No galleries to upload");
                stopSelf();
                return;
            }

            SmbConnection connection = SmbConnection.obtain(config);
            int total = galleryDirs.length;
            int success = 0;
            int failed = 0;

            for (int i = 0; i < total; i++) {
                if (mCancelled) break;

                File galleryDir = galleryDirs[i];
                String galleryName = galleryDir.getName();
                updateNotification("上传中: " + galleryName, i * 100 / total);

                try {
                    uploadGallery(connection, galleryDir, config);
                    deleteRecursive(galleryDir);
                    success++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to upload gallery: " + galleryName, e);
                    failed++;
                }
            }

            String result = String.format("上传完成: %d 成功, %d 失败", success, failed);
            updateNotification(result, 100);
        } catch (Exception e) {
            Log.e(TAG, "Upload failed", e);
            updateNotification("上传失败: " + e.getMessage(), 0);
        } finally {
            stopSelf();
        }
    }

    private void uploadGallery(SmbConnection connection, File galleryDir, SmbConfig config) throws IOException {
        File[] files = galleryDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (mCancelled) return;
            if (file.isFile()) {
                uploadFile(connection, file, config.getShare(), galleryDir.getName());
            }
        }
    }

    private void uploadFile(SmbConnection connection, File localFile, String share, String galleryName) throws IOException {
        String smbPath = galleryName + "/" + localFile.getName();
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream dramBuffer = new ByteArrayOutputStream();

        try (FileInputStream fis = new FileInputStream(localFile)) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dramBuffer.write(buffer, 0, bytesRead);
                if (dramBuffer.size() >= DRAM_BUFFER_SIZE) {
                    flushToSmb(connection, smbPath, dramBuffer.toByteArray(), false);
                    dramBuffer.reset();
                }
            }
            if (dramBuffer.size() > 0) {
                flushToSmb(connection, smbPath, dramBuffer.toByteArray(), false);
            }
        }
    }

    private void flushToSmb(SmbConnection connection, String path, byte[] data, boolean append) throws IOException {
        try (InputStream is = new java.io.ByteArrayInputStream(data)) {
            connection.openOutputStream(path, append);
            // Write using the connection's OutputStreamPipe pattern
            // For simplicity, use the connection's openOutputStream
            java.io.OutputStream os = connection.openOutputStream(path, append);
            os.write(data);
            os.close();
        }
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDir.delete();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMB 上传",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("SMB 文件上传进度");
            mNotifyManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, int progress) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, SmbUploadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPending = PendingIntent.getService(this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("SMB 上传")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPending);

        if (progress > 0) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    private void updateNotification(String text, int progress) {
        Notification notification = buildNotification(text, progress);
        mNotifyManager.notify(NOTIFICATION_ID, notification);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire(30 * 60 * 1000L); // 30 minutes max
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }
}
```

- [ ] **Step 2: Register service in AndroidManifest.xml**

Find `</application>` tag and add before it:
```xml
<service
    android:name=".smb.SmbUploadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 3: Modify SpiderDen for Local Cache Redirect

**Covers:** Download path redirect to local temp

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/spider/SpiderDen.java:84-133`

- [ ] **Step 1: Add import for SmbCacheSettings**

```java
import com.hippo.ehviewer.smb.SmbCacheSettings;
```

- [ ] **Step 2: Modify getGalleryDownloadDir method**

Replace the method to check for SMB path and redirect to local cache:

```java
public static UniFile getGalleryDownloadDir(GalleryInfo galleryInfo) {
    UniFile dir = Settings.getDownloadLocation();
    if (dir != null) {
        // Check if download path is SMB
        if (dir.getUri() != null && "smb".equals(dir.getUri().getScheme())) {
            // Redirect to local temp cache
            File smbCacheDir = SmbCacheSettings.getSmbCacheDir(
                com.hippo.ehviewer.EhApplication.getInstance());
            String dirname = FileUtils.sanitizeFilename(
                galleryInfo.gid + "-" + EhUtils.getSuitableTitle(galleryInfo));
            File galleryDir = new File(smbCacheDir, dirname);
            if (!galleryDir.exists()) {
                galleryDir.mkdirs();
            }
            return new com.hippo.unifile.RawFile(null, com.hippo.ehviewer.EhApplication.getInstance(), galleryDir);
        }

        // Original logic for local paths
        String dirname = EhDB.getDownloadDirname(galleryInfo.gid);
        if (null != dirname) {
            dirname = FileUtils.sanitizeFilename(dirname);
            EhDB.putDownloadDirname(galleryInfo.gid, dirname);
        }

        if (null == dirname) {
            try {
                UniFile[] files = dir.listFiles(new StartWithFilenameFilter(galleryInfo.gid + "-"));
                if (null != files) {
                    int maxLength = -1;
                    for (UniFile file : files) {
                        if (file.isDirectory()) {
                            String name = file.getName();
                            int length = name.length();
                            if (length > maxLength) {
                                maxLength = length;
                                dirname = name;
                            }
                        }
                    }
                    if (null != dirname) {
                        EhDB.putDownloadDirname(galleryInfo.gid, dirname);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("SpiderDen", "Failed to list files in download directory", e);
            }
        }

        if (null == dirname) {
            dirname = FileUtils.sanitizeFilename(galleryInfo.gid + "-" + EhUtils.getSuitableTitle(galleryInfo));
            EhDB.putDownloadDirname(galleryInfo.gid, dirname);
        }

        return dir.subFile(dirname);
    } else {
        return null;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 4: Modify DownloadManager to Trigger Upload

**Covers:** Cache threshold check and upload trigger

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java:1275-1285`

- [ ] **Step 1: Add import for SmbCacheSettings and SmbUploadService**

```java
import com.hippo.ehviewer.smb.SmbCacheSettings;
import com.hippo.ehviewer.smb.SmbUploadService;
import java.io.File;
```

- [ ] **Step 2: Modify the download complete handler**

Replace the SMB backup trigger section with cache check:

```java
if (info.legacy == 0) {
    info.state = DownloadInfo.STATE_FINISH;
    // Check if download location is SMB and trigger upload if cache threshold reached
    UniFile downloadLoc = Settings.getDownloadLocation();
    if (downloadLoc != null && downloadLoc.getUri() != null 
            && "smb".equals(downloadLoc.getUri().getScheme())) {
        // Download is to local cache, check if we need to upload
        File smbCacheDir = SmbCacheSettings.getSmbCacheDir(mContext);
        long cacheSize = SmbCacheSettings.getDirSize(smbCacheDir);
        SmbCacheSettings cacheSettings = new SmbCacheSettings(mContext);
        if (cacheSize >= cacheSettings.getThresholdBytes()) {
            SmbUploadService.start(mContext);
        }
    } else if (downloadLoc != null) {
        // Original SMB backup logic for local paths
        new com.hippo.ehviewer.smb.SmbBackupManager(mContext).syncGalleryToBackup(info);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 5: Add Config UI and Reminder

**Covers:** User configuration and reminder

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/ui/fragment/DownloadFragment.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add string resources**

In `values/strings.xml`:
```xml
<string name="settings_download_smb_cache_threshold">缓存上传阈值</string>
<string name="settings_download_smb_cache_threshold_summary">缓存占用达到 %1$d%% 时自动上传到 SMB</string>
<string name="settings_download_smb_reminder">⚠️ SMB网络存储性能可能受限</string>
```

In `values-en/strings.xml`:
```xml
<string name="settings_download_smb_cache_threshold">Cache Upload Threshold</string>
<string name="settings_download_smb_cache_threshold_summary">Auto-upload to SMB when cache reaches %1$d%%</string>
<string name="settings_download_smb_reminder">⚠️ SMB network storage performance may be limited</string>
```

- [ ] **Step 2: Add cache threshold preference in XML**

In `app/src/main/res/xml/download_settings.xml`, add after SMB backup preferences:
```xml
<ListPreference
    android:key="smb_cache_threshold"
    android:title="@string/settings_download_smb_cache_threshold"
    android:summary="@string/settings_download_smb_cache_threshold_summary"
    android:entries="@array/smb_cache_threshold_entries"
    android:entryValues="@array/smb_cache_threshold_values"
    android:defaultValue="60"
    android:persistent="true" />
```

In `app/src/main/res/values/arrays.xml` (or create if not exists):
```xml
<string-array name="smb_cache_threshold_entries">
    <item>50%</item>
    <item>60%</item>
    <item>75%</item>
    <item>90%</item>
</string-array>
<string-array name="smb_cache_threshold_values">
    <item>50</item>
    <item>60</item>
    <item>75</item>
    <item>90</item>
</string-array>
```

- [ ] **Step 3: Modify DownloadFragment to show reminder**

In `onViewCreated`, after setting up SMB backup preferences, add:
```java
// Show SMB reminder if download location is SMB
UniFile downloadLoc = Settings.getDownloadLocation();
if (downloadLoc != null && downloadLoc.getUri() != null 
        && "smb".equals(downloadLoc.getUri().getScheme())) {
    Toast.makeText(requireActivity(), R.string.settings_download_smb_reminder, Toast.LENGTH_LONG).show();
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 6: Full Build and Test

**Covers:** Integration testing

**Files:** None (testing only)

- [ ] **Step 1: Clean build**

Run: `cd /Users/bob/Ehviewer_CN_SXJ && ./gradlew clean assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on device**

Run: `adb -s 192.168.6.199:37517 install -r app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk`
Expected: Success

- [ ] **Step 3: Manual test checklist**

1. Configure SMB as download path
2. Verify reminder Toast appears
3. Download a gallery
4. Verify files go to local temp (`/data/data/com.xjs.ehviewer.debug/cache/smb_cache/`)
5. Verify upload triggers when threshold reached
6. Verify local files deleted after successful upload
7. Verify gallery can be read from local temp or SMB

---

## Summary

| Task | Description | Est. Time |
|------|-------------|-----------|
| 1 | SmbCacheSettings | 10 min |
| 2 | SmbUploadService | 30 min |
| 3 | SpiderDen redirect | 15 min |
| 4 | DownloadManager trigger | 10 min |
| 5 | Config UI + reminder | 15 min |
| 6 | Build + test | 20 min |
| **Total** | | **~100 min** |

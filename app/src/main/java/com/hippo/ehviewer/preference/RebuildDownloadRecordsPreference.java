package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.NumberUtils;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RebuildDownloadRecordsPreference extends Preference {

    public RebuildDownloadRecordsPreference(Context context) {
        super(context);
        init();
    }

    public RebuildDownloadRecordsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RebuildDownloadRecordsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setKey("rebuild_download_records");
        setTitle(R.string.settings_download_rebuild_download_records);
        setSummary(R.string.settings_download_rebuild_download_records_summary);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        
        com.hippo.ehviewer.task.RebuildDownloadRecordsTask task = 
            new com.hippo.ehviewer.task.RebuildDownloadRecordsTask(context);
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        
        android.widget.Toast.makeText(context, 
            R.string.settings_download_rebuild_download_records_started, 
            android.widget.Toast.LENGTH_SHORT).show();
    }

    private static class RebuildTask implements Runnable {
        private final Context mContext;
        private final String mTaskId;
        private final BackgroundTaskStatusManager mStatusManager;
        private final List<String> mLogs = new ArrayList<>();
        private final AtomicInteger mRebuildCount = new AtomicInteger(0);
        private final AtomicInteger mProcessedCount = new AtomicInteger(0);

        public RebuildTask(@NonNull Context context, String taskId, BackgroundTaskStatusManager statusManager) {
            mContext = context;
            mTaskId = taskId;
            mStatusManager = statusManager;
        }

        @Override
        public void run() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null || !downloadDir.isDirectory()) {
                mStatusManager.markTaskCompleted(mTaskId);
                return;
            }

            UniFile[] files = downloadDir.listFiles();
            if (files == null) {
                mStatusManager.markTaskCompleted(mTaskId);
                return;
            }

            int total = files.length;
            mStatusManager.updateTaskProgress(mTaskId, 0, total);

            // 创建专用的线程池用于重建任务
            int threadCount = 10; // 使用10个线程进行重建
            ExecutorService rebuildExecutor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(total);
            
            DownloadManager downloadManager = EhApplication.getDownloadManager(mContext);

            // 提交所有文件处理任务到线程池
            for (UniFile dir : files) {
                rebuildExecutor.submit(() -> {
                    try {
                        processDirectory(dir, downloadManager);
                    } finally {
                        int processed = mProcessedCount.incrementAndGet();
                        mStatusManager.updateTaskProgress(mTaskId, processed, total);
                        latch.countDown();
                    }
                });
            }

            try {
                // 等待所有任务完成
                latch.await(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rebuildExecutor.shutdown();
            }

            if (!mLogs.isEmpty()) {
                saveRebuildLog();
            }

            // 标记任务完成
            mStatusManager.markTaskCompleted(mTaskId);
            
            // 显示结果
            final int resultCount = mRebuildCount.get();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                String message = mContext.getString(R.string.settings_download_rebuild_complete, resultCount);
                android.widget.Toast.makeText(mContext, message, android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        private void processDirectory(UniFile dir, DownloadManager downloadManager) {
            if (!dir.isDirectory()) {
                return;
            }

            UniFile ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
            if (ehViewerFile == null) {
                return;
            }

            try {
                String content = IOUtils.readString(ehViewerFile.openInputStream(), StandardCharsets.UTF_8.name());
                String[] lines = content.split("\n");
                
                // 检查是否是VERSION2格式
                if (lines.length > 0 && lines[0].trim().equals("VERSION2")) {
                    // VERSION2格式解析
                    if (lines.length < 4) {
                        synchronized (mLogs) {
                            mLogs.add("Invalid VERSION2 .ehviewer file in: " + dir.getName());
                        }
                        return;
                    }
                    
                    // 解析gid和token
                    long gid;
                    String token;
                    try {
                        // 跳过版本号行，直接获取gid
                        gid = Long.parseLong(lines[2].trim());
                        token = lines[3].trim();
                    } catch (NumberFormatException e) {
                        synchronized (mLogs) {
                            mLogs.add("Invalid gid or token in VERSION2 .ehviewer file: " + dir.getName());
                        }
                        return;
                    }
                    
                    // 检查是否已经存在
                    if (downloadManager.getDownloadInfo(gid) != null) {
                        return;
                    }
                    
                    // 创建GalleryInfo对象
                    GalleryInfo galleryInfo = new GalleryInfo();
                    galleryInfo.gid = gid;
                    galleryInfo.token = token;
                    
                    // 使用目录名作为标题（如果无法从文件中解析）
                    String dirName = dir.getName();
                    if (dirName != null && dirName.contains("-")) {
                        // 尝试从目录名提取标题（去掉GID前缀）
                        String[] parts = dirName.split("-", 2);
                        if (parts.length > 1) {
                            galleryInfo.title = parts[1].trim();
                        } else {
                            galleryInfo.title = dirName;
                        }
                    } else {
                        galleryInfo.title = dirName != null ? dirName : "Unknown Title";
                    }
                    
                    // 统计图片文件数量
                    UniFile[] imageFiles = dir.listFiles();
                    int imageCount = 0;
                    if (imageFiles != null) {
                        for (UniFile file : imageFiles) {
                            String fileName = file.getName();
                            if (fileName != null && !fileName.startsWith(".") && 
                                (fileName.endsWith(".jpg") || fileName.endsWith(".png") || 
                                 fileName.endsWith(".gif") || fileName.endsWith(".webp"))) {
                                imageCount++;
                            }
                        }
                    }
                    
                    galleryInfo.pages = imageCount;
                    galleryInfo.category = EhUtils.UNKNOWN; // 默认分类
                    
                    // 添加到下载管理器
                    downloadManager.addDownload(galleryInfo, null);
                    mRebuildCount.incrementAndGet();
                    
                    synchronized (mLogs) {
                        mLogs.add("Added VERSION2 gallery: " + galleryInfo.title + " (GID: " + galleryInfo.gid + "), Pages: " + imageCount);
                    }
                    
                } else {
                    // 原有格式解析
                    if (lines.length < 8) {
                        synchronized (mLogs) {
                            mLogs.add("Invalid .ehviewer file in: " + dir.getName());
                        }
                        return;
                    }

                    // Parse gid from first line
                    long gid;
                    try {
                        gid = Long.parseLong(lines[0].trim());
                    } catch (NumberFormatException e) {
                        synchronized (mLogs) {
                            mLogs.add("Invalid gid in .ehviewer file: " + dir.getName());
                        }
                        return;
                    }

                    // Check if already exists
                    if (downloadManager.getDownloadInfo(gid) != null) {
                        return;
                    }

                    // Parse gallery info from .ehviewer file
                    GalleryInfo galleryInfo = parseGalleryInfoFromLines(lines);
                    if (galleryInfo != null) {
                        downloadManager.addDownload(galleryInfo, null);
                        mRebuildCount.incrementAndGet();
                        
                        synchronized (mLogs) {
                            mLogs.add("Added gallery: " + galleryInfo.title + " (GID: " + galleryInfo.gid + ")");
                        }
                    }
                }

            } catch (IOException | NumberFormatException e) {
                synchronized (mLogs) {
                    mLogs.add("Error processing directory " + dir.getName() + ": " + e.getMessage());
                }
            }
        }

        private GalleryInfo parseGalleryInfoFromLines(String[] lines) {
            try {
                GalleryInfo info = new GalleryInfo();
                info.gid = Long.parseLong(lines[0].trim());
                info.token = lines[1].trim();
                info.title = lines[2].trim();
                info.titleJpn = lines.length > 3 ? lines[3].trim() : "";
                info.thumb = lines.length > 4 ? lines[4].trim() : "";
                info.category = lines.length > 5 ? EhUtils.getCategory(lines[5].trim()) : EhUtils.UNKNOWN;
                info.posted = lines.length > 6 ? lines[6].trim() : "";
                info.uploader = lines.length > 7 ? lines[7].trim() : "";
                info.rating = lines.length > 8 ? Float.parseFloat(lines[8].trim()) : 0.0f;
                info.rated = lines.length > 9 ? Boolean.parseBoolean(lines[9].trim()) : false;
                info.simpleLanguage = lines.length > 10 ? lines[10].trim() : "";
                info.simpleTags = lines.length > 11 ? lines[11].trim().split(",") : new String[0];
                info.thumbWidth = lines.length > 12 ? Integer.parseInt(lines[12].trim()) : 0;
                info.thumbHeight = lines.length > 13 ? Integer.parseInt(lines[13].trim()) : 0;
                info.spanSize = lines.length > 14 ? Integer.parseInt(lines[14].trim()) : 0;
                info.spanIndex = lines.length > 15 ? Integer.parseInt(lines[15].trim()) : 0;
                info.spanGroupIndex = lines.length > 16 ? Integer.parseInt(lines[16].trim()) : 0;
                info.favoriteSlot = lines.length > 17 ? Integer.parseInt(lines[17].trim()) : -2;
                info.favoriteName = lines.length > 18 ? lines[18].trim() : null;
                info.pages = lines.length > 19 ? Integer.parseInt(lines[19].trim()) : 0;
                return info;
            } catch (Exception e) {
                return null;
            }
        }

        private void saveRebuildLog() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String fileName = "rebuild-" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadDir.createFile(fileName);
            if (logFile != null) {
                try (OutputStream os = logFile.openOutputStream()) {
                    for (String log : mLogs) {
                        os.write((log + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
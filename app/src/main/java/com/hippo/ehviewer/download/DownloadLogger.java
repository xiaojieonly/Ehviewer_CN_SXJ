package com.hippo.ehviewer.download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.EhApplication;
import com.hippo.unifile.UniFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载日志管理器
 * 负责记录下载相关操作的详细日志，并自动保存到指定目录
 */
public class DownloadLogger {
    private static final String TAG = "DownloadLogger";
    private static final String LOG_FILE_PREFIX = "ehviewer_download_";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final int MAX_LOG_QUEUE_SIZE = 1000;
    private static final long LOG_FLUSH_INTERVAL = 5000; // 5秒
    
    private static DownloadLogger sInstance;
    private final Context mContext;
    private final ExecutorService mLogExecutor;
    private final BlockingQueue<LogEntry> mLogQueue;
    private final SimpleDateFormat mDateFormat;
    private final Object mLock = new Object();
    
    private File mCurrentLogFile;
    private FileWriter mLogFileWriter;
    private long mLastFlushTime;
    private boolean mIsLoggingEnabled;
    
    public static synchronized void initialize(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new DownloadLogger(context.getApplicationContext());
        }
    }
    
    public static DownloadLogger getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("DownloadLogger not initialized");
        }
        return sInstance;
    }
    
    private DownloadLogger(@NonNull Context context) {
        mContext = context;
        mLogExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "DownloadLogger");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        mLogQueue = new LinkedBlockingQueue<>(MAX_LOG_QUEUE_SIZE);
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        mIsLoggingEnabled = Settings.getDownloadLoggingEnabled();
        
        // 启动日志处理线程
        startLogProcessor();
    }
    
    /**
     * 记录下载开始日志
     */
    public void logDownloadStart(@NonNull String gid, @NonNull String title, int totalPages) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format(Locale.getDefault(),
            "下载开始 - GID: %s, 标题: %s, 总页数: %d", gid, title, totalPages);
        log(LogLevel.INFO, "DownloadStart", message, gid, title);
    }
    
    /**
     * 记录下载进度日志
     */
    public void logDownloadProgress(@NonNull String gid, @NonNull String title, 
                                  int currentPage, int totalPages, int speed) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format(Locale.getDefault(),
            "下载进度 - GID: %s, 标题: %s, 当前进度: %d/%d, 速度: %d KB/s", 
            gid, title, currentPage, totalPages, speed);
        log(LogLevel.DEBUG, "DownloadProgress", message, gid, title);
    }
    
    /**
     * 记录下载完成日志
     */
    public void logDownloadComplete(@NonNull String gid, @NonNull String title, 
                                  long totalTime, int successCount, int failCount) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format(Locale.getDefault(),
            "下载完成 - GID: %s, 标题: %s, 总耗时: %d ms, 成功: %d, 失败: %d", 
            gid, title, totalTime, successCount, failCount);
        log(LogLevel.INFO, "DownloadComplete", message, gid, title);
    }
    
    /**
     * 记录下载错误日志
     */
    public void logDownloadError(@NonNull String gid, @NonNull String title, 
                               @NonNull String error, @Nullable Exception exception) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format(Locale.getDefault(),
            "下载错误 - GID: %s, 标题: %s, 错误: %s", gid, title, error);
        if (exception != null) {
            message += ", 异常: " + exception.getMessage();
        }
        log(LogLevel.ERROR, "DownloadError", message, gid, title, exception);
    }
    
    /**
     * 记录后台任务开始日志
     */
    public void logBackgroundTaskStart(@NonNull String taskType, @NonNull String taskName) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format("后台任务开始 - 类型: %s, 名称: %s", taskType, taskName);
        log(LogLevel.INFO, "BackgroundTaskStart", message, null, taskName);
    }
    
    /**
     * 记录后台任务进度日志
     */
    public void logBackgroundTaskProgress(@NonNull String taskType, @NonNull String taskName, 
                                        int current, int total) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format("后台任务进度 - 类型: %s, 名称: %s, 进度: %d/%d", 
            taskType, taskName, current, total);
        log(LogLevel.DEBUG, "BackgroundTaskProgress", message, null, taskName);
    }
    
    /**
     * 记录后台任务完成日志
     */
    public void logBackgroundTaskComplete(@NonNull String taskType, @NonNull String taskName, 
                                        long totalTime, boolean success) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format("后台任务完成 - 类型: %s, 名称: %s, 总耗时: %d ms, 成功: %s", 
            taskType, taskName, totalTime, success);
        log(LogLevel.INFO, "BackgroundTaskComplete", message, null, taskName);
    }
    
    /**
     * 记录设置变更日志
     */
    public void logSettingsChange(@NonNull String key, @Nullable Object oldValue, 
                                @Nullable Object newValue) {
        if (!mIsLoggingEnabled) return;
        
        String message = String.format("设置变更 - 键: %s, 旧值: %s, 新值: %s", 
            key, oldValue, newValue);
        log(LogLevel.INFO, "SettingsChange", message, null, null);
    }
    
    /**
     * 记录通用日志
     */
    public void log(@NonNull LogLevel level, @NonNull String tag, @NonNull String message, 
                   @Nullable String gid, @Nullable String title) {
        log(level, tag, message, gid, title, null);
    }
    
    /**
     * 记录通用日志（带异常）
     */
    public void log(@NonNull LogLevel level, @NonNull String tag, @NonNull String message, 
                   @Nullable String gid, @Nullable String title, @Nullable Exception exception) {
        if (!mIsLoggingEnabled) return;
        
        LogEntry entry = new LogEntry(level, tag, message, gid, title, exception, System.currentTimeMillis());
        
        // 添加到队列，如果队列满了则移除最旧的条目
        if (!mLogQueue.offer(entry)) {
            mLogQueue.poll();
            mLogQueue.offer(entry);
        }
        
        // 同时输出到Logcat
        outputToLogcat(level, tag, message, exception);
    }
    
    /**
     * 输出日志到Logcat
     */
    private void outputToLogcat(@NonNull LogLevel level, @NonNull String tag, @NonNull String message, 
                               @Nullable Exception exception) {
        switch (level) {
            case DEBUG:
                Log.d(tag, message, exception);
                break;
            case INFO:
                Log.i(tag, message, exception);
                break;
            case WARN:
                Log.w(tag, message, exception);
                break;
            case ERROR:
                Log.e(tag, message, exception);
                break;
        }
    }
    
    /**
     * 启用或禁用日志记录
     */
    public void setLoggingEnabled(boolean enabled) {
        synchronized (mLock) {
            mIsLoggingEnabled = enabled;
            Settings.putDownloadLoggingEnabled(enabled);
            
            if (enabled) {
                log(LogLevel.INFO, TAG, "下载日志已启用", null, null);
            } else {
                log(LogLevel.INFO, TAG, "下载日志已禁用", null, null);
                flushLogs();
            }
        }
    }
    
    /**
     * 立即刷新日志到文件
     */
    public void flushLogs() {
        synchronized (mLock) {
            if (mLogFileWriter != null) {
                try {
                    mLogFileWriter.flush();
                } catch (IOException e) {
                    Log.e(TAG, "刷新日志文件失败", e);
                }
            }
        }
    }
    
    /**
     * 启动日志处理器
     */
    private void startLogProcessor() {
        mLogExecutor.execute(() -> {
            while (true) {
                try {
                    LogEntry entry = mLogQueue.take();
                    writeLogToFile(entry);
                    
                    // 定期刷新日志文件
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - mLastFlushTime > LOG_FLUSH_INTERVAL) {
                        flushLogs();
                        mLastFlushTime = currentTime;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "日志处理器异常", e);
                }
            }
        });
    }
    
    /**
     * 写入日志到文件
     */
    private void writeLogToFile(@NonNull LogEntry entry) {
        synchronized (mLock) {
            try {
                // 检查是否需要创建新的日志文件
                if (mCurrentLogFile == null || shouldCreateNewLogFile(entry.timestamp)) {
                    createNewLogFile();
                }
                
                if (mLogFileWriter != null) {
                    String logLine = formatLogEntry(entry);
                    mLogFileWriter.write(logLine);
                    mLogFileWriter.write("\n");
                }
            } catch (IOException e) {
                Log.e(TAG, "写入日志文件失败", e);
            }
        }
    }
    
    /**
     * 判断是否需要创建新的日志文件
     */
    private boolean shouldCreateNewLogFile(long timestamp) {
        if (mCurrentLogFile == null) return true;
        
        // 每天创建一个新的日志文件
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDay = dayFormat.format(new Date(timestamp));
        String fileDay = dayFormat.format(new Date(mCurrentLogFile.lastModified()));
        
        return !currentDay.equals(fileDay);
    }
    
    /**
     * 创建新的日志文件
     */
    private void createNewLogFile() throws IOException {
        // 关闭当前的日志文件
        if (mLogFileWriter != null) {
            mLogFileWriter.close();
            mLogFileWriter = null;
        }
        
        // 获取日志目录
        File logDir = getLogDirectory();
        if (!logDir.exists() && !logDir.mkdirs()) {
            throw new IOException("无法创建日志目录: " + logDir.getAbsolutePath());
        }
        
        // 创建新的日志文件
        String fileName = LOG_FILE_PREFIX + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + LOG_FILE_EXTENSION;
        mCurrentLogFile = new File(logDir, fileName);
        mLogFileWriter = new FileWriter(mCurrentLogFile, true); // 追加模式
        
        // 写入文件头
        if (mCurrentLogFile.length() == 0) {
            String header = String.format("=== EhViewer 下载日志 - %s ===\n", 
                mDateFormat.format(new Date()));
            mLogFileWriter.write(header);
        }
    }
    
    /**
     * 获取日志目录
     */
    public File getLogDirectory() {
        // 优先使用用户设置的下载位置
        UniFile downloadLocation = Settings.getDownloadLocation();
        if (downloadLocation != null) {
            File logDir = new File(downloadLocation.getUri().getPath(), "logs");
            if (logDir.exists() || logDir.mkdirs()) {
                return logDir;
            }
        }
        
        // 备用：使用应用私有目录
        return new File(mContext.getFilesDir(), "logs");
    }
    
    /**
     * 获取所有日志文件
     */
    public File[] getLogFiles() {
        File logDir = getLogDirectory();
        if (logDir.exists() && logDir.isDirectory()) {
            return logDir.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX) && name.endsWith(LOG_FILE_EXTENSION));
        }
        return new File[0];
    }
    
    /**
     * 清理旧日志文件（保留最近7天）
     */
    public void cleanOldLogs() {
        File logDir = getLogDirectory();
        if (logDir.exists() && logDir.isDirectory()) {
            File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX) && name.endsWith(LOG_FILE_EXTENSION));
            if (logFiles != null) {
                long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
                for (File logFile : logFiles) {
                    if (logFile.lastModified() < sevenDaysAgo) {
                        if (logFile.delete()) {
                            Log.i(TAG, "删除旧日志文件: " + logFile.getName());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 格式化日志条目
     */
    private String formatLogEntry(@NonNull LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(mDateFormat.format(new Date(entry.timestamp)));
        sb.append(" [").append(entry.level.name()).append("]");
        sb.append(" [").append(entry.tag).append("]");
        
        if (entry.gid != null) {
            sb.append(" [GID:").append(entry.gid).append("]");
        }
        
        if (entry.title != null) {
            sb.append(" [").append(entry.title).append("]");
        }
        
        sb.append(" ").append(entry.message);
        
        if (entry.exception != null) {
            sb.append("\n异常堆栈: ").append(Log.getStackTraceString(entry.exception));
        }
        
        return sb.toString();
    }
    
    /**
     * 日志级别
     */
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 日志条目
     */
    private static class LogEntry {
        final LogLevel level;
        final String tag;
        final String message;
        final String gid;
        final String title;
        final Exception exception;
        final long timestamp;
        
        LogEntry(LogLevel level, String tag, String message, String gid, String title, 
                Exception exception, long timestamp) {
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.gid = gid;
            this.title = title;
            this.exception = exception;
            this.timestamp = timestamp;
        }
    }
}
package com.hippo.ehviewer.ui.task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.task.BackgroundTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * 后台任务信息类
 * 用于存储和管理后台任务的状态信息
 */
public class BackgroundTaskInfo {
    private final String taskId;
    private final String taskName;
    private final String taskDescription;
    private final Future<?> future;
    private final BackgroundTask.TaskType taskType;
    private final boolean uniqueTask;
    private final String taskClassName;
    private final String taskPersistData;
    private final long startTime;
    private volatile int currentProgress;
    private volatile int totalProgress;
    private volatile String progressDetail;
    private volatile boolean isCompleted;
    private volatile boolean isCancelled;
    private volatile boolean isPaused;
    private volatile boolean isQueued;
    private volatile String errorMessage;
    private volatile File logFile;
    private final List<String> logMessages;

    public BackgroundTaskInfo(@NonNull String taskId, @NonNull String taskName,
                             @Nullable String taskDescription, @Nullable Future<?> future,
                             @NonNull BackgroundTask.TaskType taskType, boolean uniqueTask,
                             @NonNull String taskClassName, @Nullable String taskPersistData,
                             long startTime) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskDescription = taskDescription;
        this.future = future;
        this.taskType = taskType;
        this.uniqueTask = uniqueTask;
        this.taskClassName = taskClassName;
        this.taskPersistData = taskPersistData;
        this.startTime = startTime;
        this.currentProgress = 0;
        this.totalProgress = -1; // -1 表示不确定进度
        this.progressDetail = null;
        this.isCompleted = false;
        this.isCancelled = false;
        this.isPaused = false;
        this.isQueued = false;
        this.errorMessage = null;
        this.logFile = null;
        this.logMessages = Collections.synchronizedList(new ArrayList<>());
    }

    @NonNull
    public String getTaskId() {
        return taskId;
    }

    @NonNull
    public String getTaskName() {
        return taskName;
    }

    @Nullable
    public String getTaskDescription() {
        return taskDescription;
    }

    @Nullable
    public Future<?> getFuture() {
        return future;
    }

    @NonNull
    public BackgroundTask.TaskType getTaskType() {
        return taskType;
    }

    @NonNull
    public String getTaskClassName() {
        return taskClassName;
    }

    @Nullable
    public String getTaskPersistData() {
        return taskPersistData;
    }

    public boolean isUniqueTask() {
        return uniqueTask;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    public int getTotalProgress() {
        return totalProgress;
    }

    public void setTotalProgress(int totalProgress) {
        this.totalProgress = totalProgress;
    }

    @Nullable
    public String getProgressDetail() {
        return progressDetail;
    }

    public void setProgressDetail(@Nullable String progressDetail) {
        this.progressDetail = progressDetail;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        isPaused = paused;
    }

    public boolean isQueued() {
        return isQueued;
    }

    public void setQueued(boolean queued) {
        isQueued = queued;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 获取进度百分比（0-100）
     * 如果总进度为-1（不确定），返回-1
     */
    public int getProgressPercentage() {
        if (totalProgress <= 0) {
            return -1;
        }
        return (int) ((currentProgress * 100L) / totalProgress);
    }

    /**
     * 获取运行时长（毫秒）
     */
    public long getRunningTime() {
        return System.currentTimeMillis() - startTime;
    }

    public void setLogFile(@Nullable File file) {
        this.logFile = file;
    }

    @Nullable
    public File getLogFile() {
        return logFile;
    }

    public void appendLog(@NonNull String message) {
        String stamped = stampMessage(message);
        logMessages.add(stamped);

        File target = logFile;
        if (target != null) {
            try (FileWriter writer = new FileWriter(target, true)) {
                writer.append(stamped).append('\n');
            } catch (IOException ignore) {
                // 忽略写入异常，仍然保留内存日志
            }
        }
    }

    public void addLogMessage(@NonNull String message) {
        logMessages.add(message);
    }

    @NonNull
    public List<String> getLogMessages() {
        synchronized (logMessages) {
            return new ArrayList<>(logMessages);
        }
    }

    private String stampMessage(@NonNull String message) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return format.format(new Date()) + " - " + message;
    }

    /**
     * 取消任务
     */
    public boolean cancel() {
        if (future != null && !future.isDone()) {
            isCancelled = future.cancel(true);
            return isCancelled;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackgroundTaskInfo that = (BackgroundTaskInfo) o;
        return taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        return taskId.hashCode();
    }
}

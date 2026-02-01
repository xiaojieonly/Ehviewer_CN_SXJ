package com.hippo.ehviewer.ui.task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.BackgroundTaskManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 后台任务状态管理器
 * 用于跟踪和管理所有后台任务的状态
 */
public class BackgroundTaskStatusManager {
    private static final String TAG = "BackgroundTaskStatusManager";
    private static BackgroundTaskStatusManager sInstance;
    
    // 存储所有活跃的任务
    private final Map<String, BackgroundTaskInfo> mActiveTasks = new ConcurrentHashMap<>();
    // 存储已完成的任务（保留最近的一些）
    private final Map<String, BackgroundTaskInfo> mCompletedTasks = new ConcurrentHashMap<>();
    // 最大保留的已完成任务数量
    private static final int MAX_COMPLETED_TASKS = 50;
    
    private BackgroundTaskStatusManager() {
    }
    
    public static synchronized BackgroundTaskStatusManager getInstance() {
        if (sInstance == null) {
            sInstance = new BackgroundTaskStatusManager();
        }
        return sInstance;
    }
    
    /**
     * 添加一个新的后台任务
     */
    @NonNull
    public String addTask(@NonNull String taskName, @Nullable String taskDescription, @NonNull Future<?> future) {
        String taskId = UUID.randomUUID().toString();
        BackgroundTaskInfo taskInfo = new BackgroundTaskInfo(taskId, taskName, taskDescription, future);
        mActiveTasks.put(taskId, taskInfo);
        return taskId;
    }
    
    /**
     * 更新任务进度
     */
    public void updateTaskProgress(@NonNull String taskId, int current, int total) {
        updateTaskProgress(taskId, current, total, null);
    }

    public void updateTaskProgress(@NonNull String taskId, int current, int total, @Nullable String detail) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setCurrentProgress(current);
            taskInfo.setTotalProgress(total);
            taskInfo.setProgressDetail(detail);
            
            // 更新通知栏进度
            BackgroundTaskManager.getInstance().updateTaskProgress(
                taskInfo.getTaskName(), 
                taskInfo.getTaskDescription(),
                current, 
                total
            );
        }
    }

    public void updateTaskLogFile(@NonNull String taskId, @Nullable File logFile) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setLogFile(logFile);
        }
    }

    public void appendTaskLog(@NonNull String taskId, @NonNull String message) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.appendLog(message);
        }
    }
    
    /**
     * 标记任务完成
     */
    public void markTaskCompleted(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setCompleted(true);
            
            // 添加到已完成任务列表
            mCompletedTasks.put(taskId, taskInfo);
            
            // 限制已完成任务的数量
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 移除最旧的任务
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
        }
    }
    
    /**
     * 标记任务取消
     */
    public void markTaskCancelled(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setCancelled(true);
            
            // 添加到已完成任务列表
            mCompletedTasks.put(taskId, taskInfo);
            
            // 限制已完成任务的数量
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 移除最旧的任务
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
        }
    }
    
    /**
     * 标记任务出错
     */
    public void markTaskError(@NonNull String taskId, @Nullable String errorMessage) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setErrorMessage(errorMessage);
            
            // 添加到已完成任务列表
            mCompletedTasks.put(taskId, taskInfo);
            
            // 限制已完成任务的数量
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 移除最旧的任务
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
        }
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            boolean cancelled = taskInfo.cancel();
            if (cancelled) {
                markTaskCompleted(taskId);
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * 获取活跃任务列表
     */
    @NonNull
    public List<BackgroundTaskInfo> getActiveTasks() {
        return new ArrayList<>(mActiveTasks.values());
    }
    
    /**
     * 获取已完成任务列表
     */
    @NonNull
    public List<BackgroundTaskInfo> getCompletedTasks() {
        return new ArrayList<>(mCompletedTasks.values());
    }
    
    /**
     * 获取指定任务信息
     */
    @Nullable
    public BackgroundTaskInfo getTaskInfo(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo == null) {
            taskInfo = mCompletedTasks.get(taskId);
        }
        return taskInfo;
    }

    @NonNull
    public List<String> getTaskLogs(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = getTaskInfo(taskId);
        if (taskInfo != null) {
            return taskInfo.getLogMessages();
        }
        return new ArrayList<>();
    }
    
    /**
     * 清除所有已完成的任务
     */
    public void clearCompletedTasks() {
        mCompletedTasks.clear();
    }
    
    /**
     * 获取活跃任务数量
     */
    public int getActiveTaskCount() {
        return mActiveTasks.size();
    }
    
    /**
     * 获取所有任务数量（包括活跃和已完成的）
     */
    public int getTotalTaskCount() {
        return mActiveTasks.size() + mCompletedTasks.size();
    }
}
package com.hippo.ehviewer.ui.task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;

import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.task.BackgroundTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 后台任务状态管理器
 * 用于跟踪和管理所有后台任务的状态
 */
public class BackgroundTaskStatusManager {
    private static final String TAG = "BackgroundTaskStatusManager";
    private static final String STATUS_FILE_NAME = "background_tasks.json";
    private static final String LOG_DIR_NAME = "background_task_logs";

    private static BackgroundTaskStatusManager sInstance;

    // 存储所有活跃的任务
    private final Map<String, BackgroundTaskInfo> mActiveTasks = new ConcurrentHashMap<>();
    // 存储已完成的任务（保留最近的一些）
    private final Map<String, BackgroundTaskInfo> mCompletedTasks = new ConcurrentHashMap<>();
    // 最大保留的已完成任务数量
    private static final int MAX_COMPLETED_TASKS = 50;

    private final File mStatusFile;
    private final File mLogDir;
    private final ExecutorService mDiskExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BackgroundTaskStatusManager-Disk");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Object mPersistLock = new Object();

    private BackgroundTaskStatusManager(Context context) {
        File filesDir = context.getFilesDir();
        mStatusFile = new File(filesDir, STATUS_FILE_NAME);
        mLogDir = new File(filesDir, LOG_DIR_NAME);
        if (!mLogDir.exists()) {
            mLogDir.mkdirs();
        }
        restorePersistedTasks();
    }
    
    public static synchronized void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new BackgroundTaskStatusManager(context.getApplicationContext());
        }
    }

    public static synchronized BackgroundTaskStatusManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("BackgroundTaskStatusManager not initialized");
        }
        return sInstance;
    }
    
    /**
     * 添加一个新的后台任务
     */
    @Nullable
    public String addTask(@NonNull String taskName, @Nullable String taskDescription, @Nullable Future<?> future) {
        String taskId = UUID.randomUUID().toString();
        return addTask(taskId, taskName, taskDescription, future, BackgroundTask.TaskType.OTHER, true);
    }

    @Nullable
    public String addTask(@NonNull String taskName, @Nullable String taskDescription, @Nullable Future<?> future,
                          @NonNull BackgroundTask.TaskType taskType, boolean uniqueTask) {
        String taskId = UUID.randomUUID().toString();
        return addTask(taskId, taskName, taskDescription, future, taskType, uniqueTask);
    }

    @Nullable
    public String addTask(@NonNull String taskId, @NonNull String taskName, @Nullable String taskDescription,
                          @Nullable Future<?> future, @NonNull BackgroundTask.TaskType taskType, boolean uniqueTask) {
        return addTask(taskId, taskName, taskDescription, future, taskType, uniqueTask,
                BackgroundTask.class.getName(), null);
    }

    @Nullable
    public String addTask(@NonNull String taskId, @NonNull String taskName, @Nullable String taskDescription,
                          @Nullable Future<?> future, @NonNull BackgroundTask.TaskType taskType, boolean uniqueTask,
                          @NonNull String taskClassName, @Nullable String taskPersistData) {
        if (uniqueTask && taskType != BackgroundTask.TaskType.DOWNLOAD) {
            BackgroundTaskInfo activeUnique = getActiveUniqueNonDownloadTask();
            if (activeUnique != null) {
                return null;
            }
        }

        BackgroundTaskInfo taskInfo = new BackgroundTaskInfo(taskId, taskName, taskDescription, future, taskType,
                uniqueTask, taskClassName, taskPersistData, System.currentTimeMillis());
        File logFile = createTaskLogFile(taskId);
        if (logFile != null) {
            taskInfo.setLogFile(logFile);
        }
        mActiveTasks.put(taskId, taskInfo);
        savePersistedTasksAsync();
        return taskId;
    }

    @Nullable
    public BackgroundTaskInfo getActiveUniqueNonDownloadTask() {
        for (BackgroundTaskInfo info : mActiveTasks.values()) {
            if (info.isUniqueTask() && info.getTaskType() != BackgroundTask.TaskType.DOWNLOAD) {
                return info;
            }
        }
        return null;
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
            savePersistedTasksAsync();
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
            savePersistedTasksAsync();
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
            savePersistedTasksAsync();
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
            savePersistedTasksAsync();
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
            savePersistedTasksAsync();
        }
    }
    
    /**
     * 暂停指定任务
     */
    public boolean pauseTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo == null || taskInfo.isCompleted() || taskInfo.isCancelled()) {
            return false;
        }
        taskInfo.setPaused(true);
        savePersistedTasksAsync();
        return true;
    }

    /**
     * 恢复指定任务
     */
    public boolean resumeTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo == null || !taskInfo.isPaused()) {
            return false;
        }
        taskInfo.setPaused(false);
        savePersistedTasksAsync();
        return true;
    }

    /**
     * 取消指定任务
     */
    public boolean cancelTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            boolean cancelled = taskInfo.cancel();
            if (cancelled) {
                mActiveTasks.remove(taskId);
                mCompletedTasks.put(taskId, taskInfo);

                // 限制已完成任务的数量
                if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                    String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                    mCompletedTasks.remove(oldestTaskId);
                }
                savePersistedTasksAsync();
            }
            return cancelled;
        }
        return false;
    }

    /**
     * 清除所有已完成的任务
     */
    public void clearCompletedTasks() {
        mCompletedTasks.clear();
        savePersistedTasksAsync();
    }

    /**
     * 取消所有活跃任务并清空所有任务记录
     */
    public void clearAllTasks() {
        List<String> taskIds = new ArrayList<>(mActiveTasks.keySet());
        for (String taskId : taskIds) {
            boolean cancelled = cancelTask(taskId);
            if (!cancelled) {
                BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
                if (taskInfo != null) {
                    taskInfo.setCancelled(true);
                    mCompletedTasks.put(taskId, taskInfo);
                }
            }
        }
        mCompletedTasks.clear();
        savePersistedTasksAsync();
    }

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

    public void removeTask(@NonNull String taskId) {
        mActiveTasks.remove(taskId);
        mCompletedTasks.remove(taskId);
        savePersistedTasksAsync();
    }

    private File createTaskLogFile(@NonNull String taskId) {
        File file = new File(mLogDir, taskId + ".log");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private void savePersistedTasksAsync() {
        mDiskExecutor.submit(this::savePersistedTasks);
    }

    private void savePersistedTasks() {
        synchronized (mPersistLock) {
            try (FileWriter writer = new FileWriter(mStatusFile, false)) {
                JSONObject root = new JSONObject();
                root.put("activeTasks", buildTaskArray(mActiveTasks.values()));
                root.put("completedTasks", buildTaskArray(mCompletedTasks.values()));
                writer.write(root.toString());
            } catch (Exception ignored) {
                // Ignore persistence errors
            }
        }
    }

    private JSONArray buildTaskArray(@NonNull Iterable<BackgroundTaskInfo> taskInfos) throws JSONException {
        JSONArray array = new JSONArray();
        for (BackgroundTaskInfo info : taskInfos) {
            JSONObject object = new JSONObject();
            object.put("taskId", info.getTaskId());
            object.put("taskName", info.getTaskName());
            object.put("taskDescription", info.getTaskDescription());
            object.put("taskType", info.getTaskType().name());
            object.put("uniqueTask", info.isUniqueTask());
            object.put("currentProgress", info.getCurrentProgress());
            object.put("totalProgress", info.getTotalProgress());
            object.put("progressDetail", info.getProgressDetail());
            object.put("startTime", info.getStartTime());
            object.put("isCompleted", info.isCompleted());
            object.put("isCancelled", info.isCancelled());
            object.put("errorMessage", info.getErrorMessage());
            object.put("taskClassName", info.getTaskClassName());
            object.put("taskPersistData", info.getTaskPersistData());
            File logFile = info.getLogFile();
            if (logFile != null) {
                object.put("logFileName", logFile.getName());
            }
            array.put(object);
        }
        return array;
    }

    private void restorePersistedTasks() {
        if (!mStatusFile.exists()) {
            return;
        }

        synchronized (mPersistLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(mStatusFile))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject root = new JSONObject(sb.toString());
                parseTaskArray(root.optJSONArray("activeTasks"), mActiveTasks);
                parseTaskArray(root.optJSONArray("completedTasks"), mCompletedTasks);
            } catch (Exception ignored) {
                // Ignore load errors, start fresh
            }
        }
    }

    private void parseTaskArray(@Nullable JSONArray array, @NonNull Map<String, BackgroundTaskInfo> target) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String taskId = object.optString("taskId", null);
            String taskName = object.optString("taskName", null);
            if (taskId == null || taskName == null) {
                continue;
            }
            String taskDescription = object.optString("taskDescription", null);
            BackgroundTask.TaskType taskType;
            try {
                taskType = BackgroundTask.TaskType.valueOf(object.optString("taskType", BackgroundTask.TaskType.OTHER.name()));
            } catch (IllegalArgumentException e) {
                taskType = BackgroundTask.TaskType.OTHER;
            }
            boolean uniqueTask = object.optBoolean("uniqueTask", false);
            String taskClassName = object.optString("taskClassName", taskName);
            String taskPersistData = object.optString("taskPersistData", null);
            BackgroundTaskInfo taskInfo = new BackgroundTaskInfo(taskId, taskName, taskDescription,
                    null, taskType, uniqueTask, taskClassName, taskPersistData,
                    object.optLong("startTime", System.currentTimeMillis()));
            taskInfo.setCurrentProgress(object.optInt("currentProgress", taskInfo.getCurrentProgress()));
            taskInfo.setTotalProgress(object.optInt("totalProgress", taskInfo.getTotalProgress()));
            taskInfo.setProgressDetail(object.optString("progressDetail", taskInfo.getProgressDetail()));
            taskInfo.setCompleted(object.optBoolean("isCompleted", false));
            taskInfo.setCancelled(object.optBoolean("isCancelled", false));
            taskInfo.setErrorMessage(object.optString("errorMessage", null));
            String logFileName = object.optString("logFileName", null);
            if (logFileName != null) {
                File logFile = new File(mLogDir, logFileName);
                taskInfo.setLogFile(logFile);
                loadTaskLogMessages(taskInfo);
            }
            target.put(taskId, taskInfo);
        }
    }

    private void loadTaskLogMessages(@NonNull BackgroundTaskInfo taskInfo) {
        File logFile = taskInfo.getLogFile();
        if (logFile == null || !logFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                taskInfo.addLogMessage(line);
            }
        } catch (IOException ignored) {
            // Ignore load errors
        }
    }
}
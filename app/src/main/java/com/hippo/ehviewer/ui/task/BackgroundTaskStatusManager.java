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
 * 鍚庡彴浠诲姟鐘舵€佺鐞嗗櫒
 * 鐢ㄤ簬璺熻釜鍜岀鐞嗘墍鏈夊悗鍙颁换鍔＄殑鐘舵€?
 */
public class BackgroundTaskStatusManager {
    private static final String TAG = "BackgroundTaskStatusManager";
    private static final String STATUS_FILE_NAME = "background_tasks.json";
    private static final String LOG_DIR_NAME = "background_task_logs";

    private static BackgroundTaskStatusManager sInstance;

    // 瀛樺偍鎵€鏈夋椿璺冪殑浠诲姟
    private final Map<String, BackgroundTaskInfo> mActiveTasks = new ConcurrentHashMap<>();
    // 瀛樺偍宸插畬鎴愮殑浠诲姟锛堜繚鐣欐渶杩戠殑涓€浜涳級
    private final Map<String, BackgroundTaskInfo> mCompletedTasks = new ConcurrentHashMap<>();
    // 鏈€澶т繚鐣欑殑宸插畬鎴愪换鍔℃暟閲?
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
     * 娣诲姞涓€涓柊鐨勫悗鍙颁换鍔?
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
        taskInfo.setQueued(future != null);
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
     * 鏇存柊浠诲姟杩涘害
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

            // 鏇存柊閫氱煡鏍忚繘搴?
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
     * 鏍囪浠诲姟瀹屾垚
     */
    public void markTaskCompleted(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setQueued(false);
            taskInfo.setCompleted(true);

            // 娣诲姞鍒板凡瀹屾垚浠诲姟鍒楄〃
            mCompletedTasks.put(taskId, taskInfo);

            // 闄愬埗宸插畬鎴愪换鍔＄殑鏁伴噺
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 绉婚櫎鏈€鏃х殑浠诲姟
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
            savePersistedTasksAsync();
        }
    }

    /**
     * 鏍囪浠诲姟鍙栨秷
     */
    public void markTaskCancelled(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setQueued(false);
            taskInfo.setCancelled(true);

            // 娣诲姞鍒板凡瀹屾垚浠诲姟鍒楄〃
            mCompletedTasks.put(taskId, taskInfo);

            // 闄愬埗宸插畬鎴愪换鍔＄殑鏁伴噺
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 绉婚櫎鏈€鏃х殑浠诲姟
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
            savePersistedTasksAsync();
        }
    }

    /**
     * 鏍囪浠诲姟鍑洪敊
     */
    public void markTaskError(@NonNull String taskId, @Nullable String errorMessage) {
        BackgroundTaskInfo taskInfo = mActiveTasks.remove(taskId);
        if (taskInfo != null) {
            taskInfo.setQueued(false);
            taskInfo.setErrorMessage(errorMessage);

            // 娣诲姞鍒板凡瀹屾垚浠诲姟鍒楄〃
            mCompletedTasks.put(taskId, taskInfo);

            // 闄愬埗宸插畬鎴愪换鍔＄殑鏁伴噺
            if (mCompletedTasks.size() > MAX_COMPLETED_TASKS) {
                // 绉婚櫎鏈€鏃х殑浠诲姟
                String oldestTaskId = mCompletedTasks.keySet().iterator().next();
                mCompletedTasks.remove(oldestTaskId);
            }
            savePersistedTasksAsync();
        }
    }

    /**
     * 鏆傚仠鎸囧畾浠诲姟
     */
    public boolean pauseTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo == null || taskInfo.isCompleted() || taskInfo.isCancelled()) {
            return false;
        }
        taskInfo.setQueued(false);
        taskInfo.setPaused(true);
        savePersistedTasksAsync();
        return true;
    }

    public void markTaskRunning(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setQueued(false);
            taskInfo.setPaused(false);
            savePersistedTasksAsync();
        }
    }

    public void markTaskQueued(@NonNull String taskId, @Nullable String detail) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            taskInfo.setQueued(true);
            if (detail != null) {
                taskInfo.setProgressDetail(detail);
            }
            savePersistedTasksAsync();
        }
    }

    /**
     * 鎭㈠鎸囧畾浠诲姟
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
     * 鍙栨秷鎸囧畾浠诲姟
     */
    public boolean cancelTask(@NonNull String taskId) {
        BackgroundTaskInfo taskInfo = mActiveTasks.get(taskId);
        if (taskInfo != null) {
            boolean cancelled = taskInfo.cancel();
            if (cancelled) {
                mActiveTasks.remove(taskId);
                mCompletedTasks.put(taskId, taskInfo);

                // 闄愬埗宸插畬鎴愪换鍔＄殑鏁伴噺
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
     * 娓呴櫎鎵€鏈夊凡瀹屾垚鐨勪换鍔?
     */
    public void clearCompletedTasks() {
        mCompletedTasks.clear();
        savePersistedTasksAsync();
    }

    /**
     * 鍙栨秷鎵€鏈夋椿璺冧换鍔″苟娓呯┖鎵€鏈変换鍔¤褰?
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
     * 鑾峰彇宸插畬鎴愪换鍔″垪琛?
     */
    @NonNull
    public List<BackgroundTaskInfo> getCompletedTasks() {
        return new ArrayList<>(mCompletedTasks.values());
    }

    /**
     * 鑾峰彇鎸囧畾浠诲姟淇℃伅
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
     * 鑾峰彇娲昏穬浠诲姟鏁伴噺
     */
    public int getActiveTaskCount() {
        return mActiveTasks.size();
    }

    /**
     * 鑾峰彇鎵€鏈変换鍔℃暟閲忥紙鍖呮嫭娲昏穬鍜屽凡瀹屾垚鐨勶級
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
            object.put("isQueued", info.isQueued());
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
            taskInfo.setQueued(object.optBoolean("isQueued", false));
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

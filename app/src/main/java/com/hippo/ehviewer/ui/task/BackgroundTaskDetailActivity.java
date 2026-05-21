package com.hippo.ehviewer.ui.task;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.util.ReadableTime;

/**
 * 后台任务详情Activity
 * 显示单个任务的详细信息和进度
 */
public class BackgroundTaskDetailActivity extends EhActivity {
    
    private static final String KEY_TASK_ID = "task_id";
    
    private TextView mTaskNameText;
    private TextView mTaskDescriptionText;
    private TextView mTaskProgressText;
    private TextView mTaskProgressDetailText;
    private TextView mTaskStatusText;
    private TextView mTaskTimeText;
    private TextView mTaskErrorText;
    private TextView mTaskLogText;
    private TextView mTaskLogPathText;
    
    private BackgroundTaskStatusManager mTaskManager;
    private String mTaskId;
    
    public static void start(@NonNull Context context, @NonNull String taskId) {
        Intent intent = new Intent(context, BackgroundTaskDetailActivity.class);
        intent.putExtra(KEY_TASK_ID, taskId);
        context.startActivity(intent);
    }

    @Override
    protected int getThemeResId(int theme) {
        // 使用父类的默认实现，支持自适应主题切换
        return super.getThemeResId(theme);
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_task_detail);
        
        mTaskId = getIntent().getStringExtra(KEY_TASK_ID);
        if (mTaskId == null) {
            finish();
            return;
        }
        
        setupActionBar();
        initViews();
        initTaskManager();
        updateTaskInfo();
    }
    
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.background_task_detail);
        }
    }
    
    private void initViews() {
        mTaskNameText = findViewById(R.id.task_name);
        mTaskDescriptionText = findViewById(R.id.task_description);
        mTaskProgressText = findViewById(R.id.task_progress);
        mTaskProgressDetailText = findViewById(R.id.task_progress_detail);
        mTaskStatusText = findViewById(R.id.task_status);
        mTaskTimeText = findViewById(R.id.task_time);
        mTaskErrorText = findViewById(R.id.task_error);
        mTaskLogText = findViewById(R.id.task_log);
        mTaskLogPathText = findViewById(R.id.task_log_path);
    }
    
    private void initTaskManager() {
        mTaskManager = BackgroundTaskStatusManager.getInstance();
    }
    
    private void updateTaskInfo() {
        if (mTaskManager == null || mTaskId == null) {
            return;
        }
        
        BackgroundTaskInfo taskInfo = mTaskManager.getTaskInfo(mTaskId);
        if (taskInfo == null) {
            finish();
            return;
        }
        
        // 更新任务名称
        if (mTaskNameText != null) {
            mTaskNameText.setText(taskInfo.getTaskName());
        }
        
        // 更新任务描述
        if (mTaskDescriptionText != null) {
            String description = taskInfo.getTaskDescription();
            mTaskDescriptionText.setText(description != null ? description : getString(R.string.no_description));
        }
        
        // 更新进度
        if (mTaskProgressText != null) {
            int percentage = taskInfo.getProgressPercentage();
            if (percentage >= 0) {
                mTaskProgressText.setText(getString(R.string.task_progress_format, 
                    taskInfo.getCurrentProgress(), taskInfo.getTotalProgress(), percentage));
            } else {
                mTaskProgressText.setText(getString(R.string.task_progress_indeterminate));
            }
        }

        // 更新进度详情
        if (mTaskProgressDetailText != null) {
            String detail = taskInfo.getProgressDetail();
            if (detail != null && !detail.isEmpty()) {
                mTaskProgressDetailText.setText(detail);
            } else {
                mTaskProgressDetailText.setText(R.string.no_description);
            }
        }
        
        // 更新状态
        if (mTaskStatusText != null) {
            String status;
            if (taskInfo.isCancelled()) {
                status = getString(R.string.task_status_cancelled);
            } else if (taskInfo.isCompleted()) {
                if (taskInfo.getErrorMessage() != null) {
                    status = getString(R.string.task_status_failed);
                } else {
                    status = getString(R.string.task_status_completed);
                }
            } else {
                status = getString(R.string.task_status_running);
            }
            mTaskStatusText.setText(status);
        }
        
        // 更新运行时间
        if (mTaskTimeText != null) {
            long runningTime = taskInfo.getRunningTime();
            mTaskTimeText.setText(getString(R.string.task_running_time, ReadableTime.getShortTimeInterval(runningTime)));
        }
        
        // 更新错误信息
        if (mTaskErrorText != null) {
            String errorMessage = taskInfo.getErrorMessage();
            if (errorMessage != null) {
                mTaskErrorText.setText(getString(R.string.task_error_format, errorMessage));
                mTaskErrorText.setVisibility(android.view.View.VISIBLE);
            } else {
                mTaskErrorText.setVisibility(android.view.View.GONE);
            }
        }

        // 更新日志
        if (mTaskLogText != null && mTaskLogPathText != null) {
            java.util.List<String> logs = mTaskManager.getTaskLogs(taskInfo.getTaskId());
            if (logs.isEmpty()) {
                mTaskLogText.setText(R.string.task_log_empty);
            } else {
                StringBuilder builder = new StringBuilder();
                for (String log : logs) {
                    builder.append(log).append('\n');
                }
                mTaskLogText.setText(builder.toString());
            }

            java.io.File logFile = taskInfo.getLogFile();
            if (logFile != null) {
                mTaskLogPathText.setText(getString(R.string.task_log_save_to, logFile.getAbsolutePath()));
            } else {
                mTaskLogPathText.setText(R.string.task_log_file_missing);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateTaskInfo();
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
package com.hippo.ehviewer.ui.task;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.ehviewer.ui.MainActivity;

/**
 * 后台任务管理Activity
 * 显示所有正在运行和已完成的后台任务
 */
public class BackgroundTaskActivity extends EhActivity {
    
    private RecyclerView mRecyclerView;
    private BackgroundTaskAdapter mAdapter;
    private BackgroundTaskStatusManager mTaskManager;
    
    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, BackgroundTaskActivity.class);
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
        setContentView(R.layout.activity_background_task);
        
        setupActionBar();
        initViews();
        initTaskManager();
    }
    
    private void setupActionBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            }
        }
    }

    private void navigateBackOrHome() {
        if (!isTaskRoot()) {
            finish();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateBackOrHome();
            return true;
        } else if (item.getItemId() == R.id.action_clear_completed) {
            if (mTaskManager != null && mAdapter != null) {
                mTaskManager.clearCompletedTasks();
                mAdapter.updateData(mTaskManager.getActiveTasks(), mTaskManager.getCompletedTasks());
                Toast.makeText(this, R.string.background_task_cleared_completed, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initViews() {
        mRecyclerView = findViewById(R.id.recycler_view);
        if (mRecyclerView != null) {
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            mAdapter = new BackgroundTaskAdapter(this);
            mRecyclerView.setAdapter(mAdapter);
            
            // 设置点击监听器
            mAdapter.setOnItemClickListener(taskInfo -> {
                // 点击任务项，显示详细信息
                BackgroundTaskDetailActivity.start(this, taskInfo.getTaskId());
            });
        }
    }
    
    private void initTaskManager() {
        mTaskManager = BackgroundTaskStatusManager.getInstance();
        if (mAdapter != null) {
            mAdapter.updateData(mTaskManager.getActiveTasks(), mTaskManager.getCompletedTasks());
        }
        updateToolbarTitle();
    }
    
    private void updateToolbarTitle() {
        int activeCount = mTaskManager != null ? mTaskManager.getActiveTasks().size() : 0;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.background_task_management_with_count, activeCount));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 刷新数据
        if (mAdapter != null && mTaskManager != null) {
            mAdapter.updateData(mTaskManager.getActiveTasks(), mTaskManager.getCompletedTasks());
        }
        updateToolbarTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_background_task, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        navigateBackOrHome();
    }
}
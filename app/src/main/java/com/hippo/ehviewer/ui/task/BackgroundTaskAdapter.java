package com.hippo.ehviewer.ui.task;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.R;
import com.hippo.util.ReadableTime;

import java.util.ArrayList;
import java.util.List;

/**
 * 鍚庡彴浠诲姟鍒楄〃閫傞厤鍣?
 */
public class BackgroundTaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ACTIVE_HEADER = 0;
    private static final int TYPE_ACTIVE_TASK = 1;
    private static final int TYPE_COMPLETED_HEADER = 2;
    private static final int TYPE_COMPLETED_TASK = 3;
    private static final int TYPE_EMPTY = 4;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private List<BackgroundTaskInfo> mActiveTasks = new ArrayList<>();
    private List<BackgroundTaskInfo> mCompletedTasks = new ArrayList<>();
    private OnItemClickListener mOnItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(BackgroundTaskInfo taskInfo);
    }

    public BackgroundTaskAdapter(@NonNull Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public void updateData(@NonNull List<BackgroundTaskInfo> activeTasks,
                          @NonNull List<BackgroundTaskInfo> completedTasks) {
        mActiveTasks = new ArrayList<>(activeTasks);
        mCompletedTasks = new ArrayList<>(completedTasks);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        int activeCount = mActiveTasks.size();
        int completedCount = mCompletedTasks.size();

        if (activeCount == 0 && completedCount == 0) {
            return TYPE_EMPTY;
        }

        if (activeCount > 0) {
            if (position == 0) {
                return TYPE_ACTIVE_HEADER;
            } else if (position <= activeCount) {
                return TYPE_ACTIVE_TASK;
            } else if (position == activeCount + 1) {
                return completedCount > 0 ? TYPE_COMPLETED_HEADER : TYPE_EMPTY;
            } else {
                return TYPE_COMPLETED_TASK;
            }
        } else {
            if (position == 0) {
                return TYPE_COMPLETED_HEADER;
            } else {
                return TYPE_COMPLETED_TASK;
            }
        }
    }

    @Override
    public int getItemCount() {
        int activeCount = mActiveTasks.size();
        int completedCount = mCompletedTasks.size();

        if (activeCount == 0 && completedCount == 0) {
            return 1; // 绌虹姸鎬?
        }

        int count = 0;
        if (activeCount > 0) {
            count += 1; // 娲昏穬浠诲姟鏍囬
            count += activeCount; // 娲昏穬浠诲姟椤?
        }
        if (completedCount > 0) {
            count += 1; // 宸插畬鎴愪换鍔℃爣棰?
            count += completedCount; // 宸插畬鎴愪换鍔￠」
        }

        return count;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_ACTIVE_HEADER:
                return new HeaderViewHolder(mInflater.inflate(R.layout.item_task_header, parent, false),
                    R.string.active_tasks);
            case TYPE_ACTIVE_TASK:
                return new TaskViewHolder(mInflater.inflate(R.layout.item_background_task, parent, false));
            case TYPE_COMPLETED_HEADER:
                return new HeaderViewHolder(mInflater.inflate(R.layout.item_task_header, parent, false),
                    R.string.completed_tasks);
            case TYPE_COMPLETED_TASK:
                return new TaskViewHolder(mInflater.inflate(R.layout.item_background_task, parent, false));
            case TYPE_EMPTY:
                return new EmptyViewHolder(mInflater.inflate(R.layout.item_empty_task, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);

        if (viewType == TYPE_ACTIVE_TASK || viewType == TYPE_COMPLETED_TASK) {
            TaskViewHolder taskHolder = (TaskViewHolder) holder;
            BackgroundTaskInfo taskInfo = getTaskInfoAtPosition(position);
            if (taskInfo != null) {
                taskHolder.bind(taskInfo);
            }
        }
    }

    @Nullable
    private BackgroundTaskInfo getTaskInfoAtPosition(int position) {
        int activeCount = mActiveTasks.size();

        if (activeCount > 0) {
            if (position > 0 && position <= activeCount) {
                return mActiveTasks.get(position - 1);
            } else if (position > activeCount + 1) {
                int completedIndex = position - activeCount - 2;
                if (completedIndex < mCompletedTasks.size()) {
                    return mCompletedTasks.get(completedIndex);
                }
            }
        } else {
            if (position > 0) {
                int completedIndex = position - 1;
                if (completedIndex < mCompletedTasks.size()) {
                    return mCompletedTasks.get(completedIndex);
                }
            }
        }

        return null;
    }

    private class TaskViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView mNameText;
        private final TextView mDescriptionText;
        private final TextView mProgressText;
        private final TextView mTimeText;
        private final TextView mStatusText;
        private final ProgressBar mProgressBar;
        private final View mActionsContainer;
        private final Button mBtnPause;
        private final Button mBtnResume;
        private final Button mBtnCancel;
        private final Button mBtnDelete;

        private BackgroundTaskInfo mTaskInfo;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            mNameText = itemView.findViewById(R.id.task_name);
            mDescriptionText = itemView.findViewById(R.id.task_description);
            mProgressText = itemView.findViewById(R.id.task_progress);
            mTimeText = itemView.findViewById(R.id.task_time);
            mStatusText = itemView.findViewById(R.id.task_status);
            mProgressBar = itemView.findViewById(R.id.progress_bar);
            mActionsContainer = itemView.findViewById(R.id.task_actions);
            mBtnPause = itemView.findViewById(R.id.btn_pause);
            mBtnResume = itemView.findViewById(R.id.btn_resume);
            mBtnCancel = itemView.findViewById(R.id.btn_cancel);
            mBtnDelete = itemView.findViewById(R.id.btn_delete);

            itemView.setOnClickListener(this);

            mBtnPause.setOnClickListener(v -> handlePause());
            mBtnResume.setOnClickListener(v -> handleResume());
            mBtnCancel.setOnClickListener(v -> handleCancel());
            mBtnDelete.setOnClickListener(v -> handleDelete());
        }

        public void bind(@NonNull BackgroundTaskInfo taskInfo) {
            mTaskInfo = taskInfo;

            // 浠诲姟鍚嶇О
            mNameText.setText(taskInfo.getTaskName());

            // 浠诲姟鎻忚堪
            String description = taskInfo.getTaskDescription();
            mDescriptionText.setText(description != null ? description : mContext.getString(R.string.no_description));

            // 杩涘害
            int percentage = taskInfo.getProgressPercentage();
            if (percentage >= 0) {
                String detail = taskInfo.getProgressDetail();
                String progressText = mContext.getString(R.string.task_progress_format,
                    taskInfo.getCurrentProgress(), taskInfo.getTotalProgress(), percentage);
                if (detail != null && !detail.isEmpty()) {
                    progressText = progressText + " - " + detail;
                }
                mProgressText.setText(progressText);
                mProgressBar.setMax(100);
                mProgressBar.setProgress(percentage);
                mProgressBar.setIndeterminate(false);
            } else {
                mProgressText.setText(R.string.task_progress_indeterminate);
                mProgressBar.setIndeterminate(true);
            }

            // 杩愯鏃堕棿
            long runningTime = taskInfo.getRunningTime();
            mTimeText.setText(mContext.getString(R.string.task_running_time, ReadableTime.getShortTimeInterval(runningTime)));

            // 鐘舵€?
            String status;
            if (taskInfo.isCancelled()) {
                status = mContext.getString(R.string.task_status_cancelled);
            } else if (taskInfo.isCompleted()) {
                if (taskInfo.getErrorMessage() != null) {
                    status = mContext.getString(R.string.task_status_failed);
                } else {
                    status = mContext.getString(R.string.task_status_completed);
                }
            } else if (taskInfo.isQueued()) {
                status = mContext.getString(R.string.task_status_pending);
            } else if (taskInfo.isPaused()) {
                status = mContext.getString(R.string.task_status_paused);
            } else {
                status = mContext.getString(R.string.task_status_running);
            }
            mStatusText.setText(status);

            // 鏍规嵁浠诲姟鐘舵€佹樉绀?闅愯棌鎸夐挳
            updateActionButtonVisibility();
        }

        private void updateActionButtonVisibility() {
            if (mTaskInfo == null) {
                mActionsContainer.setVisibility(View.GONE);
                return;
            }

            boolean isActive = !mTaskInfo.isCompleted() && !mTaskInfo.isCancelled();
            boolean isPaused = mTaskInfo.isPaused();
            boolean isCompleted = mTaskInfo.isCompleted() || mTaskInfo.isCancelled();

            if (isActive) {
                mActionsContainer.setVisibility(View.VISIBLE);
                if (isPaused) {
                    mBtnPause.setVisibility(View.GONE);
                    mBtnResume.setVisibility(View.VISIBLE);
                    mBtnCancel.setVisibility(View.VISIBLE);
                } else {
                    mBtnPause.setVisibility(View.VISIBLE);
                    mBtnResume.setVisibility(View.GONE);
                    mBtnCancel.setVisibility(View.VISIBLE);
                }
                mBtnDelete.setVisibility(View.GONE);
            } else if (isCompleted) {
                mActionsContainer.setVisibility(View.VISIBLE);
                mBtnPause.setVisibility(View.GONE);
                mBtnResume.setVisibility(View.GONE);
                mBtnCancel.setVisibility(View.GONE);
                mBtnDelete.setVisibility(View.VISIBLE);
            } else {
                mActionsContainer.setVisibility(View.GONE);
            }
        }

        private void handlePause() {
            if (mTaskInfo == null) return;
            BackgroundTaskManager.getInstance().pauseTask(mTaskInfo.getTaskId());
            Toast.makeText(mContext, R.string.task_paused, Toast.LENGTH_SHORT).show();
            refreshAdapter();
        }

        private void handleResume() {
            if (mTaskInfo == null) return;
            BackgroundTaskManager.getInstance().resumeTask(mTaskInfo.getTaskId());
            Toast.makeText(mContext, R.string.task_resumed, Toast.LENGTH_SHORT).show();
            refreshAdapter();
        }

        private void handleCancel() {
            if (mTaskInfo == null) return;
            BackgroundTaskManager.getInstance().cancelTask(mTaskInfo.getTaskId());
            Toast.makeText(mContext, R.string.task_cancelling, Toast.LENGTH_SHORT).show();
            refreshAdapter();
        }

        private void handleDelete() {
            if (mTaskInfo == null) return;
            BackgroundTaskManager.getInstance().removeTask(mTaskInfo.getTaskId());
            refreshAdapter();
        }

        private void refreshAdapter() {
            BackgroundTaskStatusManager manager = BackgroundTaskStatusManager.getInstance();
            updateData(manager.getActiveTasks(), manager.getCompletedTasks());
        }

        @Override
        public void onClick(View v) {
            if (mOnItemClickListener != null && mTaskInfo != null) {
                mOnItemClickListener.onItemClick(mTaskInfo);
            }
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitleText;

        public HeaderViewHolder(@NonNull View itemView, int titleResId) {
            super(itemView);
            mTitleText = itemView.findViewById(R.id.header_title);
            mTitleText.setText(itemView.getContext().getString(titleResId));
        }
    }

    private static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

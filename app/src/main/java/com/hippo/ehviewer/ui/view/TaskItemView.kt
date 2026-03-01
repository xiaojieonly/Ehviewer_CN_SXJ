package com.hippo.ehviewer.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.hippo.ehviewer.R
import com.hippo.ehviewer.task.TaskExecutionInfo
import com.hippo.ehviewer.task.TaskState
import java.text.SimpleDateFormat
import java.util.*

/**
 * 任务项显示View
 * 用于在列表中显示单个任务的信息
 */
class TaskItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val descriptionView: TextView
    private val progressView: ProgressBar
    private val progressTextView: TextView
    private val stateView: TextView
    private val timeView: TextView
    private val pauseButton: ImageButton
    private val resumeButton: ImageButton
    private val stopButton: ImageButton

    private var currentTaskInfo: TaskExecutionInfo? = null
    private var onTaskAction: ((taskId: String, action: TaskAction) -> Unit)? = null

    enum class TaskAction {
        PAUSE,
        RESUME,
        STOP,
        SHOW_DETAIL
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_task_item, this, true)

        titleView = findViewById(R.id.task_title)
        descriptionView = findViewById(R.id.task_description)
        progressView = findViewById(R.id.task_progress)
        progressTextView = findViewById(R.id.task_progress_text)
        stateView = findViewById(R.id.task_state)
        timeView = findViewById(R.id.task_time)
        pauseButton = findViewById(R.id.btn_pause)
        resumeButton = findViewById(R.id.btn_resume)
        stopButton = findViewById(R.id.btn_stop)

        pauseButton.setOnClickListener {
            currentTaskInfo?.let {
                onTaskAction?.invoke(it.taskId, TaskAction.PAUSE)
            }
        }

        resumeButton.setOnClickListener {
            currentTaskInfo?.let {
                onTaskAction?.invoke(it.taskId, TaskAction.RESUME)
            }
        }

        stopButton.setOnClickListener {
            currentTaskInfo?.let {
                onTaskAction?.invoke(it.taskId, TaskAction.STOP)
            }
        }

        setOnClickListener {
            currentTaskInfo?.let {
                onTaskAction?.invoke(it.taskId, TaskAction.SHOW_DETAIL)
            }
        }
    }

    fun setTaskInfo(
        info: TaskExecutionInfo,
        onTaskAction: ((taskId: String, action: TaskAction) -> Unit)?
    ) {
        currentTaskInfo = info
        this.onTaskAction = onTaskAction

        // 更新标题和描述
        titleView.text = info.task.getTaskName()
        descriptionView.text = info.task.getTaskDescription()

        // 更新状态
        updateTaskState(info)

        // 更新进度
        updateProgress(info)

        // 更新时间
        updateTime(info)

        // 更新按钮状态
        updateButtons(info)
    }

    private fun updateTaskState(info: TaskExecutionInfo) {
        val stateText = when (info.state) {
            TaskState.PENDING -> context.getString(R.string.task_state_pending)
            TaskState.RUNNING -> context.getString(R.string.task_state_running)
            TaskState.PAUSED -> context.getString(R.string.task_state_paused)
            TaskState.COMPLETED -> context.getString(R.string.task_state_completed)
            TaskState.FAILED -> context.getString(R.string.task_state_failed)
            TaskState.CANCELLED -> context.getString(R.string.task_state_cancelled)
        }
        stateView.text = stateText
        stateView.setTextColor(getStateColor(info.state))
    }

    private fun getStateColor(state: TaskState): Int {
        return when (state) {
            TaskState.RUNNING -> context.resources.getColor(R.color.task_state_running, null)
            TaskState.COMPLETED -> context.resources.getColor(R.color.task_state_completed, null)
            TaskState.FAILED -> context.resources.getColor(R.color.task_state_failed, null)
            TaskState.PAUSED -> context.resources.getColor(R.color.task_state_paused, null)
            else -> context.resources.getColor(R.color.task_state_pending, null)
        }
    }

    private fun updateProgress(info: TaskExecutionInfo) {
        val progress = info.task.getProgress()
        if (progress >= 0) {
            progressView.visibility = VISIBLE
            progressView.progress = progress
            val detail = info.task.getProgressDetail()
            progressTextView.text = if (detail != null) {
                "$progress% - $detail"
            } else {
                "$progress%"
            }
        } else {
            progressView.visibility = GONE
            progressTextView.text = context.getString(R.string.task_progress_unknown)
        }
    }

    private fun updateTime(info: TaskExecutionInfo) {
        val timeText = when {
            info.endTime != null -> {
                // 任务已完成或已失败，显示耗时
                val duration = (info.endTime!! - info.startTime) / 1000
                formatDuration(duration)
            }
            else -> {
                // 任务未完成，显示开始时间
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                dateFormat.format(Date(info.startTime))
            }
        }
        timeView.text = timeText
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m${seconds % 60}s"
            else -> "${seconds / 3600}h${(seconds % 3600) / 60}m"
        }
    }

    private fun updateButtons(info: TaskExecutionInfo) {
        when (info.state) {
            TaskState.RUNNING -> {
                if (info.task.isPausable()) {
                    pauseButton.visibility = VISIBLE
                    resumeButton.visibility = GONE
                } else {
                    pauseButton.visibility = GONE
                    resumeButton.visibility = GONE
                }
                stopButton.visibility = VISIBLE
            }
            TaskState.PAUSED -> {
                pauseButton.visibility = GONE
                resumeButton.visibility = VISIBLE
                stopButton.visibility = VISIBLE
            }
            else -> {
                pauseButton.visibility = GONE
                resumeButton.visibility = GONE
                stopButton.visibility = GONE
            }
        }
    }
}

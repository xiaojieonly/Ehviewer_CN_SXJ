package com.hippo.ehviewer.ui.progress

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.hippo.ehviewer.R
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.lib.yorozuya.SimpleHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一的后台任务对话框管理器
 * 管理所有后台任务的进度对话框，避免重复创建
 */
object ProgressDialogManager {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeDialogs = ConcurrentHashMap<String, TaskDialog>()
    private val taskListeners = ConcurrentHashMap<String, BackgroundTask.ProgressListener>()
    
    /**
     * 任务对话框数据类
     */
    private data class TaskDialog(
        val taskId: String,
        val dialog: ProgressDialog,
        var isShown: Boolean = true,
        var context: Context?
    )
    
    /**
     * 显示或更新任务进度对话框
     * @param context 上下文
     * @param task 后台任务
     * @return 如果是新建对话框返回true，如果是更新现有对话框返回false
     */
    @MainThread
    @JvmStatic
    fun showOrUpdateDialog(context: Context, task: BackgroundTask): Boolean {
        val taskId = task.getTaskId()
        val existingDialog = activeDialogs[taskId]
        
        return if (existingDialog != null && existingDialog.isShown) {
            // 更新现有对话框
            updateExistingDialog(existingDialog, task)
            false
        } else {
            // 创建新对话框
            createNewDialog(context, task)
            true
        }
    }
    
    /**
     * 创建新的进度对话框
     */
    @MainThread
    private fun createNewDialog(context: Context, task: BackgroundTask) {
        val taskId = task.getTaskId()
        val progressDialog = ProgressDialog(context).apply {
            setTitle(task.getTaskName())
            setMessage(task.getTaskDescription() ?: "Loading...")
            setIndeterminate(task.getProgress() == -1)
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            setMax(100)
            setProgress(if (task.getProgress() == -1) 0 else task.getProgress())
            
            // 添加后台运行按钮
            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.background_processing)) { dialog, which ->
                dismissDialog(taskId, false) // 不移除监听器，继续后台运行
            }
        }
        
        val taskDialog = TaskDialog(taskId, progressDialog, true, context)
        activeDialogs[taskId] = taskDialog
        
        // 设置进度监听器
        val listener = createProgressListener(taskId, task)
        taskListeners[taskId] = listener
        task.setProgressListener(listener)
        
        progressDialog.show()
    }
    
    /**
     * 更新现有对话框
     */
    @MainThread
    private fun updateExistingDialog(taskDialog: TaskDialog, task: BackgroundTask) {
        val dialog = taskDialog.dialog
        val progress = task.getProgress()
        val detail = task.getProgressDetail()
        
        dialog.apply {
            if (progress != -1) {
                setIndeterminate(false)
                setMax(100)
                setProgress(progress)
            }
            
            val message = if (!detail.isNullOrEmpty()) {
                "${task.getTaskDescription()}\n$detail"
            } else {
                task.getTaskDescription() ?: ""
            }
            setMessage(message)
        }
    }
    
    /**
     * 创建进度监听器
     */
    @MainThread
    private fun createProgressListener(taskId: String, task: BackgroundTask): BackgroundTask.ProgressListener {
        return object : BackgroundTask.ProgressListener {
            override fun onProgressChanged(progress: Int, detail: String?) {
                mainHandler.post {
                    activeDialogs[taskId]?.let { taskDialog ->
                        if (taskDialog.isShown) {
                            taskDialog.dialog.apply {
                                if (progress != -1) {
                                    setIndeterminate(false)
                                    setMax(100)
                                    setProgress(progress)
                                }
                                
                                val message = if (!detail.isNullOrEmpty()) {
                                    "${task.getTaskDescription()}\n$detail"
                                } else {
                                    task.getTaskDescription() ?: ""
                                }
                                setMessage(message)
                            }
                        }
                    }
                }
            }
            
            override fun onCompleted() {
                mainHandler.post {
                    dismissDialog(taskId, true)
                    activeDialogs[taskId]?.context?.let { context ->
                        Toast.makeText(context, R.string.task_completed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            override fun onError(error: Throwable) {
                mainHandler.post {
                    dismissDialog(taskId, true)
                    activeDialogs[taskId]?.context?.let { context ->
                        Toast.makeText(context, context.getString(R.string.task_error_format, error.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    /**
     * 关闭对话框
     * @param taskId 任务ID
     * @param removeListener 是否同时移除监听器
     */
    @MainThread
    fun dismissDialog(taskId: String, removeListener: Boolean = true) {
        activeDialogs[taskId]?.let { taskDialog ->
            if (taskDialog.isShown) {
                taskDialog.dialog.dismiss()
                taskDialog.isShown = false
            }
            
            if (removeListener) {
                taskListeners.remove(taskId)
                activeDialogs.remove(taskId)
            }
        }
    }
    
    /**
     * 检查任务对话框是否显示
     */
    @MainThread
    fun isDialogShown(taskId: String): Boolean {
        return activeDialogs[taskId]?.isShown == true
    }
    
    /**
     * 获取当前活动的任务数量
     */
    @MainThread
    fun getActiveTaskCount(): Int {
        return activeDialogs.size
    }
    
    /**
     * 清理所有对话框
     */
    @MainThread
    @JvmStatic
    fun dismissAll() {
        val taskIds = activeDialogs.keys.toList()
        taskIds.forEach { taskId ->
            dismissDialog(taskId, true)
        }
    }
    
    /**
     * 获取任务状态描述
     */
    fun getTaskStateText(state: TaskState): Int {
        return when (state) {
            TaskState.PENDING -> 0 // R.string.task_status_pending
            TaskState.RUNNING -> 0 // R.string.task_status_running
            TaskState.PAUSED -> 0 // R.string.task_status_paused
            TaskState.COMPLETED -> 0 // R.string.task_status_completed
            TaskState.FAILED -> 0 // R.string.task_status_failed
            TaskState.CANCELLED -> 0 // R.string.task_status_cancelled
        }
    }
}
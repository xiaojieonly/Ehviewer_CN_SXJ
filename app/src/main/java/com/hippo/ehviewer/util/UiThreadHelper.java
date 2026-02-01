package com.hippo.ehviewer.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhApplication;
import com.hippo.lib.yorozuya.SimpleHandler;

/**
 * UI线程工具类
 * 用于确保UI操作在主线程执行，避免跨线程更新UI的问题
 */
public class UiThreadHelper {
    
    private static final String TAG = "UiThreadHelper";
    
    /**
     * 确保在主线程执行任务
     * @param task 要执行的任务
     */
    public static void runOnUiThread(@NonNull Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 已经在主线程，直接执行
            task.run();
        } else {
            // 不在主线程，切换到主线程执行
            SimpleHandler.getInstance().post(task);
        }
    }
    
    /**
     * 在主线程延迟执行任务
     * @param task 要执行的任务
     * @param delayMillis 延迟时间（毫秒）
     */
    public static void runOnUiThreadDelayed(@NonNull Runnable task, long delayMillis) {
        SimpleHandler.getInstance().postDelayed(task, delayMillis);
    }
    
    /**
     * 安全地设置TextView文本
     * @param textView TextView实例
     * @param text 要设置的文本
     */
    public static void setTextSafely(@Nullable TextView textView, @Nullable CharSequence text) {
        if (textView == null) {
            return;
        }
        
        runOnUiThread(() -> {
            if (textView != null) {
                textView.setText(text);
            }
        });
    }
    
    /**
     * 安全地设置View可见性
     * @param view View实例
     * @param visibility 可见性（View.VISIBLE、View.GONE、View.INVISIBLE）
     */
    public static void setVisibilitySafely(@Nullable View view, int visibility) {
        if (view == null) {
            return;
        }
        
        runOnUiThread(() -> {
            if (view != null) {
                view.setVisibility(visibility);
            }
        });
    }
    
    /**
     * 安全地显示Toast
     * @param context 上下文
     * @param text 要显示的文本
     * @param duration 显示时长
     */
    public static void showToastSafely(@Nullable Context context, @Nullable CharSequence text, int duration) {
        if (context == null || text == null) {
            return;
        }
        
        runOnUiThread(() -> {
            try {
                Toast.makeText(context, text, duration).show();
            } catch (Exception e) {
                // Toast显示失败，忽略
            }
        });
    }
    
    /**
     * 安全地显示Toast
     * @param text 要显示的文本
     * @param duration 显示时长
     */
    public static void showToastSafely(@Nullable CharSequence text, int duration) {
        showToastSafely(EhApplication.getInstance(), text, duration);
    }
    
    /**
     * 安全地显示Toast
     * @param context 上下文
     * @param resId 文本资源ID
     * @param duration 显示时长
     */
    public static void showToastSafely(@Nullable Context context, int resId, int duration) {
        if (context == null) {
            return;
        }
        
        runOnUiThread(() -> {
            try {
                Toast.makeText(context, resId, duration).show();
            } catch (Exception e) {
                // Toast显示失败，忽略
            }
        });
    }
    
    /**
     * 安全地显示Toast
     * @param resId 文本资源ID
     * @param duration 显示时长
     */
    public static void showToastSafely(int resId, int duration) {
        showToastSafely(EhApplication.getInstance(), resId, duration);
    }
    
    /**
     * 安全地执行View操作
     * @param view View实例
     * @param action 要执行的操作
     */
    public static void runOnViewSafely(@Nullable View view, @NonNull ViewAction action) {
        if (view == null) {
            return;
        }
        
        runOnUiThread(() -> {
            if (view != null) {
                action.execute(view);
            }
        });
    }
    
    /**
     * 检查当前是否在主线程
     * @return 是否在主线程
     */
    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
    
    /**
     * 确保在主线程，否则抛出异常
     * @throws IllegalStateException 如果不在主线程
     */
    public static void ensureMainThread() {
        if (!isMainThread()) {
            throw new IllegalStateException("Must be called on the main thread");
        }
    }
    
    /**
     * View操作接口
     */
    public interface ViewAction {
        /**
         * 执行操作
         * @param view View实例
         */
        void execute(@NonNull View view);
    }
    
    /**
     * 安全的Runnable包装器
     * 确保Runnable在主线程执行
     */
    public static class SafeRunnable implements Runnable {
        private final Runnable mTask;
        
        public SafeRunnable(@NonNull Runnable task) {
            mTask = task;
        }
        
        @Override
        public void run() {
            if (isMainThread()) {
                mTask.run();
            } else {
                runOnUiThread(mTask);
            }
        }
    }
    
    /**
     * 创建安全的Runnable
     * @param task 原始任务
     * @return 安全的Runnable
     */
    public static SafeRunnable safeRunnable(@NonNull Runnable task) {
        return new SafeRunnable(task);
    }
}
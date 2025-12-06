/*
 * Copyright 2025 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.hippo.lib.yorozuya.thread.PriorityThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 统一的线程池管理器
 * 提供各种场景下的线程池，避免创建过多的线程，提高性能和资源利用率
 */
public class ExecutorManager {
    
    private static final String TAG = "ExecutorManager";
    
    // 主线程Handler
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    
    // IO密集型线程池（文件读写、数据库操作等）- 复用现有的IoThreadPoolExecutor
    private static final ExecutorService IO_EXECUTOR = IoThreadPoolExecutor.getInstance();
    
    // 网络请求线程池（网络连接、Socket等）
    private static final ExecutorService NETWORK_EXECUTOR = new ThreadPoolExecutor(
        2, // 核心线程数
        8, // 最大线程数
        60L, TimeUnit.SECONDS, // 空闲线程存活时间
        new LinkedBlockingQueue<>(100), // 任务队列
        new PriorityThreadFactory("Network", android.os.Process.THREAD_PRIORITY_BACKGROUND),
        new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程执行
    );
    
    // 计算密集型线程池（图片处理、数据解析等）
    private static final ExecutorService COMPUTATION_EXECUTOR = new ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(), // 核心线程数 = CPU核心数
        Runtime.getRuntime().availableProcessors() * 2, // 最大线程数
        30L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(50),
        new PriorityThreadFactory("Computation", android.os.Process.THREAD_PRIORITY_DEFAULT),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
    
    // 后台任务线程池（下载管理、批量操作等）
    private static final ExecutorService BACKGROUND_EXECUTOR = new ThreadPoolExecutor(
        1, // 核心线程数
        4, // 最大线程数
        120L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(200),
        new PriorityThreadFactory("Background", android.os.Process.THREAD_PRIORITY_BACKGROUND),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
    
    // 定时任务线程池
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = 
        Executors.newScheduledThreadPool(2, 
            new PriorityThreadFactory("Scheduled", android.os.Process.THREAD_PRIORITY_BACKGROUND));
    
    // 单线程串行执行器（用于需要顺序执行的任务）
    private static final ExecutorService SERIAL_EXECUTOR = 
        Executors.newSingleThreadExecutor(
            new PriorityThreadFactory("Serial", android.os.Process.THREAD_PRIORITY_BACKGROUND));
    
    private ExecutorManager() {
        // 私有构造函数，防止实例化
    }
    
    /**
     * 获取IO线程池（文件操作、数据库操作）
     */
    public static ExecutorService getIoExecutor() {
        return IO_EXECUTOR;
    }
    
    /**
     * 获取网络线程池（网络请求、Socket连接）
     */
    public static ExecutorService getNetworkExecutor() {
        return NETWORK_EXECUTOR;
    }
    
    /**
     * 获取计算线程池（CPU密集型任务）
     */
    public static ExecutorService getComputationExecutor() {
        return COMPUTATION_EXECUTOR;
    }
    
    /**
     * 获取后台线程池（长时间运行的后台任务）
     */
    public static ExecutorService getBackgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }
    
    /**
     * 获取定时任务线程池
     */
    public static ScheduledExecutorService getScheduledExecutor() {
        return SCHEDULED_EXECUTOR;
    }
    
    /**
     * 获取串行执行器（任务按顺序执行）
     */
    public static ExecutorService getSerialExecutor() {
        return SERIAL_EXECUTOR;
    }
    
    /**
     * 在主线程执行任务
     */
    public static void runOnMainThread(@NonNull Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }
    
    /**
     * 在主线程延迟执行任务
     */
    public static void runOnMainThreadDelayed(@NonNull Runnable runnable, long delayMillis) {
        MAIN_HANDLER.postDelayed(runnable, delayMillis);
    }
    
    /**
     * 取消主线程任务
     */
    public static void removeMainThreadCallbacks(@NonNull Runnable runnable) {
        MAIN_HANDLER.removeCallbacks(runnable);
    }
    
    /**
     * 获取主线程Handler
     */
    public static Handler getMainHandler() {
        return MAIN_HANDLER;
    }
    
    /**
     * 关闭所有线程池（应用退出时调用）
     */
    public static void shutdown() {
        try {
            NETWORK_EXECUTOR.shutdown();
            COMPUTATION_EXECUTOR.shutdown();
            BACKGROUND_EXECUTOR.shutdown();
            SCHEDULED_EXECUTOR.shutdown();
            SERIAL_EXECUTOR.shutdown();
            // IO_EXECUTOR 不关闭，因为是全局共享的
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error shutting down executors", e);
        }
    }
    
    /**
     * 立即关闭所有线程池（强制关闭）
     */
    public static void shutdownNow() {
        try {
            NETWORK_EXECUTOR.shutdownNow();
            COMPUTATION_EXECUTOR.shutdownNow();
            BACKGROUND_EXECUTOR.shutdownNow();
            SCHEDULED_EXECUTOR.shutdownNow();
            SERIAL_EXECUTOR.shutdownNow();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error shutting down executors forcefully", e);
        }
    }
}

/*
 * Copyright 2025 EhViewer Contributors
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

package com.hippo.ehviewer.transfer;

import android.app.Service;
import android.content.Intent;
import android.app.PendingIntent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.transfer.core.TransferServerManager;
import com.hippo.ehviewer.transfer.data.ClientInfo;
import com.hippo.ehviewer.ui.transfer.TransferActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * WIFI 数据传输服务
 * 支持设备发现、文件传输、任务管理等功能
 */
public class TransferService extends Service implements Observer {

    private static final String TAG = "TransferService";
    private static final int NOTIFICATION_ID = 10086;
    private static final String NOTIFICATION_CHANNEL_ID = "transfer_service";

    private final IBinder binder = new TransferBinder();
    private Handler mainHandler;
    private TransferServerManager serverManager;
    private List<ClientInfo> connectedClients = new ArrayList<>();
    private ServiceCallback callback;
    private boolean isRunning = false;

    public interface ServiceCallback {
        void onClientsChanged(List<ClientInfo> clients);
        void onTransferStatusChanged(String message);
        void onError(String error);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TransferService created");
        mainHandler = new Handler(Looper.getMainLooper());
        serverManager = new TransferServerManager(this);
        serverManager.addObserver(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TransferService started");
        
        if (!isRunning) {
            startServer();
            showForegroundNotification();
            isRunning = true;
        }
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TransferService destroyed");
        stopServer();
        isRunning = false;
    }

    /**
     * 启动传输服务器
     */
    private void startServer() {
        mainHandler.post(() -> {
            try {
                serverManager.start();
                notifyStatus("传输服务已启动");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start server", e);
                notifyError("启动服务失败: " + e.getMessage());
            }
        });
    }

    /**
     * 停止传输服务器
     */
    private void stopServer() {
        mainHandler.post(() -> {
            try {
                serverManager.stop();
                notifyStatus("传输服务已停止");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop server", e);
            }
        });
    }

    /**
     * 显示前台服务通知
     */
    private void showForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("EhViewer 传输服务")
                .setContentText("传输服务正在运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setAutoCancel(false)
                .setOngoing(true);

        // 添加停止按钮
        builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止服务",
                createStopIntent()
        );

        // 添加打开应用按钮
        Intent openIntent = new Intent(this, TransferActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    /**
     * 创建停止服务的Intent
     */
    private PendingIntent createStopIntent() {
        Intent stopIntent = new Intent(this, TransferService.class);
        stopIntent.setAction("STOP_SERVICE");
        
        return PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /**
     * 创建通知频道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager =
                    getSystemService(android.app.NotificationManager.class);
            if (notificationManager != null) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "传输服务",
                        android.app.NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("EhViewer 数据传输服务");
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 更新连接的客户端列表
     */
    public void updateClients(List<ClientInfo> clients) {
        this.connectedClients = new ArrayList<>(clients);
        if (callback != null) {
            mainHandler.post(() -> callback.onClientsChanged(connectedClients));
        }
    }

    /**
     * 获取连接的客户端列表
     */
    public List<ClientInfo> getConnectedClients() {
        return new ArrayList<>(connectedClients);
    }

    /**
     * 注册服务回调
     */
    public void registerCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    /**
     * 取消注册服务回调
     */
    public void unregisterCallback() {
        this.callback = null;
    }

    /**
     * 获取服务器管理器
     */
    public TransferServerManager getServerManager() {
        return serverManager;
    }

    /**
     * 通知状态变化
     */
    private void notifyStatus(String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTransferStatusChanged(message));
        }
    }

    /**
     * 通知错误
     */
    private void notifyError(String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof String) {
            notifyStatus((String) arg);
        }
    }

    /**
     * 服务Binder，用于Activity与Service的通信
     */
    public class TransferBinder extends Binder {
        public TransferService getService() {
            return TransferService.this;
        }
    }
}

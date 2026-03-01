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

package com.hippo.ehviewer.ui.transfer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.transfer.TransferService;
import com.hippo.ehviewer.transfer.data.ClientInfo;
import com.hippo.ehviewer.ui.ToolbarActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据传输服务界面
 * 功能:
 * - 启动/停止传输服务
 * - 显示已连接的客户端列表
 * - 管理传输任务
 * - 实时显示传输进度
 */
public class TransferActivity extends ToolbarActivity implements TransferService.ServiceCallback {

    private static final String TAG = "TransferActivity";

    private TransferService transferService;
    private ServiceConnection serviceConnection;
    private boolean isBound = false;

    // UI components
    private Button startButton;
    private Button stopButton;
    private Button sendBookmarksButton;
    private Button sendDownloadsButton;
    private Button sendFavoritesButton;
    private Button sendExportButton;
    private ProgressBar loadingBar;
    private TextView statusText;
    private TextView clientCountText;
    private RecyclerView clientListView;
    private ClientListAdapter clientAdapter;
    private List<ClientInfo> clientList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        
        initializeUI();
        setupServiceConnection();
        bindTransferService();
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        startButton = findViewById(R.id.transfer_start_button);
        stopButton = findViewById(R.id.transfer_stop_button);
        sendBookmarksButton = findViewById(R.id.transfer_client_send_bookmarks);
        sendDownloadsButton = findViewById(R.id.transfer_client_send_downloads);
        sendFavoritesButton = findViewById(R.id.transfer_client_send_favorites);
        sendExportButton = findViewById(R.id.transfer_client_send_export);
        loadingBar = findViewById(R.id.transfer_loading_bar);
        statusText = findViewById(R.id.transfer_status_text);
        clientCountText = findViewById(R.id.transfer_client_count);
        clientListView = findViewById(R.id.transfer_client_list);

        clientList = new ArrayList<>();
        clientAdapter = new ClientListAdapter(this, clientList);
        clientListView.setLayoutManager(new LinearLayoutManager(this));
        clientListView.setAdapter(clientAdapter);

        // 设置按钮监听
        startButton.setOnClickListener(v -> startTransferService());
        stopButton.setOnClickListener(v -> stopTransferService());
        sendBookmarksButton.setOnClickListener(v -> requestClientPush("bookmarks"));
        sendDownloadsButton.setOnClickListener(v -> requestClientPush("downloads"));
        sendFavoritesButton.setOnClickListener(v -> requestClientPush("favorites"));
        sendExportButton.setOnClickListener(v -> requestClientPush("export"));

        updateUI();
    }

    /**
     * 请求客户端推送指定类型数据
     */
    private void requestClientPush(String type) {
        if (!isServiceRunning() || transferService == null) {
            Toast.makeText(this, getString(R.string.transfer_service_not_running), Toast.LENGTH_SHORT).show();
            return;
        }
        String taskId = transferService.getServerManager().createClientPushTask(type);
        Toast.makeText(this, getString(R.string.transfer_client_push_requested, type, taskId), Toast.LENGTH_SHORT).show();
    }

    /**
     * 设置Service连接
     */
    private void setupServiceConnection() {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
                transferService = binder.getService();
                isBound = true;
                transferService.registerCallback(TransferActivity.this);
                
                Log.d(TAG, "Service bound");
                updateUI();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isBound = false;
                Log.d(TAG, "Service disconnected");
            }
        };
    }

    /**
     * 绑定Transfer服务
     */
    private void bindTransferService() {
        Intent intent = new Intent(this, TransferService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 启动传输服务
     */
    private void startTransferService() {
        Intent intent = new Intent(this, TransferService.class);
        startService(intent);
        
        if (isBound && transferService != null) {
            transferService.registerCallback(this);
        }
        
        Toast.makeText(this, "传输服务已启动", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    /**
     * 停止传输服务
     */
    private void stopTransferService() {
        Intent intent = new Intent(this, TransferService.class);
        stopService(intent);
        
        if (isBound && transferService != null) {
            transferService.unregisterCallback();
        }
        
        Toast.makeText(this, "传输服务已停止", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    /**
     * 更新UI状态
     */
    private void updateUI() {
        boolean isServiceRunning = isServiceRunning();
        
        startButton.setEnabled(!isServiceRunning);
        stopButton.setEnabled(isServiceRunning);
        
        if (isServiceRunning && isBound && transferService != null) {
            List<ClientInfo> clients = transferService.getConnectedClients();
            updateClientList(clients);
            clientCountText.setText("已连接客户端: " + clients.size());
            statusText.setText("传输服务运行中");
            loadingBar.setVisibility(View.GONE);
        } else {
            clientCountText.setText("已连接客户端: 0");
            statusText.setText("传输服务未运行");
            loadingBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新客户端列表
     */
    private void updateClientList(List<ClientInfo> clients) {
        clientList.clear();
        clientList.addAll(clients);
        clientAdapter.notifyDataSetChanged();
    }

    /**
     * 检查服务是否运行
     */
    private boolean isServiceRunning() {
        if (!isBound || transferService == null) {
            return false;
        }
        return transferService.getServerManager().isRunning();
    }

    @Override
    public void onClientsChanged(List<ClientInfo> clients) {
        runOnUiThread(() -> {
            clientList.clear();
            clientList.addAll(clients);
            clientAdapter.notifyDataSetChanged();
            clientCountText.setText("已连接客户端: " + clients.size());
        });
    }

    @Override
    public void onTransferStatusChanged(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Toast.makeText(TransferActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            statusText.setText("错误: " + error);
            Toast.makeText(TransferActivity.this, error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound && transferService != null) {
            transferService.unregisterCallback();
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    /**
     * 客户端列表适配器
     */
    public static class ClientListAdapter extends RecyclerView.Adapter<ClientViewHolder> {
        private Context context;
        private List<ClientInfo> clients;

        public ClientListAdapter(Context context, List<ClientInfo> clients) {
            this.context = context;
            this.clients = clients;
        }

        @NonNull
        @Override
        public ClientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_transfer_client, parent, false);
            return new ClientViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ClientViewHolder holder, int position) {
            ClientInfo client = clients.get(position);
            holder.bind(client);
        }

        @Override
        public int getItemCount() {
            return clients.size();
        }
    }

    /**
     * 客户端列表项ViewHolder
     */
    public static class ClientViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceNameText;
        private TextView ipAddressText;
        private TextView connectionTimeText;
        private TextView transferStatusText;
        private ProgressBar transferProgressBar;

        public ClientViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameText = itemView.findViewById(R.id.client_device_name);
            ipAddressText = itemView.findViewById(R.id.client_ip_address);
            connectionTimeText = itemView.findViewById(R.id.client_connection_time);
            transferStatusText = itemView.findViewById(R.id.client_transfer_status);
            transferProgressBar = itemView.findViewById(R.id.client_transfer_progress);
        }

        public void bind(ClientInfo client) {
            deviceNameText.setText("设备: " + client.getDeviceName());
            ipAddressText.setText("IP: " + client.getIpAddress() + ":" + client.getPort());
            
            long connectedTime = System.currentTimeMillis() - client.getConnectedTime();
            String timeStr = formatTime(connectedTime);
            connectionTimeText.setText("连接时长: " + timeStr);
            
            ClientInfo.TransferStatus status = client.getTransferStatus();
            transferStatusText.setText("状态: " + status.getStatus() + 
                    " (" + status.getTransferredFiles() + "/" + status.getTotalFiles() + ")");
            transferProgressBar.setProgress(status.getProgressPercent());
        }

        private String formatTime(long millis) {
            long seconds = millis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            if (hours > 0) {
                return hours + "小时";
            } else if (minutes > 0) {
                return minutes + "分钟";
            } else {
                return seconds + "秒";
            }
        }
    }
}

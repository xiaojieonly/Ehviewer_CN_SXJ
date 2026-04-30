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

package com.hippo.ehviewer.transfer.core;

import android.content.Context;
import android.util.Log;

import com.hippo.ehviewer.transfer.data.ClientInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 传输服务器管理器
 * 负责mDNS设备发现、HTTP服务器、客户端连接管理
 */
public class TransferServerManager extends Observable {

    private static final String TAG = "TransferServerManager";
    private static final int DEFAULT_PORT = 8080;
    private static final String SERVICE_TYPE = "_ehviewer-transfer._tcp.local";

    private Context context;
    private TransferHttpServer httpServer;
    private MdnsDiscoveryManager discoveryManager;
    private List<ClientInfo> connectedClients;
    private boolean isRunning = false;

    public TransferServerManager(Context context) {
        this.context = context;
        this.connectedClients = new CopyOnWriteArrayList<>();
        this.httpServer = new TransferHttpServer(DEFAULT_PORT, this);
        this.discoveryManager = new MdnsDiscoveryManager(context, SERVICE_TYPE, DEFAULT_PORT);
    }

    /**
     * 启动传输服务器
     */
    public void start() throws Exception {
        Log.d(TAG, "Starting transfer server...");
        
        // 启动HTTP服务器
        httpServer.start();
        Log.d(TAG, "HTTP Server started on port " + DEFAULT_PORT);

        // 注册mDNS服务
        discoveryManager.registerService(getServiceName(), SERVICE_TYPE, DEFAULT_PORT);
        Log.d(TAG, "mDNS service registered: " + getServiceName());

        isRunning = true;
        setChanged();
        notifyObservers("传输服务已启动，监听端口: " + DEFAULT_PORT);
    }

    /**
     * 停止传输服务器
     */
    public void stop() throws Exception {
        Log.d(TAG, "Stopping transfer server...");
        
        if (httpServer != null) {
            httpServer.stop();
        }

        if (discoveryManager != null) {
            discoveryManager.unregisterService();
        }

        isRunning = false;
        connectedClients.clear();
        
        setChanged();
        notifyObservers("传输服务已停止");
    }

    /**
     * 添加已连接的客户端
     */
    public void addClient(ClientInfo client) {
        connectedClients.add(client);
        setChanged();
        notifyObservers("客户端已连接: " + client.getDeviceName());
        Log.d(TAG, "Client added: " + client);
    }

    /**
     * 移除断开连接的客户端
     */
    public void removeClient(String deviceId) {
        connectedClients.removeIf(client -> client.getDeviceId().equals(deviceId));
        setChanged();
        notifyObservers("客户端已断开连接");
        Log.d(TAG, "Client removed: " + deviceId);
    }

    /**
     * 获取所有已连接的客户端
     */
    public List<ClientInfo> getConnectedClients() {
        return new ArrayList<>(connectedClients);
    }

    /**
     * 根据设备ID获取客户端
     */
    public ClientInfo getClient(String deviceId) {
        for (ClientInfo client : connectedClients) {
            if (client.getDeviceId().equals(deviceId)) {
                return client;
            }
        }
        return null;
    }

    /**
     * 获取已连接客户端数量
     */
    public int getClientCount() {
        return connectedClients.size();
    }

    /**
     * 获取服务名称
     */
    private String getServiceName() {
        String deviceName = android.os.Build.MODEL;
        return "EhViewer-" + deviceName.replace(" ", "-");
    }

    /**
     * 检查服务是否运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取服务端口
     */
    public int getPort() {
        return DEFAULT_PORT;
    }

    /**
     * 更新客户端传输状态
     */
    public void updateClientStatus(String deviceId, ClientInfo.TransferStatus status) {
        ClientInfo client = getClient(deviceId);
        if (client != null) {
            client.setTransferStatus(status);
            setChanged();
            notifyObservers("客户端状态已更新: " + deviceId);
        }
    }

    /**
     * 创建一个客户端推送任务（书签/下载/收藏夹/导出）占位方法
     */
    public String createClientPushTask(String type) {
        String taskId = java.util.UUID.randomUUID().toString();
        Log.d(TAG, "Create client push task: " + type + " id=" + taskId);
        setChanged();
        notifyObservers("已请求客户端传输: " + type);
        return taskId;
    }
}

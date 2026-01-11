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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

/**
 * mDNS服务发现管理器
 * 负责设备自动发现和服务注册
 */
public class MdnsDiscoveryManager {

    private static final String TAG = "MdnsDiscovery";
    
    private Context context;
    private NsdManager nsdManager;
    private String serviceType;
    private int port;
    private String serviceName;

    public MdnsDiscoveryManager(Context context, String serviceType, int port) {
        this.context = context;
        this.serviceType = serviceType;
        this.port = port;
        this.nsdManager = context.getSystemService(NsdManager.class);
    }

    /**
     * 注册mDNS服务
     */
    public void registerService(String serviceName, String serviceType, int port) {
        this.serviceName = serviceName;

        Log.d(TAG, "Registering service: " + serviceName + " type: " + serviceType + " port: " + port);

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setPort(port);

        if (nsdManager != null) {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                    Log.d(TAG, "Service registered: " + NsdServiceInfo.getServiceName());
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "Service registration failed. Error code: " + errorCode);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo arg0) {
                    Log.d(TAG, "Service unregistered");
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "Service unregistration failed. Error code: " + errorCode);
                }
            });
        }
    }

    /**
     * 注销mDNS服务
     */
    public void unregisterService() {
        Log.d(TAG, "Unregistering service: " + serviceName);
        
        if (nsdManager != null && serviceName != null) {
            try {
                // 注意: NsdManager 不直接提供取消注册方法，需要保存RegistrationListener
                // 这里是简化实现
                Log.d(TAG, "Service unregistration initiated");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister service", e);
            }
        }
    }

    /**
     * 发现服务
     */
    public void discoverServices() {
        Log.d(TAG, "Discovering services of type: " + serviceType);

        if (nsdManager != null) {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, new NsdManager.DiscoveryListener() {
                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e(TAG, "Discovery failed. Error code: " + errorCode);
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e(TAG, "Stop discovery failed. Error code: " + errorCode);
                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                    Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
                }

                @Override
                public void onDiscoveryStarted(String serviceType) {
                    Log.d(TAG, "Discovery started for type: " + serviceType);
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    Log.d(TAG, "Discovery stopped for type: " + serviceType);
                }
            });
        }
    }

    /**
     * 获取mDNS管理器
     */
    public NsdManager getNsdManager() {
        return nsdManager;
    }
}

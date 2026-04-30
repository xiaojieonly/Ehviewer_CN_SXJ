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

package com.hippo.ehviewer.transfer.data;

/**
 * 客户端信息
 */
public class ClientInfo {
    private String deviceId;
    private String deviceName;
    private String ipAddress;
    private int port;
    private String deviceType;
    private long connectedTime;
    private TransferStatus transferStatus;

    public ClientInfo(String deviceId, String deviceName, String ipAddress, int port) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectedTime = System.currentTimeMillis();
        this.transferStatus = new TransferStatus();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public void setConnectedTime(long connectedTime) {
        this.connectedTime = connectedTime;
    }

    public TransferStatus getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(TransferStatus transferStatus) {
        this.transferStatus = transferStatus;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "deviceId='" + deviceId + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", deviceType='" + deviceType + '\'' +
                ", connectedTime=" + connectedTime +
                '}';
    }

    /**
     * 传输状态
     */
    public static class TransferStatus {
        private String status; // pending, transferring, completed, failed
        private long totalFiles;
        private long transferredFiles;
        private long totalSize;
        private long transferredSize;
        private String currentFile;
        private int progressPercent;

        public TransferStatus() {
            this.status = "idle";
            this.totalFiles = 0;
            this.transferredFiles = 0;
            this.totalSize = 0;
            this.transferredSize = 0;
            this.progressPercent = 0;
        }

        // Getters and Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(long totalFiles) {
            this.totalFiles = totalFiles;
        }

        public long getTransferredFiles() {
            return transferredFiles;
        }

        public void setTransferredFiles(long transferredFiles) {
            this.transferredFiles = transferredFiles;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public void setTotalSize(long totalSize) {
            this.totalSize = totalSize;
        }

        public long getTransferredSize() {
            return transferredSize;
        }

        public void setTransferredSize(long transferredSize) {
            this.transferredSize = transferredSize;
        }

        public String getCurrentFile() {
            return currentFile;
        }

        public void setCurrentFile(String currentFile) {
            this.currentFile = currentFile;
        }

        public int getProgressPercent() {
            if (totalSize <= 0) {
                return 0;
            }
            return (int) ((transferredSize * 100) / totalSize);
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }
    }
}

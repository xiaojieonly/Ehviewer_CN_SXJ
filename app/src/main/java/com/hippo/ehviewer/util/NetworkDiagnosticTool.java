package com.hippo.ehviewer.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络连通性诊断工具类
 */
public class NetworkDiagnosticTool {

    private static final String TAG = "NetworkDiagnostic";

    public static class SiteInfo {
        public String domain;
        public String resolvedIP;
        public boolean isAccessible;
        public long responseTime;
        public String error;

        public SiteInfo(String domain) {
            this.domain = domain;
            this.isAccessible = false;
            this.responseTime = -1;
        }

        @Override
        public String toString() {
            return "SiteInfo{" +
                    "domain='" + domain + '\'' +
                    ", resolvedIP='" + resolvedIP + '\'' +
                    ", isAccessible=" + isAccessible +
                    ", responseTime=" + responseTime + "ms" +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    public static class NetworkInfo {
        public String currentIP;
        public String networkType;  // "WiFi", "Mobile", "Unknown"
        public boolean isConnected;
    }

    /**
     * 获取当前网络信息
     */
    public static NetworkInfo getNetworkInfo(Context context) {
        NetworkInfo info = new NetworkInfo();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                info.isConnected = true;
                int type = activeNetwork.getType();
                if (type == ConnectivityManager.TYPE_WIFI) {
                    info.networkType = "WiFi";
                } else if (type == ConnectivityManager.TYPE_MOBILE) {
                    info.networkType = "Mobile";
                } else {
                    info.networkType = "Other";
                }
            } else {
                info.isConnected = false;
                info.networkType = "Unknown";
            }
        }

        // 获取本地IP（简化方式）
        try {
            info.currentIP = getLocalIP();
        } catch (Exception e) {
            info.currentIP = "N/A";
        }

        return info;
    }

    /**
     * 获取本地IP地址
     */
    private static String getLocalIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 忽略回环地址和IPv6
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        
        return "N/A";
    }

    /**
     * 检测站点的DNS解析和可访问性
     */
    public static SiteInfo checkSite(String domain) {
        SiteInfo info = new SiteInfo(domain);
        long startTime = System.currentTimeMillis();

        try {
            // DNS解析
            InetAddress address = InetAddress.getByName(domain);
            info.resolvedIP = address.getHostAddress();
            Log.d(TAG, domain + " resolved to: " + info.resolvedIP);

            // 检测连通性（尝试连接80和443端口）
            if (checkPort(domain, 443) || checkPort(domain, 80)) {
                info.isAccessible = true;
                Log.d(TAG, domain + " is accessible");
            } else {
                info.isAccessible = false;
                info.error = "Connection refused";
                Log.d(TAG, domain + " is NOT accessible");
            }

        } catch (UnknownHostException e) {
            info.resolvedIP = "FAILED";
            info.isAccessible = false;
            info.error = "DNS resolution failed";
            Log.e(TAG, "DNS resolution failed for " + domain, e);
        } catch (Exception e) {
            info.isAccessible = false;
            info.error = e.getMessage();
            Log.e(TAG, "Error checking site: " + domain, e);
        }

        info.responseTime = System.currentTimeMillis() - startTime;
        return info;
    }

    /**
     * 检测特定端口是否可连接
     */
    private static boolean checkPort(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            // 增加超时时间到10秒，某些网络环境需要更长时间
            socket.connect(new java.net.InetSocketAddress(host, port), 10000);
            return true;
        } catch (java.net.SocketTimeoutException e) {
            Log.d(TAG, "Port " + port + " connection timeout for " + host);
            return false;
        } catch (java.net.ConnectException e) {
            Log.d(TAG, "Port " + port + " connection refused for " + host);
            return false;
        } catch (Exception e) {
            Log.d(TAG, "Port " + port + " check failed for " + host + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 批量检测多个站点
     */
    public static List<SiteInfo> checkMultipleSites(String[] domains) {
        List<SiteInfo> results = new ArrayList<>();
        for (String domain : domains) {
            results.add(checkSite(domain));
        }
        return results;
    }
}

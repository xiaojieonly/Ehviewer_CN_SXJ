package com.hippo.ehviewer.server.util;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class NetworkUtils {

    private NetworkUtils() {
    }

    @NonNull
    public static List<JSONObject> getLanIpv4Addresses() {
        ArrayList<JSONObject> results = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return results;
            }
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                String label = interfaceLabel(nif.getName());
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String host = address.getHostAddress();
                    if (host == null || host.startsWith("169.254.")) {
                        continue;
                    }
                    JSONObject row = new JSONObject();
                    row.put("interface", nif.getName());
                    row.put("label", label);
                    row.put("address", host);
                    results.add(row);
                }
            }
        } catch (Throwable e) {
            ServerLog.e("Network interface scan failed: " + e.getMessage());
        }
        return results;
    }

    @NonNull
    public static JSONArray getLanIpv4AddressesJsonArray() {
        JSONArray array = new JSONArray();
        List<JSONObject> list = getLanIpv4Addresses();
        for (JSONObject item : list) {
            array.add(item);
        }
        return array;
    }

    @NonNull
    private static String interfaceLabel(@NonNull String name) {
        String lower = name.toLowerCase();
        if (lower.startsWith("wlan") || lower.contains("wifi")) {
            return "Wi-Fi";
        }
        if (lower.startsWith("rmnet") || lower.startsWith("ccmni") || lower.contains("mobile")) {
            return "Mobile";
        }
        if (lower.startsWith("usb") || lower.contains("rndis")) {
            return "USB";
        }
        if (lower.startsWith("eth")) {
            return "Ethernet";
        }
        if (lower.startsWith("ap")) {
            return "Tethering";
        }
        return name;
    }
}

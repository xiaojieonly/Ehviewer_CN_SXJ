package com.hippo.ehviewer.ui.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_INFO;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_LABEL;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_FAVORITE_INFO;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_QUICK_SEARCH;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_DISCONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_INFO_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_LABEL_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.FAVORITE_INFO_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_MSG;
import static com.hippo.ehviewer.client.wifi.ConnectThread.IS_CLIENT;
import static com.hippo.ehviewer.client.wifi.ConnectThread.QUICK_SEARCH_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_ERROR;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_SUCCESS;
import static com.hippo.ehviewer.event.SomethingNeedRefresh.bookmarkDrawNeedRefresh;
import static com.hippo.ehviewer.event.SomethingNeedRefresh.downloadInfoNeedRefresh;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.hippo.ehviewer.client.wifi.ConnectThread;
import com.hippo.ehviewer.client.wifi.ListenerThread;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.util.ExecutorManager;
import com.hippo.util.PermissionRequester;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WiFiClientActivity extends AppCompatActivity {

    private final int pCode = 88888;

    private TextView textState;
    private TextView receiveMessage;
    private EditText ipAddressInput;
    /**
     * 连接线程
     */
    private ConnectThread connectThread;

    /**
     * 监听线程
     */
    private ListenerThread listenerThread;

    /**
     * 热点名称
     */
    private static final String WIFI_HOTSPOT_SSID = "TEST";
    /**
     * 端口号
     */
    private static final int PORT = 54321;
    private WifiManager wifiManager;

    private TextView statusInit;

    private WiFiClientHandler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_client);
//        findViewById(R.id.send).setOnClickListener(this::send);
        findViewById(R.id.connect_server).setOnClickListener(this::connectAuto);
        findViewById(R.id.connect_custom_ip).setOnClickListener(this::connectCustomIP);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        boolean result = PermissionRequester.request(this, Manifest.permission.CHANGE_WIFI_STATE,
                getString(R.string.wifi_server_no_permission),pCode);
        //检查Wifi状态
        if (result && !wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);
        textState = findViewById(R.id.status_info);
        receiveMessage = findViewById(R.id.receive_message);
        statusInit = findViewById(R.id.status_init);
        ipAddressInput = findViewById(R.id.ip_address_input);

        String initText = getString(R.string.wifi_connected_info, wifiManager.getConnectionInfo().getSSID(), getIp(), getWifiRouteIPAddress(this));
        statusInit.setText(initText);

        if (handler == null) {
            handler = new WiFiClientHandler(getMainLooper());
        }
        //        initBroadcastReceiver();
        //        开启连接线程
        connectSocket();
        listenerThread = new ListenerThread(PORT, handler);
        listenerThread.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == pCode){
            wifiManager.setWifiEnabled(true);
            connectSocket();
        }
    }

    private void connectSocket() {
        connectSocketWithIP(getWifiRouteIPAddress(WiFiClientActivity.this));
    }

    private void connectSocketWithIP(String targetIP) {
        ExecutorManager.getNetworkExecutor().execute(() -> {
            try {
                Socket socket = new Socket(targetIP, PORT);
                connectThread = new ConnectThread(getApplicationContext(), socket, handler, IS_CLIENT);
                connectThread.start();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> textState.setText(getString(R.string.wifi_connection_failed)));
                try {
                    Thread.sleep(2000);
                    runOnUiThread(() -> textState.setText(getString(R.string.wifi_try_reconnect)));
                    connectSocketWithIP(targetIP);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void connectAuto(View view) {
        String text = getString(R.string.wifi_connected_info, wifiManager.getConnectionInfo().getSSID(), getIp(), getWifiRouteIPAddress(this));
        statusInit.setText(text);
        connectSocket();
    }

    private void connectCustomIP(View view) {
        String customIP = ipAddressInput.getText().toString().trim();
        if (customIP.isEmpty()) {
            textState.setText(getString(R.string.wifi_please_enter_ip));
            return;
        }
        
        // 简单的IP地址格式验证
        if (!isValidIP(customIP)) {
            textState.setText(getString(R.string.wifi_invalid_ip_format));
            return;
        }
        
        String text = getString(R.string.wifi_connecting_to_custom_ip, customIP, getIp());
        statusInit.setText(text);
        connectSocketWithIP(customIP);
    }

    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * 获取已连接的热点路由
     *
     * @return
     */
    private String getIp() {
       try {
           //检查Wifi状态
           if (!wifiManager.isWifiEnabled())
               wifiManager.setWifiEnabled(true);
           WifiInfo wi = wifiManager.getConnectionInfo();
           //获取32位整型IP地址
           int ipAdd = wi.getIpAddress();
           //把整型地址转换成“*.*.*.*”地址
           return intToIp(ipAdd);
       }catch (SecurityException e){
           return "";
       }
    }

    private String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                (i >> 24 & 0xFF);
    }

    /**
     * wifi获取 已连接网络路由  路由ip地址---方法同上
     *
     * @param context
     * @return
     */
    private static String getWifiRouteIPAddress(Context context) {
        WifiManager wifi_service = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifi_service.getDhcpInfo();
        //        WifiInfo wifiinfo = wifi_service.getConnectionInfo();
        //        System.out.println("Wifi info----->" + wifiinfo.getIpAddress());
        //        System.out.println("DHCP info gateway----->" + Formatter.formatIpAddress(dhcpInfo.gateway));
        //        System.out.println("DHCP info netmask----->" + Formatter.formatIpAddress(dhcpInfo.netmask));
        //DhcpInfo中的ipAddress是一个int型的变量，通过Formatter将其转化为字符串IP地址
        String routeIp = Formatter.formatIpAddress(dhcpInfo.gateway);
        Log.i("route ip", "wifi route ip：" + routeIp);

        return routeIp;
    }

    /**
     * 获取连接到热点上的手机ip
     *
     * @return
     */
    private ArrayList<String> getConnectedIP() {
        ArrayList<String> connectedIP = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    "/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted.length >= 4) {
                    String ip = splitted[0];
                    connectedIP.add(ip);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //        Log.i("connectIp:", connectedIP);
        return connectedIP;
    }

    @Override
    protected void onDestroy() {
        if (connectThread!=null){
            connectThread.closeConnect();
            connectThread = null;
        }
        if (listenerThread!=null){
            listenerThread.closeConnect();
            listenerThread = null;
        }
        super.onDestroy();
    }

    private void onReceiveMsg(WiFiDataHand response) {
        switch (response.dataType){
            case DATA_TYPE_QUICK_SEARCH:
                dealWithQuickSearch(response);
                break;
            case DATA_TYPE_DOWNLOAD_LABEL:
                dealWithDownloadLabel(response);
                break;
            case DATA_TYPE_DOWNLOAD_INFO:
                dealWithDownloadInfo(response);
                break;
            case DATA_TYPE_FAVORITE_INFO:
                dealWithFavoriteInfo(response);
                break;
            default:
                receiveMessage.setText(R.string.wifi_server_receive_message_unknown);
                connectThread.dataProcessed(response);
                break;
        }

    }

    private void dealWithFavoriteInfo(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(FAVORITE_INFO_DATA_KEY);
        ExecutorManager.getIoExecutor().execute(() -> {
            for (int i = 0; i < jsonArray.size(); i++) {
                EhDB.putLocalFavorite(GalleryInfo.galleryInfoFromJson(jsonArray.getJSONObject(i)));
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
        });
    }

    private void dealWithDownloadInfo(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(DOWNLOAD_INFO_DATA_KEY);
        DownloadManager manager = EhApplication.getDownloadManager();
        new Thread(()->{
            for (int i = 0; i < jsonArray.size(); i++) {
               try{
                   DownloadInfo info = DownloadInfo.downloadInfoFromJson(jsonArray.getJSONObject(i));
                   manager.addDownloadInfo(info,info.label);
               }catch (ClassCastException e){
                   Analytics.recordException(e);
               }
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
        }).start();
    }

    private void dealWithDownloadLabel(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(DOWNLOAD_LABEL_KEY);
        DownloadManager manager = EhApplication.getDownloadManager();
        ExecutorManager.getBackgroundExecutor().execute(() -> {
            for (int i = 0; i < jsonArray.size(); i++) {
                manager.addLabelInSyncThread(jsonArray.getString(i));
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
            EventBus.getDefault().post(downloadInfoNeedRefresh());
       });
    }

    private void dealWithQuickSearch(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(QUICK_SEARCH_DATA_KEY);

        List<QuickSearch> quickSearchList = new ArrayList<>();

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            quickSearchList.add(QuickSearch.quickSearchFromJson(object));
        }
        ExecutorManager.getIoExecutor().execute(() -> {
            EhDB.takeOverQuickSearchList(quickSearchList);
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
            EventBus.getDefault().post(bookmarkDrawNeedRefresh());
        });
    }

    public void updateReceiveMessage(String message){
        runOnUiThread(()->receiveMessage.setText(message));
    }

    private class WiFiClientHandler extends Handler {

        WiFiClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DEVICE_CONNECTED:
                    textState.setText(R.string.wifi_server_connection_succeeded);
                    break;
                case DEVICE_DISCONNECTED:
                    textState.setText(R.string.wifi_server_disconnect);
                    break;
                case SEND_MSG_SUCCESS:
                    textState.setText(getString(R.string.wifi_server_send_success, msg.getData().getString("MSG")));
                    break;
                case SEND_MSG_ERROR:
                    textState.setText(getString(R.string.wifi_server_send_fail, msg.getData().getString("MSG")));
                    break;
                case GET_MSG:
                    onReceiveMsg(new WiFiDataHand(msg.getData().getString("MSG")));
                    break;
                default:
                    break;
            }
        }
    }
}

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
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import com.hippo.util.PermissionRequester;
import com.microsoft.appcenter.crashes.Crashes;

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
        findViewById(R.id.connect_server).setOnClickListener(this::connect);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        boolean result = PermissionRequester.request(this, Manifest.permission.CHANGE_WIFI_STATE,
                getString(R.string.wifi_server_no_permission),pCode);
        //检查Wifi状态
        if (result && !wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);
        textState = findViewById(R.id.status_info);
        receiveMessage = findViewById(R.id.receive_message);
        statusInit = findViewById(R.id.status_init);

        String initText = "已连接到：" + wifiManager.getConnectionInfo().getSSID() +
                "\nIP:" + getIp()
                + "\n路由：" + getWifiRouteIPAddress(this);
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
        new Thread(() -> {
            try {
                Socket socket = new Socket(getWifiRouteIPAddress(WiFiClientActivity.this), PORT);
                connectThread = new ConnectThread(getApplicationContext(), socket, handler, IS_CLIENT);
                connectThread.start();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> textState.setText("通信连接失败"));
                try {
                    Thread.sleep(2000);
                    runOnUiThread(() -> textState.setText("尝试重新链接"));
                    connectSocket();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

        }).start();
    }

    private void connect(View view) {
        String text = "已连接到：" + wifiManager.getConnectionInfo().getSSID() +
                "\nIP:" + getIp()
                + "\n路由：" + getWifiRouteIPAddress(this);
        statusInit.setText(text);
//        connectSocket();
    }

    /**
     * 获取已连接的热点路由
     *
     * @return
     */
    private String getIp() {
        //检查Wifi状态
        if (!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);
        WifiInfo wi = wifiManager.getConnectionInfo();
        //获取32位整型IP地址
        int ipAdd = wi.getIpAddress();
        //把整型地址转换成“*.*.*.*”地址
        return intToIp(ipAdd);
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
        new Thread(()->{
            for (int i = 0; i < jsonArray.size(); i++) {
                EhDB.putLocalFavorite(GalleryInfo.galleryInfoFromJson(jsonArray.getJSONObject(i)));
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
        }).start();
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
                   Crashes.trackError(e);
               }
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
        }).start();
    }

    private void dealWithDownloadLabel(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(DOWNLOAD_LABEL_KEY);
        DownloadManager manager = EhApplication.getDownloadManager();
        new Thread(()->{
            for (int i = 0; i < jsonArray.size(); i++) {
                manager.addLabelInSyncThread(jsonArray.getString(i));
            }
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
            EventBus.getDefault().post(downloadInfoNeedRefresh());
       }).start();
    }

    private void dealWithQuickSearch(WiFiDataHand response) {
        JSONArray jsonArray = response.getData().getJSONArray(QUICK_SEARCH_DATA_KEY);

        List<QuickSearch> quickSearchList = new ArrayList<>();

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            quickSearchList.add(QuickSearch.quickSearchFromJson(object));
        }
        new Thread(()->{
            EhDB.takeOverQuickSearchList(quickSearchList);
            connectThread.dataProcessed(response);
            updateReceiveMessage(getString(R.string.wifi_server_receive_message, response.toString()));
            EventBus.getDefault().post(bookmarkDrawNeedRefresh());
        }).start();
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

package com.hippo.ehviewer.ui.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTING;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_MSG;
import static com.hippo.ehviewer.client.wifi.ConnectThread.IS_CLIENT;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_ERROR;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_SUCCESS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
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

import androidx.appcompat.app.AppCompatActivity;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.hippo.ehviewer.client.wifi.ConnectThread;
import com.hippo.ehviewer.client.wifi.ListenerThread;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class WiFiClientActivity extends AppCompatActivity {

    private TextView textState;
    private TextView receiveMessage;
    /**
     * 连接线程
     */
    private ConnectThread connectThread;
    private ConnectThread connectThreadNew;


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

    private ClientReceiver receiver;

    int CHOOSE_FILE_RESULT_CODE = 1001;

    int FILE_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_client);
//        findViewById(R.id.send).setOnClickListener(this::send);
        findViewById(R.id.connect_server).setOnClickListener(this::connect);
//        findViewById(R.id.fileButton).setOnClickListener(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        //检查Wifi状态
        if (!wifiManager.isWifiEnabled())
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

    private String intToRouterIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                1;
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    //文件传输
    //    https://blog.csdn.net/yuankundong/article/details/51489823

    /**
     * 查找当前连接状态
     */
    private void initBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        if (receiver == null) {
            receiver = new ClientReceiver();
        }
        registerReceiver(receiver, intentFilter);
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
        connectThread.closeConnect();
        connectThread.interrupt();
        connectThread = null;
        listenerThread.closeConnect();
        listenerThread.interrupt();
        listenerThread = null;
        super.onDestroy();
    }

    private void onReceiveMsg(WiFiDataHand response) {
        connectThread.dataProcessed();
        receiveMessage.setText(response.toString());
    }

    private class WiFiClientHandler extends Handler {

        WiFiClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DEVICE_CONNECTING:
//                    connectThread.closeConnect();
//                    connectThread = new ConnectThread(WiFiClientActivity.this, listenerThread.getSocket(), handler, IS_CLIENT);
//                    connectThread.start();
                    break;
                case DEVICE_CONNECTED:
                    textState.setText(R.string.wifi_server_connection_succeeded);
                    break;
                case SEND_MSG_SUCCESS:
                    textState.setText(getString(R.string.wifi_server_send_success, msg.getData().getString("MSG")));
                    break;
                case SEND_MSG_ERROR:
                    textState.setText(getString(R.string.wifi_server_send_fail, msg.getData().getString("MSG")));
                    break;
                case GET_MSG:
                    textState.setText(getString(R.string.wifi_server_receive_message, msg.getData().getString("MSG")));
                    onReceiveMsg(new WiFiDataHand(msg.getData().getString("MSG")));
                    break;
            }
        }
    }

    private class ClientReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                Log.i("BBB", "SCAN_RESULTS_AVAILABLE_ACTION");
                // wifi已成功扫描到可用wifi。
                //                List<ScanResult> scanResults = wifiManager.getScanResults();
                //                wifiListAdapter.clear();
                //                wifiListAdapter.addAll(scanResults);
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                Log.i("BBB", "WifiManager.WIFI_STATE_CHANGED_ACTION");
                int wifiState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, 0);
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_ENABLED:
                        //获取到wifi开启的广播时，开始扫描
                        //                        wifiManager.startScan();
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        //wifi关闭发出的广播
                        break;
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                Log.i("BBB", "WifiManager.NETWORK_STATE_CHANGED_ACTION");
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    textState.setText("连接已断开");
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
//                    text_state.setText("已连接到网络:" + wifiInfo.getSSID()
//                            + "\n" + wifiInfo.getIpAddress()
//                            + "\n" + wifiInfo.getNetworkId()
//                            + "\n" + wifiInfo.getMacAddress());
                    textState.setText(R.string.wifi_server_connection_succeeded);
                    Log.i("AAA", "wifiInfo.getSSID():" + wifiInfo.getSSID() +
                            "  WIFI_HOTSPOT_SSID:" + WIFI_HOTSPOT_SSID);
                    if (wifiInfo.getSSID().equals(WIFI_HOTSPOT_SSID)) {
                        //如果当前连接到的wifi是热点,则开启连接线程
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    ArrayList<String> connectedIP = getConnectedIP();
                                    for (String ip : connectedIP) {
                                        if (ip.contains(".")) {
                                            Log.i("AAA", "IP:" + ip);
                                            Socket socket = new Socket(ip, PORT);
                                            connectThread = new ConnectThread(getApplicationContext(), socket, handler, IS_CLIENT);
                                            connectThread.start();
                                        }
                                    }

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                } else {
                    NetworkInfo.DetailedState state = info.getDetailedState();
                    if (state == state.CONNECTING) {
                        textState.setText("连接中...");
                    } else if (state == state.AUTHENTICATING) {
                        textState.setText("正在验证身份信息...");
                    } else if (state == state.OBTAINING_IPADDR) {
                        textState.setText("正在获取IP地址...");
                    } else if (state == state.FAILED) {
                        textState.setText("连接失败");
                    }
                }

            }
        }
    }
}

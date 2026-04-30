package com.hippo.ehviewer.ui.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_INFO;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_LABEL;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_FAVORITE_INFO;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_QUICK_SEARCH;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTING;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_INFO_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_LABEL_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.FAVORITE_INFO_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_MSG;
import static com.hippo.ehviewer.client.wifi.ConnectThread.IS_SERVER;
import static com.hippo.ehviewer.client.wifi.ConnectThread.QUICK_SEARCH_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_ERROR;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_SUCCESS;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONArray;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.hippo.ehviewer.client.wifi.ConnectThread;
import com.hippo.ehviewer.client.wifi.ListenerThread;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.ui.ToolbarActivity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;


public class WiFiServerActivity extends ToolbarActivity {

    private static final int REQUEST_CODE = 996;

    /**
     * 连接线程
     */
    private ConnectThread connectThread;

    /**
     * 监听线程
     */
    private ListenerThread listenerThread;

    /**
     * 端口号
     */
    private static final int PORT = 54321;

    private WiFiServerHandler handler;

    private boolean sending = false;

    private final LinkedList<WiFiDataHand> dataHands = new LinkedList<>();

    private Context mContext;
    private TextView textState;
    private TextView ipAddressesText;

    private Button statusButton;
    private ProgressBar loadingIndicator;
    private CheckBox checkboxBookmark;
    private CheckBox checkboxFavorite;
    private CheckBox checkboxDownload;
    
    private WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        setContentView(R.layout.activity_wifi_server);
        textState = findViewById(R.id.receive);
        ipAddressesText = findViewById(R.id.ip_addresses);
        statusButton = findViewById(R.id.status_change);
        loadingIndicator = findViewById(R.id.loading_indicator);
        checkboxBookmark = findViewById(R.id.checkbox_bookmark);
        checkboxFavorite = findViewById(R.id.checkbox_favorite);
        checkboxDownload = findViewById(R.id.checkbox_download);
        
        // 默认勾选第一个checkbox（书签）
        checkboxBookmark.setChecked(true);
        
        // 设置checkbox监听器，当状态改变时更新按钮状态
        checkboxBookmark.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        checkboxFavorite.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        checkboxDownload.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState());
        
        statusButton.setOnClickListener(this::onStatusChange);
        updateStatusButton();
        displayIPAddresses();
        checkWifiHotspotState();
        
        boolean result = requestMyPermission();
        if (result) {
            openConnectThread();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openConnectThread();
            } else {
                Toast.makeText(mContext, R.string.wifi_server_no_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void openConnectThread() {
        if (handler == null) {
            handler = new WiFiServerHandler(getMainLooper());
        }
        //        开启连接线程
        new Thread(() -> {
            try {
                Log.d("WiFiServer", "Starting listener thread on port " + PORT);
                listenerThread = new ListenerThread(PORT, handler);
                listenerThread.start();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("WiFiServer", "Thread sleep interrupted", e);
                }
                
                String ipAddress = getWifiApIpAddress();
                Log.i("WiFiServer", "Detected IP address: " + ipAddress);
                
                // 更新IP地址显示
                runOnUiThread(() -> displayIPAddresses());
                
                //本地路由开启通信
                openSocket();

            } catch (IOException e) {
                Log.e("WiFiServer", "Failed to open connection thread", e);
                runOnUiThread(() -> {
                    textState.setText(R.string.wifi_server_connection_fail);
                    Toast.makeText(mContext, getString(R.string.wifi_server_reconnect_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    updateButtonState();
                });
            }
        }).start();
    }

    private boolean requestMyPermission() {
        int result = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_WIFI_STATE);
        if (result != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE}, REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void openSocket() throws IOException {
        String ip = getWifiApIpAddress();
        if (ip == null) {
            ip = "192.168.43.1";
            Log.w("WiFiServer", "Using default IP address: " + ip);
        } else {
            Log.d("WiFiServer", "Using detected IP address: " + ip);
        }
        
        try {
            Log.d("WiFiServer", "Attempting to connect to " + ip + ":" + PORT);
            Socket socket = new Socket(ip, PORT);
            connectThread = new ConnectThread(WiFiServerActivity.this, socket, handler, IS_SERVER);
            connectThread.start();
            Log.d("WiFiServer", "Socket connection established successfully");
        } catch (IOException e) {
            Log.e("WiFiServer", "Failed to connect to " + ip + ":" + PORT, e);
            throw e;
        }
    }

    // 不再需要这些方法，因为我们已经使用CheckBox替代了Spinner

    private void onStatusChange(View view) {
        Log.d("WiFiServer", "Status button clicked, sending=" + sending + ", dataHands.size()=" + dataHands.size());
        
        if (sending) {
            Log.d("WiFiServer", "Stopping data transmission");
            sending = false;
            updateStatusButton();
            return;
        }
        
        if (!dataHands.isEmpty()){
            Log.d("WiFiServer", "Resuming data transmission, remaining data: " + dataHands.size());
            sendNextPage();
        }else{
            // 检查哪些checkbox被选中
            boolean bookmarkSelected = checkboxBookmark.isChecked();
            boolean favoriteSelected = checkboxFavorite.isChecked();
            boolean downloadSelected = checkboxDownload.isChecked();
            
            Log.d("WiFiServer", "Checkbox states - bookmark: " + bookmarkSelected + ", favorite: " + favoriteSelected + ", download: " + downloadSelected);
            
            if (!bookmarkSelected && !favoriteSelected && !downloadSelected) {
                Log.w("WiFiServer", "No data type selected");
                Toast.makeText(mContext, R.string.wifi_please_select_data_type, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 根据选择创建数据
            if (bookmarkSelected) {
                Log.d("WiFiServer", "Creating bookmark data");
                createBookmarkData();
            }
            if (favoriteSelected) {
                Log.d("WiFiServer", "Creating favorite data");
                createFavoriteData();
            }
            if (downloadSelected) {
                Log.d("WiFiServer", "Creating download data");
                createDownloadData();
            }
            
            // 如果有数据被添加到队列，开始发送
            if (!dataHands.isEmpty()) {
                Log.d("WiFiServer", "Starting data transmission with " + dataHands.size() + " data packets");
                sendNextPage();
            }
        }
    }

    private void createFavoriteData() {
        if (sending) {
            Toast.makeText(mContext, R.string.wifi_sending, Toast.LENGTH_LONG).show();
            return;
        }
        List<GalleryInfo> list = EhDB.getAllLocalFavorites();
        new Thread(() -> {
            int pageSize = 10;
            int pageCount = totalPage(list.size(), pageSize);

            for (int i = 0; i < pageCount; i++) {
                WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.SEND);
                wiFiDataHand.dataType = DATA_TYPE_FAVORITE_INFO;
                wiFiDataHand.pageSize = pageCount;
                wiFiDataHand.pageIndex = i + 1;
                JSONArray objects = new JSONArray();
                for (int j = 0; j < pageSize; j++) {
                    if (list.isEmpty()) {
                        continue;
                    }
                    GalleryInfo galleryInfo = list.remove(0);
                    objects.add(galleryInfo.toJson());
                }
                wiFiDataHand.addData(FAVORITE_INFO_DATA_KEY, objects);
                dataHands.add(wiFiDataHand);
            }
            sendNextPage();
        }).start();
    }

    private void createBookmarkData() {
        if (sending) {
            Toast.makeText(mContext, R.string.wifi_sending, Toast.LENGTH_LONG).show();
            return;
        }
        List<QuickSearch> list = EhDB.getAllQuickSearch();
        new Thread(() -> {
            int pageSize = 10;
            int pageCount = totalPage(list.size(), pageSize);

            for (int i = 0; i < pageCount; i++) {
                WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.SEND);
                wiFiDataHand.dataType = DATA_TYPE_QUICK_SEARCH;
                wiFiDataHand.pageSize = pageCount;
                wiFiDataHand.pageIndex = i + 1;
                JSONArray objects = new JSONArray();
                for (int j = 0; j < pageSize; j++) {
                    if (list.isEmpty()) {
                        continue;
                    }
                    QuickSearch quickSearch = list.remove(0);
                    objects.add(quickSearch.toJson());
                }
                wiFiDataHand.addData(QUICK_SEARCH_DATA_KEY, objects);
                dataHands.add(wiFiDataHand);
            }
            sendNextPage();
        }).start();
    }

    private void createDownloadData() {
        if (sending) {
            Toast.makeText(mContext, R.string.wifi_sending, Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            List<DownloadLabel> labels = EhDB.getAllDownloadLabelList();
            WiFiDataHand dataHand = new WiFiDataHand(WiFiDataHand.SEND);
            dataHand.dataType = DATA_TYPE_DOWNLOAD_LABEL;
            JSONArray labelArray = new JSONArray();
            for (int i = 0; i < labels.size(); i++) {
                labelArray.add(labels.get(i).getLabel());

            }
            dataHand.addData(DOWNLOAD_LABEL_KEY, labelArray);
            dataHands.add(dataHand);

            List<DownloadInfo> allInfo = EhDB.getAllDownloadInfo();

            int pageSize = 10;
            int pageCount = totalPage(allInfo.size(), pageSize);
            for (int i = 0; i < pageCount; i++) {
                WiFiDataHand infoHand = new WiFiDataHand(WiFiDataHand.SEND);
                infoHand.dataType = DATA_TYPE_DOWNLOAD_INFO;
                infoHand.pageSize = pageCount;
                infoHand.pageIndex = i + 1;
                JSONArray infoArray = new JSONArray();
                for (int j = 0; j < pageSize; j++) {
                    if (allInfo.isEmpty()) {
                        continue;
                    }
                    DownloadInfo downloadInfo = allInfo.remove(0);
                    infoArray.add(downloadInfo.toJson());
                }
                infoHand.addData(DOWNLOAD_INFO_DATA_KEY, infoArray);
                dataHands.add(infoHand);
            }
            sendNextPage();
        }).start();
    }

    private void updateButtonState() {
        boolean anyChecked = checkboxBookmark.isChecked() || checkboxFavorite.isChecked() || checkboxDownload.isChecked();
        boolean enabled = anyChecked && !sending;
        statusButton.setEnabled(enabled);
        Log.d("WiFiServer", "Button state updated: enabled=" + enabled + ", anyChecked=" + anyChecked + ", sending=" + sending);
    }
    
    private void checkConnectionState() {
        if (connectThread != null && connectThread.isSocketClose()) {
            Log.w("WiFiServer", "Connection is closed, attempting to reconnect");
            runOnUiThread(() -> {
                textState.setText(R.string.wifi_server_reconnecting);
                statusButton.setEnabled(false);
            });
            
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 等待2秒后重连
                    openSocket();
                    runOnUiThread(() -> {
                        textState.setText(R.string.wifi_server_connection_succeeded);
                        updateButtonState();
                    });
                } catch (Exception e) {
                    Log.e("WiFiServer", "Failed to reconnect", e);
                    runOnUiThread(() -> {
                        textState.setText(getString(R.string.wifi_server_reconnect_failed, e.getMessage()));
                        updateButtonState();
                    });
                }
            }).start();
        }
    }
    
    private void updateStatusButton(){
        String content;
        if (!sending) {
            if (dataHands.isEmpty()){
                content = getString(R.string.wifi_send_start,"");
            }else{
                content = getString(R.string.wifi_send_start,"("+dataHands.size()+")");
            }
            statusButton.setText(content);
            loadingIndicator.setVisibility(View.GONE);
            // 更新按钮可用状态
            updateButtonState();
            return;
        }
        if (dataHands.isEmpty()){
            content = getString(R.string.wifi_send_stop,"");
        }else{
            content = getString(R.string.wifi_send_stop,"("+dataHands.size()+")");
        }
        statusButton.setText(content);
        loadingIndicator.setVisibility(View.VISIBLE);
    }


    private int totalPage(int length, int pageSize) {
        int a = length / pageSize;
        int b = length % pageSize;
        if (b > 0) {
            return a + 1;
        }
        return a;
    }

    private void sendNextPage() {
        if (dataHands.isEmpty()) {
            Log.d("WiFiServer", "No more data to send");
            sending = false;
            updateStatusButton();
            return;
        }

        new Thread(() -> {
            sending = true;
            WiFiDataHand dataHand = dataHands.removeFirst();
            runOnUiThread(this::updateStatusButton);
            
            // 检查connectThread是否为null或已关闭
            if (connectThread == null || connectThread.isSocketClose()) {
                Log.w("WiFiServer", "Connection is closed or null, attempting to reconnect");
                runOnUiThread(() -> textState.setText(R.string.wifi_server_reconnecting));
                
                try {
                    openSocket();
                    // 等待连接建立
                    Thread.sleep(1000);
                } catch (Exception e) {
                    Log.e("WiFiServer", "Failed to reconnect", e);
                    runOnUiThread(() -> {
                        textState.setText(getString(R.string.wifi_server_reconnect_failed, e.getMessage()));
                        sending = false;
                        updateStatusButton();
                    });
                    return;
                }
            }
            
            // 再次检查connectThread
            if (connectThread != null && !connectThread.isSocketClose()) {
                // 发送数据
                Log.d("WiFiServer", "Sending data: " + dataHand.dataType + ", page " + dataHand.pageIndex + "/" + dataHand.pageSize);
                connectThread.sendData(dataHand);
            } else {
                Log.e("WiFiServer", "Failed to initialize connection thread or socket is closed");
                runOnUiThread(() -> {
                    Toast.makeText(getBaseContext(), R.string.wifi_server_connect_unable, Toast.LENGTH_LONG).show();
                    textState.setText("连接线程初始化失败或Socket已关闭");
                    sending = false;
                    updateStatusButton();
                });
            }
        }).start();
    }

    public String getWifiApIpAddress() {
        try {
            // 首先尝试获取WiFi热点IP
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().contains("wlan") || intf.getName().contains("ap")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf
                            .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()
                                && (inetAddress.getAddress().length == 4)) {
                            String ip = inetAddress.getHostAddress();
                            Log.d("WiFiServer", "Found WiFi AP IP: " + ip);
                            return ip;
                        }
                    }
                }
            }
            
            // 如果没找到WiFi热点IP，尝试获取其他非回环IP
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.isUp() && !intf.isLoopback()) {
                    for (Enumeration<InetAddress> enumIpAddr = intf
                            .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()
                                && (inetAddress.getAddress().length == 4)
                                && !inetAddress.getHostAddress().startsWith("127.")) {
                            String ip = inetAddress.getHostAddress();
                            Log.d("WiFiServer", "Found alternative IP: " + ip + " from interface: " + intf.getName());
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("WiFiServer", "Error getting IP address", ex);
        }
        return null;
    }
    
    private void checkWifiHotspotState() {
        try {
            // 检查WiFi热点状态
            if (wifiManager != null) {
                int apState = getWifiApState();
                Log.d("WiFiServer", "WiFi AP state: " + apState);
                
                if (apState != 13) { // 13 = WIFI_AP_STATE_ENABLED
                    Log.w("WiFiServer", "WiFi hotspot is not enabled, state: " + apState);
                    runOnUiThread(() -> {
                        textState.setText(R.string.wifi_server_hotspot_not_enabled);
                        Toast.makeText(mContext, R.string.wifi_server_please_enable_hotspot, Toast.LENGTH_LONG).show();
                    });
                } else {
                    Log.d("WiFiServer", "WiFi hotspot is enabled");
                    runOnUiThread(() -> {
                        textState.setText(R.string.wifi_server_hotspot_enabled);
                    });
                }
            }
        } catch (Exception e) {
            Log.e("WiFiServer", "Error checking WiFi hotspot state", e);
        }
    }
    
    // 使用反射获取WiFi热点状态，因为API可能被隐藏
    private int getWifiApState() {
        try {
            java.lang.reflect.Method method = wifiManager.getClass().getMethod("getWifiApState");
            return (int) method.invoke(wifiManager);
        } catch (Exception e) {
            Log.e("WiFiServer", "Error getting WiFi AP state", e);
            return -1;
        }
    }
    
    private void displayIPAddresses() {
        List<String> ipList = getAllIPAddresses();
        if (!ipList.isEmpty()) {
            String ipText = TextUtils.join(", ", ipList);
            ipAddressesText.setText(getString(R.string.wifi_server_ip_addresses, ipText));
        } else {
            ipAddressesText.setText(getString(R.string.wifi_server_ip_addresses, "未找到"));
        }
    }
    
    private List<String> getAllIPAddresses() {
        List<String> ipList = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.isLoopback() || !intf.isUp() || intf.isVirtual()) {
                    continue;
                }
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(":") < 0) {
                        ipList.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Main", ex.toString());
        }
        return ipList;
    }

    @Override
    protected void onDestroy() {
        if (connectThread != null) {
            connectThread.closeConnect();
            connectThread.interrupt();
            connectThread = null;
        }
        if (listenerThread != null) {
            listenerThread.closeConnect();
            listenerThread = null;
        }
        super.onDestroy();
    }

    private class WiFiServerHandler extends Handler {

        WiFiServerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DEVICE_CONNECTING:
                    if (connectThread == null) {
                        return;
                    }
                    connectThread.closeConnect();
                    connectThread = new ConnectThread(WiFiServerActivity.this, listenerThread.getSocket(), handler, IS_SERVER);
                    connectThread.start();
                    break;
                case DEVICE_CONNECTED:
                    textState.setText(R.string.wifi_server_connection_succeeded);
                    break;
                case SEND_MSG_SUCCESS:
                    textState.setText(getString(R.string.wifi_server_send_success, msg.getData().getString("MSG")));
                    if (dataHands.isEmpty()){
                        Toast.makeText(mContext,R.string.wifi_send_done,Toast.LENGTH_LONG).show();
                        sending = false;
                        updateStatusButton();
                        break;
                    }
                    sendNextPage();
                    break;
                case SEND_MSG_ERROR:
                    textState.setText(getString(R.string.wifi_server_send_fail, msg.getData().getString("MSG")));
                    sending = false;
                    break;
                case GET_MSG:
                    textState.setText(getString(R.string.wifi_server_receive_message, msg.getData().getString("MSG")));
                    break;
            }
        }
    }
}

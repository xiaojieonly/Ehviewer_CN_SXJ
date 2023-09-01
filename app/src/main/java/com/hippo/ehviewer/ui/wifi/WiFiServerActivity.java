package com.hippo.ehviewer.ui.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_INFO;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_DOWNLOAD_LABEL;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DATA_TYPE_QUICK_SEARCH;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTING;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_INFO__DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DOWNLOAD_LABEL_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_MSG;
import static com.hippo.ehviewer.client.wifi.ConnectThread.IS_SERVER;
import static com.hippo.ehviewer.client.wifi.ConnectThread.QUICK_SEARCH_DATA_KEY;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_ERROR;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_SUCCESS;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONArray;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.hippo.ehviewer.client.wifi.ConnectThread;
import com.hippo.ehviewer.client.wifi.ListenerThread;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.ui.ToolbarActivity;
import com.microsoft.appcenter.crashes.Crashes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;


public class WiFiServerActivity extends ToolbarActivity{

    private static final int REQUEST_CODE = 996;
    private Context mContext;
    private TextView textState;
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

    private final LinkedList<WiFiDataHand> dataHands = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        setContentView(R.layout.activity_wifi_server);
        textState = findViewById(R.id.receive);
        Button sendBookmark = findViewById(R.id.send_bookmark);
        sendBookmark.setOnClickListener(this::createBookmarkData);
        Button sendDownload = findViewById(R.id.send_download);
        sendDownload.setOnClickListener(this::createDownloadData);
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
                listenerThread = new ListenerThread(PORT, handler);
                listenerThread.start();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.i("ip", "getWifiApIpAddress()" + getWifiApIpAddress());
                //本地路由开启通信
                openSocket();

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> textState.setText(R.string.wifi_server_connection_fail));
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
        }
        Socket socket = new Socket(ip, PORT);
        connectThread = new ConnectThread(WiFiServerActivity.this, socket, handler, IS_SERVER);
        connectThread.start();
    }

    private void createBookmarkData(View view) {
        List<QuickSearch> list = EhDB.getAllQuickSearch();
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
            wiFiDataHand.addData(QUICK_SEARCH_DATA_KEY,objects);
            dataHands.add(wiFiDataHand);
        }
        sendNextPage();
    }

    private void createDownloadData(View view) {
        DownloadManager downloadManager = EhApplication.getDownloadManager();
        new Thread(()->{
            List<DownloadLabel> labels = EhDB.getAllDownloadLabelList();
            WiFiDataHand dataHand = new WiFiDataHand(WiFiDataHand.SEND);
            dataHand.dataType = DATA_TYPE_DOWNLOAD_LABEL;
            JSONArray labelArray = new JSONArray();
            for (int i = 0; i < labels.size(); i++) {
                labelArray.add(labels.get(i).getLabel());

            }
            dataHand.addData(DOWNLOAD_LABEL_KEY,labelArray);
            dataHands.add(dataHand);

            List<DownloadInfo> allInfo = downloadManager.getAllDownloadInfoList();

            int pageSize = 10;
            int pageCount = totalPage(allInfo.size(), pageSize);
            int j = 0;
            WiFiDataHand infoHand = new WiFiDataHand(WiFiDataHand.SEND);
            infoHand.dataType = DATA_TYPE_DOWNLOAD_INFO;
            JSONArray infoArray = new JSONArray();
            for (int i = 0; i < allInfo.size(); i++) {
                if (infoArray.size()==10){
                    infoHand.addData(DOWNLOAD_INFO__DATA_KEY,infoArray);
                    dataHands.add(infoHand);
                    infoHand = new WiFiDataHand(WiFiDataHand.SEND);
                    infoHand.dataType = DATA_TYPE_DOWNLOAD_INFO;
                    infoArray = new JSONArray();
                }
                infoArray.add(allInfo.get(i));
            }

            sendNextPage();
        }).start();
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
            return;
        }

        new Thread(() -> {
            if (connectThread != null) {
                WiFiDataHand dataHand = dataHands.removeFirst();
                if (connectThread.isSocketClose()) {
                    try {
                        openSocket();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Crashes.trackError(e);
                    }
                }
                connectThread.sendData(dataHand);
            } else {
                runOnUiThread(() -> Toast.makeText(getBaseContext(), R.string.wifi_server_connect_unable, Toast.LENGTH_LONG).show());
                Log.w("AAA", "connectThread == null");
            }
        }).start();
    }

    public String getWifiApIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().contains("wlan")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf
                            .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()
                                && (inetAddress.getAddress().length == 4)) {
                            Log.d("Main", inetAddress.getHostAddress());
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Main", ex.toString());
        }
        return null;
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
                    sendNextPage();
                    break;
                case SEND_MSG_ERROR:
                    textState.setText(getString(R.string.wifi_server_send_fail, msg.getData().getString("MSG")));
                    break;
                case GET_MSG:
                    textState.setText(getString(R.string.wifi_server_receive_message, msg.getData().getString("MSG")));
                    break;
            }
        }
    }
}

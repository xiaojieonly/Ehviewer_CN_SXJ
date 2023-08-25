package com.hippo.ehviewer.ui.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTED;
import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTING;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_MSG;
import static com.hippo.ehviewer.client.wifi.ConnectThread.IS_SERVER;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_ERROR;
import static com.hippo.ehviewer.client.wifi.ConnectThread.SEND_MSG_SUCCESS;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.hippo.ehviewer.client.wifi.ConnectThread;
import com.hippo.ehviewer.client.wifi.ListenerThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;


public class WiFiServerActivity extends AppCompatActivity implements View.OnClickListener {


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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_server);
        findViewById(R.id.send_bookmark).setOnClickListener(this);
        textState = findViewById(R.id.receive);
        /**
         * 先开启监听线程，在开启连接
         */
        if (handler == null) {
            handler = new WiFiServerHandler(getMainLooper());
        }
        listenerThread = new ListenerThread(PORT, handler);
        listenerThread.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //        开启连接线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("ip", "getWifiApIpAddress()" + getWifiApIpAddress());
                    //本地路由开启通信
                    String ip = getWifiApIpAddress();
                    if (ip != null) {
                    } else {
                        ip = "192.168.43.1";
                    }
                    Socket socket = new Socket(ip, PORT);
                    connectThread = new ConnectThread(WiFiServerActivity.this, socket, handler,IS_SERVER);
                    connectThread.start();

                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textState.setText(R.string.wifi_server_connection_fail);
                        }
                    });

                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.send_bookmark:
                //TODO implement
                sendData();
                break;
        }
    }

    int i = 1;
    private void sendData() {
        new Thread(() -> {
            if (connectThread != null) {
                JSONObject object = new JSONObject();
                object.put("text","sdf代课教师封号斗罗会计法花洒放大花洒开发德哈卡估计会送达方干哈开发技术犯规红烧豆腐好卡代发搜嘎好啦" +
                        "所肩负的忽高忽低发过火科技阿萨法高等级"+"\n这是来自Wifi热点的消息"+i);
                WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.SEND,object);
                connectThread.sendData(wiFiDataHand.toSendString());
                i++;
            } else {
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
        connectThread.closeConnect();
        connectThread.interrupt();
        connectThread = null;
        listenerThread.closeConnect();
        listenerThread.interrupt();
        listenerThread = null;
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
//                    connectThread = new ConnectThread(WiFiServerActivity.this, listenerThread.getSocket(), handler,IS_SERVER);
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
                    break;
            }
        }
    }
}

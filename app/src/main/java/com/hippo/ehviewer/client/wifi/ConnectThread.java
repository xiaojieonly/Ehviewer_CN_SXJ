package com.hippo.ehviewer.client.wifi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;
import com.microsoft.appcenter.crashes.Crashes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectThread extends Thread {

    public static final int DEVICE_CONNECTING = 1;//有设备正在连接热点
    public static final int DEVICE_CONNECTED = 2;//有设备连上热点
    public static final int DEVICE_DISCONNECTED = 3;//有设备连上热点
    public static final int SEND_MSG_SUCCESS = 4;//发送消息成功
    public static final int SEND_MSG_ERROR = 5;//发送消息失败
    public static final int GET_MSG = 6;//获取新消息

    public static final int IS_SERVER = 101;
    public static final int IS_CLIENT = 102;

    public static final int DATA_TYPE_QUICK_SEARCH = 1001;
    public static final String QUICK_SEARCH_DATA_KEY = "quick_search";
    public static final int DATA_TYPE_DOWNLOAD_INFO = 1002;
    public static final String DOWNLOAD_INFO_DATA_KEY = "download_info";
    public static final int DATA_TYPE_DOWNLOAD_LABEL = 1003;
    public static final String DOWNLOAD_LABEL_KEY = "download_label";

    public static final int DATA_TYPE_FAVORITE_INFO = 1004;
    public static final String FAVORITE_INFO_DATA_KEY = "favorite_info";
    private final Socket socket;
    private final Handler handler;
    private final int connectKind;
    private OutputStream outputStream;
    Context context;

    private boolean processed = true;

    private boolean close = false;

    public ConnectThread(Context context, Socket socket, Handler handler, int connectKind) {
        setName("ConnectThread");
        Log.i("ConnectThread", "ConnectThread");
        this.connectKind = connectKind;
        this.socket = socket;
        this.handler = handler;
        this.context = context;
    }

    @Override
    public void run() {
        if (socket == null) {
            return;
        }
        handler.sendEmptyMessage(DEVICE_CONNECTED);
        try {
            InputStream inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            while (!isInterrupted()) {
                //获取数据流
                WiFiDataHand wiFiDataHand = isToResponse(inputStream);
                if (close) {
                    break;
                }
                if (wiFiDataHand != null) {
                    if (connectKind == IS_CLIENT) {
                        solveTheData(wiFiDataHand);
                    } else {
                        sendNextData(wiFiDataHand);
                    }
                }
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            Crashes.trackError(e);
        }
    }

    private void sendNextData(WiFiDataHand wiFiDataHand) {
        if (wiFiDataHand.messageType != WiFiDataHand.RECEIVED) {
            return;
        }
        Message message = Message.obtain();
        message.what = SEND_MSG_SUCCESS;
        Bundle bundle = new Bundle();
        bundle.putString("MSG", wiFiDataHand.toString());
        message.setData(bundle);
        handler.sendMessage(message);
    }

    private void solveTheData(WiFiDataHand wiFiDataHand) {
        if (wiFiDataHand.messageType != WiFiDataHand.SEND) {
            return;
        }
        Message message = Message.obtain();
        message.what = GET_MSG;
        Bundle bundle = new Bundle();
        bundle.putString("MSG", wiFiDataHand.toString());
        message.setData(bundle);
        handler.sendMessage(message);
    }


    /**
     * 发送数据
     */
    public void sendData(WiFiDataHand dataHand) {
        try {
            if (outputStream == null) {
                outputStream = socket.getOutputStream();
            }
            Log.i("ConnectThread", "发送数据:" + (outputStream == null));
            outputStream.write(dataHand.getSendBytes());
            outputStream.flush();
            Log.i("ConnectThread", "发送消息：" + dataHand);
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            message.what = SEND_MSG_ERROR;
            Bundle bundle = new Bundle();
            bundle.putString("MSG", dataHand.toString());
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }

    public void dataProcessed(WiFiDataHand response) {
        processed = true;
        WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.RECEIVED);
        wiFiDataHand.setData(response.getData());
        new Thread(()-> sendData(wiFiDataHand)).start();
    }

    private WiFiDataHand isToResponse(InputStream inputStream) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024];
            String result = "";
            for (int length; (length = inputStream.read(bytes)) != -1; ) {
                outputStream.write(bytes, 0, length);
                result = outputStream.toString("UTF-8");
                if (result.endsWith("}:END")) {
                    result = result.substring(0, result.length() - 4);
                    break;
                }
            }
            if (result.isEmpty()) {
                return null;
            }
            return new WiFiDataHand(result);
        } catch (Throwable throwable) {
            Crashes.trackError(throwable);
            if (socket.isClosed()) {
                interrupt();
            }
            return null;
        }
    }

    public void closeConnect() {
        try {
            socket.close();
            interrupt();
            close = true;
        } catch (IOException|NullPointerException e) {
            Crashes.trackError(e);
        }
    }

    public boolean isSocketClose() {
        if (socket==null){
            return true;
        }
        return socket.isClosed();
    }
}

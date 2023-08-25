package com.hippo.ehviewer.client.wifi;

import static com.hippo.ehviewer.client.wifi.ConnectThread.DEVICE_CONNECTING;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.hippo.ehviewer.ui.wifi.WiFiServerActivity;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ListenerThread extends Thread {
    private ServerSocket serverSocket = null;
    private Handler handler;
    private int port;
    private Socket socket;

    public ListenerThread(int port, Handler handler) {
        setName("ListenerThread");
        this.port = port;
        this.handler = handler;
        try {
            serverSocket = new ServerSocket(port);//监听本机的12345端口
            serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        try {
            while (!interrupted()) {
                Log.i("ListennerThread", "阻塞");
                //阻塞，等待设备连接
                if (serverSocket != null) {
                    socket = serverSocket.accept();
                } else {
                    try {
                        serverSocket = new ServerSocket(port);//监听本机的12345端口
                        serverSocket.setReuseAddress(true);
                    } catch (IOException ignore) {

                    }
                }
                Message message = Message.obtain();
                message.what = DEVICE_CONNECTING;
                handler.sendMessage(message);
                Thread.sleep(500);
            }
            serverSocket.close();
        } catch (IOException | InterruptedException e) {
            Log.i("ListennerThread", "error:" + e.getMessage());
            e.printStackTrace();
            interrupt();
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public void closeConnect() {
        try {
            socket.close();
            serverSocket.close();
            interrupt();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }
}

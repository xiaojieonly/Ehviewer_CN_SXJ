package com.hippo.ehviewer.client.wifi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectThread extends Thread {

    public static final int DEVICE_CONNECTING = 1;
    public static final int DEVICE_CONNECTED = 2;
    public static final int DEVICE_DISCONNECTED = 3;
    public static final int SEND_MSG_SUCCESS = 4;
    public static final int SEND_MSG_ERROR = 5;
    public static final int GET_MSG = 6;
    public static final int GET_FILE_CHUNK = 7;
    public static final int GET_DIR_MANIFEST = 8;
    public static final int GET_DIR_ACK = 9;

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

    public static final int DATA_TYPE_DOWNLOAD_DIR_MANIFEST = 1005;
    public static final int DATA_TYPE_DOWNLOAD_DIR_ACK = 1006;
    public static final int DATA_TYPE_DOWNLOAD_DIR_FILE = 1007;

    private final Socket socket;
    private final Handler handler;
    private final int connectKind;
    private OutputStream outputStream;
    Context context;

    private boolean close = false;

    private final Object sendLock = new Object();

    @Nullable
    private volatile FileChunkReceiver fileChunkReceiver;

    /** 在读取线程同步处理文件块，避免主线程队列堆积大对象。 */
    public interface FileChunkReceiver {
        void onFileChunk(@NonNull WiFiDownloadMigrationHelper.FileChunk chunk);
    }

    public void setFileChunkReceiver(@Nullable FileChunkReceiver receiver) {
        this.fileChunkReceiver = receiver;
    }

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

            while (!isInterrupted() && !close) {
                WiFiFrame frame = readNextFrame(inputStream);
                if (close) {
                    break;
                }
                if (frame != null) {
                    dispatchFrame(frame);
                }
            }
        } catch (IOException e) {
            if (!close) {
                Analytics.recordException(e);
            }
        } finally {
            close = true;
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
            handler.sendEmptyMessage(DEVICE_DISCONNECTED);
        }
    }

    @Nullable
    private WiFiFrame readNextFrame(InputStream inputStream) {
        try {
            return WiFiFrameCodec.readFrame(inputStream);
        } catch (EOFException e) {
            Log.i("ConnectThread", "Connection closed by peer");
            close = true;
            return null;
        } catch (IOException e) {
            if (!close && !socket.isClosed() && !isInterrupted()) {
                Analytics.recordException(e);
            }
            close = true;
            return null;
        }
    }

    private void dispatchFrame(@NonNull WiFiFrame frame) {
        if (connectKind == IS_CLIENT) {
            if (frame.messageType != WiFiDataHand.SEND) {
                return;
            }
            if (frame.dataType == DATA_TYPE_DOWNLOAD_DIR_FILE) {
                WiFiDownloadMigrationHelper.FileChunk chunk =
                        WiFiFrameCodec.decodeFileChunk(frame);
                if (chunk != null) {
                    FileChunkReceiver receiver = fileChunkReceiver;
                    if (receiver != null) {
                        receiver.onFileChunk(chunk);
                    } else {
                        Message message = Message.obtain();
                        message.what = GET_FILE_CHUNK;
                        message.obj = chunk;
                        handler.sendMessage(message);
                    }
                }
            } else if (frame.dataType == DATA_TYPE_DOWNLOAD_DIR_MANIFEST) {
                WiFiDownloadMigrationHelper.DirManifest manifest =
                        WiFiFrameCodec.decodeManifest(frame);
                if (manifest != null) {
                    Message message = Message.obtain();
                    message.what = GET_DIR_MANIFEST;
                    message.obj = manifest;
                    handler.sendMessage(message);
                }
            } else {
                WiFiDataHand hand = WiFiFrameCodec.decodeDataHand(frame);
                deliverDataHand(hand);
            }
        } else {
            if (frame.messageType != WiFiDataHand.RECEIVED) {
                return;
            }
            if (frame.dataType == DATA_TYPE_DOWNLOAD_DIR_ACK) {
                WiFiDownloadMigrationHelper.DirAck ack = WiFiFrameCodec.decodeAck(frame);
                if (ack != null) {
                    Message message = Message.obtain();
                    message.what = GET_DIR_ACK;
                    message.obj = ack;
                    handler.sendMessage(message);
                }
            } else {
                WiFiDataHand hand = WiFiFrameCodec.decodeDataHand(frame);
                Message message = Message.obtain();
                message.what = SEND_MSG_SUCCESS;
                Bundle bundle = new Bundle();
                bundle.putString("MSG", hand.toString());
                message.obj = hand;
                message.setData(bundle);
                handler.sendMessage(message);
            }
        }
    }

    private void deliverDataHand(@NonNull WiFiDataHand hand) {
        Message message = Message.obtain();
        message.what = GET_MSG;
        Bundle bundle = new Bundle();
        bundle.putString("MSG", hand.toString());
        message.obj = hand;
        message.setData(bundle);
        handler.sendMessage(message);
    }

    public void sendData(WiFiDataHand dataHand) {
        sendFrame(WiFiFrameCodec.encode(dataHand), dataHand.toString());
    }

    public void sendFrame(@NonNull WiFiFrame frame) {
        sendFrame(frame, frame.dataType + "/" + frame.pageIndex);
    }

    private void sendFrame(@NonNull WiFiFrame frame, String logLabel) {
        if (close || socket.isClosed()) {
            return;
        }
        try {
            synchronized (sendLock) {
                if (close || socket.isClosed()) {
                    return;
                }
                if (outputStream == null) {
                    outputStream = socket.getOutputStream();
                }
                WiFiFrameCodec.writeFrame(outputStream, frame);
            }
            Log.i("ConnectThread", "发送帧：" + logLabel);
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            message.what = SEND_MSG_ERROR;
            Bundle bundle = new Bundle();
            bundle.putString("MSG", logLabel);
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }

    public void dataProcessed(WiFiDataHand response) {
        WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.RECEIVED);
        wiFiDataHand.dataType = response.dataType;
        wiFiDataHand.setData(response.getData());
        wiFiDataHand.pageIndex = response.pageIndex;
        wiFiDataHand.pageSize = response.pageSize;
        new Thread(() -> sendData(wiFiDataHand)).start();
    }

    public void sendDirAck(long gid, @NonNull String dirname,
            @NonNull java.util.List<String> filesNeeded) {
        new Thread(() -> sendFrame(WiFiFrameCodec.encodeAck(gid, dirname, filesNeeded))).start();
    }

    public void closeConnect() {
        try {
            socket.close();
            interrupt();
            close = true;
        } catch (IOException | NullPointerException e) {
            Analytics.recordException(e);
        }
    }

    public boolean isSocketClose() {
        if (socket == null) {
            return true;
        }
        return socket.isClosed();
    }
}

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
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_DIR_MANIFEST;
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_FILE_CHUNK;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.hippo.ehviewer.client.wifi.WiFiDownloadMigrationHelper;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.unifile.UniFile;
import com.hippo.util.PermissionRequester;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WiFiClientActivity extends AppCompatActivity {

    private final int pCode = 88888;

    private TextView textState;
    private TextView receiveMessage;
    private ConnectThread connectThread;

    private ListenerThread listenerThread;

    private static final int PORT = 54321;
    private WifiManager wifiManager;

    private TextView statusInit;

    private WiFiClientHandler handler;

    private final WiFiDownloadMigrationHelper.FileWriteState fileWriteState =
            new WiFiDownloadMigrationHelper.FileWriteState();

    private final Map<String, UniFile> galleryDirCache = new HashMap<>();

    private final Map<Long, String> dirnameByGid = new HashMap<>();

    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor();

    private int dirFilesCompleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_client);
        findViewById(R.id.connect_server).setOnClickListener(this::connect);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        boolean result = PermissionRequester.request(this, Manifest.permission.CHANGE_WIFI_STATE,
                getString(R.string.wifi_server_no_permission),pCode);
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
                connectThread.setFileChunkReceiver(this::applyFileChunk);
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

    private String getIp() {
       try {
           if (!wifiManager.isWifiEnabled())
               wifiManager.setWifiEnabled(true);
           WifiInfo wi = wifiManager.getConnectionInfo();
           int ipAdd = wi.getIpAddress();
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

    private static String getWifiRouteIPAddress(Context context) {
        WifiManager wifi_service = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifi_service.getDhcpInfo();
        String routeIp = Formatter.formatIpAddress(dhcpInfo.gateway);
        Log.i("route ip", "wifi route ip：" + routeIp);

        return routeIp;
    }

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
        return connectedIP;
    }

    @Override
    protected void onDestroy() {
        if (connectThread != null) {
            connectThread.setFileChunkReceiver(null);
            connectThread.closeConnect();
            connectThread = null;
        }
        migrationExecutor.shutdownNow();
        if (listenerThread!=null){
            listenerThread.closeConnect();
            listenerThread = null;
        }
        super.onDestroy();
    }

    @NonNull
    private static String dirKey(long gid, @NonNull String dirname) {
        return gid + ":" + dirname;
    }

    private void onReceiveMsg(@NonNull WiFiDataHand response) {
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

    private void dealWithDownloadDirManifest(@NonNull WiFiDownloadMigrationHelper.DirManifest manifest) {
        migrationExecutor.execute(() -> processDownloadDirManifest(manifest));
    }

    private void processDownloadDirManifest(
            @NonNull WiFiDownloadMigrationHelper.DirManifest manifest) {
        if (WiFiDownloadMigrationHelper.getDownloadRoot() == null) {
            runOnUiThread(() -> Toast.makeText(this,
                    R.string.wifi_download_dir_no_location, Toast.LENGTH_LONG).show());
            if (connectThread != null) {
                connectThread.sendDirAck(manifest.gid, manifest.dirname, new ArrayList<>());
            }
            return;
        }
        UniFile galleryDir = WiFiDownloadMigrationHelper.ensureGalleryDir(
                manifest.gid, manifest.dirname);
        if (galleryDir == null) {
            if (connectThread != null) {
                connectThread.sendDirAck(manifest.gid, manifest.dirname, new ArrayList<>());
            }
            return;
        }
        dirnameByGid.put(manifest.gid, manifest.dirname);
        galleryDirCache.put(dirKey(manifest.gid, manifest.dirname), galleryDir);
        if (manifest.pageIndex == 1) {
            dirFilesCompleted = 0;
        }
        List<String> needed = WiFiDownloadMigrationHelper.filterFilesNeeded(
                galleryDir, manifest.files);
        if (connectThread != null) {
            connectThread.sendDirAck(manifest.gid, manifest.dirname, needed);
        }
        String progress = getString(R.string.wifi_download_dir_manifest_received,
                manifest.dirname, manifest.pageIndex, manifest.pageSize,
                manifest.files.size() - needed.size(), needed.size());
        updateReceiveMessage(progress);
    }

    /**
     * 在 ConnectThread 读取线程同步调用：写盘完成后再读下一帧，限制同时在内存中的分块数量。
     */
    private void applyFileChunk(@NonNull WiFiDownloadMigrationHelper.FileChunk chunk) {
        String dirname = dirnameByGid.get(chunk.gid);
        if (dirname == null) {
            return;
        }
        String key = dirKey(chunk.gid, dirname);
        UniFile galleryDir = galleryDirCache.get(key);
        if (galleryDir == null) {
            galleryDir = WiFiDownloadMigrationHelper.ensureGalleryDir(chunk.gid, dirname);
            if (galleryDir == null) {
                return;
            }
            galleryDirCache.put(key, galleryDir);
        }
        String fileKey = key + "/" + chunk.name;
        try {
            long offset = fileWriteState.getOffset(fileKey);
            if (chunk.chunkIndex == 0) {
                offset = 0;
                fileWriteState.clear(fileKey);
                UniFile existing = WiFiDownloadMigrationHelper.resolveFileInGallery(
                        galleryDir, chunk.name);
                if (existing != null && existing.isFile()) {
                    existing.delete();
                }
            }
            if (chunk.data.length > 0) {
                WiFiDownloadMigrationHelper.writeChunk(
                        galleryDir, chunk.name, chunk.data, offset);
                fileWriteState.addWritten(fileKey, chunk.data.length);
            }
            if (chunk.lastChunk) {
                UniFile file = WiFiDownloadMigrationHelper.resolveFileInGallery(
                        galleryDir, chunk.name);
                if (file != null && file.isFile()) {
                    if (!WiFiDownloadMigrationHelper.verifyFileMd5(file, chunk.fileMd5)) {
                        file.delete();
                        Analytics.recordException(new IOException(
                                "MD5 mismatch: " + chunk.name));
                        runOnUiThread(() -> Toast.makeText(this,
                                R.string.wifi_download_dir_file_failed, Toast.LENGTH_SHORT).show());
                    }
                }
                fileWriteState.clear(fileKey);
                dirFilesCompleted++;
                if (dirFilesCompleted % 10 == 0) {
                    final int count = dirFilesCompleted;
                    updateReceiveMessage(getString(R.string.wifi_download_dir_files_progress, count));
                }
            }
        } catch (IOException e) {
            Analytics.recordException(e);
        }
    }

    private void dealWithDownloadDirFileChunk(@NonNull WiFiDownloadMigrationHelper.FileChunk chunk) {
        applyFileChunk(chunk);
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
                    if (msg.obj instanceof WiFiDataHand) {
                        onReceiveMsg((WiFiDataHand) msg.obj);
                    } else if (msg.getData() != null) {
                        onReceiveMsg(new WiFiDataHand(msg.getData().getString("MSG")));
                    }
                    break;
                case GET_DIR_MANIFEST:
                    if (msg.obj instanceof WiFiDownloadMigrationHelper.DirManifest) {
                        dealWithDownloadDirManifest(
                                (WiFiDownloadMigrationHelper.DirManifest) msg.obj);
                    }
                    break;
                case GET_FILE_CHUNK:
                    if (msg.obj instanceof WiFiDownloadMigrationHelper.FileChunk) {
                        dealWithDownloadDirFileChunk(
                                (WiFiDownloadMigrationHelper.FileChunk) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}

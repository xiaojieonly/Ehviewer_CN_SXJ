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
import static com.hippo.ehviewer.client.wifi.ConnectThread.GET_DIR_ACK;
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
import android.widget.AdapterView;
import android.widget.Button;
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
import com.hippo.ehviewer.client.wifi.WiFiDownloadMigrationHelper;
import com.hippo.ehviewer.client.wifi.WiFiFrame;
import com.hippo.ehviewer.client.wifi.WiFiFrameCodec;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.ui.ToolbarActivity;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class WiFiServerActivity extends ToolbarActivity implements AdapterView.OnItemSelectedListener {

    private static final int REQUEST_CODE = 996;

    private ConnectThread connectThread;

    private ListenerThread listenerThread;

    private static final int PORT = 54321;

    private WiFiServerHandler handler;

    private volatile boolean sending = false;

    private final LinkedList<WiFiFrame> sendQueue = new LinkedList<>();

    private final Map<String, WiFiDownloadMigrationHelper.GalleryDirEntry> dirEntryMap = new HashMap<>();

    private final Map<String, Map<String, WiFiDownloadMigrationHelper.FileMeta>> dirFileMetaMap =
            new HashMap<>();

    private Context mContext;
    private TextView textState;

    private Button statusButton;

    private int selectIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        setContentView(R.layout.activity_wifi_server);
        textState = findViewById(R.id.receive);
        Spinner spinner = findViewById(R.id.migrate_spinner);
        spinner.setOnItemSelectedListener(this);
        statusButton = findViewById(R.id.status_change);
        statusButton.setOnClickListener(this::onStatusChange);
        updateStatusButton();
        boolean result = requestMyPermission();
        if (result) {
            openConnectThread();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
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
        new Thread(() -> {
            listenerThread = new ListenerThread(PORT, handler);
            listenerThread.start();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.i("ip", "getWifiApIpAddress()" + getWifiApIpAddress());
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      selectIndex = position;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        selectIndex = 999;
    }

    private void stopMigration() {
        sending = false;
        sendQueue.clear();
        updateStatusButton();
    }

    private void onStatusChange(View view) {
        if (sending) {
            stopMigration();
            return;
        }
        if (!sendQueue.isEmpty()){
            sending = true;
            updateStatusButton();
            sendNextPage();
        }else{
            sending = true;
            updateStatusButton();
            switch (selectIndex){
                case 0:
                    createBookmarkData();
                    break;
                case 1:
                    createFavoriteData();
                    break;
                case 2:
                    createDownloadData();
                    break;
                case 3:
                    createDownloadDirData();
                    break;
                default:
                    break;
            }
        }
    }

    private void createFavoriteData() {
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
                enqueueHand(wiFiDataHand);
            }
            sendNextPage();
        }).start();
    }

    private void createBookmarkData() {
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
                enqueueHand(wiFiDataHand);
            }
            sendNextPage();
        }).start();
    }

    private void createDownloadData() {
        new Thread(() -> {
            List<DownloadLabel> labels = EhDB.getAllDownloadLabelList();
            WiFiDataHand dataHand = new WiFiDataHand(WiFiDataHand.SEND);
            dataHand.dataType = DATA_TYPE_DOWNLOAD_LABEL;
            JSONArray labelArray = new JSONArray();
            for (int i = 0; i < labels.size(); i++) {
                labelArray.add(labels.get(i).getLabel());

            }
            dataHand.addData(DOWNLOAD_LABEL_KEY, labelArray);
            enqueueHand(dataHand);

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
                enqueueHand(infoHand);
            }
            sendNextPage();
        }).start();
    }

    private void createDownloadDirData() {
        new Thread(() -> {
            dirEntryMap.clear();
            dirFileMetaMap.clear();
            List<WiFiDownloadMigrationHelper.GalleryDirEntry> entries =
                    WiFiDownloadMigrationHelper.scanGalleryDirs();
            if (entries.isEmpty()) {
                sending = false;
                runOnUiThread(() -> {
                    Toast.makeText(mContext,
                            R.string.wifi_download_dir_empty, Toast.LENGTH_LONG).show();
                    updateStatusButton();
                });
                return;
            }
            int total = entries.size();
            for (int i = 0; i < total; i++) {
                if (!sending) {
                    return;
                }
                WiFiDownloadMigrationHelper.GalleryDirEntry entry = entries.get(i);
                List<WiFiDownloadMigrationHelper.FileMeta> files =
                        WiFiDownloadMigrationHelper.scanGalleryDir(entry.dir);
                String key = dirKey(entry.gid, entry.dirname);
                dirEntryMap.put(key, entry);
                Map<String, WiFiDownloadMigrationHelper.FileMeta> metaMap = new HashMap<>();
                for (WiFiDownloadMigrationHelper.FileMeta meta : files) {
                    metaMap.put(meta.name, meta);
                }
                dirFileMetaMap.put(key, metaMap);
                sendQueue.add(WiFiFrameCodec.encodeManifest(
                        entry.gid, entry.dirname, files, i + 1, total));
            }
            if (sending) {
                sendNextPage();
            }
        }).start();
    }

    @NonNull
    private static String dirKey(long gid, @NonNull String dirname) {
        return gid + ":" + dirname;
    }

    private void enqueueHand(@NonNull WiFiDataHand hand) {
        sendQueue.add(WiFiFrameCodec.encode(hand));
    }

    private void updateStatusButton(){
        String content;
        if (!sending) {
            if (sendQueue.isEmpty()){
                content = getString(R.string.wifi_send_start,"");
            }else{
                content = getString(R.string.wifi_send_start,"("+sendQueue.size()+")");
            }
            statusButton.setText(content);
            return;
        }
        if (sendQueue.isEmpty()){
            content = getString(R.string.wifi_send_stop,"");
        }else{
            content = getString(R.string.wifi_send_stop,"("+sendQueue.size()+")");
        }
        statusButton.setText(content);
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
        if (!sending) {
            return;
        }
        if (sendQueue.isEmpty()) {
            sending = false;
            runOnUiThread(() -> {
                updateStatusButton();
                Toast.makeText(mContext, R.string.wifi_send_done, Toast.LENGTH_LONG).show();
            });
            return;
        }

        new Thread(() -> {
            if (!sending) {
                return;
            }
            if (connectThread != null && !connectThread.isSocketClose()) {
                WiFiFrame frame = sendQueue.removeFirst();
                connectThread.sendFrame(frame);
            } else {
                sending = false;
                runOnUiThread(() -> Toast.makeText(getBaseContext(),
                        R.string.wifi_server_connect_unable, Toast.LENGTH_LONG).show());
                Log.w("WiFiServer", "connectThread unavailable");
            }
        }).start();
    }

    private void onDirAck(@NonNull WiFiDownloadMigrationHelper.DirAck ack) {
        new Thread(() -> {
            if (!sending) {
                return;
            }
            String key = dirKey(ack.gid, ack.dirname);
            WiFiDownloadMigrationHelper.GalleryDirEntry entry = dirEntryMap.get(key);
            Map<String, WiFiDownloadMigrationHelper.FileMeta> metaMap = dirFileMetaMap.get(key);
            if (entry == null || metaMap == null || connectThread == null) {
                if (sending) {
                    sendNextPage();
                }
                return;
            }
            int fileTotal = ack.filesNeeded.size();
            int fileIndex = 0;
            for (String name : ack.filesNeeded) {
                if (!sending) {
                    return;
                }
                WiFiDownloadMigrationHelper.FileMeta meta = metaMap.get(name);
                if (meta == null) {
                    continue;
                }
                fileIndex++;
                final int currentIndex = fileIndex;
                final String dirname = ack.dirname;
                runOnUiThread(() -> textState.setText(getString(
                        R.string.wifi_download_dir_migrating,
                        dirname, currentIndex, fileTotal)));
                if (!sendFileChunks(entry, meta)) {
                    if (sending) {
                        Analytics.recordException(new IOException(
                                "Failed to send file: " + name));
                    }
                }
            }
            if (sending) {
                sendNextPage();
            }
        }).start();
    }

    private boolean sendFileChunks(@NonNull WiFiDownloadMigrationHelper.GalleryDirEntry entry,
            @NonNull WiFiDownloadMigrationHelper.FileMeta meta) {
        UniFile file = WiFiDownloadMigrationHelper.resolveFileInGallery(entry.dir, meta.name);
        if (file == null || !file.isFile()) {
            return false;
        }
        InputStream is = null;
        try {
            is = file.openInputStream();
            if (is == null) {
                return false;
            }
            int chunkSize = WiFiDownloadMigrationHelper.FILE_CHUNK_SIZE;
            long fileLen = meta.size > 0 ? meta.size : file.length();
            int chunkTotal = (int) ((fileLen + chunkSize - 1) / chunkSize);
            if (chunkTotal <= 0) {
                chunkTotal = 1;
            }
            byte[] buffer = new byte[chunkSize];
            int chunkIndex = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (!sending) {
                    return false;
                }
                int chunkLen = read;
                byte[] data = copyOf(buffer, chunkLen);
                boolean last = chunkIndex + 1 >= chunkTotal;
                WiFiFrame frame = WiFiFrameCodec.encodeFileChunk(
                        entry.gid, meta.name, meta.md5, chunkIndex, chunkTotal, data, last);
                connectThread.sendFrame(frame);
                chunkIndex++;
            }
            if (chunkIndex == 0) {
                WiFiFrame frame = WiFiFrameCodec.encodeFileChunk(
                        entry.gid, meta.name, meta.md5, 0, 1, new byte[0], true);
                connectThread.sendFrame(frame);
            }
            return true;
        } catch (IOException e) {
            Analytics.recordException(e);
            return false;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @NonNull
    private static byte[] copyOf(@NonNull byte[] src, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, 0, out, 0, len);
        return out;
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
                    if (listenerThread == null || listenerThread.getSocket() == null) {
                        return;
                    }
                    if (connectThread != null) {
                        connectThread.closeConnect();
                        connectThread = null;
                    }
                    connectThread = new ConnectThread(WiFiServerActivity.this,
                            listenerThread.getSocket(), handler, IS_SERVER);
                    connectThread.start();
                    break;
                case DEVICE_CONNECTED:
                    textState.setText(R.string.wifi_server_connection_succeeded);
                    break;
                case ConnectThread.DEVICE_DISCONNECTED:
                    textState.setText(R.string.wifi_server_disconnect);
                    break;
                case SEND_MSG_SUCCESS:
                    textState.setText(getString(R.string.wifi_server_send_success,
                            msg.getData().getString("MSG")));
                    if (!sending) {
                        updateStatusButton();
                        break;
                    }
                    if (sendQueue.isEmpty()){
                        Toast.makeText(mContext,R.string.wifi_send_done,Toast.LENGTH_LONG).show();
                        sending = false;
                        updateStatusButton();
                        break;
                    }
                    sendNextPage();
                    break;
                case SEND_MSG_ERROR:
                    textState.setText(getString(R.string.wifi_server_send_fail,
                            msg.getData().getString("MSG")));
                    sending = false;
                    updateStatusButton();
                    break;
                case GET_DIR_ACK:
                    if (sending && msg.obj instanceof WiFiDownloadMigrationHelper.DirAck) {
                        onDirAck((WiFiDownloadMigrationHelper.DirAck) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}

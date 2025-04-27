package com.hippo.ehviewer.download;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.TorrentDownloadMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 文件下载工具
 */
public class DownloadTorrentManager {
    private static final String TAG = DownloadTorrentManager.class.getName();
    private static DownloadTorrentManager downloadTorrentManager;
    private final OkHttpClient okHttpClient;

    private Handler handler;

    public static DownloadTorrentManager get(OkHttpClient okHttpClient) {
        if (downloadTorrentManager == null) {
            downloadTorrentManager = new DownloadTorrentManager(okHttpClient);
        }
        return downloadTorrentManager;
    }

    private DownloadTorrentManager(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * url 下载连接
     * saveDir 储存下载文件的SDCard目录
     * listener 下载监听
     */
    public void download(final String url, final String saveDir, final String name, Handler handler, Context context) {
        this.handler = handler;
        Request request = new Request.Builder().url(url).build();
        // 储存下载文件的目录
        String savePath = null;
        try {
            savePath = isExistDir(saveDir);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        File file = new File(savePath, name);
        if (file.exists()) {
            sendMessage(saveDir, name, 200, false);
            return;
        }
        sendMessage(url, name, 0, false);
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 下载失败
                EhApplication.removeDownloadTorrent(context, url);
                sendMessage(url, name, -1, true);

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len;
                FileOutputStream fos = null;
                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();

                    fos = new FileOutputStream(file);
                    long sum = 0;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        sum += len;
                        int progress = (int) (sum * 1.0f / total * 100);
                        // 下载中
                        sendMessage(url, name, progress, false);
                    }
                    fos.flush();
                    // 下载完成
                    EhApplication.removeDownloadTorrent(context, url);
                    sendMessage(saveDir, name, 100, false);
                } catch (Exception e) {
                    EhApplication.removeDownloadTorrent(context, url);
                    sendMessage(url, name, -1, true);
                } finally {
                    try {
                        if (is != null)
                            is.close();
                    } catch (IOException ignored) {
                    }
                    try {
                        if (fos != null)
                            fos.close();
                    } catch (IOException e) {
                    }
                }
            }
        });
    }

    /**
     * saveDir
     * 判断下载目录是否存在
     */
    private String isExistDir(String saveDir) throws IOException {
        // 下载位置
        File downloadFile = new File(saveDir);
        if (!downloadFile.mkdirs()) {
            downloadFile.createNewFile();
        }
        return downloadFile.getAbsolutePath();
    }

    /**
     * 获取SD卡路径
     *
     * @return
     */
    public static String getSDCardPath() {
        String sdCardPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
        Log.i(TAG, "getSDCardPath:" + sdCardPath);
        return sdCardPath;
    }

    /**
     * url
     * 从下载连接中解析出文件名
     */
    @NonNull
    public static String getNameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private void sendMessage(String path, String name, int progress, boolean failed) {
        Message message = torrentDownLoadMessage(path, name, progress, failed);
        handler.sendMessage(message);
    }


    private Message torrentDownLoadMessage(String path, String name, int progress, boolean failed) {

        Message result = handler.obtainMessage();
        Bundle data = new Bundle();

        TorrentDownloadMessage message = new TorrentDownloadMessage();

        message.failed = failed;
        message.progress = progress;
        message.path = path;
        message.name = name;

        data.putParcelable("torrent_download_message", message);

        result.setData(data);

        return result;
    }

}
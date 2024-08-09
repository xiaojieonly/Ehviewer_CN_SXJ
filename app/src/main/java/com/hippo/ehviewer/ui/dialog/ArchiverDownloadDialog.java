package com.hippo.ehviewer.ui.dialog;

import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_LONG;
import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_SHORT;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.ArchiverData;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.exception.NoHAtHClientException;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.util.GZIPUtils;
import com.hippo.scene.SceneFragment;
import com.hippo.unifile.UniFile;
import com.hippo.util.FileUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class ArchiverDownloadDialog implements
        DialogInterface.OnDismissListener, EhClient.Callback<ArchiverData> {
    final private GalleryDetail galleryDetail;
    final private Context context;
    final private GalleryDetailScene detailScene;
    private final DownloadReceiver downloadReceiver;

    private Dialog dialog;

    private TextView currentFunds;
    private TextView originalCost;
    private TextView originalSize;
    private TextView resampleCost;
    private TextView resampleSize;
    private Button resampleDownload;
    private Button originalDownload;

    private ProgressBar progressBar;
    private LinearLayout body;

    private long myDownloadId;


    private ArchiverData data = new ArchiverData();


    public ArchiverDownloadDialog(GalleryDetail galleryDetail, GalleryDetailScene detailScene) {
        this.galleryDetail = galleryDetail;
        this.detailScene = detailScene;
        this.context = detailScene.getEHContext();
        downloadReceiver = new DownloadReceiver(galleryDetail);
    }

    public void showDialog() {
        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_archiver_title)
                .setView(R.layout.dialog_archiver)
                .setOnDismissListener(this)
                .show();
        currentFunds = dialog.findViewById(R.id.dialog_archiver_current_funds);
        originalCost = dialog.findViewById(R.id.dialog_archiver_original_cost);
        originalSize = dialog.findViewById(R.id.dialog_archiver_original_size);
        resampleCost = dialog.findViewById(R.id.dialog_archiver_resample_cost);
        resampleSize = dialog.findViewById(R.id.dialog_archiver_resample_size);
        resampleDownload = dialog.findViewById(R.id.dialog_archiver_resample_download);
        originalDownload = dialog.findViewById(R.id.dialog_archiver_original_download);
        progressBar = dialog.findViewById(R.id.dialog_archiver_progress);
        body = dialog.findViewById(R.id.dialog_archiver_body);
        resampleDownload.setOnClickListener(this::onArchiverDownload);
        originalDownload.setOnClickListener(this::onArchiverDownload);
        EhRequest mRequest = new EhRequest().setMethod(EhClient.METHOD_ARCHIVER)
                .setArgs(galleryDetail.archiveUrl, galleryDetail.gid, galleryDetail.token)
                .setCallback(this);
        assert mRequest != null;
        EhApplication.getEhClient(context).execute(mRequest);
    }

    private void onArchiverDownload(View view) {
        try {
            String url = null;
            String dltype = null;
            String dlcheck = null;
            if (view == originalDownload) {
                url = data.originalUrl;
                dltype = "org";
                dlcheck = "Download Original Archive";
            } else if (view == resampleDownload) {
                url = data.resampleUrl;
                dltype = "res";
                dlcheck = "Download Resample Archive";
            }
            if (url == null) {
                return;
            }
            MainActivity activity = detailScene.getActivity2();
            if (null != context && null != activity && galleryDetail != null) {

                EhRequest request = new EhRequest();
                request.setMethod(EhClient.METHOD_DOWNLOAD_ARCHIVER);
                request.setArgs(url, galleryDetail.archiveUrl, dltype, dlcheck);
                request.setCallback(new DownloadArchiverListener(context, activity.getStageId(), detailScene.getTag(), this));
                EhApplication.getEhClient(context).execute(request);
            }
        } finally {
            progressBar.setVisibility(View.VISIBLE);
            body.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {

    }

    @Override
    public void onSuccess(ArchiverData result) {
        data = result;
        String cF;
        if (Settings.getGallerySite() == EhUrl.SITE_E) {
            cF = context.getString(R.string.archiver_dialog_current_funds) + data.funds;
        } else {
            cF = data.funds;
        }

        currentFunds.setText(cF);
        String oC = context.getString(R.string.archiver_dialog_cost, data.originalCost);
        String rC = context.getString(R.string.archiver_dialog_cost, data.resampleCost);
        originalCost.setText(oC);
        resampleCost.setText(rC);
        String oS = context.getString(R.string.archiver_dialog_size, data.originalSize);
        String rS = context.getString(R.string.archiver_dialog_size, data.resampleSize);
        originalSize.setText(oS);
        resampleSize.setText(rS);
        progressBar.setVisibility(View.GONE);
        body.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFailure(Exception e) {

    }

    @Override
    public void onCancel() {

    }


    private class DownloadArchiverListener extends EhCallback<GalleryDetailScene, String> {
        final ArchiverDownloadDialog archiverDownloadDialog;
        final Context context;

        public DownloadArchiverListener(Context context, int stageId, String sceneTag, ArchiverDownloadDialog archiverDownloadDialog) {
            super(context, stageId, sceneTag);
            this.context = context;
            this.archiverDownloadDialog = archiverDownloadDialog;
        }

        @Override
        public void onSuccess(String downloadUrl) {
            if (dialog != null && !dialog.isShowing()) {
                return;
            }
            progressBar.setVisibility(View.INVISIBLE);
            body.setVisibility(View.VISIBLE);
            dialog.dismiss();
            showTip(R.string.download_archive_started, LENGTH_SHORT);
//            File file = AppConfig.getExternalArchiverDir();
//            if (file == null || downloadUrl == null) {
//                return;
//            }
            Uri downloadUri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle(galleryDetail.title);
            request.setDescription(context.getString(R.string.download_archive_started));
            request.setVisibleInDownloadsUi(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "/EhviewerArchiver/"+galleryDetail.title + ".zip");
            request.allowScanningByMediaScanner();

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            myDownloadId = downloadManager.enqueue(request);

            context.registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));


//            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
//            String name = galleryDetail.title + ".zip";
//            DownloadArchiverManager.Archiver archiver = new DownloadArchiverManager.Archiver(downloadUrl,path,name);
//            final DownloadArchiverManager manager = archiverDownloadDialog.downloadArchiverManager;
//            manager.addDownloadArchiverListener(this);
//
//            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,context.getString(R.string.download_service),NotificationManager.IMPORTANCE_LOW);
//                notificationManager.createNotificationChannel(channel);
//            }
//            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,CHANNEL_ID)
//                    .setContentTitle(context.getString(R.string.download))
//                    .setContentText(archiver.name)
//                    .setSmallIcon(R.mipmap.ic_launcher)
//                    .setProgress(100,0,false);
        }

        @Override
        public void onFailure(Exception e) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if (e instanceof NoHAtHClientException) {
                showTip(R.string.download_h_h_failure_no_hath, LENGTH_LONG);
            } else {
                showTip(R.string.download_archive_failure, LENGTH_LONG);
            }
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }

    private class DownloadReceiver extends BroadcastReceiver {
        private final static String TAG = "DownloadReceiver";

        private final GalleryDetail galleryDetail;

        public DownloadReceiver(GalleryDetail galleryDetail) {
            this.galleryDetail = galleryDetail;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if (myDownloadId != downloadId) {
                    return;
                }
                android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                //检查下载状态
                checkDownloadStatus(downloadId, downloadManager);
            }
        }

        private void checkDownloadStatus(long downloadId, android.app.DownloadManager downloadManager) {
            android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
            query.setFilterById(downloadId);//筛选下载任务，传入任务ID，可变参数
            try (Cursor c = downloadManager.query(query)) {
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                    switch (status) {
                        case android.app.DownloadManager.STATUS_PAUSED:
                            Log.i(TAG, ">>>下载暂停");
                            break;
                        case android.app.DownloadManager.STATUS_PENDING:
                            Log.i(TAG, ">>>下载延迟");
                            break;
                        case android.app.DownloadManager.STATUS_SUCCESSFUL:
                            Log.i(TAG, ">>>下载完成");
                            unzipAndImportFile(c);
                            break;
                        case android.app.DownloadManager.STATUS_FAILED:
                            Log.i(TAG, ">>>下载失败");
                            break;
                        case android.app.DownloadManager.STATUS_RUNNING:
                        default:
                            Log.i(TAG, ">>>正在下载");// 此处无法监听到
                            break;
                    }
                }
            } catch (IllegalArgumentException | URISyntaxException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        private void unzipAndImportFile(Cursor cursor) throws IllegalArgumentException, URISyntaxException {
            String path = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            Uri uri = Uri.parse(path);
            File zipFile = new File(new URI(uri.toString()));
            File tempDir = AppConfig.getExternalTempDir();
            if (tempDir == null) {
                return;
            }
            String tempFilePath = tempDir.getPath() + "/" + galleryDetail.title;
            String zipFilePath = zipFile.getPath();
            new Thread(() -> {
                boolean result = GZIPUtils.UnZipFolder(zipFilePath, tempFilePath);
                if (!result) {
                    return;
                }
                importGallery(tempFilePath);
            }).start();
        }

        private void importGallery(String tempFilePath) {
            if (tempFilePath.isEmpty() || context == null) {
                return;
            }

            File tempFile = new File(tempFilePath);

            File[] tempPictures = tempFile.listFiles();
            if (tempPictures == null) {
                return;
            }
            Arrays.sort(tempPictures, (file1, file2) -> {
                String f1N = file1.getName();
                String f2N = file2.getName();
                return f1N.compareTo(f2N);
            });

            SpiderDen spiderDen = new SpiderDen(galleryDetail);
            spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD);
            UniFile downloadDir = spiderDen.getDownloadDir();

            if (downloadDir == null) {
                return;
            }
            try {
                File downloadFile = new File(new URI(downloadDir.getUri().toString()));
                for (int i = 0; i < tempPictures.length; i++) {
                    File picture = tempPictures[i];

                    String fileName = picture.getName();
                    String[] nameArr = fileName.split("\\.");
                    String newName = SpiderDen.generateImageFilename(i, "." + nameArr[nameArr.length - 1]);
                    String newPath = downloadFile.getPath() + "/" + newName;
                    File moveToFile = new File(newPath);
                    if (moveToFile.exists()) {
                        if (!moveToFile.delete()) {
                            continue;
                        }
                    }
                    boolean result = picture.renameTo(moveToFile);
                    if (!result) {
                        if (moveToFile.exists()) {
                            if (!moveToFile.delete()) {
                                continue;
                            }
                        }
                        FileUtils.copyFile(picture, moveToFile);
                    }
                }
            } catch (URISyntaxException ignored) {
            }
            boolean deleteTemp = tempFile.delete();
            if (!deleteTemp) {
                tempFile.deleteOnExit();
            }
            String finalFileName = tempFile.getName();
            new Handler(Looper.getMainLooper()).post(() -> {
                String labelName = context.getString(R.string.download_label_archiver);
                com.hippo.ehviewer.download.DownloadManager manager = EhApplication.getDownloadManager(context);
                manager.addLabel(labelName);
                manager.addDownload(galleryDetail, labelName, DownloadInfo.STATE_FINISH);
                Toast.makeText(context,context.getString(R.string.stat_download_done_line_succeeded, finalFileName),Toast.LENGTH_LONG).show();
                if (downloadReceiver != null) {
                    context.unregisterReceiver(downloadReceiver);
                }
            });
        }
    }
}

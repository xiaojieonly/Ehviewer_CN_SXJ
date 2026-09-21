package com.hippo.ehviewer.ui.dialog;



import static com.hippo.ehviewer.client.EhConfig.ARCHIVER_PATH;

import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_LONG;

import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_SHORT;



import android.app.Dialog;

import android.app.DownloadManager;

import android.content.Context;

import android.content.DialogInterface;

import android.net.Uri;

import android.os.Environment;

import android.util.Log;

import android.view.View;

import android.widget.Button;

import android.widget.LinearLayout;

import android.widget.ProgressBar;

import android.widget.TextView;

import android.widget.Toast;



import androidx.appcompat.app.AlertDialog;



import com.hippo.ehviewer.EhApplication;

import com.hippo.ehviewer.R;

import com.hippo.ehviewer.Settings;

import com.hippo.ehviewer.client.EhClient;

import com.hippo.ehviewer.client.EhRequest;

import com.hippo.ehviewer.client.EhUrl;

import com.hippo.ehviewer.client.data.ArchiverData;

import com.hippo.ehviewer.client.data.GalleryDetail;

import com.hippo.ehviewer.client.exception.NoHAtHClientException;

import com.hippo.ehviewer.download.ArchiverDownloadCompleter;

import com.hippo.ehviewer.ui.MainActivity;

import com.hippo.ehviewer.ui.scene.EhCallback;

import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;

import com.hippo.scene.SceneFragment;



public class ArchiverDownloadDialog implements

        DialogInterface.OnDismissListener, EhClient.Callback<ArchiverData> {

    final private GalleryDetail galleryDetail;

    final private Context context;

    final private GalleryDetailScene detailScene;



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



    private ArchiverData data = new ArchiverData();





    public ArchiverDownloadDialog(GalleryDetail galleryDetail, GalleryDetailScene detailScene) {

        this.galleryDetail = galleryDetail;

        this.detailScene = detailScene;

        this.context = detailScene.getEHContext();

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

        // 下载在系统 DownloadManager 中继续；完成由 ArchiverDownloadCompleter 处理

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

        final Context context;



        public DownloadArchiverListener(Context context, int stageId, String sceneTag, ArchiverDownloadDialog archiverDownloadDialog) {

            super(context, stageId, sceneTag);

            this.context = context;

        }



        @Override

        public void onSuccess(String downloadUrl) {

            if (dialog != null && !dialog.isShowing()) {

                return;

            }

            if (downloadUrl == null || downloadUrl.trim().isEmpty()) {

                Toast.makeText(context,R.string.download_state_failed,Toast.LENGTH_LONG).show();

                return;

            }

            progressBar.setVisibility(View.INVISIBLE);

            body.setVisibility(View.VISIBLE);

            dialog.dismiss();

            showTip(R.string.download_archive_started, LENGTH_SHORT);

            String fileName = ArchiverDownloadCompleter.createFileName(galleryDetail.title, galleryDetail.gid);

            if (fileName.isEmpty()) {

                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();

                return;

            }

            Uri downloadUri = Uri.parse(downloadUrl);

            String scheme = downloadUri.getScheme();

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {

                Log.w("ArchiverDownloadDialog", "Invalid download URL scheme: " + downloadUrl);

                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();

                return;

            }

            DownloadManager.Request request;

            try {

                request = new DownloadManager.Request(downloadUri);

            } catch (IllegalArgumentException e) {

                Log.e("ArchiverDownloadDialog", "Invalid download URL: " + downloadUrl, e);

                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();

                return;

            }

            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);

            request.setAllowedOverRoaming(true);

            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

            request.setTitle(galleryDetail.title);

            request.setDescription(context.getString(R.string.download_archive_started));

            request.setVisibleInDownloadsUi(true);

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, ARCHIVER_PATH + fileName + ".zip");

            request.allowScanningByMediaScanner();



            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            if (downloadManager == null) {

                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();

                return;

            }



            ArchiverDownloadCompleter completer = ArchiverDownloadCompleter.getInstance(context);

            if (completer == null) {

                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();

                return;

            }

            completer.ensureReceiverRegistered();



            long downloadId = downloadManager.enqueue(request);

            Settings.putArchiverDownloadId(galleryDetail.gid, downloadId);

            Settings.putArchiverDownload(downloadId, galleryDetail);

            completer.checkAndHandleStatus(downloadId);

            detailScene.bindArchiverProgress(galleryDetail);

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

}



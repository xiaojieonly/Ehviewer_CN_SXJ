package com.hippo.ehviewer.ui.scene.gallery.detail;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.gallery.list.EnterGalleryDetailTransaction;
import com.hippo.scene.Announcer;

public class GalleryUpdateDialog {
    final GalleryDetailScene detailScene;
    final Context context;

    private GalleryDetail galleryDetail;

    private AlertDialog dialog;

    private AlertDialog choseDialog;

    public boolean autoDownload = false;

    public GalleryUpdateDialog(GalleryDetailScene scene, Context context) {
        this.detailScene = scene;
        this.context = context;
    }

    public void showSelectDialog(GalleryDetail galleryDetail) {
        if (galleryDetail == this.galleryDetail && dialog != null) {
            dialog.setTitle(R.string.new_version);
            dialog.show();
        }
        this.galleryDetail = galleryDetail;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setSingleChoiceItems(galleryDetail.getUpdateVersionName(), -1, (dia, index) -> {
//            GalleryInfo gi = (GalleryInfo) galleryDetail.getNewGalleryDetail(index);
//            if (gi == null) {
//                return;
//            }
//            Bundle args = new Bundle();
//            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO);
//            args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, gi);
//            Announcer announcer = new Announcer(GalleryDetailScene.class).setArgs(args);
            dialog.dismiss();
            Announcer announcer = MainActivity.createAnnouncerFromClipboardUrl(galleryDetail.newVersions[index].versionUrl);
            detailScene.startScene(announcer);
        });
        dialog = builder.create();
        dialog.setTitle(R.string.new_version);
        dialog.show();
    }

    private void showChooseDialog(String url) {
        if (choseDialog != null) {
            choseDialog.show();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        choseDialog = builder.setTitle(R.string.gallery_update_dialog_title)
                .setMessage(R.string.gallery_update_dialog_message)
                .setNeutralButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.gallery_update_download_as_new, (dialog, which) -> {
                    autoDownload = true;
                    detailScene.startDownloadAsNew(url);
                })
                .setPositiveButton(R.string.gallery_update_override_old, (dialog, which) -> {
                    detailScene.startUpdateDownload(url);
                })
                .create();
        choseDialog.show();
    }
}

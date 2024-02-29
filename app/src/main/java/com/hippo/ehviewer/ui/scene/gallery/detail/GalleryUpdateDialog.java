package com.hippo.ehviewer.ui.scene.gallery.detail;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.GalleryDetail;

public class GalleryUpdateDialog {
    final GalleryDetailScene detailScene;
    final Context context;

    private GalleryDetail galleryDetail;

    private AlertDialog dialog;

    private AlertDialog choseDialog;

    public boolean autoDownload = false;

    public GalleryUpdateDialog(GalleryDetailScene scene,Context context){
        this.detailScene = scene;
        this.context = context;
    }

    public void showSelectDialog(GalleryDetail galleryDetail,boolean update){
        if (galleryDetail==this.galleryDetail&&dialog!=null){
            setDialogTitle(update);
            dialog.show();
        }
        this.galleryDetail = galleryDetail;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setSingleChoiceItems(galleryDetail.getUpdateVersionName(),-1,(dialog,which)->{
            if (update){
                String url = galleryDetail.newVersions[which].versionUrl;
//                detailScene.startUpdateDownload(url);
                showChooseDialog(url);
            }else{
                detailScene.gotoNewVersion(galleryDetail.getNewGalleryDetail(which));
            }
            dialog.dismiss();
        });
        dialog = builder.create();
        setDialogTitle(update);
        dialog.show();
    }

    private void showChooseDialog(String url){
        if (choseDialog!=null){
            choseDialog.show();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        choseDialog = builder.setTitle(R.string.gallery_update_dialog_title)
                .setMessage(R.string.gallery_update_dialog_message)
                .setNeutralButton(R.string.cancel,(dialog,which)->{
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.gallery_update_download_as_new,(dialog,which)->{
                    autoDownload = true;
                    detailScene.startDownloadAsNew(url);
                })
                .setPositiveButton(R.string.gallery_update_override_old,(dialog,which)->{
                    detailScene.startUpdateDownload(url);
                })
                .create();
        choseDialog.show();
    }

    private void setDialogTitle(boolean update){
        if (update){
            dialog.setTitle(R.string.update_to);
        }else {
            dialog.setTitle(R.string.new_version);
        }
    }
}

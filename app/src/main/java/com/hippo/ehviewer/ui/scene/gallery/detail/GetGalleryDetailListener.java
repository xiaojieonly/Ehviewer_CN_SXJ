package com.hippo.ehviewer.ui.scene.gallery.detail;

import android.content.Context;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.sync.GalleryDetailTagsSyncTask;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.scene.SceneFragment;
import com.hippo.yorozuya.FileUtils;

public class GetGalleryDetailListener extends EhCallback<GalleryDetailScene, GalleryDetail> {

    public static int RESULT_DETAIL = 1;
    public static int RESULT_UPDATE = 0;

    private final int resultMode;

    public GetGalleryDetailListener(Context context, int stageId, String sceneTag, int resultMode) {
        super(context, stageId, sceneTag);
        this.resultMode = resultMode;
    }

    @Override
    public void onSuccess(GalleryDetail result) {
        getApplication().removeGlobalStuff(this);

        // Put gallery detail to cache
        EhApplication.getGalleryDetailCache(getApplication()).put(result.gid, result);

        // Add history
        EhDB.putHistoryInfo(result);

        // Save tags
        GalleryDetailTagsSyncTask syncTask = new GalleryDetailTagsSyncTask(result);
        syncTask.start();

        // Notify success
        GalleryDetailScene scene = getScene();
        if (scene != null) {
            scene.onGetGalleryDetailSuccess(result);
//            if (resultMode == RESULT_DETAIL) {
//                scene.onGetGalleryDetailSuccess(result);
//                return;
//            }
//            scene.onGetGalleryDetailUpdateSuccess(result, SpiderInfo.getSpiderInfo(result), newPath(result));
        }
    }

    @Override
    public void onFailure(Exception e) {
        getApplication().removeGlobalStuff(this);
        GalleryDetailScene scene = getScene();
        if (scene != null) {
            if (resultMode == RESULT_DETAIL) {
                scene.onGetGalleryDetailFailure(e);
                return;
            }
            scene.onGetGalleryDetailUpdateFailure(e);
        }
    }

    @Override
    public void onCancel() {
        getApplication().removeGlobalStuff(this);
    }

    @Override
    public boolean isInstance(SceneFragment scene) {
        return scene instanceof GalleryDetailScene;
    }

    private String newPath(GalleryDetail result) {
        return FileUtils.sanitizeFilename(result.gid + "-" + EhUtils.getSuitableTitle(result));
    }
}

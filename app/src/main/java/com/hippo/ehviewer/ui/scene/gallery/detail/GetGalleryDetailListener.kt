package com.hippo.ehviewer.ui.scene.gallery.detail

import android.content.Context
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.sync.GalleryDetailTagsSyncTask
import com.hippo.ehviewer.ui.scene.EhCallback
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.scene.SceneFragment

class GetGalleryDetailListener(
    context: Context?,
    stageId: Int,
    sceneTag: String?,
    private val resultMode: Int
) : EhCallback<GalleryDetailScene?, GalleryDetail?>(context, stageId, sceneTag) {
    override fun onSuccess(result: GalleryDetail?) {
        application.removeGlobalStuff(this)
        if (result==null){
            return
        }
        // Put gallery detail to cache
        EhApplication.getGalleryDetailCache(application).put(result.gid, result)

        // Add history
        EhDB.putHistoryInfo(result)

        // Save tags
        val syncTask = GalleryDetailTagsSyncTask(result)
        syncTask.start()

        // Notify success
        val scene = scene
        scene?.onGetGalleryDetailSuccess(result)
    }

    override fun onFailure(e: Exception) {
        application.removeGlobalStuff(this)
        val scene = scene
        if (scene != null) {
            if (resultMode == RESULT_DETAIL) {
                scene.onGetGalleryDetailFailure(e)
                return
            }
            scene.onGetGalleryDetailUpdateFailure(e)
        }
    }

    override fun onCancel() {
        application.removeGlobalStuff(this)
    }

    override fun isInstance(scene: SceneFragment?): Boolean {
        if (scene == null) {
            return false;
        }
        return scene is GalleryDetailScene
    }

    private fun newPath(result: GalleryDetail): String {
        return FileUtils.sanitizeFilename(
            result.gid.toString() + "-" + EhUtils.getSuitableTitle(
                result
            )
        )
    }

    companion object {
        @JvmField
        var RESULT_DETAIL: Int = 1
        @JvmField
        var RESULT_UPDATE: Int = 0
    }
}

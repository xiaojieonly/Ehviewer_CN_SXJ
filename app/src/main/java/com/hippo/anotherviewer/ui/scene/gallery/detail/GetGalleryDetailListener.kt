package com.hippo.anotherviewer.ui.scene.gallery.detail

import android.content.Context
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.SiteDB
import com.hippo.anotherviewer.client.SiteUtils
import com.hippo.anotherviewer.client.data.GalleryDetail
import com.hippo.anotherviewer.sync.GalleryDetailTagsSyncTask
import com.hippo.anotherviewer.ui.scene.SiteCallback
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.scene.SceneFragment

class GetGalleryDetailListener(
    context: Context?,
    stageId: Int,
    sceneTag: String?,
    private val resultMode: Int
) : SiteCallback<GalleryDetailScene?, GalleryDetail?>(context, stageId, sceneTag) {
    override fun onSuccess(result: GalleryDetail?) {
        application.removeGlobalStuff(this)
        if (result==null){
            return
        }
        // Put gallery detail to cache
        SiteApplication.getGalleryDetailCache(application).put(result.gid, result)

        // Add history
        SiteDB.putHistoryInfo(result)

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
            result.gid.toString() + "-" + SiteUtils.getSuitableTitle(
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

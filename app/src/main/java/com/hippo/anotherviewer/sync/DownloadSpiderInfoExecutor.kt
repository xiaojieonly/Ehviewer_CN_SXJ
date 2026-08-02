package com.hippo.anotherviewer.sync

import android.os.Handler
import android.os.Looper
import com.hippo.anotherviewer.callBack.SpiderInfoReadCallBack
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.dao.DownloadInfo
import com.hippo.anotherviewer.spider.SpiderDen
import com.hippo.anotherviewer.spider.SpiderInfo
import com.hippo.anotherviewer.spider.SpiderQueen
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DownloadSpiderInfoExecutor(
    private val mList: MutableList<DownloadInfo>,
    private val callBack: SpiderInfoReadCallBack?
) {
    var handler: Handler = Handler(Looper.getMainLooper())
    private val service: ExecutorService = Executors.newSingleThreadExecutor()

    val resultMap: MutableMap<Long?, SpiderInfo?> = HashMap<Long?, SpiderInfo?>()


    fun execute() {
        service.execute(Runnable {
            for (i in mList.indices) {
                val info = mList.get(i)
                resultMap.put(info.gid, getSpiderInfo(info))
            }
            handler.post(Runnable {
                if (callBack == null) {
                    return@Runnable
                }
                callBack.resultCallBack(resultMap)
            })
        })
    }

    private fun getSpiderInfo(info: GalleryInfo): SpiderInfo? {
        val spiderInfo: SpiderInfo?
        val mDownloadDir = SpiderDen.getGalleryDownloadDir(info)
        if (mDownloadDir != null && mDownloadDir.isDirectory()) {
            val file = mDownloadDir.findFile(SpiderQueen.SPIDER_INFO_FILENAME)
            spiderInfo = SpiderInfo.read(file)
            if (spiderInfo != null && spiderInfo.gid == info.gid &&
                spiderInfo.token == info.token
            ) {
                return spiderInfo
            }
        }
        return null
    }
}

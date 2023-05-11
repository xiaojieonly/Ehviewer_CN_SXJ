package com.hippo.ehviewer.sync;

import static com.hippo.ehviewer.spider.SpiderDen.getGalleryDownloadDir;
import static com.hippo.ehviewer.spider.SpiderQueen.SPIDER_INFO_FILENAME;

import android.os.Handler;
import android.os.Looper;

import com.hippo.ehviewer.callBack.SpiderInfoReadCallBack;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.unifile.UniFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadSpiderInfoExecutor {

    Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService service = Executors.newSingleThreadExecutor();
    private final SpiderInfoReadCallBack callBack;
    private final List<DownloadInfo> mList;

    final Map<Long, SpiderInfo> resultMap = new HashMap<>();


    public DownloadSpiderInfoExecutor(List<DownloadInfo> mList, SpiderInfoReadCallBack callBack) {
        this.callBack = callBack;
        this.mList = mList;
    }

    public void execute() {
        service.execute(() -> {
            for (int i = 0; i < mList.size(); i++) {
                DownloadInfo info = mList.get(i);
                resultMap.put(info.gid,getSpiderInfo(info));
            }
            handler.post(()->{
                if (callBack==null){
                    return;
                }
                callBack.resultCallBack(resultMap);
            });
        });
    }
    private SpiderInfo getSpiderInfo(GalleryInfo info) {
        SpiderInfo spiderInfo;
        UniFile mDownloadDir = getGalleryDownloadDir(info);
        if (mDownloadDir != null && mDownloadDir.isDirectory()) {
            UniFile file = mDownloadDir.findFile(SPIDER_INFO_FILENAME);
            spiderInfo = SpiderInfo.read(file);
            if (spiderInfo != null && spiderInfo.gid == info.gid &&
                    spiderInfo.token.equals(info.token)) {
                return spiderInfo;
            }
        }
        return null;
    }
}

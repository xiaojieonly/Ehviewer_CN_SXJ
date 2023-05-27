package com.hippo.ehviewer.sync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.callBack.DownloadSearchCallback;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryTags;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadSearchingExecutor {

    private final String TAG = "DownloadSearchingExecutor";

    ExecutorService service = Executors.newSingleThreadExecutor();

    Handler handler = new Handler(Looper.getMainLooper());

    private DownloadSearchCallback mDownloadSearchCallback;

    @Nullable
    private final List<DownloadInfo> mList;

    private List<DownloadInfo> resultList;

    private final String mSearchKey;

    public DownloadSearchingExecutor(@Nullable List<DownloadInfo> mList, String searchKey) {
        this.mList = mList;
        this.mSearchKey = searchKey;
    }

    public void setDownloadSearchingListener(DownloadSearchCallback downloadSearchCallback) {
        mDownloadSearchCallback = downloadSearchCallback;
    }

    public void execute() {
        service.execute(() -> {
            resultList = doInBackground();
            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    return;
                }
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
            //            try{
            //                resultList = doInBackground();
            //
            //                handler.post(()->{
            //                    if (mDownloadSearchCallback==null){
            //                        return;
            //                    }
            //                    mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            //                });
            //            }catch (Exception e){
            //                e.printStackTrace();
            //                Log.e(TAG,"搜索引擎异常,请尝试重启软件");
            //                handler.post(()->{
            //                    mDownloadSearchCallback.onDownloadSearchFailed(mList);
            //                });
            //            }
        });
    }

    protected List<DownloadInfo> doInBackground() {
        if (mDownloadSearchCallback == null) {
            return new ArrayList<>();
        }
        if (mSearchKey == null || mSearchKey.isEmpty()) {
            return mList;
        }
        if (mList == null) {
            return new ArrayList<>();
        }
        List<DownloadInfo> cache = new ArrayList<>();
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            if (EhUtils.judgeSuitableTitle(info, mSearchKey)) {
                cache.add(info);
            } else if (matchTag(mSearchKey, info)) {
                cache.add(info);
            }
        }
        return cache;
    }

    private boolean matchTag(String mSearchKey, DownloadInfo info) {
        if (info.tgList == null || info.tgList.isEmpty()) {
            info.tgList = searchTagList(info.gid);
        }
        if (info.tgList == null) {
            return false;
        }
        String[] searchTags = mSearchKey.split("  ");
        boolean result = true;
        for (String searchTag : searchTags) {
            if (!info.tgList.contains(searchTag)) {
                result = false;
                break;
            }
        }
        return result;
    }

    private ArrayList<String> searchTagList(long gid) {
        GalleryTags tags = EhDB.queryGalleryTags(gid);
        if (tags == null) {
            return null;
        }
        ArrayList<String> tagList = new ArrayList<>();
        tagList.addAll(parserList("artist", tags.artist));
        tagList.addAll(parserList("rows", tags.rows));
        tagList.addAll(parserList("cosplayer", tags.cosplayer));
        tagList.addAll(parserList("character", tags.character));
        tagList.addAll(parserList("female", tags.female));
        tagList.addAll(parserList("group", tags.group));
        tagList.addAll(parserList("language", tags.language));
        tagList.addAll(parserList("male", tags.male));
        tagList.addAll(parserList("misc", tags.misc));
        tagList.addAll(parserList("mixed", tags.mixed));
        tagList.addAll(parserList("other", tags.other));
        tagList.addAll(parserList("parody", tags.parody));
        tagList.addAll(parserList("reclass", tags.reclass));
        return tagList;
    }

    private ArrayList<String> parserList(String name, String content) {
        if (name == null || content == null) {
            return new ArrayList<>();
        }
        ArrayList<String> list = new ArrayList<>();
        String[] tagNames = content.split(",");
        for (String s : tagNames) {
            list.add(name + ":" + s);
        }
        return list;
    }
}

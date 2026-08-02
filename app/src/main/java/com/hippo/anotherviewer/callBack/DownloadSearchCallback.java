package com.hippo.anotherviewer.callBack;

import com.hippo.anotherviewer.dao.DownloadInfo;

import java.util.List;

public interface DownloadSearchCallback {

    void onDownloadSearchSuccess(List<DownloadInfo> mList);

    void onDownloadListHandleSuccess(List<DownloadInfo> mList);

    void onDownloadSearchFailed(List<DownloadInfo> mList);
}

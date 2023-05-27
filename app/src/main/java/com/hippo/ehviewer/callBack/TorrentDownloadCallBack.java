package com.hippo.ehviewer.callBack;

public interface TorrentDownloadCallBack {

    /**
     * 种子下载成功
     * @param path
     * @param name
     */
    void torrentDownLoadSuccess(String path, String name);

    /**
     * 种子下载失败
     * @param url
     * @param name
     */
    void torrentDownLoadFailed(String url, String name);

    /**
     * 种子下载中
     * @param url
     * @param name
     * @param progress
     */
    void torrentDownLoading(String url, String name, int progress);
}

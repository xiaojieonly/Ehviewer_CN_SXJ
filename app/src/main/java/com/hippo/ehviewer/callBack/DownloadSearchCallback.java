package com.hippo.ehviewer.callBack;

import com.hippo.ehviewer.dao.DownloadInfo;
import java.util.List;

public interface DownloadSearchCallback {

    void onDownloadSearchSuccess(List<DownloadInfo> mList);

    void onDownloadSearchFailed(List<DownloadInfo> mList);
}

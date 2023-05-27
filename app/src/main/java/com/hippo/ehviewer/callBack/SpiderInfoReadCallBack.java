package com.hippo.ehviewer.callBack;

import com.hippo.ehviewer.spider.SpiderInfo;
import java.util.Map;

public interface SpiderInfoReadCallBack {

    void resultCallBack(Map<Long, SpiderInfo> resultMap);
}

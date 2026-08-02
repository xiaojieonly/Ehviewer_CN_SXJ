package com.hippo.anotherviewer.callBack;

import com.hippo.anotherviewer.spider.SpiderInfo;

import java.util.Map;

public interface SpiderInfoReadCallBack {

    void resultCallBack(Map<Long, SpiderInfo> resultMap);
}

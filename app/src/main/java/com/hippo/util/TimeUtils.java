package com.hippo.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {

    public static String getTimeNow() {
        // 格式化时间
        SimpleDateFormat sdf = new SimpleDateFormat();
        // a为am/pm的标记
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");
        // 获取当前时间
        Date date = new Date();
        return sdf.format(date);
    }
}

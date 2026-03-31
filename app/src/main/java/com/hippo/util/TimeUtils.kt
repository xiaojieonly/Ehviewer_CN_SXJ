package com.hippo.util

import java.text.SimpleDateFormat
import java.util.Date

object TimeUtils {
    @JvmStatic
    val timeNow: String
        get() {
            val sdf = SimpleDateFormat() // 格式化时间
            sdf.applyPattern("yyyy-MM-dd HH:mm:ss a") // a为am/pm的标记
            val date = Date() // 获取当前时间
            return sdf.format(date)
        }
}

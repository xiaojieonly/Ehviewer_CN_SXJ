package com.hippo.ehviewer.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hippo.ehviewer.BackgroundTaskManager
import com.hippo.ehviewer.util.UiThreadHelper

/**
 * Java 鍏煎鐨勪换鍔℃墽琛屽櫒
 * 鐢ㄤ簬浠?Java 浠ｇ爜鎵ц Kotlin 鐨?suspend 鍑芥暟
 */
object TaskExecutor {

    /**
     * 鍦ㄥ悗鍙扮嚎绋嬫墽琛屼换鍔?
     * @param task 瑕佹墽琛岀殑鍚庡彴浠诲姟
     */
    @JvmStatic
    fun executeTask(task: BackgroundTask) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = task.execute()
                // 纭繚鎴愬姛鍥炶皟鍦ㄤ富绾跨▼鎵ц
                withContext(Dispatchers.Main) {
                    // 浠诲姟鎴愬姛瀹屾垚鍚庣殑UI鏇存柊鍙互鍦ㄨ繖閲屽鐞?
                }
            } catch (e: Exception) {
                // 纭繚閿欒鍥炶皟鍦ㄤ富绾跨▼鎵ц
                withContext(Dispatchers.Main) {
                    // 閿欒澶勭悊鍜孶I鏇存柊鍙互鍦ㄨ繖閲屽鐞?
                }
            }
        }
    }

    /**
     * 鑾峰彇 TaskExecutor 瀹炰緥锛堝吋瀹规棫浠ｇ爜锛?
     */
    @JvmStatic
    fun getInstance(): TaskExecutor {
        return this
    }

    /**
     * 鎵ц鍚庡彴浠诲姟锛堝吋瀹规棫浠ｇ爜锛?
     * @param task 瑕佹墽琛岀殑鍚庡彴浠诲姟
     */
    @JvmStatic
    fun execute(task: BackgroundTask) {
        executeTask(task)
    }
}

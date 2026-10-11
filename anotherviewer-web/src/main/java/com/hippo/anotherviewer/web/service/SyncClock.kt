package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * v4 服务器统一打戳（WP2）：全库唯一的单调递增时间源。
 *
 * 决策原因：v4 把「谁更新」的判定完全交给服务器——8 类同步实体 + preferences +
 * ehSession 的 lastModified 一律由本组件在落库时打戳（客户端上送值不落库）。
 * 判定条件为 `incoming.lastModified >= existing.lastModified`（同戳后到胜），
 * 因此同一毫秒内的连续写入必须严格递增（`last + 1` 兜底），否则两行相等戳会让
 * 增量 pull 的 `lastModified > since` 与 LWW 门控同时丢失区分度。
 *
 * 启动初始化（@PostConstruct）：last = 9 张表 MAX(last_modified)（preferences 取
 * updatedAt）的最大值——跨重启绝不回拨，否则重启后新写入的行会带上低于存量旧行
 * 的戳，LWW 的 `>=` 判据会短暂把新包判负。
 *
 * 仓库参数可空且带缺省值，仅为单测直构 `SyncClock()` 的源兼容保留（null 时跳过
 * 对应表的初始化）；Spring 装配按类型注入全部 9 个仓库。
 */
@Component
class SyncClock(
    private val favoriteRepository: LocalFavoriteInfoRepository? = null,
    private val historyRepository: HistoryInfoRepository? = null,
    private val downloadRepository: DownloadInfoRepository? = null,
    private val bookmarkRepository: BookmarkInfoRepository? = null,
    private val filterRepository: FilterRepository? = null,
    private val quickSearchRepository: QuickSearchRepository? = null,
    private val downloadLabelRepository: DownloadLabelRepository? = null,
    private val ehSessionRepository: EhSessionRepository? = null,
    private val userPreferenceRepository: UserPreferenceRepository? = null,
) {

    private val logger = LoggerFactory.getLogger(SyncClock::class.java)

    /** 最近一次发出的戳；@Volatile 只为观测（current()）无锁，写入一律在 synchronized 内。 */
    @Volatile
    private var last: Long = 0L

    /** 从 9 张表初始化水位（仓库缺省/null 的直构场景跳过）。 */
    @PostConstruct
    fun initFromStores() {
        val highest = listOfNotNull(
            favoriteRepository?.maxLastModified(),
            historyRepository?.maxLastModified(),
            downloadRepository?.maxLastModified(),
            bookmarkRepository?.maxLastModified(),
            filterRepository?.maxLastModified(),
            quickSearchRepository?.maxLastModified(),
            downloadLabelRepository?.maxLastModified(),
            ehSessionRepository?.maxLastModified(),
            userPreferenceRepository?.maxLastModified(),
        ).maxOrNull() ?: return
        last = maxOf(last, highest)
        logger.info("SyncClock initialized: last={}", last)
    }

    /** 下一个戳：严格单调递增（同毫秒内 `last + 1`），全进程唯一写入口。 */
    @Synchronized
    fun next(): Long {
        last = maxOf(System.currentTimeMillis(), last + 1)
        return last
    }

    /** 最近发出的戳（只读观测；测试断言落库戳用）。 */
    @Synchronized
    fun current(): Long = last
}

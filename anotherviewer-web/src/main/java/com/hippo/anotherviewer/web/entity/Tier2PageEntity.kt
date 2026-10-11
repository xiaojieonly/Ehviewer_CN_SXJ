package com.hippo.anotherviewer.web.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * Persistent EH page cache for Tier-2 browsing (gallery list pages + detail
 * pages): one row per normalized upstream URL. Written on every successful
 * origin fetch through [com.hippo.anotherviewer.web.service.Tier2PageService];
 * served back (X-Cache: stale) whenever the origin is unreachable, and
 * re-fetched oldest-first by the background refresh job.
 *
 * 非同步域（不参与 App 同步）：不 stamp username、无墓碑（A7 不适用）。
 * SQLite DDL 惯例：NOT NULL 列必须带 columnDefinition default（ddl-auto update 加列限制）；
 * 时间戳一律 Long epoch millis。
 */
@Entity
@Table(
    name = "tier2_page",
    indexes = [
        // 后台刷新（fetched_at < cutoff, oldest first）与容量裁剪共用；ddl-auto: update 自动建索引。
        Index(name = "idx_tier2_page_fetched_at", columnList = "fetched_at"),
    ],
)
class Tier2PageEntity {

    /** Normalized upstream URL（主键）：scheme/host 小写、默认端口省略、无 fragment。 */
    @Id
    @Column(name = "url", nullable = false, length = 2048)
    var url: String = ""

    /**
     * Raw response body. Byte-exact round trip: bytes are stored Latin-1
     * decoded（每字节 1:1 映射码点，TEXT 落库无损），serve 时按原编码还原为
     * 字节，Content-Type 原样回传——客户端按其 charset 解码，与源站响应逐字节一致。
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String = ""

    /** 上游响应的 Content-Type（缺省按 SiteProxyController 同款 text/html 处理）。 */
    @Column(name = "content_type", nullable = false, length = 256, columnDefinition = "varchar(256) not null default ''")
    var contentType: String = ""

    /** 最近一次成功抓取时间（epoch millis；后台刷新按其挑最旧的一批）。 */
    @Column(name = "fetched_at", nullable = false, columnDefinition = "bigint not null default 0")
    var fetchedAt: Long = 0
}

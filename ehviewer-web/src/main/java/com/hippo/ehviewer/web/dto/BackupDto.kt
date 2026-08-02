package com.hippo.ehviewer.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.file.Path

/**
 * 备份分片元数据（contracts/backup-format.md 由 T15 成文，本结构保持与其约定一致）。
 */
data class BackupSlice(
    val name: String,
    val sha256: String,
    @JsonProperty("sizeBytes")
    val sizeBytes: Long,
)

/** 备份产物清单，与分片同目录的 manifest.json 一一对应。 */
data class BackupManifest(
    @JsonProperty("formatVersion")
    val formatVersion: Int = 1,
    @JsonProperty("exportedAt")
    val exportedAt: String,
    @JsonProperty("appVersion")
    val appVersion: String,
    val slices: List<BackupSlice>,
    @JsonProperty("includesDownloads")
    val includesDownloads: Boolean,
)

/** 一次导出结果：[manifest] 与落盘的分片文件路径（位于 [directory]/backups 下）。 */
data class BackupResult(
    val manifest: BackupManifest,
    val slices: List<Path>,
    val directory: Path,
)

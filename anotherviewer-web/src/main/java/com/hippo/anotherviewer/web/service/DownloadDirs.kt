package com.hippo.anotherviewer.web.service

import java.io.File

/**
 * Resolve a download row's content directory against the CURRENT downloads root.
 *
 * Rows restored from a backup or copied from another machine carry absolute
 * paths of the old host (`/Users/...` on a macOS origin); those are
 * meaningless on this host and silently break every page write (ENOENT).
 * Backups are path-independent by contract, so a stored absolute path that
 * does not live under the current root is ignored and the default
 * `<root>/<gid>` layout is used instead. Relative stored paths keep
 * resolving against the working directory (legacy row format).
 *
 * Directory naming (2026-08-30): downloads also accept the Android-style
 * `{gid}-{title}` directory (human-readable, `{gid}` prefix leaves them
 * parseable). New writes use `{gid}-{title}` via [dirName]; legacy `{gid}`
 * dirs remain supported by [parseGid].
 */
object DownloadDirs {

    /**
     * Effective content directory for [gid]: the stored [storedDir] when it is
     * usable on this host (relative, or absolute inside the root), otherwise
     * a directory under `<rootPath>` named [dirName] (or the legacy plain gid
     * dir when no title is known).
     */
    fun resolve(rootPath: String, gid: Long, storedDir: String?, title: String? = null): File {
        val root = File(rootPath)
        if (!storedDir.isNullOrBlank()) {
            val stored = File(storedDir)
            if (!stored.isAbsolute) return stored
            val canonical = runCatching { stored.canonicalFile }.getOrNull()
            val rootCanonical = runCatching { root.canonicalFile }.getOrNull()
            if (canonical != null && rootCanonical != null &&
                canonical.path.startsWith(rootCanonical.path + File.separator)
            ) {
                return canonical
            }
        }
        return if (!title.isNullOrBlank()) File(root, dirName(gid, title))
        else File(root, gid.toString())
    }

    /**
     * 目录名的新下载写入：`{gid}-{sanitizedTitle}`，逐字对齐 Android
     * `FileUtils.sanitizeFilename`（SpiderDen.getGalleryDownloadDir），保证两端
     * 同一标题生成同一目录名（存储互用）。空白标题回落纯 `{gid}`。
     */
    fun dirName(gid: Long, title: String?): String {
        val safe = title?.let(::sanitizeTitle).orEmpty()
        return if (safe.isBlank()) gid.toString() else "$gid-$safe"
    }

    /**
     * 与 Android `com.hippo.lib.yorozuya.FileUtils.sanitizeFilename` 逐字等价：
     * 移除 FORBIDDEN_FILENAME_CHARACTERS（\ / : * ? " < > |），UTF-8 字节数
     * ≤255 截断（含代理对 4 字节），trim。差异仅限 Kotlin/Java 的尾随写法。
     */
    fun sanitizeTitle(title: String): String {
        var filename = StringBuilder(title.length)
        for (ch in title) {
            if (ch !in FORBIDDEN_CHARS) filename.append(ch)
        }
        var result = filename.toString()
        // UTF-8 byte count <= 255（镜像 Android 逐字符累加，代理对按 4 字节）
        var byteCount = 0
        var length = 0
        val chars = result
        while (length < chars.length && byteCount <= 255) {
            val ch = chars[length]
            val bytes = when {
                ch.code <= 0x7F -> 1
                ch.code <= 0x7FF -> 2
                Character.isHighSurrogate(ch) -> {
                    // 高代理=4 字节，且其低代理必须一并计入（Android: ++length 跳过 pair）
                    length++
                    4
                }
                else -> 3
            }
            byteCount += bytes
            if (byteCount > 255) {
                result = chars.substring(0, length)
                break
            }
            length++
        }
        return result.trim()
    }

    private val FORBIDDEN_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    /**
     * Parse gid from a directory name: `1234` → 1234, `1234-Some Title` → 1234.
     * Returns null when the name has no leading numeric gid (not ours).
     */
    fun parseGid(dirName: String): Long? =
        dirName.substringBefore('-').toLongOrNull()

    /**
     * Match a directory name as an ours layout: `{gid}` or `{gid}-{title}`.
     */
    fun isOursDir(dirName: String): Boolean = parseGid(dirName) != null
}

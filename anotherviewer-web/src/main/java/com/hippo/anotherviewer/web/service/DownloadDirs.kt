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
     * Directory name for new downloads: `{gid}-{sanitizedTitle}`, mirroring the
     * Android cache layout so the downloads root stays human-readable. Title is
     * sanitized to filesystem-safe characters; empty titles fall back to the
     * plain `{gid}` name (same as legacy).
     */
    fun dirName(gid: Long, title: String?): String {
        val safe = title?.let(::sanitizeTitle).orEmpty()
        return if (safe.isBlank()) gid.toString() else "$gid-$safe"
    }

    /** Filesystem-sanitized title: keep unicode, drop path separators/control chars. */
    fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
            .take(120)
    }

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

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
 */
object DownloadDirs {

    /**
     * Effective content directory for [gid]: the stored [storedDir] when it is
     * usable on this host (relative, or absolute inside the root), otherwise
     * `<rootPath>/<gid>`.
     */
    fun resolve(rootPath: String, gid: Long, storedDir: String?): File {
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
        return File(root, gid.toString())
    }
}

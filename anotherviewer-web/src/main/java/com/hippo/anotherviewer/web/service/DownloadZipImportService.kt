package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * POST /api/v1/download/import-zip 响应摘要（字段语义见 [DownloadZipImportService]）。
 */
data class ZipImportResponse(
    /** 归一化成功并落盘的顶层目录数（每目录 ≥1 页写入 downloads/<gid>/）。 */
    val imported: Int,
    /** 跳过的顶层目录数：无 gid 数字前缀、或目录内不含任何页文件。 */
    val skipped: Int,
    /** 关联到 DB 行、磁盘校验通过并置 3（已完成）的 gid 数。 */
    val verified: Int,
    /** 导入后 state=3 的下载行总数（含本次归位的行，供 WebUI 展示）。 */
    val completedTotal: Int,
)

/**
 * Android 端缓存批量导入：App（EhViewer）缓存按 `<gid>-title/00000001.jpg`
 * 组织（8 位 1-based 页码 + 站点元数据 .ehviewer/.thumb），与服务器下载布局
 * `downloads/<gid>/%04d.<ext>`（DownloadService 同款，见 DownloadDirIndex）
 * 不一致。本服务把上传 zip 的每个顶层目录归一化为该布局（幂等覆盖），之后
 * 点「全部下载」（restart-all）即"磁盘校验通过 → 跳过"直接投入使用。
 *
 * - 目录名解析：取 entry 第一段目录名，前缀正则 `^(\d+)\b` 提 gid
 *   （`1014380-(C84) ...` → 1014380）；无数字前缀 → 该目录 skipped。
 * - 页文件：基名匹配 `^(\d{2,8})\.(jpg|jpeg|png|gif|webp)$`（大小写不敏感），
 *   页码去零为 1-based（`00000001.jpg` → 1 → `0001.jpg`）；以 `.` 开头的
 *   元数据（.ehviewer/.thumb）与非图片文件一并忽略（目录内无任何页文件 →
 *   目录 skipped，不入 imported 也不建目录）。
 * - 落盘：ZipInputStream 流式展开，绝不落临时文件再拷贝；`%04d.<ext>` 覆盖写。
 *   路径穿越防护：目标名由白名单数字+扩展名重建（不可能含分隔符），写入前仍以
 *   `<root>/<gid>` 规范化前缀校验（防 zip 内 `../` entry）。
 * - 完成化：每个落盘 gid 调 [completeIfVerified]——行存在且 total>0 且目录下
 *   `%04d.*`（>0 字节）文件数 ≥ total → state=3/done=total/error=null；
 *   total<=0 未决 → 不动（交给 restart-all 时机校验）；无行 → 仅落盘。
 * - 索引失效：每次 gid 目录写完后 [DownloadDirIndex.invalidate]，下一页访问重扫。
 *
 * 空文件 / MIME 不符 → [IllegalArgumentException]（控制器转 400 BAD_REQUEST）；
 * 其余异常（zip 损坏、IO）原样抛出归 GlobalExceptionHandler → 500。
 */
@Service
class DownloadZipImportService(
    private val downloadRepository: DownloadInfoRepository,
    private val config: SiteCoreConfigProperties,
    private val downloadDirIndex: DownloadDirIndex,
) {
    private val logger = LoggerFactory.getLogger(DownloadZipImportService::class.java)

    fun importZip(file: MultipartFile): ZipImportResponse {
        if (file.isEmpty) throw IllegalArgumentException("上传的 zip 文件为空")
        file.contentType?.lowercase()?.let { ct ->
            if (ct !in ALLOWED_ZIP_MIME) {
                throw IllegalArgumentException("不支持的文件类型: $ct（仅支持 zip）")
            }
        }

        val dirs = LinkedHashMap<String, DirAcc>()
        ZipInputStream(BufferedInputStream(file.inputStream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) consumeEntry(zip, entry, dirs)
                zip.closeEntry()
            }
        }

        var imported = 0
        var skipped = 0
        val importedGids = LinkedHashSet<Long>()
        dirs.forEach { (dirName, acc) ->
            if (acc.pages == 0) {
                skipped++
                logger.info("import-zip: 跳过目录 '{}'（无 gid 前缀或目录内无页面文件）", dirName)
            } else {
                imported++
                acc.gid?.let { gid -> importedGids += gid }
            }
        }

        var verified = 0
        importedGids.forEach { gid ->
            downloadDirIndex.invalidate(gid)
            if (completeIfVerified(gid)) verified++
        }
        val completedTotal = downloadRepository.countByState(3).toInt()
        logger.info(
            "import-zip 完成: imported={}, skipped={}, verified={}, completedTotal={}",
            imported, skipped, verified, completedTotal
        )
        return ZipImportResponse(imported, skipped, verified, completedTotal)
    }

    /** 单 entry 处理：解析目录名/页文件并当页落盘（跳过条目数据由 closeEntry 丢弃）。 */
    private fun consumeEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        dirs: MutableMap<String, DirAcc>,
    ) {
        val segments = entry.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        // 根级单段文件不构成缓存目录布局（没有目录名）——忽略，不计 skipped。
        if (segments.size < 2) return
        // 先登记目录再判页文件：仅含元数据/非图片文件的目录同样计入 skipped。
        val dirName = segments.first()
        val acc = dirs.getOrPut(dirName) { DirAcc(gidOf(dirName)) }
        val fileName = segments.last()
        // .ehviewer / .thumb 等以 . 开头元数据与其它非图片文件由 pageOf 过滤。
        if (fileName.startsWith(".")) return
        val page = pageOf(fileName) ?: return
        if (acc.gid == null) return
        writePage(zip, acc.gid, page)
        acc.pages++
    }

    /** 目录名 → gid：前缀正则 `^(\d+)\b`（`1014380-(C84) ...` → 1014380；`abc` → null）。 */
    private fun gidOf(dirName: String): Long? {
        val m = DIR_GID_PATTERN.find(dirName) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    /** 基名 → (1-based 页码, 小写扩展名)；白名单外、非数字或页码 ≤0 → null。 */
    private fun pageOf(fileName: String): PageSpec? {
        val m = PAGE_FILE_PATTERN.matchEntire(fileName) ?: return null
        val number = m.groupValues[1].toIntOrNull() ?: return null
        if (number <= 0) return null
        return PageSpec(number, m.groupValues[2].lowercase())
    }

    /** 流式写单页 `downloads/<gid>/%04d.<ext>`（幂等覆盖，不解压到磁盘再拷贝）。 */
    private fun writePage(zip: ZipInputStream, gid: Long, page: PageSpec) {
        val gidDir = File(config.download.path, gid.toString())
        gidDir.mkdirs()
        val target = resolveTarget(gidDir, page)
        target.outputStream().use { out -> zip.copyTo(out) }
    }

    /** 页面目标名由白名单重建（保留源文件名布局：%04d/%08d 均可，写入前规范化前缀校验防穿越）。 */
    private fun resolveTarget(gidDir: File, page: PageSpec): File {
        // 2026-08-30（存储互用）：Android 缓存为 %08d，服务器写入 %08d——
        // 导入保留源文件名（%08d 或 %04d 均接受），不再强制归一。
        val target = File(gidDir, "%08d.%s".format(page.number, page.ext))
        val gidNorm = gidDir.toPath().toAbsolutePath().normalize()
        val targetNorm = target.toPath().toAbsolutePath().normalize()
        require(targetNorm.startsWith(gidNorm)) {
            "zip entry 越界: 目标仅允许位于 <root>/<gid>/ 之下（gid=${gidDir.name}）"
        }
        return target
    }

    /**
     * 落盘后完成化：行存在且 total>0 且目录下 `%04d.*`（>0 字节）文件数 ≥ total →
     * state=3/done=total/error=null 保存（视为 verified）。total<=0 未决 → 不动；
     * 无行 → 仅落盘（由 imported 计数）。
     */
    private fun completeIfVerified(gid: Long): Boolean {
        val row = downloadRepository.findByGid(gid) ?: return false
        val total = row.total
        if (total <= 0) return false
        val dir = File(config.download.path, gid.toString())
        val count = dir.listFiles { f ->
            f.isFile && f.length() > 0 && f.name.matches(DISK_PAGE_PATTERN)
        }?.size ?: 0
        if (count < total) return false
        if (row.state != 3) {
            row.state = 3
            row.done = total
            row.error = null
            downloadRepository.save(row)
            logger.info("import-zip: gid={} 磁盘校验通过（{} 页），标记完成", gid, count)
        }
        return true
    }

    private data class PageSpec(val number: Int, val ext: String)

    /** 顶层目录聚合：gid 解析一次（后续 entry 复用），pages 计数用于 imported/skipped。 */
    private class DirAcc(val gid: Long?) {
        var pages: Int = 0
    }

    private companion object {
        /** Android 缓存条目允许的 MIME；null 视为未声明类型，放行。 */
        val ALLOWED_ZIP_MIME = setOf(
            "application/zip", "application/x-zip-compressed", "application/octet-stream",
        )

        /** 目录名前缀 gid：`^(\d+)\b`（word 边界吸收 `-标题` 后缀）。 */
        val DIR_GID_PATTERN = Regex("^(\\d+)\\b")

        /** 页文件：2-8 位数字基名（Android 缓存为 8 位）+ 白名单图片扩展名。 */
        val PAGE_FILE_PATTERN = Regex(
            "^(\\d{2,8})\\.(jpg|jpeg|png|gif|webp)$",
            RegexOption.IGNORE_CASE,
        )

        /** 磁盘页判定（与 DownloadService.isVerifiedOnDisk / DownloadDirIndex 同款）。 */
        val DISK_PAGE_PATTERN = Regex("^\\d{4,}\\..+")
    }
}

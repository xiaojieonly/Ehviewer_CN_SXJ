package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.unifile.UniFile
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.coroutines.coroutineContext

class MergeDuplicateGalleryTask @JvmOverloads constructor(
    context: Context,
    private val taskId: String = "merge_duplicate_gallery_${System.currentTimeMillis()}",
    /** 鎸囧畾鍙壂鎻忓苟鍚堝苟鍗曚釜鐢诲粖锛坓id锛夛紝-1L 琛ㄧず鎵弿鍏ㄩ儴 */
    private val targetGid: Long = -1L
) : BaseBackgroundTask(context) {

    private val galleryGroups = mutableListOf<GalleryGroup>()
    private val errorLog = StringBuilder()
    private val mergeLog = StringBuilder()

    private var mergedCount = 0
    private var skippedCount = 0
    private var copiedCount = 0
    private var deletedCount = 0
    private var errorCount = 0
    private var lastError = ""

    /** 鏄惁涓哄崟鐢诲粖鍚堝苟妯″紡 */
    private val isSingleMode: Boolean get() = targetGid >= 0L

    /** 鍗曠敾寤婃ā寮忎笅缂撳瓨鐨勭敾寤婃爣棰橈紝鐢ㄤ簬浠诲姟鍚?*/
    private var singleTitle: String? = null

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = if (isSingleMode) {
        val title = singleTitle
        if (title != null) {
            context.getString(R.string.merge_duplicate_gallery_single_title) + " - $title"
        } else {
            context.getString(R.string.merge_duplicate_gallery_single_title)
        }
    } else {
        context.getString(R.string.settings_download_merge_duplicate_gallery)
    }

    override fun getTaskDescription(): String = if (isSingleMode) {
        context.getString(R.string.merge_duplicate_gallery_single_summary)
    } else {
        context.getString(R.string.settings_download_merge_duplicate_gallery_summary)
    }

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.MERGE

    override fun isUniqueTask(): Boolean = !isSingleMode

    override fun isPersistable(): Boolean = !isSingleMode

    override fun getTaskPersistData(): String {
        return JSONObject().toString()
    }

    companion object {
        private const val TAG = "MergeDuplicateGalleryTask"

        /**
         * 鍚堝苟鎵弿缁撴灉缂撳瓨锛? 灏忔椂鍐呭叡浜壂鎻忕粨鏋滈伩鍏嶉噸澶嶈В鏋愭枃浠跺厓鏁版嵁銆?
         * 瀹氫箟鍦?companion object 鍐呬互渚胯闂鏈夌殑 GalleryFolder 绫诲瀷銆?
         */
        object MergeScanCache {
            private const val CACHE_TTL_MS = 60 * 60 * 1000L

            @Volatile
            private var scanTime: Long = 0L
            @Volatile
            private var cachedBuckets: Map<String, List<GalleryFolder>>? = null

            @Synchronized
            fun get(): Map<String, List<GalleryFolder>>? {
                return if (System.currentTimeMillis() - scanTime < CACHE_TTL_MS) {
                    cachedBuckets
                } else {
                    null
                }
            }

            @Synchronized
            fun put(buckets: Map<String, List<GalleryFolder>>) {
                scanTime = System.currentTimeMillis()
                cachedBuckets = buckets
            }

            @Synchronized
            fun invalidate() {
                scanTime = 0L
                cachedBuckets = null
            }
        }

        const val STEP_SCAN = 0
        const val STEP_ANALYZE = 1
        const val STEP_MERGE = 2
        const val STEP_BACKUP = 3

        private const val SCAN_WEIGHT = 20
        private const val ANALYZE_WEIGHT = 20
        private const val BACKUP_WEIGHT = 10
        private const val MERGE_WEIGHT = 50
        private const val HASH_SIMILARITY_THRESHOLD = 0.75f

        private val IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".bmp"
        )

        @JvmStatic
        fun restore(context: Context, taskId: String, persistData: String?): MergeDuplicateGalleryTask? {
            if (persistData == null) {
                return null
            }
            return try {
                MergeDuplicateGalleryTask(context, taskId)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * 涓哄崟涓凡涓嬭浇瀹屾垚鐨勭敾寤婂垱寤哄悎骞朵换鍔★紝鍙壂鎻忎笌鍏跺悓鍚嶇殑鍏朵粬鐢诲粖鐩綍
         */
        @JvmStatic
        fun mergeForGallery(context: Context, gid: Long): MergeDuplicateGalleryTask {
            val taskId = "merge_single_${gid}_${System.currentTimeMillis()}"
            val task = MergeDuplicateGalleryTask(context, taskId, gid)
            // 棰勫厛鍔犺浇鏍囬鐢ㄤ簬浠诲姟鍚?
            val info = EhDB.getDownloadInfo(gid)
            if (info != null) {
                val title = com.hippo.ehviewer.client.EhUtils.getSuitableTitle(info)
                task.singleTitle = title
            }
            return task
        }
    }

    override suspend fun execute(): Result<Unit> {
        initErrorLog()
        initMergeLog()
        return try {
            logInfo("寮€濮嬫壂鎻忎笅杞界洰褰?)
            dispatchProgress(STEP_SCAN, context.getString(R.string.merge_scanning_galleries), 0, 1)
            ensureNotCancelled()
            if (!scanDownloadedGalleries()) {
                logError("鎵弿涓嬭浇鐩綍澶辫触")
                return Result.failure(IllegalStateException(lastError.ifEmpty { "鎵弿涓嬭浇鐩綍澶辫触" }))
            }

            ensureNotCancelled()
            logInfo("寮€濮嬪垎鏋愰噸澶嶇敾寤?)
            dispatchProgress(STEP_ANALYZE, context.getString(R.string.merge_analyzing_galleries), 0, 1)
            if (!analyzeDuplicateGalleries()) {
                logError("鍒嗘瀽閲嶅鐢诲粖澶辫触")
                return Result.failure(IllegalStateException(lastError.ifEmpty { "鍒嗘瀽閲嶅鐢诲粖澶辫触" }))
            }

            ensureNotCancelled()
            logInfo("寮€濮嬪浠芥暟鎹簱")
            dispatchProgress(STEP_BACKUP, context.getString(R.string.merge_backing_up_database), 0, 1)
            if (!backupDatabase()) {
                logError("澶囦唤鏁版嵁搴撳け璐?)
                return Result.failure(IllegalStateException(lastError.ifEmpty { "澶囦唤鏁版嵁搴撳け璐? }))
            }

            ensureNotCancelled()
            logInfo("寮€濮嬪悎骞堕噸澶嶇敾寤?)
            dispatchProgress(STEP_MERGE, context.getString(R.string.merge_merging_galleries), 0, maxOf(galleryGroups.size, 1))
            if (!mergeDuplicateGalleries()) {
                logError("鍚堝苟閲嶅鐢诲粖澶辫触")
                return Result.failure(IllegalStateException(lastError.ifEmpty { "鍚堝苟閲嶅鐢诲粖澶辫触" }))
            }

            notifyCompleted()
            logInfo("浠诲姟瀹屾垚: 鍚堝苟 $mergedCount 缁勶紝璺宠繃 $skippedCount 缁勶紝澶嶅埗 $copiedCount 涓枃浠讹紝鍒犻櫎 $deletedCount 涓簮鐩綍")
            Result.success(Unit)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) {
                notifyCancelled()
                logInfo("浠诲姟鍙栨秷")
                return Result.failure(e)
            }
            lastError = e.message ?: "鏈煡閿欒"
            logError("鍚堝苟杩囩▼涓彂鐢熼敊璇? ${e.message}")
            generateErrorReport()
            notifyError(e)
            Result.failure(e)
        }
    }

    private suspend fun ensureNotCancelled() {
        coroutineContext.ensureActive()
    }

    private fun dispatchProgress(step: Int, message: String, current: Int, total: Int) {
        val percent = calculateWeightedPercent(step, current, total)
        if (total > 0) {
            val detail = "$message (${minOf(current, total)}/$total)"
            updateProgress(percent, detail)
        } else {
            updateProgress(percent, message)
        }
    }

    private fun calculateWeightedPercent(step: Int, current: Int, total: Int): Int {
        val (base, span) = when (step) {
            STEP_SCAN -> 0 to SCAN_WEIGHT
            STEP_ANALYZE -> SCAN_WEIGHT to ANALYZE_WEIGHT
            STEP_BACKUP -> (SCAN_WEIGHT + ANALYZE_WEIGHT) to BACKUP_WEIGHT
            STEP_MERGE -> (SCAN_WEIGHT + ANALYZE_WEIGHT + BACKUP_WEIGHT) to MERGE_WEIGHT
            else -> return 0
        }
        if (total <= 0) {
            return base
        }
        val stagePercent = ((minOf(maxOf(current, 0), total) * 100L) / total).toInt()
        return base + stagePercent * span / 100
    }

    private fun initErrorLog() {
        errorLog.setLength(0)
        errorLog.append("=== 鍚堝苟閲嶅鐢诲粖閿欒鏃ュ織 ===\n")
        errorLog.append("寮€濮嬫椂闂? ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("\n\n")
    }

    private fun initMergeLog() {
        mergeLog.setLength(0)
        mergeLog.append("=== 鍚堝苟閲嶅鐢诲粖鎿嶄綔鏃ュ織 ===\n")
        mergeLog.append("寮€濮嬫椂闂? ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("\n\n")
    }

    private fun logError(message: String) {
        errorLog.append("[")
            .append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("] ERROR: ")
            .append(message)
            .append("\n")
        appendTaskLog("ERROR: %s", message)
        Log.e(TAG, message)
    }

    private fun logInfo(message: String) {
        mergeLog.append("[")
            .append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("] INFO: ")
            .append(message)
            .append("\n")
        appendTaskLog("INFO: %s", message)
        Log.i(TAG, message)
    }

    private fun generateErrorReport() {
        try {
            val downloadDir = Settings.getDownloadLocation() ?: return
            val sdf = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
            val fileName = "merge-error-${sdf.format(Date())}.log"
            val logFile = downloadDir.createFile(fileName)
            if (logFile != null) {
                logFile.openOutputStream().use { os ->
                    os?.write(errorLog.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate error report", e)
        }
    }

    private suspend fun scanDownloadedGalleries(): Boolean {
        return try {
            // 0. 灏濊瘯浣跨敤宸叉湁缂撳瓨锛?灏忔椂鍐呮湁鏁堬級
            val cachedBuckets = MergeScanCache.get()
            if (cachedBuckets != null) {
                logInfo("浣跨敤缂撳瓨鎵弿缁撴灉锛?{cachedBuckets.size} 缁勶級")
                return rebuildFromCache(cachedBuckets)
            }

            var infos = EhDB.getAllDownloadInfo()
            if (infos == null) {
                infos = Collections.emptyList()
            }

            // 鍗曠敾寤婃ā寮忥細鍏堟壘鍒扮洰鏍囩敾寤婂強鍏舵竻鐞嗗悗鐨勫悕瀛楋紝鍙壂鎻忓尮閰嶇殑鏂囦欢澶?
            val targetCleanName: String? = if (isSingleMode) {
                val targetInfo = infos.find { it.gid == targetGid }
                if (targetInfo == null) {
                    lastError = "鏈壘鍒扮洰鏍囦笅杞借褰? gid=$targetGid"
                    logError(lastError)
                    return false
                }
                val targetDir = SpiderDen.getGalleryDownloadDir(targetInfo)
                if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory) {
                    lastError = "鐩爣涓嬭浇鐩綍涓嶅瓨鍦? gid=$targetGid"
                    logError(lastError)
                    return false
                }
                val targetName = targetDir.name ?: targetInfo.gid.toString()
                removeIdPrefix(targetName).also {
                    logInfo("鍗曠敾寤婃ā寮? 鐩爣鐢诲粖宸叉竻鐞嗗悕绉?\"$it\" (gid=$targetGid)")
                }
            } else {
                null
            }

            val buckets = LinkedHashMap<String, MutableList<GalleryFolder>>()
            val total = infos.size
            logInfo("鎵弿鍒?$total 涓笅杞借褰?{if (isSingleMode) "锛堝崟鐢诲粖妯″紡锛? else ""}")

            for (i in infos.indices) {
                ensureNotCancelled()
                val info = infos[i]
                val dir = SpiderDen.getGalleryDownloadDir(info)
                if (dir == null || !dir.exists() || !dir.isDirectory) {
                    dispatchProgress(STEP_SCAN, "鎵弿涓? ${i + 1}/$total", i + 1, maxOf(total, 1))
                    continue
                }

                val name = dir.name ?: info.gid.toString()
                val cleanName = removeIdPrefix(name)

                // 鍗曠敾寤婃ā寮忥細鎻愬墠杩囨护闈炲尮閰嶇洰褰曪紝閬垮厤鏄傝吹鐨勫厓鏁版嵁瑙ｆ瀽
                if (isSingleMode && cleanName != targetCleanName) {
                    dispatchProgress(STEP_SCAN, "鎵弿涓? ${i + 1}/$total", i + 1, maxOf(total, 1))
                    continue
                }

                // 鍙鍖归厤鐨勭洰褰曡繘琛屽畬鏁寸殑鍏冩暟鎹В鏋?
                val folder = GalleryFolder(
                    info = info,
                    dir = dir,
                    name = name,
                    id = extractIdFromFolderName(name),
                    modifiedAt = maxOf(dir.lastModified(), 0L),
                    fileCount = countFiles(dir),
                    ehMeta = parseEhviewerMeta(dir)
                )

                logInfo(
                    "鎵弿鍒颁笅杞界洰褰? ${folder.name}锛屾枃浠舵暟=${folder.fileCount}锛屼慨鏀规椂闂?${folder.modifiedAt}锛屾槸鍚︽湁ehviewer鍏冩暟鎹?${folder.ehMeta != null}"
                )

                buckets.getOrPut(cleanName) { mutableListOf() }.add(folder)
                dispatchProgress(STEP_SCAN, "鎵弿涓? ${i + 1}/$total", i + 1, maxOf(total, 1))
            }

            // 瀛樺叆缂撳瓨渚?1 灏忔椂鍐呭叾浠栦换鍔″鐢?
            if (!isSingleMode) {
                MergeScanCache.put(HashMap(buckets))
                logInfo("鍏ㄩ噺鎵弿缁撴灉宸茬紦瀛樿嚦 MergeScanCache")
            }

            galleryGroups.clear()
            for ((key, value) in buckets) {
                if (value.size < 2) {
                    continue
                }
                galleryGroups.add(
                    GalleryGroup(
                        cleanedName = key,
                        folders = value.toMutableList()
                    )
                )
            }

            if (isSingleMode && galleryGroups.isEmpty()) {
                logInfo("鍗曠敾寤婃ā寮? 鏈彂鐜扮洰鏍囩敾寤婄殑閲嶅锛屾棤闇€鍚堝苟")
            } else {
                logInfo("鎵弿瀹屾垚锛屽彂鐜?${galleryGroups.size} 缁勫€欓€夐噸澶嶇敾寤?)
            }
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "鎵弿澶辫触"
            logError("鎵弿澶辫触: ${t.message}")
            false
        }
    }

    /**
     * 浠庣紦瀛樼殑鍏ㄩ噺 buckets 涓噸寤?galleryGroups銆?
     * 鍗曠敾寤婃ā寮忥細鍙彁鍙栫洰鏍囩敾寤婃墍鍦ㄧ粍锛涘叏閲忔ā寮忥細鎻愬彇鎵€鏈?>=2 鐨勭粍銆?
     */
    private fun rebuildFromCache(cachedBuckets: Map<String, List<GalleryFolder>>): Boolean {
        galleryGroups.clear()

        if (isSingleMode) {
            // 鍏堟壘鍒扮洰鏍?gid 瀵瑰簲鐨?cleanName
            val targetCleanName = findTargetCleanNameInCache(cachedBuckets)
            if (targetCleanName == null) {
                logInfo("鍗曠敾寤婃ā寮? 缂撳瓨涓湭鎵惧埌鐩爣鐢诲粖 gid=$targetGid锛岄噸鏂版壂鎻?)
                MergeScanCache.invalidate()
                return false
            }
            val folders = cachedBuckets[targetCleanName]
            if (folders == null || folders.size < 2) {
                logInfo("鍗曠敾寤婃ā寮? 缂撳瓨涓洰鏍囩敾寤?\"$targetCleanName\" 鏃犻噸澶嶏紝鏃犻渶鍚堝苟")
                return true
            }
            galleryGroups.add(
                GalleryGroup(
                    cleanedName = targetCleanName,
                    folders = folders.toMutableList()
                )
            )
            logInfo("浠庣紦瀛樻彁鍙栧垎缁? ${galleryGroups[0].cleanedName} (${galleryGroups[0].folders.size} 涓洰褰?")
        } else {
            for ((key, value) in cachedBuckets) {
                if (value.size < 2) continue
                galleryGroups.add(
                    GalleryGroup(
                        cleanedName = key,
                        folders = value.toMutableList()
                    )
                )
            }
            logInfo("浠庣紦瀛橀噸寤?${galleryGroups.size} 缁勫€欓€夐噸澶嶇敾寤?)
        }
        return true
    }

    /** 鍦ㄧ紦瀛?buckets 涓煡鎵惧寘鍚洰鏍?gid 鐨勫垎缁?key */
    private fun findTargetCleanNameInCache(buckets: Map<String, List<GalleryFolder>>): String? {
        for ((key, folders) in buckets) {
            for (f in folders) {
                if (f.info.gid == targetGid) {
                    return key
                }
            }
        }
        return null
    }

    private suspend fun analyzeDuplicateGalleries(): Boolean {
        return try {
            val total = maxOf(galleryGroups.size, 1)
            logInfo("寮€濮嬪垎鏋?${galleryGroups.size} 缁勫€欓€夐噸澶嶇敾寤?)
            for (i in galleryGroups.indices) {
                ensureNotCancelled()
                val group = galleryGroups[i]
                group.type = analyzeGroupRelationship(group.folders)
                group.target = chooseTargetFolder(group)
                group.reason = buildTargetReason(group)
                dispatchProgress(
                    STEP_ANALYZE,
                    "鍒嗘瀽涓? ${group.cleanedName} (${i + 1}/$total)",
                    i + 1,
                    total
                )
                logInfo("鍒嗘瀽鍒嗙粍 ${group.cleanedName} 绫诲瀷=${group.type.name}锛?{group.reason}")
            }
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "鍒嗘瀽澶辫触"
            logError("鍒嗘瀽澶辫触: ${t.message}")
            false
        }
    }

    private fun backupDatabase(): Boolean {
        return try {
            dispatchProgress(STEP_BACKUP, "澶囦唤鏁版嵁搴撲腑...", 0, 1)
            logInfo("寮€濮嬪浠芥暟鎹簱")
            val backedUp = EhDB.backupDatabase(context)
            dispatchProgress(STEP_BACKUP, if (backedUp) "鏁版嵁搴撳浠藉畬鎴? else "鏁版嵁搴撳浠藉け璐?, 1, 1)
            if (!backedUp) {
                lastError = "鏁版嵁搴撳浠藉け璐?
                logError(lastError)
            } else {
                logInfo("鏁版嵁搴撳浠芥垚鍔?)
            }
            backedUp
        } catch (t: Throwable) {
            lastError = t.message ?: "澶囦唤澶辫触"
            logError("澶囦唤澶辫触: ${t.message}")
            false
        }
    }

    private suspend fun mergeDuplicateGalleries(): Boolean {
        return try {
            logInfo("寮€濮嬪悎骞?${galleryGroups.size} 缁勫€欓€夐噸澶嶇敾寤?)
            if (galleryGroups.isEmpty()) {
                return true
            }

            var hasError = false
            val total = galleryGroups.size
            for (i in galleryGroups.indices) {
                ensureNotCancelled()
                val group = galleryGroups[i]
                val stats = processGroup(group)
                copiedCount += stats.copied
                deletedCount += stats.deleted
                errorCount += stats.errors
                if (stats.errors > 0) {
                    hasError = true
                    skippedCount++
                } else {
                    mergedCount++
                }
                dispatchProgress(
                    STEP_MERGE,
                    "鍚堝苟涓? ${group.cleanedName} [${group.type.name}]",
                    i + 1,
                    total
                )
            }

            if (hasError) {
                lastError = "閮ㄥ垎鍒嗙粍鍚堝苟澶辫触"
                false
            } else {
                true
            }
        } catch (t: Throwable) {
            lastError = t.message ?: "鍚堝苟澶辫触"
            logError("鍚堝苟澶辫触: ${t.message}")
            false
        }
    }

    private fun analyzeGroupRelationship(folders: List<GalleryFolder>): RelationshipType {
        val withEh = mutableListOf<GalleryFolder>()
        for (folder in folders) {
            if (folder.ehMeta != null && folder.ehMeta.hashes.isNotEmpty()) {
                withEh.add(folder)
            }
        }
        if (withEh.isEmpty()) {
            return RelationshipType.NO_EHVIEWER
        }

        var allSameIdentity = true
        var firstIdentity: String? = null
        for (folder in withEh) {
            val identity = folder.ehMeta!!.gid + "#" + folder.ehMeta!!.token
            if (firstIdentity == null) {
                firstIdentity = identity
            } else if (firstIdentity != identity) {
                allSameIdentity = false
            }
        }
        if (allSameIdentity) {
            return RelationshipType.DUPLICATE
        }

        var common: MutableSet<String>? = null
        var allEqual = true
        val firstSet = withEh[0].ehMeta!!.hashes

        for (folder in withEh) {
            val hashes = folder.ehMeta!!.hashes
            if (common == null) {
                common = HashSet(hashes)
            } else {
                common.retainAll(hashes)
            }
            if (hashes != firstSet) {
                allEqual = false
            }
        }

        if (allEqual) {
            return RelationshipType.DUPLICATE
        }

        val sorted = withEh.toMutableList()
        sorted.sortBy { it.ehMeta!!.hashes.size }
        var progressive = true
        for (i in 0 until sorted.size - 1) {
            if (!sorted[i + 1].ehMeta!!.hashes.containsAll(sorted[i].ehMeta!!.hashes)) {
                progressive = false
                break
            }
        }
        if (progressive) {
            return RelationshipType.PROGRESSIVE
        }

        if (common != null && common.isNotEmpty()) {
            return RelationshipType.PARTIAL_OVERLAP
        }

        var bestSimilarity = 0f
        for (i in withEh.indices) {
            for (j in i + 1 until withEh.size) {
                bestSimilarity = maxOf(
                    bestSimilarity,
                    jaccard(withEh[i].ehMeta!!.hashes, withEh[j].ehMeta!!.hashes)
                )
            }
        }

        return if (bestSimilarity >= HASH_SIMILARITY_THRESHOLD) {
            RelationshipType.PARTIAL_OVERLAP
        } else {
            RelationshipType.NO_OVERLAP
        }
    }

    private fun chooseTargetFolder(group: GalleryGroup): GalleryFolder? {
        if (group.folders.isEmpty()) {
            return null
        }

        if (group.type == RelationshipType.PROGRESSIVE) {
            var best: GalleryFolder? = null
            var bestSize = -1
            for (folder in group.folders) {
                val size = folder.ehMeta?.hashes?.size ?: 0
                if (size > bestSize) {
                    best = folder
                    bestSize = size
                }
            }
            if (best != null) {
                return best
            }
        }

        val sorted = group.folders.toMutableList()
        sorted.sortWith { a, b ->
            val aHasEh = a.ehMeta != null
            val bHasEh = b.ehMeta != null
            if (aHasEh != bHasEh) {
                return@sortWith if (aHasEh) -1 else 1
            }
            if (a.modifiedAt != b.modifiedAt) {
                return@sortWith if (a.modifiedAt > b.modifiedAt) -1 else 1
            }
            if (a.fileCount != b.fileCount) {
                return@sortWith b.fileCount.compareTo(a.fileCount)
            }
            val aid = a.id ?: -1
            val bid = b.id ?: -1
            bid.compareTo(aid)
        }
        return sorted[0]
    }

    private fun buildTargetReason(group: GalleryGroup): String {
        val target = group.target ?: return "鏃犲彲鐢ㄧ洰鏍?
        return "淇濈暀 ${target.name}锛?ehviewer=${if (target.ehMeta != null) "鏈? else "鏃?}锛屼慨鏀规椂闂?${target.modifiedAt}锛屾枃浠舵暟=${target.fileCount}锛?
    }

    private fun processGroup(group: GalleryGroup): MergeStats {
        val stats = MergeStats()
        val target = group.target
        if (target == null) {
            stats.errors++
            logError("鍒嗙粍 ${group.cleanedName} 鏃犲彲鐢ㄧ洰鏍?)
            return stats
        }

        val sources = mutableListOf<GalleryFolder>()
        for (folder in group.folders) {
            if (folder !== target) {
                sources.add(folder)
            }
        }
        if (sources.isEmpty()) {
            stats.skipped++
            return stats
        }

        logInfo("澶勭悊鍒嗙粍 ${group.cleanedName} 绫诲瀷=${group.type.name}锛?{group.reason}")

        if (group.type == RelationshipType.DUPLICATE || group.type == RelationshipType.PROGRESSIVE) {
            for (source in sources) {
                if (deleteRecursively(source.dir)) {
                    EhDB.removeDownloadDirname(source.info.gid)
                    EhDB.removeDownloadInfo(source.info.gid)
                    stats.deleted++
                } else {
                    logError("鍒犻櫎婧愮洰褰曞け璐? ${source.name}")
                    stats.errors++
                }
            }
            return stats
        }

        val mergeStats = if (group.type == RelationshipType.NO_EHVIEWER) {
            mergeByMd5(target, sources)
        } else {
            mergeByEhviewer(target, sources)
        }

        stats.copied += mergeStats.copied
        stats.skipped += mergeStats.skipped
        stats.errors += mergeStats.errors

        if (stats.errors == 0) {
            for (source in sources) {
                if (deleteRecursively(source.dir)) {
                    EhDB.removeDownloadDirname(source.info.gid)
                    EhDB.removeDownloadInfo(source.info.gid)
                    stats.deleted++
                } else {
                    logError("鍚堝苟鍚庡垹闄ゆ簮鐩綍澶辫触: ${source.name}")
                    stats.errors++
                }
            }
        }
        return stats
    }

    private fun mergeByEhviewer(target: GalleryFolder, sources: List<GalleryFolder>): MergeStats {
        val targetMeta = target.ehMeta ?: return mergeByMd5(target, sources)

        val targetHashes = HashSet(targetMeta.hashes)
        var maxIndex = -1
        for (idx in targetMeta.files.keys) {
            if (idx > maxIndex) {
                maxIndex = idx
            }
        }

        val newEntries = mutableListOf<IntArray>()
        val newHashes = mutableListOf<String>()
        val stats = MergeStats()
        var currentIndex = maxIndex + 1

        for (source in sources) {
            val sourceMeta = source.ehMeta
            if (sourceMeta == null) {
                val fallback = mergeByMd5(target, Collections.singletonList(source))
                stats.copied += fallback.copied
                stats.skipped += fallback.skipped
                stats.errors += fallback.errors
                continue
            }

            val hashToFile = buildHashToFilepath(source, sourceMeta)
            for ((hash, sourceFile) in hashToFile) {
                if (targetHashes.contains(hash)) {
                    stats.skipped++
                    continue
                }
                val ext = extensionOf(sourceFile.name)
                val fileName = String.format(Locale.US, "%06d%s", currentIndex + 1, ext)
                val targetFile = createUniqueFile(target.dir, fileName)
                if (targetFile == null || !copyFile(sourceFile, targetFile)) {
                    stats.errors++
                    logError("澶嶅埗澶辫触: ${sourceFile.name} -> $fileName")
                    continue
                }
                newEntries.add(intArrayOf(currentIndex))
                newHashes.add(hash)
                targetHashes.add(hash)
                currentIndex++
                stats.copied++
            }
        }

        if (newEntries.isNotEmpty() && !appendToEhviewer(target.dir, newEntries, newHashes)) {
            stats.errors++
        }

        return stats
    }

    private fun mergeByMd5(target: GalleryFolder, sources: List<GalleryFolder>): MergeStats {
        val stats = MergeStats()
        val targetMd5 = collectImageMd5(target.dir)

        for (source in sources) {
            val files = source.dir.listFiles() ?: continue
            for (file in files) {
                if (file == null || !file.isFile) {
                    continue
                }
                val name = file.name ?: continue
                if (SpiderQueen.SPIDER_INFO_FILENAME == name) {
                    continue
                }

                if (isImageFile(name)) {
                    val md5 = md5Of(file)
                    if (md5 == null) {
                        stats.errors++
                        continue
                    }
                    if (targetMd5.containsKey(md5)) {
                        stats.skipped++
                        continue
                    }
                    val targetFile = createUniqueFile(target.dir, name)
                    if (targetFile == null || !copyFile(file, targetFile)) {
                        stats.errors++
                    } else {
                        targetMd5[md5] = targetFile.name
                        stats.copied++
                    }
                } else {
                    val existed = target.dir.findFile(name)
                    if (existed != null) {
                        stats.skipped++
                        continue
                    }
                    val targetFile = target.dir.createFile(name)
                    if (targetFile == null || !copyFile(file, targetFile)) {
                        stats.errors++
                    } else {
                        stats.copied++
                    }
                }
            }
        }

        return stats
    }

    private fun buildHashToFilepath(folder: GalleryFolder, meta: EhviewerMeta): Map<String, UniFile> {
        val result = HashMap<String, UniFile>()
        val imageFiles = getSortedImageFiles(folder.dir)
        for ((idx, hash) in meta.files) {
            if (idx < 0 || idx >= imageFiles.size) {
                continue
            }
            result[hash] = imageFiles[idx]
        }
        return result
    }

    private fun getSortedImageFiles(dir: UniFile): List<UniFile> {
        val files = mutableListOf<UniFile>()
        val children = dir.listFiles() ?: return files
        for (child in children) {
            if (child != null && child.isFile && isImageFile(child.name)) {
                files.add(child)
            }
        }
        files.sortWith { a, b ->
            val an = a.name
            val bn = b.name
            when {
                an == null && bn == null -> 0
                an == null -> 1
                bn == null -> -1
                else -> an.compareTo(bn, ignoreCase = true)
            }
        }
        return files
    }

    private fun appendToEhviewer(targetDir: UniFile, entries: List<IntArray>, hashes: List<String>): Boolean {
        val ehFile = targetDir.findFile(SpiderQueen.SPIDER_INFO_FILENAME) ?: return false
        var os: OutputStream? = null
        return try {
            os = ehFile.openOutputStream(true)
            for (i in entries.indices) {
                val line = "\n${entries[i][0]} ${hashes[i]}"
                os?.write(line.toByteArray(StandardCharsets.UTF_8))
            }
            os?.flush()
            true
        } catch (e: IOException) {
            logError("杩藉姞 .ehviewer 澶辫触: ${e.message}")
            false
        } finally {
            closeQuietly(os)
        }
    }

    private fun collectImageMd5(dir: UniFile): MutableMap<String, String?> {
        val md5Map = HashMap<String, String?>()
        val files = dir.listFiles() ?: return md5Map
        for (file in files) {
            if (file != null && file.isFile && isImageFile(file.name)) {
                val md5 = md5Of(file)
                if (md5 != null) {
                    md5Map[md5] = file.name
                }
            }
        }
        return md5Map
    }

    private fun copyFile(source: UniFile, target: UniFile): Boolean {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        return try {
            inputStream = BufferedInputStream(source.openInputStream())
            outputStream = target.openOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = inputStream.read(buffer)
                if (count == -1) {
                    break
                }
                outputStream?.write(buffer, 0, count)
            }
            outputStream?.flush()
            true
        } catch (_: IOException) {
            false
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
        }
    }

    private fun createUniqueFile(dir: UniFile, preferredName: String): UniFile? {
        var name = preferredName
        val base = baseName(name)
        val ext = extensionOf(name)
        var suffix = 1
        while (dir.findFile(name) != null && suffix <= 1000) {
            name = "${base}_$suffix$ext"
            suffix++
        }
        return dir.createFile(name)
    }

    private fun deleteRecursively(file: UniFile?): Boolean {
        if (file == null || !file.exists()) {
            return true
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursively(child)) {
                        return false
                    }
                }
            }
        }
        return file.delete()
    }

    private fun parseEhviewerMeta(dir: UniFile): EhviewerMeta? {
        val file = dir.findFile(SpiderQueen.SPIDER_INFO_FILENAME)
        if (file == null || !file.exists() || !file.isFile) {
            return null
        }

        var inputStream: InputStream? = null
        var reader: BufferedReader? = null
        return try {
            val meta = EhviewerMeta()
            inputStream = file.openInputStream()
            reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val lines = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: break
                lines.add(line)
            }
            if (lines.size < 3) {
                return null
            }

            meta.gid = safeTrim(lines[1])
            meta.token = safeTrim(lines[2])
            for (i in 3 until lines.size) {
                val raw = lines[i] ?: continue
                val parts = raw.trim().split(Regex("\\s+"))
                if (parts.size < 2) {
                    continue
                }
                try {
                    val idx = parts[0].toInt()
                    val hash = parts[1]
                    meta.files[idx] = hash
                    meta.hashes.add(hash)
                } catch (_: NumberFormatException) {
                }
            }
            meta
        } catch (_: IOException) {
            null
        } finally {
            closeQuietly(reader)
            closeQuietly(inputStream)
        }
    }

    private fun countFiles(dir: UniFile?): Int {
        if (dir == null || !dir.exists()) {
            return 0
        }
        if (dir.isFile) {
            return 1
        }
        var total = 0
        val children = dir.listFiles()
        if (children != null) {
            for (child in children) {
                total += countFiles(child)
            }
        }
        return total
    }

    private fun removeIdPrefix(name: String?): String {
        if (name == null) {
            return ""
        }
        var cleaned = name.replace(Regex("^\\d+-"), "")
        cleaned = cleaned.replace("馃攧", "").trim()
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKC)
        return cleaned.lowercase(Locale.ROOT)
    }

    private fun extractIdFromFolderName(name: String?): Int? {
        if (name == null) {
            return null
        }
        val idx = name.indexOf('-')
        if (idx <= 0) {
            return null
        }
        return try {
            name.substring(0, idx).toInt()
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun jaccard(a: Set<String>?, b: Set<String>?): Float {
        if (a.isNullOrEmpty() || b.isNullOrEmpty()) {
            return 0f
        }
        val inter = HashSet(a)
        inter.retainAll(b)
        val union = HashSet(a)
        union.addAll(b)
        if (union.isEmpty()) {
            return 0f
        }
        return inter.size * 1.0f / union.size
    }

    private fun md5Of(file: UniFile): String? {
        var inputStream: InputStream? = null
        return try {
            val digest = MessageDigest.getInstance("MD5")
            inputStream = file.openInputStream()
            val buffer = ByteArray(65536)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
            val hash = digest.digest()
            val sb = StringBuilder(hash.size * 2)
            for (b in hash) {
                sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                sb.append(Character.forDigit(b.toInt() and 0xF, 16))
            }
            sb.toString()
        } catch (_: Throwable) {
            null
        } finally {
            closeQuietly(inputStream)
        }
    }

    private fun isImageFile(name: String?): Boolean {
        if (name == null) {
            return false
        }
        val ext = extensionOf(name).lowercase(Locale.ROOT)
        return IMAGE_EXTENSIONS.contains(ext)
    }

    private fun extensionOf(name: String?): String {
        if (name == null) {
            return ""
        }
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ""
    }

    private fun baseName(name: String?): String {
        if (name == null) {
            return "file"
        }
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(0, dot) else name
    }

    private fun safeTrim(value: String?): String {
        return value?.trim() ?: ""
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        if (closeable == null) {
            return
        }
        try {
            closeable.close()
        } catch (_: Exception) {
        }
    }

    private data class GalleryGroup(
        val cleanedName: String,
        val folders: MutableList<GalleryFolder>,
        var type: RelationshipType = RelationshipType.NO_EHVIEWER,
        var target: GalleryFolder? = null,
        var reason: String = ""
    )

    data class GalleryFolder(
        val info: DownloadInfo,
        val dir: UniFile,
        val name: String,
        val id: Int?,
        val modifiedAt: Long,
        val fileCount: Int,
        val ehMeta: EhviewerMeta?
    )

    class EhviewerMeta {
        var gid: String = ""
        var token: String = ""
        val files: MutableMap<Int, String> = HashMap()
        val hashes: MutableSet<String> = HashSet()
    }

    private class MergeStats {
        var copied = 0
        var skipped = 0
        var errors = 0
        var deleted = 0
    }

    private enum class RelationshipType {
        DUPLICATE,
        PROGRESSIVE,
        PARTIAL_OVERLAP,
        NO_OVERLAP,
        NO_EHVIEWER
    }
}

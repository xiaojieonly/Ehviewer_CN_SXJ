/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui.scene.download.part

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import com.hippo.android.resource.AttrResources
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.ehviewer.Analytics.recordException
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCacheKeyFactory
import com.hippo.ehviewer.client.EhUtils.getCategory
import com.hippo.ehviewer.client.EhUtils.getCategoryColor
import com.hippo.ehviewer.client.EhUtils.getSuitableTitle
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadService
import com.hippo.ehviewer.gallery.A7ZipArchive
import com.hippo.ehviewer.gallery.A7ZipArchive.A7ZipArchiveEntry
import com.hippo.ehviewer.gallery.Pipe
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.ui.scene.TransitionNameFactory
import com.hippo.ehviewer.ui.scene.download.DownloadsScene
import com.hippo.ehviewer.ui.scene.download.part.DownloadAdapter.DownloadHolder
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene
import com.hippo.ehviewer.ui.scene.gallery.list.EnterGalleryDetailTransaction
import com.hippo.ehviewer.widget.SimpleRatingView
import com.hippo.lib.yorozuya.AssertUtils
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.lib.yorozuya.ViewUtils
import com.hippo.ripple.Ripple
import com.hippo.scene.Announcer
import com.hippo.unifile.UniFile
import com.hippo.unifile.UniRandomAccessFile
import com.hippo.util.NaturalComparator
import com.hippo.widget.LoadImageView
import java.util.Collections
import java.util.Locale
import kotlin.math.min
import androidx.core.graphics.drawable.toDrawable

// 拖拽排序相关导入
/**
 * 下载列表适配器
 */
class DownloadAdapter(scene: DownloadsScene, callback: DownloadAdapterCallback) :
    RecyclerView.Adapter<DownloadHolder?>(), DraggableItemAdapter<DownloadHolder?> {
    private val mInflater: LayoutInflater
    private val mListThumbWidth: Int
    private val mListThumbHeight: Int
    private val mScene: DownloadsScene
    private val mCallback: DownloadAdapterCallback

    private var movedItem: View? = null

    private val thumbnailCache: MutableMap<String?, Bitmap?> = HashMap<String?, Bitmap?>()

    interface DownloadAdapterCallback {
        val indexPage: Int
        val pageSize: Int
        val paginationSize: Int
        val isCanPagination: Boolean
        fun positionInList(position: Int): Int
        fun listIndexInPage(position: Int): Int
        val list: MutableList<DownloadInfo>?
        val spiderInfoMap: MutableMap<Long?, SpiderInfo?>?
        val downloadManager: DownloadManager?
        val recyclerView: EasyRecyclerView?
    }

    init {
        DRAG_ENABLE = Settings.getDragDownloadGallery()
        this.mScene = scene
        this.mCallback = callback

        var mInflater1: LayoutInflater
        try {
            mInflater1 = scene.getLayoutInflater2()
        } catch (e: NullPointerException) {
            // Fragment 可能还未附加到 FragmentManager，使用 Context 获取 LayoutInflater
            val context = scene.context
            if (context != null) {
                mInflater1 = LayoutInflater.from(context)
            } else {
                // 如果 Context 也为 null，尝试使用 Activity
                val activity: Activity? = scene.activity
                if (activity != null) {
                    mInflater1 = LayoutInflater.from(activity)
                } else {
                    throw IllegalStateException("Cannot get LayoutInflater: Fragment is not attached and Context/Activity is null")
                }
            }
        } catch (e: IllegalStateException) {
            val context = scene.context
            if (context != null) {
                mInflater1 = LayoutInflater.from(context)
            } else {
                val activity: Activity? = scene.activity
                if (activity != null) {
                    mInflater1 = LayoutInflater.from(activity)
                } else {
                    throw IllegalStateException("Cannot get LayoutInflater: Fragment is not attached and Context/Activity is null")
                }
            }
        }
        mInflater = mInflater1
        AssertUtils.assertNotNull(mInflater)

        val calculator = mInflater.inflate(R.layout.item_gallery_list_thumb_height, null)
        ViewUtils.measureView(calculator, 1024, ViewGroup.LayoutParams.WRAP_CONTENT)
        mListThumbHeight = calculator.measuredHeight
        mListThumbWidth = mListThumbHeight * 2 / 3
    }

    override fun getItemId(position: Int): Long {
        val posInList = mCallback.positionInList(position)
        val list = mCallback.list
        if (list == null || posInList < 0 || posInList >= list.size) {
            return 0
        }
        return list.get(posInList).gid
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadHolder {
        val holder = DownloadHolder(mInflater.inflate(R.layout.item_download, parent, false))

        val lp = holder.thumb.layoutParams
        lp.width = mListThumbWidth
        lp.height = mListThumbHeight
        holder.thumb.setLayoutParams(lp)

        return holder
    }

    override fun onBindViewHolder(holder: DownloadHolder, position: Int) {
        val list = mCallback.list ?: return

        try {
            val pos = mCallback.positionInList(position)
            val info = list.get(pos)

            var title = getSuitableTitle(info)
            val importedArchive = isImportedArchive(info)
            val localAlbum = DownloadAlbumImporter.isLocalAlbum(info)
            val localImport = importedArchive || localAlbum
            // Add special prefix for imported archives
            if (importedArchive) {
                title = "📦 $title"
            } else if (localAlbum) {
                title = "📁 $title"
            }
            // Handle thumbnail loading for imported archives
            if (importedArchive) {
                holder.thumb.setTag(R.id.thumb, null)
                // For imported archives, extract first image as thumbnail
                loadArchiveThumbnail(holder.thumb, Uri.parse(info.archiveUri))
            } else if (localAlbum) {
                loadLocalAlbumThumbnail(holder.thumb, info)
            } else {
                holder.thumb.setTag(R.id.thumb, null)
                // Normal thumbnail loading for regular downloads
                holder.thumb.load(
                    EhCacheKeyFactory.getThumbKey(info.gid), info.thumb,
                    ThumbDataContainer(info), true, false
                )
            }



            holder.title.text = title
            holder.uploader.text = info.uploader

            // Handle rating display for imported archives
            if (localImport) {
                // For imported archives, show 5 stars or hide rating
                holder.rating.rating = 5.0f
            } else {
                // For normal downloads, show actual rating
                holder.rating.rating = info.rating
            }

            val spiderInfo = mCallback.spiderInfoMap!![info.gid]

            // 归档条目每次绑定都重设文本，避免复用其他条目的进度。
            if (importedArchive) {
                holder.readProgress.text = spiderInfo?.let { formatArchiveReadingProgress(it.startPage, it.pages) }
            } else if (spiderInfo != null) {
                val startPage = spiderInfo.startPage + 1
                val readText = startPage.toString() + "/" + spiderInfo.pages
                holder.readProgress.text = readText
            }

            val category = holder.category
            val newCategoryText: String?
            val categoryColor: Int
            // Special handling for imported archives - prioritize archiveUri over category field
            if (localImport) {
                newCategoryText = mScene.getString(R.string.imported_archive_category)
                categoryColor = -0xb350b0 // Green color for imported archives
            } else {
                newCategoryText = getCategory(info.category)
                categoryColor = getCategoryColor(info.category)
            }

            if (newCategoryText != category.getText()) {
                category.text = newCategoryText
                category.setBackgroundColor(categoryColor)
            }
            bindForState(holder, info)

            // Update transition name
            ViewCompat.setTransitionName(
                holder.thumb,
                TransitionNameFactory.getThumbTransitionName(info.gid)
            )
        } catch (e: Exception) {
            recordException(e)
        }
    }

    override fun getItemCount(): Int {
        val list = mCallback.list ?: return 0
        val listSize = list.size
        if (listSize < mCallback.paginationSize || !mCallback.isCanPagination) {
            return listSize
        }
        val count = listSize - mCallback.pageSize * (mCallback.indexPage - 1)
        return min(count, mCallback.pageSize)
    }

    private fun isImportedArchive(info: DownloadInfo?): Boolean {
        return info != null && info.archiveUri != null && info.archiveUri.startsWith("content://")
    }

    private fun bindForState(holder: DownloadHolder, info: DownloadInfo) {
        val resources = mScene.getResources2()
        if (null == resources) {
            return
        }

        // Check if this is an imported archive - skip state judging
        val isImportedArchive = isImportedArchive(info)
        if (isImportedArchive) {
            bindState(holder, info, resources.getString(R.string.download_state_finish))
            return
        }

        when (info.state) {
            DownloadInfo.STATE_NONE -> bindState(
                holder,
                info,
                resources.getString(R.string.download_state_none)
            )

            DownloadInfo.STATE_WAIT -> bindState(
                holder,
                info,
                resources.getString(R.string.download_state_wait)
            )

            DownloadInfo.STATE_DOWNLOAD -> bindProgress(holder, info)
            DownloadInfo.STATE_FAILED -> {
                val text: String?
                if (info.legacy <= 0) {
                    text = resources.getString(R.string.download_state_failed)
                } else {
                    text = resources.getString(R.string.download_state_failed_2, info.legacy)
                }
                bindState(holder, info, text)
            }

            DownloadInfo.STATE_FINISH -> bindState(
                holder,
                info,
                resources.getString(R.string.download_state_finish)
            )
        }
    }

    private fun bindState(holder: DownloadHolder, info: DownloadInfo, state: String?) {
        holder.uploader.setVisibility(View.VISIBLE)
        holder.rating.setVisibility(View.VISIBLE)
        holder.category.setVisibility(View.VISIBLE)
        holder.readProgress.setVisibility(View.VISIBLE)
        holder.state.setVisibility(View.VISIBLE)
        holder.progressBar.setVisibility(View.GONE)
        holder.percent.setVisibility(View.GONE)
        holder.speed.setVisibility(View.GONE)
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE)
            holder.stop.setVisibility(View.VISIBLE)
        } else {
            holder.start.setVisibility(View.VISIBLE)
            holder.stop.setVisibility(View.GONE)
        }

        holder.state.setText(state)
    }

    @SuppressLint("SetTextI18n")
    private fun bindProgress(holder: DownloadHolder, info: DownloadInfo) {
        holder.uploader.setVisibility(View.GONE)
        holder.rating.setVisibility(View.GONE)
        holder.category.setVisibility(View.GONE)
        holder.readProgress.setVisibility(View.GONE)
        holder.state.setVisibility(View.GONE)
        holder.progressBar.setVisibility(View.VISIBLE)
        holder.percent.setVisibility(View.VISIBLE)
        holder.speed.setVisibility(View.VISIBLE)
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE)
            holder.stop.setVisibility(View.VISIBLE)
        } else {
            holder.start.setVisibility(View.VISIBLE)
            holder.stop.setVisibility(View.GONE)
        }

        if (info.total <= 0 || info.finished < 0) {
            holder.percent.setText(null)
            holder.progressBar.setIndeterminate(true)
        } else {
            holder.percent.setText(info.finished.toString() + "/" + info.total)
            holder.progressBar.setIndeterminate(false)
            holder.progressBar.setMax(info.total)
            holder.progressBar.setProgress(info.finished)
        }
        var speed = info.speed
        if (speed < 0) {
            speed = 0
        }
        holder.speed.setText(FileUtils.humanReadableByteCount(speed, false) + "/S")
    }


    // 拖拽排序相关方法实现
    override fun onCheckCanStartDrag(
        holder: DownloadHolder,
        position: Int,
        x: Int,
        y: Int
    ): Boolean {
        if (!DRAG_ENABLE) {
            return false
        }
        // 检查是否点击在thumb上
        return ViewUtils.isViewUnder(holder.thumb, x, y, 0)
    }

    override fun onGetItemDraggableRange(
        holder: DownloadHolder,
        position: Int
    ): ItemDraggableRange? {
        return null
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }
        val list = mCallback.list ?: return

        // 计算在完整列表中的位置
        val fromPosInList = mCallback.positionInList(fromPosition)
        val toPosInList = mCallback.positionInList(toPosition)

        if (fromPosInList >= 0 && fromPosInList < list.size && toPosInList >= 0 && toPosInList < list.size) {
            // 先更新数据库中的顺序（通过 time 字段）
            EhDB.moveDownloadInfo(list, fromPosInList, toPosInList)

            // 再尝试更新当前列表的内存顺序
            // 某些场景下（如搜索结果列表）mList 可能是 Arrays.asList(...)
            // 这类列表不支持结构修改，直接 remove/add 会抛出 UnsupportedOperationException
            try {
                val item: DownloadInfo = list.removeAt(fromPosInList)
                list.add(toPosInList, item)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "onMoveItem: list is unmodifiable, only DB order updated", e)
            }

            // 通知适配器刷新界面
            notifyDataSetChanged()
        }
    }

    override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean {
        return DRAG_ENABLE
    }

    override fun onItemDragStarted(position: Int) {
        // 拖拽开始时的处理
        try {
            // 设置RecyclerView为软件渲染模式以避免硬件位图问题
            if (mCallback.recyclerView != null) {
                movedItem = mCallback.recyclerView!!.getChildAt(position)
                movedItem!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.d("DownloadAdapter", "onItemDragStarted: $position")
            }
        } catch (e: Exception) {
            // 忽略硬件位图相关错误
            Log.e("DownloadAdapter", "Error in onItemDragStarted: " + e.message)
        }
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        // 拖拽结束时的处理
        try {
            // 恢复RecyclerView为硬件加速模式
            val recyclerView: RecyclerView? = mCallback.recyclerView
            if (recyclerView != null) {
//                if (recyclerView.getChildCount() >= toPosition + 1) {
//                    recyclerView.getChildAt(toPosition + 1).setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                    Log.d("DownloadAdapter", "toPosition+1: " + (toPosition + 1));
//                }

//                if (toPosition >= 1) {
//                    recyclerView.getChildAt(toPosition - 1).setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                    Log.d("DownloadAdapter", "toPosition-1: " + (toPosition - 1));
//                }
//                recyclerView.getChildAt(fromPosition).setLayerType(View.LAYER_TYPE_HARDWARE, null);

                if (movedItem != null) {
                    movedItem!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    Log.d("DownloadAdapter", "movedItem: $movedItem")
                } else {
                    recyclerView.getChildAt(toPosition).setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    Log.d("DownloadAdapter", "onItemDragFinished: $toPosition")
                }
            }
        } catch (e: Exception) {
            // 忽略硬件位图相关错误
            Log.e("DownloadAdapter", "Error in onItemDragFinished: " + e.message)
        }
    }

    private fun loadLocalAlbumThumbnail(thumb: LoadImageView, info: DownloadInfo) {
        val cacheKey = DownloadAlbumImporter.URI_PREFIX + info.gid
        thumb.setTag(R.id.thumb, cacheKey)
        val cachedThumbnail = thumbnailCache[cacheKey]
        if (cachedThumbnail != null && !cachedThumbnail.isRecycled) {
            val resources = mScene.getResources2()
            if (resources != null) {
                thumb.load(BitmapDrawable(resources, cachedThumbnail))
            } else {
                thumb.setImageBitmap(cachedThumbnail)
            }
            return
        }

        thumb.load(Color.TRANSPARENT.toDrawable())
        Thread(Runnable {
            val thumbnail = decodeLocalAlbumThumb(info)
            mScene.runOnUiThread(Runnable {
                if (cacheKey != thumb.getTag(R.id.thumb)) {
                    return@Runnable
                }
                if (thumbnail != null && !thumbnail.isRecycled) {
                    thumbnailCache[cacheKey] = thumbnail
                    val resources = mScene.getResources2()
                    if (resources != null) {
                        thumb.load(BitmapDrawable(resources, thumbnail))
                    } else {
                        thumb.setImageBitmap(thumbnail)
                    }
                }
            })
        }).start()
    }

    private fun decodeLocalAlbumThumb(info: DownloadInfo): Bitmap? {
        val dir = SpiderDen.getGalleryDownloadDir(info)
        if (dir == null || !dir.isDirectory()) {
            return null
        }
        var thumbFile = dir.findFile(".thumb")
        if (thumbFile == null) {
            thumbFile = SpiderDen.findImageFile(dir, 0)
        }
        if (thumbFile == null) {
            return null
        }
        try {
            thumbFile.openInputStream().use { boundsStream ->
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(boundsStream, null, options)
                val thumbnailSize = 150
                var sampleSize = 1
                if (options.outHeight > thumbnailSize || options.outWidth > thumbnailSize) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / sampleSize) >= thumbnailSize && (halfWidth / sampleSize) >= thumbnailSize) {
                        sampleSize *= 2
                    }
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                options.inPreferredConfig = Bitmap.Config.RGB_565
                thumbFile.openInputStream().use { decodeStream ->
                    return BitmapFactory.decodeStream(decodeStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode local album thumbnail for gid=" + info.gid, e)
            return null
        }
    }

    private fun loadArchiveThumbnail(thumb: LoadImageView, archiveUri: Uri) {
        val uriString = archiveUri.toString()

        // Check cache first
        if (thumbnailCache.containsKey(uriString)) {
            val cachedThumbnail = thumbnailCache.get(uriString)
            if (cachedThumbnail != null && !cachedThumbnail.isRecycled()) {
                thumb.setImageBitmap(cachedThumbnail)
                return
            } else {
                // Remove invalid cached entry
                thumbnailCache.remove(uriString)
            }
        }

        // Set default icon immediately as fallback
        thumb.setImageResource(R.drawable.v_archive_hh_primary_x48)

        // Load thumbnail in background thread
        Thread(Runnable {
            try {
                val thumbnail = extractFirstImageFromArchive(archiveUri)
                mScene.runOnUiThread {
                    if (thumbnail != null && !thumbnail.isRecycled) {
                        // Cache the thumbnail
                        thumbnailCache[uriString] = thumbnail
                        thumb.setImageBitmap(thumbnail)
                    } else {
                        // If extraction fails, check if we have a previous cached thumbnail
                        val fallbackThumbnail = thumbnailCache[uriString]
                        if (fallbackThumbnail != null && !fallbackThumbnail.isRecycled) {
                            thumb.setImageBitmap(fallbackThumbnail)
                        }
                        // Otherwise keep the default archive icon that was already set
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load archive thumbnail for " + uriString, e)
                // Keep the default icon that was already set - no need to change anything
            }
        }).start()
    }

    private fun extractFirstImageFromArchive(archiveUri: Uri): Bitmap? {
        val context = mScene.ehContext ?: return null

        var uraf: UniRandomAccessFile? = null
        var archive: A7ZipArchive? = null

        try {
            // Verify URI accessibility first and try to restore permission if needed
            try {
                context.contentResolver.openInputStream(archiveUri).use { testStream ->
                    if (testStream == null) {
                        Log.w(TAG, "Cannot access archive URI: $archiveUri")
                        return null
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "URI permission lost, attempting to restore: $archiveUri", e)
                // Try to restore the permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        archiveUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Log.d(TAG, "Successfully restored URI permission for: $archiveUri")
                    context.contentResolver.openInputStream(archiveUri).use { retryStream ->
                        if (retryStream == null) {
                            Log.w(
                                TAG,
                                "Still cannot access URI after permission restore: $archiveUri"
                            )
                            return null
                        }
                    }
                } catch (restoreEx: Exception) {
                    Log.e(TAG, "Failed to restore URI permission for: $archiveUri", restoreEx)
                    return null
                }
            } catch (e: Exception) {
                Log.w(TAG, "URI not accessible: $archiveUri", e)
                return null
            }

            // Open the archive file
            val file = UniFile.fromUri(context, archiveUri)
            if (file == null || !file.exists()) {
                Log.w(TAG, "Archive file not found: $archiveUri")
                return null
            }

            uraf = file.createRandomAccessFile("r")
            if (uraf == null) {
                Log.w(TAG, "Cannot create random access file for: $archiveUri")
                return null
            }

            archive = A7ZipArchive.create(uraf)
            if (archive == null) {
                Log.w(TAG, "Cannot create archive reader for: $archiveUri")
                return null
            }

            val entries = archive.getArchiveEntries()
            if (entries.isEmpty()) {
                Log.w(TAG, "Archive is empty: $archiveUri")
                return null
            }

            // Sort entries by name (natural order)
            Collections.sort<A7ZipArchiveEntry?>(
                entries,
                Comparator { o1: A7ZipArchiveEntry?, o2: A7ZipArchiveEntry? ->
                    val comparator = NaturalComparator()
                    comparator.compare(o1!!.path, o2!!.path)
                })

            // Find the first image file
            for (entry in entries) {
                val fileName = entry.path.lowercase(Locale.getDefault())
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") || fileName.endsWith(".bmp") ||
                    fileName.endsWith(".gif") || fileName.endsWith(".webp")
                ) {
                    try {
                        // Create a pipe to extract the image
                        var pipe = Pipe(8 * 1024) // Increased buffer size

                        // Extract in another thread with timeout
                        val finalPipe = pipe
                        var extractThread = Thread(Runnable {
                            try {
                                entry.extract(finalPipe.outputStream)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to extract image: $fileName", e)
                            }
                        })
                        extractThread.start()

                        // Decode the image with size limits
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeStream(pipe.inputStream, null, options)

                        // Calculate sample size for thumbnail (smaller target size for better performance)
                        val thumbnailSize = 150
                        var sampleSize = 1
                        if (options.outHeight > thumbnailSize || options.outWidth > thumbnailSize) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while ((halfHeight / sampleSize) >= thumbnailSize && (halfWidth / sampleSize) >= thumbnailSize) {
                                sampleSize *= 2
                            }
                        }

                        // Recreate pipe for actual decoding
                        pipe = Pipe(8 * 1024)
                        val finalPipe1 = pipe
                        extractThread = Thread(Runnable {
                            try {
                                entry.extract(finalPipe1.outputStream)
                            } catch (e: Exception) {
                                Log.w(
                                    TAG,
                                    "Failed to extract image on second attempt: $fileName",
                                    e
                                )
                            }
                        })
                        extractThread.start()

                        // Decode with sample size
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                        val bitmap = BitmapFactory.decodeStream(pipe.inputStream, null, options)

                        extractThread.join(3000) // Wait max 3 seconds (reduced from 5)

                        if (bitmap != null && !bitmap.isRecycled) {
                            Log.d(TAG, "Successfully extracted thumbnail from $fileName")
                            return bitmap
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract thumbnail from $fileName", e)
                        // Continue to next image file
                    }
                }
            }

            Log.w(TAG, "No extractable images found in archive: $archiveUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process archive for thumbnail: $archiveUri", e)
        } finally {
            // Ensure resources are properly closed
            if (archive != null) {
                try {
                    archive.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close archive", e)
                }
            }
            if (uraf != null) {
                try {
                    uraf.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close file", e)
                }
            }
        }

        return null
    }

    inner class DownloadHolder(itemView: View) : AbstractDraggableItemViewHolder(itemView),
        View.OnClickListener {
        @JvmField
        val thumb: LoadImageView
        val title: TextView
        val uploader: TextView
        val rating: SimpleRatingView
        val category: TextView
        val readProgress: TextView
        val start: View
        val stop: View
        val state: TextView
        val progressBar: ProgressBar
        val percent: TextView
        val speed: TextView

        init {
            thumb = itemView.findViewById<LoadImageView>(R.id.thumb)
            title = itemView.findViewById<TextView>(R.id.title)
            uploader = itemView.findViewById<TextView>(R.id.uploader)
            rating = itemView.findViewById<SimpleRatingView>(R.id.rating)
            category = itemView.findViewById<TextView>(R.id.category)
            readProgress = itemView.findViewById<TextView>(R.id.read_progress)
            start = itemView.findViewById<View>(R.id.start)
            stop = itemView.findViewById<View>(R.id.stop)
            state = itemView.findViewById<TextView>(R.id.state)
            progressBar = itemView.findViewById<ProgressBar>(R.id.progress_bar)
            percent = itemView.findViewById<TextView>(R.id.percent)
            speed = itemView.findViewById<TextView>(R.id.speed)

            // TODO cancel on click listener when select items
            thumb.setOnClickListener(this)
            start.setOnClickListener(this)
            stop.setOnClickListener(this)

            val isDarkTheme = !AttrResources.getAttrBoolean(
                mScene.ehContext!!,
                androidx.appcompat.R.attr.isLightTheme
            )
            Ripple.addRipple(start, isDarkTheme)
            Ripple.addRipple(stop, isDarkTheme)
        }

        override fun onClick(v: View?) {
            val context = mScene.ehContext
            val recyclerView = mCallback.recyclerView
            if (null == context || null == recyclerView || recyclerView.isInCustomChoice) {
                return
            }
            val list = mCallback.list
            if (list == null) {
                return
            }
            val size = list.size
            val index = recyclerView.getChildAdapterPosition(itemView)
            if (index !in 0..<size) {
                return
            }

            if (thumb === v) {
                val currentInfo: DownloadInfo = list[mScene.positionInList(index)]
                if (isImportedArchive(currentInfo) || DownloadAlbumImporter.isLocalAlbum(currentInfo)) {
                    // Show info dialog for imported archive
                    val message =
                        mScene.getString(R.string.imported_archive_info_message) + "\n\n" + currentInfo.archiveUri
                    AlertDialog.Builder(context)
                        .setTitle(R.string.imported_archive_info_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    // Normal behavior for regular downloads
                    val args = Bundle()
                    args.putString(
                        GalleryDetailScene.KEY_ACTION,
                        GalleryDetailScene.ACTION_DOWNLOAD_GALLERY_INFO
                    )
                    args.putParcelable(
                        GalleryDetailScene.KEY_GALLERY_INFO,
                        list[mCallback.positionInList(index)]
                    )
                    val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
                    announcer.setTranHelper(EnterGalleryDetailTransaction(thumb))
                    mScene.startScene(announcer)
                }
            } else if (start === v) {
                val info: DownloadInfo = list[mCallback.positionInList(index)]
                val intent = Intent(context, DownloadService::class.java)
                intent.setAction(DownloadService.ACTION_START)
                intent.putExtra(DownloadService.KEY_GALLERY_INFO, info)
                context.startService(intent)
            } else if (stop === v) {
                val downloadManager =
                    mCallback.downloadManager
                downloadManager?.stopDownload(list[mCallback.positionInList(index)].gid)
            }
        }
    }

    companion object {
        /** 处理未知总页数、负页码和超界页码，并避免整数加一溢出。 */
        @JvmStatic
        private fun formatArchiveReadingProgress(startPage: Int, pageCount: Int): String? {
            val start = maxOf(startPage, 0)
            if (pageCount <= 0) return if (start > 0) "${start.toLong() + 1}/?" else null
            return "${minOf(start, pageCount - 1) + 1}/$pageCount"
        }

        private val TAG: String = DownloadAdapter::class.java.getSimpleName()
        @JvmField
        var DRAG_ENABLE: Boolean = false
    }
}

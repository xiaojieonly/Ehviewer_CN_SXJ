package com.hippo.ehviewer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.hippo.ehviewer.BackgroundTaskManager
import com.hippo.ehviewer.task.ScanLocalGalleryTask
import java.util.concurrent.Executors

class LocalGalleryScanService : Service() {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startService(Intent(context, LocalGalleryScanService::class.java))
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            return START_NOT_STICKY
        }
        running = true

        val handle = BackgroundTaskManager.getInstance().submitBackgroundTask(ScanLocalGalleryTask(this))
        executor.execute {
            try {
                handle.future.get()
            } catch (_: Exception) {
                // Ignored.
            } finally {
                running = false
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

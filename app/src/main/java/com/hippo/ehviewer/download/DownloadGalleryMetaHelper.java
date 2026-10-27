package com.hippo.ehviewer.download;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.unifile.UniFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

public final class DownloadGalleryMetaHelper {

    private DownloadGalleryMetaHelper() {
    }

    /**
     * 获取下载目录时间戳，优先尝试目录创建时间，不可用时回退最后修改时间，再回退任务时间。
     */
    public static long getGalleryDirectoryTimestamp(@NonNull DownloadInfo info) {
        UniFile downloadDir = SpiderDen.getGalleryDownloadDir(info);
        long time = getDirectoryCreationTime(downloadDir);
        if (time > 0) {
            return time;
        }
        time = getDirectoryLastModifiedTime(downloadDir);
        if (time > 0) {
            return time;
        }
        return Math.max(info.time, 0L);
    }

    private static long getDirectoryCreationTime(UniFile downloadDir) {
        if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory()) {
            return -1L;
        }

        try {
            Uri uri = downloadDir.getUri();
            if (uri != null && "file".equalsIgnoreCase(uri.getScheme()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String path = uri.getPath();
                if (path != null) {
                    BasicFileAttributes attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
                    if (attrs.creationTime() != null) {
                        long creation = attrs.creationTime().toMillis();
                        if (creation > 0) {
                            return creation;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return -1L;
    }

    private static long getDirectoryLastModifiedTime(UniFile downloadDir) {
        if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory()) {
            return -1L;
        }
        try {
            return downloadDir.lastModified();
        } catch (Exception ignored) {
            return -1L;
        }
    }
}

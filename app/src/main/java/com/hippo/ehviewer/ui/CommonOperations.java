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

package com.hippo.ehviewer.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.hippo.app.ListCheckBoxDialogBuilder;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.GalleryVersionMap;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.dialog.DownloadProgressDialog;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.ui.scene.gallery.list.EnterGalleryDetailTransaction;
import com.hippo.lib.yorozuya.SimpleHandler;
import com.hippo.util.ExecutorManager;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.collect.LongList;
import com.hippo.scene.Announcer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CommonOperations {

    private static void doAddToFavorites(Activity activity, GalleryInfo galleryInfo,
                                         int slot, EhClient.Callback<Void> listener) {
        if (slot == -1) {
            EhDB.putLocalFavorite(galleryInfo);
            listener.onSuccess(null);
        } else if (slot >= 0 && slot <= 9) {
            EhClient client = EhApplication.getEhClient(activity);
            EhRequest request = new EhRequest();
            request.setMethod(EhClient.METHOD_ADD_FAVORITES);
            request.setArgs(galleryInfo.gid, galleryInfo.token, slot, "");
            request.setCallback(listener);
            client.execute(request);
        } else {
            listener.onFailure(new Exception()); // TODO Add text
        }
    }

    public static void addToFavorites(final Activity activity, final GalleryInfo galleryInfo,
                                      final EhClient.Callback<Void> listener, boolean isDefaultFavSolt) {
        int slot = Settings.getDefaultFavSlot();
        String[] items = new String[11];
        items[0] = activity.getString(R.string.local_favorites);
        String[] favCat = Settings.getFavCat();
        System.arraycopy(favCat, 0, items, 1, 10);
        if ((slot >= -1 && slot <= 9)&&!isDefaultFavSolt) {
            String newFavoriteName = slot >= 0 ? items[slot + 1] : null;
            doAddToFavorites(activity, galleryInfo, slot, new DelegateFavoriteCallback(listener, galleryInfo, newFavoriteName, slot));
        } else {
            new ListCheckBoxDialogBuilder(activity, items,
                    (builder, dialog, position) -> {
                        int slot1 = position - 1;
                        String newFavoriteName = (slot1 >= 0 && slot1 <= 9) ? items[slot1 + 1] : null;
                        doAddToFavorites(activity, galleryInfo, slot1, new DelegateFavoriteCallback(listener, galleryInfo, newFavoriteName, slot1));
                        if (builder.isChecked()) {
                            Settings.putDefaultFavSlot(slot1);
                        } else {
                            Settings.putDefaultFavSlot(Settings.INVALID_DEFAULT_FAV_SLOT);
                        }
                    }, activity.getString(R.string.remember_favorite_collection), false)
                    .setTitle(R.string.add_favorites_dialog_title)
                    .setOnCancelListener(dialog -> listener.onCancel())
                    .show();
        }
    }

    public static void removeFromFavorites(Activity activity, GalleryInfo galleryInfo,
                                           final EhClient.Callback<Void> listener) {
        EhDB.removeLocalFavorites(galleryInfo.gid);
        EhClient client = EhApplication.getEhClient(activity);
        EhRequest request = new EhRequest();
        request.setMethod(EhClient.METHOD_ADD_FAVORITES);
        request.setArgs(galleryInfo.gid, galleryInfo.token, -1, "");
        request.setCallback(new DelegateFavoriteCallback(listener, galleryInfo, null, -2));
        client.execute(request);
    }

    private static class DelegateFavoriteCallback implements EhClient.Callback<Void> {

        private final EhClient.Callback<Void> delegate;
        private final GalleryInfo info;
        private final String newFavoriteName;
        private final int slot;

        DelegateFavoriteCallback(EhClient.Callback<Void> delegate, GalleryInfo info,
                                 String newFavoriteName, int slot) {
            this.delegate = delegate;
            this.info = info;
            this.newFavoriteName = newFavoriteName;
            this.slot = slot;
        }

        @Override
        public void onSuccess(Void result) {
            info.favoriteName = newFavoriteName;
            info.favoriteSlot = slot;
            delegate.onSuccess(result);
            EhApplication.getFavouriteStatusRouter().modifyFavourites(info.gid, slot);
        }

        @Override
        public void onFailure(Exception e) {
            delegate.onFailure(e);
        }

        @Override
        public void onCancel() {
            delegate.onCancel();
        }
    }

    public static void startDownload(final MainActivity activity, final GalleryInfo galleryInfo, boolean forceDefault) {
        startDownload(activity, Collections.singletonList(galleryInfo), forceDefault);
    }

    // TODO Add context if activity and context are different style
    public static void startDownload(final MainActivity activity, final List<GalleryInfo> galleryInfos, boolean forceDefault) {
        // 如果是多个画廊，显示进度对话框
        final boolean isMultiDownload = galleryInfos.size() > 1;
        final DownloadProgressDialog progressDialog;
        
        if (isMultiDownload) {
            progressDialog = DownloadProgressDialog.show(activity, 
                activity.getString(R.string.download_multi_select_processing));
        } else {
            progressDialog = null;
            // 显示准备下载的Toast提示，避免用户感觉界面卡死
            SimpleHandler.getInstance().post(() -> {
                if (isActivityAlive(activity)) {
                    Toast.makeText(activity, activity.getString(R.string.preparing_download), Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // 在后台线程执行耗时操作，避免阻塞主线程
        ExecutorManager.getBackgroundExecutor().execute(() -> {
            AtomicBoolean deferDismiss = new AtomicBoolean(false);
            try {
                final DownloadManager dm = EhApplication.getDownloadManager(activity);

                LongList toStart = new LongList();
                List<GalleryInfo> toAdd = new ArrayList<>();
                for (int i = 0; i < galleryInfos.size(); i++) {
                    GalleryInfo gi = galleryInfos.get(i);
                    if (dm.containDownloadInfo(gi.gid)) {
                        toStart.add(gi.gid);
                    } else {
                        toAdd.add(gi);
                    }

                    final int progressCurrent = i + 1;
                    if (isMultiDownload && progressDialog != null) {
                        SimpleHandler.getInstance().post(() -> {
                            if (isActivityAlive(activity) && progressDialog.isShowing()) {
                                progressDialog.updateProgress(progressCurrent, galleryInfos.size());
                            }
                        });
                    }
                }

                if (!toStart.isEmpty()) {
                    DownloadService.startRangeDownload(activity, toStart);
                }

                if (toAdd.isEmpty()) {
                    SimpleHandler.getInstance().post(() -> {
                        if (isActivityAlive(activity)) {
                            activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
                        }
                    });
                    return;
                }

                boolean justStart = forceDefault;
                String label = null;
                // Get default download label
                if (!justStart && Settings.getHasDefaultDownloadLabel()) {
                    label = Settings.getDefaultDownloadLabel();
                    justStart = label == null || dm.containLabel(label);
                }
                // If there is no other label, just use null label
                if (!justStart && 0 == dm.getLabelList().size()) {
                    justStart = true;
                    label = null;
                }

                if (justStart) {
                    addDownloadsToWait(activity, dm, toAdd, label, progressDialog, isMultiDownload);
                } else {
                    // Let use chose label - 需要在主线程显示对话框
                    deferDismiss.set(true);
                    SimpleHandler.getInstance().post(() -> {
                        if (!isActivityAlive(activity)) {
                            deferDismiss.set(false);
                            return;
                        }
                        List<DownloadLabel> list = dm.getLabelList();
                        final String[] items = new String[list.size() + 1];
                        items[0] = activity.getString(R.string.default_download_label_name);
                        for (int i = 0, n = list.size(); i < n; i++) {
                            items[i + 1] = list.get(i).getLabel();
                        }

                        new ListCheckBoxDialogBuilder(activity, items,
                                (builder, dialog, position) -> {
                                    String label1;
                                    if (position == 0) {
                                        label1 = null;
                                    } else {
                                        label1 = items[position];
                                        if (!dm.containLabel(label1)) {
                                            label1 = null;
                                        }
                                    }
                                    String finalLabel = label1;
                                    ExecutorManager.getBackgroundExecutor().execute(() -> {
                                        addDownloadsToWait(activity, dm, toAdd, finalLabel, progressDialog, isMultiDownload);
                                    });

                                    // Save settings
                                    if (builder.isChecked()) {
                                        Settings.putHasDefaultDownloadLabel(true);
                                        Settings.putDefaultDownloadLabel(finalLabel);
                                    } else {
                                        Settings.putHasDefaultDownloadLabel(false);
                                    }
                                    // Notify
                                    if (isActivityAlive(activity)) {
                                        activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
                                    }
                                }, activity.getString(R.string.remember_download_label), false)
                                .setTitle(R.string.download_dialog_title)
                                .show();
                    });
                }
            } catch (Exception e) {
                Log.e("CommonOperations", "Error starting download", e);
                SimpleHandler.getInstance().post(() -> {
                    if (isActivityAlive(activity)) {
                        Toast.makeText(activity, "启动下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                // 确保对话框在主线程中被关闭
                if (isMultiDownload && progressDialog != null && !deferDismiss.get()) {
                    SimpleHandler.getInstance().post(() -> {
                        if (isActivityAlive(activity) && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    });
                }
            }
        });
    }

    private static void addDownloadsToWait(MainActivity activity, DownloadManager dm,
                                           List<GalleryInfo> toAdd, @Nullable String label,
                                           @Nullable DownloadProgressDialog progressDialog,
                                           boolean isMultiDownload) {
        int total = toAdd.size();
        for (int i = 0; i < toAdd.size(); i++) {
            GalleryInfo gi = toAdd.get(i);
            dm.addDownload(gi, label, DownloadInfo.STATE_WAIT);

            if (isMultiDownload && progressDialog != null) {
                int current = i + 1;
                SimpleHandler.getInstance().post(() -> {
                    if (isActivityAlive(activity) && progressDialog.isShowing()) {
                        progressDialog.updateProgress(current, total);
                    }
                });
            }
        }

        if (isMultiDownload && progressDialog != null) {
            SimpleHandler.getInstance().post(() -> {
                if (isActivityAlive(activity) && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            });
        }

        SimpleHandler.getInstance().post(() -> {
            if (isActivityAlive(activity)) {
                activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
            }
        });

        DownloadService.ensureRunning(activity);
    }

    private static boolean isActivityAlive(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
    }

    /**
     * 查找同名但不同ID的画廊
     */
    private static Long findSameNameGallery(@NonNull GalleryInfo galleryInfo) {
        if (!Settings.getIncrementalDownloadUpdate()) {
            return null;
        }

        UniFile downloadLocation = Settings.getDownloadLocation();
        if (downloadLocation == null) {
            return null;
        }

        String galleryTitle = EhUtils.getSuitableTitle(galleryInfo);
        String sanitizedTitle = FileUtils.sanitizeFilename(galleryTitle);

        try {
            UniFile[] files = downloadLocation.listFiles();
            if (files == null) {
                return null;
            }

            for (UniFile file : files) {
                if (!file.isDirectory()) {
                    continue;
                }

                String dirName = file.getName();
                if (dirName == null) {
                    continue;
                }

                // 解析文件夹名称，格式为 {gid}-{title}
                int dashIndex = dirName.indexOf('-');
                if (dashIndex <= 0 || dashIndex >= dirName.length() - 1) {
                    continue;
                }

                String dirTitle = dirName.substring(dashIndex + 1);
                if (!dirTitle.equals(sanitizedTitle)) {
                    continue;
                }

                try {
                    long dirGid = Long.parseLong(dirName.substring(0, dashIndex));
                    if (dirGid != galleryInfo.gid) {
                        // 找到同名但不同ID的画廊
                        return dirGid;
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的文件夹名
                }
            }
        } catch (Exception e) {
            Log.w("CommonOperations", "Error finding same name gallery", e);
        }

        return null;
    }

    /**
     * 处理增量更新
     */
    private static void handleIncrementalUpdate(Activity activity, GalleryInfo newGalleryInfo, long oldGid) {
        String newTitle = EhUtils.getSuitableTitle(newGalleryInfo);
        Log.i("CommonOperations", "开始处理增量更新: " + newTitle + " (旧GID: " + oldGid + ", 新GID: " + newGalleryInfo.gid + ")");
        
        // 创建临时GalleryInfo对象来获取旧画廊的下载目录
        GalleryInfo oldGalleryInfo = new GalleryInfo();
        oldGalleryInfo.gid = oldGid;
        oldGalleryInfo.title = newGalleryInfo.title; // 使用相同的标题
        
        // 获取旧画廊的下载目录
        UniFile oldDownloadDir = SpiderDen.getGalleryDownloadDir(oldGalleryInfo);
        if (oldDownloadDir == null || !oldDownloadDir.exists()) {
            Log.e("CommonOperations", "旧画廊下载目录不存在: " + oldDownloadDir);
            return;
        }
        Log.d("CommonOperations", "旧画廊下载目录: " + oldDownloadDir.getUri());

        try {
            // 创建.updateGallery文件
            UniFile updateFile = oldDownloadDir.createFile(".updateGallery");
            if (updateFile != null) {
                String updateContent = "newGid=" + newGalleryInfo.gid + "\n" +
                        "oldGid=" + oldGid + "\n" +
                        "title=" + newGalleryInfo.title + "\n" +
                        "updateTime=" + System.currentTimeMillis() + "\n";
                OutputStream os = updateFile.openOutputStream();
                os.write(updateContent.getBytes("UTF-8"));
                os.close();
                Log.d("CommonOperations", ".updateGallery文件创建成功");
            } else {
                Log.e("CommonOperations", "无法创建.updateGallery文件");
            }

            // 复制旧版本的.ehviewer文件为.ehviewer.[原始ID]
            UniFile oldEhviewerFile = oldDownloadDir.findFile(".ehviewer");
            if (oldEhviewerFile != null) {
                InputStream is = oldEhviewerFile.openInputStream();
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String oldEhviewerContent = new String(buffer, StandardCharsets.UTF_8);
                String backupFileName = ".ehviewer." + oldGid;
                UniFile backupFile = oldDownloadDir.createFile(backupFileName);
                if (backupFile != null) {
                    OutputStream os = backupFile.openOutputStream();
                    os.write(oldEhviewerContent.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    Log.d("CommonOperations", ".ehviewer文件备份成功: " + backupFileName);
                } else {
                    Log.e("CommonOperations", "无法创建.ehviewer备份文件");
                }
            } else {
                Log.w("CommonOperations", "未找到.ehviewer文件");
            }

            // 在数据库中添加映射关系
            GalleryVersionMap existingMap = EhDB.getGalleryVersionMap(oldGid);
            long originalGid;
            if (existingMap != null) {
                originalGid = existingMap.getOriginalGid();
                Log.d("CommonOperations", "找到已存在的版本映射，原始GID: " + originalGid);
            } else {
                originalGid = oldGid;
                Log.d("CommonOperations", "未找到已存在的版本映射，使用当前GID作为原始GID: " + originalGid);
            }
            
            // 添加新的映射关系
            EhDB.addGalleryVersionMap(newGalleryInfo.gid, originalGid, newGalleryInfo.title);
            Log.i("CommonOperations", "版本映射关系添加成功: " + originalGid + " -> " + newGalleryInfo.gid);

            // 显示提示
            SimpleHandler.getInstance().post(() -> {
                Toast.makeText(activity, "检测到画廊更新，将保留已下载的进度", Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e("CommonOperations", "处理增量更新时发生异常", e);
        }
        
        Log.i("CommonOperations", "增量更新处理完成: " + newTitle);
    }

    public static void ensureNoMediaFile(UniFile file) {
        if (null == file) {
            return;
        }

        UniFile noMedia = file.createFile(".nomedia");
        if (null == noMedia) {
            return;
        }

        InputStream is = null;
        try {
            is = noMedia.openInputStream();
        } catch (IOException e) {
            // Ignore
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static void removeNoMediaFile(UniFile file) {
        if (null == file) {
            return;
        }

        UniFile noMedia = file.subFile(".nomedia");
        if (null != noMedia && noMedia.isFile()) {
            noMedia.delete();
        }
    }
}

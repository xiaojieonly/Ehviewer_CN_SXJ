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
import android.content.Intent;

import com.hippo.app.ListCheckBoxDialogBuilder;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.unifile.UniFile;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.collect.LongList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        final DownloadManager dm = EhApplication.getDownloadManager(activity);

        LongList toStart = new LongList();
        List<GalleryInfo> toAdd = new ArrayList<>();
        for (GalleryInfo gi : galleryInfos) {
            if (dm.containDownloadInfo(gi.gid)) {
                toStart.add(gi.gid);
            } else {
                toAdd.add(gi);
            }
        }

        if (!toStart.isEmpty()) {
            Intent intent = new Intent(activity, DownloadService.class);
            intent.setAction(DownloadService.ACTION_START_RANGE);
            intent.putExtra(DownloadService.KEY_GID_LIST, toStart);
            activity.startService(intent);
        }

        if (toAdd.isEmpty()) {
            activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
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
            // Got default label
            for (GalleryInfo gi : toAdd) {
                Intent intent = new Intent(activity, DownloadService.class);
                intent.setAction(DownloadService.ACTION_START);
                intent.putExtra(DownloadService.KEY_LABEL, label);
                intent.putExtra(DownloadService.KEY_GALLERY_INFO, gi);
                activity.startService(intent);
            }
            // Notify
            activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
        } else {
            // Let use chose label
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
                        // Start download
                        for (GalleryInfo gi : toAdd) {
                            Intent intent = new Intent(activity, DownloadService.class);
                            intent.setAction(DownloadService.ACTION_START);
                            intent.putExtra(DownloadService.KEY_LABEL, label1);
                            intent.putExtra(DownloadService.KEY_GALLERY_INFO, gi);
                            activity.startService(intent);
                        }
                        // Save settings
                        if (builder.isChecked()) {
                            Settings.putHasDefaultDownloadLabel(true);
                            Settings.putDefaultDownloadLabel(label1);
                        } else {
                            Settings.putHasDefaultDownloadLabel(false);
                        }
                        // Notify
                        activity.showTip(R.string.added_to_download_list, BaseScene.LENGTH_SHORT);
                    }, activity.getString(R.string.remember_download_label), false)
                    .setTitle(R.string.download)
                    .show();
        }
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

package com.hippo.ehviewer.sync.nas;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.unifile.UniFile;

public final class NasPageFetcher {
    private NasPageFetcher() {}

    public static boolean fetch(@NonNull Context context, @NonNull GalleryInfo galleryInfo,
                                int index) {
        if (!NasConfigStore.isEnabled(context)) return false;
        NasCatalogEntry entry = NasCatalogStore.find(context, galleryInfo.gid);
        if (entry == null) return false;
        UniFile localRoot = Settings.getDownloadLocation();
        if (localRoot == null) return false;
        NasSyncConfig config = NasConfigStore.load(context);
        try {
            boolean downloaded = new NasCatalogClient(config).downloadPage(localRoot, entry, index);
            if (downloaded) NasCatalogStore.markPartiallyCached(context, galleryInfo.gid);
            return downloaded;
        } catch (Exception ignored) {
            config.clearPassword();
            return false;
        }
    }
}

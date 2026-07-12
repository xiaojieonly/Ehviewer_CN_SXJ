package com.hippo.ehviewer.sync.nas;

import androidx.annotation.NonNull;

public final class NasCatalogEntry {
    public final long gid;
    @NonNull public final String title;
    @NonNull public final String directoryName;
    @NonNull public final String remoteDirectory;
    @NonNull public final String remoteThumbnail;
    @NonNull public final String token;
    @NonNull public final String titleJpn;
    @NonNull public final String thumb;
    @NonNull public final String uploader;
    @NonNull public final String posted;
    @NonNull public final String simpleLanguage;
    public final int category;
    public final float rating;
    @NonNull public final String label;
    public final long time;

    public NasCatalogEntry(long gid, @NonNull String title, @NonNull String directoryName,
                           @NonNull String remoteDirectory, @NonNull String remoteThumbnail,
                           @NonNull String token, @NonNull String titleJpn,
                           @NonNull String thumb, @NonNull String uploader,
                           @NonNull String posted, @NonNull String simpleLanguage,
                           int category, float rating, @NonNull String label) {
        this(gid, title, directoryName, remoteDirectory, remoteThumbnail, token, titleJpn,
                thumb, uploader, posted, simpleLanguage, category, rating, label, 0L);
    }

    public NasCatalogEntry(long gid, @NonNull String title, @NonNull String directoryName,
                           @NonNull String remoteDirectory, @NonNull String remoteThumbnail,
                           @NonNull String token, @NonNull String titleJpn,
                           @NonNull String thumb, @NonNull String uploader,
                           @NonNull String posted, @NonNull String simpleLanguage,
                           int category, float rating, @NonNull String label, long time) {
        this.gid = gid;
        this.title = title;
        this.directoryName = directoryName;
        this.remoteDirectory = remoteDirectory;
        this.remoteThumbnail = remoteThumbnail;
        this.token = token;
        this.titleJpn = titleJpn;
        this.thumb = thumb;
        this.uploader = uploader;
        this.posted = posted;
        this.simpleLanguage = simpleLanguage;
        this.category = category;
        this.rating = rating;
        this.label = label;
        this.time = time;
    }
}

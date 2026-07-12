package com.hippo.ehviewer.sync.nas;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class NasSyncResult {
    public int uploaded;
    public int downloaded;
    public int skipped;
    public int conflicts;
    public int catalogEntries;
    /** Updated full NAS manifest. Empty means no trusted full manifest was available. */
    @NonNull public final List<NasDatabaseStore.ManifestEntry> manifest = new ArrayList<>();
    @Nullable public Throwable error;

    public boolean isSuccess() {
        return error == null;
    }
}

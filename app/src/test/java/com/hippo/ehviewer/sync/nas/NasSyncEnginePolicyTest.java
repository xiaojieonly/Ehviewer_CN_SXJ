package com.hippo.ehviewer.sync.nas;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NasSyncEnginePolicyTest {
    @Test
    public void mutableMetadataIsContentChecked() {
        assertTrue(NasSyncEngine.isMutableMetadataPath("gallery/.ehviewer"));
        assertTrue(NasSyncEngine.isMutableMetadataPath("gallery/.thumb"));
        assertTrue(NasSyncEngine.isMutableMetadataPath(
                NasSyncEngine.DATABASE_BACKUP_FILENAME));
        assertTrue(NasSyncEngine.isMutableMetadataPath("GALLERY/.THUMB"));
    }

    @Test
    public void immutableGalleryContentUsesFastPath() {
        assertFalse(NasSyncEngine.isMutableMetadataPath("gallery/001.jpg"));
        assertFalse(NasSyncEngine.isMutableMetadataPath("gallery/archive.zip"));
        assertFalse(NasSyncEngine.isMutableMetadataPath(".ehviewer-notes"));
    }

    @Test
    public void completeManifestSkipsOnlySameSizeImmutableFiles() {
        assertTrue(NasSyncEngine.canSkipFromManifest("gallery/00000001.jpg", 4096, 4096));
        assertFalse(NasSyncEngine.canSkipFromManifest("gallery/00000001.jpg", 4096, 2048));
        assertFalse(NasSyncEngine.canSkipFromManifest("gallery/.ehviewer", 4096, 4096));
        assertFalse(NasSyncEngine.canSkipFromManifest("gallery/.thumb", 4096, 4096));
    }
}

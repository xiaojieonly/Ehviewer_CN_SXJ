package com.hippo.ehviewer.sync.nas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NasDatabaseStoreTest {
    @Test
    public void acceptsOnlySafeRelativeManifestPaths() {
        assertTrue(NasDatabaseStore.isSafeRelativePath("123-title/.ehviewer"));
        assertTrue(NasDatabaseStore.isSafeRelativePath("123-title/00000001.jpg"));
        assertFalse(NasDatabaseStore.isSafeRelativePath("../secret"));
        assertFalse(NasDatabaseStore.isSafeRelativePath("/absolute/path"));
        assertFalse(NasDatabaseStore.isSafeRelativePath("gallery//page.jpg"));
        assertFalse(NasDatabaseStore.isSafeRelativePath("gallery/./page.jpg"));
    }

    @Test
    public void staleDeviceCannotOverwriteNewerNasRevision() {
        assertFalse(NasDatabaseStore.isRemoteRevisionConflict("", "remote"));
        assertFalse(NasDatabaseStore.isRemoteRevisionConflict("same", "SAME"));
        assertTrue(NasDatabaseStore.isRemoteRevisionConflict("phone-base", "tablet-update"));
        assertTrue(NasDatabaseStore.isRemoteRevisionConflict("phone-base", ""));
    }

    @Test
    public void phoneUploadMakesPreviouslySyncedTabletStale() {
        String bothDevicesLastPulled = "nas-r0";
        String afterPhoneUpload = "nas-r1";

        assertTrue(NasDatabaseStore.isRemoteRevisionConflict(
                bothDevicesLastPulled, afterPhoneUpload));
        assertFalse(NasDatabaseStore.isRemoteRevisionConflict(
                afterPhoneUpload, afterPhoneUpload));
    }

    @Test
    public void firstDeviceCanInitializeAnEmptyNas() {
        assertFalse(NasDatabaseStore.isRemoteRevisionConflict("", ""));
        assertFalse(NasDatabaseStore.isRemoteRevisionConflict("", "nas-created-by-pc"));
    }
}

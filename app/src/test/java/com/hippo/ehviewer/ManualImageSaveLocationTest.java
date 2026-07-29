/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.hippo.ehviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.preference.PreferenceManager;

import com.hippo.unifile.UniFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@Config(manifest = Config.NONE, sdk = 28)
@RunWith(RobolectricTestRunner.class)
public class ManualImageSaveLocationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        AppConfig.initialize(context);
        Settings.initialize(context);
    }

    @Test
    public void storesAndRestoresWritableDirectory() throws Exception {
        File directory = temporaryFolder.newFolder("manual-save");
        UniFile selected = UniFile.fromFile(directory);

        Settings.putManualImageSaveLocation(selected);

        assertEquals(selected.getUri(), Settings.getManualImageSaveLocationUri());
        UniFile restored = Settings.getConfiguredManualImageSaveLocation();
        assertNotNull(restored);
        assertEquals(selected.getUri(), restored.getUri());
        assertEquals(selected.getUri(), Settings.getManualImageSaveLocation().getUri());
    }

    @Test
    public void rejectsConfiguredLocationThatIsNotDirectory() throws Exception {
        File regularFile = temporaryFolder.newFile("not-a-directory");
        UniFile selected = UniFile.fromFile(regularFile);

        Settings.putManualImageSaveLocation(selected);

        assertEquals(selected.getUri(), Settings.getManualImageSaveLocationUri());
        assertNull(Settings.getConfiguredManualImageSaveLocation());
    }

    @Test
    public void clearingLocationRestoresDefaultSelectionState() throws Exception {
        UniFile selected = UniFile.fromFile(temporaryFolder.newFolder("manual-save"));
        Settings.putManualImageSaveLocation(selected);

        Settings.clearManualImageSaveLocation();

        assertNull(Settings.getManualImageSaveLocationUri());
        assertNull(Settings.getConfiguredManualImageSaveLocation());
    }
}

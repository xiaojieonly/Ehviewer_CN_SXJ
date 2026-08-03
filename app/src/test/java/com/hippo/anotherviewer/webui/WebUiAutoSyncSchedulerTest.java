/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link WebUiAutoSyncScheduler} decision logic (ADR-0003
 * D4), exercising the testability seams: {@code periodicDelayMs} and the
 * synchronous trigger core {@code runTriggerOnce} with injected
 * settings/sync fakes — no Android SharedPreferences or network involved.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiAutoSyncSchedulerTest {

    private static final String SERVER_KEY = "http://127.0.0.1:8080";

    /** Fake {@link WebUiAutoSyncScheduler.SettingsSource} with in-memory state. */
    private static final class FakeSettings implements WebUiAutoSyncScheduler.SettingsSource {
        WebUiConfig config;
        String deviceId = "android-00000000-0000-0000-0000-000000000099";
        long storedWatermark = 0;
        long savedWatermark = Long.MIN_VALUE; // sentinel: Long.MIN_VALUE = never saved
        int intervalSec = 900;

        @Override public WebUiConfig loadConfig() { return config; }
        @Override public String deviceId() { return deviceId; }
        @Override public long lastSyncTimestamp(String serverKey) { return storedWatermark; }
        @Override public void setLastSyncTimestamp(String serverKey, long timestamp) { savedWatermark = timestamp; }
        @Override public int autoSyncIntervalSec() { return intervalSec; }
    }

    /** Fake {@link WebUiAutoSyncScheduler.SyncRunner} capturing arguments. */
    private static final class FakeRunner implements WebUiAutoSyncScheduler.SyncRunner {
        boolean throwOnRun = false;
        long returnedServerTimestamp = 5000;
        long capturedSince = -1;
        String capturedDeviceId = null;
        String capturedBaseUrl = null;

        @Override
        public WebUiSyncEngine.Result run(WebUiConfig config, String deviceId, long since) throws IOException {
            if (throwOnRun) throw new IOException("server unreachable");
            capturedSince = since;
            capturedDeviceId = deviceId;
            capturedBaseUrl = config.baseUrl();
            WebUiSyncEngine.Result result = new WebUiSyncEngine.Result();
            result.serverTimestamp = returnedServerTimestamp;
            return result;
        }
    }

    private static WebUiConfig configured() {
        return new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
    }

    // ==================== periodicDelayMs ====================

    @Test
    public void periodicDelayMs_zeroDisablesPeriodicLeg() {
        assertEquals(-1L, WebUiAutoSyncScheduler.periodicDelayMs(0));
    }

    @Test
    public void periodicDelayMs_negativeClampedToDisabled() {
        assertEquals(-1L, WebUiAutoSyncScheduler.periodicDelayMs(-30));
    }

    @Test
    public void periodicDelayMs_positiveSecondsToMillis() {
        assertEquals(900_000L, WebUiAutoSyncScheduler.periodicDelayMs(900));
        assertEquals(60_000L, WebUiAutoSyncScheduler.periodicDelayMs(60));
    }

    // ==================== runTriggerOnce ====================

    @Test
    public void trigger_unconfiguredServer_skipsSync() {
        FakeSettings settings = new FakeSettings();
        settings.config = null; // no paired server
        FakeRunner runner = new FakeRunner();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner);

        assertFalse("no sync when the server is not configured", ran);
        assertEquals("sync must not run", -1L, runner.capturedSince);
        assertEquals("watermark untouched", Long.MIN_VALUE, settings.savedWatermark);
    }

    @Test
    public void trigger_configured_runsSyncAndPersistsWatermark() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        settings.storedWatermark = 1234;
        FakeRunner runner = new FakeRunner();
        runner.returnedServerTimestamp = 5000;

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner);

        assertTrue("sync runs when configured", ran);
        assertEquals("seeds since from the stored watermark", 1234L, runner.capturedSince);
        assertEquals(SERVER_KEY, runner.capturedBaseUrl);
        assertEquals("persists the returned server timestamp", 5000L, settings.savedWatermark);
    }

    @Test
    public void trigger_failedSync_leavesWatermarkUntouchedForRetry() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        settings.storedWatermark = 777;
        FakeRunner runner = new FakeRunner();
        runner.throwOnRun = true;

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner);

        assertFalse("a failed sync reports not-ran", ran);
        assertEquals("watermark untouched so the next trigger retries", Long.MIN_VALUE, settings.savedWatermark);
    }

    @Test
    public void trigger_passesConfiguredDeviceId() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        settings.deviceId = "android-abc";
        FakeRunner runner = new FakeRunner();

        WebUiAutoSyncScheduler.runTriggerOnce(settings, runner);

        assertEquals("android-abc", runner.capturedDeviceId);
    }
}

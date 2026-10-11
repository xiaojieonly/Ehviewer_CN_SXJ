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

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link WebUiAutoSyncScheduler} decision logic (ADR-0003
 * D4), exercising the testability seams: {@code periodicDelayMs} and the
 * synchronous trigger core {@code runTriggerOnce} with injected
 * settings/sync fakes — no Android SharedPreferences or network involved.
 * 另覆盖偏好腿接缝（{@code PrefSyncLeg}）与生产实现
 * {@code PreferenceSyncHook} 的指纹门控/节流/采纳语义。
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
        boolean skipped = false; // 引擎的「被并发周期代表」字段
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
            result.skipped = skipped;
            return result;
        }
    }

    /** Fake {@link WebUiAutoSyncScheduler.PrefSyncLeg} recording invocations. */
    private static final class FakeLeg implements WebUiAutoSyncScheduler.PrefSyncLeg {
        int calls;
        WebUiConfig lastConfig;
        String lastDeviceId;
        RuntimeException throwOnCall;

        @Override public void afterCycleSuccess(WebUiConfig config, String deviceId) {
            calls++;
            lastConfig = config;
            lastDeviceId = deviceId;
            if (throwOnCall != null) throw throwOnCall;
        }
    }

    private static WebUiConfig configured() {
        return new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
    }

    @Before
    public void resetSharedSchedulerState() {
        // 偏好腿的节流计数是静态的（实例腿与 triggerOnce 腿共享）：用例间
        // 必须归零，否则用例执行顺序会污染计数断言。
        WebUiAutoSyncScheduler.PreferenceSyncHook.resetCycleCounterForTest();
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
    public void trigger_unpairedToken_skipsSync() {
        FakeSettings settings = new FakeSettings();
        settings.config = new WebUiConfig("http", "127.0.0.1", 8080, "user", "");
        FakeRunner runner = new FakeRunner();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner);

        assertFalse("no sync before pairing completes", ran);
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

    // ==================== PrefSyncLeg 接缝 ====================

    @Test
    public void trigger_success_invokesPrefLegExactlyOnceWithCycleArgs() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        settings.deviceId = "android-leg";
        FakeRunner runner = new FakeRunner();
        FakeLeg leg = new FakeLeg();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner, leg);

        assertTrue("主周期成功才带偏好腿", ran);
        assertEquals("偏好腿恰调一次", 1, leg.calls);
        assertEquals("腿拿到主周期的 config", SERVER_KEY, leg.lastConfig.baseUrl());
        assertEquals("腿拿到主周期的 deviceId", "android-leg", leg.lastDeviceId);
    }

    @Test
    public void trigger_failedSync_neverInvokesPrefLegAndKeepsWatermark() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        FakeRunner runner = new FakeRunner();
        runner.throwOnRun = true;
        FakeLeg leg = new FakeLeg();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner, leg);

        assertFalse("主周期失败即失败", ran);
        assertEquals("失败周期不跑偏好腿", 0, leg.calls);
        assertEquals("水位不动", Long.MIN_VALUE, settings.savedWatermark);
    }

    @Test
    public void trigger_prefLegThrowing_runTriggerStillTrueAndWatermarkPersisted() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        settings.storedWatermark = 42;
        FakeRunner runner = new FakeRunner();
        runner.returnedServerTimestamp = 4242;
        FakeLeg leg = new FakeLeg();
        // 兜底契约针对 Throwable：Error/RuntimeException 都不能影响主周期结果。
        leg.throwOnCall = new RuntimeException("leg blew up");

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner, leg);

        assertTrue("偏好腿崩溃不影响主周期成功", ran);
        assertEquals("水位已持久化（腿失败不回滚）", 4242L, settings.savedWatermark);
    }

    @Test
    public void trigger_unconfigured_neverInvokesPrefLeg() {
        FakeSettings settings = new FakeSettings();
        settings.config = null;
        FakeRunner runner = new FakeRunner();
        FakeLeg leg = new FakeLeg();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner, leg);

        assertFalse(ran);
        assertEquals("未配置不跑偏好腿", 0, leg.calls);
    }

    @Test
    public void trigger_skippedResult_persistsWatermarkButStaysSilent() {
        FakeSettings settings = new FakeSettings();
        settings.config = configured();
        FakeRunner runner = new FakeRunner();
        runner.skipped = true; // 本轮被并发周期代表、水位已由对方回填
        FakeLeg leg = new FakeLeg();

        boolean ran = WebUiAutoSyncScheduler.runTriggerOnce(settings, runner, leg);

        assertTrue("被代表也算本轮成功（水位已安全回填）", ran);
        assertEquals("水位照常回填", runner.returnedServerTimestamp, settings.savedWatermark);
        assertEquals("静默：代表性周期已带偏好腿，本轮不再跑", 0, leg.calls);
    }

    // ==================== PreferenceSyncHook（生产偏好腿） ====================

    /** 测试侧独立 FNV-1a（与实现同 offset/prime），用于钉指纹落库值。 */
    private static long fnv1a(String s) {
        long h = 1469598103934665603L; // FNV-1a 64-bit offset basis
        for (int i = 0; i < s.length(); i++) {
            h = (h ^ s.charAt(i)) * 1099511628211L; // FNV-1a prime
        }
        return h;
    }

    /** Fake ops：export/push/pull 全部可控，不碰网络。 */
    private static final class FakeOps implements WebUiAutoSyncScheduler.PreferenceSyncHook.Ops {
        String exported = "A";
        int pushCalls;
        int pullCalls;
        String pulled; // null = 服务器无偏好实体
        IOException pullFailure;
        IOException pushFailure;

        @Override public String export(Context context) { return exported; }

        @Override public void push(WebUiConfig config, Context context, String deviceId) throws IOException {
            // 先计数再抛：pushCalls 语义 = 「推送尝试数」，失败重试用例据此断言。
            pushCalls++;
            if (pushFailure != null) throw pushFailure;
        }

        @Override public String pull(WebUiConfig config, Context context, String deviceId) throws IOException {
            if (pullFailure != null) throw pullFailure;
            pullCalls++;
            return pulled;
        }
    }

    private static Context appContext() {
        return RuntimeEnvironment.application;
    }

    private static SharedPreferences statePrefs(Context context) {
        return context.getSharedPreferences("webui_sync_state", Context.MODE_PRIVATE);
    }

    @Test
    public void hook_pushGate_pushesOnlyWhenDocumentChanges() {
        Context context = appContext();
        WebUiConfig config = configured();
        FakeOps ops = new FakeOps();
        ops.exported = "A";
        WebUiAutoSyncScheduler.PreferenceSyncHook hook =
                new WebUiAutoSyncScheduler.PreferenceSyncHook(context, ops);

        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("首份文档 A（无已存指纹）：推 1 次", 1, ops.pushCalls);

        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("文档未变（仍 A）：不重复推", 1, ops.pushCalls);

        ops.exported = "B";
        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("文档变 B：再推 1 次", 2, ops.pushCalls);
    }

    @Test
    public void hook_pullThrottle_pullsExactlyOnceEverySixCycles() {
        Context context = appContext();
        WebUiConfig config = configured();
        FakeOps ops = new FakeOps();
        WebUiAutoSyncScheduler.PreferenceSyncHook hook =
                new WebUiAutoSyncScheduler.PreferenceSyncHook(context, ops);

        for (int i = 0; i < 5; i++) {
            hook.afterCycleSuccess(config, "android-leg");
        }
        assertEquals("前 5 个周期不拉（节流等待）", 0, ops.pullCalls);

        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("第 6 个周期恰拉 1 次", 1, ops.pullCalls);

        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("随后回到节流等待", 1, ops.pullCalls);
    }

    @Test
    public void hook_pullAdoption_doesNotEchoPulledContentBack() {
        Context context = appContext();
        WebUiConfig config = configured();
        FakeOps ops = new FakeOps();
        ops.exported = "A";
        WebUiAutoSyncScheduler.PreferenceSyncHook hook =
                new WebUiAutoSyncScheduler.PreferenceSyncHook(context, ops);

        // 周期 1：推 A，指纹(A) 落库。
        hook.afterCycleSuccess(config, "android-leg");
        assertEquals(1, ops.pushCalls);

        // 周期 2–5：无 pull、无变化，安静。
        for (int i = 0; i < 4; i++) {
            hook.afterCycleSuccess(config, "android-leg");
        }
        assertEquals(0, ops.pullCalls);

        // 周期 6（pull 节流点）：服务器有不同偏好 B。pull 返回 B 文档并已
        // 导入本地（导出随之变 B）。若无采纳语义，本周期 push 门控会看到
        // 指纹变化并把刚拉下来的内容原样回推（回显）。
        ops.pulled = "B";
        ops.exported = "B";
        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("节流点恰拉 1 次", 1, ops.pullCalls);
        assertEquals("拉下来的内容不被回显推送", 1, ops.pushCalls);

        // 采纳只压制「与已采纳指纹相同」的那一轮：之后的真实本地变更照常推。
        ops.exported = "C";
        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("采纳不永久压制后续真实变更", 2, ops.pushCalls);
    }

    @Test
    public void hook_fingerprintLandsUnderBaseUrlPrefPushFp() {
        Context context = appContext();
        WebUiConfig config = configured();
        FakeOps ops = new FakeOps();
        ops.exported = "A";
        WebUiAutoSyncScheduler.PreferenceSyncHook hook =
                new WebUiAutoSyncScheduler.PreferenceSyncHook(context, ops);

        hook.afterCycleSuccess(config, "android-leg");

        String fpKey = SERVER_KEY + ".pref_push_fp";
        assertTrue("指纹键落在 webui_sync_state 的 <baseUrl>.pref_push_fp",
                statePrefs(context).contains(fpKey));
        assertEquals("指纹值是导出文档的 FNV-1a", fnv1a("A"),
                statePrefs(context).getLong(fpKey, 0L));
    }

    @Test
    public void hook_pushFailure_keepsFingerprintUnwrittenSoNextCycleRetries() {
        Context context = appContext();
        WebUiConfig config = configured();
        FakeOps ops = new FakeOps();
        ops.exported = "A";
        ops.pushFailure = new IOException("server down");
        WebUiAutoSyncScheduler.PreferenceSyncHook hook =
                new WebUiAutoSyncScheduler.PreferenceSyncHook(context, ops);

        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("push 失败仍被尝试", 1, ops.pushCalls);
        assertFalse("失败不落指纹（键不存在 ≠ 与 0 号指纹相同）",
                statePrefs(context).contains(SERVER_KEY + ".pref_push_fp"));

        ops.pushFailure = null;
        hook.afterCycleSuccess(config, "android-leg");
        assertEquals("下周期指纹仍不一致 → 自动重试", 2, ops.pushCalls);
    }
}

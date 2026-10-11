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

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.upload.AutoUploadPrefs;
import com.hippo.anotherviewer.upload.UploadReconciler;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network-aware auto-sync (ADR-0003 D4): a sync cycle runs when the network
 * changes (default-network callback, API 24+) and periodically every
 * {@code autoSyncIntervalSec} while a paired server is configured.
 * {@code autoSyncIntervalSec == 0} disables the periodic leg (network-change
 * only). New networks never auto-pair — an unconfigured server simply does not
 * trigger anything.
 *
 * <p>All sync work runs on a background executor; the engine is synchronous.
 */
public final class WebUiAutoSyncScheduler {

    private static final String TAG = "WebUiAutoSync";

    /**
     * Testability seam (D4): the settings the scheduler reads/writes. The
     * production implementation wraps {@link WebUiSettings}; unit tests inject
     * a fake to drive the trigger logic without Android SharedPreferences.
     */
    interface SettingsSource {
        WebUiConfig loadConfig();
        String deviceId();
        long lastSyncTimestamp(String serverKey);
        void setLastSyncTimestamp(String serverKey, long timestamp);
        int autoSyncIntervalSec();
    }

    /**
     * Testability seam (D4): runs one sync cycle. Production delegates to
     * {@link WebUiSyncEngine#sync}; unit tests inject a fake to assert the
     * trigger passes the stored watermark and persists the new one.
     */
    interface SyncRunner {
        WebUiSyncEngine.Result run(WebUiConfig config, String deviceId, long since) throws IOException;
    }

    /**
     * Testability seam: the preference leg hung off the auto-sync cycle.
     * Runs after a successful cycle once the watermark is persisted; failures
     * must never affect the cycle's return value or watermark (the caller
     * guards with try/catch(Throwable)). The Context travels inside the leg —
     * keeping {@code runTriggerOnce} Context-free preserves its pure-JVM
     * testability (FakeSettings/FakeRunner, no Robolectric needed).
     */
    interface PrefSyncLeg {
        void afterCycleSuccess(WebUiConfig config, String deviceId);
    }

    private final Context appContext;
    private final SettingsSource settings;
    private final SyncRunner runner;
    private final PrefSyncLeg prefLeg;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webui-auto-sync");
        t.setDaemon(true);
        return t;
    });

    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean running;

    public WebUiAutoSyncScheduler(@NonNull Context context) {
        appContext = context.getApplicationContext();
        settings = wrap(new WebUiSettings(appContext));
        runner = WebUiSyncEngine::sync;
        // 生产偏好腿：随周期拉取/推送用户偏好（见 PreferenceSyncHook）。
        prefLeg = new PreferenceSyncHook(appContext);
    }

    private static SettingsSource wrap(@NonNull final WebUiSettings s) {
        return new SettingsSource() {
            @Override public WebUiConfig loadConfig() { return s.loadConfig(); }
            @Override public String deviceId() { return s.deviceId(); }
            @Override public long lastSyncTimestamp(String serverKey) { return s.lastSyncTimestamp(serverKey); }
            @Override public void setLastSyncTimestamp(String serverKey, long timestamp) { s.setLastSyncTimestamp(serverKey, timestamp); }
            @Override public int autoSyncIntervalSec() { return s.autoSyncIntervalSec(); }
        };
    }

    /** Periodic leg delay; {@code -1} when the interval disables it (0 = network-change only). */
    public static long periodicDelayMs(int autoSyncIntervalSec) {
        if (autoSyncIntervalSec <= 0) return -1L;
        return autoSyncIntervalSec * 1000L;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        registerNetworkCallback();
        scheduleNextPeriodic();
    }

    public synchronized void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        unregisterNetworkCallback();
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                trigger();
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
            // Best effort: some OEMs throw on late registration.
            networkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // Already unregistered.
            }
        }
        networkCallback = null;
    }

    private void scheduleNextPeriodic() {
        long delay = periodicDelayMs(settings.autoSyncIntervalSec());
        if (delay < 0) return;
        handler.postDelayed(() -> {
            trigger();
            synchronized (WebUiAutoSyncScheduler.this) {
                if (running) scheduleNextPeriodic();
            }
        }, delay);
    }

    /** Runs one sync cycle if a server is configured; failures are swallowed. */
    public void trigger() {
        executor.submit(() -> runTriggerOnce(settings, runner, prefLeg));
    }

    /**
     * Fire-and-forget immediate sync on the production settings, for callers
     * that want one cycle right now (e.g. right after an EH login, so the new
     * session reaches the server without waiting for the next auto-sync leg).
     * No-ops silently when the server is not configured or not paired yet
     * (no token); the watermark advances only on success and failures are
     * swallowed, exactly like the auto-sync legs. EH 登录后的一次性周期同样带
     * 偏好腿——否则刚登录拿到的偏好要等下一个自动周期才会同步。
     */
    public static void triggerOnce(@NonNull Context context) {
        final WebUiSettings settings = new WebUiSettings(context.getApplicationContext());
        if (settings.loadConfig() == null) return;
        Thread thread = new Thread(() -> {
            try {
                runTriggerOnce(wrap(settings), WebUiSyncEngine::sync,
                        new PreferenceSyncHook(context.getApplicationContext()));
            } catch (Throwable ignored) {
                // Fire-and-forget: never surface sync failures.
            }
        }, "webui-sync-once");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Synchronous trigger core, extracted for testability (D4). Runs one sync
     * cycle when a server is configured, seeding {@code since} from the stored
     * per-server watermark and persisting the returned server timestamp; a
     * failed sync leaves the watermark untouched so the next trigger retries.
     * A configured-but-unpaired server (no token yet) also skips the cycle.
     *
     * @return {@code true} if a sync cycle ran successfully, {@code false} if
     *         skipped (no server configured / not paired) or the sync threw.
     */
    static boolean runTriggerOnce(@NonNull SettingsSource settings, @NonNull SyncRunner runner) {
        // 2 参旧签名保留委托 null 偏好腿：既有测试与调用方零改动。
        return runTriggerOnce(settings, runner, null);
    }

    /**
     * 三参核心：{@code prefLeg} 非空时在主周期成功且水位持久化之后执行偏好腿。
     * 偏好腿被 try/catch(Throwable) 兜底——失败只记日志，绝不影响本方法的
     * 返回值与已持久化的水位（主周期成功是事实，不能被附加腿回滚）。
     */
    static boolean runTriggerOnce(@NonNull SettingsSource settings, @NonNull SyncRunner runner,
            @Nullable PrefSyncLeg prefLeg) {
        WebUiConfig config = settings.loadConfig();
        if (config == null) return false;
        if (TextUtils.isEmpty(config.getToken())) return false;
        String serverKey = config.baseUrl();
        try {
            WebUiSyncEngine.Result result = runner.run(config, settings.deviceId(), settings.lastSyncTimestamp(serverKey));
            settings.setLastSyncTimestamp(serverKey, result.serverTimestamp);
            // skipped（引擎字段）：本轮被一个并发周期代表、水位已由对方安全回填。
            // 静默处理：不跑偏好腿（代表性周期已带它自己的腿），也不安排立即
            // 重试——下一节拍自然重试即可，立刻重试只会撞上同一个并发周期。
            if (result.skipped) {
                return true;
            }
            if (prefLeg != null) {
                try {
                    prefLeg.afterCycleSuccess(config, settings.deviceId());
                } catch (Throwable t) {
                    // 偏好腿失败只记日志：不影响返回值与水位，下周期自动重试。
                    Log.w(TAG, "preference sync leg failed", t);
                }
            }
            return true;
        } catch (IOException ignored) {
            // Server unreachable: retry on the next trigger.
            return false;
        }
    }

    /**
     * 生产偏好腿：主周期成功后按「先 pull 后 push」维护用户偏好。
     *
     * <ul>
     * <li>pull 节流：静态周期计数每 {@link #PULL_EVERY_N_CYCLES}（6）个周期拉取
     *     一次——默认 10s 节拍下约 1 分钟一次偏好拉取，足够近实时又不至于每个
     *     周期都打一次网络。计数是静态 {@link AtomicLong}，实例腿与
     *     {@code triggerOnce} 一次性腿共享同一节拍。</li>
     * <li>push 门控：逐周期对 {@link PreferenceSyncHelper#exportPreferences}
     *     做 FNV-1a 指纹比对，指纹没变就不推。每周期都跑 export 是有意为之
     *     （门控必须逐周期比对才知道变了没有）——3 个 JSONObject 约 30 个键的
     *     序列化是亚毫秒级，远低于同步周期本身的耗时，不构成热点。不引入
     *     OnSharedPreferenceChangeListener：Settings 用静态默认 prefs，监听器
     *     需要生命周期接线且会对全部无关键误触发，复杂度与收益不成比例。</li>
     * <li>顺序先 pull 后 push：pull 成功且有实体时本地偏好刚被服务器内容改写，
     *     重新 export 并把新指纹记为「已知已推送」（采纳语义，仿引擎 ehSession
     *     的不回显决策）——否则配对后的首个推送门控周期会把刚拉下来的内容
     *     原样回推，制造一次无意义的服务端覆盖。</li>
     * </ul>
     */
    static final class PreferenceSyncHook implements PrefSyncLeg {

        /**
         * 偏好指纹的存储文件。字面量镜像 {@code SiteDbWebUiSyncStore.PREFS}
         * （该类不在本文件所有权内，不能引用其常量之外的实现细节；实际上指纹
         * 键落在它的清理范围里——{@code clearConfig()} 按 baseUrl 前缀清空该
         * 文件，解除配对后指纹自动归零，重配对后首轮必推送一次）。
         */
        private static final String SYNC_STATE_PREFS = "webui_sync_state";

        /** 指纹键后缀：完整键为 {@code <baseUrl>.pref_push_fp}。 */
        private static final String PREF_PUSH_FP_SUFFIX = ".pref_push_fp";

        /**
         * 偏好 pull 的周期节流：每 6 个周期拉一次（10s 节拍 ≈ 1 分钟一次）。
         * 选 6 而非更小：偏好是低频变更数据，1 分钟粒度足够；而非更大：与
         * 下载记录补推（同节流点）共享节拍，太稀会拖慢新下载记录的补推。
         */
        static final long PULL_EVERY_N_CYCLES = 6;

        /** 共享周期计数：实例腿与 triggerOnce 一次性腿共用同一节拍。 */
        private static final AtomicLong CYCLE_COUNTER = new AtomicLong();

        /** 包私有：仅供测试重置共享节流计数器（静态状态不能跨用例残留）。 */
        static void resetCycleCounterForTest() {
            CYCLE_COUNTER.set(0);
        }

        /**
         * 测试接缝：export/push/pull 的可注入实现。生产实现直通
         * {@link PreferenceSyncHelper} 的静态方法；单测注入 FakeOps 以在不碰
         * 网络的情况下钉死指纹门控/节流/采纳语义。
         */
        interface Ops {
            String export(Context context);
            void push(WebUiConfig config, Context context, String deviceId) throws IOException;
            /** 返回被消费的偏好文档；null = 服务器无偏好实体。 */
            String pull(WebUiConfig config, Context context, String deviceId) throws IOException;
        }

        private static final Ops PRODUCTION_OPS = new Ops() {
            @Override public String export(Context context) {
                return PreferenceSyncHelper.exportPreferences(context);
            }
            @Override public void push(WebUiConfig config, Context context, String deviceId) throws IOException {
                PreferenceSyncHelper.pushToServer(config, context, deviceId);
            }
            @Override public String pull(WebUiConfig config, Context context, String deviceId) throws IOException {
                return PreferenceSyncHelper.pullPreferencesOnly(config, context, deviceId);
            }
        };

        private final Context appContext;
        private final Ops ops;

        PreferenceSyncHook(@NonNull Context appContext) {
            this(appContext, PRODUCTION_OPS);
        }

        /** 包私有：测试注入 FakeOps。 */
        PreferenceSyncHook(@NonNull Context appContext, @NonNull Ops ops) {
            this.appContext = appContext.getApplicationContext();
            this.ops = ops;
        }

        @Override
        public void afterCycleSuccess(@NonNull WebUiConfig config, @NonNull String deviceId) {
            // 顺序先 pull 后 push（见类注释：pull 采纳要抢在 push 门控之前）。
            boolean pullDue = CYCLE_COUNTER.incrementAndGet() % PULL_EVERY_N_CYCLES == 0;
            if (pullDue) {
                pullAndAdopt(config, deviceId);
                // 下载记录补推与偏好拉取共用同一节流点（每 6 周期）。
                enqueueMissingDownloads();
            }
            pushIfFingerprintChanged(config, deviceId);
        }

        /**
         * 节流点内的偏好拉取：拉到实体则按采纳语义把「刚拉下来的内容」的
         * 新指纹记为已知已推送，本轮随后的 push 门控视其为未变化（不回显）。
         */
        private void pullAndAdopt(@NonNull WebUiConfig config, @NonNull String deviceId) {
            try {
                String pulled = ops.pull(config, appContext, deviceId);
                if (pulled != null) {
                    // pull 已改写本地偏好，重导出取新指纹（采纳，不回显）。
                    long adopted = fingerprint(ops.export(appContext));
                    statePrefs().edit()
                            .putLong(config.baseUrl() + PREF_PUSH_FP_SUFFIX, adopted)
                            .apply();
                }
            } catch (IOException e) {
                // 拉取失败只记日志：本轮仍有 push 门控兜底本地变更，下周期重拉。
                Log.w(TAG, "preference pull leg failed", e);
            }
        }

        /**
         * 节流点内的下载记录补推：对账「本地已 FINISHED 但未上传成功」的
         * 下载并入队自动上传。开关门控在这里做（{@link AutoUploadPrefs}）——
         * Reconciler 自身不检查开关（契约口径：门控归调用方）。重复调用
         * 幂等：服务端 init 的 existingPages 差分与 pushed_gids TTL 去重
         * 都会吸收。失败只记日志——补推腿绝不能影响主周期与偏好腿。
         */
        private void enqueueMissingDownloads() {
            try {
                if (!AutoUploadPrefs.isEnabled(appContext)) {
                    return;
                }
                UploadReconciler.maybeEnqueueMissing(appContext);
            } catch (Throwable t) {
                Log.w(TAG, "download upload reconcile failed", t);
            }
        }

        /**
         * push 门控：本地偏好指纹与上次已推送指纹不同才推。用 {@code contains()}
         * 精判「从未推送过」——0 是合法 FNV 值，{@code getLong(key, 0)} 会把
         * 首轮（键不存在）误判成「与 0 号指纹相同」而漏推。
         */
        private void pushIfFingerprintChanged(@NonNull WebUiConfig config, @NonNull String deviceId) {
            SharedPreferences state = statePrefs();
            String fpKey = config.baseUrl() + PREF_PUSH_FP_SUFFIX;
            long fingerprint = fingerprint(ops.export(appContext));
            if (state.contains(fpKey) && state.getLong(fpKey, 0L) == fingerprint) {
                return; // 指纹未变：不推。
            }
            try {
                ops.push(config, appContext, deviceId);
                // 成功才落指纹：失败留给下周期（指纹仍不一致 → 自动重试）。
                state.edit().putLong(fpKey, fingerprint).apply();
            } catch (IOException e) {
                Log.w(TAG, "preference push leg failed", e);
            }
        }

        private SharedPreferences statePrefs() {
            return appContext.getSharedPreferences(SYNC_STATE_PREFS, Context.MODE_PRIVATE);
        }

        /**
         * 独立的 64-bit FNV-1a（offset {@code 1469598103934665603}、prime
         * {@code 1099511628211}）。引擎里有同构的私有 mixFingerprint，但为避免
         * 耦合其私有实现（它还混入 ehSession 的结构语义），这里自写一份纯
         * 字符串版本。
         */
        private static long fingerprint(String s) {
            long h = 1469598103934665603L; // FNV-1a 64-bit offset basis
            for (int i = 0; i < s.length(); i++) {
                h = (h ^ s.charAt(i)) * 1099511628211L; // FNV-1a prime
            }
            return h;
        }
    }
}

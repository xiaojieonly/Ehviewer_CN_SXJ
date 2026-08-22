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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * TH8：WebUiCredentialStore 的 IV 拼接 / Base64 / 截断防御逻辑。
 *
 * <p>AndroidKeyStore 在 JVM 单测中不可用，因此「可用密钥」路径用桌面 JCE 的
 * AES 密钥注入私有 {@code keyStore} 字段来驱动真实 save/load 代码（仅测试侧
 * 反射，不改产品代码）：save 产出的 Base64 必须是 {@code IV(12B) || GGM 密文}
 * 且 load 能还原——IV 拼接与拆分、Base64 编解码、GCM 认证失败兜底全部被覆盖。
 *
 * <p>截断防御（combined.length <= IV 长度一律返回空串）在触碰密钥之前生效，
 * JVM 上即可直测。keystore 完全不可用时 save/load 均优雅降级为空串、绝不抛出，
 * 也一并钉死（FBE 未解锁场景的进程内表现）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class WebUiCredentialStoreTest {

    /** 与产品常量一致：GCM 标准 IV 12 字节。 */
    private static final int IV_LENGTH = 12;

    @Before
    public void setUp() {
        // android.util.Base64 / TextUtils 由 Robolectric 提供。
    }

    private static void injectKeyStore(WebUiCredentialStore store, KeyStore keyStore) {
        try {
            Field field = WebUiCredentialStore.class.getDeclaredField("keyStore");
            field.setAccessible(true);
            field.set(store, keyStore);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 桌面 JCE 生成的 AES-256 密钥，别名与产品一致，模拟已初始化的 keystore。 */
    private static KeyStore usableKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        ks.setKeyEntry("anotherviewer_webui_token", generator.generateKey(), null, null);
        return ks;
    }

    // ------------------------------------------------------------------
    // 空输入防御
    // ------------------------------------------------------------------

    @Test
    public void emptyInputsShortCircuitToEmptyString() {
        WebUiCredentialStore store = new WebUiCredentialStore();
        assertEquals("", store.save(null));
        assertEquals("", store.save(""));
        assertEquals("", store.load(null));
        assertEquals("", store.load(""));
        assertEquals("", store.load("   "));
    }

    // ------------------------------------------------------------------
    // 截断防御：<= IV 长度的 payload 一律拒绝（先于任何密钥访问）
    // ------------------------------------------------------------------

    @Test
    public void payloadsNotLongerThanIvAreRejectedBeforeKeyAccess() {
        WebUiCredentialStore store = new WebUiCredentialStore();

        byte[] exactlyIv = new byte[IV_LENGTH];          // 恰好等于 IV 长度：密文为空
        assertEquals("", store.load(Base64.getEncoder().encodeToString(exactlyIv)));

        byte[] shorter = new byte[IV_LENGTH - 1];
        assertEquals("", store.load(Base64.getEncoder().encodeToString(shorter)));

        byte[] tiny = new byte[]{1};
        assertEquals("", store.load(Base64.getEncoder().encodeToString(tiny)));
    }

    // ------------------------------------------------------------------
    // 损坏输入防御
    // ------------------------------------------------------------------

    @Test
    public void corruptBase64NeverThrows() {
        WebUiCredentialStore store = new WebUiCredentialStore();
        assertEquals("", store.load("not-base64!!!"));
        assertEquals("", store.load("a"));
        assertEquals("", store.load("abc$def"));
    }

    // ------------------------------------------------------------------
    // 可用密钥下的完整往返：IV 拼接/拆分 + Base64 + GCM
    // ------------------------------------------------------------------

    @Test
    public void saveProducesIvPrefixedBase64AndLoadRoundTrips() throws Exception {
        WebUiCredentialStore store = new WebUiCredentialStore();
        injectKeyStore(store, usableKeyStore());

        String secret = "bearer-token-αβγ-123";
        String encoded = store.save(secret);

        assertNotEquals("", encoded);
        assertFalse("Base64 NO_WRAP 不含换行", encoded.contains("\n"));

        byte[] combined = Base64.getDecoder().decode(encoded);
        assertTrue("payload 应为 IV || ciphertext", combined.length > IV_LENGTH);
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

        // 用同一密钥手动解密后 12 字节起的密文：证明前 12 字节就是随机 IV。
        Cipher probe = Cipher.getInstance("AES/GCM/NoPadding");
        probe.init(Cipher.DECRYPT_MODE, keyOf(store),
                new GCMParameterSpec(128, iv));
        byte[] plaintext = probe.doFinal(
                java.util.Arrays.copyOfRange(combined, IV_LENGTH, combined.length));
        assertEquals(secret, new String(plaintext, StandardCharsets.UTF_8));

        // 产品自己的 load 也必须还原。
        assertEquals(secret, store.load(encoded));
    }

    @Test
    public void eachSaveUsesFreshRandomIvSoCiphertextsDiffer() throws Exception {
        WebUiCredentialStore store = new WebUiCredentialStore();
        injectKeyStore(store, usableKeyStore());

        String first = store.save("same-secret");
        String second = store.save("same-secret");

        assertNotEquals("随机化加密要求两次密文不同", first, second);
        assertEquals("same-secret", store.load(first));
        assertEquals("same-secret", store.load(second));
    }

    @Test
    public void tamperedCiphertextOrWrongKeyDegradesToEmptyString() throws Exception {
        WebUiCredentialStore store = new WebUiCredentialStore();
        injectKeyStore(store, usableKeyStore());
        String encoded = store.save("secret-value");
        byte[] combined = Base64.getDecoder().decode(encoded);

        // 翻转密文区一个比特 → GCM 认证失败 → 空串，不抛异常。
        byte[] tampered = combined.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertEquals("", store.load(Base64.getEncoder().encodeToString(tampered)));

        // 拆掉最后一个字节（密文截断）→ 同样空串。
        byte[] truncated = new byte[combined.length - 1];
        System.arraycopy(combined, 0, truncated, 0, truncated.length);
        assertEquals("", store.load(Base64.getEncoder().encodeToString(truncated)));

        // 换一把钥匙（模拟 keystore 重置后读旧数据）→ 空串。
        WebUiCredentialStore freshStore = new WebUiCredentialStore();
        injectKeyStore(freshStore, usableKeyStore());
        assertEquals("", freshStore.load(encoded));
    }

    @Test
    public void legacyStylePayloadWithGarbageAfterIvStillTriesAndDegrades() throws Exception {
        WebUiCredentialStore store = new WebUiCredentialStore();
        injectKeyStore(store, usableKeyStore());

        byte[] junk = new byte[IV_LENGTH + 5]; // 长度合法但内容非 GCM 密文
        assertEquals("", store.load(Base64.getEncoder().encodeToString(junk)));
    }

    // ------------------------------------------------------------------
    // keystore 不可用（JVM/FBE 未解锁）：优雅降级、不抛出、不锁死
    // ------------------------------------------------------------------

    @Test
    public void unavailableKeystoreDegradesGracefullyWithoutThrowing() {
        WebUiCredentialStore store = new WebUiCredentialStore(); // 未注入 → AndroidKeyStore 加载失败

        assertEquals("", store.save("anything"));
        // > IV 长度的输入会走到密钥获取；不可用时也必须回空串而不是抛错。
        byte[] longEnough = new byte[IV_LENGTH + 16];
        assertEquals("", store.load(Base64.getEncoder().encodeToString(longEnough)));

        // 失败不闩锁：注入密钥后同一实例立即可用（R4-16 重试语义）。
        try {
            injectKeyStore(store, usableKeyStore());
            assertEquals("recovered", store.load(store.save("recovered")));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static SecretKey keyOf(WebUiCredentialStore store) throws Exception {
        Field field = WebUiCredentialStore.class.getDeclaredField("keyStore");
        field.setAccessible(true);
        KeyStore ks = (KeyStore) field.get(store);
        return (SecretKey) ks.getKey("anotherviewer_webui_token", null);
    }
}

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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts the WebUI bearer token at rest using the AndroidKeyStore. Follows the
 * same AES/GCM approach as {@link com.hippo.anotherviewer.smb.SmbCredentialStore} but
 * uses a distinct key alias so the two backends do not share key material.
 *
 * <p>All fallible work (KeyStore load, key generation) is deferred to a lazy,
 * thread-safe init performed on first use. Any keystore failure degrades
 * gracefully to "no token" instead of throwing, so this class is safe to
 * construct on the UI thread even on FBE devices before first unlock
 * (see audit: construction used to run KeyStore work on every settings refresh).
 *
 * <p>Init success latches for the instance's lifetime; failure does not
 * (R4-16): the Tier-2 interceptor's credential store is constructed once,
 * together with the singleton OkHttp client, so a transient keystore outage
 * at that early moment (e.g. FBE before first unlock) must not degrade Bearer
 * attachment until a cold restart — later requests retry the init instead.
 */
public final class WebUiCredentialStore {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "anotherviewer_webui_token";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private KeyStore keyStore;

    public WebUiCredentialStore(Context context) {
        // No fallible work here: keystore access happens lazily on first use so
        // construction can never throw or block the caller's thread.
    }

    /**
     * Lazily loads the keystore and creates the key on first use. Never throws.
     * Success latches; failure does not — the next call retries, so a transient
     * early outage is not pinned for the whole process lifetime (R4-16).
     */
    private synchronized boolean ensureInitialized() {
        if (keyStore != null) {
            return true;
        }
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build());
                generator.generateKey();
            }
            keyStore = ks;
        } catch (GeneralSecurityException | IOException e) {
            // E.g. keystore locked before first unlock on FBE devices. Degrade
            // to "no token" rather than crashing; the next call retries instead
            // of latching the failure until a cold restart (R4-16).
            keyStore = null;
        }
        return keyStore != null;
    }

    public String save(String secret) {
        if (TextUtils.isEmpty(secret)) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey key = getSecretKey();
            if (key == null) {
                return "";
            }
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    public String load(String encoded) {
        if (TextUtils.isEmpty(encoded)) {
            return "";
        }
        try {
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                return "";
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey key = getSecretKey();
            if (key == null) {
                return "";
            }
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return "";
        }
    }

    /** Returns {@code null} (instead of throwing) when the keystore is unusable. */
    private SecretKey getSecretKey() {
        if (!ensureInitialized()) {
            return null;
        }
        try {
            return (SecretKey) keyStore.getKey(ALIAS, null);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }
}

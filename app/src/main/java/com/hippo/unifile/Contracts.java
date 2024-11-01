/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.unifile;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Contracts {
    private Contracts() {
    }

    static String queryForString(Context context, Uri self, String column,
                                 String defaultValue) {
        final ContentResolver resolver = context.getContentResolver();

        Cursor c = null;
        try {
            c = resolver.query(self, new String[]{column}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            } else {
                return defaultValue;
            }
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            return defaultValue;
        } finally {
            com.hippo.unifile.Utils.closeQuietly(c);
        }
    }

    static int queryForInt(Context context, Uri self, String column,
                           int defaultValue) {
        return (int) queryForLong(context, self, column, defaultValue);
    }

    static long queryForLong(Context context, Uri self, String column,
                             long defaultValue) {
        final ContentResolver resolver = context.getContentResolver();

        Cursor c = null;
        try {
            c = resolver.query(self, new String[]{column}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getLong(0);
            } else {
                return defaultValue;
            }
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            return defaultValue;
        } finally {
            com.hippo.unifile.Utils.closeQuietly(c);
        }
    }

    @NonNull
    static OutputStream openOutputStream(Context context, Uri uri) throws IOException {
        try {
            OutputStream os = context.getContentResolver().openOutputStream(uri);
            if (os == null) {
                throw new IOException("Can't open OutputStream");
            }
            return os;
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            throw new IOException("Can't open OutputStream", e);
        }
    }

    @NonNull
    static OutputStream openOutputStream(Context context, Uri uri, boolean append) throws IOException {
        try {
            OutputStream os = context.getContentResolver().openOutputStream(uri, append ? "wa" : "w");
            if (os == null) {
                throw new IOException("Can't open OutputStream");
            }
            return os;
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            throw new IOException("Can't open OutputStream", e);
        }
    }

    @NonNull
    static InputStream openInputStream(Context context, Uri uri) throws IOException {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) {
                throw new IOException("Can't open InputStream");
            }
            return is;
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            throw new IOException("Can't open InputStream", e);
        }
    }
}

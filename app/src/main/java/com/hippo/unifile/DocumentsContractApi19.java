/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.unifile;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

@TargetApi(Build.VERSION_CODES.KITKAT)
final class DocumentsContractApi19 {
    private static final String TAG = DocumentsContractApi19.class.getSimpleName();

    private DocumentsContractApi19() {
    }

    public static boolean isDocumentUri(Context context, Uri self) {
        return DocumentsContract.isDocumentUri(context, self);
    }

    public static String getName(Context context, Uri self) {
        return Contracts.queryForString(context, self, DocumentsContract.Document.COLUMN_DISPLAY_NAME, null);
    }

    private static String getRawType(Context context, Uri self) {
        return Contracts.queryForString(context, self, DocumentsContract.Document.COLUMN_MIME_TYPE, null);
    }

    public static String getType(Context context, Uri self) {
        final String rawType = getRawType(context, self);
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(rawType)) {
            return null;
        } else {
            return rawType;
        }
    }

    public static boolean isDirectory(Context context, Uri self) {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(getRawType(context, self));
    }

    public static boolean isFile(Context context, Uri self) {
        final String type = getRawType(context, self);
        return !(DocumentsContract.Document.MIME_TYPE_DIR.equals(type) || TextUtils.isEmpty(type));
    }

    public static long lastModified(Context context, Uri self) {
        return Contracts.queryForLong(context, self, DocumentsContract.Document.COLUMN_LAST_MODIFIED, -1L);
    }

    public static long length(Context context, Uri self) {
        return Contracts.queryForLong(context, self, DocumentsContract.Document.COLUMN_SIZE, -1L);
    }

    public static boolean canRead(Context context, Uri self) {
        // Ignore if grant doesn't allow read
        if (context.checkCallingOrSelfUriPermission(self, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // Ignore documents without MIME
        return !TextUtils.isEmpty(getRawType(context, self));
    }

    public static boolean canWrite(Context context, Uri self) {
        // Ignore if grant doesn't allow write
        if (context.checkCallingOrSelfUriPermission(self, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        final String type = getRawType(context, self);
        final int flags = Contracts.queryForInt(context, self, DocumentsContract.Document.COLUMN_FLAGS, 0);

        // Ignore documents without MIME
        if (TextUtils.isEmpty(type)) {
            return false;
        }

        // Deletable documents considered writable
        if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0) {
            return true;
        }

        // Writable normal files considered writable
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(type)
                && (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0) {
            // Directories that allow create considered writable
            return true;
        } else return !TextUtils.isEmpty(type)
                && (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
    }

    public static boolean delete(Context context, Uri self) {
        try {
            return DocumentsContract.deleteDocument(context.getContentResolver(), self);
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            // Maybe user ejects tf card
            Log.e(TAG, "Failed to renameTo", e);
            return false;
        }
    }

    public static boolean exists(Context context, Uri self) {
        final ContentResolver resolver = context.getContentResolver();

        Cursor c = null;
        try {
            c = resolver.query(self, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            return null != c && c.getCount() > 0;
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            // Log.w(TAG, "Failed query: " + e);
            return false;
        } finally {
            Utils.closeQuietly(c);
        }
    }
}

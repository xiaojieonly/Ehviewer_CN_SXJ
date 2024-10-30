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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class DocumentsContractApi21 {
    private static final String TAG = DocumentsContractApi21.class.getSimpleName();
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_TREE = "tree";

    private DocumentsContractApi21() {
    }

    public static Uri createFile(Context context, Uri self, String mimeType,
                                 String displayName) {
        try {
            return DocumentsContract.createDocument(context.getContentResolver(), self, mimeType,
                    displayName);
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            // Maybe user ejects tf card
            Log.e(TAG, "Failed to createFile: " + self, e);
            return null;
        }
    }

    public static Uri createDirectory(Context context, Uri self, String displayName) {
        return createFile(context, self, DocumentsContract.Document.MIME_TYPE_DIR, displayName);
    }

    public static Uri prepareTreeUri(Uri treeUri) {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
    }

    public static String getTreeDocumentPath(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 4 && PATH_TREE.equals(paths.get(0)) && PATH_DOCUMENT.equals(paths.get(2))) {
            return paths.get(3);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    public static Uri buildChildUri(Uri uri, String displayName) {
        return DocumentsContract.buildDocumentUriUsingTree(uri,
                getTreeDocumentPath(uri) + "/" + displayName);
    }

    public static Uri[] listFiles(Context context, Uri self) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(self,
                DocumentsContract.getDocumentId(self));
        final ArrayList<Uri> results = new ArrayList<>();

        Cursor c = null;
        try {
            c = resolver.query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            if (null != c) {
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(self,
                            documentId);
                    results.add(documentUri);
                }
            }
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            Log.e(TAG, "Failed listFiles: " + self, e);
        } finally {
            Utils.closeQuietly(c);
        }

        return results.toArray(new Uri[results.size()]);
    }

    public static Uri renameTo(Context context, Uri self, String displayName) {
        try {
            return DocumentsContract.renameDocument(context.getContentResolver(), self, displayName);
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            // Maybe user ejects tf card
            Log.e(TAG, "Failed to renameTo:" + self + ", " + displayName, e);
            return null;
        }
    }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * In Android files can be accessed via {@link File} and {@link Uri}.
 * The UniFile is designed to emulate File interface for both File and Uri.
 */
public abstract class UniFile {

    private static List<com.hippo.unifile.UriHandler> sUriHandlerArray;

    private final UniFile mParent;

    UniFile(UniFile parent) {
        mParent = parent;
    }

    /**
     * Add a UriHandler to get UniFile from uri
     */
    public static void addUriHandler(@NonNull com.hippo.unifile.UriHandler handler) {
        if (sUriHandlerArray == null) {
            sUriHandlerArray = new ArrayList<>();
        }
        sUriHandlerArray.add(handler);
    }

    /**
     * Remove the UriHandler added before
     */
    public static void removeUriHandler(UriHandler handler) {
        if (sUriHandlerArray != null) {
            sUriHandlerArray.remove(handler);
        }
    }

    /**
     * Create a {@link UniFile} representing the given {@link File}.
     *
     * @param file the file to wrap
     * @return the {@link UniFile} representing the given {@link File}.
     */
    @Nullable
    public static UniFile fromFile(@Nullable File file) {
        return file != null ? new com.hippo.unifile.RawFile(null, file) : null;
    }

    /**
     * Create a {@link UniFile} representing the single document at the
     * given {@link Uri}. This is only useful on devices running
     * {@link Build.VERSION_CODES#KITKAT} or later, and will return
     * {@code null} when called on earlier platform versions.
     *
     * @param singleUri the {@link Intent#getData()} from a successful
     *                  {@link Intent#ACTION_OPEN_DOCUMENT} or
     *                  {@link Intent#ACTION_CREATE_DOCUMENT} request.
     * @return the {@link UniFile} representing the given {@link Uri}.
     */
    public static UniFile fromSingleUri(Context context, Uri singleUri) {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 19) {
            return new com.hippo.unifile.SingleDocumentFile(null, context, singleUri);
        } else {
            return null;
        }
    }

    /**
     * Create a {@link UniFile} representing the document tree rooted at
     * the given {@link Uri}. This is only useful on devices running
     * {@link Build.VERSION_CODES#LOLLIPOP} or later, and will return
     * {@code null} when called on earlier platform versions.
     *
     * @param treeUri the {@link Intent#getData()} from a successful
     *                {@link Intent#ACTION_OPEN_DOCUMENT_TREE} request.
     * @return the {@link UniFile} representing the given {@link Uri}.
     */
    public static UniFile fromTreeUri(Context context, Uri treeUri) {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 21) {
            return new com.hippo.unifile.TreeDocumentFile(null, context,
                    DocumentsContractApi21.prepareTreeUri(treeUri));
        } else {
            return null;
        }
    }

    /**
     * Create a {@link UniFile} representing the media file rooted at
     * the given {@link Uri}.
     *
     * @param mediaUri the media uri to wrap
     * @return the {@link UniFile} representing the given {@link Uri}.
     */
    public static UniFile fromMediaUri(Context context, Uri mediaUri) {
        return new com.hippo.unifile.MediaFile(context, mediaUri);
    }

    /**
     * Create a {@link UniFile} representing the given {@link Uri}.
     */
    public static UniFile fromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }

        // Custom handler
        if (sUriHandlerArray != null) {
            for (int i = 0, size = sUriHandlerArray.size(); i < size; i++) {
                UniFile file = sUriHandlerArray.get(i).fromUri(context, uri);
                if (file != null) {
                    return file;
                }
            }
        }

        if (isFileUri(uri)) {
            return fromFile(new File(uri.getPath()));
        } else if (isDocumentUri(context, uri)) {
            if (isTreeUri(uri)) {
                return fromTreeUri(context, uri);
            } else {
                return fromSingleUri(context, uri);
            }
        } else if (com.hippo.unifile.MediaFile.isMediaUri(context, uri)) {
            return new com.hippo.unifile.MediaFile(context, uri);
        } else {
            return null;
        }
    }

    /**
     * Test if given Uri is FileUri
     */
    public static boolean isFileUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_FILE.equals(uri.getScheme());
    }

    /**
     * Test if given Uri is backed by a
     * {@link android.provider.DocumentsProvider}.
     */
    public static boolean isDocumentUri(Context context, Uri uri) {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 19) {
            return com.hippo.unifile.DocumentsContractApi19.isDocumentUri(context, uri);
        } else {
            return false;
        }
    }

    /**
     * Test if given Uri is TreeUri
     */
    public static boolean isTreeUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        final List<String> paths = uri.getPathSegments();
        return (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && paths.size() >= 2 && "tree".equals(paths.get(0)));
    }

    /**
     * Create a new file as a direct child of this directory.
     *
     * @param displayName name of new file
     * @return file representing newly created document, or null if failed
     * @see android.provider.DocumentsContract#createDocument(ContentResolver,
     * Uri, String, String)
     */
    public abstract UniFile createFile(String displayName);

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName name of new directory
     * @return file representing newly created directory, or null if failed
     * @see android.provider.DocumentsContract#createDocument(ContentResolver,
     * Uri, String, String)
     */
    public abstract UniFile createDirectory(String displayName);

    /**
     * Return a Uri for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content. You can use {@link #isTreeUri(Uri)} to
     * test if the returned Uri is backed by a
     * {@link android.provider.DocumentsProvider}.
     *
     * @return uri of the file
     * @see Intent#setData(Uri)
     * @see Intent#setClipData(android.content.ClipData)
     * @see ContentResolver#openInputStream(Uri)
     * @see ContentResolver#openOutputStream(Uri)
     * @see ContentResolver#openFileDescriptor(Uri, String)
     */
    @NonNull
    public abstract Uri getUri();

    /**
     * Return the display name of this file.
     *
     * @return name of the file, or null if failed
     * @see android.provider.DocumentsContract.Document#COLUMN_DISPLAY_NAME
     */
    @Nullable
    public abstract String getName();

    /**
     * Return the MIME type of this file.
     *
     * @return MIME type of the file, or null if failed
     * @see android.provider.DocumentsContract.Document#COLUMN_MIME_TYPE
     */
    @Nullable
    public abstract String getType();

    /**
     * Return the parent file of this file. Only defined inside of the
     * user-selected tree; you can never escape above the top of the tree.
     * <p>
     * The underlying {@link android.provider.DocumentsProvider} only defines a
     * forward mapping from parent to child, so the reverse mapping of child to
     * parent offered here is purely a convenience method, and it may be
     * incorrect if the underlying tree structure changes.
     *
     * @return parent of the file, or null if it is the top of the file tree
     */
    @Nullable
    public UniFile getParentFile() {
        return mParent;
    }

    /**
     * Indicates if this file represents a <em>directory</em>.
     *
     * @return {@code true} if this file is a directory, {@code false}
     * otherwise.
     * @see android.provider.DocumentsContract.Document#MIME_TYPE_DIR
     */
    public abstract boolean isDirectory();

    /**
     * Indicates if this file represents a <em>file</em>.
     *
     * @return {@code true} if this file is a file, {@code false} otherwise.
     * @see android.provider.DocumentsContract.Document#COLUMN_MIME_TYPE
     */
    public abstract boolean isFile();

    /**
     * Returns the time when this file was last modified, measured in
     * milliseconds since January 1st, 1970, midnight. Returns -1 if the file
     * does not exist, or if the modified time is unknown.
     *
     * @return the time when this file was last modified, <code>-1L</code> if can't get it
     * @see android.provider.DocumentsContract.Document#COLUMN_LAST_MODIFIED
     */
    public abstract long lastModified();

    /**
     * Returns the length of this file in bytes. Returns -1 if the file does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     *
     * @return the number of bytes in this file, <code>-1L</code> if can't get it
     * @see android.provider.DocumentsContract.Document#COLUMN_SIZE
     */
    public abstract long length();

    /**
     * Indicates whether the current context is allowed to read from this file.
     *
     * @return {@code true} if this file can be read, {@code false} otherwise.
     */
    public abstract boolean canRead();

    /**
     * Indicates whether the current context is allowed to write to this file.
     *
     * @return {@code true} if this file can be written, {@code false}
     * otherwise.
     * @see android.provider.DocumentsContract.Document#COLUMN_FLAGS
     * @see android.provider.DocumentsContract.Document#FLAG_SUPPORTS_DELETE
     * @see android.provider.DocumentsContract.Document#FLAG_SUPPORTS_WRITE
     * @see android.provider.DocumentsContract.Document#FLAG_DIR_SUPPORTS_CREATE
     */
    public abstract boolean canWrite();

    /**
     * It works like mkdirs, but it will return true if the UniFile is directory
     *
     * @return {@code true} if the directory was created
     * or if the directory already existed.
     */
    public abstract boolean ensureDir();

    /**
     * Make sure the UniFile is file
     *
     * @return {@code true} if the file can be created
     * or if the file already existed.
     */
    public abstract boolean ensureFile();

    /**
     * Get child file of this directory, the child might not exist.
     *
     * @return the child file, {@code null} if not supported
     */
    @Nullable
    public abstract UniFile subFile(String displayName);

    /**
     * Deletes this file.
     * <p>
     * Note that this method does <i>not</i> throw {@code IOException} on
     * failure. Callers must check the return value.
     *
     * @return {@code true} if this file was deleted, {@code false} otherwise.
     * @see android.provider.DocumentsContract#deleteDocument(ContentResolver,
     * Uri)
     */
    public abstract boolean delete();

    /**
     * Returns a boolean indicating whether this file can be found.
     *
     * @return {@code true} if this file exists, {@code false} otherwise.
     */
    public abstract boolean exists();

    /**
     * Returns an array of files contained in the directory represented by this
     * file.
     *
     * @return an array of files or {@code null}.
     * @see android.provider.DocumentsContract#buildChildDocumentsUriUsingTree(Uri,
     * String)
     */
    @Nullable
    public abstract UniFile[] listFiles();

    /**
     * Gets a list of the files in the directory represented by this file. This
     * list is then filtered through a FilenameFilter and the names of files
     * with matching names are returned as an array of strings.
     *
     * @param filter the filter to match names against, may be {@code null}.
     * @return an array of files or {@code null}.
     */
    @Nullable
    public abstract UniFile[] listFiles(FilenameFilter filter);

    /**
     * Test there is a file with the display name in the directory.
     *
     * @return the file if found it, or {@code null}.
     */
    @Nullable
    public abstract UniFile findFile(String displayName);

    /**
     * Renames this file to {@code displayName}.
     * <p>
     * Note that this method does <i>not</i> throw {@code IOException} on
     * failure. Callers must check the return value.
     * <p>
     * Some providers may need to create a new file to reflect the rename,
     * potentially with a different MIME type, so {@link #getUri()} and
     * {@link #getType()} may change to reflect the rename.
     * <p>
     * When renaming a directory, children previously enumerated through
     * {@link #listFiles()} may no longer be valid.
     *
     * @param displayName the new display name.
     * @return true on success.
     * @see android.provider.DocumentsContract#renameDocument(ContentResolver,
     * Uri, String)
     */
    public abstract boolean renameTo(String displayName);

    /**
     * Open a stream on to the content associated with the file, clean it if it exists
     *
     * @return the {@link OutputStream}
     * @throws IOException
     */
    @NonNull
    public abstract OutputStream openOutputStream() throws IOException;

    /**
     * Open a stream on to the content associated with the file
     *
     * @param append {@code true} for do not clean it if it exists
     * @return the {@link OutputStream}
     * @throws IOException
     */
    @NonNull
    public abstract OutputStream openOutputStream(boolean append) throws IOException;

    /**
     * Open a stream on to the content associated with the file
     *
     * @return the {@link InputStream}
     * @throws IOException
     */
    @NonNull
    public abstract InputStream openInputStream() throws IOException;

    /**
     * Get a random access stuff of the UniFile
     *
     * @param mode "r" or "rw"
     * @return the random access stuff
     * @throws IOException
     */
    @NonNull
    public abstract UniRandomAccessFile createRandomAccessFile(String mode) throws IOException;
}

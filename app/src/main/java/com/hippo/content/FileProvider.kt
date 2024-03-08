/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.hippo.content

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * FileProvider is a special subclass of [ContentProvider] that facilitates secure sharing
 * of files associated with an app by creating a `content://` [Uri] for a file
 * instead of a `file:///` [Uri].
 *
 *
 * A content URI allows you to grant read and write access using
 * temporary access permissions. When you create an [Intent] containing
 * a content URI, in order to send the content URI
 * to a client app, you can also call [Intent.setFlags()][Intent.setFlags] to add
 * permissions. These permissions are available to the client app for as long as the stack for
 * a receiving [android.app.Activity] is active. For an [Intent] going to a
 * [android.app.Service], the permissions are available as long as the
 * [android.app.Service] is running.
 *
 *
 * In comparison, to control access to a `file:///` [Uri] you have to modify the
 * file system permissions of the underlying file. The permissions you provide become available to
 * *any* app, and remain in effect until you change them. This level of access is
 * fundamentally insecure.
 *
 *
 * The increased level of file access security offered by a content URI
 * makes FileProvider a key part of Android's security infrastructure.
 *
 *
 * This overview of FileProvider includes the following topics:
 *
 *
 *  1. [Defining a FileProvider](#ProviderDefinition)
 *  1. [Specifying Available Files](#SpecifyFiles)
 *  1. [Retrieving the Content URI for a File](#GetUri)
 *  1. [Granting Temporary Permissions to a URI](#Permissions)
 *  1. [Serving a Content URI to Another App](#ServeUri)
 *
 * <h3 id="ProviderDefinition">Defining a FileProvider</h3>
 *
 *
 * Since the default functionality of FileProvider includes content URI generation for files, you
 * don't need to define a subclass in code. Instead, you can include a FileProvider in your app
 * by specifying it entirely in XML. To specify the FileProvider component itself, add a
 * `[&lt;provider&gt;]({@docRoot}guide/topics/manifest/provider-element.html)`
 * element to your app manifest. Set the `android:name` attribute to
 * `android.support.v4.content.FileProvider`. Set the `android:authorities`
 * attribute to a URI authority based on a domain you control; for example, if you control the
 * domain `mydomain.com` you should use the authority
 * `com.mydomain.fileprovider`. Set the `android:exported` attribute to
 * `false`; the FileProvider does not need to be public. Set the
 * [android:grantUriPermissions]({@docRoot}guide/topics/manifest/provider-element.html#gprmsn) attribute to `true`, to allow you
 * to grant temporary access to files. For example:
 * <pre class="prettyprint">
 * &lt;manifest&gt;
 * ...
 * &lt;application&gt;
 * ...
 * &lt;provider
 * android:name="android.support.v4.content.FileProvider"
 * android:authorities="com.mydomain.fileprovider"
 * android:exported="false"
 * android:grantUriPermissions="true"&gt;
 * ...
 * &lt;/provider&gt;
 * ...
 * &lt;/application&gt;
 * &lt;/manifest&gt;</pre>
 *
 *
 * If you want to override any of the default behavior of FileProvider methods, extend
 * the FileProvider class and use the fully-qualified class name in the `android:name`
 * attribute of the `<provider>` element.
 * <h3 id="SpecifyFiles">Specifying Available Files</h3>
 * A FileProvider can only generate a content URI for files in directories that you specify
 * beforehand. To specify a directory, specify the its storage area and path in XML, using child
 * elements of the `<paths>` element.
 * For example, the following `paths` element tells FileProvider that you intend to
 * request content URIs for the `images/` subdirectory of your private file area.
 * <pre class="prettyprint">
 * &lt;paths xmlns:android="http://schemas.android.com/apk/res/android"&gt;
 * &lt;files-path name="my_images" path="images/"/&gt;
 * ...
 * &lt;/paths&gt;
</pre> *
 *
 *
 * The `<paths>` element must contain one or more of the following child elements:
 *
 * <dl>
 * <dt>
 * <pre class="prettyprint">
 * &lt;files-path name="*name*" path="*path*" /&gt;
</pre> *
</dt> *
 * <dd>
 * Represents files in the `files/` subdirectory of your app's internal storage
 * area. This subdirectory is the same as the value returned by [     Context.getFilesDir()][Context.getFilesDir].
</dd> *
 * <dt>
 * <pre>
 * &lt;cache-path name="*name*" path="*path*" /&gt;
</pre> *
</dt> * <dt>
</dt> * <dd>
 * Represents files in the cache subdirectory of your app's internal storage area. The root path
 * of this subdirectory is the same as the value returned by [     getCacheDir()][Context.getCacheDir].
</dd> *
 * <dt>
 * <pre class="prettyprint">
 * &lt;external-path name="*name*" path="*path*" /&gt;
</pre> *
</dt> *
 * <dd>
 * Represents files in the root of the external storage area. The root path of this subdirectory
 * is the same as the value returned by
 * [Environment.getExternalStorageDirectory()][Environment.getExternalStorageDirectory].
</dd> *
 * <dt>
 * <pre class="prettyprint">
 * &lt;external-files-path name="*name*" path="*path*" /&gt;
</pre> *
</dt> *
 * <dd>
 * Represents files in the root of your app's external storage area. The root path of this
 * subdirectory is the same as the value returned by
 * `Context#getExternalFilesDir(String) Context.getExternalFilesDir(null)`.
</dd> *
 * <dt>
 * <pre class="prettyprint">
 * &lt;external-cache-path name="*name*" path="*path*" /&gt;
</pre> *
</dt> *
 * <dd>
 * Represents files in the root of your app's external cache area. The root path of this
 * subdirectory is the same as the value returned by
 * [Context.getExternalCacheDir()][Context.getExternalCacheDir].
</dd> *
</dl> *
 *
 *
 * These child elements all use the same attributes:
 *
 * <dl>
 * <dt>
 * `name="*name*"`
</dt> *
 * <dd>
 * A URI path segment. To enforce security, this value hides the name of the subdirectory
 * you're sharing. The subdirectory name for this value is contained in the
 * `path` attribute.
</dd> *
 * <dt>
 * `path="*path*"`
</dt> *
 * <dd>
 * The subdirectory you're sharing. While the `name` attribute is a URI path
 * segment, the `path` value is an actual subdirectory name. Notice that the
 * value refers to a **subdirectory**, not an individual file or files. You can't
 * share a single file by its file name, nor can you specify a subset of files using
 * wildcards.
</dd> *
</dl> *
 *
 *
 * You must specify a child element of `<paths>` for each directory that contains
 * files for which you want content URIs. For example, these XML elements specify two directories:
 * <pre class="prettyprint">
 * &lt;paths xmlns:android="http://schemas.android.com/apk/res/android"&gt;
 * &lt;files-path name="my_images" path="images/"/&gt;
 * &lt;files-path name="my_docs" path="docs/"/&gt;
 * &lt;/paths&gt;
</pre> *
 *
 *
 * Put the `<paths>` element and its children in an XML file in your project.
 * For example, you can add them to a new file called `res/xml/file_paths.xml`.
 * To link this file to the FileProvider, add a
 * [&lt;meta-data&gt;]({@docRoot}guide/topics/manifest/meta-data-element.html) element
 * as a child of the `<provider>` element that defines the FileProvider. Set the
 * `<meta-data>` element's "android:name" attribute to
 * `android.support.FILE_PROVIDER_PATHS`. Set the element's "android:resource" attribute
 * to `&#64;xml/file_paths` (notice that you don't specify the `.xml`
 * extension). For example:
 * <pre class="prettyprint">
 * &lt;provider
 * android:name="android.support.v4.content.FileProvider"
 * android:authorities="com.mydomain.fileprovider"
 * android:exported="false"
 * android:grantUriPermissions="true"&gt;
 * &lt;meta-data
 * android:name="android.support.FILE_PROVIDER_PATHS"
 * android:resource="&#64;xml/file_paths" /&gt;
 * &lt;/provider&gt;
</pre> *
 * <h3 id="GetUri">Generating the Content URI for a File</h3>
 *
 *
 * To share a file with another app using a content URI, your app has to generate the content URI.
 * To generate the content URI, create a new [File] for the file, then pass the [File]
 * to [getUriForFile()][.getUriForFile]. You can send the content URI
 * returned by [getUriForFile()][.getUriForFile] to another app in an
 * [Intent]. The client app that receives the content URI can open the file
 * and access its contents by calling
 * [ ContentResolver.openFileDescriptor][android.content.ContentResolver.openFileDescriptor] to get a [ParcelFileDescriptor].
 *
 *
 * For example, suppose your app is offering files to other apps with a FileProvider that has the
 * authority `com.mydomain.fileprovider`. To get a content URI for the file
 * `default_image.jpg` in the `images/` subdirectory of your internal storage
 * add the following code:
 * <pre class="prettyprint">
 * File imagePath = new File(Context.getFilesDir(), "images");
 * File newFile = new File(imagePath, "default_image.jpg");
 * Uri contentUri = getUriForFile(getContext(), "com.mydomain.fileprovider", newFile);
</pre> *
 * As a result of the previous snippet,
 * [getUriForFile()][.getUriForFile] returns the content URI
 * `content://com.mydomain.fileprovider/my_images/default_image.jpg`.
 * <h3 id="Permissions">Granting Temporary Permissions to a URI</h3>
 * To grant an access permission to a content URI returned from
 * [getUriForFile()][.getUriForFile], do one of the following:
 *
 *  *
 * Call the method
 * [     Context.grantUriPermission(package, Uri, mode_flags)][Context.grantUriPermission] for the `content://`
 * [Uri], using the desired mode flags. This grants temporary access permission for the
 * content URI to the specified package, according to the value of the
 * the `mode_flags` parameter, which you can set to
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION], [Intent.FLAG_GRANT_WRITE_URI_PERMISSION]
 * or both. The permission remains in effect until you revoke it by calling
 * [revokeUriPermission()][Context.revokeUriPermission] or until the device
 * reboots.
 *
 *  *
 * Put the content URI in an [Intent] by calling [setData()][Intent.setData].
 *
 *  *
 * Next, call the method [Intent.setFlags()][Intent.setFlags] with either
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION] or
 * [Intent.FLAG_GRANT_WRITE_URI_PERMISSION] or both.
 *
 *  *
 * Finally, send the [Intent] to
 * another app. Most often, you do this by calling
 * [setResult()][android.app.Activity.setResult].
 *
 *
 * Permissions granted in an [Intent] remain in effect while the stack of the receiving
 * [android.app.Activity] is active. When the stack finishes, the permissions are
 * automatically removed. Permissions granted to one [android.app.Activity] in a client
 * app are automatically extended to other components of that app.
 *
 *
 *
 * <h3 id="ServeUri">Serving a Content URI to Another App</h3>
 *
 *
 * There are a variety of ways to serve the content URI for a file to a client app. One common way
 * is for the client app to start your app by calling
 * [startActivityResult()][android.app.Activity.startActivityForResult],
 * which sends an [Intent] to your app to start an [android.app.Activity] in your app.
 * In response, your app can immediately return a content URI to the client app or present a user
 * interface that allows the user to pick a file. In the latter case, once the user picks the file
 * your app can return its content URI. In both cases, your app returns the content URI in an
 * [Intent] sent via [setResult()][android.app.Activity.setResult].
 *
 *
 *
 * You can also put the content URI in a [ClipData] object and then add the
 * object to an [Intent] you send to a client app. To do this, call
 * [Intent.setClipData()][Intent.setClipData]. When you use this approach, you can
 * add multiple [ClipData] objects to the [Intent], each with its own
 * content URI. When you call [Intent.setFlags()][Intent.setFlags] on the [Intent]
 * to set temporary access permissions, the same permissions are applied to all of the content
 * URIs.
 *
 *
 *
 * **Note:** The [Intent.setClipData()][Intent.setClipData] method is
 * only available in platform version 16 (Android 4.1) and later. If you want to maintain
 * compatibility with previous versions, you should send one content URI at a time in the
 * [Intent]. Set the action to [Intent.ACTION_SEND] and put the URI in data by calling
 * [setData()][Intent.setData].
 *
 * <h3 id="">More Information</h3>
 *
 *
 * To learn more about FileProvider, see the Android training class
 * [Sharing Files Securely with URIs]({@docRoot}training/secure-file-sharing/index.html).
 *
 */
class FileProvider : ContentProvider() {
    private var mStrategy: PathStrategy? = null

    /**
     * The default FileProvider implementation does not need to be initialized. If you want to
     * override this method, you must provide your own subclass of FileProvider.
     */
    override fun onCreate(): Boolean {
        return true
    }

    /**
     * After the FileProvider is instantiated, this method is called to provide the system with
     * information about the provider.
     *
     * @param context A [Context] for the current component.
     * @param info A [ProviderInfo] for the new provider.
     */
    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)

        // Sanity check our security
        if (info.exported) {
            throw SecurityException("Provider must not be exported")
        }
        if (!info.grantUriPermissions) {
            throw SecurityException("Provider must grant uri permissions")
        }
        mStrategy = getPathStrategy(context, info.authority)
    }

    /**
     * Use a content URI returned by
     * [getUriForFile()][.getUriForFile] to get information about a file
     * managed by the FileProvider.
     * FileProvider reports the column names defined in [MediaStore.MediaColumns]:
     *
     *  * [MediaStore.MediaColumns.DISPLAY_NAME]
     *  * [MediaStore.MediaColumns.SIZE]
     *  * [MediaStore.MediaColumns.DATA]
     *
     * For more information, see
     * [ ContentProvider.query()][ContentProvider.query].
     *
     * @param uri A content URI returned by [.getUriForFile].
     * @param projection The list of columns to put into the [Cursor]. If null all columns are
     * included.
     * @param selection Selection criteria to apply. If null then all data that matches the content
     * URI is returned.
     * @param selectionArgs An array of [String], containing arguments to bind to
     * the *selection* parameter. The *query* method scans *selection* from left to
     * right and iterates through *selectionArgs*, replacing the current "?" character in
     * *selection* with the value at the current position in *selectionArgs*. The
     * values are bound to *selection* as [String] values.
     * @param sortOrder A [String] containing the column name(s) on which to sort
     * the resulting [Cursor].
     * @return A [Cursor] containing the results of the query.
     */
    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        // ContentProvider has already checked granted permissions
        var projection = projection
        val file = mStrategy!!.getFileForUri(uri)
        if (projection == null) {
            projection = COLUMNS
        }
        var cols = arrayOfNulls<String>(projection.size)
        var values = arrayOfNulls<Any>(projection.size)
        var i = 0
        for (col in projection) {
            if (MediaStore.MediaColumns.DISPLAY_NAME == col) {
                cols[i] = MediaStore.MediaColumns.DISPLAY_NAME
                values[i++] = file.name
            } else if (MediaStore.MediaColumns.SIZE == col) {
                cols[i] = MediaStore.MediaColumns.SIZE
                values[i++] = file.length()
            } else if (MediaStore.MediaColumns.DATA == col) {
                cols[i] = MediaStore.MediaColumns.DATA
                values[i++] = file.path
            }
        }
        cols = copyOf(cols, i)
        values = copyOf(values, i)
        val cursor = MatrixCursor(cols, 1)
        cursor.addRow(values)
        return cursor
    }

    /**
     * Returns the MIME type of a content URI returned by
     * [getUriForFile()][.getUriForFile].
     *
     * @param uri A content URI returned by
     * [getUriForFile()][.getUriForFile].
     * @return If the associated file has an extension, the MIME type associated with that
     * extension; otherwise `application/octet-stream`.
     */
    override fun getType(uri: Uri): String? {
        // ContentProvider has already checked granted permissions
        val file = mStrategy!!.getFileForUri(uri)
        val lastDot = file.name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = file.name.substring(lastDot + 1)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }
        return "application/octet-stream"
    }

    /**
     * By default, this method throws an [UnsupportedOperationException]. You must
     * subclass FileProvider if you want to provide different functionality.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    /**
     * By default, this method throws an [UnsupportedOperationException]. You must
     * subclass FileProvider if you want to provide different functionality.
     */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("No external updates")
    }

    /**
     * Deletes the file associated with the specified content URI, as
     * returned by [getUriForFile()][.getUriForFile]. Notice that this
     * method does **not** throw an [IOException]; you must check its return value.
     *
     * @param uri A content URI for a file, as returned by
     * [getUriForFile()][.getUriForFile].
     * @param selection Ignored. Set to `null`.
     * @param selectionArgs Ignored. Set to `null`.
     * @return 1 if the delete succeeds; otherwise, 0.
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        // ContentProvider has already checked granted permissions
        val file = mStrategy!!.getFileForUri(uri)
        return if (file.delete()) 1 else 0
    }

    /**
     * By default, FileProvider automatically returns the
     * [ParcelFileDescriptor] for a file associated with a `content://`
     * [Uri]. To get the [ParcelFileDescriptor], call
     * [ ContentResolver.openFileDescriptor][android.content.ContentResolver.openFileDescriptor].
     *
     * To override this method, you must provide your own subclass of FileProvider.
     *
     * @param uri A content URI associated with a file, as returned by
     * [getUriForFile()][.getUriForFile].
     * @param mode Access mode for the file. May be "r" for read-only access, "rw" for read and
     * write access, or "rwt" for read and write access that truncates any existing file.
     * @return A new [ParcelFileDescriptor] with which you can access the file.
     */
    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // ContentProvider has already checked granted permissions
        val file = mStrategy!!.getFileForUri(uri)
        val fileMode = modeToMode(mode)
        return ParcelFileDescriptor.open(file, fileMode)
    }

    /**
     * Strategy for mapping between [File] and [Uri].
     *
     *
     * Strategies must be symmetric so that mapping a [File] to a
     * [Uri] and then back to a [File] points at the original
     * target.
     *
     *
     * Strategies must remain consistent across app launches, and not rely on
     * dynamic state. This ensures that any generated [Uri] can still be
     * resolved if your process is killed and later restarted.
     *
     * @see SimplePathStrategy
     */
    internal interface PathStrategy {
        /**
         * Return a [Uri] that represents the given [File].
         */
        fun getUriForFile(file: File): Uri?

        /**
         * Return a [File] that represents the given [Uri].
         */
        fun getFileForUri(uri: Uri): File
    }

    /**
     * Strategy that provides access to files living under a narrow whitelist of
     * filesystem roots. It will throw [SecurityException] if callers try
     * accessing files outside the configured roots.
     *
     *
     * For example, if configured with
     * `addRoot("myfiles", context.getFilesDir())`, then
     * `context.getFileStreamPath("foo.txt")` would map to
     * `content://myauthority/myfiles/foo.txt`.
     */
    internal class SimplePathStrategy(private val mAuthority: String) : PathStrategy {
        private val mRoots = HashMap<String, File>()

        /**
         * Add a mapping from a name to a filesystem root. The provider only offers
         * access to files that live under configured roots.
         */
        fun addRoot(name: String, root: File) {
            var root = root
            require(!TextUtils.isEmpty(name)) { "Name must not be empty" }
            root = try {
                // Resolve to canonical path to keep path checking fast
                root.canonicalFile
            } catch (e: IOException) {
                throw IllegalArgumentException(
                    "Failed to resolve canonical path for $root", e
                )
            }
            mRoots[name] = root
        }

        override fun getUriForFile(file: File): Uri? {
            var path: String
            path = try {
                file.canonicalPath
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to resolve canonical path for $file")
            }

            // Find the most-specific root path
            var mostSpecific: Map.Entry<String, File>? = null
            for (root in mRoots.entries) {
                val rootPath = root.value.path
                if (path.startsWith(rootPath) && (mostSpecific == null
                            || rootPath.length > mostSpecific.value.path.length)
                ) {
                    mostSpecific = root
                }
            }
            requireNotNull(mostSpecific) { "Failed to find configured root that contains $path" }

            // Start at first char of path under root
            val rootPath = mostSpecific.value.path
            path = if (rootPath.endsWith("/")) {
                path.substring(rootPath.length)
            } else {
                path.substring(rootPath.length + 1)
            }

            // Encode the tag and path separately
            path = Uri.encode(mostSpecific.key) + '/' + Uri.encode(path, "/")
            return Uri.Builder().scheme("content")
                .authority(mAuthority).encodedPath(path).build()
        }

        override fun getFileForUri(uri: Uri): File {
            var path = uri.encodedPath
            val splitIndex = path!!.indexOf('/', 1)
            val tag = Uri.decode(path.substring(1, splitIndex))
            path = Uri.decode(path.substring(splitIndex + 1))
            val root = mRoots[tag]
                ?: throw IllegalArgumentException("Unable to find configured root for $uri")
            var file = File(root, path)
            file = try {
                file.canonicalFile
            } catch (e: IOException) {
                throw IllegalArgumentException("Failed to resolve canonical path for $file")
            }
            if (!file.path.startsWith(root.path)) {
                throw SecurityException("Resolved path jumped beyond configured root")
            }
            return file
        }
    }

    companion object {
        private val COLUMNS = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )
        private const val META_DATA_FILE_PROVIDER_PATHS = "com.hippo.content.FILE_PROVIDER_PATHS"
        private const val TAG_ROOT_PATH = "root-path"
        private const val TAG_FILES_PATH = "files-path"
        private const val TAG_CACHE_PATH = "cache-path"
        private const val TAG_EXTERNAL = "external-path"
        private const val TAG_EXTERNAL_FILES = "external-files-path"
        private const val TAG_EXTERNAL_CACHE = "external-cache-path"
        private const val ATTR_NAME = "name"
        private const val ATTR_PATH = "path"
        private val DEVICE_ROOT = File("/")

        // @GuardedBy("sCache")
        private val sCache = HashMap<String, PathStrategy?>()

        /**
         * Return a content URI for a given [File]. Specific temporary
         * permissions for the content URI can be set with
         * [Context.grantUriPermission], or added
         * to an [Intent] by calling [setData()][Intent.setData] and then
         * [setFlags()][Intent.setFlags]; in both cases, the applicable flags are
         * [Intent.FLAG_GRANT_READ_URI_PERMISSION] and
         * [Intent.FLAG_GRANT_WRITE_URI_PERMISSION]. A FileProvider can only return a
         * `content` [Uri] for file paths defined in their `<paths>`
         * meta-data element. See the Class Overview for more information.
         *
         * @param context A [Context] for the current component.
         * @param authority The authority of a [FileProvider] defined in a
         * `<provider>` element in your app's manifest.
         * @param file A [File] pointing to the filename for which you want a
         * `content` [Uri].
         * @return A content URI for the file.
         * @throws IllegalArgumentException When the given [File] is outside
         * the paths supported by the provider.
         */
        @JvmStatic
        fun getUriForFile(context: Context, authority: String, file: File): Uri? {
            val strategy = getPathStrategy(context, authority)
            return strategy!!.getUriForFile(file)
        }

        /**
         * Return [PathStrategy] for given authority, either by parsing or
         * returning from cache.
         */
        private fun getPathStrategy(context: Context, authority: String): PathStrategy? {
            var strat: PathStrategy?
            synchronized(sCache) {
                strat = sCache[authority]
                if (strat == null) {
                    strat = try {
                        parsePathStrategy(context, authority)
                    } catch (e: IOException) {
                        throw IllegalArgumentException(
                            "Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e
                        )
                    } catch (e: XmlPullParserException) {
                        throw IllegalArgumentException(
                            "Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e
                        )
                    }
                    sCache[authority] = strat
                }
            }
            return strat
        }

        /**
         * Parse and return [PathStrategy] for given authority as defined in
         * [.META_DATA_FILE_PROVIDER_PATHS] `<meta-data>`.
         *
         * @see .getPathStrategy
         */
        @Throws(IOException::class, XmlPullParserException::class)
        private fun parsePathStrategy(context: Context, authority: String): PathStrategy {
            val strat = SimplePathStrategy(authority)
            val info = context.packageManager
                .resolveContentProvider(authority, PackageManager.GET_META_DATA)
            val `in` = info!!.loadXmlMetaData(
                context.packageManager,
                META_DATA_FILE_PROVIDER_PATHS
            )
                ?: throw IllegalArgumentException(
                    "Missing " + META_DATA_FILE_PROVIDER_PATHS + " meta-data"
                )
            var type: Int
            while (`in`.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG) {
                    val tag = `in`.name
                    val name = `in`.getAttributeValue(null, ATTR_NAME)
                    val path = `in`.getAttributeValue(null, ATTR_PATH)
                    var target: File? = null
                    if (TAG_ROOT_PATH == tag) {
                        target = DEVICE_ROOT
                    } else if (TAG_FILES_PATH == tag) {
                        target = context.filesDir
                    } else if (TAG_CACHE_PATH == tag) {
                        target = context.cacheDir
                    } else if (TAG_EXTERNAL == tag) {
                        target = Environment.getExternalStorageDirectory()
                    } else if (TAG_EXTERNAL_FILES == tag) {
                        val externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null)
                        if (externalFilesDirs.size > 0) {
                            target = externalFilesDirs[0]
                        }
                    } else if (TAG_EXTERNAL_CACHE == tag) {
                        val externalCacheDirs = ContextCompat.getExternalCacheDirs(context)
                        if (externalCacheDirs.size > 0) {
                            target = externalCacheDirs[0]
                        }
                    }
                    if (target != null) {
                        strat.addRoot(name, buildPath(target, path))
                    }
                }
            }
            return strat
        }

        /**
         * Copied from ContentResolver.java
         */
        private fun modeToMode(mode: String): Int {
            val modeBits: Int
            modeBits = if ("r" == mode) {
                ParcelFileDescriptor.MODE_READ_ONLY
            } else if ("w" == mode || "wt" == mode) {
                (ParcelFileDescriptor.MODE_WRITE_ONLY
                        or ParcelFileDescriptor.MODE_CREATE
                        or ParcelFileDescriptor.MODE_TRUNCATE)
            } else if ("wa" == mode) {
                (ParcelFileDescriptor.MODE_WRITE_ONLY
                        or ParcelFileDescriptor.MODE_CREATE
                        or ParcelFileDescriptor.MODE_APPEND)
            } else if ("rw" == mode) {
                (ParcelFileDescriptor.MODE_READ_WRITE
                        or ParcelFileDescriptor.MODE_CREATE)
            } else if ("rwt" == mode) {
                (ParcelFileDescriptor.MODE_READ_WRITE
                        or ParcelFileDescriptor.MODE_CREATE
                        or ParcelFileDescriptor.MODE_TRUNCATE)
            } else {
                throw IllegalArgumentException("Invalid mode: $mode")
            }
            return modeBits
        }

        private fun buildPath(base: File, vararg segments: String): File {
            var cur = base
            for (segment in segments) {
                if (segment != null) {
                    cur = File(cur, segment)
                }
            }
            return cur
        }

        private fun copyOf(original: Array<String?>, newLength: Int): Array<String?> {
            val result = arrayOfNulls<String>(newLength)
            System.arraycopy(original, 0, result, 0, newLength)
            return result
        }

        private fun copyOf(original: Array<Any?>, newLength: Int): Array<Any?> {
            val result = arrayOfNulls<Any>(newLength)
            System.arraycopy(original, 0, result, 0, newLength)
            return result
        }
    }
}
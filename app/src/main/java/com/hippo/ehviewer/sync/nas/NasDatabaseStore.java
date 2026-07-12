package com.hippo.ehviewer.sync.nas;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical NAS catalog and file manifest stored inside the exported EhViewer database. */
public final class NasDatabaseStore {
    public static final String MANIFEST_TABLE = "NAS_FILE_MANIFEST";
    public static final String ORDER_TABLE = "NAS_GALLERY_ORDER";
    public static final String META_TABLE = "NAS_SYNC_META";
    private static final int FORMAT_VERSION = 1;

    private NasDatabaseStore() {}

    public static final class ManifestEntry {
        @NonNull public final String path;
        public final long size;
        public final long modified;
        public final int sortOrder;

        public ManifestEntry(@NonNull String path, long size, long modified, int sortOrder) {
            this.path = normalizePath(path);
            this.size = size;
            this.modified = modified;
            this.sortOrder = sortOrder;
        }
    }

    public static final class Snapshot {
        public final long generatedAt;
        @NonNull public final List<NasCatalogEntry> entries;
        @NonNull public final List<ManifestEntry> files;

        Snapshot(long generatedAt, @NonNull List<NasCatalogEntry> entries,
                 @NonNull List<ManifestEntry> files) {
            this.generatedAt = generatedAt;
            this.entries = entries;
            this.files = files;
        }
    }

    @NonNull
    public static Snapshot read(@NonNull File file) throws IOException {
        if (!file.isFile() || file.length() <= 0L) {
            throw new IOException("NAS metadata database not found");
        }
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(file.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            requireTable(database, "DOWNLOADS");
            requireTable(database, "DOWNLOAD_DIRNAME");

            Map<String, Integer> directoryOrder = new HashMap<>();
            if (tableExists(database, "main", ORDER_TABLE)) {
                try (Cursor cursor = database.query(ORDER_TABLE,
                        new String[]{"DIRECTORY", "POSITION"}, null, null, null, null,
                        "POSITION ASC")) {
                    while (cursor.moveToNext()) {
                        directoryOrder.put(safe(cursor.getString(0)).toLowerCase(Locale.ROOT),
                                cursor.getInt(1));
                    }
                }
            }

            List<NasCatalogEntry> entries = new ArrayList<>();
            String query = "SELECT d.GID,d.TOKEN,d.TITLE,d.TITLE_JPN,d.THUMB,d.CATEGORY," +
                    "d.POSTED,d.UPLOADER,d.RATING,d.SIMPLE_LANGUAGE,d.TIME,d.LABEL,n.DIRNAME " +
                    "FROM DOWNLOADS d INNER JOIN DOWNLOAD_DIRNAME n ON n.GID=d.GID";
            try (Cursor cursor = database.rawQuery(query, null)) {
                while (cursor.moveToNext()) {
                    long gid = cursor.getLong(0);
                    String directory = safe(cursor.getString(12));
                    if (gid <= 0L || directory.isEmpty()) continue;
                    entries.add(new NasCatalogEntry(gid, safe(cursor.getString(2)), directory,
                            "download/" + directory, "download/" + directory + "/.thumb",
                            safe(cursor.getString(1)), safe(cursor.getString(3)),
                            safe(cursor.getString(4)), safe(cursor.getString(7)),
                            safe(cursor.getString(6)), safe(cursor.getString(9)),
                            cursor.getInt(5), cursor.getFloat(8), safe(cursor.getString(11)),
                            cursor.getLong(10)));
                }
            }
            Collections.sort(entries, (left, right) -> {
                int leftOrder = directoryOrder.getOrDefault(
                        left.directoryName.toLowerCase(Locale.ROOT), Integer.MAX_VALUE);
                int rightOrder = directoryOrder.getOrDefault(
                        right.directoryName.toLowerCase(Locale.ROOT), Integer.MAX_VALUE);
                int comparison = Integer.compare(leftOrder, rightOrder);
                if (comparison != 0) return comparison;
                comparison = Long.compare(left.time, right.time);
                return comparison != 0 ? comparison
                        : left.directoryName.compareToIgnoreCase(right.directoryName);
            });

            List<ManifestEntry> files = new ArrayList<>();
            if (tableExists(database, "main", MANIFEST_TABLE)) {
                try (Cursor cursor = database.query(MANIFEST_TABLE,
                        new String[]{"PATH", "SIZE", "MODIFIED", "SORT_ORDER"},
                        null, null, null, null, "SORT_ORDER ASC")) {
                    while (cursor.moveToNext()) {
                        String path = normalizePath(safe(cursor.getString(0)));
                        long size = cursor.getLong(1);
                        long modified = cursor.getLong(2);
                        if (!isSafeRelativePath(path) || size < 0L || modified < 0L) {
                            throw new IOException("Invalid NAS database manifest entry: " + path);
                        }
                        files.add(new ManifestEntry(path, size, modified, cursor.getInt(3)));
                    }
                }
            }

            ensureCatalogCoverage(entries, files);
            return new Snapshot(readGeneratedAt(database, file.lastModified()), entries, files);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Unable to read NAS metadata database", error);
        } finally {
            if (database != null) database.close();
        }
    }

    public static void writeManifest(@NonNull File databaseFile,
                                     @NonNull List<ManifestEntry> files,
                                     @NonNull List<String> galleryOrder) throws IOException {
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(databaseFile.getPath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            database.beginTransaction();
            database.execSQL("CREATE TABLE IF NOT EXISTS " + MANIFEST_TABLE +
                    " (PATH TEXT PRIMARY KEY NOT NULL,SIZE INTEGER NOT NULL," +
                    "MODIFIED INTEGER NOT NULL,SORT_ORDER INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS " + ORDER_TABLE +
                    " (DIRECTORY TEXT PRIMARY KEY NOT NULL,POSITION INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS " + META_TABLE +
                    " (KEY TEXT PRIMARY KEY NOT NULL,VALUE TEXT NOT NULL)");
            database.delete(MANIFEST_TABLE, null, null);
            database.delete(ORDER_TABLE, null, null);
            database.delete(META_TABLE, null, null);
            int position = 0;
            Set<String> knownDirectories = new HashSet<>();
            for (String directory : galleryOrder) {
                if (directory == null || directory.trim().isEmpty()) continue;
                String key = directory.toLowerCase(Locale.ROOT);
                if (!knownDirectories.add(key)) continue;
                ContentValues values = new ContentValues();
                values.put("DIRECTORY", directory);
                values.put("POSITION", position++);
                database.insertOrThrow(ORDER_TABLE, null, values);
            }
            for (int i = 0; i < files.size(); i++) {
                ManifestEntry entry = files.get(i);
                if (!isSafeRelativePath(entry.path)) {
                    throw new IOException("Unsafe NAS manifest path: " + entry.path);
                }
                ContentValues values = new ContentValues();
                values.put("PATH", entry.path);
                values.put("SIZE", entry.size);
                values.put("MODIFIED", entry.modified);
                values.put("SORT_ORDER", i);
                database.insertOrThrow(MANIFEST_TABLE, null, values);
            }
            putMeta(database, "format_version", Integer.toString(FORMAT_VERSION));
            putMeta(database, "generated_at", Long.toString(System.currentTimeMillis()));
            database.setTransactionSuccessful();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Unable to update NAS metadata database", error);
        } finally {
            if (database != null) {
                if (database.inTransaction()) database.endTransaction();
                database.close();
            }
        }
        // Re-open through the strict reader so an incomplete database is never uploaded.
        read(databaseFile);
    }

    /** Merges NAS-only records into a fresh local export; local records win on conflicts. */
    public static void mergeRemoteMetadata(@Nullable File remoteDatabase,
                                           @NonNull File localExport) throws IOException {
        if (remoteDatabase == null || !remoteDatabase.isFile()) return;
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(localExport.getPath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            database.execSQL("ATTACH DATABASE " + DatabaseUtils.sqlEscapeString(
                    remoteDatabase.getPath()) + " AS nas_remote");
            database.beginTransaction();
            String[] tables = {
                    "DOWNLOADS", "DOWNLOAD_LABELS", "DOWNLOAD_DIRNAME", "HISTORY",
                    "QUICK_SEARCH", "LOCAL_FAVORITES", "BOOKMARKS", "FILTER",
                    "Black_List", "Gallery_Tags"
            };
            for (String table : tables) mergeMissingRows(database, table);
            database.setTransactionSuccessful();
        } catch (Exception error) {
            throw new IOException("Unable to merge NAS metadata database", error);
        } finally {
            if (database != null) {
                if (database.inTransaction()) database.endTransaction();
                try { database.execSQL("DETACH DATABASE nas_remote"); } catch (Exception ignored) {}
                database.close();
            }
        }
    }

    /** Removes galleries explicitly deleted by the user after merging remote metadata. */
    public static void removeDownloadEntries(@NonNull File databaseFile,
                                             @NonNull java.util.Collection<Long> gids)
            throws IOException {
        if (gids.isEmpty()) return;
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(databaseFile.getPath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            database.beginTransaction();
            for (long gid : gids) {
                String[] args = {Long.toString(gid)};
                if (tableExists(database, "main", "DOWNLOADS")) {
                    database.delete("DOWNLOADS", "GID=?", args);
                }
                if (tableExists(database, "main", "DOWNLOAD_DIRNAME")) {
                    database.delete("DOWNLOAD_DIRNAME", "GID=?", args);
                }
            }
            database.setTransactionSuccessful();
        } catch (Exception error) {
            throw new IOException("Unable to remove deleted NAS metadata", error);
        } finally {
            if (database != null) {
                if (database.inTransaction()) database.endTransaction();
                database.close();
            }
        }
    }

    static boolean isRemoteRevisionConflict(@Nullable String baseHash,
                                            @Nullable String remoteHash) {
        return baseHash != null && !baseHash.isEmpty()
                && (remoteHash == null || !baseHash.equalsIgnoreCase(remoteHash));
    }

    /** Compares logical database content while ignoring SQLite layout and generated timestamps. */
    public static boolean hasSameCanonicalContent(@NonNull File existing,
                                                  @NonNull File candidate)
            throws IOException {
        final String[] tables = {
                "android_metadata", "DOWNLOADS", "DOWNLOAD_LABELS", "DOWNLOAD_DIRNAME",
                "HISTORY", "QUICK_SEARCH", "LOCAL_FAVORITES", "BOOKMARKS", "FILTER",
                "Black_List", "Gallery_Tags", MANIFEST_TABLE, ORDER_TABLE
        };
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(existing.getPath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            database.execSQL("ATTACH DATABASE " + DatabaseUtils.sqlEscapeString(
                    candidate.getPath()) + " AS nas_candidate");
            if (pragmaInt(database, "main", "user_version")
                    != pragmaInt(database, "nas_candidate", "user_version")) return false;
            for (String table : tables) {
                boolean left = tableExists(database, "main", table);
                boolean right = tableExists(database, "nas_candidate", table);
                if (left != right) return false;
                if (!left) continue;
                String quoted = "\"" + table + "\"";
                if (hasDifference(database, "main." + quoted,
                        "nas_candidate." + quoted)
                        || hasDifference(database, "nas_candidate." + quoted,
                        "main." + quoted)) return false;
            }
            return true;
        } catch (Exception error) {
            throw new IOException("Unable to compare NAS metadata databases", error);
        } finally {
            if (database != null) {
                try { database.execSQL("DETACH DATABASE nas_candidate"); } catch (Exception ignored) {}
                database.close();
            }
        }
    }

    private static boolean hasDifference(SQLiteDatabase database, String left, String right) {
        try (Cursor cursor = database.rawQuery("SELECT EXISTS(SELECT * FROM " + left +
                " EXCEPT SELECT * FROM " + right + ")", null)) {
            return cursor.moveToFirst() && cursor.getInt(0) != 0;
        }
    }

    private static int pragmaInt(SQLiteDatabase database, String schema, String name) {
        try (Cursor cursor = database.rawQuery("PRAGMA " + schema + "." + name, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static void mergeMissingRows(SQLiteDatabase database, String table) {
        if (!tableExists(database, "main", table) || !tableExists(database, "nas_remote", table)) {
            return;
        }
        database.execSQL("INSERT OR IGNORE INTO main.\"" + table + "\" SELECT * FROM " +
                "nas_remote.\"" + table + "\"");
    }

    private static void ensureCatalogCoverage(List<NasCatalogEntry> entries,
                                              List<ManifestEntry> files) throws IOException {
        Set<String> catalog = new HashSet<>();
        for (NasCatalogEntry entry : entries) {
            catalog.add(entry.directoryName.toLowerCase(Locale.ROOT));
        }
        Set<String> missing = new HashSet<>();
        for (ManifestEntry file : files) {
            String directory = topDirectory(file.path);
            if (!catalog.contains(directory.toLowerCase(Locale.ROOT))) missing.add(directory);
        }
        if (!missing.isEmpty()) {
            throw new IOException("NAS database has files without gallery metadata: " +
                    missing.iterator().next());
        }
    }

    private static long readGeneratedAt(SQLiteDatabase database, long fallback) {
        if (!tableExists(database, "main", META_TABLE)) return fallback;
        try (Cursor cursor = database.query(META_TABLE, new String[]{"VALUE"}, "KEY=?",
                new String[]{"generated_at"}, null, null, null)) {
            if (cursor.moveToFirst()) return Long.parseLong(cursor.getString(0));
        } catch (Exception ignored) {}
        return fallback;
    }

    private static void putMeta(SQLiteDatabase database, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("KEY", key);
        values.put("VALUE", value);
        database.insertOrThrow(META_TABLE, null, values);
    }

    private static void requireTable(SQLiteDatabase database, String table) throws IOException {
        if (!tableExists(database, "main", table)) {
            throw new IOException("NAS database is missing table: " + table);
        }
    }

    private static boolean tableExists(SQLiteDatabase database, String schema, String table) {
        try (Cursor cursor = database.rawQuery("SELECT 1 FROM " + schema +
                ".sqlite_master WHERE type='table' AND lower(name)=lower(?) LIMIT 1",
                new String[]{table})) {
            return cursor.moveToFirst();
        }
    }

    @NonNull
    private static String safe(String value) { return value != null ? value : ""; }

    @NonNull
    private static String normalizePath(@NonNull String path) {
        return path.replace('\\', '/');
    }

    private static String topDirectory(String path) {
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    static boolean isSafeRelativePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.indexOf('\0') >= 0) return false;
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }
}

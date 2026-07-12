package com.hippo.ehviewer.sync.nas;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.dao.DownloadInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NasCatalogStore {
    private static final int MAGIC = 0x45484E43; // EHNC
    // Version 4 is populated only from the canonical NAS database. Older versions could contain
    // title-only placeholders derived from the removed TSV index and are intentionally ignored.
    private static final int VERSION = 4;
    private static final int MAX_ENTRIES = 100_000;
    private static final String FILE_NAME = "nas_catalog_v1.bin";
    private static final String NAS_MARKER = "ehviewer-nas:";
    private static final String PARTIAL_PREFS = "nas_catalog_partial";
    private static final String HIDDEN_PREFS = "nas_catalog_hidden";
    private static final String FULL_PREFIX = "full_";
    private static volatile Map<Long, NasCatalogEntry> cachedEntries;

    private NasCatalogStore() {}

    public static void save(@NonNull Context context, long generatedAt,
                            @NonNull List<NasCatalogEntry> entries) throws IOException {
        File target = new File(context.getFilesDir(), FILE_NAME);
        File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(generatedAt);
            output.writeInt(entries.size());
            for (NasCatalogEntry entry : entries) {
                output.writeLong(entry.gid);
                output.writeUTF(entry.title);
                output.writeUTF(entry.directoryName);
                output.writeUTF(entry.remoteDirectory);
                output.writeUTF(entry.remoteThumbnail);
                output.writeUTF(entry.token);
                output.writeUTF(entry.titleJpn);
                output.writeUTF(entry.thumb);
                output.writeUTF(entry.uploader);
                output.writeUTF(entry.posted);
                output.writeUTF(entry.simpleLanguage);
                output.writeInt(entry.category);
                output.writeFloat(entry.rating);
                output.writeUTF(entry.label);
                output.writeLong(entry.time);
            }
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Unable to replace NAS catalog");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to store NAS catalog");
        }
        Map<Long, NasCatalogEntry> cache = new LinkedHashMap<>();
        for (NasCatalogEntry entry : entries) cache.put(entry.gid, entry);
        cachedEntries = cache;
    }

    @NonNull
    public static List<NasCatalogEntry> load(@NonNull Context context) {
        Map<Long, NasCatalogEntry> existing = cachedEntries;
        if (existing != null) return new ArrayList<>(existing.values());
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return Collections.emptyList();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new FileInputStream(file)))) {
            if (input.readInt() != MAGIC) {
                return Collections.emptyList();
            }
            int version = input.readInt();
            if (version != VERSION) return Collections.emptyList();
            input.readLong();
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) return Collections.emptyList();
            List<NasCatalogEntry> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long gid = input.readLong();
                String title = input.readUTF();
                String directory = input.readUTF();
                String remoteDirectory = input.readUTF();
                String remoteThumbnail = input.readUTF();
                result.add(new NasCatalogEntry(gid, title, directory, remoteDirectory,
                        remoteThumbnail, input.readUTF(), input.readUTF(), input.readUTF(),
                        input.readUTF(), input.readUTF(), input.readUTF(), input.readInt(),
                        input.readFloat(), input.readUTF(), input.readLong()));
            }
            Map<Long, NasCatalogEntry> cache = new LinkedHashMap<>();
            for (NasCatalogEntry entry : result) cache.put(entry.gid, entry);
            cachedEntries = cache;
            return result;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static NasCatalogEntry find(@NonNull Context context, long gid) {
        Map<Long, NasCatalogEntry> entries = cachedEntries;
        if (entries == null) {
            load(context);
            entries = cachedEntries;
        }
        return entries != null ? entries.get(gid) : null;
    }

    public static synchronized void upsertFromDownload(@NonNull Context context,
                                                       @NonNull DownloadInfo info,
                                                       @NonNull String directoryName)
            throws IOException {
        List<NasCatalogEntry> entries = load(context);
        Map<Long, NasCatalogEntry> merged = new LinkedHashMap<>();
        for (NasCatalogEntry entry : entries) merged.put(entry.gid, entry);
        NasCatalogEntry previous = merged.get(info.gid);
        String remoteThumbnail = previous != null ? previous.remoteThumbnail
                : "download/" + directoryName + "/.thumb";
        merged.put(info.gid, new NasCatalogEntry(info.gid, safe(info.title), directoryName,
                "download/" + directoryName, remoteThumbnail, safe(info.token),
                safe(info.titleJpn), safe(info.thumb), safe(info.uploader), safe(info.posted),
                safe(info.simpleLanguage), info.category, info.rating, safe(info.label),
                info.time));
        save(context, System.currentTimeMillis(), new ArrayList<>(merged.values()));
    }

    public static synchronized void mergeLocalDownloads(@NonNull Context context,
                                                         @NonNull List<DownloadInfo> downloads)
            throws IOException {
        Map<Long, NasCatalogEntry> merged = new LinkedHashMap<>();
        for (NasCatalogEntry entry : load(context)) merged.put(entry.gid, entry);
        List<DownloadInfo> oldestFirst = new ArrayList<>(downloads);
        Collections.reverse(oldestFirst);
        for (DownloadInfo info : oldestFirst) {
            String directoryName = com.hippo.ehviewer.EhDB.getDownloadDirname(info.gid);
            if (directoryName == null || directoryName.isEmpty()) continue;
            NasCatalogEntry previous = merged.get(info.gid);
            String remoteThumbnail = previous != null ? previous.remoteThumbnail
                    : "download/" + directoryName + "/.thumb";
            merged.put(info.gid, new NasCatalogEntry(info.gid, safe(info.title), directoryName,
                    "download/" + directoryName, remoteThumbnail, safe(info.token),
                    safe(info.titleJpn), safe(info.thumb), safe(info.uploader), safe(info.posted),
                    safe(info.simpleLanguage), info.category, info.rating, safe(info.label),
                    info.time));
        }
        save(context, System.currentTimeMillis(), new ArrayList<>(merged.values()));
    }

    public static synchronized void renameLabel(@NonNull Context context,
                                                @NonNull String from,
                                                @NonNull String to) throws IOException {
        rewriteLabel(context, from, to);
    }

    public static synchronized void deleteLabel(@NonNull Context context,
                                                @NonNull String label) throws IOException {
        rewriteLabel(context, label, "");
    }

    /** Updates labels/order edited from the download list without touching NAS image files. */
    public static synchronized void updateFromDownloads(@NonNull Context context,
                                                        @NonNull List<DownloadInfo> downloads)
            throws IOException {
        Map<Long, DownloadInfo> updates = new HashMap<>();
        for (DownloadInfo info : downloads) updates.put(info.gid, info);
        List<NasCatalogEntry> entries = load(context);
        boolean changed = false;
        List<NasCatalogEntry> rewritten = new ArrayList<>(entries.size());
        for (NasCatalogEntry entry : entries) {
            DownloadInfo info = updates.get(entry.gid);
            if (info == null) {
                rewritten.add(entry);
                continue;
            }
            String label = safe(info.label);
            if (!label.equals(entry.label) || info.time != entry.time) changed = true;
            rewritten.add(copyWithLabelAndTime(entry, label, info.time));
        }
        if (changed) {
            // The catalog is stored oldest-first and reversed for display.
            Collections.sort(rewritten, (left, right) -> Long.compare(left.time, right.time));
            save(context, System.currentTimeMillis(), rewritten);
        }
    }

    public static synchronized void remove(@NonNull Context context, long gid)
            throws IOException {
        List<NasCatalogEntry> entries = load(context);
        List<NasCatalogEntry> rewritten = new ArrayList<>(entries.size());
        for (NasCatalogEntry entry : entries) {
            if (entry.gid != gid) rewritten.add(entry);
        }
        if (rewritten.size() != entries.size()) {
            save(context, System.currentTimeMillis(), rewritten);
        }
        showLocally(context, gid);
        markRemoteOnly(context, gid);
    }

    private static void rewriteLabel(Context context, String from, String to) throws IOException {
        List<NasCatalogEntry> entries = load(context);
        boolean changed = false;
        List<NasCatalogEntry> rewritten = new ArrayList<>(entries.size());
        for (NasCatalogEntry entry : entries) {
            if (from.equals(entry.label)) {
                rewritten.add(copyWithLabel(entry, to));
                changed = true;
            } else {
                rewritten.add(entry);
            }
        }
        if (changed) save(context, System.currentTimeMillis(), rewritten);
    }

    private static NasCatalogEntry copyWithLabel(NasCatalogEntry entry, String label) {
        return copyWithLabelAndTime(entry, label, entry.time);
    }

    private static NasCatalogEntry copyWithLabelAndTime(NasCatalogEntry entry, String label,
                                                        long time) {
        return new NasCatalogEntry(entry.gid, entry.title, entry.directoryName,
                entry.remoteDirectory, entry.remoteThumbnail, entry.token, entry.titleJpn,
                entry.thumb, entry.uploader, entry.posted, entry.simpleLanguage,
                entry.category, entry.rating, label, time);
    }

    private static String safe(String value) { return value != null ? value : ""; }

    public static void markPartiallyCached(@NonNull Context context, long gid) {
        context.getSharedPreferences(PARTIAL_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(Long.toString(gid), true).apply();
    }

    public static boolean isPartiallyCached(@NonNull Context context, long gid) {
        return context.getSharedPreferences(PARTIAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(Long.toString(gid), false);
    }

    public static void markFullyCached(@NonNull Context context,
                                       @NonNull List<NasCatalogEntry> entries) {
        android.content.SharedPreferences.Editor editor = context
                .getSharedPreferences(PARTIAL_PREFS, Context.MODE_PRIVATE).edit();
        for (NasCatalogEntry entry : entries) {
            editor.putBoolean(Long.toString(entry.gid), true);
            editor.putBoolean(FULL_PREFIX + entry.gid, true);
        }
        editor.apply();
    }

    public static void markRemoteOnly(@NonNull Context context, long gid) {
        context.getSharedPreferences(PARTIAL_PREFS, Context.MODE_PRIVATE).edit()
                .remove(Long.toString(gid))
                .remove(FULL_PREFIX + gid)
                .apply();
    }

    public static void hideLocally(@NonNull Context context, long gid) {
        context.getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(Long.toString(gid), true).apply();
    }

    public static void showLocally(@NonNull Context context, long gid) {
        context.getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE).edit()
                .remove(Long.toString(gid)).apply();
    }

    public static boolean isHiddenLocally(@NonNull Context context, long gid) {
        return context.getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE)
                .getBoolean(Long.toString(gid), false);
    }

    public static boolean isFullyCached(@NonNull Context context, long gid) {
        return context.getSharedPreferences(PARTIAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(FULL_PREFIX + gid, false);
    }

    @NonNull
    public static DownloadInfo asNasOnlyDownload(@NonNull NasCatalogEntry entry) {
        DownloadInfo info = new DownloadInfo(entry.gid);
        info.title = entry.title;
        info.titleJpn = entry.titleJpn;
        info.token = entry.token;
        info.thumb = entry.thumb;
        info.uploader = entry.uploader;
        info.posted = entry.posted;
        info.simpleLanguage = entry.simpleLanguage;
        info.category = entry.category;
        info.rating = entry.rating;
        info.label = entry.label.isEmpty() ? null : entry.label;
        info.state = DownloadInfo.STATE_NAS_ONLY;
        info.time = entry.time;
        info.archiveUri = markerFor(entry.directoryName);
        return info;
    }

    public static boolean isNasOnly(DownloadInfo info) {
        return info != null && info.state == DownloadInfo.STATE_NAS_ONLY
                && info.archiveUri != null && info.archiveUri.startsWith(NAS_MARKER);
    }

    @NonNull
    private static String markerFor(@NonNull String directoryName) {
        return NAS_MARKER + Base64.encodeToString(directoryName.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}

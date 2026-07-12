package com.hippo.ehviewer.sync.nas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.unifile.UniFile;
import com.hippo.widget.LoadImageView;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Iterator;

public final class NasThumbnailLoader {
    private static final int MAX_WORKERS = 3;
    private static final int BATCH_SIZE = 12;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(MAX_WORKERS);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Long, PendingThumbnail> PENDING = new LinkedHashMap<>();
    private static int activeWorkers;

    private NasThumbnailLoader() {}

    public static void load(@NonNull Context context, @NonNull LoadImageView view,
                            @NonNull NasCatalogEntry entry, String onlineUrl) {
        if (!NasConfigStore.isEnabled(context)) {
            view.load(R.drawable.image_failed);
            return;
        }
        view.setTag(R.id.thumb, entry.gid);
        UniFile localThumbnail = findLocalThumbnail(entry);
        if (localThumbnail != null) {
            Bitmap bitmap = decode(localThumbnail);
            if (bitmap != null) {
                setStaticBitmap(view, bitmap);
                return;
            }
            // Never keep a zero-byte or undecodable final thumbnail around. It otherwise causes
            // ImageDecoder failures on every RecyclerView bind.
            localThumbnail.delete();
        }
        File cached = NasCatalogClient.getCachedThumbnail(context, entry.gid);
        if (cached.isFile()) {
            Bitmap bitmap = decode(cached);
            if (bitmap != null) {
                setStaticBitmap(view, bitmap);
                return;
            }
            cached.delete();
        }
        view.load(R.drawable.image_failed);
        synchronized (PENDING) {
            PendingThumbnail pending = PENDING.get(entry.gid);
            if (pending == null) {
                pending = new PendingThumbnail(entry, onlineUrl);
                PENDING.put(entry.gid, pending);
            } else if ((pending.onlineUrl == null || pending.onlineUrl.isEmpty())
                    && onlineUrl != null && !onlineUrl.isEmpty()) {
                pending.onlineUrl = onlineUrl;
            }
            pending.views.add(new WeakReference<>(view));
            if (activeWorkers < MAX_WORKERS) {
                activeWorkers++;
                Context applicationContext = context.getApplicationContext();
                EXECUTOR.execute(() -> drain(applicationContext));
            }
        }
    }

    private static void drain(Context context) {
        while (true) {
            List<PendingThumbnail> batch;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) {
                    activeWorkers--;
                    return;
                }
                batch = new ArrayList<>(Math.min(BATCH_SIZE, PENDING.size()));
                Iterator<Map.Entry<Long, PendingThumbnail>> iterator =
                        PENDING.entrySet().iterator();
                while (iterator.hasNext() && batch.size() < BATCH_SIZE) {
                    batch.add(iterator.next().getValue());
                    iterator.remove();
                }
            }
            List<NasCatalogEntry> entries = new ArrayList<>(batch.size());
            Map<Long, PendingThumbnail> callbacks = new LinkedHashMap<>();
            for (PendingThumbnail pending : batch) {
                entries.add(pending.entry);
                callbacks.put(pending.entry.gid, pending);
            }
            if (!NasConfigStore.isEnabled(context)) continue;
            NasSyncConfig config = NasConfigStore.load(context);
            try {
                new NasCatalogClient(config).fetchThumbnailBatch(context, entries,
                        (entry, file) -> deliver(callbacks.get(entry.gid), file));
            } catch (Exception ignored) {
                config.clearPassword();
            }
            for (PendingThumbnail pending : batch) {
                if (!pending.delivered) fallbackOnline(pending);
            }
        }
    }

    private static void deliver(PendingThumbnail pending, File file) {
        if (pending == null) return;
        Bitmap bitmap = decode(file);
        if (bitmap == null) {
            file.delete();
            return;
        }
        pending.delivered = true;
        MAIN.post(() -> {
            for (WeakReference<LoadImageView> reference : pending.views) {
                LoadImageView view = reference.get();
                if (view == null) continue;
                Object tag = view.getTag(R.id.thumb);
                if (tag instanceof Long && ((Long) tag) == pending.entry.gid) {
                    setStaticBitmap(view, bitmap);
                }
            }
        });
    }

    private static void fallbackOnline(@NonNull PendingThumbnail pending) {
        if (pending.onlineUrl == null || pending.onlineUrl.isEmpty()) return;
        MAIN.post(() -> {
            for (WeakReference<LoadImageView> reference : pending.views) {
                LoadImageView view = reference.get();
                if (view == null) continue;
                Object tag = view.getTag(R.id.thumb);
                if (tag instanceof Long && ((Long) tag) == pending.entry.gid) {
                    view.load(EhCacheKeyFactory.getThumbKey(pending.entry.gid),
                            pending.onlineUrl, true);
                }
            }
        });
    }

    private static void setStaticBitmap(@NonNull LoadImageView view, @NonNull Bitmap bitmap) {
        // LoadImageView clears network-loaded drawables whenever a RecyclerView row detaches.
        // Using its drawable API cancels an old recycled-row request and marks this bitmap as a
        // stable local drawable, so scrolling off-screen does not erase it on reattach.
        view.load(new BitmapDrawable(view.getResources(), bitmap));
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 900) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static Bitmap decode(@NonNull UniFile file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = file.openInputStream()) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 900) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream input = file.openInputStream()) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UniFile findLocalThumbnail(@NonNull NasCatalogEntry entry) {
        UniFile root = Settings.getDownloadLocation();
        if (root == null) return null;
        UniFile directory = root.findFile(entry.directoryName);
        if (directory == null || !directory.isDirectory()) return null;
        UniFile thumbnail = directory.findFile(".thumb");
        return thumbnail != null && thumbnail.isFile() ? thumbnail : null;
    }

    private static final class PendingThumbnail {
        final NasCatalogEntry entry;
        final List<WeakReference<LoadImageView>> views = new ArrayList<>();
        String onlineUrl;
        volatile boolean delivered;

        PendingThumbnail(NasCatalogEntry entry, String onlineUrl) {
            this.entry = entry;
            this.onlineUrl = onlineUrl;
        }
    }
}

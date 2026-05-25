package com.hippo.ehviewer.widget;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.download.ArchiverDownloadCompleter;

import java.util.Locale;

public class ArchiverDownloadProgress extends LinearLayout {
    final Context context;
    private TextView myTextView;
    private ProgressBar myProgressBar;

    private boolean showing;

    private String reasonString = "Unknown";

    public ArchiverDownloadProgress(Context context) {
        super(context);
        this.context = context;
        init(context);
    }


    public ArchiverDownloadProgress(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context);
    }

    public ArchiverDownloadProgress(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init(context);
    }

    public ArchiverDownloadProgress(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.widget_archiver_progress, this);
        myTextView = findViewById(R.id.archiver_downloading);
        myProgressBar = findViewById(R.id.archiver_progress);
    }

    public void initThread(GalleryInfo galleryInfo) {
        if (galleryInfo == null) {
            return;
        }
        if (showing) {
            return;
        }
        long dId = Settings.getArchiverDownloadId(galleryInfo.gid);
        if (dId == -1L) {
            return;
        }
        showing = true;
        setVisibility(VISIBLE);
        myTextView.setText(context.getString(R.string.archiver_downloading, "0%"));
        myProgressBar.setProgress(0);

        ArchiverDownloadCompleter completer = ArchiverDownloadCompleter.getInstance(context);
        if (completer != null) {
            completer.ensureReceiverRegistered();
        }

        new Thread(() -> {
            DownloadManager downloadManager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                postDone();
                return;
            }
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(dId);
            try {
                while (true) {
                    try (Cursor cursor = downloadManager.query(query)) {
                        if (cursor == null || !cursor.moveToFirst()) {
                            break;
                        }
                        int state = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (state == DownloadManager.STATUS_SUCCESSFUL) {
                            if (completer != null) {
                                completer.checkAndHandleStatus(dId);
                            }
                            break;
                        }
                        if (state == DownloadManager.STATUS_FAILED) {
                            if (completer != null) {
                                completer.checkAndHandleStatus(dId);
                            }
                            break;
                        }
                        if (state == DownloadManager.STATUS_PAUSED) {
                            int reason = cursor.getInt(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                            switch (reason) {
                                case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                                    reasonString = "Waiting for WiFi";
                                    break;
                                case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                                    reasonString = "Waiting for connectivity";
                                    break;
                                case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                    reasonString = "Waiting to retry";
                                    break;
                                default:
                                    break;
                            }
                            post(() -> Toast.makeText(context, reasonString, Toast.LENGTH_LONG).show());
                            Thread.sleep(6000);
                            continue;
                        }
                        long downloaded = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        long total = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        double progress = total > 0 ? downloaded * 100d / total : 0d;
                        String result = String.format(Locale.getDefault(), "%.2f", progress) + "%";
                        myTextView.post(() -> myTextView.setText(
                                context.getString(R.string.archiver_downloading, result)));
                        myProgressBar.post(() -> myProgressBar.setProgress((int) progress));
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                myTextView.post(() -> myTextView.setText(R.string.download_state_failed));
            } catch (RuntimeException e) {
                myTextView.post(() -> myTextView.setText(R.string.download_state_failed));
                e.printStackTrace();
            } finally {
                postDone();
            }
        }).start();
    }

    private void postDone() {
        post(() -> {
            setVisibility(GONE);
            showing = false;
        });
    }
}

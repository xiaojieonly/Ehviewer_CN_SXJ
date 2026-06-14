package com.hippo.ehviewer.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.smb.SmbBackupService;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class SmbBackupProgressActivity extends ToolbarActivity {

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = this::refreshUi;

    @Nullable
    private TextView mStatusView;
    @Nullable
    private TextView mModeView;
    @Nullable
    private TextView mSpeedView;
    @Nullable
    private android.widget.ProgressBar mProgressBar;
    @Nullable
    private MaterialButton mCancelButton;
    @Nullable
    private MaterialButton mCloseButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smb_backup_progress);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        setTitle(R.string.settings_download_smb_backup_syncing);

        mStatusView = findViewById(R.id.smb_backup_status);
        mModeView = findViewById(R.id.smb_backup_mode);
        mSpeedView = findViewById(R.id.smb_backup_speed);
        mProgressBar = findViewById(R.id.smb_backup_progress_bar);
        mCancelButton = findViewById(R.id.smb_backup_cancel);
        mCloseButton = findViewById(R.id.smb_backup_close);

        if (mCancelButton != null) {
            mCancelButton.setOnClickListener(v -> {
                SmbBackupService.cancel(this);
                refreshUi();
            });
        }
        if (mCloseButton != null) {
            mCloseButton.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshUi();
    }

    @Override
    protected void onStop() {
        mHandler.removeCallbacks(mRefreshRunnable);
        super.onStop();
    }

    private void refreshUi() {
        SmbBackupService.ProgressSnapshot snapshot = SmbBackupService.getProgressSnapshot();
        boolean running = SmbBackupService.isRunning();

        if (snapshot == null) {
            if (!running) {
                finish();
                return;
            }
            if (mStatusView != null) {
                mStatusView.setText(getString(R.string.settings_download_smb_backup_scanning));
            }
            if (mModeView != null) {
                mModeView.setText("");
            }
            if (mSpeedView != null) {
                mSpeedView.setText("");
            }
            if (mProgressBar != null) {
                mProgressBar.setIndeterminate(true);
                mProgressBar.setProgress(0);
            }
        } else {
            if (mStatusView != null) {
                mStatusView.setText(String.format(Locale.US, "%s (%d/%d)",
                        snapshot.text, snapshot.current, snapshot.total));
            }
            if (mModeView != null) {
                mModeView.setText(snapshot.aggressive
                        ? getString(R.string.settings_download_smb_backup_sync_aggressive)
                        : getString(R.string.settings_download_smb_backup_sync_normal));
            }
            if (mSpeedView != null) {
                mSpeedView.setText(formatSpeed(snapshot.speedBps));
            }
            if (mProgressBar != null) {
                mProgressBar.setIndeterminate(snapshot.total <= 0);
                if (snapshot.total > 0) {
                    mProgressBar.setMax(snapshot.total);
                    mProgressBar.setProgress(snapshot.current);
                }
            }
        }

        if (mCancelButton != null) {
            mCancelButton.setEnabled(running);
        }

        mHandler.removeCallbacks(mRefreshRunnable);
        if (running) {
            mHandler.postDelayed(mRefreshRunnable, 500L);
        }
    }

    private String formatSpeed(int speedBps) {
        if (speedBps <= 0) {
            return "";
        }
        if (speedBps > 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB/s", speedBps / (1024.0 * 1024.0));
        }
        if (speedBps > 1024) {
            return String.format(Locale.US, "%.1f KB/s", speedBps / 1024.0);
        }
        return String.format(Locale.US, "%d B/s", speedBps);
    }
}

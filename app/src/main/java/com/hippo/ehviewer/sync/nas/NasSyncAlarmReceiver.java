package com.hippo.ehviewer.sync.nas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class NasSyncAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            NasSyncScheduler.update(context);
            return;
        }
        try {
            if (!NasConfigStore.isEnabled(context) || !NasConfigStore.isConfigured()
                    || !NasConfigStore.isScheduleEnabled()) return;
            String mode = NasConfigStore.getScheduleMode();
            if (NasConfigStore.SCHEDULE_UPLOAD.equals(mode)) {
                NasSyncService.start(context, NasSyncService.ACTION_UPLOAD_ALL);
            } else if (NasConfigStore.SCHEDULE_DOWNLOAD.equals(mode)) {
                NasSyncService.start(context, NasSyncService.ACTION_DOWNLOAD_ALL);
            } else {
                NasSyncService.start(context, NasSyncService.ACTION_MERGE);
            }
        } catch (RuntimeException error) {
            Log.e("NasSyncAlarm", "Unable to start scheduled NAS sync", error);
        } finally {
            NasSyncScheduler.update(context);
        }
    }
}

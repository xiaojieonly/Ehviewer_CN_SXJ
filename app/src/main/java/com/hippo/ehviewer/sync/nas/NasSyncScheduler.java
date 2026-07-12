package com.hippo.ehviewer.sync.nas;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public final class NasSyncScheduler {
    private static final int REQUEST_CODE = 0x4e4153;

    private NasSyncScheduler() {}

    public static void update(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        PendingIntent operation = PendingIntent.getBroadcast(app, REQUEST_CODE,
                new Intent(app, NasSyncAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(operation);
        if (!NasConfigStore.isEnabled(app) || !NasConfigStore.isConfigured()
                || !NasConfigStore.isScheduleEnabled()) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, NasConfigStore.getScheduleHour());
        next.set(Calendar.MINUTE, NasConfigStore.getScheduleMinute());
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    next.getTimeInMillis(), operation);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), operation);
        } else {
            alarms.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), operation);
        }
    }
}

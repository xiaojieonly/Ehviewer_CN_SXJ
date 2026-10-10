package com.hippo.ehviewer.server.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.server.model.ServerState;
import com.hippo.ehviewer.server.util.ServerLog;
import com.hippo.ehviewer.server.util.ServerSettings;
import com.hippo.ehviewer.ui.SettingsActivity;

public class LanServerService extends Service {

    public static final String ACTION_START = "com.hippo.ehviewer.server.START";
    public static final String ACTION_STOP = "com.hippo.ehviewer.server.STOP";
    public static final String ACTION_RESTART = "com.hippo.ehviewer.server.RESTART";

    public static final String CHANNEL_ID = "ehviewer_lan_server";
    private static final int NOTIFY_ID = 4112;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopServerAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_RESTART.equals(action)) {
            ServerController.get(this).restart();
        } else {
            ServerController.get(this).start();
        }

        startForeground(NOTIFY_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ServerController.get(this).stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startServer(@NonNull Context context) {
        Intent intent = new Intent(context, LanServerService.class);
        intent.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stopServer(@NonNull Context context) {
        Intent intent = new Intent(context, LanServerService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static void restartServer(@NonNull Context context) {
        Intent intent = new Intent(context, LanServerService.class);
        intent.setAction(ACTION_RESTART);
        ContextCompat.startForegroundService(context, intent);
    }

    private void stopServerAndSelf() {
        ServerSettings.setEnabled(false);
        ServerController.get(this).stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.server_notification_channel_desc));
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        ServerState state = ServerController.get(this).getState();

        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        PendingIntent settingsPending = PendingIntent.getActivity(
                this,
                1001,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, LanServerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1002,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = getString(R.string.server_notification_title);
        String text;
        if (state.running && state.boundPort > 0) {
            text = getString(R.string.server_notification_running, state.boundPort);
        } else {
            text = getString(R.string.server_notification_stopped);
            if (!state.lastError.isEmpty()) {
                ServerLog.e("Service state error: " + state.lastError);
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.v_download_dark_x24)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(settingsPending)
                .addAction(new NotificationCompat.Action(
                        R.drawable.v_clear_all_dark_x24,
                        getString(R.string.server_notification_stop_action),
                        stopPending))
                .build();
    }
}

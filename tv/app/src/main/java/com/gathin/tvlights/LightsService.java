package com.gathin.tvlights;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * The foreground service that keeps the connections to the lights and runs them (LightsController). The launcher's
 * palette lines reach it through PaletteReceiver, commands from outside through CommandReceiver; the screen reaches it
 * with the same actions. The notification is the
 * quietest Android allows: no sound, no icon in the status bar on most TVs, one line of state.
 */
public final class LightsService extends Service implements LightsController.Listener {
    static final String ACTION_PALETTE = "com.gathin.tvlights.PALETTE";
    /** A command for the lights, from the screen or (through CommandReceiver) from outside: see EXTRA_CMD. */
    static final String ACTION_COMMAND = "com.gathin.tvlights.COMMAND";
    /** Just be running (the screen opened, the TV started, the add-on was updated). */
    static final String ACTION_ENSURE = "com.gathin.tvlights.ENSURE";
    static final String EXTRA_COLORS = "colors";
    /** toggle, on, off, scene; and from the screen only: look, level, follow. */
    static final String EXTRA_CMD = "cmd";
    /** A light's id in lights.json, or "all". */
    static final String EXTRA_LIGHT = "light";
    /** With scene: all_on, all_off, evening or cinema. */
    static final String EXTRA_SCENE = "scene";
    /** With look, level and follow: the value. */
    static final String EXTRA_VALUE = "value";

    private static final String CHANNEL = "lights";
    private static final int NOTIFICATION = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LightsController controller;
    private int shownLink = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(NOTIFICATION, notification());
        controller = new LightsController(this, this);
        controller.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION, notification());   // a start through startForegroundService must be answered
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PALETTE.equals(action)) {
            controller.onPalette(intent.getStringExtra(EXTRA_COLORS));
        } else if (ACTION_COMMAND.equals(action)) {
            String cmd = intent.getStringExtra(EXTRA_CMD);
            controller.onCommand(cmd, intent.getStringExtra(EXTRA_LIGHT),
                    intent.getStringExtra("scene".equals(cmd) ? EXTRA_SCENE : EXTRA_VALUE));
        }
        return START_STICKY;
    }

    @Override
    public void onStatus() {
        main.post(this::refreshNotification);
    }

    private void refreshNotification() {
        if (shownLink == LightsStatus.link) return;
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
    }

    private Notification notification() {
        shownLink = LightsStatus.link;
        int text;
        switch (shownLink) {
            case LightsStatus.CONNECTED:
                text = R.string.note_connected;
                break;
            case LightsStatus.UNREACHABLE:
                text = R.string.note_unreachable;
                break;
            case LightsStatus.NO_CONFIG:
                text = R.string.note_no_config;
                break;
            default:
                text = R.string.note_connecting;
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(text))
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .build();
    }

    @Override
    public void onDestroy() {
        if (controller != null) controller.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

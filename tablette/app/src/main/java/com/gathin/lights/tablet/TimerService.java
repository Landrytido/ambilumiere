package com.gathin.lights.tablet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs only while a switch-off is armed: a minimal notification that keeps the app alive until the alarm, and the
 * switch-off itself when the alarm goes off. It stops as soon as nothing is armed.
 */
public class TimerService extends Service {
    static final String ACT_FIRE = "com.gathin.lights.tablet.FIRE";
    private static final String CHANNEL = "timer";
    private static final int NOTIFICATION = 1;

    /** Makes the service run (or stop) to match what is armed; for the screen, which is in front. */
    static void refresh(Context c) {
        if (!TimerStore.armed(c)) {
            c.stopService(new Intent(c, TimerService.class));
            return;
        }
        try {
            c.startForegroundService(new Intent(c, TimerService.class));
        } catch (RuntimeException ignored) {
            // The alarm still works without the service; only the notification is missing.
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean fire = intent != null && ACT_FIRE.equals(intent.getAction());
        show(fire ? "Extinction des lumières…" : text());
        if (fire) {
            String which = intent.getStringExtra("which");
            new Thread(() -> switchOff(which), "timer-off").start();
        } else if (!TimerStore.armed(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void switchOff(String which) {
        Hub hub = Hub.get(this);
        CountDownLatch done = new CountDownLatch(1);
        hub.acquire();
        hub.allPower(false);
        hub.release(done::countDown);
        try {
            done.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        TimerStore.afterFire(this, which);
        if (TimerStore.armed(this)) {
            show(text());
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private String text() {
        long at = TimerStore.nextEvent(this);
        if (at == 0) return "Extinction programmée";
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(at);
        return String.format(Locale.FRANCE, "Les lumières s’éteindront à %02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    private void show(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Minuteur", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bulb)
                .setContentTitle("Lumières")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, n);
    }
}

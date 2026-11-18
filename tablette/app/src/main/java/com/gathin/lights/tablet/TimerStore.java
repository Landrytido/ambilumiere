package com.gathin.lights.tablet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * The two ways to switch everything off by itself: a countdown ("in 30 minutes") and a time every day. What is armed is kept
 * in the app's preferences and set up with AlarmManager.setAlarmClock, which needs no special permission on Android 14
 * and is the alarm the system is most careful to deliver on time. The foreground service (TimerService) runs only while
 * something is armed.
 */
final class TimerStore {
    static final String ACT_COUNTDOWN = "com.gathin.lights.tablet.COUNTDOWN", ACT_DAILY = "com.gathin.lights.tablet.DAILY";
    static final int DEFAULT_DAILY_MINUTES = 23 * 60;

    private TimerStore() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    /** When the countdown ends (epoch ms), or 0 when there is none. */
    static long countdownEnd(Context c) {
        long end = p(c).getLong("end", 0);
        return end > System.currentTimeMillis() ? end : 0;
    }

    static boolean dailyOn(Context c) {
        return p(c).getBoolean("daily_on", false);
    }

    static int dailyMinutes(Context c) {
        return p(c).getInt("daily_min", DEFAULT_DAILY_MINUTES);
    }

    static boolean armed(Context c) {
        return countdownEnd(c) > 0 || dailyOn(c);
    }

    /** When the next daily switch-off happens (epoch ms): today at that time if it is still ahead, else tomorrow. */
    static long nextDaily(long now, int minutesOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        cal.set(Calendar.MINUTE, minutesOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    static void startCountdown(Context c, int minutes) {
        long end = System.currentTimeMillis() + minutes * 60_000L;
        p(c).edit().putLong("end", end).apply();
        schedule(c, ACT_COUNTDOWN, end);
        TimerService.refresh(c);
    }

    static void cancelCountdown(Context c) {
        p(c).edit().putLong("end", 0).apply();
        unschedule(c, ACT_COUNTDOWN);
        TimerService.refresh(c);
    }

    static void setDaily(Context c, boolean on, int minutes) {
        p(c).edit().putBoolean("daily_on", on).putInt("daily_min", minutes).apply();
        if (on) schedule(c, ACT_DAILY, nextDaily(System.currentTimeMillis(), minutes));
        else unschedule(c, ACT_DAILY);
        TimerService.refresh(c);
    }

    /** The app started: the system forgets alarms when the tablet restarts, so what is armed is set up again. */
    static void rearm(Context c) {
        long end = countdownEnd(c);
        if (end > 0) schedule(c, ACT_COUNTDOWN, end);
        else p(c).edit().putLong("end", 0).apply();
        if (dailyOn(c)) schedule(c, ACT_DAILY, nextDaily(System.currentTimeMillis(), dailyMinutes(c)));
        if (armed(c)) TimerService.refresh(c);
    }

    /** An alarm went off and the lights are off: forget the countdown, plan tomorrow's switch-off. */
    static void afterFire(Context c, String action) {
        if (ACT_COUNTDOWN.equals(action)) {
            p(c).edit().putLong("end", 0).apply();
        } else if (ACT_DAILY.equals(action) && dailyOn(c)) {
            schedule(c, ACT_DAILY, nextDaily(System.currentTimeMillis() + 60_000, dailyMinutes(c)));
        }
    }

    /** The time of the next switch-off of either kind, or 0. */
    static long nextEvent(Context c) {
        long a = countdownEnd(c);
        long b = dailyOn(c) ? nextDaily(System.currentTimeMillis(), dailyMinutes(c)) : 0;
        if (a == 0) return b;
        if (b == 0) return a;
        return Math.min(a, b);
    }

    private static PendingIntent pending(Context c, String action) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        int code = ACT_COUNTDOWN.equals(action) ? 1 : 2;
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void schedule(Context c, String action, long when) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent show = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pending(c, action));
    }

    private static void unschedule(Context c, String action) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pending(c, action));
    }
}

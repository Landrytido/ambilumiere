package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

/**
 * Exists so the launcher can read media sessions (what is playing, see NowPlaying): Android
 * gives that to apps with notification access. Granted over adb
 * (cmd notification allow_listener); the launcher never reads or touches notifications.
 * It also feeds the lights (LightsPalette) from here, so they follow the song whichever screen is up,
 * not only while Home or the screensaver is showing.
 * Adapted from majnioui/CleanXiaomiTV (MIT).
 */
public class MediaListener extends NotificationListenerService {
    private NowPlaying music;

    @Override
    public void onListenerConnected() {
        if (music == null) {
            music = new NowPlaying(this, new Handler(Looper.getMainLooper()), track -> LightsPalette.publish(this, track));
        }
        music.start();
    }

    @Override
    public void onListenerDisconnected() {
        if (music != null) music.stop();
        LightsPalette.publish(this, null);
    }
}

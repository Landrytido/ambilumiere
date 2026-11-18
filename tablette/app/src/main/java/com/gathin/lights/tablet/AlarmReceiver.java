package com.gathin.lights.tablet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The system's alarm for a switch-off: handed to the timer service, which does the work. Not reachable by other apps. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!TimerStore.ACT_COUNTDOWN.equals(action) && !TimerStore.ACT_DAILY.equals(action)) return;
        try {
            context.startForegroundService(new Intent(context, TimerService.class).setAction(TimerService.ACT_FIRE).putExtra("which", action));
        } catch (RuntimeException e) {
            // The system refused a service start: do the switch-off here, as far as this broadcast's time allows.
            Hub hub = Hub.get(context);
            hub.acquire();
            hub.allPower(false);
            TimerStore.afterFire(context, action);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(hub::release, 8_000);
        }
    }
}

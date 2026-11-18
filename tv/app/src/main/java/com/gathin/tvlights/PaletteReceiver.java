package com.gathin.tvlights;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Wakes the service: for a palette line from the launcher (action com.gathin.tvlights.PALETTE, extra "colors" with
 * "#RRGGBB,#RRGGBB,..." or "off", what the launcher also writes to the log), and when the TV starts or the add-on is
 * updated, so the connection to the strip is there before the first song. A line that isn't a palette is dropped here.
 */
public final class PaletteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Intent service = new Intent(context, LightsService.class);
        if (LightsService.ACTION_PALETTE.equals(action)) {
            String colors = intent.getStringExtra(LightsService.EXTRA_COLORS);
            if (!LightsController.isOff(colors) && LightsController.parsePalette(colors) == null) return;
            service.setAction(LightsService.ACTION_PALETTE).putExtra(LightsService.EXTRA_COLORS, colors);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            service.setAction(LightsService.ACTION_ENSURE);
        } else {
            return;
        }
        context.startForegroundService(service);
    }
}

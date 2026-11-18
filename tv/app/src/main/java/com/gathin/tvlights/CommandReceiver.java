package com.gathin.tvlights;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Commands for the lights from outside (action com.gathin.tvlights.COMMAND): String extras cmd = toggle, on, off or
 * scene; light = a light's id from lights.json, or all; scene = all_on, all_off, evening or cinema. A command that
 * isn't one of these is dropped here; the panel's own commands (look, level, follow) only come from the screen.
 */
public final class CommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!LightsService.ACTION_COMMAND.equals(intent.getAction())) return;
        String cmd = intent.getStringExtra(LightsService.EXTRA_CMD);
        String light = intent.getStringExtra(LightsService.EXTRA_LIGHT);
        String scene = intent.getStringExtra(LightsService.EXTRA_SCENE);
        if (!valid(cmd, light, scene)) return;
        Intent service = new Intent(context, LightsService.class).setAction(LightsService.ACTION_COMMAND)
                .putExtra(LightsService.EXTRA_CMD, cmd).putExtra(LightsService.EXTRA_LIGHT, light == null ? "all" : light);
        if (scene != null) service.putExtra(LightsService.EXTRA_SCENE, scene);
        context.startForegroundService(service);
    }

    static boolean valid(String cmd, String light, String scene) {
        if (cmd == null) return false;
        if (light != null && !light.matches("[A-Za-z0-9_-]{1,32}")) return false;
        switch (cmd) {
            case "toggle":
            case "on":
            case "off":
                return true;
            case "scene":
                return LightCommands.isScene(scene);
            default:
                return false;
        }
    }
}

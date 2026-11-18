package com.gathin.tvlights;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lights as a group: the list from lights.json (re-read when the file changes), the commands (switch on or off, a
 * scene, the panel's choices) and the music. A palette arrives (the colours of the cover that plays): the strip takes
 * it in a wave, and each bulb that follows the music and is on takes one colour of it; when the music stops, or after
 * a track change that sends no new cover, every light goes back to what it was. A light that is off is never turned
 * on by the music. This thread only decides; each LightUnit talks to its light on a thread of its own.
 */
final class LightsController implements LightUnit.Owner {
    /** A palette line "off" counts only if no palette follows within this long: a track change sends one. */
    static final long OFF_DELAY_MS = 2_000;
    /** How often the config file is looked at again. */
    static final long MAINTAIN_MS = 5_000;
    private static final int MAX_COLORS = 8;

    static final String PREFS = "lights";
    /** The saved intensity of the cover-follow: Intensity.SOFT, NORMAL (the default) or LIVELY. */
    static final String PREF_INTENSITY = "intensity";

    interface Listener {
        /** The link state or what is on the lights changed; on any thread. */
        void onStatus();
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("lights");
    private Handler worker;
    private final Object palettes = new Object();
    private long configStamp = -1;

    /** Worker thread only. */
    private List<LightUnit> units = Collections.emptyList();
    private double[][] lastColors;
    private boolean musicOn;

    private final Runnable restoreNow = this::restoreAll;
    private final Runnable maintain = new Runnable() {
        @Override
        public void run() {
            maintain();
            worker.postDelayed(this, MAINTAIN_MS);
        }
    };

    LightsController(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.listener = listener;
        Intensity.mode = Intensity.clamp(prefs.getInt(PREF_INTENSITY, Intensity.NORMAL));
    }

    void start() {
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(maintain);
    }

    void stop() {
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> {
                for (LightUnit u : units) u.stop();   // each leaves its light as it was before the music
                units = Collections.emptyList();
                thread.quitSafely();
            });
        } else {
            thread.quitSafely();
        }
        LightsStatus.units = Collections.emptyList();
        LightsStatus.link = LightsStatus.STOPPED;
    }

    // ---- what the service hands in (any thread) ------------------------------------------------------------------

    /** A palette line from the launcher: "#RRGGBB,#RRGGBB,..." or "off". */
    void onPalette(String value) {
        if (worker == null) return;
        if (isOff(value)) {
            worker.removeCallbacks(restoreNow);
            worker.postDelayed(restoreNow, OFF_DELAY_MS);
            return;
        }
        double[][] colors = parsePalette(value);
        if (colors == null) return;
        worker.removeCallbacks(restoreNow);
        worker.removeCallbacksAndMessages(palettes);   // only the latest palette matters
        worker.postAtTime(() -> {
            lastColors = colors;
            musicOn = true;
            distribute();
        }, palettes, SystemClock.uptimeMillis());
    }

    /**
     * toggle, on, off, scene (arg: the scene) for one light or "all"; look, level and follow (arg: the value) for one
     * light: what the panel on screen asks.
     */
    void onCommand(String cmd, String light, String arg) {
        if (worker == null || cmd == null) return;
        worker.post(() -> command(cmd, light == null ? "all" : light, arg));
    }

    // ---- the worker thread ---------------------------------------------------------------------------------------

    private void command(String cmd, String light, String arg) {
        List<LightUnit> targets = targets(light);
        switch (cmd) {
            case "toggle":
                boolean anyOn = false;
                for (LightUnit u : targets) anyOn |= u.isOn();
                // one light: switch it; several: all off if any is on, else all on
                for (LightUnit u : targets) u.setPower(targets.size() == 1 ? !u.isOn() : !anyOn);
                break;
            case "on":
            case "off":
                for (LightUnit u : targets) u.setPower("on".equals(cmd));
                break;
            case "scene":
                if (LightCommands.isScene(arg)) for (LightUnit u : targets) u.scene(arg);
                break;
            case "look":
            case "level":
                int n = parseInt(arg);
                for (LightUnit u : targets) {
                    if ("look".equals(cmd)) u.setLook(n);
                    else u.setLevel(n);
                }
                break;
            case "follow":
                for (LightUnit u : targets) u.setFollow("1".equals(arg));
                if (musicOn && "1".equals(arg)) distribute();
                break;
            case "intensity":
                // Applies live: the palette now playing is shown again at the new intensity (one colour change per light).
                Intensity.mode = Intensity.clamp(parseInt(arg));
                prefs.edit().putInt(PREF_INTENSITY, Intensity.mode).apply();
                if (musicOn) distribute();
                break;
            default:
        }
    }

    private List<LightUnit> targets(String light) {
        if ("all".equals(light)) return units;
        for (LightUnit u : units) if (u.cfg.id.equals(light)) return Collections.singletonList(u);
        return Collections.emptyList();
    }

    /** The palette to the lights that take it: the strip all of it, the i-th bulb colour i. */
    private void distribute() {
        if (lastColors == null) return;
        int bulb = 0;
        for (LightUnit u : units) {
            if (!u.eligible()) continue;
            u.playMusic(lastColors, u.cfg.isStrip() ? 0 : bulb++);
        }
    }

    private void restoreAll() {
        musicOn = false;
        for (LightUnit u : units) u.stopMusic();
    }

    /** Every few seconds: the config file (a new or changed one is taken up). The units keep their own connections. */
    private void maintain() {
        LightsConfig.Result r = LightsConfig.read(ctx);
        if (r.stamp != configStamp) {
            configStamp = r.stamp;
            apply(r.config);
        }
        if (r.config == null) {
            LightsStatus.problem = r.problem;
            LightsStatus.link = LightsStatus.NO_CONFIG;
            listener.onStatus();
        } else {
            LightsStatus.problem = "";
            onUnitChanged();
        }
    }

    /** The lights of a new config: those unchanged keep running, the others stop or start. */
    private void apply(LightsConfig config) {
        List<LightUnit> next = new ArrayList<>();
        if (config == null) {
            // No config (removed, or reset): the music follows nothing again until the user says so.
            SharedPreferences.Editor e = prefs.edit();
            for (String k : prefs.getAll().keySet()) if (k.startsWith("follow_")) e.remove(k);
            e.apply();
        } else {
            for (LightsConfig.Light l : config.lights) {
                LightUnit keep = null;
                for (LightUnit u : units) if (u.cfg.sameAs(l)) keep = u;
                if (keep == null) {
                    keep = new LightUnit(l, prefs, this);
                    keep.start();
                }
                next.add(keep);
            }
        }
        for (LightUnit u : units) if (!next.contains(u)) u.stop();
        units = next;
        LightsStatus.units = Collections.unmodifiableList(new ArrayList<>(next));
    }

    @Override
    public void onUnitChanged() {
        int link = LightsStatus.NO_CONFIG;
        boolean connecting = false;
        for (LightUnit u : LightsStatus.units) {
            if (u.linkState == LightsStatus.CONNECTED) {
                link = LightsStatus.CONNECTED;
                break;
            }
            connecting |= u.linkState == LightsStatus.CONNECTING;
            link = connecting ? LightsStatus.CONNECTING : LightsStatus.UNREACHABLE;
        }
        LightsStatus.link = link;
        listener.onStatus();
    }

    // ---- palette lines -------------------------------------------------------------------------------------------

    static boolean isOff(String value) {
        return value != null && value.trim().equalsIgnoreCase("off");
    }

    /** "#RRGGBB,..." as the strip's colours (see LightsMath.fromRgb), or null when it isn't a palette. */
    static double[][] parsePalette(String value) {
        if (value == null || value.length() > 200) return null;
        String[] parts = value.split(",");
        double[][] colors = new double[Math.min(parts.length, MAX_COLORS)][];
        int n = 0;
        try {
            for (String p : parts) {
                if (p.trim().isEmpty()) continue;
                if (n == MAX_COLORS) break;
                colors[n++] = LightsMath.fromRgb(LightsMath.parseHex(p));
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (n == 0) return null;
        return n == colors.length ? colors : java.util.Arrays.copyOf(colors, n);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

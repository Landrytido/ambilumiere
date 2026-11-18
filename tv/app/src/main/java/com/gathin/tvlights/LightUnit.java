package com.gathin.tvlights;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One light at run time: its connection, its live state (on or off, colour, as the light reports it), the choices the
 * panel remembers (colour, brightness, "follows the music"), and the music. Everything that talks to the light runs on
 * the light's own thread, so one that doesn't answer never holds up the others; a command from the user goes in front
 * of the music and stops a wave in progress. The fields without a lock are read by the screen.
 */
final class LightUnit implements LightLink.Events, LightsEngine.Sink {
    interface Owner {
        /** The light's state or link changed; on any thread. */
        void onUnitChanged();
    }

    static final int UNKNOWN = -1;
    /** After the music is over, the updates a light sends are still its echo of our commands for this long. */
    static final long HOLD_TAIL_MS = 1_500;
    /** How often the connection is checked. */
    static final long MAINTAIN_MS = 5_000;
    /** A bulb moves to a new music colour in this many commands (about 1.8 s). */
    private static final int FADE_FRAMES = 6;
    private static final Object MUSIC = new Object();

    final LightsConfig.Light cfg;
    private final Owner owner;
    private final SharedPreferences prefs;
    private final HandlerThread thread;
    private Handler handler;
    private final Shadow shadow = new Shadow();
    private final LightLink link;
    private final LightsEngine engine = new LightsEngine(this);

    volatile int linkState = LightsStatus.CONNECTING;
    /** UNKNOWN until the light has said; then 0 or 1. */
    volatile int power = UNKNOWN;
    /** The colour of the dot on screen. */
    volatile int colour = LightsMath.whiteRgb(0);
    /** The panel's choices: one of LightCommands.SWATCHES, a brightness in percent, and whether the music reaches this light. */
    volatile int look, level;
    /** Off until the user turns it on, for each light. */
    volatile boolean follow;
    /** What the light shows now, as the screen names it (LightCommands.colourName), and how bright, 0..1 (-1: not known). */
    volatile int colourIdx = LightCommands.WARM;
    volatile double brightness = -1;
    private volatile boolean hasTemp;
    private volatile boolean interrupted;

    /** Guarded by this: what the light last reported, for the dot. */
    private String mode = "white";
    private double hue, sat, val, white = -1;
    private int temp;

    /** Worker thread only. */
    private boolean musicActive;
    private JSONObject held;
    private double[] bulbNow;

    private final Runnable maintain = new Runnable() {
        @Override
        public void run() {
            link.ensureOpen();
            handler.postDelayed(this, MAINTAIN_MS);
        }
    };

    LightUnit(LightsConfig.Light cfg, SharedPreferences prefs, Owner owner) {
        this.cfg = cfg;
        this.prefs = prefs;
        this.owner = owner;
        this.thread = new HandlerThread("light-" + cfg.id);
        this.link = new LightLink(cfg, this);
        int savedLook = prefs.getInt("look_" + cfg.id, LightCommands.WARM);
        this.look = savedLook >= 0 && savedLook < LightCommands.SWATCHES ? savedLook : LightCommands.WARM;
        this.level = prefs.getInt("level_" + cfg.id, 100);
        this.follow = prefs.getBoolean("follow_" + cfg.id, false);
    }

    void start() {
        thread.start();
        handler = new Handler(thread.getLooper());
        shadow.seed(savedOriginal());
        handler.post(maintain);
    }

    /** Leaves the light as it was before the music, closes the connection and ends the thread. */
    void stop() {
        Handler h = handler;
        if (h == null) {
            thread.quitSafely();
            return;
        }
        h.removeCallbacksAndMessages(null);
        h.post(() -> {
            restoreMusic();
            link.close();
            thread.quitSafely();
        });
    }

    boolean isOn() {
        return power == 1;
    }

    /** The music may take this light now: it follows, it is on and it answers. */
    boolean eligible() {
        return follow && power == 1 && linkState == LightsStatus.CONNECTED;
    }

    // ---- what the user asks (any thread) -------------------------------------------------------------------------

    void setPower(boolean on) {
        command(() -> {
            cancelMusic();
            int before = power;
            power = on ? 1 : 0;
            owner.onUnitChanged();
            if (!link.send(LightCommands.power(on))) {
                power = before;
                owner.onUnitChanged();
            }
        });
    }

    /** On, with the panel's colour n (0..9) at the brightness the panel has. */
    void setLook(int swatch) {
        if (swatch < 0 || swatch >= LightCommands.SWATCHES) return;
        look = swatch;
        prefs.edit().putInt("look_" + cfg.id, swatch).apply();
        applyLook();
    }

    /** Brightness in percent; the colour stays the one the light shows (or the last one chosen, if it isn't one of the eight). */
    void setLevel(int percent) {
        if (percent < LightCommands.MIN_LEVEL || percent > LightCommands.MAX_LEVEL) return;
        int shown = colourIdx;
        if (shown >= 0 && shown < LightCommands.SWATCHES) look = shown;
        level = percent;
        prefs.edit().putInt("level_" + cfg.id, percent).apply();
        applyLook();
    }

    void setFollow(boolean on) {
        follow = on;
        prefs.edit().putBoolean("follow_" + cfg.id, on).apply();
        owner.onUnitChanged();
        if (!on) command(this::restoreMusic);
    }

    void scene(String name) {
        command(() -> {
            cancelMusic();
            JSONObject dps = LightCommands.scene(name, cfg.isStrip(), hasTemp);
            if (LightCommands.SCENE_ALL_ON.equals(name)) {
                look = LightCommands.WARM;
                level = 100;
                prefs.edit().putInt("look_" + cfg.id, look).putInt("level_" + cfg.id, level).apply();
            }
            boolean ok = link.send(dps != null ? dps : LightCommands.power(false));
            if (ok) {
                power = dps != null ? 1 : 0;
                owner.onUnitChanged();
            }
        });
    }

    private void applyLook() {
        int swatch = look, percent = level;
        command(() -> {
            cancelMusic();
            if (link.send(LightCommands.look(cfg.isStrip(), hasTemp, swatch, percent))) power = 1;
            owner.onUnitChanged();
        });
    }

    /** Runs r on the light's thread before any music, and cuts short a wave or fade in progress. */
    private void command(Runnable r) {
        Handler h = handler;
        if (h == null) return;
        interrupted = true;
        h.removeCallbacksAndMessages(MUSIC);
        h.post(() -> {
            interrupted = false;
            r.run();
        });
    }

    // ---- the music (any thread) ----------------------------------------------------------------------------------

    /** The strip takes the whole palette as a wave; a bulb takes colour number index of it (wrapping round). */
    void playMusic(double[][] colors, int index) {
        Handler h = handler;
        if (h == null) return;
        h.removeCallbacksAndMessages(MUSIC);
        h.postAtTime(() -> music(colors, index), MUSIC, SystemClock.uptimeMillis());
    }

    /** Back to what the light was before the music (nothing to do if the music never reached it). */
    void stopMusic() {
        Handler h = handler;
        if (h == null) return;
        h.removeCallbacksAndMessages(MUSIC);
        h.postAtTime(this::restoreMusic, MUSIC, SystemClock.uptimeMillis());
    }

    // ---- the worker thread ---------------------------------------------------------------------------------------

    private void music(double[][] colors, int index) {
        if (!eligible()) return;
        if (!musicActive) {
            // What the light was set to before the music: the state to come back to. Kept for after a restart too.
            held = shadow.snapshot();
            shadow.hold();
            if (held.length() > 0) prefs.edit().putString("original_" + cfg.id, held.toString()).apply();
            double[][] now = LightsMath.originalColors(held);
            if (cfg.isStrip()) engine.setCurrent(LightsMath.zonesOf(now));
            else bulbNow = now != null && now.length > 0 ? now[0] : null;
            musicActive = true;
        }
        // The cover's colours at the intensity the user chose (Doux, Normal, Vif), for the strip and the bulbs alike.
        double[][] shown = Intensity.apply(colors, Intensity.mode);
        if (cfg.isStrip()) engine.show(LightsMath.spread(shown));
        else fade(shown[index % shown.length]);
    }

    private void restoreMusic() {
        if (!musicActive) return;
        JSONObject original = held != null && held.length() > 0 ? held : savedOriginal();
        if (cfg.isStrip()) {
            engine.restore(original);
        } else if (original != null && original.length() > 0) {
            restoreBulb(original);
        }
        endMusic();
    }

    private void restoreBulb(JSONObject original) {
        double[][] colors = LightsMath.originalColors(original);
        if (colors == null) {   // a white: fade to something that looks like it, then send the exact state
            double[] white = LightsMath.whiteAsColour(original);
            if (white != null) colors = new double[][]{white};
        }
        if (colors != null && colors.length > 0) fade(colors[0]);
        try {
            JSONObject dps = new JSONObject();
            // A bulb takes a write of the colour as "switch to colour", so a white state must not carry its old colour.
            String[] keys = "colour".equals(original.optString("21")) ? new String[]{"21", "24"} : new String[]{"21", "22", "23"};
            for (String k : keys) if (original.has(k)) dps.put(k, original.get(k));
            if (dps.length() > 0) link.send(dps);
        } catch (JSONException ignored) {
        }
    }

    /** The user took the light over: no music to come back from. */
    private void cancelMusic() {
        if (musicActive) endMusic();
    }

    private void endMusic() {
        musicActive = false;
        held = null;
        shadow.release(SystemClock.elapsedRealtime(), HOLD_TAIL_MS);
    }

    /** One colour {h, s, v}, reached by an eased fade from the last one shown. */
    private boolean fade(double[] target) {
        double[] from = bulbNow;
        int frames = from != null ? FADE_FRAMES : 1;
        for (int k = 1; k <= frames; k++) {
            double t = (double) k / frames;
            t = t * t * (3 - 2 * t);
            double[] m = from != null ? LightsMath.mix(from, target, t) : target;
            try {
                JSONObject dps = new JSONObject().put("21", "colour").put("24", LightsMath.hex(m));
                if (!send(dps)) return false;
            } catch (JSONException e) {
                return false;
            }
        }
        bulbNow = target;
        return true;
    }

    /** LightsEngine's way to the light (the strip's waves): stops at once when the user has asked for something. */
    @Override
    public boolean send(JSONObject dps) {
        return !interrupted && link.send(dps);
    }

    private JSONObject savedOriginal() {
        String s = prefs.getString("original_" + cfg.id, null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            return null;
        }
    }

    // ---- what the light reports (any thread) ---------------------------------------------------------------------

    @Override
    public void onDps(JSONObject dps) {
        absorb(dps);
        shadow.merge(dps, SystemClock.elapsedRealtime());
        owner.onUnitChanged();
    }

    @Override
    public void onLink(int state) {
        linkState = state;
        owner.onUnitChanged();
    }

    private synchronized void absorb(JSONObject dps) {
        if (dps == null) return;
        if (dps.has("20")) power = dps.optBoolean("20") ? 1 : 0;
        if (dps.has("21")) mode = dps.optString("21", mode);
        if (dps.has("23")) {
            hasTemp = true;
            temp = dps.optInt("23", temp);
        }
        double[] c = null;
        if (dps.has("103")) {
            double[][] all = LightsMath.decode103(dps.optString("103"));
            if (all != null && all.length > 0) c = all[0];
        } else if (dps.has("24")) {
            c = LightsMath.parseHex24(dps.optString("24"));
        }
        if (c != null) {
            hue = c[0];
            sat = c[1];
            val = c[2];
        }
        if (dps.has("22")) white = dps.optInt("22") / 1000.0;
        boolean isWhite = "white".equals(mode);
        colour = isWhite ? LightsMath.whiteRgb(temp) : LightsMath.rgb(hue, sat, 1.0);
        colourIdx = LightCommands.colourName(isWhite, hue, sat, temp);
        brightness = isWhite ? white : (val > 0 ? val : -1);
    }
}

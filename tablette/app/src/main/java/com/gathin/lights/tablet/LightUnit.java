package com.gathin.lights.tablet;

import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One light at run time: its connection and its live state (on or off, colour, brightness, as the light reports it,
 * and as the user just asked). Everything that talks to the light runs on the light's own thread, so one that doesn't
 * answer never holds up the others. Colour and brightness commands that pile up while the light is busy are merged:
 * only the latest one is sent, so dragging a finger over the colour wheel never builds a queue.
 */
final class LightUnit implements LightLink.Events {
    interface Owner {
        /** The light's state or link changed; on any thread. */
        void onUnitChanged();
    }

    /** What the screen needs of a light, read in one go. level is 10..100, or -1 while the light hasn't said. */
    static final class State {
        final boolean on, white;
        final int link, level, temp, rgb, colourIdx;
        final double hue, sat;

        State(boolean on, boolean white, int link, int level, int temp, double hue, double sat) {
            this.on = on;
            this.white = white;
            this.link = link;
            this.level = level;
            this.temp = temp;
            this.hue = hue;
            this.sat = sat;
            this.rgb = white ? LightsMath.whiteRgb(temp) : LightsMath.rgb(hue, sat, 1.0);
            this.colourIdx = LightCommands.colourName(white, hue, sat, temp);
        }

        boolean online() {
            return link == LightsStatus.CONNECTED;
        }
    }

    static final int UNKNOWN = -1;
    /** How often the connection is checked. */
    static final long MAINTAIN_MS = 5_000;

    final LightsConfig.Light cfg;
    private final Owner owner;
    private final HandlerThread thread;
    private Handler handler;
    private final LightLink link;
    private final AtomicReference<JSONObject> pending = new AtomicReference<>();

    private volatile int linkState = LightsStatus.CONNECTING;
    private volatile int power = UNKNOWN;
    private volatile boolean hasTemp;

    /** Guarded by this: what the light last reported, or what the user just asked for. */
    private boolean white = true;
    private double hue, sat, bright = -1;
    private int temp;

    private final Runnable maintain = new Runnable() {
        @Override
        public void run() {
            link.ensureOpen();
            handler.postDelayed(this, MAINTAIN_MS);
        }
    };

    LightUnit(LightsConfig.Light cfg, Owner owner) {
        this.cfg = cfg;
        this.owner = owner;
        this.thread = new HandlerThread("light-" + cfg.id);
        this.link = new LightLink(cfg, this);
    }

    void start() {
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(maintain);
    }

    /** Closes the connection and ends the thread once the commands already asked for are done; done runs then. */
    void stop(Runnable done) {
        Handler h = handler;
        if (h == null) {
            thread.quitSafely();
            if (done != null) done.run();
            return;
        }
        h.removeCallbacks(maintain);
        h.post(() -> {
            link.close();
            thread.quitSafely();
            if (done != null) done.run();
        });
    }

    synchronized State state() {
        int level = bright < 0 ? -1 : Math.max(LightCommands.MIN_LEVEL, Math.min(100, (int) Math.round(bright * 100)));
        return new State(power == 1, white, linkState, level, temp, hue, sat);
    }

    boolean isOn() {
        return power == 1;
    }

    boolean hasTemp() {
        return hasTemp;
    }

    // ---- what the user asks (any thread) -------------------------------------------------------------------------

    void setPower(boolean on) {
        post(() -> {
            int before = power;
            power = on ? 1 : 0;
            owner.onUnitChanged();
            if (!link.send(LightCommands.power(on))) {
                power = before;
                owner.onUnitChanged();
            }
        });
    }

    /** A free colour: h in degrees, s 0..1, at the brightness the light has. */
    void setColour(double h, double s) {
        double hh = ((h % 360) + 360) % 360;
        double ss = Math.max(0, Math.min(1, s));
        int level = currentLevel();
        synchronized (this) {
            white = false;
            hue = hh / 360.0;
            sat = ss;
            bright = level / 100.0;
        }
        power = 1;
        sendLatest(LightCommands.colour(cfg.isStrip(), (int) Math.round(hh), (int) Math.round(ss * 1000), level * 10));
        owner.onUnitChanged();
    }

    /** Brightness in percent (10 to 100), the colour staying as it is. */
    void setLevel(int percent) {
        int level = Math.max(LightCommands.MIN_LEVEL, Math.min(LightCommands.MAX_LEVEL, percent));
        JSONObject dps;
        synchronized (this) {
            bright = level / 100.0;
            if (white) {
                dps = LightCommands.power(true);
                try {
                    dps.put("22", level * 10);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                dps = LightCommands.colour(cfg.isStrip(), (int) Math.round(hue * 360), (int) Math.round(sat * 1000), level * 10);
            }
        }
        power = 1;
        sendLatest(dps);
        owner.onUnitChanged();
    }

    /** One of the eight colours of LightCommands, at the brightness the light has. */
    void setSwatch(int swatch) {
        if (swatch < 0 || swatch >= LightCommands.SWATCHES) return;
        int level = currentLevel();
        synchronized (this) {
            if (swatch < 1 && (hasTemp || cfg.isStrip())) {
                white = true;
                temp = 0;
            } else {
                white = false;
                hue = swatch < 1 ? 28 / 360.0 : LightCommands.swatchHue(swatch) / 360.0;
                sat = swatch < 1 ? 0.48 : 1.0;
            }
            bright = level / 100.0;
        }
        power = 1;
        sendLatest(LightCommands.look(cfg.isStrip(), hasTemp, swatch, level));
        owner.onUnitChanged();
    }

    /** What a scene says for this light. */
    void apply(SceneStore.Setting s) {
        if (s == null) return;
        synchronized (this) {
            if (s.on) {
                white = s.white && (hasTemp || cfg.isStrip());
                if (!white) {
                    hue = (s.white ? 28 : s.h) / 360.0;
                    sat = s.white ? 0.48 : s.s / 1000.0;
                }
                temp = 0;
                bright = s.level / 100.0;
            }
        }
        power = s.on ? 1 : 0;
        JSONObject dps = s.dps(cfg.isStrip(), hasTemp);
        post(() -> link.send(dps));
        owner.onUnitChanged();
    }

    /** The light didn't answer: try again now instead of after the pause that grows. */
    void retry() {
        post(() -> {
            link.retryNow();
            link.ensureOpen();
        });
    }

    private int currentLevel() {
        synchronized (this) {
            return bright < 0 ? 100 : Math.max(LightCommands.MIN_LEVEL, Math.min(100, (int) Math.round(bright * 100)));
        }
    }

    private void post(Runnable r) {
        Handler h = handler;
        if (h != null) h.post(r);
    }

    /** Only the latest of the colour and brightness commands asked while the light is busy goes out. */
    private void sendLatest(JSONObject dps) {
        pending.set(dps);
        post(() -> {
            JSONObject d = pending.getAndSet(null);
            if (d == null) return;
            if (link.send(d)) power = 1;
            owner.onUnitChanged();
        });
    }

    // ---- what the light reports (any thread) ---------------------------------------------------------------------

    @Override
    public void onDps(JSONObject dps) {
        absorb(dps);
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
        if (dps.has("21")) white = "white".equals(dps.optString("21", "white"));
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
        double val = -1;
        if (c != null) {
            hue = c[0];
            sat = c[1];
            val = c[2];
        }
        if (white) {
            if (dps.has("22")) bright = dps.optInt("22") / 1000.0;
        } else if (val > 0) {
            bright = val;
        }
    }
}

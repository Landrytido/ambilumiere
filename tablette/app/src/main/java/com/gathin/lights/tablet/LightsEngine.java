package com.gathin.lights.tablet;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * What goes to the strip, in order, with nothing Android in it: the wave that carries the strip from the colours on
 * show to new ones, and the way back to the state the Tuya app had set. Every command goes through a Sink, which
 * spaces them out (MIN_INTERVAL) and talks to the strip. Ported from the PC tool lumieres.py; the tests compare the two.
 */
final class LightsEngine {
    /** Takes one datapoint map ({"21": "colour", "103": "..."}); false when the strip didn't take it. */
    interface Sink {
        boolean send(JSONObject dps);
    }

    /** A change of palette flows along the strip over this long (seconds)... */
    static final double WAVE_SECONDS = 3.2;
    /** ...the last zone starting this long after the first, clockwise from the bottom left. */
    static final double WAVE_LAG = 1.2;
    /** Seconds between two commands to the strip. */
    static final double MIN_INTERVAL = 0.3;

    private final Sink sink;
    /** The zones last sent, where the next wave starts from; null when unknown (then the change is one command). */
    private double[][] current;

    LightsEngine(Sink sink) {
        this.sink = sink;
    }

    double[][] current() {
        return current;
    }

    void setCurrent(double[][] zones) {
        current = zones;
    }

    private boolean send(String key, Object value, String key2, Object value2) {
        try {
            JSONObject dps = new JSONObject();
            dps.put(key, value);
            dps.put(key2, value2);
            return sink.send(dps);
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * The new colours flow in along the strip like water: each zone starts its change a little after the one before
     * it, and each change is eased. One command per frame.
     */
    boolean show(double[][] target) {
        double[][] start = current;
        int frames = start != null ? Math.max(2, (int) Math.rint(WAVE_SECONDS / MIN_INTERVAL)) : 1;
        double span = WAVE_SECONDS - WAVE_LAG;
        for (int k = 1; k <= frames; k++) {
            double now = k * WAVE_SECONDS / frames;
            double[][] zones = new double[target.length][];
            for (int i = 0; i < target.length; i++) {
                if (start == null) {
                    zones[i] = target[i];
                    continue;
                }
                double p = Math.min(1.0, Math.max(0.0, (now - WAVE_LAG * i / (LightsMath.ZONES - 1)) / span));
                p = p * p * (3 - 2 * p);   // eased: slow start, slow end
                double[] m = LightsMath.mix(start[i], target[i], p);
                zones[i] = new double[]{m[0], m[1], Math.max(m[2], 0.55)};   // never dark half way
            }
            if (!send("21", "colour", "103", LightsMath.payload(zones))) return false;
        }
        current = target;
        return true;
    }

    /**
     * Puts the strip back as remembered, with a wave when that is a colour or a combination: then the exact state is
     * sent. original null or empty: the strip's purple.
     */
    boolean restore(JSONObject original) {
        boolean known = original != null && original.length() > 0;
        double[][] colors = known ? LightsMath.originalColors(original) : new double[][]{{
                LightsMath.PURPLE[0] / 360.0, LightsMath.PURPLE[1] / 1000.0, LightsMath.PURPLE[2] / 1000.0}};
        if (colors != null && colors.length > 0 && current != null) show(LightsMath.zonesOf(colors));   // the way back, then the exact state
        current = LightsMath.zonesOf(colors);
        try {
            JSONObject dps = new JSONObject();
            if (!known || !original.has("21")) {
                dps.put("21", "colour");
                dps.put("24", String.format(Locale.ROOT, "%04x%04x%04x", LightsMath.PURPLE[0], LightsMath.PURPLE[1], LightsMath.PURPLE[2]));
                dps.put("103", LightsMath.single(LightsMath.PURPLE[0], LightsMath.PURPLE[1], LightsMath.PURPLE[2]));
                return sink.send(dps);
            }
            dps.put("21", original.get("21"));
            if ("colour".equals(original.optString("21"))) {
                if (original.has("103")) {
                    dps.put("103", original.get("103"));
                } else if (original.has("24")) {
                    String hex = original.getString("24");
                    dps.put("24", hex);
                    dps.put("103", LightsMath.single(Integer.parseInt(hex.substring(0, 4), 16),
                            Integer.parseInt(hex.substring(4, 8), 16), Integer.parseInt(hex.substring(8, 12), 16)));
                }
            } else if ("scene".equals(original.optString("21")) && original.has("102")) {
                dps.put("102", original.get("102"));
            } else {
                for (String k : new String[]{"22", "23"}) if (original.has(k)) dps.put(k, original.get(k));
            }
            return sink.send(dps);
        } catch (JSONException | NumberFormatException | StringIndexOutOfBoundsException e) {
            return false;
        }
    }
}

package com.gathin.tvlights;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the Tuya app has set on the strip: its mode, brightness, colour, scene, combination. It is where the strip goes
 * back to when the music stops, whatever that was (one colour, a combination, white, a scene).
 * Fed by what the strip reports: its answer to a status request, and the updates it pushes when the app changes
 * something. While a palette is on (hold) nothing is taken in, those updates being our own; the hold lasts a little
 * after release() so the echo of the last command can't pass for the app's.
 */
final class Shadow {
    private static final String[] KEYS = {"21", "22", "23", "24", "102", "103"};

    private final JSONObject state = new JSONObject();
    private boolean hold;
    private long holdUntil;

    /** Starts from a state noted earlier (the strip doesn't report a combination when asked, only when it is set). */
    synchronized void seed(JSONObject saved) {
        if (saved == null) return;
        take(saved);
    }

    /** What the strip reported (datapoint number to value); ignored while a palette is on. */
    synchronized void merge(JSONObject dps, long nowMs) {
        if (dps == null || hold || nowMs < holdUntil) return;
        take(dps);
    }

    private void take(JSONObject dps) {
        for (String key : KEYS) {
            if (!dps.has(key)) continue;
            try {
                state.put(key, dps.get(key));
            } catch (JSONException ignored) {
            }
        }
    }

    synchronized JSONObject snapshot() {
        try {
            return new JSONObject(state.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** A palette is about to go on: from now on the strip's updates are ours. */
    synchronized void hold() {
        hold = true;
    }

    /** The palette is off (or never went on); the strip's updates count again after tailMs. */
    synchronized void release(long nowMs, long tailMs) {
        hold = false;
        holdUntil = nowMs + tailMs;
    }

    synchronized boolean holding() {
        return hold;
    }
}

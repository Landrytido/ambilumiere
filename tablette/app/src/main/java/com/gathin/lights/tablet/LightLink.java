package com.gathin.lights.tablet;

import org.json.JSONObject;

import java.io.IOException;

/**
 * The connection to one light, for that light's worker thread (everything but close() and isOpen() is called from it
 * alone): opens it when there is none, with pauses that grow while the light doesn't answer, spaces the commands
 * MIN_INTERVAL apart, and hands what the light reports (its answer to a status request, and the updates it pushes)
 * to the Events.
 */
final class LightLink implements LightsEngine.Sink {
    interface Events {
        /** Datapoints the light reported, on any thread. */
        void onDps(JSONObject dps);

        /** One of LightsStatus.CONNECTING, CONNECTED, UNREACHABLE; on any thread. */
        void onLink(int state);
    }

    private static final long GAP_MS = Math.round(LightsEngine.MIN_INTERVAL * 1000);
    private static final long RETRY_MS = 500;
    private static final long BACKOFF_MIN_MS = 1_000, BACKOFF_MAX_MS = 15_000;

    private final Events events;
    private final LightsConfig.Light light;
    private volatile TuyaLan lan;
    private long lastSendNs;
    private int failures;
    private long nextAttemptMs;

    LightLink(LightsConfig.Light light, Events events) {
        this.light = light;
        this.events = events;
    }

    boolean isOpen() {
        TuyaLan l = lan;
        return l != null && l.isOpen();
    }

    void close() {
        TuyaLan l = lan;
        lan = null;
        if (l != null) {
            l.setListener(null);
            l.close();
        }
    }

    /** True when the connection is open (opening it if it isn't and the pause since the last failure is over). */
    boolean ensureOpen() {
        if (isOpen()) return true;
        long now = System.currentTimeMillis();
        if (now < nextAttemptMs) return false;
        close();
        events.onLink(LightsStatus.CONNECTING);
        TuyaLan next = new TuyaLan(light.host, light.port, light.deviceId, light.key, light.version);
        next.setListener(new TuyaLan.Listener() {
            @Override
            public void onDps(JSONObject dps) {
                events.onDps(dps);
            }

            @Override
            public void onClosed() {
                if (lan == next) events.onLink(LightsStatus.UNREACHABLE);
            }
        });
        try {
            next.open();
            lan = next;
            JSONObject status = next.queryStatus();
            failures = 0;
            events.onLink(LightsStatus.CONNECTED);
            events.onDps(status);
            return true;
        } catch (IOException | RuntimeException e) {
            next.setListener(null);
            next.close();
            lan = null;
            nextAttemptMs = System.currentTimeMillis() + Math.min(BACKOFF_MAX_MS, BACKOFF_MIN_MS << Math.min(failures, 5));
            failures++;
            events.onLink(LightsStatus.UNREACHABLE);
            return false;
        }
    }

    /** Forget the pause that grows while the light doesn't answer: the next attempt is made at once. */
    void retryNow() {
        failures = 0;
        nextAttemptMs = 0;
    }

    /** One command, at least MIN_INTERVAL after the one before; once more over a fresh connection if the first try fails. */
    @Override
    public boolean send(JSONObject dps) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!ensureOpen()) return false;
            if (!space()) return false;
            try {
                TuyaLan l = lan;
                if (l == null) continue;
                l.setDps(dps);
                return true;
            } catch (IOException | RuntimeException e) {
                close();
                nextAttemptMs = 0;
                try {
                    Thread.sleep(RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        events.onLink(LightsStatus.UNREACHABLE);
        return false;
    }

    private boolean space() {
        long wait = lastSendNs + GAP_MS * 1_000_000L - System.nanoTime();
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        lastSendNs = System.nanoTime();
        return true;
    }
}

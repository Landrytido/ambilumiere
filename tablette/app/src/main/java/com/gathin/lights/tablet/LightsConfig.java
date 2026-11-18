package com.gathin.lights.tablet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The lights and how to talk to each: its name, the address on the home network, the port (6668), the protocol version
 * (3.5 or 3.3), its Tuya device id and local key. The keys come only from the file lights.json that is pushed with adb
 * into the app's own folder; they are never part of the app, and never shown or logged.
 */
final class LightsConfig {
    static final int MAX_LIGHTS = 8;

    /** One light. A "strip" is the ten-zone LED strip; anything else is a bulb with one colour. */
    static final class Light {
        final String id, name, kind, host, version, deviceId, key;
        final int port;

        Light(String id, String name, String kind, String host, int port, String version, String deviceId, String key) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.host = host;
            this.port = port;
            this.version = version;
            this.deviceId = deviceId;
            this.key = key;
        }

        boolean isStrip() {
            return "strip".equals(kind);
        }

        boolean sameAs(Light o) {
            return o != null && id.equals(o.id) && name.equals(o.name) && kind.equals(o.kind) && host.equals(o.host)
                    && port == o.port && version.equals(o.version) && deviceId.equals(o.deviceId) && key.equals(o.key);
        }
    }

    final List<Light> lights;

    private LightsConfig(List<Light> lights) {
        this.lights = Collections.unmodifiableList(lights);
    }

    /** What reading the file gave: a config, or the reason there is none. */
    static final class Result {
        final LightsConfig config;
        final String problem;
        /** The file's modification time and size when read, to tell a changed file from an unchanged one. */
        final long stamp;

        Result(LightsConfig config, String problem, long stamp) {
            this.config = config;
            this.problem = problem;
            this.stamp = stamp;
        }
    }

    static File file(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "lights.json");
    }

    /** Checks a lights.json the user picked and keeps a copy in the app's folder: null when stored, else the problem. */
    static String store(Context ctx, String text) {
        Result r = parse(text, 0);
        if (r.config == null) return r.problem;
        File f = file(ctx);
        if (f == null) return "unreadable";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "unreadable";
        }
        return null;
    }

    static Result read(Context ctx) {
        File f = file(ctx);
        if (f == null || !f.isFile()) return new Result(null, "missing", 0);
        long stamp = f.lastModified() * 31 + f.length();
        byte[] bytes = new byte[(int) Math.min(f.length(), 32 * 1024)];
        try (InputStream in = new FileInputStream(f)) {
            int n = 0;
            while (n < bytes.length) {
                int r = in.read(bytes, n, bytes.length - n);
                if (r < 0) break;
                n += r;
            }
            return parse(new String(bytes, 0, n, StandardCharsets.UTF_8), stamp);
        } catch (IOException e) {
            return new Result(null, "unreadable", stamp);
        }
    }

    /**
     * {"lights": [{"id": "tv", "name": "Ruban TV", "kind": "strip", "host": "192.168.1.20", "port": 6668,
     * "version": "3.5", "device_id": "bf...", "key": "...", "zones": 10}, ...]}; name, port, version and zones are optional.
     */
    static Result parse(String text, long stamp) {
        try {
            JSONArray array = new JSONObject(text).optJSONArray("lights");
            if (array == null || array.length() == 0 || array.length() > MAX_LIGHTS) return new Result(null, "incomplete", stamp);
            List<Light> out = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) return new Result(null, "incomplete", stamp);
                String id = o.optString("id", "").trim();
                String host = o.optString("host", "").trim();
                String deviceId = o.optString("device_id", "").trim();
                String key = o.optString("key", "");
                String version = o.optString("version", "3.5").trim();
                int port = o.optInt("port", 6668);
                if (id.isEmpty() || host.isEmpty() || deviceId.isEmpty() || key.isEmpty() || !ids.add(id)) {
                    return new Result(null, "incomplete", stamp);
                }
                if (port < 1 || port > 65535 || !(version.equals("3.5") || version.equals("3.3"))) {
                    return new Result(null, "incomplete", stamp);
                }
                String kind = o.optString("kind", "bulb").trim().equals("strip") ? "strip" : "bulb";
                // The colour maths and the datapoint are written for the ten zones of the LSC strip.
                if (kind.equals("strip") && o.optInt("zones", LightsMath.ZONES) != LightsMath.ZONES) {
                    return new Result(null, "zones", stamp);
                }
                String name = o.optString("name", "").trim();
                out.add(new Light(id, name.isEmpty() ? id : name, kind, host, port, version, deviceId, key));
            }
            return new Result(new LightsConfig(out), null, stamp);
        } catch (JSONException e) {
            return new Result(null, "unreadable", stamp);
        }
    }
}

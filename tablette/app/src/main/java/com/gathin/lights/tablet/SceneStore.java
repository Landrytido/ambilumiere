package com.gathin.lights.tablet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The scenes: the two built-in ones (Soirée and Cinéma, worked out for each light from its kind) and the ones the user
 * made, each saying for some lights whether it is on, and its colour and brightness. Kept as JSON in the app's
 * preferences; the JSON and the datapoints are plain Java.
 */
final class SceneStore {
    static final String EVENING = "evening", CINEMA = "cinema";

    /** What a scene wants of one light. h 0..360 and s 0..1000 for a colour; white: the warm white instead; level 10..100. */
    static final class Setting {
        final boolean on, white;
        final int h, s, level;

        Setting(boolean on, boolean white, int h, int s, int level) {
            this.on = on;
            this.white = white;
            this.h = h;
            this.s = s;
            this.level = Math.max(LightCommands.MIN_LEVEL, Math.min(LightCommands.MAX_LEVEL, level));
        }

        static Setting off() {
            return new Setting(false, true, 0, 0, 100);
        }

        /** One of the eight colours of LightCommands, 0 being the warm white. */
        static Setting swatch(int swatch, int level) {
            return swatch < 1 ? new Setting(true, true, 0, 0, level)
                    : new Setting(true, false, LightCommands.swatchHue(swatch), 1000, level);
        }

        JSONObject dps(boolean strip, boolean hasTemp) {
            if (!on) return LightCommands.power(false);
            if (white) return LightCommands.look(strip, hasTemp, LightCommands.WARM, level);
            return LightCommands.colour(strip, h, s, level * 10);
        }

        /** What it looks like on a preview, 0xFFRRGGBB at full brightness. */
        int rgb() {
            return white ? LightsMath.whiteRgb(0) : LightsMath.rgb(h / 360.0, s / 1000.0, 1.0);
        }

        /** The swatch it is, or -1 for any other colour. */
        int swatchIndex() {
            if (white) return LightCommands.WARM;
            for (int i = 1; i < LightCommands.SWATCHES; i++) {
                if (LightCommands.swatchHue(i) == h && s == 1000) return i;
            }
            return -1;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("on", on).put("white", white).put("h", h).put("s", s).put("level", level);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        static Setting fromJson(JSONObject o) {
            return new Setting(o.optBoolean("on", true), o.optBoolean("white", true), o.optInt("h", 0), o.optInt("s", 1000),
                    o.optInt("level", 100));
        }
    }

    static final class Scene {
        final String id, name;
        /** EVENING or CINEMA for the built-in ones, null for a scene of the user. */
        final String builtin;
        final Map<String, Setting> lights;

        Scene(String id, String name, String builtin, Map<String, Setting> lights) {
            this.id = id;
            this.name = name;
            this.builtin = builtin;
            this.lights = lights;
        }

        boolean isBuiltin() {
            return builtin != null;
        }

        Scene renamed(String newName) {
            return new Scene(id, newName, builtin, lights);
        }

        /** What this scene says for a light, or null when it leaves it alone. */
        Setting settingFor(LightsConfig.Light light) {
            if (EVENING.equals(builtin)) return new Setting(true, false, 30, 700, light.isStrip() ? 30 : 40);
            if (CINEMA.equals(builtin)) return light.isStrip() ? new Setting(true, true, 0, 0, 10) : Setting.off();
            return lights.get(light.id);
        }
    }

    private static final String KEY = "scenes";

    private SceneStore() {
    }

    static List<Scene> builtins() {
        List<Scene> out = new ArrayList<>();
        out.add(new Scene(EVENING, "Soirée", EVENING, new LinkedHashMap<>()));
        out.add(new Scene(CINEMA, "Cinéma", CINEMA, new LinkedHashMap<>()));
        return out;
    }

    static String newId() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    static String toJson(List<Scene> scenes) {
        JSONArray array = new JSONArray();
        try {
            for (Scene s : scenes) {
                if (s.isBuiltin()) continue;
                JSONObject lights = new JSONObject();
                for (Map.Entry<String, Setting> e : s.lights.entrySet()) lights.put(e.getKey(), e.getValue().toJson());
                array.put(new JSONObject().put("id", s.id).put("name", s.name).put("lights", lights));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    static List<Scene> fromJson(String text) {
        List<Scene> out = new ArrayList<>();
        if (text == null) return out;
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String name = o.optString("name", "").trim();
                JSONObject lights = o.optJSONObject("lights");
                if (id.isEmpty() || name.isEmpty() || lights == null) continue;
                Map<String, Setting> map = new LinkedHashMap<>();
                Iterator<String> keys = lights.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    JSONObject s = lights.optJSONObject(k);
                    if (s != null) map.put(k, Setting.fromJson(s));
                }
                out.add(new Scene(id, name, null, map));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("scenes", Context.MODE_PRIVATE);
    }

    /** The built-in scenes, then the user's. */
    static List<Scene> load(Context c) {
        List<Scene> all = builtins();
        all.addAll(fromJson(prefs(c).getString(KEY, null)));
        return all;
    }

    static void save(Context c, List<Scene> all) {
        prefs(c).edit().putString(KEY, toJson(all)).apply();
    }
}

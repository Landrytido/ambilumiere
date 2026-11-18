package com.gathin.tvlights;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What goes to a light for each thing the screen or a command asks, as datapoint maps, with nothing Android in it:
 * the eight colours of the settings screen, the brightness (10 to 100 %) and the four scenes. A bulb has the
 * datapoints 20 (on), 21 (mode), 22 (white brightness), 23 (white temperature, not on every bulb) and 24 (colour
 * "hhhhssssvvvv"); the strip has the same plus 103, which it shows instead of 24.
 */
final class LightCommands {
    static final String SCENE_ALL_ON = "all_on", SCENE_ALL_OFF = "all_off", SCENE_EVENING = "evening", SCENE_CINEMA = "cinema";

    /** The settings screen's eight colours: warm white, then red, orange, yellow, green, cyan, blue, violet. */
    static final int SWATCHES = 8;
    static final int WARM = 0;
    /** What colourName() says besides the swatches: a neutral and a cool white. */
    static final int NEUTRAL_WHITE = 8, COOL_WHITE = 9;
    static final int MIN_LEVEL = 10, MAX_LEVEL = 100, LEVEL_STEP = 10;

    /** The hue (degrees) of swatches 1 to 7. */
    private static final int[] HUE = {0, 25, 55, 120, 180, 240, 280};
    /** What a bulb without white temperature shows for the warm white: h, s of a pale amber. */
    private static final int WARM_AS_COLOUR_H = 28, WARM_AS_COLOUR_S = 480;

    private LightCommands() {
    }

    static boolean isScene(String name) {
        return SCENE_ALL_ON.equals(name) || SCENE_ALL_OFF.equals(name) || SCENE_EVENING.equals(name) || SCENE_CINEMA.equals(name);
    }

    static JSONObject power(boolean on) {
        return dps().fluent("20", on).out;
    }

    /** What the dot of a swatch shows on screen. */
    static int swatchRgb(int swatch) {
        return swatch < 1 ? LightsMath.whiteRgb(0) : LightsMath.rgb(HUE[swatch - 1] / 360.0, 1.0, 1.0);
    }

    /** On, with that colour at that brightness (10 to 100 %). hasTemp: the light has a white temperature. */
    static JSONObject look(boolean strip, boolean hasTemp, int swatch, int level) {
        int v = Math.max(10, level * 10);
        Dps d = dps().fluent("20", true);
        if (swatch < 1 && (hasTemp || strip)) {
            return d.fluent("21", "white").fluent("22", v).fluent("23", 0).out;
        }
        if (swatch < 1) return colour(d, strip, WARM_AS_COLOUR_H, WARM_AS_COLOUR_S, v).out;
        return colour(d, strip, HUE[swatch - 1], 1000, v).out;
    }

    /** On, one colour: h 0-360, s and v 0-1000. */
    static JSONObject colour(boolean strip, int h, int s, int v) {
        return colour(dps().fluent("20", true), strip, h, s, v).out;
    }

    private static Dps colour(Dps d, boolean strip, int h, int s, int v) {
        d.fluent("21", "colour").fluent("24", LightsMath.hex(h, s, v));
        return strip ? d.fluent("103", LightsMath.single(h, s, v)) : d;
    }

    /**
     * Which colour a light shows, as a swatch index (0 to 7) or NEUTRAL_WHITE or COOL_WHITE, for the name on its card.
     * white: the light is in white mode, with that temperature (0 warm .. 1000 cool); else h 0..1 and s 0..1 of its colour.
     */
    static int colourName(boolean white, double h, double s, int temp) {
        if (white) return temp < 333 ? WARM : temp < 667 ? NEUTRAL_WHITE : COOL_WHITE;
        double deg = (h - Math.floor(h)) * 360;
        if (s < 0.2) return NEUTRAL_WHITE;
        if (s < 0.55 && deg >= 15 && deg < 50) return WARM;   // the pale amber of a warm white
        if (deg < 15 || deg >= 340) return 1;
        if (deg < 40) return 2;
        if (deg < 70) return 3;
        if (deg < 165) return 4;
        if (deg < 200) return 5;
        if (deg < 265) return 6;
        return 7;
    }

    /**
     * A scene for one light, or null when the light is switched off by it (use power(false)).
     * all_on: soft warm white at 100 %; evening: amber, soft; cinema: bulbs off, the strip a faint warm white.
     */
    static JSONObject scene(String name, boolean strip, boolean hasTemp) {
        switch (name) {
            case SCENE_ALL_ON:
                return look(strip, hasTemp, WARM, 100);
            case SCENE_EVENING:
                return colour(strip, 30, 700, strip ? 300 : 400);
            case SCENE_CINEMA:
                return strip ? dps().fluent("20", true).fluent("21", "white").fluent("22", 100).fluent("23", 0).out : null;
            default:
                return null;
        }
    }

    private static Dps dps() {
        return new Dps();
    }

    /** A JSONObject that takes keys without a checked exception. */
    private static final class Dps {
        final JSONObject out = new JSONObject();

        Dps fluent(String key, Object value) {
            try {
                out.put(key, value);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }
    }
}

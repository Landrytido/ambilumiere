package com.gathin.lights.tablet;

import org.json.JSONObject;

import java.util.Base64;
import java.util.Locale;

/**
 * The colour side of the strip, with nothing Android in it: a palette spread over the strip's ten zones, the mix
 * between two colours, and the datapoint 103 the LSC strip takes (the same bytes the Tuya app writes).
 * A colour is {h, s, v}, each 0..1. Ported from the PC tool lumieres.py; the tests compare the two.
 */
final class LightsMath {
    static final int ZONES = 10;
    /** The strip's own single colour when nothing is known: h, s, v in its units (360, 1000, 1000). */
    static final int[] PURPLE = {270, 600, 980};

    private LightsMath() {
    }

    /** Python's x % 1.0: never negative. */
    private static double frac(double x) {
        return x - Math.floor(x);
    }

    /** "#RRGGBB" (the # is optional) as {r, g, b} in 0..1. */
    static double[] parseHex(String text) {
        String t = text.trim();
        while (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) throw new IllegalArgumentException("not a colour: " + text);
        double[] rgb = new double[3];
        for (int i = 0; i < 3; i++) rgb[i] = Integer.parseInt(t.substring(2 * i, 2 * i + 2), 16) / 255.0;
        return rgb;
    }

    /** The strip's look: no black, no dull grey. A near-neutral colour becomes a soft white, not a tinted one. */
    static double[] lively(double h, double s, double v) {
        if (s < 0.15) return new double[]{h, 0.0, Math.max(v, 0.7)};
        return new double[]{h, Math.max(s, 0.5), Math.min(Math.max(v, 0.6), 1.0)};
    }

    static double[] fromRgb(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double h, s, v = max;
        if (min == max) {
            h = 0.0;
            s = 0.0;
        } else {
            double range = max - min;
            s = range / max;
            double rc = (max - r) / range, gc = (max - g) / range, bc = (max - b) / range;
            if (r == max) h = bc - gc;
            else if (g == max) h = 2.0 + rc - bc;
            else h = 4.0 + gc - rc;
            h = frac(h / 6.0);
        }
        return lively(h, s, v);
    }

    static double[] fromRgb(double[] rgb) {
        return fromRgb(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Between two colours by the shorter way round the hue circle. A white has no hue: the other colour's is kept,
     * so the mix only gains or loses saturation and never sweeps through other colours.
     */
    static double[] mix(double[] a, double[] b, double t) {
        double ah = a[0], bh = b[0];
        if (a[1] < 0.2 && 0.2 <= b[1]) ah = bh;
        else if (b[1] < 0.2 && 0.2 <= a[1]) bh = ah;
        double dh = frac(bh - ah + 0.5) - 0.5;
        return new double[]{frac(ah + dh * t), a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** One colour per zone, evenly along the strip. */
    static double[][] spread(double[][] colors) {
        double[][] out = new double[ZONES][];
        if (colors.length == 1) {
            for (int i = 0; i < ZONES; i++) out[i] = colors[0];
            return out;
        }
        for (int i = 0; i < ZONES; i++) {
            double pos = (double) (i * (colors.length - 1)) / (ZONES - 1);
            int k = Math.min((int) pos, colors.length - 2);
            out[i] = mix(colors[k], colors[k + 1], pos - k);
        }
        return out;
    }

    /** Datapoint 103 as the app writes it: 00 03 00 0a 00, then h, s, v (2 bytes each) per zone. h 0-360, s and v 0-1000. */
    static String payload(double[][] zones) {
        byte[] data = new byte[5 + 6 * zones.length];
        data[1] = 3;
        data[3] = ZONES;
        for (int i = 0; i < zones.length; i++) {
            put(data, 5 + 6 * i, (int) Math.rint(zones[i][0] * 360));
            put(data, 7 + 6 * i, (int) Math.rint(zones[i][1] * 1000));
            put(data, 9 + 6 * i, (int) Math.rint(zones[i][2] * 1000));
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /** Datapoint 103 with one colour over the whole strip; h, s, v in the strip's units. */
    static String single(int h, int s, int v) {
        byte[] data = new byte[11];
        data[1] = 2;
        data[3] = ZONES;
        put(data, 5, h);
        put(data, 7, s);
        put(data, 9, v);
        return Base64.getEncoder().encodeToString(data);
    }

    /** Datapoint 24 of a bulb (and of the strip): "hhhhssssvvvv", h 0-360, s and v 0-1000, hex. */
    static String hex(int h, int s, int v) {
        return String.format(Locale.ROOT, "%04x%04x%04x", h, s, v);
    }

    /** The same from a colour {h, s, v} in 0..1. */
    static String hex(double[] hsv) {
        return hex((int) Math.rint(hsv[0] * 360), (int) Math.rint(hsv[1] * 1000), (int) Math.rint(hsv[2] * 1000));
    }

    /** Datapoint 24 -> {h, s, v} in 0..1, or null when it isn't one. */
    static double[] parseHex24(String hex) {
        if (hex == null || hex.length() < 12) return null;
        try {
            return new double[]{Integer.parseInt(hex.substring(0, 4), 16) / 360.0,
                    Integer.parseInt(hex.substring(4, 8), 16) / 1000.0, Integer.parseInt(hex.substring(8, 12), 16) / 1000.0};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Opaque 0xFFRRGGBB of a colour {h, s, v} in 0..1, for the dots on screen. */
    static int rgb(double h, double s, double v) {
        double hh = frac(h) * 6;
        int sector = (int) hh;
        double f = hh - sector, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double[] c;
        switch (sector) {
            case 0:
                c = new double[]{v, t, p};
                break;
            case 1:
                c = new double[]{q, v, p};
                break;
            case 2:
                c = new double[]{p, v, t};
                break;
            case 3:
                c = new double[]{p, q, v};
                break;
            case 4:
                c = new double[]{t, p, v};
                break;
            default:
                c = new double[]{v, p, q};
        }
        return 0xFF000000 | ((int) Math.rint(c[0] * 255) << 16) | ((int) Math.rint(c[1] * 255) << 8) | (int) Math.rint(c[2] * 255);
    }

    /** What a white of that temperature (0 warm .. 1000 cool) looks like on screen. */
    static int whiteRgb(int temp) {
        double t = Math.min(1000, Math.max(0, temp)) / 1000.0;
        double[][] stops = {{255, 177, 110}, {255, 236, 215}, {205, 224, 255}};   // warm, neutral, cool
        int k = t < 0.5 ? 0 : 1;
        double u = (t - 0.5 * k) / 0.5;
        int rgb = 0xFF000000;
        for (int i = 0; i < 3; i++) rgb |= (int) Math.rint(stops[k][i] + (stops[k + 1][i] - stops[k][i]) * u) << (16 - 8 * i);
        return rgb;
    }

    private static void put(byte[] data, int at, int value) {
        data[at] = (byte) (value >> 8);
        data[at + 1] = (byte) value;
    }

    /** Datapoint 103 -> its colours (one, or a combination), or null when it isn't one. 5 header bytes, then 6 per colour. */
    static double[][] decode103(String value) {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (data.length < 11 || (data.length - 5) % 6 != 0) return null;
        double[][] colors = new double[(data.length - 5) / 6][];
        for (int i = 0; i < colors.length; i++) {
            int at = 5 + 6 * i;
            colors[i] = new double[]{get(data, at) / 360.0, get(data, at + 2) / 1000.0, get(data, at + 4) / 1000.0};
        }
        return colors;
    }

    private static int get(byte[] data, int at) {
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }

    /** The colours a remembered state shows, for the wave to start from; null when it is white, a scene or music. */
    static double[][] originalColors(JSONObject state) {
        if (state == null || !"colour".equals(state.optString("21", null))) return null;
        if (state.has("103")) return decode103(state.optString("103"));
        if (state.has("24")) {
            String hex = state.optString("24");
            if (hex.length() < 12) return null;
            try {
                return new double[][]{{Integer.parseInt(hex.substring(0, 4), 16) / 360.0,
                        Integer.parseInt(hex.substring(4, 8), 16) / 1000.0,
                        Integer.parseInt(hex.substring(8, 12), 16) / 1000.0}};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** One colour per zone for a list of colours: the one colour everywhere, or a palette spread. null for none. */
    static double[][] zonesOf(double[][] colors) {
        if (colors == null || colors.length == 0) return null;
        if (colors.length == 1) {
            double[][] out = new double[ZONES][];
            for (int i = 0; i < ZONES; i++) out[i] = colors[0];
            return out;
        }
        return spread(colors);
    }
}

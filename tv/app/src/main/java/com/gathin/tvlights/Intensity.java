package com.gathin.tvlights;

/**
 * How strongly the lights show the colours of the cover: Doux, Normal or Vif. The palette's colours (already made lively
 * by LightsMath.fromRgb) are scaled just before they become datapoints, for the strip and for the bulbs alike: Vif is the
 * colours as they are, Normal and Doux are dimmer, Doux a little less saturated too. Nothing ever goes above the colour
 * it started from, a neutral white stays white, and a floor keeps the light visible. The numbers to tune are the first
 * constants.
 */
final class Intensity {
    static final int SOFT = 0, NORMAL = 1, LIVELY = 2;

    // ---- to tune: for Doux, Normal and Vif ----------------------------------------------------------------------
    /** The brightness (v) of Vif, times this. */
    static final double[] BRIGHTNESS = {0.55, 0.85, 1.00};
    /** The saturation (s) of Vif, times this. */
    static final double[] SATURATION = {0.85, 1.00, 1.00};
    /** Never dimmer than this (v), whatever the mode. */
    static final double MIN_BRIGHTNESS = 0.20;
    /** A colour is never made paler than this (s), unless it was already. */
    static final double MIN_SATURATION = 0.40;
    /** Below this a colour is a white (no hue): its saturation is left alone. */
    static final double NEUTRAL_SATURATION = 0.15;

    /** What the lights use now; set by the controller from the saved choice and the screen's command. */
    static volatile int mode = NORMAL;

    private Intensity() {
    }

    static int clamp(int m) {
        return m < SOFT || m > LIVELY ? NORMAL : m;
    }

    /** One colour {h, s, v}, each 0..1, at that intensity (a new array, or the same one when nothing changes). */
    static double[] apply(double[] c, int m) {
        int i = clamp(m);
        if (BRIGHTNESS[i] >= 1.0 && SATURATION[i] >= 1.0) return c;
        double v = Math.min(c[2], Math.max(MIN_BRIGHTNESS, c[2] * BRIGHTNESS[i]));
        double s = c[1] < NEUTRAL_SATURATION ? c[1] : Math.min(c[1], Math.max(MIN_SATURATION, c[1] * SATURATION[i]));
        return new double[]{c[0], s, v};
    }

    static double[][] apply(double[][] colors, int m) {
        double[][] out = new double[colors.length][];
        for (int k = 0; k < colors.length; k++) out[k] = apply(colors[k], m);
        return out;
    }
}

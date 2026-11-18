package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tells the lights what the cover looks like, through the system log: "colors=#RRGGBB,..." with four or five vivid,
 * distinct colours of the playing song's cover, most present first, or "colors=off" when there is no cover to show.
 * Something outside this app (a PC on adb logcat) reads the line; the launcher itself needs no network for it.
 * One line per change: MediaListener feeds it from its own NowPlaying, and a line is only logged when it differs from the last.
 */
final class LightsPalette {
    private static final String TAG = "CytvLights";
    private static final String ADDON_PACKAGE = "com.gathin.tvlights";
    private static final String ADDON_ACTION = "com.gathin.tvlights.PALETTE";
    private static final int SIZE = 48;
    /** Hue is sorted into 24 bins of 15 degrees; two picked colours are at least two bins (30 degrees) apart. */
    private static final int BINS = 24;
    private static final int MIN_COLORS = 4, MAX_COLORS = 5;
    /** Pixels duller or darker than this are background, not colour. */
    private static final float MIN_SATURATION = 0.25f, MIN_VALUE = 0.2f;
    /** A hue needs this share of all the colour found to be picked. */
    private static final float MIN_SHARE = 0.03f;

    private static String lastLine;
    private static WeakReference<Bitmap> cachedArt = new WeakReference<>(null);
    private static String cachedColors = "";

    private LightsPalette() {
    }

    /**
     * The song now shown (or null): logs its cover's colours, or "off" when no cover is shown, and hands the same
     * line to the Lumieres add-on when it is installed (an explicit broadcast: the launcher needs no network).
     * Main thread.
     */
    static synchronized void publish(Context ctx, NowPlaying.Track track) {
        Bitmap art = track != null && track.artIsCover ? track.art : null;
        String line;
        if (art == null) {
            line = "off";
        } else {
            if (cachedArt.get() != art) {
                cachedColors = colors(art);
                cachedArt = new WeakReference<>(art);
            }
            line = cachedColors;
        }
        if (line.equals(lastLine)) return;
        lastLine = line;
        Log.i(TAG, "colors=" + line);
        ctx.sendBroadcast(new Intent(ADDON_ACTION).setPackage(ADDON_PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra("colors", line));
    }

    private static String colors(Bitmap art) {
        int[] px = new int[SIZE * SIZE];
        Bitmap small = Bitmap.createScaledBitmap(art, SIZE, SIZE, true);
        small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        if (small != art) small.recycle();

        float[] weight = new float[BINS];
        float[] red = new float[BINS], green = new float[BINS], blue = new float[BINS];
        float total = 0f;
        float[] hsv = new float[3];
        for (int p : px) {
            Color.colorToHSV(p, hsv);
            if (hsv[1] < MIN_SATURATION || hsv[2] < MIN_VALUE) continue;
            int bin = (int) (hsv[0] / 360f * BINS) % BINS;
            float w = hsv[1] * hsv[2];
            weight[bin] += w;
            red[bin] += w * Color.red(p);
            green[bin] += w * Color.green(p);
            blue[bin] += w * Color.blue(p);
            total += w;
        }

        List<Integer> picked = new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        while (total > 0f && out.size() < MAX_COLORS) {
            int best = -1;
            for (int b = 0; b < BINS; b++) {
                if (weight[b] < total * MIN_SHARE || (best >= 0 && weight[b] <= weight[best])) continue;
                boolean apart = true;
                for (int q : picked) {
                    int d = Math.abs(b - q);
                    if (Math.min(d, BINS - d) < 2) apart = false;
                }
                if (apart) best = b;
            }
            if (best < 0) break;
            picked.add(best);
            out.add(Color.rgb(Math.round(red[best] / weight[best]), Math.round(green[best] / weight[best]),
                    Math.round(blue[best] / weight[best])));
        }
        if (out.isEmpty()) out.add(average(px));
        for (int i = 0; out.size() < MIN_COLORS; i++) out.add(out.get(i));   // a cover of one or two hues: repeat them

        StringBuilder line = new StringBuilder();
        for (int c : out) {
            Color.colorToHSV(c, hsv);
            if (hsv[1] >= MIN_SATURATION) {   // lamps show a pale colour as white: keep a hue strong and bright
                hsv[1] = Math.max(hsv[1], 0.5f);   // the add-on's Doux/Normal/Vif sets the strength; keep the cover's own look
                hsv[2] = Math.max(hsv[2], 0.6f);
            }
            if (line.length() > 0) line.append(',');
            line.append(String.format(Locale.US, "#%06X", Color.HSVToColor(hsv) & 0xFFFFFF));
        }
        return line.toString();
    }

    /** A grey or nearly black cover has no vivid hue: its mean colour, so the lights still follow its brightness. */
    private static int average(int[] px) {
        long r = 0, g = 0, b = 0;
        for (int p : px) {
            r += Color.red(p);
            g += Color.green(p);
            b += Color.blue(p);
        }
        int n = Math.max(1, px.length);
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }
}

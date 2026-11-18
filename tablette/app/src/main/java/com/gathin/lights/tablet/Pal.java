package com.gathin.lights.tablet;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** The look: the colours of the light or dark theme, and the three choices of the settings screen (kept between runs). */
final class Pal {
    static final int LAYOUT_GRID = 0, LAYOUT_LIST = 1, LAYOUT_COLUMN = 2;
    static final int THEME_LIGHT = 0, THEME_DARK = 1, THEME_AUTO = 2;
    static final int STYLE_HALO = 0, STYLE_ORB = 1;
    static final int GREEN = 0xFF34C759;

    final boolean dark;
    final int bg, card, text, sub, track, off, selected;
    final int layout, theme, style;

    private Pal(boolean dark, int layout, int theme, int style) {
        this.dark = dark;
        this.layout = layout;
        this.theme = theme;
        this.style = style;
        if (dark) {
            bg = 0xFF0B1410;
            card = 0xFF1C1C1E;
            text = 0xFFF2F2F7;
            sub = 0xFF8E8E93;
            track = 0xFF3A3A3C;
            off = 0xFF48484A;
            selected = 0xFF2C2C2E;
        } else {
            bg = 0xFFF2F2F7;
            card = 0xFFFFFFFF;
            text = 0xFF1C1C1E;
            sub = 0xFF6C6C70;
            track = 0xFFD1D1D6;
            off = 0xFFC7C7CC;
            selected = 0xFFE5E5EA;
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    static Pal load(Context c) {
        SharedPreferences p = prefs(c);
        int theme = p.getInt("theme", THEME_AUTO);
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = theme == THEME_DARK || (theme == THEME_AUTO && night);
        return new Pal(dark, p.getInt("layout", LAYOUT_GRID), theme, p.getInt("style", STYLE_HALO));
    }

    static void save(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }
}

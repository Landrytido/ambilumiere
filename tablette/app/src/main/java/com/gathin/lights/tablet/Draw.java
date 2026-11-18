package com.gathin.lights.tablet;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.TextView;

/** Small helpers for building the screens in code: sizes, rounded backgrounds, text and the switch. */
final class Draw {
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final RectF TMP = new RectF();

    private Draw() {
    }

    static float dpf(float v) {
        return v * Resources.getSystem().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(dpf(v));
    }

    static float spf(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, Resources.getSystem().getDisplayMetrics());
    }

    static int alpha(int color, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    /** t = 0 gives a, 1 gives b; both opaque. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static GradientDrawable rect(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpf(radiusDp));
        return d;
    }

    static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (medium) t.setTypeface(MEDIUM);
        return t;
    }

    static TextView pill(Context c, String s, Pal pal) {
        TextView t = text(c, s, 15, pal.text, true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(26), dp(14), dp(26), dp(14));
        t.setBackground(rect(pal.card, 999));
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** An iOS-style switch, 44 by 26 dp at the given place. */
    static void drawSwitch(Canvas canvas, Paint p, RectF r, boolean on, Pal pal) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? Pal.GREEN : pal.track);
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
        float m = dpf(2.5f);
        float d = r.height() - 2 * m;
        float x = on ? r.right - m - d : r.left + m;
        p.setColor(0xFFFFFFFF);
        TMP.set(x, r.top + m, x + d, r.top + m + d);
        canvas.drawOval(TMP, p);
    }

    static Drawable icon(Context c, int res, int tint) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(tint);
        return d;
    }

    static String colourLabel(int idx) {
        switch (idx) {
            case 0:
                return "Blanc chaud";
            case 1:
                return "Rouge";
            case 2:
                return "Orange";
            case 3:
                return "Jaune";
            case 4:
                return "Vert";
            case 5:
                return "Cyan";
            case 6:
                return "Bleu";
            case 7:
                return "Violet";
            case LightCommands.COOL_WHITE:
                return "Blanc froid";
            default:
                return "Blanc";
        }
    }
}

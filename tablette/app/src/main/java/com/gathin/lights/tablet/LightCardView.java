package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * A light as a card of the "Grille" layout: a switch top right, in the middle the light's colour (a soft wash with
 * the bulb in a circle, or a glass orb), under it the name and one line "Colour · 80 %", and a thin brightness bar at the
 * bottom. Tap the bulb or the switch: on or off. Tap anywhere else, or hold: the light's settings. A light that
 * doesn't answer is grey with "Hors ligne", and a tap tries again.
 */
final class LightCardView extends View {
    interface Actions {
        void toggle(LightUnit unit);

        void open(LightUnit unit);
    }

    private final Pal pal;
    private final LightUnit unit;
    private final Actions actions;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable bulb;
    private final RectF card = new RectF(), sw = new RectF(), tmp = new RectF();
    private final Path clip = new Path();
    private float cx, cy, hitR;
    private final GestureDetector detector;

    LightCardView(Context c, Pal pal, LightUnit unit, Actions actions) {
        super(c);
        this.pal = pal;
        this.unit = unit;
        this.actions = actions;
        this.bulb = c.getDrawable(R.drawable.ic_bulb).mutate();
        namePaint.setTextSize(Draw.spf(14));
        namePaint.setTypeface(Draw.MEDIUM);
        namePaint.setColor(pal.text);
        subPaint.setTextSize(Draw.spf(12));
        subPaint.setColor(pal.sub);
        setContentDescription(unit.cfg.name);
        detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                tap(e.getX(), e.getY());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                LightCardView.this.actions.open(LightCardView.this.unit);
            }
        });
    }

    private void tap(float x, float y) {
        LightUnit.State s = unit.state();
        if (!s.online()) {
            unit.retry();
            return;
        }
        boolean onSwitch = x >= sw.left - Draw.dpf(14) && x <= sw.right + Draw.dpf(14) && y <= sw.bottom + Draw.dpf(14);
        boolean onBulb = Math.hypot(x - cx, y - cy) <= hitR;
        if (onSwitch || onBulb) actions.toggle(unit);
        else actions.open(unit);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) setAlpha(0.88f);
        else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) setAlpha(1f);
        return detector.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        LightUnit.State s = unit.state();
        boolean online = s.online();
        boolean on = online && s.on;
        float b = s.level < 0 ? 1f : s.level / 100f;
        int col = s.rgb;
        float w = getWidth(), h = getHeight();
        float pad = Draw.dpf(16), radius = Draw.dpf(18);
        boolean orb = pal.style == Pal.STYLE_ORB;

        card.set(0, 0, w, h);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.card);
        canvas.drawRoundRect(card, radius, radius, p);

        sw.set(w - pad - Draw.dpf(44), pad, w - pad, pad + Draw.dpf(26));
        float barH = Draw.dpf(4), barTop = h - pad - barH;
        float subBase = barTop - Draw.dpf(12), nameBase = subBase - Draw.dpf(20);
        float r = orb ? Draw.dpf(42) : Draw.dpf(23);
        float lo = sw.bottom + Draw.dpf(6) + r, hi = nameBase - Draw.dpf(18) - r;
        cx = w / 2;
        cy = hi > lo ? Math.max(lo, Math.min(hi, h * 0.46f)) : h * 0.46f;
        hitR = r + Draw.dpf(12);

        if (on) {
            // The wash of colour in the empty middle of the card.
            clip.reset();
            clip.addRoundRect(card, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            float a = 0.16f + 0.30f * b;
            float wash = Math.min(w, h) * 0.62f;
            p.setShader(new RadialGradient(cx, cy, wash, Draw.alpha(col, a), Draw.alpha(col, 0f), Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, wash, p);
            p.setShader(null);
            canvas.restore();
        }

        if (orb) drawOrb(canvas, on, col, b, r);
        else drawHalo(canvas, on, col, r);

        if (online) {
            Draw.drawSwitch(canvas, p, sw, on, pal);
        } else {
            p.setColor(pal.off);
            canvas.drawRoundRect(sw, sw.height() / 2, sw.height() / 2, p);
        }

        float textW = w - 2 * pad;
        canvas.drawText(TextUtils.ellipsize(unit.cfg.name, namePaint, textW, TextUtils.TruncateAt.END).toString(), pad, nameBase, namePaint);
        String line = !online ? "Hors ligne" : !on ? "Éteinte" : Draw.colourLabel(s.colourIdx) + (s.level >= 0 ? " · " + s.level + " %" : "");
        canvas.drawText(TextUtils.ellipsize(line, subPaint, textW, TextUtils.TruncateAt.END).toString(), pad, subBase, subPaint);

        tmp.set(pad, barTop, w - pad, barTop + barH);
        p.setColor(pal.track);
        canvas.drawRoundRect(tmp, barH / 2, barH / 2, p);
        if (on) {
            tmp.set(pad, barTop, pad + (w - 2 * pad) * b, barTop + barH);
            p.setColor(col);
            canvas.drawRoundRect(tmp, barH / 2, barH / 2, p);
        }
    }

    /** "Halo doux": the bulb in a circle tinted with the colour. */
    private void drawHalo(Canvas canvas, boolean on, int col, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? Draw.alpha(col, 0.22f) : pal.off);
        canvas.drawCircle(cx, cy, r, p);
        float g = r * 0.52f;
        bulb.setTint(on ? col : pal.sub);
        bulb.setBounds(Math.round(cx - g), Math.round(cy - g), Math.round(cx + g), Math.round(cy + g));
        bulb.draw(canvas);
    }

    /** "Orbe": a glass ball that glows more the brighter the light, with a white bulb inside. */
    private void drawOrb(Canvas canvas, boolean on, int col, float b, float r) {
        p.setStyle(Paint.Style.FILL);
        if (on) {
            float glow = Draw.dpf(6 + 18 * b);
            float outer = r + glow;
            p.setShader(new RadialGradient(cx, cy, outer, new int[]{Draw.alpha(col, 0.40f * b + 0.10f), Draw.alpha(col, 0.40f * b + 0.10f), Draw.alpha(col, 0f)},
                    new float[]{0f, r / outer, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, outer, p);
            float o = 0.55f + 0.45f * b;
            float hx = cx - r + 2 * r * 0.35f, hy = cy - r + 2 * r * 0.30f;
            p.setShader(new RadialGradient(hx, hy, r * 1.5f, new int[]{Draw.alpha(Draw.mix(col, 0xFFFFFFFF, 0.55f), o), Draw.alpha(col, o)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, p);
            p.setShader(null);
        } else {
            p.setColor(pal.off);
            canvas.drawCircle(cx, cy, r, p);
        }
        float g = r * 0.42f;
        bulb.setTint(on ? 0xFFFFFFFF : pal.sub);
        bulb.setBounds(Math.round(cx - g), Math.round(cy - g), Math.round(cx + g), Math.round(cy + g));
        bulb.draw(canvas);
    }
}

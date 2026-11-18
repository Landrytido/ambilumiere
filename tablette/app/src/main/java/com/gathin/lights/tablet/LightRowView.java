package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * A light as a row, for the layouts "Liste + panneau" (icon, name, one line, switch) and "Colonne" (icon, name,
 * brightness bar, colour dot, switch). Tap the bulb or the switch: on or off; the rest of the row, or a long press:
 * choose the light (list) or open its settings (column). A light that doesn't answer shows "Hors ligne".
 */
final class LightRowView extends View {
    private final Pal pal;
    private final LightUnit unit;
    private final LightCardView.Actions actions;
    private final boolean wide;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable bulb;
    private final RectF box = new RectF(), sw = new RectF(), tmp = new RectF();
    private float cx, cy, hitR;
    private boolean selected;
    private final GestureDetector detector;

    LightRowView(Context c, Pal pal, LightUnit unit, boolean wide, LightCardView.Actions actions) {
        super(c);
        this.pal = pal;
        this.unit = unit;
        this.wide = wide;
        this.actions = actions;
        this.bulb = c.getDrawable(R.drawable.ic_bulb).mutate();
        namePaint.setTextSize(Draw.spf(15));
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
                LightRowView.this.actions.open(LightRowView.this.unit);
            }
        });
    }

    LightUnit unit() {
        return unit;
    }

    void setSelected2(boolean s) {
        if (selected != s) {
            selected = s;
            invalidate();
        }
    }

    private void tap(float x, float y) {
        LightUnit.State s = unit.state();
        if (!s.online()) {
            unit.retry();
            return;
        }
        boolean onSwitch = x >= sw.left - Draw.dpf(14);
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
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Draw.dp(wide ? 76 : 72));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        LightUnit.State s = unit.state();
        boolean online = s.online();
        boolean on = online && s.on;
        float b = s.level < 0 ? 1f : s.level / 100f;
        int col = s.rgb;
        float w = getWidth(), h = getHeight(), pad = Draw.dpf(16), radius = Draw.dpf(18);

        box.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(selected ? pal.selected : pal.card);
        canvas.drawRoundRect(box, radius, radius, p);

        float r = Draw.dpf(22);
        cx = pad + r;
        cy = h / 2;
        hitR = r + Draw.dpf(10);
        p.setColor(on ? Draw.alpha(col, 0.22f) : pal.off);
        canvas.drawCircle(cx, cy, r, p);
        float g = r * 0.55f;
        bulb.setTint(on ? col : pal.sub);
        bulb.setBounds(Math.round(cx - g), Math.round(cy - g), Math.round(cx + g), Math.round(cy + g));
        bulb.draw(canvas);

        sw.set(w - pad - Draw.dpf(44), cy - Draw.dpf(13), w - pad, cy + Draw.dpf(13));
        if (online) {
            Draw.drawSwitch(canvas, p, sw, on, pal);
        } else {
            p.setColor(pal.off);
            canvas.drawRoundRect(sw, sw.height() / 2, sw.height() / 2, p);
        }

        float textX = cx + r + Draw.dpf(14);
        String line = !online ? "Hors ligne" : !on ? "Éteinte" : Draw.colourLabel(s.colourIdx) + (s.level >= 0 ? " · " + s.level + " %" : "");
        if (wide) {
            float nameW = Math.min(Draw.dpf(220), w * 0.30f);
            canvas.drawText(TextUtils.ellipsize(unit.cfg.name, namePaint, nameW, TextUtils.TruncateAt.END).toString(), textX, cy - Draw.dpf(3), namePaint);
            canvas.drawText(TextUtils.ellipsize(line, subPaint, nameW, TextUtils.TruncateAt.END).toString(), textX, cy + Draw.dpf(16), subPaint);
            float dotR = Draw.dpf(8);
            float dotX = sw.left - Draw.dpf(22) - dotR;
            float barL = textX + nameW + Draw.dpf(16), barR = dotX - dotR - Draw.dpf(20);
            if (barR > barL) {
                float bh = Draw.dpf(6);
                tmp.set(barL, cy - bh / 2, barR, cy + bh / 2);
                p.setColor(pal.track);
                canvas.drawRoundRect(tmp, bh / 2, bh / 2, p);
                if (on) {
                    tmp.set(barL, cy - bh / 2, barL + (barR - barL) * b, cy + bh / 2);
                    p.setColor(col);
                    canvas.drawRoundRect(tmp, bh / 2, bh / 2, p);
                }
            }
            p.setColor(on ? col : pal.off);
            canvas.drawCircle(dotX, cy, dotR, p);
        } else {
            float textW = sw.left - Draw.dpf(14) - textX;
            canvas.drawText(TextUtils.ellipsize(unit.cfg.name, namePaint, textW, TextUtils.TruncateAt.END).toString(), textX, cy - Draw.dpf(3), namePaint);
            canvas.drawText(TextUtils.ellipsize(line, subPaint, textW, TextUtils.TruncateAt.END).toString(), textX, cy + Draw.dpf(16), subPaint);
        }
    }
}

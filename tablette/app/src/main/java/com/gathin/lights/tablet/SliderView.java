package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A brightness slider from min to 100 per cent, large enough for a finger. Calls onChange while dragging, about ten times a second. */
final class SliderView extends View {
    interface Listener {
        void onChange(int value, boolean done);
    }

    private static final long GAP_MS = 100;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Pal pal;
    private final int min;
    private int value = 100;
    private int accent;
    private boolean dragging;
    private long lastMs;
    private Listener listener;

    SliderView(Context c, Pal pal, int min) {
        super(c);
        this.pal = pal;
        this.min = min;
        this.accent = pal.text;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void set(int v, int accentColour) {
        if (dragging) return;
        value = Math.max(min, Math.min(100, v));
        accent = accentColour;
        invalidate();
    }

    int value() {
        return value;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Draw.dp(44));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), cy = getHeight() / 2f, th = Draw.dpf(8), knob = Draw.dpf(14);
        float l = knob, rr = w - knob;
        float x = l + (rr - l) * (value - min) / (100f - min);
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.track);
        r.set(l, cy - th / 2, rr, cy + th / 2);
        canvas.drawRoundRect(r, th / 2, th / 2, p);
        p.setColor(accent);
        r.set(l, cy - th / 2, x, cy + th / 2);
        canvas.drawRoundRect(r, th / 2, th / 2, p);
        p.setColor(0x33000000);
        canvas.drawCircle(x, cy + Draw.dpf(1), knob, p);
        p.setColor(0xFFFFFFFF);
        canvas.drawCircle(x, cy, knob - Draw.dpf(1), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (!dragging) return false;
        float knob = Draw.dpf(14);
        float f = (e.getX() - knob) / Math.max(1f, getWidth() - 2 * knob);
        value = Math.max(min, Math.min(100, Math.round(min + f * (100 - min))));
        boolean done = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        invalidate();
        long now = System.currentTimeMillis();
        if (listener != null && (done || now - lastMs >= GAP_MS)) {
            lastMs = now;
            listener.onChange(value, done);
        }
        if (done) dragging = false;
        return true;
    }
}

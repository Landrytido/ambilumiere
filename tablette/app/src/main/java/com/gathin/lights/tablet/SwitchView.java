package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** The on/off switch, for the places that are not a card (the settings, the timer, the light's sheet). */
final class SwitchView extends View {
    interface Listener {
        void onChange(boolean on);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Pal pal;
    private boolean on;
    private Listener listener;

    SwitchView(Context c, Pal pal) {
        super(c);
        this.pal = pal;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void set(boolean v) {
        if (on != v) {
            on = v;
            invalidate();
        }
    }

    boolean isOn() {
        return on;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Draw.dp(60), Draw.dp(44));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = Draw.dpf(44), h = Draw.dpf(26);
        float l = (getWidth() - w) / 2, t = (getHeight() - h) / 2;
        r.set(l, t, l + w, t + h);
        Draw.drawSwitch(canvas, p, r, on, pal);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            on = !on;
            invalidate();
            if (listener != null) listener.onChange(on);
        }
        return true;
    }
}

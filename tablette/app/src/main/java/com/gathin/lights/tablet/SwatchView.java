package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** The eight ready colours (warm white, red, orange, yellow, green, cyan, blue, violet) as dots in a row. */
final class SwatchView extends View {
    interface Listener {
        void onPick(int swatch);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Pal pal;
    private int selected = -1;
    private Listener listener;

    SwatchView(Context c, Pal pal) {
        super(c);
        this.pal = pal;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void set(int swatch) {
        if (selected != swatch) {
            selected = swatch;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Draw.dp(48));
    }

    private float slot() {
        return getWidth() / (float) LightCommands.SWATCHES;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float slot = slot(), cy = getHeight() / 2f, r = Math.min(Draw.dpf(16), slot / 2 - Draw.dpf(3));
        for (int i = 0; i < LightCommands.SWATCHES; i++) {
            float cx = slot * (i + 0.5f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(LightCommands.swatchRgb(i));
            canvas.drawCircle(cx, cy, r, p);
            if (i == 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Draw.dpf(1));
                p.setColor(Draw.alpha(pal.sub, 0.5f));
                canvas.drawCircle(cx, cy, r, p);
            }
            if (i == selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Draw.dpf(2.5f));
                p.setColor(pal.text);
                canvas.drawCircle(cx, cy, r + Draw.dpf(4), p);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int i = Math.max(0, Math.min(LightCommands.SWATCHES - 1, (int) (e.getX() / slot())));
            selected = i;
            invalidate();
            if (listener != null) listener.onPick(i);
            return true;
        }
        return true;
    }
}

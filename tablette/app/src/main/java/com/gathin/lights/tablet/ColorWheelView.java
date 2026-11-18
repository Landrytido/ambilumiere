package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * The free colour wheel: the angle is the hue, the distance from the centre the saturation (white in the middle).
 * Calls onPick while the finger moves, at most about ten times a second, and once more when it lifts.
 */
final class ColorWheelView extends View {
    interface Listener {
        /** h in degrees, s 0..1; done: the finger lifted. */
        void onPick(double h, double s, boolean done);
    }

    private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
    private static final long GAP_MS = 100;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Pal pal;
    private Listener listener;
    private double hue, sat;
    private boolean dragging;
    private long lastMs;

    ColorWheelView(Context c, Pal pal) {
        super(c);
        this.pal = pal;
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isDragging() {
        return dragging;
    }

    void set(double hueDeg, double s) {
        if (dragging) return;
        hue = hueDeg;
        sat = s;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int size = Math.min(w, Draw.dp(280));
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) size = Math.min(size, MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, handle = Draw.dpf(13);
        float r = Math.min(cx, cy) - handle;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new SweepGradient(cx, cy, HUES, null));
        canvas.drawCircle(cx, cy, r, p);
        p.setShader(new RadialGradient(cx, cy, r, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, p);
        p.setShader(null);

        double a = Math.toRadians(hue);
        float hx = cx + (float) (Math.cos(a) * sat * r), hy = cy + (float) (Math.sin(a) * sat * r);
        p.setColor(LightsMath.rgb(hue / 360.0, sat, 1.0));
        canvas.drawCircle(hx, hy, handle, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Draw.dpf(3));
        p.setColor(0xFFFFFFFF);
        canvas.drawCircle(hx, hy, handle, p);
        p.setStrokeWidth(Draw.dpf(1));
        p.setColor(0x33000000);
        canvas.drawCircle(hx, hy, handle + Draw.dpf(1.5f), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (!dragging) return false;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - Draw.dpf(13);
        float dx = e.getX() - cx, dy = e.getY() - cy;
        hue = (Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360;
        sat = Math.min(1.0, Math.hypot(dx, dy) / r);
        boolean done = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        invalidate();
        long now = System.currentTimeMillis();
        if (listener != null && (done || now - lastMs >= GAP_MS)) {
            lastMs = now;
            listener.onPick(hue, sat, done);
        }
        if (done) dragging = false;
        return true;
    }
}

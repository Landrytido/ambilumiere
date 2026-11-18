package com.gathin.tvlights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The few drawn pieces of the screen: a switch, a thin bar, a colour swatch. All flat, all from the same few colours. */
final class Widgets {
    static final int BG = 0xFF0B1410, CARD = 0xFF1C1C1E, TEXT = 0xFFF2F2F7, SECONDARY = 0xFF8E8E93, OFF = 0xFF636366;
    static final int SWITCH_ON = 0xFF34C759, SWITCH_OFF = 0xFF48484A, TRACK = 0xFF3A3A3C;

    private Widgets() {
    }

    static float dp(Context ctx, float dp) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }

    /** An iOS-style switch: a rounded track and a white thumb. It only shows the state; the card or row around it is what is pressed. */
    static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float width, height;
        private boolean on;

        SwitchView(Context ctx, float widthDp, float heightDp) {
            super(ctx);
            width = dp(ctx, widthDp);
            height = dp(ctx, heightDp);
        }

        void setChecked(boolean value) {
            if (on == value) return;
            on = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(width), Math.round(height));
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setColor(on ? SWITCH_ON : SWITCH_OFF);
            rect.set(0, 0, width, height);
            c.drawRoundRect(rect, height / 2, height / 2, paint);
            float inset = height * 0.1f, r = height / 2 - inset;
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(on ? width - inset - r : inset + r, height / 2, r, paint);
        }
    }

    /** A thin rounded bar filled from the left: the brightness. */
    static final class BarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float height;
        private float value;

        BarView(Context ctx, float heightDp) {
            super(ctx);
            height = dp(ctx, heightDp);
        }

        void setValue(double v) {
            float next = (float) Math.min(1, Math.max(0, v));
            if (next == value) return;
            value = next;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Math.round(height));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            paint.setColor(TRACK);
            rect.set(0, 0, w, height);
            c.drawRoundRect(rect, height / 2, height / 2, paint);
            if (value <= 0) return;
            paint.setColor(TEXT);
            rect.set(0, 0, Math.max(height, w * value), height);
            c.drawRoundRect(rect, height / 2, height / 2, paint);
        }
    }

    /** A round colour swatch; the chosen one has a ring round it with a small gap, and the focused one a thin outline further out. */
    static final class SwatchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float size, radius;
        private int color;
        private boolean chosen;

        SwatchView(Context ctx, int color, float sizeDp) {
            super(ctx);
            this.color = color;
            size = dp(ctx, sizeDp + 16);
            radius = dp(ctx, sizeDp / 2);
            setFocusable(true);
            setClickable(true);
        }

        void setChosen(boolean value) {
            if (chosen == value) return;
            chosen = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(size), Math.round(size));
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = size / 2, cy = size / 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            c.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(TEXT);
            if (chosen) {
                paint.setStrokeWidth(dp(getContext(), 2));
                c.drawCircle(cx, cy, radius + dp(getContext(), 4), paint);
            }
            if (isFocused()) {
                paint.setStrokeWidth(dp(getContext(), 1.5f));
                c.drawCircle(cx, cy, radius + dp(getContext(), 6.5f), paint);
            }
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(gained, direction, previous);
            invalidate();
        }
    }
}

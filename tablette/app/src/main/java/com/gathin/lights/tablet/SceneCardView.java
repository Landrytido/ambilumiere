package com.gathin.lights.tablet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * A scene as a card: its name and, for each light, a small block showing the colour it will have (paler when the
 * brightness is low, grey when the light is off in this scene), with the light's first word under it. A card with
 * a dashed outline and a plus, "Nouvelle scène", is the same view with no scene.
 */
final class SceneCardView extends View {
    interface Actions {
        void run(SceneStore.Scene scene);

        void more(SceneStore.Scene scene);

        void create();
    }

    private final Pal pal;
    private final SceneStore.Scene scene;
    private final List<LightsConfig.Light> lights;
    private final Actions actions;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tinyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final GestureDetector detector;
    private long flashUntil;

    SceneCardView(Context c, Pal pal, SceneStore.Scene scene, List<LightsConfig.Light> lights, Actions actions) {
        super(c);
        this.pal = pal;
        this.scene = scene;
        this.lights = lights;
        this.actions = actions;
        namePaint.setTextSize(Draw.spf(16));
        namePaint.setTypeface(Draw.MEDIUM);
        namePaint.setColor(scene == null ? pal.sub : pal.text);
        tinyPaint.setTextSize(Draw.spf(10));
        tinyPaint.setColor(pal.sub);
        tinyPaint.setTextAlign(Paint.Align.CENTER);
        setContentDescription(scene == null ? "Nouvelle scène" : scene.name);
        detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (SceneCardView.this.scene == null) {
                    SceneCardView.this.actions.create();
                } else {
                    flashUntil = System.currentTimeMillis() + 700;
                    postInvalidateDelayed(720);
                    invalidate();
                    SceneCardView.this.actions.run(SceneCardView.this.scene);
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (SceneCardView.this.scene != null && !SceneCardView.this.scene.isBuiltin()) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    SceneCardView.this.actions.more(SceneCardView.this.scene);
                }
            }
        });
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
        float w = getWidth(), h = getHeight(), pad = Draw.dpf(16), radius = Draw.dpf(18);
        r.set(0, 0, w, h);
        if (scene == null) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Draw.dpf(1.5f));
            p.setColor(pal.track);
            p.setPathEffect(new DashPathEffect(new float[]{Draw.dpf(7), Draw.dpf(6)}, 0));
            r.inset(Draw.dpf(1), Draw.dpf(1));
            canvas.drawRoundRect(r, radius, radius, p);
            p.setPathEffect(null);
            float cx = w / 2, cy = h / 2 - Draw.dpf(10), a = Draw.dpf(11);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Draw.dpf(2.5f));
            p.setColor(pal.sub);
            canvas.drawLine(cx - a, cy, cx + a, cy, p);
            canvas.drawLine(cx, cy - a, cx, cy + a, p);
            canvas.drawText("Nouvelle scène", cx - namePaint.measureText("Nouvelle scène") / 2, cy + Draw.dpf(40), namePaint);
            return;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.card);
        canvas.drawRoundRect(r, radius, radius, p);
        canvas.drawText(TextUtils.ellipsize(scene.name, namePaint, w - 2 * pad, TextUtils.TruncateAt.END).toString(), pad, pad + Draw.dpf(15), namePaint);

        int n = lights.size();
        if (n > 0) {
            float gap = Draw.dpf(8);
            float bw = Math.min(Draw.dpf(46), (w - 2 * pad - gap * (n - 1)) / n);
            float bh = Draw.dpf(30);
            float total = bw * n + gap * (n - 1);
            float x = pad + Math.max(0, (w - 2 * pad - total) / 2) * 0;
            float top = h - pad - Draw.dpf(14) - bh;
            for (LightsConfig.Light l : lights) {
                SceneStore.Setting s = scene.settingFor(l);
                int fill;
                if (s == null) fill = Draw.alpha(pal.track, 0.35f);
                else if (!s.on) fill = pal.off;
                else fill = Draw.mix(pal.card, s.rgb(), 0.28f + 0.72f * s.level / 100f);
                p.setColor(fill);
                r.set(x, top, x + bw, top + bh);
                canvas.drawRoundRect(r, Draw.dpf(9), Draw.dpf(9), p);
                String first = l.name.split("\\s+")[0];
                canvas.drawText(TextUtils.ellipsize(first, tinyPaint, bw + gap, TextUtils.TruncateAt.END).toString(), x + bw / 2, top + bh + Draw.dpf(12), tinyPaint);
                x += bw + gap;
            }
        }
        if (System.currentTimeMillis() < flashUntil) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Draw.dpf(2));
            p.setColor(Pal.GREEN);
            r.set(0, 0, w, h);
            r.inset(Draw.dpf(1), Draw.dpf(1));
            canvas.drawRoundRect(r, radius, radius, p);
        }
    }
}

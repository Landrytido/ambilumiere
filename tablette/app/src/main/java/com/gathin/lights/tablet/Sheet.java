package com.gathin.lights.tablet;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** A card that slides over the bottom of the screen (the light's settings, the new-scene form). */
final class Sheet {
    private Sheet() {
    }

    static Dialog show(Activity a, Pal pal, View content, int widthDp) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout holder = new FrameLayout(a);
        holder.setBackground(Draw.rect(pal.bg, 28));
        holder.setPadding(Draw.dp(24), Draw.dp(24), Draw.dp(24), Draw.dp(24));
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        holder.addView(scroll);
        d.setContentView(holder);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            int screenW = a.getResources().getDisplayMetrics().widthPixels;
            int screenH = a.getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min(screenW - Draw.dp(24), Draw.dp(widthDp)), WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = Draw.dp(12);
            lp.dimAmount = 0.5f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            holder.setMinimumHeight(0);
            // Never taller than the screen: the content scrolls inside.
            scroll.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            w.getDecorView().setMinimumHeight(0);
            holder.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                int max = screenH - Draw.dp(80);
                if (w.getAttributes().height != WindowManager.LayoutParams.WRAP_CONTENT) return;
                if (holder.getHeight() > max) w.setLayout(w.getAttributes().width, max);
            });
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
        return d;
    }
}

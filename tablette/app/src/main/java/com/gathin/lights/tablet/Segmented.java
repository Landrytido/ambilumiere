package com.gathin.lights.tablet;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A row of pills where one is chosen: "Grille | Liste + panneau | Colonne", "Lumières | Scènes"... */
final class Segmented extends LinearLayout {
    interface Listener {
        void onSelect(int index);
    }

    private final Pal pal;
    private final TextView[] pills;
    private final Listener listener;
    private int selected;

    Segmented(Context c, Pal pal, String[] labels, int selected, Listener listener) {
        super(c);
        this.pal = pal;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setBackground(Draw.rect(pal.card, 999));
        setPadding(Draw.dp(4), Draw.dp(4), Draw.dp(4), Draw.dp(4));
        pills = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView t = Draw.text(c, labels[i], 14, pal.sub, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine();
            t.setPadding(Draw.dp(18), Draw.dp(10), Draw.dp(18), Draw.dp(10));
            final int index = i;
            t.setOnClickListener(v -> {
                if (index == this.selected) return;
                select(index);
                this.listener.onSelect(index);
            });
            pills[i] = t;
            addView(t, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        select(selected);
    }

    void select(int index) {
        selected = index;
        for (int i = 0; i < pills.length; i++) {
            boolean s = i == index;
            pills[i].setTextColor(s ? pal.text : pal.sub);
            pills[i].setBackground(s ? Draw.rect(pal.selected, 999) : null);
        }
    }
}

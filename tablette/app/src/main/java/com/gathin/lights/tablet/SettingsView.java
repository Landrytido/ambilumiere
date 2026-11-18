package com.gathin.lights.tablet;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The settings: how the home screen is laid out, the theme, the style of the cards, and the import of the lights file. */
final class SettingsView extends ScrollView {
    SettingsView(MainActivity a, Pal pal) {
        super(a);
        setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        col.addView(section(a, pal, "Disposition", new String[]{"Grille", "Liste + panneau", "Colonne"}, pal.layout, i -> {
            Pal.save(a, "layout", i);
            a.rebuild();
        }));
        col.addView(section(a, pal, "Thème", new String[]{"Clair", "Sombre", "Auto"}, pal.theme, i -> {
            Pal.save(a, "theme", i);
            a.rebuild();
        }));
        col.addView(section(a, pal, "Style des cartes", new String[]{"Halo doux", "Orbe"}, pal.style, i -> {
            Pal.save(a, "style", i);
            a.rebuild();
        }));

        col.addView(label(a, pal, "Lumières"));
        TextView imp = Draw.pill(a, "Importer la configuration", pal);
        imp.setOnClickListener(v -> a.pickConfig());
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        il.topMargin = Draw.dp(10);
        col.addView(imp, il);
        TextView hint = Draw.text(a, "Choisissez le fichier lights.json : l’appli en garde une copie et se reconnecte à vos lumières.", 12, pal.sub, false);
        hint.setLineSpacing(Draw.dpf(2), 1f);
        hint.setPadding(0, Draw.dp(8), 0, 0);
        col.addView(hint);

        TextView version = Draw.text(a, "Lumières · version " + BuildConfig.VERSION_NAME, 12, pal.sub, false);
        version.setGravity(Gravity.START);
        version.setPadding(0, Draw.dp(30), 0, Draw.dp(12));
        col.addView(version);
    }

    private static TextView label(MainActivity a, Pal pal, String s) {
        TextView t = Draw.text(a, s, 13, pal.sub, true);
        t.setPadding(0, Draw.dp(22), 0, 0);
        return t;
    }

    private static LinearLayout section(MainActivity a, Pal pal, String title, String[] labels, int selected, Segmented.Listener l) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(label(a, pal, title));
        Segmented seg = new Segmented(a, pal, labels, selected, l);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Draw.dp(10);
        box.addView(seg, sl);
        return box;
    }
}

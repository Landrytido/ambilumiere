package com.gathin.lights.tablet;

import android.app.Dialog;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: every light, laid out in one of three ways chosen in the settings (cards in a grid, a list with a panel
 * for the chosen light, or a column of rows). Without a config it asks for one instead.
 */
final class HomeView extends FrameLayout implements LightCardView.Actions {
    private final MainActivity activity;
    private final Hub hub;
    private final Pal pal;
    private final List<View> items = new ArrayList<>();
    private LightPanel inline;
    private FrameLayout inlineHolder;
    private LightPanel sheetPanel;
    private Dialog sheet;
    private int chosen;

    HomeView(MainActivity a, Hub hub, Pal pal) {
        super(a);
        this.activity = a;
        this.hub = hub;
        this.pal = pal;
        build();
    }

    private void build() {
        removeAllViews();
        items.clear();
        List<LightUnit> units = hub.units();
        if (units.isEmpty()) {
            addView(emptyState(), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return;
        }
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        switch (pal.layout) {
            case Pal.LAYOUT_LIST:
                buildList(units, landscape);
                break;
            case Pal.LAYOUT_COLUMN:
                buildColumn(units);
                break;
            default:
                buildGrid(units, landscape);
        }
    }

    private void buildGrid(List<LightUnit> units, boolean landscape) {
        int n = units.size();
        int cols = landscape ? Math.min(n, 4) : Math.min(n, 2);
        FlowGrid grid = new FlowGrid(activity, cols, 14, 0, 1.45f);
        for (LightUnit u : units) {
            LightCardView v = new LightCardView(activity, pal, u, this);
            items.add(v);
            grid.addView(v);
        }
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void buildColumn(List<LightUnit> units) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows(units, true));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private LinearLayout rows(List<LightUnit> units, boolean wide) {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        for (LightUnit u : units) {
            LightRowView v = new LightRowView(activity, pal, u, wide, this);
            items.add(v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Draw.dp(10);
            col.addView(v, lp);
        }
        return col;
    }

    private void buildList(List<LightUnit> units, boolean landscape) {
        if (chosen >= units.size()) chosen = 0;
        ScrollView listScroll = new ScrollView(activity);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.addView(rows(units, false));
        inlineHolder = new FrameLayout(activity);
        inlineHolder.setBackground(Draw.rect(pal.card, 22));
        inlineHolder.setPadding(Draw.dp(24), Draw.dp(22), Draw.dp(24), Draw.dp(22));
        showInline(units.get(chosen));
        ScrollView panelScroll = new ScrollView(activity);
        panelScroll.setVerticalScrollBarEnabled(false);
        panelScroll.addView(inlineHolder);
        if (landscape) {
            LinearLayout row = new LinearLayout(activity);
            row.addView(listScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5);
            pl.leftMargin = Draw.dp(18);
            row.addView(panelScroll, pl);
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout all = new LinearLayout(activity);
            all.setOrientation(LinearLayout.VERTICAL);
            all.addView(rows(units, false));
            all.removeAllViews();
            items.clear();
            LinearLayout rows = rows(units, false);
            all.addView(rows);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pl.topMargin = Draw.dp(8);
            all.addView(inlineHolder.getParent() == null ? inlineHolder : new View(activity), pl);
            ScrollView whole = new ScrollView(activity);
            whole.setVerticalScrollBarEnabled(false);
            whole.addView(all);
            addView(whole, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        markChosen();
    }

    private void showInline(LightUnit u) {
        inlineHolder.removeAllViews();
        inline = new LightPanel(activity, pal, u);
        inlineHolder.addView(inline);
    }

    private void markChosen() {
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            if (v instanceof LightRowView) ((LightRowView) v).setSelected2(pal.layout == Pal.LAYOUT_LIST && i == chosen);
        }
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Draw.dp(32), Draw.dp(32), Draw.dp(32), Draw.dp(32));
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(Draw.icon(activity, R.drawable.ic_bulb, pal.sub));
        box.addView(icon, new LinearLayout.LayoutParams(Draw.dp(72), Draw.dp(72)));
        TextView title = Draw.text(activity, "Aucune lumière pour l’instant", 20, pal.text, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Draw.dp(18);
        box.addView(title, tl);
        String problem = hub.config().problem;
        String help = problem == null || "missing".equals(problem)
                ? "Choisissez le fichier de configuration des lumières (lights.json) pour les retrouver ici."
                : "Ce fichier n’a pas pu être utilisé. Vérifiez-le, puis choisissez-le de nouveau.";
        TextView body = Draw.text(activity, help, 15, pal.sub, false);
        body.setGravity(Gravity.CENTER);
        body.setMaxWidth(Draw.dp(420));
        body.setLineSpacing(Draw.dpf(3), 1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Draw.dp(10);
        box.addView(body, bl);
        TextView button = Draw.text(activity, "Importer la configuration", 16, pal.bg, true);
        button.setBackground(Draw.rect(pal.text, 999));
        button.setPadding(Draw.dp(30), Draw.dp(15), Draw.dp(30), Draw.dp(15));
        button.setOnClickListener(v -> activity.pickConfig());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Draw.dp(26);
        box.addView(button, cl);
        return box;
    }

    /** The lights' state changed: redraw. */
    void refresh() {
        for (View v : items) v.invalidate();
        if (inline != null) inline.sync();
        if (sheetPanel != null) sheetPanel.sync();
    }

    void dispose() {
        if (sheet != null) sheet.dismiss();
        sheet = null;
        sheetPanel = null;
    }

    @Override
    public void toggle(LightUnit unit) {
        unit.setPower(!unit.state().on);
    }

    @Override
    public void open(LightUnit unit) {
        if (pal.layout == Pal.LAYOUT_LIST) {
            chosen = Math.max(0, hub.units().indexOf(unit));
            showInline(unit);
            markChosen();
            return;
        }
        if (sheet != null) sheet.dismiss();
        sheetPanel = new LightPanel(activity, pal, unit);
        sheet = Sheet.show(activity, pal, sheetPanel, 460);
        sheet.setOnDismissListener(d -> {
            sheetPanel = null;
            sheet = null;
        });
    }
}

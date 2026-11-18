package com.gathin.lights.tablet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The scenes screen: a card for each scene (tap runs it), the form to make a new one, and the switch-off timer. */
final class ScenesView extends ScrollView implements SceneCardView.Actions {
    private static final long TICK_MS = 15_000;

    private final MainActivity activity;
    private final Hub hub;
    private final Pal pal;
    private final LinearLayout column;
    private final LinearLayout gridHolder, timerBox;
    private List<SceneStore.Scene> scenes;
    private Dialog editor;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            buildTimer();
            postDelayed(this, TICK_MS);
        }
    };

    ScenesView(MainActivity a, Hub hub, Pal pal) {
        super(a);
        this.activity = a;
        this.hub = hub;
        this.pal = pal;
        setVerticalScrollBarEnabled(false);
        column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        gridHolder = new LinearLayout(a);
        column.addView(gridHolder, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        timerBox = new LinearLayout(a);
        timerBox.setOrientation(LinearLayout.VERTICAL);
        timerBox.setBackground(Draw.rect(pal.card, 18));
        timerBox.setPadding(Draw.dp(20), Draw.dp(18), Draw.dp(20), Draw.dp(18));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Draw.dp(22);
        tl.bottomMargin = Draw.dp(12);
        column.addView(timerBox, tl);
        reload();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    void dispose() {
        if (editor != null) editor.dismiss();
        editor = null;
    }

    /** The lights' state changed: the previews only depend on the scenes, so nothing to redraw. */
    void refresh() {
    }

    private void reload() {
        scenes = SceneStore.load(activity);
        buildGrid();
        buildTimer();
    }

    private List<LightsConfig.Light> lights() {
        List<LightsConfig.Light> out = new ArrayList<>();
        LightsConfig.Result r = hub.config();
        if (r.config != null) out.addAll(r.config.lights);
        return out;
    }

    private void buildGrid() {
        gridHolder.removeAllViews();
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        FlowGrid grid = new FlowGrid(activity, landscape ? 4 : 2, 14, 132, 1f);
        List<LightsConfig.Light> lights = lights();
        for (SceneStore.Scene s : scenes) grid.addView(new SceneCardView(activity, pal, s, lights, this));
        grid.addView(new SceneCardView(activity, pal, null, lights, this));
        gridHolder.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---- scenes ---------------------------------------------------------------------------------------------------

    @Override
    public void run(SceneStore.Scene scene) {
        hub.runScene(scene);
    }

    @Override
    public void more(SceneStore.Scene scene) {
        String[] items = {"Renommer", "Modifier les lumières", "Supprimer"};
        new AlertDialog.Builder(new ContextThemeWrapper(activity, activity.dialogTheme())).setTitle(scene.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) rename(scene);
                    else if (which == 1) edit(scene);
                    else delete(scene);
                }).show();
    }

    @Override
    public void create() {
        edit(null);
    }

    private void persist() {
        SceneStore.save(activity, scenes);
        buildGrid();
    }

    private void delete(SceneStore.Scene scene) {
        new AlertDialog.Builder(new ContextThemeWrapper(activity, activity.dialogTheme())).setTitle("Supprimer « " + scene.name + " » ?")
                .setPositiveButton("Supprimer", (d, w) -> {
                    scenes.remove(scene);
                    persist();
                }).setNegativeButton("Garder", null).show();
    }

    private void rename(SceneStore.Scene scene) {
        EditText name = new EditText(activity);
        name.setText(scene.name);
        name.setSelectAllOnFocus(true);
        name.setSingleLine();
        FrameWrap wrap = new FrameWrap(activity, name);
        new AlertDialog.Builder(new ContextThemeWrapper(activity, activity.dialogTheme())).setTitle("Nom de la scène").setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    int i = scenes.indexOf(scene);
                    if (i >= 0) scenes.set(i, scene.renamed(n));
                    persist();
                }).setNegativeButton("Annuler", null).show();
    }

    /** A little padding round a dialog's field. */
    private static final class FrameWrap extends LinearLayout {
        FrameWrap(Context c, android.view.View child) {
            super(c);
            setPadding(Draw.dp(22), Draw.dp(8), Draw.dp(22), 0);
            addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    /** The form: a name and, for each light, on or off, a colour and a brightness. existing: the scene to change, or null for a new one. */
    private void edit(SceneStore.Scene existing) {
        Map<String, SceneStore.Setting> cur = new LinkedHashMap<>();
        List<LightsConfig.Light> lights = lights();
        for (LightsConfig.Light l : lights) {
            SceneStore.Setting s = existing == null ? fromLight(l) : existing.settingFor(l);
            cur.put(l.id, s == null ? fromLight(l) : s);
        }
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(Draw.text(activity, existing == null ? "Nouvelle scène" : "Modifier la scène", 20, pal.text, true));
        EditText name = new EditText(activity);
        name.setHint("Nom de la scène");
        name.setText(existing == null ? "" : existing.name);
        name.setSingleLine();
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setTextColor(pal.text);
        name.setHintTextColor(pal.sub);
        name.setTextSize(16);
        name.setBackground(Draw.rect(pal.card, 14));
        name.setPadding(Draw.dp(16), Draw.dp(14), Draw.dp(16), Draw.dp(14));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Draw.dp(14);
        form.addView(name, nl);

        for (LightsConfig.Light l : lights) form.addView(lightBlock(l, cur));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        TextView cancel = Draw.pill(activity, "Annuler", pal);
        TextView save = Draw.text(activity, "Enregistrer", 15, pal.bg, true);
        save.setBackground(Draw.rect(pal.text, 999));
        save.setPadding(Draw.dp(26), Draw.dp(14), Draw.dp(26), Draw.dp(14));
        save.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = Draw.dp(10);
        buttons.addView(cancel);
        buttons.addView(save, sl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Draw.dp(18);
        form.addView(buttons, bl);

        if (editor != null) editor.dismiss();
        editor = Sheet.show(activity, pal, form, 560);
        editor.setOnDismissListener(d -> editor = null);
        cancel.setOnClickListener(v -> editor.dismiss());
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Toast.makeText(activity, "Donnez un nom à la scène.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (existing == null) {
                scenes.add(new SceneStore.Scene(SceneStore.newId(), n, null, cur));
            } else {
                int i = scenes.indexOf(existing);
                if (i >= 0) scenes.set(i, new SceneStore.Scene(existing.id, n, null, cur));
            }
            persist();
            editor.dismiss();
        });
    }

    private SceneStore.Setting fromLight(LightsConfig.Light l) {
        for (LightUnit u : hub.units()) {
            if (!u.cfg.id.equals(l.id)) continue;
            LightUnit.State s = u.state();
            if (!s.online()) break;
            if (!s.on) return SceneStore.Setting.off();
            return SceneStore.Setting.swatch(s.colourIdx < LightCommands.SWATCHES ? s.colourIdx : 0, s.level < 0 ? 100 : s.level);
        }
        return SceneStore.Setting.swatch(0, 100);
    }

    private LinearLayout lightBlock(LightsConfig.Light l, Map<String, SceneStore.Setting> cur) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackground(Draw.rect(pal.card, 16));
        block.setPadding(Draw.dp(16), Draw.dp(10), Draw.dp(16), Draw.dp(10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Draw.dp(12);
        block.setLayoutParams(bp);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Draw.text(activity, l.name, 15, pal.text, true);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        SwitchView on = new SwitchView(activity, pal);
        head.addView(on);
        block.addView(head);

        SwatchView swatches = new SwatchView(activity, pal);
        SliderView slider = new SliderView(activity, pal, LightCommands.MIN_LEVEL);
        TextView percent = Draw.text(activity, "", 12, pal.sub, false);
        block.addView(swatches);
        LinearLayout lv = new LinearLayout(activity);
        lv.setGravity(Gravity.CENTER_VERTICAL);
        lv.addView(Draw.text(activity, "Luminosité", 13, pal.sub, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        lv.addView(percent);
        block.addView(lv);
        block.addView(slider);

        SceneStore.Setting start = cur.get(l.id);
        on.set(start.on);
        swatches.set(start.swatchIndex());
        slider.set(start.level, pal.text);
        percent.setText(start.level + " %");
        Runnable dim = () -> block.setAlpha(cur.get(l.id).on ? 1f : 0.55f);
        dim.run();

        on.setListener(v -> {
            SceneStore.Setting s = cur.get(l.id);
            cur.put(l.id, new SceneStore.Setting(v, s.white, s.h, s.s, s.level));
            dim.run();
        });
        swatches.setListener(i -> {
            SceneStore.Setting s = cur.get(l.id);
            cur.put(l.id, SceneStore.Setting.swatch(i, s.level));
            on.set(true);
            dim.run();
        });
        slider.setListener((v, done) -> {
            SceneStore.Setting s = cur.get(l.id);
            cur.put(l.id, new SceneStore.Setting(s.on, s.white, s.h, s.s, v));
            percent.setText(v + " %");
        });
        return block;
    }

    // ---- the timer ------------------------------------------------------------------------------------------------

    private static String clock(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return String.format(Locale.FRANCE, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    private void buildTimer() {
        timerBox.removeAllViews();
        timerBox.addView(Draw.text(activity, "Minuteur : tout éteindre dans", 14, pal.text, true));

        long end = TimerStore.countdownEnd(activity);
        if (end > 0) {
            long left = Math.max(1, (end - System.currentTimeMillis() + 59_999) / 60_000);
            String remaining = left >= 60 ? (left / 60) + " h " + String.format(Locale.FRANCE, "%02d", left % 60) : left + " min";
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Draw.text(activity, "Dans " + remaining, 26, pal.text, true));
            TextView at = Draw.text(activity, "Les lumières s’éteindront à " + clock(end), 13, pal.sub, false);
            at.setPadding(0, Draw.dp(4), 0, 0);
            words.addView(at);
            row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView cancel = Draw.pill(activity, "Annuler", pal);
            cancel.setBackground(Draw.rect(pal.selected, 999));
            cancel.setOnClickListener(v -> {
                TimerStore.cancelCountdown(activity);
                buildTimer();
            });
            row.addView(cancel);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Draw.dp(12);
            timerBox.addView(row, rl);
        } else {
            Segmented seg = new Segmented(activity, pal, new String[]{"15 min", "30 min", "1 h", "Libre"}, -1, i -> {
                if (i == 3) {
                    askMinutes();
                } else {
                    startCountdown(i == 0 ? 15 : i == 1 ? 30 : 60);
                }
            });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sl.topMargin = Draw.dp(12);
            timerBox.addView(seg, sl);
        }

        View_divider();
        LinearLayout daily = new LinearLayout(activity);
        daily.setGravity(Gravity.CENTER_VERTICAL);
        daily.addView(Draw.text(activity, "Éteindre chaque jour à", 14, pal.text, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        int minutes = TimerStore.dailyMinutes(activity);
        TextView chip = Draw.text(activity, String.format(Locale.FRANCE, "%02d:%02d", minutes / 60, minutes % 60), 15, pal.text, true);
        chip.setBackground(Draw.rect(pal.selected, 999));
        chip.setPadding(Draw.dp(18), Draw.dp(9), Draw.dp(18), Draw.dp(9));
        chip.setOnClickListener(v -> new TimePickerDialog(new ContextThemeWrapper(activity, activity.dialogTheme()), (view, h, m) -> {
            TimerStore.setDaily(activity, TimerStore.dailyOn(activity), h * 60 + m);
            if (TimerStore.dailyOn(activity)) activity.askNotifications();
            buildTimer();
        }, minutes / 60, minutes % 60, true).show());
        daily.addView(chip);
        SwitchView on = new SwitchView(activity, pal);
        on.set(TimerStore.dailyOn(activity));
        on.setListener(v -> {
            TimerStore.setDaily(activity, v, TimerStore.dailyMinutes(activity));
            if (v) activity.askNotifications();
            buildTimer();
        });
        daily.addView(on);
        timerBox.addView(daily);

        TextView note = Draw.text(activity, "Au mieux : si la tablette dort profondément, l’extinction peut avoir quelques minutes de retard.", 12, pal.sub, false);
        note.setPadding(0, Draw.dp(10), 0, 0);
        note.setLineSpacing(Draw.dpf(2), 1f);
        timerBox.addView(note);
    }

    private void View_divider() {
        android.view.View line = new android.view.View(activity);
        line.setBackgroundColor(pal.track);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = Draw.dp(16);
        lp.bottomMargin = Draw.dp(8);
        timerBox.addView(line, lp);
    }

    private void startCountdown(int minutes) {
        TimerStore.startCountdown(activity, minutes);
        activity.askNotifications();
        buildTimer();
    }

    private void askMinutes() {
        NumberPicker np = new NumberPicker(activity);
        np.setMinValue(1);
        np.setMaxValue(720);
        np.setValue(45);
        np.setWrapSelectorWheel(false);
        FrameWrap wrap = new FrameWrap(activity, np);
        new AlertDialog.Builder(new ContextThemeWrapper(activity, activity.dialogTheme())).setTitle("Éteindre dans (minutes)").setView(wrap)
                .setPositiveButton("Démarrer", (d, w) -> startCountdown(np.getValue()))
                .setNegativeButton("Annuler", null).show();
    }
}

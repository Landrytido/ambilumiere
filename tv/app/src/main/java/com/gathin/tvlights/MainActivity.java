package com.gathin.tvlights;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The add-on's screen, made for the remote: dark and calm, like the launcher. Home: one card per light (an icon, a
 * switch, its name, its colour and a thin brightness bar), four scene buttons. OK on a card switches that light;
 * holding OK (or the Menu key) opens that light's settings: eight colours, the brightness, and whether it follows the
 * cover of the music. Back closes the settings. Never more than those two levels. Opening the screen also makes sure
 * the service runs.
 */
public class MainActivity extends Activity {
    private static final long REFRESH_MS = 500;
    /** What the arrows have asked for the brightness is sent once they stop for this long. */
    private static final long LEVEL_DEBOUNCE_MS = 300;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final int[] SWATCH_NAMES = {R.string.sw_warm, R.string.sw_red, R.string.sw_orange, R.string.sw_yellow,
            R.string.sw_green, R.string.sw_cyan, R.string.sw_blue, R.string.sw_violet, R.string.sw_neutral, R.string.sw_cool};
    private static final int[] SCENE_LABELS = {R.string.scene_all_on, R.string.scene_all_off, R.string.scene_evening, R.string.scene_cinema};
    private static final int[] INTENSITY_LABELS = {R.string.intensity_soft, R.string.intensity_normal, R.string.intensity_lively};
    private static final String[] SCENES = {LightCommands.SCENE_ALL_ON, LightCommands.SCENE_ALL_OFF,
            LightCommands.SCENE_EVENING, LightCommands.SCENE_CINEMA};

    /** The views of one light's card. */
    private static final class Card {
        ImageView icon, note;
        Widgets.SwitchView toggle;
        TextView name, line;
        View dot;
        Widgets.BarView bar;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout page, cards;
    private TextView count, empty, syncPill;
    private final TextView[] intensityPills = new TextView[3];
    /** Intensity.SOFT, NORMAL or LIVELY: what the screen shows as chosen. */
    private int intensity = Intensity.NORMAL;
    private final List<Card> cardViews = new ArrayList<>();
    private String shownIds = "";

    private LinearLayout settings;
    private TextView settingsTitle, levelText;
    private final Widgets.SwatchView[] swatches = new Widgets.SwatchView[LightCommands.SWATCHES];
    private Widgets.BarView levelBar;
    private Widgets.SwitchView followSwitch;
    /** The light whose settings are open, or null. */
    private String openLight;
    private View focusBeforeSettings;
    /** The brightness the arrows have chosen and the light hasn't reported yet, or -1. */
    private int pendingLevel = -1;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            main.postDelayed(this, REFRESH_MS);
        }
    };
    private final Runnable sendLevel = () -> {
        if (openLight != null && pendingLevel > 0) command("level", openLight, LightsService.EXTRA_VALUE, String.valueOf(pendingLevel));
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Widgets.BG);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(32), px(20), px(32), px(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(22, Widgets.TEXT, true);
        title.setText(R.string.app_name);
        count = text(13, Widgets.SECONDARY, false);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(count);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        empty = text(16, Widgets.SECONDARY, false);
        empty.setGravity(Gravity.CENTER);
        FrameLayout top = new FrameLayout(this);
        top.addView(cards, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout pills = new LinearLayout(this);
        for (int i = 0; i < SCENES.length; i++) {
            final String scene = SCENES[i];
            TextView b = text(13, Widgets.TEXT, true);
            b.setText(SCENE_LABELS[i]);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundResource(R.drawable.pill_background);
            b.setFocusable(true);
            b.setClickable(true);
            b.setOnClickListener(v -> command("scene", "all", LightsService.EXTRA_SCENE, scene));
            pills.addView(b, pillParams());
        }
        // Sync all the lights with the cover, or stop: one press for all, instead of light by light.
        syncPill = text(13, Widgets.TEXT, true);
        syncPill.setText(R.string.sync_all);
        syncPill.setGravity(Gravity.CENTER);
        syncPill.setBackgroundResource(R.drawable.pill_background);
        syncPill.setFocusable(true);
        syncPill.setClickable(true);
        syncPill.setOnClickListener(v -> command("follow", "all", LightsService.EXTRA_VALUE, allFollow() ? "0" : "1"));
        pills.addView(syncPill, pillParams());

        // One more line under the pills: how strongly the strip and the bulbs show the cover's colours.
        LinearLayout intensityRow = new LinearLayout(this);
        intensityRow.setGravity(Gravity.CENTER);
        TextView intensityLabel = text(12, Widgets.SECONDARY, false);
        intensityLabel.setText(R.string.intensity_label);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMarginEnd(px(14));
        intensityRow.addView(intensityLabel, labelLp);
        for (int i = 0; i < INTENSITY_LABELS.length; i++) {
            final int mode = i;
            TextView b = text(13, Widgets.TEXT, true);
            b.setText(INTENSITY_LABELS[i]);
            b.setGravity(Gravity.CENTER);
            b.setPadding(px(20), 0, px(20), 0);
            b.setFocusable(true);
            b.setClickable(true);
            b.setOnClickListener(v -> chooseIntensity(mode));
            intensityPills[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(36));
            lp.setMargins(px(3), 0, px(3), 0);
            intensityRow.addView(b, lp);
        }
        intensity = savedIntensity();
        showIntensity();

        TextView hint = text(11, Widgets.OFF, false);
        hint.setText(R.string.hint_home);
        hint.setGravity(Gravity.CENTER);

        page.addView(header);
        // The cards and the scenes sit as one block in the middle of what is left: a third of the screen's height for
        // the cards, free space above and a little more below.
        page.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        int cardsHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.33f);
        page.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardsHeight));
        LinearLayout.LayoutParams pillsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillsLp.topMargin = px(28);
        page.addView(pills, pillsLp);
        LinearLayout.LayoutParams intensityLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        intensityLp.topMargin = px(16);
        page.addView(intensityRow, intensityLp);
        page.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1.3f));
        page.addView(hint);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildSettings();
        root.addView(settings, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startForegroundService(new Intent(this, LightsService.class).setAction(LightsService.ACTION_ENSURE));
        intensity = savedIntensity();
        showIntensity();
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    // ---- the settings --------------------------------------------------------------------------------------------

    private void buildSettings() {
        settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setBackgroundColor(Widgets.BG);
        settings.setPadding(px(32), px(20), px(32), px(14));
        settings.setVisibility(View.GONE);

        settingsTitle = text(22, Widgets.TEXT, true);

        LinearLayout colours = card();
        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < LightCommands.SWATCHES; i++) {
            final int index = i;
            Widgets.SwatchView s = new Widgets.SwatchView(this, LightCommands.swatchRgb(i), 30);
            s.setOnClickListener(v -> command("look", openLight, LightsService.EXTRA_VALUE, String.valueOf(index)));
            swatches[i] = s;
            FrameLayout cell = new FrameLayout(this);
            cell.addView(s, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            swatchRow.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        colours.addView(swatchRow);

        LinearLayout level = row();
        ImageView sun = icon(R.drawable.ic_sun, 18, Widgets.SECONDARY);
        levelBar = new Widgets.BarView(this, 6);
        levelText = text(13, Widgets.TEXT, true);
        levelText.setGravity(Gravity.END);
        level.addView(sun, new LinearLayout.LayoutParams(px(18), px(18)));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        barLp.setMargins(px(12), 0, px(12), 0);
        level.addView(levelBar, barLp);
        level.addView(levelText, new LinearLayout.LayoutParams(px(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        level.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int dir = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (dir == 0) return false;
            LightUnit u = unit(openLight);
            if (u != null && e.getRepeatCount() == 0) {
                pendingLevel = Math.min(LightCommands.MAX_LEVEL, Math.max(LightCommands.MIN_LEVEL,
                        shownLevel(u) + dir * LightCommands.LEVEL_STEP));
                refresh();
                main.removeCallbacks(sendLevel);
                main.postDelayed(sendLevel, LEVEL_DEBOUNCE_MS);
            }
            return true;
        });
        LinearLayout.LayoutParams levelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        levelLp.topMargin = px(14);
        colours.addView(level, levelLp);

        LinearLayout follow = card();
        LinearLayout followRow = row();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView followTitle = text(14, Widgets.TEXT, true);
        followTitle.setText(R.string.follow_title);
        TextView followSub = text(12, Widgets.SECONDARY, false);
        followSub.setText(R.string.follow_subtitle);
        labels.addView(followTitle);
        labels.addView(followSub);
        followSwitch = new Widgets.SwitchView(this, 44, 26);
        followRow.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        followRow.addView(followSwitch);
        followRow.setOnClickListener(v -> {
            LightUnit u = unit(openLight);
            if (u != null) command("follow", openLight, LightsService.EXTRA_VALUE, u.follow ? "0" : "1");
        });
        follow.addView(followRow);

        TextView hint = text(11, Widgets.OFF, false);
        hint.setText(R.string.hint_settings);
        hint.setGravity(Gravity.CENTER);

        settings.addView(settingsTitle);
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        c1.topMargin = px(20);
        settings.addView(colours, c1);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        c2.topMargin = px(12);
        settings.addView(follow, c2);
        settings.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        settings.addView(hint);
    }

    private void openSettings(String light) {
        LightUnit u = unit(light);
        if (u == null || openLight != null) return;
        focusBeforeSettings = getCurrentFocus();
        openLight = light;
        pendingLevel = -1;
        settingsTitle.setText(u.cfg.name);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // the remote stays on the settings
        settings.setVisibility(View.VISIBLE);
        refresh();
        int chosen = u.colourIdx < LightCommands.SWATCHES ? u.colourIdx : 0;
        swatches[chosen].requestFocus();
    }

    private void closeSettings() {
        main.removeCallbacks(sendLevel);
        settings.setVisibility(View.GONE);
        openLight = null;
        pendingLevel = -1;
        page.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (focusBeforeSettings != null) focusBeforeSettings.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                if (openLight != null) {
                    closeSettings();
                } else {
                    View f = getCurrentFocus();
                    Object tag = f == null ? null : f.getTag();
                    if (tag instanceof String) openSettings((String) tag);
                }
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && openLight != null) {
                closeSettings();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- the screen ----------------------------------------------------------------------------------------------

    private void refresh() {
        List<LightUnit> units = LightsStatus.units;
        StringBuilder ids = new StringBuilder();
        for (LightUnit u : units) ids.append(u.cfg.id).append(',');
        if (!ids.toString().equals(shownIds)) {
            shownIds = ids.toString();
            buildCards(units);
        }
        if (units.isEmpty()) {
            empty.setText(LightsStatus.link == LightsStatus.NO_CONFIG
                    ? ("missing".equals(LightsStatus.problem) ? R.string.empty_missing : R.string.empty_bad)
                    : R.string.empty_waiting);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
        int on = 0;
        for (int i = 0; i < units.size() && i < cardViews.size(); i++) {
            LightUnit u = units.get(i);
            Card c = cardViews.get(i);
            boolean reachable = u.linkState != LightsStatus.UNREACHABLE;
            boolean lit = u.power == 1 && reachable;
            if (lit) on++;
            c.toggle.setChecked(lit);
            c.icon.setColorFilter(lit ? Widgets.TEXT : Widgets.OFF, PorterDuff.Mode.SRC_IN);
            c.note.setVisibility(u.follow ? View.VISIBLE : View.GONE);
            c.name.setTextColor(lit ? Widgets.TEXT : Widgets.OFF);
            c.bar.setVisibility(lit ? View.VISIBLE : View.INVISIBLE);
            c.dot.setVisibility(lit ? View.VISIBLE : View.GONE);
            if (!reachable) {
                c.line.setText(R.string.state_unreachable);
            } else if (u.power == LightUnit.UNKNOWN) {
                c.line.setText(R.string.state_connecting);
            } else if (!lit) {
                c.line.setText(R.string.state_off);
            } else {
                c.line.setText(u.follow ? R.string.state_cover : SWATCH_NAMES[Math.min(u.colourIdx, SWATCH_NAMES.length - 1)]);
                c.dot.setBackground(round(u.colour));
                c.bar.setValue(u.brightness);
            }
        }
        count.setText(getString(on == 1 ? R.string.count_one : R.string.count_many, on));
        syncPill.setText(allFollow() ? R.string.sync_off : R.string.sync_all);

        LightUnit open = unit(openLight);
        if (open != null) {
            for (int i = 0; i < swatches.length; i++) swatches[i].setChosen(open.colourIdx == i);
            if (pendingLevel > 0 && open.level == pendingLevel) pendingLevel = -1;
            int shown = shownLevel(open);
            levelBar.setValue(shown / 100.0);
            levelText.setText(getString(R.string.percent, shown));
            followSwitch.setChecked(open.follow);
        }
    }

    /** The brightness to show for a light, in steps of 10 %: what the arrows asked, else what the light reports. */
    private int shownLevel(LightUnit u) {
        if (pendingLevel > 0) return pendingLevel;
        int percent = u.brightness >= 0 ? (int) Math.round(u.brightness * 10) * 10 : u.level;
        return Math.min(LightCommands.MAX_LEVEL, Math.max(LightCommands.MIN_LEVEL, percent));
    }

    private void buildCards(List<LightUnit> units) {
        cards.removeAllViews();
        cardViews.clear();
        for (LightUnit u : units) {
            final String id = u.cfg.id;
            Card c = new Card();
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_background);
            card.setFocusable(true);
            card.setClickable(true);
            card.setTag(id);
            card.setPadding(px(12), px(12), px(12), px(12));
            card.setOnClickListener(v -> command("toggle", id, null, null));
            card.setOnLongClickListener(v -> {
                openSettings(id);
                return true;
            });

            LinearLayout topRow = new LinearLayout(this);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            c.icon = icon(R.drawable.ic_bulb, 20, Widgets.OFF);
            c.note = icon(R.drawable.ic_note, 14, Widgets.SWITCH_ON);
            c.note.setVisibility(View.GONE);
            c.toggle = new Widgets.SwitchView(this, 34, 20);
            topRow.addView(c.icon, new LinearLayout.LayoutParams(px(20), px(20)));
            topRow.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(px(14), px(14));
            noteLp.setMarginEnd(px(8));
            topRow.addView(c.note, noteLp);
            topRow.addView(c.toggle);

            c.name = text(14, Widgets.TEXT, true);
            c.name.setText(u.cfg.name);
            c.name.setSingleLine();
            c.name.setEllipsize(TextUtils.TruncateAt.END);

            LinearLayout lineRow = new LinearLayout(this);
            lineRow.setGravity(Gravity.CENTER_VERTICAL);
            c.dot = new View(this);
            c.line = text(12, Widgets.SECONDARY, false);
            c.line.setMaxLines(1);
            c.line.setSingleLine();
            c.line.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(px(9), px(9));
            dotLp.setMarginEnd(px(6));
            lineRow.addView(c.dot, dotLp);
            lineRow.addView(c.line, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            c.bar = new Widgets.BarView(this, 4);

            card.addView(topRow);
            card.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));   // icon and switch on top, the rest at the bottom
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.topMargin = px(16);
            card.addView(c.name, nameLp);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineLp.topMargin = px(3);
            card.addView(lineRow, lineLp);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            barLp.topMargin = px(10);
            card.addView(c.bar, barLp);

            cardViews.add(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(px(6), 0, px(6), 0);
            cards.addView(card, lp);
        }
        if (cards.getChildCount() > 0 && openLight == null) cards.getChildAt(0).requestFocus();
    }

    private int savedIntensity() {
        return Intensity.clamp(getSharedPreferences(LightsController.PREFS, MODE_PRIVATE).getInt(LightsController.PREF_INTENSITY, Intensity.NORMAL));
    }

    /** OK on Doux, Normal or Vif: applied at once, and kept (the service saves it). */
    private void chooseIntensity(int mode) {
        intensity = mode;
        showIntensity();
        command("intensity", "all", LightsService.EXTRA_VALUE, String.valueOf(mode));
    }

    /** The chosen one filled #f2f2f7 with dark text, the others #1c1c1e; a thin outline on the one the remote is on. */
    private void showIntensity() {
        for (int i = 0; i < intensityPills.length; i++) {
            boolean chosen = i == intensity;
            intensityPills[i].setTextColor(chosen ? Widgets.BG : Widgets.TEXT);
            intensityPills[i].setBackground(segment(chosen));
        }
    }

    private StateListDrawable segment(boolean chosen) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, segmentShape(chosen ? Widgets.TEXT : Widgets.CARD, chosen ? Widgets.SECONDARY : Widgets.TEXT));
        s.addState(new int[]{}, segmentShape(chosen ? Widgets.TEXT : Widgets.CARD, chosen ? Widgets.TEXT : Widgets.CARD));
        return s;
    }

    private GradientDrawable segmentShape(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(px(2), stroke);
        d.setCornerRadius(px(999));
        return d;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(46), 1f);
        lp.setMargins(px(6), 0, px(6), 0);
        return lp;
    }

    /** Every light follows the cover (and there is at least one). */
    private boolean allFollow() {
        List<LightUnit> units = LightsStatus.units;
        for (LightUnit u : units) if (!u.follow) return false;
        return !units.isEmpty();
    }

    private LightUnit unit(String id) {
        if (id == null) return null;
        for (LightUnit u : LightsStatus.units) if (u.cfg.id.equals(id)) return u;
        return null;
    }

    private void command(String cmd, String light, String extra, String value) {
        if (light == null) return;
        Intent i = new Intent(this, LightsService.class).setAction(LightsService.ACTION_COMMAND)
                .putExtra(LightsService.EXTRA_CMD, cmd).putExtra(LightsService.EXTRA_LIGHT, light);
        if (extra != null) i.putExtra(extra, value);
        startForegroundService(i);
        main.postDelayed(this::refresh, 300);
    }

    // ---- views ---------------------------------------------------------------------------------------------------

    private TextView text(int sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(medium ? MEDIUM : Typeface.DEFAULT);
        return t;
    }

    private ImageView icon(int res, int dp, int tint) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    /** A rounded #1c1c1e block of the settings. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Widgets.CARD);
        d.setCornerRadius(px(18));
        c.setBackground(d);
        c.setPadding(px(16), px(16), px(16), px(16));
        return c;
    }

    /** A line of a settings card that the remote can stop on: a thin outline when it has the focus. */
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackgroundResource(R.drawable.row_focus);
        r.setPadding(px(10), px(10), px(10), px(10));
        return r;
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

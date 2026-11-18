package com.gathin.lights.tablet;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The settings of one light: its name and switch, the free colour wheel, the eight ready colours and the brightness.
 * Used in the sheet that opens from a card, and as the panel of "Liste + panneau". sync() shows the light's state
 * (and leaves a control alone while a finger is on it).
 */
final class LightPanel extends LinearLayout {
    private final Pal pal;
    private final LightUnit unit;
    private final TextView title, status, percent;
    private final SwitchView power;
    private final ColorWheelView wheel;
    private final SwatchView swatches;
    private final SliderView slider;

    LightPanel(Context c, Pal pal, LightUnit unit) {
        super(c);
        this.pal = pal;
        this.unit = unit;
        setOrientation(VERTICAL);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(c);
        names.setOrientation(VERTICAL);
        title = Draw.text(c, unit.cfg.name, 20, pal.text, true);
        status = Draw.text(c, "", 12, pal.sub, false);
        status.setPadding(0, Draw.dp(4), 0, 0);
        names.addView(title);
        names.addView(status);
        head.addView(names, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        power = new SwitchView(c, pal);
        power.setListener(unit::setPower);
        head.addView(power);
        addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        wheel = new ColorWheelView(c, pal);
        wheel.setListener((h, s, done) -> {
            LightPanel.this.swatches.set(-1);
            unit.setColour(h, s);
        });
        LayoutParams wl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        wl.topMargin = Draw.dp(14);
        addView(wheel, wl);

        swatches = new SwatchView(c, pal);
        swatches.setListener(unit::setSwatch);
        LayoutParams sl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sl.topMargin = Draw.dp(10);
        addView(swatches, sl);

        LinearLayout label = new LinearLayout(c);
        label.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Draw.text(c, "Luminosité", 14, pal.text, true);
        label.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        percent = Draw.text(c, "", 14, pal.sub, false);
        label.addView(percent);
        LayoutParams ll = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ll.topMargin = Draw.dp(12);
        addView(label, ll);

        slider = new SliderView(c, pal, LightCommands.MIN_LEVEL);
        slider.setListener((v, done) -> {
            percent.setText(v + " %");
            unit.setLevel(v);
        });
        addView(slider, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        sync();
    }

    LightUnit unit() {
        return unit;
    }

    void sync() {
        LightUnit.State s = unit.state();
        boolean online = s.online();
        status.setText(!online ? "Hors ligne · touchez la carte pour réessayer" : !s.on ? "Éteinte"
                : Draw.colourLabel(s.colourIdx) + (s.level >= 0 ? " · " + s.level + " %" : ""));
        power.set(s.on && online);
        wheel.set(s.hue * 360, s.white || !s.on ? (s.white ? 0 : s.sat) : s.sat);
        swatches.set(s.colourIdx < LightCommands.SWATCHES && (s.white || s.sat > 0.2) ? s.colourIdx : -1);
        int level = s.level < 0 ? 100 : s.level;
        slider.set(level, s.on ? s.rgb : pal.sub);
        percent.setText(level + " %");
        setAlpha(online ? 1f : 0.5f);
    }
}

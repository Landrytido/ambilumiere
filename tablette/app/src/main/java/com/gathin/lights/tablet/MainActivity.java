package com.gathin.lights.tablet;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The only screen: a header (title, "Lumières | Scènes", the settings gear), the screen chosen, and on the home screen
 * the two buttons for all the lights. Everything is built in code and rebuilt when the theme, the layout or the
 * orientation changes.
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 11, REQ_NOTIFICATIONS = 12;
    private static final int HOME = 0, SCENES = 1, SETTINGS = 2;
    private static final int MAX_CONFIG_BYTES = 32 * 1024;

    private Hub hub;
    private Pal pal;
    private int screen = HOME, before = HOME;
    private boolean started;
    private HomeView home;
    private ScenesView scenes;
    private final Runnable listener = () -> {
        if (home != null) home.refresh();
        if (scenes != null) scenes.refresh();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        hub = Hub.get(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        hub.acquire();
        hub.addListener(listener);
        TimerStore.rearm(this);
        build();
    }

    @Override
    protected void onStop() {
        started = false;
        hub.removeListener(listener);
        disposeViews();
        hub.release();
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (started) build();
    }

    @Override
    public void onBackPressed() {
        if (screen == SETTINGS) {
            screen = before;
            build();
        } else {
            super.onBackPressed();
        }
    }

    int dialogTheme() {
        return pal.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
    }

    void rebuild() {
        build();
    }

    private void disposeViews() {
        if (home != null) home.dispose();
        if (scenes != null) scenes.dispose();
        home = null;
        scenes = null;
    }

    private void go(int to) {
        if (to == SETTINGS) before = screen;
        screen = to;
        build();
    }

    private void build() {
        disposeViews();
        pal = Pal.load(this);
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(pal.bg));
        w.setStatusBarColor(pal.bg);
        w.setNavigationBarColor(pal.bg);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        decor.setSystemUiVisibility(pal.dark ? flags & ~light : flags | light);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.bg);
        root.setPadding(Draw.dp(28), Draw.dp(10), Draw.dp(28), Draw.dp(16));

        root.addView(header(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Draw.dp(60)));

        View content;
        if (screen == SETTINGS) {
            content = new SettingsView(this, pal);
        } else if (screen == SCENES) {
            scenes = new ScenesView(this, hub, pal);
            content = scenes;
        } else {
            home = new HomeView(this, hub, pal);
            content = home;
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        cl.topMargin = Draw.dp(6);
        root.addView(content, cl);

        if (screen == HOME && !hub.units().isEmpty()) {
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER);
            TextView on = Draw.pill(this, "Tout allumer", pal);
            on.setOnClickListener(v -> hub.allPower(true));
            TextView off = Draw.pill(this, "Tout éteindre", pal);
            off.setOnClickListener(v -> hub.allPower(false));
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            gap.leftMargin = Draw.dp(14);
            bar.addView(on);
            bar.addView(off, gap);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.topMargin = Draw.dp(14);
            root.addView(bar, bl);
        }
        setContentView(root);
    }

    private View header() {
        FrameLayout h = new FrameLayout(this);
        TextView title = Draw.text(this, screen == SETTINGS ? "Réglages" : "Lumières", 20, pal.text, true);
        h.addView(title, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        if (screen == SETTINGS) {
            TextView done = Draw.pill(this, "Terminé", pal);
            done.setPadding(Draw.dp(22), Draw.dp(10), Draw.dp(22), Draw.dp(10));
            done.setOnClickListener(v -> {
                screen = before;
                build();
            });
            h.addView(done, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        } else {
            Segmented nav = new Segmented(this, pal, new String[]{"Lumières", "Scènes"}, screen, i -> go(i));
            h.addView(nav, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            ImageView gear = new ImageView(this);
            gear.setImageDrawable(Draw.icon(this, R.drawable.ic_gear, pal.sub));
            gear.setPadding(Draw.dp(10), Draw.dp(10), Draw.dp(10), Draw.dp(10));
            gear.setContentDescription("Réglages");
            gear.setOnClickListener(v -> go(SETTINGS));
            h.addView(gear, new FrameLayout.LayoutParams(Draw.dp(46), Draw.dp(46), Gravity.END | Gravity.CENTER_VERTICAL));
        }
        return h;
    }

    // ---- the lights file -------------------------------------------------------------------------------------------

    /** Opens the system's file chooser for lights.json. */
    void pickConfig() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Impossible d’ouvrir le choix de fichier.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        String text = read(data.getData());
        String problem = text == null ? "unreadable" : LightsConfig.store(this, text);
        if (problem != null) {
            Toast.makeText(this, "Ce fichier n’est pas un fichier de lumières utilisable.", Toast.LENGTH_LONG).show();
            return;
        }
        hub.reload();
        screen = HOME;
        if (started) build();
        Toast.makeText(this, "Configuration importée.", Toast.LENGTH_SHORT).show();
    }

    /** The text of the chosen file, or null when it can't be read or is too big to be a lights file. */
    private String read(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_CONFIG_BYTES) return null;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    // ---- notifications ---------------------------------------------------------------------------------------------

    /** Asks for the right to show the timer's notification, only when a timer is armed (Android 13 and later). */
    void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }
}

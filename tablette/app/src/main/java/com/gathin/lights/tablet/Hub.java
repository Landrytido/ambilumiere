package com.gathin.lights.tablet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The lights of the app, shared by the screen and the timer: the config read from the user's file, one LightUnit
 * for each light, and the commands for all of them. The connections exist while someone holds the hub
 * (the screen while it is visible, the timer while it switches off).
 */
final class Hub implements LightUnit.Owner {
    private static Hub instance;

    static synchronized Hub get(Context c) {
        if (instance == null) instance = new Hub(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean notifyQueued = new AtomicBoolean();
    private LightsConfig.Result config = new LightsConfig.Result(null, "missing", 0);
    private List<LightUnit> units = Collections.emptyList();
    private int refs;

    private Hub(Context app) {
        this.app = app;
    }

    synchronized void acquire() {
        if (refs++ == 0) startUnits();
    }

    synchronized void release() {
        release(null);
    }

    /** One holder less; when it was the last, the connections close once the commands already asked for are done, then done runs. */
    synchronized void release(Runnable done) {
        if (refs > 0 && --refs == 0) {
            stopUnits(done);
        } else if (done != null) {
            done.run();
        }
    }

    /** The user chose another file: read it and reconnect. */
    synchronized void reload() {
        if (refs > 0) {
            stopUnits(null);
            startUnits();
        } else {
            config = LightsConfig.read(app);
        }
        notifyListeners();
    }

    synchronized LightsConfig.Result config() {
        return config;
    }

    synchronized List<LightUnit> units() {
        return units;
    }

    private void startUnits() {
        config = LightsConfig.read(app);
        List<LightUnit> list = new ArrayList<>();
        if (config.config != null) {
            for (LightsConfig.Light l : config.config.lights) list.add(new LightUnit(l, this));
        }
        units = Collections.unmodifiableList(list);
        for (LightUnit u : list) u.start();
    }

    private void stopUnits(Runnable done) {
        List<LightUnit> old = units;
        units = Collections.emptyList();
        if (old.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        AtomicInteger left = new AtomicInteger(old.size());
        for (LightUnit u : old) {
            u.stop(() -> {
                if (left.decrementAndGet() == 0 && done != null) done.run();
            });
        }
    }

    void addListener(Runnable r) {
        listeners.add(r);
    }

    void removeListener(Runnable r) {
        listeners.remove(r);
    }

    @Override
    public void onUnitChanged() {
        notifyListeners();
    }

    private void notifyListeners() {
        if (!notifyQueued.getAndSet(true)) {
            main.postDelayed(() -> {
                notifyQueued.set(false);
                for (Runnable r : listeners) r.run();
            }, 80);
        }
    }

    // ---- commands for all the lights -----------------------------------------------------------------------------

    void allPower(boolean on) {
        for (LightUnit u : units()) u.setPower(on);
    }

    void runScene(SceneStore.Scene scene) {
        for (LightUnit u : units()) u.apply(scene.settingFor(u.cfg));
    }
}

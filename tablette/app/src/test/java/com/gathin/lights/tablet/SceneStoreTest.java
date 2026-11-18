package com.gathin.lights.tablet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SceneStoreTest {
    private static final LightsConfig.Light STRIP = new LightsConfig.Light("tv", "Ruban TV", "strip", "192.168.1.20", 6668, "3.5", "d1", "k");
    private static final LightsConfig.Light BULB = new LightsConfig.Light("table", "Table", "bulb", "192.168.1.21", 6668, "3.3", "d2", "k");

    @Test
    public void userScenesSurviveTheJsonRoundTrip() {
        Map<String, SceneStore.Setting> lights = new LinkedHashMap<>();
        lights.put("tv", SceneStore.Setting.swatch(6, 40));
        lights.put("table", SceneStore.Setting.off());
        SceneStore.Scene s = new SceneStore.Scene("u1", "Lecture", null, lights);
        List<SceneStore.Scene> back = SceneStore.fromJson(SceneStore.toJson(Collections.singletonList(s)));
        assertEquals(1, back.size());
        assertEquals("Lecture", back.get(0).name);
        assertFalse(back.get(0).isBuiltin());
        SceneStore.Setting tv = back.get(0).settingFor(STRIP);
        assertTrue(tv.on);
        assertEquals(240, tv.h);
        assertEquals(40, tv.level);
        assertEquals(6, tv.swatchIndex());
        assertFalse(back.get(0).settingFor(BULB).on);
    }

    @Test
    public void builtInScenesAreNotSaved() {
        assertEquals("[]", SceneStore.toJson(SceneStore.builtins()));
    }

    @Test
    public void garbageGivesNoScenes() {
        assertTrue(SceneStore.fromJson("not json").isEmpty());
        assertTrue(SceneStore.fromJson(null).isEmpty());
        assertTrue(SceneStore.fromJson("[{\"id\":\"\",\"name\":\"x\",\"lights\":{}}]").isEmpty());
    }

    @Test
    public void eveningAndCinemaDependOnTheKindOfLight() {
        SceneStore.Scene evening = SceneStore.builtins().get(0), cinema = SceneStore.builtins().get(1);
        assertEquals(30, evening.settingFor(STRIP).level);
        assertEquals(40, evening.settingFor(BULB).level);
        assertTrue(cinema.settingFor(STRIP).on);
        assertEquals(10, cinema.settingFor(STRIP).level);
        assertFalse(cinema.settingFor(BULB).on);
    }

    @Test
    public void settingsTurnIntoDatapoints() {
        JSONObject off = SceneStore.Setting.off().dps(false, true);
        assertFalse(off.optBoolean("20", true));
        JSONObject red = SceneStore.Setting.swatch(1, 50).dps(true, true);
        assertEquals("colour", red.optString("21"));
        assertEquals("000003e801f4", red.optString("24"));
        assertTrue(red.has("103"));
        JSONObject warm = SceneStore.Setting.swatch(0, 20).dps(false, false);
        assertEquals("colour", warm.optString("21"));
    }

    @Test
    public void brightnessOfASettingIsKeptInRange() {
        assertEquals(10, new SceneStore.Setting(true, false, 0, 1000, 0).level);
        assertEquals(100, new SceneStore.Setting(true, false, 0, 1000, 500).level);
    }
}

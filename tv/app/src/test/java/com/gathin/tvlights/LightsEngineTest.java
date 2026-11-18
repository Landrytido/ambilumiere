package com.gathin.tvlights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LightsEngineTest {
    private static final class Recorder implements LightsEngine.Sink {
        final List<String> sent = new ArrayList<>();

        @Override
        public boolean send(JSONObject dps) {
            sent.add(canonical(dps));
            return true;
        }
    }

    /** Same text whatever the order the keys came in. */
    private static String canonical(JSONObject o) {
        List<String> keys = new ArrayList<>();
        for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) keys.add(it.next());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k).append('=').append(o.opt(k)).append(';');
        return sb.toString();
    }

    private static List<String> expected(JSONArray sent) throws Exception {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < sent.length(); i++) out.add(canonical(sent.getJSONObject(i)));
        return out;
    }

    private static void sameZones(double[][] want, double[][] got) {
        assertEquals(want.length, got.length);
        for (int z = 0; z < want.length; z++) {
            for (int c = 0; c < 3; c++) assertEquals(want[z][c], got[z][c], 1e-9);
        }
    }

    @Test
    public void constantsAreThePythonOnes() throws Exception {
        JSONObject c = Golden.DATA.getJSONObject("const");
        assertEquals(c.getInt("zones"), LightsMath.ZONES);
        assertEquals(c.getDouble("min_interval"), LightsEngine.MIN_INTERVAL, 0);
        assertEquals(c.getDouble("wave_seconds"), LightsEngine.WAVE_SECONDS, 0);
        assertEquals(c.getDouble("wave_lag"), LightsEngine.WAVE_LAG, 0);
        assertEquals(c.getDouble("off_delay") * 1000, LightsController.OFF_DELAY_MS, 0);
    }

    @Test
    public void wavesSendTheSameFramesAsPython() throws Exception {
        JSONArray waves = Golden.DATA.getJSONArray("waves");
        for (int i = 0; i < waves.length(); i++) {
            JSONObject w = waves.getJSONObject(i);
            Recorder rec = new Recorder();
            LightsEngine engine = new LightsEngine(rec);
            engine.setCurrent(Golden.colors(w.optJSONArray("start")));
            assertTrue(engine.show(Golden.colors(w.getJSONArray("target"))));
            assertEquals(w.getString("name"), expected(w.getJSONArray("sent")), rec.sent);
            sameZones(Golden.colors(w.getJSONArray("current")), engine.current());
        }
    }

    @Test
    public void restoresSendTheSameStateAsPython() throws Exception {
        JSONArray restores = Golden.DATA.getJSONArray("restores");
        for (int i = 0; i < restores.length(); i++) {
            JSONObject r = restores.getJSONObject(i);
            Recorder rec = new Recorder();
            LightsEngine engine = new LightsEngine(rec);
            engine.setCurrent(Golden.colors(r.optJSONArray("current")));
            JSONObject original = r.optJSONObject("original");
            assertTrue(engine.restore(original));
            if (original != null && "white".equals(original.optString("21")) && r.optJSONArray("current") != null) {
                // The PC tool jumps straight back to a white; here the way back is a fade, then the exact state.
                assertTrue("restore " + i + " fades before the white", rec.sent.size() > 1);
                continue;
            }
            assertEquals("restore " + i + " " + original, expected(r.getJSONArray("sent")), rec.sent);
            double[][] after = Golden.colors(r.optJSONArray("after"));
            if (after == null) assertNull(engine.current());
            else sameZones(after, engine.current());
        }
    }
}

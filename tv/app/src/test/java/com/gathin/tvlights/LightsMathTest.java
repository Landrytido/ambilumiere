package com.gathin.tvlights;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class LightsMathTest {
    private static final double EPS = 1e-9;

    @Test
    public void hexAndHsvMatchPython() throws Exception {
        JSONArray hex = Golden.DATA.getJSONArray("hex");
        JSONArray hsv = Golden.DATA.getJSONArray("hsv");
        for (int i = 0; i < hex.length(); i++) {
            double[] got = LightsMath.fromRgb(LightsMath.parseHex(hex.getString(i)));
            assertArrayEquals(hex.getString(i), Golden.triple(hsv.getJSONArray(i)), got, EPS);
        }
    }

    @Test
    public void mixMatchesPython() throws Exception {
        JSONArray mixes = Golden.DATA.getJSONArray("mixes");
        for (int i = 0; i < mixes.length(); i++) {
            JSONObject m = mixes.getJSONObject(i);
            double[] got = LightsMath.mix(Golden.triple(m.getJSONArray("a")), Golden.triple(m.getJSONArray("b")), m.getDouble("t"));
            assertArrayEquals("mix " + i, Golden.triple(m.getJSONArray("out")), got, EPS);
        }
    }

    @Test
    public void spreadAndPayloadMatchPython() throws Exception {
        JSONArray spreads = Golden.DATA.getJSONArray("spreads");
        for (int i = 0; i < spreads.length(); i++) {
            JSONObject s = spreads.getJSONObject(i);
            double[][] zones = LightsMath.spread(Golden.colors(s.getJSONArray("colors")));
            double[][] want = Golden.colors(s.getJSONArray("zones"));
            assertEquals(LightsMath.ZONES, zones.length);
            for (int z = 0; z < zones.length; z++) assertArrayEquals("spread " + i + " zone " + z, want[z], zones[z], EPS);
            if (s.has("payload")) assertEquals("payload " + i, s.getString("payload"), LightsMath.payload(zones));
        }
    }

    @Test
    public void singleMatchesPython() throws Exception {
        JSONArray singles = Golden.DATA.getJSONArray("singles");
        for (int i = 0; i < singles.length(); i++) {
            JSONObject s = singles.getJSONObject(i);
            assertEquals("single " + i, s.getString("out"), LightsMath.single(s.getInt("h"), s.getInt("s"), s.getInt("v")));
        }
    }

    @Test
    public void decode103MatchesPython() throws Exception {
        JSONArray decodes = Golden.DATA.getJSONArray("decodes");
        for (int i = 0; i < decodes.length(); i++) {
            JSONObject d = decodes.getJSONObject(i);
            double[][] want = Golden.colors(d.optJSONArray("out"));
            double[][] got = LightsMath.decode103(d.getString("value"));
            if (want == null) {
                assertNull("decode " + i, got);
                continue;
            }
            assertEquals("decode " + i, want.length, got.length);
            for (int c = 0; c < want.length; c++) assertArrayEquals("decode " + i + " colour " + c, want[c], got[c], EPS);
        }
    }

    @Test
    public void originalColorsMatchPython() throws Exception {
        JSONArray originals = Golden.DATA.getJSONArray("originals");
        for (int i = 0; i < originals.length(); i++) {
            JSONObject o = originals.getJSONObject(i);
            double[][] want = Golden.colors(o.optJSONArray("colors"));
            double[][] got = LightsMath.originalColors(o.getJSONObject("state"));
            if (want == null) {
                assertNull("original " + i, got);
                continue;
            }
            assertEquals("original " + i, want.length, got.length);
            for (int c = 0; c < want.length; c++) assertArrayEquals(want[c], got[c], EPS);
            double[][] zones = LightsMath.zonesOf(got);
            double[][] wz = Golden.colors(o.getJSONArray("zones"));
            for (int z = 0; z < wz.length; z++) assertArrayEquals("original " + i + " zone " + z, wz[z], zones[z], EPS);
        }
    }

    @Test
    public void aWhiteCanBeSeenAsAColourForTheFadeBackToIt() throws Exception {
        double[] warm = LightsMath.whiteAsColour(new JSONObject().put("21", "white").put("22", 500).put("23", 0));
        assertArrayEquals(new double[]{28 / 360.0, 0.48, 0.5}, warm, EPS);
        double[] neutral = LightsMath.whiteAsColour(new JSONObject().put("21", "white").put("22", 1000).put("23", 500));
        assertEquals(0.0, neutral[1], EPS);
        assertNull(LightsMath.whiteAsColour(new JSONObject().put("21", "colour")));
        assertNull(LightsMath.whiteAsColour(null));
    }
}

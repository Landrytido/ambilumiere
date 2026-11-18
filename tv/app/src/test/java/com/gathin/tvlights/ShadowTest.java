package com.gathin.tvlights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ShadowTest {
    @Test
    public void takesWhatTheStripReportsAndNothingElse() throws Exception {
        Shadow s = new Shadow();
        s.merge(new JSONObject("{\"21\":\"white\",\"22\":500,\"20\":true}"), 0);
        JSONObject snap = s.snapshot();
        assertEquals("white", snap.getString("21"));
        assertEquals(500, snap.getInt("22"));
        assertFalse(snap.has("20"));
    }

    @Test
    public void ignoresUpdatesWhileHeldAndForTheTailAfter() throws Exception {
        Shadow s = new Shadow();
        s.merge(new JSONObject("{\"21\":\"white\"}"), 0);
        s.hold();
        assertTrue(s.holding());
        s.merge(new JSONObject("{\"21\":\"colour\",\"103\":\"x\"}"), 10);
        assertEquals("white", s.snapshot().getString("21"));
        s.release(1000, 1500);
        assertFalse(s.holding());
        s.merge(new JSONObject("{\"21\":\"colour\"}"), 2000);   // inside the tail: still our own echo
        assertEquals("white", s.snapshot().getString("21"));
        s.merge(new JSONObject("{\"21\":\"scene\"}"), 2600);    // after it: the app's
        assertEquals("scene", s.snapshot().getString("21"));
    }

    @Test
    public void seedStartsFromTheSavedState() throws Exception {
        Shadow s = new Shadow();
        s.seed(new JSONObject("{\"21\":\"colour\",\"24\":\"00f003e803d4\"}"));
        assertEquals("00f003e803d4", s.snapshot().getString("24"));
        s.seed(null);
        assertEquals("colour", s.snapshot().getString("21"));
    }

    @Test
    public void snapshotIsACopy() throws Exception {
        Shadow s = new Shadow();
        s.merge(new JSONObject("{\"21\":\"white\"}"), 0);
        s.snapshot().put("21", "music");
        assertEquals("white", s.snapshot().getString("21"));
    }
}

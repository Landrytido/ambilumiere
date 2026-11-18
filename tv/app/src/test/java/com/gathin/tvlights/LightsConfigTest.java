package com.gathin.tvlights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LightsConfigTest {
    // Made-up values, not real devices: parsing must keep them and nothing more.
    private static final String STRIP = "{\"id\":\"tv\",\"name\":\"Ruban TV\",\"kind\":\"strip\",\"host\":\"192.168.1.20\",\"port\":6668,"
            + "\"version\":\"3.5\",\"device_id\":\"abc123\",\"key\":\"k\",\"zones\":10}";
    private static final String BULB = "{\"id\":\"table\",\"name\":\"Table\",\"kind\":\"bulb\",\"host\":\"192.168.1.21\","
            + "\"version\":\"3.3\",\"device_id\":\"def456\",\"key\":\"k2\"}";
    private static final String OK = "{\"lights\":[" + STRIP + "," + BULB + "]}";

    @Test
    public void readsTheLights() {
        LightsConfig.Result r = LightsConfig.parse(OK, 7);
        assertNull(r.problem);
        assertNotNull(r.config);
        assertEquals(7, r.stamp);
        assertEquals(2, r.config.lights.size());
        LightsConfig.Light tv = r.config.lights.get(0);
        assertEquals("tv", tv.id);
        assertEquals("Ruban TV", tv.name);
        assertEquals("192.168.1.20", tv.host);
        assertEquals(6668, tv.port);
        assertEquals("3.5", tv.version);
        assertEquals("abc123", tv.deviceId);
        assertTrue(tv.isStrip());
        LightsConfig.Light table = r.config.lights.get(1);
        assertEquals("3.3", table.version);
        assertFalse(table.isStrip());
    }

    @Test
    public void portNameKindAndVersionAreOptional() {
        LightsConfig.Result r = LightsConfig.parse("{\"lights\":[{\"id\":\"a\",\"host\":\"h\",\"device_id\":\"i\",\"key\":\"k\"}]}", 0);
        assertNull(r.problem);
        LightsConfig.Light l = r.config.lights.get(0);
        assertEquals(6668, l.port);
        assertEquals("a", l.name);
        assertEquals("3.5", l.version);
        assertFalse(l.isStrip());
    }

    @Test
    public void saysWhyThereIsNoConfig() {
        assertEquals("unreadable", LightsConfig.parse("not json", 0).problem);
        // the single-light file of version 1.0 is not a config any more
        assertEquals("incomplete", LightsConfig.parse("{\"host\":\"h\",\"id\":\"i\",\"key\":\"k\"}", 0).problem);
        assertEquals("incomplete", LightsConfig.parse("{\"lights\":[]}", 0).problem);
        assertEquals("incomplete", LightsConfig.parse("{\"lights\":[{\"id\":\"a\",\"host\":\"h\",\"key\":\"k\"}]}", 0).problem);
        assertEquals("incomplete", LightsConfig.parse(OK.replace("\"3.3\"", "\"3.4\""), 0).problem);
        assertEquals("incomplete", LightsConfig.parse(OK.replace("\"port\":6668", "\"port\":0"), 0).problem);
        assertEquals("incomplete", LightsConfig.parse("{\"lights\":[" + BULB + "," + BULB + "]}", 0).problem);
        assertEquals("zones", LightsConfig.parse(OK.replace("\"zones\":10", "\"zones\":8"), 0).problem);
    }

    @Test
    public void sameAsComparesEverything() {
        LightsConfig.Light a = LightsConfig.parse(OK, 0).config.lights.get(0);
        assertTrue(a.sameAs(LightsConfig.parse(OK, 1).config.lights.get(0)));
        assertFalse(a.sameAs(LightsConfig.parse(OK.replace("abc123", "zzz"), 0).config.lights.get(0)));
        assertFalse(a.sameAs(LightsConfig.parse(OK, 0).config.lights.get(1)));
        assertFalse(a.sameAs(null));
    }
}

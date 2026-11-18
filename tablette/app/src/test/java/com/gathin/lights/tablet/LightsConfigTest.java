package com.gathin.lights.tablet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LightsConfigTest {
    // Made-up values, not real devices.
    private static final String STRIP = "{\"id\":\"tv\",\"name\":\"Ruban TV\",\"kind\":\"strip\",\"host\":\"192.168.1.20\",\"port\":6668,"
            + "\"version\":\"3.5\",\"device_id\":\"abc123\",\"key\":\"secretkey1\",\"zones\":10}";
    private static final String BULB = "{\"id\":\"table\",\"name\":\"Table\",\"kind\":\"bulb\",\"host\":\"192.168.1.21\","
            + "\"version\":\"3.3\",\"device_id\":\"def456\",\"key\":\"secretkey2\"}";
    private static final String OK = "{\"lights\":[" + STRIP + "," + BULB + "]}";

    @Test
    public void readsTheLights() {
        LightsConfig.Result r = LightsConfig.parse(OK, 7);
        assertNull(r.problem);
        assertNotNull(r.config);
        assertEquals(2, r.config.lights.size());
        LightsConfig.Light tv = r.config.lights.get(0);
        assertEquals("Ruban TV", tv.name);
        assertEquals("3.5", tv.version);
        assertTrue(tv.isStrip());
        assertEquals("3.3", r.config.lights.get(1).version);
        assertFalse(r.config.lights.get(1).isStrip());
    }

    @Test
    public void incompleteOrBrokenFilesGiveNoConfigAndNoKeyInTheMessage() {
        String[] bad = {"", "nope", "{}", "{\"lights\":[]}",
                "{\"lights\":[{\"id\":\"a\",\"host\":\"h\",\"key\":\"secretkey1\"}]}",
                "{\"lights\":[{\"id\":\"a\",\"host\":\"h\",\"device_id\":\"d\",\"key\":\"secretkey1\",\"version\":\"4.0\"}]}"};
        for (String text : bad) {
            LightsConfig.Result r = LightsConfig.parse(text, 0);
            assertNull(r.config);
            assertNotNull(r.problem);
            assertFalse(r.problem.contains("secretkey"));
        }
    }

    @Test
    public void aStripNeedsTenZones() {
        String s = STRIP.replace("\"zones\":10", "\"zones\":8");
        assertEquals("zones", LightsConfig.parse("{\"lights\":[" + s + "]}", 0).problem);
    }

    @Test
    public void duplicateIdsAreRefused() {
        assertNull(LightsConfig.parse("{\"lights\":[" + STRIP + "," + STRIP + "]}", 0).config);
    }
}

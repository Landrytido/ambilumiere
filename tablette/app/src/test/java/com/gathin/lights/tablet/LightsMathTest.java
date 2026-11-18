package com.gathin.lights.tablet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LightsMathTest {
    @Test
    public void hexAndParseAreInverse() {
        assertEquals("00f003e801f4", LightsMath.hex(240, 1000, 500));
        double[] c = LightsMath.parseHex24("00f003e801f4");
        assertArrayEquals(new double[]{240 / 360.0, 1.0, 0.5}, c, 1e-9);
        assertNull(LightsMath.parseHex24("short"));
    }

    @Test
    public void rgbOfTheBasicColours() {
        assertEquals(0xFFFF0000, LightsMath.rgb(0, 1, 1));
        assertEquals(0xFF00FF00, LightsMath.rgb(120 / 360.0, 1, 1));
        assertEquals(0xFF0000FF, LightsMath.rgb(240 / 360.0, 1, 1));
        assertEquals(0xFFFFFFFF, LightsMath.rgb(0.3, 0, 1));
    }

    @Test
    public void singleColourPayloadRoundTrips() {
        double[][] back = LightsMath.decode103(LightsMath.single(120, 800, 600));
        assertEquals(120 / 360.0, back[0][0], 1e-6);
        assertEquals(0.8, back[0][1], 1e-6);
        assertEquals(0.6, back[0][2], 1e-6);
    }
}

package com.gathin.lights.tablet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class LightCommandsTest {
    @Test
    public void warmWhiteOnABulbWithTemperatureUsesWhiteMode() {
        JSONObject d = LightCommands.look(false, true, LightCommands.WARM, 50);
        assertEquals("white", d.optString("21"));
        assertEquals(500, d.optInt("22"));
        assertEquals(0, d.optInt("23"));
        assertTrue(d.optBoolean("20"));
    }

    @Test
    public void warmWhiteOnTheTableBulbIsAPaleAmberColour() {
        JSONObject d = LightCommands.look(false, false, LightCommands.WARM, 100);
        assertEquals("colour", d.optString("21"));
        assertFalse(d.has("23"));
        assertFalse(d.has("103"));
        assertEquals("001c01e003e8", d.optString("24"));
    }

    @Test
    public void aColourOfTheStripGoesToDatapoints24And103() {
        JSONObject d = LightCommands.colour(true, 240, 1000, 500);
        assertEquals("00f003e801f4", d.optString("24"));
        assertTrue(d.has("103"));
        assertEquals("colour", d.optString("21"));
    }

    @Test
    public void aBulbColourHasNoDatapoint103() {
        assertFalse(LightCommands.colour(false, 120, 1000, 1000).has("103"));
    }

    @Test
    public void brightnessNeverGoesBelowTheLightsOwnMinimum() {
        // The screen never asks for less than 10 % (100); the datapoint itself starts at 10.
        assertEquals(10, LightCommands.look(false, true, LightCommands.WARM, 0).optInt("22"));
        assertEquals(100, LightCommands.look(false, true, LightCommands.WARM, LightCommands.MIN_LEVEL).optInt("22"));
    }

    @Test
    public void swatchHuesAndNames() {
        assertEquals(0, LightCommands.swatchHue(0));
        assertEquals(25, LightCommands.swatchHue(2));
        assertEquals(280, LightCommands.swatchHue(7));
        assertEquals(1, LightCommands.colourName(false, 0.0, 1.0, 0));
        assertEquals(6, LightCommands.colourName(false, 240 / 360.0, 1.0, 0));
        assertEquals(LightCommands.WARM, LightCommands.colourName(true, 0, 0, 0));
        assertEquals(LightCommands.COOL_WHITE, LightCommands.colourName(true, 0, 0, 900));
    }

    @Test
    public void builtInScenesWorkOutPerKindOfLight() {
        JSONObject cinema = LightCommands.scene(LightCommands.SCENE_CINEMA, true, true);
        assertEquals(100, cinema.optInt("22"));
        assertNull(LightCommands.scene(LightCommands.SCENE_CINEMA, false, true));
    }
}

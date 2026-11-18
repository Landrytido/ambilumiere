package com.gathin.tvlights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class LightCommandsTest {
    @Test
    public void colourDatapointIsHexOfHsv() {
        assertEquals("001e02bc0190", LightsMath.hex(30, 700, 400));
        assertEquals("010803e803e8", LightsMath.hex(264, 1000, 1000));
        assertEquals("0000000a0064", LightsMath.hex(0, 10, 100));
        assertEquals("001e02bc0190", LightsMath.hex(new double[]{30 / 360.0, 0.7, 0.4}));
    }

    @Test
    public void readsDatapoint24Back() {
        double[] c = LightsMath.parseHex24("00f003e803e8");
        assertEquals(240 / 360.0, c[0], 1e-9);
        assertEquals(1.0, c[1], 1e-9);
        assertNull(LightsMath.parseHex24("00f0"));
        assertNull(LightsMath.parseHex24("zzzzzzzzzzzz"));
    }

    @Test
    public void dotColours() {
        assertEquals(0xFFFF0000, LightsMath.rgb(0, 1, 1));
        assertEquals(0xFF00FF00, LightsMath.rgb(1 / 3.0, 1, 1));
        assertEquals(0xFF0000FF, LightsMath.rgb(2 / 3.0, 1, 1));
        assertEquals(0xFFFFFFFF, LightsMath.rgb(0.3, 0, 1));
        assertEquals(0xFFFFB16E, LightsMath.whiteRgb(0));
        assertEquals(0xFFCDE0FF, LightsMath.whiteRgb(1000));
    }

    @Test
    public void warmWhiteAtFullLightOnABulbWithTemperature() {
        JSONObject d = LightCommands.look(false, true, LightCommands.WARM, 100);
        assertTrue(d.optBoolean("20"));
        assertEquals("white", d.optString("21"));
        assertEquals(1000, d.optInt("22"));
        assertEquals(0, d.optInt("23"));
    }

    @Test
    public void aWhiteOnABulbWithoutTemperatureIsAPaleColour() {
        JSONObject d = LightCommands.look(false, false, LightCommands.WARM, 50);
        assertEquals("colour", d.optString("21"));
        assertEquals(LightsMath.hex(28, 480, 500), d.optString("24"));
        assertFalse(d.has("22") || d.has("23") || d.has("103"));
    }

    @Test
    public void aColourOnABulbIsFullSaturationAtTheLevel() {
        JSONObject d = LightCommands.look(false, true, 6, 20);   // blue
        assertEquals(LightsMath.hex(240, 1000, 200), d.optString("24"));
        assertEquals("colour", d.optString("21"));
        assertTrue(d.optBoolean("20"));
        assertFalse(d.has("103"));
    }

    @Test
    public void theStripGetsDatapoint103TooAndHasWhiteTemperature() {
        JSONObject d = LightCommands.look(true, false, 1, 100);   // red
        assertEquals(LightsMath.single(0, 1000, 1000), d.optString("103"));
        assertEquals(LightsMath.hex(0, 1000, 1000), d.optString("24"));
        JSONObject w = LightCommands.look(true, false, LightCommands.WARM, 70);   // warm white
        assertEquals("white", w.optString("21"));
        assertEquals(700, w.optInt("22"));
        assertEquals(0, w.optInt("23"));
    }

    @Test
    public void brightnessNeverDropsBelowTheLightsMinimum() {
        assertEquals(10, LightCommands.look(false, true, 0, 1).optInt("22"));
    }

    @Test
    public void scenes() {
        // all on: soft warm white, 100 %, strip included
        assertEquals("white", LightCommands.scene("all_on", true, true).optString("21"));
        assertEquals(1000, LightCommands.scene("all_on", true, true).optInt("22"));
        assertEquals(0, LightCommands.scene("all_on", false, true).optInt("23"));
        // evening: bulbs amber 30 deg 70 % 40 %, the strip amber at about 30 %
        assertEquals(LightsMath.hex(30, 700, 400), LightCommands.scene("evening", false, true).optString("24"));
        JSONObject strip = LightCommands.scene("evening", true, true);
        assertEquals(LightsMath.hex(30, 700, 300), strip.optString("24"));
        assertEquals(LightsMath.single(30, 700, 300), strip.optString("103"));
        // cinema: bulbs off (null: power off), the strip warm white at 10 %
        assertNull(LightCommands.scene("cinema", false, true));
        JSONObject cinema = LightCommands.scene("cinema", true, true);
        assertEquals("white", cinema.optString("21"));
        assertEquals(100, cinema.optInt("22"));
        assertEquals(0, cinema.optInt("23"));
        // all off
        assertNull(LightCommands.scene("all_off", true, true));
    }

    @Test
    public void onlyTheFourScenesAreScenes() {
        assertTrue(LightCommands.isScene("evening"));
        assertFalse(LightCommands.isScene("party"));
        assertFalse(LightCommands.isScene(null));
    }

    @Test
    public void commandReceiverTakesOnlyKnownCommands() {
        assertTrue(CommandReceiver.valid("toggle", "table", null));
        assertTrue(CommandReceiver.valid("off", "all", null));
        assertTrue(CommandReceiver.valid("on", null, null));
        assertTrue(CommandReceiver.valid("scene", "all", "cinema"));
        assertFalse(CommandReceiver.valid("scene", "all", "nope"));
        assertFalse(CommandReceiver.valid("scene", "all", null));
        assertFalse(CommandReceiver.valid("look", "table", null));
        assertFalse(CommandReceiver.valid("toggle", "../x", null));
        assertFalse(CommandReceiver.valid(null, "all", null));
    }

    @Test
    public void namesWhatALightShows() {
        assertEquals(LightCommands.WARM, LightCommands.colourName(true, 0, 0, 0));
        assertEquals(LightCommands.NEUTRAL_WHITE, LightCommands.colourName(true, 0, 0, 500));
        assertEquals(LightCommands.COOL_WHITE, LightCommands.colourName(true, 0, 0, 1000));
        assertEquals(1, LightCommands.colourName(false, 0, 1, 0));
        assertEquals(2, LightCommands.colourName(false, 30 / 360.0, 0.7, 0));   // the evening amber
        assertEquals(LightCommands.WARM, LightCommands.colourName(false, 28 / 360.0, 0.48, 0));   // a bulb's warm white
        assertEquals(4, LightCommands.colourName(false, 120 / 360.0, 1, 0));
        assertEquals(6, LightCommands.colourName(false, 240 / 360.0, 1, 0));
        assertEquals(7, LightCommands.colourName(false, 280 / 360.0, 1, 0));
        assertEquals(LightCommands.NEUTRAL_WHITE, LightCommands.colourName(false, 0.5, 0.1, 0));
    }

    @Test
    public void everySwatchIsNamedAsItself() {
        for (int i = 1; i < LightCommands.SWATCHES; i++) {
            JSONObject d = LightCommands.look(false, true, i, 100);
            double[] hsv = LightsMath.parseHex24(d.optString("24"));
            assertEquals(i, LightCommands.colourName(false, hsv[0], hsv[1], 0));
        }
    }

    @Test
    public void thePowerCommand() {
        assertEquals("{\"20\":false}", LightCommands.power(false).toString());
    }
}

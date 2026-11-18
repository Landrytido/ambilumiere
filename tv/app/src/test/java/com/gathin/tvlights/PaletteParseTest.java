package com.gathin.tvlights;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaletteParseTest {
    @Test
    public void offIsRecognisedWhateverTheCase() {
        assertTrue(LightsController.isOff("off"));
        assertTrue(LightsController.isOff(" OFF "));
        assertFalse(LightsController.isOff("#FF0000"));
        assertFalse(LightsController.isOff(null));
    }

    @Test
    public void parsesTheLauncherLine() {
        double[][] p = LightsController.parsePalette("#FF0000,#00FF00,#0000FF");
        assertEquals(3, p.length);
        assertArrayEquals(new double[]{0.0, 1.0, 1.0}, p[0], 1e-9);
        assertArrayEquals(new double[]{1.0 / 3, 1.0, 1.0}, p[1], 1e-9);
    }

    @Test
    public void skipsEmptyPartsAndCapsTheCount() {
        assertEquals(2, LightsController.parsePalette("#FF0000,,#00FF00,").length);
        assertEquals(8, LightsController.parsePalette("#111111,#222222,#333333,#444444,#555555,#666666,#777777,#888888,#999999").length);
    }

    @Test
    public void refusesWhatIsNotAPalette() {
        assertNull(LightsController.parsePalette(null));
        assertNull(LightsController.parsePalette(""));
        assertNull(LightsController.parsePalette("red,blue"));
        assertNull(LightsController.parsePalette("#12345"));
        assertNull(LightsController.parsePalette("#GG0000"));
    }
}

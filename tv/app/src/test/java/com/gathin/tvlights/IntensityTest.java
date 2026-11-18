package com.gathin.tvlights;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntensityTest {
    private static final double[][] PALETTE = {
            {0.00, 1.00, 1.00}, {0.33, 0.80, 0.85}, {0.60, 0.55, 0.60}, {0.10, 0.00, 0.80}};

    @Test
    public void livelyIsTheColoursAsTheyAre() {
        for (double[] c : PALETTE) assertSame(c, Intensity.apply(c, Intensity.LIVELY));
    }

    @Test
    public void normalKeepsTheColourAndTakesAboutThreeQuartersOfTheBrightness() {
        double[] c = Intensity.apply(new double[]{0.5, 0.9, 1.0}, Intensity.NORMAL);
        assertEquals(0.5, c[0], 1e-9);
        assertEquals(0.9, c[1], 1e-9);
        assertEquals(0.85, c[2], 1e-9);
    }

    @Test
    public void softIsDimmerAndSlightlyLessSaturated() {
        double[] c = Intensity.apply(new double[]{0.5, 0.9, 1.0}, Intensity.SOFT);
        assertEquals(0.55, c[2], 1e-9);
        assertEquals(0.765, c[1], 1e-9);
    }

    @Test
    public void nothingEverExceedsTheColourItStartedFrom() {
        for (int mode = Intensity.SOFT; mode <= Intensity.LIVELY; mode++) {
            for (double[] c : PALETTE) {
                double[] out = Intensity.apply(c, mode);
                assertTrue(out[2] <= c[2] + 1e-12);
                assertTrue(out[1] <= c[1] + 1e-12);
                assertEquals(c[0], out[0], 0);
            }
        }
    }

    @Test
    public void aSoftLightStaysVisibleAndAWhiteStaysWhite() {
        double[] dim = Intensity.apply(new double[]{0.2, 0.9, 0.25}, Intensity.SOFT);
        assertTrue(dim[2] >= Intensity.MIN_BRIGHTNESS - 1e-12);
        double[] white = Intensity.apply(new double[]{0.1, 0.0, 0.8}, Intensity.SOFT);
        assertEquals(0.0, white[1], 0);
        double[] pale = Intensity.apply(new double[]{0.1, 0.45, 1.0}, Intensity.SOFT);
        assertTrue(pale[1] >= Intensity.MIN_SATURATION - 1e-12);
    }

    @Test
    public void theModesAreOrderedAndTheDefaultIsNormal() {
        assertEquals(Intensity.NORMAL, Intensity.clamp(-1));
        assertEquals(Intensity.NORMAL, Intensity.clamp(7));
        assertEquals(Intensity.SOFT, Intensity.clamp(0));
        double v = 0.9;
        double soft = Intensity.apply(new double[]{0, 1, v}, Intensity.SOFT)[2];
        double normal = Intensity.apply(new double[]{0, 1, v}, Intensity.NORMAL)[2];
        double lively = Intensity.apply(new double[]{0, 1, v}, Intensity.LIVELY)[2];
        assertTrue(soft < normal && normal < lively);
    }

    @Test
    public void thePaletteIsScaledColourByColour() {
        double[][] out = Intensity.apply(PALETTE, Intensity.NORMAL);
        assertEquals(PALETTE.length, out.length);
        assertArrayEquals(new double[]{0.0, 1.0, 0.85}, out[0], 1e-9);
    }
}

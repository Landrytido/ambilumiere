package com.gathin.lights.tablet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class TimerStoreTest {
    private static long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, mo, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void todayIfTheTimeIsStillAhead() {
        assertEquals(at(2026, Calendar.NOVEMBER, 3, 23, 0), TimerStore.nextDaily(at(2026, Calendar.NOVEMBER, 3, 21, 15), 23 * 60));
    }

    @Test
    public void tomorrowIfTheTimeHasPassedOrIsNow() {
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 7, 30), TimerStore.nextDaily(at(2026, Calendar.NOVEMBER, 3, 8, 0), 7 * 60 + 30));
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 23, 0), TimerStore.nextDaily(at(2026, Calendar.NOVEMBER, 3, 23, 0), 23 * 60));
    }

    @Test
    public void overTheEndOfTheMonth() {
        long next = TimerStore.nextDaily(at(2026, Calendar.NOVEMBER, 30, 23, 30), 23 * 60);
        assertEquals(at(2026, Calendar.DECEMBER, 1, 23, 0), next);
        assertTrue(next > at(2026, Calendar.NOVEMBER, 30, 23, 30));
    }
}

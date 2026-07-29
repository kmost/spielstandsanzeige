package de.kmost.scoreboard.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.kmost.scoreboard.model.ClockDirection;
import org.junit.jupiter.api.Test;

class TimeFormatterTest {

    private static final long PERIOD = 30 * 60 * 1000L;

    @Test
    void upShowsElapsedFloored() {
        assertEquals("00:00", TimeFormatter.formatClock(0, PERIOD, ClockDirection.UP));
        assertEquals("00:59", TimeFormatter.formatClock(59_999, PERIOD, ClockDirection.UP));
        assertEquals("30:00", TimeFormatter.formatClock(PERIOD, PERIOD, ClockDirection.UP));
        // 2. Halbzeit läuft vorwärts ab 30:00 weiter
        assertEquals("30:10", TimeFormatter.formatClock(PERIOD + 10_000, 2 * PERIOD, ClockDirection.UP));
    }

    @Test
    void downShowsRemainingCeiled() {
        assertEquals("30:00", TimeFormatter.formatClock(0, PERIOD, ClockDirection.DOWN));
        // angebrochene Sekunde wird aufgerundet: erst bei echtem Ablauf 0:00
        assertEquals("30:00", TimeFormatter.formatClock(100, PERIOD, ClockDirection.DOWN));
        assertEquals("29:59", TimeFormatter.formatClock(1_000, PERIOD, ClockDirection.DOWN));
        assertEquals("00:01", TimeFormatter.formatClock(PERIOD - 999, PERIOD, ClockDirection.DOWN));
        assertEquals("00:00", TimeFormatter.formatClock(PERIOD, PERIOD, ClockDirection.DOWN));
    }

    @Test
    void downSecondHalfStartsAtFullPeriodAgain() {
        assertEquals("30:00", TimeFormatter.formatClock(PERIOD, 2 * PERIOD, ClockDirection.DOWN));
        assertEquals("29:50", TimeFormatter.formatClock(PERIOD + 10_000, 2 * PERIOD, ClockDirection.DOWN));
    }

    @Test
    void parsesClockInput() {
        assertEquals(754_000, TimeFormatter.parseClockInput("12:34"));
        assertEquals(754_000, TimeFormatter.parseClockInput(" 12:34 "));
        assertEquals(65_000, TimeFormatter.parseClockInput("1:05"));
        assertEquals(65_000, TimeFormatter.parseClockInput("1:5"));
        assertEquals(720_000, TimeFormatter.parseClockInput("12"));
        assertEquals(0, TimeFormatter.parseClockInput("0:00"));
    }

    @Test
    void rejectsInvalidClockInput() {
        for (String input : new String[]{"", "abc", "12:61", "-1:00", "1:2:3", "12,34"}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseClockInput(input), input);
        }
    }

    @Test
    void formatRemainingForPenalties() {
        assertEquals("2:00", TimeFormatter.formatRemaining(120_000));
        assertEquals("1:59", TimeFormatter.formatRemaining(119_000));
        assertEquals("0:01", TimeFormatter.formatRemaining(999));
        assertEquals("0:00", TimeFormatter.formatRemaining(0));
    }
}

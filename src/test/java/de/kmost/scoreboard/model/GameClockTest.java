package de.kmost.scoreboard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameClockTest {

    private static final Duration PERIOD = Duration.ofMinutes(30);
    private static final long PERIOD_MILLIS = PERIOD.toMillis();

    private FakeNanoTime time;

    @BeforeEach
    void setUp() {
        time = new FakeNanoTime();
    }

    private GameClock clock(GameMode mode) {
        GameConfig config = new GameConfig("Heim", "Gast", mode, PERIOD,
                ClockDirection.UP, SportProfile.HANDBALL);
        return new GameClock(config, time);
    }

    @Test
    void elapsedFollowsTimeSource() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        assertEquals(10_000, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
    }

    @Test
    void pauseFreezesClock() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.pause();
        time.advanceMillis(60_000);
        clock.tick();
        assertEquals(10_000, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.PAUSED, clock.phaseProperty().get());
    }

    @Test
    void resumeContinuesWithoutJump() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.pause();
        time.advanceMillis(60_000);
        clock.start();
        time.advanceMillis(5_000);
        clock.tick();
        assertEquals(15_000, clock.elapsedMillisProperty().get());
    }

    @Test
    void clampsExactlyAtHalfTimeAndFiresCallbackOnce() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        AtomicInteger hornCount = new AtomicInteger();
        clock.setOnPeriodEnd(hornCount::incrementAndGet);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS + 7_000);
        clock.tick();
        assertEquals(PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        assertFalse(clock.runningProperty().get());
        assertEquals(1, hornCount.get());
        time.advanceMillis(5_000);
        clock.tick();
        assertEquals(1, hornCount.get());
        assertEquals(PERIOD_MILLIS, clock.elapsedMillisProperty().get());
    }

    @Test
    void secondHalfRunsFromHalfTimeMarkToFinish() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        AtomicInteger hornCount = new AtomicInteger();
        clock.setOnPeriodEnd(hornCount::incrementAndGet);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        clock.startNextPeriod();
        assertEquals(2, clock.periodProperty().get());
        assertTrue(clock.runningProperty().get());
        time.advanceMillis(10_000);
        clock.tick();
        assertEquals(PERIOD_MILLIS + 10_000, clock.elapsedMillisProperty().get());
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(2 * PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(2, hornCount.get());
    }

    @Test
    void singlePeriodFinishesDirectly() {
        GameClock clock = clock(GameMode.SINGLE_PERIOD);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS + 1);
        clock.tick();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(PERIOD_MILLIS, clock.elapsedMillisProperty().get());
    }

    @Test
    void finishStopsClockImmediatelyAndIsFinal() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.finish();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertFalse(clock.runningProperty().get());
        assertEquals(10_000, clock.elapsedMillisProperty().get());
        clock.start();
        assertFalse(clock.runningProperty().get());
    }

    @Test
    void startNextPeriodIgnoredWhileRunning() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(1_000);
        clock.tick();
        clock.startNextPeriod();
        assertEquals(1, clock.periodProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
    }

    @Test
    void startIgnoredDuringHalfTimeAndAfterFinish() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        clock.start();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        assertFalse(clock.runningProperty().get());
    }

    @Test
    void setElapsedAdjustsPausedClock() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.pause();
        clock.setElapsed(120_000);
        assertEquals(120_000, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.PAUSED, clock.phaseProperty().get());
        clock.start();
        time.advanceMillis(5_000);
        clock.tick();
        assertEquals(125_000, clock.elapsedMillisProperty().get());
    }

    @Test
    void setElapsedWhileRunningContinuesFromNewTime() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.setElapsed(60_000);
        time.advanceMillis(2_000);
        clock.tick();
        assertEquals(62_000, clock.elapsedMillisProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
    }

    @Test
    void setElapsedIsClampedToCurrentPeriod() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        clock.startNextPeriod();
        time.advanceMillis(10_000);
        clock.tick();
        // 2. Halbzeit: Werte vor Periodenbeginn werden auf 30:00 begrenzt
        clock.setElapsed(5_000);
        assertEquals(PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        clock.setElapsed(3 * PERIOD_MILLIS);
        assertEquals(2 * PERIOD_MILLIS, clock.elapsedMillisProperty().get());
    }

    @Test
    void setElapsedReopensHalfTime() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS + 5_000);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        clock.setElapsed(PERIOD_MILLIS - 30_000);
        assertEquals(GameClock.Phase.PAUSED, clock.phaseProperty().get());
        assertEquals(PERIOD_MILLIS - 30_000, clock.elapsedMillisProperty().get());
        assertEquals(1, clock.periodProperty().get());
    }

    @Test
    void setElapsedIgnoredAfterFinish() {
        GameClock clock = clock(GameMode.SINGLE_PERIOD);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        clock.setElapsed(10_000);
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(PERIOD_MILLIS, clock.elapsedMillisProperty().get());
    }
}

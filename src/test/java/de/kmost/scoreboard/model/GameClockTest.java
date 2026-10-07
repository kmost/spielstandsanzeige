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

    private static final Duration OVERTIME = Duration.ofMinutes(5);
    private static final long OVERTIME_MILLIS = OVERTIME.toMillis();

    private GameClock clock(GameMode mode) {
        GameConfig config = new GameConfig("Heim", "Gast", mode, PERIOD,
                ClockDirection.UP, SportProfile.HANDBALL);
        return new GameClock(config, time);
    }

    private GameClock clock(GameMode mode, OvertimeFormat overtimeFormat) {
        GameConfig config = new GameConfig("Heim", "Gast", mode, PERIOD,
                ClockDirection.UP, overtimeFormat, OVERTIME, SportProfile.HANDBALL);
        return new GameClock(config, time);
    }

    /** Spielt alle regulären Abschnitte durch, bis die Uhr auf FINISHED steht. */
    private void playRegulation(GameClock clock, GameMode mode) {
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        for (int period = 2; period <= mode.periodCount(); period++) {
            clock.startNextPeriod();
            time.advanceMillis(PERIOD_MILLIS);
            clock.tick();
        }
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
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
        clock.addOnPeriodEnd(hornCount::incrementAndGet);
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
        clock.addOnPeriodEnd(hornCount::incrementAndGet);
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
    void threeThirdsPauseTwiceAndFinishAfterThirdPeriod() {
        GameClock clock = clock(GameMode.THREE_THIRDS);
        AtomicInteger hornCount = new AtomicInteger();
        clock.addOnPeriodEnd(hornCount::incrementAndGet);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        clock.startNextPeriod();
        assertEquals(2, clock.periodProperty().get());
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        assertEquals(2 * PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        clock.startNextPeriod();
        assertEquals(3, clock.periodProperty().get());
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(3 * PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        assertEquals(3, hornCount.get());
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
    void overtimeAsSinglePeriodRunsAndFinishes() {
        GameClock clock = clock(GameMode.TWO_HALVES, OvertimeFormat.SINGLE_PERIOD);
        AtomicInteger hornCount = new AtomicInteger();
        clock.addOnPeriodEnd(hornCount::incrementAndGet);
        playRegulation(clock, GameMode.TWO_HALVES);
        clock.startOvertime();
        assertEquals(3, clock.periodProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
        assertEquals(2 * PERIOD_MILLIS + OVERTIME_MILLIS, clock.currentPeriodEndMillis());
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(2 * PERIOD_MILLIS + OVERTIME_MILLIS, clock.elapsedMillisProperty().get());
        assertEquals(3, hornCount.get());
    }

    @Test
    void overtimeInTwoHalvesPausesBetweenHalves() {
        GameClock clock = clock(GameMode.TWO_HALVES, OvertimeFormat.TWO_HALVES);
        playRegulation(clock, GameMode.TWO_HALVES);
        clock.startOvertime();
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        clock.startNextPeriod();
        assertEquals(4, clock.periodProperty().get());
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertEquals(2 * PERIOD_MILLIS + 2 * OVERTIME_MILLIS, clock.elapsedMillisProperty().get());
    }

    @Test
    void secondOvertimeContinuesAfterFirst() {
        GameClock clock = clock(GameMode.TWO_HALVES, OvertimeFormat.TWO_HALVES);
        playRegulation(clock, GameMode.TWO_HALVES);
        clock.startOvertime();
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        clock.startNextPeriod();
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        clock.startOvertime();
        assertEquals(5, clock.periodProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
        time.advanceMillis(OVERTIME_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        assertEquals(2 * PERIOD_MILLIS + 3 * OVERTIME_MILLIS, clock.elapsedMillisProperty().get());
    }

    @Test
    void startOvertimeIgnoredWhileRunningAndAfterAbort() {
        GameClock clock = clock(GameMode.TWO_HALVES, OvertimeFormat.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.startOvertime();
        assertEquals(1, clock.periodProperty().get());
        assertEquals(GameClock.Phase.RUNNING, clock.phaseProperty().get());
        // Spielabbruch: Uhr steht vor dem Abschnittsende — keine Verlängerung mehr möglich
        clock.finish();
        assertFalse(clock.canStartOvertime());
        clock.startOvertime();
        assertEquals(1, clock.periodProperty().get());
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
        assertFalse(clock.runningProperty().get());
    }

    @Test
    void setElapsedIsClampedToOvertimeSegment() {
        GameClock clock = clock(GameMode.TWO_HALVES, OvertimeFormat.SINGLE_PERIOD);
        playRegulation(clock, GameMode.TWO_HALVES);
        clock.startOvertime();
        time.advanceMillis(10_000);
        clock.tick();
        clock.setElapsed(5_000);
        assertEquals(2 * PERIOD_MILLIS, clock.elapsedMillisProperty().get());
        clock.setElapsed(10 * PERIOD_MILLIS);
        assertEquals(2 * PERIOD_MILLIS + OVERTIME_MILLIS, clock.elapsedMillisProperty().get());
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

    @Test
    void runningIsDerivedFromPhaseThroughAllTransitions() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        assertFalse(clock.runningProperty().get());
        clock.start();
        assertTrue(clock.runningProperty().get());
        clock.pause();
        assertFalse(clock.runningProperty().get());
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        assertFalse(clock.runningProperty().get());
        clock.startNextPeriod();
        assertTrue(clock.runningProperty().get());
        clock.finish();
        assertFalse(clock.runningProperty().get());
    }

    @Test
    void periodEndListenersSeeFinalTimeAndPhase() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        java.util.List<String> seen = new java.util.ArrayList<>();
        clock.addOnPeriodEnd(() -> seen.add(
                clock.phaseProperty().get() + "@" + clock.elapsedMillisProperty().get()));
        clock.start();
        time.advanceMillis(PERIOD_MILLIS + 1_000);
        clock.tick();
        assertEquals(java.util.List.of("HALF_TIME@" + PERIOD_MILLIS), seen);
    }

    @Test
    void severalPeriodEndListenersAllFireAndCanBeRemoved() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Runnable firstListener = first::incrementAndGet;
        clock.addOnPeriodEnd(firstListener);
        clock.addOnPeriodEnd(second::incrementAndGet);
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(1, first.get());
        assertEquals(1, second.get());

        clock.removeOnPeriodEnd(firstListener);
        clock.startNextPeriod();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertEquals(1, first.get());
        assertEquals(2, second.get());
    }

    @Test
    void canStartOvertimePropertyFollowsRegulationEndAndClose() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        assertFalse(clock.canStartOvertimeProperty().get());
        clock.start();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        clock.startNextPeriod();
        time.advanceMillis(PERIOD_MILLIS);
        clock.tick();
        assertTrue(clock.canStartOvertimeProperty().get());
        assertEquals(clock.canStartOvertime(), clock.canStartOvertimeProperty().get());

        clock.close();
        assertFalse(clock.canStartOvertimeProperty().get());
    }

    @Test
    void canStartOvertimePropertyIsFalseAfterAbort() {
        GameClock clock = clock(GameMode.TWO_HALVES);
        clock.start();
        time.advanceMillis(10_000);
        clock.tick();
        clock.finish();
        assertFalse(clock.canStartOvertimeProperty().get());
    }
}

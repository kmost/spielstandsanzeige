package de.kmost.scoreboard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameStateTest {

    private FakeNanoTime time;
    private GameState state;

    @BeforeEach
    void setUp() {
        time = new FakeNanoTime();
        // kurze Perioden (1 min), damit Halbzeit-Szenarien handlich bleiben
        GameConfig config = new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                Duration.ofMinutes(1), ClockDirection.UP, SportProfile.HANDBALL);
        state = new GameState(config, time);
    }

    @Test
    void goalsClampAtZero() {
        state.removeGoal(TeamSide.HOME);
        assertEquals(0, state.scoreProperty(TeamSide.HOME).get());
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.HOME);
        state.removeGoal(TeamSide.HOME);
        assertEquals(1, state.scoreProperty(TeamSide.HOME).get());
        assertEquals(0, state.scoreProperty(TeamSide.GUEST).get());
    }

    @Test
    void penaltyCountsDownWithClock() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(10_000);
        state.tick();
        assertEquals(110_000, penaltyRemaining(TeamSide.HOME));
    }

    @Test
    void penaltyFreezesWhileClockPaused() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(10_000);
        state.tick();
        state.clock().pause();
        time.advanceMillis(30_000);
        state.tick();
        assertEquals(110_000, penaltyRemaining(TeamSide.HOME));
    }

    @Test
    void expiredPenaltyIsRemoved() {
        state.clock().start();
        state.addPenalty(TeamSide.GUEST);
        time.advanceMillis(70_000); // Periode 1 endet nach 60 s, Uhr klemmt dort
        state.tick();
        assertEquals(1, state.penalties(TeamSide.GUEST).size());
        assertEquals(60_000, penaltyRemaining(TeamSide.GUEST));
        // Strafe läuft in Halbzeit 2 weiter und läuft dort ab
        state.clock().startNextPeriod();
        time.advanceMillis(61_000);
        state.tick();
        assertTrue(state.penalties(TeamSide.GUEST).isEmpty());
    }

    @Test
    void penaltySpansHalfTime() {
        // eigene Config mit 5-min-Halbzeiten, damit die 2-min-Strafe in HZ 2 ablaufen kann
        GameConfig config = new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                Duration.ofMinutes(5), ClockDirection.UP, SportProfile.HANDBALL);
        state = new GameState(config, time);
        state.clock().start();
        time.advanceMillis(290_000); // 10 s vor der Halbzeit
        state.tick();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(20_000); // Uhr klemmt bei 300 s (Halbzeit)
        state.tick();
        assertEquals(110_000, penaltyRemaining(TeamSide.HOME));
        time.advanceMillis(600_000); // Halbzeitpause: Strafe steht
        state.tick();
        assertEquals(110_000, penaltyRemaining(TeamSide.HOME));
        state.clock().startNextPeriod();
        time.advanceMillis(109_000);
        state.tick();
        assertEquals(1_000, penaltyRemaining(TeamSide.HOME));
        time.advanceMillis(2_000);
        state.tick();
        assertTrue(state.penalties(TeamSide.HOME).isEmpty());
    }

    @Test
    void multiplePenaltiesRunIndependently() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(10_000);
        state.tick();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(5_000);
        state.tick();
        assertEquals(2, state.penalties(TeamSide.HOME).size());
        assertEquals(105_000, state.penalties(TeamSide.HOME).get(0).remainingMillisProperty().get());
        assertEquals(115_000, state.penalties(TeamSide.HOME).get(1).remainingMillisProperty().get());
    }

    @Test
    void penaltyCanBeRemovedManually() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        state.removePenalty(state.penalties(TeamSide.HOME).get(0));
        assertTrue(state.penalties(TeamSide.HOME).isEmpty());
    }

    @Test
    void penaltyCanBeExtendedToFourMinutesOnce() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        time.advanceMillis(60_000);
        state.tick();
        PenaltyTimer timer = state.penalties(TeamSide.HOME).get(0);
        state.extendPenalty(timer);
        assertEquals(180_000, penaltyRemaining(TeamSide.HOME));
        state.extendPenalty(timer); // zweites Verlängern ändert nichts
        assertEquals(180_000, penaltyRemaining(TeamSide.HOME));
        assertTrue(timer.isExtended());
    }

    @Test
    void penaltyStoresPlayerNumber() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME, " 7 ");
        state.addPenalty(TeamSide.HOME, "  ");
        assertEquals("7", state.penalties(TeamSide.HOME).get(0).playerNumber());
        assertNull(state.penalties(TeamSide.HOME).get(1).playerNumber());
    }

    @Test
    void teamTimeoutPausesClockAndCountsDownInRealTime() {
        state.clock().start();
        time.advanceMillis(10_000);
        state.tick();
        state.startTeamTimeout(TeamSide.HOME);
        assertEquals(GameClock.Phase.PAUSED, state.clock().phaseProperty().get());
        assertEquals(1, state.timeoutsUsedProperty(TeamSide.HOME).get());
        time.advanceMillis(15_000);
        state.tick();
        // Spieluhr steht, aber der Timeout-Countdown läuft in Echtzeit
        assertEquals(10_000, state.clock().elapsedMillisProperty().get());
        assertEquals(45_000, state.activeTimeoutProperty().get().remainingMillisProperty().get());
    }

    @Test
    void teamTimeoutEndsAutomaticallyWithSignal() {
        AtomicInteger hornCount = new AtomicInteger();
        state.setOnTimeoutEnd(hornCount::incrementAndGet);
        state.clock().start();
        state.startTeamTimeout(TeamSide.GUEST);
        time.advanceMillis(60_000);
        state.tick();
        assertNull(state.activeTimeoutProperty().get());
        assertEquals(1, hornCount.get());
    }

    @Test
    void secondTimeoutIgnoredWhileOneActive() {
        state.clock().start();
        state.startTeamTimeout(TeamSide.HOME);
        state.startTeamTimeout(TeamSide.GUEST);
        assertEquals(1, state.timeoutsUsedProperty(TeamSide.HOME).get());
        assertEquals(0, state.timeoutsUsedProperty(TeamSide.GUEST).get());
    }

    @Test
    void timeoutNotAllowedBeforeStart() {
        state.startTeamTimeout(TeamSide.HOME);
        assertNull(state.activeTimeoutProperty().get());
        assertEquals(0, state.timeoutsUsedProperty(TeamSide.HOME).get());
    }

    @Test
    void resumingClockEndsActiveTimeoutWithoutSignal() {
        AtomicInteger hornCount = new AtomicInteger();
        state.setOnTimeoutEnd(hornCount::incrementAndGet);
        state.clock().start();
        state.startTeamTimeout(TeamSide.HOME);
        state.clock().start(); // Kampfgericht setzt das Spiel fort
        time.advanceMillis(1_000);
        state.tick();
        assertNull(state.activeTimeoutProperty().get());
        assertEquals(0, hornCount.get());
        assertEquals(GameClock.Phase.RUNNING, state.clock().phaseProperty().get());
    }

    @Test
    void abortGameStopsClockAndTimeoutForGood() {
        state.clock().start();
        time.advanceMillis(10_000);
        state.tick();
        state.startTeamTimeout(TeamSide.HOME);
        state.abortGame();
        assertNull(state.activeTimeoutProperty().get());
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertEquals(10_000, state.clock().elapsedMillisProperty().get());
        // Nach dem Abbruch lässt sich die Uhr nicht wieder starten
        state.clock().start();
        time.advanceMillis(5_000);
        state.tick();
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertEquals(10_000, state.clock().elapsedMillisProperty().get());
    }

    @Test
    void timeoutCanBeEndedEarly() {
        state.clock().start();
        state.startTeamTimeout(TeamSide.HOME);
        state.endTeamTimeout();
        assertNull(state.activeTimeoutProperty().get());
        assertEquals(1, state.timeoutsUsedProperty(TeamSide.HOME).get());
    }

    @Test
    void shootoutGoalsCountTowardsScore() {
        finishRegulation();
        state.startShootout(TeamSide.HOME);
        state.recordShootoutAttempt(true);  // Heim trifft
        state.recordShootoutAttempt(false); // Gast verwirft
        assertEquals(1, state.scoreProperty(TeamSide.HOME).get());
        assertEquals(0, state.scoreProperty(TeamSide.GUEST).get());
    }

    @Test
    void undoShootoutAttemptRemovesGoalFromScore() {
        finishRegulation();
        state.startShootout(TeamSide.HOME);
        state.recordShootoutAttempt(true);
        state.undoShootoutAttempt();
        assertEquals(0, state.scoreProperty(TeamSide.HOME).get());
        assertEquals(0, state.shootoutProperty().get().attempts().size());
    }

    @Test
    void shootoutOnlyAfterRegularFinishAndOnlyOnce() {
        state.clock().start();
        state.startShootout(TeamSide.HOME);
        assertNull(state.shootoutProperty().get());
        // Spielabbruch: Uhr steht vor dem Abschnittsende — kein 7-m-Werfen möglich
        state.abortGame();
        state.startShootout(TeamSide.HOME);
        assertNull(state.shootoutProperty().get());
    }

    @Test
    void shootoutEndFiresSignal() {
        AtomicInteger hornCount = new AtomicInteger();
        state.setOnShootoutEnd(hornCount::incrementAndGet);
        finishRegulation();
        state.startShootout(TeamSide.HOME);
        for (int i = 0; i < 3; i++) {
            state.recordShootoutAttempt(true);  // Heim trifft
            state.recordShootoutAttempt(false); // Gast verwirft
        }
        // 3:0 nach drei Paaren: entschieden, Hupe genau einmal
        assertEquals(TeamSide.HOME, state.shootoutProperty().get().winnerProperty().get());
        assertEquals(1, hornCount.get());
        state.recordShootoutAttempt(true); // wird ignoriert
        assertEquals(1, hornCount.get());
        assertEquals(3, state.scoreProperty(TeamSide.HOME).get());
    }

    /** Spielt beide 1-min-Halbzeiten durch, bis die Uhr regulär auf FINISHED steht. */
    private void finishRegulation() {
        state.clock().start();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(60_000);
        state.tick();
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
    }

    private long penaltyRemaining(TeamSide side) {
        return state.penalties(side).get(0).remainingMillisProperty().get();
    }
}

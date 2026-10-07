package de.kmost.scoreboard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameSnapshotTest {

    private FakeNanoTime time;
    private GameState state;

    @BeforeEach
    void setUp() {
        time = new FakeNanoTime();
        // kurze Perioden (1 min) und kurze Verlängerung (30 s), damit alle Abschnitte handlich bleiben
        GameConfig config = new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                Duration.ofMinutes(1), ClockDirection.UP, OvertimeFormat.TWO_HALVES,
                Duration.ofSeconds(30), SportProfile.HANDBALL);
        state = new GameState(config, time);
    }

    private GameState restored() {
        return GameState.restore(state.snapshot(), time);
    }

    @Test
    void restoreReproducesTheSnapshot() {
        state.clock().start();
        time.advanceMillis(20_000);
        state.tick();
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.GUEST);
        state.addGoal(TeamSide.GUEST);
        state.startTeamTimeout(TeamSide.GUEST);
        state.addPenalty(TeamSide.HOME, "7");

        GameSnapshot snapshot = state.snapshot();
        assertEquals(snapshot, GameState.restore(snapshot, time).snapshot());
    }

    @Test
    void runningClockComesBackPausedAndIgnoresDowntime() {
        state.clock().start();
        time.advanceMillis(25_000);
        state.tick();
        state.addPenalty(TeamSide.GUEST);

        GameState restored = restored();
        time.advanceMillis(600_000); // Rechner war lange aus
        restored.tick();

        assertEquals(GameClock.Phase.PAUSED, restored.clock().phaseProperty().get());
        assertFalse(restored.clock().runningProperty().get());
        assertEquals(25_000, restored.clock().elapsedMillisProperty().get());
        assertEquals(120_000, restored.penalties(TeamSide.GUEST).get(0)
                .remainingMillisProperty().get());

        // Fortsetzen läuft ab der gesicherten Zeit weiter, Strafe mit
        restored.clock().start();
        time.advanceMillis(10_000);
        restored.tick();
        assertEquals(35_000, restored.clock().elapsedMillisProperty().get());
        assertEquals(110_000, restored.penalties(TeamSide.GUEST).get(0)
                .remainingMillisProperty().get());
    }

    @Test
    void scoresAndTimeoutCountersSurvive() {
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.GUEST);
        state.startTeamTimeout(TeamSide.HOME);
        state.endTeamTimeout();
        state.clock().start();
        state.startTeamTimeout(TeamSide.HOME);
        state.endTeamTimeout();

        GameState restored = restored();
        assertEquals(2, restored.scoreProperty(TeamSide.HOME).get());
        assertEquals(1, restored.scoreProperty(TeamSide.GUEST).get());
        assertEquals(2, restored.timeoutsUsedProperty(TeamSide.HOME).get());
        assertEquals(0, restored.timeoutsUsedProperty(TeamSide.GUEST).get());
    }

    @Test
    void activeTimeoutIsNotRestored() {
        state.clock().start();
        state.startTeamTimeout(TeamSide.HOME);
        assertTrue(state.activeTimeoutProperty().get() != null);

        GameState restored = restored();
        assertNull(restored.activeTimeoutProperty().get());
        assertEquals(1, restored.timeoutsUsedProperty(TeamSide.HOME).get());
    }

    @Test
    void penaltiesKeepNumberStartOrderAndExtension() {
        state.clock().start();
        time.advanceMillis(5_000);
        state.tick();
        state.addPenalty(TeamSide.HOME, "7");
        time.advanceMillis(10_000);
        state.tick();
        state.addPenalty(TeamSide.HOME, null);
        state.extendPenalty(state.penalties(TeamSide.HOME).get(0));
        state.addPenalty(TeamSide.GUEST, "13");

        GameState restored = restored();
        List<PenaltyTimer> home = restored.penalties(TeamSide.HOME);
        assertEquals(2, home.size());
        assertEquals("7", home.get(0).playerNumber());
        assertTrue(home.get(0).isExtended());
        assertEquals(230_000, home.get(0).remainingMillisProperty().get()); // 4 min ab Start bei 5 s, jetzt 15 s
        assertNull(home.get(1).playerNumber());
        assertFalse(home.get(1).isExtended());
        assertEquals(120_000, home.get(1).remainingMillisProperty().get());
        assertEquals("13", restored.penalties(TeamSide.GUEST).get(0).playerNumber());

        // die verlängerte Strafe ist nicht ein zweites Mal verlängerbar
        restored.extendPenalty(home.get(0));
        assertEquals(230_000, home.get(0).remainingMillisProperty().get());
    }

    @Test
    void halfTimeIsRestoredAsHalfTime() {
        state.clock().start();
        time.advanceMillis(60_000);
        state.tick();
        state.addPenalty(TeamSide.HOME);

        GameState restored = restored();
        assertEquals(GameClock.Phase.HALF_TIME, restored.clock().phaseProperty().get());
        assertEquals(60_000, restored.clock().elapsedMillisProperty().get());
        restored.clock().startNextPeriod();
        assertEquals(2, restored.clock().periodProperty().get());
        assertEquals(1, restored.penalties(TeamSide.HOME).size());
    }

    @Test
    void overtimePeriodsAreRestored() {
        state.clock().start();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startOvertime();
        time.advanceMillis(10_000);
        state.tick();

        GameState restored = restored();
        GameClock clock = restored.clock();
        assertEquals(3, clock.periodProperty().get());
        assertEquals(130_000, clock.elapsedMillisProperty().get());
        assertEquals(150_000, clock.currentPeriodEndMillis());
        assertEquals(GameClock.Phase.PAUSED, clock.phaseProperty().get());

        // die Verlängerung läuft aus, danach folgt die zweite Hälfte der Verlängerung
        clock.start();
        time.advanceMillis(20_000);
        restored.tick();
        assertEquals(GameClock.Phase.HALF_TIME, clock.phaseProperty().get());
        clock.startNextPeriod();
        assertEquals(4, clock.periodProperty().get());
        time.advanceMillis(30_000);
        restored.tick();
        // 2 Halbzeiten der Verlängerung sind angesetzt: danach ist Schluss
        assertEquals(GameClock.Phase.FINISHED, clock.phaseProperty().get());
    }

    @Test
    void secondOvertimeIsPossibleAfterRestoreOfFirst() {
        state.clock().start();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startOvertime();
        time.advanceMillis(30_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(30_000);
        state.tick();
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());

        GameState restored = restored();
        assertTrue(restored.clock().canStartOvertime());
        restored.clock().startOvertime();
        assertEquals(5, restored.clock().periodProperty().get());
        assertEquals(GameClock.Phase.RUNNING, restored.clock().phaseProperty().get());
    }

    @Test
    void shootoutIsRestoredWithAttemptsAndNextThrower() {
        playToTiedEnd();
        state.startShootout(TeamSide.GUEST);
        state.recordShootoutAttempt(true);   // Gast trifft
        state.recordShootoutAttempt(false);  // Heim vergibt
        state.recordShootoutAttempt(true);   // Gast trifft

        GameState restored = restored();
        Shootout shootout = restored.shootoutProperty().get();
        assertEquals(TeamSide.GUEST, shootout.startingTeam());
        assertEquals(3, shootout.attempts().size());
        assertEquals(2, shootout.goalCount(TeamSide.GUEST));
        assertEquals(TeamSide.HOME, shootout.nextThrowerProperty().get());
        assertNull(shootout.winnerProperty().get());
        assertEquals(2, restored.scoreProperty(TeamSide.GUEST).get());

        // weiterwerfen und Rücknahme funktionieren im wiederhergestellten Spiel
        restored.recordShootoutAttempt(true);
        assertEquals(4, shootout.attempts().size());
        restored.undoShootoutAttempt();
        assertEquals(3, shootout.attempts().size());
        assertEquals(2, restored.scoreProperty(TeamSide.GUEST).get());
        assertEquals(0, restored.scoreProperty(TeamSide.HOME).get());
    }

    @Test
    void finishedShootoutKeepsWinner() {
        playToTiedEnd();
        state.startShootout(TeamSide.HOME);
        for (int i = 0; i < 3; i++) {
            state.recordShootoutAttempt(true);  // Heim
            state.recordShootoutAttempt(false); // Gast
        }
        assertEquals(TeamSide.HOME, state.shootoutProperty().get().winnerProperty().get());

        GameState restored = restored();
        assertEquals(TeamSide.HOME, restored.shootoutProperty().get().winnerProperty().get());
        assertTrue(restored.isOver());
    }

    @Test
    void isOverRules() {
        assertFalse(state.isOver()); // nicht gestartet

        state.clock().start();
        assertFalse(state.isOver());

        // Abbruch: Uhr stoppt vor dem Abschnittsende → vorbei
        state.abortGame();
        assertTrue(state.isOver());
    }

    @Test
    void tiedRegularEndStaysOpenUntilDecided() {
        playToTiedEnd();
        assertFalse(state.isOver()); // Verlängerung / Werfen noch möglich

        state.addGoal(TeamSide.HOME); // Korrektur: jetzt entschieden
        assertTrue(state.isOver());
    }

    @Test
    void regularEndWithWinnerIsOver() {
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(60_000);
        state.tick();
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertTrue(state.isOver());
    }

    @Test
    void shootoutInProgressIsNotOverEvenWithGoalsOnScoreboard() {
        playToTiedEnd();
        state.startShootout(TeamSide.HOME);
        state.recordShootoutAttempt(true);
        assertFalse(state.isOver());
    }

    @Test
    void rejectsInconsistentSnapshots() {
        state.clock().start();
        time.advanceMillis(10_000);
        state.tick();
        GameSnapshot good = state.snapshot();

        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withPeriod(good, 3, 0), time));
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withElapsed(good, 90_000), time)); // liegt in HZ 2, Periode 1
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withElapsed(good, -1), time));
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withSport(good, "Quidditch"), time));
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withScore(good, -1), time));
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withShootout(good, TeamSide.HOME,
                        List.of(new Shootout.Attempt(TeamSide.GUEST, true))), time)); // falsche Reihenfolge
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withShootout(good, null,
                        List.of(new Shootout.Attempt(TeamSide.HOME, true))), time));
        assertThrows(IllegalArgumentException.class, () ->
                GameState.restore(withPenalties(good, List.of(
                        new GameSnapshot.PenaltySnapshot(TeamSide.HOME, null, 0, 0, false))), time));
    }

    private void playToTiedEnd() {
        state.clock().start();
        time.advanceMillis(60_000);
        state.tick();
        state.clock().startNextPeriod();
        time.advanceMillis(60_000);
        state.tick();
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertTrue(state.clock().canStartOvertime());
    }

    private static GameSnapshot withPeriod(GameSnapshot s, int period, int overtimes) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), s.sport(), s.phase(),
                period, overtimes, s.elapsedMillis(), s.homeScore(), s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), s.penalties(),
                s.shootoutStart(), s.shootoutAttempts());
    }

    private static GameSnapshot withElapsed(GameSnapshot s, long elapsed) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), s.sport(), s.phase(),
                s.period(), s.overtimes(), elapsed, s.homeScore(), s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), s.penalties(),
                s.shootoutStart(), s.shootoutAttempts());
    }

    private static GameSnapshot withSport(GameSnapshot s, String sport) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), sport, s.phase(),
                s.period(), s.overtimes(), s.elapsedMillis(), s.homeScore(), s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), s.penalties(),
                s.shootoutStart(), s.shootoutAttempts());
    }

    private static GameSnapshot withScore(GameSnapshot s, int homeScore) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), s.sport(), s.phase(),
                s.period(), s.overtimes(), s.elapsedMillis(), homeScore, s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), s.penalties(),
                s.shootoutStart(), s.shootoutAttempts());
    }

    private static GameSnapshot withShootout(GameSnapshot s, TeamSide start,
                                             List<Shootout.Attempt> attempts) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), s.sport(), s.phase(),
                s.period(), s.overtimes(), s.elapsedMillis(), s.homeScore(), s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), s.penalties(), start, attempts);
    }

    private static GameSnapshot withPenalties(GameSnapshot s,
                                              List<GameSnapshot.PenaltySnapshot> penalties) {
        return new GameSnapshot(s.homeName(), s.guestName(), s.mode(), s.periodMillis(),
                s.direction(), s.overtimeFormat(), s.overtimeMillis(), s.sport(), s.phase(),
                s.period(), s.overtimes(), s.elapsedMillis(), s.homeScore(), s.guestScore(),
                s.homeTimeoutsUsed(), s.guestTimeoutsUsed(), penalties,
                s.shootoutStart(), s.shootoutAttempts());
    }
}

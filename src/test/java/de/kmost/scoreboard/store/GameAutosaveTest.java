package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.OvertimeFormat;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;

class GameAutosaveTest {

    private static final long SECOND = 1_000_000_000L;

    @TempDir
    Path tempDir;

    private AtomicLong nanos;
    private GameState state;
    private GameSnapshotStore store;
    private GameAutosave autosave;

    @BeforeEach
    void setUp() {
        nanos = new AtomicLong();
        GameConfig config = new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                Duration.ofMinutes(1), ClockDirection.UP, OvertimeFormat.TWO_HALVES,
                Duration.ofSeconds(30), SportProfile.HANDBALL);
        state = new GameState(config, nanos::get);
        store = new GameSnapshotStore(tempDir);
        autosave = new GameAutosave(state, store, nanos::get);
    }

    private Path file() {
        return tempDir.resolve("game.properties");
    }

    private void advance(long seconds) {
        nanos.addAndGet(seconds * SECOND);
        state.tick();
        autosave.tick();
    }

    @Test
    void notStartedGameIsNotSaved() {
        autosave.tick();
        autosave.saveNow();
        state.addGoal(TeamSide.HOME);
        assertFalse(Files.exists(file()));
    }

    @Test
    void startingTheClockSavesImmediately() {
        state.clock().start();
        assertTrue(Files.exists(file()));
        assertEquals(GameClock.Phase.RUNNING, GameClock.Phase.valueOf(
                store.load().orElseThrow().phase().name()));
    }

    @Test
    void goalsPenaltiesAndTimeoutsAreSavedImmediately() {
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        assertEquals(1, store.load().orElseThrow().homeScore());

        state.addPenalty(TeamSide.GUEST, "9");
        assertEquals("9", store.load().orElseThrow().penalties().get(0).playerNumber());

        state.removePenalty(state.penalties(TeamSide.GUEST).get(0));
        assertTrue(store.load().orElseThrow().penalties().isEmpty());

        state.startTeamTimeout(TeamSide.GUEST);
        assertEquals(1, store.load().orElseThrow().guestTimeoutsUsed());

        state.removeGoal(TeamSide.HOME);
        assertEquals(0, store.load().orElseThrow().homeScore());
    }

    @Test
    void runningClockIsSavedAtMostOncePerSecond() {
        state.clock().start();
        advance(1);
        assertEquals(1_000, store.load().orElseThrow().elapsedMillis());

        nanos.addAndGet(SECOND / 2);
        state.tick();
        autosave.tick(); // erst 0,5 s seit der letzten Sicherung
        assertEquals(1_000, store.load().orElseThrow().elapsedMillis());

        nanos.addAndGet(SECOND / 2);
        state.tick();
        autosave.tick();
        assertEquals(2_000, store.load().orElseThrow().elapsedMillis());
    }

    @Test
    void unchangedStateIsNotWrittenAgain() throws Exception {
        state.clock().start();
        state.clock().pause();
        autosave.saveNow();
        Files.delete(file());

        advance(5);
        advance(5);
        assertFalse(Files.exists(file()), "unverändertes Spiel darf nicht neu geschrieben werden");
    }

    @Test
    void extensionWhilePausedIsPickedUpByTick() {
        state.clock().start();
        state.addPenalty(TeamSide.HOME);
        state.clock().pause();
        advance(1);
        assertFalse(store.load().orElseThrow().penalties().get(0).extended());

        state.extendPenalty(state.penalties(TeamSide.HOME).get(0));
        advance(1);
        assertTrue(store.load().orElseThrow().penalties().get(0).extended());
    }

    @Test
    void periodChangeAndHalfTimeAreSaved() {
        state.clock().start();
        advance(60);
        assertEquals(GameClock.Phase.HALF_TIME, store.load().orElseThrow().phase());
        state.clock().startNextPeriod();
        assertEquals(2, store.load().orElseThrow().period());
    }

    @Test
    void shootoutProgressIsSaved() {
        state.clock().start();
        advance(60);
        state.clock().startNextPeriod();
        advance(60);
        assertTrue(Files.exists(file()), "Gleichstand: Spiel ist noch offen");

        state.startShootout(TeamSide.HOME);
        assertEquals(TeamSide.HOME, store.load().orElseThrow().shootoutStart());
        state.recordShootoutAttempt(true);
        state.recordShootoutAttempt(false);
        assertEquals(2, store.load().orElseThrow().shootoutAttempts().size());
        state.undoShootoutAttempt();
        assertEquals(1, store.load().orElseThrow().shootoutAttempts().size());
    }

    @Test
    void finishedShootoutDeletesTheSnapshot() {
        state.clock().start();
        advance(60);
        state.clock().startNextPeriod();
        advance(60);
        state.startShootout(TeamSide.HOME);
        for (int i = 0; i < 3; i++) {
            state.recordShootoutAttempt(true);
            state.recordShootoutAttempt(false);
        }
        assertTrue(state.isOver());
        assertFalse(Files.exists(file()));
    }

    @Test
    void abortedGameDeletesTheSnapshot() {
        state.clock().start();
        assertTrue(Files.exists(file()));
        state.abortGame();
        assertFalse(Files.exists(file()));
    }

    @Test
    void decidedRegularEndDeletesTheSnapshot() {
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        advance(60);
        state.clock().startNextPeriod();
        advance(60);
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertFalse(Files.exists(file()));
    }

    @Test
    void tiedRegularEndKeepsTheSnapshotAndOvertimeContinuesIt() {
        state.clock().start();
        advance(60);
        state.clock().startNextPeriod();
        advance(60);
        assertTrue(Files.exists(file()));

        state.clock().startOvertime();
        assertEquals(3, store.load().orElseThrow().period());
        assertEquals(1, store.load().orElseThrow().overtimes());
    }

    @Test
    void disposedAutosaveWritesNothingMore() {
        state.clock().start();
        autosave.dispose();
        state.addGoal(TeamSide.HOME);
        advance(5);
        assertEquals(0, store.load().orElseThrow().homeScore());
    }

    @Test
    void saveNowAfterCrashLikeRestartResumesWhereItStopped() {
        state.clock().start();
        advance(30);
        state.addGoal(TeamSide.GUEST);
        state.addPenalty(TeamSide.HOME, "4");
        autosave.saveNow();

        // „Absturz“: neue Instanz liest die Sicherung
        AtomicLong laterNanos = new AtomicLong(1_000 * SECOND);
        GameState resumed = GameState.restore(
                new GameSnapshotStore(tempDir).load().orElseThrow(), laterNanos::get);
        assertEquals(30_000, resumed.clock().elapsedMillisProperty().get());
        assertEquals(1, resumed.scoreProperty(TeamSide.GUEST).get());
        assertEquals("4", resumed.penalties(TeamSide.HOME).get(0).playerNumber());
        assertEquals(GameClock.Phase.PAUSED, resumed.clock().phaseProperty().get());
    }

    @Test
    void endingTheDrawDeletesTheSnapshot() {
        state.clock().start();
        advance(60);
        state.clock().startNextPeriod();
        advance(60);
        assertTrue(Files.exists(file()));

        state.endGame();

        assertFalse(Files.exists(file()));
        assertTrue(state.isOver());
    }
}

package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameSnapshot;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.OvertimeFormat;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;

class GameSnapshotStoreTest {

    @TempDir
    Path tempDir;

    /** Spiel mit allem, was gesichert wird: Umlaute, Strafen (mit/ohne Nummer), Verlängerung, 7-m-Werfen. */
    private GameSnapshot richSnapshot() {
        AtomicLong nanos = new AtomicLong();
        GameConfig config = new GameConfig("TSV Tarp – Größe", "SG Süd/Ost", GameMode.TWO_HALVES,
                Duration.ofMinutes(1), ClockDirection.DOWN, OvertimeFormat.SINGLE_PERIOD,
                Duration.ofSeconds(30), SportProfile.HANDBALL);
        GameState state = new GameState(config, nanos::get);
        state.clock().start();
        nanos.addAndGet(60_000_000_000L);
        state.tick();
        state.clock().startNextPeriod();
        nanos.addAndGet(60_000_000_000L);
        state.tick();
        state.addPenalty(TeamSide.HOME, "7");
        state.addPenalty(TeamSide.GUEST, null);
        state.extendPenalty(state.penalties(TeamSide.HOME).get(0));
        state.startShootout(TeamSide.GUEST);
        state.recordShootoutAttempt(true);
        state.recordShootoutAttempt(false);
        state.startTeamTimeout(TeamSide.HOME);
        return state.snapshot();
    }

    @Test
    void roundTripKeepsEverything() {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        GameSnapshot snapshot = richSnapshot();
        store.save(snapshot);

        Optional<GameSnapshot> loaded = new GameSnapshotStore(tempDir).load();
        assertEquals(Optional.of(snapshot), loaded);
        GameSnapshot s = loaded.orElseThrow();
        assertEquals("TSV Tarp – Größe", s.homeName());
        assertEquals("7", s.penalties().get(0).playerNumber());
        assertTrue(s.penalties().get(0).extended());
        assertNull(s.penalties().get(1).playerNumber());
        assertEquals(TeamSide.GUEST, s.shootoutStart());
        assertEquals(2, s.shootoutAttempts().size());
    }

    @Test
    void roundTripWithoutPenaltiesAndShootout() {
        GameState state = new GameState(new GameConfig("A", "B", GameMode.THREE_THIRDS,
                Duration.ofMinutes(20), ClockDirection.UP, SportProfile.HANDBALL));
        state.clock().start();
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(state.snapshot());
        GameSnapshot loaded = store.load().orElseThrow();
        assertEquals(state.snapshot(), loaded);
        assertNull(loaded.shootoutStart());
        assertTrue(loaded.penalties().isEmpty());
    }

    @Test
    void loadedSnapshotRestoresAPlayableGame() {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(richSnapshot());
        AtomicLong nanos = new AtomicLong();
        GameState restored = GameState.restore(store.load().orElseThrow(), nanos::get);
        assertEquals(GameClock.Phase.FINISHED, restored.clock().phaseProperty().get());
        assertEquals(2, restored.shootoutProperty().get().attempts().size());
        assertEquals(1, restored.penalties(TeamSide.HOME).size());
    }

    @Test
    void missingFileLoadsEmpty() {
        assertTrue(new GameSnapshotStore(tempDir).load().isEmpty());
        assertTrue(new GameSnapshotStore(tempDir.resolve("gibt-es-nicht")).load().isEmpty());
    }

    @Test
    void saveReplacesOldAndLeavesNoTempFile() throws IOException {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        GameSnapshot first = richSnapshot();
        store.save(first);
        GameState state = GameState.restore(first, new AtomicLong()::get);
        state.addGoal(TeamSide.HOME);
        store.save(state.snapshot());

        assertEquals(1, store.load().orElseThrow().homeScore());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of("game.properties"),
                    files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    void createsMissingDirectoryOnSave() {
        Path nested = tempDir.resolve("a").resolve("b");
        new GameSnapshotStore(nested).save(richSnapshot());
        assertTrue(new GameSnapshotStore(nested).load().isPresent());
    }

    @Test
    void deleteRemovesTheSnapshot() {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(richSnapshot());
        store.delete();
        assertTrue(store.load().isEmpty());
        store.delete(); // ohne Datei: kein Fehler
    }

    @Test
    void garbageFileLoadsEmptyAndIsSetAside() throws IOException {
        Files.writeString(tempDir.resolve("game.properties"), "\u0000\u0001 kein properties-inhalt \\uZZZZ",
                StandardCharsets.UTF_8);
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        assertTrue(store.load().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("game.properties")));
        assertTrue(Files.exists(tempDir.resolve("game.properties.defekt")));
        assertTrue(store.load().isEmpty()); // zweiter Start: nichts mehr da
    }

    @Test
    void truncatedFileLoadsEmpty() throws IOException {
        new GameSnapshotStore(tempDir).save(richSnapshot());
        Path file = tempDir.resolve("game.properties");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // Datei mittendrin abschneiden: wichtige Schlüssel fehlen
        Files.writeString(file, text.substring(0, text.length() / 3), StandardCharsets.UTF_8);
        assertTrue(new GameSnapshotStore(tempDir).load().isEmpty());
    }

    @Test
    void emptyFileLoadsEmpty() throws IOException {
        Files.writeString(tempDir.resolve("game.properties"), "", StandardCharsets.UTF_8);
        assertTrue(new GameSnapshotStore(tempDir).load().isEmpty());
    }

    @Test
    void unknownSchemaInvalidEnumAndBadNumberLoadEmpty() throws IOException {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(richSnapshot());
        Path file = tempDir.resolve("game.properties");
        String good = Files.readString(file, StandardCharsets.UTF_8);

        for (String[] change : new String[][] {
                {"schema=1", "schema=99"},
                {"phase=FINISHED", "phase=EXPLODIERT"},
                {"elapsed=120000", "elapsed=zwei minuten"},
                {"penalty.count=2", "penalty.count=5"}}) {
            assertTrue(good.contains(change[0]), "Testdaten ändern sich: " + change[0]);
            Files.writeString(file, good.replace(change[0], change[1]), StandardCharsets.UTF_8);
            assertTrue(new GameSnapshotStore(tempDir).load().isEmpty(), change[1]);
        }
    }

    @Test
    void quarantineSetsFileAside() {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(richSnapshot());
        store.quarantine();
        assertTrue(store.load().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("game.properties.defekt")));
        store.quarantine(); // ohne Datei: kein Fehler
    }

    private GameSnapshot endedDrawSnapshot() {
        AtomicLong nanos = new AtomicLong();
        GameConfig config = new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                Duration.ofMinutes(1), ClockDirection.UP, OvertimeFormat.TWO_HALVES,
                Duration.ofSeconds(30), SportProfile.HANDBALL);
        GameState state = new GameState(config, nanos::get);
        state.clock().start();
        nanos.addAndGet(60_000_000_000L);
        state.tick();
        state.clock().startNextPeriod();
        nanos.addAndGet(60_000_000_000L);
        state.tick();
        state.endGame();
        return state.snapshot();
    }

    @Test
    void endedFlagSurvivesTheRoundTrip() {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        GameSnapshot ended = endedDrawSnapshot();
        assertTrue(ended.ended());
        store.save(ended);
        assertEquals(ended, store.load().orElseThrow());
    }

    @Test
    void oldFilesWithoutEndedFlagStillLoadAsNotEnded() throws IOException {
        GameSnapshotStore store = new GameSnapshotStore(tempDir);
        store.save(richSnapshot());
        Path file = tempDir.resolve("game.properties");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(content.contains("\nended="));
        assertFalse(store.load().orElseThrow().ended());
    }
}

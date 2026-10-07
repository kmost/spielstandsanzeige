package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameSnapshot;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.ui.Theme;

/** Speicher- und Ladefehler der Stores landen bei der Meldestelle statt auf System.err. */
class StoreProblemReportingTest {

    @TempDir
    Path tempDir;

    private final List<String> problems = new ArrayList<>();
    private ProblemReporter reporter;
    private Path blockedDir; // „Verzeichnis“, das in Wahrheit eine Datei ist: Schreiben schlägt fehl

    @BeforeEach
    void setUp() throws IOException {
        reporter = new ProblemReporter(null);
        reporter.addListener(problems::add);
        blockedDir = Files.writeString(tempDir.resolve("blockiert"), "x").resolve("daten");
    }

    private static GameSnapshot snapshot() {
        GameState state = new GameState(new GameConfig("Heim", "Gast", GameMode.TWO_HALVES,
                java.time.Duration.ofMinutes(30), ClockDirection.UP, SportProfile.HANDBALL));
        state.clock().start();
        return state.snapshot();
    }

    @Test
    void teamRepositoryReportsWriteFailure() {
        new TeamRepository(blockedDir, reporter).saveTeam("TSV Tarp");
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("TSV Tarp"), problems.get(0));
    }

    @Test
    void teamRepositoryReportsDefaultHomeWriteFailure() {
        new TeamRepository(blockedDir, reporter).saveDefaultHomeTeam("TSV Tarp");
        assertEquals(1, problems.size());
    }

    @Test
    void teamRepositoryReportsUnreadableDatabase() throws IOException {
        // teams.properties ist ein Verzeichnis: Lesen schlägt mit IOException fehl
        Files.createDirectories(tempDir.resolve("teams.properties"));
        new TeamRepository(tempDir, reporter);
        // ein Verzeichnis gilt als Datei, die existiert, aber nicht lesbar ist
        assertEquals(1, problems.size());
    }

    @Test
    void themeRepositoryReportsWriteFailure() {
        new ThemeRepository(blockedDir, reporter).saveTheme("Dunkel", Theme.defaults());
        assertEquals(1, problems.size());
    }

    @Test
    void themeRepositoryReportsBannerImageFailure() throws IOException {
        File source = Files.writeString(tempDir.resolve("logo.png"), "png").toFile();
        File stored = new ThemeRepository(blockedDir, reporter).storeBannerImage("header-1", source);
        assertEquals(null, stored);
        assertEquals(1, problems.size());
    }

    @Test
    void themeRepositoryOnlyLogsBrokenValues() throws IOException {
        Files.writeString(tempDir.resolve("display.properties"), "clock=keineFarbe\nfontScale=abc\n");
        ThemeRepository repository = new ThemeRepository(tempDir, reporter);
        repository.currentTheme();
        assertTrue(problems.isEmpty(), "ungültige Einzelwerte stören den Nutzer nicht: " + problems);
    }

    @Test
    void snapshotStoreReportsWriteFailure() {
        new GameSnapshotStore(blockedDir, reporter).save(snapshot());
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Spielstand"), problems.get(0));
    }

    @Test
    void snapshotStoreReportsBrokenFile() throws IOException {
        Files.writeString(tempDir.resolve("game.properties"), "kaputt");
        assertTrue(new GameSnapshotStore(tempDir, reporter).load().isEmpty());
        assertEquals(1, problems.size());
        assertTrue(Files.exists(tempDir.resolve("game.properties.defekt")));
    }

    @Test
    void healthyOperationReportsNothing() {
        new TeamRepository(tempDir, reporter).saveTeam("TSV Tarp");
        new GameSnapshotStore(tempDir, reporter).save(snapshot());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void defaultConstructorsWriteLogNextToTheirData() throws IOException {
        // ohne eigene Meldestelle (so nutzen es die Tests) landet das Log im Datenverzeichnis, nicht im Home
        Path dir = tempDir.resolve("ok");
        Files.createDirectories(dir.resolve("teams.properties")); // nicht lesbar -> Meldung
        new TeamRepository(dir);
        assertTrue(Files.isRegularFile(dir.resolve("spielstandsanzeige.log")));
    }
}

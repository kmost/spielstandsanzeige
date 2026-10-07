package de.kmost.scoreboard.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemReporterTest {

    @TempDir
    Path tempDir;

    @Test
    void reportNotifiesListenersWithUserMessage() {
        ProblemReporter reporter = new ProblemReporter(null);
        List<String> received = new ArrayList<>();
        reporter.addListener(received::add);
        reporter.report("Teams konnten nicht gespeichert werden", new IOException("Platte voll"));
        assertEquals(List.of("Teams konnten nicht gespeichert werden"), received);
    }

    @Test
    void logOnlyDoesNotNotifyListeners() {
        ProblemReporter reporter = new ProblemReporter(null);
        List<String> received = new ArrayList<>();
        reporter.addListener(received::add);
        reporter.log("Ungültiger Wert", null);
        assertTrue(received.isEmpty());
    }

    @Test
    void removedListenerGetsNothing() {
        ProblemReporter reporter = new ProblemReporter(null);
        List<String> received = new ArrayList<>();
        java.util.function.Consumer<String> listener = received::add;
        reporter.addListener(listener);
        reporter.removeListener(listener);
        reporter.report("egal", null);
        assertTrue(received.isEmpty());
    }

    @Test
    void worksWithoutListenersAndWithoutLogFile() {
        new ProblemReporter(null).report("Meldung ohne Abonnent", new IOException("x"));
    }

    @Test
    void failingListenerDoesNotBreakOthersOrCaller() {
        ProblemReporter reporter = new ProblemReporter(null);
        List<String> received = new ArrayList<>();
        reporter.addListener(m -> {
            throw new IllegalStateException("kaputt");
        });
        reporter.addListener(received::add);
        reporter.report("Meldung", null);
        assertEquals(List.of("Meldung"), received);
    }

    @Test
    void writesMessageAndCauseToLogFile() throws IOException {
        Path log = tempDir.resolve("unterordner").resolve("test.log");
        new ProblemReporter(log).report("Team „Süd“ konnte nicht gespeichert werden",
                new IOException("Platte voll"));
        String content = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(content.contains("Team „Süd“ konnte nicht gespeichert werden (Platte voll)"), content);
    }

    @Test
    void appendsToExistingLog() throws IOException {
        Path log = tempDir.resolve("test.log");
        ProblemReporter reporter = new ProblemReporter(log);
        reporter.report("erste", null);
        reporter.log("zweite", null);
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("erste"));
        assertTrue(lines.get(1).endsWith("zweite"));
    }

    @Test
    void rotatesLogWhenLimitIsReached() throws IOException {
        Path log = tempDir.resolve("test.log");
        ProblemReporter reporter = new ProblemReporter(log, 200);
        for (int i = 0; i < 20; i++) {
            reporter.log("Eintrag Nummer " + i + " mit etwas Text zum Auffüllen", null);
        }
        Path rotated = tempDir.resolve("test.log.1");
        assertTrue(Files.isRegularFile(rotated), "rotierte Datei fehlt");
        assertTrue(Files.size(log) < 400, "aktuelle Datei bleibt klein: " + Files.size(log));
        assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("Nummer 19"));
        // es bleibt bei genau einer Rotationsdatei
        try (var files = Files.list(tempDir)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void unwritableLogFileNeverThrows() throws IOException {
        // Elternpfad ist eine Datei, das Verzeichnis lässt sich nicht anlegen
        Path blocker = Files.writeString(tempDir.resolve("blocker"), "x");
        ProblemReporter reporter = new ProblemReporter(blocker.resolve("test.log"));
        List<String> received = new ArrayList<>();
        reporter.addListener(received::add);
        reporter.report("Meldung", null);
        assertFalse(received.isEmpty());
    }
}

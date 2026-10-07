package de.kmost.scoreboard.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Zentrale Meldestelle für Probleme, die den Spielbetrieb nicht stoppen sollen
 * (Speichern/Laden schlägt fehl, Hupe nicht verfügbar). Jede Meldung geht in eine
 * Logdatei (~/.spielstandsanzeige/spielstandsanzeige.log, bei Überschreiten der
 * Größengrenze auf .log.1 rotiert) und an {@link System.Logger}; die UI abonniert
 * zusätzlich die Kurzmeldungen für ihre Statuszeile. UI-frei und ohne Abonnenten
 * voll funktionsfähig. Meldungen können von jedem Thread kommen — Abonnenten müssen
 * selbst auf ihren Thread wechseln.
 */
public class ProblemReporter {

    static final long MAX_LOG_BYTES = 256 * 1024;
    private static final String LOG_FILE = "spielstandsanzeige.log";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final System.Logger LOGGER = System.getLogger("de.kmost.scoreboard");
    private static final ProblemReporter DEFAULT = new ProblemReporter(
            Path.of(System.getProperty("user.home"), ".spielstandsanzeige", LOG_FILE));

    private final Path logFile; // null = keine Logdatei
    private final long maxLogBytes;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    /** Gemeinsame Instanz für die ganze App, loggt nach ~/.spielstandsanzeige/. */
    public static ProblemReporter shared() {
        return DEFAULT;
    }

    /** Meldestelle mit eigener Logdatei; {@code null} = nur System.Logger, keine Datei. */
    public ProblemReporter(Path logFile) {
        this(logFile, MAX_LOG_BYTES);
    }

    ProblemReporter(Path logFile, long maxLogBytes) {
        this.logFile = logFile;
        this.maxLogBytes = maxLogBytes;
    }

    /** Abonniert die Kurzmeldungen für den Nutzer; der Aufruf kann aus jedem Thread kommen. */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /**
     * Meldet ein Problem: {@code userMessage} ist die kurze Meldung für den Nutzer
     * (steht auch im Log), {@code cause} die technische Ursache (darf null sein).
     */
    public void report(String userMessage, Throwable cause) {
        log(userMessage, cause);
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(userMessage);
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Abonnent der Problemmeldungen fehlgeschlagen", e);
            }
        }
    }

    /** Nur ins Log, ohne den Nutzer zu stören (z. B. unlesbarer Einzelwert mit Ersatzwert). */
    public void log(String message, Throwable cause) {
        String line = cause == null || cause.getMessage() == null
                ? message : message + " (" + cause.getMessage() + ")";
        if (cause == null) {
            LOGGER.log(System.Logger.Level.WARNING, message);
        } else {
            LOGGER.log(System.Logger.Level.WARNING, message, cause);
        }
        append(line);
    }

    private synchronized void append(String line) {
        if (logFile == null) {
            return;
        }
        try {
            Files.createDirectories(logFile.getParent());
            rotateIfNeeded();
            Files.writeString(logFile, LocalDateTime.now().format(TIMESTAMP) + "  " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            // das Log darf nie selbst zum Problem werden
            LOGGER.log(System.Logger.Level.WARNING, "Logdatei nicht beschreibbar: " + e.getMessage());
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.isRegularFile(logFile) && Files.size(logFile) >= maxLogBytes) {
            Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".1"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

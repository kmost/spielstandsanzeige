package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

/** Die Hupen-Auswahl (horn.properties): eingebauter Ton und optional eine eigene Audiodatei. */
public final class HornSettingsStore {

    private static final String FILE = "horn.properties";
    /** Schema 1 = heutiges Format; Dateien ohne Eintrag (Version 0) haben dasselbe Format. */
    private static final int SCHEMA = 1;

    /** Gespeicherte Auswahl; {@code tone} leer = nichts gespeichert, {@code file} null = keine eigene Datei. */
    public record HornSettings(String tone, File file) {
    }

    private final Path baseDir;
    private final ProblemReporter reporter;

    public HornSettingsStore(Path baseDir, ProblemReporter reporter) {
        this.baseDir = baseDir;
        this.reporter = reporter;
    }

    /**
     * Merkt die Auswahl: eingebauter Ton (Name) und optional eine externe
     * Audiodatei — ist eine Datei gesetzt und ladbar, hat sie Vorrang.
     */
    public void save(String tone, File file) {
        Properties props = new Properties();
        SchemaVersion.stamp(props, SCHEMA);
        props.setProperty("tone", tone == null ? "" : tone);
        props.setProperty("file", file == null ? "" : file.getAbsolutePath());
        try {
            PropertiesFiles.store(baseDir.resolve(FILE), props, "Hupen-Auswahl der Spielstandsanzeige");
        } catch (IOException e) {
            reporter.report("Datei „" + FILE + "“ konnte nicht gespeichert werden", e);
        }
    }

    /** Gespeicherte Auswahl, mit einem Lesezugriff; ohne Datei leer. */
    public HornSettings load() {
        Path file = baseDir.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return new HornSettings("", null);
        }
        try {
            Properties props = PropertiesFiles.load(file);
            int schema = SchemaVersion.of(props);
            if (schema > SCHEMA) {
                SchemaVersion.setAsideNewer(file, schema, reporter);
                return new HornSettings("", null);
            }
            String path = props.getProperty("file", "");
            return new HornSettings(props.getProperty("tone", ""), path.isBlank() ? null : new File(path));
        } catch (IOException e) {
            reporter.report("Datei „" + FILE + "“ nicht lesbar", e);
            return new HornSettings("", null);
        }
    }
}

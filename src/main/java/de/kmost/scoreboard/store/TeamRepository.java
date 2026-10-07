package de.kmost.scoreboard.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

/**
 * Persistente Team-Datenbank: merkt sich alle genutzten Teamnamen.
 * Ablage unter ~/.spielstandsanzeige/teams.properties, damit die Daten
 * Releases und Neustarts überleben. Fehler beim Lesen/Schreiben werden
 * gemeldet, blockieren aber nie den Spielbetrieb.
 */
public class TeamRepository {

    private static final String PROPERTIES_FILE = "teams.properties";
    // interner Schlüssel, kein Teamname — „_“-Präfix wie beim Theme-Namen
    private static final String DEFAULT_HOME_KEY = "_defaultHome";

    private final Path baseDir;
    private final ProblemReporter reporter;
    // Werte alter Dateien (früher Logo-Dateinamen) werden ignoriert, aber erhalten
    private final Properties teams = new Properties();

    public TeamRepository() {
        this(Path.of(System.getProperty("user.home"), ".spielstandsanzeige"), ProblemReporter.shared());
    }

    public TeamRepository(Path baseDir) {
        this(baseDir, new ProblemReporter(baseDir.resolve("spielstandsanzeige.log")));
    }

    public TeamRepository(Path baseDir, ProblemReporter reporter) {
        this.baseDir = baseDir;
        this.reporter = reporter;
        load();
    }

    /** Alle gespeicherten Teamnamen, alphabetisch sortiert. */
    public List<String> teamNames() {
        return teams.stringPropertyNames().stream()
                .filter(name -> !name.startsWith("_"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Vorbelegtes Heimteam fürs Spiel-Setup; leer, wenn keines konfiguriert ist. */
    public String defaultHomeTeam() {
        return teams.getProperty(DEFAULT_HOME_KEY, "").strip();
    }

    /** Merkt das vorbelegte Heimteam; leer entfernt die Vorbelegung. */
    public void saveDefaultHomeTeam(String teamName) {
        String name = teamName == null ? "" : teamName.strip();
        if (name.equals(defaultHomeTeam())) {
            return;
        }
        teams.setProperty(DEFAULT_HOME_KEY, name);
        try {
            store();
        } catch (IOException e) {
            reporter.report("Standard-Heimteam konnte nicht gespeichert werden", e);
        }
    }

    /** Speichert das Team; leere Namen werden ignoriert. */
    public void saveTeam(String teamName) {
        String name = teamName == null ? "" : teamName.strip();
        if (name.isEmpty() || teams.containsKey(name)) {
            return;
        }
        teams.setProperty(name, "");
        try {
            store();
        } catch (IOException e) {
            reporter.report("Team „" + name + "“ konnte nicht gespeichert werden", e);
        }
    }

    private void load() {
        Path file = baseDir.resolve(PROPERTIES_FILE);
        if (!Files.exists(file)) {
            return;
        }
        try {
            teams.putAll(PropertiesFiles.load(file));
        } catch (IOException e) {
            reporter.report("Team-Datenbank nicht lesbar", e);
        }
    }

    private void store() throws IOException {
        PropertiesFiles.store(baseDir.resolve(PROPERTIES_FILE), teams, "Teams der Spielstandsanzeige");
    }
}

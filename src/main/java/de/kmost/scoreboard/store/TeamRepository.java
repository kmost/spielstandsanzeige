package de.kmost.scoreboard.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

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
    // Werte alter Dateien (früher Logo-Dateinamen) werden ignoriert, aber erhalten
    private final Properties teams = new Properties();

    public TeamRepository() {
        this(Path.of(System.getProperty("user.home"), ".spielstandsanzeige"));
    }

    public TeamRepository(Path baseDir) {
        this.baseDir = baseDir;
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
            System.err.println("Standard-Heimteam konnte nicht gespeichert werden: " + e.getMessage());
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
            System.err.println("Team „" + name + "“ konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        Path file = baseDir.resolve(PROPERTIES_FILE);
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            teams.load(reader);
        } catch (IOException e) {
            System.err.println("Team-Datenbank nicht lesbar: " + e.getMessage());
        }
    }

    private void store() throws IOException {
        Files.createDirectories(baseDir);
        try (Writer writer = Files.newBufferedWriter(
                baseDir.resolve(PROPERTIES_FILE), StandardCharsets.UTF_8)) {
            teams.store(writer, "Teams der Spielstandsanzeige");
        }
    }
}

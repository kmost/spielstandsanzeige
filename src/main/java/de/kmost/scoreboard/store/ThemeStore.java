package de.kmost.scoreboard.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.ui.Theme;

/**
 * Benannte Farb-Themes unter ~/.spielstandsanzeige/themes/ (eine Datei je Theme). Fehler
 * beim Lesen/Schreiben werden gemeldet, blockieren aber nie den Spielbetrieb.
 */
public final class ThemeStore {

    private static final String THEME_DIR = "themes";
    private static final String NAME_KEY = "_name";
    private static final int MAX_FILE_NAME_LENGTH = 80;
    private static final Pattern RESERVED_WINDOWS_NAME =
            Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");

    private final Path baseDir;
    private final ProblemReporter reporter;

    public ThemeStore(Path baseDir, ProblemReporter reporter) {
        this.baseDir = baseDir;
        this.reporter = reporter;
    }

    /** Ergebnis von {@link #saveTheme}: gespeichert, Dateiname-Konflikt oder Schreibfehler. */
    public record SaveResult(Status status, String conflictingName) {

        public enum Status { SAVED, NAME_CONFLICT, WRITE_FAILED }

        public boolean saved() {
            return status == Status.SAVED;
        }
    }

    /** Alle gespeicherten Theme-Namen, alphabetisch sortiert. */
    public List<String> themeNames() {
        Path dir = baseDir.resolve(THEME_DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".properties"))
                    .map(this::themeName)
                    .filter(Objects::nonNull)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            reporter.report("Themes nicht lesbar", e);
            return List.of();
        }
    }

    /** Gespeichertes Theme; null, wenn es nicht existiert oder nicht lesbar ist. */
    public Theme loadTheme(String name) {
        Path file = existingThemeFile(name);
        Properties props = file == null ? null : read(file);
        return props == null ? null : ThemeProperties.fromProperties(props, reporter);
    }

    /**
     * Speichert ein Theme unter seinem Anzeigenamen. Ein Theme mit gleichem Anzeigenamen wird
     * überschrieben (auch wenn es noch unter einem älteren Dateinamen liegt). Gehört der
     * abgeleitete Dateiname dagegen schon zu einem Theme mit anderem Namen (z. B. „A/B“ und
     * „A_B“), wird nichts geschrieben und der Konflikt gemeldet.
     */
    public SaveResult saveTheme(String name, Theme theme) {
        String wanted = name.strip();
        Path existing = existingThemeFile(wanted);
        Path target = existing != null ? existing : themeFile(wanted);
        if (existing == null && Files.exists(target)) {
            String other = themeName(target);
            return new SaveResult(SaveResult.Status.NAME_CONFLICT,
                    other != null ? other : target.getFileName().toString());
        }
        Properties props = ThemeProperties.toProperties(theme);
        props.setProperty(NAME_KEY, wanted);
        try {
            PropertiesFiles.store(target, props, "Farb-Theme der Spielstandsanzeige");
            return new SaveResult(SaveResult.Status.SAVED, null);
        } catch (IOException e) {
            reporter.report("Datei „" + target.getFileName() + "“ konnte nicht gespeichert werden", e);
            return new SaveResult(SaveResult.Status.WRITE_FAILED, null);
        }
    }

    public void deleteTheme(String name) {
        Path file = existingThemeFile(name);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            reporter.report("Theme „" + name + "“ konnte nicht gelöscht werden", e);
        }
    }

    /** Dateipfad, unter dem ein neues Theme dieses Namens abgelegt wird. */
    private Path themeFile(String name) {
        return baseDir.resolve(THEME_DIR).resolve(sanitize(name.strip()) + ".properties");
    }

    /**
     * Datei des Themes mit genau diesem Anzeigenamen; null, wenn es keines gibt. Gesucht wird
     * zuerst unter dem aktuellen Dateinamen, dann über den Anzeigenamen in allen Dateien — so
     * werden auch Themes gefunden, die eine ältere Version unter anderem Dateinamen angelegt hat
     * (früher wurden z. B. Umlaute im Dateinamen ersetzt).
     */
    private Path existingThemeFile(String name) {
        String wanted = name.strip();
        Path direct = themeFile(wanted);
        if (Files.isRegularFile(direct) && wanted.equals(themeName(direct))) {
            return direct;
        }
        Path dir = baseDir.resolve(THEME_DIR);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".properties"))
                    .filter(file -> wanted.equals(themeName(file)))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            reporter.report("Themes nicht lesbar", e);
            return null;
        }
    }

    /** Anzeigename eines Themes; steht in der Datei, damit Umlaute etc. erhalten bleiben. */
    private String themeName(Path file) {
        Properties props = read(file);
        if (props == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        return props.getProperty(NAME_KEY, fileName.substring(0, fileName.length() - ".properties".length()));
    }

    private Properties read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return PropertiesFiles.load(file);
        } catch (IOException e) {
            reporter.report("Datei „" + file.getFileName() + "“ nicht lesbar", e);
            return null;
        }
    }

    /**
     * Dateiname aus dem Theme-Namen: Buchstaben und Ziffern (auch Umlaute) bleiben erhalten,
     * alles andere wird zu „_“. Unter Windows reservierte Gerätenamen (CON, NUL, …) bekommen
     * ein „_“ vorangestellt. Zwei verschiedene Namen können denselben Dateinamen ergeben
     * („A/B“ und „A_B“); das fängt {@link #saveTheme} als Konflikt ab.
     */
    static String sanitize(String name) {
        String cleaned = Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N}\\-_]", "_");
        if (cleaned.length() > MAX_FILE_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_FILE_NAME_LENGTH);
        }
        if (cleaned.isEmpty() || RESERVED_WINDOWS_NAME.matcher(cleaned).matches()) {
            cleaned = "_" + cleaned;
        }
        return cleaned;
    }
}

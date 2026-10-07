package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.scene.paint.Color;

class ThemeStoreTest {

    @TempDir
    Path tempDir;

    private Path storeDir() {
        return tempDir.resolve("store");
    }

    private ThemeStore store() {
        return new ThemeStore(storeDir(), new ProblemReporter(tempDir.resolve("test.log")));
    }

    private Path themeFile(String fileName) {
        return storeDir().resolve("themes").resolve(fileName);
    }

    @Test
    void savedThemeSurvivesReload() {
        ThemeStore repository = store();
        Theme theme = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#123456"));
        repository.saveTheme("Dunkel", theme);

        Theme reloaded = store().loadTheme("Dunkel");
        assertNotNull(reloaded);
        assertEquals(theme, reloaded);
    }

    @Test
    void fontFamilySurvivesReload() {
        ThemeStore repository = store();
        repository.saveTheme("Serifen", Theme.defaults().withFont("Georgia"));

        Theme reloaded = store().loadTheme("Serifen");
        assertNotNull(reloaded);
        assertEquals("Georgia", reloaded.fontFamily());
        // Dateien ohne font-Eintrag (alte Themes) ergeben die Systemschrift
        assertEquals("", Theme.defaults().fontFamily());
    }

    @Test
    void fontScalesSurviveReload() {
        ThemeStore repository = store();
        repository.saveTheme("Groß", Theme.defaults()
                .with(FontScale.HEADER, 1.5)
                .with(FontScale.CLOCK, 2.0)
                .with(FontScale.FOOTER, 0.5));

        Theme reloaded = store().loadTheme("Groß");
        assertNotNull(reloaded);
        assertEquals(1.5, reloaded.scale(FontScale.HEADER));
        assertEquals(2.0, reloaded.scale(FontScale.CLOCK));
        assertEquals(0.5, reloaded.scale(FontScale.FOOTER));
        assertEquals(1.0, reloaded.scale(FontScale.SCORE));
        // Dateien ohne Einträge (alte Themes) ergeben die Standardgröße
        assertEquals(1.0, Theme.defaults().scale(FontScale.TEAM_NAME));
    }

    @Test
    void namesAreSortedAndUmlautsSurvive() {
        ThemeStore repository = store();
        repository.saveTheme("Rot/Weiß", Theme.defaults());
        repository.saveTheme("Grün", Theme.defaults());

        assertEquals(List.of("Grün", "Rot/Weiß"), store().themeNames());
        assertNotNull(store().loadTheme("Rot/Weiß"));
    }

    @Test
    void unknownThemeReturnsNull() {
        assertNull(store().loadTheme("gibt es nicht"));
    }

    @Test
    void deletedThemeIsGone() {
        ThemeStore repository = store();
        repository.saveTheme("Weg damit", Theme.defaults());
        repository.deleteTheme("Weg damit");

        assertTrue(repository.themeNames().isEmpty());
        assertNull(repository.loadTheme("Weg damit"));
    }

    @Test
    void umlautsStayInFileName() {
        ThemeStore repository = store();
        assertTrue(repository.saveTheme("Größe", Theme.defaults()).saved());
        assertTrue(Files.exists(themeFile("Größe.properties")));
        assertNotNull(repository.loadTheme("Größe"));
    }

    @Test
    void sameDerivedFileNameForDifferentNamesIsReportedAsConflict() {
        ThemeStore repository = store();
        Theme first = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#111111"));
        assertTrue(repository.saveTheme("A/B", first).saved());

        ThemeStore.SaveResult result = repository.saveTheme("A_B",
                Theme.defaults().with(ThemeColor.CLOCK, Color.web("#222222")));

        assertEquals(ThemeStore.SaveResult.Status.NAME_CONFLICT, result.status());
        assertEquals("A/B", result.conflictingName());
        // nichts wurde überschrieben, es gibt weiter genau ein Theme
        assertEquals(first, repository.loadTheme("A/B"));
        assertNull(repository.loadTheme("A_B"));
        assertEquals(List.of("A/B"), repository.themeNames());
    }

    @Test
    void savingUnderTheSameNameOverwrites() {
        ThemeStore repository = store();
        repository.saveTheme("Dunkel", Theme.defaults());
        Theme changed = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#abcdef"));

        assertTrue(repository.saveTheme("Dunkel", changed).saved());

        assertEquals(changed, repository.loadTheme("Dunkel"));
        assertEquals(List.of("Dunkel"), repository.themeNames());
    }

    @Test
    void themeFromOlderVersionWithReplacedUmlautsIsStillFoundAndOverwritten() throws IOException {
        // frühere Versionen ersetzten Umlaute im Dateinamen: „Größe“ lag als Gr__e.properties
        ThemeStore old = store();
        old.saveTheme("Größe", Theme.defaults().with(ThemeColor.CLOCK, Color.web("#111111")));
        Files.move(themeFile("Größe.properties"), themeFile("Gr__e.properties"));

        ThemeStore repository = store();
        assertNotNull(repository.loadTheme("Größe"));

        Theme changed = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#abcdef"));
        assertTrue(repository.saveTheme("Größe", changed).saved());
        assertEquals(changed, repository.loadTheme("Größe"));
        assertTrue(Files.exists(themeFile("Gr__e.properties")));
        assertTrue(Files.notExists(themeFile("Größe.properties")));
        assertEquals(List.of("Größe"), repository.themeNames());

        repository.deleteTheme("Größe");
        assertTrue(repository.themeNames().isEmpty());
        assertTrue(Files.notExists(themeFile("Gr__e.properties")));
    }

    @Test
    void themeFileWithoutStoredNameIsFoundByItsFileName() throws IOException {
        ThemeStore repository = store();
        repository.saveTheme("Alt", Theme.defaults());
        String content = Files.readString(themeFile("Alt.properties"))
                .replaceFirst("(?m)^_name=.*\\R", "");
        Files.writeString(themeFile("Alt.properties"), content);

        assertNotNull(repository.loadTheme("Alt"));
        assertEquals(List.of("Alt"), repository.themeNames());
    }

    @Test
    void windowsDeviceNamesGetAPrefix() {
        ThemeStore repository = store();
        assertTrue(repository.saveTheme("CON", Theme.defaults()).saved());
        assertTrue(Files.exists(themeFile("_CON.properties")));
        assertNotNull(repository.loadTheme("CON"));
    }

    @Test
    void writeFailureIsReported() throws IOException {
        Files.createDirectories(storeDir());
        Files.writeString(themeFile(""), "kein Verzeichnis"); // blockiert „themes/“
        ThemeStore repository = store();

        ThemeStore.SaveResult result = repository.saveTheme("Dunkel", Theme.defaults());

        assertEquals(ThemeStore.SaveResult.Status.WRITE_FAILED, result.status());
    }

    @Test
    void invalidColorFallsBackToDefault() throws IOException {
        ThemeStore repository = store();
        repository.saveTheme("Kaputt", Theme.defaults());
        Path file = storeDir().resolve("themes").resolve("Kaputt.properties");
        String content = Files.readString(file).replaceFirst("(?m)^clock=.*$", "clock=keineFarbe");
        Files.writeString(file, content);

        Theme reloaded = repository.loadTheme("Kaputt");
        assertNotNull(reloaded);
        assertEquals(ThemeColor.CLOCK.defaultColor(), reloaded.color(ThemeColor.CLOCK));
    }

    @Test
    void savingLeavesNoTemporaryFilesBehind() throws IOException {
        ThemeStore repository = store();
        repository.saveTheme("Dunkel", Theme.defaults());
        repository.saveTheme("Dunkel", Theme.defaults().with(ThemeColor.CLOCK, Color.web("#abcdef")));

        try (var files = Files.list(storeDir().resolve("themes"))) {
            assertEquals(List.of("Dunkel.properties"), files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    void leftoverTemporaryFileIsNotListedAsTheme() throws IOException {
        ThemeStore repository = store();
        repository.saveTheme("Dunkel", Theme.defaults());
        Files.writeString(themeFile("Halb.properties.tmp"), "clock=#123456");

        assertEquals(List.of("Dunkel"), repository.themeNames());
    }
}

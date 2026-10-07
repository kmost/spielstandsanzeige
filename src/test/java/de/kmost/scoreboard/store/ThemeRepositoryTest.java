package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeRepositoryTest {

    @TempDir
    Path tempDir;

    private Path storeDir() {
        return tempDir.resolve("store");
    }

    @Test
    void savedThemeSurvivesReload() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        Theme theme = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#123456"));
        repository.saveTheme("Dunkel", theme);

        Theme reloaded = new ThemeRepository(storeDir()).loadTheme("Dunkel");
        assertNotNull(reloaded);
        assertEquals(theme, reloaded);
    }

    @Test
    void fontFamilySurvivesReload() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Serifen", Theme.defaults().withFont("Georgia"));

        Theme reloaded = new ThemeRepository(storeDir()).loadTheme("Serifen");
        assertNotNull(reloaded);
        assertEquals("Georgia", reloaded.fontFamily());
        // Dateien ohne font-Eintrag (alte Themes) ergeben die Systemschrift
        assertEquals("", Theme.defaults().fontFamily());
    }

    @Test
    void fontScalesSurviveReload() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Groß", Theme.defaults()
                .with(FontScale.HEADER, 1.5)
                .with(FontScale.CLOCK, 2.0)
                .with(FontScale.FOOTER, 0.5));

        Theme reloaded = new ThemeRepository(storeDir()).loadTheme("Groß");
        assertNotNull(reloaded);
        assertEquals(1.5, reloaded.scale(FontScale.HEADER));
        assertEquals(2.0, reloaded.scale(FontScale.CLOCK));
        assertEquals(0.5, reloaded.scale(FontScale.FOOTER));
        assertEquals(1.0, reloaded.scale(FontScale.SCORE));
        // Dateien ohne Einträge (alte Themes) ergeben die Standardgröße
        assertEquals(1.0, Theme.defaults().scale(FontScale.TEAM_NAME));
    }

    @Test
    void hornSelectionSurvivesReload() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveHorn("SIRENE", new File(storeDir().toFile(), "hupe.wav"));

        ThemeRepository reloaded = new ThemeRepository(storeDir());
        assertEquals("SIRENE", reloaded.hornTone());
        assertEquals("hupe.wav", reloaded.hornFile().getName());

        // eingebauter Ton ohne Datei: Datei-Eintrag wird geleert
        repository.saveHorn("KLASSISCH", null);
        assertNull(new ThemeRepository(storeDir()).hornFile());
    }

    @Test
    void namesAreSortedAndUmlautsSurvive() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Rot/Weiß", Theme.defaults());
        repository.saveTheme("Grün", Theme.defaults());

        assertEquals(List.of("Grün", "Rot/Weiß"), new ThemeRepository(storeDir()).themeNames());
        assertNotNull(new ThemeRepository(storeDir()).loadTheme("Rot/Weiß"));
    }

    @Test
    void unknownThemeReturnsNull() {
        assertNull(new ThemeRepository(storeDir()).loadTheme("gibt es nicht"));
    }

    @Test
    void deletedThemeIsGone() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Weg damit", Theme.defaults());
        repository.deleteTheme("Weg damit");

        assertTrue(repository.themeNames().isEmpty());
        assertNull(repository.loadTheme("Weg damit"));
    }

    @Test
    void currentStateRoundtripIncludingBanners() throws IOException {
        ThemeRepository repository = new ThemeRepository(storeDir());
        Theme theme = Theme.defaults()
                .with(ThemeColor.SCORE, Color.web("#AB01CD"))
                .with(ThemeColor.FOOTER, Color.web("#FFEE0080")); // mit Transparenz
        File image = repository.storeBannerImage("footer-1",
                imageFile("sponsor.png", new byte[]{1, 2}));
        assertNotNull(image);
        BannerConfig header = new BannerConfig(List.of("Herzlich willkommen!"), List.of());
        BannerConfig footer = new BannerConfig(
                List.of("", "TSV Tarp – Handball"), List.of(image));
        repository.saveCurrent(theme, header, footer);

        ThemeRepository reloaded = new ThemeRepository(storeDir());
        assertEquals(theme, reloaded.currentTheme());
        assertEquals(header, reloaded.currentHeader());
        assertEquals(footer, reloaded.currentFooter());
    }

    @Test
    void missingCurrentStateFallsBackToDefaults() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        assertEquals(Theme.defaults(), repository.currentTheme());
        assertEquals(BannerConfig.empty(), repository.currentHeader());
        assertEquals(BannerConfig.empty(), repository.currentFooter());
    }

    @Test
    void legacyTextOnlyStateIsMigrated() throws IOException {
        Files.createDirectories(storeDir());
        Files.writeString(storeDir().resolve("display.properties"),
                "headerText=Willkommen\nfooterText=TSV Tarp\n");

        ThemeRepository repository = new ThemeRepository(storeDir());
        assertEquals("Willkommen", repository.currentHeader().text(0));
        assertEquals("TSV Tarp", repository.currentFooter().text(0));
    }

    @Test
    void legacyVariantStateIsMigratedInDisplayOrder() throws IOException {
        ThemeRepository repository = new ThemeRepository(storeDir());
        File image = repository.storeBannerImage("header-1", imageFileUnchecked("logo.png"));
        Files.writeString(storeDir().resolve("display.properties"),
                "header.variant=BILD_TEXT\nheader.text1=TSV Tarp\nheader.image1="
                        + image.getName() + "\n");

        BannerConfig migrated = repository.currentHeader();
        // Reihenfolge Bild vor Text: Bild in Slot 1, Text erst in Slot 2
        assertEquals(image, migrated.image(0));
        assertEquals("", migrated.text(0));
        assertEquals("TSV Tarp", migrated.text(1));
    }

    @Test
    void missingBannerImageIsDropped() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        File image = repository.storeBannerImage("header-1", imageFileUnchecked("logo.png"));
        repository.saveCurrent(Theme.defaults(),
                new BannerConfig(List.of(), List.of(image)),
                BannerConfig.empty());
        assertTrue(image.delete());

        assertNull(new ThemeRepository(storeDir()).currentHeader().image(0));
    }

    private File imageFile(String name, byte[] content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file.toFile();
    }

    private File imageFileUnchecked(String name) {
        try {
            return imageFile(name, new byte[]{1});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path themeFile(String fileName) {
        return storeDir().resolve("themes").resolve(fileName);
    }

    @Test
    void umlautsStayInFileName() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        assertTrue(repository.saveTheme("Größe", Theme.defaults()).saved());
        assertTrue(Files.exists(themeFile("Größe.properties")));
        assertNotNull(repository.loadTheme("Größe"));
    }

    @Test
    void sameDerivedFileNameForDifferentNamesIsReportedAsConflict() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        Theme first = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#111111"));
        assertTrue(repository.saveTheme("A/B", first).saved());

        ThemeRepository.SaveResult result = repository.saveTheme("A_B",
                Theme.defaults().with(ThemeColor.CLOCK, Color.web("#222222")));

        assertEquals(ThemeRepository.SaveResult.Status.NAME_CONFLICT, result.status());
        assertEquals("A/B", result.conflictingName());
        // nichts wurde überschrieben, es gibt weiter genau ein Theme
        assertEquals(first, repository.loadTheme("A/B"));
        assertNull(repository.loadTheme("A_B"));
        assertEquals(List.of("A/B"), repository.themeNames());
    }

    @Test
    void savingUnderTheSameNameOverwrites() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Dunkel", Theme.defaults());
        Theme changed = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#abcdef"));

        assertTrue(repository.saveTheme("Dunkel", changed).saved());

        assertEquals(changed, repository.loadTheme("Dunkel"));
        assertEquals(List.of("Dunkel"), repository.themeNames());
    }

    @Test
    void themeFromOlderVersionWithReplacedUmlautsIsStillFoundAndOverwritten() throws IOException {
        // frühere Versionen ersetzten Umlaute im Dateinamen: „Größe“ lag als Gr__e.properties
        ThemeRepository old = new ThemeRepository(storeDir());
        old.saveTheme("Größe", Theme.defaults().with(ThemeColor.CLOCK, Color.web("#111111")));
        Files.move(themeFile("Größe.properties"), themeFile("Gr__e.properties"));

        ThemeRepository repository = new ThemeRepository(storeDir());
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
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Alt", Theme.defaults());
        String content = Files.readString(themeFile("Alt.properties"))
                .replaceFirst("(?m)^_name=.*\\R", "");
        Files.writeString(themeFile("Alt.properties"), content);

        assertNotNull(repository.loadTheme("Alt"));
        assertEquals(List.of("Alt"), repository.themeNames());
    }

    @Test
    void windowsDeviceNamesGetAPrefix() {
        ThemeRepository repository = new ThemeRepository(storeDir());
        assertTrue(repository.saveTheme("CON", Theme.defaults()).saved());
        assertTrue(Files.exists(themeFile("_CON.properties")));
        assertNotNull(repository.loadTheme("CON"));
    }

    @Test
    void writeFailureIsReported() throws IOException {
        Files.createDirectories(storeDir());
        Files.writeString(themeFile(""), "kein Verzeichnis"); // blockiert „themes/“
        ThemeRepository repository = new ThemeRepository(storeDir());

        ThemeRepository.SaveResult result = repository.saveTheme("Dunkel", Theme.defaults());

        assertEquals(ThemeRepository.SaveResult.Status.WRITE_FAILED, result.status());
    }

    @Test
    void invalidColorFallsBackToDefault() throws IOException {
        ThemeRepository repository = new ThemeRepository(storeDir());
        repository.saveTheme("Kaputt", Theme.defaults());
        Path file = storeDir().resolve("themes").resolve("Kaputt.properties");
        String content = Files.readString(file).replaceFirst("(?m)^clock=.*$", "clock=keineFarbe");
        Files.writeString(file, content);

        Theme reloaded = repository.loadTheme("Kaputt");
        assertNotNull(reloaded);
        assertEquals(ThemeColor.CLOCK.defaultColor(), reloaded.color(ThemeColor.CLOCK));
    }
}

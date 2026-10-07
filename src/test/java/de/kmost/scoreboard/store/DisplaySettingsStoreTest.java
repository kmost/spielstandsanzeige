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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.store.DisplaySettingsStore.DisplaySettings;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.scene.paint.Color;

class DisplaySettingsStoreTest {

    @TempDir
    Path tempDir;

    private Path storeDir() {
        return tempDir.resolve("store");
    }

    private SettingsStores stores() {
        return SettingsStores.in(storeDir());
    }

    @Test
    void currentStateRoundtripIncludingBanners() throws IOException {
        SettingsStores repository = stores();
        Theme theme = Theme.defaults()
                .with(ThemeColor.SCORE, Color.web("#AB01CD"))
                .with(ThemeColor.FOOTER, Color.web("#FFEE0080")); // mit Transparenz
        File image = repository.banners().store("footer-1",
                imageFile("sponsor.png", new byte[]{1, 2}));
        assertNotNull(image);
        BannerConfig header = new BannerConfig(List.of("Herzlich willkommen!"), List.of());
        BannerConfig footer = new BannerConfig(
                List.of("", "TSV Tarp – Handball"), List.of(image));
        repository.display().save(theme, header, footer);

        SettingsStores reloaded = stores();
        assertEquals(theme, reloaded.display().load().theme());
        assertEquals(header, reloaded.display().load().header());
        assertEquals(footer, reloaded.display().load().footer());
    }

    @Test
    void missingCurrentStateFallsBackToDefaults() {
        SettingsStores repository = stores();
        assertEquals(Theme.defaults(), repository.display().load().theme());
        assertEquals(BannerConfig.empty(), repository.display().load().header());
        assertEquals(BannerConfig.empty(), repository.display().load().footer());
    }

    @Test
    void legacyTextOnlyStateIsMigrated() throws IOException {
        Files.createDirectories(storeDir());
        Files.writeString(storeDir().resolve("display.properties"),
                "headerText=Willkommen\nfooterText=TSV Tarp\n");

        SettingsStores repository = stores();
        assertEquals("Willkommen", repository.display().load().header().text(0));
        assertEquals("TSV Tarp", repository.display().load().footer().text(0));
    }

    @Test
    void legacyVariantStateIsMigratedInDisplayOrder() throws IOException {
        SettingsStores repository = stores();
        File image = repository.banners().store("header-1", imageFileUnchecked("logo.png"));
        Files.writeString(storeDir().resolve("display.properties"),
                "header.variant=BILD_TEXT\nheader.text1=TSV Tarp\nheader.image1="
                        + image.getName() + "\n");

        BannerConfig migrated = repository.display().load().header();
        // Reihenfolge Bild vor Text: Bild in Slot 1, Text erst in Slot 2
        assertEquals(image, migrated.image(0));
        assertEquals("", migrated.text(0));
        assertEquals("TSV Tarp", migrated.text(1));
    }

    @Test
    void missingBannerImageIsDropped() {
        SettingsStores repository = stores();
        File image = repository.banners().store("header-1", imageFileUnchecked("logo.png"));
        repository.display().save(Theme.defaults(),
                new BannerConfig(List.of(), List.of(image)),
                BannerConfig.empty());
        assertTrue(image.delete());

        assertNull(stores().display().load().header().image(0));
    }

    @Test
    void loadReturnsThemeAndBannersFromASingleFile() {
        SettingsStores stores = stores();
        Theme theme = Theme.defaults().with(ThemeColor.CLOCK, Color.web("#123456"));
        BannerConfig header = new BannerConfig(List.of("Oben"), List.of());
        BannerConfig footer = new BannerConfig(List.of("Unten"), List.of());
        stores.display().save(theme, header, footer);

        DisplaySettings loaded = stores().display().load();
        assertEquals(theme, loaded.theme());
        assertEquals(header, loaded.header());
        assertEquals(footer, loaded.footer());
    }

    @Test
    void savingLeavesNoTemporaryFileBehind() throws IOException {
        stores().display().save(Theme.defaults(), BannerConfig.empty(), BannerConfig.empty());
        assertTrue(Files.exists(storeDir().resolve("display.properties")));
        assertTrue(Files.notExists(storeDir().resolve("display.properties.tmp")));
    }

    @Test
    void unreadableFileFallsBackToDefaults() throws IOException {
        // display.properties ist ein Verzeichnis: nicht lesbar, aber der Start geht weiter
        Files.createDirectories(storeDir().resolve("display.properties"));
        assertEquals(DisplaySettings.defaults(), stores().display().load());
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
}

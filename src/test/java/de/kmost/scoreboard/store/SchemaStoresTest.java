package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.Theme;

/** Schemaversion in den Properties-Dateien: Stempel, Altdateien ohne Version, neuere Versionen. */
class SchemaStoresTest {

    @TempDir
    Path dir;

    private final List<String> problems = new ArrayList<>();
    private ProblemReporter reporter;
    private BannerImageStore banners;

    @BeforeEach
    void setUp() {
        reporter = new ProblemReporter(null);
        reporter.addListener(problems::add);
        banners = new BannerImageStore(dir, reporter);
    }

    private Properties read(String file) throws IOException {
        return PropertiesFiles.load(dir.resolve(file));
    }

    private DisplaySettingsStore display() {
        return new DisplaySettingsStore(dir, reporter, banners);
    }

    // --- display.properties ---

    @Test
    void displaySettingsAreStampedAndRoundTrip() throws IOException {
        BannerConfig header = new BannerConfig(List.of("Willkommen", "", "TSV"), List.of());
        display().save(Theme.defaults(), header, BannerConfig.empty());

        assertEquals("1", read("display.properties").getProperty("schema"));
        assertEquals(header, display().load().header());
    }

    @Test
    void unversionedGridFileLoadsAndIsStampedOnNextSave() throws IOException {
        Files.writeString(dir.resolve("display.properties"),
                "header.text.0=Hallo\nheader.text.2=Welt\nfooter.text.0=Tschüss\n");

        DisplaySettingsStore.DisplaySettings loaded = display().load();
        assertEquals("Hallo", loaded.header().text(0));
        assertEquals("Welt", loaded.header().text(2));
        assertEquals("Tschüss", loaded.footer().text(0));

        display().save(loaded.theme(), loaded.header(), loaded.footer());
        assertEquals("1", read("display.properties").getProperty("schema"));
        assertEquals(loaded.header(), display().load().header());
    }

    @Test
    void migratedLegacyFileIsWrittenInCurrentFormat() throws IOException {
        Files.writeString(dir.resolve("display.properties"),
                "headerText=Willkommen\nfooter.variant=TEXT_BILD\nfooter.text1=TSV Tarp\n");

        DisplaySettingsStore.DisplaySettings loaded = display().load();
        display().save(loaded.theme(), loaded.header(), loaded.footer());

        Properties written = read("display.properties");
        assertEquals("Willkommen", written.getProperty("header.text.0"));
        assertEquals("TSV Tarp", written.getProperty("footer.text.0"));
        assertNull(written.getProperty("headerText"));
        assertNull(written.getProperty("footer.variant"));
        assertEquals("TSV Tarp", display().load().footer().text(0));
    }

    @Test
    void newerDisplayFileIsSetAsideNotOverwritten() throws IOException {
        String newer = "schema=2\nheader.text.0=Zukunft\n";
        Files.writeString(dir.resolve("display.properties"), newer);

        DisplaySettingsStore.DisplaySettings loaded = display().load();

        assertEquals(DisplaySettingsStore.DisplaySettings.defaults(), loaded);
        assertEquals(1, problems.size());
        assertFalse(Files.exists(dir.resolve("display.properties")));
        assertEquals(newer, Files.readString(dir.resolve("display.properties.schema2")));

        // neues Speichern legt eine frische Datei an, die Sicherung bleibt unangetastet
        display().save(Theme.defaults(), BannerConfig.empty(), BannerConfig.empty());
        assertEquals("1", read("display.properties").getProperty("schema"));
        assertEquals(newer, Files.readString(dir.resolve("display.properties.schema2")));
    }

    // --- horn.properties ---

    @Test
    void hornSettingsAreStampedAndUnversionedFilesLoad() throws IOException {
        HornSettingsStore store = new HornSettingsStore(dir, reporter);
        store.save("KLASSISCH", null);
        assertEquals("1", read("horn.properties").getProperty("schema"));

        Files.writeString(dir.resolve("horn.properties"), "tone=TIEF\nfile=\n");
        assertEquals("TIEF", store.load().tone());
    }

    @Test
    void newerHornFileIsSetAside() throws IOException {
        Files.writeString(dir.resolve("horn.properties"), "schema=5\ntone=NEU\n");

        HornSettingsStore.HornSettings loaded = new HornSettingsStore(dir, reporter).load();

        assertEquals("", loaded.tone());
        assertNull(loaded.file());
        assertTrue(Files.exists(dir.resolve("horn.properties.schema5")));
        assertFalse(Files.exists(dir.resolve("horn.properties")));
        assertEquals(1, problems.size());
    }

    // --- teams.properties ---

    @Test
    void teamsAreStampedUnderInternalKey() throws IOException {
        TeamRepository teams = new TeamRepository(dir, reporter);
        teams.saveTeam("TSV Tarp");

        assertEquals("1", read("teams.properties").getProperty("_schema"));
        assertEquals(List.of("TSV Tarp"), new TeamRepository(dir, reporter).teamNames());
    }

    @Test
    void unversionedTeamFileLoadsWithOldInternalKeys() throws IOException {
        Files.writeString(dir.resolve("teams.properties"), "TSV\\ Tarp=\nSG\\ Flensburg=\n_defaultHome=TSV Tarp\n");

        TeamRepository teams = new TeamRepository(dir, reporter);

        assertEquals(List.of("SG Flensburg", "TSV Tarp"), teams.teamNames());
        assertEquals("TSV Tarp", teams.defaultHomeTeam());
    }

    @Test
    void internalKeysAreNeverTeamNames() {
        TeamRepository teams = new TeamRepository(dir, reporter);
        teams.saveTeam("_schema");
        teams.saveTeam("_defaultHome");
        teams.saveTeam("TSV Tarp");

        assertEquals(List.of("TSV Tarp"), teams.teamNames());
        assertEquals("", teams.defaultHomeTeam());
    }

    @Test
    void newerTeamFileIsSetAsideAndNotLost() throws IOException {
        String newer = "_schema=3\nTSV\\ Tarp=\n";
        Files.writeString(dir.resolve("teams.properties"), newer);

        TeamRepository teams = new TeamRepository(dir, reporter);
        assertEquals(List.of(), teams.teamNames());
        assertEquals(1, problems.size());

        teams.saveTeam("Neu");
        assertEquals(newer, Files.readString(dir.resolve("teams.properties.schema3")));
        assertEquals(List.of("Neu"), new TeamRepository(dir, reporter).teamNames());
    }

    // --- themes ---

    @Test
    void themesAreStampedAndUnversionedThemesLoad() throws IOException {
        ThemeStore store = new ThemeStore(dir, reporter);
        assertTrue(store.saveTheme("Dunkel", Theme.defaults()).saved());
        assertEquals("1", read("themes/Dunkel.properties").getProperty("schema"));

        Properties old = read("themes/Dunkel.properties");
        old.remove("schema");
        PropertiesFiles.store(dir.resolve("themes/Dunkel.properties"), old, "alt");
        assertEquals(Theme.defaults(), store.loadTheme("Dunkel"));
    }

    @Test
    void newerThemeIsIgnoredAndNeverOverwritten() throws IOException {
        Files.createDirectories(dir.resolve("themes"));
        String newer = "schema=2\n_name=Zukunft\nfont=Irgendwas\n";
        Files.writeString(dir.resolve("themes/Zukunft.properties"), newer);
        ThemeStore store = new ThemeStore(dir, reporter);

        assertEquals(List.of(), store.themeNames());
        assertNull(store.loadTheme("Zukunft"));

        ThemeStore.SaveResult result = store.saveTheme("Zukunft", Theme.defaults());
        assertFalse(result.saved());
        assertEquals(newer, Files.readString(dir.resolve("themes/Zukunft.properties")));
    }
}

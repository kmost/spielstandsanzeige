package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.store.HornSettingsStore.HornSettings;

class HornSettingsStoreTest {

    @TempDir
    Path tempDir;

    private HornSettingsStore store() {
        return SettingsStores.in(tempDir.resolve("store")).horn();
    }

    @Test
    void hornSelectionSurvivesReload() {
        store().save("SIRENE", new File(tempDir.toFile(), "hupe.wav"));

        HornSettings reloaded = store().load();
        assertEquals("SIRENE", reloaded.tone());
        assertEquals("hupe.wav", reloaded.file().getName());

        // eingebauter Ton ohne Datei: Datei-Eintrag wird geleert
        store().save("KLASSISCH", null);
        assertNull(store().load().file());
        assertEquals("KLASSISCH", store().load().tone());
    }

    @Test
    void nothingSavedMeansEmptySelection() {
        HornSettings settings = store().load();
        assertEquals("", settings.tone());
        assertNull(settings.file());
    }

    @Test
    void unreadableFileMeansEmptySelection() throws IOException {
        Files.createDirectories(tempDir.resolve("store").resolve("horn.properties"));
        assertEquals("", store().load().tone());
    }

    @Test
    void savingLeavesNoTemporaryFileBehind() {
        store().save("SIRENE", null);
        assertTrue(Files.exists(tempDir.resolve("store").resolve("horn.properties")));
        assertTrue(Files.notExists(tempDir.resolve("store").resolve("horn.properties.tmp")));
    }
}

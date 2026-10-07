package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class BannerImageStoreTest {

    @TempDir
    Path tempDir;

    private BannerImageStore store() {
        return SettingsStores.in(tempDir.resolve("store")).banners();
    }

    private File image(String name, byte... content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file.toFile();
    }

    @Test
    void storesImageUnderSlotNameWithOriginalExtension() throws IOException {
        File stored = store().store("footer-1", image("sponsor.JPG", (byte) 1, (byte) 2));

        assertNotNull(stored);
        assertEquals("footer-1.jpg", stored.getName());
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(stored.toPath()));
        assertEquals(stored, store().resolve("footer-1.jpg"));
    }

    @Test
    void replacesTheImageOfASlotAndLeavesNoTemporaryFile() throws IOException {
        store().store("header-1", image("a.png", (byte) 1));
        File replaced = store().store("header-1", image("b.png", (byte) 9));

        assertArrayEquals(new byte[] {9}, Files.readAllBytes(replaced.toPath()));
        try (var files = Files.list(replaced.toPath().getParent())) {
            assertEquals(List.of("header-1.png"), files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    void storingTheStoredFileAgainKeepsIt() throws IOException {
        File stored = store().store("header-1", image("a.png", (byte) 1, (byte) 2, (byte) 3));
        File again = store().store("header-1", stored);

        assertNotNull(again);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(again.toPath()));
    }

    @Test
    void missingSourceYieldsNullAndKeepsTheExistingImage() throws IOException {
        File stored = store().store("header-1", image("a.png", (byte) 7));

        assertNull(store().store("header-1", tempDir.resolve("gibt-es-nicht.png").toFile()));
        assertArrayEquals(new byte[] {7}, Files.readAllBytes(stored.toPath()));
        try (var files = Files.list(stored.toPath().getParent())) {
            assertEquals(1, files.count(), "keine halbe Datei bleibt zurück");
        }
    }

    @Test
    void resolveIgnoresBlankAndMissingNames() {
        assertNull(store().resolve(""));
        assertNull(store().resolve(null));
        assertNull(store().resolve("gibt-es-nicht.png"));
    }
}

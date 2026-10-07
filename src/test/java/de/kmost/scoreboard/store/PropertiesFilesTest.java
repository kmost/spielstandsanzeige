package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Das atomare Schreiben: bricht es ab, bleibt die vorhandene Datei unverändert. */
class PropertiesFilesTest {

    @TempDir
    Path dir;

    @Test
    void storedPropertiesAreReadBackInUtf8() throws IOException {
        Properties props = new Properties();
        props.setProperty("name", "Größe – Überstunde");
        PropertiesFiles.store(dir.resolve("sub").resolve("a.properties"), props, "Kommentar");

        assertEquals("Größe – Überstunde",
                PropertiesFiles.load(dir.resolve("sub").resolve("a.properties")).getProperty("name"));
        assertTrue(Files.notExists(dir.resolve("sub").resolve("a.properties.tmp")));
    }

    @Test
    void abortedWriteKeepsTheOriginalFileAndLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("a.properties");
        Files.writeString(file, "alt=1\n");

        IOException failure = assertThrows(IOException.class, () -> PropertiesFiles.writeAtomically(file, writer -> {
            writer.write("neu=halb");
            throw new IOException("Platte voll");
        }));

        assertEquals("Platte voll", failure.getMessage());
        assertEquals("alt=1\n", Files.readString(file));
        assertTrue(Files.notExists(PropertiesFiles.tempFor(file)));
    }

    @Test
    void abortedWriteWithoutOriginalLeavesNothing() {
        Path file = dir.resolve("neu.properties");

        assertThrows(IllegalStateException.class, () -> PropertiesFiles.writeAtomically(file, writer -> {
            writer.write("halb");
            throw new IllegalStateException("kaputt");
        }));

        assertTrue(Files.notExists(file));
        assertTrue(Files.notExists(PropertiesFiles.tempFor(file)));
    }

    @Test
    void overwritesAnExistingFileCompletely() throws IOException {
        Path file = dir.resolve("a.properties");
        Properties first = new Properties();
        first.setProperty("a", "1");
        first.setProperty("b", "2");
        PropertiesFiles.store(file, first, null);
        Properties second = new Properties();
        second.setProperty("c", "3");
        PropertiesFiles.store(file, second, null);

        Properties loaded = PropertiesFiles.load(file);
        assertEquals(1, loaded.size());
        assertEquals("3", loaded.getProperty("c"));
    }

    @Test
    void abortedCopyKeepsTheOriginalTarget() throws IOException {
        Path target = dir.resolve("bild.png");
        Files.write(target, new byte[] {1, 2, 3});

        assertThrows(IOException.class,
                () -> PropertiesFiles.copyAtomically(dir.resolve("gibt-es-nicht.png"), target));

        assertEquals(3, Files.size(target));
        assertTrue(Files.notExists(PropertiesFiles.tempFor(target)));
    }
}

package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

class SchemaVersionTest {

    @TempDir
    Path dir;

    @Test
    void missingOrBrokenVersionIsLegacy() {
        Properties props = new Properties();
        assertEquals(SchemaVersion.LEGACY, SchemaVersion.of(props));
        props.setProperty("schema", "");
        assertEquals(SchemaVersion.LEGACY, SchemaVersion.of(props));
        props.setProperty("schema", "abc");
        assertEquals(SchemaVersion.LEGACY, SchemaVersion.of(props));
        props.setProperty("schema", "-3");
        assertEquals(SchemaVersion.LEGACY, SchemaVersion.of(props));
    }

    @Test
    void stampedVersionIsRead() {
        Properties props = new Properties();
        SchemaVersion.stamp(props, 2);
        assertEquals(2, SchemaVersion.of(props));
        props.setProperty("schema", " 7 ");
        assertEquals(7, SchemaVersion.of(props));
    }

    @Test
    void customKeyIsSupported() {
        Properties props = new Properties();
        SchemaVersion.stamp(props, "_schema", 4);
        assertEquals(4, SchemaVersion.of(props, "_schema"));
        assertEquals(SchemaVersion.LEGACY, SchemaVersion.of(props));
    }

    @Test
    void newerFileIsSetAsideAndReported() throws IOException {
        Path file = Files.writeString(dir.resolve("display.properties"), "schema=9\nx=1\n");
        List<String> problems = new ArrayList<>();
        ProblemReporter reporter = new ProblemReporter(null);
        reporter.addListener(problems::add);

        SchemaVersion.setAsideNewer(file, 9, reporter);

        assertFalse(Files.exists(file));
        assertEquals("schema=9\nx=1\n", Files.readString(dir.resolve("display.properties.schema9")));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("display.properties.schema9"), problems.get(0));
    }

    @Test
    void setAsideReplacesAnEarlierBackupOfTheSameVersion() throws IOException {
        Files.writeString(dir.resolve("a.properties.schema2"), "alt");
        Path file = Files.writeString(dir.resolve("a.properties"), "neu");

        SchemaVersion.setAsideNewer(file, 2, new ProblemReporter(null));

        assertEquals("neu", Files.readString(dir.resolve("a.properties.schema2")));
    }
}

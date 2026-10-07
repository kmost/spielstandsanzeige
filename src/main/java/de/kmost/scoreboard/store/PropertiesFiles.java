package de.kmost.scoreboard.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Gemeinsame Datei-Hilfen der Stores: UTF-8-Properties lesen und Dateien atomar schreiben
 * (erst in eine Temp-Datei neben dem Ziel, dann umbenennen). Bricht ein Schreibvorgang ab,
 * bleibt die vorhandene Datei unverändert; eine Temp-Datei bleibt nicht zurück.
 */
final class PropertiesFiles {

    /** Schreibt den Inhalt einer Datei; darf abbrechen. */
    interface Body {
        void write(Writer writer) throws IOException;
    }

    private PropertiesFiles() {
    }

    static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    static void store(Path file, Properties props, String comment) throws IOException {
        writeAtomically(file, writer -> props.store(writer, comment));
    }

    static void writeAtomically(Path target, Body body) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = tempFor(target);
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                body.write(writer);
            }
            move(temp, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    /** Kopiert eine Datei so, dass das Ziel nie halb geschrieben ist. */
    static void copyAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = tempFor(target);
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            move(temp, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    /** Temp-Datei neben dem Ziel (gleiches Dateisystem); endet auf „.tmp“, wird also nie als Datenbestand gelesen. */
    static Path tempFor(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // die ursprüngliche Ausnahme ist die wichtige
        }
    }
}

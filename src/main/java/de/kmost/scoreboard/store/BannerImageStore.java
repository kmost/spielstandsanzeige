package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

/** Banner-Bilder in der Datenbank (~/.spielstandsanzeige/banners/, eine Datei je Slot). */
public final class BannerImageStore {

    private static final String BANNER_DIR = "banners";

    private final Path baseDir;
    private final ProblemReporter reporter;

    public BannerImageStore(Path baseDir, ProblemReporter reporter) {
        this.baseDir = baseDir;
        this.reporter = reporter;
    }

    /**
     * Kopiert ein Banner-Bild in die Datenbank (banners/<slot>.<ext>), ersetzt ein
     * vorhandenes Bild des Slots und liefert die gespeicherte Datei; null bei Fehlern.
     * Das Bild wird atomar ersetzt, ein abgebrochenes Kopieren lässt das alte Bild stehen.
     */
    public File store(String slot, File source) {
        try {
            String fileName = slot + LogoDownloader.extensionOf(source.getName());
            Path target = baseDir.resolve(BANNER_DIR).resolve(fileName);
            PropertiesFiles.copyAtomically(source.toPath(), target);
            return target.toFile();
        } catch (IOException e) {
            reporter.report("Banner-Bild konnte nicht gespeichert werden", e);
            return null;
        }
    }

    /** Gespeichertes Banner-Bild; null, wenn keines gesetzt oder die Datei fehlt. */
    public File resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        File file = baseDir.resolve(BANNER_DIR).resolve(fileName).toFile();
        return file.isFile() ? file : null;
    }
}

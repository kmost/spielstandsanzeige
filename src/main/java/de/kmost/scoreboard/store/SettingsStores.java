package de.kmost.scoreboard.store;

import java.nio.file.Path;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

/**
 * Die Stores der Anzeige-Einstellungen unter einem gemeinsamen Datenverzeichnis, damit Fenster
 * sie als eine Einheit entgegennehmen können. Reiner Halter ohne eigene Logik.
 */
public record SettingsStores(ThemeStore themes, DisplaySettingsStore display,
                             HornSettingsStore horn, BannerImageStore banners) {

    /** Der Standard-Ort ~/.spielstandsanzeige/ mit der gemeinsamen Problem-Meldestelle. */
    public static SettingsStores standard() {
        return in(Path.of(System.getProperty("user.home"), ".spielstandsanzeige"), ProblemReporter.shared());
    }

    /** Ein beliebiges Verzeichnis (Tests); Probleme gehen in dessen eigene Logdatei. */
    public static SettingsStores in(Path baseDir) {
        return in(baseDir, new ProblemReporter(baseDir.resolve("spielstandsanzeige.log")));
    }

    public static SettingsStores in(Path baseDir, ProblemReporter reporter) {
        BannerImageStore banners = new BannerImageStore(baseDir, reporter);
        return new SettingsStores(new ThemeStore(baseDir, reporter),
                new DisplaySettingsStore(baseDir, reporter, banners),
                new HornSettingsStore(baseDir, reporter),
                banners);
    }
}

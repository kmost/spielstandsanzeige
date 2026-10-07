package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.store.migration.DisplaySettingsMigration;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.Theme;

/**
 * Der zuletzt aktive Anzeige-Zustand (Farben, Schrift, Größen, Header und Footer) in
 * display.properties; wird beim nächsten Start wiederhergestellt. Die Datei trägt eine
 * Schemaversion ({@link DisplaySettingsMigration}); ältere Formate werden beim Lesen umgewandelt,
 * eine neuere Version wird beiseitegelegt statt überschrieben. Fehler beim Lesen/Schreiben
 * werden gemeldet, blockieren aber nie den Spielbetrieb.
 */
public final class DisplaySettingsStore {

    private static final String FILE = "display.properties";

    /** Aktiver Zustand der Anzeige. */
    public record DisplaySettings(Theme theme, BannerConfig header, BannerConfig footer) {

        /** Zustand, solange noch nichts gespeichert wurde. */
        public static DisplaySettings defaults() {
            return new DisplaySettings(Theme.defaults(), BannerConfig.empty(), BannerConfig.empty());
        }
    }

    private final Path baseDir;
    private final ProblemReporter reporter;
    private final BannerImageStore bannerImages;

    public DisplaySettingsStore(Path baseDir, ProblemReporter reporter, BannerImageStore bannerImages) {
        this.baseDir = baseDir;
        this.reporter = reporter;
        this.bannerImages = bannerImages;
    }

    /** Merkt den aktiven Zustand; wird beim nächsten Start wiederhergestellt. */
    public void save(Theme theme, BannerConfig header, BannerConfig footer) {
        Properties props = ThemeProperties.toProperties(theme);
        SchemaVersion.stamp(props, DisplaySettingsMigration.CURRENT_SCHEMA);
        putBanner(props, "header", header);
        putBanner(props, "footer", footer);
        try {
            PropertiesFiles.store(baseDir.resolve(FILE), props,
                    "Aktive Anzeige-Konfiguration der Spielstandsanzeige");
        } catch (IOException e) {
            reporter.report("Datei „" + FILE + "“ konnte nicht gespeichert werden", e);
        }
    }

    /** Zuletzt aktiver Zustand, mit einem Lesezugriff; Standardwerte, wenn noch nichts gespeichert wurde. */
    public DisplaySettings load() {
        Path file = baseDir.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return DisplaySettings.defaults();
        }
        Properties props;
        try {
            props = PropertiesFiles.load(file);
        } catch (IOException e) {
            reporter.report("Datei „" + FILE + "“ nicht lesbar", e);
            return DisplaySettings.defaults();
        }
        int schema = SchemaVersion.of(props);
        if (schema > DisplaySettingsMigration.CURRENT_SCHEMA) {
            SchemaVersion.setAsideNewer(file, schema, reporter);
            return DisplaySettings.defaults();
        }
        props = DisplaySettingsMigration.upgrade(props, schema);
        return new DisplaySettings(ThemeProperties.fromProperties(props, reporter),
                bannerFrom(props, "header"), bannerFrom(props, "footer"));
    }

    private static void putBanner(Properties props, String prefix, BannerConfig banner) {
        BannerConfig config = banner == null ? BannerConfig.empty() : banner;
        for (int i = 0; i < BannerConfig.TEXT_SLOTS; i++) {
            props.setProperty(prefix + ".text." + i, config.text(i));
        }
        for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
            File image = config.images().get(i);
            props.setProperty(prefix + ".image." + i, image == null ? "" : image.getName());
        }
    }

    /** Banner im Raster-Format (Schema 1); ältere Formate hat {@link DisplaySettingsMigration} schon umgewandelt. */
    private BannerConfig bannerFrom(Properties props, String prefix) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < BannerConfig.TEXT_SLOTS; i++) {
            texts.add(props.getProperty(prefix + ".text." + i, ""));
        }
        List<File> images = new ArrayList<>();
        for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
            images.add(bannerImages.resolve(props.getProperty(prefix + ".image." + i, "")));
        }
        return new BannerConfig(texts, images);
    }
}

package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.Theme;

/**
 * Der zuletzt aktive Anzeige-Zustand (Farben, Schrift, Größen, Header und Footer) in
 * display.properties; wird beim nächsten Start wiederhergestellt. Fehler beim Lesen/Schreiben
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

    private BannerConfig bannerFrom(Properties props, String prefix) {
        if (props.getProperty(prefix + ".text.0") != null) {
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
        if (props.getProperty(prefix + ".variant") != null) {
            return migrateVariantFormat(props, prefix);
        }
        // ältestes Format: nur ein Text (headerText/footerText)
        return new BannerConfig(List.of(props.getProperty(prefix + "Text", "")), List.of());
    }

    /**
     * Migration des Zwischenformats mit Layout-Varianten: die Slot-Reihenfolge der
     * Variante wird im Text/Bild-Raster nachgebildet (T = Text 1, U = Text 2,
     * 1/2 = Bild 1/2), damit die Anzeige-Reihenfolge erhalten bleibt.
     */
    private BannerConfig migrateVariantFormat(Properties props, String prefix) {
        String pattern = switch (props.getProperty(prefix + ".variant", "")) {
            case "BILD_TEXT" -> "1T";
            case "TEXT_BILD" -> "T1";
            case "BILD_TEXT_BILD" -> "1T2";
            case "TEXT_BILD_TEXT" -> "T1U";
            case "NUR_BILD" -> "1";
            case "BILDER" -> "12";
            default -> "T";
        };
        String[] texts = new String[BannerConfig.TEXT_SLOTS];
        File[] images = new File[BannerConfig.IMAGE_SLOTS];
        int position = 0; // Raster-Position: gerade = Text-Slot, ungerade = Bild-Slot
        for (char slot : pattern.toCharArray()) {
            boolean isText = slot == 'T' || slot == 'U';
            while ((position % 2 == 0) != isText) {
                position++;
            }
            if (isText) {
                texts[position / 2] = props.getProperty(
                        prefix + (slot == 'T' ? ".text1" : ".text2"), "");
            } else {
                images[position / 2] = bannerImages.resolve(props.getProperty(
                        prefix + (slot == '1' ? ".image1" : ".image2"), ""));
            }
            position++;
        }
        return new BannerConfig(Arrays.asList(texts), Arrays.asList(images));
    }
}

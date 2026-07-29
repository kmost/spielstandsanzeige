package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.scene.paint.Color;

/**
 * Persistente Anzeige-Konfiguration: benannte Farb-Themes unter
 * ~/.spielstandsanzeige/themes/ und der zuletzt aktive Zustand (Farben + Footer-Text)
 * in display.properties, der beim nächsten Start wiederhergestellt wird. Fehler beim
 * Lesen/Schreiben werden gemeldet, blockieren aber nie den Spielbetrieb.
 */
public class ThemeRepository {

    private static final String THEME_DIR = "themes";
    private static final String BANNER_DIR = "banners";
    private static final String CURRENT_FILE = "display.properties";
    private static final String HORN_FILE = "horn.properties";
    private static final String NAME_KEY = "_name";

    private final Path baseDir;

    public ThemeRepository() {
        this(Path.of(System.getProperty("user.home"), ".spielstandsanzeige"));
    }

    public ThemeRepository(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** Alle gespeicherten Theme-Namen, alphabetisch sortiert. */
    public List<String> themeNames() {
        Path dir = baseDir.resolve(THEME_DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".properties"))
                    .map(this::themeName)
                    .filter(Objects::nonNull)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            System.err.println("Themes nicht lesbar: " + e.getMessage());
            return List.of();
        }
    }

    /** Gespeichertes Theme; null, wenn es nicht existiert oder nicht lesbar ist. */
    public Theme loadTheme(String name) {
        Properties props = read(themeFile(name));
        return props == null ? null : themeFrom(props);
    }

    public void saveTheme(String name, Theme theme) {
        Properties props = propertiesFrom(theme);
        props.setProperty(NAME_KEY, name.strip());
        write(themeFile(name), props, "Farb-Theme der Spielstandsanzeige");
    }

    public void deleteTheme(String name) {
        try {
            Files.deleteIfExists(themeFile(name));
        } catch (IOException e) {
            System.err.println("Theme „" + name + "“ konnte nicht gelöscht werden: " + e.getMessage());
        }
    }

    /** Merkt den aktiven Zustand; wird beim nächsten Start wiederhergestellt. */
    public void saveCurrent(Theme theme, BannerConfig header, BannerConfig footer) {
        Properties props = propertiesFrom(theme);
        putBanner(props, "header", header);
        putBanner(props, "footer", footer);
        write(baseDir.resolve(CURRENT_FILE), props, "Aktive Anzeige-Konfiguration der Spielstandsanzeige");
    }

    /** Zuletzt aktives Theme; Standardfarben, wenn noch nichts gespeichert wurde. */
    public Theme currentTheme() {
        Properties props = read(baseDir.resolve(CURRENT_FILE));
        return props == null ? Theme.defaults() : themeFrom(props);
    }

    /** Zuletzt aktiver Header; leer, wenn noch nichts gespeichert wurde. */
    public BannerConfig currentHeader() {
        return bannerFrom(read(baseDir.resolve(CURRENT_FILE)), "header");
    }

    /** Zuletzt aktiver Footer; leer, wenn noch nichts gespeichert wurde. */
    public BannerConfig currentFooter() {
        return bannerFrom(read(baseDir.resolve(CURRENT_FILE)), "footer");
    }

    /**
     * Merkt die Hupen-Auswahl: eingebauter Ton (Name) und optional eine externe
     * Audiodatei — ist eine Datei gesetzt und ladbar, hat sie Vorrang.
     */
    public void saveHorn(String tone, File file) {
        Properties props = new Properties();
        props.setProperty("tone", tone == null ? "" : tone);
        props.setProperty("file", file == null ? "" : file.getAbsolutePath());
        write(baseDir.resolve(HORN_FILE), props, "Hupen-Auswahl der Spielstandsanzeige");
    }

    /** Name des gewählten eingebauten Huptons; leer, wenn nichts gespeichert. */
    public String hornTone() {
        Properties props = read(baseDir.resolve(HORN_FILE));
        return props == null ? "" : props.getProperty("tone", "");
    }

    /** Externe Hupen-Datei oder null, wenn keine gesetzt ist. */
    public File hornFile() {
        Properties props = read(baseDir.resolve(HORN_FILE));
        String path = props == null ? "" : props.getProperty("file", "");
        return path.isBlank() ? null : new File(path);
    }

    /**
     * Kopiert ein Banner-Bild in die Datenbank (banners/<slot>.<ext>), ersetzt ein
     * vorhandenes Bild des Slots und liefert die gespeicherte Datei; null bei Fehlern.
     */
    public File storeBannerImage(String slot, File source) {
        try {
            Files.createDirectories(baseDir.resolve(BANNER_DIR));
            String fileName = slot + LogoDownloader.extensionOf(source.getName());
            Path target = baseDir.resolve(BANNER_DIR).resolve(fileName);
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        } catch (IOException e) {
            System.err.println("Banner-Bild konnte nicht gespeichert werden: " + e.getMessage());
            return null;
        }
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
        if (props == null) {
            return BannerConfig.empty();
        }
        if (props.getProperty(prefix + ".text.0") != null) {
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < BannerConfig.TEXT_SLOTS; i++) {
                texts.add(props.getProperty(prefix + ".text." + i, ""));
            }
            List<File> images = new ArrayList<>();
            for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
                images.add(bannerFile(props.getProperty(prefix + ".image." + i, "")));
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
                images[position / 2] = bannerFile(props.getProperty(
                        prefix + (slot == '1' ? ".image1" : ".image2"), ""));
            }
            position++;
        }
        return new BannerConfig(Arrays.asList(texts), Arrays.asList(images));
    }

    /** Gespeichertes Banner-Bild; null, wenn keines gesetzt oder die Datei fehlt. */
    private File bannerFile(String fileName) {
        if (fileName.isBlank()) {
            return null;
        }
        File file = baseDir.resolve(BANNER_DIR).resolve(fileName).toFile();
        return file.isFile() ? file : null;
    }

    private Path themeFile(String name) {
        return baseDir.resolve(THEME_DIR).resolve(sanitize(name.strip()) + ".properties");
    }

    /** Anzeigename eines Themes; steht in der Datei, damit Umlaute etc. erhalten bleiben. */
    private String themeName(Path file) {
        Properties props = read(file);
        if (props == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        return props.getProperty(NAME_KEY, fileName.substring(0, fileName.length() - ".properties".length()));
    }

    private static Theme themeFrom(Properties props) {
        Map<ThemeColor, Color> colors = new EnumMap<>(ThemeColor.class);
        for (ThemeColor color : ThemeColor.values()) {
            String value = props.getProperty(color.key(), "");
            if (!value.isBlank()) {
                try {
                    colors.put(color, Color.web(value));
                } catch (IllegalArgumentException e) {
                    System.err.println("Ungültige Farbe für „" + color.key() + "“: " + value);
                }
            }
        }
        Map<FontScale, Double> scales = new EnumMap<>(FontScale.class);
        for (FontScale scale : FontScale.values()) {
            scales.put(scale, scaleFrom(props, scale.key()));
        }
        return new Theme(colors, props.getProperty("font", ""), scales);
    }

    /** Größenfaktor; fehlend oder unlesbar = Standard 1.0. */
    private static double scaleFrom(Properties props, String key) {
        try {
            return Double.parseDouble(props.getProperty(key, "1.0"));
        } catch (NumberFormatException e) {
            System.err.println("Ungültiger Wert für „" + key + "“: " + props.getProperty(key));
            return 1.0;
        }
    }

    private static Properties propertiesFrom(Theme theme) {
        Properties props = new Properties();
        for (ThemeColor color : ThemeColor.values()) {
            props.setProperty(color.key(), Theme.toWeb(theme.color(color)));
        }
        props.setProperty("font", theme.fontFamily());
        for (FontScale scale : FontScale.values()) {
            props.setProperty(scale.key(),
                    String.format(Locale.ROOT, "%.2f", theme.scale(scale)));
        }
        return props;
    }

    private static Properties read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
            return props;
        } catch (IOException e) {
            System.err.println("Datei „" + file.getFileName() + "“ nicht lesbar: " + e.getMessage());
            return null;
        }
    }

    private static void write(Path file, Properties props, String comment) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, comment);
            }
        } catch (IOException e) {
            System.err.println("Datei „" + file.getFileName() + "“ konnte nicht gespeichert werden: "
                    + e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^\\p{Alnum}\\-_]", "_");
    }
}

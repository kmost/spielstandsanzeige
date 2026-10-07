package de.kmost.scoreboard.store.migration;

import java.util.Properties;

/**
 * Wandelt die älteren Banner-Formate in display.properties ins heutige Raster-Format
 * ({@code <prefix>.text.<0-5>}, {@code <prefix>.image.<0-4>}). Es gab drei Formate, je
 * Banner (Präfix {@code header} / {@code footer}) einzeln erkannt:
 * <ol>
 *   <li>Raster (aktuell): {@code header.text.0 …}; bleibt unverändert.</li>
 *   <li>Layout-Varianten: {@code header.variant}, {@code header.text1/text2},
 *       {@code header.image1/image2}.</li>
 *   <li>Ältestes Format: nur ein Text als {@code headerText}.</li>
 * </ol>
 * Reine Properties-zu-Properties-Umwandlung ohne Dateizugriff, damit sie einzeln testbar ist.
 */
public final class BannerFormatMigration {

    /** Anzahl der Text- und Bild-Slots im Raster (siehe {@code BannerConfig}). */
    static final int TEXT_SLOTS = 6;
    static final int IMAGE_SLOTS = 5;

    private BannerFormatMigration() {
    }

    /** Gibt eine Kopie mit beiden Bannern im Raster-Format zurück; die Eingabe bleibt unverändert. */
    public static Properties migrate(Properties legacy) {
        Properties result = new Properties();
        result.putAll(legacy);
        migrateBanner(result, "header");
        migrateBanner(result, "footer");
        return result;
    }

    private static void migrateBanner(Properties props, String prefix) {
        if (props.getProperty(prefix + ".text.0") != null) {
            return;
        }
        if (props.getProperty(prefix + ".variant") != null) {
            migrateVariantFormat(props, prefix);
        } else {
            props.setProperty(prefix + ".text.0", props.getProperty(prefix + "Text", ""));
        }
        // alte Schlüssel entfernen, damit sie nicht in die neue Datei wandern
        for (String key : new String[] {".variant", ".text1", ".text2", ".image1", ".image2"}) {
            props.remove(prefix + key);
        }
        props.remove(prefix + "Text");
    }

    /**
     * Migration des Zwischenformats mit Layout-Varianten: die Slot-Reihenfolge der
     * Variante wird im Text/Bild-Raster nachgebildet (T = Text 1, U = Text 2,
     * 1/2 = Bild 1/2), damit die Anzeige-Reihenfolge erhalten bleibt.
     */
    private static void migrateVariantFormat(Properties props, String prefix) {
        String pattern = switch (props.getProperty(prefix + ".variant", "")) {
            case "BILD_TEXT" -> "1T";
            case "TEXT_BILD" -> "T1";
            case "BILD_TEXT_BILD" -> "1T2";
            case "TEXT_BILD_TEXT" -> "T1U";
            case "NUR_BILD" -> "1";
            case "BILDER" -> "12";
            default -> "T";
        };
        String[] texts = new String[TEXT_SLOTS];
        String[] images = new String[IMAGE_SLOTS];
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
                images[position / 2] = props.getProperty(
                        prefix + (slot == '1' ? ".image1" : ".image2"), "");
            }
            position++;
        }
        // text.0 immer setzen: daran erkennt der Leser das Raster-Format
        for (int i = 0; i < TEXT_SLOTS; i++) {
            props.setProperty(prefix + ".text." + i, texts[i] == null ? "" : texts[i]);
        }
        for (int i = 0; i < IMAGE_SLOTS; i++) {
            props.setProperty(prefix + ".image." + i, images[i] == null ? "" : images[i]);
        }
    }
}

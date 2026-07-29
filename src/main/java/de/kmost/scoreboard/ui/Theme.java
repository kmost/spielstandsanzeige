package de.kmost.scoreboard.ui;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import javafx.scene.paint.Color;

/**
 * Eine vollständige Farbauswahl für die Publikumsanzeige plus optionaler
 * Schriftfamilie (leer = Systemschrift) und Größenfaktoren je {@link FontScale}
 * (1.0 = Standard); bei Header und Footer skaliert der Faktor Schrift und
 * Raster-Anteil des Banners gemeinsam. Fehlende Farb- und Faktor-Einträge
 * werden beim Erzeugen mit den Standardwerten aufgefüllt. Unveränderlich.
 */
public record Theme(Map<ThemeColor, Color> colors, String fontFamily,
        Map<FontScale, Double> scales) {

    public Theme {
        Map<ThemeColor, Color> completeColors = new EnumMap<>(ThemeColor.class);
        for (ThemeColor color : ThemeColor.values()) {
            completeColors.put(color, colors.getOrDefault(color, color.defaultColor()));
        }
        colors = Map.copyOf(completeColors);
        fontFamily = fontFamily == null ? "" : fontFamily.strip();
        Map<FontScale, Double> completeScales = new EnumMap<>(FontScale.class);
        for (FontScale scale : FontScale.values()) {
            completeScales.put(scale, clampScale(scales.getOrDefault(scale, 1.0)));
        }
        scales = Map.copyOf(completeScales);
    }

    public Theme(Map<ThemeColor, Color> colors) {
        this(colors, "");
    }

    public Theme(Map<ThemeColor, Color> colors, String fontFamily) {
        this(colors, fontFamily, Map.of());
    }

    public static Theme defaults() {
        return new Theme(Map.of());
    }

    public Color color(ThemeColor color) {
        return colors.get(color);
    }

    public double scale(FontScale scale) {
        return scales.get(scale);
    }

    public Theme with(ThemeColor color, Color value) {
        Map<ThemeColor, Color> changed = new EnumMap<>(colors);
        changed.put(color, value);
        return new Theme(changed, fontFamily, scales);
    }

    public Theme with(FontScale scale, double value) {
        Map<FontScale, Double> changed = new EnumMap<>(scales);
        changed.put(scale, value);
        return new Theme(colors, fontFamily, changed);
    }

    public Theme withFont(String family) {
        return new Theme(colors, family, scales);
    }

    /** Unsinnige Werte (aus alten/kaputten Dateien) auf einen brauchbaren Bereich begrenzen. */
    private static double clampScale(double scale) {
        return Double.isNaN(scale) ? 1.0 : Math.max(0.25, Math.min(3.0, scale));
    }

    /**
     * CSS-Variablen-Deklarationen für den Style des Wurzelknotens der Anzeige.
     * Die halbtransparenten Hintergründe der Strafen- und Timeout-Chips werden
     * aus der jeweiligen Textfarbe abgeleitet.
     */
    public String css() {
        StringBuilder css = new StringBuilder();
        for (ThemeColor color : ThemeColor.values()) {
            css.append(color.cssVariable()).append(": ").append(toWeb(color(color))).append("; ");
        }
        css.append("-color-penalty-fill: ").append(toWeb(chipFill(color(ThemeColor.PENALTY)))).append("; ");
        css.append("-color-timeout-fill: ").append(toWeb(chipFill(color(ThemeColor.TIMEOUT)))).append("; ");
        return css.toString();
    }

    /**
     * Schriftfamilie als Root-Deklaration für die Publikumsanzeige; leer bei
     * Systemschrift. Gilt für alle Elemente der Anzeige, auch für Uhr, Tore
     * und Straf-/Timeout-Zeiten.
     */
    public String fontCss() {
        return fontFamily.isEmpty() ? "" : "-fx-font-family: \"" + fontFamily + "\"; ";
    }

    private static Color chipFill(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.12);
    }

    /** Web-Notation #RRGGBB bzw. #RRGGBBAA, wie Color.web() sie wieder einliest. */
    public static String toWeb(Color color) {
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
        if (color.getOpacity() < 1.0) {
            hex += String.format(Locale.ROOT, "%02X", (int) Math.round(color.getOpacity() * 255));
        }
        return hex;
    }
}

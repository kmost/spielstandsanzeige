package de.kmost.scoreboard.store;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.scene.paint.Color;

/** Abbildung eines {@link Theme} auf Properties und zurück; gemeinsam für Themes und aktive Anzeige. */
final class ThemeProperties {

    private ThemeProperties() {
    }

    static Properties toProperties(Theme theme) {
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

    static Theme fromProperties(Properties props, ProblemReporter reporter) {
        Map<ThemeColor, Color> colors = new EnumMap<>(ThemeColor.class);
        for (ThemeColor color : ThemeColor.values()) {
            String value = props.getProperty(color.key(), "");
            if (!value.isBlank()) {
                try {
                    colors.put(color, Color.web(value));
                } catch (IllegalArgumentException e) {
                    reporter.log("Ungültige Farbe für „" + color.key() + "“: " + value, e);
                }
            }
        }
        Map<FontScale, Double> scales = new EnumMap<>(FontScale.class);
        for (FontScale scale : FontScale.values()) {
            scales.put(scale, scaleFrom(props, scale.key(), reporter));
        }
        return new Theme(colors, props.getProperty("font", ""), scales);
    }

    /** Größenfaktor; fehlend oder unlesbar = Standard 1.0. */
    private static double scaleFrom(Properties props, String key, ProblemReporter reporter) {
        try {
            return Double.parseDouble(props.getProperty(key, "1.0"));
        } catch (NumberFormatException e) {
            reporter.log("Ungültiger Wert für „" + key + "“: " + props.getProperty(key), e);
            return 1.0;
        }
    }
}
